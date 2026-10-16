package org.rsmod.content.skills.thieving.configs

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.type.loc.LocType

/** The stall locs thieving uses, and what is left standing while a stall is empty. */
object ThievingLocs : LocReferences() {
    // Full stalls.
    val misc_veg_market = find("misc_veg_market")
    val etc_veg_market = find("etc_veg_market")
    val cakethiefstall = find("cakethiefstall")
    val dwarf_market_bakery = find("dwarf_market_bakery")
    val hos_stall_bread = find("hos_stall_bread")
    val fortis_market_stall_bakers = find("fortis_market_stall_bakers")
    val tea_stall = find("tea_stall")
    val dwarf_market_crafting = find("dwarf_market_crafting")
    val mm_stall_crafting = find("mm_stall_crafting")
    val mm_stall_food = find("mm_stall_food")
    val mm_stall_general = find("mm_stall_general")
    val silkthiefstall = find("silkthiefstall")
    val fortis_market_stall_silk = find("fortis_market_stall_silk")
    val prif_marketstall_silk = find("prif_marketstall_silk")
    val hos_fruit_stall_02 = find("hos_fruit_stall_02")
    val seed_stall = find("seed_stall")
    val furthiefstall = find("furthiefstall")
    val viking_fur_market = find("viking_fur_market")
    val fortis_market_stall_fur = find("fortis_market_stall_fur")
    val misc_fish_market = find("misc_fish_market")
    val etc_fish_market = find("etc_fish_market")
    val viking_fish_market = find("viking_fish_market")
    val fish_stall_warrens = find("fish_stall_warrens")
    val xbows_dwarf_market = find("xbows_dwarf_market")
    val silverthiefstall = find("silverthiefstall")
    val dwarf_market_silver = find("dwarf_market_silver")
    val prif_marketstall_silver = find("prif_marketstall_silver")
    val spicethiefstall = find("spicethiefstall")
    val prif_marketstall_spice = find("prif_marketstall_spice")
    val fortis_market_stall_spice = find("fortis_market_stall_spice")
    val mm_stall_magic = find("mm_stall_magic")
    val mm_stall_scimitar = find("mm_stall_scimitar")
    val gemthiefstall = find("gemthiefstall")
    val dwarf_market_gems = find("dwarf_market_gems")
    val prif_marketstall_gem = find("prif_marketstall_gem")
    val fortis_market_stall_gems = find("fortis_market_stall_gems")
    val tzhaar_shopcounter_gem = find("tzhaar_shopcounter_gem")
    val tzhaar_shopcounter_ore = find("tzhaar_shopcounter_ore")

    // What each one becomes when it has been robbed.
    val market = find("market")
    val viking_market = find("viking_market")
    val dwarf_market_empty_stall = find("dwarf_market_empty_stall")
    val hos_stall_empty = find("hos_stall_empty")
    val prif_marketstall_empty = find("prif_marketstall_empty")
    val fortis_market_stall = find("fortis_market_stall")
    val tzhaar_shopcounter_empty = find("tzhaar_shopcounter_empty")
}

/** A loc [loc] that can be robbed, and the [empty] one it shows until it has been refilled. */
class StallLoc(val loc: LocType, val empty: LocType)

/**
 * A stall that can be robbed. Stealing from one never fails, takes one item from [loot] (by weight)
 * and empties the stall for [respawn] ticks. [name] is how it is called in messages.
 *
 * Levels, experience, loot and respawn times are from the OSRS Wiki's "Stall/Thievable" table and
 * each stall's own page. The Wiki's Port Roberts stalls and the Wine stall (it has no loc in this
 * cache) are left out, and so is the Rocky pet. Which loc a stall turns into while empty is a guess
 * from the cache's names, as no source says.
 */
class Stall(
    val key: String,
    val name: String,
    val locs: List<StallLoc>,
    val level: Int,
    val xp: Double,
    val respawn: Int,
    val loot: List<Drop>,
)

object Stalls {
    private val o = ThievingObjs
    private val l = ThievingLocs

