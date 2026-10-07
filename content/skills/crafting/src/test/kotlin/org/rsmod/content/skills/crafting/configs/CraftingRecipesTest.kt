package org.rsmod.content.skills.crafting.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.game.type.obj.ObjType

class CraftingRecipesTest {
    private val o = CraftingObjs

    private fun recipe(product: ObjType): CraftingRecipe =
        CraftingRecipes.all.first { it.product.internalNameValue == product.internalNameValue }

    private fun check(product: ObjType, level: Int, xp: Double, count: Int = 1) {
        val recipe = recipe(product)
        assertEquals(level, recipe.level, "level of ${product.internalNameValue}")
        assertEquals(xp, recipe.xp, "xp of ${product.internalNameValue}")
        assertEquals(count, recipe.inputs[0].count, "count of ${product.internalNameValue}")
    }

    @Test
    fun `leather armour matches the wiki`() {
        check(o.leather_gloves, 1, 13.8)
        check(o.leather_boots, 7, 16.3)
        check(o.leather_cowl, 9, 18.5)
        check(o.leather_vambraces, 11, 22.0)
        check(o.leather_body, 14, 25.0)
        check(o.leather_chaps, 18, 27.0)
        check(o.hardleather_body, 28, 35.0)
        check(o.coif, 38, 37.0)
        check(o.studded_body, 41, 40.0)
        check(o.studded_chaps, 44, 42.0)
        check(o.spiky_vambraces, 32, 6.0)
        check(o.hard_leather_shield, 41, 70.0, count = 2)
    }

    @Test
    fun `dragonhide armour uses one, two and three leathers`() {
        val expected =
            listOf(
                listOf(o.green_dhide_vambraces, 57, 62.0, 1),
                listOf(o.green_dhide_chaps, 60, 124.0, 2),
                listOf(o.green_dhide_body, 63, 186.0, 3),
                listOf(o.blue_dhide_vambraces, 66, 70.0, 1),
                listOf(o.blue_dhide_chaps, 68, 140.0, 2),
                listOf(o.blue_dhide_body, 71, 210.0, 3),
                listOf(o.red_dhide_vambraces, 73, 78.0, 1),
                listOf(o.red_dhide_chaps, 75, 156.0, 2),
                listOf(o.red_dhide_body, 77, 234.0, 3),
                listOf(o.black_dhide_vambraces, 79, 86.0, 1),
                listOf(o.black_dhide_chaps, 82, 172.0, 2),
                listOf(o.black_dhide_body, 84, 258.0, 3),
            )
        for (row in expected) {
            check(row[0] as ObjType, row[1] as Int, row[2] as Double, row[3] as Int)
        }
    }

    @Test
    fun `dragonhide shields need nails and a hammer`() {
        check(o.green_dhide_shield, 62, 124.0, count = 2)
        check(o.blue_dhide_shield, 69, 140.0, count = 2)
        check(o.red_dhide_shield, 76, 156.0, count = 2)
        check(o.black_dhide_shield, 83, 172.0, count = 2)
        for (shield in listOf(o.green_dhide_shield, o.black_dhide_shield)) {
            val recipe = recipe(shield)
            assertEquals(15, recipe.inputs.last().count)
            assertTrue(recipe.tools.any { it.internalNameValue == o.hammer.internalNameValue })
            assertFalse(recipe.thread)
        }
    }

    @Test
    fun `snakeskin and other sets match the wiki`() {
        check(o.snakeskin_boots, 45, 30.0, count = 6)
        check(o.snakeskin_vambraces, 47, 35.0, count = 8)
        check(o.snakeskin_bandana, 48, 45.0, count = 5)
        check(o.snakeskin_chaps, 51, 50.0, count = 12)
        check(o.snakeskin_body, 53, 55.0, count = 15)
        check(o.snakeskin_shield, 56, 100.0, count = 2)
        check(o.broodoo_shield_poison, 35, 100.0, count = 2)
        check(o.yak_hide_legs, 43, 32.0)
        check(o.yak_hide_top, 46, 32.0, count = 2)
        check(o.xerician_hat, 14, 66.0, count = 3)
        check(o.xerician_robe, 17, 88.0, count = 4)
        check(o.xerician_top, 22, 110.0, count = 5)
        check(o.splitbark_helm, 61, 124.0, count = 2)
        check(o.splitbark_body, 62, 248.0, count = 4)
        check(o.mixed_hide_cape, 68, 62.0)
        check(o.mixed_hide_top, 72, 150.0)
        check(o.hueycoatl_vambraces, 76, 95.0)
        check(o.hueycoatl_coif, 76, 190.0, count = 2)
        check(o.hueycoatl_body, 78, 285.0, count = 3)
    }

    @Test
    fun `all nine snelms cost level 15 and 32 and a half xp`() {
        val snelms =
            listOf(
                o.red_snelm_pointed,
                o.red_snelm_round,
                o.bark_snelm,
                o.blue_snelm_pointed,
                o.blue_snelm_round,
                o.myre_snelm_pointed,
                o.myre_snelm_round,
                o.ochre_snelm_pointed,
                o.ochre_snelm_round,
            )
        for (snelm in snelms) {
            check(snelm, 15, 32.5)
        }
        check(o.crab_helmet, 15, 32.5)
        check(o.crab_claw, 15, 32.5)
    }

