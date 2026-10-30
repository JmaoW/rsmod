# Isle of Souls: the Wilderness bosses

Status: **built and tested 2026-10-17, not yet seen in a real client.** All six bosses, the six lairs
and the Escape Caves are in; 14 integration tests (`WildBossesTest`) pass, and the whole isle
suite passes (741 integration tests; the only unit failure is the old `FightCaveWavesTest`).
**Left for the user:** `./gradlew packCache` (npc edits, the multi-combat areas, the surface spawn
removal, the new varps and task structs) and a client check.

Sources: the OSRS wiki pages for the six bosses, their `/Strategies` pages and the lair pages
(downloaded with the wiki API), the cache (stats, sizes, anims, objects, the lair maps), and tsps
(almost nothing: a Callisto plugin from before the 2023 rework). Scope decisions made with the user:
shared lairs rather than team instances; loot by damage; the Escape Caves as the hub; all seven doors
in the Revenant Caves; no new kill tasks (only the ones the normal Leagues list already has for this
content).

## Where everything is

Code: `content/areas/misc/isle-of-souls/.../wilderness/`.

| File | What it is |
|---|---|
| `WildernessConfigs.kt` | References (npcs, locs, objs, seqs, spotanims, the timer) and `WildernessNpcEdits` (bosses never hunt or retaliate on their own, hero slots, respawn rates). Needs `packCache`. |
| `WildernessLairs.kt` | `WildLair` (the six lairs: arena, arrival, boss spawn, exit, the door in the Revenant Caves), `EscapeCaves`, `WildEntrance`. |
| `WildBosses.kt` | The director: one boss per lair, spawned for good at game start, each ticking itself through an npc timer (`wild_boss_tick`); target choice, chasing, delayed effects, healing when left alone. `BossFight`/`WildLogic` are the base of the three families. |
| `WildHits.kt` | The hits: a roll against defence with how much a protection prayer takes off (all, half, none), typeless hits, prayer and run drain. |
| `CallistoLogic.kt`, `VenenatisLogic.kt`, `VetionLogic.kt` | The mechanics of each family; the weaker version is the same logic with its own numbers. |
| `WildBossScript.kt` | Events: the seven doors, the lair exits, the Escape Caves hub, boss timer/hit/death/respawn, the spiderlings' bite, the Escape Caves' bears and spiders, `::wildlair`. |
| `WildernessRewards.kt` | Loot by damage and the kill credit. |
| `VoidwakerScript.kt` | Hilt + blade + gem = Voidwaker. |

Also changed: `IsleOfSoulsModule` (`WildBosses` is a shared instance), the task files (below),
`multiway.json` (7 areas), `surface.toml` and `tools/spawns/exclusions.txt` (the old surface Callisto,
Venenatis and Vet'ion are gone), `monsters.txt` + `osrs.toml` (drop tables for Artio, Spindel and
Calvar'ion, from the wiki), the symbols (`timer.sym` 29 `wild_boss_tick`, 9 counters in `varp.sym`,
17 task structs in `struct.sym`).

## The lairs and the doors

The lairs are the cache's own maps (found by scanning the map squares for their objects):

| Lair | Boss | Where | Level |
|---|---|---|---|
| Callisto's Den | Callisto (multi) | x 3351-3369, z 10317-10336 | 0 |
| Silk Chasm | Venenatis (multi) | x 3415-3431, z 10195-10213 (passage down to z 10183) | 2 |
| Vet'ion's Rest | Vet'ion (multi) | x 3287-3303, z 10195-10209 (passage down to z 10190) | 1 |
| Escape Caves | hub, bears and spiders | map square x 3328-3391, z 10240-10303 | 0 |
| Hunter's End | Artio (singles-plus) | Callisto's Den moved by (-1600, +1216) | 0 |
| Web Chasm | Spindel (singles-plus) | Silk Chasm moved by (-1792, +1344) | 2 |
| Skeletal Tomb | Calvar'ion (singles-plus) | Vet'ion's Rest moved by (-1408, +1344) | 1 |

The singles lairs are copies of the big ones far to the west, so their geometry is the same. The
seven doors are the real surface entrance objects, stood in the big cavern beside the Revenant Caves'
south arrival (x 3221-3232, z 10062-10073; positions found with the collision tools so each has an
open side to walk up to, the objects that close all sides but one face the way they do in the real
map). Move them by changing `WildEntrance.sw` and `front` in `WildernessLairs.kt`.

- **Enter** (or Jump-Down) puts the player at the north end of the arena. **Peek** says how many
  adventurers are inside. **Check-Fee** says there is no fee.
- **Big lairs' exits** land at the lair's own mouth in the Escape Caves (Callisto 3361,10296;
  Vet'ion 3342,10255; Silk Chasm 3376,10256: the three tunnel mouths in the cache's map).
