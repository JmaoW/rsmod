# Isle of Souls: Scurrius (scope)

Status: **built and tested 2026-10-16, not yet seen in a real client.** (Scoped the same day; the scope text below is kept, the build notes are at the end.) Sources: OSRS wiki pages for Scurrius and
Scurrius/Strategies, the tsps plugin `server/plugins/bosses/Scurrius.plugin.js` (reference only; its
tick counts are unverified), and our own cache and symbols. Scurrius is a normal boss (no echo
version). Pattern: the Zulrah build, a `WaveDirector` with one wave and a custom state machine, see
`isle_of_souls_zulrah_plan.md`.

## What already exists

| Piece | State |
|---|---|
| Cache npcs `rat_boss_normal` (7221, public), `rat_boss_instance` (7222), `rat_boss_giant_rat` (7223), `scurrius_pet`, `rat_boss_biologist` | In the cache, examine texts in `npcs.toml`. **No combat stats** (the cache has none): needs an `NpcEditor`, packed with `packCache`. |
| Seqs 10686-10706 (spawn, idle, feeding idle, walk, melee x2, ranged, magic, stomp, summon, enrage, death, plus feeding variants) | In `seq.sym`. |
| Spotanims 2638-2644 (magic cast, projectile, impact, ranged projectile, impact, falling debris) | In `spotanim.sym`. |
| Locs `rat_boss_entrance` (14203, "Climb-through (normal)/(private)/Peek"), `rat_boss_exit` (14204), `rat_boss_food_pile` / `_pile2` (14205/14206, "Eat") | In the cache and on the map at the real coordinates (below). |
| Items: `rat_bone_mace`, `rat_bone_bow`, `rat_bone_staff`, `rat_boss_spine`, `scurriuspet`, `big_bones`, `raw_rat_meat` | In the cache; `bone_mace` / `bone_shortbow` already have `BaseObjs` refs (no staff ref yet). |
| Varps/varbits `total_rat_boss_kills`, `rat_boss_player_eat_from_food_pile`, `buff_scurrius_food_pile` (4139), `rat_boss_food_pile_player_eat_delay` (9581) | In the symbols. |
| Mastery task 3 "Defeat Scurrius" | **Already wired**: `LeagueMasteryKills` completes it on any npc named "Scurrius". The kill event just has to fire. |
| Raging Echoes tasks: `league_task_defeat_scurrius`, `_10_times`, `_25_times`, `_scurrius_combat_achievements` | Varbits exist; counters not wired. |
| Drops | Only a `boosted.toml` entry for the spine. **No drop table yet**: run `tools/drops/wiki_import.py` with `Scurrius` in `monsters.txt`. |
| Varrock Sewers dungeon | Built (`Dungeon.VARROCK`), in place on the real map; its `DungeonArea` stops at x 3300, the lair runs to x 3309. |

## Where the lair is (read from the cache with `tools/dungeons/census.py`)

Everything lies in map square 51_154. Broken bars (entrance) at (3282, 9867), sewage water (exit)
at (3288, 9867), twelve food piles on the east and west edges of the room (x 3298-3299 and 3307-3308,
z 9858-9859 and 9867-9877), corpses and roots decoration out to x 3309, z 9857-9878. The block to copy
for an instance is zone-aligned: x 3272-3319, z 9856-9887 (6 x 4 zones, `copyZone` 409 / 1232), same
technique as the Zulrah lair.

## Mechanics to build (wiki, with tsps for the unknowns)

**Stats.** Private instance (npc 7222): 500 HP, Attack 300, Strength 100, Defence 60, Magic 50,
Ranged 50, size 3x3, attack speed 4, Defence bonuses stab/slash/crush +20, ranged +20, magic +10,
immune to cannons, 50% freeze resistance, venom turns into poison, +20% XP, rat attribute (for the
ratbane/Slayer rules). Max hits: melee 13, magic 8, ranged 7, rockfall 22 (the "instance" column on
the wiki; the 14/13/24 values are the public room, which we do not build).

