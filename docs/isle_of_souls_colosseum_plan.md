# Isle of Souls: the Fortis Colosseum (plan and as built)

Written 2026-10-30, built the same day. **Status: everything below is built and tested in the test
harness (14 unit and 22 integration tests). Not seen in a real client yet: run `packCache` and work
through the client checklist.**

Sources: the OSRS wiki (read 2026-10-30 through its API: "Fortis Colosseum", "Fortis
Colosseum/Modifiers", "Fortis Colosseum/Strategies", "Sol Heredit", "Glory", "Rewards Chest (Fortis
Colosseum)", the items' pages, and the Sol Heredit, Minimus and Gloria transcripts), tsps'
`docs/fortis-colosseum.md` and its plugin source (its live rsprox capture of the lobby to wave 1, and
the screens' client script arguments), and the revision 233 cache. The wiki agrees with tsps' numbers
(one change: the serpent shaman's max hit is 28 on the wiki, tsps says 27). Anything that is neither
wiki nor capture is marked **(guess)** or **(RSPS)**.

## As built (2026-10-30)

### Where things are

Package `content/areas/misc/isle-of-souls/.../isleofsouls/colosseum/`:

| File | What |
|------|------|
| `ColosseumConfigs.kt` | refs (npcs, locs, objs, seqs, spotanims, varps, varbits, the two screens, the reward invs, a queue), the projectile flights (`ColosseumProjAnimBuilder`) and the npc edits (`ColosseumNpcEdits`) |
| `ColosseumArena.kt` | every tile (isle arch, lobby, arena, spawn points, pillars, gates, chest, Sol's floor), the arena template (map square 28_48) and the instance type |
| `ColosseumSettings.kt` | the `colosseum` block of `event-settings.json` |
| `ColosseumWaves.kt`, `ColosseumModifiers.kt`, `ColosseumLoot.kt` | what each wave sends, the 14 modifiers and their offer rules, the chest's tables |
| `ColosseumRun.kt` | a team's run and each member's chest, Glory and hits |
| `Colosseum.kt` | the director: entry, rejoin, the intermission, waves, reinforcements, deaths, forfeit, the chest, Glory |
| `ColosseumEnemies.kt` | the seven monsters' attacks, the warband's movement, the Minotaur's healing, Red Flag's routefinding |
| `ColosseumEffects.kt`, `ColosseumHazards.kt` | the modifiers that change blows and players; the ones that put things in the arena |
| `SolHeredit.kt` | Sol Heredit and his attack patterns (`SolPatterns`) |
| `ColosseumScreens.kt` | the intermission screen (865) and the rewards chest (246) |
| `ColosseumScript.kt`, `ColosseumLobbyScript.kt`, `ColosseumItemsScript.kt`, `ColosseumCommands.kt` | the handlers, the lobby, the items and the ring, admin commands |
| `ColosseumWorld.kt`, `ColosseumCompletions.kt` | shared helpers; the league tasks |
| `ColosseumWeapons.kt` | the Tonalztics of Ralos's attack (added 2026-10-30, see "Follow-up") |
| `DizanasQuiverPouch.kt` | the quiver's extra ammunition slot (added 2026-10-30) |

Engine and API hooks (small and generic; only the Colosseum uses them so far):
- `Npc.targetDefenceRollPercent` (engine): scales a player's defence roll against that npc; the three
  npc-versus-player accuracy formulas read it (Relentless).
- `ForcedMaxHits` (combat-manager): rules that make a player's attack always hit for its max; the
  melee, ranged, spell and staff rolls ask them (the warband's weaknesses, Sol's perfect parry).
- `AttackRangeModifiers` (combat-manager): changes a player's weapon or autocast range against a npc;
  `PvNCombatScript` applies it (Myopia). Spells cast by hand are untouched.
- `WornClickHooks` (api/player): sees a click on a worn slot before the item's option (Sol's grapple).
- `WornBonuses`: Dizana's Sunfire, +10 ranged accuracy and +1 ranged strength with arrows or bolts and
  a charged or blessed quiver worn (replaces the old TODO).

Also changed: `EventSettings` (the `colosseum` block), `LobbySpawnScript` (a Colosseum death wakes up
in its lobby), `LeagueGearTasks`, `LeagueKillTasks`, `LeagueTaskList`, `LeagueTaskVars`,
`IsleOfSoulsTaskEnums`, `LeaguePets` (Smol heredit), `IsleOfSoulsModule` (the director is a
singleton).

### What it does

- **The way in.** A copy of the city's arch at (2227, 2820) on the field south of the plaza (a
  temporary spot) takes players to the real lobby under the Colosseum; the lobby's stairs bring them
  back. Minimus must be spoken to once (that opens the tunnel); the tunnel shows the warning and its
  "don't ask again" option, then puts the team in its own copy of the arena.
- **The lobby.** Minimus (introduction, questions, and a spare quiver for 4,000 splinters or one more
  1/200 roll at the pet), Gloria (your Glory and the next title), four guards, the bank chest (its
  Glory gate is `bankChestGlory`, 0 for the event), the scoreboard (a chat message with your best
  Glory, highest wave and waves completed), and a copy of a Shrine of Ralos at (1798, 9511) for
  blessing the quiver.
- **A run.** Minimus's Start-wave opens the intermission screen (each player's own loot, the team's
  three modifiers); the first player to confirm picks for the team, the screen closes for the other,
  and the wave comes in 5 ticks later. Waves are the wiki's; reinforcements arrive after 67 ticks
  through the gate on the half most players are on. Clearing a wave adds its loot to each player's
  chest, rolls the next wave's, adds the wave's Glory, and brings Minimus back.
- **Duo rules.** Each monster goes for the nearest player and keeps its target; the warband stand round
  their own target. Damage, Doom, Blasphemy and the "no damage" Glory are per player. A player who
  dies wakes in the lobby with their chest frozen and walks back in at the next intermission; they get
  nothing for waves they miss. If everyone fighting dies, the run is lost (the dead lose their
  chests); the run ends when nobody is in the arena.
- **Leaving.** Leave before wave 1, or with nothing earned, goes straight to the lobby. Between waves
  it is a forfeit: the chest comes out for that player, Start-wave waits until they have left, and
  their partner can carry on. Leaving through Minimus restores combat stats. Teleporting or logging out
  loses the loot and the run's Glory; dying keeps the Glory.
- **Sol Heredit.** He greets each player who has not beaten him (by attempt), jumps down, the
  barricade goes up, and the fight is the wiki's: Spear and Shield patterns, the triple (with the
  early-prayer rule), the grapple (click the called slot; the last tick is a perfect parry), phases
  with sand and crystals, the enrage. Beating him gives each player wave 12's loot with Dizana's
  quiver and a 1/200 pet roll, clears his sand, and brings the chest out.
