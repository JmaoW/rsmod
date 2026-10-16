package org.rsmod.content.areas.misc.isleofsouls.masteries

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * Records the 10 combat mastery tasks. They are not League tasks: they award mastery points, not
 * League points, and do not appear in the tasks list or the standings.
 *
 * A player on a team shares the tasks with their partner, like any other task. The team's record is
 * a counter per task in [LeagueTeams], and each member's `point_unlock` varbits are a copy that
 * [sync] keeps in step, including for a partner who was offline when the task was done. A player
 * with no team keeps the tasks in their own varbits.
 */
class LeagueMasteryTasks
@Inject
constructor(private val teams: LeagueTeams, private val masteries: LeagueMasteryTracker) {
    /** Whether mastery task [slot] (1 to 10) is done for [player]. */
    fun isComplete(player: Player, slot: Int): Boolean {
        val team = teams.teamOf(player) ?: return player.vars[unlockVarBit(slot)] >= 1
        return teams.counter(team, counterName(slot)) >= 1
    }

    /**
     * Completes mastery task [slot] (1 to 10) for [player] and their team. Returns `false`, doing
     * nothing, if it was already done or [slot] is not a task.
     */
    fun complete(player: Player, slot: Int): Boolean {
        if (slot !in 1..MasteryData.TASK_COUNT || isComplete(player, slot)) {
            return false
        }

        val team = teams.teamOf(player)
        val members =
            if (team == null) {
                VarPlayerIntMapSetter.set(player, unlockVarBit(slot), 1)
                listOf(player)
            } else {
                teams.incrementCounter(team, counterName(slot), 1)
                teams.onlineMembers(team)
            }
        for (member in members) {
            sync(member)
            member.requestAutosave()
            member.mes(MasteryMessages.taskCompleted(slot, MasteryData.POINTS_PER_TASK))
        }
        return true
    }

    /**
     * Adds one to the [counter] progress varp, up to [goal], and completes mastery task [slot] when
     * the goal is reached. For a team the counter is a shared total, as it is for League tasks.
     * [counter] must be one of the league's progress varps, which are kept in step with the team.
     */
    fun addProgress(player: Player, slot: Int, counter: VarpType, goal: Int) {
        if (slot !in 1..MasteryData.TASK_COUNT || isComplete(player, slot)) {
            return
        }

        val team = teams.teamOf(player)
        val progress =
            if (team == null) {
                minOf(player.vars[counter] + 1, goal)
            } else {
                teams.incrementCounter(team, counter.internalNameValue, goal)
            }
        val members = if (team == null) listOf(player) else teams.onlineMembers(team)
        for (member in members) {
            VarPlayerIntMapSetter.set(member, counter, progress)
        }
        if (progress >= goal) {
            complete(player, slot)
        }
    }

    /**
     * Makes [player]'s `point_unlock` varbits match their team's record, then refreshes the point
     * totals the client shows. A player with no team keeps their own varbits as they are.
     */
    fun sync(player: Player) {
        val team = teams.teamOf(player)
        if (team != null) {
            for (slot in 1..MasteryData.TASK_COUNT) {
                val done = if (teams.counter(team, counterName(slot)) >= 1) 1 else 0
                VarPlayerIntMapSetter.set(player, unlockVarBit(slot), done)
            }
        }
        masteries.syncPoints(player)
    }

    /**
     * Clears the mastery tasks of [player]'s team, or of [player] with no team. Used by dev
     * commands.
     */
    fun reset(player: Player) {
        val team = teams.teamOf(player)
        val killCounter = league_progress.mastery_kills_100
        if (team != null) {
            for (slot in 1..MasteryData.TASK_COUNT) {
                teams.resetCounter(team, counterName(slot))
            }
            teams.resetCounter(team, killCounter.internalNameValue)
        }
        VarPlayerIntMapSetter.set(player, killCounter, 0)
        for (slot in 1..MasteryData.TASK_COUNT) {
            VarPlayerIntMapSetter.set(player, unlockVarBit(slot), 0)
        }
        masteries.syncPoints(player)
    }

    private fun unlockVarBit(slot: Int) = MasteryVarBits.point_unlock[slot - 1]

    private fun counterName(slot: Int) = "mastery_task_$slot"
}
