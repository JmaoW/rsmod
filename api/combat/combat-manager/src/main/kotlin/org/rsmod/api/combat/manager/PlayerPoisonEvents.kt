package org.rsmod.api.combat.manager

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

public class PlayerPoisonEvents {
    /**
     * Published when [source] poisons the player [target]. A poisoned player is poisoned the same
     * way whoever did it (the orb, antipoison, curing and saving belong to the player's own
     * poison), so whatever owns that poison listens for this and sets [applied].
     *
     * @property damage the damage of the first poison hit.
     */
    public class Apply(
        public val source: Player,
        public val target: Player,
        public val damage: Int,
    ) : UnboundEvent {
        /** Whether the poison took hold: `false` if the player is protected or already poisoned. */
        public var applied: Boolean = false
    }
}
