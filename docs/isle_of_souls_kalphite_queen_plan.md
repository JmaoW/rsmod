# Isle of Souls: Kalphite Queen

Built 2026-10-16. Sources: the OSRS Wiki pages "Kalphite Queen" and "Kalphite Queen/Strategies", "Kalphite
Soldier", "Kalphite Guardian" and "Poison", and tsps (`server/plugins/bosses/KalphiteQueen.plugin.js`) for the
ids, projectile numbers and cocoon coordinates.

## Decisions (agreed with the user)

- **A team instance**, like Scurrius and Zulrah: each team gets its own copy of the chamber, no hitpoint scaling.
- **No rope**. The cache's chamber entrance is a multi-state loc driven by the varbit `kalphite_chamber_entrance`
  (0 bare tunnel, 1 roped "Climb-down", 2 normal/private). The isle sets it to 1 for everyone, on login and when
  they enter the lair, so the tunnel always shows "Climb-down" and "Look-inside".
- **The Echo Kalphite Queen is not built** (saved for later, as a pair with this one).
- **Kalphite soldiers and guardians poison** (added to the same job, see below). The hive-wall tunnel to
  Dorgesh-Kaan is not wired.

## What the fight does

| | |
|---|---|
| Forms | One npc, two forms of 255 hitpoints each (cache stats: attack/strength/defence 300, magic 150). Crawling (`kalphite_queen`, 963) then airborne (`kalphite_flyingqueen`, 965, shown with `Npc.transmog`). |
| Molt | When the crawling form hits 0 she is shown as the airborne form at once and is delayed (untargetable) for 20 ticks with the emerge animation and graphic (the animation belongs to that model; tsps does the same), then gets full hitpoints. Drained defence carries over (same npc). |
| Timeout | After 2000 ticks (20 minutes) in the airborne form she goes back to the crawling form with the hitpoints she has left; the next molt gives full hitpoints. |
| Attack | Every 4 ticks, up to 31. Next to the target: melee 1 in 2, ranged 1 in 4, magic 1 in 4. Further away (up to 10 tiles): ranged or magic. Ranged and magic never miss but protection prayers still stop them. |
| Ranged | A spine at the target, and one at every player within 1 tile of the target. A spine that rolled damage takes 1 prayer point even through Protect from Missiles; a spectral spirit shield stops the drain half the time (tsps). |
| Magic | A glow at the target that bounces to the nearest unhit player within 2 tiles of the last one (a visual player-to-player projectile, hit delays add up). |
| Protection | Not real: her defence bonuses are high against magic and ranged in the crawling form and against melee in the airborne form. The overhead icons come from the cache's npc config. |
| Movement | She walks to the nearest spot that puts her 5x5 beside the target. A player under her is out of reach, so she steps off (that is the flinch the wiki describes). Hatched workers and other npcs block her path. |
| Aggression | She waits until a player is within 12 tiles or someone hurts her. |
| Workers | Each hit on her (even a 0) has a 1 in 20 chance to hatch a Kalphite Worker (`kalphite_worker_chamber`) at one of the 13 cocoons within 10 tiles of her. They chase the nearest player, last 100 ticks, up to 12 at once. |
| Rewards | The ordinary drop table (imported earlier: consumables sub-table plus the uniques, ensouled head, princess pet, clue) for the player with the most damage, rolled at her middle tile. Slayer XP (535.4, set on the npc) goes through the existing slayer hook. |
| After a kill | She respawns 50 ticks later in the same chamber for whoever is still inside, so kill counts can be farmed. |

Entry is the rope tunnel at (3509, 9497) on level 2 of the Kalphite Lair ("Climb-down", either option); players
appear at (3507, 9494) on the chamber floor and the rope there ("Climb-up") puts them back at (3508, 9494, 2).

## League tasks

