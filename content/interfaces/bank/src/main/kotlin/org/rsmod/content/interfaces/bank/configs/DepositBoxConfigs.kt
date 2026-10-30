package org.rsmod.content.interfaces.bank.configs

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

internal typealias deposit_interfaces = DepositBoxInterfaces

internal typealias deposit_components = DepositBoxComponents

internal typealias deposit_varbits = DepositBoxVarBits

internal typealias deposit_varps = DepositBoxVarps

object DepositBoxInterfaces : InterfaceReferences() {
    val deposit_box = find("bank_depositbox")
}

object DepositBoxComponents : ComponentReferences() {
    val inventory = find("bank_depositbox:inventory")
    val deposit_inventory = find("bank_depositbox:deposit_inv")
    val deposit_worn = find("bank_depositbox:deposit_worn")
    val deposit_lootingbag = find("bank_depositbox:deposit_lootingbag")
    val quantity_1 = find("bank_depositbox:1")
    val quantity_5 = find("bank_depositbox:5")
    val quantity_10 = find("bank_depositbox:10")
    val quantity_x = find("bank_depositbox:x")
    val quantity_all = find("bank_depositbox:all")

    /** Worn slot (`Wearpos.slot`) to the box's component for it. */
    val worn_slots =
        mapOf(
            0 to find("bank_depositbox:slot0"),
            1 to find("bank_depositbox:slot1"),
            2 to find("bank_depositbox:slot2"),
            3 to find("bank_depositbox:slot3"),
            4 to find("bank_depositbox:slot4"),
            5 to find("bank_depositbox:slot5"),
            7 to find("bank_depositbox:slot7"),
            9 to find("bank_depositbox:slot9"),
            10 to find("bank_depositbox:slot10"),
            12 to find("bank_depositbox:slot12"),
            13 to find("bank_depositbox:slot13"),
        )
}

object DepositBoxVarBits : VarBitReferences() {
    /** The client reads this to choose which deposit amount is the left-click one. */
    val quantity_mode = find("depositbox_mode")

    /** Settings toggle: ask how many to deposit when using an item on a deposit box. */
    val use_ask_quantity = find("bank_depositbox_oplocu_askquantity")
}

object DepositBoxVarps : VarpReferences() {
    val requested_quantity = find("depositbox_requestedquantity")
}
