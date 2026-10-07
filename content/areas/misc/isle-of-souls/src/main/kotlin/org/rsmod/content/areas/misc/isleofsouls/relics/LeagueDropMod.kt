package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.script.onEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Makes monster drops more likely for players whose relics boost the drop rate. */
class LeagueDropMod @Inject constructor(private val relics: LeagueRelicTracker) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcDropEvents.Modify> { multiplier *= relics.dropMultiplier(hero) }
    }
}
