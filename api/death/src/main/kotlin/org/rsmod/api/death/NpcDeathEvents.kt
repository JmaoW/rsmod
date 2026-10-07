package org.rsmod.api.death

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

public class NpcDeathEvents {
    /**
     * Published when a npc has died and its drops are about to be handed out. [hero] is the player
     * that did the most damage, or `null` if no player is credited with the kill.
     */
    public data class Killed(val npc: Npc, val hero: Player?) : UnboundEvent
}
