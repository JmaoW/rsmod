package org.rsmod.api.combat.formulas

import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.varps
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.UnpackedNpcType

/**
 * Hit chance formulas internally use decimals (e.g., `1%` = `0.01`, `100%` = `1.0`). To maintain
 * consistency with other combat formulas that use whole integers, we scale them using this
 * constant.
 */
internal const val HIT_CHANCE_SCALE: Int = 10_000

internal fun scale(base: Int, multiplier: Int, divisor: Int): Int = (base * multiplier) / divisor

/**
 * Whether this npc is what [player] was sent to kill: it carries the Slayer task id the player has
 * (`slayer_target`), or one of the ids an umbrella task covers, and the task is not finished.
 */
internal fun UnpackedNpcType.isSlayerTask(player: Player): Boolean {
    if (SlayerTaskOverrides.checks.any { it(player, this) }) {
        return true
    }
    val task = player.vars[varps.slayer_target]
    if (task == 0 || player.vars[varps.slayer_count] <= 0) {
        return false
    }
    val own = param(params.slayer_task_id)
    return own == task || own in (SLAYER_TASK_GROUPS[task] ?: emptySet())
}

/**
 * Content that makes a player count as on task for a monster they were not assigned (the Slayer
 * Master relic) registers a check here.
 */
public object SlayerTaskOverrides {
    public val checks: MutableList<(Player, UnpackedNpcType) -> Boolean> =
        java.util.concurrent.CopyOnWriteArrayList()
}

/**
 * Tasks that cover monsters with task ids of their own. Metal dragons (127) are bronze (58), iron
 * (59), steel (60), mithril (93), adamant (108) and rune (109) dragons; this mirrors the Slayer
 * module's `GROUPS`.
 */
private val SLAYER_TASK_GROUPS: Map<Int, Set<Int>> = mapOf(127 to setOf(58, 59, 60, 93, 108, 109))
