# Isle of Souls: Relic Rework Planning Doc

Goal: decide what each relic should actually do in our mini-league, since a lot of the Raging
Echoes effects depend on content the Isle of Souls does not have (agility, thieving, slayer,
farming, clues, and so on).

This first pass lists the **original Raging Echoes behaviour** tier by tier, so we can mark what
to keep, change or cut. Source:
[OSRS Wiki: Raging Echoes League/Relics](https://oldschool.runescape.wiki/w/Raging_Echoes_League/Relics).
Tier layout and point thresholds match `RelicData.kt` (cache struct data).

How to use this doc: every relic has a **Mini-league change** line. Fill it in as we decide.
The **Status** line is the current state in rsmod as of 2026-10-05 (from the project notes).

Legend for status: ✅ implemented, 🟡 partly implemented, ❌ not implemented.

---

## Overview

| Tier | Points | Relics | Passive summary |
|------|--------|--------|-----------------|
| 1 | 0 | Power Miner, Lumberjack, Animal Wrangler | 5x XP, 5x drops (wiki 2x), fast farming ticks, 4x minigame points, no run drain, stackable clue boxes |
| 2 | 750 | Corner Cutter, Friendly Forager, Dodgy Deals | XP 5x → 8x |
| 3 | 1,500 | Clue Compass, Bank Heist, Fairy's Flight | 2x combat XP, free Bigger and Badder, 5x slayer points, superiors at 1/50 |
| 4 | 2,500 | Golden God, Reloaded, Equilibrium | 20x drops (wiki 5x), 8x minigame points |
| 5 | 5,000 | Treasure Arbiter, Production Master, Slayer Master | XP 8x → 12x |
| 6 | 8,000 | Total Recall, Banker's Note | None |
| 7 | 16,000 | Pocket Kingdom, Grimoire, Overgrown | XP 12x → 16x |
| 8 | 25,000 | Specialist, Guardian, Last Stand | None |

General rule from the wiki: one relic is picked per tier, and a pick is permanent for that tier.
The first relic is chosen after the tutorial; later ones unlock at the point thresholds above.

---

## Tier 1 (0 points)

### Passives
- Leagues XP multiplier is 5x.
- Items from eligible sources are 2x as common.
- Run energy never drains while running.

**Passive status:** 🟡 XP multiplier ✅. Drop multiplier, farming ticks, minigame points, run
drain and clue boxes ❌.

### Power Miner
- Grants an Echo pickaxe (non-degradable, no requirements).
- While equipped: 50% secondary chance to succeed on a failed mining roll.
- Mined items are auto-banked.
- Rocks don't deplete until 4 ores are collected (stacks with Mining gloves, capped at 7).
- Toggles: auto-smelt ores (with Smithing XP), auto-cut gems (with Crafting XP), regardless of
  levels.

**Status:** ✅ Retry, auto-bank, 4-ore rock and the Echo pickaxe. The pickaxe's "Toggle" option opens a chat menu with auto-smelt, auto-gem cut and auto-bank (each shown on/off). Auto-smelt: copper and tin give bronze, iron gives iron, coal gives steel, and mithril, adamantite and runite give their own bars without coal. Auto-gem cut cuts gems found while mining (mining now drops gems).
**Mini-league change:** _TBD_

### Lumberjack
- Grants an Echo axe (non-degradable, no requirements).
- While equipped: 50% secondary chance on a failed chop.
- Woodcutting items are auto-banked.
- Toggles: auto-burn logs (Firemaking XP), auto-fletch logs into arrow shafts (Fletching XP),
  regardless of levels.

**Status:** ✅ Retry, auto-bank and the Echo axe. The axe's "Toggle" option opens a chat menu with auto-burn, auto-fletch and auto-bank (burn and fletch exclude each other; fletching gives the real number of arrow shafts for the log: 15 for logs up to 105 for redwood, with the matching Fletching XP).
**Mini-league change:** _TBD_

### Animal Wrangler
- Grants an Echo harpoon (non-degradable, no requirements).
- While equipped: 50% secondary success chance on a failed fishing roll.
- Fishing items are auto-banked.
- Caught fish have a 100% chance to auto-cook with Cooking XP (no level requirement).
- Fishing is 1 tick faster; the harpoon acts as any fishing tool.
- Hunter: traps never fail; box traps catch chinchompas faster with doubled catches and XP;
  impling jars never break.
- Also: never burn food while cooking.

**Status:** 🟡 Echo harpoon (acts as a net, big net, lobster pot, rod or karambwan vessel, no bait needed, one tick
faster), 50% retry, auto-bank (not stackable karambwanji), auto-cook with Cooking XP and no level
requirement, and no burning ✅. Every fish is auto-cooked (the wiki says 50%, the relic text says
100%); auto-cooked fish count for cooking tasks. Hunter effects ✅ (2026-10-09, `LeagueHunterRelics`: traps never fail, box traps faster with doubled chinchompa catches and XP, impling jars never break).
**Mini-league change:** _TBD_

---

## Tier 2 (750 points)

### Passives
- Leagues XP multiplier increases from 5x to 8x.

**Passive status:** ✅

### Corner Cutter
- Receive sage's greaves on selection.
- While equipped: gain Agility XP based on Agility level while running.
- Always succeed at Agility actions; completing a course counts as two completions and gives 25%
  bonus XP.
- Marks of grace spawn with 10,000 coins.
- Double quantities of pyramid tops, hallowed marks, Brimhaven vouchers, and crystal shards from
  the Prifddinas Agility Course.

**Status:** 🟡 Greaves granted; no effects (no agility content). ❌
**Mini-league change:** _TBD_

### Friendly Forager
- Receive a forager's pouch on selection.
- While in inventory or equipped: when gathering from Woodcutting, Fishing, Mining or Hunting,
  the pouch finds and stores random grimy herbs (limited to Herblore level + 25). Works like a
  herb sack.
- Herblore: secondary ingredients have a 90% chance to not be consumed; potions are made with 4
  doses instead of 3.

**Status:** 🟡 Pouch and herb finding on mining/woodcutting ✅. Herbs are auto-banked because no
herb sack exists. Fishing hook ✅. Herblore perks (90% of secondaries kept, four-dose potions) ✅, see `isle_of_souls_herblore_plan.md`. Hunting hook ✅ (2026-10-09: each trap or pit collected, butterfly or impling caught, kebbit hunted and birdhouse emptied finds a herb).
**Mini-league change:** _TBD_

### Dodgy Deals
- Pickpocketing an NPC also pickpockets all same-type NPCs in an 11x11 square for extra loot.
- 100% success on all Thieving checks.
- Auto re-pickpocket until unable.
- Pickpocketed items are noted.
- Coin pouch maximum increased 3x.
- Stalls never deplete.

**Status:** ✅ Built 2026-10-09 (`LeagueDodgyDeals`, see `isle_of_souls_thieving_plan.md`): all six effects. The area
robbery counts every npc of the same target within five tiles of the one clicked.
**Mini-league change:** _TBD_

---

## Tier 3 (1,500 points)

### Passives
- Combat XP (including Hitpoints and Prayer) is multiplied by 2x.
- The Bigger and Badder slayer unlock is free.
- Slayer reward points are 5x from tasks; no need to complete 5 tasks first.
- Superior slayer monsters appear at a 1/50 rate.

**Passive status:** ✅ Built 2026-10-11 (`LeagueSlayerRelics`, `LeagueRelicTracker` for the combat XP): all four passives. Check in client.

### Clue Compass
- Receive a clue compass item: teleport to any STASH unit and Falo the Bard.
- Can also teleport to the current clue step (except NPC-kill steps).
- Ignores wilderness teleport restrictions.
- Cannot teleport to unlocked areas.
- Must be unlocked in a bank.

**Status:** ❌
**Mini-league change:** _TBD_

### Bank Heist
- Receive a banker's briefcase: teleport to any deposit box, bank or bank chest.
- Ignores wilderness teleport restrictions.
- Cannot teleport to unlocked areas.
- Must be unlocked in a bank.

**Status:** ❌
**Mini-league change:** _TBD_

### Fairy's Flight
- Receive a fairy mushroom: teleport to any fairy ring, spirit tree or tool leprechaun.
- Auto-completes the Tree Gnome Village quest.
- Can only teleport to unlocked regions.
- Ignores wilderness teleport restrictions.
- Must be unlocked in a bank.

**Status:** ❌
**Mini-league change:** _TBD_

---

## Tier 4 (2,500 points)

### Passives
- Items from eligible sources are 5x as common.
- Minigame points are boosted 8x.

**Passive status:** ❌

### Golden God
- High and Low Alchemy have no rune cost or level requirement.
- Items give 15% more gold, with a 65% chance to not be consumed.
- Alchemy spells auto-recast on stacks until depleted (walking and attacking don't cancel).
- Prayer: 20,000 coins can be offered at Prayer altars for XP equal to dragon bones (respects
  altar modifiers).
- Select shop items can be noted (toggle).

**Status:** ✅ (2026-10-08). Alchemy: `LeagueGoldenGod` implements the magic module's
`AlchemyModifiers` (free cast, 115% of the coins, 65% keep chance, repeat); the repeat is a soft
timer every 5 ticks (`magic_alch_recast`), not a loop, so the player is free to walk and fight, and
it ends when the stack is gone, the item is moved, or something else is cast. Prayer: using coins on
an altar is an offering of 20,000 coins for the XP of one dragon bone offering (72 x the altar's
multiplier x XP modifiers); with more coins it repeats, one offering per 20,000, and fewer than
20,000 does nothing. The chaos altar still spares half of the offerings. This goes through the new
`Altars.OfferRule` event in the prayer module. Shops: `ShopNoting` (new hook in `api/shops`, applied
in `ShopScript`) notes what is bought, when the item can be noted, from the rune shop, Yarsul's
pickaxe shop and Percy's nugget shop (`LeagueGoldenGod.NOTED_SHOPS`; add new shops there). The
switch is the shop screen's own "Noted items" button (`shopmain:note_button`): the client draws it
by itself while the relic is chosen and reads `league_shop_noted_items_disabled` for its state; the
server enables the click on its child 9 when a shop opens (`Shops.NOTE_BUTTON_CHILD`) and flips the
varbit (`LeagueGoldenGodScript`). No new variables, so no `packCache` for this. 16 tests in
`GoldenGodTest`.
**Mini-league change:** none.

### Reloaded
- Choose another relic from any previous tier.

**Status:** ✅ Built 2026-11-03 (`LeagueRelicTracker`): with Reloaded chosen, one relic from tiers 1-3 can be picked on the Relics screen; it is kept in the client's own `league_relic_selection_other_N` varbits, which the client already counts as active. Every relic effect checks `LeagueRelicTracker.has`, so the extra pick works like a normal one. Check in client.
**Mini-league change:** _TBD_

### Equilibrium
- When gaining experience, gain extra XP equal to 10% of total level (20% when training the
  skill with the least experience).
- The extra XP is not multiplied by the Leagues XP multiplier.
- Triggers per individual XP instance.

**Status:** ✅ Built 2026-11-03 (`LeagueEquilibrium`, via the new `XpBonus` hook in `PlayerSkillXP`): wiki numbers (10% / 20% of total level per xp drop). The bonus skips every multiplier, the realm's 150x too, so it is small next to normal xp. "Least experience" is the lowest xp of all 23 skills; a tie counts.
**Mini-league change:** _TBD_

---

## Tier 5 (5,000 points)

### Passives
- Leagues XP multiplier increases from 8x to 12x.

**Passive status:** ✅

### Treasure Arbiter
- Impling jars and clue-dropping creatures have a 1/15 chance of dropping clues.
- Clue geodes, nests and bottles are found 10x more often.
- All clues have the lowest possible number of steps.
- Caskets roll maximum rewards (the same item can roll multiple times): Beginner 3 rolls, Easy
  4, Medium 5, Hard 6, Elite 6, Master 7.
- Emote, Falo and Charlie clue steps no longer require items.

**Status:** ❌
**Mini-league change:** _TBD_

### Production Master
- Items are processed all at once for:
  - Smelting ores/bars and cannonballs.
  - Fletching logs, stringing bows, making arrow shafts and cutting bolt tips.
  - Making headless/regular arrows, bolts and darts (capped at 10x regular).
  - Cleaning herbs and making potions without stackable secondaries.
  - Cooking food, making pies/pizzas and jugs of wine.
  - Crafting leather, uncut gems, glassblowing, jewelry, pottery, battlestaves, and spinning
    flax/wool.

**Status:** ✅ Built 2026-11-03 (`LeagueProductionMaster`, via the new `ProductionBatch` hook used by each skill): smelting, smithing (as many as the bars allow), fletching (ammo capped at 10 sets per action), herb cleaning (every herb of the kind), potions (not with a stackable secondary), range/fire cooking (every raw piece), pies/pizzas/wine, and every crafting job. Cannonballs do not exist on the server.
**Mini-league change:** _TBD_

### Slayer Master
- Always on task for eligible slayer monsters.
- All slayer task perks unlocked for free.
- Rune pouches, herb sacks and looting bags from the slayer point store are free.
- Skip and block tasks are free.
- 1,000 to 15,000 bonus Slayer XP (before multipliers) the first time you kill the 100th of each
  unique slayer monster, scaled by the monster's Slayer level requirement.

**Status:** ✅ Built 2026-10-11 (`LeagueSlayerRelics`, see `isle_of_souls_slayer_plan.md`): all five
effects. "Unique slayer monster" is each Slayer task kind. Check in client.
**Mini-league change:** _TBD_

---

## Tier 6 (8,000 points)

### Passives
- None.

### Total Recall
- Receive a Crystal of Echoes that stores one coordinate along with hitpoints, prayer and
  special attack energy.
- Teleport back to the coordinate at any time, restoring the saved stats.

**Status:** ✅ Built 2026-11-03 (`LeagueTotalRecall`): the cache's Crystal of echoes, saved in the cache's own `league_last_recall_*` variables. Save-memory refuses inside instances; the memory stays after use and after death, as on the wiki. Uses the normal spell teleport animation. Check in client.
**Mini-league change:** _TBD_

### Banker's Note
- Receive a banker's note: use noteable items on it to note/un-note them.
- The "Activate" option notes/un-notes in one action with a chosen quantity.
- Customisable quantities for noting and un-noting.

**Status:** ✅ Built 2026-11-03 (`LeagueBankersNote`): use an item on the note to note or un-note it; Note-Quantity / Un-note-Quantity set the amounts (0 = all); Activate repeats the last item. **Guess:** Toggle switches Activate between noting and un-noting. The last item is not saved over logout.
**Mini-league change:** _TBD_

---

## Tier 7 (16,000 points)

### Passives
- Leagues XP multiplier increases from 12x to 16x.

**Passive status:** ✅

### Pocket Kingdom
- Receive a Pocket kingdom item: full Managing Miscellania access from anywhere.
- Workers cost 50% less, always have max approval, produce twice as many resources, and accrue
  resources hourly instead of daily.

**Status:** ❌
**Mini-league change:** _TBD_

### Grimoire
- Receive an Arcane grimoire: free spellbook swapping, and acts as a Book of the Dead.
- Unlocking this relic grants access to all prayers and spells regardless of area, quest or diary
  requirements.

**Status:** ✅ Built 2026-11-03 (`LeagueGrimoire`): the cache's Arcane grimoire changes to any spellbook and back to the previous one, from the inventory or worn. The "all spells and prayers" part was already true for everyone, and nothing on the server needs a Book of the Dead.
**Mini-league change:** _TBD_

### Overgrown
- Receive a Leprechaun's vault: access the Seed vault from anywhere.
- Crops never die.
- Planted seeds have a 75% chance to not be consumed.
- When a seed fully grows, if the Seed Vault has another seed of the same type, the patch
  auto-harvests and replants.
- Auto-replanted seeds have Ultracompost added automatically.

**Status:** ❌
**Mini-league change:** _TBD_

---

## Tier 8 (25,000 points)

### Passives
- None.

### Specialist
- All special attacks cost 20% of normal energy and have +100% accuracy.
- Each failed accuracy roll gives 10% special attack energy.
- Killing an NPC restores 15% special attack energy.

**Status:** ✅ Built 2026-11-03 (`LeagueSpecialist`, via the new `SpecialAccuracy` hook in `api/specials`): flat 20% cost, 2x accuracy, 10% back per failed roll, 15% per kill. A ranged special that rolls damage in one call counts a 0 as a miss.
**Mini-league change:** _TBD_

### Guardian
- Receive a Guardian horn: summon a powerful Guardian thrall for 30 minutes.
- The Guardian attacks with the style the target is weak to; in multi-combat it casts an AoE
  attack if the target is weak to range/magic.
- Stats: min hit 5, max hit 13, attack speed 4, accuracy 45000.
- Does not work in PvP; works inside The Gauntlet.
- Horn can be retrieved from The Sage if lost.

**Status:** ✅ Built 2026-11-03 (`LeagueGuardian`): the cache's Guardian horn summons a follower for 30 minutes that hits the player's target every 4 ticks with its weakest style (45,000 accuracy roll, 5-13 damage), with the area attack on ranged/magic (Toggle-modes switches it off). The cache's `league_guardian` npc has no model, so the user chose a female Kourend royal guard to stand in; Change-outfit cycles royal guard (female, male) and white knight. **Guesses:** the area attack reaches npcs within one tile of the target; the guardian hits from where it stands like the Arceuus thralls; Toggle-modes and the outfit are not saved over logout; auto-summon and Echo Cuthbert are not built. Uses the cache's own guardian cast animation, projectiles and summon animation. Check in client.
**Mini-league change:** _TBD_

### Last Stand
- If damage would reduce you to 0 HP, you are reduced to 1 HP instead.
- Combat stats are instantly boosted to 255, then drain 16 per tick to base level + 15.
- For the next 16 ticks you cannot be reduced to 0 HP.
- When the effect ends, all incoming damage during it is nullified and you are healed based on
  the damage dealt over those 16 ticks.
- Cannot be used again until death or 3 minutes pass.

**Status:** ✅ Built 2026-11-03 (`LeagueLastStand`, via the new `PlayerHitEvents.Landing` event): 1 HP instead of dying, Attack/Strength/Defence/Ranged/Magic to 255 draining 16 a tick to base + 15, 16 ticks of no damage below 1 HP, then a heal equal to the damage dealt in those ticks; 3-minute cooldown, reset by death. Not done: blocking food heals during the effect, the red heal bar. The cooldown is kept in memory, so logging out resets it.
**Mini-league change:** _TBD_

---

## Open questions for the rework

Starting points for the next pass; none of these are decided.

1. **Content gaps.** Many relics target skills with no content on the isle (agility, thieving,
   hunter, farming, slayer, clues). Replace those effects with equivalents for content we do
   have (mining, woodcutting, combat), or add the content?
2. **Passives.** Which passives are worth keeping (XP multipliers are done)? Candidates to cut or
   replace: drop-rate multipliers, minigame points, farming ticks, slayer perks, clue boxes.
3. **Tier 1 toggles.** Do the auto-smelt, auto-cut, auto-burn and auto-fletch toggles stay, given
   there's no smithing, crafting, firemaking or fletching content wired up yet?
4. **Reloaded.** Keep as a repick, or swap it for a different tier 4 effect?
5. **Teleport relics (tier 3).** The wiki versions rely on unlocked areas and wilderness
   restrictions, neither of which exists on the isle yet. What should they do instead?
6. **Tier thresholds.** Do the point thresholds (0 / 750 / 1,500 / 2,500 / 5,000 / 8,000 / 16,000 /
   25,000) still make sense with the 114-task list we have?
7. **Balance.** With a base realm XP rate of 150, how high should the league multiplier go?
