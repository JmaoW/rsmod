package org.rsmod.content.skills.firemaking.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.game.type.obj.ObjType

class FiremakingLogsTest {
    private fun log(obj: ObjType): FiremakingLog = FiremakingLogs.all.first { it.obj === obj }

    @Test
    fun `levels and xp match the wiki`() {
        val expected =
            listOf(
                objs.logs to listOf(1, 40.0),
                objs.achey_tree_logs to listOf(1, 40.0),
                objs.oak_logs to listOf(15, 60.0),
                objs.willow_logs to listOf(30, 90.0),
                objs.teak_logs to listOf(35, 105.0),
                objs.arctic_pine_logs to listOf(42, 125.0),
                objs.maple_logs to listOf(45, 135.0),
                objs.mahogany_logs to listOf(50, 157.5),
                objs.yew_logs to listOf(60, 202.5),
                objs.magic_logs to listOf(75, 303.8),
                objs.redwood_logs to listOf(90, 350.0),
            )
        assertEquals(expected.size, FiremakingLogs.all.size)
        for ((obj, values) in expected) {
            val log = log(obj)
            assertEquals(values, listOf(log.level, log.xp))
        }
    }

    @Test
    fun `light chance is 65 in 256 at level 1`() {
        assertEquals(65 / 256.0, FiremakingLogs.lightChance(1))
    }

    @Test
    fun `light chance is certain from level 43`() {
        assertTrue(FiremakingLogs.lightChance(42) < 1.0)
        assertEquals(1.0, FiremakingLogs.lightChance(43))
        assertEquals(1.0, FiremakingLogs.lightChance(99))
    }

    @Test
    fun `light chance never decreases with level`() {
        for (level in 1 until 99) {
            assertTrue(FiremakingLogs.lightChance(level) <= FiremakingLogs.lightChance(level + 1))
        }
    }
}
