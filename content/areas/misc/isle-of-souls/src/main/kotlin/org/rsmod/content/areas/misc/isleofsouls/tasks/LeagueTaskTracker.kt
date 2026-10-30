package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_components
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.Team
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * Records league task completions the way the client expects to read them: a completion bit per
 * task id, the total completed count, and league points. Also tells the player about the change:
 * the two league chat messages, a task-complete pop-up, and a redraw of the Leagues tab.
 *
 * Players on a team share their tasks, counters and points. For them the team's record in
 * [LeagueTeams] is the truth, and each member's own league varps are a copy that this class keeps
 * in step: a completion by one member is applied to every online member straight away, and
 * [syncPlayer] brings a member up to date on login or when they change team. Players who are not on
 * a team keep their progress in their own varps, as before.
 */
class LeagueTaskTracker
@Inject
constructor(private val eventBus: EventBus, private val teams: LeagueTeams) {
    fun isComplete(player: Player, taskId: Int): Boolean {
        val team = teams.teamOf(player) ?: return hasBit(player, taskId)
        return teams.isComplete(team, taskId)
    }

    /** Completes [taskId] for [player], and for their whole team if they are on one. */
    fun complete(player: Player, taskId: Int) {
        val task = LeagueTaskList.byId[taskId] ?: return
        val team = teams.teamOf(player)
        if (team == null) {
            completeSolo(player, task)
            return
        }

        val first = !teams.completedByAnyTeam(taskId)
        if (!teams.complete(team, taskId)) {
            return
        }
        for (member in teams.onlineMembers(team)) {
            syncPlayer(member)
            member.requestAutosave()
            sendCompletionMessages(member, task, completedBy = player, shared = true)
            showCompletionPopup(member, task, completedBy = player.takeUnless { it === member })
        }
        publishCompleted(player, team, task, first)
    }

    /**
     * Adds one to the [counter] progress varp, up to [goal], and completes [taskId] when the goal
     * is reached. For a team the counter is a shared total, so each member's mining counts. Only
     * [player], who did the action, gets the "Task Progress" chat line, not their teammates. For a
     * task in a [LeagueTaskSeries] the line is only sent while it is the series' lowest unfinished
     * task.
     */
    fun addProgress(player: Player, taskId: Int, counter: VarpType, goal: Int) {
        if (isComplete(player, taskId)) {
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
        val shown = LeagueTaskSeries.shownTask(taskId) { isComplete(player, it) }
        val taskName = LeagueTaskList.byId[shown]?.name
        if (shown == taskId && taskName != null) {
            player.mes(LeagueChat.taskProgress(taskName, progress, goal))
        }
        if (progress >= goal) {
            complete(player, taskId)
        }
    }

    /**
     * Adds one to [counter] for [player], or to their team's shared total, up to [cap], without
     * completing a task or saying anything: for tasks that need several counters to reach a goal
     * (the caller checks them and completes the task). Returns the new count.
     */
    fun addCount(player: Player, counter: VarpType, cap: Int): Int {
        val team = teams.teamOf(player)
        val count =
            if (team == null) {
                minOf(player.vars[counter] + 1, cap)
            } else {
                teams.incrementCounter(team, counter.internalNameValue, cap)
            }
        val members = if (team == null) listOf(player) else teams.onlineMembers(team)
        for (member in members) {
            VarPlayerIntMapSetter.set(member, counter, count)
        }
        return count
    }

    /**
     * Marks [counter] as `1` for [player], or for their whole team, without completing a task: for
     * tasks that need several different things done (each is one counter), which the caller then
     * checks with [isMarked].
     */
    fun mark(player: Player, counter: VarpType) {
        val team = teams.teamOf(player)
        val value =
            if (team == null) 1 else teams.incrementCounter(team, counter.internalNameValue, 1)
        val members = if (team == null) listOf(player) else teams.onlineMembers(team)
        for (member in members) {
            VarPlayerIntMapSetter.set(member, counter, value)
        }
    }

    /** Whether [counter] was marked for [player] (see [mark]). */
    fun isMarked(player: Player, counter: VarpType): Boolean = player.vars[counter] >= 1

    /**
     * Sets the [counter] progress varp back to zero, for tasks that need a streak. Does nothing
     * once [taskId] is complete.
     */
    fun resetProgress(player: Player, taskId: Int, counter: VarpType) {
        if (isComplete(player, taskId)) {
            return
        }

        val team = teams.teamOf(player)
        if (team != null) {
            teams.resetCounter(team, counter.internalNameValue)
        }
        val members = if (team == null) listOf(player) else teams.onlineMembers(team)
        for (member in members) {
            VarPlayerIntMapSetter.set(member, counter, 0)
        }
    }

    /**
     * Makes a team member's league varps match their team's record: every task bit, the completed
     * count, both point totals and every counter. It only ever copies from the team to the player.
     * Returns `false`, changing nothing, if [player] is not on a team.
     */
    fun syncPlayer(player: Player): Boolean {
        val team = teams.teamOf(player) ?: return false

        val bits = IntArray(LeagueTaskVars.completed.size)
        var tasks = 0
        var points = 0
        for (taskId in teams.completed(team).keys) {
            val task = LeagueTaskList.byId[taskId] ?: continue
            val index = taskId / BITS_PER_VARP
            if (index >= bits.size) {
                continue
            }
            bits[index] = bits[index] or (1 shl (taskId % BITS_PER_VARP))
            tasks++
            points += task.tier.points
        }

        for ((index, varp) in LeagueTaskVars.completed.withIndex()) {
            VarPlayerIntMapSetter.set(player, varp, bits[index])
        }
        VarPlayerIntMapSetter.set(player, LeagueTaskBitVars.total_tasks_completed, tasks)
        VarPlayerIntMapSetter.set(player, LeagueTaskVars.points_completed, points)
        VarPlayerIntMapSetter.set(player, LeagueTaskVars.points_claimed, points)
        for (counter in LeagueProgressVarps.all) {
            val value = teams.counter(team, counter.internalNameValue)
            VarPlayerIntMapSetter.set(player, counter, value)
        }
        refreshLeaguePanel(player)
        return true
    }

    /** Tasks [team] has completed that exist in the task list. */
    fun taskCount(team: Team): Int = teams.completed(team).keys.count { it in LeagueTaskList.byId }

    /** League points [team] has earned: the sum of its completed tasks' tier points. */
    fun points(team: Team): Int =
        teams.completed(team).keys.sumOf { LeagueTaskList.byId[it]?.tier?.points ?: 0 }

    /**
     * Clears this player's own league varps. Used by dev commands. A team member's real progress is
     * the team's, which this does not touch (see [LeagueTeams.resetProgress]), and they are given
     * the team's progress back the next time they are synced.
     */
    fun reset(player: Player) {
        for (varp in LeagueTaskVars.completed) {
            VarPlayerIntMapSetter.set(player, varp, 0)
        }
        VarPlayerIntMapSetter.set(player, LeagueTaskBitVars.total_tasks_completed, 0)
        VarPlayerIntMapSetter.set(player, LeagueTaskVars.points_completed, 0)
        VarPlayerIntMapSetter.set(player, LeagueTaskVars.points_claimed, 0)
        for (counter in LeagueProgressVarps.all) {
            VarPlayerIntMapSetter.set(player, counter, 0)
        }
        refreshLeaguePanel(player)
    }

    private fun hasBit(player: Player, taskId: Int): Boolean {
        val varp = LeagueTaskVars.completed[taskId / BITS_PER_VARP]
        return (player.vars[varp] ushr (taskId % BITS_PER_VARP)) and 1 == 1
    }

    private fun completeSolo(player: Player, task: LeagueTask) {
        if (hasBit(player, task.id)) {
            return
        }

        val varp = LeagueTaskVars.completed[task.id / BITS_PER_VARP]
        val bits = player.vars[varp] or (1 shl (task.id % BITS_PER_VARP))
        VarPlayerIntMapSetter.set(player, varp, bits)

        val totalTasks = LeagueTaskBitVars.total_tasks_completed
        VarPlayerIntMapSetter.set(player, totalTasks, player.vars[totalTasks] + 1)

        addPoints(player, LeagueTaskVars.points_completed, task.tier.points)
        addPoints(player, LeagueTaskVars.points_claimed, task.tier.points)
        player.requestAutosave()

        sendCompletionMessages(player, task, completedBy = player, shared = false)
        refreshLeaguePanel(player)
        showCompletionPopup(player, task, completedBy = null)
        publishCompleted(player, team = null, task, first = false)
    }

    private fun publishCompleted(player: Player, team: Team?, task: LeagueTask, first: Boolean) {
        val points = task.tier.points
        val completed =
            LeagueTaskCompleted(player, team, task.id, task.name, task.tier.label, points, first)
        eventBus.publish(completed)
    }

    /**
     * The two chat lines for a completed task. [shared] is true for team members, whose total is
     * the team's, so the second line says "Your team now has" instead of "You now have".
     */
    private fun sendCompletionMessages(
        player: Player,
        task: LeagueTask,
        completedBy: Player,
        shared: Boolean,
    ) {
        val tier = task.tier.label
        val article = if (tier.first() in "aeiou") "an" else "a"
        val who =
            if (completedBy === player) {
                "you've"
            } else {
                "your teammate ${LeagueChat.blue(teams.nameOf(completedBy))} has"
            }
        val taskName = LeagueChat.blue(task.name)
        player.mes("Congratulations, $who completed $article $tier task: $taskName.")

        val earned = LeagueChat.points(task.tier.points)
        val total = LeagueChat.points(player.vars[LeagueTaskVars.points_completed])
        player.mes(
            if (shared) {
                "You have earned $earned. Your team now has $total."
            } else {
                "You have earned $earned. You now have $total in total."
            }
        )
    }

    /**
     * The Leagues tab only draws its progress bar and texts when it loads, and the client does not
     * watch the league variables for it, so re-run its update script whenever the tab is open.
     */
    fun refreshLeaguePanel(player: Player) {
        if (!player.ui.containsOverlay(isle_interfaces.league_side_panel)) {
            return
        }
        val points = player.vars[LeagueTaskVars.points_claimed]
        player.runClientScript(
            SIDE_PANEL_UPDATE_BAR_SCRIPT,
            isle_components.panel_fill.packed,
            points,
        )
    }

    /** [completedBy] is the teammate who did it, or `null` when the player did it themselves. */
    private fun showCompletionPopup(player: Player, task: LeagueTask, completedBy: Player?) {
        val notification = isle_interfaces.notification_display
        if (!player.ui.containsOverlay(notification)) {
            player.ifOpenOverlay(notification, isle_components.toplevel_notifications, eventBus)
        }
        val by =
            if (completedBy == null) {
                "<br>"
            } else {
                "<br>Completed by <col=$WHITE>${teams.nameOf(completedBy)}</col><br>"
            }
        val body =
            "Task Completed: <col=$WHITE>${task.name}</col>$by<br>" +
                "Points Earned: <col=$WHITE>${task.tier.points}</col>"
        player.runClientScript(NOTIFICATION_DISPLAY_SCRIPT, LEAGUE_COLOUR, POPUP_TITLE, body)
    }

    private fun addPoints(player: Player, varp: VarpType, points: Int) {
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] + points)
    }

    private companion object {
        const val BITS_PER_VARP = 32

        /** `league_side_panel_update_bar(component, points)`. */
        const val SIDE_PANEL_UPDATE_BAR_SCRIPT = 3226

        /** `notification_display_init(colour, title, body)`. */
        const val NOTIFICATION_DISPLAY_SCRIPT = 3343

        /** The Raging Echoes league theme colour (param `1027` of enum `2670`, league type 5). */
        const val LEAGUE_COLOUR = 0x20C2FE

        const val WHITE = "ffffff"

        const val POPUP_TITLE = "League Task Complete!"
    }
}
