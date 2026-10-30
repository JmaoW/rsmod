package org.rsmod.content.other.bolt.effects

import jakarta.inject.Inject
import org.rsmod.api.combat.manager.BoltEffectRegistry
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Hands the damage rolls of crossbow shots the effects of enchanted bolts. */
class EnchantedBoltsScript
@Inject
constructor(private val registry: BoltEffectRegistry, private val bolts: EnchantedBolts) :
    PluginScript() {
    override fun ScriptContext.startup() {
        registry.handler = bolts
    }
}
