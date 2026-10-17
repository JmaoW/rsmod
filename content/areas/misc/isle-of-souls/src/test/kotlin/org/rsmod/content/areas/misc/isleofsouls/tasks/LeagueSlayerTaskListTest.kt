package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.configs.IsleOfSoulsTaskTexts

class LeagueSlayerTaskListTest {
    private val easy = listOf(319)
    private val medium = listOf(1524, 320, 322, 96, 98, 917)
    private val hard = listOf(99, 326, 327)
    private val elite = listOf(330, 328, 254, 255, 1474, 1497, 126)

    @Test
    fun `every slayer task is in the list in its own tier`() {
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
    fun `the slayer tasks are worth their tier points`() {
        val ids = easy + medium + hard + elite
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            1 * 10 + 6 * 30 + 3 * 80 + 7 * 200,
            ids.sumOf { LeagueTaskList.byId.getValue(it).tier.points },
        )
    }

    @Test
    fun `the helmet task no longer names Morytania`() {
        assertEquals(true, IsleOfSoulsTaskTexts.edits.any { it.taskId == 917 })
    }
}
