# Vorkath

Built 2026-10-27 from the OSRS Wiki (pages "Vorkath", "Vorkath/Strategies", "Dragonfire/Vorkath",
"Zombified Spawn", "Vorkath's head", "Ava's assembler", "Dragonbone necklace"). Timings and attack
weights come from the tsps Vorkath plugin's notes on rsprox captures (used only as a reference; the
damage numbers are the wiki's). Code is in `content/areas/misc/isle-of-souls/.../vorkath/`
(`Vorkath.kt` = the crater, the boat, the run and the fight; `VorkathAttacks.kt`; `VorkathScript.kt`;
`VorkathRewards.kt`; `VorkathConfigs.kt`) and the tasks in `tasks/LeagueVorkathTasks.kt` plus four
lines in `LeagueKillTasks`. Tests: `VorkathRunTest` (integration).

Scope agreed with the user: a team instance of the real crater (like the King Black Dragon and the
Dagannoth Kings), the Ungael boat on the isle (to be moved onto the water later), the full wiki fight,
Vorkath's head guaranteed on the 10th kill and every 50th, Ava's assembler, the dragonbone necklace,
kills counting on a Blue Dragons Slayer task, and the tasks 585, 608, 609, 610, 602 and 605.

## How a team gets in

- Torfinn's boat from Ungael (`ungael_boat`, 6x2, "Travel") sits on the field south of the plaza at
  **(2178, 2796)**, east of the Dagannoth Kings' ladder; players land at (2180, 2795) when they come
  back. Move it with `VorkathBoat` (the user will put it on the water).
- The crater is a copy of the 6x6 zones x 2248-2295, z 4040-4087 (map square 35_63). Players arrive
  at (2272, 4054), just north of the real ice chunks (2272, 4053), which take them out.
- The spawn list's sleeping Vorkath in the real crater was removed from `underground.toml`, with a
  `rect` rule in `tools/spawns/exclusions.txt`.

## The fight

| What | Numbers |
|------|---------|
| Hitpoints | 750 (set at spawn) |
| Attack speed | 5 ticks; a special after 6 standard attacks (the first after 5), taking turns |
| Melee (slash) | up to 32, only next to it (diagonals too) |
| Ranged / magic | up to 32 / 30, protection prayer stops them |
| Fiery, venomous, corrupting dragonfire | wiki "Dragonfire/Vorkath" table (80 burnt / 50 resisted unprotected, 0 with shield + super antifire); venom always envenoms, the corrupting breath turns every prayer off |
| Fireball | up to 121 on its tile, half next to it, nothing protects; lands 5 ticks after the launch; a shadow marks the tile from the launch (the Grotesque Guardians' `gargboss_debris_shadow_150`, as the cache has no Vorkath shadow; an isle addition) |
| Acid special | pools 3 ticks after it spits (one per 3x3 cell of a 7x7 grid round it, 8 edge pools, the player's tile); 25 rapid shots from tick 4, one a tick, 25-41 to a target still on the aimed tile; standing in acid 1-10 a tick, healing Vorkath; damage on it halved |
| Spawn special | icy breath freezes the target; a 38-hitpoint spawn lands ~8 tiles away (8, 7, 9, 6 or 10) on a tile with a straight path in; next to the target it explodes for `hp * 60 / 38`; Crumble Undead kills it outright; Vorkath takes no damage until the spawn is gone ("The dragon is currently immune to your attacks.") |

- Waking: "Poke" turns the sleeper into its no-op form and plays the rise; 7 ticks later it is
  replaced by the awake Vorkath, which attacks 6 ticks after that.
- When the icy breath freezes a player, their attack on Vorkath is dropped (and a queued
  auto-retaliate with it), so they are free to turn to the spawn; they can still click Vorkath
  again. Teammates keep attacking.
- It never moves. It fights its target: whoever poked it, or the nearest player once they are gone.
  The fireball and the acid pools hurt every player standing in them.
- Death: the drops fall on whichever of the 8 tiles just outside its body (corners and side
  middles) is nearest the player who gets them; 8 ticks later it lies down asleep again, ready for
  the next poke. Nobody is sent out.
- `npcs.toml` adds: slash, 750 slayer XP, ranged defences 26, poison and venom immunity, 40% fire
  weakness; the spawn 3 ranged defence, immunities, freeze immunity, 50% fire weakness.
- Admin command `::vorkathspecial acid|spawn` makes the crater's Vorkath use that special next.

## Rewards

- Drop table imported from the wiki (`osrs.toml`, two main rolls, tertiaries including the 1/50 head,
  1/1000 necklace, 1/3000 jar and pet, 1/5000 visages). `boosted.toml` already had its uniques.
- **Vorkath's head** is added as a guaranteed drop on the player's 10th collection-log kill and every
  50th (50, 100, 150...). It uses a new `NpcDropEvents.Modify.guaranteed` list, which `NpcDeath`
  appends unless the roll already gave that item, so a 1/50 head on a guaranteed kill is not doubled.
- **Ava's assembler**: use the head on Ava's accumulator with 75 mithril arrows in the pack (Ava is
  not on the isle). The assembler's ammo recovery comes from its cache params, which the ranged
  code already reads.
- **Dragonbone necklace**: worn, it restores 1-5 Prayer points per bone buried, by the wiki's table.
- **Slayer**: Vorkath already carries the Blue Dragons task id (25) in the cache, so its kills count
  on a Blue Dragons task with no code.

## Tasks

| Task | Tier | How |
|------|------|-----|
| 585 Defeat Vorkath | Hard | `LeagueKillTasks` |
| 608 / 609 / 610 Defeat Vorkath 50 / 150 / 300 Times | Hard / Hard / Elite | `LeagueKillTasks`, counters `isle_count_kill_vorkath_50/150/300` (varp.sym 65173-65171) |
| 605 Defeat Vorkath 15 Times Without Leaving | Elite | every player inside when it dies counts the kill; entering resets |
| 602 Defeat Vorkath 5 Times Without Special Damage | Elite | as above, but any damage from acid, rapid fire or an explosion ends the streak until the player leaves |

Text edits drop "on Ungael" and make all six area-free (`IsleOfSoulsTaskEdits`). Enum keys 549-554.
Left out: the combat achievements task (1430).

## Guesses and deviations

- The poke animation (827) and timings are from captures; the projectile flights are tsps' captured
  values with heights in capture units (not seen in a client yet).
- Pools 1-10 (the wiki says "up to 10"); a minute's freeze that the spawn always ends first; a spawn
  that has not arrived after 60 ticks explodes where it is (tsps' failsafe).
- In a team: only the target is frozen or rapid-fired; the others can keep attacking, but Vorkath
  is immune during the spawn and halved during acid for everyone.
- The acid pools' locs last until the special's latest end and stay visible a few ticks after it
  ends early; they hurt nobody once it is over.
- The assembler is made from the head on the accumulator, not at Ava; the 4,999-coin alternative and
  the ranging cape / max cape / quiver upgrades are not built.

## Needs

- `./gradlew packCache` for the `npcs.toml` changes, the `NpcEditor`, the projectile flights and the
  new symbols: `vorkath_tick` (`timer.sym` 36), five `projanim.sym` ids (33-37), three counters
  (`varp.sym`), six `league_task_*` structs (`struct.sym`) and the task text and enum edits.
- A client check: the boat's angle and spot, the rise, every projectile and impact graphic, the
  acid pools, the spawn's walk and explosion, the immune message, the loot tile, and the new tasks
  in the league window.

## Not built

The combat achievements; the jar of decay, Vorki and visage uses; the turquoise slayer helmet;
the stuffed head; Dragon Slayer II and its quest form.
