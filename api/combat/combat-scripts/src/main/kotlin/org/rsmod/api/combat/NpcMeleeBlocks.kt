package org.rsmod.api.combat

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcType

/**
 * Npcs that cannot be attacked with melee, even though they can be clicked and attacked from range
 * (a kraken whirlpool in the water, for example). A player who tries gets "I can't reach that!", or
 * the message the type was blocked with. For a type blocked with `reachable = true` (flying
 * monsters), a halberd's long reach or a salamander still works.
 */
@Singleton
public class NpcMeleeBlocks @Inject constructor() {
    private class Block(val message: String?, val reachable: Boolean)

    private val blocked = HashMap<Int, Block>()

    public fun block(type: NpcType, message: String? = null, reachable: Boolean = false) {
        blocked[type.id] = Block(message, reachable)
    }

    public fun isBlocked(npc: Npc): Boolean = npc.visType.id in blocked

    /** What to tell a player who tries to melee [npc]; `null` for the standard "can't reach". */
    public fun messageFor(npc: Npc): String? = blocked[npc.visType.id]?.message

    /**
     * Whether a weapon called [internalName] gets round the block on [npc]: halberds and
     * salamanders, if the type allows it.
     */
    public fun allows(npc: Npc, internalName: String?): Boolean =
        blocked[npc.visType.id]?.reachable == true &&
            internalName != null &&
            ("halberd" in internalName || "salamander" in internalName)
}
