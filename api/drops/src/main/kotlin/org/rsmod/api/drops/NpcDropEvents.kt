package org.rsmod.api.drops

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class NpcDropEvents {
    /**
     * Published before a monster's drop table is rolled for [hero]. Subscribers may raise or lower
     * [multiplier], which makes every chance-based drop that many times as likely.
     */
    public data class Modify(val npc: Npc, val hero: Player, var multiplier: Double = 1.0) :
        UnboundEvent

    /** Published after a monster's drop table was rolled, with what it dropped for [hero]. */
    public data class Rolled(val npc: Npc, val hero: Player, val drops: List<DropResult>) :
        UnboundEvent
}
