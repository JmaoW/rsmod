# Isle of Souls: the Inferno (implementation plan)

> **2026-10-10:** TzHaar-Mej-Jal and his "Exchange fire cape" option were removed from the lobby (the exchange is to be reworked closer to the real game). `InfernoUnlock` keeps `isUnlocked`, `sacrifice` and `tradeInCape`, but nothing calls them, so the Inferno entrance stays locked until the rework. Where this plan describes the gatekeeper dialogue, it is no longer built. The Inferno entrance loc on the lobby plaza (2209, 2859) was removed the same day; the entrance handler is on the loc type and answers any placement of it.

Written 2026-10-08 for review before any code. **Status: all nine steps done. What is left is yours: `packCache` and the client checklist below.** Numbers come from
the OSRS wiki (The Inferno, TzKal-Zuk, Ancestral Glyph and each monster's page, read 2026-10-08).
Anything the wiki does not give is marked **(guess)** and listed again in "Open numbers" so you can
tune it after the first client test.

## As built

### Step 0: generic wave runner (done 2026-10-08)

New package `...isleofsouls.waves`: `WaveState`, `WaveRun` (run state), `WaveDirector<R>` (enter, leave,
tick, sweep, wave timing, spawning, retargeting, win and clean-up; the game supplies `createRun`,
`startPlayer`, `spawnWave`, `onWaveTick`, `retargetExtras`, `engage`, `onWin`), `WaveFile`
(shared TOML loader, `Wave<M>`, `WaveList<M>`). The Caves now sit on it: `FightCaves` extends
`WaveDirector<FightCaveRun>`, `FightCaveRun` extends `WaveRun`, `FightCaveWaves` extends `WaveList`.
`FightCaves.waveLoader` lets tests supply their own waves.

Behaviour is unchanged: all 32 Caves integration tests (5 arena + 27 run) pass, and so do 15 of the 16
Caves unit tests plus 5 new `WaveFileTest` tests. **The one failing unit test is
`the shortened file loads with eight waves and Jad`: the committed
`fight_caves_waves_shortened.toml` holds only Jad (its comment still says "8 waves, then Jad").**
That was already failing before the refactor, and before it 14 of the run tests failed for the same
reason. They now use their own copy of the 8+Jad list (`TEST_SHORTENED_WAVES`), so they no longer
depend on the event file. Decide whether the Jad-only file is intended (for quick Jad testing) or
should go back to 8+Jad; I did not change it.

### Step 1: probe results (2026-10-08, from the cache)

- **The Inferno map exists: map square 35_83** (x 2240-2303, z 5312-5375). The template is
  `copyAllLevels(copyZoneX = 280, copyZoneZ = 664)` with 8x8 zones, as guessed.
- **The arena is one open rectangle: x 2257-2285 (29 wide), z 5329-5358 (30 tall).** Everything
  else on level 0 is blocked (rock and lava). No pillars, outcrops or blind spots are in the static
  collision, so the three pillars are placed by the script.
- **`inferno_exit` (loc 30283, 5x4) is on the map at (2269, 5325)**, covering x 2269-2273, z 5325-5328,
  right below the arena's south edge. Use it as the exit loc (no placement needed). The map also has a
  `inferno_collapsing_wall_safespot_state1` at (2270, 5363) and a `inferno_collapsing_prison_roof`
  (level 1) at (2267, 5368): that is Zuk's prison in the north wall, so Zuk (size 7) stands with his
  south-west tile at about **(2267, 5362)** **(guess; check in the client)**. The glyph (size 3) floats
  on the blocked rows just south of him, z about 5359-5361.
- **Cache npc stats match the wiki** (e.g. Jad 350 HP / 750 / 1020 / 480 / 510 / 1020, Zuk 1200 HP /
  350 / 600 / 260 / 150 / 400, Jal-Xil 125 HP). Every Inferno npc has `attackRange = 1`,
  `maxRange = 7`, `huntRange = 5`, `giveChase = true`, mode Wander, so an `InfernoNpcEdits`
  (`NpcEditor`, as `FightCaveNpcEdits`) must set `maxRange`, `attackRange` and `defaultMode = None`.
  The glyph `inferno_moving_safespot` (7707, "Ancestral Glyph") is a real size-3 npc with 600 HP.
- **Line of sight works with pillar locs, tested with the real ray caster.** A placed
  `inferno_safespot_100/75/50/25` loc sets `LOC | LOC_PROJ_BLOCKER` on its tile and a ray through
  that tile has no line of sight, while a ray beside it does. **But the loc types are 1x1.** A pillar
  that is 3x3 therefore needs either nine locs or one loc at the centre plus the other eight tiles'
  collision set by hand with `CollisionFlagMap` (and removed again at collapse). Check how the model
  looks in the client and choose then. HP is tracked by the script (the cache's "Rocky support" npcs
  have 10 HP and cannot be used for it).
- The arena is 29 by 30 with the player start near the south; the pillar positions are not in the
  data. First guess for the 3x3 pillars' south-west tiles: west (2261, 5342), east (2279, 5342),
  and south (2270, 5336), with the nibblers spawning at the middle (2270, 5343) **(guess, adjust
  after seeing it in the client)**.

### Steps 2 and 3: instance, entrance, unlock, waves (done 2026-10-08)

All in `content/areas/misc/isle-of-souls/.../inferno/`. 14 integration and 11 unit tests cover it; the
whole isle module still passes (every integration class, 0 failures).

| File | What it does |
| --- | --- |
| `Inferno.kt` | `InfernoArena` (template of map square 35_83, floor rectangle, start, plaza exit and entrance), `InfernoInstance`, `InfernoRun`, and the `Inferno` director over `WaveDirector` (settings, `waveLoader`; spawning and winning are still empty) |
| `InfernoWaves.kt` | `InfernoMonster` keys, `InfernoMode`, `InfernoWaves` (parse and load; boss wave must be Zuk alone, Zuk nowhere else, `firstJadWave`) |
| `InfernoSettings.kt` | `infernoMode` in `.data/league/event-settings.json` (default `full`), same file as the Caves |
| `InfernoUnlock.kt` | `isUnlocked`, `hasFireCape`, `sacrifice` (pack first, then the worn cape; one cape, once) and the gatekeeper dialogue `offerFireCape` |
| `InfernoScript.kt` | Registers the instance, entrance and exit options (the lobby entrance loc was removed 2026-10-10), the run timer, the send-off and win messages |
| `InfernoConfigs.kt` | `inferno_locs`, `inferno_timers`, `inferno_queues`, `inferno_vars`, and the `isle_inferno_unlocked` varp |
| `resources/.../inferno/inferno_waves_{full,shortened}.toml` | The 69 real waves and the 9-wave shortened list |

- **The wave table is checked against the wiki**: the generated 69 waves hold exactly the wiki's totals
  (210 Jal-Nib, 48 bats, 40 blobs, 36 melee, 34 rangers, 33 magers, 4 Jads, 1 Zuk), and a unit test
  keeps it that way, along with the nibbler counts and the "new monster the wave after a six-nibbler
  wave" rule.
- **The gatekeeper stays TzHaar-Mej-Jal**, option 3 "Exchange fire cape", handled in
  `FightCaveScript` (a loc or npc option can only have one handler) which calls `InfernoUnlock`. His
  talk-to text now explains the one-off cape.
- **The entrance is closed for now**: `InfernoScript.OPEN = false`, so players are told "The Inferno
  is not open yet" instead of walking into an empty arena. Flip it when step 7 is done.
- **New symbols, so `./gradlew packCache` is needed**: varp `isle_inferno_unlocked` (65410 in
  `varp.sym`), queues `inferno_enter` (35) and `inferno_win` (36), timer `inferno_tick` (17).
- **Entrance placement is a guess**: the hole is at (2209, 2859) on the plaza, beside the Caves' exit
  tile (2208, 2858); players leaving the Inferno land on (2209, 2858). Both tiles are open floor (a
  test checks it), but check the look in the client.
- `Inferno.waveLoader` lets tests supply their own waves, like `FightCaves.waveLoader`.
- **Not done in these steps**: the "Sacrifice a fire cape" task and the Inferno completion counters
  (task step), the extra-cape pet trade-in, and every monster.

### Step 4: the ordinary monsters (done 2026-10-08)

All in `.../inferno/`. 16 integration tests (`InfernoMonstersTest`) and 5 unit tests
(`InfernoBlobAiTest`) cover it; the whole isle module still passes (155 unit tests with only the old
Caves-file failure, every integration class green).

| File | What it does |
| --- | --- |
| `InfernoMonsters.kt` | `InfernoMonsterDef`s (size, attack rate, range, death anim) for the nibbler, bat, blob and its three splinters, melee, ranger and mager (plus the final-wave ranger and mager), `InfernoAttacks` (every `NpcAttack` with the wiki's max hits), `InfernoBlobAi.chooseStyle` |
| `InfernoCombatScript.kt` | The attack handlers and death for each monster (below) |
| `Inferno.kt` | Spawning (`InfernoSpawnPlanner`), blob split, burrow, revive, the kill event, per-monster state; `InfernoArena` gained the nibbler spawn, six spawn zones and the revive spot |
| `InfernoConfigs.kt` | npc, seq and spotanim names, and `InfernoNpcEdits` (chase leash and attack ranges; **needs `./gradlew packCache`**) |

- **Nibbler**: melee crush, max 4, every 4 ticks, always hits. Still attacks players only; the
  pillars (step 5) give it its real target.
- **Bat**: ranged, max 19, every 3 ticks, range 4. A hit that does damage drains 3 run energy
  and one level of a random combat stat, unless Protect from Missiles is on.
- **Blob**: every 6 ticks. Three ticks before attacking it reads the player's prayer and uses a
  style the prayer does not cover (ranged vs Magic, magic vs Missiles, random otherwise); next to a
  player with Protect from Melee off it bites one time in three. Max 29. On death it leaves a
  ranged, a magic and a melee splinter (15 HP, max 18, range 15 for the two shooters).
- **Melee (Jal-ImKot)**: slash, max 49. Burrows when its timer ends (first at 50 ticks, then every
  40-60); fighting resets the timer, but not in the first 30 ticks of its life. It stays under for 4
  ticks, comes up on a free tile touching the nearest player and waits 6 ticks before attacking.
- **Ranger**: ranged max 46, or crush max 19 when adjacent (half the time), every 4 ticks, range 8.
- **Mager**: magic max 70 or stab max 52 when adjacent (half the time), every 4 ticks, range 10.
  One attack in ten is a revive instead: it brings back one monster that died this wave (not a
  nibbler or a splinter), once each, at half HP at the centre-east spot, with a 4 tick pause before it
  attacks; the mager then waits 8 ticks.
- **Spawning**: the nibblers go together at the nibbler spawn; the others take one of six zones, the
  biggest first, with the zones shuffled each wave. Jad and Zuk are skipped until steps 6 and 7.
- **Guesses to tune**: spawn zones and the nibbler spawn, the blob's range (8), ranger and mager
  ranges (8 and 10, from the Caves), dig time (4 ticks), the revived monster's pause (4), the bat's
  stat drain (1 level), and whether a 0-damage bat shot drains (here it does not).
- **Shared runner change**: `WaveDirector.engage` now gets the run, and `fitsAt` is available to
  subclasses.
- The nibblers' pillar behaviour, the bat flying over pillars and larger monsters lining up their
  south-west corner are not done; the first two belong to step 5.

### Step 5: the pillars (done 2026-10-08)

`inferno/InfernoPillars.kt`, plus changes in `Inferno.kt`. 12 integration tests (`InfernoPillarsTest`) and
5 unit tests (`InfernoPillarTest`); the whole isle module still passes.

- **Three pillars of 255 HP**, 3x3, placed when a run is created. **Reworked 2026-10-08 after a client
  check** (the first version was one 1x1 loc per pillar with hand-set collision; it showed up in the
  wrong places and its model was cut off by the floor, because a 1x1 loc holding a 3x3 model is
  drawn behind the neighbouring floor tiles). Now it is built the way the cache has it:
  - The pillar is `inferno_safespot1/2/3` (locs 30353-30355): a **3x3 loc with no model of its own**
    that resolves through a varbit (`inferno_safespot1/2/3_health`, 5655-5657) to one of four rocks
    (`inferno_safespot_100/75/50/25`, 64 damage each). The varbit holds the **damage taken** (0-255);
    every player in the run is sent it on entry and on each bite (`InfernoPillars.showTo`). The loc
    type's own collision gives all nine tiles `LOC | LOC_PROJ_BLOCKER`, so no hand-set flags. Tests
    check walking, line of sight (rays through every row and beside it) and the varbit values.
  - On top of it stands an invisible 3x3 npc, `inferno_invisible_3x3` ("Rocky support", 255 HP, no
    options). **It is what the nibblers hit**, so the hit splats and the **health bar** show on the pillar.
    Its hitpoints are reset from the pillar's before each bite so the bar cannot drift.
  - At zero the loc, the collision and the health npc go and `inferno_safespot_dying` (npc 7710) plays
    the cache's collapse animation (`safe_spot_distructible_pillar_collapse`) for its length, then
    removes itself.
- **Positions** read off the wiki screenshot (the user's), accurate to about a tile: south-west tiles
  **(2257, 5351)** against the west wall, **(2274, 5353)** towards the north-east and **(2267, 5337)**
  left of the middle; edit `InfernoArena.pillarSouthWest`. The nibbler spawn (2267, 5347) is the
  middle of the three, and the spawn zones, Jad spots and revive spot were moved off the pillars (a
  unit test checks that nothing spawns on one).
- **Nibblers** ignore the players while any pillar stands (they stay in no-attack mode). **All of a
  wave's nibblers go for the same pillar**: one standing pillar is picked at random when the wave
  arrives (`InfernoRun.nibblerPillar`) and again only if it falls. Each walks to the nearest free tile in
  the 16-tile ring round it (so they spread out) and bites it for 2 to 4 every 4 ticks. They
  **do not retaliate when hit** while a pillar stands (a nibbler-only `com_retaliate_player` handler in
  `InfernoCombatScript` overrides the engine's default, and its fight handler refuses to attack). When the last
  pillar falls they turn on the players and attack as before.
- **Collapse**: anyone next to the pillar (the ring round it, diagonals included) takes 49 typeless
  damage, players and monsters alike (a nibbler beside it dies). The wiki disagrees with itself
  (49 or half health), and gives nothing for monsters: both are guesses.
- **The first wave with a Jad** brings down every pillar still standing as it starts (wave 67 in
  the full mode, wave 7 in the shortened one), with the same collapse damage.
- When a run ends its pillars (locs, health npcs, rubble) are removed, without hurting anyone.
- Safespots need nothing else: the npc attack range check already requires line of sight, and npcs
  step straight at a target and stop at walls, so a monster behind a pillar cannot attack.
- **Not done**: no sound for the pillars, and they are not yet a combat-achievement hook.
- `WaveDirector` gained an `onRunEnded` hook.

### Step 6: the Jad waves (done 2026-10-08)

In `Inferno.kt`, `InfernoMonsters.kt`, `InfernoCombatScript.kt` and `InfernoConfigs.kt`. 11 integration
tests (`InfernoJadTest`); the whole isle module still passes.

- **JalTok-Jad** (350 HP, size 5): magic and ranged max 113 on the Fight Cave's telegraphed timing
  (the hit lands 5 ticks after the animation, a carried-over guess), and a stab max 113 when a player is
  adjacent. From a distance rocks or fire evenly; adjacent, bite, rocks or fire evenly (as the Caves).
  Range 14. He hits **every player in the instance** at once (`TargetingStyle.All`), each with their own
  roll and prayer. The attack graphics are the Caves' Jad graphics.
- **Lone Jad (wave 67 full, 7 shortened)**: attacks every 8 ticks (4 after a bite), five healers.
  Stands at `InfernoArena.jadSingle`.
- **Triple Jad wave (68 full, 8 shortened)**: three Jads in a triangle (`InfernoArena.jadTriangle`),
  each attacking every **9** ticks, started **3 ticks apart**, so one prayer flick per style covers all
  three; a bite does not break the cycle. Three healers each.
- **Yt-HurKot healers**: appear next to their Jad (spread round his edge) when he drops **below 175**
  HP, once only. A healer heals its Jad for 15 to 24 every 4 ticks while standing next to him, and is
  not aggressive until hit: then it goes for the player and stops healing. They never respawn.
- **A dead Jad's healers go with him** (a guess; the wiki does not say), and a Jad is never revived.
  The wave ends when the last Jad is dead.
- The first Jad wave already brought down the pillars (step 5).
- **Guesses to tune**: the Jad spawn positions, the first attack coming 6 ticks after the wave starts,
  Jad's range, the hit delay (5), and healers vanishing with their Jad. A healer that walks away after
  being hit does not go back to healing (the wiki says it does).
- **`packCache`**: Jad and healers need `InfernoNpcEdits` too (already in the same pack).
- Zuk's own Jad (480 HP phase) and its three healers use the `_final` npc types, which are defined
  here and handled by the same code (step 7 only has to spawn them).

### Step 7: TzKal-Zuk (done 2026-10-08)

`inferno/InfernoZuk.kt` (the fight) plus changes in `Inferno.kt`, and a new public
`NpcAttacks.rollDamage(npc, target: Npc, attack)` in `api/combat/combat-scripts`. 14 integration tests
(`InfernoZukTest`) and `InfernoFullRunTest`, which plays the real shortened Inferno from wave one to
Zuk's death. The whole isle module still passes.

- **Start**: the last wave puts the players on the north edge of the arena (`InfernoArena.zukStart`),
  brings TzKal-Zuk (1200 HP, size 7) out of the north wall at `zukSouthWest` and the Ancestral Glyph
  (600 HP, size 3) onto the lava row in front of the players.
- **Zuk's attack**: every 10 ticks (7 enraged) a typeless shot at every player, up to 148; no prayer
  stops it. The roll is uniform 0-148 (the wiki gives only the maximum). The hit lands when the
  projectile does.
- **The glyph** walks along the lava row a tile a tick and rests 5 ticks at each end. A player is
  **covered** when they stand in the north three rows of the arena within two tiles of its middle (a 5 by
  3 area, wiki): Zuk's shot then hits the glyph and does nothing. At 0 HP the glyph is destroyed, with a
  message, and nobody is covered after that.
- **Damage to the glyph, as asked: the monsters use their normal damage calculation.** A monster
  attacking the glyph makes its usual attack (Jal-Xil ranged max 46, Jal-Zek magic max 70, Jad magic or
  rocks max 113), rolls the usual accuracy against the glyph's defence (the new `rollDamage`, the same
  npc-versus-npc formulas the engine already has) and does 0 to its max hit, at its usual pace (4, 4 and
  8 ticks). **The result is fast:** a Jal-Xil and a Jal-Zek left alone break the 600 HP glyph in about
  44 ticks (roughly 26 seconds), and a Jad speeds it up. The first set arrives 30 ticks into the fight,
  so players have to draw the monsters off at once. This is a balance setting to revisit in the client:
  the cheapest lever is the glyph's HP (`inferno_moving_safespot` in the cache has 600).
- **Sets**: a Jal-Xil and a Jal-Zek (the `_final` types) arrive behind the players every 350 ticks
  (3:30), the first 30 ticks in. Each attacks the glyph until a player hits it (tracked by watching for
  lost health, so healing afterwards does not undo it), then goes for the players like any monster.
  When the glyph is gone every one of them turns on the players.
- **Phases**: at **600** HP the set timer stops and gets a one-off 175 ticks (1:45) added; at **480**
  Zuk's own Jad (`jad_final`, 8 tick cycle, starts on the glyph) arrives behind the players and the timer
  carries on; below **240** Zuk attacks every 7 ticks and **four Jal-MejJak** appear on the lava in front
  of him. Jad's three healers always come up on his north side.
- **Jal-MejJak**: an unhurt one heals Zuk 15-24 every 3 ticks. One a player has hit stops healing and
  throws lava every 3 ticks: a volley that lands 2 ticks later on a player's tile and three random
  tiles within 2, 5-10 typeless damage to anyone on them. The pattern, delay and the graphic
  (`wild_falloff_meteor_blast`) are guesses.
- **Zuk's death wins the run**: everything else in the arena dies with him and everyone is sent out after
  12 ticks, as in the Caves. Rewards come in step 8.
- **Guesses to tune**: Zuk's start position and the lava row, the glyph's speed (1 tile a tick) and
  path, the players' start tiles, the sets' and Jad's spawn spots, the 30 tick first set and 12 tick first
  attack, MejJak's lava, the hit delay (the projectile's own), and that Zuk's damage is not tick-eatable
  as the wiki says (it is queued with the usual cap).
- **Not done**: the rock outcrops (blind spots) are not in the map data, so there are none; the glyph
  and healers have no sounds; Zuk cannot be reached by melee only because his tiles are lava.
- **The entrance is still closed** (`InfernoScript.OPEN = false`): winning gives nothing yet. Step 8
  gives the rewards and opens it.

### Step 8: rewards, tasks and the open entrance (done 2026-10-08)

`inferno/InfernoRewards.kt` (`InfernoRewards`, `InfernoCompletions`), plus small changes in
`InfernoUnlock.kt`, `Inferno.kt`, `InfernoScript.kt`, the task list and the progress varps. 7 integration
tests (`InfernoRewardsTest`); the whole isle module still passes.

- **The entrance is open.** The `OPEN` switch is gone: a player needs the fire cape sacrifice, nothing
  else. Winning needs the whole fight, so the Inferno is now playable from the gatekeeper to the cape.
- **Win rewards**, for every player inside when Zuk dies: an **infernal cape** (`infernal_cape`,
  untradeable in the cache), **16,440 Tokkul**, a **1 in 100 chance of Jal-Nib-Rek** (`infernopet`; the
  "funny feeling" message), a congratulation, the gatekeeper's win line, and a server announcement
  naming the team (with "(shortened)" in that mode). A player who left earlier gets nothing. Zuk's death
  is now only processed once, so nothing is paid twice.
- **Extra capes**: once unlocked, the gatekeeper's "Exchange fire cape" with an infernal cape in the
  pack offers to take it for another 1 in 100 pet roll.
- **Tasks added to the list** (ids from the cache, text unchanged): 661 *Sacrifice a Fire Cape to Access
  the Inferno* (elite), 667 / 668 *Complete the Inferno 5 / 10 Times* (elite), 669 *15 Times* (master),
  654 *Equip an Infernal Cape* (master, from `LeagueGearTasks`). The combat mastery task *Defeat
  TzKal-Zuk* (slot 10) is completed by a win. The sacrifice completes 661.
- **Team counters**: the "N times" counters are shared by a team, so a two-person run would count twice.
  The Inferno counts a run **once per team**. The Fight Cave still counts it once per member, so a
  two-person team finishes "Complete the Fight Caves 5 Times" in three runs; change
  `FightCaveRewards` the same way if that is not wanted.
- **New symbols, so `./gradlew packCache` is needed** (on top of the earlier ones): varps
  `isle_count_inferno_5` / `_10` / `_15` (65409 down to 65407 in `varp.sym`).
- Not added: the Inferno combat achievements and Tzhaar-Ket-Rak's challenge tasks (no counterpart).

## Client checklist (two accounts, same team)

Nothing below has been seen in a real client yet. Stop the server and run `./gradlew packCache` first
(new varps, queues, timer, npc ranges), then start it, put two accounts in one team, and give one of them
a fire cape (win the Caves, or any item command you have). Set `"infernoMode": "shortened"` in
`.data/league/event-settings.json` for a quick run; leave it out for the real 69 waves.

**1. Entrance and unlock**
- [ ] The hole (`inferno_entrance_op`) on the lobby plaza at (2209, 2859) is visible, on open floor, not
  inside the Caves' wall, and "Jump-in" works. Without the sacrifice you get the "give up a fire cape"
  message.
- [ ] TzHaar-Mej-Jal's talk-to text mentions the one-off cape. "Exchange fire cape" takes **one** cape
  (from the pack first, then the worn slot), says so, and completes the sacrifice task. A second visit says
  the Inferno is open and takes nothing.
- [ ] With an infernal cape in the pack the same option offers the hand-in (pet roll); the cape goes.

**2. The arena and the way out**
- [ ] You arrive at the south edge in front of the exit; the exit loc ("Exit", "Quick-exit") works and puts
  you on the plaza at (2209, 2858). Home teleport and spell teleports also leave the run.
- [ ] The wave messages read "Wave: N" and "Final Challenge!"; a death sends that player to the lobby, and
  they can walk back in while a teammate keeps the run alive.

**3. The pillars (most uncertain visually)**
- [ ] Three pillars stand in the wiki's places (west wall in the north, north-east, left of the middle),
  whole, standing on the floor and not cut off by it. They are 3x3 locs now; if they still look wrong
  tell me what you see.
- [ ] When a nibbler bites, a hit splat and a **health bar** appear on the pillar, and the rock changes at
  three quarters, half and a quarter health; at zero the collapse animation plays and it is gone.
- [ ] Nibblers spawn in the middle of the three, **all go to the same pillar**, spread round it, bite it and
  ignore you; when the last pillar goes they come for you.
- [ ] Standing behind a pillar, monsters with a ranged or magic attack cannot hit you (they stay stuck at
  its corners). Walking round a pillar is blocked on all nine tiles.
- [ ] A collapse hurts (49) anyone next to it.
- [ ] Every monster flinches when you hit it (Jal-Zek has no flinch animation in the cache, so it does not).

**4. The ordinary monsters** (each should show an attack animation, projectile and death animation)
- [ ] Bat: shoots from about 4 tiles; run energy and a combat level drop unless Protect from Missiles.
- [ ] Blob: switch prayers and watch it attack in the other style a few ticks later; it bites when you
  stand next to it without Protect from Melee; it leaves three splinters.
- [ ] Melee: digs down (animation), comes up next to you and waits before hitting; it does not dig while
  fighting.
- [ ] Ranger and mager: attack from a distance, melee when next to you. The mager sometimes revives a
  dead monster (half health, centre-east).
- [ ] Spawn spots look sensible and nothing spawns inside a wall or on a pillar.

**5. The Jad waves**
- [ ] Lone Jad: telegraphed fire and rocks, prayer blocks them, five healers appear north-ish of him when
  he drops under half, heal him, turn on you if hit.
- [ ] Triple Jad: three Jads in a triangle attack 3 ticks apart, so one flick per style covers all three;
  each has three healers; killing a Jad removes its healers.
- [ ] Both players are hit by every Jad attack, each with their own prayer.

**6. TzKal-Zuk**
- [ ] You are put on the north edge; Zuk comes out of the wall at the guessed spot (size 7, check he is
  not cut off by the wall) with his spawn animation; the glyph is visible on the lava in front of you.
- [ ] The glyph slides east-west, a tile a tick, and rests at each end. **Standing behind it is safe**: the
  5-wide, 3-deep zone matches what you can see. Standing outside it, Zuk's shot hurts (up to 148).
- [ ] The first Jal-Xil and Jal-Zek arrive about 30 ticks in, behind you, and shoot the glyph. **They break
  it in about half a minute** with their normal damage; draw them off by hitting them (they then attack you).
  A message tells you when the glyph is destroyed.
- [ ] At 600 health no new set comes for a long while; at 480 Zuk's own Jad arrives, with three healers on
  his north side; sets resume.
- [ ] Below 240 Zuk fires faster and four Jal-MejJak appear on the lava in front of him, healing him until
  hit; once hit they throw lava (check the graphic and the damage, 5-10).
- [ ] Killing Zuk ends the run: everything dies, you get the rewards, and you are sent out a moment later.

**7. Rewards and tasks**
- [ ] Both players inside get an infernal cape, 16,440 Tokkul and the announcement names the team. A player
  who left gets nothing. The pet appears (1 in 100) with the "funny feeling" message.
- [ ] Tasks: sacrifice (661), complete 5/10/15 times (667-669, **counted once per team**), equip an infernal
  cape (654), the combat mastery "Defeat TzKal-Zuk" (slot 10). The Fight Cave counters are now once per team
  too.

## Numbers to tune after the client check

| What | Where | Now | Why it is a guess |
| --- | --- | --- | --- |
| Pillar positions (3x3, south-west tile) | `InfernoArena.pillarSouthWest` | (2257,5351) (2274,5353) (2267,5337) | read off the wiki screenshot, about a tile out |
| Nibbler spawn, monster spawn zones, revive spot | `InfernoArena` | see file | wiki gives none |
| Jad spawns (lone, triangle) | `InfernoArena.jadSingle`, `jadTriangle` | (2268,5348); triangle | wiki: "triangle round the centre" |
| Zuk position, glyph row, players' start, set and Jad spots, MejJak spots | `InfernoArena` (`zuk*`, `glyphWest`, `mejjakSpots`) | see file | taken from the map's prison cell |
| Glyph speed, rest, travel | `InfernoZuk.SHIELD_TICKS_PER_TILE`, `SHIELD_PAUSE`, `SHIELD_TRAVEL` | 1, 5, 26 | wiki: "walking pace", 5 ticks |
| Glyph hit points | cache npc `inferno_moving_safespot` | 600 | wiki; with normal damage it breaks in ~26 s |
| First set, first Zuk attack | `FIRST_SET_DELAY`, `FIRST_ATTACK_DELAY` | 30, 12 ticks | wiki gives none |
| Zuk's damage roll | `InfernoZuk.zukAttacks` | uniform 0 to 148 | wiki gives only the maximum |
| Lava pattern, delay, graphic | `InfernoZuk.lavaStrike`, `LAVA_*`, `inferno_spotanims.lava_splash` | player tile + 3 near, 2 ticks | wiki: "like the Chaos Temple effect" |
| Jad hit delay, range, first attack | `InfernoAttacks.JAD_HIT_DELAY`, `InfernoNpcEdits.JAD_RANGE`, `Inferno.JAD_FIRST_ATTACK` | 5, 14, 6 | carried over from the Caves |
| Ranger, mager, blob ranges | `InfernoNpcEdits` | 8, 10, 8 | wiki gives none |
| Dig time, revived monster pause, bat stat drain | `Inferno.DIG_TICKS`, `REVIVED_ATTACK_DELAY`, `InfernoCombatScript.BAT_STAT_DRAIN` | 4, 4, 1 level | wiki gives none |
| Pillar collapse damage | `InfernoPillars.COLLAPSE_DAMAGE` | 49 (monsters too) | wiki says 49 and "half health" |
| Healers vanish with their Jad; hit healer does not return | `Inferno.removeHealersOf`, `retargetExtras` | as built | wiki unclear / differs |
| Pet odds, Tokkul | `InfernoRewards` | 1/100, 16,440 | wiki (no Slayer task to improve the pet) |

## Known gaps

- No sounds for the pillars, monsters, glyph or healers. The defend (flinch) animations come from
  `InfernoNpcEdits` (a `defend_anim` param per npc, needs `packCache`); the Jal-Zek and the glyph have none.
- The rock outcrops (blind spots) of the Zuk platform are not in the map data, so there are none.
- Zuk's damage can be tick-eaten (the wiki says it cannot); healers that were hit do not go back to
  healing when you walk away; no Slayer task, Karamja diary bonus, banned items or 30 minute item timer.
- The Inferno combat achievements and TzHaar-Ket-Rak's challenges have no tasks.
- `fight_caves_waves_shortened.toml` is Jad-only while its comment says 8 waves and Jad; one unit test
  (`the shortened file loads with eight waves and Jad`) fails because of it. Decide which you want.
- An intermittent test-run stall: the first run of a newly added integration class sometimes fails with
  "Could not stop all services" and passes on the immediate rerun.

**Fight Cave completion counter fixed (2026-10-08).**

`FightCaveRewards` now counts a win once per team, like the Inferno (`FightCaveCompletions.completed`
takes `countRun`). Before, two teammates in a run each added one to the team's shared counter, so
"Complete the Fight Caves 5/10 Times" finished in 3 and 5 runs. The shortened-cave win test checks it.

---

**Everything from here down is the original plan (written before the build).** It is kept for the wiki
numbers and the reasoning; where it differs from "As built" above, "As built" wins.

## Decisions (all confirmed by the user, 2026-10-08)

| Topic | Decision |
|-------|----------|
| Modes | **Both**, like the Caves: the real 69 waves and a shortened list, switched by `.data/league/event-settings.json` |
| Pillars | Yes: 3 pillars with 255 HP, nibblers eat them, they block line of sight and collapse in stages |
| TzKal-Zuk | **Full mechanic**: moving shield, minion sets on a timer, Jad at 480 HP, healers and enrage at 240 HP |
| Entry | The **first** fire cape sacrifice unlocks the Inferno for that player **forever**. Later runs cost nothing |
| Rewards | Infernal cape, Tokkul, pet roll, as in the real game |
| Team | Up to two players per run (the isle's team instance), **no stat scaling** (same as the Caves) |
| Death | A dead player respawns in the lobby and can walk back in; the run continues for whoever is inside |
| Tasks | Wired at the end (see "Tasks") |

Deliberate differences from the real game (the real game is solo, no teleporting): two-player teams, and
teleports out are allowed (the magic plan says players may bail out of boss runs), which counts as
leaving the run.

## What already exists

| Piece | Where | Notes |
|-------|-------|-------|
| Wave runner (cave-specific) | `fightcaves/FightCaves.kt` (412 lines), `FightCaveRun.kt`, `FightCaveWaves.kt`, `FightCaveSpawns.kt`, `FightCaveMonsters.kt` | Enter/leave, sweep, wave timing, retargeting, healers, win, clean-up. **Not generic yet**: monster keys, spawn points, Tz-Kek split and Jad healers are hard-coded |
| Gatekeeper | `FightCaveScript.kt`: TzHaar-Mej-Jal on the lobby plaza, option 3 "Exchange fire cape" is a stub | Becomes the sacrifice option |
| NPC attacks | `api/combat/combat-scripts`: `NpcAttack`, `NpcAttacks`, `NpcTargeting` (`One / Alternating / All`) | Projectile + delayed hit, accuracy, max hit, protection prayers. Docs: `isle_of_souls_npc_combat.md` |
| Team instances | `instances/TeamInstances`, `TeamInstanceType`, `InstanceTargetScope` | Copies a map template per team |
| Line of sight for npcs | `NpcInteractionProcessor.isValidApRange` casts a ray from the target to the npc (`RayCastValidator`, `BLOCK_PLAYERS`) | **Good news for pillars**: an npc with an attack range already needs line of sight to attack. A pillar loc with the right collision flags blocks it with no engine change. To verify in step 1 |
| Typeless damage | Used by the Tz-Kek recoil | Zuk's attack and the pillar collapse are typeless |
| Rewards, announcements, tasks | `FightCaveRewards`, `LeagueAnnouncer`, `LeagueTaskTracker` | Reused |
| Cache | See "Cache data" | Npcs, locs, anims, projectiles and the cape/pet items are all present |

## Step 1 findings to check before writing monsters (a throwaway test, like `FightCaveArenaTest`)

1. **The Inferno map exists in the cache.** The real Inferno is map square 35_83 (region 9043):
   x 2240-2303, z 5312-5375, so the template is `copyAllLevels(copyZoneX = 280, copyZoneZ = 664)` with
   an 8x8 block of zones, exactly like the Caves' `copyZoneX = 296, copyZoneZ = 632` for square 37_79.
   **(guess from the region id; confirm by reading the cache's collision map.)**
2. Print the collision flags of the arena to find: the walkable area, the pillar positions, the
   nibbler spawn point (middle of the three pillars), the north wall where Zuk stands, the lava strip
   the shield floats on, and the player's start tile. The Caves needed this too: **npcs have no
   pathfinding** (they step straight at a target and stop at walls), so every spawn tile must be
   checked by simulating a walk.
3. Check that the pillar locs (`inferno_safespot_100/75/50/25`, ids 30284-30287) carry collision that
   stops `RayCastValidator.hasLineOfSight`. If they do not, add the flag in code when the pillar is
   placed (and note that region copies only keep `BLOCK_WALK`/`ROOF` flags and static locs: the pillars
   may need to be placed as locs at run start in any case).
4. Check the cache stats of the Inferno npcs (below) and which animation, sound and projectile ids are
   configured. The Caves plan found the cache had stats but no attack anims in `npcs.toml`; expect the
   same, and the same fix (`npcs.toml` plus an `NpcEditor` class, **needs `./gradlew packCache`**).

## Step 0: make the wave runner generic (do this first)

The Caves were built so the Inferno could reuse them, but the runner was never split out. Do the
split first, with the Caves' 40 tests (24 integration, 16 unit) as the safety net. **Risk: you have
not seen the Caves in a real client yet.** The refactor must be behaviour-preserving; do not change any
Caves number while doing it, so a Caves bug found later is not blamed on the refactor.

Proposed shape (names are suggestions):

| New type | Takes from | Holds |
|----------|-----------|-------|
| `WaveRun<M>` | `FightCaveRun` | instance, mode, wave list, state (`WaitingForNextWave / InWave / Won`), wave number, monsters, timers (`nextWaveAt`, `leaveAt`), `lastTick` |
| `WaveDirector` (abstract) | `FightCaves` | enter / leave / tick / sweep, `advance`, `spawn`, `freeSpot`, `retarget`, `engage`, `clearMonsters`, `win` |
| hooks on the director | the cave-only bits | `spawnWave(run)`, `onMonsterDied(run, npc)`, `onTick(run)` (healers, Jad, Zuk, pillars), `onWin(run)` |
| `WaveFile` + `WaveMonsterKeys` | `FightCaveWaves` | loader and validator taking the set of valid monster keys, so each game supplies its own |

The cave becomes `FightCaveDirector : WaveDirector` and the Inferno `InfernoDirector : WaveDirector`.
Both register a `TeamInstanceType` (`fightcave`, `inferno`); a player can only be in one.
The `FightCaveMode` / settings file grows an `infernoMode` key with the same `full | shortened` values
(default `full`, like the Caves). A run keeps the mode it started with.

## The entrance and the unlock

- **Gatekeeper**: TzHaar-Mej-Jal (already on the plaza, `onOpNpc3`) becomes the Inferno's gatekeeper as
  well. (The wiki's TzHaar-Ket-Keh is the real one; the isle already uses Mej-Jal for the Caves, so
  keep one NPC.)
  - Talk-to: the dialogue already explains the Inferno; change the "not open yet" text.
  - "Exchange fire cape" (option 3): if the player has **not unlocked** the Inferno, take **one fire
    cape** from the inventory or the worn cape slot, set the unlock, and say so. If they have
    already unlocked it, offer the extra-cape trade (below) or say they can enter.
  - **Extra capes**: the wiki lets players hand in unwanted infernal capes for another 1/100 pet chance.
    Include it as a second dialogue branch; it is cheap and uses the pet roll already written.
- **The unlock is a persistent flag**, a new varp (`inferno_unlocked`, hand-added to `varp.sym` like the
  other league varps, so **packCache is needed**). Checked on entry; entry without it says the TzHaar
  will not let you in until you have shown a fire cape.
- **Entrance loc**: `inferno_entrance_op` (30282, with an "Enter" option; `inferno_entrance_noop` 30281
  is the same without one). It goes on the lobby plaza near the Caves entrance on free floor (the
  plaza is x 2206-2209, z 2857-2860 plus a strip x 2196-2215 at z 2856-2857; the Caves' mouth covers
  x 2207-2209, z 2861-2862). **Pick the tile with a collision probe**, then check it in the client.
  Placed from a `PluginScript` at startup with `LocRepository.add`, same as the Caves, so no repack.
- **Exit loc**: `inferno_exit` (30283) inside the arena; "Leave" sends the player to the plaza, like the
  Caves' exit.
- The player-facing restrictions that do **not** apply: teleports are allowed (see above), purple
  sweets, slayer task for Zuk, Karamja diary Tokkul bonus, the 30 minute item timer.

## The waves

### File format

Same shape as the Caves: `inferno_waves_full.toml` and `inferno_waves_shortened.toml` under
`src/main/resources/.../inferno/`, `[[wave]]` with `number`, `monsters = [...]` and optional
`boss = true`. Keys: `nibbler, bat, blob, melee, ranger, mager, jad, zuk`. The loader rejects an
unknown key, an empty wave, numbers that do not rise by one, and a file whose last wave is not the only
boss wave. The shared loader from step 0 does this.

### Full: the real 69 waves (wiki)

Written as `N` = nibbler. Every wave has 3 nibblers unless it says otherwise. Counts: 17 + 17 + 15 + 17 + 3.

```
1  N B           2  N BB          3  6N            4  N X          5  N X B
6  N X BB        7  N XX          8  6N            9  N M          10 N M B
11 N M BB        12 N M X         13 N M X B       14 N M X BB     15 N M XX
16 N MM          17 6N
```
(B = bat, X = blob, M = melee, 3N each unless shown. The wiki's three-way pattern repeats below with R = ranger.)
```
18 N R           19 N R B         20 N R BB        21 N R X        22 N R X B
23 N R X BB      24 N R XX        25 N R M         26 N R M B      27 N R M BB
28 N R M X       29 N R M X B     30 N R M X BB    31 N R M XX     32 N R MM
33 N RR          34 6N
```
(G = mager, "mage" in the wiki, shown as G here to avoid clashing with M = melee.)
```
35 N G           36 N G B         37 N G BB        38 N G X        39 N G X B
40 N G X BB      41 N G XX        42 N G M         43 N G M B      44 N G M BB
45 N G M X       46 N G M X B     47 N G M X BB    48 N G M XX     49 N G MM
50 N G R         51 N G R B       52 N G R BB      53 N G R X      54 N G R X B
55 N G R X BB    56 N G R XX      57 N G R M       58 N G R M B    59 N G R M BB
60 N G R M X     61 N G R M X B   62 N G R M X BB  63 N G R M XX   64 N G R MM
65 N G RR        66 N GG
67 1 Jad (5 healers at half HP)
68 3 Jad (3 healers each at half HP; attack 9 ticks, 3 ticks apart)
69 TzKal-Zuk (boss = true)
```
New monsters are introduced the wave after a six-nibbler wave (blob 4, melee 9, ranger 18, mager 35).
A second copy of a type only appears once the earlier types have doubled. The loader's checks should
include "nibblers are 3, or 6 on waves 3, 8, 17, 34, and none from 67 on".

### Shortened: proposed list (edit freely)

Short enough for the event, but every mechanic appears once. Pillars matter in the first six waves.

| Wave | Monsters |
|------|----------|
| 1 | 3 nibblers, bat |
| 2 | 3 nibblers, blob, bat |
| 3 | 3 nibblers, melee, blob |
| 4 | 3 nibblers, ranger, melee |
| 5 | 3 nibblers, mager, ranger |
| 6 | 3 nibblers, mager, ranger, melee, blob |
| 7 | 1 Jad (5 healers), pillars gone |
| 8 | 3 Jads (3 healers each, 9-tick cycle, 3 ticks apart) |
| 9 | TzKal-Zuk (`boss = true`) |

Both Jad waves are in the shortened list (decided 2026-10-08).

### Wave rules (both modes)

- Pacing as the Caves: first wave 10 ticks after the first player enters, 8 ticks between waves, the
  message "Wave: N" and "Final Challenge!" for the boss wave.
- **Pillars**: they exist from wave 1. At the start of the **first wave that contains a Jad**, every
  surviving pillar is destroyed (it collapses, see below). This is wave 67 in full mode, wave 7 in
  shortened.
- Spawn points: not given by the wiki. Nibblers spawn together in the middle between the three
  pillars (wiki). Other monsters use a spread of free tiles around the arena edge, picked from the
  collision probe, one cluster per side, shuffled per wave **(guess)**. Wave 68's three Jads spawn in a
  triangle around the centre (wiki).
- The monsters hunt the nearest player (the Caves' rule). Larger monsters try to line up their
  south-west corner with the target when they cannot attack (wiki); the engine's own approach does
  this well enough; only check it in the client.

## The monsters

Cache ids are from `npc.sym`. Stats are from the wiki (HP / attack / strength / defence / magic /
ranged). `NpcAttacks` already rolls accuracy from the npc's stats, so the cache values are used if
they match; otherwise an `NpcEditor` edit sets them. Max hits are fixed per attack (`NpcAttack.maxHit`).

| Monster | Cache npc | Size | HP and stats | Attacks | Special |
|---------|-----------|------|--------------|---------|---------|
| Jal-Nib (nibbler) | `inferno_nibbler` 7691 | 1 | 10 HP; def 15, mag 15 | crush, max 4, every 4 ticks, **always hits** | Walks to a random pillar and attacks it (2-4 per hit). No pillars left: attacks players. Not aggressive to players while a pillar stands |
| Jal-MejRah (bat) | `inferno_creature_harpie` 7692 | 2 | 25 HP; def 55, mag 120, ranged 120 | ranged, max 19, every 3 ticks, range 4, projectile `inferno_harpie_proj` (1382) | Each hit drains 3 run energy and slightly drains the player's combat stats (and boosts the bat's); **Protect from Missiles stops both**. Drain amounts **(guess: 1 level on a random combat stat per hit)** |
| Jal-Ak (blob) | `inferno_creature_splitter` 7693 | 3 | 40 HP; att 160, str 160, def 95, mag 160, ranged 160 | Ranged, magic, crush; max 29; every 6 ticks | 3 ticks before each attack it reads the player's protection prayer and **attacks with a style the prayer does not cover** (no prayer: random). If a player is adjacent and not on Protect from Melee, it may melee. On death it splits into three (below) |
| Jal-AkRek-Xil / -Mej / -Ket (blob splits) | `inferno_creature_splitter_range` 7695 / `_mage` 7694 / `_melee` 7696 | 1 | 15 HP, level 70; def 95 | Ranged (range 15), magic (range 15), crush; max 18; every 4 ticks | Spawn at the blob's tile when it dies. Projectiles: `inferno_babysplitter_range` 1379, `_mage` 1381; blob's own: `inferno_splitter_range` 1378, `_mage` 1380. Not revived by Jal-Zek |
| Jal-ImKot (melee) | `inferno_creature_melee` 7697 (`_small` 12594) | 4 | 75 HP; att 210, str 290, def 120, mag 120, ranged 220 | slash, max 49, every 4 ticks | **Burrows**: first dig 50 ticks after the wave starts, then every 40-60 ticks, only if it cannot reach the player (timer resets when it fights, but not in the first 30 ticks). Anims `jalimkot_digdown` 7600 / `_digup` 7601. It comes up next to the player, waits 6 ticks before attacking, and does no damage while digging |
| Jal-Xil (ranger) | `inferno_creature_ranger` 7698 (`inferno_ranger_finalwave` 7702) | 3 | 125 HP; att 140, str 180, def 60, mag 90, ranged 250 | ranged max 46, melee (crush) max 19, every 4 ticks | Melee when a player is adjacent (as Tok-Xil does in the Caves), else ranged; projectile `inferno_xil_projectile` 1377. Range **(guess: the Tok-Xil range; tune)** |
| Jal-Zek (mager) | `inferno_creature_mager` 7699 (`inferno_mager_finalwave` 7703) | 4 | 220 HP; att 370, str 510, def 260, mag 300, ranged 510 | magic max 70, melee (stab) max 52, every 4 ticks | **Revive**: 1 in 10 chance to use revive instead of an attack, only in combat. It resurrects one slain monster of **this wave** (not nibblers, not blob splits), **once each, at half HP**, near the centre-east of the arena, with a short delay before the revived monster attacks. The mager stands still while reviving and attacks again 8 ticks later. Anim `jalakxil_resurrect` 7611; projectile `inferno_zek_projectile` 1376 |
| JalTok-Jad | `inferno_jad` 7700 (`inferno_jad_finalwave` 7704) | 5 | 350 HP; att 750, str 1020, def 480, mag 510, ranged 1020 | magic and ranged max 113 (every 8 ticks), melee when adjacent (every 4 ticks) | Like the Caves' Jad, telegraphed; **healers** at under 175 HP (below) |
| Yt-HurKot (Jad healer) | `inferno_jad_healer` 7701 (`_finalwave` 7705) | 1 | 90 HP, level 141 | crush, max 18, every 4 ticks | Not aggressive until attacked; heals Jad 15-24 HP every 4 ticks **only while within melee distance of Jad**; do not respawn |

All Inferno animations in the cache are named `jalnib_*`, `jalmejrah_*`, `jalak_*`, `jaltokjad_*`,
`jalimkot_*`, `jalxil_*`, `jalakxil_*` and `zuk_*`; projectile graphics are the `inferno_*` spotanims
(ids 1375-1382, 1609, 1610, 2261, 2381, 2382, 3294). The mapping from attack to animation is the
Caves' step "Cache data to add": use the names, and fall back to trying ids in the client.

### Jad waves

- **Wave 67 (full) / wave 7 (shortened)**: one Jad, attack every 8 ticks, 5 healers appear at under
  175 HP (half), next to Jad.
- **Wave 68 (full) / wave 8 (shortened)**: three Jads in a triangle around the centre. Their cycle is **9 ticks**
  and they are staggered 3 ticks apart, so a player flicking one prayer per style can protect all three
  (wiki). The first attacks at once; the second and third start 3 and 6 ticks later. Each summons 3
  healers at half HP. Jad targeting for a two-player team is `TargetingStyle.All`, as in the Caves.
- **Wave 69 (Jad at 480 HP of Zuk)**: see Zuk. One Jad, attack rate 8, 3 healers that always spawn
  north of it.
- A Jad's healers: 15-24 HP per 4 ticks while next to the Jad, not respawning. Attacking a healer
  stops its healing and turns it on the player; if the player walks away it goes back to healing.

## The pillars

- **Three pillars, 255 HP each** (wiki), placed at the real positions (from the collision probe).
- **All nibblers pick the same random surviving pillar** and walk to it; each hit takes 2-4 off it every
  4 ticks. When all three are gone they attack players.
- **Stages**: the pillar is a 3x3 multiloc (`inferno_safespot1-3`) whose varbit holds the damage
  taken; the rock changes at 64, 128 and 192 damage. An invisible 3x3 npc carries the health bar.
  At 0 the loc, collision and npc go and `inferno_safespot_dying` (npc 7710, size 3) plays the
  collapse animation; the tiles **no longer block line of sight**.
- **Collapse damage**: players and monsters on tiles next to a collapsing pillar take damage. The wiki
  disagrees with itself (49 damage, or half current HP, to a player within one tile). **Use 49
  typeless to players within one tile of the pillar, and the same to monsters (guess)**.
- **Pillars still standing at the first Jad wave** collapse the same way at the wave's start.
- No "pillar falls" announcement is needed; the client sees the loc change.

## TzKal-Zuk

### Setup

`inferno_tzkalzuk_placeholder` (7706), size 7, stands at the north wall, 1200 HP, attack 350, strength
600, defence 260, magic 150, ranged 400 (wiki). At the start of wave 69 he "breaks free": play
`zuk_spawn` (7563) and then idle (`zuk_idle` 7564). **Players stand in front of him at the north wall.**
(If the arena start tile is not already there, teleport the team to the Zuk start tile, found by the
collision probe, with a message; **decide after step 1**.) Only ranged and magic can reach him:
melee players get "You cannot reach that." Melee is impossible because he is on the lava, so no
extra rule is needed if his tiles are unreachable; check `opNpc` range in the client.

### The attack

- Typeless magic and ranged, **max hit 148**, every **10 ticks**; **7 ticks when enraged** (below
  240 HP). Projectile `zuk_proj` (7571 / `inferno_zuk_projectile` 1375 and its small/mid/gigantic
  variants); the hit lands with the projectile.
- Protection prayers do **not** reduce it (typeless). Accuracy is a hybrid roll; for the first build:
  roll uniformly 0 to 148 **(guess: wiki gives the max only)**, so that an uncovered player can still
  take a huge hit but is not killed on every attack.
- Target: `TargetingStyle.All` (each player in the instance is resolved separately).
- **The shield blocks it.** At the moment the hit would land, if the player is **covered** the hit is
  cancelled and the projectile is shown hitting the shield. Covered means: the player stands inside the
  glyph's 5 wide by 3 deep zone (the northmost 3 rows of the player area, x within 2 of the glyph's
  centre). Two players can both be covered (a 5 wide zone).

### The shield (Ancestral Glyph)

- Npc `inferno_moving_safespot` (7707), floating on the lava between Zuk and the player area, moving
  **east-west back and forth**, with a pause at each end. The wiki says "a walking pace" and "about 5
  ticks pause at each edge"; the exact speed is not given. **Start at 1 tile per tick and pause 5
  ticks (guess; tune).** The path runs the width of the player area (from the probe).
- **600 HP** against monsters only. Players cannot hurt it.
- **Shield drain model (guess)**: minions attack the shield until a player attacks them. Each attack
  they make on the shield removes a fixed amount: ranger 12, mager 14, Jad 30. A full set alone then
  breaks it in about 55 seconds; with a Jad, faster. When the shield is destroyed, **players are never
  covered again** and Zuk's hits are as dangerous as the wiki says. Taking the minions' attention by
  attacking them (they turn on the player) stops the drain.
- Direction is saved across a logout in the real game; here a logout leaves the run, so ignore it.

### Minion sets and the timer

- **Every 3:30 (350 ticks)** a **Jal-Xil and a Jal-Zek spawn behind the player** (south side of the
  player area). They attack the shield until attacked, then fight the player. Timer starts when the
  fight starts.
- **At 600 HP**: the timer pauses and **175 ticks (1:45) are added once**. It resumes when the Jad
  spawns at 480.
- **At 480 HP**: a **JalTok-Jad** spawns behind the player. It attacks the shield first. It gets 3
  healers, spawned north of it, when it drops below half HP.
- **When Zuk dies, every minion still alive dies with him.**
- The fight is unwinnable if the shield falls and nobody kills the sets; that is the real game too.

### Enrage (below 240 HP)

- Attack every **7 ticks** instead of 10.
- **Four Jal-MejJak** (`inferno_zuk_healer`, 7708, size 1, 75 HP, level 250) appear on the lava in
  front of Zuk. Each **heals Zuk 15-24 HP every 3 ticks** until a player attacks it. Once attacked,
  it stops healing and instead **rains lava**, an area attack every 3 ticks: a few tiles around the
  attacked player's position (and some random ones) take **5-10** damage. Implement as: every 3
  ticks, pick the player's tile and 3 random tiles within 2; players on those tiles are hit for
  5-10 typeless **(guess for the pattern; the wiki gives only 5-10 and "similar to the Chaos Temple
  effect")**. They cannot be reached by melee (they are on lava).

### Special cases

- If **all players** die the run fails like any other (the run ends when nobody is inside).
- If a player is on the **blind spots** (the six tiles with no line of sight to Zuk at the north
  edge), they cannot attack him and are "pulled to the middle" in the real game. For the first build
  there is no pull; they just cannot attack from there. (The real game's blind spots come from rock
  outcrops on the map, so they come for free with the map's collision.)
- The wiki's "four safespots from the attack and shield timing" arise on their own once speed and
  attack rate are right; **do not code them**, check them in the client.

## Rewards (on killing Zuk)

Each player inside the instance receives:

- **Infernal cape**: `infernal_cape` (obj 21295). Untradeable, like the fire cape. Gets its normal
  equip stats from the cache.
- **Tokkul**: **16,440** (the wiki's maximum; the page gives no per-wave amounts, and the game gives
  it only for the full clear). No Karamja diary doubling.
- **Pet**: Jal-Nib-Rek, `infernopet` (obj 21291, npc `inferno_pet` 7675), **1 in 100**. The wiki
  gives 1 in 75 on a Zuk slayer task; there is no task, so use 100. (There is also `infernopet_zuk` 22319
  and `zuk_pet` 8011; not the pet, ignore.) The Caves already implement pet following, so reuse it.
- A server announcement naming the team, as for the Caves. After a win everyone is sent out after the
  usual 12 tick delay.
- Dying at any point loses nothing (the isle already keeps items). A failed run gives nothing.
- **Extra infernal capes** can be handed to the gatekeeper for another 1/100 pet roll (see the
  gatekeeper section).

## Tasks

The existing task list already has these Raging-Echoes-derived entries that the Inferno wires:

| Task | Trigger |
|------|---------|
| Sacrifice a fire cape to access the Inferno | The unlock |
| Complete the Inferno 5 / 10 / 15 times | Win counter (a new varp, like the Caves' counter) |
| Defeat TzKal-Zuk (combat mastery task 10 in the content plan) | Win |
| Equip an infernal cape (add) | Equip hook next to `LeagueGearTasks`'s fire cape check |

The "Inferno Combat Achievements" task has no counterpart on the isle and stays out. If you want more
tasks later, the wiki's list (complete a wave without taking damage, kill Zuk without a pillar
falling, and so on) can be added once the fight works. Tasks are listed in the isle's task enums and
structs; **packCache** is needed for new tasks.

## Module layout

All in the isle module, new package `...isleofsouls.inferno`. Shared code goes in `...isleofsouls.waves`.

| File | What it does |
|------|--------------|
| `waves/WaveRun.kt`, `waves/WaveDirector.kt`, `waves/WaveFile.kt` | The generic runner from step 0 |
| `fightcaves/FightCaveDirector.kt` (renamed `FightCaves`) | The cave's hooks over the director |
| `inferno/InfernoMonster.kt`, `InfernoMonsterDefs.kt` | Keys, stats, attacks, sizes, speeds |
| `inferno/InfernoArena.kt` | Template, start, exit, spawn points, pillar positions, shield path |
| `inferno/InfernoDirector.kt` | The run: pillars, wave spawning, revive record, win |
| `inferno/InfernoCombatScript.kt` | How each monster attacks and dies |
| `inferno/InfernoPillars.kt` | Pillar HP, loc swapping, collapse damage |
| `inferno/ZukFight.kt`, `ZukShield.kt` | Zuk, the shield, timer, sets, Jad, enrage, healers |
| `inferno/InfernoScript.kt` | Entrance, exit, gatekeeper options, run timer, startup placement |
| `inferno/InfernoRewards.kt`, `InfernoCompletions.kt` | Cape, Tokkul, pet, announcement, tasks |
| `inferno/InfernoConfigs.kt` | Cache names and `InfernoNpcEdits` (needs packCache) |
| `resources/.../inferno/inferno_waves_full.toml`, `..._shortened.toml` | Wave data |

New varps/timers/queues are hand-added to `.data/symbols` (`varp.sym`, `timer.sym`) as the Caves did.

## Build order

Each step ends with tests passing and its own entry in "As built".

- [x] **0. Refactor** the Caves onto the generic runner. All 32 Caves integration tests stay green. No behaviour change. *(done 2026-10-08)*
- [x] **1. Probe**: Inferno map in the cache, arena collision, pillar positions and flags, Zuk
  platform, the stat and animation data of the Inferno npcs (the four checks above). *(done 2026-10-08, see "As built")*
- [x] **2. Instance, entrance, exit, gatekeeper sacrifice, unlock varp.** A run with an empty arena. *(done 2026-10-08)*
- [x] **3. Wave data and loader** for both modes, with the validation tests (the wave rules above). *(done 2026-10-08)*
- [x] **4. Monsters**: nibbler, bat, blob and splits, melee with burrow, ranger, mager with revive.
  Anim and projectile config, `InfernoNpcEdits`. *(done 2026-10-08)*
- [x] **5. Pillars**: HP, nibbler targeting, stages, collapse, line of sight verified. *(done 2026-10-08)*
- [x] **6. Jad waves**: one Jad, three staggered Jads, healers. *(done 2026-10-08)*
- [x] **7. Zuk**: spawn, attack, shield, timer and sets, Jad at 480, enrage and healers, death. *(done 2026-10-08)*
- [x] **8. Rewards, announcement, tasks** and the pet roll. Extra-cape trade-in. *(done 2026-10-08)*
- [x] **9. Docs** ("As built" section, client checklist), memory note. *(done 2026-10-08)*
- [ ] **You:** run `./gradlew packCache` with the server stopped, then check in the client with two
  accounts (checklist to be written in step 9).

## Tests

- Unit: both wave files load; the wave rules (nibbler counts, introductions, a Jad wave from the first
  Jad wave, boss last); the shield path and cover maths (a table of glyph position against player
  tile); the Zuk timer (3:30, the +1:45 pause between 600 and 480); the blob's style choice against
  each prayer; burrow timing; the revive record (once each, half HP).
- Integration (game test harness): a shortened run to a win; a run where the last player dies and a
  re-entry gives a fresh run; a pillar being eaten and stopping line of sight, then not; the collapse
  hurting a player next to it; three Jads staggered correctly; Zuk's attack blocked by the shield and
  landing when uncovered; the enrage speed-up; the win giving cape, Tokkul and pet roll to both players;
  the unlock being kept after a failed run.
- **Test gotchas from the Caves** (from `project-fight-caves`): `random.next` is consumed by every call,
  keep damage small, make bosses inert with `actionDelay = Int.MAX_VALUE` when attacks are driven by
  hand, the harness does not copy the map's own locs into regions, `GameTestState.collision` is a
  different map from the scope's, integration tests need `WeaponAttackStylesScript` plus an
  `AttackStyles` binding, and sandbox gradle runs need output to a log file and a timeout. Never kill
  java by name.

## Open numbers (all guesses, tune after the client test)

1. Shield: speed (1 tile/tick), end pause (5 ticks), exact path and the cover zone's placement.
2. Shield damage per minion attack (ranger 12, mager 14, Jad 30).
3. Zuk's accuracy (uniform 0-148) and the projectile delay.
4. Bat stat drain amount; Jal-Xil attack range.
5. Pillar collapse damage (49) and what it hits.
6. Jal-MejJak's lava pattern.
7. Monster spawn positions (all except the nibblers) and wave timings.
8. The shortened wave list.
9. The Jad delay constant carried over from the Caves (`JAD_HIT_DELAY` = 5), which was already a guess.

## Risks

1. **Pillar line of sight and region copies**: the biggest unknown. If the pillar locs do not block the
   ray, the whole safespot mechanic needs a manual check in the attack code. Step 1 decides.
2. **Npcs have no pathfinding.** Big monsters (sizes 3-5) will get stuck on pillars and walls; this is
   how the real game's safespots work, but monsters that cannot reach the player at all must still
   burrow (melee) or be handled (stuck ranger/mager are fine because they stop at range).
3. **Zuk's size 7 and the lava**: check that a size-7 npc can stand in the arena's collision and that
   attack range is measured from its edge.
4. **Two-player load**: `TargetingStyle.All` for three Jads and Zuk at once; the Caves tested two players
   with one Jad only.
5. **Everything is unseen in a real client**, including the Caves; the Inferno inherits that risk.
6. **Wiki conflicts**: the pillar collapse damage, and wave 69 composition ("Jad and magers", but the
   Zuk page says the wave is the Zuk fight with sets and a Jad at 480). I follow the Zuk page.

## Zuk fixes after the first client look (2026-10-08)

- **The shortened Inferno is just Zuk for now** (`inferno_waves_shortened.toml`; the old six-wave list is
  kept commented out in the file and as a test copy). Zuk's wave also brings down any pillars.
- **Zuk's set (Jal-Xil, Jal-Zek) and his Jad never walk**: their `attackRange` is 40 (`ZUK_SET_RANGE`,
  needs `packCache`), more than the arena is long, so they stand where they spawn and shoot. The
  **Jal-Xil now spawns on the east side and the Jal-Zek on the west** (swapped).
- **The glyph walks** (`npc.walk`, one tile a tick, `MoveRestrict.Blocked` because the lava row is blocked
  terrain) so the client slides it; if walking ever fails to move it, it falls back to hopping a tile a
  tick instead of freezing.
- **Zuk's prison opens** (`InfernoPrison.kt`): the map holds it closed. On breakout the static glyph loc is
  deleted, the corner pieces become their "state2" locs, the two side walls become "state2" (playing the
  cache's `inferno_collapsing_wall_side_*` animation) and then "state3" rubble, and the roof multiloc is
  hidden by the varbit `inferno_prisonroof_hidden` that every player in the run is sent. The piece
  positions are read from the map file (`InfernoArena.prison*`). Not verifiable in the test world (it has no
  map): the tests put the pieces in by hand.
- **Zuk no longer vanishes at a distance**: players in the Inferno get an npc view distance of 44 tiles
  (`Client.setNpcViewDistance`, rsprot's `NpcInfo.setViewDistance`; default 15), reset when they leave.

### Third round (2026-10-08)

- **A set or Jad that is hit shoots back from where it stands.** The engine's default retaliation walks
  an npc up to melee; every Inferno monster with a ranged attack now has its own retaliation that uses
  the approach-and-shoot mode, so it only moves when the player is out of its range (Zuk's set has range
  40, so never). They melee only when someone stands next to them.
- **The first set waits for one full rotation of the glyph**: it comes when the glyph touches the end it
  touched first for the second time (the third end, east-west-east). Then one every 3:30 as before.
- **Zuk's Jad's healers** come at under half health as for any Jad. If he is drawn up against the north
  wall and there is no room on his north side, they now appear wherever there is room (before, none came).
- **Zuk moved one tile east** (south-west tile (2268, 5362), middle 2271 = the arena's middle, and the
  middle of the gap between the prison's side walls).
- **Jal-MejJak** stand on the lava row z 5362, 8 and 5 tiles either side of Zuk's middle (x 2263, 2266,
  2276, 2279), from the user's screenshot, facing north towards Zuk while they heal. **They never aim at
  a player**: once hit, a healer turns round to face south, plays its attack animation and every 3 ticks
  throws four lava balls (projectile, a guess: the Wilderness meteor) onto the ground in front of it,
  random tiles 3 to 8 south of it and 1 either side (`LAVA_NEAR/FAR/SPREAD`). Whoever stands on a tile
  when its ball lands takes 5 to 10, so it can be dodged.
- **The left side wall's rubble is turned half a turn** (`InfernoPrison.LEFT_RUBBLE_TURN`): the cache has
  one rubble loc for both sides, and the left one showed up wrongly turned. A guess; try 1 or 3 if
  it is still wrong.

- **Zuk never turns** (`rotationFlag = false` on his npc type in `InfernoNpcEdits`, needs `packCache`; he is also told to face south at spawn) and stands one tile further north: south-west tile (2268, 5363).

- **Zuk turned to face a player who hit him** because the engine's retaliation (`com_retaliate_player`) puts a hit npc into a chase mode that also faces the target. Zuk and the Jal-MejJak now have an empty retaliation handler in `InfernoCombatScript`, so they never face or walk to a player; the `rotationFlag` edit stays as a second guard.
- **The west side of Zuk's prison was missing a wall piece.** The map's right join is a collapsing piece but the left one is an ordinary static wall (`inferno_wall_edge_large_06` at (2267, 5366)), so it never changed when the prison opened. It is now deleted and replaced by `inferno_prison_wall_corner_join_left_state2`, mirroring the east side.
- **Zuk's spawn direction and the west join's turn.** With rotation off the client keeps the direction an npc appeared with, which defaulted to west: Zuk's `respawnDir` is now south (he is placed facing it). The open left join (`join_left_state2`) is now turned a half turn from the map's piece (`InfernoPrison.LEFT_JOIN_TURN`): measured from the decoded models (vertex bounds of 33088/33090, 33035/33039, 33038), with the map's turn it reaches into the cell, with a half turn it lies where the east join's mirror image does. The side walls need no turn (their models are exact mirrors); the left rubble needs a half turn (same measurement). The left floor corner is ambiguous by ~15 units and stays as the map has it.
- **Zuk still faced the wrong way** because his spawn direction was set after he was added (the client is told at that moment); it is now set before, and also on his npc type (`respawnDir = south` in `InfernoNpcEdits`, needs `packCache`). The left floor corner is now also turned a half turn (`LEFT_CORNER_TURN`, a guess for the gap left at the foot of the west wall).
