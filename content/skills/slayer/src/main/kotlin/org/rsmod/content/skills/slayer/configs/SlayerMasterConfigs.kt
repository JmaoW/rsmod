package org.rsmod.content.skills.slayer.configs

import org.rsmod.api.config.refs.params
import org.rsmod.api.shops.config.ShopParams
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.editors.obj.ObjEditor
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType
import org.rsmod.map.CoordGrid

/**
 * The post-quest-free versions of the masters, with the "Assignment", "Trade" and "Rewards" ops.
 */
object SlayerNpcs : NpcReferences() {
    val turael = find("slayer_master_1_tureal")
    val vannaka = find("slayer_master_3")
    val duradel = find("slayer_master_5_duradel")
}

object SlayerInvs : InvReferences() {
    /** The cache's Slayer Equipment shop; its stock is set below. */
    val shop = find("slayershop")
}

object SlayerObjs : ObjReferences() {
    val gem = find("slayer_gem")
    val helm = find("slayer_helm")
    val helm_imbued = find("slayer_helm_i")
    val ring = find("slayer_ring_8")
    val staff = find("slayer_staff")
    val mirror_shield = find("slayer_mirror_shield")
    val leaf_bladed_spear = find("slayer_leafbladed_spear")
    val broad_arrows = find("slayer_broad_arrows")
    val bag_of_salt = find("slayer_bag_of_salt")
    val rock_hammer = find("slayer_rock_hammer")
    val facemask = find("slayer_facemask")
    val earmuffs = find("slayer_earmuffs")
    val nose_peg = find("slayer_nosepeg")
    val spiny_helmet = find("wallbeast_spike_helmet")
    val fishing_explosive = find("fishing_explosive")
    val ice_cooler = find("slayer_icy_water")
    val slayer_gloves = find("deal_slayer_gloves")
    val bug_lantern = find("slayer_buglan_off")
    val insulated_boots = find("slayer_boots")
    val fungicide_spray = find("slayer_spray_pump_10")
    val fungicide = find("slayer_fungicide")
    val witchwood_icon = find("witchwood_icon")
    val slayer_bell = find("molanisk_bell")
    val broad_arrowheads = find("slayer_broad_arrowhead")
    val broad_arrowhead_pack = find("pack_slayer_broad_arrowhead")
    val broad_bolts_unfinished = find("slayer_broad_bolt_unfinished")
    val broad_bolts_unfinished_pack = find("pack_slayer_broad_bolt_unfinished")
    val rock_thrownhammer = find("slayer_rock_thrownhammer")
    val boots_of_stone = find("boots_of_stone")
    val reinforced_goggles = find("slayer_reinforced_goggles")
    val anti_dragon_shield = find("antidragonbreathshield")
    val slayer_staff_enchanted = find("slayer_staff_enchanted")
    val v_shield = find("v_shield")
    val black_mask = find("harmless_black_mask")
    val bug_lantern_lit = find("slayer_buglan_on")
    val tinderbox = find("tinderbox")
    val leaf_bladed_sword = find("leafbladed_sword")
    val leaf_bladed_battleaxe = find("leafbladed_battleaxe")
    val broad_arrows_2 = find("slayer_broad_arrows_2")
    val broad_arrows_3 = find("slayer_broad_arrows_3")
    val broad_arrows_4 = find("slayer_broad_arrows_4")
    val broad_arrows_5 = find("slayer_broad_arrows_5")
    val broad_bolts = find("slayer_broad_bolt")
    val broad_bolts_amethyst = find("slayer_broad_bolt_amethyst")
    val eternal_gem = find("slayer_eternal_gem")
    val imbued_heart = find("imbued_heart")
    val mist_battlestaff = find("mist_battlestaff")
    val dust_battlestaff = find("dust_battlestaff")
    val avas_accumulator = find("anma_50_reward")
}

