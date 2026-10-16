package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskTexts

class LeagueHunterTaskListTest {
    private val easy = listOf(379, 380, 1369)
    private val medium = listOf(382, 439, 445, 519, 691, 694, 859, 912, 976, 977, 1614, 1833)
    private val hard = listOf(384, 440, 578, 978, 979, 1615, 1697, 1513)
    private val elite = listOf(256, 257, 1475, 1498, 385, 692, 1377, 1616, 1696, 1720, 1722, 980)

    @Test
    fun `every hunter task is in the list in its own tier`() {
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
    fun `the hunter tasks are worth 3430 points`() {
        val ids = easy + medium + hard + elite
        assertEquals(35, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(3430, ids.sumOf { LeagueTaskList.byId.getValue(it).tier.points })
    }

    @Test
    fun `hunter tasks that named a place or an area were rewritten`() {
        val rewritten = IsleOfSoulsTaskTexts.edits.map { it.taskId }.toSet()
        for (id in
            listOf(1369, 439, 445, 519, 691, 694, 859, 912, 976, 977, 1833, 440, 578, 978, 979) +
                listOf(980, 1697, 1513, 692, 1377, 1696, 1720, 1722)) {
            assertTrue(id in rewritten, "task $id still has its area")
        }
    }
}
