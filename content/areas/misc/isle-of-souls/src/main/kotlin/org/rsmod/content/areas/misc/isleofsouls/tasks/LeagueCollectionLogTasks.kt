package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.interfaces.collectionlog.CollectionLogSlotFilled
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's collection log tasks: "N Collection log slots" and "Fill N <tier> Clue
 * Collection Log Slots". The log is each player's own, but tasks are shared by a team, so a task is
 * done as soon as any one member's log reaches the number (the tracker hands it to the team).
 *
 * The tier tasks count the tier's "(Rare)" category too, where the log has one. The clue tiers
 * cannot be filled until clue caskets exist; their tasks are in the list so they complete the
 * moment they can.
 */
class LeagueCollectionLogTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val log: CollectionLog) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<CollectionLogSlotFilled> { check(player) }
        onPlayerLogin { check(player) }
    }

    /** Completes every task [player]'s log has reached. */
    internal fun check(player: Player) {
        val total = log.slotCounts(player).sum()
        for (task in totalTasks) {
            if (total >= task.slots) {
                tracker.complete(player, task.taskId)
            }
        }
        for (task in tierTasks) {
            val needed =
                task.slots ?: task.categories.sumOf { log.catalog.category(it)?.objs?.size ?: 0 }
            if (log.filled(player, task.categories) >= needed) {
                tracker.complete(player, task.taskId)
            }
        }
    }

    /** A task for [slots] slots anywhere in the log. */
    internal data class TotalTask(val taskId: Int, val slots: Int)

    /**
     * A task for [slots] slots in [categories] (category struct ids), or for every slot there if
     * [slots] is `null`.
     */
    internal data class TierTask(val taskId: Int, val categories: List<Int>, val slots: Int?)

    internal companion object {
        private val BEGINNER = listOf(593)
        private val EASY = listOf(508)
        private val MEDIUM = listOf(509)
        private val HARD = listOf(510, 2869)
        private val ELITE = listOf(511, 2870)
        private val MASTER = listOf(512, 2871)

        val totalTasks =
            listOf(
                TotalTask(1448, 5),
                TotalTask(1449, 15),
                TotalTask(1450, 30),
                TotalTask(1451, 50),
                TotalTask(1452, 100),
                TotalTask(1453, 200),
                TotalTask(1454, 350),
                TotalTask(1455, 500),
                TotalTask(1456, 750),
            )

        val tierTasks =
            listOf(
                TierTask(39, BEGINNER, 5),
                TierTask(40, BEGINNER, 10),
                TierTask(37, BEGINNER, null),
                TierTask(38, EASY, 5),
                TierTask(35, EASY, 20),
                TierTask(36, EASY, 50),
                TierTask(33, MEDIUM, 5),
                TierTask(34, MEDIUM, 20),
                TierTask(31, MEDIUM, 40),
                TierTask(32, HARD, 3),
                TierTask(29, HARD, 15),
                TierTask(30, HARD, 30),
                TierTask(27, ELITE, 3),
                TierTask(28, ELITE, 10),
                TierTask(8, ELITE, 25),
                TierTask(9, MASTER, 5),
                TierTask(10, MASTER, 25),
            )
    }
}
