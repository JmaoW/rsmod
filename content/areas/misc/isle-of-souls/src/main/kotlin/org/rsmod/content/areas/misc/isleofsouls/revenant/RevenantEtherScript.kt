package org.rsmod.content.areas.misc.isleofsouls.revenant

import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Charging and looking after the revenant weapons and the bracelet of ethereum (OSRS Wiki).
 * Revenant ether is used on a weapon to charge it. The first 1,000 activate the weapon and are not
 * spent by it; up to 16,000 more can be added. Uncharging gives all of it back, and dismantling an
 * uncharged weapon gives 7,500 ether (the bracelet 250), or takes the part off a weapon that was
 * upgraded.
 *
 * The cache's item options are the ones used here: Check (3rd option of a charged item), Swap (4th
 * of the sceptres), Uncharge (5th of a charged item) and Dismantle (3rd of an uncharged weapon, 4th
 * of the uncharged bracelet). The bracelet's Toggle-absorption is the 3rd option when it is
 * uncharged and the 4th when charged.
 *
 * Not in the Wiki and so guessed: the wording of every message, and that the upgrades can be taken
 * apart again (the Wiki says so only for the Accursed sceptre).
 */
class RevenantEtherScript @Inject constructor(private val objRepo: ObjRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        for (weapon in EtherItems.weapons) {
            onOpHeldU(weapon.uncharged, ether_objs.ether) { chargeWeapon(weapon, it.firstSlot) }
            onOpHeldU(weapon.charged, ether_objs.ether) { chargeWeapon(weapon, it.firstSlot) }
            onOpHeld3(weapon.charged) { checkWeapon(weapon, it.obj) }
            // Worn, a charged weapon's Check is its `wear_op1` (Op2 of the worn item).
            onOpWorn2(weapon.charged) { checkWeapon(weapon, it.obj) }
            onOpHeld5(weapon.charged) { unchargeWeapon(weapon, it.inventory, it.slot) }
            onOpHeld3(weapon.uncharged) { dismantle(weapon, it.inventory, it.slot) }
        }
        for ((from, to) in EtherItems.swaps) {
            onOpHeld4(from.uncharged) { swap(it.inventory, it.slot, it.obj, to.uncharged) }
            onOpHeld4(from.charged) { swap(it.inventory, it.slot, it.obj, to.charged) }
        }
        for (upgrade in EtherItems.upgrades) {
            onOpHeldU(upgrade.plain, upgrade.part) { upgrade(upgrade, it.firstSlot, it.secondSlot) }
        }

        onOpHeldU(ether_objs.bracelet, ether_objs.ether) { chargeBracelet(it.firstSlot) }
        onOpHeldU(ether_objs.bracelet_uncharged, ether_objs.ether) { chargeBracelet(it.firstSlot) }
        onOpHeld3(ether_objs.bracelet) { checkBracelet(it.obj) }
        onOpHeld4(ether_objs.bracelet) { toggleAbsorption(it.inventory, it.slot) }
        onOpHeld5(ether_objs.bracelet) { unchargeBracelet(it.inventory, it.slot) }
        onOpHeld3(ether_objs.bracelet_uncharged) { toggleAbsorption(it.inventory, it.slot) }
        onOpHeld4(ether_objs.bracelet_uncharged) { dismantleBracelet(it.inventory, it.slot) }

        // Worn, the bracelet's Check and Toggle-absorption are its `wear_op1` and `wear_op2`, in
        // both forms (Op2 and Op3 of the worn item).
        onOpWorn2(ether_objs.bracelet) { checkBracelet(it.obj) }
        onOpWorn3(ether_objs.bracelet) { toggleAbsorption(player.worn, it.slot) }
        onOpWorn2(ether_objs.bracelet_uncharged) { checkBracelet(it.obj) }
        onOpWorn3(ether_objs.bracelet_uncharged) { toggleAbsorption(player.worn, it.slot) }
    }

    private suspend fun ProtectedAccess.chargeWeapon(weapon: EtherWeapon, slot: Int) {
        val current = inv[slot] ?: return
        val stored = RevenantEther.stored(current)
        val room = RevenantEther.ACTIVATION + RevenantEther.WEAPON_CAPACITY - stored
        if (room <= 0) {
            mes("Your ${weapon.name} cannot hold any more ether.")
            return
        }
        val have = invTotal(inv, ether_objs.ether)
        val activating = stored == 0
        if (have < (if (activating) RevenantEther.ACTIVATION else 1)) {
            if (activating) {
                mes(
                    "You need at least ${RevenantEther.ACTIVATION.formatAmount} revenant ether " +
                        "to activate your ${weapon.name}."
                )
            } else {
                mes("You do not have any revenant ether.")
            }
            return
        }
        val limit = min(have, room)
        val question =
            if (activating) {
                "How much ether? (${RevenantEther.ACTIVATION.formatAmount} to activate, " +
                    "up to ${limit.formatAmount})"
            } else {
                "How much ether do you want to add? (Up to ${limit.formatAmount})"
            }
        val requested = min(countDialog(question), limit)
        if (requested <= 0) {
            return
        }
        if (activating && requested < RevenantEther.ACTIVATION) {
            mes(
                "It takes ${RevenantEther.ACTIVATION.formatAmount} revenant ether to activate " +
                    "your ${weapon.name}."
            )
            return
        }
        // The slot is read again: the dialog was open while the inventory could have changed.
        val now = inv[slot]
        if (!(now.isType(weapon.uncharged) || now.isType(weapon.charged))) {
            return
        }
        if (invDel(inv, ether_objs.ether, requested).failure) {
            return
        }
        inv[slot] =
            EtherCharges.withStored(now!!, weapon.charged, RevenantEther.stored(now) + requested)
        if (activating) {
            mes(
                "You activate your ${weapon.name} with ${RevenantEther.ACTIVATION.formatAmount} " +
                    "revenant ether and add ${(requested - RevenantEther.ACTIVATION).formatAmount}."
            )
        } else {
            mes("You add ${requested.formatAmount} revenant ether to your ${weapon.name}.")
        }
    }

    private fun ProtectedAccess.checkWeapon(weapon: EtherWeapon, obj: InvObj) {
        val usable = RevenantEther.usable(obj)
        mes("Your ${weapon.name} has ${usable.formatAmount} revenant ether left to use.")
    }

    private suspend fun ProtectedAccess.unchargeWeapon(
        weapon: EtherWeapon,
        inventory: Inventory,
        slot: Int,
    ) {
        val obj = inventory[slot] ?: return
        val stored = RevenantEther.stored(obj)
        val proceed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title = "Uncharge your ${weapon.name}? It gives back all of its ether.",
            )
        if (!proceed || !inventory[slot].isType(weapon.charged)) {
            return
        }
        inventory[slot] = InvObj(weapon.uncharged)
        invAddOrDrop(objRepo, ether_objs.ether, stored)
        mes("You uncharge your ${weapon.name} and get ${stored.formatAmount} revenant ether back.")
    }

    /** The Dismantle option of an uncharged weapon: ether, or the part of an upgrade. */
    private suspend fun ProtectedAccess.dismantle(
        weapon: EtherWeapon,
        inventory: Inventory,
        slot: Int,
    ) {
        val upgrade = EtherItems.upgrades.firstOrNull { inventory[slot].isType(it.made) }
        val prompt =
            if (upgrade != null) {
                "Take the part off your ${weapon.name}?"
            } else {
                "Dismantle your ${weapon.name} for " +
                    "${EtherItems.DISMANTLE_WEAPON_ETHER.formatAmount} revenant ether?"
            }
        val proceed = choice2("Proceed.", true, "Cancel.", false, title = prompt)
        if (!proceed || !inventory[slot].isType(weapon.uncharged)) {
            return
        }
        if (upgrade != null) {
            inventory[slot] = InvObj(upgrade.plain)
            invAddOrDrop(objRepo, upgrade.part)
            mes("You take the part off your ${weapon.name}.")
            return
        }
        inventory[slot] = null
        invAddOrDrop(objRepo, ether_objs.ether, EtherItems.DISMANTLE_WEAPON_ETHER)
        mes(
            "You dismantle your ${weapon.name} and get " +
                "${EtherItems.DISMANTLE_WEAPON_ETHER.formatAmount} revenant ether."
        )
    }

    /** The Swap option of the sceptres: the same sceptre, with or without autocasting. */
    private fun ProtectedAccess.swap(inventory: Inventory, slot: Int, obj: InvObj, to: ObjType) {
        inventory[slot] = InvObj(to, obj.count, obj.vars)
        mes("You swap your sceptre to its other form.")
    }

    /** Puts [upgrade]'s part on its plain weapon, which takes [EtherItems.UPGRADE_LEVEL]. */
    private fun ProtectedAccess.upgrade(
        upgrade: EtherItems.Upgrade,
        plainSlot: Int,
        partSlot: Int,
    ) {
        if (!inv[plainSlot].isType(upgrade.plain) || !inv[partSlot].isType(upgrade.part)) {
            return
        }
        if (player.stat(upgrade.skill) < EtherItems.UPGRADE_LEVEL) {
            mes(
                "You need ${EtherItems.UPGRADE_LEVEL} ${upgrade.skillName} to make the " +
                    "${upgrade.label}."
            )
            return
        }
        invDel(inv, upgrade.part, 1, slot = partSlot)
        inv[plainSlot] = InvObj(upgrade.made)
        mes("You make the ${upgrade.label}.")
    }

    private suspend fun ProtectedAccess.chargeBracelet(slot: Int) {
        val current = inv[slot] ?: return
        val stored = RevenantEther.stored(current)
        val room = RevenantEther.BRACELET_CAPACITY - stored
        if (room <= 0) {
            mes("Your bracelet of ethereum cannot hold any more ether.")
            return
        }
        val have = invTotal(inv, ether_objs.ether)
        if (have <= 0) {
            mes("You do not have any revenant ether.")
            return
        }
        val limit = min(have, room)
        val requested =
            min(
                countDialog("How much ether do you want to add? (Up to ${limit.formatAmount})"),
                limit,
            )
        if (requested <= 0) {
            return
        }
        val now = inv[slot]
        if (!(now.isType(ether_objs.bracelet) || now.isType(ether_objs.bracelet_uncharged))) {
            return
        }
        if (invDel(inv, ether_objs.ether, requested).failure) {
            return
        }
        inv[slot] =
            EtherCharges.withStored(
                now!!,
                ether_objs.bracelet,
                RevenantEther.stored(now) + requested,
            )
        mes("You add ${requested.formatAmount} revenant ether to your bracelet of ethereum.")
    }

    private fun ProtectedAccess.checkBracelet(obj: InvObj) {
        mes(
            "Your bracelet of ethereum has ${RevenantEther.stored(obj).formatAmount} revenant ether."
        )
    }

    private suspend fun ProtectedAccess.unchargeBracelet(inventory: Inventory, slot: Int) {
        val obj = inventory[slot] ?: return
        val stored = RevenantEther.stored(obj)
        val proceed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title = "Uncharge your bracelet of ethereum? It gives back all of its ether.",
            )
        if (!proceed || !inventory[slot].isType(ether_objs.bracelet)) {
            return
        }
        inventory[slot] = EtherCharges.withStored(obj, ether_objs.bracelet_uncharged, 0)
        invAddOrDrop(objRepo, ether_objs.ether, stored)
        mes("You uncharge your bracelet of ethereum and get ${stored.formatAmount} ether back.")
    }

    private fun ProtectedAccess.toggleAbsorption(inventory: Inventory, slot: Int) {
        val obj = inventory[slot] ?: return
        val on = !EtherCharges.absorbing(obj)
        inventory[slot] = EtherCharges.withAbsorbing(obj, on)
        if (on) {
            mes("Your bracelet of ethereum will absorb the ether revenants drop.")
        } else {
            mes("Your bracelet of ethereum will no longer absorb the ether revenants drop.")
        }
    }

    private suspend fun ProtectedAccess.dismantleBracelet(inventory: Inventory, slot: Int) {
        val proceed =
            choice2(
                "Proceed.",
                true,
                "Cancel.",
                false,
                title =
                    "Dismantle your bracelet of ethereum for " +
                        "${EtherItems.DISMANTLE_BRACELET_ETHER.formatAmount} revenant ether?",
            )
        if (!proceed || !inventory[slot].isType(ether_objs.bracelet_uncharged)) {
            return
        }
        inventory[slot] = null
        invAddOrDrop(objRepo, ether_objs.ether, EtherItems.DISMANTLE_BRACELET_ETHER)
        mes(
            "You dismantle your bracelet of ethereum and get " +
                "${EtherItems.DISMANTLE_BRACELET_ETHER.formatAmount} revenant ether."
        )
    }
}
