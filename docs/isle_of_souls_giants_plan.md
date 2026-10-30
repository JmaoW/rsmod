# Obor and Bryophyta (the giants' lairs)

Built 2026-10-19 in `content/areas/misc/isle-of-souls/.../giants/`. Source: tsps
(`server/plugins/bosses/giantlairs/*` and `data/giant-boss-lairs.json`, from rsprox captures), the OSRS
Wiki (Obor, Bryophyta, Growthling, the two chests, Bryophyta's staff) and the cache's map. Tests:
`GiantLairsTest` (13). **Left for the user:** `./gradlew packCache` (npc edits, new timer, synth, varp
and struct symbols, the task text edits) and a client check (list at the end).

## What a player sees

- **Obor** (Edgeville Dungeon): the gate at 3095,9832-3 asks "Enter Obor's Lair?". Without a key it says
  "The gate is locked shut." until a Giant key has opened it once; then the key is kept and the gate
  opens without one. The lair is a **team instance**: a copy of the zones around his lair
  (x 3072-3111, z 9784-9823), alive while anyone is in it, shared by the team. A player arrives in the
  safe zone north of the fence; the rocks (`Climb`) slide them into the pit and back out. Only a player
  in the pit is fought, and only a player in the pit can hurt him.
- **Bryophyta** (Varrock Sewers): the gate at 3174,9901 gives the instance warning, then "Are you sure
  you wish to open it?" and drops the team straight into her lair (x 3200-3239, z 9920-9951) with her
  attacking at once. Same key rule with the Mossy key.
- **Exit:** Obor's gate halves and Bryophyta's rock pile: "Open"/"Clamber" asks, "Quick-exit" does not.
  Leaving puts the player outside the gate in the real dungeon. Logging out ends the lair (the instance
  login pipeline moves them out).
- **Chest** (in the copy, as in the real map): while the boss is up "You can't loot the chest whilst Obor
  is still attacking you!". After a kill the first click (key or not) says "You hear the ground
  rumble..." / "You hear the leaves stir..." and brings the boss back 15 ticks later (scaled by the
  respawn-speed knob). With a key (once per kill) it is used up, the loot spills beside the chest, the
  chest count (varps 1529 / 1733, the client's own kill-count entries) goes up and the chest shows open
  for 19 ticks.
- **Giant bones** (always dropped by both): "Take" says "I don't think those will fit in my
  backpack...", "Bury" asks (Yes / Yes, and don't ask again / No) then 150 Prayer XP, anim, sound, no
  message. Counts for the league's bury tasks (`Burying.BoneBuried`).
- **Bryophyta's staff:** her essence on a battlestaff (62 Crafting) makes the uncharged staff; nature runes
  used on it charge it (up to 1,000, all the player holds); "Uncharge" empties it, the runes are lost.
  The engine lets a charged staff stand in for any number of nature runes, so charges are not counted
  down (deviation: the real staff has a 1/15 chance to save one).

## The fights

**Obor** (120 hp, max 22 melee / 26 ranged, speed 6): next to him he punches; 1 in 4 punches also knocks the
player back up to 5 tiles (hit 2 ticks later); from range the same swing throws a boulder (hit 1 tick,
earthstrike splash and knock-down a tick later, camera shake); 1 in 3 attacks when adjacent (1 in 2 further
away) is the stamp: splash on his tile, a tick later rocks fall along the line to the player (6 client
cycles a tile), hit 2 ticks after the stamp. Ranged hits are halved by Protect from Missiles (`GiantHits`
reduction 50). After each attack he wants the next target either next to him or anywhere within 8.
**Bryophyta** (115 hp, max 16 melee / 10 magic, speed 6, poison 8): never moves; adjacent half her attacks
are a punch (1 in 4 of those that hit poison for 8), everything else an earth blast within 8. Once she has
lost 25% of her hitpoints each attack has a 1 in 5 chance to summon three growthlings (10 hp, max 5, speed
4), one batch at a time, 20 ticks after the last died. While one lives she takes no damage. An axe
(not a battleaxe, pickaxe, zombie or blessed axe) or magic secateurs equipped kills one per hit ("You prune
the Growthling."); anything else can hurt but never kill it ("Cut the growthling down with an axe or
secateurs."). The logs in her lair give a bronze axe ("You take a bronze axe from the logs.", back 49 ticks
later) or say "You already have an axe.".

## Loot

- Obor's kill table is the wiki's (imported with the same code as `osrs.toml`, appended to `isle.toml`):
  giant bones always, Giant key 1/16, ensouled giant head 1/25, beginner clue 1/50, bones/long bone etc.
- Both chest tables are the wiki's (checked against tsps' json), stored on the npc types
  `gb_hillgiant_chest` / `gb_mossgiant_chest`, with a 1/16 Giant key / Mossy key tertiary in each (wiki:
  "Chest (Obor's lair)" lists the key; tsps left it out). The chests are rolled by `GiantRewards`
  through the usual `NpcDropEvents.Modify`/`Rolled`, so the relic boosts for Hill giant club and
  Bryophyta's essence (already in `boosted.toml`) apply, and the loot tracker is told. Uncut rubies come
  with uncut diamonds (added in code).
- Bryophyta's kill table (already imported) has the Mossy key at 1/16. Keys also drop from hill giants
  (1/128) and moss giants (1/150). Not done: the 1/8 on a moss giant task.

## League tasks

New: 472 "Defeat Obor" (Medium) and 477 "Equip Bryophyta's Staff" (Hard, charged or not); 476 "Defeat
Bryophyta" already existed. Text edits and struct symbols for all three (`league_task_472/476/477`); the
enum has two new entries at the end (structs 1966, 1971).

## Wiring and decisions

- Spawn of Bryophyta in the real lair removed (`underground.toml`, `exclusions.txt` `npc 8195`).
- Team instance per kind of lair (user decision); `Obor` and `Bryophyta` are `WaveDirector`s like Scurrius;
  the boss spawns on the team's first tick and again 15 ticks after a chest click.
- Camera shake needed a new `Camera.camShake` in `api/player-output`.

## Not built / deviations

Combat Achievements and their fight rules (Protection from Moss, Squashing the Giant), the free-world kill
tasks, the Obor entry fade (screen goes straight to the lair), Bryophyta's "on a moss giant task 1/8" key
rate, use-item-on-growthling (equipping works), the 1/15 staff charge save, music unlock lines, the
steel key ring and the giantsoul amulet.

## Guesses to check in the client

Death animation length (5 ticks for both, no frame data in the cache); `CamShake(4, 4, 0, 1)` argument
order; knockback and pit slide cycles (20/40 and 10/90) and facing; rockfall and boulder graphic heights
(96, 160) and delays; which tile the player must stand on to reach the chest (the loot tile is beside it);
where the instance copy cuts off the lair (Obor's: only zones x 384-388, z 1223-1227 are copied); the
stamp/knockback/range odds (tsps' own estimates); that growthling spawn tiles look right; the 25%
threshold for growthlings (wiki) against tsps, which allows them from the start.
