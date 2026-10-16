package org.rsmod.content.other.food

internal object Foods {
    /** The wiki's formula: `floor(x / 10) + 2 * floor(x / 25) + 5 * floor(x / 93) + 2`. */
    fun anglerfishHeal(baseHitpoints: Int): Int =
        baseHitpoints / 10 + 2 * (baseHitpoints / 25) + 5 * (baseHitpoints / 93) + 2
}

/** Every food and drink that can be eaten, the generated table first and then the drinks. */
internal object FoodTable {
    val all: List<FoodDef> = FoodData.all + FoodExtras.all + Drinks.all
}
