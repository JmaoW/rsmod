package org.rsmod.content.skills.crafting.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.skills.crafting.configs.CraftingInterfaces
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.configs.TanSlot
import org.rsmod.content.skills.crafting.configs.TannerComponents
import org.rsmod.content.skills.crafting.configs.Tanning
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - cured yak-hide (Thakkrad Sigmundson in Neitiznot cures yak-hide for 5 coins), the higher prices
//   of the Canifis and Auburnvale tanners, and the Tan Leather spell (Magic)
//
// A tanner is a "Tanner" or "Ellis" npc with a "Trade" op. Trading opens the old-style tanner
// interface (a fixed layout of eight hides, see [Tanning]), where the server fills in each hide's
// picture, name and price. Each hide has four buttons: tan 1, 5, X or all of them. Tanning needs
// no level and gives no experience, and every hide costs coins from the player.
class Tanner
@Inject
constructor(private val npcTypes: NpcTypeList, private val objTypes: ObjTypeList) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in npcTypes.values) {
            if (isTanner(type)) {
                onOpNpc3(type) { open() }
            }
        }
        for (index in Tanning.slots.indices) {
            onIfModalButton(TannerComponents.tan_1[index]) { tan(index, 1) }
            onIfModalButton(TannerComponents.tan_5[index]) { tan(index, 5) }
            onIfModalButton(TannerComponents.tan_x[index]) { tan(index, countDialog()) }
            onIfModalButton(TannerComponents.tan_all[index]) { tan(index, ALL) }
        }
    }

    /** Opens the interface and fills in what each of its eight slots shows. */
    private fun ProtectedAccess.open() {
        ifOpenMainModal(CraftingInterfaces.tanner)
        for ((index, slot) in Tanning.slots.withIndex()) {
            ifSetObj(TannerComponents.models[index], slot.hide, MODEL_ZOOM)
            ifSetText(TannerComponents.names[index], slot.name)
            ifSetText(TannerComponents.costs[index], slot.costText)
        }
    }

    /**
     * Tans up to [wanted] of the hide in [slotIndex], as many as the player has and can pay for.
     */
    private fun ProtectedAccess.tan(slotIndex: Int, wanted: Int) {
        val slot = Tanning.slots[slotIndex]
        val hides = invTotal(inv, slot.hide)
        if (hides == 0) {
            mes("You do not have any ${nameOf(slot.hide)} to tan.")
            return
        }
        val affordable = invTotal(inv, CraftingObjs.coins) / slot.cost
        if (affordable == 0) {
            mes("You do not have enough coins to tan that.")
            return
        }
        val amount = minOf(wanted.coerceAtLeast(1), hides, affordable)
        if (!exchange(slot, amount)) {
            mes("You do not have enough inventory space to tan that.")
            return
        }
        val plural = if (amount == 1) "hide" else "hides"
        mes("The tanner tans $amount $plural for you.")
        publish(HidesTanned(player, slot.leather, amount))
    }

    /** Trades [amount] of [slot]'s hide and the coins for the leather. */
    private fun ProtectedAccess.exchange(slot: TanSlot, amount: Int): Boolean {
        if (!invDel(inv, slot.hide, amount).success) {
            return false
        }
        if (!invDel(inv, CraftingObjs.coins, slot.cost * amount).success) {
            invAdd(inv, slot.hide, amount)
            return false
        }
        if (!invAdd(inv, slot.leather, amount).success) {
            invAdd(inv, slot.hide, amount)
            invAdd(inv, CraftingObjs.coins, slot.cost * amount)
            return false
        }
        return true
    }

    private fun isTanner(type: UnpackedNpcType): Boolean =
        type.name in TANNER_NAMES && type.op.getOrNull(TRADE_OP_INDEX) == TRADE_OP

    private fun nameOf(obj: ObjType): String = objTypes[obj].name.lowercase()

    /** Published once hides have been tanned, with the [leather] they made. */
    class HidesTanned(val player: Player, val leather: ObjType, val quantity: Int) : UnboundEvent

    private companion object {
        /** The tanners that charge the standard prices. */
        val TANNER_NAMES = setOf("Ellis", "Tanner")

        const val TRADE_OP = "Trade"
        const val TRADE_OP_INDEX = 2

        /** Stands for "all of them" until it is cut down to what the player has. */
        const val ALL = Int.MAX_VALUE

        /**
         * Zoom for the hide pictures. The client divides the item's own zoom by it, as a percent.
         */
        const val MODEL_ZOOM = 100
    }
}
