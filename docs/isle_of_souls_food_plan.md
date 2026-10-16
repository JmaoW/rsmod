# Isle of Souls: food, drink and potions

Built 2026-10-12. The user asked for "the rest of the food": pizzas, cakes, bread, the missing fish,
potatoes, pies, wines and anything else that was left. Before this, `content/other/food` could only
eat the cooked fish, so cooked meat, bread, pies and pizza (which Cooking could already produce)
could not be eaten at all. The user's answers to the scope question:

1. Add the recipes that make the food (dough, pie shells, pizza, cake, stew, curry, potatoes, wine).
2. Drinks: the full set (ales, wine, spirits, cocktails, tea).
3. Hunter meats: eatable and cookable at the same time.
4. Minigame and boss food, especially blighted food, and potions.

## What was added

### Eating (`content/other/food`)

| Group | Notes |
| --- | --- |
| About 350 edible items | 232 foods from the wiki's Food page plus 64 drinks, found **by cache name** (a minigame's own shark, the Barbarian Assault food and the blighted food are eaten like the normal one). |
| Several bites | Cake and chocolate cake (3), pizzas (2), pies (2, leaving a pie dish), giant crab meat (5 bites of one item). The item turns into the next bite and then into whatever it leaves behind (`Pie dish`, `Bowl`, `Jug`, `Beer glass`). |
| Per bite eat delay | The wiki's fast foods: cake 2, 2, 3; members' pies 1, 1; apple, meat and redberry pie and the pizzas 1, 2. Standard food is still 3. |
| Effects | Garden, fish, admiral, wild, summer, mushroom, botanical and dragonfruit pies boost their skills; jangerberries, T-bone, strange fruit (30% run energy), summer pie (10% run energy). |
| Random heals | Snails, slimy and cave eel, spiders, frog spawn, sweets (`Heal.Roll`); strawberry, sweetcorn, watermelon slice heal a percent of the Hitpoints level (`Heal.OfLevel`). |
| Kebabs | Kebab, super kebab and Varlamorian kebab roll one of several outcomes (heal, boost, nothing, drain), with the wiki's weights and messages. |
| Hunter meats | A second heal 7 ticks later (a newer hunter meat replaces a pending one), with 10% run energy for the dashing kebbit. |
| Combo food | Karambwan, gnome food, paddlefish: own 3 tick cooldown, 2 tick attack delay, can follow other food in the same tick. |
| Blighted, Barbarian Assault, Gauntlet, ToA, Chambers of Xeric | Blighted manta ray, anglerfish and karambwan; BA shark/monkfish/karambwan; paddlefish; honey locust; the CoX fish and bats (5 to 23 hitpoints). |
| Drinks | Beer, ales and ciders (young and mature), moonlight mead, grog, jug of wine (-2 Attack), the Moonrise wines, spirits, gnome cocktails, tea, squirk juice, chocolatey milk. |

### Potions (`content/skills/herblore`)

`Potions.all` is still what Herblore makes. `Potions.drinkable` adds `MinigamePotions.all`:

- Copies of Herblore's potions that other content hands out: Barbarian Assault prayer, ranging,
  Sanfew, Saradomin brew, stamina, super combat, super energy, super restore; the Deadman combat
  potion; **blighted super restore and blighted overload**.
- **Overload** (Nightmare Zone and Chambers of Xeric, three strengths): five combat stats boosted and
  held for 5 minutes, refreshed every 25 ticks, 5 hits of 10 in the first ticks, needs more than 50
  Hitpoints, wears off with 50 hitpoints healed back.
- Chambers of Xeric gourd potions: elder, twisted, kodai, revitalisation, prayer enhance, Xeric's aid
  and antipoison, each in weak (-), normal and strong (+). They leave nothing behind.
- Barbarian mixes (29): the base potion's effect plus 3 or 6 hitpoints, two doses, leave a vial.
- Super ranging, super magic, bastion, battlemage (and divine versions), magic essence.
- Egniol (Gauntlet), Sanfew serum, antidote+ and ++, anti-venom and +, ancient brew, forgotten
  brew, Menaphite remedy (restores combat stats every 25 ticks for 500), Guthix rest, nectar and
  ambrosia (Tombs of Amascut).

Not done: absorption, Tears of Elidinis, liquid adrenaline, surge, the Hunter mixes, the Wintertodt
rejuvenation potion, Soul Wars potion of power, goblin potion, Castle Wars brew.

