package org.rsmod.content.skills.magic.spell.attacks.standard

import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.Npc

/**
 * Holds npcs still for the bind spells and Ice Rush, Burst, Blitz and Barrage. A held npc cannot
 * move, but it can still attack, be attacked, and take the hit that froze it. After a freeze ends,
 * the npc cannot be held again for a short while.
 */
@Singleton
class NpcFreezes {
    private val immuneUntil = WeakHashMap<Npc, Int>()

    /** Whether [npc] is held, or too soon after being held to be held again. */
    fun isHeld(npc: Npc, clock: Int): Boolean = clock < (immuneUntil[npc] ?: Int.MIN_VALUE)

    /** Holds [npc] for [ticks] from [clock]. */
    fun freeze(npc: Npc, ticks: Int, clock: Int) {
        npc.freeze(ticks)
        immuneUntil[npc] = clock + ticks + IMMUNITY_TICKS
    }

    /**
     * Holds [npc] for [ticks] without making it immune afterwards, which is how the grasp spells
     * bind: they can bind the same npc again at once.
     */
    fun hold(npc: Npc, ticks: Int) {
        npc.freeze(ticks)
    }

    private companion object {
        const val IMMUNITY_TICKS = 5
    }
}
