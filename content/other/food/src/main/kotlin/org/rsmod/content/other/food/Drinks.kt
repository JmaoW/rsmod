package org.rsmod.content.other.food

import org.rsmod.api.config.refs.stats

/** Food the generated table does not have, from the wiki's Food page and the item pages. */
internal object FoodExtras {
    val all: List<FoodDef> =
        listOf(
            // The Tombs of Amascut snack. It is the "Heal" option of the wiki's other table.
            FoodDef(name = "Honey locust", heal = Heal.Fixed(20))
        )
}

/**
 * Everything with a `Drink` option that is not a potion: ales, wine, spirits, cocktails and tea.
 * They are food as far as timing goes, so they use the same cooldown as eating.
 *
 * Effects come from the wiki's pages for each drink. The mature ales, the wines the wiki gives no
 * numbers for and the cocktails only heal, which is a guess made for lack of numbers.
 */
internal object Drinks {
    /** An ale, which comes in a [BEER_GLASS] and leaves one behind. */
    private fun ale(
        name: String,
        heal: Int = 1,
        boosts: List<StatAmount> = emptyList(),
        drains: List<StatAmount> = emptyList(),
        leaves: String? = BEER_GLASS,
    ) =
        FoodDef(
            name = name,
            heal = Heal.Fixed(heal),
            effects = FoodEffects(boosts = boosts, drains = drains),
            leaves = leaves,
            verb = DRINK,
        )

    private fun drink(
        name: String,
        heal: Int,
        leaves: String? = null,
        effects: FoodEffects = FoodEffects.None,
        kind: FoodKind = FoodKind.Standard,
        message: String? = null,
        afterMessage: String? = null,
    ) =
        FoodDef(
            name = name,
            heal = Heal.Fixed(heal),
            effects = effects,
            leaves = leaves,
            kind = kind,
            verb = DRINK,
            message = message,
            afterMessage = afterMessage,
        )

    private fun attack(constant: Int, percent: Int = 0) =
        StatAmount(stats.attack, constant, percent)

    private fun strength(constant: Int, percent: Int = 0) =
        StatAmount(stats.strength, constant, percent)

    private fun defence(constant: Int, percent: Int = 0) =
        StatAmount(stats.defence, constant, percent)

    private val meleeDrain3 = listOf(attack(3), strength(3), defence(3))

    private val ales: List<FoodDef> =
        listOf(
            ale("Beer", boosts = listOf(strength(1, 2)), drains = listOf(attack(1, 6))),
            ale("Grog", heal = 3, boosts = listOf(strength(1, 4)), drains = listOf(attack(3, 5))),
            ale("Asgarnian ale", boosts = listOf(strength(2)), drains = listOf(attack(2, 5))),
            ale("Dragon bitter", boosts = listOf(strength(2)), drains = listOf(attack(2, 5))),
            ale(
                "Greenman's ale",
                boosts = listOf(StatAmount(stats.herblore, 1)),
                drains = meleeDrain3,
            ),
            ale(
                "Wizard's mind bomb",
                boosts = listOf(StatAmount(stats.magic, 2, 2)),
                drains = listOf(attack(1, 5), strength(1, 5), defence(1, 5)),
            ),
            ale(
                "Dwarven stout",
                boosts = listOf(StatAmount(stats.mining, 1), StatAmount(stats.smithing, 1)),
                drains = listOf(attack(2, 4), strength(2, 4), defence(2, 4)),
            ),
            ale(
                "Axeman's folly",
                boosts = listOf(StatAmount(stats.woodcutting, 1)),
                drains = listOf(attack(3), strength(3)),
            ),
            ale(
                "Chef's delight",
                boosts = listOf(StatAmount(stats.cooking, 1, 5)),
                drains = listOf(attack(2, 5), strength(2, 5)),
            ),
            ale(
                "Slayer's respite",
                boosts = listOf(StatAmount(stats.slayer, 2)),
                drains = listOf(attack(2, 2), strength(2, 2)),
            ),
            ale(
                "Cider",
                boosts = listOf(StatAmount(stats.farming, 1)),
                drains = listOf(attack(2), strength(2)),
            ),
            ale(
                "Bandit's brew",
                boosts = listOf(StatAmount(stats.thieving, 1), attack(1)),
                drains = listOf(strength(3, 6), defence(3, 6)),
            ),
            ale("Moonlight mead", heal = 4),
            // The mature ales heal twice as much. The wiki gives no numbers for their stronger
            // effects, except for Slayer's respite, so they keep those of the young ale.
            ale("Moonlight mead(m)", heal = 6),
            ale(
                "Asgarnian ale(m)",
                heal = 2,
                boosts = listOf(strength(2)),
                drains = listOf(attack(2, 5)),
            ),
            ale(
                "Dragon bitter(m)",
                heal = 2,
                boosts = listOf(strength(2)),
                drains = listOf(attack(2, 5)),
            ),
            ale(
                "Greenman's ale(m)",
                heal = 2,
                boosts = listOf(StatAmount(stats.herblore, 1)),
                drains = meleeDrain3,
            ),
            ale(
                "Mature wmb",
                heal = 2,
                boosts = listOf(StatAmount(stats.magic, 2, 2)),
                drains = listOf(attack(1, 5), strength(1, 5), defence(1, 5)),
            ),
            ale(
                "Dwarven stout(m)",
                heal = 2,
                boosts = listOf(StatAmount(stats.mining, 1), StatAmount(stats.smithing, 1)),
                drains = listOf(attack(2, 4), strength(2, 4), defence(2, 4)),
            ),
            ale(
                "Axeman's folly(m)",
                heal = 2,
                boosts = listOf(StatAmount(stats.woodcutting, 1)),
                drains = listOf(attack(3), strength(3)),
            ),
            ale(
                "Chef's delight(m)",
                heal = 2,
                boosts = listOf(StatAmount(stats.cooking, 1, 5)),
                drains = listOf(attack(2, 5), strength(2, 5)),
            ),
            ale(
                "Slayer's respite(m)",
                heal = 2,
                boosts = listOf(StatAmount(stats.slayer, 4)),
                drains = listOf(attack(3, 2), strength(3, 2)),
            ),
            ale(
                "Mature cider",
                heal = 2,
                boosts = listOf(StatAmount(stats.farming, 1)),
                drains = listOf(attack(2), strength(2)),
            ),
            ale("Kelda stout", heal = 1),
            ale("Beer tankard", heal = 4, leaves = TANKARD),
            ale("Light beer", heal = 1),
            ale("Sunbeam ale", heal = 1),
            ale("Kovac's grog", heal = 1),
            ale("Elven dawn", heal = 1, leaves = null),
            ale("Bloody bracer", heal = 2, leaves = null),
        )

