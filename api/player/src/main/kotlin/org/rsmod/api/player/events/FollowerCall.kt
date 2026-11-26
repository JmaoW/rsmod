package org.rsmod.api.player.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Published when [player] presses "Call follower" on the worn equipment tab. A follower system that
 * owns the player's follower sets [handled] (and says what happened). When nothing handles it, the
 * player is told they have no follower.
 */
public class FollowerCall(public val player: Player) : UnboundEvent {
    public var handled: Boolean = false
}
