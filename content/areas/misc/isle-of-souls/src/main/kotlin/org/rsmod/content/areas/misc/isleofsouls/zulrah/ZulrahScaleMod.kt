package org.rsmod.content.areas.misc.isleofsouls.zulrah

import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The league gives more scales than Zulrah's table (ten times, by default: the
 * `zulrahScaleMultiplier` event setting). The drop table is the ordinary one, so this only touches
 * her kill drops, not the scales from dismantling her uniques.
 */
class ZulrahScaleMod : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Modify> {
            if (ZULRAH_TYPES.any { npc.type.isType(it) }) {
                countMultipliers[zulrah_objs.scales] = EventSettings.current.zulrahScaleMultiplier
            }
        }
    }

    private companion object {
        val ZULRAH_TYPES = listOf(ZulrahNpcs.green, ZulrahNpcs.red, ZulrahNpcs.blue)
    }
}
