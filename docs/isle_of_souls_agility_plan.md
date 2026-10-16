# Isle of Souls: Agility plan and status

> **2026-10-10: the six courses were removed** (the whole agility park, on your request). The
> Brimhaven Agility Arena and its entrance hut and Grace stay. (The five shortcuts and the eleven
> course and shortcut league tasks were removed the same day.) `Courses.all` is
> now empty; the lap machinery in `Agility.kt` is still there but has nothing to run, so marks of
> grace, lap counts and the lap-based league tasks below cannot happen until a course comes back.

Scoped 2026-10-09, built the same day. **Not seen in a real client yet.** Sources: the tsps agility
docs and code (`RSPSApp/tsps`, BSD-2: `docs/agility-courses.md`, `docs/agility-shortcuts.md`,
`server/plugins/skills/agility/`), the real course locs in this cache (revision 233), and the OSRS
Wiki (graceful shop, Brimhaven Agility Arena). Everything taken from tsps stays "unverified against
the real game".

## Decisions (yours, 2026-10-09)

1. Courses are **flat** on open ground (level 0), not raised on roofs.
2. The **core six**: Gnome 1, Draynor 10, Varrock 30, Falador 50, Seers' Village 60, Ardougne 90.
3. I chose the place.
4. Sage's greaves: `10 + floor(level * 2 / 3)` Agility XP about every 10 ticks while running (moving 2
   tiles in a tick), before league multipliers (the Wiki's rule).
5. Shortcuts: five (removed 2026-10-10).
6. The Brimhaven Agility Arena: built in its own map (below); you asked for the real arena on 2026-10-09.
7. Grace's prices from the Wiki.

## What is built

### Courses (removed 2026-10-10)
The six flat courses (49 obstacle entries, generated into `configs/courses/*Course.kt`) and their
integration tests were deleted. What is left of the course side: the `Course` and `Obstacle` models,
`Courses` (an empty list), and the lap tracking, mark of grace and lap count code in `scripts/Agility.kt`.
To bring a course back, add it to `Courses.all`; its obstacles are placed at startup.

### Grace (`scripts/Grace.kt`, `configs/GraceConfigs.kt`)
- Grace stands at (2213, 2811). Shop on the cache's `roguesden_shop`, currency `mark_of_grace`
  (`currency.sym` line 2). Graceful hood 35, top 55, legs 60, gloves 30, boots 40, cape 40 marks (260 for
  the set, as on the Wiki), amylase packs 10. The shop prices by an item's value, so those pieces' value
  was set to their price in marks. "View Laps" and "Toggle Counter" work; the amylase pack's "Open"
  gives 100 crystals.
- The engine already does graceful's run-energy bonus (`graceful_restore_rate`) and the cache's weights
  do the rest.

### Shortcuts (removed 2026-10-10)
The five shortcuts (rope swing, log balance, two sets of stepping stones, obstacle pipe) were removed
from the isle. `configs/Shortcuts.kt` keeps only the step builders the arena crosses its obstacles
with, and `Agility.ShortcutUsed` is still published by the arena.

### Brimhaven Agility Arena (`scripts/Arena*.kt`, `configs/ArenaLayout.kt`, `configs/ArenaConfigs.kt`)
The real arena from the cache (map squares x 2752-2812, z 9536-9598, level 3 for the platforms and level 0
for the pits), not a copy. 25 pillars in a 5 x 5 grid, 40 crossings between neighbours (all of the Wiki's
obstacles and traps), 24 ticket dispensers. `ArenaLayout.kt` was generated from the cache's locs and
collision (pillar index = row * 5 + column, row 0 south).
- **Way in:** the arena's ground-level entrance, the bamboo hut from Brimhaven (2805-2812, 3189-3196), stands on
  the beach with its door at (2280, 2802) (`configs/ArenaHut.kt`; every piece is an offset from `ArenaHut.DOOR`, so
  changing that one coordinate moves the hut, the ladder, Izzy and Jackie). Cap'n Izzy (2279, 2805) takes 200 coins
  (`ArenaEntrance.FEE`; set it to 0 to drop the fee); the ladder at (2280, 2807) then goes down to the arena's last
  pillar (2805, 9589). The ladder there (2805, 9590) climbs out to (2280, 2806). Pirate Jackie stands inside too
  (2281, 2805). The hut is put up at startup by `Arena.placeEntrance` from the cache's own locs (walls, windows,
  doorway, picture, tables, hat stand, pots, plant, bookcase); the 2x2 beach bush at (2278, 2808) that stood in it
  is removed. Not copied: the wooden floor (map terrain, so it is sand) and the roof (the client only hides a roof
  over terrain tiles flagged for it, so a placed roof would cover the inside for good), so the hut is open to the
  sky. The beach slopes about one tile across it, so the walls follow the slope.
- **Obstacles** (`ArenaObstacles`): ledge 16 XP, log 12, balancing rope 10, monkey bars 14, hand holds 22 (level
  20), plank 6, rope swing 20, pillar 18, low wall 8: the Wiki's. A click walks to the nearer end and crosses
  by exact moves. The Wiki gives no chances of falling, so those are guesses by tier (`ArenaStats`: easy 170->252,
  medium 140->250, hard 110->245 out of 256 at levels 1->99); nobody falls at 99. A fall costs 5% of hitpoints
  + 2 (never killing) and drops the player into the pit under the pillar they left, where a rope climbs back
  to the platform. The low wall only stumbles back. Three planks join the pillars in two places; one is sound,
  re-picked every minute and drawn so (broken middles swapped in), the others drop the player (99 Agility or the
  Corner Cutter relic walks any).
