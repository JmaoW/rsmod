# King Black Dragon

Built 2026-10-22. The normal King Black Dragon only: the Echo version is saved for later, together with
the echo-orb mechanic the other eight echo bosses need (see `isle_of_souls_content_plan.md`, 3.4).

Code: `content/areas/misc/isle-of-souls/.../kingblackdragon/` (`KbdConfigs.kt`, `KingBlackDragon.kt`,
`KbdScript.kt`), the breath effects in `content/other/dragonfire/.../BreathEffects.kt` and
`DragonScript.kt`. 11 integration tests (`KingBlackDragonRunTest`).

## How a team gets in

- A copy of the real entrance lever (`dragonkinginlever`, "Pull / Commune / Private") hangs on a wall of
  the Revenant Caves at **(3220, 10061)**, three tiles from where the cave's own exit to the surface
  stands. It is a wall decoration (shape 4, angle south): the wall is the south edge of that tile, found
  with `tools/dungeons/collision.py`. Players who leave the lair land on (3220, 10062), the tile north.
  To move it, change `KbdLever` in `KingBlackDragon.kt` (it needs a floor tile with a straight wall on
  its south edge and a free tile north of it).
- Pull and Private both enter: every team gets a lair of its own (a `TeamInstance`, like Scurrius and the
  Kalphite Queen). There is no fee and no spiders. "Commune" only says nothing answers.
- The lair is the real map (region 9033) copied by zone block 281/584 (6x6 zones, all levels). The floor
  is x 2256-2287, z 4680-4711; players arrive on (2271, 4682). The lever on the south wall (2271, 4680)
  is the real exit lever and takes players back to the cave lever.
- The world spawn the cache's spawn list had in the real lair was removed (`underground.toml`, and npc
  239 / 2642 are in `tools/spawns/exclusions.txt`).

## The fight

- One wave: 10 ticks after the team arrives the dragon appears on its map spawn tile (2269, 4697),
  walks up to breathing range of the nearest player and fights there. It is aimed by the director
  (`retargetExtras`), not by the engine, so being hit does not turn it.
- How it attacks is `DragonScript` (already built): up close one attack in two is breath, otherwise a
  bite; from a distance (range 5) always breath. Half its breaths are fire (max 65 unprotected), half
  one of the three special breaths (max 50). The shield / prayer / antifire tables are the wiki's.
- **New:** the special breaths publish a `DragonBreathLanded` event when they hurt, one time in two
  (`DragonScript.EFFECT_ODDS`, a guess: the wiki says "a chance"). `KbdScript` applies the effect:
  poison starting at 8 (an antipoison potion stops it, `PlayerPoison`), a freeze of 10 ticks, or every
  stat but Hitpoints lowered by 2 (`statSub`, so it stacks).
- Stats come from the cache (240 hitpoints, 5x5, stab 40 / slash 90 / crush 90, magic 80). `npcs.toml`
  adds what the cache lacks: ranged defence 70 / 70 / 40, a 50% water-spell weakness, burn immunity,
  7.5% bonus xp and 258 Slayer xp.
- Death: the drops fall at the middle of its 5x5 for whoever did the most damage (hero points,
  16 slots). Nobody is sent out: after 16 ticks (the wiki's respawn, scaled by the global respawn
  speed) the next dragon appears in the same lair, for whoever is still in it.
- Kills count for the whole team. League task **1000** "Defeat the King Black Dragon" (Hard) was added
  to the task list, enum (struct 2514), kill tasks and text edits (the cache text says "in the
  Wilderness"). The collection log kill count and the drop table (wiki, incl. the Prince Black Dragon
  pet and the draconic visage) already existed and are keyed by the same npc type.

## Guesses (the wiki is silent)

- The odds of the second effect of a special breath (1 in 2), and that it needs the breath to have
  done damage; the freeze and drain messages.
- The breath range (5, from the dragonfire module), fire odds next to the player (1 in 2), the 10-tick
  delay before it first appears, and that it comes at the players at once (the wiki says aggressive,
  "may attack immediately from a distance").
- Animations (all unseen in a client): bite = 91 `dragon_head_attack` (set as the npc's attack_anim; the
  cache's derived 80 `dragon_attack` is the other dragons' claw swipe and looked wrong), all breaths =
  84 `dragon_firebreath_middle_attack` (`DragonSeqs.king_breath`; the other dragons keep 86), hit
  reaction 4638 `dragon_block_kbd`, death 92, lever 2140 `macro_lever_switch_down`, no teleport
  graphic. 91 and 84 are what tsps and the old games use. `::kbdanim <seqId>` (admin) plays any seq on
  the dragon of the lair you stand in, to find the right ones (80-84 and 86 are the dragon breath/claw
  family, 91 the bite).
- The death animation is the cache's `dragon_death` and its length is read from it.

## Needs

- `./gradlew packCache` for the `npcs.toml` stats, the `NpcEditor` (chase and hero slots) and the new
  symbols (`kbd_tick` in `timer.sym`, `league_task_1000` in `struct.sym`, the task enum entry).
- A client check: that the lever is visible and clickable on its wall (the south wall pieces near it
  are diagonal in places, so it may sit slightly off), the breath graphics, the freeze, and that the
  lair map and the new league task show up.

## Not built

The Echo King Black Dragon; the 50,000 coin private-instance fee (every lair is private and free);
the lair's treasure-trail restrictions; the Combat Achievement tasks (not in the league list); the
dragonfire shield's charges and special attack.
