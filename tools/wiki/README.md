# Wiki generators

`gen_skills.py` writes `wiki/data/skills-generated.js`, the generated half of the wiki's skill pages:

```bash
python tools/wiki/gen_skills.py
```

Re-run it after changing a skill's Kotlin tables, the league task list, a dungeon entrance or
`.data/league/event-settings.json`. It takes a few seconds.

What it produces for each skill (all of them except Farming and Construction, which are not built):

- **Tables** parsed from the skill module's Kotlin config (levels, XP, loot), with item and npc names
  read from the vanilla cache.
- **Places**: every rock, stall or pickpocket target that a player can walk to, grouped into clusters.
  `reach.py` decides what is walkable: a flood fill from the isle and from every dungeon's arrival
  tile (same model and limits as `tools/slayer/coverage.py`). Npcs come from the world spawn files.
- **League tasks** for the skill (skill from `docs/raging_echoes_tasks.json`), with a `live` flag: the
  task id appears in a file that uses the task tracker. That is a heuristic, so a task can be marked
  live by a stray number.

**Crafting and Herblore** build their recipe tables in Kotlin code that is hard to read as text, so
they are dumped by a unit test in each module instead, into `tools/wiki/dumps/`. Refresh the dumps
after changing either skill, then run the generator:

```bash
WIKI_DUMP=tools/wiki/dumps ./gradlew :content:skills:crafting:test :content:skills:herblore:test --tests "*WikiDumpTest*"
```

The test does nothing in a normal test run (no `WIKI_DUMP` set).

Page text can point at a landmark with `{dir:<key>}` (the generator's `entrances`: dungeon keys,
starter objects like `gerrant` or `furnace`, slayer masters, `grace`, `arena_hut`, `tower`), which
becomes "about 60 tiles south of the lobby" worked out from the code.

To add a skill: write a `<skill>(reach, all_tasks, ...)` function returning `tables`, `places` and
`tasks`, add it to `main()`, then write the hand half in `wiki/data/skills.js` and set `page: true`.

**Skill icons**: `gen_skill_icons.py` writes the Skills-tab icons (sprite groups 197 to 221) to
`wiki/assets/skills/<skill>.png`. The pages pick them by skill id; a page covering several skills
lists them in `icons: [...]` in `skills.js` (Melee). Only needed again if the cache changes.

```bash
python tools/wiki/gen_skill_icons.py
```

## Relics

`gen_relics.py` writes `wiki/data/relics-generated.js` and the relic icons in `wiki/assets/relics/`:

```bash
python tools/wiki/gen_relics.py
```

Re-run it after changing `relicPointThresholds`, `xpMultiplier` or `dropMultiplier` in
`.data/league/event-settings.json` (a key left out falls back to the default in `EventSettings.kt`),
the relic layout in `RelicData.kt`, or the task list. It reads each relic's icon from its cache struct
(param 884, a 120 x 120 sprite) through `tools/cs2/sprites.py`, which writes PNGs with no image
library. What each relic does is hand-written in `wiki/data/relics.js`.

## Bosses

`gen_bosses.py` writes `wiki/data/bosses-generated.js`, the generated half of the boss pages:

```bash
python tools/wiki/gen_bosses.py
```

Re-run it after changing a boss, its drops, its entrance, its tasks or the event settings it reads
(`toa`, `tob`, `cox`, the cave modes, PvP). It gives each fight its stats (from the cache, or from the
code where the code sets them: ToB hitpoints, the Chambers of Xeric base figures in `CoxScaling.kt`
and the Great Olm's hitpoints by party size), drop tables with league rates, the collection log
category, where the entrance is and the league tasks. A new page needs an entry in `FIGHTS` and
`PAGES` here and its hand-written half in `wiki/data/bosses.js`.

## Pets

`gen_pets.py` writes `wiki/data/pets-generated.js` from the game's pet table
(`tools/pets/gen_pets.py`):

```bash
python -I tools/wiki/gen_pets.py
```

Where each pet comes from on the isle is hand-written in its `SOURCES`; add a pet there when the
content that gives it is built.