### Cooking (`content/skills/cooking`)

- **54 more cooked foods** (`CookingExtras`, generated): manta ray, sea turtle, dark crab, lava, cave
  and slimy eel, rainbow fish, ugthanki meat, snails, chompy, bird and beast meat (with the iron spit
  handed back), **all nine Hunter meats**, baked potato, sweetcorn, scrambled egg, fried onions and
  mushrooms, stew, curry, cake (the tin comes back), the members' pies, the Chambers of Xeric fish
  and bats. Levels, experience and success charts are the wiki's.
- **Preparation** (`Preparation`, `CookingPrep`): flour and water make dough, pastry dough and pizza
  base (35); pie shells and every pie in steps; pizza and its toppings; uncooked cake and chocolate
  cake; incomplete stew, stew, curry; the potato toppings and the bowls that go on them; **wine**:
  grapes in a jug of water ferment 20 ticks later (all unfermented wine at once, 200 experience
  each, 15 batches rolled together, chance from 19% to 138%), failures are bad wine.
  Using two items on each other offers a menu, then one item every 2 ticks.

## Decisions and guesses

- **Heal values, bites, delays, effects and messages** come from tsps (BSD-2, see
  `tools/food/README.md`), which took them from the wiki and recordings of the live game.
- **Karambwan and other combo food** now wait 3 ticks before the next one (was 2).
- **Blighted food** can be eaten anywhere. The Wilderness restriction is left out.
- **Poison:** strange fruit and moonlight antelope should cure it, and strange fruit gives
  immunity. `PlayerPoison` lives in the isle module, which depends on food, so nothing here cures it.
  Antipoison and antivenom potions work through `PotionEffects.hasPoisonProtection` as before.
- **Mature ales** heal twice as much but keep the young ale's boost and drain (the wiki gives no
  numbers). Spirits, the gnome cocktails and most wines only heal.
- **Nectar** lowers the offensive stats like a Saradomin brew; the wiki says it lowers them without
  giving the amount.
- **Chambers of Xeric potions** can be drunk anywhere. Overload ends by healing 50 (tsps).
- **Mixes** use the potion cooldown, not the food cooldown the real game uses.
- **Fire/range success numbers** for food the wiki has no chart for (most Hunter meats, eels) are
  made up from the level the wiki says it stops burning at: 55% when it can first be cooked,
  rising in a line to 100% there. `tools/food/gen_cooking.py` has the formula.
- **Wine:** the wiki says rolls come in 15 batches; this does the same. The "Fountain (wine)" and
  the dairy churn, windmill and flour recipes are left out (no churn or mill on the isle).
- A **latent bug** was fixed on the way: `statHeal` throws when a stat is above its base level, so
  drinking a restore potion while boosted (or a prayer regeneration tick above base) could throw.
  Drinking now skips stats that are already full.

## Needs a restart and `packCache`

Three new symbols (all in `.data/symbols`): timer `25 food_delayed_heal`, timer `26 cooking_wine`
and queue `37 cooking_prepare`. Nothing else in the cache changes. **Not tried in a real client.**

## Code

- `content/other/food`: `Eating`, `FoodConfigs` (the model), `FoodData` (generated), `Drinks`,
  `FoodIndex`, `Foods`. Tests: `FoodsTest`, `EatingTest`, `FoodVarietyTest`.
- `content/skills/herblore`: `MinigamePotions`, `MinigamePotionObjs` (generated), `PotionEffects`
  (overload, prayer enhance, Menaphite remedy), `Drinking`. Tests: `MinigamePotionsTest`.
- `content/skills/cooking`: `CookingExtras` (generated), `CookingPrep`, `Preparation`. Tests:
  `PreparationTest`, `CookingFoodsTest`.
- `tools/food`: the generators and checks, see its README. `check_food.py` lists the names the cache
  does not have; the 18 it prints (Sailing fish, the Varlamorian crab meats, the berries without an
  Eat option) are skipped when the server starts.
- Event for league tasks: `Eating.Eaten(player, obj, healed)` and `Preparation.Prepared`.

## Left out

Gnome restaurant dishes (the food is eatable, the cooking is not), butter, cheese and cream, flour,
sandwiches, poison karambwan, the Moons of Peril food (bream, moss lizard), Halloween candy and
Easter sweets, rock cakes, the Servery and Vampyrium food, kegs of ale.
