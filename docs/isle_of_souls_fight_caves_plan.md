# Isle of Souls: Fight Caves (implementation plan)

Written 2026-10-06 for a fresh implementation session. **Status: built the same day, not yet seen
in a real client.** The code, tests and what you must check are in "As built" below; the rest of
this file is the original plan, which the build followed except where "As built" says otherwise.

## As built (2026-10-06)

All of it is in `content/areas/misc/isle-of-souls/.../fightcaves/`. 24 integration tests and 16
unit tests cover it (the whole isle module, 286 tests, passes). **You must run
`./gradlew packCache` with the server stopped** before it works properly: it adds the cave
monsters' animations (`npcs.toml`) and their chase and attack ranges (`FightCaveNpcEdits`), and the
timer and counters it uses were added to `.data/symbols` (`timer.sym`, `varp.sym`).

| File | What it does |
| --- | --- |
| `FightCaveWaves.kt` | Loads and checks the wave files; `FightCaveMode`, `FightCaveMonster` |
| `FightCaveSettings.kt` | `.data/league/event-settings.json` `{ "fightCaveMode": "shortened" }`; default full |
| `FightCaveSpawns.kt` | The arena tiles, the 15-place cycle and the rotation planner, Jad spawns where the first Tz-Kek did |
| `FightCaveMonsters.kt` | Each monster's attacks (max hits, anims, projectiles), size, speed, range |
| `FightCaves.kt` | The director: enter/leave, wave spawning, hunting, Tz-Kek split, healers, win, clean-up |
| `FightCaveCombatScript.kt` | How each monster attacks and dies; Jad uses `TargetingStyle.All` |
| `FightCaveScript.kt` | Entrance loc, gatekeeper, the run timer, startup placement |
| `FightCaveRewards.kt`, `FightCaveCompletions.kt` | Fire cape + 8,032 Tokkul + 1/200 pet for everyone inside, announcement, tasks |
| `FightCaveConfigs.kt` | Cache names, and `FightCaveNpcEdits` (needs packCache) |

How a run goes: a player uses the entrance ("Enter") and a run starts for their team (a teammate
who enters later joins it). Wave 1 spawns 10 ticks later; each cleared wave is followed by 8 ticks
and the next. Everyone in the cave is hunted by the nearest-player rule. Killing Jad gives every
player **inside the cave at that moment** the rewards and sends everyone out 12 ticks later. A run
ends when nobody is inside (death, leaving, Home Teleport, logout); its monsters are removed and
the next entry is a fresh cave. A player who died can walk back in and rejoin a live run.

### Where the build differs from the plan above

1. **Gatekeeper is TzHaar-Mej-Jal** (`tzhaar_fightcave_master`, 2180), not `inferno_master`: the
   cache gives him the "Talk-to" and "Exchange fire cape" options already. He stands at
   (2206, 2859). "Exchange fire cape" says the Inferno is not open yet.
2. **Entrance** is at **(2207, 2861)**, set into the rock row, facing `LocAngle.East` (the real
   cave's angle). The loc is 3 wide and 2 deep, so players use it from the plaza tile in front.
   If it opens towards the rock in the client, change `ENTRANCE_ANGLE` to `West`.
3. **Monsters have no pathfinding.** The engine moves npcs by stepping straight at the target and
   stopping at walls, as in the real game, so the spawn tiles are not where the wiki's picture puts
   them: a probe showed that the whole north-west room and the pocket behind the south-east rock
   are dead ends. The five tiles in `FightCaveArena.points` were chosen so that a monster of every
   size can walk to the start tile from each (a test checks it). The names (SE, SW, C, NW, S) and
   the rotation cycle are the wiki's; the tiles are not. **Move them if the client shows a bad one.**
4. **Jad's healers** appear when he drops below **150** hitpoints (the wiki's number, not half),
   once only, heal him **5 every 4 ticks** while within 5 tiles, and only fight if attacked.
