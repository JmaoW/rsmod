package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskTexts

class LeagueAgilityTaskListTest {
    private val easy = listOf(638)
    private val medium = listOf(386)
    private val hard = listOf(641)
    private val master = listOf(1576)
    private val elite = listOf(769, 244, 245, 1469, 1492)

    @Test
    fun `every agility task is in the list in its own tier`() {
        for ((ids, tier) in
            listOf(
                easy to LeagueTier.Easy,
                medium to LeagueTier.Medium,
                hard to LeagueTier.Hard,
                elite to LeagueTier.Elite,
                master to LeagueTier.Master,
            )) {
            for (id in ids) {
                val task = LeagueTaskList.byId[id]
                assertNotNull(task, "task $id is not in the list")
                assertEquals(tier, task!!.tier, "tier of task $id")
            }
        }
    }

    @Test
    fun `the agility tasks are worth 1520 points`() {
        val ids = easy + medium + hard + elite + master
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1520, ids.sumOf { LeagueTaskList.byId.getValue(it).tier.points })
    }

    @Test
    fun `agility tasks that named a region were rewritten`() {
        val rewritten = IsleOfSoulsTaskTexts.edits.map { it.taskId }.toSet()
        for (id in listOf(769, 638, 641, 1576)) {
            assertTrue(id in rewritten, "task $id still has its area")
        }
    }

    @Test
    fun `the course and shortcut tasks are gone from the list`() {
        for (id in listOf(686, 459, 1522, 389, 460, 1523, 768, 640, 688, 1590, 689)) {
            assertTrue(id !in LeagueTaskList.byId, "task $id should have been removed")
        }
    }
}
