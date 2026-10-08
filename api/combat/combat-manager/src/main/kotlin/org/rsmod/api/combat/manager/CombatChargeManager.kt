package org.rsmod.api.combat.manager

import jakarta.inject.Inject
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.player.righthand
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.varobjbit.UnpackedVarObjBitType

public class CombatChargeManager
@Inject
constructor(private val manager: ObjChargeManager, private val masteries: CombatMasteryEffects) {
    /**
     * Returns the number of charges on the player's weapon.
     *
     * Internally calls [ObjChargeManager.getCharges] with `obj = player.righthand`.
     */
    public fun getWeaponCharges(player: Player, varobj: UnpackedVarObjBitType): Int =
        manager.getCharges(player.righthand, varobj)

    /**
     * Attempts to reduce the charges on the player's weapon by [decrement].
     *
     * Internally calls [ObjChargeManager.reduceWornCharges] with `wearpos = Wearpos.RightHand`.
     *
     * The first combat mastery passive saves the charges most of the time. A saved use reduces
     * nothing and still counts as a success, but only if the weapon has the charges to spend.
     */
    public fun attemptDetractWeapon(
        player: Player,
        varobj: UnpackedVarObjBitType,
        decrement: Int = 1,
    ): ObjChargeManager.Uncharge =
        manager.reduceWornCharges(
            player,
            Wearpos.RightHand,
            varobj,
            spent(player, varobj, decrement),
        )

    private fun spent(player: Player, varobj: UnpackedVarObjBitType, decrement: Int): Int {
        val canSpend = decrement > 0 && getWeaponCharges(player, varobj) >= decrement
        return if (canSpend && masteries.savesResource(player)) 0 else decrement
    }
}
