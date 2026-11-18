package org.rsmod.content.areas.misc.isleofsouls.tasks

/**
 * Tasks of the same kind that differ only by amount, such as "Defeat Zulrah 50 Times" and "...150
 * Times". Each list is in ascending order of amount. Progress is only shown for the lowest
 * unfinished task in a series, so a player sees one counter at a time.
 */
internal object LeagueTaskSeries {
    private val series: List<List<Int>> =
        listOf(
            // Complete the Fight Caves 5 / 10 times.
            listOf(664, 665),
            // Complete the Inferno 5 / 10 / 15 times.
            listOf(667, 668, 669),
            // Defeat Zulrah 50 / 150 / 300 times.
            listOf(553, 554, 1397),
            // Defeat Scurrius 10 / 25 times.
            listOf(1649, 1650),
            // Defeat the Kalphite Queen 50 / 150 times.
            listOf(874, 875),
            // Defeat each Dagannoth King 50 / 150 / 300 times.
            listOf(613, 614, 615),
            // Defeat Callisto / Venenatis / Vet'ion 50 / 150 / 300 times.
            listOf(1404, 1405, 1406),
            listOf(1398, 1399, 1400),
            listOf(1401, 1402, 1403),
            // Complete the Theatre of Blood 25 / 50 / 100 times.
            listOf(946, 947, 948),
            // Defeat Vorkath 50 / 150 / 300 times.
            listOf(608, 609, 610),
            // Defeat any God Wars Dungeon boss 100 / 250 / 500 times.
            listOf(809, 810, 811),
            // Kill 150 / 300 / 500 Demonic Gorillas.
            listOf(731, 732, 733),
            // Craft 200 / 2500 essence into runes.
            listOf(367, 370),
            // Defeat 10 / 20 / 50 Superior Slayer Creatures.
            listOf(326, 327, 330),
            // 25 / 75 / 100 Superior Slayer Encounters.
            listOf(96, 98, 99),
        )

    private val seriesByTask: Map<Int, List<Int>> =
        series.flatMap { group -> group.map { taskId -> taskId to group } }.toMap()

    /**
     * The task whose progress should be shown for [taskId]: the lowest unfinished task of its
     * series, or [taskId] itself when it has no series or every task in it is finished.
     */
    fun shownTask(taskId: Int, isComplete: (Int) -> Boolean): Int {
        val group = seriesByTask[taskId] ?: return taskId
        return group.firstOrNull { !isComplete(it) } ?: taskId
    }
}