    private val wine: List<FoodDef> =
        listOf(
            drink(
                "Jug of wine",
                11,
                leaves = JUG,
                effects = FoodEffects(drains = listOf(attack(2))),
                afterMessage = "It makes you feel a little dizzy.",
            ),
            drink("Half full wine jug", 7, leaves = JUG),
            drink("Bottle of wine", 14, leaves = "Empty wine bottle"),
            drink(
                "Chichilihui ros",
                16,
                effects =
                    FoodEffects(
                        boosts = listOf(StatAmount(stats.herblore, 1)),
                        drains = listOf(attack(5), StatAmount(stats.farming, 1)),
                    ),
            ),
            drink(
                "Ixcoztic white",
                16,
                effects =
                    FoodEffects(
                        boosts = listOf(StatAmount(stats.farming, 1)),
                        drains = listOf(attack(5), StatAmount(stats.herblore, 1)),
                    ),
            ),
            drink(
                "Tonameyo white",
                16,
                effects =
                    FoodEffects(
                        restores = listOf(StatAmount(stats.prayer, 1)),
                        drains = listOf(attack(5), StatAmount(stats.thieving, 1)),
                    ),
            ),
        )

    private val spirits: List<FoodDef> =
        listOf(
            drink("Vodka", 5),
            drink("Whisky", 5),
            drink("Gin", 5),
            drink("Brandy", 5),
            drink("Rum", 5),
            drink("Braindeath 'rum'", 14),
        )

    private val cocktails: List<FoodDef> =
        listOf(
            drink("Premade blurb' sp.", 7, COCKTAIL_GLASS),
            drink("Premade choc s'dy", 5, COCKTAIL_GLASS),
            drink("Premade dr' dragon", 5, COCKTAIL_GLASS),
            drink("Premade fr' blast", 9, COCKTAIL_GLASS),
            drink("Premade p' punch", 9, COCKTAIL_GLASS),
            drink("Premade sgg", 5, COCKTAIL_GLASS),
            drink("Premade wiz blz'd", 5, COCKTAIL_GLASS),
            drink("Pineapple punch", 9, COCKTAIL_GLASS),
            drink("Wizard blizzard", 5, COCKTAIL_GLASS),
            drink("Blurberry special", 7, COCKTAIL_GLASS),
            drink("Choc saturday", 5, COCKTAIL_GLASS),
            drink("Short green guy", 5, COCKTAIL_GLASS),
            drink("Fruit blast", 9, COCKTAIL_GLASS),
            drink("Dirty blast", 9, COCKTAIL_GLASS),
            drink("Drunk dragon", 5, COCKTAIL_GLASS),
        )

    private val others: List<FoodDef> =
        listOf(
            drink(
                "Cup of tea",
                3,
                leaves = "Empty cup",
                effects = FoodEffects(boosts = listOf(attack(2, 2))),
            ),
            drink("Chocolatey milk", 4, leaves = "Bucket"),
            drink(
                "Spring sq'irkjuice",
                0,
                effects =
                    FoodEffects(boosts = listOf(StatAmount(stats.thieving, 1)), runEnergy = 10),
            ),
            drink(
                "Summer sq'irkjuice",
                0,
                effects =
                    FoodEffects(boosts = listOf(StatAmount(stats.thieving, 3)), runEnergy = 20),
            ),
            drink(
                "Autumn sq'irkjuice",
                0,
                effects =
                    FoodEffects(boosts = listOf(StatAmount(stats.thieving, 2)), runEnergy = 15),
            ),
            drink("Winter sq'irkjuice", 0, effects = FoodEffects(runEnergy = 5)),
        )

    val all: List<FoodDef> = ales + wine + spirits + cocktails + others

    private const val DRINK = "drink"
    private const val BEER_GLASS = "Beer glass"
    private const val TANKARD = "Tankard"
    private const val JUG = "Jug"
    private const val COCKTAIL_GLASS = "Cocktail glass"
}
