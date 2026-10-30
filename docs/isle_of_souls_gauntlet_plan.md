# Isle of Souls: The Gauntlet and the Corrupted Gauntlet

Scoped 2026-10-20 with the user. **Steps 0 to 9 are built and tested (overnight, 2026-10-20 to 21); nothing has been seen in a client.** The open problems are in `isle_of_souls_gauntlet_issues.md`. This file is
the plan; progress is recorded at the bottom of each step as it lands.

Why: content plan 3.4 needs the Corrupted Gauntlet before Corrupted Hunllef (Echo) and mastery
tasks 7-9, and the Raging Echoes task list has 4 Gauntlet tasks plus about 10 crystal tasks.

## 1. Decisions (user, 2026-10-20)

| # | Decision |
|---|----------|
| 1 | **Duo run in one shared maze.** TSPS built the real game's solo run; ours takes the whole team (one or two players) into one maze and one Hunllef. |
| 2 | **Gauntlet and Corrupted Gauntlet together.** Corrupted is a second data set on the same code. It stays behind one normal completion, like the real game (varp 2353 shows the "Enter-corrupted" option). |
| 3 | **A small crystal crafting version** so the seeds from the chest are useful (section 6, step 8). |
| 4 | **Hunllef at wiki stats** (600 / 1000 hp) with **one hp multiplier constant** to tune after the first client test. |
| 5 | **The Hunllef alternates targets per attack** (the engine's alternating targeting style). |
| 6 | **A player who dies is out of the run**: they lose their kit (as in the real game), land in the lobby and cannot re-enter that maze. Their reward uses the "anything else" table. The run ends when both are out or the Hunllef dies. |
| 7 | Echo Corrupted Hunllef is **saved for later as a pair** (like the Kalphite Queen). Combat Achievements do not exist on the isle, so the Gauntlet CA task (1437) stays out. |

Defaults taken without asking (say if you disagree): no Song of the Elves check (the isle has no
quests and no area gating), each player gets their own starting kit and their own items, the maze
and its nodes are shared, points and rewards are per player, completions count once per team.

## 2. What TSPS gives us

`tsps/docs/gauntlet.md` plus 15 files in `server/plugins/minigames/gauntlet/` (about 3,100 lines of
JavaScript) and a 1,042 line test file. It is written for a different engine, so it is a **fact
source, not code to port**. Its numbers come from three places, and its own doc says which:

- **Wiki**: prep times, the singing bowl recipes, the Hunllef's mechanics, max hits by armour tier, weapon speeds, the reward chest tables, points, the Corrupted drop weights.
- **Cache**: ids, node and loc names, template chunks (checked again here, section 4).
- **Near-Reality** (a leaked server, no licence, "behaviour oracle only"): animation and projectile ids, the tornado summon every 56 ticks, a floor pattern every 30 ticks and orange for 6, a 15% chance for a magic attack to disable prayers, the three floor-pattern sets, how rooms are stocked with resources and monsters, the gather rate (1 tick = 2 ticks, 1 xp, 1 in 5 shards). Facts such as ids and ticks are fine to read; copy no code and no tables.

TSPS's own open gaps: no unlock check (we skip it too) and the dragon's impact graphic (1703 /
1704 is not shown). Everything taken from it stays "unverified against the real game" until the
client check.

## 3. What the cache already holds

Same revision as TSPS, nothing needs adding to the cache:

- Items 23820-23904 (both modes: tools, shards, components, vials, armour t1-t3, weapons t1-t3, teleport crystal, cape 23859), 25958-25961 (combo fish, escape crystals). Names are `gauntlet_*` and `gauntlet_*_hm`.
- Npcs 9021-9025 (Hunllef forms, death, tornado) and 9035-9039 (Corrupted), 9026-9034 and 9040-9048 (the 9 monsters each mode), 9020 (Bryn, `gauntlet_instructor`), pets 8737 / 8738.
- Locs 35964+ (Corrupted nodes, bowl, tools, range, sink, blockade, floor tiles) and 36060+ (Regular), the unlit and lit door walls (the nodes).
- Varps 2353 / 2354 (completed counts), 2322, 2333 / 2334; varbits 9177 (boss started), 9178 (in gauntlet), 9179 (reward waiting), 9240-9288 (49 rooms found), 9289 / 9290 (current room), 9291 (start), 9292 (corrupted).
- Interfaces `gauntlet_overlay` (637), `gauntlet_map` (the client map), `gauntlet_scoreboard` (639) and `gauntlet_recipes` (640), and cs2 scripts 2908-2919 for them. All are driven by the varbits above, so the server only sets varbits.
- Clog kill-count varps 65251 / 65263 already exist; the Egniol potion already exists in `herblore/MinigamePotions.kt`; the Gauntlet pickaxe is already in `Mining.kt`.

## 4. Step 0: the engine probe (done 2026-10-20)

Questions and answers. Probe code: `gauntlet/GauntletRegionProbeTest.kt` (integration, 2 tests, both pass against a
sandbox `.data`). The test world has no map, so real-map facts were read from the cache with
the existing Python readers (`tools/dungeons/census.py`).

**A. Is the template in this cache?** Yes. Map squares 29_88 (regular) and 30_88 (corrupted, 8
chunks east) exist, and the lobby squares 47_95 and 50_95 too. The chunks are exactly where TSPS
says (chunk coordinates, 8x8 tiles):

| Room | Source chunks |
|------|----------------|
| Four exits (middle) | x 232-233, y 704-711 (four variants of 2x2 chunks, y 704/706/708/710) |
| Three exits (rim) | x 234-235, same four variants |
| Two exits (corner) | x 236-237, same four variants |
| Start room | x 238-239, y 708-709 (holds the tools, books, singing bowl, range, sink, exit) |
| Hunllef room | x 238-239, y 710-711 (holds the blockades) |

The Corrupted ones are +8 chunks (x 240-247). Each room chunk already carries the unlit door
walls (the "nodes"), invisible walls and the start room carries every station as a static loc, so
the stations need no spawning.

**B. Does the maze need a large region?** No. 7x7 rooms of 2x2 chunks is 14x14 zones = 112 tiles
and a small region is 16 zones = 128 tiles. One small region per run, 19 x 85 of them available.

**C. Can a region grow rooms after it is live?** Yes at the registry level:
`RegionRegistry.registerZone(region, regionZone, copy)` is public, allocates collision, copies
BLOCK_WALK / ROOF flags and the static locs, rotates both, and fails on a zone already lit. A new
region has every zone blank and unwalkable until lit, which is the fog. A room turned r times
places its four zones with `RegionRotations.translateZone(r, x, y, 2, 2)`. The probe test pins
all of this.

**D. Does the client get the new room?** **No, not today.** `RspCycle.rebuildArea` only sends
`RebuildRegion` when the player's *build area* changes, caches the zone list per region uid (its
TODO names the Gauntlet) and the zone-update processor only resends a zone's locs and objs for
zones that became newly visible. Lighting a room needs a small engine hook:
a `Player` flag that makes the next cycle (1) drop the cached zone provider, (2) send
`RebuildRegion` again and (3) clear `visibleZoneKeys` so every zone is reset and its dynamic locs,
objs and (separately) npcs are resent. This is step 1 below and can only be proven in a real
client; the test covers the server half.

