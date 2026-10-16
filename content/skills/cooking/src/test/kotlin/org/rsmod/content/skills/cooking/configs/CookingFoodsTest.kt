package org.rsmod.content.skills.cooking.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

class CookingFoodsTest {
    private fun food(raw: ObjType): CookableFood = CookingFoods.all.first { it.raw === raw }

    @Test
    fun `levels and xp match the wiki`() {
        val expected =
            listOf(
                CookingObjs.raw_beef to listOf(1, 30),
                CookingObjs.raw_chicken to listOf(1, 30),
                CookingObjs.raw_rabbit to listOf(1, 30),
                CookingObjs.raw_shrimp to listOf(1, 30),
                CookingObjs.raw_anchovies to listOf(1, 30),
                CookingObjs.raw_sardine to listOf(1, 40),
                CookingObjs.raw_herring to listOf(5, 50),
                CookingObjs.raw_mackerel to listOf(10, 60),
                CookingObjs.raw_trout to listOf(15, 70),
                CookingObjs.raw_cod to listOf(18, 75),
                CookingObjs.raw_pike to listOf(20, 80),
                CookingObjs.raw_salmon to listOf(25, 90),
                CookingObjs.raw_tuna to listOf(30, 100),
                CookingObjs.raw_karambwan to listOf(30, 190),
                CookingObjs.raw_lobster to listOf(40, 120),
                CookingObjs.raw_bass to listOf(43, 130),
                CookingObjs.raw_swordfish to listOf(45, 140),
                CookingObjs.raw_monkfish to listOf(62, 150),
                CookingObjs.raw_shark to listOf(80, 210),
                CookingObjs.raw_anglerfish to listOf(84, 230),
                CookingObjs.bread_dough to listOf(1, 40),
                CookingObjs.uncooked_redberry_pie to listOf(10, 78),
                CookingObjs.uncooked_meat_pie to listOf(20, 110),
                CookingObjs.uncooked_apple_pie to listOf(30, 130),
                CookingObjs.uncooked_pizza to listOf(35, 143),
            )
        assertTrue(CookingFoods.all.size >= expected.size)
        for ((raw, values) in expected) {
            val food = food(raw)
            assertEquals(values, listOf(food.level, food.xp.toInt()))
        }
    }

    @Test
    fun `fire stop burning levels match the wiki`() {
        val expected =
            listOf(
                CookingObjs.raw_beef to 34,
                CookingObjs.raw_chicken to 34,
                CookingObjs.raw_rabbit to 34,
                CookingObjs.raw_shrimp to 34,
                CookingObjs.raw_anchovies to 34,
                CookingObjs.raw_sardine to 38,
                CookingObjs.raw_herring to 41,
                CookingObjs.raw_mackerel to 45,
                CookingObjs.raw_trout to 49,
                CookingObjs.raw_cod to 51,
                CookingObjs.raw_pike to 54,
                CookingObjs.raw_salmon to 58,
                CookingObjs.raw_tuna to 63,
                CookingObjs.raw_lobster to 74,
                CookingObjs.raw_bass to 80,
                CookingObjs.raw_swordfish to 86,
                CookingObjs.raw_monkfish to 92,
            )
        for ((raw, level) in expected) {
            assertEquals(level, food(raw).stopBurnLevel(HeatSource.Fire), "Fire level for $raw.")
        }
    }

    @Test
    fun `range stop burning levels match the wiki`() {
        val expected =
            listOf(
                CookingObjs.raw_cod to 49,
                CookingObjs.raw_swordfish to 80,
                // The wiki lists 90, but its own range values for monkfish reach 100% at 91.
                CookingObjs.raw_monkfish to 91,
                CookingObjs.bread_dough to 38,
                CookingObjs.uncooked_redberry_pie to 45,
                CookingObjs.uncooked_meat_pie to 54,
                CookingObjs.uncooked_apple_pie to 63,
                CookingObjs.uncooked_pizza to 68,
            )
        for ((raw, level) in expected) {
            assertEquals(level, food(raw).stopBurnLevel(HeatSource.Range), "Range level for $raw.")
        }
    }

    @Test
    fun `foods that can always burn never stop burning`() {
        assertNull(food(CookingObjs.raw_shark).stopBurnLevel(HeatSource.Fire))
        assertNull(food(CookingObjs.raw_shark).stopBurnLevel(HeatSource.Range))
        assertNull(food(CookingObjs.raw_anglerfish).stopBurnLevel(HeatSource.Fire))
        assertNull(food(CookingObjs.raw_karambwan).stopBurnLevel(HeatSource.Fire))
        assertTrue(food(CookingObjs.raw_karambwan).range.chance(99) > 0.99)
    }

    @Test
    fun `baked goods cannot be cooked on a fire`() {
        for (raw in
            listOf(
                CookingObjs.bread_dough,
                CookingObjs.uncooked_redberry_pie,
                CookingObjs.uncooked_meat_pie,
                CookingObjs.uncooked_apple_pie,
                CookingObjs.uncooked_pizza,
            )) {
            assertNull(food(raw).fire)
            assertNotNull(food(raw).range)
            assertEquals("bake", food(raw).verb)
        }
        assertNotNull(food(CookingObjs.raw_shrimp).fire)
    }

    @Test
    fun `success chance rises with level`() {
        for (food in CookingFoods.all) {
            val rate = food.range
            var previous = 0.0
            for (level in food.level..99) {
                val chance = rate.chance(level)
                assertTrue(chance >= previous, "Chance dropped for ${food.raw} at level $level.")
                assertTrue(chance in 0.0..1.0)
                previous = chance
            }
        }
    }

    @Test
    fun `range is never worse than a fire`() {
        // The wiki's numbers for a few of the extra foods (ugthanki meat) say otherwise.
        for (food in CookingFoods.all.filter { it !in CookingExtras.all }) {
            val fire = food.fire ?: continue
            for (level in food.level..99) {
                assertTrue(food.range.chance(level) >= fire.chance(level))
            }
        }
    }

    @Test
    fun `no raw food is listed twice`() {
        val duplicates =
            CookingFoods.all.groupBy { it.raw.toString() }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Listed twice: $duplicates")
    }

    @Test
    fun `foods without a success chart cannot burn once they stop burning`() {
        for (food in CookingFoods.all) {
            val stop = food.stopBurnLevel(HeatSource.Range)
            if (stop != null) {
                assertTrue(stop >= food.level, "${food.raw} stops burning before it can be cooked")
            }
        }
    }

    @Test
    fun `the extra foods keep the wiki's level and experience`() {
        val rows = CookingExtras.all.map { it.level to it.xp.toInt() }
        assertTrue(rows.contains(91 to 216), "Manta ray")
        assertTrue(rows.contains(51 to 143), "Kyatt")
        assertTrue(rows.contains(7 to 15), "Baked potato")
        assertTrue(rows.contains(40 to 180), "Cake")
        assertTrue(rows.contains(25 to 117), "Stew")
        assertTrue(rows.contains(60 to 280), "Curry")
    }

    @Test
    fun `a cake leaves its tin behind`() {
        val cakes = CookingExtras.all.filter { it.leaves != null }
        assertTrue(cakes.any { it.level == 40 })
    }

    @Test
    fun `preparation recipes are made of named items`() {
        for (recipe in PrepRecipes.all) {
            assertTrue(recipe.inputs.isNotEmpty())
            assertTrue(recipe.outputs.isNotEmpty())
            assertTrue(recipe.level in 1..99)
        }
    }
}
