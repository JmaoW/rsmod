package org.rsmod.content.areas.misc.isleofsouls.zulrah

import jakarta.inject.Inject
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player

/** The league tasks that Zulrah feeds. */
class ZulrahCompletions @Inject constructor(private val tracker: LeagueTaskTracker) {
    /**
     * [player] was in the lair when Zulrah died. The kill counts towards the "defeat Zulrah N
     * times" tasks only when [countRun] is set: those counters are shared by a team, so the rewards
     * set it for one member of each team, not for all of them.
     */
    fun defeated(player: Player, countRun: Boolean = true) {
        tracker.complete(player, TASK_DEFEAT)
        if (!countRun) {
            return
        }
        tracker.addProgress(player, TASK_DEFEAT_FIFTY, league_progress.zulrah_50, 50)
        tracker.addProgress(player, TASK_DEFEAT_150, league_progress.zulrah_150, 150)
        tracker.addProgress(player, TASK_DEFEAT_300, league_progress.zulrah_300, 300)
    }

    /** [player] dismantled one of Zulrah's uniques for scales. */
    fun dismantled(player: Player) {
        tracker.complete(player, TASK_DISMANTLE)
    }

    companion object {
        /** The ids are the Raging Echoes league's own (`docs/raging_echoes_tasks.json`). */
        const val TASK_DEFEAT = 540
        const val TASK_DEFEAT_FIFTY = 553
        const val TASK_DEFEAT_150 = 554
        const val TASK_DEFEAT_300 = 1397
        const val TASK_DISMANTLE = 1390
    }
}

/**
 * What a kill of Zulrah gives everyone who is in the lair: the tasks. Her drops are the ordinary
 * drop table, rolled for whoever did the most damage.
 */
class ZulrahRewards
@Inject
constructor(private val teams: LeagueTeams, private val completions: ZulrahCompletions) {
    fun win(players: List<Player>, @Suppress("UNUSED_PARAMETER") run: ZulrahRun) {
        // The kill counters belong to the team: count the kill once for each.
        val counted = HashSet<Any>()
        for (player in players) {
            val countRun = counted.add(teams.teamOf(player) ?: player.username)
            completions.defeated(player, countRun)
        }
    }
}
