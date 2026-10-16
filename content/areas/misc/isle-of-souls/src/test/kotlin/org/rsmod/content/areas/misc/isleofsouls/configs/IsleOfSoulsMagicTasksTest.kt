package org.rsmod.content.areas.misc.isleofsouls.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskList

class IsleOfSoulsMagicTasksTest {
    @Test
    fun `every magic task is in the task list under its own id`() {
        for ((index, def) in IsleOfSoulsMagicTasks.all.withIndex()) {
            val task = LeagueTaskList.byId[IsleOfSoulsMagicTasks.FIRST_TASK + index]
            assertEquals(def.name, task?.name)
            assertEquals(def.tier, task?.tier)
        }
    }

    @Test
    fun `task ids and struct ids follow the order of the list`() {
        assertEquals(1846, IsleOfSoulsMagicTasks.taskId("wind_strike"))
        assertEquals(1871, IsleOfSoulsMagicTasks.taskId("xp_35m"))
        assertEquals(6246, IsleOfSoulsMagicTasks.structId("wind_strike"))
        assertEquals(6271, IsleOfSoulsMagicTasks.structId("xp_35m"))
    }

    @Test
    fun `the keys are unique and the ids have completion bits`() {
        val keys = IsleOfSoulsMagicTasks.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(IsleOfSoulsMagicTasks.taskId(keys.last()) < 1984)
    }
}
