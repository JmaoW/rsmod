package org.rsmod.api.drops

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.rsmod.api.random.GameRandom
import org.rsmod.api.testing.factory.obj.TestObjTypeFactory
import org.rsmod.game.type.obj.ObjType

class DropRollerTest {
    private val factory = TestObjTypeFactory()
    private val bones = factory.create(1)
    private val sword = factory.create(2)
    private val coins = factory.create(3)
    private val gem = factory.create(4)
    private val scroll = factory.create(5)

    private fun item(obj: ObjType, min: Int = 1, max: Int = min) =
        DropOutcome.Item(DropStack(obj, min, max))

    private fun roller(vararg doubles: Double) = DropRoller(ScriptedRandom(doubles.toList()))

    private val goblin =
        DropTable(
            name = "goblin",
            always = listOf(DropStack(bones, 1, 1)),
            main =
                listOf(
                    // The sword and the scroll are rare items that the relics boost.
                    DropEntry(0.25, item(sword), boosted = true),
                    DropEntry(0.50, item(coins, 5, 5)),
                    // The remaining 25% is "nothing".
                ),
            tertiary = listOf(DropEntry(0.01, item(scroll), boosted = true)),
        )

    @Test
    fun `always drops are always handed out`() {
        // Main roll lands in the "nothing" gap, tertiary roll misses.
        val results = roller(0.99, 0.5).roll(goblin)
        assertEquals(listOf(DropResult(bones, 1)), results)
    }

    @Test
    fun `main roll picks one entry using its share of the range`() {
        val first = roller(0.10, 0.5).roll(goblin)
        assertEquals(listOf(DropResult(bones, 1), DropResult(sword, 1)), first)

        val second = roller(0.30, 0.5).roll(goblin)
        assertEquals(listOf(DropResult(bones, 1), DropResult(coins, 5)), second)
    }

    @Test
    fun `main roll boundaries`() {
        // 0.25 is the first value past the first entry.
        assertEquals(DropResult(coins, 5), roller(0.25, 0.5).roll(goblin)[1])
        // 0.75 is the first value past the last entry.
        assertEquals(1, roller(0.75, 0.5).roll(goblin).size)
    }

    @Test
    fun `tertiary entries are rolled independently`() {
        val results = roller(0.10, 0.005).roll(goblin)
        assertEquals(
            listOf(DropResult(bones, 1), DropResult(sword, 1), DropResult(scroll, 1)),
            results,
        )
    }

    @Test
    fun `multiplier scales boosted drops but not always drops`() {
        // A 0.015 tertiary roll misses the 1% clue scroll, but hits it at 2x (2%). The main roll
        // of 0.99 is in the "nothing" gap at 1x. At 2x the boosted sword takes 0.50 of the range
        // (not 0.25) and the coins keep their 0.50, so it lands on the coins.
        val normal = roller(0.99, 0.015).roll(goblin)
        assertEquals(listOf(DropResult(bones, 1)), normal)

        val doubled = roller(0.99, 0.015).roll(goblin, multiplier = 2.0)
        assertEquals(
            listOf(DropResult(bones, 1), DropResult(coins, 5), DropResult(scroll, 1)),
            doubled,
        )
    }

    @Test
    fun `boost only changes boosted entries`() {
        // Without the boost the sword covers 0 to 0.25 and the coins 0.25 to 0.75. With a 2x boost
        // the sword covers 0 to 0.50, so a roll of 0.40 is a sword instead of coins.
        assertEquals(DropResult(coins, 5), roller(0.40, 0.99).roll(goblin)[1])
        assertEquals(DropResult(sword, 1), roller(0.40, 0.99).roll(goblin, multiplier = 2.0)[1])

        // A table with no boosted entries ignores the multiplier completely.
        val plain = DropTable("plain", main = listOf(DropEntry(0.5, item(coins, 5, 5))))
        assertEquals(1, roller(0.40).roll(plain, multiplier = 100.0).size)
        assertEquals(0, roller(0.60).roll(plain, multiplier = 100.0).size)
    }

    @Test
    fun `main table is scaled back down when the boost passes 100 percent`() {
        val table =
            DropTable(
                "boosted",
                main =
                    listOf(
                        DropEntry(0.25, item(sword), boosted = true),
                        DropEntry(0.75, item(coins, 5, 5)),
                    ),
            )
        // At 2x the entries are 0.5 and 0.75 (total 1.25): they cover 0.4 and 0.6 of the range.
        assertEquals(DropResult(sword, 1), roller(0.39).roll(table, multiplier = 2.0)[0])
        assertEquals(DropResult(coins, 5), roller(0.41).roll(table, multiplier = 2.0)[0])

        // No "nothing" gap is left: even the highest roll lands on an entry.
        assertEquals(DropResult(coins, 5), roller(0.999).roll(table, multiplier = 2.0)[0])
    }

    @Test
    fun `the main table is rolled once per main roll`() {
        val table =
            DropTable(
                "double",
                main = listOf(DropEntry(0.5, item(sword)), DropEntry(0.5, item(coins, 5, 5))),
                mainRolls = 2,
            )
        val results = roller(0.1, 0.9).roll(table)
        assertEquals(listOf(DropResult(sword, 1), DropResult(coins, 5)), results)
    }

    @Test
    fun `quantity is rolled in range`() {
        val table = DropTable("coins", main = listOf(DropEntry(1.0, item(coins, 10, 20))))
        val random = ScriptedRandom(listOf(0.0), ints = listOf(17))
        val results = DropRoller(random).roll(table)
        assertEquals(listOf(DropResult(coins, 17)), results)
    }

    @Test
    fun `nested tables are rolled when their entry is hit`() {
        val rare =
            DropTable("rare", main = listOf(DropEntry(0.5, item(gem)), DropEntry(0.5, item(sword))))
        val table = DropTable("boss", main = listOf(DropEntry(0.1, DropOutcome.Nested(rare))))
        // First roll hits the nested entry, second roll picks the second rare item.
        val results = roller(0.05, 0.9).roll(table)
        assertEquals(listOf(DropResult(sword, 1)), results)
    }

    @Test
    fun `main drops cannot add up to more than 100 percent`() {
        assertThrows<IllegalArgumentException> {
            DropTable(
                "bad",
                main = listOf(DropEntry(0.6, item(sword)), DropEntry(0.6, item(coins))),
            )
        }
    }

    @Test
    fun `invalid entries are rejected`() {
        assertThrows<IllegalArgumentException> { DropEntry(0.0, item(sword)) }
        assertThrows<IllegalArgumentException> { DropStack(sword, 0, 1) }
        assertThrows<IllegalArgumentException> { DropStack(sword, 5, 1) }
    }

    @Test
    fun `an empty table drops nothing`() {
        assertTrue(roller().roll(DropTable("empty")).isEmpty())
    }

    /**
     * Hands back the scripted doubles in order, and the scripted ints (or the minimum) for sizes.
     */
    private class ScriptedRandom(doubles: List<Double>, ints: List<Int> = emptyList()) :
        GameRandom {
        private val doubles = ArrayDeque(doubles)
        private val ints = ArrayDeque(ints)

        override fun of(maxExclusive: Int): Int = ints.removeFirstOrNull() ?: 0

        override fun of(minInclusive: Int, maxInclusive: Int): Int =
            ints.removeFirstOrNull() ?: minInclusive

        override fun randomDouble(): Double = doubles.removeFirst()
    }
}
