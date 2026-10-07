package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RelicMessagesTest {
    private val lumberjack = RelicChoice(tier = 0, option = 2, name = "Lumberjack")
    private val tier3 = RelicChoice(tier = 3, option = 1, name = "Golden God")

    private fun message(
        result: RelicResult,
        choice: RelicChoice? = lumberjack,
        points: Int = 0,
        onTeam: Boolean = false,
    ) = RelicMessages.of(result, choice, points, onTeam)

    @Test
    fun `a chosen relic is named in blue`() {
        assertEquals(
            "You have unlocked a new Relic: <col=147194>Lumberjack</col>.",
            message(RelicResult.Selected),
        )
    }

    @Test
    fun `a chosen relic with no known choice still gets a message`() {
        assertEquals("You have unlocked a new Relic.", message(RelicResult.Selected, choice = null))
    }

    @Test
    fun `refusals start with a red not yet`() {
        val red = "<col=8b1a1a>Not yet:</col>"
        assertEquals("$red select a Relic to view first.", message(RelicResult.NothingViewed))
        assertEquals(
            "$red you have already unlocked this Relic.",
            message(RelicResult.AlreadyUnlocked),
        )
        assertEquals(
            "$red you have already unlocked a Relic from this tier.",
            message(RelicResult.TierAlreadyChosen),
        )
        assertEquals(
            "$red unlock a Relic from the previous tier first.",
            message(RelicResult.PreviousTierMissing),
        )
    }

    @Test
    fun `not enough points shows how far along the player is`() {
        assertEquals(
            "<col=8b1a1a>Not yet:</col> you have <col=147194>340</col> of the " +
                "<col=147194>2,500</col> League Points needed for a Relic from this tier.",
            message(RelicResult.NotEnoughPoints, tier3, points = 340),
        )
    }

    @Test
    fun `team members are told it is the team's points`() {
        assertEquals(
            "<col=8b1a1a>Not yet:</col> your team has <col=147194>340</col> of the " +
                "<col=147194>2,500</col> League Points needed for a Relic from this tier.",
            message(RelicResult.NotEnoughPoints, tier3, points = 340, onTeam = true),
        )
    }
}
