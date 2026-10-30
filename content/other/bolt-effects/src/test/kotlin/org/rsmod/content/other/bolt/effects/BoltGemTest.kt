package org.rsmod.content.other.bolt.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoltGemTest {
    @Test
    fun `every enchanted bolt of a regular metal is told apart`() {
        val names =
            mapOf(
                "xbows_crossbow_bolts_bronze_tipped_opal_enchanted" to BoltGem.Opal,
                "xbows_crossbow_bolts_blurite_tipped_jade_enchanted" to BoltGem.Jade,
                "xbows_crossbow_bolts_iron_tipped_pearl_enchanted" to BoltGem.Pearl,
                "xbows_crossbow_bolts_steel_tipped_redtopaz_enchanted" to BoltGem.Topaz,
                "xbows_crossbow_bolts_mithril_tipped_sapphire_enchanted" to BoltGem.Sapphire,
                "xbows_crossbow_bolts_mithril_tipped_emerald_enchanted" to BoltGem.Emerald,
                "xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted" to BoltGem.Ruby,
                "xbows_crossbow_bolts_adamantite_tipped_diamond_enchanted" to BoltGem.Diamond,
                "xbows_crossbow_bolts_runite_tipped_dragonstone_enchanted" to BoltGem.Dragonstone,
                "xbows_crossbow_bolts_runite_tipped_onyx_enchanted" to BoltGem.Onyx,
            )
        for ((name, gem) in names) {
            assertEquals(gem, BoltGem.of(name), name)
        }
    }

    @Test
    fun `the stack variants of an enchanted bolt count too`() {
        for (stack in 2..5) {
            assertEquals(
                BoltGem.Ruby,
                BoltGem.of("xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted_$stack"),
            )
        }
    }

    @Test
    fun `every enchanted dragon bolt is told apart`() {
        val gems =
            mapOf(
                "opal" to BoltGem.Opal,
                "jade" to BoltGem.Jade,
                "pearl" to BoltGem.Pearl,
                "topaz" to BoltGem.Topaz,
                "sapphire" to BoltGem.Sapphire,
                "emerald" to BoltGem.Emerald,
                "ruby" to BoltGem.Ruby,
                "diamond" to BoltGem.Diamond,
                "dragonstone" to BoltGem.Dragonstone,
                "onyx" to BoltGem.Onyx,
            )
        for ((key, gem) in gems) {
            assertEquals(gem, BoltGem.of("dragon_bolts_enchanted_$key"), key)
        }
    }

    @Test
    fun `bolts that are not enchanted have no gem`() {
        assertNull(BoltGem.of("xbows_crossbow_bolts_runite"))
        assertNull(BoltGem.of("xbows_crossbow_bolts_adamantite_tipped_ruby"))
        assertNull(BoltGem.of("dragon_bolts_unenchanted_ruby"))
        assertNull(BoltGem.of("dragon_bolts"))
        assertNull(BoltGem.of("opal_bolt"))
        assertNull(BoltGem.of(null))
    }

    @Test
    fun `chances are the wiki's`() {
        assertEquals(50, BoltGem.Opal.pvm)
        assertEquals(0, BoltGem.Topaz.pvm, "topaz only works on players")
        assertEquals(40, BoltGem.Topaz.pvp)
        assertEquals(250, BoltGem.Sapphire.pvm)
        assertEquals(50, BoltGem.Sapphire.pvp)
        assertEquals(550, BoltGem.Emerald.pvm)
        assertEquals(60, BoltGem.Ruby.pvm)
        assertEquals(110, BoltGem.Ruby.pvp)
        assertEquals(100, BoltGem.Diamond.pvm)
        assertEquals(110, BoltGem.Onyx.pvm)
    }

    @Test
    fun `six effects work on a shot that would miss`() {
        val bypass = BoltGem.entries.filter { it.bypassesAccuracy }
        assertEquals(
            setOf(
                BoltGem.Opal,
                BoltGem.Jade,
                BoltGem.Pearl,
                BoltGem.Topaz,
                BoltGem.Ruby,
                BoltGem.Diamond,
            ),
            bypass.toSet(),
        )
    }

    @Test
    fun `dragons and giants of fire are fiery and baby dragons are not`() {
        for (name in
            listOf(
                "red_dragon",
                "red_dragon3",
                "black_dragon_strongholdcave_2",
                "green_dragon5",
                "bronze_dragon",
                "steel_dragon_strongholdcave",
                "rune_dragon",
                "adamant_dragon",
                "lava_dragon",
                "brut_green_dragon",
                "deadman_breach_king_black_dragon",
                "firegiant",
                "firegiant_strongholdcave_2",
                "kourend_firegiant1",
                "slayer_pyrefiend_2",
                "wild_cave_pyrefiend",
            )) {
            assertTrue(BoltTargets.isFieryNpc(name), name)
        }
        for (name in
            listOf(
                "babybluedragon",
                "babyreddragon2",
                "baby_black_dragon_nohunt",
                "dragonslayer_zombie_2",
                "dragonslayer_demon",
                "poh_babyreddragon",
                "goblin",
            )) {
            assertFalse(BoltTargets.isFieryNpc(name), name)
        }
        assertFalse(BoltTargets.isFieryNpc(null))
    }

    @Test
    fun `a player is fiery in a fire staff or cape and unaffected by pearls in a water staff`() {
        assertTrue(BoltTargets.isFireGear("staff_of_fire", null))
        assertTrue(BoltTargets.isFireGear(null, "tzhaar_cape_fire"))
        assertFalse(BoltTargets.isFireGear("staff_of_water", "cape_of_legends"))
        assertTrue(BoltTargets.isWaterStaff("staff_of_water"))
        assertTrue(BoltTargets.isWaterStaff("mystic_water_staff"))
        assertFalse(BoltTargets.isWaterStaff("staff_of_fire"))
        assertFalse(BoltTargets.isWaterStaff(null))
    }
}
