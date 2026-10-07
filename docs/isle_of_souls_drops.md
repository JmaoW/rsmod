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
**2x** as likely and a tier 4 relic makes them **5x** (`LeagueDropMod`, which replaces the 2x rather
than stacking).

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

1. Add the monster's wiki page name to `tools/drops/monsters.txt` (one per line). A line can add
   options: `Grotesque Guardians | ids_from=Dusk; Dawn` takes npc ids from other pages when the
   page's own infobox has none, and `| ids=1,2,3` gives ids directly.
2. Run `python tools/drops/wiki_import.py` (add `--refresh` to download pages again; pages are
   cached in `tools/drops/.cache`, which is not committed).
3. Read the report it prints (also written to `tools/drops/last_report.txt`). It lists what was
   skipped and why.
4. Start the server and check the `Drop data:` warnings in the log.

What the import understands: the infobox (so each drop table gets its **npc ids** and the
wiki's own drop versions), the 100% / main / tertiary / pre-roll sections, clue scroll lines,
the rare drop table and gem drop table (with their own `[[shared]]` tables), herb drop tables,
`rolls=2` tables, line-level `dropversion` variants, and the boosted drops list. Tables that
list several separate 100% rolls (such as Kalphite Queen's consumables) become separate
always-rolled tables. Pre-roll lines are treated as independent rolls.

What it skips (and tells you): lines with a word instead of a number as the rarity (`Common`,
`Varies`, `Once`), "always" lines inside tertiary tables (those are conditional on a quest or a
task), brimstone keys, free-to-play-only lines, and tables made by templates that are not
understood yet (seed, talisman and slayer-specific tables). **Check the numbers on anything
important** — a table that did not add up to 100% is scaled down and noted in the report.

## Not built yet

- Clue scroll items for the tertiary entries (the plan's section 3.5), and the treasure trail
  and superior slayer boosts.
- Announcements for rare drops (hook `LeagueAnnouncer.drop` to `NpcDropEvents.Rolled`).
- Kill-count tasks (hook them to `NpcDeathEvents.Killed`).
- The seed, talisman and slayer-specific drop templates in the import.
- Echo bosses (`Cerberus (Echo)` and friends): their pages need checking before they can go in
  `monsters.txt`.
