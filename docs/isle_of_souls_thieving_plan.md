# Isle of Souls: Thieving plan and status

Written and built 2026-10-08/09. The numbers come from two places: the tsps doc `docs/thieving.md`
and data file `server/data/definitions/pickpocketing.json` (`RSPSApp/tsps`, BSD-2, see the content
plan section 11; itself transcribed from the OSRS Wiki), and the OSRS Wiki's `Stall/Thievable` table
and each stall's own page (fetched through the wiki API). Nothing is from RuneScape 3. Every item id
was checked against this cache (revision 233).

## What is built (module `content/skills/thieving`)

| Piece | Where | What it does |
| --- | --- | --- |
| Pickpocketing | `scripts/Pickpocketing.kt`, `configs/PickpocketTargets.kt` | 23 targets (man to TzHaar-Hur). Every cache npc with the "Pickpocket" option and a target's name can be robbed, so a npc placed later just works. Success chance from the Wiki's interpolation of the 1/99 chart, then the gloves of silence (x1.05) and a Thieving or max cape (x1.1). Caught: the npc's line and attack, the stunned graphic, a 9 tick stun (8 ticks without pickpocketing), the target's stun damage |
| Loot | same | Always items, then the "first" rolls, then one weighted table roll. Coins come as the target's coin pouch. Rogue equipment: 15% per piece to double, all five always; a doubled pouch is the pouch and its coins. The Master Farmer's herb seeds follow Farming level |
| Gear | same | Gloves of silence wear out after 62 caught pickpockets; the dodgy necklace stops 25% of stuns and crumbles on its tenth save. Both counts are kept on the player (varps `thieving_gloves_fails`, `thieving_necklace_saves`) |
| Coin pouches | `scripts/CoinPouches.kt` | "Open-all" and "Open". A stack of 28 of one kind stops pickpocketing until it is opened (84 with Dodgy Deals) |
| Stalls | `scripts/StallThieving.kt`, `configs/Stalls.kt` | 19 stalls from the Wiki table that have a loc in this cache (vegetable, baker's, tea, monkey food/general, crafting, silk, fruit, seed, fur, fish, crossbow, silver, spice, magic, scimitar, gem, and the Mor Ul Rek gem and ore stalls). Never fails: two ticks after reaching, one weighted item and the XP, then the stall is empty for its respawn time |
| Dev helpers | `ThievingDebugCommands` | `::thievekit` gives the gloves, a dodgy necklace, the rogue set and a Thieving cape |
| Relic | `relics/LeagueDodgyDeals.kt` (isle module) | All six effects of Dodgy Deals (below) |
| Tasks | `tasks/LeagueSkillTasks.kt` | 33 thieving tasks worth 1,890 points |

Events other modules can use: `Pickpocketing.Pickpocketed`, `Pickpocketing.Modifiers`,
`StallThieving.Stolen`, `StallThieving.Modifiers`, `CoinPouches.PouchesOpened`.

## Decisions (all mine, from the plan; say if any should change)

1. **Quests do not lock targets.** The isle has no quests, so cave goblins, Fremennik, Knights of
   Varlamore, vyres and both kinds of elf are all robbable (their npcs are in the cache). The Digsite
   workman uses the after-quest loot (4/11 coins, no animal skull). TzHaar-Hur still needs a fire or
   infernal cape (worn or carried), which the isle can get.
2. **Left out of the tsps table:** the pirate (Sailing loot that revision 233 has not got), clue
   scroll rolls (no clue scrolls on the isle yet; the rates are listed in `PickpocketTargets.kt`),
   blackjacking, the wealthy citizen's street urchin, the Ardougne diary bonuses, Shadow Veil, the
   Rocky pet and the loot tracker. The H.A.M. robe-during-a-quest table is not modelled either.
3. **Left out of the stalls:** the Port Roberts stalls (new Sailing content), the Wine stall (no loc in
   the cache), the dairy cow bell, the candle stand, chests and doors, the stall owner and guards
   (a stall is simply empty for its respawn time), and the Rocky pet.
4. **Which loc a robbed stall turns into** is a guess from the cache names (`market` for the
   Ardougne-style ones, the Keldagrim, Hosidius, Prifddinas, Fortis and Mor Ul Rek empties for theirs).
5. **Timing:** the strike, the graphic and the damage come on the tick the thief is caught (tsps's
   capture has them a tick later), so clicking away cannot dodge the stun. "You've been stunned!"
   still comes a tick later.
6. **Messages are mostly guesses** where tsps says "unverified": the requirement messages, the
   pouch-limit message, the necklace and gloves messages, and the stall steal message
   ("You steal a silk from the silk stall.").

## Dodgy Deals (relic tier 2, option 3)

The skill publishes modifier events and the relic fills them in, so thieving knows nothing of it.
- Pickpocketing a target also pickpockets every npc of the same target within five tiles of it (an
  11 by 11 square): extra loot for each, no extra XP. Each one counts for the 50-times tasks.
- 100% success on pickpockets (stalls never fail anyway).
- A pickpocket or a steal from a stall repeats by itself until something stops it (full inventory,
  too many pouches, a click elsewhere).
- What is pickpocketed comes noted (the pouches are not noteable).
- Coin pouch stacks can reach 84.
- Stalls never empty.

## League tasks (33, 1,890 points)

Easy (10): Pickpocket a Citizen, Steal a Chocolate Slice, Steal From a Tea Stall, Steal Some Silk.
Medium (30): Golovanova fruit top, Master Farmer, Open 28 Coin Pouches At Once, 800 coins from pouches
at once, HAM member, Guard, Bullseye lantern from a cave goblin, Knight 50 times, Bandit, House key,
15 House keys, Diamond from a gem stall, Tiara from a silver stall, Fur, Silver, Spice, Fish, Wooden
stock, Chisel. Hard (80): Gem stall, Elf 50 times, Hero, Menaphite Thug 50 times, Vyre 50 times, King
Worm. Elite (200): level 99 and 25m, 35m and 50m XP.

22 of them had an area in the client's list, so their struct text was rewritten
(`IsleOfSoulsTaskTexts`) and they complete anywhere. Left out: Pyramid Plunder, artefacts, the Stealing Valuables minigame, the
cow bell, Rogues' Castle, and the duplicate "Fortis Spice Stall" (the Kandarin one covers spice).
"Equip a Full Rogue Outfit" is left out because nothing on the isle gives the outfit.
Counters are the new varps `isle_count_pickpocket_knight_50`, `..._elf_50`, `..._vyre_50`,
`..._thug_50` and `isle_count_house_keys_15`.

## What the isle still lacks for thieving

Gloves of silence (Hunter), the rogue outfit (Rogues' Den) and the dodgy necklace's opal (Crafting
and Magic can make it) have no source except `::thievekit`. Clue scrolls will add their rolls to
the H.A.M., wealthy citizen, paladin, gnome and hero tables when they exist. Shop money now has its
source: pouches open into coins, and the shops take coins.

## Tests

- `ThievingTablesTest` (7): the chance formula, the gear multipliers, monotone charts, table sanity.
- `PickpocketingTest` (12), `StallThievingTest` (5): a pickpocket, level
  and fire cape checks, getting caught (stun, damage, the lockout), the rogue set, the pouch limit,
  gloves wearing out, the necklace, opening pouches, stall loot, refilling, the placement.
- Isle module: `LeagueThievingTasksTest` (9), `LeagueDodgyDealsTest` (3), `LeagueThievingTaskListTest` (3).

## Before it can be tried in the client

1. Restart the server **and run `./gradlew packCache` once**: two thieving varps, five counter varps
   (65402..65396 in `varp.sym`), 22 `league_task_<id>` structs (`struct.sym`) and the changed task
   enum all go into the cache.
2. Walk to a stall or a pickpocket target of the map (the dev stalls and npcs north of the lobby, `ThievingSpawns`, were removed on 2026-10-10).
3. Things to look at in the client: the empty-stall models, the stunned graphic height, whether
   "Steal-from" and "Pickpocket" are the first option for each npc, and the Dodgy Deals loop.
