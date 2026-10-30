package org.rsmod.content.other.dragonfire

import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.plugin.module.PluginModule

class DragonfireModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpecialAttackMap>(DragonfireShieldSpecials::class.java)
    }
}