    @Test
    fun `gems match the wiki, and only the semiprecious ones crush`() {
        val expected =
            listOf(
                Triple(o.opal, 1, 15.0),
                Triple(o.jade, 13, 20.0),
                Triple(o.red_topaz, 16, 25.0),
                Triple(o.sapphire, 20, 50.0),
                Triple(o.emerald, 27, 67.5),
                Triple(o.ruby, 34, 85.0),
                Triple(o.diamond, 43, 107.5),
                Triple(o.dragonstone, 55, 137.5),
                Triple(o.onyx, 67, 167.5),
                Triple(o.zenyte, 89, 50.0),
            )
        for ((gem, level, xp) in expected) {
            check(gem, level, xp)
        }
        assertEquals(3.8, recipe(o.opal).crush!!.crushXp)
        assertEquals(5.0, recipe(o.jade).crush!!.crushXp)
        assertEquals(6.3, recipe(o.red_topaz).crush!!.crushXp)
        for (gem in listOf(o.sapphire, o.emerald, o.ruby, o.diamond, o.dragonstone)) {
            assertNull(recipe(gem).crush)
        }
    }

    @Test
    fun `gem crush chances follow the wiki formulas`() {
        // Opal: (level - 1) * 122 / 98 + 129 out of 256, so 129 at level 1.
        val opal = recipe(o.opal).crush!!
        assertEquals(129, opal.successOutOf256(1))
        assertEquals(129 + 49 * 122 / 98, opal.successOutOf256(50))
        // Jade starts at 101 and red topaz at 91.
        assertEquals(101, recipe(o.jade).crush!!.successOutOf256(1))
        assertEquals(91, recipe(o.red_topaz).crush!!.successOutOf256(1))
        // Without a Jeweller's chisel a red topaz is never certain: 91 + 150 out of 256 at 99.
        assertEquals(241, recipe(o.red_topaz).crush!!.successOutOf256(99))
    }

    @Test
    fun `battlestaves and amethyst match the wiki`() {
        check(o.water_battlestaff, 54, 100.0)
        check(o.earth_battlestaff, 58, 112.5)
        check(o.fire_battlestaff, 62, 125.0)
        check(o.air_battlestaff, 66, 137.5)
        assertEquals(15, recipe(o.amethyst_bolt_tips).output)
        assertEquals(15, recipe(o.amethyst_arrowtips).output)
        assertEquals(5, recipe(o.amethyst_javelin_tips).output)
        assertEquals(8, recipe(o.amethyst_dart_tips).output)
        check(o.amethyst_bolt_tips, 83, 60.0)
        check(o.amethyst_arrowtips, 85, 60.0)
        check(o.amethyst_javelin_tips, 87, 60.0)
        check(o.amethyst_dart_tips, 89, 60.0)
    }

    @Test
    fun `glassblowing, pottery and birdhouses match the wiki`() {
        check(o.beer_glass, 1, 17.5)
        check(o.candle_lantern, 4, 19.0)
        check(o.oil_lamp, 12, 25.0)
        check(o.oil_lantern, 26, 50.0)
        check(o.vial, 33, 35.0)
        check(o.fishbowl, 42, 42.5)
        check(o.unpowered_orb, 46, 52.5)
        check(o.lantern_lens, 49, 55.0)
        check(o.empty_light_orb, 87, 70.0)
        check(o.light_orb, 87, 104.0)
        check(o.molten_glass, 1, 20.0)
        check(o.unfired_pot, 1, 6.3)
        check(o.pot, 1, 6.3)
        check(o.pie_dish, 7, 10.0)
        check(o.bowl, 8, 15.0)
        check(o.plant_pot, 19, 17.5)
        check(o.pot_lid, 25, 20.0)
        check(o.bird_house, 5, 15.0)
        check(o.redwood_bird_house, 90, 55.0)
        check(o.ball_of_wool, 1, 2.5)
        check(o.bow_string, 10, 15.0)
        check(o.magic_string, 19, 30.0)
        check(o.strip_of_cloth, 10, 12.0, count = 4)
        check(o.basket, 36, 56.0, count = 6)
    }

    @Test
    fun `one soft clay makes four cups, and clay cracks only below level 13`() {
        assertEquals(4, recipe(o.unfired_cup).output)
        val pot = recipe(o.pot)
        assertTrue(pot.crack)
        assertEquals(181, pot.fireSuccessOutOf256(1))
        assertEquals(256, pot.fireSuccessOutOf256(13))
        assertEquals(256, pot.fireSuccessOutOf256(99))
        assertEquals(256, recipe(o.leather_body).fireSuccessOutOf256(1))
    }

