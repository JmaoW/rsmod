package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskTexts

class LeagueThievingTaskListTest {
    private val easy = listOf(273, 275, 457, 1071)
    private val medium =
        listOf(76, 86, 274, 277, 454, 455, 458, 684, 850, 1699, 1700, 1768, 1769) +
            listOf(1817, 1818, 1819, 1834, 1835, 1844)
    private val hard = listOf(279, 526, 685, 851, 911, 1541)
    private val elite = listOf(248, 249, 1471, 1494)

    @Test
    fun `every thieving task is in the list in its own tier`() {
        for ((ids, tier) in
            listOf(
                easy to LeagueTier.Easy,
                medium to LeagueTier.Medium,
                hard to LeagueTier.Hard,
                elite to LeagueTier.Elite,
            )) {
            for (id in ids) {
                val task = LeagueTaskList.byId[id]
                assertNotNull(task, "task $id is not in the list")
                assertEquals(tier, task!!.tier, "tier of task $id")
            }
        }
    }

    @Test
    fun `the thieving tasks are worth 1890 points`() {
        val ids = easy + medium + hard + elite
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1890, ids.sumOf { LeagueTaskList.byId.getValue(it).tier.points })
    }

    @Test
    fun `thieving tasks that named a place were rewritten`() {
        val rewritten = IsleOfSoulsTaskTexts.edits.map { it.taskId }.toSet()
        // These had an area in the vanilla list; the rest were already global.
        for (id in listOf(457, 76, 454, 455, 458, 684, 850, 1699, 1700, 1768, 1769)) {
            assertTrue(id in rewritten, "task $id still has its area")
        }
        for (id in listOf(1817, 1818, 1819, 1834, 1835, 1844, 526, 685, 851, 911, 1541)) {
            assertTrue(id in rewritten, "task $id still has its area")
        }
    }
}
