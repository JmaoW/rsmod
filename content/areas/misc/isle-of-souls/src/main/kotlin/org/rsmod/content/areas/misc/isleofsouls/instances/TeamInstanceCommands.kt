package org.rsmod.content.areas.misc.isleofsouls.instances

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.TeamMessages
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Admin commands for trying team instances with the [TestPatchInstance], until real content (the
 * Fight Caves and so on) registers its own types.
 */
class TeamInstanceCommands
@Inject
constructor(private val instances: TeamInstances, private val teams: LeagueTeams) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(TestPatchInstance.type)

        onCommand("instance") {
            modLevel = modlevels.admin
            desc = "Enter your team's instance of a type, or leave the one you are in"
            invalidArgs = TeamMessages.usage("::instance type, or ::instance leave")
            cheat {
                val name = args[0].lowercase()
                if (name == "leave") {
                    val left = instances.leave(player)
                    player.mes(if (left) TeamMessages.left() else TeamMessages.notInInstance())
                    return@cheat
                }

                val type = instances.type(name)
                if (type == null) {
                    val known = instances.registeredTypes.joinToString(", ") { it.name }
                    val reason = "there is no instance called $name. Try $known"
                    player.mes(TeamMessages.notEntered(reason))
                    return@cheat
                }
                player.mes(
                    when (instances.enter(player, type)) {
                        EnterResult.Entered -> TeamMessages.entered(display(type.name))
                        EnterResult.AlreadyInside ->
                            TeamMessages.notEntered("you are already in that instance")
                        EnterResult.NoSpace -> TeamMessages.notEntered(NO_ROOM)
                        EnterResult.InvalidEntry ->
                            TeamMessages.notEntered("that instance's entry point is not valid")
                    }
                )
            }
        }

        onCommand("instances") {
            modLevel = modlevels.admin
            desc = "List the live team instances and who is inside"
            cheat {
                val live = instances.live()
                if (live.isEmpty()) {
                    player.mes(TeamMessages.NO_INSTANCES)
                    return@cheat
                }
                player.mes(TeamMessages.instancesHeader(live.size))
                for (instance in live) {
                    val names = instances.playersIn(instance).map { teams.nameOf(it) }
                    val owner = ownerLabel(instance, names)
                    player.mes(TeamMessages.instanceLine(owner, display(instance.type.name), names))
                }
            }
        }
    }

    /** `Alpha (team 1)` for a team's instance, `Carol (solo)` for a lone player's. */
    private fun ownerLabel(instance: TeamInstance, insideNames: List<String>): String {
        val team = instance.owner.removePrefix("team:").toIntOrNull()?.let(teams::team)
        return when {
            team != null -> "${LeagueChat.blue(team.name)} (team ${team.id})"
            insideNames.isNotEmpty() -> "${LeagueChat.blue(insideNames.first())} (solo)"
            else -> instance.owner
        }
    }

    private fun display(typeName: String): String = typeName.replaceFirstChar(Char::uppercaseChar)

    private companion object {
        const val NO_ROOM = "there is no room for another instance right now. Try again in a moment"
    }
}
