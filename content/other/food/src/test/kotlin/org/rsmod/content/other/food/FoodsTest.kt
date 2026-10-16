package org.rsmod.content.other.food

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FoodsTest {
    @Test
    fun `anglerfish heal scales with hitpoints level`() {
        val expected =
            mapOf(
                10 to 3,
                20 to 4,
                25 to 6,
                40 to 8,
                50 to 11,
                70 to 13,
                80 to 16,
                92 to 17,
                99 to 22,
            )
        for ((level, heal) in expected) {
            assertEquals(heal, Foods.anglerfishHeal(level), "Hitpoints level $level")
        }
    }

    @Test
    fun `no food is in the table twice`() {
        val names = FoodTable.all.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Foods listed twice: $duplicates")
    }

    @Test
    fun `every food has a heal, outcomes or a refusal`() {
        for (food in FoodTable.all) {
            val has = food.heal != null || food.outcomes.isNotEmpty() || food.refuse != null
            assertTrue(has || !food.effects.isEmpty(), "${food.name} does nothing")
        }
    }

    @Test
    fun `per bite settings match the number of bites`() {
        for (food in FoodTable.all) {
            assertTrue(food.eatDelays.size <= food.biteCount, "${food.name} has too many delays")
            assertTrue(food.messages.size <= food.biteCount, "${food.name} has too many messages")
        }
    }

    @Test
    fun `multi bite food that leaves something behind is a pie, a pizza or a cake`() {
        val pies = FoodTable.all.filter { it.biteCount > 1 }
        assertTrue(pies.any { it.leaves == "Pie dish" })
        assertTrue(pies.none { it.leaves == "Bowl" })
    }

    @Test
    fun `outcome weights are positive`() {
        for (food in FoodTable.all.filter { it.outcomes.isNotEmpty() }) {
            assertTrue(food.outcomes.all { it.weight > 0 }, "${food.name} has an empty outcome")
        }
    }

    @Test
    fun `combo foods are the karambwan, gnome food and paddlefish`() {
        val combos = FoodTable.all.filter { it.kind == FoodKind.Combo }.map { it.name }.toSet()
        assertTrue("Cooked karambwan" in combos)
        assertTrue("Worm batta" in combos)
        assertTrue("Shark" !in combos)
    }

    @Test
    fun `the wiki's pie eat delays are kept`() {
        val wild = FoodTable.all.first { it.name == "Wild pie" }
        assertEquals(listOf(1, 1), wild.eatDelays)
        val apple = FoodTable.all.first { it.name == "Apple pie" }
        assertEquals(listOf(1, 2), apple.eatDelays)
        assertEquals(3, FoodTable.all.first { it.name == "Shark" }.eatDelay(0))
    }
}
