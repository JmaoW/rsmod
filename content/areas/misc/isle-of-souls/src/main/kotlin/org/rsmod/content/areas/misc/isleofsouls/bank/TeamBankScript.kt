package org.rsmod.content.areas.misc.isleofsouls.bank

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.bank.BankEvents
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Keeps the team banks current while the league runs: tab layout handed from one teammate to the
 * next, and the bank saved to disk. `LeagueTeamScript` loads the saved banks at startup.
 *
 * Giving a player the shared bank in the first place is [TeamBanks.attach], called from
 * `TeamMembership.refresh` on login and on every roster change.
 */
class TeamBankScript
@Inject
constructor(private val banks: TeamBanks, private val teams: LeagueTeams) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<BankEvents.Opened> { banks.onOpen(player) }
        onEvent<BankEvents.Closed> { banks.onClose(player) }

        onPlayerLogin {
            if (teams.teamOf(player) != null) {
                player.softTimer(team_bank_timers.sync, TeamBanks.SYNC_TICKS)
            }
        }
        onPlayerSoftTimer(team_bank_timers.sync) {
            if (teams.teamOf(player) == null) {
                player.clearSoftTimer(team_bank_timers.sync)
                return@onPlayerSoftTimer
            }
            banks.sync(player)
        }

        // The bank screen is closed as part of logging out, but the bank tidies itself a moment
        // later; this catches the finished layout and saves one last time.
        onPlayerLogout {
            banks.onClose(player)
            banks.save()
        }
    }
}
