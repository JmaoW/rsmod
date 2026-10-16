package org.rsmod.content.skills.runecraft.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuneAltarsTest {
    @Test
    fun `air runes multiply at the wiki's levels`() {
        val air = RuneAltars.air
        assertEquals(1, air.runesPerEssence(1))
        assertEquals(1, air.runesPerEssence(10))
        assertEquals(2, air.runesPerEssence(11))
        assertEquals(3, air.runesPerEssence(22))
        assertEquals(10, air.runesPerEssence(99))
    }

    @Test
    fun `other runes multiply at their own levels`() {
        assertEquals(2, RuneAltars.mind.runesPerEssence(14))
        assertEquals(3, RuneAltars.water.runesPerEssence(38))
        assertEquals(2, RuneAltars.earth.runesPerEssence(26))
        assertEquals(3, RuneAltars.fire.runesPerEssence(70))
        assertEquals(2, RuneAltars.cosmic.runesPerEssence(59))
        assertEquals(1, RuneAltars.blood.runesPerEssence(99))
        assertEquals(1, RuneAltars.wrath.runesPerEssence(99))
    }

    @Test
    fun `rune essence only makes the six lowest runes`() {
        val plain = RuneAltars.all.filter { !it.pureOnly }.map { it.name }.toSet()
        assertEquals(setOf("air", "mind", "water", "earth", "fire", "body"), plain)
    }

    @Test
    fun `every altar has a distinct altar and rift`() {
        val altars = RuneAltars.all
        assertEquals(altars.size, altars.map { it.altar.internalNameValue }.toSet().size)
        assertEquals(altars.size, altars.map { it.rift.internalNameValue }.toSet().size)
    }

    @Test
    fun `combination runes give less experience at the lower altar`() {
        val lava = RuneAltars.combos.first { it.level == 23 }
        assertEquals(10.0, lava.xpAt(RuneAltars.earth))
        assertEquals(10.5, lava.xpAt(RuneAltars.fire))
        val mist = RuneAltars.combos.first { it.level == 6 }
        assertEquals(8.0, mist.xpAt(RuneAltars.air))
        assertEquals(8.5, mist.xpAt(RuneAltars.water))
    }

    @Test
    fun `each elemental altar offers three combination runes`() {
        for (altar in listOf(RuneAltars.air, RuneAltars.water, RuneAltars.earth, RuneAltars.fire)) {
            assertEquals(3, RuneAltars.combosAt(altar).size, altar.name)
        }
        assertTrue(RuneAltars.combosAt(RuneAltars.body).isEmpty())
    }

    @Test
    fun `the colossal pouch grows with level`() {
        assertEquals(8, Pouches.colossal.capacity(25))
        assertEquals(40, Pouches.colossal.capacity(85))
        assertEquals(40, Pouches.colossal.capacity(99))
        assertTrue(Pouches.colossal.capacity(50) in 8..40)
        assertEquals(12, Pouches.giant.capacity(99))
    }
}
