package org.rsmod.content.skills.fishing.scripts

import kotlin.math.abs
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.routefinder.flag.CollisionFlag

class FishingTestSpotsTest {
    /** Land above the line `z = 100`, water at or below it. */
    private val coast: (Int, Int) -> Int = { _, z -> if (z <= 100) CollisionFlag.BLOCK_WALK else 0 }

    @Test
    fun `picks water tiles that touch land`() {
        val tiles = FishingTestSpots.findShoreTiles(coast, anchorX = 500, anchorZ = 101, count = 7)
        assertEquals(7, tiles.size)
        for ((_, z) in tiles) {
            assertEquals(100, z, "Only the row of water next to the land touches it.")
        }
    }

    @Test
    fun `spots are spaced apart and ordered west to east`() {
        val tiles = FishingTestSpots.findShoreTiles(coast, anchorX = 500, anchorZ = 101, count = 7)
        assertEquals(tiles.sortedBy { it.first }, tiles)
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                val distance =
                    max(
                        abs(tiles[i].first - tiles[j].first),
                        abs(tiles[i].second - tiles[j].second),
                    )
                assertTrue(distance >= 2, "Spots ${tiles[i]} and ${tiles[j]} are too close.")
            }
        }
    }

    @Test
    fun `tiles with a loc are not water`() {
        val flags: (Int, Int) -> Int = { x, z ->
            when {
                z > 100 -> 0
                x == 500 && z == 100 -> CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
                else -> CollisionFlag.BLOCK_WALK
            }
        }
        val tiles = FishingTestSpots.findShoreTiles(flags, anchorX = 500, anchorZ = 101, count = 3)
        assertTrue((500 to 100) !in tiles)
    }

    @Test
    fun `finds nothing without a shore`() {
        val allLand: (Int, Int) -> Int = { _, _ -> 0 }
        val allWater: (Int, Int) -> Int = { _, _ -> CollisionFlag.BLOCK_WALK }
        assertTrue(FishingTestSpots.findShoreTiles(allLand, 500, 500, 7).isEmpty())
        assertTrue(FishingTestSpots.findShoreTiles(allWater, 500, 500, 7).isEmpty())
    }
}