Fallback if the client half misbehaves: light the whole maze at the start (no fog, nodes
cosmetic, rooms still gated by the unlit door walls). The rest of the plan is the same.

## 5. Design

### 5.1 Run and instance
- One **GauntletRun** per team and mode, owning one small region (`RegionRepository.add`, template built per run with the boss and start rooms and nothing else), a layout plan, a prep timer, its players, kills and points. Players not on a team get their own run.
- The existing `TeamInstances` does not fit (it builds from a fixed template, enters at a fixed tile and keeps the instance alive while anyone is in it). The Gauntlet gets its own small registry, using the same engine calls (region add, telejump, `isZoneValid`) and the same "freed when empty" rule.
- Entering takes **everything** and leaves it behind: Bryn requires empty inventory and equipment, the run hands out the starting kit, and every exit (platform, barrier Escape, death, logout, timer) takes the kit back and returns the player to the lobby.
- Members enter together at the lobby entrance. A teammate who arrives later (before the barrier is passed) joins the same maze. After the boss phase starts the maze is closed.

### 5.2 Maze
- Layout planner: the Hunllef room in the centre, the start room on a random side, six demi-boss rooms on the rim (bear, dragon, dark beast, twice each), every other room a random template of its type turned so closed sides face outward. Pure and testable with a seeded random (TSPS's `planLayout`, re-derived from the Wiki).
- Lighting: nodes on lit-room edges are interacted with; the neighbour is registered with `registerZone`, the player scene is rebuilt (4D), the varbit `9240 + y * 7 + x` marks it found, current room varbits update as players move.
- Resources and monsters are stocked into a room when it is lit, not in the template.

### 5.3 Duo rules
- Prep time, the 49 room varbits and the barrier are per run; both players see the same map.
- **Gathering nodes** are shared (a spent node is spent for both), inventories are separate. Starting kits are per player.
- **Points** (10 demi-boss, 5 strong, 2 weak, upgrades and cooking as in TSPS) go to the player who earned them; **kill drops** go to the player who dealt the most damage (the existing hero-points rule). Each player has their own chest reward and their own stats.
- **Completion** counts once per team for team task counters (like the Inferno), once per player for the clog kill-count varp and the scoreboard.
- **Hunllef**: one npc. It alternates its target per attack among players still alive and inside. The 4-attack style cycle is global. The protection prayer switches after every sixth hit that is *not* of its protected style, counted over both players together. Each player's own protection prayer and armour tier decide that player's max hit. Tornado count and floor patterns follow the wiki thirds of its hp, unchanged by the number of players. A single hp constant (`HUNLLEF_HP_MULTIPLIER = 1.0`) scales it.
- **Death**: out of the run, as decision 6. If the last one dies the run ends with no Hunllef kill.
- With every combat mastery unlocked (echo hits, never-miss ranged, 50% attack rate) the fight is easier than the wiki's. This is the same situation as the Caves and Inferno: balance after the first client test, with the multiplier.

### 5.4 Hunllef
Scripted like Zuk and Zulrah, not through the default combat: the engine's `NpcAttacks` performs the
hits (accuracy, protection prayers, projectile, impact timing). New pieces: a protection rule
("hits of the protected style do nothing", with the six-hit switch), the head icon, tornado npcs
that chase for 20 ticks, floor tiles as loc swaps (`+1` blue / crimson, `+2` orange), the stomp,
and the 600 / 1000 hp transmog form per protected style (9021-9023 / 9035-9037).

### 5.5 Weapons and armour
Three weapons per mode (halberd, bow, staff) and three armour tiers. Halberd speed 4 (not 7),
range 2; the bow takes no arrows (strength on the bow); the staff has a built-in spell and a
fixed max hit of 23 / 31 / 39 by tier regardless of level. The engine needs: the bow without
ammo (the existing `RangedWeapon` types), a powered staff with a fixed max hit, and the armour
tier check (lowest piece of a full set) used by the Hunllef. Item stats already come from the cache.

### 5.6 Rewards
Per the Wiki (Reward Chest): a Hunllef kill gives crystal shards and two rolls (three in Corrupted,
plus the cape on the first one); anything else gives one item from the incomplete table (50+
points), junk (1-49) or nothing. Tertiaries are rolled on their own: elite clue (a casket, per the
content plan), crystal weapon seed, crystal armour seed, enhanced crystal weapon seed, Youngllef
pet. The chest needs a free slot; the rest goes on the ground. The exact tables are in
`tsps/docs/gauntlet.md` (section "Rewards") and get re-read from the Wiki when the step is built.

## 6. Steps

Each step ends with tests against a sandbox `.data` (see the testing note in memory), a line here,
and a client-checklist line. Rough size next to what exists: about the size of the Inferno (10
steps), a little under the Kalphite Queen plus Zulrah together.

| Step | Contents | State |
|------|----------|-------|
| 0 | Engine probe (section 4) | done |
| 1 | **Engine hook**: scene resend when a region gains a zone. Lobby, Bryn, entry rules, starting kit, run and timer, overlay (637), map interface, exits, death, logout recovery, varbits | built 2026-10-20 (section 6.1) |
| 2 | **Maze**: lighting from nodes (the planner, the run's region, room lighting and the varbits came with step 1), teleport rules | built 2026-10-20 (section 6.2) |
| 3 | **Preparing** (built 2026-10-20, section 6.3): resource nodes and gathering (5 types, Corrupted ids), tool storage, water pump, range, the singing bowl (recipe menu, upgrades in place), Egniol chain, teleport / escape crystals, paddlefish | |
| 4 | **Monsters** (built 2026-10-20, section 6.4): 9 npcs per mode, `NpcEditor` ranges and anims, demi-bosses, room stocking, run drop rules (shards, frames, components, paddlefish, grym, teleport crystal) | |
| 5 | **Weapons and armour in combat** (section 5.5) | built (section 6.5) |
| 6 | **Hunllef** (section 5.4) and the barrier | built (section 6.6) |
| 7 | **Rewards** (built, section 6.7): points, chest and tables, tertiaries, pets, stats, scoreboard (639), announcements, clog kill counts, tasks 534 / 535 / 547 / 548, mastery hooks | |
| 8 | **Crystal crafting** (built, section 6.8): singing bowl recipes outside the Gauntlet for crystal bow, shield, armour, halberd-class crystal items and the enhanced weapons, reaching tasks 530, 531, 546, 549, 558, 1396, 1512; tasks 560 (crystal crown) and 1809 (crystal tool) need their sources checked on the wiki first, since the tool seed is a Zalcano drop | |
| 9 | Docs (this file), issues list, memory | done: see `isle_of_souls_gauntlet_issues.md` |

Task points available: 534 (80), 535 (80), 547 (200), 548 (200); crafting group 530 (80), 531
(200), 546 (80), 549 (80), 558 (80), 560 (400), 1396 (200), 1512 (80), 1809 (400) = 1,600.
Task 1437 (Gauntlet Combat Achievements, 400) stays out. Mastery tasks 7-9 wait for the echo bosses.

### 6.1 Step 1 as built (2026-10-20)

Code: `content/areas/misc/isle-of-souls/.../gauntlet/` (`GauntletConfigs`, `GauntletMode`,
`GauntletLayout` pure planner, `GauntletRun`, `Gauntlet` the manager, `GauntletScript` the
handlers). 6 unit tests (`GauntletLayoutTest`) and 17 integration tests (`GauntletRunTest`,
`GauntletRegionProbeTest`) pass against a sandbox `.data`.

- **Engine hook**: `Player.requestRegionRebuild()` (`engine/game`) sets a flag and clears `visibleZoneKeys`; `RspCycle.rebuildArea` drops its cached zone provider and sends `RebuildRegion` again when the flag is set. The `PlayerPostTickProcess` order (client cycle first, zone updates after) means the rebuild reaches the client before the zones' locs and objs. Not testable without a client.
- **Run**: one `GauntletRun` per team (or lone player), mode fixed at creation. The maze is a small region that starts blank; the Hunllef's room and the start room are lit at creation and `Gauntlet.lightRoom` lights any other room later (4 zones x 4 levels, turned) and makes everyone inside resend the region.
- **Entering** (`Gauntlet.enter`): Bryn must have been talked to, no reward waiting (varbit 9179, set by step 7), empty pack and worn items, Corrupted only after a completion, teammates join while the run is in preparation, nobody joins once the fight began, and a player who died or left cannot re-enter that maze. Not checked yet: a pet being out (no pet system to ask).
- **Kit**: sceptre wielded, axe, pickaxe, harpoon, pestle and mortar and teleport crystal in the pack (Corrupted ids for the Corrupted version).
- **Clock and varbits**: preparation 1000 / 750 ticks, the HUD overlay (interface 637) opened on `toplevel_target_overlay_hud`, cs2 2914 told the time left on entry, every 50 ticks and over the last 15; at zero everyone is taken to the Hunllef's room (cs2 2916 "Final Encounter"). Maze varbits: version, `player_in_gauntlet`, start room, the 49 found bits, current room, boss started.
- **Ways out**: the start room's platform (Exit asks, Quick-exit does not), the barrier's Escape, death (out of the run, wakes in the lobby via `LobbySpawnScript`), logout (kit cleared, left in the lobby), being moved out by anything else (kit cleared on the next tick), and a login after a cut-short run (`isle_gauntlet_in_run` varp) clears the kit and moves them to the lobby. The barrier's Pass takes the whole team in together.
- **Lobby and isle**: the lobby (entrance 37340, chest, scoreboard, deposit box, Bryn) is already in the real map at (3032, 6127, 1); only the entrance, Bryn and the platform are wired. The portal is a copy of the Prifddinas one placed on the isle at (2125, 2887) facing north (moved there by request; originally (2236, 2809) facing south); the lobby platform's Channel brings a player back to (2128, 2890). Bryn explains the place on first talk.
- **Needs**: `./gradlew packCache` (4 new varps: `isle_gauntlet_bryn`, `_done`, `_done_hm`, `_in_run`, ids 65315-65312 in `varp.sym`) and a restart.
- **Not done in step 1**: the Hunllef itself (the boss room is empty), fades, the pet check, the reward chest / scoreboard / deposit box handlers (step 7 and the real deposit box needs no handler), nodes and everything in the maze (steps 2-5).
- An unrelated existing failure showed up in the sandbox: `ZulrahLobbyTest` "the boat and the way out of it stand on free ground of the plaza" (boat tile 2089,2937 blocked). It does not load any Gauntlet code; not looked into.

### 6.2 Step 2 as built (2026-10-20)

Code: `GauntletNodes` (new), plus `Gauntlet.lightNode` / `useTeleportCrystal`, node and crystal handlers in
`GauntletScript`, and a home-teleport block in `lobby/HomeTeleportScript`. 6 more integration
tests (`GauntletNodesTest`); the package now has 24 integration and 6 unit tests, all passing in a sandbox.

- **Nodes**: every side of a room has two "Light" nodes and two symbols (read from the cache: the same room-relative tiles in every room of both templates). The "Light" op (needs the sceptre in the pack or in hand) lights the room on the other side of the node's side: the room is drawn, the version of its found varbit is set, everyone inside resends the region, and the nodes and symbols on both sides of that passage change to their lit versions (`locRepo.change` over the static map loc). Which side a node is on is worked out from where its footprint stands in the (possibly turned) room, so turned rooms need no special case. The passage between the start room and the Hunllef's room is lit as soon as the run is made.
- **Symbols**: the cache has three versions of each arch (`unlit` "Inactive Symbol", `lit` "Illuminated Symbol", `lit_inactive` unnamed). TSPS left them alone because it could not tell which the game uses; this swaps unlit to lit (the one named "Illuminated Symbol") on a lit passage. A guess to check in the client.
- **Teleport crystal** (Activate): back to the start room and used up; not in the start room (kept, "You're already at the start..."); not once the fight began ("you win or you die"), both as the wiki says.
- **Teleports out**: the isle's Home Teleport spell refuses while in a maze. Every other teleport needs runes or items the kit does not have; anything that does move a player out is caught on the next tick, which takes the kit and the player's place in the run (the same rule as the real game's "teleports are blocked").
- **Test method note**: the test world has no map, so `placeMapLoc` seeds the nodes and symbols into the template chunks, and `Gauntlet.pick` is replaced to fix the plan.
- **Not done in step 2**: the escape crystal (made at the singing bowl in step 3, with its use), the node lighting sound, fades.

### 6.3 Step 3 as built (2026-10-20)

Code: `GauntletItems` (item sets for both versions, from the cache names), `GauntletRecipes` (pure
bowl recipes), `GauntletResources` (the five node kinds and the stocking), `GauntletCrafting`
(bowl and Egniol logic, injectable), `GauntletPrepScript` (the stations' handlers). 14 more
integration tests (`GauntletPrepTest`).

- **Stocking**: when a room is lit it is stocked once, as Near-Reality does it (the wiki gives no placement): 3 rooms in 4 get 1-3 gathering nodes (at most 2 with monsters), 1 in 5 also 1-3 grym roots; nodes on floor beside a wall, apart, clear of doorway approaches, never cutting one doorway off from another. Demi-boss rooms hold only the demi-boss; the start and boss rooms are never stocked. `GauntletRoom.monsters` says whether step 4 puts monsters in. Tile freedom is read from the maze's own collision, so turned rooms need no special case. The connectivity check is on tiles only (it ignores wall edges), which is enough for the template walls.
- **Gathering**: Deposit (pickaxe, 3 ore), Roots (axe, 3 bark), Fishing (harpoon, 4 raw paddlefish, 2x2), Linum (no tool, 3), Grym (no tool, 1), both versions' ids. A harvest every 2 ticks with 1 experience in Mining / Woodcutting / Fishing / Farming, 1 in 5 also 5-30 shards. The op repeats itself each tick like the other skills; a spent node becomes the next object id. Nodes are shared by the team (the count left is on the run).
- **Start room**: Tool Storage (offers everything / the first four missing tools), the Crystal Sceptre holder, the Water Pump (also a vial used on it or on a fishing spot: one vial a tick), the Range, the Singing Bowl, the two books (the recipes book opens interface 640; the Egniol book prints its three lines).
- **Range**: the Cooking module cooks the paddlefish (a new food entry: level 1, 15 xp, range only, success `169 + (level - 1) * 87 / 47` out of 256, which never burns from level 48 as Near-Reality's `48 - level` per cent does). The Gauntlet counts 1 point per paddlefish cooked through `Cooking.Cooked`.
- **Singing bowl**: `skillMulti` menu of up to ten products: vial (10 shards), teleport crystal (50), escape crystal (200), crystal paddlefish (10 shards + a paddlefish), and each helm / body / legs / halberd / staff / bow at the tier after the one the player has. Armour needs the previous tier plus ore, bark and linum; weapons: frame, then 50 shards, then the component. Crafting and Smithing get the same experience. A needed piece that is worn is upgraded where it is worn (`WearposChange` is published so the weapon interface and bonuses follow). Points: 2 / 5 / 10 for a basic / attuned / perfected piece and 1 for crystal paddlefish, kept per player on the run for the reward in step 7. The armour totals are tested against the wiki: 150 / 350 / 650 shards and 3 / 7 / 13 of each resource.
- **Egniol potion**: water-filled vial + grym leaf makes a grym potion (unf); a pestle on 10 shards makes 10 dust; 10 dust on the potion makes an Egniol potion (3) and 10 Herblore xp. Drinking it already works in the Herblore module.
- **Escape crystal**: takes a player out at once, in preparation or the fight (`Gauntlet.useEscapeCrystal`). How long it channels in the real game is not known.
- **Not tested**: the tool storage's choice menu with several tools missing (the test harness cannot answer a chat choice) and the recipes book interface in a client.

### 6.4 Step 4 as built (2026-10-20)

`GauntletDrops` (the pure drop rules), `GauntletMonsters` (rooms, aiming), `GauntletCombatScript`
(the two shooters' attacks, the drop hook, the `NpcEditor`). 14 integration tests.
- **Rooms**: demi-boss rooms hold their demi-boss on room tile (7, 7); a room marked for monsters (by the stocking) gets 1-4 weak monsters three times in four when it is not on the rim, else two strong ones. Nothing respawns; the maze removes them when it closes.
- **Aiming**: each tick a monster goes for the nearest player in its own room, never one in the safe start room, and lets go when they leave. The shooters use `apPlayer2`, the rest `opPlayer2`; the dragon casts magic (graphics `crystal_dragon_magic_*`), the dark beast shoots (`crystal_dark_beast_range_travel`; TSPS had the wrong ids), both with the wiki's max hit 28 (48 Corrupted) every 4 ticks. The others use the engine's melee from their cache stats.
- **Drops**: the wiki's Corrupted pages' weights (see `GauntletDrops`). Each player keeps their own record (kills of each tier, whether a strong monster has dropped a frame, which components have dropped), so the "first weak kill / second strong kill" guarantees and the component rule are per player. The engine change that lets this replace the bones drop: `NpcDeath` now publishes `NpcDropEvents.Claim` for a monster without a table.
- **Points**: weak 2, strong 5, demi-boss 10, to the player who gets the kill.

### 6.5 Step 5 as built

`GauntletWeapons` (a `WeaponMap`, bound in `IsleOfSoulsModule`, plus an `ObjEditor`). 4 tests
(`GauntletWeaponsTest`, built on the revenant weapons' harness). The bows fire without arrows;
the staves are powered staves with a fixed max hit of 23 / 31 / 39 whatever the Magic level (checked
at Magic 1 and 99); the halberds are ordinary melee weapons whose speed the editor sets to 4. The
armour tier (lowest piece of a full set) is read by the Hunllef.

### 6.6 Step 6 as built

`GauntletHunllef` (the fight), `GauntletHunllefScript` (hit protection, death, edits). 15 tests.
- The Hunllef stands in its room from the start of the run; the fight begins with the boss phase.
- Attack every 5 ticks, ranged first, style change every fourth attack (with its animation); a stomp for anyone under it (not counted); tornadoes (Crystalline 1/2/3, Corrupted 2/3/4 by thirds of its hitpoints, every 56 ticks, 20 ticks each, damage by armour tier); floor patterns every 30 ticks (three pattern sets by thirds, blue then orange, 10-20 a tick on orange); 15% of magic attacks disable prayers.
- Max hits by armour tier and prayer are the wiki's (see `GauntletHunllef`); the prayer is read when the hit lands.
- Protection: hits of the protected style do nothing, every sixth hit of another style switches it (the form is swapped with `transmog`).
- **Duo**: it alternates its attacks between the players in the arena; everything else is shared. `HITPOINTS_MULTIPLIER` (1.0) is the knob.
- **Winning** (`Gauntlet.hunllefDied`): each player in the maze gets the completion counted, their statistics, the time messages and the league tasks; the whole team is taken to the lobby 5 ticks later.

### 6.7 Step 7 as built

`GauntletRewards`, `GauntletRewardTables` (generated from the TSPS tables, item names checked against the cache), `GauntletScoreboard`. 14 tests (`GauntletRewardsTest`).
- A player who leaves a run gets a reward by how they left: won, or any non-platform exit by points (50 or more the incomplete table, 1-49 junk, 0 nothing; the platform gives nothing). The reward is a number saved with the player (`isle_gauntlet_reward`); Bryn will not let them back in until the chest is opened; the chest shows open from varbit 9179, set again at login.
- Opening the chest needs a free slot; the items come from the wiki tables (shards, two rolls, the cape if not owned, the tertiaries), notes where the table says so, extras on the ground. Rare items are announced and go in the collection log.
- Personal statistics (completions, deaths, best time per version) are saved varps; the world's totals are in `.data/league/gauntlet-scoreboard.json`; the scoreboard loc (36060) opens interface 639 filled with both.
- League tasks 534, 535 (hard), 547, 548 (elite, counted once per team) were added to the list, the client enum and the text edits.

### 6.8 Step 8 as built

`CrystalSinging` / `CrystalSingingScript` (a copy of Prifddinas's singing bowl beside the isle portal) and `CrystalGearTasks`. 6 tests. Recipes from the wiki: crystal helm / legs / body (70 / 72 / 74), bow, halberd and shield (78), crystal axe / pickaxe / harpoon from the dragon tool and a tool seed (76), Blade of Saeldor and Bow of Faerdhinen (82), each needing both Crafting and Smithing at that level, giving the wiki's experience in both. Tasks 530 (craft armour), 531, 546, 549, 1512, 1396 (equip) were added. Charging, degrading and the other recipes are not built.

## 7. Unknowns and guesses (to confirm in the client)

- Scene resend after lighting (4D, built in step 1) is untested in a real client.
- The portal's tile and facing on the isle, and that the start room and Hunllef room appear (lit at creation, so this also tests the first `RebuildRegion` of a Gauntlet region).
- Whether a loc swap of the door walls looks lit; TSPS does not swap the "symbols" because it could not tell 36095 from 36096.
- Hunllef animations, projectiles, tornado cadence, floor patterns and the 15% prayer disable are Near-Reality's, unverified.
- Crystalline (regular) monster drop weights: the wiki only says common / uncommon, so TSPS uses the Corrupted weights. We do the same.
- Duo numbers are all guesses; the Hunllef hp constant is the knob.
- `./gradlew packCache` is needed (new varps / symbols, `NpcEditor` for the 18 monsters, anims for the Hunllef).

## 8. Not built

Combat Achievements, the Song of the Elves quest check, Echo Corrupted Hunllef, a second player
joining after the barrier, the dragon's impact graphic.
