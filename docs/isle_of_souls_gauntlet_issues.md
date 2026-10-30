# Gauntlet: issues and open questions

Kept while building steps 4 to 9 overnight (2026-10-20 to 21). Nothing here blocks the build; each
entry says what was decided meanwhile. Work through them in the morning, most important first.
The plan and what was built are in `isle_of_souls_gauntlet_plan.md`.

## A. Needs your hands (cannot be done from here)

1. **`./gradlew packCache` and a restart** (stop your server first). New since the last cache:
   - 11 varps (`isle_gauntlet_*` and two task counters, ids 65305-65315 in `varp.sym`) and 4 struct names (`league_task_*`, `struct.sym`);
   - `NpcEditor` edits for the 18 monsters, the Hunllef's six forms and its tornado types (`GauntletNpcEdits`, `GauntletHunllefEdits`);
   - an `ObjEditor` giving the six Gauntlet halberds attack speed 4 (`GauntletWeaponEdits`);
   - the task enum (7 new league tasks) and their struct text edits.
   I packed and started a sandbox copy of `.data` with all of this: the cache packs, **281 plugin scripts load with no clash** and the server only stops at the port bind (yours is up). Your real cache was not touched.
2. **A client check of the scene resend** (`Player.requestRegionRebuild()`, `RspCycle.rebuildArea`). Everything in the maze depends on it: lighting a room draws it for players already inside. Fallback if it misbehaves: `isle_of_souls_gauntlet_plan.md` section 4D (light every room at the start).
3. **The isle objects**: the Gauntlet portal at (2125, 2887) facing north (6x2), and the crystal singing bowl at (2127, 2891). Check they show, are clear and reachable. The lobby (3032, 6127, 1), Bryn, the chest, the scoreboard and the deposit box are the real map's and were not moved.
4. Walk a whole run once with two accounts: portal, Bryn, enter, light a few rooms (nodes), gather, cook, sing at the bowl, drink an Egniol, barrier, Hunllef, win, chest.

## B. Things I could not verify, ranked by how likely they are to look wrong

