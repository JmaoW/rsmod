package org.rsmod.content.skills.thieving.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThievingTablesTest {
    @Test
    fun `the Wiki interpolation gives the end chances at level 1 and 99`() {
        val man = PickpocketTargets.byKey("man")
        assertEquals(181 / 256.0, man.chance(1))
        assertEquals(241 / 256.0, man.chance(99))
    }

    @Test
    fun `the chance rises with the level`() {
        for (target in PickpocketTargets.all) {
            var last = -1.0
            for (level in target.level..99) {
                val chance = target.chance(level)
                assertTrue(chance >= last, "${target.key} fell at level $level")
                last = chance
            }
        }
    }

    @Test
    fun `gloves and the cape scale both ends of the chart and round down`() {
        val hero = PickpocketTargets.byKey("hero")
        // 39 and 160 become 40 and 168 with the gloves; the cape then makes them 44 and 184.
        val plain = hero.chance(80)
        val geared = hero.chance(80, listOf(1.05, 1.1))
        assertTrue(geared > plain)
        // (floor(44 * 19 / 98 + 184 * 79 / 98 + 0.5) + 1) / 256
        assertEquals(158 / 256.0, geared)
    }

    @Test
    fun `no chance goes above one`() {
        for (target in PickpocketTargets.all) {
            assertTrue(target.chance(99, listOf(1.05, 1.1)) <= 1.0, target.key)
        }
    }

    @Test
    fun `every target has something to take and a way to be told apart`() {
        assertEquals(PickpocketTargets.all.size, PickpocketTargets.all.map { it.key }.toSet().size)
        for (target in PickpocketTargets.all) {
            assertTrue(
                target.always.isNotEmpty() || target.table.isNotEmpty(),
                "${target.key} has no loot",
            )
            assertTrue(target.npcs.isNotEmpty(), "${target.key} names no npc")
        }
    }

    @Test
    fun `targets rise in level the way the Wiki lists them`() {
        val levels = PickpocketTargets.all.map { it.level }
        assertEquals(levels.sorted(), levels)
    }

    @Test
    fun `every stall can be told apart and has loot`() {
        assertEquals(Stalls.all.size, Stalls.all.map { it.key }.toSet().size)
        for (stall in Stalls.all) {
            assertTrue(stall.loot.isNotEmpty(), "${stall.key} has no loot")
            assertTrue(stall.loot.all { it.weight > 0.0 }, "${stall.key} has a zero weight")
            assertTrue(stall.locs.isNotEmpty(), "${stall.key} has no loc")
            assertTrue(stall.respawn > 0, "${stall.key} never refills")
        }
    }
}
