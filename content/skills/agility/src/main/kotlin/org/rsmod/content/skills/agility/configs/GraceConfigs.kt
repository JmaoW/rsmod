package org.rsmod.content.skills.agility.configs

import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.currency.CurrencyReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.map.CoordGrid

object GraceCurrencies : CurrencyReferences() {
    /** Needs a line in `.data/symbols/currency.sym`. */
    val marks = find("mark_of_grace")
}

object GraceNpcs : NpcReferences() {
    val grace = find("rooftops_grace")
}

object GraceInvs : InvReferences() {
    /** The cache's Rogues' Den shop, where Grace lives, which has no stock of its own. */
    val shop = find("roguesden_shop")
}

object GraceObjs : ObjReferences() {
    val amylase = find("amylase")
    val amylase_pack = find("pack_amylase")
}

object GraceSpot {
    /** On open ground between the Gnome Stronghold and Draynor courses. */
    val tile = CoordGrid(2213, 2811, 0)

    /** How many tiles from there that Grace wanders. */
    const val WANDER_RANGE = 2

    /** What the graceful pieces cost in marks of grace (the Wiki's full set is 260). */
    val prices =
        listOf(
            AgilityObjs.graceful_hood to 35,
            AgilityObjs.graceful_top to 55,
            AgilityObjs.graceful_legs to 60,
            AgilityObjs.graceful_gloves to 30,
            AgilityObjs.graceful_boots to 40,
            AgilityObjs.graceful_cape to 40,
        )
}

/**
 * Grace's Graceful Clothing: the six graceful pieces for marks of grace, and amylase packs. The
 * shop prices by an item's value, so each piece's value is set to its price in marks. Needs
 * `packCache`.
 */
internal object GraceInvEdits : InvEditor() {
    init {
        edit(GraceInvs.shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            for ((obj, _) in GraceSpot.prices) {
                stock += stock(obj, count = 100, restockCycles = 1)
            }
            stock += stock(GraceObjs.amylase_pack, count = 1000, restockCycles = 1)
        }
    }
}

internal object GraceObjEdits : ObjEditor() {
    init {
        for ((obj, price) in GraceSpot.prices) {
            edit(obj) { cost = price }
        }
        edit(GraceObjs.amylase_pack) { cost = 10 }
    }
}

/** Grace sells at the price in marks and buys back at 80%, with prices that never move. */
internal object GraceNpcEdits : NpcEditor() {
    init {
        edit(GraceNpcs.grace) {
            wanderRange = GraceSpot.WANDER_RANGE
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 800
            param[ShopParams.shop_change_percentage] = 0
        }
    }
}
