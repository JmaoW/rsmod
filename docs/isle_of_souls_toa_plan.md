# Isle of Souls: Tombs of Amascut

Scoped 2026-10-22. Nothing built yet. Reference: tsps `docs/tombs-of-amascut.md` and
`server/plugins/minigames/TombsOfAmascut.plugin.js` + `toa/*.js` (24 files, about 14,000 lines, 1 test
file). TSPS's numbers come from Near-Reality and OpenRune with some wiki checks, so treat them as
facts to verify against the wiki, never as code to port (content plan 11.3). Everything is rewritten
in Kotlin in this repo's style.

## 1. Decisions (user, 2026-10-22)

1. **Full raid, built in playable stages.** All four paths, the Wardens and the invocations, but in
   milestones so a pair can finish a raid early. An event-settings key sets how many paths a run
   needs (1-4) so a short raid is available if time runs out.
2. **Packed copy per team.** Each raid gets its own large region (320x320) holding copies of every
   room, at repacked offsets, the way the Gauntlet maze works. Teams never see each other.
3. **Real party board.** The obelisk party list (772) and party details panel (774) are built, with
   applications, kicks and presets, so the lobby works as in the real game. The party is not
   restricted to a league team, but its size cap is an event setting.
4. **Loot: real wiki rates plus one league boost knob** on the unique chance, tuned after a client
   test. The 100-completions task is probably unreachable in a day and is flagged, not forced.

## 2. What already exists

- Cache (rev matches TSPS): 143 `toa_*` npcs, 1,171 `toa_*` locs, 78 `toa_*` objs, 148 varbits,
  interfaces 481, 482, 771-778, and all seqs and spotanims. Invocation data lives in cache structs
  (params 1159/1161/1162), supplies and books are objs 27214+.
- Map squares for the whole tombs block (x 3520-3967, z 5120-5439) are in the cache, with the
  teleport crystals, barriers, vault chests (8 slots), sarcophagus, doors and so on.
- Content already built here: Tumeken's shadow (`special-weapons/.../TumekensShadowWeapons.kt`,
  charging), Osmumten's fang and keris specials (`special-attacks`), the Amascut npc attribute,
  Masori/fang hooks in the combat formula collectors. Masori set bonus, Elidinis' ward and the
  fortified/Armadylean upgrades must be checked, not assumed.
- Instancing patterns: `TeamInstances` (small regions), the Gauntlet (composed rooms, per-zone
  rotation, `RegionRegistry.registerZone`, `Player.requestRegionRebuild`), team shared loot, the
  `WaveDirector` and `NpcAttack` infrastructure.

## 3. What is new work

Biggest to smallest, with TSPS line counts as a rough size guide:

| Part | TSPS lines | Notes |
| --- | --- | --- |
| Wardens (obelisk, core, final phase) | 1,701 + 471 data | Largest fight, three phases, floor collapse |
| Raid state (instance, scaling, points, deaths, HUD) | 1,316 + 537 shared | The base every room uses |
| Zebak | 1,086 | Water, rocks, volleys, tidal waves |
| Akkha + Het puzzle | 822 + 777 | Beam/mirror puzzle, orb paths |
| Lobby + party board | 798 + 221 | Interfaces 772/774/776, presets |
| Ba-Ba + Apmeken | 764 + 529 | Boulders, sight, corruption special |
| Kephri + Scabaras | 752 + 737 | Four puzzle variants, eggs |
| Crondis | 577 | Palm watering, crocodiles |
| Nexus, supplies, rewards, loot, items, invocations, commands | about 2,400 | Chest 771, bag 778, supplies 777 |

Expect roughly 12,000-15,000 lines of Kotlin plus tests. This is several days of work, done in stages.

## 4. Engine risks to probe before building

- **Packed region.** Fit about 11 rooms (each at most about 60x60) into one `createLarge` region.
  Needs a real-to-instance translation, so no code may hard-code a real coordinate. Check that
  multi-level rooms (Akkha z1, Wardens z1) keep their planes, and that locs, collision, map
  multiway flags and map-owned entrances copy correctly. The Gauntlet already proved runtime zone
  add and `requestRegionRebuild`, so this is a bigger version of that.
- **Interfaces 772/774/776/771/777/778.** TSPS decoded the 774 button layout and the invocation
  enum; the rest need decoding against this client, like the slayer and collection log screens.
  Cs2 patches may be needed (see [[project-fkeys]] style patches).
- **Many NPC movements** (orbs, boulders, waves) with collision off; check how this engine moves
  npcs without pathfinding (Fight Caves notes).
- **Tick cost** of a 320x320 region with many projectiles per raid; unmeasured.

## 5. Milestones

| M | Content | Done when |
| --- | --- | --- |
| 0 (done) | Probe: packed large region, translation, plane handling, scene resend | A test enters the packed region and walks between rooms |
| 1 (done) | Shell: lobby, party board, invocations, raid level and scaling, HUD, points, deaths and ghosts, Nexus and path choice, exits, vault and reward chest (771), task hooks | An empty raid can be entered, scored and exited with loot |
| 2 (done) | Het + Akkha vertical slice, Tumeken's shadow integration | A pair can finish one path end to end |
| 3 (done) | Apmeken + Ba-Ba, Crondis + Zebak, Scabaras + Kephri | All four paths complete |
| 4 (done) | The Wardens, all three phases | A full raid can be won |
| 5 (done) | Supplies, invocation punishments, item effects (Masori, ward, fang), polish, docs, tests | Event-ready, client checklist written |

Each milestone ends with sandbox tests (per `feedback-testing-and-process-safety`), a doc section and
a list of things the user must check in a real client.

## 6. League hooks

Tasks to wire: 1186 (complete once), 1187/1188/1189 (25/50/100), 1228 (Elidinis ward), 1230/1231
(Masori piece / full set), 1232/1233 (Osmumten's fang / (or)), 1236 (Tumeken's shadow). Task 1426
(ToA Combat Achievements) stays out, since there is no Combat Achievements system. Task 890 is the
unrelated Nardah statuette. The task text says "normal or Expert", so Entry-mode completions
(raid level 0-149) must not count toward 1186-1189 (TSPS ranges: Normal 150-299, Expert 300+).

## 7. Open questions, answered and remaining (user, 2026-10-22)

