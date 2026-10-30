package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.vorkath.Vorkath
import org.rsmod.content.areas.misc.isleofsouls.vorkath.vorkath_npcs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's two Vorkath tasks that are not a plain kill count (those are
 * [LeagueKillTasks]): "Defeat Vorkath 15 Times Without Leaving" and "Defeat Vorkath 5 Times Without
 * Special Damage" (in a row, without leaving, without any damage from the acid, the rapid fire or
 * the spawn's explosion; once hurt, the player has to leave the crater to try again).
 *
 * Both count every Vorkath that dies while the player is in their team's crater, whoever gets the
 * drops: the whole team fights it (see [Vorkath.visitsAt]). Left out: the combat achievements task.
 */
class LeagueVorkathTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val vorkath: Vorkath) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDeathEvents.Killed> {
            if (npc.type.id != vorkath_npcs.boss.id) {
                return@onEvent
            }
            for ((player, visit) in vorkath.visitsAt(npc)) {
                if (visit.kills >= WITHOUT_LEAVING) {
                    tracker.complete(player, TASK_WITHOUT_LEAVING)
                } else if (!tracker.isComplete(player, TASK_WITHOUT_LEAVING)) {
                    player.mes(progress(TASK_WITHOUT_LEAVING, visit.kills, WITHOUT_LEAVING))
                }
                if (visit.streak >= WITHOUT_SPECIAL_DAMAGE && !visit.failed) {
                    tracker.complete(player, TASK_WITHOUT_SPECIAL_DAMAGE)
                }
            }
        }
    }

    private fun progress(taskId: Int, count: Int, goal: Int): String {
        val name = LeagueTaskList.byId[taskId]?.name ?: return ""
        return "${LeagueChat.blue("Task Progress:")} $name: $count/$goal"
    }

    companion object {
        const val TASK_WITHOUT_LEAVING = 605
        const val TASK_WITHOUT_SPECIAL_DAMAGE = 602
        const val WITHOUT_LEAVING = 15
        const val WITHOUT_SPECIAL_DAMAGE = 5
    }
}
