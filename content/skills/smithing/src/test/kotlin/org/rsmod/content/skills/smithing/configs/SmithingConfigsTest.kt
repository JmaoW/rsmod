package org.rsmod.content.skills.smithing.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.smithing.scripts.Smithing
import org.rsmod.game.type.obj.ObjType

class SmithingConfigsTest {
    private fun product(metal: SmithingMetal, slot: Int): SmithingProduct =
        SmithingProducts.find(metal, slot) ?: error("No product for $metal in slot $slot.")

    @Test
    fun `every product fits an interface button`() {
        for (product in SmithingProducts.all) {
            assertTrue(product.slot in SmithingComponents.slots.indices) { "Slot of $product." }
        }
    }

    @Test
    fun `no metal has two products in one button`() {
        val keys = SmithingProducts.all.map { it.metal to it.slot }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `every metal has the standard items`() {
        for (metal in SmithingMetal.entries) {
            for (slot in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18)) {
                assertNotNull(SmithingProducts.find(metal, slot)) { "$metal slot $slot" }
            }
        }
        assertEquals(156, SmithingProducts.all.size)
    }

    private class Expected(
        val metal: SmithingMetal,
        val slot: Int,
        val level: Int,
        val bars: Int,
        val xp: Double,
    )

    @Test
    fun `levels bars and xp match the wiki`() {
        val expected =
            listOf(
                Expected(SmithingMetal.Bronze, 0, 1, 1, 12.5),
                Expected(SmithingMetal.Bronze, 5, 1, 1, 12.5),
                Expected(SmithingMetal.Bronze, 13, 18, 5, 62.5),
                Expected(SmithingMetal.Iron, 0, 15, 1, 25.0),
                Expected(SmithingMetal.Iron, 13, 33, 5, 125.0),
                Expected(SmithingMetal.Steel, 4, 44, 3, 112.5),
                Expected(SmithingMetal.Mithril, 13, 68, 5, 250.0),
                Expected(SmithingMetal.Adamant, 12, 86, 3, 187.5),
                Expected(SmithingMetal.Rune, 0, 85, 1, 75.0),
                Expected(SmithingMetal.Rune, 13, 99, 5, 375.0),
            )
        for (row in expected) {
            val product = product(row.metal, row.slot)
            val where = "${row.metal} slot ${row.slot}"
            assertEquals(row.level, product.level, "level of $where")
            assertEquals(row.bars, product.bars, "bars of $where")
            assertEquals(row.xp, product.xp, 0.001, "xp of $where")
        }
    }

    @Test
    fun `stackable items come in the wiki quantities`() {
        assertEquals(15, product(SmithingMetal.Bronze, 14).quantity) // nails
        assertEquals(10, product(SmithingMetal.Iron, 20).quantity) // dart tips
        assertEquals(15, product(SmithingMetal.Steel, 21).quantity) // arrowtips
        assertEquals(5, product(SmithingMetal.Mithril, 22).quantity) // knives
        assertEquals(10, product(SmithingMetal.Rune, 25).quantity) // bolts
        assertEquals(1, product(SmithingMetal.Rune, 13).quantity)
    }

    @Test
    fun `anvil metals map to the client bar types`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), SmithingMetal.entries.map { it.barType })
    }

    @Test
    fun `smelting levels and xp match the wiki`() {
        // Identity lookups: unresolved refs all compare equal in plain unit tests.
        val expected =
            listOf(
                Triple(SmithingObjs.bronze_bar, 1, 6.2),
                Triple(SmithingObjs.iron_bar, 15, 12.5),
                Triple(SmithingObjs.silver_bar, 20, 13.7),
                Triple(SmithingObjs.steel_bar, 30, 17.5),
                Triple(SmithingObjs.gold_bar, 40, 22.5),
                Triple(SmithingObjs.mithril_bar, 50, 30.0),
                Triple(SmithingObjs.adamantite_bar, 70, 37.5),
                Triple(SmithingObjs.runite_bar, 85, 50.0),
            )
        assertEquals(expected.size, SmeltingBars.all.size)
        for ((bar, level, xp) in expected) {
            val recipe = SmeltingBars.all.first { it.bar === bar }
            assertEquals(level, recipe.level)
            assertEquals(xp, recipe.xp, 0.001)
        }
    }

    private fun coalFor(bar: ObjType): Int {
        val recipe = SmeltingBars.all.first { it.bar === bar }
        return recipe.ingredients.firstOrNull { it.ore === SmithingObjs.coal }?.count ?: 0
    }

    @Test
    fun `coal counts match the wiki`() {
        assertEquals(0, coalFor(SmithingObjs.bronze_bar))
        assertEquals(2, coalFor(SmithingObjs.steel_bar))
        assertEquals(4, coalFor(SmithingObjs.mithril_bar))
        assertEquals(6, coalFor(SmithingObjs.adamantite_bar))
        assertEquals(8, coalFor(SmithingObjs.runite_bar))
    }

    @Test
    fun `only iron can fail`() {
        for (recipe in SmeltingBars.all) {
            val expected = if (recipe.bar === SmithingObjs.iron_bar) 0.5 else 1.0
            assertEquals(expected, recipe.successChance)
        }
    }

    @Test
    fun `chat description of a smithed item`() {
        assertEquals("a dagger", Smithing.describe(product(SmithingMetal.Bronze, 0)))
        assertEquals("an axe", Smithing.describe(product(SmithingMetal.Bronze, 5)))
        assertEquals("15 nails", Smithing.describe(product(SmithingMetal.Bronze, 14)))
        assertEquals("some plate legs", Smithing.describe(product(SmithingMetal.Bronze, 11)))
        assertEquals("a plate body", Smithing.describe(product(SmithingMetal.Bronze, 13)))
    }
}