5. **Prayer is checked when a hit lands**, not when the attack starts: `NpcAttacks` now uses
   `queueImpactHit`. So a player who switches prayer after seeing the animation is protected, and
   the right prayer blocks all of a Jad hit. This affects every `NpcAttack`, not just the cave.
   `NpcAttack` also got `hitDelay` (Jad's rocks land 4 ticks after the animation).
6. **`NpcTargeting` has a range per npc type**; Jad uses 64 so that he reaches everyone in the cave.
7. **Anchoring the leash**: the engine cancels a chase when the target is further than
   `maxRange + attackRange` from where the npc spawned, so the director moves each monster's
   `spawnCoords` to its target every tick (and `FightCaveNpcEdits` raises `maxRange` too).
8. **Tz-Kih drains 1 prayer point** with every attack (the wiki does not give the amount; change
   `FightCaveAttacks.TZ_KIH_PRAYER_DRAIN`). Yt-MejKot heals itself or a monster below half health
   for 10, one attack in three.
9. **Tasks**: "Complete the Fight Caves 5 Times" (664) and "10 Times" (665) were added (the list is
   now 141 tasks, 11,030 points). "Equip a Fire Cape" (653) and "Survive a Hit From TzTok-Jad
   Without Prayer" (657) are not wired yet.
10. The pet is the `jad_pet` item.

### What to check in the real client (two accounts)

- [ ] After `packCache`: the cave entrance and TzHaar-Mej-Jal stand on the plaza north of the
  graveyards; the entrance faces the plaza and "Enter" shows.
- [ ] Both teammates enter and end up in the **same** cave; a third player gets their own.
- [ ] Wave 1 spawns about 6 seconds in; the monsters walk to you and none starts stuck.
- [ ] Each monster's attack animation and projectile look right (Tok-Xil spines, Ket-Zek fireball,
  Jad's fire and rocks); Jad's two attacks have different animations you can read.
- [ ] The correct protection prayer blocks Jad completely, the wrong one does not.
- [ ] Jad hits both players at once. Healers appear below 150 and heal him.
- [ ] Killing Jad gives each player a fire cape and Tokkul and sends you out.
- [ ] Dying in the cave puts you in the lobby; walking back in rejoins the run.
- [ ] Switching to shortened: write `.data/league/event-settings.json`, restart, check 9 waves.

### Not done

The Inferno; the two tasks in point 9; the pet following the player; sounds for the monsters; a
proper "Wave N" interface (it is a chat line); scaling for two players (decided: none).

## Decisions (all confirmed by the user)

1. **Build the full Fight Caves with the real 63 waves, plus a shortened version, and switch between
   them in code or config.** Players never see a choice: no in-game option, no menu.
2. **The cave entrance and an NPC go near the Soul Wars lobby.** The NPC is the one that takes
   **fire capes in trade** (the gatekeeper for the Inferno, built later; see below).
3. **Everyone inside the instance gets the fire cape** when Jad dies.
4. **No scaling for two players** for now: waves and Jad are exactly as in the real game.
5. Jad attacks **all players in the instance** (decided earlier): one projectile per player.
6. Every area is already multi-combat; the cave is a **team instance** (both teammates in one
   private copy; solo is allowed).

## What already exists (read these first)

| Piece | Where | Notes |
| --- | --- | --- |
| NPC melee/ranged/magic attacks | `api/combat/combat-scripts`: `NpcAttack`, `NpcAttacks` | Docs: `docs/isle_of_souls_npc_combat.md`. Projectiles, accuracy, max hit, prayer, delayed hits |
| Multi-target styles | `NpcTargeting` (`TargetingStyle.One/Alternating/All`) | `InstanceTargetScope` (isle module) keeps `All` inside one instance |
| Team instances | isle module `instances/`: `TeamInstances`, `TeamInstanceType` | Enter/leave, fresh copy when empty, login-inside handling. See `isle_of_souls_teams.md` |
| Drop tables / kill events | `api/drops`, `NpcDeathEvents.Killed` | Jad's pet etc. can go in `isle.toml` |
| Death and respawn | `PlayerDeath` -> lobby respawn | `PlayerDeathEvents.Died/Respawn` |
| League tasks and announcements | `LeagueTaskTracker`, `LeagueAnnouncer` | Tasks for the cave are added here |
| Wave data | `content/areas/misc/isle-of-souls/src/main/resources/org/rsmod/content/areas/misc/isleofsouls/fightcaves/` | `fight_caves_waves_full.toml` (the real 63 waves, checked against the wiki's counting rule) and `fight_caves_waves_shortened.toml` (8 waves + Jad) |

## The mode toggle

- A `FightCaveMode { Full, Shortened }` setting. **Default in code** (`Full`); an optional override
  in `.data/league/event-settings.json` (`{ "fightCaveMode": "shortened" }`), read when the server
  starts. No in-game control. An admin command to switch is optional and must be admin-only.
- The mode picks which wave file is loaded. A run keeps the mode it started with, so changing the
  setting never changes a run in progress.
- The Inferno gets the same toggle later (reuse the setting and the runner).

## The waves

- **Full**: the 63 waves in `fight_caves_waves_full.toml`. Wave *n* is built from monsters worth
  1 (Tz-Kih), 3 (Tz-Kek), 7 (Tok-Xil), 15 (Yt-MejKot) and 31 (Ket-Zek) points adding up to *n*
  (up to six monsters in a wave). Wave 63 is TzTok-Jad alone with `boss = true`; his four Yt-HurKot
  healers appear when he reaches half health (not at the start).
- **Shortened** (proposed list, edit freely): 1 = 2 Tz-Kih, 2 = Tz-Kek + Tz-Kih, 3 = 2 Tz-Kek,
  4 = Tok-Xil + Tz-Kek, 5 = 2 Tok-Xil, 6 = Yt-MejKot + Tok-Xil, 7 = Ket-Zek + Tz-Kek,
  8 = Ket-Zek + Yt-MejKot, 9 = Jad (`boss = true`).
- **File format**: `[[wave]]` with `number`, `monsters = ["tz_kih", ...]` and optional `boss`. The
  monster keys are `tz_kih`, `tz_kek`, `tok_xil`, `yt_mejkot`, `ket_zek`, `tztok_jad`. The loader
  must reject an unknown key, an empty wave, a non-increasing number, and a file whose last wave
  is not the boss.
- **Pacing**: when a wave is cleared, a short pause (about 8 ticks, a constant) and then the next
  wave spawns with the message "Wave N of M" in the league's blue (`LeagueChat`).

## Spawn points

The real cave uses five spawn points: **SE, SW, C, NW, S**. A run picks one of 15 rotations
(random per run, replacing the real game's time-based pick). The rule (from the wiki's rotations
page): the cycle is
`SE, SW, C, NW, SW, SE, S, NW, C, SE, SW, S, NW, C, S` (repeating). At the start of each wave, the
**highest-level monster spawns at the next location in the cycle**, then the lower ones at the
following locations, in order of level. A rotation is the starting offset (0-14) into the cycle.
**Jad spawns where the first Tz-Kek (wave 3) spawned.** If a wave has more than one monster for
the same point, the extra one spawns on an adjacent free tile.

**To find out (first task):** the exact tiles of the five spawn points. Try the cache's static npc
spawns for the cave's map square (about `37_79`, world x 2368-2431, z 5056-5119) first (the
`tzhaar_fightcave_swarm_*b` npcs, the "spawn point" versions, may be placed there), then the wiki's
map picture. The player's start tile is also needed.

## The monsters

Stats come from the cache (npc hitpoints, attack, defence, and so on); the wiki infobox values are
below for checking. The cache's cave npcs are `tzhaar_fightcave_swarm_<type><a|b>`; `a` looks like
the fighting version and `b` the "spawn point" version (**verify in the cache**).

| Key | Cache npc | Level | HP | Size | Attacks | Speed | Max hit | Special |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tz_kih` | `..._1a` (3116) | 22 | 10 | 1 | Melee (stab) | 4 | 4 | Drains the target's prayer on hit |
| `tz_kek` | `..._2a` (3118) | 45 | 20 | 2 | Melee (crush) | 4 | 7 | On death spawns two level-22 Tz-Kek (`..._2spawn`, 3120, 10 HP, max hit 4) |
| `tok_xil` | `..._3a` (3121) | 90 | 40 | 3 | Ranged from distance, melee adjacent | 4 | 14 ranged, 13 melee | Chooses ranged unless adjacent |
| `yt_mejkot` | `..._4a` (3123) | 180 | 80 | 4 | Melee (crush) | 4 | 25 | Heals itself and other monsters below half health |
| `ket_zek` | `..._5a` (3125) | 360 | 160 | 5 | Magic from distance, melee adjacent | 4 | 52 magic, 55 melee | Chooses magic unless adjacent |
| `tztok_jad` | `..._boss` (3127) | 702 | 250 | 5 | Magic or ranged (50/50) from distance, melee adjacent | 8 | 95 magic, 97 ranged, 97 melee | Telegraphs the style with its animation; summons 4 healers at half health |
| `yt_hurkot` | `..._boss_cleric` (3128) | 108 | 60 | 1 | Melee | 4 | 14 | Heals Jad when next to him (or within 4 tiles of him while fighting the player); heals itself or another healer below half health if it cannot reach Jad |

The wiki's pages (`Tz-Kih` and the others, `TzTok-Jad`) have the details. **Check Tz-Kih's exact
prayer drain amount on its page.** Monsters do not respawn.

### Cache data to add (first, because everything needs it)

The enrichment file (`api/cache-enricher/.../npc/npcs.toml`) only has an examine text for these
npcs. Add, per npc: attack, defend and death animations, attack and death sounds, attack rate,
attack type (stab/crush/etc.), and the projectile/spotanim ids (Tok-Xil arrows, Ket-Zek magic,
Jad's two attacks, and Jad's ranged and magic hit graphics). Find animation ids from the cache's
seq names (search the symbol files) and fall back to trying them in the client. Existing entries
show the format (`attack_anim`, `defend_anim`, `death_anim`, `attack_melee`, ...). Changing this
file needs `./gradlew packCache` (user stops the server first; see the foundation checklist).

## Jad

- **One attack per 8 ticks.** If the player is adjacent, 1/3 chance to melee instead; otherwise
  50/50 magic or ranged, chosen at the moment he starts the animation. Use `NpcAttack` with
  `projectile`/`projanim` and a fixed `maxHit`; `alwaysHits = false` so the cave's accuracy applies.
- **Targeting:** `targeting.setStyle(jad, TargetingStyle.All)`. Every player in the instance gets
  their own projectile and their own roll, and each can protect with their own prayer.
- **Prayer:** the correct protection prayer must block a Jad hit entirely. **Check the engine:**
  `StandardPlayerHitModifier` handles protection prayers for npc attackers; confirm the reduction
  for npcs is 100% for a matching hit type, or Jad is unbeatable for no reason. Write a test.
- **Healers:** at 50% health (125 HP) spawn four Yt-HurKot (the real positions are around Jad).
  Healing rule above. If all four are killed, no more come; if they heal Jad above half, another
  four spawn when he drops below half again (as in the real game).
- **Death:** kill Jad = win.

## Run state and rules

One `FightCaveRun` per instance holds the mode, the wave list, the current wave, the living
monsters, the rotation, and a state (`WaitingForNextWave`, `InWave`, `Won`, `Failed`).
- **Entering**: through the entrance (below) using `TeamInstances.enter`. The first player in
  creates a run (rotation, mode); a teammate who enters later joins that run. A player who
  re-enters after dying joins the same run.
- **Starting**: wave 1 spawns a few seconds after the first player enters.
- **A dead player** respawns in the lobby as everywhere and can walk back in; the run continues
  for whoever is inside.
- **Failure**: when the last player leaves or dies and nobody is inside, the run ends and the
  instance empties (it resets on the next entry; `TeamInstances` already does this).
- **Logging out inside** is already handled: the player is moved out at login.
- **Win**: when Jad dies, every player **inside the instance at that moment** receives the
  `tzhaar_cape_fire` (the fire cape) and Tokkul (`tzhaar_token`, 8,032), the league tasks complete
  for the team, a server announcement names the team, and the pet `jadpet` is rolled (1/200).
  Every player inside gets one cape per win (the cape is untradeable, so duplicates only matter
  to the player). After the win, everyone is teleported out after a short delay.

## The entrance and the NPC

A free area just north of the lobby graveyards (graveyard floor is x 2205-2207 and 2213-2215,
z 2847-2853; the fences are at z 2845 and 2855). A probe of the cache's collision map shows a
fully walkable plaza, **x 2206-2209, z 2857-2860** (and a strip x 2196-2215 at z 2856-2857), with
a decorative rock row above it at z 2861. Proposed:

- **Cave entrance loc** `tzhaar_fightcave_wall_entrance` (11833) at **(2208, 2860)**, facing
  south, with an "Enter" option that sends the team to the cave (it shows the usual confirmation:
  "Enter the Fight Cave?"). The matching exit loc inside the instance is
  `tzhaar_fightcave_wall_exit` (11834).
- **NPC** `inferno_master` (7690; the cache also has `inferno_master_1op`/`_2op` variants) at
  **(2206, 2859)**, no wander. This is the TzHaar who takes fire capes. For now:
  - Talk-to: explains the Fight Cave and the Inferno.
  - "Trade fire cape": the Inferno does not exist yet, so the option tells the player it is not
    open. Keep the option and the dialogue ready so the Inferno step only has to fill it in:
    entering the Inferno requires giving up a fire cape, and the infernal cape is the reward.
- **Placing them**: spawn at startup from a `PluginScript` (`LocRepository.add`,
  `NpcRepository.add`), which needs no repack; or use `MapLocSpawnBuilder`/`MapNpcSpawnBuilder` and
  `packCache`. Either is fine.
- **Verify in the client** that the loc is visible, not inside a wall, and the NPC stands on free
  floor; move by a tile if not. Re-check the collision map if you move them
  (a throwaway integration test that prints flags from `CollisionFlagMap` works).

## Module layout (suggested)

All in the isle module, package `...isleofsouls.fightcaves`:
`FightCaveConfig` (mode, constants, settings file), `FightCaveWaves` (loader and validation),
`FightCaveMonster` (the six keys and their cache npcs and attacks), `FightCaveRun` (run state),
`FightCaveScript` (entrance, exit, NPC, run start/end, wave loop), `FightCaveMonsterScripts`
(attack behaviour for the five types), `JadScript` (Jad, healers, healing), `FightCaveRewards`
(cape, Tokkul, pet, announcement, tasks). Make the wave loop generic (`WaveRun`) so the Inferno
can reuse it with its own wave file and monsters.

## Tests

- Unit: wave loader (both real files load; every error case); the counting rule check (each full
  wave's monster values sum to its number); spawn cycle and rotation (a given rotation gives the
  expected points for the first waves, Jad spawns at the wave-3 point).
- Integration (game test harness): a run in the shortened mode through every wave to a win; a
  run where the last player dies (failure, then re-entry gives a fresh run); a teammate joining
  and a re-entry after death; Tz-Kek splitting; Jad's all-targets attack hitting two players and a
  correct prayer blocking it; healers healing Jad; the win giving a cape to each player inside.
- **Client test by the user** with two accounts, as in the foundation checklist.
- **Test setup warning (important):** when you run integration tests against a sandbox copy of
  `.data`, the init script must set the test working directory in
  `gradle.projectsEvaluated { ... }` and must be **checked** (print the working directory in a
  throwaway test). In the last session a plain `tasks.matching{...}.configureEach` ran *before*
  the integration plugin set `workingDir = rootDir`, so the tests used the **real** `.data`. No
  data was found damaged (the real league files kept their old timestamps), but it should not
  happen again.

## Risks and things to check early

1. **Animation, sound and projectile ids** for the cave npcs (above): the biggest unknown.
2. **Prayer blocking npc hits** (see Jad).
3. **Pathing for big monsters** (sizes 3-5) around the cave's rocks; the real cave relies on
   monsters getting stuck on corners (safespots). The engine's npc pathfinding is used as is.
4. **The `a`/`b` npc variants** (meaning and which to spawn).
5. **Static spawn data** for the cave's map square and the start tile.
6. **Tz-Kih's prayer drain amount** (check its wiki page).
7. **Cache npc ids above 14792 do not exist** in this cache; nothing in this plan needs them.

## Not part of this plan

The Inferno (separate plan), the Fight Cave **task list** beyond "complete the cave" (add kill and
wave tasks from the wiki list when you add tasks), echo bosses, and any scaling for two players.
