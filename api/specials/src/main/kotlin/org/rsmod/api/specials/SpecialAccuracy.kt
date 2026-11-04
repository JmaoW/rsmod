package org.rsmod.api.specials

import org.rsmod.game.entity.Player

/** Content that changes how accurate special attacks are, such as a league relic. */
public interface SpecialAccuracyRule {
    /** What [player]'s special attack accuracy rolls are multiplied by (`1.0` for no change). */
    public fun multiplier(player: Player): Double

    /** Called each time one of [player]'s special attack accuracy rolls fails. */
    public fun missed(player: Player)
}

/** The [SpecialAccuracyRule] in use, installed by content at startup. `null` when there is none. */
public object SpecialAccuracy {
    @Volatile public var rule: SpecialAccuracyRule? = null

    internal fun multiplier(player: Player): Double = rule?.multiplier(player) ?: 1.0

    internal fun result(player: Player, hit: Boolean): Boolean {
        if (!hit) {
            rule?.missed(player)
        }
        return hit
    }
}
