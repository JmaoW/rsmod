package org.rsmod.api.shops.operation

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.game.type.obj.ObjTypeList

public class StandardGpShopOperations
@Inject
constructor(objTypes: ObjTypeList, restockProcess: ShopRestockProcess, marketPrices: MarketPrices) :
    ObjCurrencyShopOperations(objTypes, restockProcess, marketPrices, objs.coins, "coin") {
    public companion object {
        public const val NOT_ENOUGH_COINS: String = "You don't have enough coins."
    }
}
