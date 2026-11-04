# Wiki generators

`gen_skills.py` writes `wiki/data/skills-generated.js`, the generated half of the wiki's skill pages:

```bash
python tools/wiki/gen_skills.py
```

Re-run it after changing a skill's Kotlin tables, the league task list, a dungeon entrance or
`.data/league/event-settings.json`. It takes a few seconds.

What it produces for each skill it knows (Mining and Thieving so far):

- **Tables** parsed from the skill module's Kotlin config (levels, XP, loot), with item and npc names
  read from the vanilla cache.
- **Places**: every rock, stall or pickpocket target that a player can walk to, grouped into clusters.
  `reach.py` decides what is walkable: a flood fill from the isle and from every dungeon's arrival
  tile (same model and limits as `tools/slayer/coverage.py`). Npcs come from the world spawn files.
- **League tasks** for the skill (skill from `docs/raging_echoes_tasks.json`), with a `live` flag: the
  task id appears in a file that uses the task tracker. That is a heuristic, so a task can be marked
  live by a stray number.

To add a skill: write a `<skill>(reach, all_tasks, ...)` function returning `tables`, `places` and
`tasks`, add it to `main()`, then write the hand half in `wiki/data/skills.js` and set `page: true`.
