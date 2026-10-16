package org.rsmod.content.areas.misc.isleofsouls.tasks

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.dungeons.GodWarsNames
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.varp.VarpType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Completes the league's God Wars tasks: defeating each of the four generals, defeating any one of
 * them 100, 250 and 500 times, equipping the dungeon's gear, and getting an ecumenical key (that
 * one is in `GodWarsKeyScript`).
 *
 * Equipment is matched by cache name, with the ornament-kit versions counting as the plain item.
 * Left out: the Nex tasks, the Wilderness dungeon, Equip Every Godsword (it needs the Ancient
 * godsword from Nex), the combat achievements and the Saradomin's light task.
 */
class LeagueGodWarsTasks
@Inject
constructor(private val tracker: LeagueTaskTracker, private val objTypes: ObjTypeList) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            val name = npc.visType.internalName ?: return@onEvent
            val boss = GENERALS[name] ?: return@onEvent
            kill(hero, boss)
        }
        onWearposChange { check(player) }
        onPlayerLogin { check(player) }
    }

    private fun kill(player: Player, boss: General) {
        tracker.complete(player, boss.task)
        tracker.addProgress(player, TASK_100, boss.hundred, 100)
        tracker.addProgress(player, TASK_250, boss.twoFifty, 250)
        tracker.addProgress(player, TASK_500, boss.fiveHundred, 500)
    }

    private fun check(player: Player) {
        val worn =
            player.worn.objs.mapNotNullTo(mutableSetOf()) { obj ->
                obj?.let { objTypes[it].internalName?.removeSuffix("_ornament") }
            }

        if (worn.any { it in GODSWORDS }) {
            tracker.complete(player, TASK_GODSWORD)
        }
        if ("saradomin_sword" in worn) {
            tracker.complete(player, TASK_SARADOMIN_SWORD)
        }
        if ("zamorak_spear" in worn) {
            tracker.complete(player, TASK_ZAMORAKIAN_SPEAR)
        }
        if ("acb" in worn) {
            tracker.complete(player, TASK_CROSSBOW)
        }
        if ("sotd" in worn) {
            tracker.complete(player, TASK_STAFF_OF_THE_DEAD)
        }
        set(player, worn, ARMADYL_SET, TASK_ARMADYL_PIECE, TASK_ARMADYL_SET)
        set(player, worn, BANDOS_SET, TASK_BANDOS_PIECE, TASK_BANDOS_SET)
    }

    private fun set(player: Player, worn: Set<String>, pieces: Set<String>, piece: Int, full: Int) {
        val count = pieces.count { it in worn }
        if (count >= 1) {
            tracker.complete(player, piece)
        }
        if (count == pieces.size) {
            tracker.complete(player, full)
        }
    }

    /** A general: the task for defeating it and its three counters for the "any boss" tasks. */
    private class General(
        val task: Int,
        val hundred: VarpType,
        val twoFifty: VarpType,
        val fiveHundred: VarpType,
    )

    companion object {
        const val TASK_KRIL = 788
        const val TASK_GRAARDOR = 793
        const val TASK_KREE = 797
        const val TASK_ZILYANA = 801
        const val TASK_100 = 809
        const val TASK_250 = 810
        const val TASK_500 = 811

        const val TASK_SARADOMIN_SWORD = 794
        const val TASK_ZAMORAKIAN_SPEAR = 789
        const val TASK_ARMADYL_PIECE = 790
        const val TASK_ARMADYL_SET = 791
        const val TASK_BANDOS_PIECE = 795
        const val TASK_BANDOS_SET = 796
        const val TASK_CROSSBOW = 798
        const val TASK_STAFF_OF_THE_DEAD = 784
        const val TASK_GODSWORD = 802

        private val GODSWORDS = setOf("ags", "bgs", "sgs", "zgs", "agsg", "bgsg", "sgsg", "zgsg")

        private val ARMADYL_SET = setOf("armadyl_helmet", "armadyl_chestplate", "armadyl_skirt")
        private val BANDOS_SET = setOf("bandos_chestplate", "bandos_skirt", "bandos_boots")

        private val GENERALS =
            mapOf(
                GodWarsNames.KRIL to
                    General(
                        TASK_KRIL,
                        GwdProgress.kril_100,
                        GwdProgress.kril_250,
                        GwdProgress.kril_500,
                    ),
                GodWarsNames.GRAARDOR to
                    General(
                        TASK_GRAARDOR,
                        GwdProgress.graardor_100,
                        GwdProgress.graardor_250,
                        GwdProgress.graardor_500,
                    ),
                GodWarsNames.KREE to
                    General(
                        TASK_KREE,
                        GwdProgress.kree_100,
                        GwdProgress.kree_250,
                        GwdProgress.kree_500,
                    ),
                GodWarsNames.ZILYANA to
                    General(
                        TASK_ZILYANA,
                        GwdProgress.zilyana_100,
                        GwdProgress.zilyana_250,
                        GwdProgress.zilyana_500,
                    ),
            )
    }
}

/** Server-only kill counters for the "defeat any boss N times" tasks. Saved with the player. */
object GwdProgress : VarpReferences() {
    val kril_100 = find("isle_count_gwd_kril_100")
    val kril_250 = find("isle_count_gwd_kril_250")
    val kril_500 = find("isle_count_gwd_kril_500")
    val graardor_100 = find("isle_count_gwd_graardor_100")
    val graardor_250 = find("isle_count_gwd_graardor_250")
    val graardor_500 = find("isle_count_gwd_graardor_500")
    val kree_100 = find("isle_count_gwd_kree_100")
    val kree_250 = find("isle_count_gwd_kree_250")
    val kree_500 = find("isle_count_gwd_kree_500")
    val zilyana_100 = find("isle_count_gwd_zilyana_100")
    val zilyana_250 = find("isle_count_gwd_zilyana_250")
    val zilyana_500 = find("isle_count_gwd_zilyana_500")
}

internal object GwdProgressVarpBuilder : VarpBuilder() {
    init {
        for (boss in listOf("kril", "graardor", "kree", "zilyana")) {
            for (goal in listOf(100, 250, 500)) {
                build("isle_count_gwd_${boss}_$goal") {}
            }
        }
    }
}
