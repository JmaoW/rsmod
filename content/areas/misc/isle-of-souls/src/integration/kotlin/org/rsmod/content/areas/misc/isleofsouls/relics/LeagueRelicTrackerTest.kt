package org.rsmod.content.areas.misc.isleofsouls.relics

import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.IsleOfSoulsLeagueModals
import org.rsmod.content.areas.misc.isleofsouls.IsleOfSoulsLeaguePanel
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_components
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskVars
import org.rsmod.game.entity.Player

class LeagueRelicTrackerTest {
    private val relics = LeagueRelicTracker()

    private fun Player.setPoints(points: Int) {
        VarPlayerIntMapSetter.set(this, LeagueTaskVars.points_claimed, points)
    }

    private fun Player.choose(card: Int): RelicResult {
        relics.view(this, card)
        return relics.confirm(this)
    }

    @Test
    fun `relic cards map to tiers and options in client order`() {
        assertEquals(23, RelicData.cardCount)
        assertEquals(RelicChoice(0, 1, "Power Miner"), RelicData.choiceForCard(0))
        assertEquals(RelicChoice(0, 3, "Animal Wrangler"), RelicData.choiceForCard(2))
        assertEquals(RelicChoice(1, 1, "Corner Cutter"), RelicData.choiceForCard(3))
        assertEquals(RelicChoice(4, 3, "Slayer Master"), RelicData.choiceForCard(14))
        // Tier 6 has only two relics, so tier 7 starts at card 17.
        assertEquals(RelicChoice(5, 1, "Total Recall"), RelicData.choiceForCard(15))
        assertEquals(RelicChoice(5, 2, "Banker's Note"), RelicData.choiceForCard(16))
        assertEquals(RelicChoice(6, 1, "Pocket Kingdom"), RelicData.choiceForCard(17))
        assertEquals(RelicChoice(7, 3, "Last Stand"), RelicData.choiceForCard(22))
        assertNull(RelicData.choiceForCard(23))
        assertNull(RelicData.choiceForCard(-1))
    }

    @Test
    fun GameTestState.`confirming needs a viewed relic and rejects a second pick in the tier`() =
        runGameTest {
            assertEquals(RelicResult.NothingViewed, relics.confirm(player))

            assertEquals(RelicResult.Selected, player.choose(0))
            assertEquals(1, relics.selected(player, 0))

            // Confirming the same relic again, or another from the same tier, is rejected.
            assertEquals(RelicResult.AlreadyUnlocked, relics.confirm(player))
            assertEquals(RelicResult.TierAlreadyChosen, player.choose(1))
            assertEquals(1, relics.selected(player, 0))
        }

    @Test
    fun GameTestState.`later tiers need the previous tier and enough points`() = runGameTest {
        // Tier 2 (card 3) cannot be chosen before tier 1.
        player.setPoints(10_000)
        assertEquals(RelicResult.PreviousTierMissing, player.choose(3))

        player.setPoints(0)
        assertEquals(RelicResult.Selected, player.choose(0))

        player.setPoints(749)
        assertEquals(RelicResult.NotEnoughPoints, player.choose(3))
        assertEquals(0, relics.selected(player, 1))

        player.setPoints(750)
        assertEquals(RelicResult.Selected, player.choose(4))
        assertEquals(2, relics.selected(player, 1))
    }

