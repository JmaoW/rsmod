package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Changes how far a player can attack a npc from, with their weapon or an autocast spell: [range]
 * is what the weapon and style give, and the result is used instead (at least 1). Spells cast by
 * hand are never changed.
 */
public fun interface AttackRangeModifier {
    public fun modify(player: Player, target: Npc, range: Int): Int
}

/** The [AttackRangeModifier]s content has registered, applied in the order they were added. */
@Singleton
public class AttackRangeModifiers @Inject constructor() {
    private val modifiers = ArrayList<AttackRangeModifier>()

    public fun register(modifier: AttackRangeModifier) {
        modifiers += modifier
    }

    public fun modify(player: Player, target: Npc, range: Int): Int {
        var result = range
        for (modifier in modifiers) {
            result = modifier.modify(player, target, result)
        }
        return maxOf(1, result)
    }
}
