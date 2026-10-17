package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.skills.slayer.configs.SlayerUnlocks
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerEvents
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Slayer side of two relics.
 *
 * Passives (any tier 3 relic): Slayer reward points are five times as many, the "Bigger and Badder"
 * unlock is free, and a superior comes up one time in [PASSIVES_SUPERIOR_ODDS] instead of one
 * in 200. (The 2x combat XP is in [LeagueRelicTracker].)
 *
 * Slayer Master (tier 5): the player is always on task for every monster they could be assigned
 * (they get its Slayer XP, the Slayer helmet's bonus and a chance of its superior without the
 * task), every Slayer unlock is theirs, cancelling and blocking tasks and the store's items are
 * free, and the 100th kill of each kind of Slayer monster gives bonus Slayer XP of 1,000 to 15,000
 * by the monster's Slayer level.
 *
 * The Slayer skill asks through [SlayerEngine.hooks], so it knows nothing of the relics.
 */
class LeagueSlayerRelics
@Inject
constructor(
    private val relics: LeagueRelicTracker,
    private val engine: SlayerEngine,
    private val xpMods: XpModifiers,
) : PluginScript() {
    fun hasPassives(player: Player): Boolean = relics.selected(player, PASSIVES_TIER) != 0

    fun isSlayerMaster(player: Player): Boolean =
        relics.selected(player, MASTER_TIER) == MASTER_OPTION

    override fun ScriptContext.startup() {
        val hooks = engine.hooks
        hooks.pointMultipliers += { if (hasPassives(it)) PASSIVES_POINTS else 1 }
        hooks.unlocked += { player, bit ->
            isSlayerMaster(player) ||
                (hasPassives(player) && bit == SlayerUnlocks.BIGGER_AND_BADDER)
        }
        hooks.free += { isSlayerMaster(it) }
        hooks.alwaysOnTask += { isSlayerMaster(it) }
        hooks.superiorOdds += { if (hasPassives(it)) PASSIVES_SUPERIOR_ODDS else null }

        onEvent<SlayerEvents.Kill> {
            if (isSlayerMaster(player)) {
                countKill(player, npc.visType.param(params.slayer_levelrequire), task.id)
            }
        }
    }

    /** Counts a kill of the kind of monster [taskId] covers and pays the bonus on the 100th. */
    private fun countKill(player: Player, slayerLevel: Int, taskId: Int) {
        val slot = SlayerKillCounters.slot(engine, taskId) ?: return
        val varp = SlayerKillCounters.varps[slot / SlayerKillCounters.PER_VARP]
        val shift = (slot % SlayerKillCounters.PER_VARP) * SlayerKillCounters.BITS
        val packed = player.vars[varp]
        val kills = (packed ushr shift) and SlayerKillCounters.MASK
        if (kills >= SlayerKillCounters.TARGET) {
            return
        }
        val next = kills + 1
        val cleared = packed and (SlayerKillCounters.MASK shl shift).inv()
        VarPlayerIntMapSetter.set(player, varp, cleared or (next shl shift))
        if (next == SlayerKillCounters.TARGET) {
            val bonus = bonusXp(slayerLevel)
            player.statAdvance(stats.slayer, bonus * xpMods.get(player, stats.slayer))
            val name = engine.tables.tasks[taskId]?.lowerName ?: "that monster"
            player.mes("You have slain $next $name, and gain $bonus bonus Slayer XP.")
        }
    }

    companion object {
        const val PASSIVES_TIER = 2
        const val MASTER_TIER = 4
        const val MASTER_OPTION = 3
        const val PASSIVES_POINTS = 5
        const val PASSIVES_SUPERIOR_ODDS = 50

        const val MIN_BONUS_XP = 1_000
        const val MAX_BONUS_XP = 15_000

        /** The bonus for the 100th kill: 1,000 at Slayer level 1, rising to 15,000 at 99. */
        fun bonusXp(slayerLevel: Int): Int {
            val level = slayerLevel.coerceIn(1, 99)
            return MIN_BONUS_XP + (MAX_BONUS_XP - MIN_BONUS_XP) * (level - 1) / 98
        }
    }
}

/**
 * Kills of each kind of Slayer monster, for the Slayer Master relic. A counter is seven bits (0 to
 * 100), four to a varp, in the order of the task ids.
 */
object SlayerKillCounters : VarpReferences() {
    const val BITS = 7
    const val MASK = (1 shl BITS) - 1
    const val PER_VARP = 4
    const val TARGET = 100
    const val VARP_COUNT = 21

    val varps = List(VARP_COUNT) { find("isle_slayer_unique_$it") }

    /** The counter position of the task with id [taskId], or `null` if it has none. */
    fun slot(engine: SlayerEngine, taskId: Int): Int? {
        val ids = engine.tables.tasks.keys.sorted()
        val index = ids.indexOf(taskId)
        return if (index in 0 until VARP_COUNT * PER_VARP) index else null
    }
}

internal object SlayerKillCounterBuilder : VarpBuilder() {
    init {
        for (i in 0 until SlayerKillCounters.VARP_COUNT) {
            build("isle_slayer_unique_$i") {}
        }
    }
}
