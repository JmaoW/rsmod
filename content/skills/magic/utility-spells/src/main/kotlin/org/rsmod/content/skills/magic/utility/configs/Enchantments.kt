package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.game.type.obj.ObjType

/** The jewellery the Lvl-1 to Lvl-7 Enchant spells turn into the enchanted forms. */
object JewelleryObjs : ObjReferences() {
    val sapphire_ring = find("sapphire_ring")
    val ring_of_recoil = find("ring_of_recoil")
    val jewl_sapphire_bracelet = find("jewl_sapphire_bracelet")
    val jewl_bracelet_of_clay = find("jewl_bracelet_of_clay")
    val sapphire_necklace = find("sapphire_necklace")
    val necklace_of_minigames_8 = find("necklace_of_minigames_8")
    val strung_sapphire_amulet = find("strung_sapphire_amulet")
    val amulet_of_magic = find("amulet_of_magic")
    val opal_ring = find("opal_ring")
    val ring_of_pursuit = find("ring_of_pursuit")
    val opal_bracelet = find("opal_bracelet")
    val expeditious_bracelet = find("expeditious_bracelet")
    val opal_necklace = find("opal_necklace")
    val dodgy_necklace = find("dodgy_necklace")
    val strung_opal_amulet = find("strung_opal_amulet")
    val amulet_of_bounty = find("amulet_of_bounty")
    val emerald_ring = find("emerald_ring")
    val ring_of_dueling_8 = find("ring_of_dueling_8")
    val emerald_necklace = find("emerald_necklace")
    val magic_emerald_necklace = find("magic_emerald_necklace")
    val jewl_emerald_bracelet = find("jewl_emerald_bracelet")
    val jewl_castlewars_bracelet3 = find("jewl_castlewars_bracelet3")
    val strung_emerald_amulet = find("strung_emerald_amulet")
    val amulet_of_defence = find("amulet_of_defence")
    val jade_ring = find("jade_ring")
    val ring_of_returning_5 = find("ring_of_returning_5")
    val jade_necklace = find("jade_necklace")
    val necklace_of_passage_5 = find("necklace_of_passage_5")
    val jade_bracelet = find("jade_bracelet")
    val flamtaer_bracelet = find("flamtaer_bracelet")
    val strung_jade_amulet = find("strung_jade_amulet")
    val amulet_of_chemistry = find("amulet_of_chemistry")
    val ruby_ring = find("ruby_ring")
    val ring_of_forging = find("ring_of_forging")
    val ruby_necklace = find("ruby_necklace")
    val necklace_of_digsite_5 = find("necklace_of_digsite_5")
    val jewl_ruby_bracelet = find("jewl_ruby_bracelet")
    val jewl_bracelet_of_innoculation = find("jewl_bracelet_of_innoculation")
    val strung_ruby_amulet = find("strung_ruby_amulet")
    val amulet_of_strength = find("amulet_of_strength")
    val topaz_ring = find("topaz_ring")
    val vampyre_ring = find("vampyre_ring")
    val topaz_necklace = find("topaz_necklace")
    val necklace_of_faith = find("necklace_of_faith")
    val topaz_bracelet = find("topaz_bracelet")
    val bracelet_of_slaughter = find("bracelet_of_slaughter")
    val strung_topaz_amulet = find("strung_topaz_amulet")
    val burning_amulet_5 = find("burning_amulet_5")
    val diamond_ring = find("diamond_ring")
    val ring_of_life = find("ring_of_life")
    val diamond_necklace = find("diamond_necklace")
    val jewl_necklace_of_phoenix = find("jewl_necklace_of_phoenix")
    val jewl_diamond_bracelet = find("jewl_diamond_bracelet")
    val jewl_runerunning_bracelet_5 = find("jewl_runerunning_bracelet_5")
    val strung_diamond_amulet = find("strung_diamond_amulet")
    val amulet_of_power = find("amulet_of_power")
    val dragonstone_ring = find("dragonstone_ring")
    val ring_of_wealth = find("ring_of_wealth")
    val dragonstone_necklace = find("dragonstone_necklace")
    val jewl_necklace_of_skills = find("jewl_necklace_of_skills")
    val jewl_dragonstone_bracelet = find("jewl_dragonstone_bracelet")
    val jewl_bracelet_of_combat = find("jewl_bracelet_of_combat")
    val strung_dragonstone_amulet = find("strung_dragonstone_amulet")
    val amulet_of_glory = find("amulet_of_glory")
    val onyx_ring = find("onyx_ring")
    val enchanted_onyx_ring = find("enchanted_onyx_ring")
    val onyx_necklace = find("onyx_necklace")
    val jewl_beserker_necklace = find("jewl_beserker_necklace")
    val jewl_onyx_bracelet = find("jewl_onyx_bracelet")
    val jewl_bracelet_regen = find("jewl_bracelet_regen")
    val strung_onyx_amulet = find("strung_onyx_amulet")
    val enchanted_onyx_amulet = find("enchanted_onyx_amulet")
    val zenyte_ring = find("zenyte_ring")
    val zenyte_ring_enchanted = find("zenyte_ring_enchanted")
    val zenyte_necklace = find("zenyte_necklace")
    val zenyte_necklace_enchanted = find("zenyte_necklace_enchanted")
    val zenyte_bracelet = find("zenyte_bracelet")
    val zenyte_bracelet_enchanted = find("zenyte_bracelet_enchanted")
    val zenyte_amulet = find("zenyte_amulet")
    val zenyte_amulet_enchanted = find("zenyte_amulet_enchanted")
}

/** One jewellery item, and the enchanted item the spell turns it into. */
class Enchantment(val input: ObjType, val output: ObjType)

