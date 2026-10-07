package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.IsleOfSoulsLeaguePanel
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_components
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams

class LeagueMenuTaskTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    @Test
    fun GameTestState.`opening a league screen from the panel completes the menu task`() =
        runGameTest(IsleOfSoulsLeaguePanel::class) {
            player.ifOpenOverlay(
                isle_interfaces.league_side_panel,
                components.toplevel_target_side2,
            )
            assertFalse(tracker().isComplete(player, 189))

            player.ifButton(isle_components.panel_info)
            advance()

            assertTrue(tracker().isComplete(player, 189))
        }

    @Test
    fun GameTestState.`opening the tasks window also completes the menu task`() =
        runGameTest(IsleOfSoulsLeaguePanel::class) {
            player.ifOpenOverlay(
                isle_interfaces.league_side_panel,
                components.toplevel_target_side2,
            )

            player.ifButton(isle_components.panel_tasks)
            advance()

            assertTrue(tracker().isComplete(player, 189))
        }
}
