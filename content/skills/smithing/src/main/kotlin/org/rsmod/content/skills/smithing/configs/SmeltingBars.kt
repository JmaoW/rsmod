package org.rsmod.content.skills.smithing.configs

import org.rsmod.game.type.obj.ObjType

/** An ore, and how many of it a bar needs. */
internal class SmeltingIngredient(val ore: ObjType, val count: Int)

/**
 * A bar that can be smelted at a furnace.
 *
 * @property successChance the chance (from `0.0` to `1.0`) that smelting works. Only iron fails.
 * @property oreNames what the player is told they put in, such as `copper and tin`.
 * @property noun what the player is told they got, such as `bronze`.
 */
internal class SmeltingBar(
    val bar: ObjType,
    val level: Int,
    val xp: Double,
    val ingredients: List<SmeltingIngredient>,
    val oreNames: String,
    val noun: String,
    val successChance: Double = 1.0,
)

internal object SmeltingBars {
    /** The bars a furnace can make, with the level and xp values from the wiki. */
    val all: List<SmeltingBar> =
        listOf(
            SmeltingBar(
                SmithingObjs.bronze_bar,
                level = 1,
                xp = 6.2,
                ingredients =
                    listOf(
                        SmeltingIngredient(SmithingObjs.copper_ore, 1),
                        SmeltingIngredient(SmithingObjs.tin_ore, 1),
                    ),
                oreNames = "copper and tin",
                noun = "bronze",
            ),
            SmeltingBar(
                SmithingObjs.iron_bar,
                level = 15,
                xp = 12.5,
                ingredients = listOf(SmeltingIngredient(SmithingObjs.iron_ore, 1)),
                oreNames = "iron",
                noun = "iron",
                successChance = 0.5,
            ),
            SmeltingBar(
                SmithingObjs.silver_bar,
                level = 20,
                xp = 13.7,
                ingredients = listOf(SmeltingIngredient(SmithingObjs.silver_ore, 1)),
                oreNames = "silver",
                noun = "silver",
            ),
            SmeltingBar(
                SmithingObjs.steel_bar,
                level = 30,
                xp = 17.5,
                ingredients =
                    listOf(
                        SmeltingIngredient(SmithingObjs.iron_ore, 1),
                        SmeltingIngredient(SmithingObjs.coal, 2),
                    ),
                oreNames = "iron and coal",
                noun = "steel",
            ),
            SmeltingBar(
                SmithingObjs.gold_bar,
                level = 40,
                xp = 22.5,
                ingredients = listOf(SmeltingIngredient(SmithingObjs.gold_ore, 1)),
                oreNames = "gold",
                noun = "gold",
            ),
            SmeltingBar(
                SmithingObjs.mithril_bar,
                level = 50,
                xp = 30.0,
                ingredients =
                    listOf(
                        SmeltingIngredient(SmithingObjs.mithril_ore, 1),
                        SmeltingIngredient(SmithingObjs.coal, 4),
                    ),
                oreNames = "mithril and coal",
                noun = "mithril",
            ),
            SmeltingBar(
                SmithingObjs.adamantite_bar,
                level = 70,
                xp = 37.5,
                ingredients =
                    listOf(
                        SmeltingIngredient(SmithingObjs.adamantite_ore, 1),
                        SmeltingIngredient(SmithingObjs.coal, 6),
                    ),
                oreNames = "adamantite and coal",
                noun = "adamantite",
            ),
            SmeltingBar(
                SmithingObjs.runite_bar,
                level = 85,
                xp = 50.0,
                ingredients =
                    listOf(
                        SmeltingIngredient(SmithingObjs.runite_ore, 1),
                        SmeltingIngredient(SmithingObjs.coal, 8),
                    ),
                oreNames = "runite and coal",
                noun = "runite",
            ),
        )

    /**
     * The recipe whose bar a single [ore] turns into when it is smelted without a furnace, or
     * `null` if [ore] is not an ore. Every ore gives a bar on its own: copper and tin give bronze,
     * coal gives steel, and the ores that need coal at a furnace (mithril, adamantite and runite)
     * do not need it here.
     */
    fun autoSmelt(ore: ObjType): SmeltingBar? {
        // Compared by id: the ore of a game event is the unpacked type, not the reference.
        val id = ore.id
        val bar =
            when (id) {
                SmithingObjs.copper_ore.id,
                SmithingObjs.tin_ore.id -> SmithingObjs.bronze_bar
                SmithingObjs.iron_ore.id -> SmithingObjs.iron_bar
                SmithingObjs.coal.id -> SmithingObjs.steel_bar
                SmithingObjs.silver_ore.id -> SmithingObjs.silver_bar
                SmithingObjs.gold_ore.id -> SmithingObjs.gold_bar
                SmithingObjs.mithril_ore.id -> SmithingObjs.mithril_bar
                SmithingObjs.adamantite_ore.id -> SmithingObjs.adamantite_bar
                SmithingObjs.runite_ore.id -> SmithingObjs.runite_bar
                else -> return null
            }
        return all.first { it.bar === bar }
    }

    /** Whether [obj] is an ore (or coal) that some bar needs. */
    fun isIngredient(obj: ObjType): Boolean =
        all.any { recipe -> recipe.ingredients.any { it.ore.id == obj.id } }
}
