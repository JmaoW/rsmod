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
