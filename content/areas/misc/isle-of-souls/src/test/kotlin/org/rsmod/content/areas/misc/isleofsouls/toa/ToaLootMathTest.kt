package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToaLootMathTest {
    @Test
    fun `the unique chance is one percent per 10500 minus 20 per level, as in the wiki example`() {
        // Raid level 400 gives 1% per 3,700 points.
        assertEquals(340.0, ToaLootMath.uniqueLevel(400), 1e-9)
        assertEquals(0.01, ToaLootMath.uniqueChance(3_700, 400), 1e-9)
        assertEquals(0.02, ToaLootMath.uniqueChance(7_400, 400), 1e-9)
        assertEquals(0.01, ToaLootMath.uniqueChance(10_500 - 20 * 300, 300), 1e-9)
    }

    @Test
    fun `the unique chance stops at fifty-five percent`() {
        assertEquals(0.55, ToaLootMath.uniqueChance(1_000_000, 400), 1e-9)
    }

    @Test
    fun `levels above 430 count a sixth`() {
        assertEquals(350.0, ToaLootMath.uniqueLevel(430), 1e-9)
        assertEquals(360.0, ToaLootMath.uniqueLevel(490), 1e-9)
    }

    @Test
    fun `unique weights add up to one and follow the wiki`() {
        for (level in listOf(0, 150, 300, 350, 400, 450, 500, 600)) {
            assertEquals(1.0, ToaLootMath.uniqueWeights(level).values.sum(), 1e-9, "level $level")
        }
        val base = ToaLootMath.uniqueWeights(200)
        assertEquals(7 / 24.0, base.getValue(ToaUnique.OsmumtensFang), 1e-9)
        assertEquals(3 / 24.0, base.getValue(ToaUnique.ElidinisWard), 1e-9)
        assertEquals(1 / 24.0, base.getValue(ToaUnique.TumekensShadow), 1e-9)
        val deep = ToaLootMath.uniqueWeights(500)
        assertTrue(
            deep.getValue(ToaUnique.TumekensShadow) > base.getValue(ToaUnique.TumekensShadow)
        )
    }

    @Test
    fun `a unique that is out of level for the raid needs another roll of fifty`() {
        // The fang comes first in the table, so a roll of 0 picks it; the shadow is last.
        assertEquals(ToaUnique.OsmumtensFang, ToaLootMath.pickUnique(200) { 0 })
        // At level 100 the ward, Masori and shadow are out of level: the 1 in 50 must also hit.
        val missed = ToaLootMath.pickUnique(100) { n -> if (n == 1_000_000) 999_999 else 7 }
        assertNull(missed)
        val hit = ToaLootMath.pickUnique(100) { n -> if (n == 1_000_000) 999_999 else 0 }
        assertEquals(ToaUnique.TumekensShadow, hit)
        // Below level 50 every unique needs it.
        assertNull(ToaLootMath.pickUnique(10) { n -> if (n == 1_000_000) 0 else 3 })
    }

    @Test
    fun `a teammate's unique always comes, picked again among the in-level ones on a miss`() {
        val roll = { n: Int -> if (n == 1_000_000) 999_999 else 7 }
        // At level 100 the shadow misses its 1 in 50, so the pick is redone among the fang and
        // the Lightbearer; the same high roll lands on the Lightbearer, the last of them.
        assertEquals(ToaUnique.Lightbearer, ToaLootMath.pickTeamUnique(100, roll))
        // An out-of-level unique that passes its roll is kept.
        assertEquals(
            ToaUnique.TumekensShadow,
            ToaLootMath.pickTeamUnique(100) { n -> if (n == 1_000_000) 999_999 else 0 },
        )
        // Below level 50 nothing is in level, so it falls back to the fang and Lightbearer.
        val low = ToaLootMath.pickTeamUnique(10) { n -> if (n == 1_000_000) 0 else 3 }
        assertEquals(ToaUnique.OsmumtensFang, low)
    }

    @Test
    fun `common quantities are points over the divisor, with the bonus from level 300`() {
        val coins = ToaLootMath.common.first { it.name == "Coins" }
        val death = ToaLootMath.common.first { it.name == "Death rune" }
        val cache = ToaLootMath.common.last()
        assertEquals(10_000, ToaLootMath.commonQuantity(coins, 10_000, 100))
        assertEquals(500, ToaLootMath.commonQuantity(death, 10_000, 100))
        assertEquals(1, ToaLootMath.commonQuantity(cache, 10_000, 100))
        // 15% at 300, 35% at 400.
        assertEquals(11_500, ToaLootMath.commonQuantity(coins, 10_000, 300))
        assertEquals(13_500, ToaLootMath.commonQuantity(coins, 10_000, 400))
        assertEquals(27, ToaLootMath.COMMON_ODDS)
        assertEquals(27, ToaLootMath.common.size)
    }

    @Test
    fun `fossilised dung replaces the table below fifteen hundred points`() {
        assertTrue(ToaLootMath.givesDung(1_499))
        assertTrue(!ToaLootMath.givesDung(1_500))
    }

    @Test
    fun `tertiaries rise with completions and the pet with points`() {
        assertEquals(0.1, ToaLootMath.threadChance(0, hasThread = false), 1e-9)
        assertEquals(0.3, ToaLootMath.threadChance(15, hasThread = false), 1e-9)
        assertEquals(0.02, ToaLootMath.threadChance(50, hasThread = true), 1e-9)
        assertEquals(1 / 50.0, ToaLootMath.jewelChance(0), 1e-9)
        assertEquals(3 / 50.0, ToaLootMath.jewelChance(75), 1e-9)
        assertEquals(3 / 50.0, ToaLootMath.jewelChance(400), 1e-9)
        // 1% per (350,000 - 700 x level) points: 20,000 points at level 400 is 20,000 / 70,000 %.
        assertEquals(20_000 / 70_000.0 / 100.0, ToaLootMath.petChance(20_000, 400), 1e-9)
        assertEquals(0.01, ToaLootMath.eliteClueChance(2_000), 1e-9)
        assertEquals(0.25, ToaLootMath.eliteClueChance(1_000_000), 1e-9)
    }
}
