package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskVars
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wires up the league Relics screen: clicking a relic card records which relic is being viewed, and
 * the confirm button applies it and closes the screen.
 */
class LeagueRelicScript
@Inject
constructor(
    private val relics: LeagueRelicTracker,
    private val tasks: LeagueTaskTracker,
    private val tools: LeagueRelicTools,
    private val teams: LeagueTeams,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(isle_interfaces.league_relics) {
            for (component in RelicEventTargets.all) {
                player.ifSetEvents(component, -1..-1, IfEvent.Op1, IfEvent.Op2)
                player.ifSetEvents(component, 0..CHILD_EVENT_LIMIT, IfEvent.Op1, IfEvent.Op2)
            }
        }

        onIfModalButton(RelicComponents.clickzones) {
            relics.view(player, it.comsub)
            openExpandedView(player, it.comsub)
        }

        onIfModalButton(RelicComponents.confirm_button) {
            val choice = relics.viewedChoice(player)
            val result = relics.confirm(player)
            val points = player.vars[LeagueTaskVars.points_claimed]
            val onTeam = teams.teamOf(player) != null
            player.mes(RelicMessages.of(result, choice, points, onTeam))
            if (result == RelicResult.Selected) {
                with(tools) { grantMissing() }
                tasks.refreshLeaguePanel(player)
                ifClose()
            }
        }
    }

    /**
     * Clicking a relic card makes the client show a "Loading..." layer and wait for the server to
     * run `league_relic_expanded_view`, which draws the relic's details and hides the loading
     * layer. Its 21 int arguments are 17 component handles, a state code, and the relic and
     * tier-passive struct ids.
     */
    private fun openExpandedView(player: Player, card: Int) {
        val choice = RelicData.choiceForCard(card) ?: return
        val view = RelicViewComponents
        val args =
            listOf(
                view.view_all.packed,
                view.relic_available_header.packed,
                view.view_one.packed,
                view.loading.packed,
                view.icon.packed,
                view.relic_name.packed,
                view.description_header.packed,
                view.description.packed,
                view.select_button.packed,
                view.select_back.packed,
                view.confirm.packed,
                view.confirm_frame.packed,
                view.confirm_text.packed,
                view.confirm_button.packed,
                view.confirm_cancel.packed,
                view.passive_header.packed,
                view.passive_description.packed,
                view.view_all_scrollbar.packed,
                relics.viewState(player, choice),
                RelicData.relicStructs[choice.tier][choice.option - 1],
                RelicData.passiveStructs[choice.tier],
            )
        player.runClientScript(EXPANDED_VIEW_SCRIPT, args)
    }

    private companion object {
        /** `league_relic_expanded_view`. */
        const val EXPANDED_VIEW_SCRIPT = 3193

        /** Highest child index that gets events; the card layer has 23 children. */
        const val CHILD_EVENT_LIMIT = 127
    }
}
