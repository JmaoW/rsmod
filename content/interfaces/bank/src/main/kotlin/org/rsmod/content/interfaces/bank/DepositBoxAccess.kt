package org.rsmod.content.interfaces.bank

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.utils.vars.VarEnumDelegate
import org.rsmod.content.interfaces.bank.configs.deposit_interfaces
import org.rsmod.content.interfaces.bank.configs.deposit_varbits
import org.rsmod.content.interfaces.bank.scripts.BankInvScript

/**
 * The deposit box's left-click amount. The values are the client's own: its quantity buttons write
 * them, and the item menu it builds reads them back, so they are not in order of amount.
 */
enum class DepositQuantity(override val varValue: Int) : VarEnumDelegate {
    One(0),
    Five(1),
    All(2),
    X(3),
    Ten(4),
}

/**
 * Opening a deposit box and depositing into the bank with it, for the loc scripts that sit next to
 * the bank ones. The deposit box puts items in the same bank inventory a booth does, so it goes
 * through [BankAccess] too and refuses when another player is using a shared bank.
 */
class DepositBoxAccess
@Inject
constructor(private val bankAccess: BankAccess, private val bankScript: BankInvScript) {
    private val ProtectedAccess.askQuantity by intVarBit(deposit_varbits.use_ask_quantity)

    /** Opens the deposit box screen for [access] unless the bank is [BankAccess.blocked]. */
    fun open(access: ProtectedAccess): Boolean {
        if (bankAccess.blocked(access)) {
            return false
        }
        access.ifOpenMainModal(deposit_interfaces.deposit_box)
        return true
    }

    /**
     * Deposits the inventory obj in [slot] after it was used on a deposit box: all of it, or an
     * amount typed in when the player has the setting for that turned on.
     */
    suspend fun depositUsed(access: ProtectedAccess, slot: Int) {
        if (access.inv[slot] == null || bankAccess.blocked(access)) {
            return
        }
        val count = if (access.askQuantity == 1) access.countDialog() else Int.MAX_VALUE
        if (count <= 0) {
            return
        }
        with(bankScript) { access.invDeposit(slot, count, access.inv) }
    }
}
