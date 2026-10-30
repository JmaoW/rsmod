package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToaScalingTest {
    @Test
    fun `party size adds ninety percent for the second and third player and sixty after`() {
        assertEquals(1.0, ToaScaling.partyFactor(1), 1e-9)
        assertEquals(1.9, ToaScaling.partyFactor(2), 1e-9)
        assertEquals(2.8, ToaScaling.partyFactor(3), 1e-9)
        assertEquals(3.4, ToaScaling.partyFactor(4), 1e-9)
        assertEquals(5.8, ToaScaling.partyFactor(8), 1e-9)
    }

    @Test
    fun `path levels add eight percent then five a level`() {
        assertEquals(0.0, ToaScaling.pathBonus(0), 1e-9)
        assertEquals(0.08, ToaScaling.pathBonus(1), 1e-9)
        assertEquals(0.13, ToaScaling.pathBonus(2), 1e-9)
        assertEquals(0.33, ToaScaling.pathBonus(6), 1e-9)
        assertEquals(0.33, ToaScaling.pathBonus(9), 1e-9)
    }

    @Test
    fun `hitpoints match the live captures OpenRune recorded for Zebak`() {
        // Zebak has 580 hitpoints: 580 / 930 / 1,280 alone at raid level 0 / 150 / 300, and 1,760
        // for two players at 150.
        assertEquals(580, ToaScaling.hitpoints(580, 0, 1, 0))
        assertEquals(930, ToaScaling.hitpoints(580, 150, 1, 0))
        assertEquals(1_280, ToaScaling.hitpoints(580, 300, 1, 0))
        assertEquals(1_760, ToaScaling.hitpoints(580, 150, 2, 0))
    }

    @Test
    fun `damage is capped at two and a half times before path levels`() {
        assertEquals(1.4, ToaScaling.damageFactor(100, 0), 1e-9)
        assertEquals(2.5, ToaScaling.damageFactor(500, 0), 1e-9)
        assertEquals(2.5 * 1.08, ToaScaling.damageFactor(600, 1), 1e-9)
        assertEquals(1.0 + 100 / 250.0, ToaScaling.rollFactor(100), 1e-9)
    }
}