1. **Hunllef hit splats.** Its ranged and magic hits are *typeless* hits (I needed that so the engine's protection prayer rule does not cut the damage twice; the prayer is read when the hit lands). They may show with a different hit splat from the real game's red one.
2. **Hunllef animations / projectiles / tornado movement.** Attack anim `hunllef_attack_ranged`, stomp `hunllef_attack_melee`, tornado summon `hunllef_attack_special`, style change `hunllef_attack_transition_magic`, the TSPS (Near-Reality) names for them. The tornadoes are moved a tile a tick with a teleport, ignoring walls (they have no walk animation, so they may slide).
3. **Scoreboard text.** I set text on the 13 label/value components of each panel (interface 639) in the order TSPS used. The cache names agree (`gauntlet_scoreboard:regular_title` ... `global_time`), but no client has seen it.
4. **The recipes book** (interface 640) is opened with `ifOpenMainModal` and nothing else; whether it fills itself (cs2 `gauntlet_recipes_init`) is not known.
5. **The timer overlay**: interface 637 on the HUD overlay, cs2 2914 (time left) and 2916 (final encounter), the same calls TSPS used.
6. **The symbol arches** swap to "Illuminated Symbol" (36095 / 35992) on a lit passage; TSPS left them alone because it could not tell which the game uses.
7. **Bryn's lines** are condensed from the wiki transcript TSPS carries; the choice menu and flag follow it.
8. **Weapon looks**: the bow has no graphics of its own in the cache, so it borrows the crystal bow's (spotanims 249 / 250). The staff uses the cache's `crystal_staff_*` graphics, the halberd its own animations from the cache.
9. **Floor tiles**: the boss room has a ground-decoration floor loc on every one of the 144 arena tiles (checked in the cache) and the fight swaps them with `locRepo.change`, but the client has not seen the swap.

## C. Decisions I took that you may want to change

1. **Monster damage**: the melee monsters (rat, spider, bat, unicorn, scorpion, wolf, bear) use the engine's own melee from their cache stats, **not** the wiki max hits (4 / 4 / 8 / 6 / 11 / 8 / 28; Corrupted 14 / 5 / 11 / 17 / 17 / 19 / 48). I did not compare the two. If the cache numbers are far off, give those monsters scripted `NpcAttack(maxHit = ...)` like the dragon and dark beast have.
2. **Shooters' range (8) and the monsters' leash (30)** are guesses; the wiki gives neither. The monsters do not wander (TSPS lets them wander 3 tiles) and go for the nearest player in their own room.
3. **Aggro** is "same room as the player"; nothing attacks into the start room (it is safe).
4. **Escape crystal** leaves at once (the real game channels it for some time, not known). It works in the fight too.
5. **Teleports out** are blocked only for the isle's Home Teleport spell (every other teleport needs items the kit does not have); anything that moves a player out of the maze costs them their place and kit on the next tick.
6. **Dead players** are out of the run and may not return to that maze, as agreed. They get the reward their points earn (incomplete table at 50 points or more, junk 1-49).
7. **The Hunllef alternates** its target between the players still standing in the arena. The style cycle, the protection switch counter (every sixth hit that is not of its protected style, over both players together) and the thirds of its hitpoints are shared. `GauntletHunllef.HITPOINTS_MULTIPLIER = 1.0` is the one knob; above 1 the health bar reads full for longer than it should.
8. **Reward per player**: each player has their own chest reward and their own points (a death keeps the points). Completion counts for the team's tasks (547 / 548 counters) once per team, for each player's own scoreboard counts, kc and best time.
9. **Seeds and the relics**: the three seeds (crystal armour, crystal weapon, enhanced weapon) are rolled with the drop-rate relic multiplier (the wiki's boosted list names them); the elite clue and the Youngllef are not.
10. **Crystal singing** is on the isle, at a bowl copy beside the portal. Recipes: armour (helm, legs, body), bow, halberd, shield, the three crystal tools, Blade of Saeldor and Bow of Faerdhinen, with the wiki's levels, experience and inputs. The products are the charged items; **charging and degrading are not built**.
11. **Tasks added**: 530, 531, 546, 549, 1512, 1396 (crystal), 534, 535, 547, 548 (Gauntlet). Left out: 1809 (crystal tool: the tool seed is Zalcano's drop and the isle has no source), 558 (eternal teleport crystal: needs an enhanced teleport seed), 560 (crystal crown) and 524 (crystal grail) which I did not research, and 1437 (Combat Achievements, which do not exist here).
12. **Rates**: 531 (a full crystal set) needs six armour seeds. At the wiki's 1 in 120 (Crystalline) or 1 in 50 (Corrupted) per win that is a very long grind even with the relics; consider dropping 531 or raising the seed rates for the event.
13. **Engine changes to know about** (all small and additive): `Player.requestRegionRebuild()` + `RspCycle` resend; `NpcDeath` publishes `NpcDropEvents.Claim` for monsters without a drop table too (so a minigame can replace the bones drop); the Cooking module has a new food (paddlefish, range only, `CookRate(169, 350)`); `HomeTeleportScript` refuses inside a maze; `LobbySpawnScript` asks `Gauntlet.respawnFor` first.

## D. Not built

- **Pets**: a rolled Youngllef is given as the item; there is no follower system to turn it into one.
- **Elite clue scroll**: dropped as the scroll item; the clue system (content plan 3.5) does not exist, so reading it does nothing yet.
- **Bryn's pet check** (no pet system to ask), fades on entering and leaving, the lit-node sound, the dragon impact is shown but the sound is not.
- **Echo Corrupted Hunllef**, **Combat Achievements** (task 1437).
- **A third player or a late joiner** after the barrier is passed (the maze is closed to them).
- **Tool storage's choice menu** with several tools missing and the **recipes book** could not be tested (the test world cannot answer a chat choice or open a client interface).

## E. Code smells I left

- `Gauntlet.pick` is a public mutable function so tests can fix the dice. Everything random in the Gauntlet goes through it (the engine's own random is used inside `NpcAttacks` for hit rolls and for the engine melee).
- `GauntletRun.points` is kept by account id, so a player who relogs on a new account id would lose theirs (they cannot re-enter that maze anyway).
- `Gauntlet` is large (about 700 lines). It could be split into entry, clock and results.
- Test files repeat the same room-template seeding helper; a shared support file would be tidier.

## F. Test state

- Gauntlet: 6 unit + 91 integration tests pass in a sandbox `.data`.
- The wider isle suite (931 integration + 209 unit tests) has 4 failures that load no Gauntlet code: `InfernoRunTest` and `FightCaveWavesTest` (the known shortened-mode setting), `ZulrahLobbyTest` (the boat tile) and `ZulrahRunTest` ("ten times the scales"). The last two I did not check against an unmodified tree.
- Cooking module integration tests pass (the paddlefish entry).

## G. Found in the first client check (2026-10-20)

1. **Scene resend off-centre (fixed).** The forced `RebuildRegion` was centred on the player's current zone, but every coordinate the client gets afterwards is relative to `buildArea`. After walking, rooms and monsters were drawn offset (melee from far away). `RspCycle.rebuildArea` now centres on the build area. Confirmed better in the client.
2. **Lit symbols/nodes at the wrong angle in turned rooms (fixed, not yet seen in the client).** `LocRegistryRegion.findAll` returns a room's static locs with their template angle, not the angle after the zone's rotation (its `findType` does rotate). `GauntletNodes.light` now adds `room.rotation` before the swap and the side check. `GauntletNodesTest` checks the redrawn angles in a turned room. Note for any other code that swaps static region locs from `findAll`.
3. **Sceptre not showing, gear not clearing (fixed, needs a client look).** `giveKit` and `clearKit` wrote `player.worn` directly, so nothing told the weapon scripts or redrew the player. They now publish `HeldEquipEvents.WearposChange` (and `WornUnequipOp.notifyWornUnequip` for each slot taken) and rebuild the appearance, as equipping from the pack does; `GauntletRunTest` checks the events. At login only the model is redrawn.
4. **Defend, attack and death animations (fixed, needs `packCache`).** The nine monsters had no `defend_anim`/`attack_anim`/`death_anim`, so they flinched and died with the human defaults. They now carry their own creature's anims (giant rat, bat, bear, scorpion, wolf, dragon, dark beast, the Gauntlet spider, unicorn), read from the captured animations in TSPS's `npc-captured-animations.json`; none has a hit animation in the real game, so `defend_anim` is their idle. The Hunllef does not flinch (idle) and dies in two parts: `hunllef_death_part_a` played by `die`, then `part_b` through the engine's death. The tornado and the `crystal_hunllef_death` / `crystals` npc forms were not touched.
5. **Hunllef protection icon and the prayer-penetration mastery (fixed 2026-10-20, needs a client look).** Nothing set an overhead icon on the Hunllef, so its protection could not be seen. It now sends a head icon change from the `headicons_prayer` sprite group (440, checked in the cache; index 0 melee, 1 missiles, 2 magic) when it spawns and whenever its protection switches (`GauntletHunllef.showProtection`). The mastery passive 6 (60% prayer penetration) was never read by anything, so a protected hit was always zeroed. `GauntletHunllefScript` now handles the hit in `NpcHitEvents.ModifyAny` (which carries the attacker) and lets `CombatMasteryEffects.prayerPenetration` percent of the damage through; the damage is no longer zeroed before the attacker is known. Two tests in `GauntletHunllefTest`.
