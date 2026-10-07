package org.rsmod.content.generic.locs.banks

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class BankBooth @Inject constructor(private val bankAccess: BankAccess) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2(content.bank_booth) { bankAccess.open(this) }
    }
}
