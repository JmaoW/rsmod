package org.rsmod.content.areas.misc.isleofsouls.revenant

import org.rsmod.api.config.refs.varobjs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/**
 * Ether stored in revenant weapons and the bracelet of ethereum. The count lives in the item's
 * `ether_charges` var, so it goes wherever the item goes. A weapon's count includes the
 * [RevenantEther.ACTIVATION] ether that the passive never spends; the bracelet has no activation.
 */
object EtherCharges {
    /** [obj] as [type] with its ether set to [total] (the other bits of its vars are kept). */
    fun withStored(obj: InvObj, type: ObjType, total: Int): InvObj =
        InvObj(type, 1, obj.vars.withBits(varobjs.ether_charges.bits, total))

    /** Whether the bracelet [obj] takes in the ether that the revenants its wearer kills drop. */
    fun absorbing(obj: InvObj?): Boolean =
        obj != null && obj.vars.getBits(varobjs.ether_absorb.bits) == 1

    fun withAbsorbing(obj: InvObj, on: Boolean): InvObj =
        obj.copy(vars = obj.vars.withBits(varobjs.ether_absorb.bits, if (on) 1 else 0))

    /** The bracelet of ethereum [player] wears, charged or not. */
    fun wornBracelet(player: Player): InvObj? {
        val obj = player.worn[Wearpos.Hands.slot]
        return if (obj.isType(ether_objs.bracelet) || obj.isType(ether_objs.bracelet_uncharged)) {
            obj
        } else {
            null
        }
    }

    /** The ether in the bracelet [player] wears, `0` if it is not charged or not worn. */
    fun braceletEther(player: Player): Int {
        val obj = player.worn[Wearpos.Hands.slot]
        return if (obj.isType(ether_objs.bracelet)) RevenantEther.stored(obj) else 0
    }

    /**
     * Spends one ether from the charged revenant weapon [player] wields, if it has one to spend.
     * Returns whether an ether was spent. The activation ether is never spent, and a weapon that
     * has none to spend stays charged (it only loses its passive).
     */
    fun spendWeaponEther(player: Player): Boolean {
        val weapon = player.worn[Wearpos.RightHand.slot] ?: return false
        val charged = EtherItems.chargedOf(weapon) ?: return false
        if (RevenantEther.usable(weapon) <= 0) {
            return false
        }
        val left = RevenantEther.stored(weapon) - 1
        player.worn[Wearpos.RightHand.slot] = withStored(weapon, charged.charged, left)
        if (left <= RevenantEther.ACTIVATION) {
            player.mes("Your ${charged.name} has run out of ether.")
        }
        return true
    }

    /**
     * Spends one ether from the charged bracelet [player] wears. A bracelet that spends its last
     * ether is the uncharged one again. Returns whether an ether was spent.
     */
    fun spendBraceletEther(player: Player): Boolean {
        val bracelet = player.worn[Wearpos.Hands.slot]
        if (!bracelet.isType(ether_objs.bracelet)) {
            return false
        }
        val left = RevenantEther.stored(bracelet) - 1
        if (left <= 0) {
            player.worn[Wearpos.Hands.slot] = withStored(bracelet, ether_objs.bracelet_uncharged, 0)
            player.mes("Your bracelet of ethereum has run out of ether.")
        } else {
            player.worn[Wearpos.Hands.slot] = withStored(bracelet, ether_objs.bracelet, left)
        }
        return true
    }

    /** What the bracelet turns a revenant's hit of [damage] into: a quarter of it, rounded. */
    fun braceletDamage(damage: Int): Int = (damage + 2) / 4
}
