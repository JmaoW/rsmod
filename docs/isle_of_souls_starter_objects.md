# Starter objects (2026-10-13)

Small placements around the isle's starting areas, all in
`content/areas/misc/isle-of-souls/.../starter/` (`StarterScript`, `StarterConfigs`). Everything is
placed by `StarterScript.place()` when the server starts; the coordinates are constants in its
companion object.

| What | Where | Notes |
| --- | --- | --- |
| Rocks with a pickaxe (`royal_mining_stone_pickaxe`, 15127) | 2195, 2785 | "Take-pickaxe" gives a bronze pickaxe and swaps the rocks for `royal_mining_stone` (15128), which turn back after 10 ticks. Refused if the player already has a pickaxe in their inventory or has no room. |
| Stump with an axe (`log_withaxe`, 5581) | 2168, 2808 | The "Take-axe" option is Lumbridge's (`LumbridgeScript.takeAxeFromLogs`): bronze axe, `log_withoutaxe` (5582) for 10 ticks (was 50). |
| Gerrant (npc 2891) | 2167, 2786 | Talk-to and Trade. Shop is the cache's `fishingshop` inv (19 slots, stocked from the Wiki's Gerrant's Fishy Business page); he sells at value and buys at 70%, 1% price change. |
| General store shop keeper (npc 2813) | 2162, 2805 | The same npc type as Lumbridge's shop keeper, so Lumbridge's script answers: same dialogue, same `generalshop1` stock, titled "Lumbridge General Store". |
| Hammer (obj 2347) | 2217, 2789 | A permanent ground spawn, so it respawns when taken. The respawn time is the hammer type's `respawnRate`, set to 10 ticks (`StarterObjEdits`), which applies to every server-placed hammer. |
| Furnace (`varrock_diary_furnace`, 16469) | 2218, 2787 | Edgeville's furnace, facing east like the real one. Smelting finds it on its own: any loc named "Furnace" with a "Smelt" op. |

The test furnace and anvil that `SmithingTestLocs` put in the ruin by the fishing spots were removed.

## Engine change

`LocRepository.change(from, into, duration)` swaps a loc for another and reverts after `duration`.
That only worked for locs from the map: a loc a script had placed (`locRepo.add`) was simply
replaced and nothing brought it back. It now remembers a spawned `from` and puts it back, which is
what lets the stump and the pickaxe rocks (both placed at startup) refill.

## Needs

`./gradlew packCache` (Gerrant's stock and prices, the hammer's respawn time), then a restart.
