package org.rsmod.api.spells.attack

import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.Npc

/**
 * How long each npc stays immune to being frozen again after a freeze. Kept apart from the spells
 * that set it so content that ends a freeze early (Callisto's roar) can end the immunity too.
 */
@Singleton
public class NpcFreezeImmunity {
    private val immuneUntil = WeakHashMap<Npc, Int>()

    /** Whether [npc] is immune to a new freeze at [clock]: held, or too soon after being held. */
    public fun isImmune(npc: Npc, clock: Int): Boolean = clock < (immuneUntil[npc] ?: Int.MIN_VALUE)

    public fun immuneUntil(npc: Npc, clock: Int) {
        immuneUntil[npc] = clock
    }

    /** Forgets the immunity of [npc], as when something breaks its freeze. */
    public fun clear(npc: Npc) {
        immuneUntil.remove(npc)
    }
}