- **Glory, titles and the emote.** Best Glory is the vanilla varp `colosseum_glory`, which the existing
  Fortis Salute handler already reads (Grand Champion, 20,000).
- **Items.** Splinters charge Dizana's quiver (up to 20,000; each arrow or bolt fired uses one);
  Uncharge gives them back; the shrine blesses it for 150,000 splinters, counting its charges. An echo
  crystal on guardian boots makes echo boots. The ring of dueling's Rub and its worn "Fortis
  Colosseum" option go to the isle's arch (Glory gate `ringGlory`, 0 for the event; the real one is
  12,000) and use a charge; its other places say they are not on the isle.
- **Tasks.** 1655 (ring teleport), 1664/1665/1666 (wave 1/6/12, "or later"), 1662 (bank chest),
  1670/1671 (a fanatic piece, the set), 1667/1668/1669 (20k/40k/58k Glory), 1672 (blessed quiver),
  1683/1684 (Sol 5/10 times, team counters). The collection log gets claimed loot (as it is taken from
  the chest), the pet, and Sol's kill count.
- **Admin commands.** `::colosseum` (to the lobby, intro done), `::colowave <n>`, `::colokill`,
  `::cologlory <n>`, `::colomod <name> <tier>`.

### Follow-up (2026-10-30, second session): the parts left out, and an animation audit

