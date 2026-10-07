package org.rsmod.content.areas.misc.isleofsouls

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifCloseSub
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_components
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.InterfaceType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Opens the league interfaces from the buttons on the journal's Leagues tab.
 *
 * The buttons only need the server to open the matching interface; each interface's own
 * clientscript draws its contents. Interface choices follow each interface's `onLoad` clientscript
 * in the cache.
 */
class IsleOfSoulsLeaguePanel
@Inject
constructor(
    private val eventBus: EventBus,
    private val protectedAccess: ProtectedAccessLauncher,
    private val tracker: LeagueTaskTracker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(isle_components.panel_info) {
            player.openLeagueScreen(isle_interfaces.league_info)
        }
        onIfOverlayButton(isle_components.panel_areas) {
            player.openLeagueScreen(isle_interfaces.league_areas)
        }
        onIfOverlayButton(isle_components.panel_tasks) {
            player.openLeagueScreen(isle_interfaces.league_tasks)
        }
        onIfOverlayButton(isle_components.panel_masteries) {
            player.openLeagueScreen(isle_interfaces.league_combat_mastery)
        }
        onIfOverlayButton(isle_components.panel_relics) {
            player.openLeagueScreen(isle_interfaces.league_relics)
        }
        onIfOverlayButton(isle_components.panel_summary) {
            player.openLeagueScreen(isle_interfaces.league_summary)
        }
        onIfOverlayButton(isle_components.panel_rank) {
            player.openLeagueScreen(isle_interfaces.league_rank)
        }
    }

    /**
     * Closes whatever league screen is open, then opens [interf]. Opening one main modal directly
     * over another leaves the client with a mis-sized window, so this follows the same
     * close-then-open order as the other side-panel buttons.
     *
     * The Tasks screen is the exception: it is a draggable window whose position and size the
     * client saves, and it places itself using the main modal area's coordinates. It therefore has
     * to live on the full-screen floater layer (a sibling of the main modal) instead of inside the
     * main modal, where those coordinates are offset and the window gets clipped.
     */
    private fun Player.openLeagueScreen(interf: InterfaceType) {
        ifClose(eventBus)
        if (ui.containsOverlay(isle_interfaces.league_tasks)) {
            ifCloseSub(isle_interfaces.league_tasks, eventBus)
        }

        if (interf.id == isle_interfaces.league_tasks.id) {
            ifOpenOverlay(interf, eventBus)
            tracker.complete(this, TASK_OPEN_LEAGUES_MENU)
            return
        }

        val opened = protectedAccess.launch(this) { ifOpenMainModal(interf) }
        if (opened) {
            tracker.complete(this, TASK_OPEN_LEAGUES_MENU)
        } else {
            mes("Please finish what you're doing first.")
        }
    }

    private companion object {
        const val TASK_OPEN_LEAGUES_MENU = 189
    }
}
