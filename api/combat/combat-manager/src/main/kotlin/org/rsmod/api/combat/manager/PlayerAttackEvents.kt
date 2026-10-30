package org.rsmod.api.combat.manager

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjType

public class PlayerAttackEvents {
    /**
     * Published when [source] has queued a melee or ranged hit on [target], whatever kind of attack
     * it was part of. This is where the effects a hit can have (a poisoned weapon's poison, say)
     * are rolled.
     *
     * @property damage the damage the hit was queued with; prayers and armour may lower it, which a
     *   listener that needs the final number should read from the hit itself.
     * @property ammo the ammunition a ranged hit was shot with, if any.
     */
    public class Hit(
        public val source: Player,
        public val target: PathingEntity,
        public val type: HitType,
        public val damage: Int,
        public val ammo: ObjType?,
    ) : UnboundEvent
}
