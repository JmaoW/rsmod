package org.rsmod.api.player.worn

import org.rsmod.api.config.refs.varobjs
import org.rsmod.game.inv.InvObj
import org.rsmod.utils.bits.getBits

/**
 * The revenant ether stored in a revenant weapon (Viggora's chainmace, Craw's bow, Thammaron's
 * sceptre and the weapons made from them) or in the bracelet of ethereum.
 *
 * A weapon is activated with [ACTIVATION] ether that its passive never uses, so its stored count is
 * the activation plus the ether that can be spent: [usable] is what is left after the activation.
 * The bracelet has no activation.
 */
public object RevenantEther {
    /** The ether a weapon takes to activate. It is returned only when the weapon is uncharged. */
    public const val ACTIVATION: Int = 1_000

    /** The ether a weapon can hold on top of its activation. */
    public const val WEAPON_CAPACITY: Int = 16_000

    /** The ether the bracelet of ethereum can hold. */
    public const val BRACELET_CAPACITY: Int = 16_000

    /** The ether stored in [obj], activation included. */
    public fun stored(obj: InvObj?): Int = obj?.vars?.getBits(varobjs.ether_charges.bits) ?: 0

    /** The ether [obj] (a weapon) can still spend: its stored ether without the activation. */
    public fun usable(obj: InvObj?): Int = maxOf(0, stored(obj) - ACTIVATION)
}
