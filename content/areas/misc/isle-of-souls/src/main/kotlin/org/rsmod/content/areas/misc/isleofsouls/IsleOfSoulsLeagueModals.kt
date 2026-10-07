package org.rsmod.content.areas.misc.isleofsouls

import jakarta.inject.Inject
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_components
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.events.EventBus
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Handles closing the league interfaces that the Leagues tab opens. */
class IsleOfSoulsLeagueModals @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        val staticCloseButtons =
            listOf(
                isle_components.relics_close,
                isle_components.areas_close,
                isle_components.masteries_close,
                isle_components.rank_close,
                isle_components.info_close,
            )
        for (closeButton in staticCloseButtons) {
            onIfModalButton(closeButton) { ifClose() }
        }

        // The Tasks screen is a draggable window that lives on the full-screen floater layer rather
        // than in the main modal, so its close button is an overlay button.
        onIfOverlayButton(isle_components.tasks_close) {
            player.ifCloseSub(isle_interfaces.league_tasks, eventBus)
        }

        // The summary's frame and close button are built dynamically by the `steelborder`
        // clientscript, so the button is a child of the frame layer and the server has to enable
        // its events itself. The "X" graphic is created as child 11 of that layer.
        onIfOpen(isle_interfaces.league_summary) {
            player.ifSetEvents(
                isle_components.summary_frame,
                SUMMARY_CLOSE_CHILD..SUMMARY_CLOSE_CHILD,
                IfEvent.Op1,
            )
        }
        onIfModalButton(isle_components.summary_frame) {
            if (it.comsub == SUMMARY_CLOSE_CHILD) {
                ifClose()
            }
        }
    }

    private companion object {
        const val SUMMARY_CLOSE_CHILD = 11
    }
}