All five things the first build left out are now built:

- **Sol Heredit's health bar.** ToA's boss bar is now a shared class,
  `instances/BossHealthBar.kt` (ToA uses it unchanged). Each run has one; every fighter sees Sol's
  bar from his landing until he is gone, and it fades for anyone who dies, leaves or logs out.
- **His jump cutscene.** A new stage, `SolJump`, between the greeting and the fight. The players'
  cameras are put on his seat and they cannot act or click npcs, locs or objs; he plays his jump
  (it was never seen before: the seated Sol was removed the same tick it started), the screen fades
  to black over the next 2 ticks, he lands behind the black (barricade, players moved, landing
  animation and graphic), the camera turns to him and the screen fades back in, and 3 ticks later
  everyone gets their camera back. The wiki only says a cutscene plays: **the camera spots, heights
  and fade timing are mine**. "Sol Heredit jumps down from his seat..." now comes with the jump.
- **Dizana's quiver's extra ammunition slot.** The client draws it from varps
  `dizanas_quiver_temp_ammo` (obj, `-1` for none) and `dizanas_quiver_temp_ammo_amount` (client
  scripts 5023-5028 decoded: it shows while a quiver is worn or carried). In the worn equipment tab
  (and the equipment stats screen): empty, **Fill** moves the ammo slot's arrows or bolts in;
  filled, **Quiver-Remove** takes them to the inventory and **Swap** trades them with the ammo slot.
  The quiver's **Open** shows its own screen (592: Remove, the charges) and **Empty** takes the
  ammunition out. Only arrows and bolts go in. Firing: the real game fires from whichever slot holds
  what the weapon needs. Here, a new combat hook (`AmmoPouches` in combat-manager, asked by the
  player-versus-npc and player-versus-player ranged attacks before they read the ammo slot) swaps
  the pouch into the ammo slot when the slot's ammunition does not suit the bow or crossbow and the
  pouch's does. So the ammo slot always shows what is being fired, and bonuses, bolt effects and
  ammo use work unchanged. **A visible difference from the real game**: switching between a bow
  and a crossbow moves the stacks between the two slots.
- **The Tonalztics of Ralos.** It has its own attack now (`ColosseumWeapons`): as a thrown weapon
  the engine used to throw it away like a dart. It comes back, hits for up to 75% of the max hit,
  and charged throws two blades (each rolled on its own) for one charge a throw. The last charge
  turns it back into the uncharged weapon. Splinters on it (or its Charge option) charge it up to
  20,000; Check and Uncharge too. The special, Division, now follows the wiki: 50% more accuracy,
  a Defence drain of an eighth of the target's Magic per blade that lands (it was a tenth), one
  blade uncharged and two charged, its own animation and graphics for each, and a charge a throw.
  Range 6 uncharged and 7 charged (the obj data said 5).
- **Echo boots.** An echo crystal on guardian boots gives them 6,000 charges; another crystal on the
  boots adds 6,000 (60,000 at most). While active, whatever damages the wearer from within the 3x3
  round them takes 1 back, for a charge (`PlayerHitEvents.Impact`). Check, Toggle-activity (our own
  varp `isle_echo_boots_recoil_off`, 65168) and Revert (back to guardian boots; the crystal is
  lost). Typeless hits from players are not recoiled, so two players in echo boots do not ping-pong.

**Animation audit.** Every Colosseum npc's seqs were checked against its model's skeleton (the
`seqs.py`/`derive_anims` tools) and against tsps' captured ids. All the attack, death, block and
special seqs already used match their skeletons and tsps. Fixed:

| What was wrong | Now |
|---|---|
| Sol's jump never showed (seated Sol removed the same tick) | the cutscene above plays it in full |
| The serpent shaman flinched with the human unarmed block (tsps: no block) | it holds its staff stance (no flinch) |
| Sol's death graphic (spotanim 2680) was never played | played with his death animation |
| His light crystals vanished on his death | they play their break-up (seq 10800), then go |
| Bees! swarms never animated | appear (10821), sting (10823, every other tick while stinging), break up when killed (10824) |
| The Manticore and Minotaur appeared with no animation | their own appear seqs (10870/10871 for the first/second Manticore, 10845 for the Minotaur): **guesses**, they are only used by these npcs and tsps never played them |
| The charged Tonalztics threw with the uncharged animation | charged throw 10923 (obj data) |
| Division always used the charged animation | 10914 charged, 10916 uncharged |

