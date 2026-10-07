package org.rsmod.content.areas.misc.isleofsouls.miningguild

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.shops.operation.ObjCurrencyShopOperations
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.currency.CurrencyReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.skills.mining.configs.ProspectorObjs
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.game.type.obj.ObjTypeList

object MiningGuildObjs : ObjReferences() {
    /** What mining in the guild gives, and Percy's shop takes as payment. */
    val golden_nugget = find("motherlode_nugget")
}

object MiningGuildCurrencies : CurrencyReferences() {
    val golden_nuggets = find("golden_nuggets")
}

object MiningGuildNpcs : NpcReferences() {
    val percy = find("motherlode_percy")
    val yarsul = find("mguild_pickaxeseller")

    /** How many tiles from where they stand that Percy and Yarsul wander. */
    const val WANDER_RANGE = 3
}

object MiningGuildInvs : InvReferences() {
    /** Yarsul's pickaxe shop, which the cache has but gives no stock. */
    val pickaxe_shop = find("mguild_pickaxeshop")

    /** The guild's reward shop in the cache, which has no stock, is Percy's nugget shop here. */
    val nugget_shop = find("mguild_rewardshop")
}

/** Percy's shop, with golden nuggets in place of coins. */
class NuggetShopOperations
@Inject
constructor(objTypes: ObjTypeList, restockProcess: ShopRestockProcess, marketPrices: MarketPrices) :
    ObjCurrencyShopOperations(
        objTypes,
        restockProcess,
        marketPrices,
        MiningGuildObjs.golden_nugget,
        "golden nugget",
    )

/**
 * The stock of Yarsul's pickaxe shop and Prospector Percy's nugget shop, as on the wiki. Percy
 * sells the four prospector pieces; his coal bag, gem bag, soft clay pack and bag full of gems are
 * left out because nothing in the game uses them. Needs `packCache`.
 */
internal object MiningGuildInvEdits : InvEditor() {
    init {
        edit(MiningGuildInvs.pickaxe_shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(objs.bronze_pickaxe, count = 6, restockCycles = 50)
            stock += stock(objs.iron_pickaxe, count = 5, restockCycles = 100)
            stock += stock(objs.steel_pickaxe, count = 4, restockCycles = 200)
            stock += stock(objs.mithril_pickaxe, count = 3, restockCycles = 300)
            stock += stock(objs.adamant_pickaxe, count = 2, restockCycles = 500)
            stock += stock(objs.rune_pickaxe, count = 1, restockCycles = 700)
        }

        edit(MiningGuildInvs.nugget_shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(ProspectorObjs.helmet, count = 100, restockCycles = 1)
            stock += stock(ProspectorObjs.jacket, count = 100, restockCycles = 1)
            stock += stock(ProspectorObjs.legs, count = 100, restockCycles = 1)
            stock += stock(ProspectorObjs.boots, count = 100, restockCycles = 1)
        }
    }
}

/**
 * Yarsul sells at the item's value and buys at 60%, his prices moving 2% a purchase. Percy sells at
 * the item's value and buys back at 80%, with prices that never move. Percentages are in tenths.
 * Both wander up to [MiningGuildNpcs.WANDER_RANGE] tiles. Needs `packCache`.
 */
internal object MiningGuildNpcEdits : NpcEditor() {
    init {
        edit(MiningGuildNpcs.yarsul) {
            wanderRange = MiningGuildNpcs.WANDER_RANGE
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 600
            param[ShopParams.shop_change_percentage] = 20
        }

        edit(MiningGuildNpcs.percy) {
            wanderRange = MiningGuildNpcs.WANDER_RANGE
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 800
            param[ShopParams.shop_change_percentage] = 0
        }
    }
}
