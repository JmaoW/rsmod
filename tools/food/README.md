# Food, drink and potion data tools

These scripts build the data for `content/other/food`, `content/skills/cooking` and the minigame
potions in `content/skills/herblore`. The generated Kotlin files say which script wrote them; edit
the script or its input and run it again rather than editing the Kotlin.

Python 3, no packages. Scripts that read or write the big working files take their folder from the
`FOOD_TOOL_OUT` environment variable (the default is this folder). Keep that folder outside the
repo: `objs.json` alone is 5 MB.

| Script | Reads | Writes |
| --- | --- | --- |
| `objdump.py` | the vanilla cache (`.data/cache/vanilla`) | `edibles.json`, `objs.json` |
| `gen_food.py` | `tsps_food.json` | `FoodData.kt` (every food: heals, bites, delays, effects) |
| `gen_potion_refs.py` | `objs.json` | `MinigamePotionObjs.kt` (dose chains of the minigame potions and mixes) |
| `wiki_cooking.py` | the OSRS wiki (Recipe and success chart templates) | `wiki_cooking.json`, `wiki_pages/` |
| `gen_cooking.py` | `wiki_cooking.json`, `objs.json` | `CookingExtras.kt` (the food that is cooked) |
| `check_food.py` | `objs.json`, the Kotlin sources | prints names the cache does not have |

Order for a fresh start: `objdump.py`, `gen_food.py`, `gen_potion_refs.py`, `wiki_cooking.py`,
`gen_cooking.py`, `check_food.py`.

The wiki needs a descriptive `User-Agent`, which `wiki_cooking.py` sets; plain `curl` is blocked.

## Sources

- `tsps_food.json` and the potion effects in `MinigamePotions.kt` come from
  [RSPSApp/tsps](https://github.com/RSPSApp/tsps) (`server/plugins/items/data/food.json` and
  `Potions.plugin.js`), which is BSD 2-Clause licensed (Copyright (c) 2022-2026, dennisdev, xrsps; the licence is in
  `TSPS_LICENSE`). Its food
  table was written from the OSRS Wiki and recordings of the real game. `tsps_food.md` is its
  documentation of the format.
- Cooking levels, experience and success charts come from the OSRS Wiki item pages.
- Which items exist, and what the cache calls them, comes from the cache itself.