- **Party size cap:** 8, as the board supports.
- **Boost default:** decided later, tuned after a client test.
- **Lobby entrance:** the real lobby is used (Jaltevas pyramid beneath Necropolis, x 3340-3377, z 9100-9132), but the real door is far too big to fit on the isle. We need our own way in, such as a small portal or entrance on the isle that teleports to the lobby, as the Gauntlet does. Exit from the lobby returns to that entrance. Where it goes and what it looks like is still to pick (milestone 1).
- **Failed raid:** items are kept, like other deaths on this server. No retrieval chest or fee.

## 8. Milestone 0 as built (2026-10-22)

Code is in `isleofsouls/toa/`: `ToaPacking` (the layout and the real-to-region maths), `ToaConfigs`
(loc references, to grow), `ToaProbe` (`::instance toa` and `::toatp <room>` for a client check).
Tests: `ToaPackingTest` (5 unit) and `ToaRegionProbeTest` (6 integration), all pass in a sandbox
copy of `.data`. A sandbox server start loads all 297 scripts (only the port bind failed, because
the real server is up).

**What the real map holds** (read with the census tools): only 12 map squares are part of the tombs:
columns 55, 57, 59, 61 by rows 80, 82, 84, plus a sliver of square 54_82 (x 3516-3519, a few locs of
the Scabaras puzzle room). Columns 56, 58, 60 and rows 81, 83 are empty. The square 60-62 by row 78
is unrelated Wilderness-style terrain. Each path is one column: Scabaras, Het, Apmeken, Crondis, with
the Nexus, vault and Wardens along row 80 and each path's puzzle (row 82) and boss (row 84) above.

**The packing.** One large region (40x40 zones). The four columns are copied whole, on all four
levels, with a two-zone gap (16 tiles) between them: region zone x 0-8 (Scabaras, from real zone 439
so the x 3512-3519 sliver is included), 11-18 (Het), 21-28 (Apmeken), 31-38 (Crondis). Rows are
copied unchanged, so real z maps to `southWest.z + realZ - 5120`, and only x depends on the column
(`ToaPacking.toRegion` / `toReal`; the engine's `region.normal[real]` gives the same answer, which
the tests assert). 33 x 40 x 4 zones are registered per raid. The gaps stay blank so a room never
sees another path's room; the lobby is not part of the region (it stays at its real place).

**Engine facts found:**
- A region nobody stands in is reclaimed the moment any other region is created. Flag the entry
  zone with `ZonePlayerActivityBitSet.flag` straight after moving someone in, as `TeamInstances`
  already does (so two raids built in one tick need this, or the first vanishes).
- Building a packed region costs about 11 ms (5 in 57 ms), so it can be done on demand.
- Large regions: 420 slots, so concurrent raids are not a limit. Freed slots are not reused lowest
  first.
- Loc and collision copies work through `registerZone` as for any instance: wall flags and map locs
  at real tiles appear at the translated tile, and deleting a loc in one raid leaves the other.
- `combat_multiway_everywhere` is true here, so the region having no multiway area flag does not
  matter.
- `TeamInstances` already hosts a packed template (`TeamInstanceType` with a real-coordinate entry),
  so M1 can reuse it or a ToA-specific manager keyed by party.
- `RspCycle` already builds a region scene from any region (large included) with the xteas of the
  real squares; all 13 squares involved have keys in `xteas.json`.

**Not verified (needs a client or a real map):** that the client draws each copied room correctly
and nothing from a neighbouring column shows through the 16-tile gaps; real collision after copying
(the test world has no map, so walls were put in by hand); NPC and player visibility across a gap.
To check: run `::instance toa`, then `::toatp nexus|vault|scabaras|kephri|het|akkha|apmeken|baba|
crondis|zebak|wardens|wardens3` and walk about, looking at the room edges. If something shows through
a gap, the gaps cannot simply grow: 39 of the 40 zone columns are in use. The fallback is to use
the empty rows (81 and 83 hold nothing) to stack the columns in two bands instead of one.

## 9. Milestone 1 as built (2026-10-23)

The shell of the raid, in `isleofsouls/toa/`. Playable end to end with the admin commands (no room has
content yet): form a party, enter, choose paths, die, clear paths, take the Wardens, collect the loot.

**Files.** `ToaInvocations` (the board, from the cache), `ToaScaling`, `ToaPoints`, `ToaLootMath`
(pure rules), `ToaParties` + `ToaBoard` (the lobby party model and the 772/774 screens), `ToaRaid` +
`ToaRaids` (a raid and the manager), `ToaHud` (interface 481), `ToaRewards` (rolls and interface 771),
`ToaLobbyScript`, `ToaVaultScript`, `ToaGearTasks`, `ToaCommands`, `ToaSettings`, `ToaRooms`,
`ToaEvents` (what room scripts listen to), `ToaConfigs` (references).

**Decisions made while building.**
- **Invocations are read from the cache**, not a table in code: enum 4664 lists the 46 structs in board
  order and each holds its name (param 1160), bit (1159), category (1161), level (1162), the number it
  means (1299: attempts, a time in ticks, a percentage or a path level) and the invocation it needs
  (1346). Blazing Tombs I and II (the last two) are not offered. Missing the target time costs twice
  the invocation's level (wiki: 20, 30, 40, 50), where TSPS had half that.
- **The HUD's path numbers are Scabaras 1, Het 2, Apmeken 3, Crondis 4, Wardens 5 and 6**, read from
  the HUD script's switch; TSPS had Apmeken first, which would have shown the wrong path and levels.
  The path-level varbits are named for their paths in the cache and used by name.
- **Pause buttons keep their interface open.** The engine closed an interface and resumed the player's
  coroutine on every `resume_pausebutton`; the party board needs many clicks, so
  `ResumePauseButtonHandler` now publishes `IfPauseButton` first and only does the old thing when no
  script claims the click (`onIfPauseButton`).
- **Completions** use the collection log's own counters (`clog_kc_tombs_of_amascut_*`), so the log and
  the party panel agree. Attempts and deaths per mode are three new varps each (`isle_toa_attempts_*`,
  `isle_toa_deaths_*`). The reward inventory `toa_chests` (811) is set to be saved with the player
  (`ToaInvEdits`), so loot waits in the lobby chest across logouts.
- **Loot** follows the wiki's chest page: unique chance, weights by raid level, three common rolls or
  fossilised dung under 1,500 points, thread, jewels, pet and clue each on their own. The unique goes to
  a player weighted by their share of the points. Items are found by display name, as the drop tables
  do. `toa.uniqueMultiplier` in the event file multiplies the unique chance.
