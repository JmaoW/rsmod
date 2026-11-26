package org.rsmod.content.interfaces.equipment

import jakarta.inject.Inject
import org.rsmod.api.player.events.FollowerCall
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.interfaces.equipment.configs.equip_components
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class EquipmentTabScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onIfOverlayButton(equip_components.call_follower) { player.callFollower() }
    }

    /** A follower system (the isle's pets) answers the call; without one there is no follower. */
    private fun Player.callFollower() {
        val call = FollowerCall(this)
        eventBus.publish(call)
        if (!call.handled) {
            mes("You do not have a follower.")
        }
    }
}
