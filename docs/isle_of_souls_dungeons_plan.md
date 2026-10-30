# Isle of Souls: dungeon access (scope and plan)

Written 2026-10-09. **Phase 0 is built (same day); see "Built so far" below.** Facts marked *(cache)* were read from the revision 233
cache with `tools/dungeons/census.py`; facts marked *(wiki)* come from the OSRS Wiki; the rest are my
reading of the repo.

## Built so far (Phase 0, 2026-10-09)

Code: `content/areas/misc/isle-of-souls/.../dungeons/` (`Dungeon.kt` is the table, `DungeonLocs.kt` the
object names, `DungeonScript.kt` the behaviour), `DungeonTest` (9 integration tests), and the generic
`DungeonLadderClimb` event in `DungeonLadderScript`. The Mining Guild now uses the same event. **Needs
only a server restart, no `packCache`**, except for the revenants (`./gradlew packCache`).

- **14 entrances** stand in two rows on the open field south of the lobby plaza (leave the plaza by the
  passage at x 2208-2212, z 2825-2836; the rows are at z 2817-2818 and z 2809-2815, x 2190-2220). The plaza
  itself is too crowded (agility arena, Zulrah's boat, altars, bank, cave mouths) for a 4x4 Brimhaven tree.
  Found from the cache collision map so each footprint is clear ground with its front tile reachable. Each is a copy of the real
  entrance object (or the nearest lookalike where the real one is a hidden multi-state object).
  Angles are chosen from the objects' approach flags: caves, manholes and the slayer-tower ladder
  only open on one side, so they face the side the player stands on.
- **`::dungeon <key>`** (admin) teleports to a dungeon's arrival tile; `::dungeon` lists the keys:
  `taverley edgeville varrock revenant stronghold brimhaven karamja kraken smoke catacombs forthos
  ham slayer crash`.
- **Exits:** 23 listed exit objects, plus every `ladder_from_cellar` inside a dungeon's area (the event
  rewrites its destination), all land on the tile in front of that dungeon's entrance. The Karamja
  Dungeon's cave to Mor Ul Rek works both ways.
- **Audit:** `python tools/dungeons/audit.py` checks the table against the cache (every exit is a real
  object on its tile, arrivals are walkable, entrances are on clear lobby ground, and no
  `ladder_from_cellar` reachable from an arrival lies outside a dungeon area, which would leak to
  the mainland). It reports no problems.
- **Revenants:** `tools/dungeons/revenants.py` writes all 30 (the wiki's quantities) to
  `revenants.toml`, at the spots on the wiki's Revenant Caves map (fitted to the cave's walkable tiles,
  4 px a tile, so a tile off at most). `tools/spawns/exclusions.txt` drops the source list's own 15.
- **Fight Cave and Inferno exits** now put you in Mor Ul Rek: (2438,5168) in front of the real Fight Cave
  wall entrance, and (2497,5119) at the end of the corridor south of the Inferno entrance. The plaza
  entrances for both still exist and still work.
- **Fight Caves from Mor Ul Rek:** the isle's Fight Cave entrance is the real
  `tzhaar_fightcave_wall_entrance` object, and its handler is by type, so the real wall entrance in
  Mor Ul Rek should already start a cave. Not tested. The Inferno's entrance there is a multi-state
  object and is not wired.

## Built so far (Phase 1-3, 2026-10-10)

Needs `./gradlew packCache` (door edits) and a restart. 509 isle integration tests pass (the one
failure, `InfernoRunTest` "entering starts a run...", is the old shortened-mode setting).

- **Doors and gates** (`DungeonDoors.kt`): `DungeonDoorEdits` gives the prison doors, slayer tower
  doors, the two odd walls and the metal gates their open versions in the cache, so the generic door
  and picket-gate scripts swing them. Doors with no open model (members' gates, the Taverley prison
  and gate doors, the jail door, the Forthos temple doors) vanish for a minute when used, both halves
  of a pair together. The dusty key, the temple key and the Giant key are not asked for.
