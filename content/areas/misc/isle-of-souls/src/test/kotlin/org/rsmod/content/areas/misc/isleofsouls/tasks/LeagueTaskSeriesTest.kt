package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeagueTaskSeriesTest {
    @Test
    fun `a task outside any series shows its own progress`() {
        assertEquals(306, LeagueTaskSeries.shownTask(306) { false })
    }

    @Test
    fun `the lowest unfinished task of a series is the one shown`() {
        // Defeat Zulrah 50 / 150 / 300 times.
        assertEquals(553, LeagueTaskSeries.shownTask(553) { false })
        assertEquals(553, LeagueTaskSeries.shownTask(1397) { false })
        assertEquals(554, LeagueTaskSeries.shownTask(1397) { it == 553 })
        assertEquals(1397, LeagueTaskSeries.shownTask(1397) { it == 553 || it == 554 })
    }

    @Test
    fun `a finished series falls back to the task itself`() {
        assertEquals(1397, LeagueTaskSeries.shownTask(1397) { true })
    }
}