- **The Escape Caves** are entered from the Revenant Caves (landing on one of the four exits' floor
  at random, as in the wiki) and their four exits all lead back to the door in the Revenant Caves.
  The wiki's "enter from the surface, not from a lair" prayer drain is not built.
- **Singles lairs' exits** go back to the Revenant Caves, in front of their own door.
- `::wildlair <callisto|hunters|silk|web|vetion|tomb|caves>` teleports an admin in.

Left out on purpose (the isle has no Wilderness rules): the 50,000 coin fee and its reduction by kills,
the Wilderness diary and Slayer task gates, the 3-tick teleport lock, dropping the fee on a PvP death,
the Escape Caves prayer drain, the ten-second shared exit.

## The fights

The cache has all the stats, sizes and defences; nothing was typed in. Each boss is a permanent spawn
that respawns by the wiki's respawn time (Callisto/Artio 11, Venenatis 10, Spindel 9, Vet'ion 15,
Calvar'ion 14 ticks, through `respawn rate`, so the respawn-speed knob scales them). Everyone in the lair is
a target; the boss heals and walks home after 50 ticks alone.

**Callisto / Artio** (1000 / 450 hp). Reworked 2026-10-18 against tsps's rsprox captures (its
`docs/wilderness-bosses.md`, `Bears.WildernessBosses.js`, `Traps.WildernessBosses.js`), which beat the wiki's
speeds. Walks up to a target. Cadence: melee then 6 ticks, ranged 5, the orb 8, the first attack 7 ticks after
the spawn (`npc_callisto_spawn01`). Melee 35-55 (Artio 15-35) never misses, hits everyone within a tile of the
target, Protect from Melee takes off half. Out of reach: a crescent (max 31 / 17, 30 + 6 per tile client cycles,
cast graphic, impact graphic + sound as it lands) at everyone, Protect from Missiles stops it. Every 8-12
attacks (a counter, not a die) a wind orb (`windblast_travel`, 60 + 12 per tile cycles): whoever is within a
tile of the target when it lands without Protect from Magic is thrown 3 tiles straight away from his middle
(8 compass directions by octant), takes 5 damage three ticks later plus 15 for each tile a wall stopped
them short; no stun. At 66% and 33% he roars (breaking a freeze; Artio's graphic is the small one); from the
FIRST roar five bear traps go down every 6 ticks on open floor within 12 tiles of his middle (graphic + sound,
the trap loc `callisto_trap_loc` a tick later, 30 ticks life). Stepping on one: message, spring anim, held 3
ticks, up to 15 damage; all traps spring when he dies.

**Venenatis / Spindel** (850 / 515 hp, speed 4). Never walks to a target: a player next to her gets the
lunge, everyone else the cycle of 8 ranged then 8 magic attacks. Every 4 attacks she walks to another
place; on the first ranged attack she calls 2 spiderlings (up to 6; 5 hp, they bite for up to 3 and
drain a prayer point whether or not it hits), on the third magic attack she spits a sticky web, 7x5
tiles for 12 ticks: 3 damage a tick, 3 prayer and 10% run energy. Max hits 21/35/30 (Spindel 14/31/24).
Living spiderlings raise her max hit by 10% each (to +50%) and cut protection by 20% each.

**Vet'ion / Calvar'ion** (255 / 150 hp per form, speed 6, 5 enraged). Walks up to a target. Five bolts of
lightning (one per player in a group, up to seven): he shouts and a glowing circle appears on each bolt's
tile, a tick later he swings (sparks on his weapon), the strike plays on the circles and the damage to the
3x3 lands as the 4-tick graphic ends (the middle
up to 44 / 26, the rest half); typeless. He walks only between attacks: each one holds him
still until it lands. Next to a player he may bash (one turn in three), by where the player stands in the ring round his 3x3
body: on the middle tile of a side, two straight lines in front of that side (5 tiles centred on the middle
one tile out, 7 tiles two out); on any other ring tile, the same two lines bent round the nearest corner.
Dark shadows (cache `gargboss_debris_shadow_120`) mark them for 4 ticks, then, on the same tick, he bashes (the shield
motion, cache name `defend_parry`), smoke bursts (`zebak_roar_wave_dust`, the user's pick) and whoever is on
them (or under him) at that moment takes up to 35 / 26 and is stunned 8 ticks. `::bashfx shadow|dust id` swaps the graphics
live. He stays facing the spot the bash was aimed at until it lands. He has no block animation (his hit reaction is idle); the shield block is what he does when he calls
his hounds. At half health he calls 2 skeleton
hellhounds (30 hp) and cannot be hurt while they live; the normal form falling makes him rise with full
health, enraged (twelve orange strikes in a circle round him, drawn on those exact tiles and hurting the 3x3 round each
like the other bolts, 25% harder hits, greater hellhounds at half health).
Five minutes enraged without a kill and he turns back with the health he has left.

**The Escape Caves** hold 15 bears (typeless slash, max 8, speed 4) and spiders (typeless, max 8,
speed 5) that notice a player within 10 tiles and never let go.

**Loot** (`WildRewards`): the top ten damage dealers each roll the boss's drop table. Ordinary drops are
scaled by their share of the damage (at least a quarter, never less than one); a unique (the rings, the
Voidwaker pieces, claws, fangs, skull, dragon 2h/pickaxe, long/curved bone, pet, elite clue) is kept
with a chance equal to the share. The kill counts once per team (a team shares its kill counters), for
the player of the team who did the most damage.

## Tasks

Only tasks the normal Leagues list already has for this content (area text removed, as for the other
bosses): Defeat Callisto, Venenatis and Vet'ion (992, 988, 996), each 50/150/300 times (1404-1406,
1398-1400, 1401-1403; Artio, Spindel and Calvar'ion count towards them as the wiki says), and the
gear tasks Equip a Dragon 2-Handed Sword, a Tyrannical Ring, a Treasonous Ring, a Ring of the Gods
and the Voidwaker (1003, 993, 989, 997, 1256). The Voidwaker is made by using the hilt, blade and gem
on each other. Slayer needs nothing: the bosses are in the Bears, Spiders and Skeletons categories and
the Boss category through the cache, so their kills count for a task as any monster's do.

## Guesses and what is not built

- Numbers the wiki does not give: how often the orb is thrown (1 in 6), the trap count and life, the
  stun lengths, how long a web lasts and how often it hurts, the spiderling effect sizes, a bolt's
  warning (3 ticks) and edge damage (half), how often Vet'ion bashes, the enraged hit
  increase, the Escape Caves' spawn spread and aggro range, the knockback direction (straight away
  from his middle).
- The sound/animation choices are the cache's names (`npc_callisto_*`, `npc_venenatis_*`,
  `npc_vetion_*`), the orb is the wind blast projectile (tsps capture), the web is the web impact graphic on each tile
  (the web floor objects were not used, their rotations are unknown).