    val all: List<Stall> =
        listOf(
            Stall(
                key = "vegetable",
                name = "vegetable stall",
                locs =
                    listOf(
                        StallLoc(l.misc_veg_market, l.market),
                        StallLoc(l.etc_veg_market, l.market),
                    ),
                level = 2,
                xp = 10.0,
                respawn = 2,
                loot =
                    listOf(
                        Drop(o.potato, 1..1, 3.0),
                        Drop(o.cabbage, 1..1, 2.0),
                        Drop(o.onion, 1..1, 2.0),
                        Drop(o.tomato, 1..1, 2.0),
                        Drop(o.garlic, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "bakery",
                name = "baker's stall",
                locs =
                    listOf(
                        StallLoc(l.cakethiefstall, l.market),
                        StallLoc(l.dwarf_market_bakery, l.dwarf_market_empty_stall),
                        StallLoc(l.hos_stall_bread, l.hos_stall_empty),
                        StallLoc(l.fortis_market_stall_bakers, l.fortis_market_stall),
                    ),
                level = 5,
                xp = 16.0,
                respawn = 4,
                loot =
                    listOf(
                        Drop(o.bread, 1..1, 5.0),
                        Drop(o.cake, 1..1, 13.0),
                        Drop(o.chocolate_slice, 1..1, 2.0),
                    ),
            ),
            Stall(
                key = "tea",
                name = "tea stall",
                locs = listOf(StallLoc(l.tea_stall, l.market)),
                level = 5,
                xp = 16.0,
                respawn = 4,
                loot = listOf(Drop(o.cup_of_tea, 1..1)),
            ),
            Stall(
                key = "monkey_food",
                name = "food stall",
                locs = listOf(StallLoc(l.mm_stall_food, l.market)),
                level = 5,
                xp = 16.0,
                respawn = 6,
                loot = listOf(Drop(o.banana, 1..1)),
            ),
            Stall(
                key = "crafting",
                name = "crafting stall",
                locs =
                    listOf(
                        StallLoc(l.dwarf_market_crafting, l.dwarf_market_empty_stall),
                        StallLoc(l.mm_stall_crafting, l.market),
                    ),
                level = 5,
                xp = 20.0,
                respawn = 8,
                loot =
                    listOf(
                        Drop(o.amulet_mould, 1..1, 12.0),
                        Drop(o.jewl_bracelet_mould, 1..1, 12.0),
                        Drop(o.chisel, 1..1, 6.0),
                        Drop(o.gold_bar, 1..1, 2.0),
                        Drop(o.necklace_mould, 1..1, 11.0),
                        Drop(o.ring_mould, 1..1, 11.0),
                    ),
            ),
            Stall(
                key = "monkey_general",
                name = "general stall",
                locs = listOf(StallLoc(l.mm_stall_general, l.market)),
                level = 5,
                xp = 25.0,
                respawn = 8,
                loot =
                    listOf(Drop(o.hammer, 1..1), Drop(o.pot_empty, 1..1), Drop(o.tinderbox, 1..1)),
            ),
            Stall(
                key = "silk",
                name = "silk stall",
                locs =
                    listOf(
                        StallLoc(l.silkthiefstall, l.market),
                        StallLoc(l.fortis_market_stall_silk, l.fortis_market_stall),
                        StallLoc(l.prif_marketstall_silk, l.prif_marketstall_empty),
                    ),
                level = 20,
                xp = 24.0,
                respawn = 8,
                loot = listOf(Drop(o.silk, 1..1)),
            ),
            Stall(
                key = "fruit",
                name = "fruit stall",
                locs = listOf(StallLoc(l.hos_fruit_stall_02, l.hos_stall_empty)),
                level = 25,
                xp = 28.5,
                respawn = 4,
                loot =
                    listOf(
                        Drop(o.cooking_apple, 1..1, 40.0),
                        Drop(o.banana, 1..1, 20.0),
                        Drop(o.jangerberries, 1..1, 5.0),
                        Drop(o.lemon, 1..1, 5.0),
                        Drop(o.redberries, 1..1, 5.0),
                        Drop(o.pineapple, 1..1, 5.0),
                        Drop(o.lime, 1..1, 5.0),
                        Drop(o.strawberry, 1..1, 7.0),
                        Drop(o.macro_triffidfruit, 1..1, 5.0),
                        Drop(o.golovanova_top, 1..1, 2.0),
                        Drop(o.papaya, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "seed",
                name = "seed stall",
                locs = listOf(StallLoc(l.seed_stall, l.market)),
                level = 27,
                xp = 10.0,
                respawn = 4,
                loot =
                    listOf(
                        Drop(o.hammerstone_hop_seed, 1..1, 120.0),
                        Drop(o.potato_seed, 1..1, 119.0),
                        Drop(o.marigold_seed, 1..1, 119.0),
                        Drop(o.barley_seed, 1..1, 118.0),
                        Drop(o.onion_seed, 1..1, 89.0),
                        Drop(o.asgarnian_hop_seed, 1..1, 83.0),
                        Drop(o.cabbage_seed, 1..1, 71.0),
                        Drop(o.yanillian_hop_seed, 1..1, 47.0),
                        Drop(o.rosemary_seed, 1..1, 36.0),
                        Drop(o.nasturtium_seed, 1..1, 35.0),
                        Drop(o.tomato_seed, 1..1, 35.0),
                        Drop(o.jute_seed, 1..1, 35.0),
                        Drop(o.sweetcorn_seed, 1..1, 30.0),
                        Drop(o.krandorian_hop_seed, 1..1, 24.0),
                        Drop(o.strawberry_seed, 1..1, 18.0),
                        Drop(o.wildblood_hop_seed, 1..1, 12.0),
                        Drop(o.watermelon_seed, 1..1, 9.0),
                    ),
            ),
            Stall(
                key = "fur",
                name = "fur stall",
                locs =
                    listOf(
                        StallLoc(l.furthiefstall, l.market),
                        StallLoc(l.viking_fur_market, l.viking_market),
                        StallLoc(l.fortis_market_stall_fur, l.fortis_market_stall),
                    ),
                level = 35,
                xp = 45.0,
                respawn = 12,
                loot = listOf(Drop(o.grey_wolf_fur, 1..1)),
            ),
            Stall(
                key = "fish",
                name = "fish stall",
                locs =
                    listOf(
                        StallLoc(l.misc_fish_market, l.market),
                        StallLoc(l.etc_fish_market, l.market),
                        StallLoc(l.viking_fish_market, l.viking_market),
                        StallLoc(l.fish_stall_warrens, l.market),
                    ),
                level = 42,
                xp = 42.0,
                respawn = 12,
                loot =
                    listOf(
                        Drop(o.raw_salmon, 1..1, 14.0),
                        Drop(o.raw_tuna, 1..1, 5.0),
                        Drop(o.raw_lobster, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "crossbow",
                name = "crossbow stall",
                locs = listOf(StallLoc(l.xbows_dwarf_market, l.dwarf_market_empty_stall)),
                level = 49,
                xp = 52.0,
                respawn = 8,
                loot =
                    listOf(
                        Drop(o.bolt, 3..3, 46.0),
                        Drop(o.xbows_crossbow_limbs_bronze, 3..3, 18.0),
                        Drop(o.xbows_crossbow_bolts_mithril, 3..3, 3.0),
                        Drop(o.xbows_crossbow_limbs_mithril, 3..3, 1.0),
                        Drop(o.xbows_crossbow_stock_wood, 3..3, 12.0),
                    ),
            ),
            Stall(
                key = "silver",
                name = "silver stall",
                locs =
                    listOf(
                        StallLoc(l.silverthiefstall, l.market),
                        StallLoc(l.dwarf_market_silver, l.dwarf_market_empty_stall),
                        StallLoc(l.prif_marketstall_silver, l.prif_marketstall_empty),
                    ),
                level = 50,
                xp = 205.0,
                respawn = 32,
                loot =
                    listOf(
                        Drop(o.silver_ore, 1..1, 16.0),
                        Drop(o.silver_bar, 1..1, 3.0),
                        Drop(o.tiara, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "spice",
                name = "spice stall",
                locs =
                    listOf(
                        StallLoc(l.spicethiefstall, l.market),
                        StallLoc(l.prif_marketstall_spice, l.prif_marketstall_empty),
                        StallLoc(l.fortis_market_stall_spice, l.fortis_market_stall),
                    ),
                level = 65,
                xp = 92.0,
                respawn = 10,
                loot = listOf(Drop(o.spicespot, 1..1)),
            ),
            Stall(
                key = "magic",
                name = "magic stall",
                locs = listOf(StallLoc(l.mm_stall_magic, l.market)),
                level = 65,
                xp = 90.0,
                respawn = 12,
                loot =
                    listOf(
                        Drop(o.airrune, 1..1, 6.0),
                        Drop(o.earthrune, 1..1, 6.0),
                        Drop(o.firerune, 1..1, 6.0),
                        Drop(o.lawrune, 1..1, 1.0),
                        Drop(o.naturerune, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "scimitar",
                name = "scimitar stall",
                locs = listOf(StallLoc(l.mm_stall_scimitar, l.market)),
                level = 65,
                xp = 210.0,
                respawn = 32,
                loot =
                    listOf(
                        Drop(o.iron_scimitar, 1..1, 23.0),
                        Drop(o.steel_scimitar, 1..1, 13.0),
                        Drop(o.mithril_scimitar, 1..1, 3.0),
                        Drop(o.adamant_scimitar, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "gem",
                name = "gem stall",
                locs =
                    listOf(
                        StallLoc(l.gemthiefstall, l.market),
                        StallLoc(l.dwarf_market_gems, l.dwarf_market_empty_stall),
                        StallLoc(l.prif_marketstall_gem, l.prif_marketstall_empty),
                        StallLoc(l.fortis_market_stall_gems, l.fortis_market_stall),
                    ),
                level = 75,
                xp = 408.0,
                respawn = 100,
                loot =
                    listOf(
                        Drop(o.uncut_sapphire, 1..1, 105.0),
                        Drop(o.uncut_emerald, 1..1, 17.0),
                        Drop(o.uncut_ruby, 1..1, 5.0),
                        Drop(o.uncut_diamond, 1..1, 1.0),
                    ),
            ),
            Stall(
                key = "mor_ul_rek_gem",
                name = "gem stall",
                locs = listOf(StallLoc(l.tzhaar_shopcounter_gem, l.tzhaar_shopcounter_empty)),
                level = 75,
                xp = 408.0,
                respawn = 175,
                loot =
                    listOf(
                        Drop(o.uncut_sapphire, 1..1, 1000.0),
                        Drop(o.sapphire, 1..1, 500.0),
                        Drop(o.uncut_emerald, 1..1, 600.0),
                        Drop(o.emerald, 1..1, 300.0),
                        Drop(o.uncut_ruby, 1..1, 300.0),
                        Drop(o.ruby, 1..1, 150.0),
                        Drop(o.uncut_diamond, 1..1, 98.0),
                        Drop(o.diamond, 1..1, 49.0),
                        Drop(o.uncut_dragonstone, 1..1, 2.0),
                        Drop(o.dragonstone, 1..1, 1.0),
                        Drop(o.uncut_onyx, 1..1, 0.04),
                        Drop(o.onyx, 1..1, 0.02),
                    ),
            ),
            Stall(
                key = "mor_ul_rek_ore",
                name = "ore stall",
                locs = listOf(StallLoc(l.tzhaar_shopcounter_ore, l.tzhaar_shopcounter_empty)),
                level = 82,
                xp = 350.0,
                respawn = 50,
                loot =
                    listOf(
                        Drop(o.iron_ore, 1..1, 25.0),
                        Drop(o.coal, 1..1, 15.0),
                        Drop(o.silver_ore, 1..1, 10.0),
                        Drop(o.gold_ore, 1..1, 10.0),
                        Drop(o.mithril_ore, 1..1, 10.0),
                        Drop(o.adamantite_ore, 1..1, 8.0),
                        Drop(o.runite_ore, 1..1, 2.0),
                    ),
            ),
        )

    /** The stall that [loc] is, if it can be robbed. */
    fun forLoc(loc: LocType): Stall? =
        all.firstOrNull { stall -> stall.locs.any { it.loc.id == loc.id } }

    fun byKey(key: String): Stall =
        all.firstOrNull { it.key == key } ?: error("No stall called $key.")
}
