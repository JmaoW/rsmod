# Isle of Souls: Content Plan for the Event

Everything we want built before the one-day team league (teams of two, ranked by shared team
points, fastest wins ties). First written 2026-10-06 from the project notes,
`isle_of_souls_relic_plan.md` and `isle_of_souls_teams.md`; updated the same day with your answers.

**Decided so far:**
1. **All skills** are trainable and useful in some way.
2. **All 8 relic tiers** are expected to be unlocked, so every relic must work.
3. **Fight Caves and Inferno**, both as shortened versions.
4. The event runs **8-10 hours**. **No date is set**, so there is no deadline: the build order
   below is about dependencies, not about cutting things.
5. **Best players should be able to max during the event**, so 99s arriving quickly in the higher
   relic tiers is fine. XP rates stay high for testing and are tuned down before the event.
6. **The task list grows as content is added**: every wiki task that is possible on the isle goes
   back in. It must end up able to reach the final relic tier.
7. **Clues are a drop, not a treasure trail**: scrolls drop, reading one gives the casket straight
   away, for everyone (section 3.5).
8. **No Wintertodt or Tempoross** this event.
9. **The whole isle is open from the start and there are no area unlocks** (section 5.1).
10. **Drop tables start from the real OSRS tables**, with your own additions to chosen monsters.
    You will supply the monster list later.
11. **Clue caskets give exactly the real game's rewards** (section 3.5).
12. **Many more bosses than the two instances**, including as many of the nine Raging Echoes echo
    bosses as possible (section 3.4).
13. **Combat masteries, all of them accessible**: all 18 masteries and the 6 passives, worded as
    close to the original as possible, with each of the 10 mastery tasks granting 2 points
    (section 3.6).

How to use this doc: each item has a priority and a checkbox. **(decided)** means you chose it;
**(proposed)** is my suggestion and needs your yes or no.

**Reference sources:** two outside GitHub repos, `RSPSApp/tsps` and `RSPSApp/osrsreboxed-db`, are
useful for many items below. Section 11 lists what each is good for and the cautions; items that
can use them carry a **Reference:** line.

Priority key: **P0** the event is broken without it, **P1** the event is much better with it,
**P2** only if time is left.

---

## 0. Scope

The decisions above add up to roughly: 17 skills still to build, 22 relics with real effects,
two boss instances, a combat core with drop tables, clue and casket rewards, 18 combat masteries
with 6 passives, and a task list about four times its current size. With no date set this is a long project, so each piece is still kept
to a **minimum useful version** so the whole thing stays finishable:

> A skill, relic or boss is "done" when a player can train it, it feeds at least one task, and it
> helps with something else on the isle (a boss, another skill, a relic, shop money). Nothing more.

The order matters because early items unblock everything after them (section 9).

### Findings, now settled
- **Task list size.** The current 139 tasks are worth 10,630 points; the top relic tier unlocks at
  25,000. **(decided)** the list grows as skills, NPCs and bosses are added, until the final tier
  is comfortably reachable. The thresholds are set last, from the finished list (section 6.2).
- **XP rates.** Realm rate 150x with the relic multiplier ending at 16x is about 2,400x, which makes
  level 99 roughly 5,400 raw XP. **(decided)** rates stay high while testing and are tuned down
  before the event, with the aim that the best players can max during the 8-10 hours. Because 99s
  are meant to come quickly late in the event, the level-99 and XP-milestone tasks (most of the 32
  elite tasks, 6,400 points) stay in, but they will only separate teams once the final rate
  is set (section 1.3).

---

## 0.1 Where we are

| Area | State |
|------|-------|
| League screens | Done and confirmed in the real client: Leagues tab, tasks list, relic select, tracker, points, pop-ups |
| Teams | Done, 64 tests pass, **not yet tried in a real client**: roster file, shared tasks/points/bank/loot, rankings, freeze, announcements, scoreboard |
| Instances | `TeamInstances` done, **not yet tried in a real client** (no real content uses it yet) |
| Skills | Mining, woodcutting, fishing, cooking, firemaking, smithing, fletching and prayer built and wired to tasks; gem cutting partly |
| Relics | 17 of 23 relics have effects (2026-11-03); left: the three tier 3 teleports, Treasure Arbiter, Pocket Kingdom, Overgrown |
| Combat content | Combat engine exists, but there are **no drop tables, no combat NPC content, no bosses and no two-player NPC targeting** |
| Combat masteries | Screen, unlock rules, points and the 10 mastery tasks (state only) are built and tested; **no combat effects yet** (section 3.6) |

---

## 1. Skills

**(decided)** every skill trainable and useful. All 23 are listed with what "useful" means for the
event. Effort: S = a day or less, M = a few days, L = a week or more of normal work.