object SlayerSpots {
    val turael = CoordGrid(2218, 2788, 0)
    val duradel = CoordGrid(2135, 2993, 2)
}

object SlayerShop {
    const val TITLE = "Slayer Equipment"

    /** A stocked item: [count] in stock, and one more every [restock] cycles once sold. */
    class Item(val obj: org.rsmod.game.type.obj.ObjType, val count: Int, val restock: Int)

    /** The Wiki's stock for the Slayer Equipment shop, which every master shares. */
    val stock: List<Item> =
        listOf(
            Item(SlayerObjs.gem, 50, 100),
            Item(SlayerObjs.mirror_shield, 100, 100),
            Item(SlayerObjs.leaf_bladed_spear, 50, 100),
            Item(SlayerObjs.broad_arrows, 50000, 5),
            Item(SlayerObjs.bag_of_salt, 5000, 5),
            Item(SlayerObjs.rock_hammer, 50, 100),
            Item(SlayerObjs.facemask, 50, 100),
            Item(SlayerObjs.earmuffs, 50, 100),
            Item(SlayerObjs.nose_peg, 50, 100),
            Item(SlayerObjs.staff, 50, 100),
            Item(SlayerObjs.spiny_helmet, 50, 100),
            Item(SlayerObjs.fishing_explosive, 5000, 50),
            Item(SlayerObjs.ice_cooler, 5000, 5),
            Item(SlayerObjs.slayer_gloves, 50, 16),
            Item(SlayerObjs.bug_lantern, 50, 100),
            Item(SlayerObjs.insulated_boots, 50, 100),
            Item(SlayerObjs.fungicide_spray, 50, 100),
            Item(SlayerObjs.fungicide, 5000, 5),
            Item(SlayerObjs.witchwood_icon, 50, 100),
            Item(SlayerObjs.slayer_bell, 50, 100),
            Item(SlayerObjs.broad_arrowheads, 3000, 1),
            Item(SlayerObjs.broad_arrowhead_pack, 800, 1),
            Item(SlayerObjs.broad_bolts_unfinished, 5000, 1),
            Item(SlayerObjs.broad_bolts_unfinished_pack, 1000, 1),
            Item(SlayerObjs.rock_thrownhammer, 5000, 5),
            Item(SlayerObjs.boots_of_stone, 50, 1),
            // The Wiki adds these after A Porcine of Interest; the isle has no quests.
            Item(SlayerObjs.reinforced_goggles, 50, 1),
            // Not on the Wiki's list: added for the isle so dragon slayers can protect themselves.
            Item(SlayerObjs.anti_dragon_shield, 50, 100),
            // Not on the Wiki's list: Ava's quest is not on the isle, so the shop sells the
            // accumulator for [AVAS_ACCUMULATOR_COST] instead.
            Item(SlayerObjs.avas_accumulator, 10, 100),
        )

    const val AVAS_ACCUMULATOR_COST = 100_000
}

/** Sets the accumulator's value, which the shop prices it from. Needs `packCache`. */
internal object SlayerShopObjEdits : ObjEditor() {
    init {
        edit(SlayerObjs.avas_accumulator) { cost = SlayerShop.AVAS_ACCUMULATOR_COST }
    }
}

/** The shop's stock. Needs `packCache`. */
internal object SlayerInvEdits : InvEditor() {
    init {
        edit(SlayerInvs.shop) {
            scope = InvScope.Shared
            stack = InvStackType.Always
            autoSize = true
            restock = true
            for (item in SlayerShop.stock) {
                stock += stock(item.obj, count = item.count, restockCycles = item.restock)
            }
        }
    }
}

/**
 * The masters stand still, sell at the Wiki's 100% and buy back at 60%, with prices that move by 1%
 * for each item sold.
 */
