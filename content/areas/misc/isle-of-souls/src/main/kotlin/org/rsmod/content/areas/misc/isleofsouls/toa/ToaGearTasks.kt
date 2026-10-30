package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's "Equip ..." tasks for the raid's rewards: the Elidinis' ward, a piece of
 * Masori armour and the full set, Osmumten's fang and its ornament, and Tumeken's shadow. The
 * fortified and ornament forms count as the item they are made from.
 */
class ToaGearTasks
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
        if (worn.any { it.startsWith("elidinis_ward") }) {
            tracker.complete(player, TASK_WARD)
        }
        val pieces = MASORI.map { piece -> worn.any { it == piece || it == "${piece}_fortified" } }
        if (pieces.any { it }) {
            tracker.complete(player, TASK_MASORI_PIECE)
        }
        if (pieces.all { it }) {
            tracker.complete(player, TASK_MASORI_SET)
        }
        if ("osmumtens_fang" in worn || "osmumtens_fang_ornament" in worn) {
            tracker.complete(player, TASK_FANG)
        }
        if ("osmumtens_fang_ornament" in worn) {
            tracker.complete(player, TASK_FANG_ORNAMENT)
        }
        if ("tumekens_shadow" in worn) {
            tracker.complete(player, TASK_SHADOW)
        }
    }

    private companion object {
        val MASORI = listOf("masori_mask", "masori_body", "masori_chaps")

        const val TASK_WARD = 1228
        const val TASK_MASORI_PIECE = 1230
        const val TASK_MASORI_SET = 1231
        const val TASK_FANG = 1232
        const val TASK_FANG_ORNAMENT = 1233
        const val TASK_SHADOW = 1236
    }
}
