package org.rsmod.api.shops

import com.google.inject.ImplementedBy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player
import org.rsmod.game.shop.Shop

/**
 * Decides whether what [player] buys from [shop] is handed over as notes, for the items that can be
 * noted. By default nothing is; a world can bind its own (the Isle of Souls' Golden God relic).
 */
@ImplementedBy(NoShopNoting::class)
public interface ShopNoting {
    public fun shouldNote(player: Player, shop: Shop): Boolean
}

@Singleton
public class NoShopNoting @Inject constructor() : ShopNoting {
    override fun shouldNote(player: Player, shop: Shop): Boolean = false
}