- **Crossings** (`DungeonCrossings.kt`, `DungeonCrossingScript`): twelve Agility shortcuts made in
  place on the real map's objects with the agility module's step builder, so they look like the
  arena's: Taverley pipe (70), spiked floor (80) and loose railing (63); the Edgeville-Varrock pipe
  (51); Brimhaven's entrance pipe (22 one way), dragon pipe (34 one way), west stones (12 from the
  north) and two sets of east stones (56); the Catacombs' southern crevice (17), northern crevice
  (34) and stepping stones (28). Experience is the Wiki's where it has one (pipes 8.5-10, spikes 10,
  railing 8), zero where it gives none. A click from within 8 tiles starts one, since some stones
  stand where the walker cannot get next to them.
- **Brimhaven's stairs and vine** (`Dungeon.links`): both stairs between its two levels, and the
  north vine (87 Agility up, nothing down).
- **Passages** (`DungeonPassageScript`): the Forthos Dungeon's Grubby Door (57 Thieving, 10 XP); webs
  slashed with a knife (50%) or by the weapon's slash bonus (20% at +1, 100% at +100) in Forthos and
  the Varrock Sewers; Brimhaven's five vines chopped with any axe (10 Woodcutting for the two near
  the entrance, 34 for the three that lead on).
- **Kraken Cove** (`DungeonKrakenScript`): "Disturb" on a whirlpool hides it and puts a cave kraken
  there (magic, max 13, every 6 ticks); the whirlpool is back 50 ticks after the kill.
- **Smoke Devil Dungeon** (`DungeonSmokeScript`): without a face mask or any slayer helmet a player
  coughs and loses 1 hitpoint every 4 ticks, never the last one. The amount and interval are guesses.
- **Revenants** (`DungeonRevenantScript`): the eleven kinds fight in melee, ranged or magic with the
  Wiki's max hits (2 to 30), pick the style the player's defence is weakest against (60%) and avoid
  one they are protecting from, and heal when under half their hitpoints (one heal every 15 ticks at
  most, 8 to 36 of them). Attack speed 4 ticks, heal size and odds are guesses. The bracelet of
  ethereum, the revenant weapons and their ether are in `isle_of_souls_revenant_weapons_plan.md`.
- **Tools** added: `collision.py` (flood fill), `localmap.py` (ASCII map round a tile with reachability),
  `barriers.py` (passage objects each arrival can reach), `audit.py` now also checks the crossings.

- **Revenant Caves pillars (2026-10-20):** all five `wild_cave_agility_jump` pillars are crossings (3 tiles in, 3 out, jumped
  via the pillar tile; `ShortcutKind.Pillar` now honours a `stones` list). Levels/xp from the Wiki: 65 (6.5) at 3220,10086; 75 (7.5)
  at 3200,10136, 3180,10209 and 3202,10196; 89 (8.9) at 3241,10145. The Wiki's failed jump (reduced xp, 4-11 damage, still crosses)
  is not built. The map has the pillars on level 1 over a bridge flag, i.e. level 0 in game. No `packCache` needed.

Still not done: the Forthos temple library shortcut (63 Agility) and the thick webs to Sarachnis, the
Taverley hellhound-cave crawl, dragon rocks and steps, Brimhaven's log balance and crevices to the
metal dragons, the Stronghold Slayer Cave tunnels, Mor Ul Rek's hot vent doors, the Kraken boss's
whirlpools and the H.A.M. storeroom door (the Obor gate and Bryophyta's lair were built on
2026-10-19, see `isle_of_souls_giants_plan.md`). The H.A.M. Hideout past its first door is
unchecked (the flood-fill model cannot read the diagonal door there).

## Built so far (round 4, 2026-10-12): four more dungeons

Fremennik Slayer Dungeon, Kalphite Lair, Mos Le'Harmless Cave and the Isle of Souls Dungeon. All
four are rows in the `Dungeon` table like the rest (18 entries now), so entrances, exits and the
`::dungeon` command (`fremennik kalphite mos souls`) work the same way. **No `packCache` needed**,
only a restart. Facts below are from the cache (`tools/dungeons`).

