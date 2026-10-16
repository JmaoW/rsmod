package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.game.type.obj.ObjType

/** An ore, and how many of it a superheated bar needs. */
class SuperheatIngredient(val ore: ObjType, val count: Int)

/**
 * A bar the Superheat Item spell can make. The spell works like a furnace: it gives the same
 * Smithing experience and needs the same Smithing level (the wiki's values), but it never fails,
 * not even for iron.
 */
class SuperheatRecipe(
    val bar: ObjType,
    val level: Int,
    val xp: Double,
    val ingredients: List<SuperheatIngredient>,
)

object SuperheatRecipes {
    val all: List<SuperheatRecipe> =
        listOf(
            recipe(
                utility_objs.bronze_bar,
                1,
                6.2,
                utility_objs.copper_ore to 1,
                utility_objs.tin_ore to 1,
            ),
            recipe(utility_objs.iron_bar, 15, 12.5, utility_objs.iron_ore to 1),
            recipe(utility_objs.silver_bar, 20, 13.7, utility_objs.silver_ore to 1),
            recipe(
                utility_objs.steel_bar,
                30,
                17.5,
                utility_objs.iron_ore to 1,
                utility_objs.coal to 2,
            ),
            recipe(utility_objs.gold_bar, 40, 22.5, utility_objs.gold_ore to 1),
            recipe(
                utility_objs.mithril_bar,
                50,
                30.0,
                utility_objs.mithril_ore to 1,
                utility_objs.coal to 4,
            ),
            recipe(
                utility_objs.adamantite_bar,
                70,
                37.5,
                utility_objs.adamantite_ore to 1,
                utility_objs.coal to 6,
            ),
            recipe(
                utility_objs.runite_bar,
                85,
                50.0,
                utility_objs.runite_ore to 1,
                utility_objs.coal to 8,
            ),
        )

    /**
     * The recipes that use [ore], the ones needing the most ingredients first, so that iron ore
     * becomes steel when there is coal to go with it and an iron bar when there is not.
     */
    fun using(ore: ObjType): List<SuperheatRecipe> =
        all.filter { recipe -> recipe.ingredients.any { it.ore.id == ore.id } }
            .sortedByDescending { it.ingredients.sumOf(SuperheatIngredient::count) }

    private fun recipe(
        bar: ObjType,
        level: Int,
        xp: Double,
        vararg ingredients: Pair<ObjType, Int>,
    ) =
        SuperheatRecipe(
            bar,
            level,
            xp,
            ingredients.map { SuperheatIngredient(it.first, it.second) },
        )
}