Raging Echoes ids from `docs/raging_echoes_tasks.json`: 864 "Defeat the Kalphite Queen", 874 "... 50 Times", 875
"... 150 Times" (Hard). They complete from `NpcDeathEvents.Killed` through `LeagueKillTasks` (by npc name, to the
player with the most damage and their team). The combat mastery that needs a high-level boss fires from the same
event. The tasks' area was cleared (Kharidian Desert) and "in the Kalphite Lair" dropped from their descriptions.

## Poison from soldiers and guardians

`dungeons/KalphitePoisonScript.kt`: soldier (4) and guardian (6) bites that do damage poison one time in four (a
guess; the wiki gives no chance, this is K'ril's). They use the cache's own stats, accuracy and max hit; only the
attack animation is new (the cache does not name one for them): soldier `kalphite_update_claw_attack`, guardian
`kalphite_update_claw_lord_attack`. The poison timer is the one `GodWarsZamorakScript` already runs. Antipoison
stops it as for K'ril.

## As built

| File | What it is |
|---|---|
| `kalphite/KalphiteConfigs.kt` | References (npcs, locs, seqs, spotanims, timer, varbit) and `KalphiteNpcEdits` (the queen never hunts; workers chase the whole chamber and bite with their jaws). Needs `packCache`. |
| `kalphite/KalphiteQueen.kt` | `KalphiteArena` (zone block 6x5 at zone 433/1185, start, exit, her spot, the 13 cocoons), `KalphiteRun`/`KalphiteFight`, `KalphiteQueen` (a `WaveDirector` with one wave: target choice, walking, the attack mix, molt and revert, workers). |
| `kalphite/KalphiteCombatScript.kt` | The molt (crawling form's death queue), her real death, hits (aggro, worker hatching). |
| `kalphite/KalphiteScript.kt` | The rope in and out, the entrance varbit, the run timer. |
| `dungeons/KalphitePoisonScript.kt` | Soldier and guardian poison. |

Also changed: `IsleOfSoulsModule` (binds `KalphiteQueen`), `LeagueKillTasks`, `LeagueTaskList`, `LeagueTaskSeries`,
`LeagueTaskVars` (two counters), `IsleOfSoulsTaskEnums` (keys 489-491), `IsleOfSoulsTaskEdits`; symbols `timer.sym`
(`kalphite_queen_tick`), `varp.sym` (65297, 65296), `struct.sym` (`league_task_864/874/875`); `npcs.toml` (both
forms: defences, fire weakness 40%, +5% XP, Slayer XP 535.4, venom turns to poison, attack anims).

Tests: `KalphiteQueenRunTest` (19) and `KalphitePoisonTest` (3), all green against a sandbox copy of `.data`.

## Guesses and what is not built

Guesses: aggression range 12, attack range 10, target leash 25, splash radius 1 (tsps; the wiki says "close"),
bounce radius 2 (tsps used 1), a 2-tick wait after the molt, worker cap 12, poison chance 1 in 4, the airborne attack
animations (tsps: 6234 for melee and ranged, 6235 for magic), the magic cast glow (`kalphite_queen_glow`) which tsps
does not use.

Not built: the 256th-kill tattered head, the Konar brimstone key, the "peek" count (it only gives a message), the
legs that fall off in the molt, the 5-minute out-of-combat health reset, the Echo queen, the hive-wall tunnel,
the ironman instance and braziers, the cocoon open/close animations.

## Client checklist (needs `./gradlew packCache`, then a restart)

1. The tunnel at (3509, 9497) on level 2 of the lair shows "Climb-down" and "Look-inside" with no rope.
2. The chamber loads (a copy of the lair above it comes with it) and the queen waits in the west.
3. Both forms show the right overhead icons (magic + missiles, then melee) and the animations look right; the
   airborne attack animations and the magic glow are the likeliest to be wrong.
4. The molt: animation and graphic, she cannot be attacked for 20 ticks, then she rises.
5. Spines hit players beside the target; the glow bounces visibly between players; prayer drains.
6. A worker hatches now and then and attacks.
7. Soldiers and guardians in the lair poison (the orb turns green).
8. Tasks 864, 874 and 875 appear in the league panel and tick off.
