package org.rsmod.content.skills.fletching.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

class FletchingRecipesTest {
    private val o = FletchingObjs

    private fun recipe(product: ObjType, vararg inputs: ObjType): FletchingRecipe =
        FletchingRecipes.all.first { recipe ->
            recipe.product.internalNameValue == product.internalNameValue &&
                inputs.all { input ->
                    recipe.inputs.any { it.obj.internalNameValue == input.internalNameValue }
                }
        }

    @Test
    fun `bows match the wiki`() {
        val expected =
            listOf(
                Triple(o.shortbow_u, 5, 5.0),
                Triple(o.longbow_u, 10, 10.0),
                Triple(o.oak_shortbow_u, 20, 16.5),
                Triple(o.oak_longbow_u, 25, 25.0),
                Triple(o.willow_shortbow_u, 35, 33.3),
                Triple(o.willow_longbow_u, 40, 41.5),
                Triple(o.maple_shortbow_u, 50, 50.0),
                Triple(o.maple_longbow_u, 55, 58.3),
                Triple(o.yew_shortbow_u, 65, 67.5),
                Triple(o.yew_longbow_u, 70, 75.0),
                Triple(o.magic_shortbow_u, 80, 83.3),
                Triple(o.magic_longbow_u, 85, 91.5),
            )
        for ((bow, level, xp) in expected) {
            val recipe = recipe(bow)
            assertEquals(level, recipe.level)
            assertEquals(xp, recipe.xp)
            assertEquals(1, recipe.output)
        }
    }

    @Test
    fun `arrow shafts give more per log for better logs`() {
        val expected =
            listOf(
                listOf(o.logs, 1, 15, 5.0),
                listOf(o.oak_logs, 15, 30, 10.0),
                listOf(o.willow_logs, 30, 45, 15.0),
                listOf(o.maple_logs, 45, 60, 20.0),
                listOf(o.yew_logs, 60, 75, 25.0),
                listOf(o.magic_logs, 75, 90, 30.0),
                listOf(o.redwood_logs, 90, 105, 35.0),
            )
        for (row in expected) {
            val logs = row[0] as ObjType
            val recipe = recipe(o.arrow_shaft, logs)
            assertEquals(row[1], recipe.level)
            assertEquals(row[2], recipe.output)
            assertEquals(row[3], recipe.xp)
        }
    }

    @Test
    fun `crossbows pair each stock with its limbs and string for half the experience`() {
        val expected =
            listOf(
                listOf(o.wooden_stock, o.bronze_limbs, o.bronze_crossbow_u, 9, 12.0),
                listOf(o.willow_stock, o.iron_limbs, o.iron_crossbow_u, 39, 44.0),
                listOf(o.teak_stock, o.steel_limbs, o.steel_crossbow_u, 46, 54.0),
                listOf(o.maple_stock, o.mithril_limbs, o.mithril_crossbow_u, 54, 64.0),
                listOf(o.mahogany_stock, o.adamant_limbs, o.adamant_crossbow_u, 61, 82.0),
                listOf(o.yew_stock, o.rune_limbs, o.rune_crossbow_u, 69, 100.0),
            )
        for (row in expected) {
            val unstrung = recipe(row[2] as ObjType, row[0] as ObjType, row[1] as ObjType)
            assertEquals(row[3], unstrung.level)
            assertEquals(row[4], unstrung.xp)
            assertEquals(
                listOf(o.hammer.internalNameValue),
                unstrung.tools.map { it.internalNameValue },
            )

            val strung =
                FletchingRecipes.all.first { recipe ->
                    recipe.inputs.any {
                        it.obj.internalNameValue == (row[2] as ObjType).internalNameValue
                    } &&
                        recipe.inputs.any {
                            it.obj.internalNameValue == o.crossbow_string.internalNameValue
                        }
                }
            assertEquals(row[3], strung.level)
            assertEquals((row[4] as Double) / 2, strung.xp)
        }
    }

    @Test
    fun `ammo is made in the usual batches`() {
        assertEquals(15, recipe(o.headless_arrow, o.arrow_shaft, o.feather).maxUnits)
        assertEquals(15, recipe(o.iron_arrow, o.headless_arrow, o.iron_arrowheads).maxUnits)
        assertEquals(15, recipe(o.steel_javelin, o.javelin_shaft, o.steel_javelin_head).maxUnits)
        assertEquals(10, recipe(o.mithril_dart, o.mithril_dart_tip, o.feather).maxUnits)
        assertEquals(10, recipe(o.rune_bolts, o.rune_bolts_unf, o.feather).maxUnits)
        assertEquals(10, recipe(o.ruby_bolts, o.ruby_bolt_tips, o.adamant_bolts).maxUnits)
    }

    @Test
    fun `a gem cuts into a dozen tips and an onyx into two dozen`() {
        assertEquals(12, recipe(o.sapphire_bolt_tips, o.sapphire).output)
        assertEquals(24, recipe(o.onyx_bolt_tips, o.onyx).output)
    }

