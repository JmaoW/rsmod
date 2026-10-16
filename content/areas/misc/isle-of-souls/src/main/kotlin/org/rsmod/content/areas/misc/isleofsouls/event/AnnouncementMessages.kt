package org.rsmod.content.areas.misc.isleofsouls.event

import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.blue
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.number
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.points

/**
 * The server-wide announcement lines. Every one starts with a blue `League:` so players can tell
 * them from ordinary game messages. Plain text in, finished (colour-tagged) text out.
 */
object AnnouncementMessages {
    private val prefix = blue("League:")

    /** `Alice of Alpha`, or just `Alice` for a player with no team. */
    fun who(name: String, team: String?): String =
        if (team == null) blue(name) else "${blue(name)} of ${blue(team)}"

    fun taskCompleted(who: String, tier: String, task: String): String =
        "$prefix $who has completed the $tier task: ${blue(task)}."

    fun firstToComplete(who: String, tier: String, task: String): String =
        "$prefix $who is the first to complete the $tier task: ${blue(task)}."

    fun levelReached(who: String, level: Int, skill: String): String =
        "$prefix $who has reached level ${number(level)} in ${blue(skill)}."

    fun death(who: String): String = "$prefix $who has died."

    fun drop(who: String, item: String, count: Int): String {
        val received = if (count == 1) blue(item) else "${number(count)} x ${blue(item)}"
        return "$prefix $who has received a rare drop: $received."
    }

    fun pet(who: String, pet: String): String =
        "$prefix $who has a funny feeling like they are being followed: ${blue(pet)}."

    fun leadTaken(team: String, points: Int): String =
        "$prefix ${blue(team)} has taken the lead with ${points(points)}."

    /** The announcement for a freeze: one line, then the top of the standings. */
    fun frozen(top: List<TeamStanding>): List<String> =
        listOf("$prefix The standings are now frozen.") + top.map(::standingLine)

    fun unfrozen(): String = "$prefix The standings are live again."

    fun standingLine(standing: TeamStanding): String =
        "${standing.rank}. ${blue(standing.name)} - ${points(standing.points)}"
}
