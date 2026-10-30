package org.rsmod.content.areas.misc.isleofsouls.pvp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class ArenaTilesTest {
    private val tiles = ArenaTiles.parse("10 1-3 7-7\n11 2-5\n\n")

    @Test
    fun `a tile is inside when its row has a range that holds it`() {
        for (x in listOf(1, 2, 3, 7)) {
            assertTrue(CoordGrid(x, 10, 0) in tiles, "x=$x")
        }
        for (x in listOf(0, 4, 6, 8)) {
            assertFalse(CoordGrid(x, 10, 0) in tiles, "x=$x")
        }
        assertTrue(CoordGrid(5, 11, 0) in tiles)
        assertFalse(CoordGrid(6, 11, 0) in tiles)
    }

    @Test
    fun `other rows and other levels are outside`() {
        assertFalse(CoordGrid(2, 9, 0) in tiles)
        assertFalse(CoordGrid(2, 12, 0) in tiles)
        assertFalse(CoordGrid(2, 10, 1) in tiles)
    }

    @Test
    fun `the size counts every tile`() {
        assertEquals(3 + 1 + 4, tiles.size)
    }

    @Test
    fun `the arena resource loads`() {
        val arena = PvpAreas.SOUL_WARS_ARENA
        assertEquals(6037, arena.size)
        assertTrue(CoordGrid(2142, 2905, 0) in arena, "the blue portal's arrival")
        assertTrue(CoordGrid(2272, 2919, 0) in arena, "the red portal's arrival")
        assertTrue(CoordGrid(2207, 2912, 0) in arena, "the middle island")
        assertTrue(CoordGrid(2170, 2913, 0) in arena, "the west bridge")
        assertTrue(CoordGrid(2240, 2911, 0) in arena, "the east bridge")
        assertFalse(CoordGrid(2264, 2889, 0) in arena, "a pocket behind the tree wall")
        assertFalse(CoordGrid(2205, 2847, 0) in arena, "the lobby")
    }
}
