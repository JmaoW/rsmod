package org.rsmod.content.other.special.attacks.ranged

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos

/**
 * The Tonalztics of Ralos (wiki): a thrown weapon that comes back, so it uses no ammunition. It
 * hits for up to 75% of the player's ranged max hit. Charged with sunfire splinters (up to 20,000,
 * the varbit `charges_tonalztics_of_ralos_quantity`) it throws two blades, each rolled on its own,
 * and every throw uses one charge, special attack or not. The last charge turns it back into the
 * uncharged weapon. Its normal attack is the Colosseum's `ColosseumWeapons`; its special is
 * Division in [RareRangedSpecials].
 */
object TonalzticsOfRalos {
    const val MAX_HIT_FRACTION = 0.75
    const val MAX_CHARGES = 20_000

    object Vars : VarBitReferences() {
        val charges = find("charges_tonalztics_of_ralos_quantity")
    }

    object Seqs : SeqReferences() {
        val special_charged = find("human_glaive_ralos01_charged_special")
        val special_uncharged = find("human_glaive_ralos01_uncharged_special")
    }

    object Spots : SpotanimReferences() {
        val launch_charged = find("vfx_glaive_charged_regular")
        val travel_1 = find("projanim_glaive_01_regular")
        val travel_2 = find("projanim_glaive_02_regular")
        val impact = find("vfx_glaive_01_impact_regular_01")
        val special_launch_charged = find("vfx_glaive_charged_special")
        val special_launch_uncharged = find("vfx_glaive_uncharged_special")
        val special_travel_1 = find("projanim_glaive_01_special")
        val special_travel_2 = find("projanim_glaive_02_special")
        val special_impact_1 = find("vfx_glaive_01_impact_special_01")
        val special_impact_2 = find("vfx_glaive_02_impact_special_01")
    }

    /** Whether [player] wields the charged weapon with charges left: it throws twice. */
    fun charged(player: Player): Boolean =
        player.righthand?.id == sobjs.tonalztics_of_ralos_charged.id &&
            player.vars[Vars.charges] > 0

    /**
     * A throw: a charged weapon uses one charge, and turns back into the uncharged one when that
     * was its last. Returns whether the throw was a charged one.
     */
    fun spendCharge(player: Player): Boolean {
        if (!charged(player)) {
            return false
        }
        val left = player.vars[Vars.charges] - 1
        VarPlayerIntMapSetter.set(player, Vars.charges, left)
        if (left <= 0) {
            player.worn[Wearpos.RightHand.slot] = InvObj(sobjs.tonalztics_of_ralos_uncharged, 1)
            player.rebuildAppearance()
            player.mes("<col=ef1020>Your Tonalztics of Ralos has run out of charges.</col>")
        }
        return true
    }
}