- Not built: the cannon ban, shared exit tiles, the kill-count gate to peek, the Wilderness Slayer
  tertiary table, Echo versions.
- **Freezing** (added 2026-10-17): the isle's ice and bind spells already freeze npcs (`NpcFreezes`; the
  first version of this doc wrongly said they could not). A frozen boss does not walk, still attacks;
  Callisto's and Artio's roar breaks a freeze. New hook `SpellAccuracyOverrides` (api/spells, used
  by `SpellAttackManager.rollSpellAccuracy`): the first Bind, Snare, Entangle or Ice spell on Callisto or
  Artio while they are not frozen and the caster's magic attack bonus is 0 or more always hits
  (wiki), the ones after it have to land.
- **Yelled quotes** (added 2026-10-17): `VetionQuotes` has the real lines from the wiki's Vet'ion transcript, by
  cue (lightning, shield bash, hellhounds called, hellhounds dead, rising enraged, death); the enraged
  form says them in capitals, plus its own. There is no Calvar'ion transcript, so he shares them.

## Client checklist

1. After `packCache` and a restart, walk into the big cavern south of the arrival in the Revenant
   Caves: seven cave mouths; each Enter lands in its lair, facing the boss.
2. Callisto swings, throws the orb, roars (traps appear); Venenatis spits, summons, webs; Vet'ion's
   lightning glows before it lands, hellhounds appear at half health.
3. Exits: the big lairs open into the Escape Caves, whose exits go back to the cavern.
4. The task list shows Defeat Callisto/Venenatis/Vet'ion with no place in the text.

## Callisto rework (2026-10-18)

Compared with tsps's capture-based plugin and ported: the cadence (6/5/8, first attack at 7), the orb as a
counter of 8-12 attacks with the captured projectile, landing time, octant knockback, no stun and damage after
3 ticks; flat melee and ranged damage (no accuracy roll); ranged cast/impact graphics and sound; traps from
the first roar in waves of five with the real trap object, spring animation, 3-tick hold, messages and sounds;
Artio's small roar graphic and the spawn animation; and the loot split (shares are of the top ten's damage;
one unique roll per kill given to one of the ten by share; pets rolled per player at their share; clue scrolls
whole). Not ported by decision: the entry fee. New sound names in `synth.sym` (`wild_callisto_*`) need
`packCache`. Unverified: whether the trap loc blocks walking in the client, and the roar length.