| Dungeon | Entrance on the isle | Arrival | Way out |
|---|---|---|---|
| Fremennik Slayer Dungeon | `slayer_dungeon_entrance` (3x2 cave) at 2186,2800 facing west | 2808,10002 | `slayer_dungeon_exit` 2809,10001 |
| Kalphite Lair | `kalphite_burrow_entrance_withrope` ("Climb-down") at 2186,2804 | 3484,9510 **level 2** | rope `kalphite_burrow_exit` 3483,9510 |
| Mos Le'Harmless Cave | `harmless_cave_entrance_left` (one tile, the cave's right half is not copied) at 2189,2800 | 3748,9373 | two cave exits 3749,9373/9374, **and the two spiral stairs at 3814,9462 and 3829,9462**, which climb to the real Mos Le'Harmless and would have leaked |
| Isle of Souls Dungeon | **the map's own `sw_dungeon_entrance` at 2308,2918** (not placed; `Dungeon.onMap`) | 2167,9308 | `sw_dungeon_exit` 2168,9307, lands on 2310,2919 beside the cave |

- The Isle of Souls cave has a twin at 2116,5990 (the Soul Wars tutorial area); its handler checks
  the coordinates so only the real one works.
- All monsters were already spawned (Fremennik 81, Kalphite 35, Mos 95, Isle of Souls 33). A flood
  fill over the cache collision from each arrival reaches every spawn without using any shortcut,
  except one stray kurask troll in the Fremennik dungeon and the Kalphite Queen's chamber (the queen
  and three larvae), which nothing yet leads to.
- **Shortcuts (added the same day, `DungeonCrossings.kt`, levels from the Wiki):** Fremennik strange
  floor (43 Agility, 2768,10002 to 2775,10003, jumped tile by tile), Fremennik crevice (61, 2730,10008
  to 2735,10008) and the Kalphite Lair crevice (86, on level 2, 3500,9510 to 3506,9505; the Elite Desert
  Diary it also asks for is dropped). Both ways, no experience (the Wiki gives none).
- **Not wired:** the Kalphite Queen's chamber (the rope entrance at 3509,9497 is a multi-state
  object and the queen is a boss, so it is left for the boss plan), the lair's hive-wall tunnel
  (3515,9520, which the Wiki says leads into the Dorgesh-Kaan South Dungeon, not part of the isle), the
  Isle of Souls lockpick chest (2139,9299). (The Mos Le'Harmless cave horror's witchwood-icon mechanics
  are built, in the slayer module's `CaveHorrorScript`.) The dungeons are fully walkable without them.
- Entrances sit in the free ground at the south-west of the field (x 2186-2190, z 2800-2805), picked by
  collision and kept clear of npc spawns. Not seen in a real client; the mouth models (rotations
  copied from the real map) may need flipping.
- `audit.py` now understands `onMap` entrances and reports no problems for the new four; it still
  lists three older problems that come from entrances the user moved (Taverley, Varrock, Slayer Tower
  Basement), which it treats as sitting on blocked ground.

## Built so far (round 5, 2026-10-12): Karuulm, God Wars and the Lizardman Caves

Three more rows in the `Dungeon` table (21 now): keys `lizardman karuulm godwars`. **Needs a restart, plus
`./gradlew packCache` for the Zamorak bridge's "Cross" option** (`DungeonBridgeEdits`); everything else
works from the unmodified cache. 57 dungeon integration tests pass (12 of them in the new `DungeonGodWarsTest`).

| Dungeon | Entrance on the isle | Arrival | Way out |
|---|---|---|---|
| Lizardman Caves (Shayzien) | `lizardman_cave_entrance` (3x3 cave) at 2223,2807 facing north | 1305,9973 | `lizardman_cave_exit` crevice 1305,9974 |
| Karuulm Slayer Dungeon | the Brimstone elevator copied piece by piece (17 `Dungeon.props` round the "Elevator controls" at 2231,2806; the platform centre is also clickable) | 1311,10189 | `brimstone_dungeon_exit` 1311,10185, lands on the platform |
| God Wars Dungeon | `godwars_entrance_groundhole02` (4x4 hole, the roped state) at 2236,2804 | 2882,5310 **level 2** | `godwars_entrance_rope_bottom` 2881,5311 |

New machinery: `Dungeon.props` / `Dungeon.alsoEnter`, a `DungeonEntered` event (published by the
entrance and `::dungeon`), `DungeonGates.kt` + `DungeonGateScript` (walk-through gates with a rule:
free, a skill level, or a kill count), `GodWarsScript` (ropes, kill counts).

- **Lizardman Caves:** four crevice pairs are squeeze crossings (no level asked; the Wiki lists none), so
  all eleven spawned shamans, soldiers and guards can be reached.
- **Karuulm:** three rock climbs, two lava-gap jumps, the level 1 wall tunnel and the hydra corridor rocks
  are `DungeonCrossings` (no level); the stairs between its three levels and the two ends of the
  mysterious pipe (88 Agility, from the Wiki) are `Dungeon.links`; the two alchemical doors to the
  Alchemical Hydra's room are free gates. Every spawn is reachable but the three Tasakaal, who stand on
  blocked tiles. **Left out:** the dungeon's heat (4 damage a tick without boots of stone, brimstone or
  granite: an item rule, like the keys) and the Alchemical Hydra's phases (the boss is spawned and
  fightable as an ordinary monster).
