package org.rsmod.content.skills.slayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.slayer.configs.SlayerTables

class SlayerTablesTest {
    private val tables = SlayerTables.default

    @Test
    fun `every master task has a sensible weight and range`() {
        for (master in tables.masters.values) {
            assertTrue(master.tasks.isNotEmpty(), master.key)
            for (entry in master.tasks) {
                val label = "${master.key} ${entry.task.name}"
                assertTrue(entry.weight > 0, label)
                assertTrue(entry.min in 1..entry.max, label)
                assertTrue(entry.task.id in entry.task.members, label)
            }
        }
    }

    @Test
    fun `no task is listed twice for a master`() {
        for (master in tables.masters.values) {
            val ids = master.tasks.map { it.task.id }
            assertEquals(ids.distinct(), ids, master.key)
        }
    }

    @Test
    fun `points follow the largest interval the streak ends on`() {
        val duradel = tables.master("duradel")!!.points
        assertEquals(15, duradel.forStreak(1))
        assertEquals(15, duradel.forStreak(9))
        assertEquals(75, duradel.forStreak(10))
        assertEquals(225, duradel.forStreak(50))
        assertEquals(75, duradel.forStreak(60))
        assertEquals(375, duradel.forStreak(100))
        assertEquals(525, duradel.forStreak(250))
        assertEquals(750, duradel.forStreak(1000))
        assertEquals(0, tables.master("turael")!!.points.forStreak(10))
    }

    @Test
    fun `metal dragons cover the six dragon task ids`() {
        val metal = tables.tasks.getValue(127)
        for (id in listOf(58, 59, 60, 93, 108, 109)) {
            assertTrue(metal.covers(id), "task id $id")
        }
        assertTrue(!metal.covers(26), "red dragons are their own task")
    }

    @Test
    fun `every superior belongs to a known task id`() {
        assertEquals(29, tables.superiors.size)
        assertTrue(tables.superiors.all { it.task > 0 })
    }
}
