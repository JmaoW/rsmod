package org.rsmod.content.areas.misc.isleofsouls.teams

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.areas.misc.isleofsouls.event.EventMessages
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueEvent
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueStandings
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.cheat.Cheat
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Team commands. `::myteam` and `::teams` are for everyone. The rest are admin commands for fixing
 * mistakes while the event is running (the roster normally comes from `teams.json`), and anything
 * that can lose progress needs `confirm` typed out. All wording lives in [TeamMessages].
 */
class LeagueTeamCommands
@Inject
constructor(
    private val teams: LeagueTeams,
    private val tracker: LeagueTaskTracker,
    private val membership: TeamMembership,
    private val standings: LeagueStandings,
    private val event: LeagueEvent,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("myteam") {
            desc = "Show your league team and its progress"
            cheat {
                val team = teams.teamOf(player)
                if (team == null) {
                    player.mes(TeamMessages.NOT_ON_TEAM)
                    return@cheat
                }
                val points = tracker.points(team)
                player.mes(TeamMessages.teamSummary(team.name, points, tracker.taskCount(team)))
                val teammates = teams.memberNames(team, except = player).map(MemberName::name)
                player.mes(TeamMessages.teammates(teammates))
            }
        }

        onCommand("teams") {
            desc = "List the league teams, ranked by points"
            cheat {
                if (teams.teams.isEmpty()) {
                    player.mes(TeamMessages.NO_TEAMS)
                    return@cheat
                }
                player.mes(TeamMessages.teamsHeader(event.frozen))
                for (standing in standings.official()) {
                    val members = teams.team(standing.teamId)?.let(teams::memberNames).orEmpty()
                    player.mes(
                        TeamMessages.teamLine(
                            name = standing.name,
                            points = standing.points,
                            tasks = standing.tasks,
                            members = members,
                        )
                    )
                }
            }
        }

        onCommand("teamnew") {
            modLevel = modlevels.admin
            desc = "Create an empty league team"
            invalidArgs = TeamMessages.usage("::teamnew id name", "::teamnew 3 The Miners")
            cheat {
                val id = args[0].toInt()
                val name = args.drop(1).joinToString(" ")
                attempt {
                    val change = teams.createTeam(id, name)
                    val created = checkNotNull(teams.team(id))
                    player.mes(TeamMessages.created(created.name, created.id, change.saved))
                }
            }
        }

        onCommand("teamset") {
            modLevel = modlevels.admin
            desc = "Put a player on a league team, leaving their old one"
            invalidArgs = TeamMessages.usage("::teamset team-id player", "::teamset 3 some player")
            cheat {
                val id = args[0].toInt()
                val name = args[1].let { args.drop(1).joinToString(" ") }
                val team = teams.team(id)
                if (team == null) {
                    val reason = "there is no team $id. Make it with ::teamnew first"
                    player.mes(TeamMessages.refused("Not changed:", reason))
                    return@cheat
                }
                attempt {
                    val change = teams.assign(name, id)
                    val shown = teams.nameOf(name)
                    val left = change.previous
                    val saved = change.saved
                    player.mes(
                        when {
                            left == null -> TeamMessages.added(shown, team.name, id, saved)
                            left.id == id -> TeamMessages.alreadyOn(shown, team.name, id, saved)
                            else ->
                                TeamMessages.moved(shown, team.name, id, left.name, left.id, saved)
                        }
                    )

                    val target = teams.onlinePlayer(name) ?: return@attempt
                    membership.refresh(target)
                    val partners = teams.memberNames(team, except = target).map(MemberName::name)
                    target.mes(TeamMessages.joined(team.name, partners))
                }
            }
        }

        onCommand("teamremove") {
            modLevel = modlevels.admin
            desc = "Take a player off their league team"
            invalidArgs = TeamMessages.usage("::teamremove player", "::teamremove some player")
            cheat {
                val name = args[0].let { args.joinToString(" ") }
                attempt {
                    // Looked up first: removing a player also forgets their roster display name.
                    val shown = teams.nameOf(name)
                    val change = teams.remove(name)
                    val left = change.previous
                    if (left == null) {
                        player.mes(TeamMessages.notOnATeam(shown))
                        return@attempt
                    }
                    player.mes(TeamMessages.removed(shown, left.name, left.id, change.saved))
                    teams.onlinePlayer(name)?.let {
                        membership.refresh(it)
                        it.mes(TeamMessages.LEFT_TEAM)
                    }
                }
            }
        }

        onCommand("teamreload") {
            modLevel = modlevels.admin
            desc = "Re-read teams.json after editing it by hand"
            cheat {
                val problem = teams.reloadRoster()
                if (problem != null) {
                    player.mes(TeamMessages.notReloaded(problem))
                    return@cheat
                }
                var synced = 0
                for (team in teams.teams) {
                    for (member in teams.onlineMembers(team)) {
                        membership.refresh(member)
                        synced++
                    }
                }
                player.mes(TeamMessages.reloaded(teams.teams.size, synced))
            }
        }

        onCommand("teamreset") {
            modLevel = modlevels.admin
            desc = "Erase a team's (or every team's) shared tasks, points and counters"
            invalidArgs =
                TeamMessages.usage("::teamreset team-id confirm", "::teamreset all confirm")
            cheat {
                val target = args[0].lowercase()
                val everyone = target == "all"
                val id = if (everyone) 0 else target.toInt()
                if (args.getOrNull(1) != "confirm") {
                    player.mes(
                        if (everyone) {
                            TeamMessages.resetAllPrompt()
                        } else {
                            TeamMessages.resetPrompt(teams.team(id)?.name, id)
                        }
                    )
                    return@cheat
                }

                val team = if (everyone) null else teams.team(id)
                val cleared = if (everyone) teams.resetAllProgress() else teams.resetProgress(id)
                // A full reset is a fresh start, so it also lifts a freeze left over from testing.
                val liftedFreeze = everyone && event.unfreeze()
                standings.syncLeader()
                val affected = if (everyone) teams.teams else listOfNotNull(team)
                for (affectedTeam in affected) {
                    teams.onlineMembers(affectedTeam).forEach(membership::refresh)
                }
                player.mes(
                    if (everyone) {
                        TeamMessages.clearedAll(cleared)
                    } else {
                        TeamMessages.cleared(cleared, team?.name, id)
                    }
                )
                if (liftedFreeze) {
                    player.mes(EventMessages.freezeCleared())
                }
            }
        }
    }

    /** Shows a rejected roster change to the admin instead of reporting a crash. */
    private fun Cheat.attempt(change: () -> Unit) {
        try {
            change()
        } catch (e: IllegalStateException) {
            player.mes(TeamMessages.refused("Not changed:", e.message ?: "that change was refused"))
        } catch (e: IllegalArgumentException) {
            player.mes(TeamMessages.refused("Not changed:", e.message ?: "that change was refused"))
        }
    }
}
