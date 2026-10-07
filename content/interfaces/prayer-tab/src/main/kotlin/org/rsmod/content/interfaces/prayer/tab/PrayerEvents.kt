package org.rsmod.content.interfaces.prayer.tab

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player

object PrayerEvents {
    /** Published after [player] has switched on [prayer]. */
    class Enabled(val player: Player, val prayer: Prayer) : UnboundEvent
}