    @Test
    fun GameTestState.`xp multiplier follows the chosen relic tiers`() = runGameTest {
        fun choose(tier: Int) {
            VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], 1)
        }
        val mining = stats.mining
        val attack = stats.attack
        assertEquals(5.0, relics.xpMultiplier(player, mining))

        choose(1)
        assertEquals(8.0, relics.xpMultiplier(player, mining))
        assertEquals(8.0, relics.xpMultiplier(player, attack))

        // The tier 3 combat bonus doubles combat skills only.
        choose(2)
        assertEquals(8.0, relics.xpMultiplier(player, mining))
        assertEquals(16.0, relics.xpMultiplier(player, attack))

        choose(4)
        assertEquals(12.0, relics.xpMultiplier(player, mining))
        assertEquals(24.0, relics.xpMultiplier(player, attack))

        choose(6)
        assertEquals(16.0, relics.xpMultiplier(player, mining))
        assertEquals(32.0, relics.xpMultiplier(player, attack))
    }

    @Test
    fun GameTestState.`drop multiplier follows the chosen relic tiers`() = runGameTest {
        fun choose(tier: Int) {
            VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], 1)
        }
        assertEquals(1.0, relics.dropMultiplier(player))

        choose(0)
        assertEquals(5.0, relics.dropMultiplier(player))

        // Tier 2 and 3 relics do not change drops.
        choose(1)
        choose(2)
        assertEquals(5.0, relics.dropMultiplier(player))

        // Tier 4 replaces the 5x with 20x rather than stacking on it.
        choose(3)
        assertEquals(20.0, relics.dropMultiplier(player))
    }

    @Test
    fun GameTestState.`relic screen clicks select a relic through the interface events`() =
        runGameTest(LeagueRelicScript::class) {
            player.ifOpenMain(isle_interfaces.league_relics)

            // Viewing a relic card, then pressing confirm, chooses it.
            player.ifButton(RelicComponents.clickzones, 1)
            advance()
            // The client waits on `league_relic_expanded_view`: 17 components, a state, 2 structs.
            val expanded = client.filterIsInstance<RunClientScript>().filter { it.id == 3193 }
            assertEquals(1, expanded.size)
            val args = expanded.single().values
            assertEquals(21, args.size)
            assertEquals(RelicViewComponents.view_one.packed, args[2])
            assertEquals(RelicViewComponents.loading.packed, args[3])
            assertEquals(RelicViewComponents.select_button.packed, args[8])
            // Select unhides this layer (the "Are you sure?" dialog) and Cancel hides it again.
            assertEquals(RelicViewComponents.confirm.packed, args[10])
            assertEquals(RelicViewComponents.confirm_button.packed, args[13])
            assertEquals(2, args[18]) // selectable
            assertEquals(1119, args[19]) // Lumberjack
            assertEquals(1144, args[20]) // tier 1 passive

            player.ifButton(RelicComponents.confirm_button)
            advance()

            assertEquals(2, relics.selected(player, 0))
            val messages = client.filterIsInstance<MessageGame>().map { it.message }
            assertEquals(
                listOf(
                    "You have unlocked a new Relic: <col=147194>Lumberjack</col>.",
                    "You receive the <col=147194>Echo axe</col>.",
                ),
                messages,
            )
            // Lumberjack hands out its tool.
            assertTrue(objs.league_trailblazer_axe in player.inv)
            // A successful pick closes the Relics screen.
            assertFalse(player.ui.containsModal(isle_interfaces.league_relics))
        }

    @Test
    fun GameTestState.`picking a tier 2 relic through the screen hands out its item`() =
        runGameTest(LeagueRelicScript::class) {
            player.setPoints(750)
            assertEquals(RelicResult.Selected, player.choose(0))
            player.ifOpenMain(isle_interfaces.league_relics)

            // Card 4 is Friendly Forager, the second relic of tier 2.
            player.ifButton(RelicComponents.clickzones, 4)
            advance()
            player.ifButton(RelicComponents.confirm_button)
            advance()

            assertEquals(2, relics.selected(player, 1))
            assertTrue(isle_objs.foragers_pouch in player.inv)
        }

    @Test
    fun GameTestState.`the tasks screen opens as a floating overlay and closes with its button`() =
        runGameTest(IsleOfSoulsLeaguePanel::class, IsleOfSoulsLeagueModals::class) {
            player.ifOpenOverlay(
                isle_interfaces.league_side_panel,
                components.toplevel_target_side2,
            )
            player.ifOpenMain(isle_interfaces.league_relics)
            assertTrue(player.ui.containsModal(isle_interfaces.league_relics))

            // Tasks replaces the open modal, and lives on the floater layer, not in the modal.
            player.ifButton(isle_components.panel_tasks)
            advance()
            assertTrue(player.ui.containsOverlay(isle_interfaces.league_tasks))
            assertFalse(player.ui.containsModal(isle_interfaces.league_tasks))
            assertFalse(player.ui.containsModal(isle_interfaces.league_relics))

            // Its close button closes the overlay.
            player.ifButton(isle_components.tasks_close)
            advance()
            assertFalse(player.ui.containsOverlay(isle_interfaces.league_tasks))
        }

    @Test
    fun GameTestState.`opening another league screen closes the tasks window`() =
        runGameTest(IsleOfSoulsLeaguePanel::class) {
            player.ifOpenOverlay(
                isle_interfaces.league_side_panel,
                components.toplevel_target_side2,
            )
            player.ifButton(isle_components.panel_tasks)
            advance()
            assertTrue(player.ui.containsOverlay(isle_interfaces.league_tasks))

            player.ifButton(isle_components.panel_relics)
            advance()
            assertTrue(player.ui.containsModal(isle_interfaces.league_relics))
            assertFalse(player.ui.containsOverlay(isle_interfaces.league_tasks))
        }

    @Test
    fun GameTestState.`relic view state codes match the clients messages`() = runGameTest {
        val tier0 = RelicChoice(0, 1, "Power Miner")
        val tier1 = RelicChoice(1, 1, "Corner Cutter")

        // Tier 2 with no tier 1 relic yet: previous tier first (3).
        player.setPoints(10_000)
        assertEquals(3, relics.viewState(player, tier1))

        // Tier 1 is free and open.
        player.setPoints(0)
        assertEquals(2, relics.viewState(player, tier0))
        assertEquals(RelicResult.Selected, player.choose(0))
        assertEquals(1, relics.viewState(player, tier0)) // unlocked
        assertEquals(4, relics.viewState(player, RelicChoice(0, 2, "Lumberjack"))) // tier chosen

        // Tier 2 now needs 750 points (0), then becomes selectable (2).
        player.setPoints(749)
        assertEquals(0, relics.viewState(player, tier1))
        player.setPoints(750)
        assertEquals(2, relics.viewState(player, tier1))
    }
}
