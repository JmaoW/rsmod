package org.rsmod.content.areas.misc.isleofsouls.instances

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcTargeting
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Keeps a boss's multi-target attacks inside its team instance: a npc in an instance only attacks
 * players in that same instance, and never players in the copy next to it.
 */
class InstanceTargetScope
@Inject
constructor(private val instances: TeamInstances, private val targeting: NpcTargeting) :
    PluginScript() {
    override fun ScriptContext.startup() {
        targeting.addScope { npc, player ->
            val live = instances.live()
            val npcInstance = live.firstOrNull { it.contains(npc.coords) }
            val playerInstance = live.firstOrNull { it.contains(player.coords) }
            if (npcInstance == null && playerInstance == null) null
            else npcInstance === playerInstance
        }
    }
}
