# Isle of Souls: God Wars Dungeon, aggression, god items and the four bosses (scope)

Agreed with the user 2026-10-12. The dungeon itself (entrance, ropes, gates, kill counts, kill count box)
is built; see `isle_of_souls_dungeons_plan.md` round 5. This file is what is left. Nothing below is built yet
unless it says so.

## Done 2026-10-12 (before scoping)

- **Second Saradomin rope.** The cache's `godwars_rock_rope_multi2_bottom` reuses `godwars_rock_rope_bottom1`
  as its tied state (it never uses `..._bottom2`), so the second rope's "Climb-up" ran the first rope's
  handler and landed on the top level. `GodWarsScript` now has one handler on that type and picks the rope
  by the clicked loc's coordinates (2914,5300,1 -> 2912,5300,2; 2920,5274,0 -> 2920,5276,1).
- **70 Agility** (base level, Wiki says not boostable) to tie either Saradomin rock or climb down its rope.
  Climbing up and the entrance rope are free. 15 tests in `DungeonGodWarsTest` pass.

## Facts found while scoping

- **There is no aggression for ordinary monsters on the isle.** The engine's hunt system exists
  (`NpcPlayerHuntProcessor`, hunt modes in `HuntBuilds`) but no npc config sets a `huntMode`/`huntRange`
  except ducks, Inferno and Zulrah. Walking into the dungeon today, nothing attacks first.
- The hunt modes support a varp condition per mode (`checkVar1..3`, operators incl. `Equals`), so "ignore
  players wearing a god's item" fits as one hunt mode per god that checks an "aligned" varp.
- Once an npc has a target it does not hunt again, so the Wiki's "aggression persists until the fight ends;
  unequipping and re-equipping does not make them tolerant again" comes for free.
- 362 God Wars spawns exist already (all four generals, 12 bodyguards, every follower) with drop tables.
  Followers and bosses attack in melee only with cache stats (`NvPCombat`); `NpcAttack`/`NpcAttacks`
  (`docs/isle_of_souls_npc_combat.md`) is the framework for ranged, magic and area attacks.
- Wiki (God Wars Dungeon): monsters are aggressive unless the player wears at least one item of that
  monster's god; one item per god is enough; other gods' items do not cancel. Zamorak followers are in every
  corner, so a Zamorak item is needed almost everywhere. Per area: Armadyl's Eyrie needs Armadyl + Zamorak,
  Bandos' Stronghold Bandos + Zamorak, Saradomin's Encampment Saradomin + Zamorak, Zamorak's Fortress
  Zamorak only. That table needs no code of its own: it falls out of which followers stand where.

## Step 1 as built (2026-10-12)

- `dungeons/GodItems.kt`: `GodItems` (injectable; `alignedWith(player)`, `isDevoted(god, id)`, `sync(player)`), the
  four temporary varps `isle_godwars_aligned_<god>` (ids 65333-65330 in `varp.sym`, `God.aligned`), and
  `GodItemNames` with the lists by cache symbol name: Armadyl 34, Bandos 30, Saradomin 64, Zamorak 81 items. Ids
  came from each Wiki item page's infobox, checked against `obj.sym` (the cache has no names for many of
  these items, so name matching does not work). Dropped because the cache lacks them: Saradomin/Bandos/Armadyl
  cloaks, croziers, stoles, mjolnirs, Sunspear and others. Added: the four ornament godswords. Zaros skipped.
- `GodItemsScript` syncs at login and on every `WearposChange`; `GodWarsScript`'s per-tick timer also syncs
  while the player is inside the dungeon (covers death and other paths that skip the event).
