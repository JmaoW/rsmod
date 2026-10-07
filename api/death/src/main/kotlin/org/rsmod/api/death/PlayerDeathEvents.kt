package org.rsmod.api.death

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public class PlayerDeathEvents {
    /** Published when a player's death is processed, before the death sequence starts. */
    public data class Died(val player: Player) : UnboundEvent

    /**
     * Published when the death sequence is about to move the player to their respawn point.
     * [coords] starts as the default respawn; subscribers may replace it.
     */
    public data class Respawn(val player: Player, var coords: CoordGrid) : UnboundEvent
}
