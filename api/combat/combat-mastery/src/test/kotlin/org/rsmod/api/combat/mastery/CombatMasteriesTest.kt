package org.rsmod.api.combat.mastery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.entity.Player

class CombatMasteriesTest {
    private class Fixed(val melee: Int = 0, val ranged: Int = 0, val magic: Int = 0) :
        CombatMasteryProvider {
        override fun tier(player: Player, style: MasteryStyle): Int =
            when (style) {
                MasteryStyle.Melee -> melee
                MasteryStyle.Ranged -> ranged
                MasteryStyle.Magic -> magic
            }
    }

    private val player = Player()

    @Test
    fun `no provider means no mastery`() {
        val masteries = CombatMasteries(emptySet())

        for (style in MasteryStyle.entries) {
            assertEquals(0, masteries.tier(player, style))
        }
        assertEquals(0, masteries.passiveTier(player))
        assertFalse(masteries.hasPassive(player, 1))
    }

    @Test
    fun `tier is the highest any provider reports and is clamped`() {
        val masteries = CombatMasteries(setOf(Fixed(melee = 2), Fixed(melee = 9, ranged = 1)))

        assertEquals(CombatMasteries.MAX_TIER, masteries.tier(player, MasteryStyle.Melee))
        assertEquals(1, masteries.tier(player, MasteryStyle.Ranged))
        assertEquals(0, masteries.tier(player, MasteryStyle.Magic))
    }

    @Test
    fun `passives follow the highest tier reached in any style`() {
        val masteries = CombatMasteries(setOf(Fixed(melee = 1, ranged = 3, magic = 2)))

        assertEquals(3, masteries.passiveTier(player))
        assertTrue(masteries.hasPassive(player, 1))
        assertTrue(masteries.hasPassive(player, 3))
        assertFalse(masteries.hasPassive(player, 4))
        assertTrue(masteries.has(player, MasteryStyle.Ranged, 3))
        assertFalse(masteries.has(player, MasteryStyle.Melee, 2))
    }
}
