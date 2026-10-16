package org.rsmod.api.repo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RespawnSpeedTest {
    @Test
    fun `it is off by default and leaves every timer alone`() {
        val respawn = RespawnSpeed()
        assertTrue(respawn.isOff)
        assertEquals(100, respawn.scale(100))
        assertEquals(Int.MAX_VALUE, respawn.scale(Int.MAX_VALUE))
    }

    @Test
    fun `a speed above 1 shortens timers and never goes below one tick`() {
        val respawn = RespawnSpeed().apply { speed = 4.0 }
        assertEquals(25, respawn.scale(100))
        assertEquals(3, respawn.scale(10))
        assertEquals(1, respawn.scale(2))
        assertEquals(1, respawn.scale(1))
    }

    @Test
    fun `a speed below 1 slows timers down`() {
        val respawn = RespawnSpeed().apply { speed = 0.5 }
        assertEquals(200, respawn.scale(100))
    }

    @Test
    fun `never and non-positive timers are not scaled`() {
        val respawn = RespawnSpeed().apply { speed = 10.0 }
        assertEquals(Int.MAX_VALUE, respawn.scale(Int.MAX_VALUE))
        assertEquals(0, respawn.scale(0))
        assertEquals(-1, respawn.scale(-1))
    }

    @Test
    fun `setting the speed back to 1 turns it off again`() {
        val respawn = RespawnSpeed().apply { speed = 3.0 }
        respawn.speed = RespawnSpeed.OFF
        assertTrue(respawn.isOff)
        assertEquals(60, respawn.scale(60))
    }

    @Test
    fun `a speed that is not above zero is rejected`() {
        val respawn = RespawnSpeed()
        assertThrows(IllegalArgumentException::class.java) { respawn.speed = 0.0 }
        assertThrows(IllegalArgumentException::class.java) { respawn.speed = -2.0 }
        assertThrows(IllegalArgumentException::class.java) { respawn.speed = Double.NaN }
        assertThrows(IllegalArgumentException::class.java) { respawn.speed = Double.POSITIVE_INFINITY }
    }
}
