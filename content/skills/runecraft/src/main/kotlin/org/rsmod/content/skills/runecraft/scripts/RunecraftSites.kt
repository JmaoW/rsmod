package org.rsmod.content.skills.runecraft.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Where everything runecraft puts or sends a player. Spots on the isle are the free tiles closest
 * to a fixed anchor, judged from the loaded collision map, so they follow the isle's real layout;
 * with no collision loaded (as in tests) the anchor itself is used.
 */
@Singleton
class RunecraftSites @Inject constructor(private val collision: CollisionFlagMap) {
    private val taken = HashSet<Long>()

    /** Where the Mage of Zamorak stands, and where everything in runecraft sends a player back. */
    val home: CoordGrid by lazy { claim(MAGE_ANCHOR) }

    /** Aubury, who teleports players to the essence mine. */
    val aubury: CoordGrid by lazy {
        home
        claim(AUBURY_ANCHOR)
    }

    /** Where the Dark Mage floats in the middle of the Abyss. */
    val darkMage: CoordGrid by lazy { claim(DARK_MAGE_ANCHOR) }

    /** Where the Mage of Zamorak drops a player in the Abyss: right beside the Dark Mage. */
    val abyssArrival: CoordGrid by lazy {
        darkMage
        claim(DARK_MAGE_ANCHOR)
    }

    /** Where Aubury's teleport lands a player in the essence mine. */
    val mineArrival: CoordGrid by lazy { claim(MINE_ANCHOR) }

    /** The portal that leads out of the essence mine, beside [mineArrival]. */
    val mineExit: CoordGrid by lazy {
        mineArrival
        claim(MINE_ANCHOR)
    }

    /** The exit portal the isle adds to the Astral Altar, which has none of its own. */
    val astralExit: CoordGrid by lazy { claim(ASTRAL_EXIT_ANCHOR) }

    /** The Abyss rifts for blood, astral and wrath, which the cache does not place. */
    val bloodRift: CoordGrid by lazy { claim(BLOOD_RIFT_ANCHOR) }
    val astralRift: CoordGrid by lazy { claim(ASTRAL_RIFT_ANCHOR) }
    val wrathRift: CoordGrid by lazy { claim(WRATH_RIFT_ANCHOR) }

    /** The nearest free tile to an altar's exit portal [inside], which is where a player lands. */
    fun landing(inside: CoordGrid): CoordGrid = nearestFree(inside, LANDING_RADIUS, reserve = false)

    private fun claim(anchor: CoordGrid): CoordGrid =
        nearestFree(anchor, SEARCH_RADIUS, reserve = true)

    private fun nearestFree(anchor: CoordGrid, radius: Int, reserve: Boolean): CoordGrid {
        val candidates = ArrayList<CoordGrid>()
        for (x in anchor.x - radius..anchor.x + radius) {
            for (z in anchor.z - radius..anchor.z + radius) {
                candidates += CoordGrid(x, z, anchor.level)
            }
        }
        candidates.sortBy { abs(it.x - anchor.x) + abs(it.z - anchor.z) }
        val found = candidates.firstOrNull { isFree(it) } ?: anchor
        if (reserve) {
            taken += key(found)
        }
        return found
    }

    private fun isFree(coords: CoordGrid): Boolean {
        if (coords.x in LOBBY_X && coords.z in LOBBY_Z && coords.level == 0 && coords.z < 4000) {
            return false
        }
        if (key(coords) in taken) {
            return false
        }
        return (collision[coords.x, coords.z, coords.level] and BLOCKED) == 0
    }

    private fun key(coords: CoordGrid): Long =
        (coords.x.toLong() shl 32) or (coords.z.toLong() shl 2) or coords.level.toLong()

    companion object {
        /** Just outside the Soul Wars lobby's fenced graveyards. */
        private val MAGE_ANCHOR = CoordGrid(2210, 2862, 0)
        private val AUBURY_ANCHOR = CoordGrid(2214, 2862, 0)

        /** The middle of the Abyss' inner ring. */
        private val DARK_MAGE_ANCHOR = CoordGrid(3039, 4833, 0)

        /** Between the essence mine's four rocks. */
        private val MINE_ANCHOR = CoordGrid(2910, 4830, 0)

        private val ASTRAL_EXIT_ANCHOR = CoordGrid(2161, 3859, 0)

        // The rifts sit on the free ground of the inner ring, away from the cache's twelve.
        private val BLOOD_RIFT_ANCHOR = CoordGrid(3049, 4826, 0)
        private val ASTRAL_RIFT_ANCHOR = CoordGrid(3040, 4843, 0)
        private val WRATH_RIFT_ANCHOR = CoordGrid(3032, 4839, 0)

        private const val SEARCH_RADIUS = 18
        private const val LANDING_RADIUS = 4

        /** The lobby's fenced graveyards, which stay clear. */
        private val LOBBY_X = 2202..2218
        private val LOBBY_Z = 2844..2856

        private val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
    }
}
