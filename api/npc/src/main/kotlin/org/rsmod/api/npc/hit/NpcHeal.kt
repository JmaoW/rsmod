package org.rsmod.api.npc.hit

import org.rsmod.api.config.refs.hitmarks
import org.rsmod.api.config.refs.params
import org.rsmod.game.entity.Npc
import org.rsmod.game.headbar.Headbar
import org.rsmod.game.hit.Hitmark

/**
 * Heals this npc by [amount] hitpoints, up to [max], the way a mechanic does (a boss drinking
 * blood, a minion fed to it): everyone who can see it gets the purple heal hitsplat with the
 * hitpoints actually restored, and its health bar is redrawn. Natural regeneration does not go
 * through here, and neither does anything for players.
 *
 * Returns the hitpoints restored (0, with no hitsplat, when it was already at [max]).
 */
public fun Npc.healHitpoints(amount: Int, max: Int = baseHitpointsLvl, delay: Int = 0): Int {
    if (amount <= 0 || !isSlotAssigned) {
        return 0
    }
    val before = hitpoints
    val after = maxOf(before, minOf(max, before + amount))
    val healed = after - before
    if (healed <= 0) {
        return 0
    }
    hitpoints = after
    showHealHitmark(healed, delay)
    return healed
}

/** The purple heal hitsplat for [amount] on this npc, with its health bar redrawn to match. */
public fun Npc.showHealHitmark(amount: Int, delay: Int = 0) {
    if (amount <= 0 || !isSlotAssigned) {
        return
    }
    val mark = hitmarks.heal.id
    showHitmark(Hitmark.fromNoSource(mark, mark, mark, amount, delay))
    val headbar = visHeadbar(params.headbar)
    val maxHp = baseHitpointsLvl.coerceAtLeast(1)
    val fill = (hitpoints.coerceIn(0, maxHp) * headbar.segments) / maxHp
    showHeadbar(
        Headbar.fromNoSource(
            self = headbar.id,
            public = headbar.id,
            startFill = fill,
            endFill = fill,
            startTime = delay,
            endTime = delay,
        )
    )
}
