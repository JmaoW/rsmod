package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's "Equip ..." tasks for the theatre's rewards: the Avernic defender, the
 * Sanguinesti staff, the Scythe of Vitur, a Justiciar piece and the full set, and the Ghrazi
 * rapier. Charged, uncharged and ornamented forms count as the item they are made from. The two
 * ornament kit tasks complete when the kit is attached ([TobRewardItems]).
 */
class TobGearTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val objTypes: ObjTypeList) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onWearposChange { check(player) }
        onPlayerLogin { check(player) }
    }

    private fun check(player: Player) {
        val worn =
            player.worn.objs.mapNotNullTo(mutableSetOf()) { obj ->
                obj?.let { objTypes[it].internalName }
            }
        if (worn.any { it.startsWith("infernal_defender") }) {
            tracker.complete(player, TASK_AVERNIC)
        }
        if (worn.any { "sanguinesti_staff" in it }) {
            tracker.complete(player, TASK_STAFF)
        }
        if (
            worn.any {
                "scythe_of_vitur" in it && !it.startsWith("corrupted") && !it.startsWith("br_")
            }
        ) {
            tracker.complete(player, TASK_SCYTHE)
        }
        val pieces = JUSTICIAR.map { piece -> worn.any { it.startsWith(piece) } }
        if (pieces.any { it }) {
            tracker.complete(player, TASK_JUSTICIAR_PIECE)
        }
        if (pieces.all { it }) {
            tracker.complete(player, TASK_JUSTICIAR_SET)
        }
        if (worn.any { "ghrazi_rapier" in it && !it.startsWith("br_") }) {
            tracker.complete(player, TASK_RAPIER)
        }
    }

    companion object {
        val JUSTICIAR =
            listOf("justiciar_faceguard", "justiciar_chestguard", "justiciar_leg_guards")

        const val TASK_AVERNIC = 940
        const val TASK_STAFF = 941
        const val TASK_SCYTHE = 942
        const val TASK_JUSTICIAR_PIECE = 943
        const val TASK_JUSTICIAR_SET = 944
        const val TASK_RAPIER = 945
        const val TASK_SANGUINE_KIT = 1135
        const val TASK_HOLY_KIT = 1136
    }
}
