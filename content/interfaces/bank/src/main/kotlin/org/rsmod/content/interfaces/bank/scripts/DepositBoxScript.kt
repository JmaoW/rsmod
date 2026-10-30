package org.rsmod.content.interfaces.bank.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.UpdateInventory.resendSlot
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.worn.WornUnequipResult
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfModalDrag
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.content.interfaces.bank.BankEvents
import org.rsmod.content.interfaces.bank.DepositQuantity
import org.rsmod.content.interfaces.bank.configs.bank_queues
import org.rsmod.content.interfaces.bank.configs.deposit_components
import org.rsmod.content.interfaces.bank.configs.deposit_interfaces
import org.rsmod.content.interfaces.bank.configs.deposit_varbits
import org.rsmod.content.interfaces.bank.configs.deposit_varps
import org.rsmod.content.interfaces.bank.setBanksideExtraOps
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The bank deposit box screen: the inventory, the worn items, the deposit buttons and the
 * left-click amount buttons. Everything that moves items into the bank is the bank screen's own
 * code ([BankInvScript]), so a deposit here lands in the bank exactly where it would from a booth.
 *
 * The client draws all of it from the cache. What it needs from the server is the click events on
 * the inventory slots (the worn slots and buttons carry theirs in the cache) and the amount
 * buttons' state: they only change a varbit on the client, so the server learns the choice from the
 * click.
 */
class DepositBoxScript
@Inject
constructor(
    private val bankScript: BankInvScript,
    private val bankAccess: BankAccess,
    private val objTypes: ObjTypeList,
    private val eventBus: EventBus,
) : PluginScript() {
    private var ProtectedAccess.quantityMode by
        enumVarBit<DepositQuantity>(deposit_varbits.quantity_mode)
    private var ProtectedAccess.requestedQuantity by intVarp(deposit_varps.requested_quantity)

    override fun ScriptContext.startup() {
        val comps = deposit_components

        onIfOpen(deposit_interfaces.deposit_box) { player.onDepositBoxOpen() }
        onIfClose(deposit_interfaces.deposit_box) { player.onDepositBoxClose() }

        onIfModalButton(comps.inventory) { invOp(it.comsub, it.op) }
        onIfModalDrag(comps.inventory) { drag -> with(bankScript) { dragSideInv(drag) } }
        for ((wornSlot, component) in comps.worn_slots) {
            onIfModalButton(component) { wornOp(wornSlot, it.op) }
        }

        onIfModalButton(comps.deposit_inventory) { depositInventory() }
        onIfModalButton(comps.deposit_worn) { depositWorn() }
        onIfModalButton(comps.deposit_lootingbag) { mes("You have nothing to deposit.") }

        onIfModalButton(comps.quantity_1) { quantityMode = DepositQuantity.One }
        onIfModalButton(comps.quantity_5) { quantityMode = DepositQuantity.Five }
        onIfModalButton(comps.quantity_10) { quantityMode = DepositQuantity.Ten }
        onIfModalButton(comps.quantity_all) { quantityMode = DepositQuantity.All }
        onIfModalButton(comps.quantity_x) { selectQuantityX(it.op) }
    }

    private fun Player.onDepositBoxOpen() {
        eventBus.publish(BankEvents.Opened(this))
        setBanksideExtraOps(objTypes)
        ifSetEvents(
            deposit_components.inventory,
            inv.indices,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
            IfEvent.Op6,
            IfEvent.Op7,
            IfEvent.Op8,
            IfEvent.Op9,
            IfEvent.Op10,
            IfEvent.Depth1,
            IfEvent.DragTarget,
        )
    }

    private fun Player.onDepositBoxClose() {
        eventBus.publish(BankEvents.Closed(this))
        queue(bank_queues.bank_compress, 1)
    }

    private suspend fun ProtectedAccess.invOp(slot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(inv, slot)
            return
        }
        if (bankAccess.blocked(this)) {
            return
        }
        val count =
            when (op) {
                IfButtonOp.Op1 -> leftClickCount()
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> {
                    val input = countDialog()
                    if (input > 0) {
                        requestedQuantity = input
                    }
                    input
                }
                IfButtonOp.Op6 -> Int.MAX_VALUE
                IfButtonOp.Op9 -> {
                    with(bankScript) { clickBanksideExtraOp(slot) }
                    return
                }
                else -> return
            }
        if (count <= 0) {
            return
        }
        with(bankScript) { invDeposit(slot, count, inv) }
    }

    private fun ProtectedAccess.leftClickCount(): Int =
        when (quantityMode) {
            DepositQuantity.One -> 1
            DepositQuantity.Five -> 5
            DepositQuantity.Ten -> 10
            DepositQuantity.X -> maxOf(1, requestedQuantity)
            DepositQuantity.All -> Int.MAX_VALUE
        }

    private fun ProtectedAccess.wornOp(wornSlot: Int, op: IfButtonOp) {
        if (op == IfButtonOp.Op10) {
            objExamine(worn, wornSlot)
            return
        }
        if (worn[wornSlot] == null) {
            return resendSlot(worn, wornSlot)
        }
        when (op) {
            IfButtonOp.Op1 -> {
                val result = wornUnequip(wornSlot)
                if (result is WornUnequipResult.Fail) {
                    result.message?.let(::mes)
                    return
                }
                player.setBanksideExtraOps(objTypes)
            }
            IfButtonOp.Op2 -> {
                if (!bankAccess.blocked(this)) {
                    with(bankScript) { depositWornObj(wornSlot) }
                }
            }
            else -> return
        }
    }

    private fun ProtectedAccess.depositInventory() {
        if (bankAccess.blocked(this)) {
            return
        }
        with(bankScript) { depositInv() }
    }

    private fun ProtectedAccess.depositWorn() {
        if (bankAccess.blocked(this)) {
            return
        }
        with(bankScript) { depositWorn() }
    }

    private suspend fun ProtectedAccess.selectQuantityX(op: IfButtonOp) {
        if (op == IfButtonOp.Op2) {
            val input = countDialog("Enter amount:")
            if (input > 0) {
                requestedQuantity = input
            }
        }
        quantityMode = if (requestedQuantity <= 0) DepositQuantity.One else DepositQuantity.X
    }
}
