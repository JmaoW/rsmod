package org.rsmod.api.player.hit.events

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

public class PlayerHitEvents {
    /**
     * Published as a hit lands, before [player] takes it. [damage] may be lowered here, for effects
     * that stop a hit from killing the player.
     */
    public class Landing(public val player: Player, public val hit: Hit, public var damage: Int) :
        UnboundEvent

    /**
     * Published once a hit has taken hitpoints from [player].
     *
     * @property damage what was actually taken, which is less than [Hit.damage] if the player had
     *   fewer hitpoints left.
     * @property npcSource the npc that dealt the hit, if it came from one and can still be found.
     */
    public class Impact(
        public val player: Player,
        public val hit: Hit,
        public val damage: Int,
        public val npcSource: Npc?,
    ) : UnboundEvent
}
