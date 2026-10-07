package org.rsmod.content.skills.crafting.configs

import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.obj.ObjType

/** A product button of a jewellery interface, and the recipe it makes. */
class JewelleryItem(val component: ComponentType, val recipe: CraftingRecipe)

/**
 * What the gold and silver jewellery interfaces make, from the wiki's Crafting page. A gold or
 * silver bar is used on a furnace, then the player picks an item; they need the mould, the gem (if
 * any) and the bar. The client greys out what the player cannot make, the server checks it again.
 *
 * Left out: the gold tiara (the client's gold interface has no tiara button), and the silver
 * interface's quest items (lightning rod, Ivandis, Silvthrill rod, Demonic sigil).
 */
object JewelleryRecipes {
    private val o = CraftingObjs
    private val g = GoldComponents
    private val v = SilverComponents

    val gold: List<JewelleryItem> = buildList {
        // A gem's rings, necklaces, unstrung amulets and bracelets: level and experience each.
        fun set(
            gem: ObjType?,
            ring: Triple<ObjType, Int, Double>,
            ringButton: ComponentType,
            necklace: Triple<ObjType, Int, Double>,
            necklaceButton: ComponentType,
            amulet: Triple<ObjType, Int, Double>,
            amuletButton: ComponentType,
            bracelet: Triple<ObjType, Int, Double>,
            braceletButton: ComponentType,
        ) {
            add(item(ringButton, o.ring_mould, gem, o.gold_bar, ring))
            add(item(necklaceButton, o.necklace_mould, gem, o.gold_bar, necklace))
            add(item(amuletButton, o.amulet_mould, gem, o.gold_bar, amulet))
            add(item(braceletButton, o.bracelet_mould, gem, o.gold_bar, bracelet))
        }
        set(
            null,
            Triple(o.gold_ring, 5, 15.0),
            g.gold_ring,
            Triple(o.gold_necklace, 6, 20.0),
            g.gold_necklace,
            Triple(o.gold_amulet_u, 8, 30.0),
            g.gold_amulet,
            Triple(o.gold_bracelet, 7, 25.0),
            g.gold_bracelet,
        )
        set(
            o.sapphire,
            Triple(o.sapphire_ring, 20, 40.0),
            g.sapphire_ring,
            Triple(o.sapphire_necklace, 22, 55.0),
            g.sapphire_necklace,
            Triple(o.sapphire_amulet_u, 24, 65.0),
            g.sapphire_amulet,
            Triple(o.sapphire_bracelet, 23, 60.0),
            g.sapphire_bracelet,
        )
        set(
            o.emerald,
            Triple(o.emerald_ring, 27, 55.0),
            g.emerald_ring,
            Triple(o.emerald_necklace, 29, 60.0),
            g.emerald_necklace,
            Triple(o.emerald_amulet_u, 31, 70.0),
            g.emerald_amulet,
            Triple(o.emerald_bracelet, 30, 65.0),
            g.emerald_bracelet,
        )
        set(
            o.ruby,
            Triple(o.ruby_ring, 34, 70.0),
            g.ruby_ring,
            Triple(o.ruby_necklace, 40, 75.0),
            g.ruby_necklace,
            Triple(o.ruby_amulet_u, 50, 85.0),
            g.ruby_amulet,
            Triple(o.ruby_bracelet, 42, 80.0),
            g.ruby_bracelet,
        )
        set(
            o.diamond,
            Triple(o.diamond_ring, 43, 85.0),
            g.diamond_ring,
            Triple(o.diamond_necklace, 56, 90.0),
            g.diamond_necklace,
            Triple(o.diamond_amulet_u, 70, 100.0),
            g.diamond_amulet,
            Triple(o.diamond_bracelet, 58, 95.0),
            g.diamond_bracelet,
        )
        set(
            o.dragonstone,
            Triple(o.dragonstone_ring, 55, 100.0),
            g.dragon_ring,
            Triple(o.dragonstone_necklace, 72, 105.0),
            g.dragon_necklace,
            Triple(o.dragonstone_amulet_u, 80, 150.0),
            g.dragon_amulet,
            Triple(o.dragonstone_bracelet, 74, 110.0),
            g.dragon_bracelet,
        )
        set(
            o.onyx,
            Triple(o.onyx_ring, 67, 115.0),
            g.onyx_ring,
            Triple(o.onyx_necklace, 82, 120.0),
            g.onyx_necklace,
            Triple(o.onyx_amulet_u, 90, 165.0),
            g.onyx_amulet,
            Triple(o.onyx_bracelet, 84, 125.0),
            g.onyx_bracelet,
        )
        set(
            o.zenyte,
            Triple(o.zenyte_ring, 89, 150.0),
            g.zenyte_ring,
            Triple(o.zenyte_necklace, 92, 165.0),
            g.zenyte_necklace,
            Triple(o.zenyte_amulet_u, 98, 200.0),
            g.zenyte_amulet,
            Triple(o.zenyte_bracelet, 95, 180.0),
            g.zenyte_bracelet,
        )

        // The Slayer ring needs an enchanted gem (the eternal gem makes the eternal ring).
        add(
            item(
                g.slayer_ring,
                o.ring_mould,
                o.slayer_gem,
                o.gold_bar,
                Triple(o.slayer_ring, 75, 15.0),
            )
        )
        add(
            item(
                g.slayer_ring,
                o.ring_mould,
                o.eternal_gem,
                o.gold_bar,
                Triple(o.eternal_slayer_ring, 75, 15.0),
            )
        )
    }

