# Isle of Souls: Chambers of Xeric plan

Scoped with the user on 2026-11-24 and built the same day in one go (section 7 is the as-built
record).

Sources: the OSRS Wiki is the authority. The tsps write-up (`../tsps/docs/chambers-of-xeric.md` and
`server/plugins/minigames/cox/`, about 10.5k lines) is read for facts only (ids, rsprox-captured timings,
decoded interface contracts), never ported. Its own doc says the fights are untested in game, and the
ToA work showed its UI contracts can be wrong, so every interface is checked against this client's cs2
first.

## 1. Decisions

| Topic | Decision |
| --- | --- |
| Modes | **Normal and Challenge Mode.** CM: the fixed 3-floor layout with every room, +50% scaling (Tekton defence +20%/+35%), target times (+5,000 points, kit 1/75, dust 1/400), Captain Rimor's capes. |
| Layouts | **Real random layouts**: one of the layout patterns, combat rooms as a run from one of the two Wiki rotations, 2 of the 4 puzzles, scavenger and resource rooms, two floors, then the Great Olm. Rooms are copied from the cache's raid map and turned to face the way in (the engine already has turned zone copies: `RegionZoneCopy`, `RegionRotations`). |
| Prep layer | **Full real prep**: resource rooms (fishing + cave snake, bats, herb patches, weeds, gourds and geysers, saplings and kindling, old tools, energy well), raid Herblore (overloads, Xeric's aid, revitalisation, prayer enhance, antipoison), storage units (private + shared, built and upgraded with mallignum planks), food and potion points. |
| Entrance | **Real Mount Quidamortem**: recruiting boards, the entrance, Captain Rimor, the bank chest and the mountain's private storage unit, in their real places. |
| Getting there | **Two-way portal** between the Soul Wars lobby and the mountain, **plus Xeric's talisman's Xeric's Honour** teleport landing there (league task 120). The way down the mountain into Kebos is **blocked**. |
| Parties | **Board-only parties** on the real recruiting board (499) and party page (507): make, advertise, join, kick and disband without a friends chat (rsmod has none). The leader starts the raid from the real side panel (500). OSRS's channel rank rules (Recruit to make, General to enter early, kick rights) are left out. |
| Party cap | Event key `cox.partyCap`, **default 100** (real limit). Scaling formulas take any size. If the party page only draws a fixed number of rows, extra members go in a chat list like ToB's players 6-8 (to decode in M0). |
| Loot | **Same rules as ToA/ToB**: Wiki points-based unique chance (1% per 8,676 points, 570,000 a roll), the league drop boost (`relics.dropMultiplier`, 5x/20x) on the unique chance with the team's boost weighted by points, team uniques (finder claims, everyone else gets a pick), olmlet unboosted. Keys `cox.uniqueMultiplier`, `cox.teamUniques`, `cox.teamUniquesFromPoints`. |
| Deaths | Isle rule: items are kept. Points follow the Wiki (40% lost, or 5% of the team's total under 5%). Raid-only items drop where the player died. |

**Assumed unless you say otherwise:**
- League tasks: 114-117 (1/25/50/100 completions, Normal or CM), 177 (equip Xeric's talisman), 120 (Xeric's Honour), 1061 (twisted buckler), 1067 (ancestral recolour), 131 (twisted bow). 1424 (Combat Achievements) stays out, like ToA's 1426.
- Completions go on the collection log varps that already exist (`clog_kc_chambers_of_xeric_completions`, `_cm_completions`), with a CoX clog page on claim.
- Xeric's talisman gets all its teleports only as far as they land on places the event uses; Xeric's Honour needs the ancient tablet read (Wiki), and the tablet comes from the raid.
- Reward items: twisted bow, elder maul, kodai wand, dragon claws, dragon hunter crossbow, dinh's bulwark, ancestral set, dexterous/arcane prayer scrolls, buckler, olmlet. Their specials already exist (special attacks done 2026-10-22); passive effects not built yet (twisted bow scaling, dragon hunter crossbow, kodai, ancestral, bulwark) are part of this work.
- "Large raids" (24+ players in OSRS) are not split out: one formula for every party size up to the cap.

## 2. What the cache already has

- Npcs: about 170 raid npcs named in `npc.sym` (`raids_*`, `olm_*`, Tekton forms, Vasa, Vespula and grubs, Vanguards, Muttadiles as `raids_dogodile_*`, tightrope guards, scavengers, `raids_olm_pet`).
- About 400 locs, 360 objs, 160 seqs, 130 varbits, 15 varps.
- Interfaces: 21 `raids_challenge`, 271 `raids_storage_private`, 499 `raids_lobby_partylist`, 500 `raids_sidepanel`, 507 `raids_lobby_partydetails`, 513 `raids_overlay`, 539 `raids_rewards`, 550 `raids_storage_shared`, 551 `raids_storage_side`.
- Captain Rimor is already in the world spawn data. Xeric's talisman (13392/13393) exists as an obj.
- Raid map (tsps facts, to confirm in M0): rooms are 4x4 chunks at chunk x 408/412/416 (turn west / straight / turn east), wall chunk (416, 647), Olm's chamber 5x7 chunks from (402, 713).
- Herblore already has the CoX potions as items (2026-10-12 food plan); this work adds the raid's own mixing.

## 3. Size

Bigger than ToB and roughly the size of ToA: 13 room types plus Olm, two modes, the prep layer and the
storage units. Most of the risk is in the map (copied, turned rooms on three planes) and the
interfaces (party board, side panel, storage, rewards).

| Part | Size |
| --- | --- |
| Mountain, portal, fence, Xeric's talisman, board-only parties | M |
| Layout generator + instance builder (turned rooms, floors, Olm chamber), points, deaths, leaving | L |
| Prep: resource rooms, raid Herblore, storage units, supply points | L |
| Puzzles: Ice Demon, Thieving, Tightrope, Crabs; scavengers + shortcuts | M |
| Combat rooms: Tekton, Muttadiles, Vanguards, Vasa, Vespula, Guardians, Shamans, Mystics | L |
| Great Olm | L |
| Challenge Mode, rewards, ancient chest, Rimor's capes, tasks, clog | M |
| Reward item passives | M |

## 4. Milestones

- **M0 probe:** pack the raid map rooms into one instance region (turned copies on planes 3/2/1 + the Olm chamber), check collision after a turned copy, check the mountain map, decode interfaces 499/507/500/513 against this client's cs2 (row count on the party page), dev `::coxprobe`.
- **M1 shell:** mountain, portal both ways, fence, Xeric's Honour, board-only parties, entering, the layout generator, floors and passages, the side panel and points overlay, deaths, leaving, rejoin on login.
- **M2 prep:** resource rooms, raid Herblore, storage units, food and potion points.
- **M3 puzzles + scavengers.**
- **M4 combat rooms** (shared room runtime like ToA's: build, start, tick, complete, exit crystals).
- **M5 Great Olm.**
- **M6 Challenge Mode, rewards, chest, capes, tasks, clog, reward item passives.**
- **M7 wiki review** of every room, like ToA's.

## 5. Open questions

- Answered: built all in one go (like ToB).
- The portal and fence tiles were picked from the map (section 7); move them if you like.

## 6. Wiki facts gathered for the build (2026-11-24)

**Potions** (herb + water-filled gourd vial + secondary; Herblore level / XP):

| Potion | Herb | Secondary | (-) | normal | (+) |
| --- | --- | --- | --- | --- | --- |
| Elder | golpar | stinkhorn mushroom | 47 / 6.5 | 59 / 10 | 70 / 13 |
| Twisted | golpar | cicely | 47 / 6.5 | 59 / 10 | 70 / 13 |
| Kodai | golpar | endarkened juice | 47 / 6.5 | 59 / 10 | 70 / 13 |
| Revitalisation | buchu leaf | stinkhorn mushroom | 52 / 13.5 | 65 / 20 | 78 / 26.5 |
| Prayer enhance | buchu leaf | cicely | 52 / 13.5 | 65 / 20 | 78 / 26.5 |
| Xeric's aid | buchu leaf | endarkened juice | 52 / 13.5 | 65 / 20 | 78 / 26.5 |
| Antipoison | noxifer | cicely | 60 / 13.5 | 75 / 20 | 90 / 26.5 |
| Overload | noxifer + elder, twisted, kodai (4) of that strength | - | 60 / 33.5 | 75 / 50 | 90 / 66.5 |

Cleaning: golpar 47 / 1 XP, buchu 52 / 2, noxifer 60 / 3 (Farming 27 / 39 / 55 to grow).
Secondaries: Tekton 5 stinkhorn; Ice demon 7 stinkhorn, 5 cicely, 7 juice; Muttadiles 1-3 stinkhorn; Vasa 5 juice;
scavenger beasts 3-11 stinkhorn, 3-6 cicely, 5-14 juice (each 2 x 2/18).

**Food** (fish: Fishing level / XP, Cooking level / XP, heals; bats the same with Hunter):
pysk 1/20, 1/10, 5; suphi 15/23, 15/13, 8; leckish 30/26, 30/16, 11; brawk 45/29, 45/19, 14; mycil 60/32, 60/22, 17;
roqed 75/35, 75/25, 20; kyren 90/38, 90/28, 23. Bats guanic 1/5, prael 15/9, giral 30/13, phluxia 45/17, kryket 60/21,
murng 75/25, psykk 90/29 (cooking as the fish of the same tier). Rod + cave worms (scavenger drops); bats need a butterfly
net or 99 Hunter. Resource rooms are 50% bats, 50% fishing.

**Kindling**: saplings give 1-8 per chop (max = Woodcutting / 12), 30 XP at 1 to 81.5 at 96; stack cap 28; 48 Firemaking XP
each; sapling respawns in 30 seconds.

**Storage units**: small 30 Con, 2 planks, 250 shared / 30 personal; medium 60, 4, 500 / 60; large 90, 6, 1,000 / 90;
massive 99, 8, 1,500 / 120; 150 XP and 100 points a tier; mallignum root planks; hammer.

**Points**: damage dealt; storage 100 a tier; shortcuts level x 5; cooking 4 + 8 x tier per piece eaten (x1.5 when someone
else eats), soft cap 20 x raiders, hard cap 40 x; potions capped at 5 sips x raiders; thieving 115 a grub, max
max(4500, total Thieving / 6 x 150); death loses 40% personal (or the team 5% if under 5% of the total); common loot
scales to 131,071 points.

**Loot**: 1% unique chance per 8,676 points (cap 65.7% at 570,000; extra points roll more, up to 6); a unique replaces
the commons. Weights normal / CM: dexterous 14/12, arcane 14/12, buckler 4, DHCB 4, bulwark 3, ancestral hat/top/bottom
4 each, claws 3, maul 2, kodai insignia 2, twisted bow 2 (totals 60 / 56). Commons: two rolls, never the same item; a third
of herb rolls become seeds. Dark journal always unless banked; ancient tablet 1/10 if not owned; elite clue 1/12 without
a unique; olmlet 1/53 of uniques. CM: kit 1/75, dust 1/400 within the target time (1 player 70 min, 2: 65, 3: 50,
4: 45, 5-10: 42, 11-15: 45, 16-23: 60, 24+: 80), +5,000 points each.

**Great Olm**: rotation standard, empty, standard, crystal burst, standard, empty, standard, lightning, standard, empty,
standard, swap; a skipped standard is caught up once; at least 4 phases, +1 per 8 players; phase attack 1 in 3 (1 in 2 last
phase); 20% style swap after an attack; prayer cuts 75-80%; spheres take 50% of current hp unless prayed; acid spray 3-6 a
tick (poison), acid drip; deep burn 5 every few ticks draining 2 stats, six hits; fire wall 8-10 to leap, 50-65 if caught;
falling crystals 11, 15-20 on the tile; crystal bombs up to 3 (2 for small teams), 15 + 15 per tile closer, radius 4; head
max hit 27/28/29 (phase 4); 4-tick attacks.

## 7. As built (2026-11-24)

Code: `content/areas/misc/isle-of-souls/src/main/kotlin/.../isleofsouls/cox/` (about 6,000 lines).

| File | What it owns |
| --- | --- |
| `CoxLayout.kt` | Room types (cache rows and versions), the 26 patterns, the two rotations, Challenge Mode's floors, laying a floor out as a path. |
| `CoxMap.kt` | The raid's large region: turned room copies on planes 3/2/1, wall filler, the Olm's chamber on 0-1; template tile to raid tile and back. |
| `CoxRaid.kt`, `CoxRaids.kt` | Raid state and lifecycle: entering, the leader's start, passages, holes and ropes, floors and level messages, points, deaths, leaving. |
| `CoxParties.kt`, `CoxBoard.kt` | Board-only parties; the recruiting board (499) and party page (507). |
| `CoxHud.kt` | Side panel (500, on the chat-channel tab) and points overlay (513). |
| `CoxScript.kt` | Mount Quidamortem (portals, fence, board, entrance, bank, Xeric's talisman, ancient tablet) and inside (start button, passages, holes, ropes, steps, bank chest, energy well). |
| `CoxRoomRuntime.kt`, `CoxRoomDirector.kt` | The shared room runtime (template tiles, scaled spawns, exit crystals, attacks, monster driving) and the registry/director. |
| `CoxScaling.kt` | The Wiki DPS calculator's scaling and the monsters' base figures. |
| `CoxResources.kt` | Saplings and kindling, kindling fires, fishing and the cave snake, weeds, herb patches, gourds and geysers, tools, the bat room. |
| `CoxHerblore.kt` | Cleaning, potions, overloads, food and potion points. |
| `CoxStorage.kt` | Storage units (tiers, building) and the private and shared storage screens. |
| `CoxPuzzles.kt` | Ice Demon, Thieving, Tightrope, Crabs. |
| `CoxCombat.kt` | Scavengers (and the shortcut), Tekton, Muttadiles, Vanguards, Vasa, Vespula, Guardians, Shamans, Mystics; fires and tendrils. |
| `CoxOlm.kt` | The barrier and the Great Olm. |
| `CoxRewards.kt` | Loot rolling, the ancient chest (539), counts, tasks, Captain Rimor's capes, gear tasks, the twisted ancestral kit. |
| `CoxCommands.kt` | `::cox`, `::coxlayout`, `::coxpoints`, `::coxroom n` / `olm`, `::coxopen`. |
| `CoxSettings.kt` | Event keys `cox.partyCap` (100), `cox.uniqueMultiplier`, `cox.teamUniques`, `cox.teamUniquesFromPoints`. |

Also changed: `EventSettings.kt` (the `cox` object), `LobbySpawnScript.kt` (deaths in a raid wake at
the start of the floor reached), `LeagueTaskList.kt` and `LeagueTaskSeries.kt` (tasks 114-117,
120, 177, 1061, 1067, 131).

Tests: `CoxRulesTest` (unit: 400 random layouts plus Challenge Mode laid out without overlaps,
every room's way out meeting the next room's way in after turning, tile round trips for all four
turns, scaling, loot maths), `CoxRaidTest` (party, entry, start, all rooms built, rooms opening,
floors down to the Olm and back up, points and deaths, the Olm's phases to its death and the
loot, leaving) and `CoxFightTest` (every room of a Challenge Mode raid run 200 ticks with two
raiders: every combat room must hit someone and every encounter must end with its way on open).
All pass three runs in a row against a sandbox copy of `.data`; the sandbox server loads 352
scripts with no errors. Test gotcha: player hits only land in tests with `PlayerHitScript`
loaded.

### Decoded client contracts

- Board 499: script 1566 row `r` = `leader|members|preferred size|preferred combat|preferred
  total|scaling|challenge|age|` (TSPS sends 7 fields; the script reads 8, the 6th is scaling).
  Rows resume `raids_lobby_partylist:list` child r; Refresh and Make party resume `:contents`
  children 0 and 1 (enum 1538). Varp `raids_party_groupholder` -1 shows "Make party".
- Party page 507: script 1517 adds a member `name|combat|total|kc|` and the 23 skills; 1524 the
  header (state 0/1/2, advertised, adverts left, title). Buttons are `:contents` children (enum
  1539): 0 Join/Leave, 1 Advertise, 2 Disband, 3 Refresh, 4 Back, 5 size, 6 combat, 7 total, 8
  scaling, 9 Challenge Mode, 10 map layout.
- Side panel 500: varbits `raids_client_isleader`, `_partysize`, `_partysize_scaled`,
  `_highestcombat`, `_progress` (0 waiting to 5 "Get out!"). "Start raid" is op 1 on
  `raids_sidepanel:start`. Its kick goes through the friends chat (not available here). Script
  1547 sizes the list, 1548 adds `name|combat|total|`.
- Overlay 513: varbit `raids_client_partyscore` (17 bits), varp `raids_playerscore`, varbit
  `raids_timer` counted up by the client while the progress is under 5.
- Storage: private 271 (inv 583, items on `:items`), shared 550 (inv 582), side 551 (inv 93);
  item ops 1 one, 2 five, 3 ten, 4 all, 5 X. Rewards 539 (inv 581, `:items` op 1 Take).
- Ops read from the cache: bats op 1 Catch, meat tree op 1 Cut, crabs op 3 Smash, Rimor op 1
  Talk-to and op 3 Claim-cape, Xeric's talisman held op 3 Rub and op 4 Check.

### Deviations and guesses (to review)

- Logging out leaves the raid (OSRS lets you back in).
- Exit crystals only where TSPS's captures give tiles (Tekton, Muttadiles, Vanguards, Vasa,
  Vespula's boils, Guardians' blockers); every other room's way on is held shut by its passage
  until the room is done.
- Damage points 5 a hitpoint (TSPS's guess; the Wiki only says "based on damage"); the keystone
  500 points and the meat tree 50 a chunk are guesses.
- Food and potion points are given when cooked or made (items carry no maker here).
- Gathering: a try every 4 (chop) or 5 (fish) ticks, success 40% + level / 2; saplings wear out 1
  in 4; raked seeds 1-3; patches grow a stage every 12 ticks; about three herbs a patch; the cave
  snake is a 1 in 40 bite with no npc.
- The crab puzzle is simplified: orbs bounce off walls, take a crab's colour, raiders turn them
  clockwise, smashing a crab cycles its colour.
- Thieving chest odds 39% + 22% x level / 99 (+21 with lockpicks), 1 in 8 a bat chest; the
  tightrope wants 80% of the raiders' average Agility.
- Combat rooms keep to the Wiki and TSPS rules in each class comment; timings not on the Wiki are
  TSPS's or guesses (Vasa's special every 40 ticks, Vespula's stings, grub drain 25 of 100,
  shaman acid/jump/spawn odds, mystics 3 + raiders / 2).
- The Olm: spheres 1 in 8 of standard attacks, lightning hits half the raiders, the head attacks
  every raider in the zone it faces, the clench and Life Siphon aren't built, the fire wall is a
  timed area rather than npcs.
- Loot: the common table's points per item come from the Wiki's maxima where it gives them, the
  rest from memory (unchecked); herb rolls never become seeds; leaving with loot banks it (OSRS
  drops it on the floor).
- Rimor's capes at 100 / 500 / 1,000 / 1,500 / 2,000 Challenge Mode completions (guessed tiers).
- Xeric's talisman has unlimited charges and only Xeric's Honour works (after reading the ancient
  tablet); its worn teleport ops aren't wired (Rub is).
- The Kodai wand's 15% rune saving isn't built (its stats, the twisted bow, the dragon hunter
  crossbow, the bulwark and the specials already were).
- The mountain fence is collision on z 3532, x 1205-1290 (a flood fill from the boards showed it
  seals every path down); the minecart says it doesn't run.

### Tiles picked

Isle portal (2225, 2821) east of ToB's, its return (2225, 2820); the mountain's portal back
(1240, 3559) and arrival (1240, 3558); Xeric's Honour (1254, 3559); leaving the raid (1233, 3572).

### What you need to do

1. Stop the server and run `packCache` (two invs made Perm: `raids_rewards`,
   `raids_privatestorage`).
2. Client walk-through: the portal; the board and party page (rows, Join, preferences, Challenge
   Mode); the entrance warning; the side panel's Start raid and the overlay's points and timer; a
   turned room's passage; a hole down and the rope back; building storage and both storage
   screens; each room type; the Olm's rising, claws and phase attacks; the chest's Take; Rimor.
