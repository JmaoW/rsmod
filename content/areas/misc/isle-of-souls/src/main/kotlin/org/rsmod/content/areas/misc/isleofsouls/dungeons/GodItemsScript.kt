package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.script.advanced.onWearposChange
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Keeps [GodAlignmentVars] in line with the worn items: at login and on every equip or unequip. */
class GodItemsScript @Inject constructor(private val godItems: GodItems) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { godItems.sync(player) }
        onWearposChange { godItems.sync(player) }
    }
}