- 7 tests in `DungeonGodItemsTest`; the 15 God Wars tests still pass. Needs `./gradlew packCache` (new varps).
- For step 2: kill counts and aggression differ for two followers. `God.ofFollower` (kill counts) puts icefiends
  under Saradomin and goraks under Bandos, but for aggression the Wiki has both as **Zamorak** followers
  (Zamorak's list: imps, icefiends, pyrefiends, feral vampyres, bloodveld, werewolves, hellhounds, goraks).
  Step 2 needs its own `God.ofAlignment(name)`.

## Step 2 as built (2026-10-12)

- `dungeons/GodWarsAggression.kt`: `GodWarsNames` (every `godwars_` npc by cache name: followers per god, 16
  leaders, 2 quest bystanders; `godOf(name)`), `GodWarsRoster`, five hunt modes `godwars_<god>_hunt` and
  `godwars_leader_hunt` (ids 23-27 added to `hunt.sym`), and `GodWarsNpcEdits` giving each follower its god's
  mode with hunt range 5 and each leader the leader mode with range 8 (both ranges are guesses).
- A follower mode is: player, line of sight, no strength check, **melee** (`OpPlayer2`), and `God.aligned == 0`.
  None checks recent combat, so everything in the dungeon joins in (it is multi-combat). Ranged and magic
  variants (`ApPlayer2`) come with step 3. Spiritual rangers/mages and aviansies walk up and melee until then.
- Icefiends and goraks are Zamorak followers here (they stay Saradomin/Bandos for kill counts in `God.ofFollower`).
  K'ril's bodyguards are the three `ancient_*_demon` types, which only stand in his chamber; they still count
  for the Zamorak kill count (existing behaviour, left alone).
- **Engine fix:** `NpcMovementProcessor` built a chasing npc's route destination with level 0 (the naive
  destination has no level), so on any other level the npc reached the spot, never matched the waypoint and threw
  "Invalid step tile offset: 0, 0" every tick. The dungeon's main caves are on level 2, so it now uses the
  chaser's level.
- **Packing quirk:** a hunt mode that is new cannot be set on an npc during the first `packCache` pass (it has no
  id yet), so `GodWarsNpcEdits` skips itself then; the packer sees the new modes, adds them and runs again.
- 9 tests in `DungeonGodAggressionTest` (a test npc has no viewer and so never hunts: the test gives it an
  always-active `NpcInfoProtocol`). All 8 dungeon test classes pass (77 tests). Needs `./gradlew packCache`.

## Step 3 as built (2026-10-12)

- `dungeons/GodWarsFollowerScript.kt`: `FollowerProfile` (rate, main attack, optional close attack) for the 26
  followers that are not melee only, from the Wiki's monster pages: the 15 Armadyl aviansies (ranged, 4 ticks,
  max hit 8-16 by cache combat level), the Armadyl spiritual warrior (ranged, 3 ticks, 15), spiritual rangers
  (15/16/13/16 for Zamorak/Saradomin/Bandos/Armadyl, rates 4/4/4/3) and mages (19/20/17/17, rates 4/4/5/3), the
  Saradomin wizard (magic 20, or a 14 stab next to the player half the time, 4 ticks) and the bloodveld (magic
  hits, max 6, 4 ticks, from melee range). Every other follower keeps the standard melee fight.
- Ranged and magic followers use four new hunt modes `godwars_<god>_ranged_hunt` (ids 28-31, `ApPlayer2`) and an
  `attackRange` of 7 (`GodWarsNames.ATTACK_RANGE`, a guess), so they walk to range and stop. Handlers are
  `onAiOpPlayer2` and `onAiApPlayer2`.
- The bloodveld's cache has magic level 1, so a magic hit never landed. `GodWarsNpcEdits` gives it magic 80
  (`GodWarsNames.MAGIC_MELEE_LEVEL`, its attack level; the Wiki calls the attack "magical melee").
- Graphics: the cache's where a name says whose they are (Bandos ranger and mage, Armadyl mage and warrior's
  spear); everything else is a plain bow or cast animation with the standard arrow or magic projectile. A guess
  to check in a client (the Zamorak and Saradomin spiritual rangers and mages and the aviansies are generic).
- 14 tests in `DungeonGodFollowerTest`: the table, and protection prayers by style (the right prayer stops the
  ranger, mage, aviansie and bloodveld; the wrong one does not). Test npcs outlive a test in the shared world,
  so the tests remove theirs. All 9 dungeon test classes pass (91 tests). Needs `./gradlew packCache`.

