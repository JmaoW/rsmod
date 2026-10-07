package org.rsmod.routefinder.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.routefinder.flag.BlockAccessFlag.EAST
import org.rsmod.routefinder.flag.BlockAccessFlag.NORTH
import org.rsmod.routefinder.flag.BlockAccessFlag.SOUTH
import org.rsmod.routefinder.flag.BlockAccessFlag.WEST
import org.rsmod.routefinder.util.Rotations.rotate

class RotationsTest {
    @Test
    fun `rotate loc width`() {
        val (width, length) = 3 to 2
        assertEquals(width, rotate(angle = 0, width, length))
        assertEquals(length, rotate(angle = 1, width, length))
        assertEquals(width, rotate(angle = 2, width, length))
        assertEquals(length, rotate(angle = 3, width, length))
    }

    @Test
    fun `rotate loc length`() {
        val (width, length) = 3 to 2
        assertEquals(length, rotate(angle = 0, length, width))
        assertEquals(width, rotate(angle = 1, length, width))
        assertEquals(length, rotate(angle = 2, length, width))
        assertEquals(width, rotate(angle = 3, length, width))
    }

    @Test
    fun `rotate north block-access flag`() {
        assertEquals(NORTH, rotate(angle = 0, NORTH))
        assertEquals(EAST, rotate(angle = 1, NORTH))
        assertEquals(SOUTH, rotate(angle = 2, NORTH))
        assertEquals(WEST, rotate(angle = 3, NORTH))
    }

    @Test
    fun `rotate east block-access flag`() {
        assertEquals(EAST, rotate(angle = 0, EAST))
        assertEquals(SOUTH, rotate(angle = 1, EAST))
        assertEquals(WEST, rotate(angle = 2, EAST))
        assertEquals(NORTH, rotate(angle = 3, EAST))
    }

    @Test
    fun `rotate south block-access flag`() {
        assertEquals(SOUTH, rotate(angle = 0, SOUTH))
        assertEquals(WEST, rotate(angle = 1, SOUTH))
        assertEquals(NORTH, rotate(angle = 2, SOUTH))
        assertEquals(EAST, rotate(angle = 3, SOUTH))
    }

    @Test
    fun `rotate west block-access flag`() {
        assertEquals(WEST, rotate(angle = 0, WEST))
        assertEquals(NORTH, rotate(angle = 1, WEST))
        assertEquals(EAST, rotate(angle = 2, WEST))
        assertEquals(SOUTH, rotate(angle = 3, WEST))
    }

    @Test
    fun `rotate ignores the bit above the direction flags`() {
        // The cache's approach flags: 0x10 plus every side blocked but one (30 leaves north open).
        val northOpen = 0x10 or EAST or SOUTH or WEST
        assertEquals(EAST or SOUTH or WEST, rotate(angle = 0, northOpen))
        assertEquals(SOUTH or WEST or NORTH, rotate(angle = 1, northOpen))
        assertEquals(WEST or NORTH or EAST, rotate(angle = 2, northOpen))
        assertEquals(NORTH or EAST or SOUTH, rotate(angle = 3, northOpen))
    }
}
