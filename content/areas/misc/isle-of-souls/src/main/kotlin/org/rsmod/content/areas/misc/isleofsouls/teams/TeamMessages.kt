package org.rsmod.content.areas.misc.isleofsouls.teams

import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.blue
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.commands
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.counted
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.green
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.grey
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.number
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.red

/**
 * Every chat message the team and instance commands send, in one place. Plain text in, finished
 * (colour-tagged) text out, so the wording can be changed and tested without the game.
 *
 * Colours: blue for names and numbers, green for a change that went through, red for one that did
 * not, grey for offline players.
 */
object TeamMessages {
    const val NOT_ON_TEAM = "You are not on a league team. Ask an organiser to add you."
    const val NO_TEAMS = "There are no league teams yet."
    const val LEFT_TEAM = "You are no longer on a league team."

    val TEAMS_HEADER: String = blue("League Teams")

    /** The `::teams` header, with a red note while the standings are frozen. */
    fun teamsHeader(frozen: Boolean): String =
        if (frozen) "$TEAMS_HEADER ${red("(frozen)")}" else TEAMS_HEADER

    /* Players */

    fun login(team: String, partners: List<String>): String =
        "Welcome to the league. You are on ${blue(team)}${withNames(partners)}."

    fun teamSummary(team: String, points: Int, tasks: Int): String =
        "Your team, ${blue(team)}, has earned ${number(points)} League " +
            "${if (points == 1) "Point" else "Points"} from ${counted(tasks, "task")}."

    fun teammates(names: List<String>): String =
        if (names.isEmpty()) {
            "You have no teammates yet."
        } else {
            "Teammates: ${names.joinToString(", ", transform = ::blue)}."
        }

    /** One line of `::teams`: `Alpha - 340 pts, 1 task - Alice, Bob`. */
    fun teamLine(name: String, points: Int, tasks: Int, members: List<MemberName>): String {
        val players =
            if (members.isEmpty()) {
                grey("no players")
            } else {
                members.joinToString(", ") { if (it.online) green(it.name) else grey(it.name) }
            }
        return "${blue(name)} - ${number(points)} pts, ${counted(tasks, "task")} - $players"
    }

    /** Sent to a player an admin has just put on a team. */
    fun joined(team: String, partners: List<String>): String =
        "You are now on ${blue(team)}${withNames(partners)}. " +
            "Your league progress now matches your team's."

    /* Admin: roster changes */

    fun created(team: String, id: Int, saved: Boolean): String =
        "${green("Created:")} ${blue(team)} (team $id).${unsaved(saved)}"

    fun added(player: String, team: String, id: Int, saved: Boolean): String =
        "${green("Added:")} ${blue(player)} is now on ${blue(team)} (team $id).${unsaved(saved)}"

    fun moved(
        player: String,
        team: String,
        id: Int,
        fromTeam: String,
        fromId: Int,
        saved: Boolean,
    ): String =
        "${green("Moved:")} ${blue(player)} is now on ${blue(team)} (team $id), " +
            "leaving ${blue(fromTeam)} (team $fromId).${unsaved(saved)}"

    fun alreadyOn(player: String, team: String, id: Int, saved: Boolean): String =
        "${blue(player)} is already on ${blue(team)} (team $id).${unsaved(saved)}"

    fun removed(player: String, team: String, id: Int, saved: Boolean): String =
        "${green("Removed:")} ${blue(player)} left ${blue(team)} (team $id). " +
            "They keep their current progress as their own.${unsaved(saved)}"

    fun notOnATeam(player: String): String = "${blue(player)} is not on a team."

    fun reloaded(teams: Int, synced: Int): String =
        "${green("Reloaded:")} ${counted(teams, "team")}; " +
            "${counted(synced, "online player")} synced."

    fun notReloaded(reason: String): String =
        "${red("Not reloaded:")} ${clause(reason)}. The current teams are unchanged."

    /** A change that was refused. [prefix] is e.g. `Not changed:`; the reason may name commands. */
    fun refused(prefix: String, reason: String): String =
        "${red(prefix)} ${commands(clause(reason))}."

    /* Admin: resets */

    fun resetPrompt(team: String?, id: Int): String {
        val whose = if (team == null) "team $id's" else "${blue(team)}'s"
        return "This erases $whose tasks, points and counters. " +
            "Type ${blue("::teamreset $id confirm")} to do it."
    }

    fun resetAllPrompt(): String =
        "This erases ${red("every")} team's tasks, points and counters. " +
            "Type ${blue("::teamreset all confirm")} to do it."

    fun cleared(tasks: Int, team: String?, id: Int): String {
        val whose = if (team == null) "team $id" else "${blue(team)} (team $id)"
        return "${green("Cleared:")} ${counted(tasks, "task")} for $whose. " +
            "The old progress was backed up first."
    }

    fun clearedAll(tasks: Int): String =
        "${green("Cleared:")} ${counted(tasks, "task")} across every team. " +
            "The old progress was backed up first."

    fun leagueResetBlocked(team: String, id: Int): String =
        "${red("Not cleared:")} your tasks and points belong to ${blue(team)}. " +
            "Your relic choices were cleared; use ${blue("::teamreset $id confirm")} " +
            "to clear the team."

    fun leagueResetDone(): String =
        "${green("Cleared:")} your league tasks, points and relic choices."

    /* Usage */

    fun usage(command: String, example: String? = null): String =
        "${blue("Usage:")} $command" + if (example == null) "" else " - example: $example"

    /* Instances */

    fun entered(type: String): String = "You enter your team's ${blue(type)} instance."

    fun left(): String = "You leave the instance."

    fun notInInstance(): String = "You are not in an instance."

    fun notEntered(reason: String): String = refused("Not entered:", reason)

    fun instancesHeader(count: Int): String = "${blue("Live instances")} - $count"

    fun instanceLine(owner: String, type: String, players: List<String>): String =
        "$owner - ${blue(type)}: ${players.joinToString(", ", transform = ::green)}"

    const val NO_INSTANCES = "There are no live instances."

    /** ` with Bob, Carol`, or nothing when there is nobody else. */
    private fun withNames(names: List<String>): String =
        if (names.isEmpty()) "" else " with ${names.joinToString(", ", transform = ::blue)}"

    private fun unsaved(saved: Boolean): String =
        if (saved) "" else " ${red("Not saved to teams.json; see the server log.")}"

    /** Turns a sentence into the tail of a message: no leading capital, no closing full stop. */
    private fun clause(text: String): String {
        val trimmed = text.trim().removeSuffix(".")
        val second = trimmed.getOrNull(1)
        return if (second != null && second.isLowerCase() && trimmed.first().isUpperCase()) {
            trimmed.replaceFirstChar(Char::lowercaseChar)
        } else {
            trimmed
        }
    }
}
