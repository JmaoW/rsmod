package org.rsmod.api.drops

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

public class NpcDropEvents {
    /**
     * Published before a monster's drop table is rolled for [hero]. Subscribers may raise or lower
     * [multiplier], which makes every chance-based drop that many times as likely, and may set
     * [countMultipliers] to give more (or fewer) of an item than its table rolled.
     */
    public data class Modify(val npc: Npc, val hero: Player, var multiplier: Double = 1.0) :
        UnboundEvent {
        /** Items to multiply the rolled count of, keyed by item. Items not listed are unchanged. */
        public val countMultipliers: MutableMap<ObjType, Double> = HashMap()

        /**
         * The variant of the monster's table to roll for [hero], if it has one. `null` rolls the
         * usual table.
         */
        public var variant: DropVariant? = null

        /**
         * Drops [hero] gets on top of the roll, such as a boss's reward for a kill count. One is
         * left out when the roll already gave that item, so the two never stack.
         */
        public val guaranteed: MutableList<DropResult> = ArrayList()

        /** How many of [drop] to give after [countMultipliers] has been applied. */
        public fun countOf(drop: DropResult): Int {
            val factor = countMultipliers[drop.obj] ?: return drop.count
            return (drop.count * factor).toInt()
        }
    }

    /**
     * Published after a monster's drop table was rolled and before it is dropped. A subscriber that
     * takes drops for [hero] (the bracelet of ethereum absorbing revenant ether) removes them from
     * [drops]: only what is left is put on the ground. [Rolled] and the loot tracker still list the
     * whole roll.
     */
    public class Claim(
        public val npc: Npc,
        public val hero: Player,
        public val drops: MutableList<DropResult>,
    ) : UnboundEvent

    /** Published after a monster's drop table was rolled, with what it dropped for [hero]. */
    public data class Rolled(val npc: Npc, val hero: Player, val drops: List<DropResult>) :
        UnboundEvent
}
