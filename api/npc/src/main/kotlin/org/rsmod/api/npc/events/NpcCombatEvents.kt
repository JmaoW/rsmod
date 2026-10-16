package org.rsmod.api.npc.events

import org.rsmod.events.KeyedEvent
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder

public class NpcHitEvents {
    public data class Modify(
        public val npc: Npc,
        public val hit: HitBuilder,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent

    /**
     * Published for every incoming hit after the npc's own [Modify] handler, so that content which
     * applies to many npc types (Slayer rules, for one) needs no handler per type. [source] is the
     * player who dealt the hit, if it was one.
     */
    public class ModifyAny(
        public val npc: Npc,
        public val hit: HitBuilder,
        public val source: Player?,
    ) : UnboundEvent

    public data class Impact(
        public val npc: Npc,
        public val hit: Hit,
        override val id: Long = npc.visType.id.toLong(),
    ) : KeyedEvent
}
