package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.content.skills.agility.configs.AgilityObjs
import org.rsmod.content.skills.agility.configs.ArenaObjs
import org.rsmod.game.entity.Player

/**
 * Completes the league's Agility tasks from the events the Agility skill publishes: an arena
 * ticket, a mark of grace picked up, and the graceful outfit worn. The level and experience tasks
 * are with the other skills' in [LeagueSkillTasks].
 *
 * The isle has no agility course or shortcut, so every task that needed one is not in the list. The
 * mark of grace task stays for when marks have a source again.
 */
class LeagueAgilityTasks @Inject constructor(private val tracker: LeagueTaskTracker) {
    /** [player] tagged a ticket dispenser, [streak] in a row. */
    fun onTagged(player: Player, streak: Int) {
        tracker.complete(player, TASK_ARENA_TICKET)
        if (streak == 1) {
            tracker.resetProgress(
                player,
                TASK_ARENA_STREAK,
                league_progress.agility_arena_streak_30,
            )
        }
        tracker.addProgress(player, TASK_ARENA_STREAK, league_progress.agility_arena_streak_30, 30)
    }

    fun onMarkTaken(player: Player) {
        tracker.complete(player, TASK_MARK_OF_GRACE)
    }

    /** "Equip a Full Graceful Outfit" (the six pieces worn at once) and "Equip a Pirate Hook". */
    fun checkGear(player: Player) {
        if (ArenaObjs.pirate_hook in player.worn) {
            tracker.complete(player, TASK_PIRATE_HOOK)
        }
        if (tracker.isComplete(player, TASK_GRACEFUL)) {
            return
        }
        if (
            AgilityObjs.graceful_pieces.all { (carried, worn) ->
                carried in player.worn || worn in player.worn
            }
        ) {
            tracker.complete(player, TASK_GRACEFUL)
        }
    }

    companion object {
        const val TASK_MARK_OF_GRACE = 386
        const val TASK_GRACEFUL = 769
        const val TASK_ARENA_TICKET = 638
        const val TASK_ARENA_STREAK = 641
        const val TASK_PIRATE_HOOK = 1576
    }
}
