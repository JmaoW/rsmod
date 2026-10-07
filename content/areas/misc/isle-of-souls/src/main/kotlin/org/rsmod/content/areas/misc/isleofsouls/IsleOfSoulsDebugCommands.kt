package org.rsmod.content.areas.misc.isleofsouls

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varbits
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.TeamMessages
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Development helpers for checking the league interfaces on the Isle of Souls. */
internal class IsleOfSoulsDebugCommands
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val tracker: LeagueTaskTracker,
    private val relics: LeagueRelicTracker,
    private val teams: LeagueTeams,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("leagues") {
            modLevel = modlevels.admin
            desc = "Set the league type and open the league tasks interface"
            invalidArgs = "Use as ::leagues [leagueType] (default 5, ex: ::leagues 5)"
            cheat {
                val leagueType = args.getOrNull(0)?.toInt() ?: DEFAULT_LEAGUE_TYPE
                protectedAccess.launch(player) {
                    vars[isle_varbits.league_type] = leagueType
                    mes("Set league_type to $leagueType and opening the league tasks interface.")
                    ifOpenOverlay(isle_interfaces.league_tasks)
                }
            }
        }

        onCommand("leaguetask") {
            modLevel = modlevels.admin
            desc = "Complete a league task by id"
            invalidArgs = "Use as ::leaguetask taskId (ex: ::leaguetask 289)"
            cheat {
                val taskId = args[0].toInt()
                tracker.complete(player, taskId)
                player.mes("Attempted to complete league task $taskId.")
            }
        }

        onCommand("leaguereset") {
            modLevel = modlevels.admin
            desc = "Clear all league task progress and points"
            cheat {
                // A team's progress is shared with the partner and is never wiped by accident: it
                // takes ::teamreset with `confirm`.
                val team = teams.teamOf(player)
                if (team != null) {
                    relics.reset(player)
                    player.mes(TeamMessages.leagueResetBlocked(team.name, team.id))
                    return@cheat
                }
                tracker.reset(player)
                relics.reset(player)
                player.mes(TeamMessages.leagueResetDone())
            }
        }
    }

    private companion object {
        const val DEFAULT_LEAGUE_TYPE = 5
    }
}
