package org.rsmod.content.generic.locs.banks

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankChest @Inject constructor(private val bankAccess: BankAccess) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(content.bank_chest) { bankAccess.open(this) }
    }
}
