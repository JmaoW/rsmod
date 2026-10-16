package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttackStyle

class InfernoBlobAiTest {
    /** A roll that always gives [value] (or the largest legal number below the bound). */
    private fun always(value: Int): (Int) -> Int = { bound -> minOf(value, bound - 1) }

    @Test
    fun `against Protect from Magic the blob shoots and against Missiles it casts`() {
        for (roll in 0..2) {
            assertEquals(
                NpcAttackStyle.Ranged,
                InfernoBlobAi.chooseStyle(Protection.Magic, adjacent = false, always(roll)),
            )
            assertEquals(
                NpcAttackStyle.Magic,
                InfernoBlobAi.chooseStyle(Protection.Missiles, adjacent = false, always(roll)),
            )
        }
    }

    @Test
    fun `against no prayer or Protect from Melee it picks ranged or magic at random`() {
        for (protection in listOf(Protection.None, Protection.Melee)) {
            assertEquals(
                NpcAttackStyle.Ranged,
                InfernoBlobAi.chooseStyle(protection, adjacent = false, always(0)),
            )
            assertEquals(
                NpcAttackStyle.Magic,
                InfernoBlobAi.chooseStyle(protection, adjacent = false, always(1)),
            )
        }
    }

    @Test
    fun `next to a player without Protect from Melee it sometimes bites`() {
        assertEquals(
            NpcAttackStyle.Melee,
            InfernoBlobAi.chooseStyle(Protection.None, adjacent = true, always(0)),
        )
        assertEquals(
            NpcAttackStyle.Melee,
            InfernoBlobAi.chooseStyle(Protection.Magic, adjacent = true, always(0)),
        )
        // The bite is the one outcome in MELEE_ODDS; any other roll falls through to the prayer.
        assertEquals(
            NpcAttackStyle.Ranged,
            InfernoBlobAi.chooseStyle(Protection.Magic, adjacent = true, always(1)),
        )
    }

    @Test
    fun `with Protect from Melee on it never bites`() {
        for (roll in 0..2) {
            val style = InfernoBlobAi.chooseStyle(Protection.Melee, adjacent = true, always(roll))
            assertEquals(false, style == NpcAttackStyle.Melee)
        }
    }

    @Test
    fun `away from the player it never bites`() {
        for (protection in Protection.entries) {
            val style = InfernoBlobAi.chooseStyle(protection, adjacent = false, always(0))
            assertEquals(false, style == NpcAttackStyle.Melee)
        }
    }
}
