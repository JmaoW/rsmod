package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.skills.slayer.scripts.SlayerEvents
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's Slayer tasks from the events the Slayer skill publishes: finishing a task,
 * kills on task, superiors, checking the task with a gem and making the helmet. The level and
 * experience tasks are with the other skills' in [LeagueSkillTasks].
 *
 * Left out: the quests (Demon Slayer, Vampyre Slayer), the Karuulm drake, the Grotesque Guardians,
 * the Slayer's staff (e) and the eternal ring, which have nothing on the isle that makes them.
 */
class LeagueSlayerTasks @Inject constructor(private val tracker: LeagueTaskTracker) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SlayerEvents.Checked> { tracker.complete(player, TASK_CHECK_TASK) }
        onEvent<SlayerEvents.Completed> { tracker.complete(player, TASK_ONE_TASK) }
        onEvent<SlayerEvents.HelmAssembled> { tracker.complete(player, TASK_HELM) }
        onEvent<SlayerEvents.Kill> {
            if (onTask) {
                tracker.addProgress(player, TASK_250_KILLS, slayer_progress.kills_250, 250)
                if (task.id == ABYSSAL_DEMONS) {
                    tracker.complete(player, TASK_ABYSSAL_DEMON)
                }
            }
        }
        onEvent<SlayerEvents.SuperiorKilled> { superior(player, onTask) }
    }

    private fun superior(player: Player, onTask: Boolean) {
        tracker.complete(player, TASK_SUPERIOR)
        tracker.addProgress(player, TASK_SUPERIOR_10, slayer_progress.superior_10, 10)
        tracker.addProgress(player, TASK_SUPERIOR_20, slayer_progress.superior_20, 20)
        tracker.addProgress(player, TASK_SUPERIOR_50, slayer_progress.superior_50, 50)
        if (onTask) {
            tracker.addProgress(player, TASK_ENCOUNTER_25, slayer_progress.encounter_25, 25)
            tracker.addProgress(player, TASK_ENCOUNTER_75, slayer_progress.encounter_75, 75)
            tracker.addProgress(player, TASK_ENCOUNTER_100, slayer_progress.encounter_100, 100)
        }
    }

    companion object {
        const val TASK_CHECK_TASK = 319
        const val TASK_ONE_TASK = 1524
        const val TASK_250_KILLS = 320
        const val TASK_SUPERIOR = 322
        const val TASK_SUPERIOR_10 = 326
        const val TASK_SUPERIOR_20 = 327
        const val TASK_SUPERIOR_50 = 330
        const val TASK_ENCOUNTER_25 = 96
        const val TASK_ENCOUNTER_75 = 98
        const val TASK_ENCOUNTER_100 = 99
        const val TASK_HELM = 917
        const val TASK_ABYSSAL_DEMON = 328

        /** The Slayer task id of abyssal demons. */
        const val ABYSSAL_DEMONS = 42
    }
}

internal typealias slayer_progress = LeagueSlayerVarps

/** Server-only counters for the Slayer tasks that need many repeats. Saved with the player. */
object LeagueSlayerVarps : VarpReferences() {
    val kills_250 = find("isle_count_slayer_kills_250")
    val superior_10 = find("isle_count_superior_10")
    val superior_20 = find("isle_count_superior_20")
    val superior_50 = find("isle_count_superior_50")
    val encounter_25 = find("isle_count_superior_task_25")
    val encounter_75 = find("isle_count_superior_task_75")
    val encounter_100 = find("isle_count_superior_task_100")
}

internal object LeagueSlayerVarpBuilder : VarpBuilder() {
    init {
        build("isle_count_slayer_kills_250") {}
        build("isle_count_superior_10") {}
        build("isle_count_superior_20") {}
        build("isle_count_superior_50") {}
        build("isle_count_superior_task_25") {}
        build("isle_count_superior_task_75") {}
        build("isle_count_superior_task_100") {}
    }
}
