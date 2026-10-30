package org.rsmod.api.player.hit.modifier

import java.util.concurrent.ConcurrentHashMap
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder

/**
 * Hit modifiers that content adds to every hit [StandardPlayerHitModifier] processes, after its
 * own protection prayer reduction. This is where effects that lessen the damage a player takes
 * (a staff's Power of Death, say) live, so that they apply to every source of damage.
 */
public object PlayerHitModifiers {
    private val modifiers = ConcurrentHashMap<String, PlayerHitModifier>()

    /**
     * Adds [modifier] to every hit taken from now on. A modifier registered under the same [key]
     * as an earlier one replaces it, so that registering again (a server that restarts its
     * scripts, a test) does not apply it twice.
     */
    public fun register(key: String, modifier: PlayerHitModifier) {
        modifiers[key] = modifier
    }

    internal fun HitBuilder.applyAll(target: Player) {
        for (modifier in modifiers.values) {
            with(modifier) { modify(target) }
        }
    }
}
