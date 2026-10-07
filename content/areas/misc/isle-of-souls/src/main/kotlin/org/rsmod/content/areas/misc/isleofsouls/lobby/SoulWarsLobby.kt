package org.rsmod.content.areas.misc.isleofsouls.lobby

import org.rsmod.api.random.GameRandom
import org.rsmod.map.CoordGrid

/**
 * The Soul Wars lobby on the Isle of Souls: where new characters start, where a dead player
 * respawns, and where Home Teleport lands.
 *
 * The lobby has two fenced graveyards side by side, a blue one (vine rails) and a red one (bone
 * rails). Gravestones line the inside of each fence, which leaves a 3x7 patch of open floor in the
 * middle. The bounds come from the locs in the cache's map data (the fence rails at x=2203/2209 and
 * x=2211/2217, z=2845/2855, and the gravestone columns one tile inside them).
 */
object SoulWarsLobby {
    /** An axis-aligned patch of open floor on level 0, with inclusive bounds. */
    class Graveyard(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        val tiles: List<CoordGrid> =
            (minX..maxX).flatMap { x -> (minZ..maxZ).map { z -> CoordGrid(x, z, 0) } }
    }

    val blue = Graveyard(minX = 2205, minZ = 2847, maxX = 2207, maxZ = 2853)

    val red = Graveyard(minX = 2213, minZ = 2847, maxX = 2215, maxZ = 2853)

    /** Every tile in either graveyard, each equally likely to be picked. */
    val spawnTiles: List<CoordGrid> = blue.tiles + red.tiles

    fun randomSpawn(random: GameRandom): CoordGrid = spawnTiles[random.of(spawnTiles.size)]
}