    @Test
    fun `the high level items match the wiki`() {
        val expected =
            listOf(
                Triple(o.broad_arrow, 52, 10.0),
                Triple(o.amethyst_arrow, 82, 13.5),
                Triple(o.dragon_arrow, 90, 15.0),
                Triple(o.amethyst_javelin, 84, 13.5),
                Triple(o.dragon_javelin, 92, 15.0),
                Triple(o.amethyst_dart, 90, 21.0),
                Triple(o.dragon_dart, 95, 25.0),
                Triple(o.blurite_bolts, 24, 1.0),
                Triple(o.silver_bolts, 43, 2.5),
                Triple(o.broad_bolts, 55, 3.0),
                Triple(o.dragon_bolts, 84, 12.0),
                Triple(o.amethyst_broad_bolts, 76, 10.6),
                Triple(o.barbed_bolts, 51, 9.5),
                Triple(o.magic_stock, 78, 70.0),
            )
        for ((product, level, xp) in expected) {
            val recipe = recipe(product)
            assertEquals(level, recipe.level, product.internalNameValue)
            assertEquals(xp, recipe.xp, product.internalNameValue)
        }
    }

    @Test
    fun `blurite and dragon crossbows use their own stock and limbs`() {
        val blurite = recipe(o.blurite_crossbow_u, o.oak_stock, o.blurite_limbs)
        assertEquals(24, blurite.level)
        assertEquals(32.0, blurite.xp)

        val dragon = recipe(o.dragon_crossbow_u, o.magic_stock, o.dragon_limbs)
        assertEquals(78, dragon.level)
        assertEquals(135.0, dragon.xp)
        assertEquals(listOf(o.hammer.internalNameValue), dragon.tools.map { it.internalNameValue })

        val strung = recipe(o.dragon_crossbow, o.dragon_crossbow_u, o.crossbow_string)
        assertEquals(70.0, strung.xp)
    }

    @Test
    fun `pearls make tips by the pearl and onyx makes two dozen`() {
        assertEquals(6, recipe(o.pearl_bolt_tips, o.oyster_pearl).output)
        assertEquals(24, recipe(o.pearl_bolt_tips, o.oyster_pearls).output)
        assertEquals(12, recipe(o.jade_bolt_tips, o.jade).output)
    }

    @Test
    fun `every tip goes on dragon bolts at level 84`() {
        val tipped =
            listOf(
                o.opal_dragon_bolts,
                o.jade_dragon_bolts,
                o.pearl_dragon_bolts,
                o.topaz_dragon_bolts,
                o.sapphire_dragon_bolts,
                o.emerald_dragon_bolts,
                o.ruby_dragon_bolts,
                o.diamond_dragon_bolts,
                o.dragonstone_dragon_bolts,
                o.onyx_dragon_bolts,
            )
        for (bolts in tipped) {
            val recipe = recipe(bolts, o.dragon_bolts)
            assertEquals(84, recipe.level)
            assertEquals(10, recipe.maxUnits)
        }
    }

    @Test
    fun `any coloured feather works in place of a plain one`() {
        val plain = FletchingRecipes.offered(o.arrow_shaft, o.feather)
        assertEquals(
            listOf(o.headless_arrow.internalNameValue),
            plain.map { it.product.internalNameValue },
        )
        for (feather in
            listOf(
                o.stripy_feather,
                o.red_feather,
                o.blue_feather,
                o.orange_feather,
                o.yellow_feather,
            )) {
            val offered = FletchingRecipes.offered(feather, o.arrow_shaft)
            assertEquals(
                plain.map { it.product.internalNameValue },
                offered.map { it.product.internalNameValue },
            )
            assertTrue(FletchingRecipes.offered(o.dragon_dart_tip, feather).isNotEmpty())
            assertTrue(FletchingRecipes.offered(o.dragon_bolts_unf, feather).isNotEmpty())
        }
    }

    @Test
    fun `each product is made one way per trigger`() {
        val keys =
            FletchingRecipes.all.map {
                Triple(
                    it.trigger.first.internalNameValue,
                    it.trigger.second.internalNameValue,
                    it.product.internalNameValue,
                )
            }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every pair of objs is only listed once whichever way round`() {
        val pairs =
            FletchingRecipes.triggers().map {
                setOf(it.first.internalNameValue, it.second.internalNameValue)
            }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `a pair offers every recipe made from it in either order`() {
        val forward = FletchingRecipes.offered(o.knife, o.oak_logs)
        val backward = FletchingRecipes.offered(o.oak_logs, o.knife)
        assertEquals(
            forward.map { it.product.internalNameValue },
            backward.map { it.product.internalNameValue },
        )
        // Arrow shafts, a shortbow, a longbow, a stock and a shield.
        assertEquals(5, forward.size)
    }

    @Test
    fun `no recipe asks for more than a stack of one ingredient`() {
        for (recipe in FletchingRecipes.all) {
            assertTrue(recipe.inputs.size in 1..2, "Unexpected inputs for ${recipe.product}")
            assertTrue(recipe.maxUnits >= 1)
            assertTrue(recipe.level in 1..99)
        }
    }
}
