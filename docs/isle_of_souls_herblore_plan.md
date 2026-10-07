# Isle of Souls: Herblore

Built 2026-10-06. Scope agreed first (option B, "boss-ready"), then implemented. **Not yet tried in a
real client.** You must restart the server once so the new timer, queue and varps are packed.

## What already existed
Grimy and clean herb items, unfinished potions and every potion dose item in the cache; `statBoost`
and `statHeal`; the Friendly Forager relic, which stores random grimy herbs in the bank. Nothing
cleaned a herb, made a potion or drank one.

## What was built

| Piece | Where |
|-------|-------|
| Clean grimy herbs (inventory op 1, instant), 15 herbs | `content/skills/herblore` (`Herblore`, `Herbs`) |
| Unfinished potions, finished potions, upgrades and grinding (item on item, chatbox menu, batches) | `Herblore`, `HerbloreRecipes` |
| Drinking: one dose a time, 3 tick potion cooldown, every effect below | `Drinking`, `Potions` |
| Effects that keep running (stamina, divine hold, prayer regeneration, antipoison/antifire/goading state) | `PotionEffects` + soft timer `herblore_effects` |
| A hold on stat boosts so they do not wear off (divine potions) | `StatBoostHolds` in `api/stats-plugin`; `StatRegenScript` skips held stats |
| Forager relic perks (four dose potions, 90% of secondaries kept) | `HerbloreMod` / `HerbloreModifiers`; `isleofsouls/relics/LeagueHerblore` |
| 30 league tasks | `LeagueSkillTasks`, `LeagueTaskList`, `IsleOfSoulsTaskEnums` |

New symbol entries (hand-added, like the earlier skills): timer `12 herblore_effects`, queue
`33 herblore_make`, and 16 counter varps `65454` down to `65439` (`isle_count_clean_*`,
`isle_count_make_*`).

### Decisions (yours)
- Scope B: the core set plus super combat, super restore, antifire and extended antifire, brews,
  goading, prayer regeneration, divine potions and the pestle and mortar.
- Secondary ingredients are **not** supplied yet. Nothing on the isle gives eye of newt, snape
  grass, unicorn horn, limpwurt root, red spiders' eggs, white berries, toad's legs, crystal dust,
  amylase, aldarium and so on, so players need them added from elsewhere (shop, drops) before most
  recipes can be made.

## Recipes (wiki levels and experience)

**Cleaning:** guam 3 / 2.5, marrentill 5 / 3.8, tarromin 11 / 5, harralander 20 / 6.3, ranarr 25 /
7.5, toadflax 30 / 8, irit 40 / 8.8, avantoe 48 / 10, kwuarm 54 / 11.3, huasca 58 / 11.8, snapdragon
59 / 11.8, cadantine 65 / 12.5, lantadyme 67 / 13.1, dwarf weed 70 / 13.8, torstol 75 / 15.

**Unfinished** (a vial of water and the clean herb, no experience): levels 3, 5, 12, 22, 30, 34, 45,
50, 55, 58, 63, 66, 69, 72, 78 in the order above.

**Finished** (unfinished potion and one secondary, made with 3 doses): attack 3 / 25, antipoison 5 /
37.5, strength 12 / 50, restore 22 / 62.5, energy 26 / 67.5, defence 30 / 75, agility 34 / 80, combat
36 / 84, prayer 38 / 87.5, super attack 45 / 100, superantipoison 48 / 106.3, fishing 50 / 112.5,
super energy 52 / 117.5, hunter 53 / 120, goading 54 / 132, super strength 55 / 125, prayer
regeneration 58 / 132, super restore 63 / 142.5, super defence 66 / 150, antifire 69 / 157.5, ranging
72 / 162.5, magic 76 / 172.5, zamorak brew 78 / 175, saradomin brew 81 / 180.

**Upgrades** (same number of doses in and out, 1 to 4): stamina 77 (super energy plus one amylase a
dose, 25.5 a dose), extended antifire 84 (antifire plus one lava shard a dose, 27.5 a dose), super
combat 90 (super attack, super strength, super defence of that many doses plus torstol, 150),
divine super attack, strength and defence 70, divine ranging 74, divine magic 78, divine super
combat 97 (the base potion plus one crystal dust a dose, 0.5 a dose).

**Grinding** (no experience): pestle and mortar on unicorn horn, desert goat horn, chocolate bar,
blue dragon scale, or any of six bird nests (crushed nest); chisel on a lava scale (3 to 6 lava
shards).

## Drinking
Doses go 4, 3, 2, 1, then an empty vial. Effects (wiki):

