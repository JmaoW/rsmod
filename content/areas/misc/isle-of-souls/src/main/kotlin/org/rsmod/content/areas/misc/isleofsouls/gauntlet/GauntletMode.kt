package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.game.type.obj.ObjType

/**
 * The two versions of the Gauntlet. They share every rule; only the numbers and the ids differ: the
 * Corrupted one has a shorter preparation time and its maze is a second copy of the template eight
 * chunks east of the first (verified in the cache, see `isle_of_souls_gauntlet_plan.md`).
 */
enum class GauntletMode(
    val key: String,
    val corrupted: Boolean,
    /** Ticks to prepare before the Hunllef takes the player: 10 minutes, Corrupted 7:30 (wiki). */
    val prepTicks: Int,
    /** How many chunks east of the regular template this version's template sits. */
    val chunkOffset: Int,
) {
    Regular("regular", corrupted = false, prepTicks = 1000, chunkOffset = 0),
    Corrupted("corrupted", corrupted = true, prepTicks = 750, chunkOffset = 8);

    /** Every item of this version. */
    val items: GauntletItemSet
        get() = if (corrupted) GauntletCorruptedItems else GauntletRegularItems

    /** The weapon a player starts with, wielded: it lights the nodes (wiki). */
    val sceptre: ObjType
        get() = items.sceptre

    /**
     * The starting kit in the pack: an axe, a pickaxe, a harpoon, a pestle and mortar, and a
     * teleport crystal (wiki). The sceptre is [sceptre].
     */
    val tools: List<ObjType>
        get() = items.let { listOf(it.axe, it.pickaxe, it.harpoon, it.pestle, it.teleportCrystal) }

    /** The crystal that takes a player back to the start room. */
    val teleportCrystal: ObjType
        get() = items.teleportCrystal

    companion object {
        fun of(corrupted: Boolean): GauntletMode = if (corrupted) Corrupted else Regular
    }
}
