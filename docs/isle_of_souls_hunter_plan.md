# Isle of Souls: Hunter plan and status

Scoped with the user and built 2026-10-09. The numbers come from the tsps hunter plugin
(`server/plugins/skills/hunter/` in `RSPSApp/tsps`, BSD-2, see the content plan section 11.1:
`Data.Hunter.js` for the 27 trap creatures, the butterflies, implings, falconry kebbits and the
birdhouse tiers, `ImplingLoot.json` and `LuckyLoot.json` for the loot), which took them from the OSRS
Wiki. tsps marks a few values as estimates (the tatty fur curve, the birdhouse clue-nest rolls); those
stay guesses here. tsps targets revision 237 and this cache is revision 233, so every item, npc and loc
was matched against `.data/symbols` and loot rows this cache does not have were dropped (two impling
rows: dragon and rune javelin tips; one lucky row).

## What is built (module `content/skills/hunter`)

| Piece | Where | What it does |
| --- | --- | --- |
| Floor traps | `scripts/HunterTraps.kt` | Bird snare, box trap and magic box are laid with their item ("Lay", "Activate") where the hunter stands. Level 1, 27 and 71 to lay; limit `min(5, 1 + level / 20)` traps |
| Deadfall | same | Set on the boulders left lying about ("Set-trap", knife and logs, level 23, at most two) |
| Net traps | same | Set on young trees (rope and small fishing net; swamp 29, orange 47, red 59, black 67, tecu 79). A net lies beside the tree |
| The catch | same | Every trap looks for a creature of its kind within 2 (bird, magic) or 3 tiles every 5 ticks, walks it over, and at the trap rolls the Wiki chart (`Creature.chance`). The catch stays in the trap until it is checked (that is when the experience comes). A trap collapses 100 ticks after it last changed |
| Bait and scent | same | Matching bait is worth +3/256 (tar, 5 of it, for net traps), a lit torch +2/256 |
| Pitfalls | `scripts/Pitfalls.kt` | Pit N is one of 25 per-player multilocs (varbit `hunt_pitfall_stateN`): 1-6 kyatt, 7-11 larupia, 12-16 graahk, 17-21 sunlight antelope, 22-25 moonlight antelope. "Trap" (knife, logs), "Tease" a cat with a teasing stick or hunter's spear so it follows, lead it to the pit, "Jump" across; it falls in or leaps over on its chart. A teased cat claws (max 5, 7 for kyatt and graahk, softened by the matching big cat clothing) |
| Butterflies, moths, implings | `scripts/HunterCatching.kt` | "Catch" with a butterfly net (level) or barehanded (higher level); into an empty jar when one is carried. Release a butterfly for its boost; "Loot" an impling jar (1 in 10 breaks). A barehanded impling is looted at once. Loot tables in `src/main/resources/.../impling_loot.tsv` and `lucky_loot.tsv` |
| Falconry | `scripts/Falconer.kt` | With a falconer's glove, "Catch" a spotted (43), dark (57) or dashing (69) kebbit: bones and fur. Simplified, see below |
| Birdhouses | `scripts/Birdhouses.kt` | Four spots (per-player multilocs on varps `birdhouse_transmit_a..d`). Build with a birdhouse of your level, put in 10 seeds (herb, hop, allotment, flower, bush; the better ones count double), wait `GROWTH_MINUTES` (10, the Wiki has 50), then Empty (clockwork back) or Reset (logs, hammer, chisel, Crafting level) |
| Dev helpers | `HunterSpawns`, `HunterDebugCommands` | The dev creatures and the dev trap sites (boulders, young trees, pits, birdhouse spots) that `HunterSpawns` put north east of the lobby were removed on 2026-10-10, and the file is gone, so nothing is placed at startup. `::huntkit` gives traps, rope, nets, knife, logs, both butterfly nets, jars, teasing stick, falconer's glove, bait |
| Isle side | `relics/LeagueHunterRelics`, `tasks/LeagueHunterTasks` (isle module) | The Animal Wrangler hunter half, the Friendly Forager hook, and 35 tasks |

Events other modules can use: `HunterTraps.Modifiers/Roll/Collecting/Trapped`,
`HunterCatching.Caught/Opening/Looted`, `Falconer.Hunted`, `Birdhouses.Built/Collected/Dismantled`.

## Decisions (user)

1. **Wiki values** for trap timing: scan every 5 ticks, 100 ticks of life, the Wiki's catch charts.
2. **Birdhouse growth** shortened: 10 minutes instead of 50.

## Decisions (mine, say if any should change)