| Potion | Effect |
|--------|--------|
| Attack, strength, defence | +3 and 10% |
| Super attack, strength, defence, super combat | +5 and 15% (super combat all three) |
| Combat | attack and strength +3 and 10% |
| Ranging | +4 and 10%; magic +4; agility, fishing, hunter +3 |
| Restore | all stats but hitpoints and prayer, 10 and 30% |
| Super restore | all stats but hitpoints, 8 and 25% (includes prayer) |
| Prayer | 7 and 25% of the prayer level |
| Energy / super energy | 10% / 20% run energy |
| Stamina | 20% run energy and the stamina varbit on for 200 ticks |
| Saradomin brew | hitpoints +2 and 15%, defence +2 and 20%, attack, strength, magic, ranged -2 and 10% |
| Zamorak brew | attack +2 and 20%, strength +2 and 12%, defence -2 and 10%, hitpoints -(2 + 10% of what you have), never below 1 |
| Prayer regeneration | one prayer point every 12 ticks, 66 points a dose (more doses add to it) |
| Divine potions | the base boost, 10 damage (needs 11+ hitpoints), boost held for 500 ticks, then back to base |
| Antipoison 150 ticks, super 600, antifire 600, extended 1200, goading 500 | recorded in `PotionEffects` (`hasPoisonProtection`, `hasDragonfireProtection`, `isGoading`); nothing on the isle poisons or breathes fire yet |

### Details I chose (not from the wiki)
- **Potion cooldown:** 3 ticks, kept apart from food, and a potion adds no attack delay.
- **Speed:** each potion made, each herb mixed and each grind takes 2 ticks. A batch is timed with a
  weak queue (never `delay`), so walking away ends it.
- **Energy potion** restores 10% (my memory of the wiki; the page summary I fetched said 15%).
- **Divine potion experience** is 0.5 a dose, as the two potion pages I read said; this looks low,
  check it against the wiki.
- **Goading duration** (500 ticks) and the lava shard count (3 to 6) are guesses.
- **State is not saved:** stamina, divine holds and timers end on logout (login clears the stamina
  varbit).
- **Antifire and poison:** the effects are only recorded until something uses them.

## League wiring
**Friendly Forager:** the relic's pouch (carried or worn) gives potions one extra dose (four
instead of three) and a 90% chance that the secondary is not used up. It is a `HerbloreMod`
registered by `LeagueHerblore`; other content can register its own.

### 30 tasks added (2,590 points)

| Tier | Tasks |
|------|-------|
| Easy (10) | 391 Clean a Grimy Guam, 392 Make an Attack Potion, 393 Drink a Strength Potion, 1366 Create an Antipoison, 1784 Clean 25 Guam, 1785 Clean 15 Tarromin |
| Medium (30) | 394 Make 30 Prayer Potions, 395 Clean a Grimy Avantoe, 396 Make a 4 dose potion (Forager), 778 Make 20 Stamina Potions, 1046 Make a Combat Potion, 1786 Clean 50 Ranarr, 1788 Clean 50 Cadantine |
| Hard (80) | 397 Saradomin Brew, 777 Make 20 Ranging, 856 Make 20 Magic, 1738 Make 50 Goading, 1739 Make 100 Prayer Regeneration, 1762 Create 25 Divine Super Attack, 1763 Create 25 Divine Super Strength, 1787 Clean 100 Avantoe, 1789 Clean 50 Lantadyme |
| Elite (200) | 399 Super Combat, 974 Extended Antifire, 1380 Create 100 Divine Super Combat, 1381 Create 100 Divine Ranging, 246 Level 99, 247 / 1470 / 1493 25, 35 and 50 million XP |

Area tags are ignored on purpose (the isle has no areas), as with the other skills. Tasks left out:
Compost potion (farming), Guthix rest tea, Ancient, Forgotten and Menaphite brews, Jekyll's trade,
the Degrime spell, herbiboar, the Alchemists' outfit and the Varlamore purchases.

## Left out
Weapon poisons, antidote+/++ and anti-venom (no poison system), barbarian mixes, the Herblore
skillcape emote, the herb sack, the Production Master relic hook, bastion, battlemage, ancient and
armadyl brews, surge, extreme energy, super antifire. No source for secondary ingredients (see
above); Friendly Forager still sends herbs straight to the bank.

## Client checklist
1. Restart the server (new timer `herblore_effects`, queue `herblore_make`, 16 varps).
2. Click a grimy herb: cleaned. Use a vial of water on a herb, then a secondary on the unfinished
   potion: the "What would you like to make?" menu appears with the quantity buttons.
3. Drink a potion: the stat boost shows, the dose item changes, an empty vial after the last dose.
4. Drink a divine potion: 10 damage splat, boost held, gone after five minutes.
5. Stamina: the energy orb drains slowly for two minutes.

## Tests
8 unit tests (recipe table) and 26 integration tests in `content/skills/herblore`, 7 task tests and
3 relic tests in the isle module. Run against a sandbox copy of `.data`, not the real one.