- **Event file:** `"toa": { "pathsRequired": 1-4, "uniqueMultiplier": x }`.
- **The entrance** is the pyramid's small stair door (`toa_entrance_open`, "Enter", 2x2) set in the
  clearing east of the Gauntlet's portal at (2129, 2891), facing south; the exit puts a player at
  (2129, 2889). The real Necropolis door uses the same loc, so it works there too.
- **Tasks** 1186-1189 (complete 1/25/50/100, Normal and Expert only) and the equip tasks 1228, 1230,
  1231, 1232, 1233, 1236 are in the league list. Task 1426 (Combat Achievements) stays out.
- **Death:** items are kept (this server has no item loss). In a puzzle, boss or Wardens room a dead
  player respawns at the room's start as a ghost (the `ghost` npc) until the room is won; when everyone
  in the room is a ghost the team has wiped: an attempt is spent (if the invocations limit them), the
  room is told to reset (`ToaEvents.RoomReset`) and everyone is put back at its start; with none left
  the raid fails and everyone goes to the lobby.

**Tests.** 33 unit tests (invocation rules, scaling against OpenRune's captured Zebak hitpoints,
points, loot maths against the wiki's example) and 24 integration tests (the cache reads, the party
board, entering, paths, Walk the Path, deaths and wipes, the Wardens, the vault and the chest, the
settings), plus the 6 from M0. The room scripts, the tick and the loot roll are run by hand in the
tests, because plugin scripts are not loaded there; a sandbox server start loads all 300 scripts with no
duplicate registration.

**Not built (left for later milestones):**
- Nothing happens inside the rooms: the path doors only move the party in. Puzzles, bosses and the
  Wardens call `completeRoom` / `completePath` / `advanceWardens` / `wardensDefeated` when won.
- Ghosts only look and are tracked: attacking, eating, teleporting and equipping are not blocked yet, and
  monsters do not ignore them (needs monsters first, M2).
- The helpful spirit's supplies (interfaces 777/778) and removing raid items on leaving: M5.
- Osmumten's sarcophagus ceremony (the unique is simply in the finder's chest; the flames still turn
  purple), the lobby scoreboard (775), the shroud chest, the sack and the camel.
- The party panel's "set preferred completions" prompt, the "clear all" and "discard" confirmations
  other than discard, the friends filter, and Osmumten's reward-potential text.
- A player's invocation board is remembered for the length of the server run only; presets are saved.
- Raids do not survive a logout; the region's login hook puts a player who was saved inside one on the
  isle.

**Client checklist (nothing here has been seen in a client).**
1. Walk through the entrance on the isle; check the lobby overlay (names list) and that the exit works.
2. Inspect the grouping obelisk: the list (772) fills, My Party makes a party, the panel (774) opens
   with the right tab; invocations toggle and the raid level and mode update; presets save and load;
   a second player can apply, be accepted, declined and kicked, and sees the same panel.
3. Enter the tombs: HUD with the party names and health orbs, raid level, path levels; Nexus doors
   (selected path dims the others); `::toaskip` and `::toawin 30000 400` to reach the vault.
4. In the vault, your chest shows as yours, opens interface 771 (Take, Take-5, Take-10, Take-All, bank
   all, inventory all, discard), the chest empties afterwards.
5. Dying inside a path room: ghost look, wipe message, room start.


## 10. Milestone 2 as built (2026-10-23): shared room scaffolding, Path of Het, Akkha

**Take-all (reported after M1).** Could not be reproduced on the server: `take`, `takeAll`, the whole
click path (`chests_items` Op2/Op4, `chests_inventory`) and a full roll of notes, uniques and repeats
all move whole stacks, and three regression tests cover it. If it still shows after `packCache`, note
exactly which button (Take-All under the item, or the bottom-left "Take-all") and what was in the chest.

**Shared scaffolding** (`ToaRoomRuntime`, `ToaRoomRegistry`, `ToaRoomDirector`, `ToaPathScript`).
- A room's content is a `ToaRoomRuntime` (build, start, tick, complete, reset, destroy). Content scripts
  register a factory per room (`rooms.register(ToaRoom.X) { env, raid -> ... }`); the director builds
  the runtime when the first player walks in, ticks all of them, resets on a wipe and removes everything
  when the raid ends. Everything a runtime spawns (npcs, locs) is tracked and removed with it.
- All coordinates in room code are the real tombs coordinates; the packed region translates them.
- Damage on a room npc goes through `modifyHit` before it counts for points, so an immune boss gives no
  points. `Npc.attackRollPercent` scales a room npc's attack rolls with the raid level.
- `ToaPathScript`: the barrier into a puzzle (Pass / Quick-Pass, no leaving once started), the continue
  door to the boss, the boss crystal, Osmumten (takes a won room home), and the exit doors.
- All ToA npcs are tagged `amascutnpc` (`npc_attributes.toml`), so players' rolls against them scale
  with the raid level.

