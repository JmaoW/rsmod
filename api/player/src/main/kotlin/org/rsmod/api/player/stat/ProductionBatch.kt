package org.rsmod.api.player.stat

import org.rsmod.game.entity.Player

/** Content that lets a player do a whole production job (fletching, smithing...) in one go. */
public fun interface ProductionBatchRule {
    /** Whether [player] makes everything they asked for at once. */
    public fun batches(player: Player): Boolean
}

/**
 * How many actions of a production job happen at once. Without a [rule] (or for a player it does
 * not apply to) that is always one; with it, it is everything left, up to the skill's own cap.
 */
public object ProductionBatch {
    /** Installed by content at startup, such as the league's Production Master relic. */
    @Volatile public var rule: ProductionBatchRule? = null

    /** The cap for ammo-type jobs (arrows, bolts, darts), which are already made in sets. */
    public const val AMMO_CAP: Int = 10

    /** How many of the [remaining] actions [player] does in this one, at most [cap]. */
    public fun actions(player: Player, remaining: Int, cap: Int = Int.MAX_VALUE): Int {
        if (remaining <= 1 || rule?.batches(player) != true) {
            return 1
        }
        return minOf(remaining, cap)
    }

    /**
     * Does as many of a job's [remaining] actions as [actions] allows, now. [action] does one and
     * returns whether it could; its argument says whether it is the first, which is the only one
     * that should tell the player why it could not. Returns how many were done.
     */
    public inline fun make(
        player: Player,
        remaining: Int,
        cap: Int = Int.MAX_VALUE,
        action: (first: Boolean) -> Boolean,
    ): Int {
        val batch = actions(player, remaining, cap)
        var made = 0
        while (made < batch && action(made == 0)) {
            made++
        }
        return made
    }
}