## Step 4a: Kree'arra and her bodyguards, as built (2026-10-12)

- `dungeons/GodWarsArmadylScript.kt` (Wiki: Kree'arra, Strategies, the bodyguards' pages). **Kree'arra**, every 3
  ticks: when someone in the chamber is attacking her, a ranged tornado (up to 69, Protect from Missiles) or a
  magic tornado (up to 21, Protect from Magic, accuracy = her Magic roll against the player's **Ranged**
  defence), each hitting every player in the chamber with their own roll and pushing them one tile away from her
  (not out of a corner; along a wall they slide). When nobody is attacking her she flies to her target (switches
  to melee chase) and swipes with magical melee (up to 26, Protect from Melee, her melee roll against the player's
  Magic defence) and stays in melee range afterwards. **Bodyguards**, every 5 ticks: Flight Kilisa melee slash 15,
  Wingman Skree magic 16, Flockleader Geerin ranged 25. When the chamber is empty they heal to full (an AI timer
  every 10 ticks; hitpoints only, stats are not reset).
- **No melee** on Kree'arra, her bodyguards, the aviansies and the Armadyl spiritual warrior, ranger and mage
  ("The Aviansie is flying too high for you to attack using melee."): `NpcMeleeBlocks.block` now takes a message
  and a `reachable` flag, and halberds and salamanders (by weapon name) get round it. Spells, ranged weapons
  and the kraken's block are unchanged. Not done: the scythe and Dinh's bulwark hitting through, and making an
  aviansie turn on a player who tries.
- New hunt mode `godwars_leader_ranged_hunt` (id 32). Kree'arra hunts with range 16 and attack range 16 (the
  chamber is x 2824-2842, z 5296-5308 on level 2), Skree and Geerin attack from 8. Her cache anims are used
  (wind attack, claw, defend, death) with the standard arrow and magic flight; the bodyguards use default anims.
- Guesses: ranged is picked 7 times in 10 over magic, the push happens on every attack (hit, block or miss), the
  tornado graphics. The Wiki's two max-hit figures for her melee (25 in the box, 26 in the text): 26 used.
- **Correction to step 3:** "magical melee" is protected by **Protect from Melee**, not Magic (the Wiki's
  Magical melee page), with accuracy rolled as the monster's melee roll against the player's Magic defence. The
  bloodveld now does that (`FollowerProfile.magicalMelee`, `AccuracyFormulae.rollMagicalMeleeAccuracy`); the
  magic level 80 edit from step 3 is gone. The isle module now depends on `combat-formulas`.
- 17 tests in `DungeonGodArmadylTest` (blocks, hunt modes, knockback, melee vs ranged/magic by prayer, all
  players hit, bodyguard styles, healing). All 10 dungeon test classes (106 tests) and the 29 combat-scripts
  integration tests pass. Needs `./gradlew packCache`.
- Test lessons: the test player and the world are shared by every test in the JVM, so tests must clear prayers
  and remove the npcs they spawn (a leftover npc blocks the next test's tile).

## Step 4b: General Graardor and his sergeants, as built (2026-10-12)

- `dungeons/GodWarsBandosScript.kt` (Wiki: General Graardor, Strategies, the sergeants). **Graardor**, every 6
  ticks: 2 in 3 a melee punch (crush, up to 60, Protect from Melee), 1 in 3 a ground slam that hits every
  player in the chamber with their own ranged roll for 15 to 35 on a hit, equal odds per number (Protect from
  Missiles); he only slams from next to his target (he is always in melee range when his handler runs).
  **Sergeants**, every 5 ticks: Strongstack melee crush 15, Steelwill magic 15, Grimspike ranged 21. On the tick
  they are due to attack they switch to the player who last hurt Graardor (the killer once he is dead), by
  `opPlayer2` (Strongstack) or `apPlayer2` (the others), and attack next tick. One shared "last hitter" because
  there is one chamber and no instancing; `recordHit(player)` is the hook the `onNpcHit` handler calls. When the
  chamber empties all four heal to full (hitpoints only) and the favourite is forgotten.
- `GodChambers` (shared chamber rectangles, replaces `ArmadylChamber`): Armadyl x 2824-2842 z 5296-5308, Bandos
  x 2864-2876 z 5351-5369, both level 2. Hunt modes: Graardor and Strongstack melee, Steelwill and Grimspike the
  ranged leader mode (attack range 8); AI timer for the heal; Graardor's death anim set (attack and defend were
  already derived).
