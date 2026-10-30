package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class ToaPackingTest {
    private val southWest = CoordGrid(6432, 32, 0)

    /**
     * Tiles of the real tombs, as the cache has them (`tools/dungeons/census.py 3520 5120 3967
     * 5439`): the crossings of the rooms and the places the fights happen.
     */
    private val realTiles =
        listOf(
            CoordGrid(3550, 5161, 0), // main hall
            CoordGrid(3516, 5270, 0), // scabaras puzzle, last tiles of map square 54
            CoordGrid(3522, 5279, 0), // scabaras puzzle spawn
            CoordGrid(3583, 5290, 0), // east edge of the scabaras column
            CoordGrid(3538, 5408, 0), // kephri teleport
            CoordGrid(3662, 5275, 0), // het pickaxe statue
            CoordGrid(3698, 5279, 0), // het puzzle spawn
            CoordGrid(3699, 5406, 1), // akkha exit, level 1
            CoordGrid(3686, 5145, 0), // vault chest
            CoordGrid(3678, 5140, 0), // vault barrier
            CoordGrid(3792, 5279, 0), // apmeken puzzle spawn
            CoordGrid(3794, 5408, 0), // ba-ba teleport
            CoordGrid(3807, 5176, 1), // wardens spawn
            CoordGrid(3955, 5278, 0), // crondis exit
            CoordGrid(3958, 5407, 0), // zebak spawn
            CoordGrid(3935, 5168, 1), // final wardens arena
            CoordGrid(3967, 5439, 3), // far north-east corner
            CoordGrid(3512, 5120, 0), // far south-west corner
        )

    @Test
    fun `the columns fit a large region without overlapping`() {
        val columns = ToaPacking.columns
        assertEquals(4, columns.size)
        assertTrue(ToaPacking.usedZoneColumns <= 40, "uses ${ToaPacking.usedZoneColumns} zones")
        for ((a, b) in columns.zipWithNext()) {
            val gap = b.regionZoneX - (a.regionZoneX + a.zoneWidth)
            assertEquals(ToaPacking.GAP_ZONES, gap, "gap between ${a.name} and ${b.name}")
            // The real map has more than the gap between them, so nothing is copied twice.
            assertTrue(a.realMaxX < b.realMinX)
        }
        assertEquals(40, ToaPacking.ZONE_ROWS)
    }

    @Test
    fun `every tile of the tombs maps inside the region and back`() {
        for (real in realTiles) {
            val inside = ToaPacking.toRegion(real, southWest)
            assertNotNull(inside, "$real has no place in the region")
            assertTrue(inside!!.x in southWest.x until southWest.x + 320, "$real -> $inside")
            assertTrue(inside.z in southWest.z until southWest.z + 320, "$real -> $inside")
            assertEquals(real.level, inside.level)
            assertEquals(real, ToaPacking.toReal(inside, southWest), "round trip of $real")
        }
    }

    @Test
    fun `rows stay put and only columns slide west`() {
        val real = CoordGrid(3662, 5275, 0)
        val inside = ToaPacking.toRegion(real, southWest)!!
        assertEquals(southWest.z + (5275 - 5120), inside.z)
        // Het is the second column: 11 zones in, from real zone 456.
        assertEquals(southWest.x + 11 * 8 + (3662 - 456 * 8), inside.x)
    }

    @Test
    fun `tiles that are not part of the tombs have no copy`() {
        // Between the Scabaras and Het columns (map squares 56), and between Het and Apmeken.
        assertNull(ToaPacking.toRegion(CoordGrid(3600, 5280, 0), southWest))
        assertNull(ToaPacking.toRegion(CoordGrid(3740, 5280, 0), southWest))
        // The empty map square rows between the paths' rooms are copied, so the rows stay aligned.
        assertNotNull(ToaPacking.toRegion(CoordGrid(3680, 5220, 0), southWest))
        // The lobby is a real area, not part of the raid.
        assertNull(ToaPacking.toRegion(CoordGrid(3358, 9113, 0), southWest))
        // Gap zones in the region have nothing to map back to.
        val gap = CoordGrid(southWest.x + 9 * 8 + 3, southWest.z + 40, 0)
        assertNull(ToaPacking.toReal(gap, southWest))
    }

    @Test
    fun `the template copies every column on all four levels`() {
        val columns = ToaPacking.columns
        val zonesPerLevel = columns.sumOf { it.zoneWidth } * ToaPacking.ZONE_ROWS
        assertEquals(33 * 40, zonesPerLevel)
        ToaPacking.template()
    }

    /** Where [real] lands in the client's scene for a player standing on it. */
    private fun sceneOf(real: CoordGrid): Pair<Int, Int> {
        val anchor = checkNotNull(ToaPacking.sceneAnchor(real, southWest))
        val inside = checkNotNull(ToaPacking.toRegion(real, southWest))
        return (inside.x - anchor.x) to (inside.z - anchor.z)
    }

    @Test
    fun `a room's map square sits 16 tiles into the scene, as in the real game`() {
        for (real in realTiles) {
            val (x, z) = sceneOf(real)
            assertEquals((real.x and 63) + 16, x, "$real")
            assertEquals((real.z and 63) + 16, z, "$real")
            // Inside the scene's middle, so walking about the room never moves it.
            assertTrue(x in 16 until 88 && z in 16 until 88, "$real at ($x, $z)")
        }
        val anchor = checkNotNull(ToaPacking.sceneAnchor(CoordGrid(3540, 5288, 0), southWest))
        assertEquals(0, anchor.x % 8)
        assertEquals(0, anchor.z % 8)
    }

    @Test
    fun `the plugin's scabaras scene tiles line up`() {
        // RuneLite Tombs of Amascut plugin: the light/addition corner, an obelisk, a half's flame.
        assertEquals(36 to 56, sceneOf(CoordGrid(3540, 5288, 0)))
        assertEquals(53 to 44, sceneOf(CoordGrid(3557, 5276, 0)))
        assertEquals(36 to 57, sceneOf(CoordGrid(3540, 5289, 0)))
        assertEquals(28 to 54, sceneOf(CoordGrid(3532, 5286, 0)))
    }

    @Test
    fun `nothing outside the tombs has a scene anchor`() {
        assertNull(ToaPacking.sceneAnchor(CoordGrid(3200, 3200, 0), southWest))
    }
}
