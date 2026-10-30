package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.dagannothkings.DagannothKings
import org.rsmod.content.areas.misc.isleofsouls.dagannothkings.DkKing
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.varp.VarpType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's Dagannoth Kings tasks: all three kings in one visit to the lair, each king
 * 50, 150 and 300 times, and equipping each of their four rings (and every one of them, which is
 * done once each ring's own task is, since only one ring can be worn at a time). As with every kill
 * task, a kill goes to the player who gets the drops, and to their team.
 *
 * "Each king N times" keeps one count per king (shared by the team, and capped at 300), and a task
 * is done once the lowest of the three reaches its goal. "Without leaving" counts the kings the
 * player killed since they last climbed down (see [DagannothKings.killsThisVisit]). The imbued
 * rings count as the plain ones. Left out: Trade with Bardur (no Bardur on the isle) and the combat
 * achievements task.
 */
class LeagueDagannothKingTasks
@Inject
constructor(
    private val tracker: LeagueTaskTracker,
    private val dks: DagannothKings,
    private val objTypes: ObjTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            val king = DkKing.of(npc) ?: return@onEvent
            kill(hero, king)
        }
        onWearposChange { checkRings(player) }
        onPlayerLogin { checkRings(player) }
    }

    /** [player] (or their team) killed [king]. */
    fun kill(player: Player, king: DkKing) {
        if (dks.killsThisVisit(player).containsAll(DkKing.entries)) {
            tracker.complete(player, TASK_WITHOUT_LEAVING)
        }

        tracker.addCount(player, counterOf(king), LADDER.last().second)
        val counts = DkKing.entries.associateWith { player.vars[counterOf(it)] }
        val lowest = counts.values.min()
        for ((task, goal) in LADDER) {
            if (lowest >= goal) {
                tracker.complete(player, task)
            }
        }

        val next = LADDER.firstOrNull { (task, _) -> !tracker.isComplete(player, task) } ?: return
        val name = LeagueTaskList.byId[next.first]?.name ?: return
        player.mes(progressMessage(name, counts, next.second))
    }

    private fun checkRings(player: Player) {
        val worn =
            player.worn.objs.mapNotNullTo(mutableSetOf()) { obj ->
                obj?.let { objTypes[it].internalName?.let(::plainRing) }
            }
        for ((ring, task) in RINGS) {
            if (ring in worn) {
                tracker.complete(player, task)
            }
        }
        // There is one ring slot: "every ring" is each of the four rings' own tasks done.
        if (RINGS.values.all { tracker.isComplete(player, it) }) {
            tracker.complete(player, TASK_EVERY_RING)
        }
    }

    companion object {
        const val TASK_WITHOUT_LEAVING = 596
        const val TASK_EVERY_RING = 597

        /** "Defeat Each Dagannoth King N Times": the task and its N. */
        val LADDER = listOf(613 to 50, 614 to 150, 615 to 300)

        /** Each ring by cache name, and its "Equip a ... Ring" task. */
        val RINGS =
            mapOf(
                "berzerker_ring" to 599,
                "warrior_ring" to 601,
                "seer_ring" to 604,
                "ranger_ring" to 607,
            )

        /** The imbued rings (Nightmare Zone, Soul Wars) are the plain ring with a prefix. */
        private val IMBUED_PREFIXES = listOf("nzone_", "sw_")

        fun plainRing(internalName: String): String =
            IMBUED_PREFIXES.firstOrNull { internalName.startsWith(it) }
                ?.let { internalName.removePrefix(it) } ?: internalName

        fun counterOf(king: DkKing): VarpType =
            when (king) {
                DkKing.Rex -> league_progress.kill_dk_rex
                DkKing.Prime -> league_progress.kill_dk_prime
                DkKing.Supreme -> league_progress.kill_dk_supreme
            }

        fun progressMessage(taskName: String, counts: Map<DkKing, Int>, goal: Int): String {
            val each =
                counts.entries.joinToString(", ") { (king, count) -> "$king ${minOf(count, goal)}" }
            return "${LeagueChat.blue("Task Progress:")} $taskName: $each (of $goal)"
        }
    }
}
