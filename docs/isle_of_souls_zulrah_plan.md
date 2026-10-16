# Isle of Souls: Zulrah

Status: **built 2026-10-08, not yet seen in a real client.** Sources: the OSRS wiki pages for Zulrah,
Zulrah/Strategies and Snakeling. Everything the wiki does not give is listed under "Numbers to tune"
and is a guess. Code: `content/areas/misc/isle-of-souls/.../zulrah/`.

Zulrah is the third boss instance after the Fight Caves and the Inferno (content plan 3.4, "many more
bosses"). She has no echo version in Raging Echoes, so she is a normal boss.

## Decisions (user, 2026-10-08)

1. **Venom:** a small venom system, yes (`PlayerVenom`).
2. **Who fights:** a team instance of up to 2, one Zulrah, no scaling (as the Caves and the Inferno).
3. **Tasks:** no new tasks. The Raging Echoes league's own Zulrah tasks are wired: 540 Defeat Zulrah,
   553 / 554 / 1397 Defeat Zulrah 50 / 150 / 300 times, 1390 Dismantle a Zulrah scale unique. Task 1428
   (Zulrah Combat Achievements) is not wired: the isle has no Combat Achievements.
4. Everything else (entrance, rewards, numbers) took the plan's defaults.

## What was built

| File | What it is |
|---|---|
| `ZulrahRotations.kt` | The four rotations as data (forms, spots, action lists, and the tiles of every cloud barrage and snakeling orb), plus `ZulrahCloudSites`; checked by `ZulrahRotationsTest`. |
| `ZulrahConfigs.kt` | References (npcs, locs, objs, seqs, spotanims, timers, varps), the `isle_venom` varp, and `ZulrahNpcEdits` (needs `packCache`). |
| `Zulrah.kt` | `ZulrahArena` (map block, spots, start/exit), `ZulrahRun`/`ZulrahFight` state and `Zulrah`, a `WaveDirector` with one wave: the phase state machine, attacks, clouds, orbs, snakelings, tail swing. |
| `ZulrahCombatScript.kt` | No damage cap or underwater invulnerability, her death (drops land where the killer stands), the snakelings' attacks and death. |
| `ZulrahScript.kt` | The boat (entrance), the exit scroll, the run and venom timers, venom cleared on login, dismantling the uniques. |
| `PlayerVenom.kt` | Venom: 6 then +2 a hit up to 20, every 30 ticks, cures and immunity. |
| `ZulrahRewards.kt` | Task counters (once per team) and the isle announcement. |

Also changed: `WaveDirector.waveMessage` (so Zulrah says nothing at the start), `LeagueTaskList`,
`IsleOfSoulsTaskEnums` (keys 324-328), `IsleOfSoulsTaskEdits` (the Zulrah tasks made area-free),
`LeagueTaskVars` (three kill counters), `IsleOfSoulsModule`, `.data/symbols` (`timer.sym`, `varp.sym`,
`struct.sym`), `tools/drops/monsters.txt` + the regenerated `osrs.toml` (Zulrah's wiki drop table, rolled
by the ordinary drop system: scales always, two rolls on the main table, uniques, jar, pet, elite clue).

### How the fight works

- **Entrance:** the sacrificial boat (`snakeboss_boat_1op`, "Board", 3x3) on the lobby plaza at
  (2200, 2855); leaving puts you at (2203, 2856). The lair is a copy of the block of 8x8 zones x 2240-2303,
  z 3040-3103 (the shrine lies across map squares 35_47 and 35_48). Players appear at (2268, 3069); the
  Zul-andra teleport scroll (`snakeboss_exit`, "Read") at (2268, 3068) is the way out.
- **Zulrah** is one npc. The form is changed with `Npc.transmog` (the player formulas read `visType`, so
  the defences change with it). She starts green in the centre, rises (spawn animation, 5 ticks), then
  runs her rotation: each phase she does its actions, dives (3 ticks, `submerged`: Attack stays on the menu and hits still land in full), and rises (3 ticks) in the next form at the next spot. The first rotation is picked at random. The last
  phase of a rotation (five ranged, then the four opening barrages) counts as the first phase of the next
  one, which may be the same: it carries on from its second phase, without a second fill. From then on
  one phase in ten shows a different form than expected (wiki: "a chance to be in a different phase").
- **Actions:** ranged and magic shots (max 41, every 3 ticks, rolled when fired, prayer applied when the
  hit lands, 1 hit in 4 that does damage may envenom); blue form picks magic or ranged at random; tail swing
  (red only: two swings, aimed at the tile the player stands on at ticks 0 and 9 of an 18-tick action and landing at ticks 5 and 15 (Near-Reality via tsps); 20-30 typeless, stun for 3 seconds (5 ticks), envenoms, throws the player up to 4 tiles away from her; whoever is within a tile of the aimed tile is hit, so moving away in time avoids it; the four tiles beside the pillars, (2263/2264/2272/2273, 3072), are never hit);
  cloud barrage (two 3x3 venom clouds on the fixed sites the rotation data gives, whoever stands where; 1-4
  damage a tick, no venom (wiki), last 30 ticks; a second cloud on the same site renews it); snakeling orb
  (lands on the tile the data gives, or the nearest platform tile if that is water; a snakeling comes out, one
  in four magic, attacking 3 ticks later; lives 67 ticks; one hitpoint). A red phase still throws the clouds
  and orbs it lists: only its shots turn into tail swings.
- **Targets:** each phase she picks a random player of the instance and shoots only them.
- **Death:** `bossDied` wins the run (tasks, announcement), the death script plays her animation and rolls
  her drops at the killer's tile (the water is not reachable). Players keep the lair for 5 minutes, then
  are sent out.
- **Venom** (`PlayerVenom`): first hit 6, +2 each time, max 20, every 30 ticks, shown as a venom hitsplat
  and the client's poison orb (varp `poison` set from 1,000,000 up). Immune with the charged serpentine
  helm worn or while an antipoison's protection runs (`PotionEffects.hasPoisonProtection`). Cured by
  leaving the lair, dying or logging in. The isle has no antivenom; the cache's antivenom / antidote+
  items are not craftable here.

### Differences from the real game

- Melee reach is not restricted (the wiki: since 2025 only halberds reach her); in practice nobody can
  melee her anyway, she is in the water.
- No stepping-stone / sacrifice quest, no deathbank, no Konar slayer drops (brimstone key), no
  Combat Achievements, no Elite Western diary resurrection.
- Poison (the weaker sickness) does not exist; venom and its cures are simplified as above.
- **Where the tiles come from:** the wiki gives no cloud, orb or Zulrah tiles. They are the Near-Reality
  server's, as ported by the RSPSApp/tsps project (`ZulrahRotations.js`, `docs/zulrah.md`), and the port
  itself says they are unchecked against the real game. The ten cloud sites form a 3-tile lattice that tiles
  the platform, and the opening fill leaves exactly the tips clear, as the wiki says. See
  `docs/zulrah_cloud_tiles_per_phase.md` for the map of every phase.
- The snakeling's hitpoints are set when it spawns (a type's hitpoints cannot be edited by `packCache`:
  the cache says 10, the wiki says 1).

