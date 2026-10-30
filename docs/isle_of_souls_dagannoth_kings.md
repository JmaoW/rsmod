# Dagannoth Kings

Built 2026-10-27 from the OSRS Wiki (pages "Dagannoth Kings", "Dagannoth Rex", "Dagannoth Prime",
"Dagannoth Supreme", "Spinolyp"); tsps has no plugin for them. Code is in
`content/areas/misc/isle-of-souls/.../dagannothkings/` (`DagannothKings.kt` = the lair, the kings,
the spinolyps and the run; `DkAttacks.kt`; `DkScript.kt`; `DkConfigs.kt`) and the tasks in
`tasks/LeagueDagannothKingTasks.kt`. Tests: `DagannothKingsRunTest` (integration).

Scope agreed with the user: a team instance of the real lair (like the King Black Dragon, Kalphite
Queen and Obor), a ladder on the isle, the spinolyps included, kills count for a Dagannoth Slayer
task, normal kings only (the Echo kings wait for the shared echo work), and the tasks 596, 597, 599,
601, 604, 607, 613, 614 and 615.

## How a team gets in

- The Kings' ladder down (`dagexp_bossroomladder_down_normal`, the multiloc's child without
  "Private") stands on the field south of the plaza at **(2176, 2808)**, west of the dungeon
  entrances; players land at (2176, 2807) when they climb back up. Move it with `DkLadder`.
- "Standard" and "Slayer" both climb down into the team's lair (there is no Slayer-only lair:
  every lair is private, and the kings carry the Dagannoth task id 35 in the cache, so they count on
  a Dagannoth task wherever they die). "Peek" says how many of the team are in the lair.
- The lair is a copy of the 5x5 zones x 2896-2935, z 4432-4471 (Waterbirth Island Dungeon, sixth
  level). Players arrive at (2900, 4449), east of the real Kings' ladder up, which takes them out.
- The world spawns of the kings and spinolyps in the real lair and in the Slayer-only copy south of
  it were removed from `underground.toml` (56 spawns), with a `rect` rule in
  `tools/spawns/exclusions.txt`.

## The fight

All from the wiki unless listed under guesses.

| King | Style | Max hit | Weak to | Home (south-west tile) |
|------|-------|---------|---------|------------------------|
| Rex | melee, slash | 26 | magic | 2913, 4445 |
| Prime | magic, also hits every player within 1 tile of the target (3x3) | 50 | ranged | 2911, 4451 |
| Supreme | ranged, also hits every player within 3 tiles of the target ("6x6") | 30 | melee | 2906, 4448 |

- Each attacks every 4 ticks, has 255 hitpoints, and comes back 150 ticks after it dies, scaled by
  the event's respawn speed. The homes are the spawn list's tiles in the real lair.
- Stats and stab/slash/crush/magic defences are the cache's. `npcs.toml` adds the ranged defences
  (Rex 255, Prime 10, Supreme 550), the slayer XP, the 30% bonus XP of Rex and Prime, the earth
  weakness (35%), venom turning into poison, Rex's slash, and each king's own attack animation (the
  derived anims file gave all three the melee one).
- Aiming: every tick a king with no target goes for the nearest player inside the lair within 8
  tiles of it who is within 12 tiles of its home (its side). Once it has a target it keeps them
  wherever they go in the lair (user's call, 2026-10-27): its leash (`maxRange`) is 40, more than
  the whole lair, and Prime and Supreme shoot from up to 32 tiles, so they hit their target anywhere
  in the lair they can see without walking over; Rex walks over. A king lets go only when its
  target leaves the lair or dies, then wanders round its home. A king that is hit fights back as
  usual (Prime and Supreme from where they stand). The spawn tiles are close together, so a player
  at the ladder is on Supreme's side and off Rex's.
- Drops are the imported tables (`osrs.toml`, rings already boosted in `boosted.toml`) and fall at
  the middle of the 3x3. The collection log kill counts are fed by name, as for every boss.

**Spinolyps:** 25 spots round the water's edge (the wiki's 25), each one of three cache kinds with
its own "suspicious water" twin. They never move. Every 4 ticks each shoots the nearest player
within 10 tiles, picking magic or ranged at random; both roll its Ranged accuracy against the
player's Ranged defence, up to 10. The magic hit drains 1 prayer point when it hits; the ranged spine
can poison (6). Now and then a spinolyp dives (shown as its suspicious water, which takes no
damage) and comes up again later. A killed one is back after 10 ticks.

## Tasks

| Task | Tier | How |
|------|------|-----|
| 596 Defeat the Dagannoth Kings Without Leaving | Hard | the player who gets the drops has killed all three since they last climbed down |
| 613 / 614 / 615 Defeat Each Dagannoth King 50 / 150 / 300 Times | Hard / Hard / Elite | one count per king (team-shared, capped at 300); done when the lowest reaches the goal |
| 599 / 601 / 604 / 607 Equip a Berserker / Warrior / Seer's / Archer's Ring | Hard | worn; the Nightmare Zone and Soul Wars imbued rings count |
| 597 Equip Every Dagannoth King Ring | Elite | all four ring tasks done (there is one ring slot) |

The descriptions of 596 and 613-615 lose "in the Waterbirth Island Dungeon", and all nine are made
area-free (`IsleOfSoulsTaskEdits`). Enum keys 540-548. Counters `isle_count_kill_dk_rex/prime/
supreme` (varp.sym 65176-65174). Left out: Trade with Bardur (1551), the combat achievements task
(1429).

## Guesses (the wiki is silent)

- Hunt range 8 and side 12 from home (for picking a target), wander 4.
- Spinolyps: range 10, magic or ranged at 50/50, 1 in 4 damaging spines poison, up for 25-50 ticks
  and under for 8-20.
- Projectile flights reuse the stock `magic_spell` and `arrow` ones; graphics are Water Wave for
  Prime, `dagannoth_spine_spotanim_travel` for Supreme, Water Strike and `horror_spines_travel` for
  the spinolyps.
- Which suspicious water belongs to which spinolyp is read from the spawn list (the same spot holds
  one or the other in the two copies of the lair).

## Needs

- `./gradlew packCache` for the `npcs.toml` changes, the `NpcEditor` (leash, wander, attack range,
  hero slots) and the new symbols: `dk_tick` (`timer.sym` 35), the three counters (`varp.sym`), the
  nine `league_task_*` structs (`struct.sym`) and the task text and enum edits.
- A client check: the ladder on the field (its angle is a guess), the attack animations and
  graphics, the spinolyps diving and surfacing, and the new tasks in the league window.

## Not built

The Echo kings (kingly orb, amulet and ring of kings); the Waterbirth Island Dungeon itself; the
spectral spirit shield halving the spinolyp prayer drain; the suspicious water's quick melee
attack; bones dropped by a spinolyp that dies as suspicious water; the combat achievements.