**Phases** (thresholds on max HP: 80% = 400, 30% = 150).
1. 100-80%: melee tail swipe (no warning), occasional Falling Bricks, from the east side.
2. 80-30%: at 80% he moves to a random nearby Food Pile (breaks freezes) and eats ten times, but only
   five eats heal. The heal goes to **him** (the wiki text is ambiguous): 5 per player in the room per heal, so 10 for a team of two (tsps uses 5 a heal, 8 ticks apart: guess).
   While eating he uses only magic, ranged, bricks and summons; between eatings he is back in melee.
   Scurrius takes slightly reduced damage while eating. If he heals above 30% he stays phase 2.
3. Below 30%: back to the centre, ranged and magic only, faster (tsps: speed 3), bricks several
   times in a row.

**Attacks.** Tail swipe (melee, Protect from Melee negates). Bolts of Electricity (magic, impact
hit, Protect from Magic). Flying Fur (ranged, impact hit, Protect from Missiles). Falling Bricks:
shadows mark tiles, one always lands on the player's tile at cast time, the rest random, 15-22
damage, prayer does not help (tsps: ~15-20 tiles in radius 7, 3 tick wind-up: guess). Summon: six
level 46 giant rats around the arena, up to 12 waves, they outlive Scurrius and block Food Piles.

**Giant rats.** One-hit kills; wiki: max 4, Protect from Melee cuts it to 0-1. Rat bone weapons
(mace, shortbow, staff) attack with **no delay** against them.

**Players.** Food Pile "Eat" outside combat heals to full once per 10 minutes. Prayer 43 matters; no
cannons or fires in the lair.

**Rewards.** Two drop rolls like the wiki: MVP table (big bones + raw rat meat always, 6/100 and 3/100
rows, cheese 1/100, clue, spine 1/33, long bone 1/400, curved bone 1/5012, pet 1/3000) and the non-MVP
table for the second player. Drops land on the boss's centre tile; long bone, curved bone and clue on
the south-west tile. Drops disappear after 2.5 minutes of ground time (wiki), the team keeps the
arena for a few minutes like Zulrah.

## As built (2026-10-16)

Code: `content/areas/misc/isle-of-souls/.../scurrius/`. Tests: `ScurriusRunTest` (17 integration tests, all
green; the whole isle integration suite was not rerun). User decisions: the defaults in this doc.

| File | What it is |
|---|---|
| `ScurriusConfigs.kt` | References (npcs, locs, objs, seqs, spotanims, timer), `ScurriusNpcEdits` (boss never hunts, no flinch; rats chase). Needs `packCache`. |
| `Scurrius.kt` | `ScurriusArena` (zone block 6x4 at zone 409/1232, start, exit, the three food piles, spots), `ScurriusRun`/`ScurriusFight`, `Scurrius` (a `WaveDirector` with one wave: the phase state machine, walking, eating, attacks, rockfall, rats), `ScurriusAttacks`. |
| `ScurriusCombatScript.kt` | Death (drops at the middle of his 3x3), eating damage reduction, rat bites and deaths, the ratbane delay. |
| `ScurriusScript.kt` | The bars (both climb-through options go to the private lair, Peek gives a message), the sewage water (Cross / Quick-escape), the run timer, food pile "Eat". |
| `ScurriusRewards.kt` | Non-MVP drops for the rest of the team. |