## The fight (wiki)

Zulrah is one npc fighting in four forms; she dives, moves to one of the four spots (centre, west, east,
south) and resurfaces in the next form. All forms have 500 HP together (the hp is one pool, copied to the new
npc on each change, or one npc whose type changes). Hits are not capped. She is immune to poison
and venom. **Since 2019 she does not change phase at zero hp: she dies at 0 whatever the phase.**

After a kill she comes back in the same lair for whoever is still inside (a guess: 50 ticks, scaled by the
respawn speed), with a fresh rotation. Each kill counts for the tasks and the announcement.

| Form | Colour | Attack | Weak to |
|---|---|---|---|
| Serpentine | Green | Ranged (max 41 at level 99 ranged defence 0) | Magic |
| Magma | Red | Melee tail swing at the player's tile, typeless, stuns | Magic (most resistant) |
| Tanzanite | Blue | Magic or ranged | Ranged |

- **Attack speed:** 3 ticks (the page lists 3; verify in the probe, the in-game rate has been 4).
- **Damage is rolled when the attack is made, not when the projectile lands** (so prayer does not stop a hit
  already flying, but the player can still react to the tell).
- **Tail swing (red):** each swing's animation starts as she aims: she turns to face the tile the player stands on then (she does not follow the player) and the tail lands there 5 ticks later (6 for the second); moving two tiles away avoids it. Her next action, and her dive, wait for the whole 18 ticks. Melee only reaches her with a halberd (`NpcMeleeBlocks`, so a salamander also gets round it).
- **Jad phase:** the green form on the west or east that alternates ranged and magic, 8-10 attacks.
- **Venom clouds** (any form, "barrage" by Zulrah): 3x3 tiles that deal damage every tick to anyone standing in
  them (they do not envenom). **Snakeling orbs:** Zulrah spits orbs that become snakelings on the ground; snakelings are
  melee (stab) or magic (shoots venom), 1 HP, die after 40 seconds or with Zulrah.
- **Rotation:** four fixed rotations chosen at random at the start; the second phase says which one. Each takes
  about three minutes; after the last phase the next rotation starts (it may be the same). All rotations
  after the first can randomly show a different phase or switch between magic and ranged unpredictably.

### The four rotations (phase: position, form, what she does)

Position C = centre, W = west, E = east, S = south. "Clouds" = venom cloud barrage, "Orbs" = snakeling orb,
"R" = ranged, "M/R" = magic and/or ranged, "melee" = tail swings.

**Rotation 1 (crimson A)**
1. C green: 4 clouds. 2. C crimson: 2 melee. 3. C tanzanite: 4 M/R. 4. S green: 5 R, then 2 orbs, 2 clouds, 2 orbs.
5. C crimson: 2 melee. 6. W tanzanite: 5 M/R. 7. S green: 3 clouds, 4 orbs. 8. S tanzanite: 5 M/R then
alternating 5 orbs and clouds. 9. W green (Jad): 10 attacks alternating R / M starting with R, then 4 clouds.
10. C crimson: 2 melee. 11. C green: 5 R, then 4 clouds.

