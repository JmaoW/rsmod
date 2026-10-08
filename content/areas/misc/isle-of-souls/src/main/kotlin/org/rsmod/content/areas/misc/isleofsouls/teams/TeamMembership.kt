package org.rsmod.content.areas.misc.isleofsouls.teams

import jakarta.inject.Inject
import org.rsmod.content.areas.misc.isleofsouls.bank.TeamBanks
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Player

/**
 * Keeps everything that depends on which team a player is on in line with the roster: the shared
 * task progress, the shared loot, and the shared bank.
 *
 * Shared loot uses [Player.observerUUID], the id the engine matches against an obj's receiver to
 * decide who may see and pick up a private drop. Teammates get the same id, so a drop made for one
 * of them (a kill, or an item they dropped) is visible to, and can be picked up by, the other.
 */
class TeamMembership
@Inject
constructor(
    private val teams: LeagueTeams,
    private val tracker: LeagueTaskTracker,
    private val banks: TeamBanks,
    private val masteryTasks: LeagueMasteryTasks,
) {
    /** Call on login and whenever [player]'s team may have changed. */
    fun refresh(player: Player) {
        tracker.syncPlayer(player)
        masteryTasks.sync(player)
        applyObserver(player)
        banks.attach(player)
    }

    /**
     * The observer id every member of [team] shares. It sits in a range far below any player's own
     * id (those are small positive character ids) and away from the engine's "no observer" value.
     */
    fun observerIdOf(team: Team): Long = TEAM_OBSERVER_BASE - team.id

    private fun applyObserver(player: Player) {
        val wanted = teams.teamOf(player)?.let(::observerIdOf) ?: player.uuid
        if (player.observerUUID == wanted) {
            return
        }
        player.observerUUID = wanted

        // The client was sent the ground items around it using the old id, so make the zone
        // processor treat every nearby zone as new and send them again with the new one. Do not
        // touch `lastProcessedZone`: the player registry uses it to track which zone the player is
        // registered in, and resetting it makes the registry add the player to their zone twice.
        player.visibleZoneKeys.clear()
    }

    private companion object {
        const val TEAM_OBSERVER_BASE = Long.MIN_VALUE / 2
    }
}