    @Test
    fun `gold jewellery matches the wiki`() {
        val gold = JewelleryRecipes.gold.map { it.recipe }
        fun level(product: ObjType) =
            gold.first { it.product.internalNameValue == product.internalNameValue }

        assertEquals(5, level(o.gold_ring).level)
        assertEquals(15.0, level(o.gold_ring).xp)
        assertEquals(40.0, level(o.sapphire_ring).xp)
        assertEquals(60.0, level(o.sapphire_bracelet).xp)
        assertEquals(34, level(o.ruby_ring).level)
        assertEquals(50, level(o.ruby_amulet_u).level)
        assertEquals(85.0, level(o.ruby_amulet_u).xp)
        assertEquals(80, level(o.dragonstone_amulet_u).level)
        assertEquals(150.0, level(o.dragonstone_amulet_u).xp)
        assertEquals(90, level(o.onyx_amulet_u).level)
        assertEquals(165.0, level(o.onyx_amulet_u).xp)
        assertEquals(98, level(o.zenyte_amulet_u).level)
        assertEquals(200.0, level(o.zenyte_amulet_u).xp)
        assertEquals(75, level(o.slayer_ring).level)
        assertEquals(15.0, level(o.slayer_ring).xp)
        // Four jewellery types for gold and every gem, plus the two Slayer rings.
        assertEquals(8 * 4 + 2, gold.size)
    }

    @Test
    fun `silver jewellery and symbols match the wiki`() {
        val silver = JewelleryRecipes.silver.map { it.recipe }
        fun recipe(product: ObjType) =
            silver.first { it.product.internalNameValue == product.internalNameValue }

        assertEquals(1, recipe(o.opal_ring).level)
        assertEquals(10.0, recipe(o.opal_ring).xp)
        assertEquals(45, recipe(o.topaz_amulet_u).level)
        assertEquals(80.0, recipe(o.topaz_amulet_u).xp)
        assertEquals(16, recipe(o.unstrung_symbol).level)
        assertEquals(17, recipe(o.unstrung_emblem).level)
        assertEquals(18, recipe(o.silver_sickle).level)
        assertEquals(21, recipe(o.silver_bolts_unfeathered).level)
        assertEquals(10, recipe(o.silver_bolts_unfeathered).output)
        assertEquals(23, recipe(o.tiara).level)
        assertEquals(52.5, recipe(o.tiara).xp)
    }

    @Test
    fun `stringing any amulet gives four xp`() {
        for (strung in
            listOf(o.gold_amulet, o.ruby_amulet, o.onyx_amulet, o.zenyte_amulet, o.opal_amulet)) {
            assertEquals(CraftingRecipes.STRING_XP, recipe(strung).xp)
        }
    }

    @Test
    fun `tanning prices match the wiki`() {
        val prices = Tanning.slots.map { it.leather.internalNameValue to it.cost }
        assertEquals("leather" to 1, prices[0])
        assertEquals("hard_leather" to 3, prices[1])
        assertEquals(15, Tanning.slots[2].cost)
        assertEquals(20, Tanning.slots[3].cost)
        assertTrue(Tanning.slots.drop(4).all { it.cost == 20 })
        assertEquals("1 coin", Tanning.slots[0].costText)
        assertEquals("20 coins", Tanning.slots[4].costText)
        assertEquals(8, Tanning.slots.size)
    }

    @Test
    fun `needle and thread on leather offer the seven leather items`() {
        val needleOnLeather = CraftingRecipes.offered(o.needle, o.leather).map { it.product }
        assertEquals(7, needleOnLeather.size)
        val threadOnLeather = CraftingRecipes.offered(o.thread, o.leather).map { it.product }
        assertEquals(needleOnLeather.size, threadOnLeather.size)
        // Either order of the click works.
        assertEquals(needleOnLeather.size, CraftingRecipes.offered(o.leather, o.needle).size)
        assertNotNull(CraftingRecipes.offered(o.chisel, o.amethyst).firstOrNull())
    }

    @Test
    fun `chisel on an uncut gem offers exactly one recipe`() {
        val gems =
            listOf(
                o.uncut_opal,
                o.uncut_jade,
                o.uncut_red_topaz,
                o.uncut_sapphire,
                o.uncut_emerald,
                o.uncut_ruby,
                o.uncut_diamond,
                o.uncut_dragonstone,
                o.uncut_onyx,
                o.uncut_zenyte,
            )
        for (gem in gems) {
            assertEquals(1, CraftingRecipes.offered(o.chisel, gem).size)
            assertNotNull(CraftingRecipes.cutting(gem))
        }
        assertNull(CraftingRecipes.cutting(o.opal))
    }

    @Test
    fun `thread lasts five actions`() {
        assertEquals(5, CraftingRecipe.THREAD_USES)
        assertTrue(recipe(o.leather_body).thread)
        assertTrue(recipe(o.snakeskin_body).thread)
        assertFalse(recipe(o.green_dhide_shield).thread)
        assertFalse(recipe(o.studded_body).thread)
    }

    @Test
    fun `triggers have no duplicate pairs`() {
        val pairs =
            CraftingRecipes.triggers().map {
                setOf(it.first.internalNameValue, it.second.internalNameValue)
            }
        assertEquals(pairs.size, pairs.toSet().size)
        assertTrue(pairs.isNotEmpty())
    }
}
