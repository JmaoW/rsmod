package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskVars
import org.rsmod.game.entity.Player

class LeagueReloadedTest {
    private val relics = LeagueRelicTracker()

    private fun Player.choose(card: Int): RelicResult {
        relics.view(this, card)
        return relics.confirm(this)
    }

    /** Power Miner, Corner Cutter, Clue Compass, then [tier4Card] in tier 4. */
    private fun Player.reachTier4(tier4Card: Int) {
        relics.reset(this)
        VarPlayerIntMapSetter.set(this, LeagueTaskVars.points_claimed, 10_000)
        for (card in listOf(0, 3, 6, tier4Card)) {
            assertEquals(RelicResult.Selected, choose(card))
        }
    }

    @Test
    fun GameTestState.`reloaded picks one extra relic from an earlier tier`() = runGameTest {
        player.reachTier4(tier4Card = 10)
        assertNull(relics.reloadedTier(player))

        // Lumberjack: the other tier 1 relic.
        assertEquals(RelicResult.Selected, player.choose(1))
        assertEquals(1, relics.selected(player, 0), "Power Miner stays")
        assertEquals(2, relics.other(player, 0))
        assertTrue(relics.has(player, 0, 1))
        assertTrue(relics.has(player, 0, 2))
        assertEquals(0, relics.reloadedTier(player))

        // Only one extra pick.
        assertEquals(RelicResult.TierAlreadyChosen, player.choose(4))
        assertFalse(relics.has(player, 1, 2))
    }

    @Test
    fun GameTestState.`reloaded cannot take a relic from its own tier or later`() = runGameTest {
        player.reachTier4(tier4Card = 10)
        assertEquals(RelicResult.TierAlreadyChosen, player.choose(11))
        assertEquals(RelicResult.AlreadyUnlocked, player.choose(0))
        assertNull(relics.reloadedTier(player))
    }

    @Test
    fun GameTestState.`without reloaded earlier tiers stay locked`() = runGameTest {
        player.reachTier4(tier4Card = 9)
        assertEquals(RelicResult.TierAlreadyChosen, player.choose(1))
        assertFalse(relics.has(player, 0, 2))
    }

    @Test
    fun GameTestState.`relic items count the reloaded pick`() = runGameTest {
        player.reachTier4(tier4Card = 10)
        player.choose(4)
        val tools = LeagueRelicTools(relics, objTypes)
        assertTrue(tools.isChosen(player, RelicItem.CornerCutter))
        assertTrue(tools.isChosen(player, RelicItem.FriendlyForager))
    }
}
