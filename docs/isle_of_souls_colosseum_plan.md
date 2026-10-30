# Isle of Souls: the Fortis Colosseum (implementation plan)

Written 2026-10-30 for review before any code. **Status: scoped, nothing built.**

Sources: the tsps `docs/fortis-colosseum.md` (read 2026-10-30; it is built from the OSRS wiki, a live
rsprox capture of the lobby to wave 1, the cache, and Offline_Scape's port, and it marks which is
which), the revision 233 cache symbols, and the Raging Echoes task list. **I have not yet checked
tsps's wiki numbers against the wiki itself**; step 0 does that. Anything that is neither wiki nor
capture is marked **(guess)** or **(RSPS)** (an RSPS port's value) and listed under "Open numbers".

## Decisions (user, 2026-10-30)

| Question | Decision |
|----------|----------|
| Who plays | **Team of 2** in one instance, as the Inferno and Gauntlet. No scaling. |
| Length | **Full 12 waves + a shortened mode**, picked by an event-settings key (`colosseumMode`, like `infernoMode`). |
| Shortened run | **Waves 1, 4, 7, 10, then Sol (12)**. Each keeps its real wave number for composition, loot and Glory. Modifier picks still happen before each. |
| Modifiers | **All 14 with their tiers**, the real offer rules, real Glory. |
| Entrance | **The real underground lobby** (Minimus, Gloria, bank chest gated by Glory, the tunnel warning), copied into the team instance, reached from an entrance on the isle. |
| Duo loot | **Each player rolls their own loot every wave**, into their own chest, as if each did a solo run. Fanatic duplicate protection per player. |
| Duo death | **The dead player respawns in the lobby and can walk back in at the next intermission, keeping their chest.** The run ends (both lose their chests) only when both are dead at once, or on a wave both were out of. |
| Modifier pick | **Either player; the first confirm locks it in** for the team and closes the screen for the other. |

Defaults I am using unless you change them:
- Sol Heredit in full (phases, Spear/Shield patterns, triple, grapple, light crystals, molten sand).
- The chest-and-forfeit loop: leave between waves through Minimus to claim, die/teleport/log out and lose.
- Glory, titles (Rookie to Grand Champion), Smol Heredit at 1/200. The 2,000 Glory bank chest gate is built but off for the event (see Answers).
- **No echo Sol Heredit.** The echo bosses still need their own conversation; nothing here creates echo npcs.
- Death is the event's normal safe death (respawn in the lobby, keep items), not the real unsafe one.

## What already exists

| Need | Where it is |
|------|-------------|
| Every npc | `npc.sym` 12807-12833: `colosseum_master(_outside)`, `colosseum_glory`, the warband (`warbander_ranged_female`, `_mage_male`, `_melee_male`), `standard_mager` (shaman), `jaguar_warrior`, `javelin_colossus`, `shockwave_colossus`, `manticore`, `minotaur(_routefind)`, `sol_p1`, `boss_seated`, `beam_crystal`, `healing_totem`, `solar_flare`, `modifier_bees`, the gibs, lobby guards 13110-13113, `solheredit_pet` |
| Interfaces | 865 `colosseum_intermission_2` (modifier pick, cs2 4931), 246 `colosseum_reward_chest`, 866 `colosseum_reward`, 867 `colosseum_scoreboard`, 592 `dizanas_quiver` |
| State | varps 4127-4139 (Glory, current Glory, wave start, damage taken, last modifier Glory, durations, rewards, killtime); varbits 9788 (selected modifier), the 10 modifier tier varbits, 9800 grapple pending, 9801/10681 Doom, 9802 loot opened, 9804/9807/9809/9810 talk and cutscene flags, 9811/9812 kill times, 9993 entry warning, 11410 highest wave |
| Loot | invs 843/844/845 (so far / next / last), dbrows `synth_colosseumrewards`, `synth_npccolosseum`, `synth_colosseummodifiers` |
| Items | sunfire splinters + runes, fanatic helm/body/legs + set, Dizana's quiver (uncharged/charged/infinite/broken), Tonalztics (special attack already built in `RareRangedSpecials.kt`), Smol Heredit, echo crystal |
| League tasks | cache varbits 17457 (teleport to the Colosseum), 17464 (bank chest), 17465 (Fortis salute), 17466-17468 (waves 1/6/12) |
| Collection log | `sol_heredit_kills` source and kc varp already wired; `tools/clog/coverage.txt` lists 7 missing Colosseum items |
| Engine | the `waves` package (`WaveDirector`/`WaveRun`/`WaveFile`), team instances with `copyAllLevels`, `NpcAttacks`, `NpcEditor`, the Inferno's view-distance and retaliation fixes, boss kill tasks |

**Gaps:** Dizana's quiver bonus is a TODO (`api/player/.../WornBonuses.kt:199`). No engine hooks yet
for an attack-range penalty (Myopia), a temporary unsaved max-HP cap (Frailty), forced max hits (the
warband's weakness, Sol's perfect parry), npcs walking through npcs (warband, bees, the flare orb),
several spotanims on one npc at once (the Manticore's orbs), or a custom hitsplat type with a count
(Doom). There is no combat achievement system, so "Colosseum Combat Achievements" has no source.

## Duo rules (the real game is solo, so these are mine)

- **Targets.** Each enemy picks the nearest living player when it spawns and keeps them unless they die
  or leave **(guess)**. Each warbander heads for its own tile beside *its* target, so two trios can end up
  around different players.
- **Reinforcements** come through the gate on the half where most living players stand; a tie goes to
  the leader's half **(guess)**.
- **Damage-taken and Glory are per player**: "no damage" is lost only by the player who got hit. Each
  player's best run sets their own Glory, title and bank chest access.
- **Modifiers apply to the team** (one pick, one tier), but stacking effects (Doom, Blasphemy, Mantimayhem
  venom) are tracked per player.
- **Sol** targets one player at a time, nearest first; the triple and the grapple go to his target. Phase
  beams of light fall around **each** living player **(guess)**. AoE patterns hit anyone in them.
- **Rejoin.** A player who died stays in the lobby with their chest frozen. At the next intermission the
  tunnel lets them back in. They keep their chest but get no loot for the wave they missed.
- **Leaving.** Minimus's Leave works per player. One player can forfeit and claim their chest while the
  partner goes on alone.

## Waves

Composition (tsps, which credits LlemonDuck's RuneLite plugin and says it matches the wiki; recheck in
step 0):
- The Fremennik trio (berserker north, archer west, seer east of the target) every wave; the serpent
  shaman on waves 1-6.
- Javelin Colossi: 1 on wave 2, 2 on wave 3, then alternating 2/1 from wave 5.
- Manticores: 1 on waves 4-8, 2 from wave 9.
- Shockwave Colossus on waves 7, 8 and 11.
- Quartet adds a random warbander to the south; Dynamic Duo doubles the Shockwave Colossus.
- Wave 12 is Sol Heredit alone.
- **Reinforcements** after 67 ticks if the wave isn't cleared: jaguar warrior (waves 1-6), a shaman
  (4-6, 10-11), the Minotaur (7-11, the route-finding one with Red Flag). None on 12.
- Spawns: the trio around a random tile in (1821-1827, 3105-3111) (real coordinates, translated into the
  instance); the rest at distinct points among 12 fixed ones, none within 4 tiles of any player.

Both lists live in TOML files read by `WaveFile`, like the Caves and Inferno: `colosseum_waves.toml`
(12) and `colosseum_waves_shortened.toml` (1, 4, 7, 10, 12).

## Enemies (tsps / wiki)

| Enemy | Max hit | Range | Speed | Special |
|-------|---------|-------|-------|---------|
| Berserker | 29 | 1 | 6 | Weak to magic: always takes the player's max hit from it |
| Seer | 12 | 1 | 6 | Magic from melee range; weak to ranged |
| Archer | 14 | 1 | 6 | Ranged from melee range; weak to melee |
| Serpent shaman | 27 | 10 | 5 | Magic |
| Jaguar warrior | 47 | 1 | 5 | Three hits, each rolled separately |
| Javelin Colossus | 48 | 15 | 5 | Every 5th attack: sky javelin onto the target's tile, up to 40 typeless (3 ticks RSPS, wiki says 6) |
| Manticore | 31/36/31 | 15 | 10 | Charge, then magic/ranged orbs in a shown order, then melee; each rolled against the prayer at throw |
| Shockwave Colossus | 56 | 15 | 5 | Magic |
| Minotaur | 74 | 1 | 5 | Damage a tick after the swing; heals hurt enemies within 6 tiles while not in melee range |

The warband moves differently: runs, walks through npcs, routes around pillars to its side tile, and skips
a swing if it was still moving. Animation and graphic ids are in the tsps doc; I'd take them from there and
check each against `seq.sym`/`spotanim.sym` names.

## Modifiers

Enum 5312 lists the 14 by the key cs2 4931 takes; each is a struct (params 1896 name, 1897-1899 tier
texts, 1901 tiered, 1903 Glory per wave, 1914 icon). Offer rules (wiki): wave 1 is always Blasphemy,
Relentless, Frailty; Red Flag and Dynamic Duo only from wave 7 (Dynamic Duo not after 11); a maxed or
taken untiered modifier is not offered again; upgrades of picked ones are favoured (tsps uses twice as
likely, **guess**).

| Modifier | Effect | Engine need |
|----------|--------|-------------|
| Blasphemy I-III | Prayer drained by 20/40/60% of each hit taken | Hit-taken hook (exists) |
| Doom I-III | A stack per hit; 15/10/5 stacks kill; cleared at wave end | Counted hitsplat type |
| Frailty I-III | Max HP -10/20/40% for the run | Temporary level cap |
| Myopia I-II | Attack range -2/4/6 (min 1), manual casts unaffected | Attack-range hook |
| Relentless I-III | Enemy max hits +1/3/6; accuracy as if 33/66% Defence gone; III always hits | Npc accuracy hook |
| Mantimayhem I-III | Two of each orb; venom if not prayed; random order | Spotanim slots |
| Bees! I-III | 1-3 swarms stepping toward a player every 12 ticks, poison | Walk-through npcs |
| Solarflare I-III | Orb circling a pillar; III turns prayers off | Walk-through npcs |
| Totemic | Healing totem by any enemy at half HP or less | none |
| Volatility I-III | Dead enemies explode; III leaves molten sand | none |
| Reentry I-III | Sky javelins leave molten sand, permanent from II | none |
| Quartet, Dynamic Duo, Red Flag | Composition changes | none |

Solarflare and Volatility damage, the totem heal rate and bee spawn points are not on the wiki
(**guesses**, below).

## Sol Heredit (wave 12)

1,500 HP, size 5. Greeting by attempt count (varbit 9810), jump-down cutscene (no camera), barricade
locs 50753-50758 go up around the four pillars, players placed at (1825, 3103), Sol lands at (1823, 3108)
(**RSPS** tiles).

- **Spear 1/2** (speed 7, 6 from 75%) and **Shield 1/2** (6, 5 from 75%): AoE patterns up to 44 typeless,
  landing 2 ticks after the swing; a repeat of the same attack uses pattern 2.
- **Triple** (below 90%): hits at 3/6/9 ticks for 15/25/35 (15/30/45 and 3/6/10 from 50%); Protect from
  Melee must go on in the last 2 ticks or it is switched off and the hit lands.
- **Grapple** (below 75%): calls a slot; click that worn item within 4 ticks to defend; last-tick parry gives
  a max hit within 5 ticks; else 20-45 and the item goes to the inventory. Needs an item-click-on-worn hook
  during the grapple window.
- **Phases** at 90/75/50/25/10%: 6 light beams leaving molten sand, a new light crystal (4 max) patrolling
  one barricade edge, firing every 25-35 ticks for 60-75. Enraged at 10%: more pools, crystals every 12.
- Totemic, Solarflare and Volatility still apply.
- Boss health bar: the ToA boss bar work may carry over; tsps never did it.

## Rewards and Glory

- **Loot**: one roll per wave per player on the wiki tables (wave 1 always 80 sunfire splinters), rolled before
  the wave so the intermission shows it. Wave 12 adds Dizana's quiver (uncharged) and Smol Heredit at 1/200.
- **The chest** (loc 50741) appears after a forfeit or Sol's death; interface 246 with Take / Bank-all /
  Take-all / Discard-all. Loot left in it is lost.
- **Glory** per cleared wave, per player: 100 x wave, +100 x wave with no damage from enemies, + each active
  modifier's Glory x tier, + max(0, 500 x wave - ticks) rounded down to even.
- **Items**: Dizana's quiver +10 ranged attack / +1 ranged strength when charged, charging and blessing
  per the wiki (read in step 7); fanatic armour and Tonalztics use their cache stats.

**Glory in the shortened run** only counts 5 waves, so the 40,000 and 58,000 Glory tasks are probably out of
reach there. Decided: no multiplier, those need the full run.

## Tasks (from the Raging Echoes list)

| Task | Points | Note |
|------|--------|------|
| Teleport to the Fortis Colosseum | 30 | Needs a teleport to the isle entrance (event teleport list or the ring of dueling) |
| Complete Wave 1 / 6 / 12 | 30 / 80 / 200 | Counts "this wave or later", so the shortened run's wave 7 gives wave 6 |
| Use the Bank Chest inside Fortis Colosseum | 80 | No Glory needed while the gate is off |
| Equip a piece / full Sunfire Fanatic | 80 / 80 | |
| Obtain 20,000 / 40,000 / 58,000 Glory | 200 / 200 / 400 | See the shortened-run note |
| Equip Blessed Dizana's Quiver | 400 | |
| Defeat Sol Heredit 5 / 10 times | 400 / 400 | |
| Colosseum Combat Achievements | 400 | **Drop**: no combat achievements in the event |
| Use the Fortis Salute emote | — | Cache task varbit 17465 exists; only if the emote is unlocked somewhere |

Plus a combat mastery slot if the mastery tasks want one, and the boss-kill hooks for the leaderboard
and the collection log.

## Module layout

New package `...isleofsouls.colosseum`, following the Inferno:

| File | What |
|------|------|
| `ColosseumConfigs.kt` | npc/loc/interface/varp/varbit refs |
| `ColosseumNpcEdits.kt` | ranges, modes, defend/death anims (`NpcEditor`, one `edit()` per type) |
| `ColosseumLobby.kt`, `ColosseumScript.kt` | isle entrance, lobby instance, Minimus/Gloria, bank chest gate, tunnel warning |
| `Colosseum.kt`, `ColosseumRun.kt`, `ColosseumArena.kt` | `WaveDirector`/`WaveRun`, instance layout, entry, leave, death, rejoin |
| `ColosseumWaves.kt` + two TOMLs | composition, spawn points, reinforcements |
| `ColosseumIntermission.kt` | interface 865: offer, first-confirm lock for the team, the 5-tick start |
| `ColosseumModifiers.kt`, `ColosseumModifierEffects.kt`, `ColosseumHazards.kt` | offer rules; blow modifiers; arena hazards and molten sand |
| `ColosseumEnemies.kt`, `ColosseumWarband.kt` | the seven enemy types; warband movement |
| `SolHeredit.kt`, `SolPatterns.kt`, `SolGrapple.kt` | the boss |
| `ColosseumLoot.kt`, `ColosseumRewards.kt`, `ColosseumGlory.kt` | tables, chest, Glory, titles |
| `ColosseumTasks.kt` | task hooks |

Engine additions (each small, each with a test): attack-range modifier hook, temporary level cap,
force-max-hit on the hit roll, npc walk-through-entities flag, spotanim slots, counted hitsplat,
worn-item click during a grapple window, Dizana's quiver bonus in `WornBonuses`.

## Build order

0. **Probe** (throwaway test, like Inferno step 1): lobby and arena map squares and their zones for
   `copyAllLevels`; which locs are on the map (entrance 50749, stairs 50750, tunnel 50751, bank chest 50748,
   Sol's seat 50703, pillars, gates); npc stats against the wiki; cs2 4931's argument list; enum 5312 and the
   structs; the loot dbrows. Check tsps's wave composition and numbers against the wiki.
1. **Lobby and instance**: isle entrance, lobby copy, Minimus intro, tunnel warning, arena instance for the
   team, Start-wave/Leave, death and rejoin, the intermission screen offering modifiers (no effects yet),
   waves and reinforcements with placeholder attacks. Tests for entry, leave, both deaths, rejoin.
2. **Enemies**: the seven types and the warband.
3. **Rewards**: per-player loot rolls, the chest, Glory and titles, the bank chest gate, forfeit.
   (Rewards before modifiers so a no-modifier run is playable end to end early.)
4. **Blow modifiers** with their engine hooks: Blasphemy, Doom, Frailty, Myopia, Relentless, Mantimayhem,
   plus the composition ones.
5. **Hazard modifiers**: Bees!, Solarflare, Totemic, Volatility, Reentry, molten sand.
6. **Sol Heredit**.
7. **Items and tasks**: Dizana's quiver bonus/charging/blessing, pet, collection log items, kill count, tasks.
8. **Docs**: client checklist, numbers to tune, known gaps.

Steps 4 and 5 are the largest. If time runs short, step 5 can ship after step 6.

## Tests

Integration tests through the scratchpad `run-it.sh` + `it-init.gradle` sandbox only (never the real `.data`):
entry/leave/death/rejoin, wave clear and reinforcement timing, the first-confirm modifier lock, each
modifier's effect, loot per player, Glory maths, Sol's phase thresholds (set 10 below because the harness
heals npcs +1/tick), grapple defend/parry/fail.

## Open numbers (guesses until checked)

| What | Value | Source |
|------|-------|--------|
| Sky javelin delay | 3 ticks (wiki says 6) | RSPS capture |
| Upgrade weight in the offer | 2x a new modifier | tsps |
| Solarflare damage | up to 5/10/15 | tsps guess |
| Volatility explosion | up to 15 | tsps guess |
| Totem heal rate | every 5 ticks | tsps guess |
| Minotaur heal rate | every 5 ticks to full | tsps guess |
| Bee spawn point | random, 4+ tiles from players | tsps guess |
| Sol's tiles, barricade, crystal patrols and cooldown | see Sol section | RSPS |
| Grapple minimum damage, triple-attack speed | 20, 12/11 | RSPS |
| Duo targeting, reinforcement gate, beams per player | see Duo rules | mine |

## Answers (user, 2026-10-30)

1. **Entrance**: on the field south of the plaza, among the dungeon entrances, at **(2227, 2820)** (the
   `colosseum_entrance_outside` loc, 1x2, shape 10). A temporary spot; the user will move it later.
   (2200, 2815) was free too but the God Wars tests stand players there.
2. **High Glory tasks in shortened mode**: no multiplier. The 40,000 and 58,000 tasks need the full run.
   May be revisited.
3. **Wave 6 task in shortened mode**: clearing wave 7 counts ("wave 6 or later"). Same rule for wave 1 and
   12, which the shortened run plays anyway.
4. **Bank chest Glory gate**: build it, with an event-settings key `colosseumBankGlory` (default 0 = no gate
   for the event, 2,000 = the real rule).
