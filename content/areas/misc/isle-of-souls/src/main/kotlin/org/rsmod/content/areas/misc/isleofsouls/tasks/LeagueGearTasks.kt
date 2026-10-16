package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.inferno.inferno_objs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's "Equip ..." tasks: the fire cape, metal weapons, metal armour sets, and
 * the elemental staffs. Only gear that the isle can make or hand out is covered.
 */
class LeagueGearTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val objTypes: ObjTypeList) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onWearposChange { check(player) }
        onPlayerLogin { check(player) }
    }

    private fun check(player: Player) {
        val worn = wornNames(player)

        if (player.worn.objs.any { it != null && it.isType(cave_objs.fire_cape) }) {
            tracker.complete(player, TASK_FIRE_CAPE)
        }
        if (player.worn.objs.any { it != null && it.isType(inferno_objs.infernal_cape) }) {
            tracker.complete(player, TASK_INFERNAL_CAPE)
        }

        for (metal in metalSets) {
            if (worn.any { name -> weapons.any { name == "${metal.prefix}_$it" } }) {
                tracker.complete(player, metal.weaponTask)
            }
            val setTask = metal.setTask
            if (setTask != null && hasFullSet(worn, metal.prefix)) {
                tracker.complete(player, setTask)
            }
        }

        if (worn.any { it in elementalStaffs }) {
            tracker.complete(player, TASK_ELEMENTAL_STAFF)
        }
        if (worn.any { it in battlestaffs }) {
            tracker.complete(player, TASK_ELEMENTAL_BATTLESTAFF)
        }

        // "Equip some Steel armour": any one piece will do.
        if (worn.any { it in steelPieces }) {
            tracker.complete(player, TASK_STEEL_ARMOUR)
        }
    }

    /** A platebody, a full helm, and either platelegs or a plateskirt. */
    private fun hasFullSet(worn: Set<String>, prefix: String): Boolean =
        "${prefix}_platebody" in worn &&
            "${prefix}_full_helm" in worn &&
            ("${prefix}_platelegs" in worn || "${prefix}_plateskirt" in worn)

    private fun wornNames(player: Player): Set<String> =
        player.worn.objs.mapNotNullTo(mutableSetOf()) { obj ->
            obj?.let { objTypes[it].internalName }
        }

    /** The task ids for one metal; [setTask] is `null` where the list has no full-set task. */
    private class Metal(val prefix: String, val weaponTask: Int, val setTask: Int?)

    private companion object {
        const val TASK_FIRE_CAPE = 653
        const val TASK_INFERNAL_CAPE = 654
        const val TASK_STEEL_ARMOUR = 1072
        const val TASK_ELEMENTAL_STAFF = 430
        const val TASK_ELEMENTAL_BATTLESTAFF = 433

        val elementalStaffs = setOf("air", "water", "earth", "fire").map { "staff_of_$it" }.toSet()

        /** An elemental battlestaff, or the mystic staff of the same element. */
        val battlestaffs =
            setOf("air", "water", "earth", "fire")
                .flatMap { listOf("${it}_battlestaff", "mystic_${it}_staff") }
                .toSet()

        /** The anvil's fighting weapons. Axes and pickaxes are tools, so they do not count. */
        val weapons =
            listOf(
                "dagger",
                "sword",
                "scimitar",
                "longsword",
                "2h_sword",
                "mace",
                "warhammer",
                "battleaxe",
                "claws",
            )

        val metalSets =
            listOf(
                Metal("mithril", weaponTask = 412, setTask = 417),
                Metal("adamant", weaponTask = 413, setTask = 418),
                Metal("rune", weaponTask = 414, setTask = 419),
            )

        val steelPieces = setOf("steel_platebody", "steel_platelegs", "steel_full_helm")
    }
}