- **Quests lock nothing** (box traps needed Eagles' Peak, Varlamore creatures their quests).
- **Left out:** herbiboar, aerial fishing, drift nets, crab traps, Broavs, the Chambers of Xeric bats,
  Hunter Rumours and their loot sacks, the Guild Hunter outfit, the dragon hunter weapons, pets, the
  monkey trap and rabbit snare, kebbit capes. No clue nests either (the isle has no clue scrolls).
- **Falconry is simplified:** no Matthias, no falcon npc to fetch; the catch comes straight back.
- **Tatty fur** (the three big cat furs) is a straight line from 0 to 255 over the trapper's level.
- **Big cat clothing** has no crafting recipe on the isle yet, so the task "Equip full Graahk, Larupia
  or Kyatt" (381) is not in the list. The clothing still softens a cat's claws when worn.
- **Butterfly boosts** are 4 + 15% (Attack, Defence, Strength), 8 hitpoints (snowy knight), a restore of
  drained stats (sunlight moth), 22 prayer points (moonlight moth). The Wiki numbers were not checked.
- **Pit and birdhouse decorations** (corner pieces) and the net trap's two tile loc are placed from the
  cache's names and sizes alone. See the client checklist.
- **Fur and loot of kill-free hunting:** all catches go to the inventory; what does not fit is dropped.

## Relics

- **Animal Wrangler** (tier 1, works without the harpoon): traps never fail (`HunterTraps.Roll`), box traps look every 2 ticks and
  show the catch after 1 (instead of 5 and 3), a chinchompa catch gives two and twice the experience
  (`Collecting.catches`), impling jars never break (`HunterCatching.Opening`).
- **Friendly Forager** (tier 2): each trap or pit collected, butterfly or impling caught, kebbit hunted
  and birdhouse emptied finds a grimy herb through `LeagueForager`.

## League tasks (35, 3,430 points)

Easy 3: Catch a Baby Impling (379), Snare a Bird (380), Catch a wild kebbit (1369).
Medium 12: Swamp Lizard or Salamander (382), Dismantle 20 Filled Bird Houses (439), Catch 50 Implings
(445), Crystal Impling (519), Trap a Spined Larupia (691), Red Salamander (694), 30 Orange Salamanders
(859), Swamp Lizard (912), Black Salamander (976), Black Chinchompa (977), Grey Chinchompa (1614),
Sabre-toothed Kebbit (1833).
Hard 8: Dragon Impling (384), Set up a Magic Bird House (440), Trap a Sabre-toothed Kyatt (578), Equip
100 and 250 Black Chinchompas (978, 979), 50 Grey Chinchompas (1615), 25 Sunlight Antelopes (1697),
Dragonstone Amulet from a Crystal Impling (1513).
Elite 12: Level 99 and 25, 35, 50 million Hunter XP (256, 257, 1475, 1498), Lucky Impling Bare-Handed
(385), 150 and 300 Red Chinchompas (692, 1377), 500 Chinchompas (1616), 50 Moonlight Antelopes (1696),
100 Tecu salamanders (1720), Equip a Tecu Salamander (1722), Equip 500 Black Chinchompas (980).

Tasks that named a place or an area tag (23 of them) are rewritten area-free through
`IsleOfSoulsTaskTexts`. Left out as they need content the isle does not have: Rumours (1657-1660), the
loot sacks (1709, 1708), Guild Hunter outfit (1706), herbiboar (443, 444), dragon hunter lance and
crossbow (129, 1060), golden tench (187), Polar Kebbit (1831), full Graahk/Larupia/Kyatt (381), the
Varlamore "give an antelope" and moth teleport tasks.
Counters: ten `isle_count_hunter_*` varps (65387..65378). New enum keys 362..396.

## What the user has to do

Stop the server and run `./gradlew packCache` (new timer `hunter_tick`, 8 server varps, 10 counter
varps, 23 `league_task_<id>` structs, new task enum keys), then check the client.

## Client checklist (not seen in a real client)

1. The hunter block north east of the lobby is gone (removed 2026-10-10); try Hunter on the creatures and trap locs of the map.
2. `::huntkit`, then lay a bird snare (Lay) and a box trap; wait near a swift or chinchompa. The trap
   should change picture when something goes in, and "Check" gives the catch.
3. Net trap: use "Set-trap" on a young tree. The net should lie beside the tree, and the caught and
   failed pictures should cover tree and net (a guess about where the two tile loc sits).
4. Deadfall on a boulder; magic box with an imp.
5. Pit: "Trap" the pit with knife and logs, "Tease" a cat, jump across. Check the pit pictures (set,
   falling, full) and that the corner pieces sit round the pit.
6. Butterflies and implings with the net and jars; Release / Loot on the jars.
7. Birdhouse spots (four houses in the block): Build, add seeds, wait ten minutes, Empty.
8. Switch on Animal Wrangler and Friendly Forager and watch for the effects.

## Numbers to tune

`HunterTraps.LIFETIME` (100), `SCAN_TICKS` (5), `LURE_TICKS` (12), `SETTLE_TICKS` (3);
`Birdhouses.GROWTH_MINUTES` (10); `Pitfalls.TEASE_TICKS` (50); `LeagueHunterRelics.FAST_SCAN_TICKS`.
