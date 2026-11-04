package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Player

/**
 * The league tasks the Colosseum completes. The equip tasks are `LeagueGearTasks`', the Sol Heredit
 * kill counts `LeagueKillTasks`'. "Complete wave N" counts that wave or any later one, so a
 * shortened run's wave 7 completes "wave 6" (user decision).
 */
class ColosseumCompletions @Inject constructor(private val tracker: LeagueTaskTracker) {
    fun waveCleared(player: Player, wave: Int) {
        for ((from, task) in WAVE_TASKS) {
            if (wave >= from) {
                tracker.complete(player, task)
            }
        }
    }

    fun glory(player: Player, best: Int) {
        for ((needed, task) in GLORY_TASKS) {
            if (best >= needed) {
                tracker.complete(player, task)
            }
        }
    }

    fun bankChest(player: Player) {
        tracker.complete(player, TASK_BANK_CHEST)
    }

    fun teleported(player: Player) {
        tracker.complete(player, TASK_TELEPORT)
    }

    companion object {
        const val TASK_TELEPORT = 1655
        const val TASK_BANK_CHEST = 1662
        val WAVE_TASKS = listOf(1 to 1664, 6 to 1665, 12 to 1666)
        val GLORY_TASKS = listOf(20_000 to 1667, 40_000 to 1668, 58_000 to 1669)
    }
}