**Rotation 2 (crimson B)**
1-3 as rotation 1. 4. W green: 3 clouds, 4 orbs. 5. S tanzanite: 5 M/R, then 2 orbs, 2 clouds, 2 orbs.
6. C crimson: 2 melee. 7. E green: 5 R. 8. S tanzanite: 5 M/R, then alternating 5 orbs and clouds.
9. W green (Jad): 10 alternating starting with R, then 4 clouds. 10. C crimson: 2 melee. 11. C green: 5 R, 4 clouds.

**Rotation 3 (serp)**
1. C green: 4 clouds. 2. E green: 5 R, 3 orbs. 3. C crimson: alternating 6 clouds / orbs, then 2 melee.
4. W tanzanite: 5 M/R. 5. S green: 5 R. 6. E tanzanite: 5 M/R. 7. C green: 3 clouds, 3 orbs. 8. W green: 5 R.
9. C tanzanite: 5 M/R, then 2 clouds, 3 orbs. 10. E green (Jad): 10 alternating starting with M. 11. C tanzanite:
4 orbs. 12. C green: 5 R, 4 clouds.

**Rotation 4 (tanz)**
1. C green: 4 clouds. 2. E tanzanite: 4 orbs, 6 M/R. 3. S green: 4 R, 2 clouds. 4. W tanzanite: 4 orbs, 4 M/R.
5. C crimson: 2 melee, 2 clouds. 6. E green: 4 R. 7. S green: 6 orbs, 3 clouds. 8. W tanzanite: 5 M/R, 4 orbs.
9. C green: 4 R. 10. C tanzanite: 4 M/R, 3 clouds. 11. E green (Jad): 8 alternating starting with M.
12. C tanzanite: 4 orbs. 13. C green: 5 R, 4 clouds.

Notes: the first phase of every rotation fills the shrine with clouds and leaves the two tips (east and west)
clear; the Jad phase of rotations 1 and 2 ends with clouds leaving both sides clear; rotations 3 and 4 send no
clouds after the Jad phase. The final tanzanite phase of rotations 3 and 4 summons 4 snakelings (2 each side).

## Tests

- Unit: `ZulrahRotationsTest` (9) checks every phase of every rotation against the lists above.
- Integration (sandbox harness): `ZulrahRunTest` (22: enter, platform tiles, clouds, dive and rise in the
  right form and spot, shot rate, orb to snakeling and its lifetime, tail swing reach and stun, cloud
  damage and venom, the venom ladder 6-8-...-20, helm immunity, cure on leaving, hits taken in full (also under the water), the kill and the respawn, the once-per-team counters, the drop at the killer's tile,
  dismantling a unique, the rotation wrapping) and `ZulrahLobbyTest` (3: boat tiles free, boarding and
  leaving by the scroll, the copied block holds the platform and the four spots).
- Needs `./gradlew packCache` (new `NpcEditor` edits and `StructEditor` area edits).

## Numbers to tune (guesses; the wiki gives none)

The cloud, orb and surfacing tiles (Near-Reality's, see above), cloud damage (1-4 a tick) and duration (30
ticks) (also Near-Reality's), the hit chance of venom (1 in 4), dive / rise lengths (3 + 4 ticks, tsps) and the wait before her first action (5 after rising, tsps),
the tail's timings (18-tick action, strikes at 5 and 15, tsps/Near-Reality), the knock-back (4 tiles), the respawn after a kill (50 ticks), a barrage's length (3, tsps), the odds of a different
form (1 in 10) and of a blue form picking magic over ranged (even), the snakeling's magic range (6, Near-Reality's) and
attack rate (3), the projectile spotanims (green orb for ranged, blue fireball for magic, double orb for
clouds, egg for snakelings: picked from the cache's names), and whether her attack speed is 3 (the
wiki's infobox) or 4 in play.

## Client checklist (with the server stopped and `./gradlew packCache` run once)

1. The boat on the plaza at (2200, 2855): where it stands, that "Board" works from the front.
2. In the lair: Zulrah's size and where she rises (centre, west, east, south); does she overlap the
   platform or float over land?
3. Dive and rise animations; does the transmog flicker her health bar or target?
4. The projectiles (green, blue, cloud orb, snakeling egg) and where they land.
5. The cloud loc: 3x3, plays its animation, disappears after about 25 ticks.
6. The tail swing animation (left / right) and where the stun and hit land.
7. Venom: the hitsplat colour, the poison orb turning green, the damage ladder.
8. Her drop on the killer's tile; the pet snakeling; the clue casket line.
9. The Zulrah tasks in the league task list (no area shown), and dismantling a fang / magic fang /
   visage for 20,000 scales.

## Known gaps

No way to cure venom on the isle other than leaving the lair (no antivenom, antidote+ or antidote++
recipes in Herblore; the cache items exist). No serpentine helm source (the visage can be dismantled but
there is no crafting or charging). Venom is not saved across a logout (cleared on login).
