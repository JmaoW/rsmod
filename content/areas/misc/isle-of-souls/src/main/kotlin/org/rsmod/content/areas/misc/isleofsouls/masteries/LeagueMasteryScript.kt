package org.rsmod.content.areas.misc.isleofsouls.masteries

import jakarta.inject.Inject
import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wires up the league Combat Masteries screen. The client draws the grid from the progress varbits.
 * Clicking a mastery records which one is being viewed and asks the client to draw its details, and
 * the confirm button unlocks it.
 */
class LeagueMasteryScript
@Inject
constructor(private val masteries: LeagueMasteryTracker, private val tasks: LeagueMasteryTasks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(isle_interfaces.league_combat_mastery) {
            // Bring the task copies and point totals up to date before the client draws.
            tasks.sync(player)
            for (component in EVENT_TARGETS) {
                player.ifSetEvents(component, -1..-1, IfEvent.Op1)
                player.ifSetEvents(component, 0..CHILD_EVENT_LIMIT, IfEvent.Op1)
            }
        }

        onIfModalButton(MasteryComponents.clickzones) {
            masteries.view(player, it.comsub)
            openExpandedView(player, it.comsub)
        }

        onIfModalButton(MasteryComponents.confirm_button) {
            val choice = masteries.viewedChoice(player)
            val result = masteries.confirm(player)
            player.mes(MasteryMessages.of(result, choice))
            if (result == MasteryResult.Unlocked) {
                redraw(player)
            }
        }
    }

    /**
     * Clicking a mastery makes the client show a "Loading..." layer and wait for the server to run
     * `league_combat_mastery_expanded_view`, which draws the details and hides the loading layer.
     * Its 19 int arguments are 17 component handles followed by the mastery and passive struct ids.
     * The script works out locked, available and unlocked itself from the progress varbits.
     */
    private fun openExpandedView(player: Player, card: Int) {
        val choice = MasteryData.choiceForCard(card) ?: return
        val view = MasteryViewComponents
        val args =
            listOf(
                view.view_all.packed,
                view.view_all_scrollbar.packed,
                view.view_one.packed,
                view.loading.packed,
                view.icon.packed,
                view.name.packed,
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
                MasteryData.structOf(choice),
                MasteryData.passiveStructOf(choice),
            )
        player.runClientScript(EXPANDED_VIEW_SCRIPT, args)
    }

    /**
     * Redraws the grid and returns to it from the expanded view. The first argument is the grid's
     * scroll position, which only the client knows, so the grid goes back to the top.
     */
    private fun redraw(player: Player) {
        player.runClientScript(DRAW_CONTENT_SCRIPT, 0, CombatMasteries.MAX_TIER)
    }

    private companion object {
        /** `league_combat_mastery_expanded_view`. */
        const val EXPANDED_VIEW_SCRIPT = 7674

        /** `league_combat_mastery_draw_content`. */
        const val DRAW_CONTENT_SCRIPT = 7661

        /** Highest child index that gets events; the grid has 18 masteries. */
        const val CHILD_EVENT_LIMIT = 31

        val EVENT_TARGETS =
            listOf(
                MasteryComponents.clickzones,
                MasteryComponents.confirm_button,
                MasteryComponents.select_button,
                MasteryComponents.select_back,
                MasteryComponents.confirm_cancel,
            )
    }
}