- Graphics from the cache's names: the sergeants' sonic (Steelwill) and handaxe (Grimspike) attacks, Graardor's
  slam anim (`godwars_bandos_ranged`) and the `godwars_bandos_spot` graphic on him (a guess; no projectile).
  Sergeant 1/2/3 = Strongstack/Steelwill/Grimspike (npc ids 2216-2218 in the Wiki).
- 10 tests in `DungeonGodBandosTest` (prayers per attack, slam damage always 15-35, punch past 35, slam hits a
  far player, sergeants' styles and favourite switching, heal). All 11 dungeon test classes (116 tests) pass.
  Needs `./gradlew packCache`.

## Step 4c: Commander Zilyana and her bodyguards, as built (2026-10-12)

- `dungeons/GodWarsSaradominScript.kt` (Wiki: Commander Zilyana, Strategies, the bodyguards). **Zilyana** attacks
  every **2** ticks, only from next to her target: a melee hit up to 27 (crush, Protect from Melee) or a magic
  hit of 10 to 20 when it lands (Protect from Magic). **Bodyguards**, every 5 ticks: Starlight the unicorn (npc
  `godwars_saradomin_unicorn`) melee crush 15, Growler the lion magic 16, Bree the centaur ranged 16. When the
  chamber empties they heal to full (hitpoints only).
- Chamber: x 2889-2907, z 5258-5275 on **level 0** (`GodChambers.saradomin`). Zilyana and Starlight hunt in melee,
  Growler and Bree from range 8. Zilyana's attack, defend and death anims were already derived; Bree's defend
  and death are set in `GodWarsNpcEdits`; her ranged attack and the lion's spell use their own cache anims,
  casts and flights; Zilyana's casting anim and strike graphic are the cache's `godwars_saradomin_*` ones.
- Guesses: she casts 1 attack in 3 (the Wiki gives no odds), the graphics. **Not built:** Starlight's random
  extra steps while she stands under Zilyana.
- 8 tests in `DungeonGodSaradominTest` (prayers per attack, magic always 10-20, melee reaching 27, her speed
  against a bodyguard's, bodyguard styles, heal). All 12 dungeon test classes (124 tests) pass. Needs
  `./gradlew packCache` (the bodyguards' hunt modes already exist; new edits only).
- Test lesson: the test player must stand within the monster's hunt range (8 tiles) or it never attacks, which
  makes "this prayer stops it" tests pass for the wrong reason; every such test is paired with a "the wrong
  prayer hurts" check.

## Step 4d: K'ril Tsutsaroth and his bodyguards, as built (2026-10-12)

- `dungeons/GodWarsZamorakScript.kt` (Wiki: K'ril Tsutsaroth, Strategies, the bodyguards). **K'ril**, every 6
  ticks, from next to one target: 2 in 3 a **melee** swipe up to 46 (slash, Protect from Melee) that, when it
  lands, can poison even through the prayer; 1 in 3 a **magic** hit of 10 to 30 when it lands (Protect from
  Magic); and against a target praying Protect from Melee, 1 swipe in 9 is the **prayer smash**: he shouts
  "YARRRRRRR!", it always hits for 35 to 49 as a typeless hit (no prayer touches it) and drains half the
  target's current Prayer points rounded down, a quarter with a spectral spirit shield (cache name
  `spectral`). **Bodyguards**, every 5 ticks: Tstanon Karlak (greater demon) melee crush 15, Zakl'n Gritch
  (lesser demon) ranged snowballs 21, Balfrug Kreeyath (black demon) magic fireballs 16. When K'ril dies each
  goes for a random player in the chamber (op/apPlayer2). Everything heals when the chamber empties.
- **Poison is new** (`PlayerPoison.kt`, nothing on the isle poisoned before): hurts every 30 ticks, starts at 16
  and drops by 1 after every 5 hits until it ends; antipoison protection stops and cures it; not saved. New
  varps `isle_godwars_poison` and `_hits` (65329, 65328), timer `godwars_poison` (24), and the client's
  `poison` orb varp is shared with Zulrah's venom (venom wins while both are set).
- **Leash fix for all four chambers:** every general and bodyguard now has `maxRange` 24. The cache default
  made them drop a target on the far side of the chamber (it is up to 19 tiles across), which would have
  broken the sergeants' and demons' retargeting.
- Chamber: `GodChambers.zamorak` x 2918-2936, z 5318-5331, level 2. K'ril's own anims were already derived; the
  casting anim, the strike graphic, the snowball and fireball graphics are the cache's Zamorak ones (guesses
  for which graphic is which). Guess: a landed swipe poisons 1 time in 4.
- 13 tests in `DungeonGodZamorakTest`. All 13 dungeon test classes (137 tests) pass. Needs `./gradlew packCache`.

## Steps 5-7 as built (2026-10-12)

- **Altars** (`dungeons/GodWarsAltarScript.kt`, Wiki: God Wars Dungeon). Pray: restores Prayer and lifts it one
  point above the maximum for each worn item of the altar's god (`GodItems.wornCount`), once every 10 minutes
  (1000 ticks, shared by the four altars, kept in memory until a restart), not in combat; it says how long is
  left otherwise. Teleport: to just outside that chamber's door, even in combat. Not built: dying inside a
  chamber leaving the grave outside it.
- **Ecumenical key** (`dungeons/GodWarsKeyScript.kt`). Any follower killed in the dungeon drops one 1 in 60; a
  player carrying 3 gets "You already have enough of these." (bank not counted; no diary or combat achievement
  changes, no key shards). A chamber door uses the kill count first and takes one key if there are too few
  kills, with "The ecumenical key crumbles to dust as the door opens." Completes league task 1011.
- **League tasks** (`tasks/LeagueGodWarsTasks.kt`, 17 added to `LeagueTaskList`): Obtain an Ecumenical Key
  (Medium); Defeat each of the four generals, any boss 100 and 250 times, Equip a Saradomin Sword (Hard); any
  boss 500 times, Zamorakian spear, piece and full set of Armadyl and Bandos armour (ornament versions count),
  Armadyl crossbow, Staff of the Dead, any Godsword (Elite). The "any boss N times" tasks use twelve saved
  counters (`isle_count_gwd_<boss>_<N>`, varps 65327-65316), so the kills of different bosses are not added
  together. Left out: the Nex tasks, Equip Every Godsword (needs the Ancient godsword), the Wilderness
  dungeon, combat achievements, Saradomin's light.
- Tests: `DungeonGodExtrasTest` (15: altars, key rolls, doors with keys) and `LeagueGodWarsTasksTest` (9). The
  full isle integration suite is 648 tests; only the old `InfernoRunTest` shortened-mode failure remains.
  Needs `./gradlew packCache` (new varps).
- Test lesson: a player counts as in combat while the map clock is within 8 ticks of their last-combat tick
  (0 at the start), so a test that clicks something combat-gated has to advance 8+ ticks first; and a prayer
  altar (like any loc) must be placed with its real rotation or the click is refused from some sides.

## Private rooms, as built (2026-10-12)

The user asked for private rooms with shared instances for teams; decisions: keep the public chambers (the
door's "Open (normal)"), every player pays the 40 kills (or a key) on entry, the generals respawn while the
room is occupied.

- **Doors.** The real chamber doors are multi-state objects whose varbit `godwars_entrance` (`door_state`)
  chooses the version: 1 shows the private door with "Open (normal)", "Open (private)" and "Peek". The
  dungeon now sets it to **1** on entry (it was 0, plain doors with Open and Peek). Op1 is the existing public
  entry; op2 `Open (private)` pays the kill count (or a key, kills first) and enters the team's room; op3 `Peek`
  says how many players are in the public room. Inside a private room, the door (a copy at other coordinates)
  and the altar's Teleport lead out.
- **`GodRooms`** (`dungeons/GodRooms.kt`, a singleton service): four `GodRoomDef`s, each a `TeamInstanceType`
  copying the zone block round one chamber (Armadyl 352,661 4x3 level 2; Bandos 357,668 3x4; Saradomin 360,657
  4x3 level 0; Zamorak 364,664 4x3), with entry just inside the door and exit just outside it. It spawns the
  general and three bodyguards in a new room (permanent adds, so they respawn on death after the cache
  respawn time), and `sweep` removes them when nobody is left. Teammates share one room through
  `TeamInstances` (one per team; a lone player gets their own); another team gets another copy.
- **Room-aware scripts.** A chamber exists at its real coordinates and at each instance's, so all four boss
  scripts, the kill count box and the altar ask `GodRooms`: `roomOf(chamber, coords)`, `toNormal`, `toReal`,
  `sameRoom`, `playersIn`, `checkIn`. A boss's area attacks, Kree'arra's knockback, Graardor's sergeants'
  favourite (kept per room), K'ril's bodyguard refocus and every "heal when empty" are per room. A private
  room's monster that finds its room empty (or already reclaimed by the engine) removes itself.
- **Not done:** Peek does not count private rooms; relogging inside a room still sends the player to the first
  registered instance type's exit (an existing quirk of `TeamInstanceLoginApplier`); drops on the floor go with
  the room.
- 12 tests in `DungeonGodRoomsTest` (entry and cost, sharing, separate teams, keys, leaving by door and altar,
  isolation of targets and favourites, public vs private, knockback inside a room, monsters removed). The full
  isle suite is 660 tests with only the old `InfernoRunTest` failure. Needs a server restart only (no cache
  change).
- Test lessons: `placeMapLoc` does not work for locs inside an instance region (the click is dropped); add a
  dynamic loc with `LocRepository.add` and wrap it in a `BoundLocInfo`, as the Zulrah lobby test does.

## tsps comparison changes, as built (2026-10-16)

Compared with the tsps God Wars plugin (github.com/RSPSApp/tsps, rsprox captures) and took:

- **Captured presentation** (`GodWarsPresentation.kt`): every general and bodyguard attack now uses the captured
  animation, cast graphic (with a height via the new `NpcAttack.castHeight`), projectile and sound. Sounds are named in
  `synth.sym` (`godwars_*`, 26 ids); each projectile has its own flight (`GodWarsProjAnims`, ids 17-26 in `projanim.sym`;
  `lengthAdjustment = base - launch cycle`, `stepMultiplier = cycles per tile`). Graardor's slam is now a projectile that
  lands with it; K'ril's flames and the demons' shots are projectiles too; impact graphics the captures do not have
  (Steelwill, Growler) are gone, and Zilyana's cast graphic and K'ril's old target strike are gone. Death sounds for
  Graardor, Kree'arra, Zilyana, K'ril and the sergeants are set as `death_sound` params. Which of two bodyguards that
  attack together owns a sound is a guess (as in tsps). Needs `packCache`.
- **Crossings with sequences** (`GodWarsCrossings.kt`): Bandos' gong (anim 7214, sound at +3), Armadyl's pillar (fire
  line, grapple graphic, pillar swing, two exact moves) and Zamorak's ice bridge (a swim under it to the captured bank
  tiles 2885,5333 / 5345, with "Dripping, you climb out of the water."). The Saradomin rope climb plays the ladder anim
  with "You climb down the rope.". **Item checks are in the code but off**: `GodWarsCrossingItems.required` (event
  settings key `godWarsCrossingItems`, or `::gwditems on|off` until restart). On: hammer for the gong, a crossbow and a
  mithril grapple bolt worn for the pillar, rocks start untied and tying takes (and spends) a rope.
- **Chamber doors**: the door loc swings open for 2 ticks (open pair 26506/26507, per-god angles from tsps) and the
  captured wording is used: locked message "This door is locked by the power of X! ...", key "Your ecumenical key melts
  into the door.". The "door devours the life-force" line is not used because nothing is spent any more.
- **Kill counts**: the cap is 4000 (the varbits hold 12 bits) and the 40 needed for a chamber is a one-time threshold:
  entering (public or private) no longer spends them. A key is still used up each time one is used instead of the count.
- **Empty room**: the general and bodyguards also get Attack, Strength, Defence, Ranged and Magic back to base
  (`Npc.resetForEmptyRoom`), and all 16 have a 150-tick respawn (`GodWarsNpcEdits`). The league's global respawn speedup
  still divides that (10x at the time of writing, so 15 ticks).
- **Prayer drain** only when swimming north into Zamorak's side.
- Not taken: the Ironman shared-room rule, the 150,000 coin private room (ours is the team room), kill counts clearing
  when leaving, Peek counting private rooms.

## Decisions

| Question | Decision |
|---|---|
| Do god items calm the generals and bodyguards? | **No.** Only followers respect god items; the 4 generals and 12 bodyguards are always aggressive. (Wiki is silent; this is how the game plays as remembered.) |
| Boss depth | **Full fidelity**: real attack styles, special moves, immunities, phases and bodyguard behaviour. |
| Follower attacks | **Real styles** (aviansie, spiritual rangers/mages, Saradomin wizards, etc.), not melee only. |
| Extras | **God Wars altars**, **league tasks**, **Ecumenical key**. Nex and the Ancient Prison stay out. |

## Plan, in build order

1. **(BUILT 2026-10-12) God item table** (`GodItems`): obj -> god, built from the Wiki's Armadyl, Bandos, Saradomin and Zamorak
   item lists (the obj symbols have 100-190 name matches per god, mostly noise such as potions, house items
   and clue robes, so the list is Wiki-driven, not name-matched). Four varps (one per god, 0/1) kept in step
   with the worn items on equip/unequip and login.
2. **(BUILT 2026-10-12) Aggression.** Four hunt modes `godwars_<god>_aggressive` (aggressive melee/ranged variants, `checkVar1`
   = that god's varp equals 0) and a startup edit that sets `huntMode` + `huntRange` on every follower by
   god (`God.ofFollower`), and a plain always-aggressive mode on the generals and bodyguards. Tests: calm with
   the item, aggressive without, other gods' items do not help, persists mid-fight after unequipping.
3. **(BUILT 2026-10-12) Follower attack styles.** One table npc -> `NpcAttack` (style, anim, projectile, max hit), from the Wiki
   and cache stats.
4. **(BUILT 2026-10-12) Bosses**: all four generals, one script each on `NpcAttacks`, with the bodyguards: Kree'arra (Skree, Geerin, Kilisa),
   General Graardor (Strongstack, Steelwill, Grimspike), Commander Zilyana (Starlight, Growler, Bree),
   K'ril Tsutsaroth (Balfrug Kreeyath, Tstanon Karlak, Zakl'n Gritch). Exact mechanics to be read from the
   Wiki per boss when each is built (immunities, specials, max hits, attack speeds, protection-prayer rules).
5. **(BUILT 2026-10-12) Altars** (prayer restore + boost one above max per god item worn, once per 10 minutes out of combat).
6. **(BUILT 2026-10-12) Ecumenical key** drop from followers; skips a chamber's 40 kill count (`DungeonGates`).
7. **(BUILT 2026-10-12) League tasks** for the generals' kills and any-boss 100/250/500 (check `LeagueTaskList` first).
8. Tests per step, docs, memory.

## Known risks

- Not seen in a real client; needs `packCache` for the new hunt modes and any new config.
- Boss animations and projectile graphics need cache lookups (`seqs`/`spotanims` names) per boss; where the
  cache has no suitable sequence the npc falls back to derived anims (`tools/npc-anims`).
