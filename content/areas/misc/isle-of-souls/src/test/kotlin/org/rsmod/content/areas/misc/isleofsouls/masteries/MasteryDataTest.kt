package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.mastery.MasteryStyle

class MasteryDataTest {
    @Test
    fun `cards run melee then ranged then magic, six tiers each`() {
        assertEquals(MasteryChoice(MasteryStyle.Melee, 1), MasteryData.choiceForCard(0))
        assertEquals(MasteryChoice(MasteryStyle.Melee, 6), MasteryData.choiceForCard(5))
        assertEquals(MasteryChoice(MasteryStyle.Ranged, 1), MasteryData.choiceForCard(6))
        assertEquals(MasteryChoice(MasteryStyle.Ranged, 2), MasteryData.choiceForCard(7))
        assertEquals(MasteryChoice(MasteryStyle.Magic, 1), MasteryData.choiceForCard(12))
        assertEquals(MasteryChoice(MasteryStyle.Magic, 6), MasteryData.choiceForCard(17))
        assertNull(MasteryData.choiceForCard(18))
        assertNull(MasteryData.choiceForCard(-1))
    }

    @Test
    fun `every card maps back to itself`() {
        for (card in 0 until MasteryData.MASTERY_COUNT) {
            val choice = checkNotNull(MasteryData.choiceForCard(card))
            assertEquals(card, MasteryData.cardOf(choice))
        }
    }

    @Test
    fun `names and struct ids follow the cache`() {
        assertEquals("Melee I", MasteryChoice(MasteryStyle.Melee, 1).name)
        assertEquals("Ranged IV", MasteryChoice(MasteryStyle.Ranged, 4).name)
        assertEquals("Magic VI", MasteryChoice(MasteryStyle.Magic, 6).name)
        assertEquals(1165, MasteryData.structOf(MasteryChoice(MasteryStyle.Melee, 1)))
        assertEquals(1176, MasteryData.structOf(MasteryChoice(MasteryStyle.Ranged, 6)))
        assertEquals(1159, MasteryData.structOf(MasteryChoice(MasteryStyle.Magic, 1)))
        // Passive tier 3 is accuracy and tier 4 damage reduction in the cache.
        assertEquals(1156, MasteryData.passiveStructOf(MasteryChoice(MasteryStyle.Melee, 3)))
        assertEquals(1155, MasteryData.passiveStructOf(MasteryChoice(MasteryStyle.Melee, 4)))
    }

    @Test
    fun `there are twenty points for eighteen masteries`() {
        assertEquals(10, MasteryData.taskNames.size)
        assertEquals(20, MasteryData.TASK_COUNT * MasteryData.pointsPerTask)
        assertEquals(18, MasteryData.MASTERY_COUNT * MasteryData.UNLOCK_COST)
    }

    @Test
    fun `messages name the mastery`() {
        val ranged = MasteryChoice(MasteryStyle.Ranged, 3)
        assertEquals(
            "You have unlocked a new Combat Mastery: <col=147194>Ranged III</col>.",
            MasteryMessages.of(MasteryResult.Unlocked, ranged),
        )
        assertEquals(
            "<col=8b1a1a>Not yet:</col> unlock the previous ranged Combat Mastery first.",
            MasteryMessages.of(MasteryResult.PreviousTierMissing, ranged),
        )
        assertEquals(
            "<col=8b1a1a>Not yet:</col> select a Combat Mastery to view first.",
            MasteryMessages.of(MasteryResult.NothingViewed, null),
        )
        assertEquals(
            "<col=147194>Combat Mastery:</col> Defeat TzKal-Zuk. " +
                "You have earned <col=147194>2</col> mastery points.",
            MasteryMessages.taskCompleted(10, 2),
        )
    }
}