object Enchantments {
    /** The items each enchant spell works on, by the spell's level (1 to 7). */
    val byLevel: Map<Int, List<Enchantment>> =
        mapOf(
            1 to
                listOf(
                    Enchantment(JewelleryObjs.sapphire_ring, JewelleryObjs.ring_of_recoil),
                    Enchantment(
                        JewelleryObjs.jewl_sapphire_bracelet,
                        JewelleryObjs.jewl_bracelet_of_clay,
                    ),
                    Enchantment(
                        JewelleryObjs.sapphire_necklace,
                        JewelleryObjs.necklace_of_minigames_8,
                    ),
                    Enchantment(
                        JewelleryObjs.strung_sapphire_amulet,
                        JewelleryObjs.amulet_of_magic,
                    ),
                    Enchantment(JewelleryObjs.opal_ring, JewelleryObjs.ring_of_pursuit),
                    Enchantment(JewelleryObjs.opal_bracelet, JewelleryObjs.expeditious_bracelet),
                    Enchantment(JewelleryObjs.opal_necklace, JewelleryObjs.dodgy_necklace),
                    Enchantment(JewelleryObjs.strung_opal_amulet, JewelleryObjs.amulet_of_bounty),
                ),
            2 to
                listOf(
                    Enchantment(JewelleryObjs.emerald_ring, JewelleryObjs.ring_of_dueling_8),
                    Enchantment(
                        JewelleryObjs.emerald_necklace,
                        JewelleryObjs.magic_emerald_necklace,
                    ),
                    Enchantment(
                        JewelleryObjs.jewl_emerald_bracelet,
                        JewelleryObjs.jewl_castlewars_bracelet3,
                    ),
                    Enchantment(
                        JewelleryObjs.strung_emerald_amulet,
                        JewelleryObjs.amulet_of_defence,
                    ),
                    Enchantment(JewelleryObjs.jade_ring, JewelleryObjs.ring_of_returning_5),
                    Enchantment(JewelleryObjs.jade_necklace, JewelleryObjs.necklace_of_passage_5),
                    Enchantment(JewelleryObjs.jade_bracelet, JewelleryObjs.flamtaer_bracelet),
                    Enchantment(JewelleryObjs.strung_jade_amulet, JewelleryObjs.amulet_of_chemistry),
                ),
            3 to
                listOf(
                    Enchantment(JewelleryObjs.ruby_ring, JewelleryObjs.ring_of_forging),
                    Enchantment(JewelleryObjs.ruby_necklace, JewelleryObjs.necklace_of_digsite_5),
                    Enchantment(
                        JewelleryObjs.jewl_ruby_bracelet,
                        JewelleryObjs.jewl_bracelet_of_innoculation,
                    ),
                    Enchantment(JewelleryObjs.strung_ruby_amulet, JewelleryObjs.amulet_of_strength),
                    Enchantment(JewelleryObjs.topaz_ring, JewelleryObjs.vampyre_ring),
                    Enchantment(JewelleryObjs.topaz_necklace, JewelleryObjs.necklace_of_faith),
                    Enchantment(JewelleryObjs.topaz_bracelet, JewelleryObjs.bracelet_of_slaughter),
                    Enchantment(JewelleryObjs.strung_topaz_amulet, JewelleryObjs.burning_amulet_5),
                ),
            4 to
                listOf(
                    Enchantment(JewelleryObjs.diamond_ring, JewelleryObjs.ring_of_life),
                    Enchantment(
                        JewelleryObjs.diamond_necklace,
                        JewelleryObjs.jewl_necklace_of_phoenix,
                    ),
                    Enchantment(
                        JewelleryObjs.jewl_diamond_bracelet,
                        JewelleryObjs.jewl_runerunning_bracelet_5,
                    ),
                    Enchantment(JewelleryObjs.strung_diamond_amulet, JewelleryObjs.amulet_of_power),
                ),
            5 to
                listOf(
                    Enchantment(JewelleryObjs.dragonstone_ring, JewelleryObjs.ring_of_wealth),
                    Enchantment(
                        JewelleryObjs.dragonstone_necklace,
                        JewelleryObjs.jewl_necklace_of_skills,
                    ),
                    Enchantment(
                        JewelleryObjs.jewl_dragonstone_bracelet,
                        JewelleryObjs.jewl_bracelet_of_combat,
                    ),
                    Enchantment(
                        JewelleryObjs.strung_dragonstone_amulet,
                        JewelleryObjs.amulet_of_glory,
                    ),
                ),
            6 to
                listOf(
                    Enchantment(JewelleryObjs.onyx_ring, JewelleryObjs.enchanted_onyx_ring),
                    Enchantment(JewelleryObjs.onyx_necklace, JewelleryObjs.jewl_beserker_necklace),
                    Enchantment(
                        JewelleryObjs.jewl_onyx_bracelet,
                        JewelleryObjs.jewl_bracelet_regen,
                    ),
                    Enchantment(
                        JewelleryObjs.strung_onyx_amulet,
                        JewelleryObjs.enchanted_onyx_amulet,
                    ),
                ),
            7 to
                listOf(
                    Enchantment(JewelleryObjs.zenyte_ring, JewelleryObjs.zenyte_ring_enchanted),
                    Enchantment(
                        JewelleryObjs.zenyte_necklace,
                        JewelleryObjs.zenyte_necklace_enchanted,
                    ),
                    Enchantment(
                        JewelleryObjs.zenyte_bracelet,
                        JewelleryObjs.zenyte_bracelet_enchanted,
                    ),
                    Enchantment(JewelleryObjs.zenyte_amulet, JewelleryObjs.zenyte_amulet_enchanted),
                ),
        )
}