- **God Wars:** the ropes are tied for anyone coming in (and an untied rock ties with no rope item).
  The ropes down to Saradomin's camp are real links. Gated crossings with the Wiki's levels, items
  dropped: Bandos' big door 70 Strength (in only), Armadyl's pillar 70 Ranged (both ways), Zamorak's
  broken ice bridge 70 Hitpoints (base level, both ways) and it drains Prayer to 0. Each god's chamber
  door asks for **40 kills** of that god's followers (counted by cache name in `God.ofFollower`, in the
  dungeon only, saved with the player and never reset by coming or going, 40 spent on going in;
  leaving is free; no combat achievement discounts). The count is told in chat, and the real kill count
  box (`godwars_overlay`, interface 406) is opened on the full-screen atmosphere layer while the player is
  inside the dungeon's area and closed outside it (a per-tick soft timer, `godwars_overlay_tick`, so
  teleports, death and relogging all work). The box's own client scripts read the four counts from the
  varbits and hide it outside the map area; the layer it opens on is the one guess (not seen in a client).
  A graph check over the cache collision with these links reaches all 362 spawns.
- **Saradomin ropes (2026-10-12):** tying the rock or climbing down needs 70 base Agility (Wiki: not boostable); climbing up is free. The cache's second bottom rope reuses the first rope's tied loc type (`godwars_rock_rope_bottom1`), so `GodWarsScript` registers that one type and tells the ropes apart by the clicked loc's coordinates (the second now lands on the middle level).
- **Not done:** the Ancient Prison / Nex (doors, forge and chest are inert), instanced chambers ("Open
  (private)" is never shown). The altars and the Ecumenical key were done later; see
  `isle_of_souls_godwars_plan.md`.
- Entrances sit in the free ground east of the older ones (x 2223-2239, z 2803-2809), chosen by
  collision clear of npc spawns. The tools know the new keys (`arrivals.py`, `reachexits.py`);
  `audit.py` reports nothing new. Not seen in a real client.

## The idea

Every dungeon already exists in the cache (maps, objects) and its monsters already exist in
`osrs-npc-spawns/underground.toml` with drop tables. So **no dungeon is rebuilt**. The work is:

1. a new entrance object on the isle that teleports a player into the real dungeon map;
2. making every way *out* of the dungeon (ladders, ropes, vines, stairs, cave exits) lead back to
   that isle entrance instead of the real surface, so nobody walks out onto the mainland;
3. making the dungeon's internal objects work (gates, stairs, shortcuts, hazards), because the repo
   has very few generic object scripts.

This is the Mining Guild approach (`miningguild/MiningGuildScript.kt`) done 14 times from one table.

## Design

**One table, one module.** New package `isleofsouls/dungeons/` with a `Dungeon` entry per dungeon:
key, name, isle entrance (object type, coords, angle), arrival tile inside the dungeon, and optional
requirement. All 14 entrances live in one file (`DungeonEntrances.kt`), so moving one later is
changing one line. The exit redirect reads the same entry, so **an exit always returns to wherever you
last put its entrance**. Nothing else needs touching when you move them.

**Test placement.** All 14 go in a row in the lobby plaza (near the bank and graveyards, x 2205-2216,
z 2845-2862). I will pick free tiles from the collision map, not by eye. A `::dungeon <key>` command
teleports straight in for testing.

**Exits (the part that can leak).** The cache has about 25 different exit object types, not one:
`ladder_from_cellar` (shared with Lumbridge's cellar), `fai_varrock_manhole_ladder`, `osf_ham_ladder`,
`karam_dungeon_exit`, `brimhaven_backdoor_exit`, `slayer_cave_exit_left/right`,
`slayer_cave_kraken_maincave_exit`, `wild_cave_exit_high/low`, `zeah_cata_exit_01-04`, `cata_exit`,
`mm2_cave_boss_exit`, `climbing_rope2` and so on. Three pieces:

- A table of every exit object in every dungeon rectangle, generated by `tools/dungeons/census.py`
  (not typed by hand).
- Each dungeon gets exactly one isle entrance, so every exit in that dungeon returns there. The few
  exits that are *inside* a dungeon group (Karamja Dungeon to Mor Ul Rek, Forthos to Catacombs) stay
  as internal links.
- **A leak test**: an integration test that walks the generated exit table and fails if any exit
  object has no handler. That is what stops a missed ladder dropping a player into real Varrock.

**One engine change.** `ladder_from_cellar` can only have one type-level handler, and the Mining Guild
already took it over. So I will change the generic `DungeonLadderScript` to publish a mutable
`DungeonLadderClimb` event (destination can be rewritten), the same pattern the skills use for relic
hooks, and move the Mining Guild's ladder onto it. The dungeon module subscribes too. Without this
the two scripts collide (the bank-chest "one handler per event" lesson).

**No instances.** Everyone shares one copy of each dungeon, like the rest of the isle. Everything is
already multi-combat, so crowding is fine. A per-team copy of the Catacombs would be enormous for no gain.

**What does not need work:** wilderness rules. The repo has none (no skulls, no wilderness level, no
PvP area), so the Revenant Caves and the Edgeville north half are safe as they are. Dying anywhere
respawns in the lobby as today.

## The 14 dungeons

"Where" is the map rectangle in the cache. "Surface entrance" is the real object I will copy so the
isle entrance looks right. Sizes: S = under an hour of work once the core exists, M = a session, L = more.

| # | Dungeon | Where *(cache)* | Surface entrance *(cache)* | Inside, needs work | Size |
|---|---|---|---|---|---|
| 1 | Taverley Dungeon | x 2816-3007, z 9728-9983 | `ladder_outside_to_underground`, 2884,3397 (arrive at the 2884,9797 ladder) | Pipe (`taverly_dungeon_pipe_sc`, 70 Agility), spike floor, loose railing, scorpion wall, two blue-dragon rock jumps, "Cave: Crawl" to the hellhound cave, metal gates, Heroes' Guild crevice. The rectangle also holds neighbours I should fence off or ignore (Death's Domain, Grim Tales basement, Warriors' Guild basement, Dwarven mine train). Cerberus lair hangs off the hellhound cave (see Bosses). | M |
| 2 | Varrock Sewers + Edgeville Dungeon | x 3070-3300, z 9790-9960 | Edgeville: trapdoor at 3097,3468 (arrive at ladder 3097,9867). Varrock: `manholeclosed` 3237,3458 (arrive at `fai_varrock_manhole_ladder` 3237,9858) | **Two entrances, one pipe.** The wiki says they join only through the 51 Agility obstacle pipe, which the isle already has as a shortcut copy. I would give each its own entrance and make the in-place pipe work (reusing the agility module). Metal gates, members' gate, Obor's gate (needs a giant key), Bryophyta's lair stays out. | M |
| 3 | Revenant Caves | x 3150-3270, z 10040-10240 | `wild_cave_entrance_high`, 3124,3831 (exit `wild_cave_exit_high` 3244,10215; the low one is `wild_cave_exit_low` 3218,10058) | **Revenants barely exist.** The source spawn list has only 15 of them, one per type, where the real cave holds a hundred-plus. Needs a hand-made revenant spawn list from the wiki, plus the real revenant mechanics (weakened-by-ether, bracelet) if you want them. Everything else is walking. | M-L |
| 4 | Stronghold Slayer Cave | x 2368-2495, z 9728-9855 | `slayer_cave_entrance`, 2428,3423 | Tunnel pairs (`slayer_cave_tunnel_*` "Enter", `slayer_cave_exit_*` "Use") are the main obstacle. The Eyeglo (Observatory-style quest) props in the west are inert. Monsters: aberrant spectres, bloodvelds, ankou, hellhounds, fire giants. | S |
| 5 | Brimhaven Dungeon | x 2560-2751, z 9344-9663 (the cache calls it `karam_dungeon_*`; the Yanille agility dungeon overlaps the box and is ignored) | Stairs at 2745,3152; arrive at `karam_dungeon_exit` 2714,9564 | The biggest obstacle set: stepping stones, vine climb, log balances, pipes (two), vine-blocking "Chop-down", crevices to the metal dragons, stairs between levels, gates and a back-door crevice. Real game charges 875 gp once; I would skip the fee (your call). Metal dragons are spawned. | L |
| 6 | Karamja Dungeon + Mor Ul Rek | Dungeon x 2816-2943, z 9536-9727; Mor Ul Rek x 2432-2559, z 5056-5183 | `volcano_entrance` ("Rocks: Climb-down"), 2856,3168. Mor Ul Rek is entered from inside, `tzhaar_karamjadungeon_wall_entrance` at 2863,9571 | One entrance covers both (they are physically joined). Mor Ul Rek's exit (2479,5176) already returns to the dungeon, so it needs nothing. TzHaar shops, forge and the pass-through "hot vent doors" need scripts. **Overlap:** the Fight Caves and Inferno entrance objects sit inside Mor Ul Rek (`tzhaar_fightcave_wall_entrance` 2437,5166; `inferno_entrance` 2493,5124). Our instances start from the isle today, so I would also wire those two to the existing instance code. | M |
| 7 | ~~Crandor~~ (dropped) | Dragon Slayer dungeon, same rectangle family: x 2816-2943, z 9536-9727; exit rope `dragon_slayer_qip_climbing_rope` 2833,9657 | `dragon_slayer_qip_ruin_entrance` ("Hole: Enter"), 2833,3255 | **Not joined to the Karamja Dungeon**, so its own entrance. Quest props (Melzar's maze doors, rock climb-over, Elvarg's mine tunnels, DS2 lab). Elvarg is spawned but is a quest boss, so I would leave her inert or remove her. | S-M |
| 8 | Kraken Cove | x 2240-2303, z 9984-10047 | `slayer_cave_kraken_maincave_entrance`, 2277,3611 (arrive at `slayer_cave_kraken_maincave_exit` 2276,9987) | The cave krakens spawn as **Whirlpool disguises** (25 of them) and nothing reveals them; a reveal script is required. The boss room is the `Crevice: Enter/Private/Peek` at 2280,10017 with one boss whirlpool and four tentacle whirlpools. The boss itself is a separate job (Bosses). | M (cove), M-L (boss) |
| 9 | Smoke Devil Dungeon | x 2304-2431, z 9408-9471 | `smokedevil_cave_entrance` ("Smoky cave"), 2411,3061 | Smoke devils (32) spawn fine. The real dungeon hurts you without a facemask or slayer helmet; I would add that (small). Boss crevice at 2378,9452 leads to the Thermonuclear smoke devil (Bosses). | S |
| 10 | Catacombs of Kourend | x 1600-1727, z 9984-10111 | Four surface entrances in Kourend; I will use one as the model (the cache name is not found by search, so I will find it from the six `zeah_cata_exit_*` vines on the way down) | Stepping-stone puzzle (nine `zeah_cata_stepstone`), vine exits (six, all must return to the isle), Skotizo altar. 88+ spawns per quarter, all `kourend_*` names. Joined to Forthos below. Skotizo's room is the Dark altar (needs the totem pieces; leave out). | M |
| 11 | Forthos Dungeon | x 1792-1855, z 9856-9983 | `hosdun_entrance_ladder` 1830,9974 and `hosdun_entrance_spiral_stairs` 1798,9967 are the *inside* ends; the surface is the Forthos ruin | Joined to the Catacombs through a passage (`cata_hole1_multi`, 1804,9967) *(wiki)*. Temple doors, Grubby Door (57 Thieving to pick), thick webs to Sarachnis (one spawned), slashable webs, lever, level-63 Agility shortcut in the library, undead druids (18), spiders (21), red dragons. | M |
| 12 | H.A.M. Hideout | x 3136-3199, z 9600-9663 | Trapdoor (`ham_multi_trapdoor`), 3166,3251 (Lumbridge) | `dttd_ham_trapdoor` inside, the locked prison door (Open/Hidden/Pick-lock), and the quest-style NPCs (Johanhus, Jimmy, Sigmund) with no dialogue. Combat is H.A.M. members and guards. | S |
| 13 | Slayer Tower Basement | x 3392-3455, z 9920-9983, **plane 3** *(cache)* | `slayer_tower_dungeon_laddertop`, 3417,3535 | The basement is on **plane 3**, not plane 0, so arrival must be plane 3 (exit ladder `slayer_tower_dungeon_ladderbottom` 3412,9931,3). Doors `slayertower_small_door` x4. Abyssal demons (14), nechryaels (12), gargoyles (12), bloodvelds. The tower above is not requested; entering the basement only. | S |
| 14 | Crash Site Cavern | x 2048-2175, z 5632-5695 | The Gnome Stronghold crash site, north of the Grand Tree *(wiki)*; the cache has `spiralstairs_wooden` near 2396,3501 as the nearest surface object | Tortured and demonic gorillas (38 spawned), Carado and Zooknock (post-boss NPCs, will be inert). The cave exit is a rope (`mm2_cave_boss_exit`, 2129,5647). Requires Monkey Madness II in the real game; gating it is not needed. Opening it is the whole job. | S |

## Phases

**Phase 0: core and all 14 entrances (one session).** Data model, entrance placement in the lobby row,
arrival tiles (checked against collision), the exit table generator, the `DungeonLadderClimb` event,
exit handlers, the leak test, `::dungeon`. Result: every dungeon can be entered and left. Dungeons
whose paths are already clear (Slayer Tower Basement, Smoke Devil, Crash Site, H.A.M., Stronghold
Slayer Cave) are playable immediately.

**Phase 1: a reachability audit.** A script that flood-fills each dungeon from its arrival tile using the
cache collision and lists the interactive objects that stand between regions. This is what turns the
tables above into an exact to-do list per dungeon, and it tells us if my "connected" claims (Karamja to
Mor Ul Rek, Catacombs to Forthos) hold in the cache.

**Phase 2: shared object handlers.** Add to `generic-locs`: metal and members' gates, prison and cell
doors, plain "Tunnel/Crevice/Cave" crawl-throughs, staircases and rope climbs. Add in-place
agility shortcuts (pipes, ledges, stepping stones, vines, log balances, monkeybars) by reusing the
agility module's `ObstacleRunner`. These are reused by several dungeons, so doing them once is cheaper.

**Phase 3: per-dungeon specifics, in this order:** Taverley, Brimhaven (largest), Karamja + Mor Ul Rek,
Edgeville/Varrock, Catacombs + Forthos, Kraken reveal, Smoke Devil facemask, Revenant spawns.

**Out of scope here (they plug into the entrances later):** Cerberus (Taverley's hellhound cave),
the Kraken boss, Thermonuclear smoke devil, Skotizo, Sarachnis and the other boss lairs. They are
already in the echo-boss plan; the entrances above are where they will hang.

## Decisions (settled 2026-10-09)

1. **Requirements:** keep the real *level* gates on in-dungeon obstacles (Agility 51 pipe, Thieving 57
   door and so on); drop fees, quests and keys.
2. **Karamja:** one entrance for the Karamja Dungeon and Mor Ul Rek. **No Crandor entrance** (its rope
   exit still returns to the Karamja entrance because the two maps are joined).
3. **Edgeville and Varrock:** two entrances, joined inside by the Agility 51 pipe.
4. **Revenants:** all of them (30, from the wiki's quantities).
5. **Crash Site Cavern entrance object** is `mm2_cavern_entrance` (id 28686, "Cavern entrance"). It
   stands in the Monkey Madness II crash site instance at 2026,5612, so it is only an object to copy.
   Its rope exit is `mm2_cave_boss_exit` (id 28687, 2129,5647).

## Risks

- About 23,300 NPCs are already spawned and their tick cost has never been measured; adding dungeons
  adds no NPCs but does add more places players can stand near them.
- Disguised monsters (kraken, zygomites) look like inert scenery until a reveal script exists.
- Nothing here has been seen in a real client. Entrance models in particular (some cave and ladder
  objects have fixed rotations) may need flipping.
- The census tool reads the cache directly; it is accurate for object positions, but "reachable"
  claims need the Phase 1 audit.
