package org.rsmod.content.generic.locs.banks

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.interfaces.bank.DepositBoxAccess
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class DepositBox @Inject constructor(private val depositBoxAccess: DepositBoxAccess) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(content.bank_deposit_box) { depositBoxAccess.open(this) }
        // Bank booths have a "Deposit-box" option as well.
        onOpLoc4(content.bank_booth) { depositBoxAccess.open(this) }
        onOpLocU(content.bank_deposit_box) { depositBoxAccess.depositUsed(this, it.invSlot) }
    }
}