**Path of Het** (`HetPuzzle.kt`, `HetScript.kt`, `HetBeam.kt`, `HetData.kt`). The seal (wiki health by
party size) is protected until the beam from the caster statue is bounced off mirrors onto the shielded
statue (checked every 9 ticks); then walls and mirrors drop away for 15 ticks while the party mines the
seal (pickaxe tier and Mining level set the damage, 2.5 room points a point), and a new layout rises
afterwards. Orbs roll across the floor throughout. Mirrors: Pick-up, Rotate (both ways), Push, Clean,
and "Place" from the inventory; breakable walls: Break (25 xp); seal: Destroy (35 xp a swing). The
wall cavities keep one pickaxe per player (varbit `toa_pickaxe_stored`, the cache's own varp 3635) and
give a bronze pickaxe when empty. The five layouts and the orb paths were read off the real game.

**Akkha** (`Akkha.kt`, `AkkhaData.kt`, `AkkhaScript.kt`). Forms (melee, ranged, magic) each hurt by one
style only; every 5-7 swings a special: memory blast (quadrants glow, then all but the lit one are
blasted), orb trail, or detonation (a plus of three tiles around each player, group only). Shadows at
80/60/40/20 percent blast their quadrant every 50 ticks and shield Akkha while he stands in a quadrant
whose shadow lives; one shadow can be dispelled a wave. At zero he makes a final stand as four shadows:
the real one takes melee only, swaps place with a fake after `2 + party size` hits, and unstable orbs
roll across the arena. The four elements (darkness damage over time, lightning drops protection
prayers, ice slows attacks a tick, fire burns and spreads) apply as in the wiki. Bosses never die from
a hit: it leaves one hitpoint and the room's tick takes the next step.

**Guesses and gaps (all flagged in code):** the timers, the max hits (22 style, 14 quadrant, 13 trail
and unstable, 15 detonation) and the blast order are the read-off values; prayer percentages through
protection (25 melee, 15 ranged and magic) are too; the lightning only switches the protection prayers
off (no short lockout); Akkha has no boss health bar, no Het's capture or eldritch ashes drop, and no
sounds; his "vanish" hides the npc through `NpcRepository.hide`; the final shadows all use the normal
shadow npc so that the real one cannot be told apart (the cache has special enrage shadow npcs that may
look different); a bronze pickaxe from the cavity is not removed on leaving.

**Tests.** `HetBeamTest` (8 unit), `HetRoomTest` (11) and `AkkhaRoomTest` (14) integration; the Akkha
tests turn the boss's own swinging off (`fightEnabled`) and load `PlayerHitScript` so that hits land.
The test world has no map, so the arena floor is cleared by hand.

**Client checklist (nothing here has been seen in a client).**
1. Het: the barrier, Quick-Pass, walls rising, mirror ops, the beam visuals along the row (check each
   spotanim shows in the right direction), the weakened seal text, Destroy swings, the wall cavities.
2. Akkha: forms change model, shadows and quadrants visible, the lit symbols during the memory blast,
   Akkha hides and returns, the final stand, rolling orbs; does he walk (npc `walk` with mode None).

**M2 changes after the first client look (2026-10-23).**
- The Het seal takes pickaxe damage as real hits: health bar and hitsplats (`damageSeal` queues an npc
  hit; `modifyHit` leaves one hitpoint and the room's tick wins). The beam also stops at the edge of
  the room instead of running out of the region.
- Akkha prays against one style only (the one his form attacks with; the overhead was already right).
  Before: two styles were blocked. In the final stand the real one takes melee only.
- Akkha and his shadows have the idle animation as their block animation (no flinch); needs
  `packCache` (`AkkhaEdits`).
- Akkha is built when the party enters the boss room and wanders the arena (`onIdle`) until the crystal
  starts the fight, then targets the closest player.
- Shading: the cache's shadow npcs use the shaded model, so Akkha is shown as `akkha_shadow` while he
  stands in a quadrant whose shadow lives and as his form otherwise; shadows are all `akkha_shadow`.
  In the final stand the real one is `akkha_enrage` (full colour, both protection icons) and the others
  `akkha_shadow_enrage`; a swap transmogs the two.
- Unstable orbs and the other orbs no longer block each other (`blockWalk` none), roll on until they
  stop moving (a wall) or leave the arena, then burst and go.
- Bosses and the seal now also survive an overkill that lands as hitpoints 0: a no-op death queue
  records the npc as down (`hitpointsGone`).


## 11. Milestones 3, 4 and 5 as built (2026-10-24)

All four paths, the Wardens and the raid's supplies are in. 102 ToA integration tests (52 new) and
41 unit tests pass in a sandbox copy of `.data`; a sandbox server start loads all 313 scripts with no
clashes. Nothing here has been seen in a client yet. **Needs `packCache`** (6 new projanims in
`projanim.sym`, the swim animation set `toa_swim` in `bas.sym`).

**Shared room code (`ToaRoomRuntime`).**
- `ToaMonster` + `driveMonsters()`: the rooms move and swing for their monsters (straight steps,
  melee beside not on a corner, a range for the rest); `swing` is overridden for special monsters.
  `toaMonsterDeaths` gives each monster type its death animation (played in the script, so no cache
  edit) and tells the room; `toaBosses` keeps a boss from dying of a hit (the room takes the step).
- The boss health bar: `bossBar = npc` shows the client's boss overlay (varp `hpbar_hud_npc` is the
  npc type, varbits `hpbar_hud_hp`/`basehp`) to everyone in the room. Every boss, the obelisk and
  the palm use it. This closes the M2 gap.
- `swapLoc`/`restoreLocs` change the map's own locs (pillars, platforms, sarcophagi) and put them
  back; `privateGraphic` is a map graphic only one player sees (new engine feature:
  `WorldRepository.spotanimMapFor`, a private `MapAnim` filtered per observer like private objs),
  used by Apmeken's Sight; `knockback`, `projectile` between two tiles, `animLoc`, `hurtFlat`.
- Bosses hand out their book once per player (varbits `toa_book_*`) and their trophy to the top
  damage dealer (big banana, fang, Eldritch ashes, scarab dung).

**Path of Apmeken** (`ApmekenPuzzle.kt`, `ApmekenScript.kt`). The wiki's 8 waves (one extra combat
baboon in a group), Apmeken's Sight passing between players (the first player in starts with it; the
HUD's `toa_client_primary*` marks the holder), vents, roof supports and (in groups) corruption, the
hammer and potion crates, venom pools, shamans summoning thralls and draining prayer, volatile
baboons exploding (and killing baboons near them), cursed baboons wandering. The weak style always
lands the player's max hit (read from varp `com_maxhit`). Completion 450 points (OpenRune).
**Ba-Ba** (`BaBa.kt`): melee at the lowest-health player (fully blocked by prayer), the slam (fast
when stood under), rockfall and the thrown rock (beside debris, a closed sarcophagus or a baboon it
does nothing, and the cover pays 10/30/12), baboons from 75% that break sarcophagi open (which then
spit skulls), the boulder phases at 66% and 33% (the wiki's boulder health table, the cracked one
takes ranged max hits, melee on her ends it), Mind the Gap, Gotta Have Faith, Jungle Japes peels,
Shaking Things Up, Boulderdash.

**Path of Crondis** (`CrondisPuzzle.kt`, `CrondisScript.kt`). Each player's two containers on the
floor (private ground items), waterfalls that run dry for a while, the palm (175 + 125 a player) that
grows in five stages, acid statues and spear rows on TSPS's rhythms (a hit spills half the water and
takes 6 Defence, 3 Agility, 2 Prayer), crocodiles from one of three sides that drink from a watered
palm or bite (18, +3 a recent trap hit, up to 36; a third through prayer for 12 Prayer). Completion
400 points. **Zebak** (`Zebak.kt`): split volleys, the bite (prayer halves it) and its bleed, the
roar with rocks (150 health, 50 a roar), jugs (push, pull, hit) that wash acid away, the tidal waves
(holes close with path level) that wash players into the water (they swim: walk only, no attacks,
crocodiles bite, out by the rock steps), the enrage at 25%, Not Just a Head (barrage heals him
twice its damage, blood clouds), Arterial Spray, Blood Thinners, Upset Stomach, his Defence floor.

