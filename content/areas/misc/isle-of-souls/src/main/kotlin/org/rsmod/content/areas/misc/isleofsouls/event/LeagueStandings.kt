package org.rsmod.content.areas.misc.isleofsouls.event

import jakarta.inject.Inject
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.Team

/**
 * The event standings. [live] ranks the teams as they are right now; [official] is what players are
 * shown, which is the frozen snapshot while the standings are frozen and [live] otherwise. Every
 * display (chat, announcements, the scoreboard) reads [official] so they cannot disagree.
 */
class LeagueStandings
@Inject
constructor(
    private val teams: LeagueTeams,
    private val tracker: LeagueTaskTracker,
    private val event: LeagueEvent,
) {
    private var announcedLeader: Int? = null

    fun live(): List<TeamStanding> = TeamRanking.rank(teams.teams.map(::scoreOf))

    fun official(): List<TeamStanding> = event.frozenStandings() ?: live()

    /** Remembers the current leader without reporting it, so a restart does not re-announce. */
    fun syncLeader() {
        announcedLeader = TeamRanking.leader(live())?.teamId
    }

    /**
     * Returns the team that has just taken the lead, or `null` if the lead has not changed. Always
     * `null` while the standings are frozen; call again after unfreezing to catch a change made in
     * the meantime.
     */
    fun takeLeadChange(): TeamStanding? {
        if (event.frozen) {
            return null
        }
        val leader = TeamRanking.leader(live())
        if (leader == null || leader.teamId == announcedLeader) {
            return null
        }
        announcedLeader = leader.teamId
        return leader
    }

    private fun scoreOf(team: Team): TeamScore =
        TeamScore(
            teamId = team.id,
            name = team.name,
            points = tracker.points(team),
            tasks = tracker.taskCount(team),
            reachedAt = teams.completed(team).values.maxOrNull(),
        )
}