    val silver: List<JewelleryItem> = buildList {
        fun set(
            gem: ObjType,
            ring: Triple<ObjType, Int, Double>,
            ringButton: ComponentType,
            necklace: Triple<ObjType, Int, Double>,
            necklaceButton: ComponentType,
            amulet: Triple<ObjType, Int, Double>,
            amuletButton: ComponentType,
            bracelet: Triple<ObjType, Int, Double>,
            braceletButton: ComponentType,
        ) {
            add(item(ringButton, o.ring_mould, gem, o.silver_bar, ring))
            add(item(necklaceButton, o.necklace_mould, gem, o.silver_bar, necklace))
            add(item(amuletButton, o.amulet_mould, gem, o.silver_bar, amulet))
            add(item(braceletButton, o.bracelet_mould, gem, o.silver_bar, bracelet))
        }
        set(
            o.opal,
            Triple(o.opal_ring, 1, 10.0),
            v.opal_ring,
            Triple(o.opal_necklace, 16, 35.0),
            v.opal_necklace,
            Triple(o.opal_amulet_u, 27, 55.0),
            v.opal_amulet,
            Triple(o.opal_bracelet, 22, 45.0),
            v.opal_bracelet,
        )
        set(
            o.jade,
            Triple(o.jade_ring, 13, 32.0),
            v.jade_ring,
            Triple(o.jade_necklace, 25, 54.0),
            v.jade_necklace,
            Triple(o.jade_amulet_u, 34, 70.0),
            v.jade_amulet,
            Triple(o.jade_bracelet, 29, 60.0),
            v.jade_bracelet,
        )
        set(
            o.red_topaz,
            Triple(o.topaz_ring, 16, 35.0),
            v.topaz_ring,
            Triple(o.topaz_necklace, 32, 70.0),
            v.topaz_necklace,
            Triple(o.topaz_amulet_u, 45, 80.0),
            v.topaz_amulet,
            Triple(o.topaz_bracelet, 38, 75.0),
            v.topaz_bracelet,
        )

        add(
            item(
                v.holy_symbol,
                o.holy_mould,
                null,
                o.silver_bar,
                Triple(o.unstrung_symbol, 16, 50.0),
            )
        )
        add(
            item(
                v.unholy_symbol,
                o.unholy_mould,
                null,
                o.silver_bar,
                Triple(o.unstrung_emblem, 17, 50.0),
            )
        )
        add(item(v.sickle, o.sickle_mould, null, o.silver_bar, Triple(o.silver_sickle, 18, 50.0)))
        add(
            item(
                v.crossbow_bolt,
                o.bolt_mould,
                null,
                o.silver_bar,
                Triple(o.silver_bolts_unfeathered, 21, 50.0),
                output = 10,
            )
        )
        add(item(v.tiara, o.tiara_mould, null, o.silver_bar, Triple(o.tiara, 23, 52.5)))
    }

    val all: List<JewelleryItem> = gold + silver

    private fun item(
        button: ComponentType,
        mould: ObjType,
        gem: ObjType?,
        bar: ObjType,
        product: Triple<ObjType, Int, Double>,
        output: Int = 1,
    ): JewelleryItem {
        val inputs = listOfNotNull(gem?.let { CraftingInput(it) }, CraftingInput(bar))
        val recipe =
            CraftingRecipe(
                method = CraftingMethod.Mould,
                triggers = emptyList(),
                station = null,
                tools = listOf(mould),
                inputs = inputs,
                returns = emptyList(),
                product = product.first,
                output = output,
                level = product.second,
                xp = product.third,
                thread = false,
                crush = null,
                crack = false,
                anim = CraftingSeqs.furnace,
            )
        return JewelleryItem(button, recipe)
    }
}
