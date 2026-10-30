package org.rsmod.content.other.special.attacks.support

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

/**
 * Events that special attacks publish for content that owns something they touch. The poison and
 * venom a player suffers belong to whoever made them, so a special that cures or reads it asks.
 */
object SpecialPlayerEvents {
    /**
     * Published when a special attack takes the poison and venom away from [player]. A listener
     * that owns a sickness cures it and adds the damage its next hit would have dealt to
     * [damage].
     */
    class CureSickness(val player: Player) : UnboundEvent {
        /** The damage the cured sickness would have dealt next, added up. */
        var damage: Int = 0
    }
}
