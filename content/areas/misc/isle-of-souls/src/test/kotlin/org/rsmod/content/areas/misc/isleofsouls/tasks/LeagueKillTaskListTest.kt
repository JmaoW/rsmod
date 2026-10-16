package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LeagueKillTaskListTest {
    @Test
    fun `every kill task is in the task list`() {
        for (task in LeagueKillTasks.tasks) {
            assertNotNull(LeagueTaskList.byId[task.taskId], "task ${task.taskId} is not listed")
        }
    }

    @Test
    fun `a task is only listed once for the same kill count`() {
        val ids = LeagueKillTasks.tasks.map { it.taskId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `counted tasks have a progress varp and single kills do not`() {
        for (task in LeagueKillTasks.tasks) {
            val counted = task.count > 1
            assertEquals(
                counted,
                task.taskId in LeagueKillTasks.counters,
                "progress varp of task ${task.taskId}",
            )
        }
        val counters = LeagueKillTasks.counters.values.map { it.internalNameValue }
        assertEquals(counters.size, counters.toSet().size)
    }

    @Test
    fun `monster names are lower case so the kill lookup finds them`() {
        for (task in LeagueKillTasks.tasks) {
            assertEquals(task.monster.lowercase(), task.monster, "monster of task ${task.taskId}")
            assertTrue(task.monster.isNotBlank())
        }
    }

    @Test
    fun `the names of the kill tasks name their monster`() {
        for (task in LeagueKillTasks.tasks) {
            val name = LeagueTaskList.byId.getValue(task.taskId).name.lowercase()
            val words = task.monster.split(' ').first()
            assertTrue(
                name.contains(words.removeSuffix("s").take(4)),
                "task ${task.taskId} `$name` does not mention `${task.monster}`",
            )
        }
    }
}
