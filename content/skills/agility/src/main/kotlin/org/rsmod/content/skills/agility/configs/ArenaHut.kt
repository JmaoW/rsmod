package org.rsmod.content.skills.agility.configs

import org.rsmod.map.CoordGrid

/** One loc of [ArenaHut]: [dx] and [dz] are tiles from [ArenaHut.DOOR]. */
class HutPiece(val loc: String, val dx: Int, val dz: Int, val shape: Int, val angle: Int) {
    fun at(door: CoordGrid): CoordGrid = door.translate(dx, dz)
}

/**
 * The Brimhaven Agility Arena's ground-level entrance: the bamboo hut at 2805-2812, 3189-3196 in
 * the cache, copied loc by loc onto the isle's beach. Walls, windows, the picture, the furniture
 * and the ladder down are generated from the cache's level 0 locs, as offsets from the doorway tile
 * (2809, 3189 in Brimhaven). The plants, fences and map icons round it are left out.
 *
 * What cannot be copied: the hut's wooden floor (map terrain, so the floor is the beach's sand) and
 * its roof (the client only hides a roof over tiles the terrain flags, so a placed roof would cover
 * the inside for good). The hut is open to the sky.
 */
object ArenaHut {
    /** The doorway tile: its wall is on the tile's north side, so the hut lies north of it. */
    val DOOR = CoordGrid(2280, 2802, 0)

    /**
     * A map loc the hut would stand inside (a 2x2 beach bush), taken out when the hut is placed.
     */
    const val BUSH = "enakh_leaflessbush"
    val BUSH_AT = CoordGrid(2278, 2808, 0)

    val pieces =
        listOf(
            HutPiece("bamboo_window", -2, 0, 0, 1),
            HutPiece("bamboo_wall", -1, 0, 0, 1),
            HutPiece("bamboo_doorway", 0, 0, 0, 1),
            HutPiece("bamboo_wall", 1, 0, 0, 1),
            HutPiece("bamboo_wall", -3, 1, 9, 1),
            HutPiece("table_bamboo", -1, 1, 10, 1),
            HutPiece("hatstand", 1, 1, 10, 0),
            HutPiece("bamboo_wall", 2, 1, 9, 0),
            HutPiece("bamboo_wall", -4, 2, 0, 2),
            HutPiece("pots_bamboo", -3, 2, 10, 2),
            HutPiece("table_bamboo", -1, 2, 10, 0),
            HutPiece("bamboo_wall", 3, 2, 0, 0),
            HutPiece("bamboo_wall", -4, 3, 0, 2),
            HutPiece("bamboo_window", 3, 3, 0, 0),
            HutPiece("bamboo_window", -4, 4, 0, 2),
            HutPiece("pottedplant_bamboo", -3, 4, 10, 0),
            HutPiece("shelves_bamboo", 2, 4, 10, 2),
            HutPiece("bamboo_wall", 3, 4, 0, 0),
            HutPiece("bamboo_wall", -4, 5, 0, 2),
            HutPiece("bamboo_wall", 3, 5, 0, 0),
            HutPiece("bamboo_wall", -3, 6, 9, 2),
            HutPiece("bamboo_picture", -1, 6, 4, 1),
            HutPiece("bamboo_wall", 2, 6, 9, 3),
            HutPiece("bamboo_wall", -2, 7, 0, 3),
            HutPiece("bamboo_wall", -1, 7, 0, 3),
            HutPiece("bamboo_wall", 0, 7, 0, 3),
            HutPiece("bamboo_wall", 1, 7, 0, 3),
        )
}