Also changed: `NpcAttackDelays` (new, `api/combat/combat-scripts`) and the four attack-delay sites in `PvNCombat`
(a content hook that can change a player's delay against one npc); `LeagueKillTasks` (tasks 1648, 1649, 1650
with counters `isle_count_kill_scurrius_10/25`), `LeagueTaskList`, `LeagueTaskSeries`, `IsleOfSoulsTaskEnums`
(keys 486-488), `IsleOfSoulsTaskEdits`, `LeagueTaskVars`, `IsleOfSoulsModule`; symbols `timer.sym`
(`scurrius_tick`), `varp.sym` (two counters), `struct.sym` (`league_task_1648-1650`); `npcs.toml` (boss
anims, defences, +20% XP, 600 Slayer XP, cannon immune, venom turns to poison); drop data (`Scurrius` in
`monsters.txt` and the regenerated `osrs.toml`, the non-MVP table in `isle.toml` on the unused
`rat_boss_giant_rat_patrol_control` npc).

### How the fight works

- **Entry:** "Climb-through" (normal or private, both the private lair) on the broken bars at (3282, 9867); the
  team's lair is a copy of the room. Players appear at (3291, 9867); the sewage water is the way out, to
  (3281, 9867). The kill respawns him 30 ticks later in the same lair, so kill counts can be farmed.
- **Phase 1 (above 80%):** he walks up to the nearest player (to a tile that puts his 3x3 beside them; after 10
  ticks of not reaching anyone he shoots instead) and swipes (max 13) or, one turn in six, brings down
  rockfall. Attack rate 4 ticks.
- **At 80%:** he walks to a pile (nearest two times in three), eats ten bites 4 ticks apart, five of them
  heal 5 per player in the room. While eating he uses only magic (max 8), ranged (max 7), rockfall and
  summons, at 4 ticks, and takes 10% less damage. Then he swipes again. He eats once per fight.
- **Below 30%:** he roars, walks to the middle and stays; only magic, ranged, rockfall (often 2-3 in a row) and
  rats, every 3 ticks.
- **Rockfall:** the animation, then a shadow on the player's tile and 14-19 floor tiles; 5 ticks later (2026-10-16: was 3, hit landed early) anyone
  standing on one takes 15-22 typeless (no prayer).
- **Rats:** six per summon, up to twelve summons; 1 hitpoint, no drops, very accurate, up to 4, up to 1 through
  Protect from Melee. Bone mace, bow and staff have a one tick delay against them.
- **Rewards:** the ordinary drop table for the top damage dealer (wiki MVP table), the non-MVP table for each
  other player in the lair; tasks 1648 (once), 1649 (10) and 1650 (25) and mastery task 3 complete on the kill
  through the existing `NpcDeathEvents.Killed` hooks.

### Differences from the real game and guesses

- No public room, no Peek counts, no grave or Aldo, no music, no Combat Achievements (task 1641 skipped), no
  Slayer assignment hook (the cache gives him slayer category 3, Rats, but the rules were not checked).
- Healing: each bite pair heals him 5 per player (a guess from "5 or 10 HP to each player"). He eats only once
  per fight (tsps does the same); the wiki may have him eat again if he falls back below 80%.
- All ticks are tsps's or guesses: rockfall delay 3, bite spacing 4, enraged rate 3, rat wake 3, death animation
  6 ticks (the death seq has no frame data to read a length from), the 10% eating reduction.
- Giant rats have 1 hitpoint (the cache says 15) so any hit kills; their prayer rule is the wiki's "0-1".
- The attack animation choice for the swipe (two left/right variants) is random; the spawn, idle and
  enrage anims were chosen from the seq names.
- The Varrock Sewers `DungeonArea` was left alone (the lair only exists as an instance).
- A food pile heals a player once per 10 minutes only until the server restarts (kept in memory).
- Needs `./gradlew packCache` (npc edits, the new struct/timer/varp symbols, the task enum).

### Client checklist

1. Climb through the bars: you appear at the west side of the room, Scurrius is on the east.
2. He walks up and swipes; rockfall shadows appear on the floor and 5 ticks later hurt.
3. At 400 hp he walks to a pile (face it, bite animation), at 150 hp he walks to the middle and roars.
4. Rats show a bite animation and die in one hit; the bone weapons attack every tick on them.
5. Task list shows "Defeat Scurrius" (+10 / 25 times) as area-free tasks.
