package org.rsmod.content.interfaces.worldmap

import jakarta.inject.Inject
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class WorldMapScript
@Inject
constructor(private val eventBus: EventBus, private val protectedAccess: ProtectedAccessLauncher) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(world_map_components.orb) { player.selectOrb(op) }
        onIfOverlayButton(world_map_components.close) { player.closeFloating() }
        onIfModalButton(world_map_components.close) { ifClose() }
        onIfOpen(world_map_interfaces.worldmap) { player.startYouAreHere() }
        onIfClose(world_map_interfaces.worldmap) {
            player.clearSoftTimer(world_map_timers.you_are_here)
        }
        onPlayerSoftTimer(world_map_timers.you_are_here) { player.updateYouAreHere() }
    }

    /*
     * The orb ops are set by cs2 `orbs_worldmap_setup`: op2 is "Floating World Map" (relabelled
     * "Close Floating panel" while the floater is occupied) and op3 is "Fullscreen World Map".
     */
    private fun Player.selectOrb(op: IfButtonOp) {
        when (op) {
            IfButtonOp.Op2 -> toggleFloating()
            IfButtonOp.Op3 -> openFullscreen()
            else -> {}
        }
    }

    private fun Player.toggleFloating() {
        if (ui.containsOverlay(world_map_interfaces.worldmap)) {
            closeFloating()
        } else {
            sendYouAreHere()
            ifOpenOverlay(world_map_interfaces.worldmap, eventBus)
        }
    }

    private fun Player.openFullscreen() {
        closeFloating()
        protectedAccess.launch(this) {
            player.sendYouAreHere()
            ifOpenMain(world_map_interfaces.worldmap)
        }
    }

    private fun Player.closeFloating() {
        ifCloseOverlay(world_map_interfaces.worldmap, eventBus)
    }

    /*
     * The "you are here" marker is driven by the `worldmap_youarehere` varc, which the client only
     * ever sets through cs2 `worldmap_transmitdata` (args: you-are-here, clue, gravestone; each a
     * packed coord or -1 for none). `worldmap_init` reads it to choose the initial map, so it must
     * be sent before the interface opens. While the map is open, the overlay timer in cs2
     * `worldmap_overlay` polls the varc, so resending on each cycle keeps the marker following the
     * player.
     */
    private fun Player.sendYouAreHere() {
        runClientScript(WORLDMAP_TRANSMIT_DATA, coords.packed, NO_COORD, NO_COORD)
    }

    private fun Player.startYouAreHere() {
        sendYouAreHere()
        softTimer(world_map_timers.you_are_here, 1)
    }

    private fun Player.updateYouAreHere() {
        val open =
            ui.containsOverlay(world_map_interfaces.worldmap) ||
                ui.containsModal(world_map_interfaces.worldmap)
        if (!open) {
            clearSoftTimer(world_map_timers.you_are_here)
            return
        }
        sendYouAreHere()
    }

    private companion object {
        const val WORLDMAP_TRANSMIT_DATA = 1749
        const val NO_COORD = -1
    }
}
