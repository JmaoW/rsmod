package org.rsmod.game.entity.npc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class NpcTintTest {
    @Test
    fun `channels take the client's hsl ranges or keep`() {
        assertDoesNotThrow { NpcTint(hue = 63, saturation = 7, lightness = 127, weight = 127) }
        assertDoesNotThrow { NpcTint(NpcTint.KEEP, NpcTint.KEEP, NpcTint.KEEP, weight = 1) }
        assertThrows<IllegalArgumentException> { NpcTint(64, 0, 0, 1) }
        assertThrows<IllegalArgumentException> { NpcTint(0, 8, 0, 1) }
        assertThrows<IllegalArgumentException> { NpcTint(0, 0, 128, 1) }
        assertThrows<IllegalArgumentException> { NpcTint(-2, 0, 0, 1) }
    }

    @Test
    fun `weight must show something the client draws`() {
        assertThrows<IllegalArgumentException> { NpcTint(0, 0, 0, 0) }
        assertThrows<IllegalArgumentException> { NpcTint(0, 0, 0, 128) }
    }

    @Test
    fun `the longest tint fits the client's unsigned short end time`() {
        assertEquals(2184, NpcTint.MAX_DURATION)
        assert(NpcTint.MAX_DURATION * NpcTint.CLIENT_CYCLES_PER_TICK <= 65535)
    }
}
