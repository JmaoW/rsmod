package org.rsmod.api.stats.plugin

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/**
 * Stats whose boost does not wear off over time, such as the ones a divine potion holds for five
 * minutes. [StatRegenScript] skips a held stat when it lowers boosted stats back towards their base
 * level.
 *
 * Whatever holds a stat is also responsible for releasing it, and for bringing the stat back down
 * if it should not keep the boost.
 */
@Singleton
public class StatBoostHolds @Inject constructor() {
    private val held = WeakHashMap<Player, MutableSet<Int>>()

    public fun hold(player: Player, stat: StatType) {
        held.getOrPut(player) { HashSet() }.add(stat.id)
    }

    public fun release(player: Player, stat: StatType) {
        val stats = held[player] ?: return
        stats.remove(stat.id)
        if (stats.isEmpty()) {
            held.remove(player)
        }
    }

    public fun isHeld(player: Player, stat: StatType): Boolean =
        held[player]?.contains(stat.id) == true
}
