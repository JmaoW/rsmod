package org.rsmod.content.other.food

import org.junit.jupiter.api.Assertions.assertEquals
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
}
