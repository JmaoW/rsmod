package org.rsmod.content.areas.misc.isleofsouls.starter

import org.rsmod.api.config.refs.objs
import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType

object StarterLocs : LocReferences() {
    /** A rock face with a pickaxe stuck in it, and the same rocks once it has been taken. */
    val rocks_with_pickaxe = find("royal_mining_stone_pickaxe")
    val rocks = find("royal_mining_stone")

    /** The logs with a bronze axe in them, whose "Take-axe" option is Lumbridge's script. */
    val stump_with_axe = find("log_withaxe")

    /** Edgeville's furnace, the one that has the "Smelt" option. */
    val furnace = find("varrock_diary_furnace")
}

object StarterNpcs : NpcReferences() {
    /** Gerrant, of Gerrant's Fishy Business in Port Sarim. */
    val gerrant = find("gerrant")

    /** The general store's shop keeper. His talk and trade options are the Lumbridge script's. */
    val shop_keeper = find("generalshopkeeper1")

    /** How many tiles from where he stands Gerrant wanders. */
    const val WANDER_RANGE = 2
}

object StarterInvs : InvReferences() {
    /** Gerrant's shop in the cache, which has the right size (19 slots) but no stock. */
    val fishing_shop = find("fishingshop")
}

object StarterObjs : ObjReferences() {
    val small_fishing_net = find("net")
    val fishing_rod = find("fishing_rod")
    val fly_fishing_rod = find("fly_fishing_rod")
    val harpoon = find("harpoon")
    val lobster_pot = find("lobster_pot")
    val fishing_bait = find("fishing_bait")
    val bait_pack = find("pack_fishing_bait")
    val feather = find("feather")
    val feather_pack = find("pack_feather")
    val sandworms = find("piscarilius_sandworms")
    val raw_shrimps = find("raw_shrimp")
    val raw_sardine = find("raw_sardine")
    val raw_herring = find("raw_herring")
    val raw_anchovies = find("raw_anchovies")
    val raw_trout = find("raw_trout")
    val raw_pike = find("raw_pike")
    val raw_salmon = find("raw_salmon")
    val raw_tuna = find("raw_tuna")
    val raw_lobster = find("raw_lobster")
    val raw_swordfish = find("raw_swordfish")
}

/**
 * Gerrant's stock, as on the Wiki's Gerrant's Fishy Business page, plus sandworms for the
 * Piscarilius anglerfish spot. The raw fish are in the shop at 0 so that Gerrant buys them from
 * players and then sells them back. Needs `packCache`.
 */
internal object StarterInvEdits : InvEditor() {
    init {
        edit(StarterInvs.fishing_shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            stock += stock(StarterObjs.small_fishing_net, count = 5, restockCycles = 100)
            stock += stock(StarterObjs.fishing_rod, count = 5, restockCycles = 100)
            stock += stock(StarterObjs.fly_fishing_rod, count = 5, restockCycles = 100)
            stock += stock(StarterObjs.harpoon, count = 2, restockCycles = 400)
            stock += stock(StarterObjs.lobster_pot, count = 2, restockCycles = 400)
            stock += stock(StarterObjs.fishing_bait, count = 1500, restockCycles = 1)
            stock += stock(StarterObjs.bait_pack, count = 80, restockCycles = 5)
            stock += stock(StarterObjs.feather, count = 1000, restockCycles = 1)
            stock += stock(StarterObjs.feather_pack, count = 100, restockCycles = 2)
            stock += stock(StarterObjs.sandworms, count = 1000, restockCycles = 1)
            stock += stock(StarterObjs.raw_shrimps, count = 0, restockCycles = 300)
            stock += stock(StarterObjs.raw_sardine, count = 200, restockCycles = 1)
            stock += stock(StarterObjs.raw_herring, count = 0, restockCycles = 900)
            stock += stock(StarterObjs.raw_anchovies, count = 0, restockCycles = 1200)
            stock += stock(StarterObjs.raw_trout, count = 0, restockCycles = 1500)
            stock += stock(StarterObjs.raw_pike, count = 0, restockCycles = 1800)
            stock += stock(StarterObjs.raw_salmon, count = 0, restockCycles = 2100)
            stock += stock(StarterObjs.raw_tuna, count = 0, restockCycles = 2300)
            stock += stock(StarterObjs.raw_lobster, count = 0, restockCycles = 2600)
            stock += stock(StarterObjs.raw_swordfish, count = 0, restockCycles = 2900)
        }
    }
}

/**
 * Gerrant sells at the item's value and buys at 70%, his prices moving 1% a purchase, as on the
 * Wiki. Percentages are in tenths. Needs `packCache`.
 */
internal object StarterNpcEdits : NpcEditor() {
    init {
        edit(StarterNpcs.gerrant) {
            wanderRange = StarterNpcs.WANDER_RANGE
            param[ShopParams.shop_sell_percentage] = 1000
            param[ShopParams.shop_buy_percentage] = 700
            param[ShopParams.shop_change_percentage] = 10
        }
    }
}

/**
 * The hammer lying by the anvil comes back [HAMMER_RESPAWN_TICKS] ticks after it is taken. This is
 * the respawn time of every hammer the server itself places on the ground. Needs `packCache`.
 */
internal object StarterObjEdits : ObjEditor() {
    const val HAMMER_RESPAWN_TICKS = 10

    init {
        edit(objs.hammer) { respawnRate = HAMMER_RESPAWN_TICKS }
    }
}
