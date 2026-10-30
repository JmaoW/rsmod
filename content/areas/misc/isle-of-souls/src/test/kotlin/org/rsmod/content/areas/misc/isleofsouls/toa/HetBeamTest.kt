package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HetBeamTest {
    private val open: (Int, Int) -> HetBlocker = { _, _ -> HetBlocker.Open }

    @Test
    fun `an open row runs to the first wall and stops there`() {
        val wall = 94
        val result =
            HetBeam.trace(
                100,
                5,
                blocker = { x, _ -> if (x == wall) HetBlocker.Wall else HetBlocker.Open },
                shield = { _, _ -> false },
                stops = { _, _ -> false },
            )
        assertEquals(6, result.tiles.size)
        assertEquals(HetBeamTile.Shape.End, result.tiles.last().shape)
        assertEquals(wall, result.tiles.last().x)
        assertTrue(result.tiles.dropLast(1).all { it.shape == HetBeamTile.Shape.Horizontal })
        assertFalse(result.hitsShield)
    }

    @Test
    fun `a mirror facing the beam turns it a quarter along the side it faces`() {
        // Angle 0 faces north-east: a beam flying west, arriving from the east, leaves to the
        // north.
        assertEquals(0 to 1, HetBeam.turn(0, -1, 0))
        // Angle 1 faces south-east: arriving from the east leaves to the south.
        assertEquals(0 to -1, HetBeam.turn(1, -1, 0))
        // Angle 2 faces south-west: a beam flying east arrives from the west and leaves south.
        assertEquals(0 to -1, HetBeam.turn(2, 1, 0))
        // Angle 3 faces north-west: a beam flying south arrives from the north and leaves west.
        assertEquals(-1 to 0, HetBeam.turn(3, 0, -1))
    }

    @Test
    fun `the back of a mirror stops the beam`() {
        // Angle 2 faces south-west, so a beam arriving from the east hits its back.
        assertNull(HetBeam.turn(2, -1, 0))
        val result =
            HetBeam.trace(
                100,
                5,
                blocker = { x, _ -> if (x == 97) HetBlocker.Mirror(2) else HetBlocker.Open },
                shield = { _, _ -> false },
                stops = { _, _ -> false },
            )
        assertEquals(HetBeamTile.Shape.End, result.tiles.last().shape)
        assertEquals(97, result.tiles.last().x)
    }

    @Test
    fun `a mirror carries the beam to the shielded statue`() {
        val result =
            HetBeam.trace(
                100,
                5,
                blocker = { x, z ->
                    if (x == 97 && z == 5) HetBlocker.Mirror(0) else HetBlocker.Open
                },
                shield = { x, z -> x == 97 && z == 9 },
                stops = { _, _ -> false },
            )
        assertTrue(result.hitsShield)
        val corner = result.tiles.first { it.shape == HetBeamTile.Shape.NorthEast }
        assertEquals(97, corner.x)
        assertEquals(5, corner.z)
        assertEquals(9, result.tiles.last().z)
        assertEquals(HetBeamTile.Shape.Vertical, result.tiles[3].shape)
    }

    @Test
    fun `reaching the seal or the caster ends the beam without weakening anything`() {
        val result =
            HetBeam.trace(
                100,
                5,
                blocker = open,
                shield = { _, _ -> false },
                stops = { x, _ -> x == 96 },
            )
        assertFalse(result.hitsShield)
        assertEquals(96, result.tiles.last().x)
    }

    @Test
    fun `a beam with nothing to hit still ends`() {
        val result =
            HetBeam.trace(
                100,
                5,
                blocker = open,
                shield = { _, _ -> false },
                stops = { _, _ -> false },
            )
        assertEquals(200, result.tiles.size)
        assertFalse(result.hitsShield)
    }

    @Test
    fun `pickaxe damage grows with the pickaxe tier and the Mining level`() {
        assertEquals(5..7, HetMining.damageRange(60, 1))
        assertEquals(8..10, HetMining.damageRange(60, 41))
        assertEquals(15..17, HetMining.damageRange(85, 41))
        assertEquals(19..21, HetMining.damageRange(99, 61))
        assertEquals(10..12, HetMining.damageRange(70, 61))
    }

    @Test
    fun `the layouts only use real pieces and keep clear of the caster and the seal`() {
        assertEquals(5, HetData.layouts.size)
        for (layout in HetData.layouts) {
            assertTrue(layout.isNotEmpty())
            val tiles = layout.map { it.x to it.z }
            assertEquals(tiles.size, tiles.toSet().size, "a tile holds two pieces")
            for (tile in layout) {
                assertFalse(
                    tile.x in 3676..3678 && tile.z in 5279..5281,
                    "piece on the caster statue at ${tile.x},${tile.z}",
                )
                assertFalse(
                    tile.x in 3679..3681 && tile.z in 5279..5281,
                    "piece on the seal at ${tile.x},${tile.z}",
                )
            }
        }
    }
}