- **Traps** (`ArenaTraps`): pressure pads (level 20, 26 XP), floor spikes (20, 24; nobody fails from 50),
  spinning blades (40, 28) and darts (40, 30; a hit drains 2 Agility) have an invisible wall in the middle of
  a corridor: stepping up to it jumps/dives across, or a failed roll hurts and throws the player back. The pads
  pay twice then nothing for 8 ticks. The timed blades (no experience) rise out of the floor for 3 of every 8
  ticks; anyone under one is hit and thrown clear.
- **Dispensers** (`Arena`): one of the 24 is marked at a time, a minute each, with a flashing hint arrow
  (new `HintArrows` in `api/player-output`). Tag it for an agility arena ticket, a voucher and 30 XP per ten
  levels (300 max); the Wiki's messages are used. Streaks (`Arena.Tagged`) work as before. Pirate Jackie (2281,
  2805) trades tickets for 345 XP each and sells the pirate hook for 800 vouchers.
- Left out: the Karamja glove bonuses and diary double tickets, the announcement of the first tag, the logout and
  teleport-out re-entry delays, the "Agility arena overlay" interface, and the Wiki's other shop stock (see
  `isle_of_souls_missing_items.md`). The trap mechanics (what a step onto a pad or the blade timings are) are a
  design of mine from the Wiki's wording; the animations used are guesses from the cache's names.

### Corner Cutter (`relics/LeagueCornerCutter.kt`, isle module)
- Sage's greaves pay running XP (decision 4) through a timer on every player.
- Every agility check succeeds (only the pipe can fail), a lap counts as two and gives 25% of the
  course's lap XP on top, and a mark of grace comes with a stack of 10,000 coins.
- The Wiki also doubles pyramid tops, hallowed marks, Brimhaven vouchers and Prifddinas shards: nothing
  on the isle gives pyramid tops, hallowed marks or shards, and the arena's vouchers are not doubled here.
  The skill asks through `Agility.Modifiers`, so it knows nothing of the relic.

### League tasks (`tasks/LeagueAgilityTasks.kt`)
After the 2026-10-10 removals there are 11 Agility tasks, 1,520 points: Easy: an arena ticket. Medium:
a mark of grace (kept for when marks have a source again). Hard: 30 arena tickets with no mistakes.
Elite: full graceful, level 99, 25m, 35m and 50m XP. Master: "Equip a Pirate Hook".
Removed with the courses and shortcuts (11 tasks, 540 points; dropped from `LeagueTaskList`, from the
client enum, which was renumbered to 470 keys, and from `struct.sym`/`varp.sym`): the Gnome, Draynor,
Varrock, Falador, Seers' and Ardougne laps (686, 459, 1522, 768, 688, 689), 10 Draynor and 10 Varrock
laps (460, 1523), 50 rooftop laps (389), and the two shortcut tasks (640, 1590). Counters left:
`isle_count_agility_arena_streak_30`.
The tasks that named a region have their area cleared (struct edits for 769, 638, 641, 1576).
Left out because the isle has no such course: Al Kharid, Canifis, Rellekka, Barbarian Outpost,
Werewolf, Wilderness, Shayzien, Prifddinas and the Agility Pyramid tasks.

While adding the counters I found 19 earlier ones (magic, thieving, hunter) missing from
`LeagueProgressVarps.all`, the list that syncs team counters at login, and added them.

## Tests
- Agility module (integration): every arena obstacle both ways, falls and ropes, planks, traps, blades, the fee and the ladders, dispensers and streaks.
- Isle module: the remaining agility tasks, the Corner Cutter relic (modifiers and running XP), edited structs.
- Pre-existing and left alone: `FightCaveWavesTest` (the wave file is Jad-only).

## Before it can be tried in the client
1. Restart the server and run `./gradlew packCache`: new varps (`agility_laps_*`,
   `agility_lap_counter_off`, four `isle_count_agility_*`), a currency (`mark_of_grace`,
   `brimhaven_voucher`), two timers, 13 task structs, the task enum and obj values.
2. `::agility grace` (or arena) teleports there; `::agilitykit` gives 300 marks, the graceful set and amylase packs.
3. Look at: whether obstacles read well at ground height, the arena's trap animations (jump, dive, spikes
   and saw twists are guessed from cache names), the flashing arrow over the marked dispenser, the hut
   ladder's facing (it should open to the south), and the "Agility arena ticket" voucher (it shows with the
   ticket's name; see the missing items doc). `::agility arena` takes you straight into the arena; the
   proper way is Cap'n Izzy then the ladder at (2280, 2807).
4. Raised rooftops were not tried (the collision map treats unallocated zones as blocked and the client's
   drawing of upper planes with no map data is unverified).

## Numbers to tune
`Agility.MARK_CHANCE`, `Agility.MARK_LIFETIME`, `Arena.LIT_TICKS`, `Arena.TICKET_XP`, `ArenaStats`, `ArenaEntrance.FEE`,
`LeagueCornerCutter.PAY_TICKS`, `GraceSpot.prices`.