Not changed, and why: Sol and the colossi/Manticore keep no block animation (none in the cache, they
hold their stance); the warband, jaguar and Minotaur blocks were already their own; the javelin's
`walkfade` and the gib npcs' seqs are not used (the explosions are spotanims, as tsps does).

### For you to do

1. Stop the server and run `./gradlew packCache`, then restart. It packs the npc edits (modes,
   ranges, block animations, walking through npcs), the seven projectile flights (new `projanim.sym`
   ids 38-44), the task enum keys 555-567, and the new varps (65170, 65169) and queue (38). The
   follow-up adds varp 65168 (echo boots' toggle), the shaman's block, and the Tonalztics's
   animation and ranges (obj data), so it needs `packCache` too.
2. Work through the checklist below with two accounts on one team.

## Client checklist (two accounts, same team)

1. The arch at (2227, 2820) shows and "Enter" goes to the lobby; the stairs bring you back in front of
   it. (The arch is a 1x2 piece of the Colosseum's wall and may look odd on its own; we said we would
   look at that later.)
2. Minimus, Gloria, the guards and the shrine stand in the lobby; Minimus's first talk opens the tunnel.
3. The tunnel's warning and its three options; both players end up in the same arena, Minimus says "A
   Rookie approaches!" and the seated Sol is above the north gate.
4. **Start-wave opens the intermission screen with your loot (80 sunfire splinters) and the three
   modifiers.** The client's own scripts draw this screen from what tsps captured; check that the
   modifier buttons highlight, the tier text shows, and Continue works. The value line uses the items'
   store values, not Grand Exchange prices.
5. The first confirm closes the other player's screen; the wave comes in 5 ticks later and "Wave: 1"
   is red in the chat.
6. The trio run at you and stand north, east and west; the shaman shoots from range. Magic on the
   berserker, ranged on the seer and melee on the archer always max hit.
7. Wave 2's Javelin Colossus throws a sky javelin every fifth attack (a shadow, landing on your tile).
8. Wave 4's Manticore: the three orbs show on it while it charges and come a tick apart; praying each
   as it is thrown blocks it.
9. Reinforcements come through a gate after 40 seconds; the Minotaur (wave 7+) can be safespotted
   behind a pillar, Red Flag's cannot.
10. A cleared wave: "Wave N completed! Duration: ...", Minimus reappears next to you, and the screen
    shows the loot so far and the next wave's.
11. Modifiers: `::colomod` each one between waves and check it (bees, the Solarflare orb's path round a
    pillar, totems, Volatility's explosions, Reentry's molten sand, the Doom hitsplat with its count,
    Frailty's cap, Myopia's shorter range).
12. One player dies: they wake in the lobby, the tunnel refuses them during the wave and lets them in
    at the next intermission, with their chest.
13. Leave between waves: the forfeit line, the chest comes out (3x3, rising), Search opens it,
    Take-all, Bank-all, Discard-all and clicking an item work, and Leave again goes to the lobby.
14. Wave 12 (`::colowave 12`): his greeting, the jump, the barricade of shield-bearers, the Spear and
    Shield smoke, the triple's telegraph, the grapple's call and clicking the slot, a phase's beams of
    light and sand, the crystals patrolling and firing.
15. Beating him: the chest with the quiver, his last words overhead, the task popups.
16. The emote tab's Fortis Salute works at 20,000 Glory (`::cologlory 20000`).
17. The ring of dueling: Rub offers "Fortis Colosseum.", the worn option works, the charge message.
18. Splinters on Dizana's quiver charge it; the ranged bonus applies with arrows.
19. **Sol's jump cutscene**: the camera looks at his seat, he jumps, the screen goes black and comes
    back on his landing, then your own camera returns. Tune the camera constants in `SolHeredit`
    (`JUMP_CAMERA_*`, `LAND_CAMERA_*`, `FADE_*`) if it looks wrong.
20. **His health bar** shows at the top from his landing, goes down as he is hit, and fades when he
    dies (and for a player who dies or leaves).
21. **The quiver's extra slot**: with the quiver worn or in the inventory, the slot shows above the
    ammo slot in the worn equipment tab. Fill, Swap, Quiver-Remove, Examine; the quiver's Open and
    Empty. With bolts in the ammo slot and arrows in the pouch, a bow fires the arrows (they swap
    in). Also check the equipment stats screen's slot.
22. **The Tonalztics**: charged throws show two blades and use a charge each throw; Check, Charge,
    Uncharge; the weapon is never dropped. Division with and without charges.
23. **Echo boots**: Check, Toggle-activity, Revert; a monster hitting you in melee takes 1s.
24. **Animations**: the shaman does not flinch; bees appear, sting and break up; the crystals break up
    when Sol dies, with his death graphic; the Manticore's and Minotaur's appear poses (guesses).

## Numbers to tune after the client check

| What | Value | Source |
|------|-------|--------|
| Sky javelin delay | 3 ticks (the wiki says 6) | RSPS capture |
| Upgrade weight in the offer | 2x a new modifier | tsps |
| Solarflare damage | up to 5/10/15 | tsps guess |
| Volatility explosion | up to 15 | tsps guess |
| Totem heal rate | every 5 ticks | tsps guess |
| Minotaur heal rate | every 5 ticks, to full | tsps guess |
| Bee spawn | a random spawn point 4+ tiles from every player | tsps guess |
| Sol's landing, the players' tiles, the barricade, crystal patrols, the 25-35 tick laser cooldown | | RSPS |
| The grapple's 20 minimum, the triple's 12/11-tick speed, the transitions' 7 | | RSPS |
| Sol's greeting time: 3 ticks plus 3 a line, then he jumps | | guess |
| Sol's cutscene camera and fade | | mine |
| Which Tonalztics graphics go with which throw | | guess from their names |
| Duo: targeting, the reinforcement gate, beams round each player | | mine |
| The lobby guards', Gloria's and the shrine's tiles | | guesses |

## Known gaps

- **Frailty** does not lower the Hitpoints level the client shows: the engine has no temporary level
  cap, and lowering the saved base level would be undone by the next Hitpoints xp. Healing above the
  cap is taken off every tick instead.
- **Relentless** lowers the player's defence roll by 33/66% rather than their Defence level: close,
  not exact.
- **Dizana's quiver's extra slot** swaps into the ammo slot to be fired rather than firing from
  where it is (see "Follow-up"). The bank's and the deposit box's copies of the slot
  (`bankmain:extra_quiver_slot`, `bank_depositbox:extra_quiver_slot`) have no handlers, and Ava's
  upgrade to the quiver is not built.
- **Echo boots** keep their charges on the account (the cache's varbit), not on the item.
- **Two Manticores** do not copy each other's orb order; the enraged crystals do not aim at the player.
- **The scoreboard** (interface 867) is a chat message, not the real screen.
- The sky javelin, Solarflare, sand and explosions never cost the "no damage" Glory (wiki); bees are
  treated the same.
- A player who logs out in the arena wakes at the first registered instance type's exit (the shared
  `TeamInstanceLogin` rule), not the Colosseum lobby.
- "Colosseum Combat Achievements" (1675) is not in the task list.

## Decisions (user, 2026-10-30)

| Question | Decision |
|----------|----------|
| Who plays | **Team of 2** in one instance, as the Inferno and Gauntlet. No scaling. |
| Length | **Full 12 waves + a shortened mode**, picked by an event-settings key (`colosseum.mode`; the list is `colosseum.shortenedWaves`). |
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
4. **Bank chest Glory gate**: build it, with an event-settings key `colosseum.bankChestGlory` (default 0 = no gate
   for the event, 2,000 = the real rule).