internal object SlayerNpcEdits : NpcEditor() {
    init {
        for (master in listOf(SlayerNpcs.turael, SlayerNpcs.vannaka, SlayerNpcs.duradel)) {
            edit(master) {
                wanderRange = 0
                param[ShopParams.shop_sell_percentage] = 1000
                param[ShopParams.shop_buy_percentage] = 600
                param[ShopParams.shop_change_percentage] = 10
            }
        }
    }
}

/**
 * Slayer helmets and black masks by their cache names. The combat code treats an item with
 * `slayer_helm` or `blackmask` as giving the on-task bonus, and the protection rules treat a helmet
 * as a nose peg, earmuffs, facemask, spiny helmet and goggles in one; nothing else sets these.
 */
object SlayerHeadgear : ObjReferences() {
    private val colours =
        listOf(
            "",
            "_black",
            "_green",
            "_red",
            "_purple",
            "_turquoise",
            "_hydra",
            "_twisted",
            "_jad",
            "_verzik",
            "_zuk",
            "_araxyte",
        )

    val helms = colours.map { find("slayer_helm$it") }

    /** The imbued helmets in the cache, including the Soul Wars and PvP Arena copies. */
    private val imbuedNames =
        setOf(
            "slayer_helm_i",
            "slayer_helm_i_black",
            "slayer_helm_i_green",
            "slayer_helm_i_red",
            "slayer_helm_i_purple",
            "slayer_helm_i_turquoise",
            "slayer_helm_i_hydra",
            "slayer_helm_i_twisted",
            "slayer_helm_i_jad",
            "slayer_helm_i_verzik",
            "slayer_helm_i_zuk",
            "slayer_helm_i_araxyte",
            "sw_slayer_helm_i",
            "sw_slayer_helm_i_black",
            "sw_slayer_helm_i_green",
            "sw_slayer_helm_i_red",
            "sw_slayer_helm_i_purple",
            "sw_slayer_helm_i_turquoise",
            "sw_slayer_helm_i_hydra",
            "sw_slayer_helm_i_twisted",
            "sw_slayer_helm_i_jad",
            "sw_slayer_helm_i_verzik",
            "sw_slayer_helm_i_zuk",
            "sw_slayer_helm_i_araxyte",
            "pvpa_slayer_helm_i",
            "pvpa_slayer_helm_i_black",
            "pvpa_slayer_helm_i_green",
            "pvpa_slayer_helm_i_red",
            "pvpa_slayer_helm_i_purple",
            "pvpa_slayer_helm_i_turquoise",
            "pvpa_slayer_helm_i_hydra",
            "pvpa_slayer_helm_i_twisted",
            "pvpa_slayer_helm_i_jad",
            "pvpa_slayer_helm_i_verzik",
            "pvpa_slayer_helm_i_zuk",
            "pvpa_slayer_helm_i_araxyte",
        )

    val imbuedHelms = imbuedNames.map { find(it) }

    val masks =
        listOf(find("harmless_black_mask")) + (1..10).map { find("harmless_black_mask_$it") }

    val imbuedMasks =
        listOf("nzone_black_mask", "sw_black_mask", "pvpa_black_mask")
            .flatMap { base -> listOf(base) + (1..10).map { "${base}_$it" } }
            .map { find(it) }
}

/** Marks the headgear above for the combat code and the protection rules. Needs `packCache`. */
internal object SlayerHeadgearEdits : ObjEditor() {
    init {
        for (obj in SlayerHeadgear.helms) {
            edit(obj) { param[params.slayer_helm] = 1 }
        }
        for (obj in SlayerHeadgear.imbuedHelms) {
            edit(obj) {
                param[params.slayer_helm] = 1
                param[params.slayer_helm_imbued] = 1
            }
        }
        for (obj in SlayerHeadgear.masks) {
            edit(obj) { param[params.blackmask] = 1 }
        }
        for (obj in SlayerHeadgear.imbuedMasks) {
            edit(obj) {
                param[params.blackmask] = 1
                param[params.blackmask_imbued] = 1
            }
        }
    }
}
