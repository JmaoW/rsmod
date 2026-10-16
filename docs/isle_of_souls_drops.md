# Isle of Souls drop tables

How monster drops work, and how to add or change them. Code: `api/drops` (the model and the roll),
`content/other/drop-tables` (loading the data files) and `tools/drops` (the wiki import).

## What happens when a monster dies

1. The engine publishes `NpcDeathEvents.Killed(npc, hero)`. The hero is the player who did the most
   damage. Teammates share an observer id, so they can pick up what the hero is given.
2. If the monster has a drop table, it is rolled and every item is dropped on the ground for the
   hero. A `NpcDropEvents.Modify` event is published first so relics can change the drop rate of
   boosted drops, and `NpcDropEvents.Rolled(npc, hero, drops)` afterwards (for announcements and
   tasks).
3. A monster **without** a drop table still drops bones only, as before.

No hero (nobody did damage) means no drops.

## Boosted drops (the relic drop rate)

The Raging Echoes relics do **not** raise every drop: they raise the chance of specific rare items
from specific monsters (the wiki's
[Boosted drops](https://oldschool.runescape.wiki/w/Raging_Echoes_League/Boosted_drops) list: 242
item and monster pairs, such as the abyssal whip from an abyssal demon). A tier 1 relic makes those
**5x** as likely and a tier 4 relic makes them **20x** (`LeagueDropMod`, which replaces the 5x
rather than stacking). These are raised from the wiki's 2x and 5x for the shortened event.

So the tables mark each entry as `boosted` or not, and the drop rate multiplier only applies to
boosted entries. Everything else, and every `always` drop, stays as it is. If boosting a `main`
table would take it over 100%, its entries are scaled back in proportion.

`boosted.toml` lists the boosted pairs (generated from the wiki page by the import tool); the
loader marks a monster's matching items as boosted when it reads the tables. You can also write
`boosted = true` on any line yourself (in `isle.toml`, for your own additions). Echo boss uniques
are deliberately not boosted: the wiki gives those a fixed rate.

Not yet boosted: treasure trail casket uniques and superior slayer monsters (the wiki's "Global"
section); they come with the clue and slayer systems.

## The data files

All in `content/other/drop-tables/src/main/resources/org/rsmod/content/other/drops/`, read in this
order when the server starts:

| File | Purpose |
| --- | --- |
| `osrs.toml` | The real OSRS tables. **Generated** by `tools/drops/wiki_import.py`; do not hand-edit. |
| `boosted.toml` | Which of those drops the relics boost. **Generated** by the same tool. |
| `isle.toml` | Your changes. A monster here **adds** its lines to the same monster in `osrs.toml`; `replace = true` throws the OSRS table away. |

Because the layers are separate, re-running the import never loses what you changed.

### Format

```toml
[[shared]]                      # a table other tables can roll into, such as the rare drop table
name = "Rare drop table"
main = [{ item = "Nature rune", qty = "67", chance = "5/128" }]

[[monster]]
name = "Goblin"                 # matches every npc with this name (any case)...
levels = [2, 13]                # ...optionally only these combat levels
npcs = ["goblin_2"]             # ...or add npcs by internal name
ids = [3029, 3030]              # ...or by npc type id (what the wiki import writes)
source = "Goblin"               # label that [[boost]] lines refer to (defaults to name)
replace = false                 # isle.toml only: true replaces instead of adds
main_rolls = 1                  # roll the main table this many times (default 1)

always = [{ item = "Bones" }]                                  # every kill
main = [                                                       # ONE roll: at most one of these
  { item = "Coins", qty = "5-9", chance = "28/128" },
  { item = "Coins", qty = "100", noted = true, chance = "1/128" },
  { item = "Abyssal whip", chance = "1/512", boosted = true },
  { table = "Rare drop table", chance = "1/128" },
]                                                              # leftover chance = nothing
tertiary = [{ item = "Clue scroll (easy)", chance = "1/128" }] # each rolled on its own

[[boost]]                       # normally in boosted.toml
source = "Abyssal demon"
item = "Abyssal whip"
```

- `item` is the item's name in the game; the lowest id wins when several items share a name, and
  noted and placeholder items are never picked by name. `obj = "internal_name"` picks an exact item.
- `qty` is `5` or `1-5`. `chance` is `3/128`, `~1/5,000`, `12.5%` or `0.25`.
- `main` chances add up to at most 100%. This is how the wiki's tables work: a monster's main table
  is one roll, so the numbers on the wiki are used as they are.

### What is an error and what is a warning

- **A mistake in the data stops the server on start** with the file and monster named: a bad
  chance, a missing `chance`, `main` adding up to over 100%, a shared table that includes itself.
- **A name that is not in the cache is a warning** in the log (`Drop data: ...`), and that line is
  skipped: an item that does not exist, a monster or npc id that matched nothing, a noted version
  that does not exist.
- The log also says how many boosted drops matched a monster and how many have none yet (the rest
  of the boosted list belongs to monsters that have not been imported).

## Importing monsters from the wiki

Every attackable monster the world spawns has a drop table (about 1,645 of 1,690 npc types, 95% of the
spawns). The rest are monsters the wiki gives no drop table at all (ducks, leeches, mosquitos, the
revenants, a few quest npcs); they drop bones only.

1. `python tools/drops/spawned_monsters.py` lists the wiki page of every attackable npc in the
   generated spawn files (`tools/spawns`) into `tools/drops/monsters_spawned.txt`. The page of an npc
   id comes from osrsreboxed-db (names like Guard or Man are ambiguous on the wiki), so an npc id
   always lands on the right page. Run it again after the spawn files change.
2. `tools/drops/monsters.txt` holds the hand-written lines (monsters to add, or with options such as
   `Grotesque Guardians | ids_from=Dusk; Dawn` or `| ids=1,2,3`). It is read first and wins over
   the generated list.
3. `python tools/drops/wiki_import.py` downloads the pages (cached in `tools/drops/.cache`, which is
   not committed; `--refresh` downloads again, `--no-spawned` uses only `monsters.txt`) and writes
   `osrs.toml` and `boosted.toml`. A full download takes about 15 minutes at the wiki's rate limit;
   from the cache it takes seconds.
4. Read the report it prints (also `tools/drops/last_report.txt`): what was skipped and why.
5. `python tools/drops/coverage.py` writes `tools/drops/coverage.txt`: the spawned npc types that
   still have no table, grouped by name.
6. `python tools/drops/crosscheck.py` compares each table with osrsreboxed-db's reading of the same wiki
   page and writes `tools/drops/crosscheck.txt`: about 65 of 580 monsters differ, almost all for
   known reasons (skilling drops such as an Ent's logs, tables the import gives to another version of
   the page, items renamed in the cache, the Kalphite Queen's consumables, which the import turns
   into shared tables). Read it after changing the import.
7. Run the drop-tables integration tests. `OsrsDropDataTest` loads the real data against the real
   cache and writes every warning to `isle-drop-warnings.txt` (and the boosted drops that matched
   nothing to `isle-drop-unmatched-boosts.txt`) in the temp directory; the server itself only logs
   the first 25.

What the import understands: the infobox (so each drop table gets its **npc ids** and the wiki's
own drop versions, including the default version of a page that also has Catacombs or Giants'
Plateau versions), the 100% / main / tertiary / pre-roll / secondary sections, clue scroll lines, the
rare drop table and gem drop table (with their own `[[shared]]` tables), the herb tables (normal,
useful, combat), the seed tables (general, uncommon, rare, allotment, tree and herb) and the talisman
table, `rolls=2` tables, line-level `dropversion` variants, herb tables written out by hand with
`{{#vardefine}}` and `{{#expr}}`, and the boosted drops list. Plural and grouped names in that list
("Steel dragons", "Bandos armour") are spelled out into the exact monsters and items.

What it skips on purpose (and counts in the report):
- Lines only for the free-to-play game, or "in place of the regular drops" lines.
- "Always" lines inside tertiary tables (those are conditional on a quest or a task), brimstone keys.
- Tables for content this event does not have: the Wilderness Slayer task tables (Krystilia's), the
  Catacombs of Kourend variants, Fossil Island, the Wilderness God Wars Dungeon, and pickpocket loot.
- Rarities that are a word with no number (`Unknown`, `Varies`, `Once`).

Word rarities are **approximated** and rolled on their own (so a guess cannot disturb the exact
numbers): Common 1/10, Uncommon 1/40, Rare 1/250, Very rare 1/1,000. Each one is named in the report.

A table that did not add up to 100% is scaled down and noted in the report. About 50 are over by 1
to 10% (the wiki's own totals, mostly dragons, bosses and a few low-level monsters), and the
bosses with several separate rolls (Callisto, Venenatis, Vet'ion, the Desert Treasure II bosses) are
over by more; **check those by hand before you rely on their numbers**.

Items the wiki names that this cache does not have (some quest and elemental items) are
skipped when the server starts and listed in `isle_of_souls_missing_items.md`.

## Announcements and kill tasks

- `LeagueEventScript` announces a rare drop to everyone, through `LeagueAnnouncer.drop`, for each
  item `NpcDropEvents.Rolled` hands out that is on the list in `announcements.json`
  (`"drops": { "objs": ["abyssal_whip", ...] }`, the item's internal name). The list is empty by
  default, so nothing is announced until you pick the items.
- `LeagueKillTasks` completes the league's kill tasks when `NpcDeathEvents.Killed` is published
  (65 tasks: "Defeat a Goblin", "Kill 30 Bandits", "Defeat 150 Demonic Gorillas"). The monster name
  is matched exactly, in any letter case. Place words are removed from the task text ("Defeat a
  Kurask in Tirannwn" is "Defeat a Kurask"). The list is made by `tools/drops/kill_tasks.py` from
  the task list and the spawned monsters; boss ladders (50, 150, 300 kills) wait for the boss fights
  (content plan 3.4), and so do tasks with a special way of killing (a spell, a weapon, a time limit).

## Not built yet

- Clue scroll items for the tertiary entries (the plan's section 3.5), and the treasure trail
  and superior slayer boosts.
- Echo bosses (`Cerberus (Echo)` and friends): their pages need checking before they can go in
  `monsters.txt`.
- Disguised monsters (Cave kraken, Sand crabs, Zygomites) spawn as their disguise, so their tables
  are attached to the real monster but no player meets it yet.
- Monsters added to the game after the 2023 spawn list have no spawns, so no table is imported
  for them (the spawn doc lists them).