| Skill | State | What makes it useful (proposed) | Effort |
|-------|-------|--------------------------------|--------|
| Mining | Done | Ore for smithing, runes (essence), gems | done |
| Smithing | Done | Weapons and armour for combat | done |
| Woodcutting | Done | Logs for firemaking, fletching | done |
| Firemaking | Done | Tasks, pyre/fire mechanics, cooking spots | done |
| Fishing | Done | Raw food for cooking, tasks | done |
| Cooking | Done; 2026-10-12 added 54 more cooked foods (all Hunter meats, manta ray, sea turtle, dark crab, eels, stew, curry, cake, members' pies, baked potato), item-on-item preparation (dough, pies, pizza, cake, stew, potato toppings) and wine, plus eating every one of them (`isle_of_souls_food_plan.md`) | Food to survive combat and bosses | done |
| Attack, Strength, Defence, Hitpoints | Engine only | Trained by killing NPCs; they decide who can beat Fight Caves/Inferno | M (needs NPCs, 4.1) |
| Ranged | Engine only | Ammo from fletching, hunter chinchompas, dragonhide from crafting | M |
| Magic | Elemental spells only | Combat spells, alchemy for shop money, teleports, runes from runecraft | M |
| Prayer | Done: bury, altars (normal and chaos), Redemption, all prayers unlocked, 14 tasks (`isle_of_souls_prayer_plan.md`) | Bones from kills, altar; protection prayers are needed for Jad and Zuk | done |
| Fletching | Done: every wiki recipe except Crafting's amethyst tips and the ogre bow (shafts, bows, stocks, shields, crossbows incl. blurite/dragon, arrows, javelins, darts, bolts, tips, broad/amethyst/dragon ammo, any feather); 18 tasks; Echo axe auto-fletch counts | Arrows, bolts, bows for ranged; Lumberjack toggle | done |
| Crafting | Done (scope C, `isle_of_souls_crafting_plan.md`): every wiki recipe except crystal singing and golem crafting - gems (with crush), leather, d'hide + shields, snakeskin, yak, snelms, crab, Xerician, splitbark, mixed hide, Hueycoatl, battlestaves, amethyst tips, spinning, loom, pottery, glass, gold + silver jewellery on the real interfaces, birdhouses, tanner NPC; 20 tasks; `::craftkit` | Dragonhide armour, jewellery, gems for ranged, magic and boss gear. **No source for any material yet** | done |
| Herblore | Done: cleaning, unfinished and finished potions (33 potions incl. super combat, divine, brews, stamina, extended antifire, goading, prayer regeneration), pestle and mortar, drinking with real effects, Forager perks, 30 tasks (`isle_of_souls_herblore_plan.md`); 2026-10-12 added the potions other content hands out: overload, Chambers of Xeric, blighted, Barbarian Assault copies, mixes, brews (`isle_of_souls_food_plan.md`) | Combat, prayer and ranging potions for bosses; Forager herbs become useful. **No source for secondaries yet** | done |
| Runecraft | Done 2026-10-07 (`isle_of_souls_runecraft_plan.md`): essence mine, 14 altars (soul phase 2), combination runes, tiaras, pouches, the Abyss via the Mage of Zamorak (who sells runes), 25 tasks, `::runekit` | Runes for magic, so magic actually works | done |
| Thieving | Done 2026-10-09 (`isle_of_souls_thieving_plan.md`): 23 pickpocket targets from tsps, 19 stalls from the Wiki, coin pouches, gloves of silence, rogue set and dodgy necklace effects, Dodgy Deals, 33 tasks, `::thievekit` | Stalls and pickpocketing as the coin source; Dodgy Deals | done |
| Agility | Done 2026-10-09 (`isle_of_souls_agility_plan.md`): the real Brimhaven Agility Arena, Grace's graceful shop, marks of grace, Corner Cutter, 11 tasks (the 6 courses and 5 shortcuts built first were removed 2026-10-10 with their 11 tasks) | One course on the isle, run energy, shortcuts; Corner Cutter | done |
| Hunter | Done 2026-10-09 (`isle_of_souls_hunter_plan.md`): 27 trap creatures (bird snare, box trap, magic box, deadfall, net traps, pitfalls), butterflies, moths, implings, falconry, birdhouses on a ten minute growth, 35 tasks, `::huntkit` | Chinchompas (ranged ammo), implings, fur; Animal Wrangler hunter half and Forager hook | done |
| Slayer | Done 2026-10-11 (`isle_of_souls_slayer_plan.md`): 3 masters (Turael, Vannaka, Duradel), the cache task tables, gear rules, finishing items, the rewards store as a dialogue, superiors, Passives and Slayer Master relics, 16 tasks | Task assigner, Slayer points and XP; reuses combat NPCs | done |
| Farming | None | Herb and food patches on the isle. Needs a shortened growth time (see below) | L |
| Construction | None | Biggest unknown: see below | L |

**Construction and Farming (decided as the starting point, design still open):** you called these
the two hardest to figure out, and agreed the ideas below are a good place to start. Expect to
revise them once the other skills exist.
- **Construction:** instead of a player-owned house, a few **build sites** on the isle where players
  build a small set of benefits (restore pool, range, bank chest, teleport pad) from planks and
  nails. This gives XP, tasks and a use for woodcutting and smithing without houses or instancing.
- **Farming:** wiki growth times run to hours, so keep herb and food patches only, with a global
  growth-time multiplier. (You earlier decided to drop the farming-ticks *relic passive*; a global
  setting is a different thing, but say if you want it gone too.) Overgrown depends on this.

### 1.1 Finish the six skills we already have
- [ ] **P0** Audit the 139 tasks: for each task with no trigger, build it, replace it or delete it.
- [ ] **P0** Test all six skills in the real client end to end (smithing's skill-multi and anvil
  interfaces have never been seen in the client).
- [ ] **P1** Confirm Power Miner / Lumberjack toggles and firemaking never-fail in the client.
- [ ] **(decided)** Wintertodt and Tempoross are **out** for this event. Delete their league tasks
  (the three Tempoross kill tasks, ids 1084-1086) and any task that needs them.
- [ ] **P2** Dark crabs, anglerfish, pizzas, pyre logs, torches/lanterns, so their tasks can come
  back.

### 1.2 Build order for new skills
**Reference (section 11):** thieving has a ready target table (`pickpocketing.json`, `thieving.md`
in tsps); hunter, agility, farming, construction and slayer each have a tsps doc or data file;
`slayer-tasks.json` in osrsreboxed-db has all 16 masters' task tables.

Cheapest first, and weighted by what they unlock:
1. **Combat training** (four melee stats, ranged, magic, hitpoints, prayer): the foundation. Needs
   section 4.1 first.
2. **Fletching, runecraft, crafting, herblore**: small, and they make ranged, magic and potions
   work for boss runs.
3. **Thieving, agility**: shop money and two relics.
4. **Slayer**: reuses the combat NPCs; unlocks two relic passives and Slayer Master.
5. **Hunter, farming, construction**: the largest, and each unlocks only one relic. Do them last.

### 1.3 Skill-wide
- [ ] **P0 (before the event, not now)** Final XP rates **(decided: keep high for testing, tune down
  for the event)**. Target: the best players can reach 99 in most skills within the 8-10 hours, with
  99s coming quickly once a team is in the higher relic tiers. Set the base rate and the relic
  multipliers together, then check the level-99 and XP-milestone task times against it.
- [ ] **P1** Island-scoped XP and area state: needs `./gradlew packCache` run once.

---

## 2. Relics

**(decided)** every tier can be unlocked, so all 22 relics need a working effect. Source for the
original wording is `isle_of_souls_relic_plan.md`. Earlier decisions still stand: keep XP
multipliers and drop-rate passives; drop minigame-points and farming-tick passives; Reloaded stays
a repick; tier 3 teleports become island teleports.

### 2.1 P0
- [ ] Rework thresholds (section 6.2) so tier 8 is reachable in the event.
- [x] **Reloaded** repick flow (done 2026-11-03).
- [ ] Drop-rate passives need the drop-table system (section 3.1).
- [ ] Remove any relic that cannot be finished: a selectable relic that does nothing is worse than none.

### 2.2 Tier by tier

| Tier | Relic | State | What it needs |
|------|-------|-------|---------------|
| 1 | Power Miner, Lumberjack | Done | Check toggles in client |
| 1 | Animal Wrangler | Done 2026-10-09 (hunter half: traps never fail, box traps faster with doubled chinchompa catches and XP, jars never break) | Check in client |
| 2 | Corner Cutter | Done 2026-10-09: greaves running XP, always succeed, double laps and 25% bonus, marks with 10,000 coins | Check in client |
| 2 | Friendly Forager | Done: herbs from mining, woodcutting, fishing and (2026-10-09) hunting | Check in client |
| 2 | Dodgy Deals | Done 2026-10-09: area pickpocket, 100% success, auto-repeat, noted loot, 3x pouches, stalls never empty | Check in client |
| 3 | Clue Compass, Bank Heist, Fairy's Flight | Nothing | Island teleports (destinations in section 5). There are no treasure trails, so the Compass cannot teleport to clue steps: **(proposed)** it teleports to boss entrances and other named places on the isle |
| 3 | Passives | Done: 2x combat XP; 2026-10-11 5x Slayer points, free Bigger and Badder, superiors 1 in 50 | Check in client |
| 4 | Golden God | Done 2026-10-08: free, +15%, 65% kept, repeating alchemy; 20,000 coins on an altar for dragon bone XP; noted shop purchases through the shop's Noted items button | Check in client |
| 4 | Reloaded | Done 2026-11-03: one extra relic from tiers 1-3, in the client's `selection_other` varbits | Check in client |
| 4 | Equilibrium | Done 2026-11-03: wiki numbers, bonus skips every multiplier | Check in client |
| 5 | Production Master | Done 2026-11-03: every production job in one go (ammo 10 sets) | Check in client |
| 5 | Treasure Arbiter | Nothing | **(decided)** clues drop more often and caskets always roll the maximum number of times (section 3.5). Needs the clue and casket system |
| 5 | Slayer Master | Done 2026-10-11: always on task, free perks and store, 100th-kill bonus XP | Check in client |
| 6 | Total Recall, Banker's Note | Done 2026-11-03: crystal of echoes (not in instances), banker's note (Toggle guessed) | Check in client |
| 7 | Pocket Kingdom | Nothing | **(decided)** replaced by "Pocket Workshop": portable furnace, anvil, range and loom (no Miscellania) |
| 7 | Grimoire | Done 2026-11-03: arcane grimoire swaps spellbooks | Check in client |
| 7 | Overgrown | Nothing | Farming |
| 8 | Specialist | Done 2026-11-03: 20% cost, 2x accuracy, refunds | Check in client |
| 8 | Guardian | Done 2026-11-03: horn follower, weakest-style hits, area attack; looks like a royal guard (cache npc has no model) | Check in client |
| 8 | Last Stand | Done 2026-11-03: heal from damage dealt; food not blocked | Check in client |

Relics that depend on a skill are built when that skill is.

---

## 3. Combat core and bosses

### 3.1 Combat core: what exists and what to build
A survey of the engine (2026-10-06) found more than the plan first assumed. Already there: monster
stats come from the cache, npc melee attacks and retaliation (`NvPCombat`), aggression and hunting,
the npc death sequence and respawn, hero points (who gets the loot), and shared team loot.

Status of the build, in order:
- [x] **Drop tables (done 2026-10-06)**: real OSRS format, layered `osrs.toml` + `boosted.toml` +
  `isle.toml`, a roller, and the death hook. See `isle_of_souls_drops.md`.
- [x] **Relic drop rates (done)**: the relics boost only the wiki's list of boosted item and monster
  pairs (5x with a tier 1 relic, 20x with tier 4; the wiki's 2x and 5x, raised for the short event), not every drop. The boosted list is imported from
  the wiki (242 pairs).
- [x] **Wiki import tool (done)**: `tools/drops/wiki_import.py` fills `osrs.toml` and `boosted.toml`
  for the monsters in `tools/drops/monsters.txt`, with npc ids from the wiki's infoboxes. First
  batch of 16 pages imported (basic monsters plus the normal bosses that echo bosses pair with).
- [x] **Multi-combat everywhere (done)**: `combat_multiway_everywhere` in `Constants.kt`, used by
  every single-combat check.
- [x] **Monster list (done 2026-10-09)**: every attackable monster in the world spawn list. The wiki pages
  come from the spawns (`tools/drops/spawned_monsters.py`), so no list had to be supplied; `monsters.txt`
  is for hand-written additions. 1,645 of 1,690 spawned npc types have a real drop table.
- [x] **Monster spawns (done 2026-10-09, needs packCache)**: 22,700 world spawns from the rs-map-viewer list,
  checked against the cache, in `content/areas/misc/osrs-npc-spawns`. See `isle_of_souls_npc_spawns.md`.
- [x] **Kill hooks (done 2026-10-09)**: rare drops announce through `LeagueAnnouncer.drop` (the item list
  is in `announcements.json`), and `LeagueKillTasks` completes 65 kill tasks.
- [x] **Npc melee, ranged and magic attacks (done 2026-10-06)**: `NpcAttacks` performs an
  `NpcAttack` with the standard accuracy and max hit formulas, protection prayers, a projectile
  and a hit that lands with it. See `isle_of_souls_npc_combat.md`.
- [x] **Multi-target attacks (done 2026-10-06)**: every npc type has a targeting style, set by its
  boss script: one target (default), alternating, or all targets in range (Jad fires one
  projectile at each teammate, each with its own roll and damage). Team instances keep it to the
  players in the same instance. 7 integration tests.
- [ ] **Shops**: general store and skill supply shops (the shop API exists in `api/shops`).
- [ ] Death inside an instance (last unchecked line of the foundation checklist).
- [x] Kill-count task triggers via `tracker.addProgress` (`LeagueKillTasks`; boss ladders wait for the bosses).

### 3.2 Fight Caves **(built 2026-10-06, needs a real-client check)**
Team instance, both players inside, Jad at the end. Built with the real 63 waves **and** the
shortened 8 + Jad, switched by `.data/league/event-settings.json` (default full). Details, what
differs from the plan and the client checklist are in `isle_of_souls_fight_caves_plan.md`.
- [x] Wave lists, spawn rotations, all five monster types, Jad (telegraphed fire/rocks/bite, all
  players targeted, healers), Tz-Kek split, win rewards (fire cape, Tokkul, pet roll), announcement.
- [x] Death: a dead player respawns in the lobby and can walk back in; the run continues.
- [x] Tasks: complete the cave 5 and 10 times. **Not yet:** equip a fire cape, survive a Jad hit
  without prayer.
- [ ] **You:** run `./gradlew packCache`, then check it in the client (list in the plan file).
- [x] The Inferno gatekeeper option ("Exchange fire cape") now takes the one-off cape (2026-10-08). *Removed again 2026-10-10 pending a rework.*

### 3.3 Inferno **(built 2026-10-08, needs a real-client check)**
Built in full, not just shortened: the real 69 waves **and** a shortened list (9 waves, with both Jad
waves and TzKal-Zuk), switched by `infernoMode` in `.data/league/event-settings.json`. Pillars,
the moving glyph, the minion sets, Zuk's Jad and the enrage are all in. What was built, what to check
in the client and every guessed number are in `isle_of_souls_inferno_plan.md`. The original plan
below is kept for reference.

#### Original plan (3.3)
Built on the same wave system as the caves, so do it after.
- [ ] **P1** Shortened wave list **(proposed)**: about 6-8 waves (bat, blob, melee, ranger, mager,
  Jad-class), then TzKal-Zuk with a simplified shield mechanic.
- [ ] Rewards: infernal cape.
- [ ] Tasks: complete, kill Zuk.

### 3.4 More bosses, including the echo bosses **(decided)**
You plan to add many more bosses than the two instances, and as many of the Raging Echoes echo
bosses as possible. **(decided)** No Wintertodt or Tempoross.

**Zulrah (built 2026-10-08, needs a real-client check):** the first of these bosses that is not an echo
pair. All four rotations, venom, clouds, snakelings, the wiki drop table and the existing Raging Echoes
Zulrah tasks. What was built, the numbers to tune and the client checklist are in
`docs/isle_of_souls_zulrah_plan.md`.

The nine echo bosses from the
[wiki](https://oldschool.runescape.wiki/w/Raging_Echoes_League/Echo_bosses_and_echo_equipment). Each
one is reached by carrying the echo orb dropped by its normal version, so the normal boss has to
exist first (build them as pairs):

| Echo boss | Normal boss needed first | Difficulty in Raging Echoes | Notes |
|-----------|--------------------------|-----------------------------|-------|
| Kalphite Queen (Echo) | Kalphite Queen | Elite | Two-phase fight |
| Cerberus (Echo) | Cerberus | Master | Needs the Cerberus lair |
| King Black Dragon (Echo) | King Black Dragon | Elite | Breath attacks |
| Grotesque Guardians (Echo) | Grotesque Guardians | Elite | Two bosses at once, rooftop |
| Dagannoth Kings (Echo) | Dagannoth Kings | Master | Three bosses, three styles |
| Thermonuclear smoke devil (Echo) | Thermonuclear smoke devil | Elite | Single boss, simple |
| Corrupted Hunllef (Echo) | Corrupted Gauntlet | Master | Needs the Gauntlet, large |
| Hespori (Echo) | Hespori | Elite | Needs farming |
| Sol Heredit (Echo) | Colosseum | Grandmaster | Needs the Colosseum, very large |

**What the cache already holds (read 2026-10-07 from the revision 233 cache, table
`leagues_echo_bosses`, one row per echo boss).** The echo orbs and echo items exist as objects
(`infernal_echoing_orb` to `solar_echoing_orb`, `echo_godsword`, `drygore_blowpipe`, and the rest).
The rates below are the cache's own numbers; I assume "1 in N" but have not confirmed the unit, so
check it against the wiki before using them.

| Echo boss | Orb (rate) | Unique drops (rate) | Requirement | Where |
|-----------|------------|---------------------|-------------|-------|
| Cerberus | infernal (50) | echo godsword (25) | 91 Slayer | Taverley Dungeon; no slayer task needed |
| Kalphite Queen | queenly (30) | drygore blowpipe (20) | none | Kalphite Lair |
| Dagannoth Kings | kingly (30) | amulet of kings, ring of kings (25 each) | none | Waterbirth Island Dungeon, sixth level |
| Thermonuclear smoke devil | explosive (50) | devil's element (25) | 93 Slayer | Smoke Devil Dungeon; no slayer task needed |
| Grotesque Guardians | stonebound (30) | damned gloves (25) | 75 Slayer | Slayer Tower roof; no task needed, does not respawn |
| Corrupted Hunllef | bestial (15) | crystal blessing (15) | Gauntlet completion | The Gauntlet; skips the preparation phase |
| King Black Dragon | draconic (30) | thunder khopesh, thousand-bone door (25 each) | none | Wilderness, west of the Lava Maze |
| Hespori | tangled (1) | tangled lizard (15) | 65 Farming (boostable) | Farming Guild; no fully grown hespori needed |
| Sol Heredit | solar (1) | weapon of sol (5), sunlight cuffs (7) | none | Final wave of the Colosseum, waves skipped |

Difficulty in the cache is 1 = Elite, 2 = Master, 3 = Grandmaster, which matches the table above.
Each row also holds a region id and a combat-achievement id (not mapped yet).

**What the cache does not hold.** No combat stats, attack patterns or mechanics (those come from
the wiki), and no echo npc types: this cache's highest npc id is 14792, and the wiki's echo npc
ids start at 15,000. Only parts exist (`black_dragon_echokbd`, `dagannoth_water_creature_echo`,
`crystal_hunllef_crystals_echo_crystals`). The echo attack effects do exist as spotanims (for
example `echo_thermy_proj`, `echo_kalphite_queen_spine`, `hespori_magic_proj_echo`,
`dragon_ranged_echo_attack`, the `vfx_colosseum_*_echo` set), so echo fights can reuse them.

**Recoloured models (feasible, not tried yet).** The echo version of a boss is the normal model
with different colours (the King Black Dragon goes from black and red to dark blue with pink
feet and cyan eyes). Colours live in the npc type, as `recol_s`/`recol_d` pairs (source colour in
the model, replacement colour), and the server controls them:
1. Add a new npc type for each echo boss, copying the normal npc and setting the recolour pairs.
   `NpcPluginBuilder` already exposes `recol1s`, `recol1d` and so on, `NpcTypeEncoder` writes them,
   and the cache packer puts new types into the game cache that JS5 serves to the client. Players
   download the new definitions on their next login.
2. Colours are 16-bit values, and the source values have to match colours in the boss model. They
   have to be read from the model data (or copied from an existing echo definition) once per boss.
   The cache's own `black_dragon_echokbd` already seems to carry six recolour pairs, which shows
   the approach is the one Jagex used.
3. Limits: recolouring is per npc type, not per instance, which is fine here because the echo is
   its own type. It cannot add geometry, so the pink disc under the echo King Black Dragon in the
   wiki picture is probably a separate effect (a spotanim or ground model) and needs checking.
4. Retexturing (`retex_s`/`retex_d`) is also supported if a boss uses textures instead of flat
   colours.
- [ ] **P1** Make the first recoloured echo (King Black Dragon is the simplest) to prove the
  method in the real client before doing the other eight.

Suggested order, easiest first: Thermonuclear smoke devil, King Black Dragon, Cerberus, Kalphite
Queen, Dagannoth Kings, Grotesque Guardians, then Hespori, Corrupted Gauntlet, Colosseum. The last
three are each a whole minigame.

**Reference (section 11):** tsps has plugins for King Black Dragon, Kalphite Queen, the Gauntlet
(with Hunllef) and the Colosseum (with Sol Heredit), each with a wiki-sourced doc that marks which
values are guesses. It has nothing for Cerberus, Dagannoth Kings, Grotesque Guardians or the
Thermonuclear smoke devil, so those four come from the wiki alone. Read `gauntlet.md` and
`fortis-colosseum.md` before committing to those two, to see what a whole minigame costs.

- [ ] **P1** A shared **boss framework** built once: arena (overworld or instance), phases and
  attack patterns, two-player targeting, drop-table hookup, kill-count tasks, death handling.
  The Fight Caves are the first user of it, so build the framework there.
- [ ] **P1** Echo orb and echo equipment drops, per the wiki page; echo gear you already use
  (pickaxe, axe, harpoon) is separate.
- [ ] **P1** Boss-kill tasks for every boss added (the Raging Echoes list has many of them).
- [ ] Each boss: decide overworld or team instance, and whether it must work for two players.

### 3.5 Clue scrolls and caskets **(decided: no treasure trails)**
Clue scrolls drop like any other item, but reading one does not start a trail: it gives the casket
straight away. This is how it works for every player; there are no steps, emotes or STASH units.
- [ ] **P1** Scroll items for the tiers (beginner, easy, medium, hard, elite, master) and a
  "read" action that swaps the scroll for the matching casket.
- [ ] **P1** Casket opening with the real game's reward tables and roll counts **(decided: exactly
  as in the real game)**, taken from the wiki for each tier. Items that do not exist in this
  server's cache are **(decided)** skipped, and each one is noted in
  `docs/isle_of_souls_missing_items.md` (same rule for drop tables).
- [ ] **P1** Treasure Arbiter's maximum rolls (per the relic doc: 3 / 4 / 5 / 6 / 6 / 7 for
  beginner / easy / medium / hard / elite / master) replace the random roll count.
- [ ] **P1** Sources: monster drop tables (tier by monster level), bosses, and skilling finds (the
  task list already has "Obtain a Bird Nest" and "Obtain a Clue Geode While Mining"; fishing
  bottles and implings if hunter is built).
- [ ] **P1** **Treasure Arbiter** (tier 5): clue-dropping creatures and skilling finds drop clues
  more often (wiki: 1/15 for clue-dropping creatures and impling jars, 10x for geodes, nests and
  bottles), and caskets always roll the maximum number of times, the same item allowed to roll
  more than once. The wiki's other effects (shortest clues, no emote items) are not needed.
- [ ] Rare casket rewards announced through `LeagueAnnouncer.drop`.
- [ ] Tasks: open caskets of each tier, find geodes, nests and bottles. This is a good source of
  tasks the isle can really support.

### 3.6 Combat masteries **(decided: all accessible, 2 points per task)**
Source: the wiki's [Combat Masteries](https://oldschool.runescape.wiki/w/Raging_Echoes_League/Combat_Masteries)
page. Three styles (melee, ranged, magic) with six tiers each, so **18 masteries**, plus **6
universal passives**. Each tier needs the previous tier of the same style. The wording below is the
wiki's, and the effects are built to match it.

**Points.** The real game has 10 mastery tasks worth 1 point each. **(decided)** each one is worth
**2 points here, so 20 points in total**. **(decided)** each mastery costs **1 point** (the wiki
does not state the cost). 20 points cover all 18 masteries with 2 spare, which is what makes "all
accessible" true. Because everything ends up unlocked, the choice is only the order, so the order of
the tasks is what shapes the event (see the build notes). **(decided)** points are **per player**:
a mastery task works like any other team task, so when the team completes it, both players get the
task and their own 2 points, and each spends them in their own order. **(decided)** These are mastery
points and are separate from league points: the mastery tasks add nothing to the league total.

**The 10 mastery tasks.** Taken from the wiki, with what each one needs on the isle:

| # | Task | Needs |
|---|------|-------|
| 1 | Defeat a Giant | A giant on the monster list (hill, moss or fire giant) |
| 2 | Defeat 10 monsters with combat level 100+ | Mid-to-high level monsters (4.1) |
| 3 | Defeat Scurrius | Scurrius. **(decided)** it does not need to be soloed: a team kill completes it for both players |
| 4 | Defeat a monster with a 55+ Slayer requirement | Slayer-only monsters on the list (for example a Turoth) |
| 5 | Defeat TzTok-Jad in the Fight Caves | Built (3.2) |
| 6 | Reach combat level 100 | Nothing, only combat XP |
| 7 | Defeat one echo boss | Echo bosses (3.4) |
| 8 | Defeat two unique echo bosses | Same |
| 9 | Defeat three unique echo bosses | Same |
| 10 | Defeat TzKal-Zuk | The Inferno (3.3) |

Tasks 1, 2, 4 and 6 come with the combat monster ladder. Task 5 is available as soon as the caves
are tried in a real client. Tasks 3 and 7-10 wait for their bosses, so the last points arrive late.
With 2 points per task, tiers 1 and 2 of all three styles (6 masteries) need only 6 points, which is
three tasks; all 18 need nine tasks.

**Melee**

| Tier | Effect |
|------|--------|
| I | Melee hits have a 25% chance to roll damage twice and take the highest result |
| II | Melee hits have a 10% chance to generate an echo hit (additional melee hit, 50% max hit, respects accuracy, PvM only) |
| III | Melee attack rate set to 80%, rounding down |
| IV | Melee hits have a 5% chance to heal 40% of damage dealt |
| V | Melee attack rate set to 50%, rounded down for 5t and above, rounded up below 4t |
| VI | Chance to generate a melee echo increases to 20%, and echoes can generate additional echoes (up to 8 times in a row) |

**Ranged**

| Tier | Effect |
|------|--------|
| I | Damage rolls beneath 30% of max hit with ranged are increased to 30% |
| II | Each subsequent ranged attack has its max hit increased by an additional 5%; resets after +20% |
| III | Ranged attack rate set to 80%, rounding down |
| IV | Every 5th ranged hit, heal 5 hitpoints |
| V | Ranged attack rate set to 50%, rounded down for 5t and above, rounded up below 4t |
| VI | Never miss with ranged (PvM only) |

**Magic**

| Tier | Effect |
|------|--------|
| I | When you roll above 90% of your max hit with magic, damage is increased by 50% |
| II | Magic max hit is increased by 5% per tick in between your attacks (up to +40%) |
| III | Magic attack rate set to 80%, rounding down |
| IV | When you roll above 90% of your max hit with magic, heal 10% of damage dealt |
| V | Magic attack rate set to 50%, rounded down for 5t and above, rounded up below 4t |
| VI | Max hit with magic is increased by 1% for every 100 hitpoints remaining on the target (up to 10%); on a successful magic hit, if the target has fewer hitpoints than your max hit, you max hit |

**Passives (all styles).** The first time a player reaches a tier in any style, that tier's passive
turns on for every style:

| Tier | Passive |
|------|---------|
| 1 | 95% chance to save weapon charges, ammunition and runes used for spells |
| 2 | Healing from all sources is increased by 20% |
| 3 | Accuracy with all styles is increased by 100% |
| 4 | Damage taken is reduced by 15% |
| 5 | Prayer point gain from all sources is increased by 25% |
| 6 | Attacks with all styles now have 60% prayer penetration |

**Build notes** (what each piece needs from the engine):
- [x] **P1 Mastery state and points.** *(built 2026-10-07, `masteries/` package)* Per-player mastery unlocks (varps or a save field) and a
  point balance, with a service that grants 2 points when a mastery task is completed, checks the
  previous tier, and spends 1 point per unlock. **(decided)** the points and unlocks are
  per player: a team-completed mastery task grants the 2 points to both partners, who each pick
  their own order.
- [x] **P1 The masteries screen.** *(built 2026-10-07; decoded contract in the memory note `reference-combat-mastery-screen`; the click-zone order and the redraw after an unlock still need a check in a real client)* `league_combat_mastery` exists in the cache and its side-panel
  button is wired. Its components, varps and the unlock click have to be decoded from the client
  cache first, the way the relics and tasks screens were; this is the main unknown.
- [x] **P1 Damage roll hooks** *(built 2026-10-07: melee I, ranged I, II, VI, magic I, II, VI and the
  accuracy passive; the magic IV heal waits for the heals step)*. These need a mastery step inside the max hit and damage roll in `PlayerAttackManager` and
  `SpellAttackManager`. The tier VI "max hit" rule and the 90% threshold need the rolled value and
  the max hit available at the same point.
- [x] **P1 Attack rate** (III and V in each style) *(built 2026-10-07 in `WeaponSpeeds` and `CombatMasteryEffects.attackDelay`; the old player speed halving was removed first)*. A multiplier on the weapon's attack speed:
  80% of 5 ticks rounds down to 4; for 50%, 5 ticks and above round down, below 4 ticks round up
  (so 4 ticks gives 2 and 3 ticks gives 2). Never below 1 tick. The wiki's sentence is short, so
  I read it this way; check it against your own copy of the description.
- [x] **P1 Echo hits** (melee II and VI) *(built 2026-10-07: `PlayerAttackManager.queueMeleeEchoes`, normal melee attacks only)*. A second, delayed hit at 50% of max hit that rolls
  accuracy, and chains up to 8 times at tier VI. Needs a cap on hits per attack so a long chain
  cannot lag a tick.
- [x] **P1 Healing and sustain hooks** *(built 2026-10-07; Redemption is not raised, its code cannot be injected)* (melee IV, ranged IV, magic IV, passive 2). Passive 2 raises
  every heal (food, potions, prayer, regeneration), so it has to sit where heals are applied;
  check whether eating food (`content/other/food`) and the other heals share one place to hook.
- [x] **P1 Passives 1, 3 and 4.** *(built 2026-10-07)* 95% saving of charges, ammunition and runes (a roll where ammo,
  charges and runes are consumed; Ava's-style retrieval is separate), accuracy doubled in the
  attack roll, and a 15% damage reduction applied to damage taken. Passive 1 also saves the
  weapon charges, ammunition and spell runes, so it touches the magic and ranged code.
- [x] **P2 Passives 5 and 6.** *(built 2026-10-07; 6 is a helper nothing reads yet, 5 covers potions, not altars)* Prayer gain +25% (altars, potions) and 60% prayer penetration. The
  penetration only matters against targets that use protection prayers, which none of our bosses
  do yet; it is built so it works if one does, and it is otherwise inert.
- [ ] **P1 Tests.** One integration test per effect with an injected random source so the 25%, 10%
  and 5% chances can be forced, plus the rounding table for the attack rate.
- [~] **P1 Tasks.** *(2026-10-07: tasks 1-6 are wired, 7-10 wait for echo bosses and the Inferno; needs `./gradlew packCache` once for the new varp and the Scurrius text.)* Wire each of the 10 tasks above to its content as that content lands: the first
  four and task 6 with the combat ladder, task 5 with the caves, tasks 3 and 7-10 with their bosses.

**Interactions to keep in mind.** Everything here is player-versus-monster, so the "PvM only"
conditions always hold and no PvP check is needed. The effects stack with the relics: the
**Specialist**, **Last Stand** and **Guardian** relics and the tier 3 combat XP passive all change
combat, and tier VI echoes plus 50% attack rate plus doubled accuracy will make the Fight Caves
and Inferno much easier than the wiki design. Do the boss balancing (3.2, 3.3 and 3.4) **with all
masteries on**, since all of them will be unlocked in the real event.

---

## 4. NPCs and shops

### 4.1 Required before anything else
**Reference (section 11):** pick the monster ladder from osrsreboxed-db `monsters-complete.json`
(hitpoints, max hit, defences, attack speed, Slayer level), and the shop stock from its
`shops-by-npc.json` and `shops-items-by-shop.json`.

- [ ] **P0 Combat NPCs on the isle**: a ladder of low, mid and high level monsters so every
  combat skill can be trained, with drops and kill tasks. They also supply slayer, bones and
  hides.
- [ ] **P0 Shops**: general store plus skill-supply shops (no Grand Exchange). Decide stock,
  prices and the currency (thieving and alchemy feed it).
- [ ] **P0 Banks** across the isle (team bank done).
- [ ] **P1 Slayer master** (needed as soon as slayer exists).
- [ ] **P1 Guide NPC or sign** explaining the league and teams.

### 4.2 Existing
Bankers, cows, sheep, ducks and generic people exist in `content/generic/generic-npcs`.

### 4.3 Later
- [ ] **P2** Tool leprechaun, Sage (Guardian horn retrieval), event host NPC that shows the
  leaderboard.

---

## 5. Areas, teleports and the world

- [ ] **P0** Run `./gradlew packCache` and check the island area, Home Teleport text and cache
  edits show up in the client.
- [ ] **P0** Walk the whole isle in the client: collision, banks, trees/rocks/fishing spots,
  altars (prayer, runecraft), agility course, farming patches, build sites, boss entrances.
- [ ] **P1** Teleport destination list for the tier 3 relics: a list of named places on the isle
  (banks, mines, forest, bay, boss entrances). No discovery or unlocking.
- [ ] **P1** Area unlocks: **(decided) none** (section 5.1).
- [ ] **P1** Masteries screen: now part of the combat masteries work (section 3.6).
- [ ] **P2** Summary screen (Rank tier progress already works from points).

### 5.1 Areas **(decided: no unlocks)**
The whole isle is open from the start and nothing is gated. The Leagues Areas button still opens
the client's screen, which shows the real game's regions with no state. **(decided)** try hiding
that button, and if it is too much work leave it as it is. Teleport relics list named places
on the isle (above) and nothing about them needs unlocking. Tasks that mention a place elsewhere
in the game ("in Tirannwn") are removed or reworded instead of mapped to areas.

---

## 6. Tasks, points and thresholds

### 6.1 Task list
**(decided)** The list grows as content is added: every wiki task the isle can support goes back in.
`docs/raging_echoes_tasks.json` has 1,589 to filter from. The final list must be able to reach the
last relic tier; if it cannot, add more tasks or lower the thresholds.
- [ ] **P0** Finalize the existing 139 (see 1.1), including removing the Tempoross tasks.
- [x] **2026-10-07** Added 54 tasks: the level tasks (first level up, first level N, total level, base
  level, combat level; `LeagueLevelTasks`) and 11 that current content completes (Leagues menu, Home
  Teleport, fire cape, Jad hit without prayer, metal weapons and sets; `LeagueGearTasks`). The list
  is now 278 tasks worth 23,030 points. Base level, total level 1000+ and combat level 25+ cannot be
  completed until the missing skills and combat training exist; they need no change then.
  "Complete the Leagues Tutorial" is deliberately not a task (the tutorial is pre-completed).
- [ ] **P0** Whenever a skill, NPC, boss or clue feature is finished, add its tasks in the same
  step so the list never falls behind the content. Keep a running points total.
- [ ] **P1** Balance so no skill dominates the points.

### 6.2 Thresholds **(P0)**
Currently 0 / 750 / 1,500 / 2,500 / 5,000 / 8,000 / 16,000 / 25,000 (wiki values) with 10,630
points available. Set these **last**, from the finished task list and the final XP rates:
1. Decide the pace: for example tier 8 around hour 6-7 of an 8-10 hour event for a strong team,
   since the best players should be able to max.
2. Estimate the points a strong team earns per hour from the final list.
3. Set thresholds from that, and make sure total points available comfortably exceed the top
   threshold, so a team can keep climbing the leaderboard to the end.

---

## 7. Event operations

Already built; the work is rehearsing it. **8-10 hours** makes the crash plan more important.

- [ ] **P0 Dress rehearsal** with at least four real clients: login, team sync, shared bank,
  shared loot, task completion for both players, freeze/unfreeze, scoreboard, announcements.
- [ ] **P0** Reset checklist from `isle_of_souls_teams.md` ("Before the event").
- [ ] **P0** Hide dev commands from players (`::leagues`, `::leaguetask`, `::leaguereset`).
- [ ] **P0 (new, long event)** Soak test: server up for 10 hours with several clients, watching
  memory, save files and the team-bank saves.
- [ ] **P1** Backups: where saves and `.data/league` are copied, and a restart plan if the server
  dies mid-event (instances do not survive a restart).
- [ ] **P1** Time limit and winner: manual `::leaguefreeze` at the end, then announcement.
- [ ] **P2** One-page connection guide for friends (RSProx, revision 233, Blurite jav_config).

---

## 8. Dependencies at a glance

```
packCache + real-client check ──┬─> combat NPCs ──> drop tables ──> bosses (Caves, then Inferno)
                                │         └──────> slayer
                                └─> instance test ─────────────────> bosses
fletching, runecraft, crafting, herblore ──> ranged, magic and potions work ──> bosses are beatable
thieving, alchemy ──> shop money ──> shops
drop tables ──> clue scrolls ──> caskets ──> Treasure Arbiter
combat core ──> mastery effects ──> mastery tasks (Jad, echo bosses, Zuk arrive with their bosses)
each skill ──> its relic(s) + its tasks ──> thresholds ──> rehearsal
```

---

## 9. Suggested build order

1. **Foundation**: `packCache`, walk the isle, run teams and the instance test in a real client.
2. **Combat core**: combat NPCs, drop tables, two-player targeting, shops, banks.
3. **Quick skills**: fletching, runecraft, crafting, herblore, prayer, magic extras. Clue scrolls
   and caskets can go in right after drop tables, since they only need the drop system.
4. **Fight Caves** (shortened), then **Inferno** (shortened) on the same wave system. **Combat
   masteries** (3.6) go in alongside, once the combat ladder exists, so boss fights are balanced
   with them on.
5. **Thieving, agility, slayer** with their relics.
6. **Hunter, farming, construction** with their relics. These are the hardest to design and each
   unlocks only one relic, so they go last and get a design discussion first.
7. **Tasks and thresholds** finalised alongside steps 4-6, then **rehearsal and reset**.

---

## 10. Open questions

Still open (answered on 2026-10-06 unless noted):

1. **Monster list:** *open.* You will provide it later; drop tables and the combat ladder wait for
   it.
2. **Boss order:** *open.* You will decide later whether the easiest-first order in 3.4 is right,
   and which bosses are overworld versus team instances.
3. **Missing items:** **(decided)** if a real casket reward or drop is not in this server's cache,
   skip it, and keep a note of each skipped item in `docs/isle_of_souls_missing_items.md`
   (created when the first one is skipped) so they can be revisited.
4. **Construction and farming:** *open.* The build-site and fast-patch ideas are only a starting
   point; we design them together when we reach them.
5. **Areas button:** **(decided)** try hiding it; if that turns out to be too much work, leave the
   empty screen as it is.
6. **Scurrius (mastery task 3):** **(decided)** does not need to be soloed; a team kill completes
   it for both players, like any other task.
7. **Mastery points:** **(decided)** per player.
8. **Mastery cost:** **(decided)** 1 point per mastery.
9. **Echo-boss mastery tasks (7-9):** *open.* Revisited when we look into echo bosses.

---

## 11. Outside reference sources

Checked 2026-10-08, while building Zulrah. Both are GitHub repos under the `RSPSApp` account.
Nothing was cloned into this project; their files were read through the public API. Neither is a
dependency. They are material to read, check against, and take facts from.

### 11.1 `RSPSApp/tsps` (BSD-2-Clause)

A TypeScript OSRS server and client, about 2,860 files, 2,185 commits, active. Its code is
JavaScript for a different engine, so it is reference only and cannot be ported. The value is in
the `docs/` pages and the `server/data/definitions/` files, which say where each number came from
(wiki, live capture, or "Near-Reality") and what is still unverified. Zulrah already uses it:
`docs/zulrah.md` and `server/plugins/bosses/zulrah/` supplied the cloud and orb tiles, the four
Zulrah spawn tiles and the cloud numbers (see `isle_of_souls_zulrah_plan.md`).

**What it is useful for, by item in this plan:**

| Plan item | tsps material | What to take |
|-----------|---------------|--------------|
| Thieving (1.2) | `docs/thieving.md`, `data/definitions/pickpocketing.json` | Every pickpocket target: level, XP, success out of 256, stun damage, coin pouch size, loot. Fastest way to build the shop-money source. |
| Hunter (1.2) | `server/plugins/skills/hunter/README.md`, 27 files | Trap families, falconry, butterflies, impling loot tables, birdhouses, herbiboar, chinchompa catch curves. The README lists where it approximates. |
| Agility (1.2) | `docs/agility-courses.md`, `docs/agility-shortcuts.md`, `agility-shortcuts.json` | Course and shortcut levels and XP. |
| Farming (1.2) | `server/plugins/skills/Farming.md`, `farming-data.json` | 82 crops and patch tables. Our plan keeps herb and food patches only, so most of it is surplus. The doc mentions a Hespori encounter service, which is unread. |
| Construction (1.2) | `docs/construction-features.md` | Restore-pool tiers and what each restores, for the build-site idea. It assumes a full player-owned house. |
| Slayer (1.2) | `server/plugins/skills/Slayer.plugin.js`, `data/definitions/slayer-tasks.json`, `docs/slayer-tower.md` | Task assignment and masters. Overlaps the osrsreboxed-db file below. |
| King Black Dragon and Kalphite Queen echoes (3.4) | `server/plugins/bosses/KingBlackDragon.plugin.js`, `KalphiteQueen.plugin.js` | Attack ids, timings and the two-phase structure of the normal fights. |
| Corrupted Hunllef echo (3.4) | `docs/gauntlet.md`, 14 files in `server/plugins/minigames/gauntlet/` | Hunllef mechanics, max hits by crystal tier, weapons, resources, rewards. |
| Sol Heredit echo (3.4) | `docs/fortis-colosseum.md`, 14 files in `server/plugins/minigames/colosseum/` | Wave 12 phases at 90/75/50/25/10% HP, attacks, modifiers, Glory, loot. |
| More bosses (3.4) | `Scurrius`, `GiantMole`, `CorporealBeast` plugins, `docs/corporeal-beast.md` | Pattern references. |
| Fight Caves and Inferno (3.2, 3.3) | `server/plugins/minigames/TzhaarFightCaves.plugin.js`, `minigames/inferno/` | A second implementation to cross-check ours against. |
| Combat checks (3.1) | `npc-animations.json`, `npc-combat-defs.json`, `item-combat-styles.json`, `docs/weapon-data.md` | Per-NPC animation ids (BSD, from RuneLite), weapon styles and speeds. |
| Shops (4.1) | `shops.json`, `custom-shops.json` | Stock lists. |
| Drop tables (3.5, 4.1) | `npc-drop-overrides.json`, `docs/npc-drops.md` | Corrections to the raw wiki data, for example Zulrah's two rolls per kill. |
| Serpentine helm, blowpipe (Zulrah gaps) | Not covered | Write from the wiki. |

**Not in tsps:** Cerberus, Dagannoth Kings, Grotesque Guardians and the Thermonuclear smoke devil.
Wintertodt and Tombs of Amascut exist there but are out of scope for this event. Construction
and farming are far bigger than our plan needs.

### 11.2 `RSPSApp/osrsreboxed-db` (code GPL-3.0, wiki data CC BY-NC-SA 3.0)

A fork of DayV-git's fork of osrsbox-db, JSON data only, current as of September 2026. The
`docs/` folder is about 150 MB. These are lookups and cross-checks, not gameplay logic.

| File | Holds | Use in this plan |
|------|-------|------------------|
| `monsters-complete.json` | 3,883 monsters, about 44 fields: hitpoints, max hit, attack type and speed, five defence values, ranged defence split by ammo weight, elemental weakness, venom flags, Slayer data | Choose the combat ladder and set stats (4.1, section 3.1); cross-check `docs/isle_of_souls_npc_combat.md`. |
| `drops-json/npc-drops.json` | 3,092 NPCs, 1,112 tables: weights, quantity ranges, sections, `separate_roll`, quest-gated notes; shared tables in `subtables.json` | A cleaner second source beside `tools/drops/wiki_import.py` (`isle_of_souls_drops.md`); cross-check or fill NPCs the importer has not run on. |
| `slayer-tasks.json` | 16 masters: quantity, weights, Slayer and combat levels, the monsters each task covers | Build Slayer (1.2). |
| `shops-by-npc.json`, `shops-items-by-shop.json` | 619 shop NPCs and their stock | Starting stock for the isle's shops (4.1). |
| `items-complete.json` and per-item JSON | 16,755 items with equipment bonuses and requirements | Check gear tiers against the cache. |
| `prayers-complete.json` | 29 prayers: drain and bonuses | Check only; prayer is built. |
| `npcs-interactions.json`, `npc-dialogues*.json` | Click options and a very large dialogue dump | Not needed. |

### 11.3 Cautions

- **Treat unverified values as guesses.** Many tsps tile, timing and ratio values come from
  Near-Reality, a leaked server dump with no license that tsps itself labels "behavior oracle
  only". Facts such as tiles and animation ids are fine to read; do not copy its code or tables.
  Prefer values marked wiki or captured. Everything taken from tsps stays "unverified against the
  real game" until the real-client checks in section 9.
- **Cache revision.** tsps mixes revisions and says live OSRS may be newer than its cache. Ours is
  revision 233. Check every id against our cache, as was done for Zulrah.
- **Data quirks.** In osrsreboxed-db, more than half of the monster entries leave some stat fields
  null or zero, Zulrah's entries are marked `incomplete`, and the red form lists max hit 30 where
  the other forms say 41. Use the wiki to settle conflicts.
- **Licenses.** Keep attribution for the wiki-derived data where we use it, as
  `isle_of_souls_drops.md` already does.

### 11.4 Other repos checked and not used

- `RSPSApp/elvarg-rsps` (317 server): deprecated, no license, no modern bosses, no Zulrah.
- `AlterRSPS/Alter` (BSD-2): a framework fork of the older Tomm0017 RSMod, revision 228; only
  King Black Dragon, Barrows and thieving as content, and a different plugin system.
- `rsps-releases/osrs/kronos-osrs-184` and `os-scape` (GitLab, no license, apparently released
  commercial servers): Kronos has Vorkath, Hydra, Cerberus, Kraken and Chambers of Xeric scripts,
  but its Zulrah is marked "a mess" and its combat data is tuned for a custom server. Not
  copied. If a boss from there is built, read it only for facts and check them against the wiki.
