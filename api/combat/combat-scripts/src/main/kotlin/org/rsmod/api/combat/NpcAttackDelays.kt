package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Content that changes how long a player waits between attacks on a particular npc (a ratbane
 * weapon, which has no delay against giant rats). A change returns the delay to use instead of the
 * weapon's, or `null` to leave it.
 */
@Singleton
public class NpcAttackDelays @Inject constructor() {
    private val changes = ArrayList<(Player, Npc, Int) -> Int?>()

    public fun register(change: (player: Player, target: Npc, delay: Int) -> Int?) {
        changes += change
    }

    /** The delay [player] waits after attacking [target], given the weapon's [delay]. */
    public fun adjust(player: Player, target: Npc, delay: Int): Int {
        var result = delay
        for (change in changes) {
            result = change(player, target, result) ?: result
        }
        return result
    }
}
