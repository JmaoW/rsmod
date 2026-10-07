package org.rsmod.game.entity.player

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public class SessionStateEvent {
    /**
     * Fired on the game thread when a brand new account is about to log in for the first time,
     * before its first map is built. [coords] starts as the realm's configured spawn; subscribers
     * may replace it to change where the new player appears.
     */
    public data class NewPlayerSpawn(val player: Player, var coords: CoordGrid) : UnboundEvent

    /** Fired when a player is registered to the player list. */
    public data class Initialize(val player: Player) : UnboundEvent

    /** Fired after [Initialize] during the player login sequence. */
    public data class Login(val player: Player) : UnboundEvent

    /** Fired after [Login] during the player login sequence. */
    public data class EngineLogin(val player: Player, override val id: Long = 0L) : KeyedEvent

    /** Fired before the player's account data is queued for saving. */
    public data class Logout(val player: Player) : UnboundEvent

    /** Fired when a player is unregistered from the player list. */
    public data class Delete(val player: Player) : UnboundEvent
}
