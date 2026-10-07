package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.rsmod.content.areas.misc.isleofsouls.teams.Team
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published once for every completed league task. A team's task is one event however many members
 * are online, with [player] being the member who did it. [team] is `null` for a player who is not
 * on a team. [firstTeamToComplete] is true when no other team had completed the task before.
 */
data class LeagueTaskCompleted(
    val player: Player,
    val team: Team?,
    val taskId: Int,
    val taskName: String,
    val tier: String,
    val points: Int,
    val firstTeamToComplete: Boolean,
) : UnboundEvent
