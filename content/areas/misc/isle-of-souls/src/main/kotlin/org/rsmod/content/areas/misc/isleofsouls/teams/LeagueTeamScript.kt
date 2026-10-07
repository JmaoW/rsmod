package org.rsmod.content.areas.misc.isleofsouls.teams

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onPlayerInit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.misc.isleofsouls.bank.TeamBanks
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueEvent
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueScoreboard
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueStandings
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Loads the team roster, shared progress, team banks, freeze state, announcement settings and
 * scoreboard when the server starts, and brings each team member's league varps in line with their
 * team's record as they log in. The sync runs on [onPlayerInit] so the values are in place before
 * the client receives its first copy of the varps.
 */
class LeagueTeamScript
@Inject
constructor(
    private val teams: LeagueTeams,
    private val membership: TeamMembership,
    private val banks: TeamBanks,
    private val event: LeagueEvent,
    private val announcer: LeagueAnnouncer,
    private val standings: LeagueStandings,
    private val scoreboard: LeagueScoreboard,
) : PluginScript() {
    override fun ScriptContext.startup() {
        // Order matters: the later pieces read what the earlier ones load.
        teams.load(LeagueFiles.DATA_DIR)
        banks.load(LeagueFiles.DATA_DIR)
        event.load(LeagueFiles.DATA_DIR)
        announcer.load(LeagueFiles.DATA_DIR)
        standings.syncLeader()
        scoreboard.start(LeagueFiles.DATA_DIR)

        onPlayerInit { membership.refresh(player) }

        onPlayerLogin {
            val team = teams.teamOf(player) ?: return@onPlayerLogin
            val partners = teams.memberNames(team, except = player).map(MemberName::name)
            player.mes(TeamMessages.login(team.name, partners))
        }
    }
}
