package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences

typealias utility_objs = UtilityObjs

typealias utility_seqs = UtilitySeqs

typealias utility_spotanims = UtilitySpotanims

typealias utility_locs = UtilityLocs

typealias utility_timers = UtilityTimers

/** Everything the utility spells use or make. The names are the internal obj names. */
object UtilityObjs : ObjReferences() {
    val coins = find("coins")
    val banana = find("banana")
    val peach = find("peach")

    val bones = find("bones")
    val burnt_bones = find("bones_burnt")
    val bat_bones = find("bat_bones")
    val big_bones = find("big_bones")
    val wolf_bones = find("wolf_bones")
    val monkey_bones = find("mm_normal_monkey_bones")
    val jogre_bones = find("tbwt_jogre_bones")

    val copper_ore = find("copper_ore")
    val tin_ore = find("tin_ore")
    val iron_ore = find("iron_ore")
    val silver_ore = find("silver_ore")
    val gold_ore = find("gold_ore")
    val mithril_ore = find("mithril_ore")
    val adamantite_ore = find("adamantite_ore")
    val runite_ore = find("runite_ore")
    val coal = find("coal")

    val unpowered_orb = find("stafforb")
    val air_orb = find("air_orb")
    val earth_orb = find("earth_orb")
    val fire_orb = find("fire_orb")
    val water_orb = find("water_orb")

    val bronze_bar = find("bronze_bar")
    val iron_bar = find("iron_bar")
    val steel_bar = find("steel_bar")
    val silver_bar = find("silver_bar")
    val gold_bar = find("gold_bar")
    val mithril_bar = find("mithril_bar")
    val adamantite_bar = find("adamantite_bar")
    val runite_bar = find("runite_bar")
}

object UtilitySeqs : SeqReferences() {
    val cast_low_alchemy = find("human_castlowlvlalchemy")
    val cast_high_alchemy = find("human_casthighlvlalchemy")
    val cast_bones_to_bananas = find("human_castbonestobananas")
    val cast_superheat = find("human_castsuperheatitem")
    val cast_telegrab = find("human_casttelegrab")
    val cast_charge_orb = find("human_castchargeorb")
    val cast_god_charge = find("human_casting")
    val enchant_ring = find("human_cast_enchantring")
    val cast_teleport_other = find("human_teleport_other_casting")
    val teleport_other_impact = find("human_teleport_other_impact")
    val cast_teleport = find("human_castteleport")
    val cast_teleport_reverse = find("human_castteleport_reverse")
    val cast_teleport_ancient = find("zaros_vertical_casting")
    val cast_teleport_lunar = find("human_cast2_teleport")
    val enchant_level_1 = find("human_enchantamuletlvl1")
    val enchant_level_2 = find("human_enchantamuletlvl2")
    val enchant_level_3 = find("human_enchantamuletlvl3")
}

object UtilitySpotanims : SpotanimReferences() {
    val low_alchemy = find("lowlvlalchemy_casting")
    val high_alchemy = find("highlvlalchemy_casting")
    val bones_to_bananas = find("bonestobananas_casting")
    val superheat = find("superheatitem_casting")
    val enchant_level_1 = find("enchant_amulet_lvl1")
    val enchant_level_2 = find("enchant_amulet_lvl2")
    val enchant_level_3 = find("enchant_amulet_lvl3")
    val enchant_level_4 = find("enchant_amulet2_lvl4")
    val enchant_level_5 = find("enchant_amulet2_lvl5")
    val enchant_level_6 = find("enchant_amulet2_lvl6")
    val telegrab_impact = find("telegrab_impact")
    val teleport_other_casting = find("teleport_other_casting")
    val teleport_other_impact = find("teleport_other_impact")
    val teleport = find("teleport_casting")
    val teleport_ancient = find("zaros_teleport")
    val teleport_lunar = find("lunar_teleport_spotanim")
}

object UtilityLocs : LocReferences() {
    val obelisk_air = find("obelisk_air")
    val obelisk_earth = find("obelisk_earth")
    val obelisk_fire = find("obelisk_fire")
    val obelisk_water = find("obelisk_water")
}

object UtilityTimers : TimerReferences() {
    /** Ends the Charge spell after its duration. */
    val charge = find("magic_charge")

    /** Casts alchemy again on a stack while the Golden God relic says to. */
    val alchemy_repeat = find("magic_alch_recast")

    /** Takes the player back to the Lunar spellbook after a Spellbook Swap. */
    val spellbook_swap = find("magic_spellbook_swap")

    /** Ends Mark of Darkness. */
    val mark_of_darkness = find("magic_mark_of_darkness")
}
