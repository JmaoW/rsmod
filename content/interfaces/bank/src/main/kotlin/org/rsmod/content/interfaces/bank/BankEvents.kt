package org.rsmod.content.interfaces.bank

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Events about a player's bank screen, for content that needs to react to it. The engine allows
 * only one handler per interface open or close, and the bank module owns those, so others listen
 * for these instead.
 */
class BankEvents {
    /** Published as the bank screen opens, before the bank starts sending items to the client. */
    data class Opened(val player: Player) : UnboundEvent

    /**
     * Published as the bank screen closes, including when the player logs out. The bank tidies
     * itself a moment later (it moves items to fill gaps), so tab sizes can still change after
     * this.
     */
    data class Closed(val player: Player) : UnboundEvent
}