**Path of Scabaras** (`ScabarasPuzzle.kt`, `ScabarasScript.kt`). The four chambers in a random
order (lights out with the 20-damage flip, the 20-45 sum and its tablet, the obelisk order, the
memory plates), each east chamber waiting for the west one on the other half, fire gates, the
matching boards (four pairs shown alone, wiki), the scarabs, the crawl passage between the halves.
Plates are noticed each tick from where a player stands (and the tile a run went over). Completion
300 points. **Kephri** (`Kephri.kt`): fireballs on players' tiles (24 max, a third through prayer),
eggs that burst or hatch agile scarabs, the dung strike and its trail, two dazed intermissions with
the wiki's swarm schedule (28 then 24 swarms) healing her up to 115%, the overlords (spitting, then
soldier and arcane), the final phase (80 health) and Aerial Assault, Medic!, Lively Larvae, More
Overlords, Blowing Mud.

**The Wardens** (`Wardens.kt`, `WardensFinal.kt`, `WardensData.kt` generated from TSPS's tile
tables, `WardensScript.kt`). Osmumten's Begin (everyone ready, or two minutes after the first), the
obelisk phase with orbs to the platforms (3 to a player who blocks one), blades and energy balls in
turn; the more charged Warden fights (prays melee and one style, 100% accuracy on the shield), the
core out for 21/29/37 ticks taking melee max hits passed five-fold, divine throws above 55% and
entombing below, the obelisk's beams, skull bombs and wheel. The final room: floor slams, siphon
skulls at 80/60/40/20% (melee only), the phantom pairs, the 5% enrage (heals 20%) with the floor
falling away and red lightning, then the teleport crystal to the vault. A wipe in the final room
sends the party back to the obelisk. Ancient Haste, Acceleration, Penetration, Overclocked (1, 2),
Insanity, Stay Vigilant and Aerial Assault are used.

**Supplies** (`ToaSupplies.kt`, `ToaSuppliesScript.kt`). The helpful spirit comes after the second
and fourth paths with the Life, Chaos and Power bundles (interface 777, inventories 807-809; the help
invocations scale them, one of each kept), the supplies bag (interface 778 beside the inventory,
inventory 810: Open, Withdraw 1, Withdraw All, Resupply, using a supply on it), tears (and the
ally beside), smelling salts, the blessed crystal scarab, liquid adrenaline, silk dressings; nectar
and ambrosia come from herblore (nectar's drain fixed to the wiki: a twentieth plus 5, Defence too).
Honey locusts are handed out on a failed room. Everything the raid gave is taken away on leaving (and
on logging in outside a raid).

**Raid-wide rules.** New engine hooks: `PvnRules` (a player may not attack a npc), `EatRules`,
`DrinkRules`, `SpecialEnergyCosts`. ToA uses them for: ghosts (no attacking, eating or drinking),
swimmers and the entombed (no attacking), On a Diet (no food), Dehydration (no healing potions),
raid supplies only inside the raid, Overly Draining (specials cost 100%) and liquid adrenaline
(half). Deadly Prayers drains a fifth of the damage taken from prayer. Akkha's lightning now keeps
protection prayers off for 5 ticks.

**Items** (`ToaItems.kt`): Masori fortified with Armadylean plates and plates chiselled from Armadyl
armour, the arcane sigil on Elidinis' ward, the Menaphite kit, the cursed phalanx, the keris jewels
(each with its Dismantle), the Thread of Elidinis on a rune pouch. Tumeken's shadow (x4), the fang
and the keris of the sun already had their raid bonuses in the combat formulas.

**Admin:** `::toaroom` wins the room you stand in (puzzle, boss or either Wardens room) the normal way.

### Guesses and choices to review (flagged in the code too)

- Apmeken: large baboons from wave 5; which extra baboon a group gets; the missed-fix damage is 22
  times the share missed; thralls drain half their damage from prayer; a volatile's blast follows its
  remaining health (base 14); poisoning a shaman does not stop its summons yet.
- Ba-Ba: 2-4 baboons as 2 + (party - 1) / 2; the sarcophagus spread grows 2 tiles every 2 path
  levels; her Defence drain floor (60) is not enforced; the peel stun is 5 ticks.
- Crondis: crocodiles go for a palm with water first, then a carrier within 3 tiles, then whoever
  hit them (the wiki's order; the sight range is a guess); the containers never become visible to
  others.
- Zebak: acid ramps 2 a tick while stood in (the wiki only says it spikes); the swim uses a new
  animation set.
- Scabaras: the statue between the matching boards stays where the map has it (TSPS moved it for
  groups; the wiki's wording is unclear); the platform shortcut is not in this map (only the passage).
- Kephri: TSPS's 8 swarm spawns (the wiki says 6); a swarm that cannot reach her is given up after 20
  ticks; the arcane scarab moves after 2 + party size solid hits or one of 40.
- Wardens: the phase-two shield is the cache's 140 scaled; the exposed health is the wiki's 4,500
  scaled by raid level and party size (the wiki says it grows for groups but gives no formula); the
  phase-two max hit is the wiki's 20; breaking every siphon takes 5% of the final Warden's health
  (TSPS).
- Supplies: honey locusts 3 (5 after a Wardens room), Expert 2 (3); one raid supply every 3 ticks on
  its own timer; no warning when leaving the Nexus with an unclaimed bundle.
- The bronze pickaxe from a Het cavity is still kept on leaving; ghosts can still teleport and
  change equipment.

### Client checklist

1. `packCache`, then a raid with `::toaroom` to step through rooms if needed.
2. Apmeken: waves spawn and fight, the sight's HUD mark, the warning skulls only for the sighted
   player, pillars shake, venom pools, crates. Ba-Ba: the boss bar, shadows of the slam, rockfall,
   cover, the knock-back and boulders rolling (do they animate while walking?), sarcophagi opening.
3. Crondis: containers on the floor for each player only, dry waterfalls, the acid clouds' graphics
   line up with the damage, spear animations, crocodile openings, the palm growing. Zebak: volley
   burst at the peak (helper npc), rocks/jugs/acid, waves, swimming animation, rock steps.
4. Scabaras: plates show (ground decor), lights, sum glows, the obelisks light, the memory sequence,
   matching boards, fire gates. Kephri: telegraphs, eggs, dung, swarms, the dazed look, overlords.
5. Wardens: Osmumten's Begin, platforms filling, orbs, blades, balls; the Warden rising and walking
   in, the core, the obelisk's beams/skulls/wheel; the final room's slams, siphons, phantoms, the
   floor collapsing, the crystal.
6. Supplies: the spirit's bundles screen (777), the bag panel (778) and its options, each supply.
7. Items: each upgrade and dismantle.
8. Animations (fixed 2026-10-23 after a client report, see below): monsters hit while idle or
   walking no longer twist or stall, baboons and scarabs finish dying before they vanish, players
   pushed back face the way they came, and Het's mirrors, cavities and rising walls animate.

### Animation fixes (2026-10-23)

The cache has no block animation for any ToA monster (only the Crondis crocodile has one), so they
all flinched with the `defend_anim` default `human_unarmedblock`. Played on their keyframe rigs it
twisted the model, and it held up a walking monster for about 2 ticks. The fix is engine-wide:
`Npc.combatPlayDefendAnim` now plays nothing when `defend_anim` equals the npc's ready animation, or
when the npc has no ready animation, and `tools/npc-anims/derive_anims.py` gives every attackable npc
that has no block seq and a non-human rig (skeleton read from the cache) its idle (775 npcs, ToA
included). Also: `toaMonsterDeaths`, Osmumten leaving and Kephri's swarm fade wait for the seq's real
length (`ToaRoomRuntime.ticksOf`); `knockback` faces back along the push unless told otherwise; Het
plays pick-up, push, take and slide animations (ids from TSPS). Tests: `ToaAnimTest`. Needs
`packCache`.

### Wiki review fixes (2026-10-29)

A review against the wiki (Strategies, Akkha, Kephri, Zebak, Ba-Ba pages) and TSPS. 111 ToA
integration tests pass. **Needs `packCache`** (Zebak wave edit, 60 new `toa_*` names in `synth.sym`).

- **Collision.** Players put "block npcs" on their tile, so rolling things stopped in front of the
  player they should hit. New engine strategy `CollisionStrategy.IgnoreEntities` (walls and locs
  still block) and `spawn(passThrough = true)`: Akkha's unstable orbs, Het's orbs, the Wardens' P1
  energy orbs, Ba-Ba's boulders, Kephri's swarms. Zebak's waves get `blockWalk = None` (`ZebakEdits`).
- **Akkha.** A special every 7th attack (6th with Stay Vigilant), a style change after every special;
  Stay Vigilant 1/4 after an attack, 1/5 after a special, two attacks a style at least; a style change
  after 100 ticks without a swing; he keeps one target and only re-picks after a special (closest to
  his centre, then highest defence bonus, bulwark x1.5, then party order). Darkness drains every combat
  stat by 3; fire burns for 5; unstable orbs share the hit on a tile, Protect from Magic takes 25%,
  capped at 25. Kept TSPS's "max + 0-2" for his special damage (captured data).
- **Kephri.** No dung or eggs in the final phase; six swarm spawns N, NE, SE, S, SW, NW (the N and S
  tiles are a guess between TSPS's two); agile scarabs back off from players within 2 tiles; the shield
  spotanims `kephri_shield_alive`/`_dead` loop while shielded/dazed (check in a client).
- **Ba-Ba.** The slam lands in front of her towards her target (8 directions); a thrown rock that
  breaks debris brings the rockfall again.
- **Wardens.** A miss on the P2 shield becomes a hit of 1 to max hit (the real accuracy scaling is
  not published); the shield regains 5% every 5 ticks after 10 ticks untouched (guess); broken
  siphons fly into the Warden.
- **Het.** The shield statue flash is spotanim 732, not the rockfall.
- **Sounds.** Every TSPS sound in the bosses and puzzle rooms (`ToaSynths`, room helper `sound`).
  Lobby/board sounds are still silent.

### Playtest fixes (2026-10-29, after a client run)

All 117 ToA integration tests pass (3 new in `ToaPlaytestFixesTest`, 2 new in `ToaRaidTest`), plus 2
new venom tests in `PvpPoisonTest`. No new symbols, so no `packCache` needed for these.

- **Boss drops.** Books and trophies go on the floor in front of Osmumten (he faces the arena's
  arrival side), each seen only by the player it is for, gone with the raid. Osmumten plays
  `ghost_summon2_priority` when he appears. Akkha's Osmumten moved to the west side (3673, 5407).
- **Fades.** `ToaRaids.fadeMove`: fade to black (interface 174, cs2 948, 15 cycles), the player is
  delayed 2 ticks, moved, faded back in. Used for path doors, Osmumten home, the boss door, the
  Wardens door, the final phase and the vault. Entering from the lobby and leaving start black and
  fade in (the move happens at once there, because a region nobody stands in can be reclaimed).
  Tests set `fadeTicks = 0`.
- **Wardens final phase** puts the party on the floor (`place(onFloor = true)`, by the challenge
  tile); the arrival tiles at the back stay the death respawn.
- **Sickness.** Ambrosia cures poison and venom (`SicknessTreatment.treat`, 1200 ticks antipoison).
  Venom cures any poison when it lands, and nothing poisons an envenomed player.
- **Boss bars.** Akkha (and the real final-stand Akkha) and the Het seal now set `bossBar`. The bar
  is coloured with `if_setcolour` (captured 25600/576/800) when shown; Kephri's is the captured shield
  blue (132/623/853) until her final phase, redrawn with cs2 2102. New `Player.ifSetColour` in
  api/player. Root cause of "no bar on any boss" was not found server-side (the palm path is the same
  code): **check in a client**.
- **Akkha** walks a tile every 2 ticks (fight and pre-fight wander) and faces his target; the memory
  blast hurts the moment it goes off (graphics start at once; shadows' sweeps keep their timing).
- **Crondis crocodiles** step every 2 ticks (`ToaMonster.stepTicks`, `ToaRoomRuntime.step`).
- **Scabaras.** The stepping stone (loc 45396) "Jump-to" works from either half (ap range 4): onto the
  stone, then 2 ticks later on to the far side (`human_spot_jump`, tiles from TSPS). The memory
  plates now swap the floor plate itself (GroundDecor) instead of laying the lit plate over the
  raised one, which hid it (guess at the cause of the bad highlight; check in a client).
- **Kephri.** The overlords follow the wiki (first down: spitting scarab; second: soldier and arcane),
  unchanged; if the ranger is still missing at the first down in a client, look at its spawn tile.
- **Volatile baboons** burst when killed too (full damage, kills baboons beside it, so chains go off),
  and burst 2 ticks after reaching a player instead of 4.
- **Ba-Ba** cannot be hit below 66% or 33% until that boulder phase has begun.
- **Wardens.** The obelisk's active forms (and every other hittable Warden form) are registered as
  raid bosses, so the engine no longer has it fight back or chase. Core hits show their damage (the
  core refills, the Warden takes it five-fold). P2 projectiles use TSPS's timings (attacks 25 to 115,
  divine 25 to 145, imprison 10 to 130, Akkha phantom 0 to 54) through `projectile(flight = ...)`.
- **The vault Osmumten** no longer wanders.

### RuneLite plugin fixes (2026-10-29)

The RuneLite "Tombs of Amascut" plugin (LlemonDuck, plugin hub) did nothing in Scabaras. The build
that runs against this server is the one RSProx's RuneLite 1.11.19 loads, from
`~/.rlcustom/plugins` (the newer build in `~/.runelite/plugins` is for the live game).

- **Scene offset.** The plugin finds the lights-out and addition puzzles, and the obelisk-skip
  flames, at fixed tiles of the client's scene. The real game always loads a room's map square 16
  tiles in from the scene's south-west corner; we centred the scene on the arrival tile. New engine
  hook `BuildAreaAnchors` (api/game-process): content can pin a player's build area, and
  `PlayerBuildAreaProcessor` uses it while the player stands clear of its edges (resending zone
  contents when it changes). ToA registers `ToaRaids.sceneAnchor` / `ToaPacking.sceneAnchor`, so
  every room loads as in the real game. The room never re-centres while you walk about it.
- **Memory plates.** The lit and stepped plates (45341, 45342) are animated glow models, like the
  lights' `fx01`, so they now lie on top of the floor plate as scenery again (the plugin watches for
  them as game objects). This undoes the playtest swap above, which was a guess. A wrong step drops
  the rocks as a map graphic on the tile (the plugin resets on it).
- **Matching tiles** show the matching game's own symbols (`toa_scabaras_memorygame_tile1-9`) and
  glows, not the addition plates (TSPS had them).
- **Addition diamond glow** is `fx08`; TSPS had the star's `fx04`.
- **Het.** A movable mirror picked up or pushed leaves `invisible_type8_nonblocking` (the plugin
  reads it as an empty tile); it goes with the layout.
- **Kephri.** A killed swarm plays `npc_swarm01_scarab01_death01` (the plugin counts kills by it).
- **Chat wording** the plugin matches: "Challenge started: Path of Het." (full stop); boss rooms are
  named by the boss (Kephri, Akkha, Ba-Ba, Zebak, The Wardens); every completion line ends with
  ". Total: m:ss" (raid time so far), and the final Wardens line carries "Tombs of Amascut: <mode>
  Mode challenge completion time:", followed by the total completion time; entering sends "You
  enter the Tombs of Amascut (<mode> Mode)..."; with a time limit, "Overall time to beat: mm:ss"
  on entry and again when the timer starts.
- **Not fixed:** the newer plugin build's Apmeken wave overlay reads npc positions within the map
  square, which the packed columns shift (Apmeken by 40 tiles), and its tablet pattern expects the
  live game's colour macros. Neither is in the build RSProx's RuneLite loads.
- **Client check:** every Scabaras overlay, the Het beam timer and solver, splits, points reset,
  and that the room does not visibly reload when you walk about.

### Second playtest fixes (2026-10-29)

- **Boss kills restore.** Beating a path boss gives everyone in its room their hitpoints and prayer
  back (a boost is kept) and cures poison and venom (`ToaRoomRuntime.restoreAfterBoss`).
- **Lobby bank camel.** The map places the multi-loc `toa_bank_camel_parent`; its "Bank" now opens
  the (team) bank through `BankAccess`.
- **Slow movers crawl.** Akkha and the Crondis crocodiles use `MoveSpeed.Crawl`: the client glides
  them a tile over two ticks instead of a step and a wait.
- **Akkha's death.** He leaves once his death animation has played (`ticksOf`), not 5 ticks later.
- **Het mining.** Breaking a wall or the seal ends everyone's mining of it at once
  (`stopWorkingOn`: the interaction is cleared and the swing put down).
- **Far npcs drawn.** Players inside a raid region are told about npcs up to 40 tiles away
  (`ToaRaids.tickViewDistance`); Zebak used to vanish from the far side of his arena.
- **Line of sight.** Ranged room monsters need line of sight to swing (`canSee`), and the Scabaras
  scarabs only pick players they can see; they used to spit through walls.
- **Hits trimmed as they land.** Hits are modified when they are queued, so a hit in flight could
  land after a phase change. Engine: `NpcHitEvents.Landing` (published by
  `StandardNpcHitProcessor` before the npc takes a hit; damage can only be lowered) and
  `ToaRoomRuntime.landingDamage`. Kephri is downed when the hit lands, and a hit landing while she
  is dazed does nothing (both phase transitions used to fire together). Ba-Ba's health floor is her
  next threshold until that boulder phase begins, then 1 hp while the last boulders roll; the next
  phase and her death wait for the current boulders to stop.
- **The obelisk.** Two hits in flight could take its last hitpoint, which left it at 0, unattackable,
  with no Warden. `WardensRuntime.hitpointsGone` now starts phase two (and downs the Warden).
- **Warden charge (guess).** Each orb that arrives now gives 2% and the passive charge is 1% every
  two ticks (it was 1% and 2%). Unblocked, the rate is the same; blocking orbs now holds the
  specials back a good deal without stopping them (wiki: blocking "will significantly reduce,
  although not prevent" the charge).
- **Venom trails.** The pool loc's models carry no shape types, and the client draws those only as
  a centrepiece, so pools placed as ground decor were invisible. They are now centrepieces.
- **Client check:** all of the above, in particular crawl animation speed, the bank camel, and
  whether a venom pool on a tile hides any map scenery there.

### Third playtest fixes (2026-10-29)

- **Zebak's jugs.** Push and Pull work from all 8 tiles around a jug (ap handlers with range 1, so
  a diagonal click acts without walking to a side); from a corner the jug rolls diagonally. "Hit"
  now starts an ordinary attack with the player's weapon (`opNpc2`), so ranged and magic hit from
  where the player stands. The jug type has no "Attack" op, so combat refused it: new
  `PvnRules.allowAttack(type)` lets content mark such types. A landed blow still shatters the jug
  the tick after, and jugs never fight back (`toaBosses`).
- **Scabaras matching tiles** turn from a diagonal tile too (ap handler, range 1).
- **Kephri.** Her fireballs drop straight down onto each tile from high above (a projectile from the
  tile to itself, heights 250 to 0, landing as the bomb goes off) instead of flying out of her. Her
  second down brings a spitter as well as the soldier and arcane scarabs. Dead, she is out of
  every queue and mode: her dead form is in `toaBosses` (a hit still in flight made the engine's
  own retaliation send her after the player), `finalDeath` sets `noneMode`, and hits after the
  room is complete do nothing.
- **Baboons wait out their spawn animation.** `monster(spawnAnim = ...)` plays it and holds the
  monster (`ToaMonster.heldUntil`) until it ends; used for Ba-Ba's baboons and every Apmeken baboon
  and thrall (cursed ones do not wander while held).
- **Volatile baboon blast** is a real hit on baboons beside it (hitsplat and health bar), the same
  damage the players take. It used to set their hitpoints to 0 (a guess that they all die).
- **Ba-Ba's thresholds.** Once a threshold is reached she is held there until her boulder special
  visibly begins (she leaps for her throne), not just until the phase is queued; melee during
  the wind-up no longer ends the boulder phase before it starts.
- **No attack delay** after hitting a Warden skull (the siphons) or a Scabaras obelisk
  (`NpcAttackDelays`, delay 0).
- **Final Warden's death.** Its collapse (34 ticks) plays on the Warden; then it is removed and
  the five `toa_warden_p3_death_*` npcs take over the fall (22 ticks, then their lying idle). They now face north like the Warden: their
  animations are offset towards their front, so facing south showed mirrored copies behind it.
  With the Warden gone it can no longer stand up again when its animation ends.
- **Final crystal.** The crystal is `toa_teleport_crystal_continue_wardens` (45138, TSPS's id; the
  old `wardens_teleport` is the first room's map crystal), placed on the arena's first row
  (3936, 5157), in front of the Warden: the one row the floor collapse never takes. It used to sit
  under the Warden.
- **Client check:** the fireball drop, the Warden's fall (facing guessed from the offset names),
  diagonal jug and tile clicks, "Hit" with a bow from range, baboon spawn timing.

### Fourth playtest fixes (2026-10-29)

- **Boss health bars.** Cause found from an RSProx transcript plus the client scripts: the server
  sent exactly TSPS's captured sequence, but `hp_hud_open` (2377) only resets the bar and starts
  its own fade in while the `hp` layer (303:5) is hidden, and a fade step (2888) only runs when told
  the bar's current transparency. Our hide faded the bar out to 255 and left `hp` "shown", so
  every later show skipped the reset and the captured fade from 254 did nothing: only the first
  bar after logging in could ever appear. Each show now hides 303:5 just before 2376.
- **Volatile baboons** die by bursting: their death queue only tells the room, which plays
  `npc_mandrill_explode` instead of the usual death and, 2 ticks later, sets off the blast and
  removes the baboon together, exactly like a burst on reaching someone. (Going through
  `toaMonsterDeaths` would have added a tick for a baboon that had just moved, `arriveDelay`.)
- **Ba-Ba's boulders** stop colliding (no knockback, no crushing baboons or clearing debris) the
  moment the hit that will break them is queued; they roll on and are removed when it lands, as
  before. Tracked as the damage in flight per boulder (`modifyHit` adds, landing takes away).
- **Warden core.** Hits are no longer trimmed to the core's health, so the hitsplat shows the
  whole hit. The core is topped up as each hit lands (new `ToaRoomRuntime.landing(npc, hit,
  damage)` hook), so it never dies: two hits in flight (a special, two players) used to take it
  to 0, which ended the attack on it. The kneeling Warden shows the five-fold damage as a hitsplat
  of its own (a copy of the core hit's hitmark).
- **Collection log** hears of ToA loot only when it is claimed (Take or Take-All, to the inventory
  or the bank), in the vault or later from the lobby's rewards hole, so the popup no longer gives
  the loot away. Discarded loot is never logged.
- **Client check:** every boss bar shows (seal, Akkha, palm, Ba-Ba, Zebak, Kephri, obelisk,
  Wardens), the volatile burst, a doomed boulder rolling through you, the Warden's hitsplats.

### Loot points fix (2026-11-03)

- **Points are per player now.** Jagex's drop mechanics post: every player starts on 5,000, "you
  cannot accumulate more than 64,000 Total points as an individual", and a death takes 20% of the
  dying player's own total. `ToaPoints` used to keep one team total with one 64,000 cap, so no
  raid could pass about 13% purple at level 300 or 18% at 500 (the 55% cap was unreachable) and a
  death cost the whole team 20%.
- **Unique and pet** are each one roll on the team's summed loot points (`teamLootPoints`,
  players who left included, per Mod Ash) and go to a player by their share of those points. The
  pet used to be rolled for each player at the team's chance.
- **Commons, fossilised dung, the elite clue and the personal contribution varp** use the player's
  own loot points (the dung check used the team total, so it never fired).
- **Guess:** a unique or pet roll that lands on the share of a player who left gives nobody the
  drop.
- **League drop boost (same day):** the purple chance is multiplied by the league's drop-rate
  relics (`dropMultiplier.byRelicTier`, 5x from tier 1 and 20x from tier 4), after the wiki's 55%
  cap and capped at 100%. Relics are per player, so the team's boost is each player's multiplier
  weighted by their loot points (`ToaRewards.relicBoost`). `toa.uniqueMultiplier` still multiplies
  on top. The pet is not boosted (Raging Echoes did not boost pets).
- **Team uniques (same day):** when the raid gives a purple, the finder (picked as before by
  share of points) claims it from the sarcophagus, which only they can open now; everyone else
  still in the raid gets a unique of their own in their chest, in place of their common rolls
  (`ToaLootMath.pickTeamUnique`: a normal pick, redone among in-level uniques if the 1 in 50
  misses, fang/Lightbearer below level 50). Event key `toa.teamUniquesFromLevel` (default 0 =
  every raid; Raging Echoes used 500).
