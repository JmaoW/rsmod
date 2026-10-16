package org.rsmod.content.skills.runecraft.configs

import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences

/** Everything runecraft uses or makes. The names are the internal obj names from the cache. */
object RunecraftObjs : ObjReferences() {
    val rune_essence = find("blankrune")
    val pure_essence = find("blankrune_high")

    val air_rune = find("airrune")
    val mind_rune = find("mindrune")
    val water_rune = find("waterrune")
    val earth_rune = find("earthrune")
    val fire_rune = find("firerune")
    val body_rune = find("bodyrune")
    val cosmic_rune = find("cosmicrune")
    val chaos_rune = find("chaosrune")
    val astral_rune = find("astralrune")
    val nature_rune = find("naturerune")
    val law_rune = find("lawrune")
    val death_rune = find("deathrune")
    val blood_rune = find("bloodrune")
    val wrath_rune = find("wrathrune")

    val mist_rune = find("mistrune")
    val dust_rune = find("dustrune")
    val mud_rune = find("mudrune")
    val smoke_rune = find("smokerune")
    val steam_rune = find("steamrune")
    val lava_rune = find("lavarune")

    val air_talisman = find("air_talisman")
    val mind_talisman = find("mind_talisman")
    val water_talisman = find("water_talisman")
    val earth_talisman = find("earth_talisman")
    val fire_talisman = find("fire_talisman")
    val body_talisman = find("body_talisman")
    val cosmic_talisman = find("cosmic_talisman")
    val chaos_talisman = find("chaos_talisman")
    val nature_talisman = find("nature_talisman")
    val law_talisman = find("law_talisman")
    val death_talisman = find("death_talisman")
    val blood_talisman = find("blood_talisman")
    val wrath_talisman = find("wrath_talisman")
    val elemental_talisman = find("elemental_talisman")
    val catalytic_talisman = find("catalytic_talisman")

    /** The plain tiara a talisman is bound into. Crafting makes it from a silver bar. */
    val tiara = find("tiara")
    val air_tiara = find("tiara_air")
    val mind_tiara = find("tiara_mind")
    val water_tiara = find("tiara_water")
    val earth_tiara = find("tiara_earth")
    val fire_tiara = find("tiara_fire")
    val body_tiara = find("tiara_body")
    val cosmic_tiara = find("tiara_cosmic")
    val chaos_tiara = find("tiara_chaos")
    val nature_tiara = find("tiara_nature")
    val law_tiara = find("tiara_law")
    val death_tiara = find("tiara_death")
    val wrath_tiara = find("tiara_wrath")

    val small_pouch = find("rcu_pouch_small")
    val medium_pouch = find("rcu_pouch_medium")
    val large_pouch = find("rcu_pouch_large")
    val giant_pouch = find("rcu_pouch_giant")
    val colossal_pouch = find("rcu_pouch_colossal")
    val abyssal_needle = find("abyssal_needle")

    /** The emerald necklace that makes combination runes every time. */
    val binding_necklace = find("magic_emerald_necklace")
}

object RunecraftLocs : LocReferences() {
    val air_altar = find("air_altar")
    val mind_altar = find("mind_altar")
    val water_altar = find("water_altar")
    val earth_altar = find("earth_altar")
    val fire_altar = find("fire_altar")
    val body_altar = find("body_altar")
    val cosmic_altar = find("cosmic_altar")
    val chaos_altar = find("chaos_altar")
    val astral_altar = find("astral_altar")
    val nature_altar = find("nature_altar")
    val law_altar = find("law_altar")
    val death_altar = find("death_altar")
    val blood_altar = find("blood_altar")
    val wrath_altar = find("wrath_altar")

    /**
     * The rifts the cache has in the Abyss' inner ring. There is none for blood, astral or wrath.
     */
    val air_rift = find("abyss_exit_to_air")
    val mind_rift = find("abyss_exit_to_mind")
    val water_rift = find("abyss_exit_to_water")
    val earth_rift = find("abyss_exit_to_earth")
    val fire_rift = find("abyss_exit_to_fire")
    val body_rift = find("abyss_exit_to_body")
    val cosmic_rift = find("abyss_exit_to_cosmic")
    val chaos_rift = find("abyss_exit_to_chaos")
    val nature_rift = find("abyss_exit_to_nature")
    val law_rift = find("abyss_exit_to_law")
    val death_rift = find("abyss_exit_to_death")
    val soul_rift = find("abyss_exit_to_soul")

    /** Spare portal types that become the blood, astral and wrath rifts, see [RiftEdits]. */
    val blood_rift = find("essencemine_portal_3")
    val astral_rift = find("essencemine_portal_4")
    val wrath_rift = find("essencemine_portal_5")

    val air_exit = find("airtemple_exit_portal")
    val mind_exit = find("mindtemple_exit_portal")
    val water_exit = find("watertemple_exit_portal")
    val earth_exit = find("earthtemple_exit_portal")
    val fire_exit = find("firetemple_exit_portal")
    val body_exit = find("bodytemple_exit_portal")
    val cosmic_exit = find("cosmictemple_exit_portal")
    val chaos_exit = find("chaostemple_exit_portal")
    val nature_exit = find("naturetemple_exit_portal")
    val law_exit = find("lawtemple_exit_portal")
    val death_exit = find("deathtemple_exit_portal")
    val blood_exit = find("bloodtemple_exit_portal")
    val wrath_exit = find("wrathtemple_exit_portal")

    /** The large rune essence rock and the portal that leads out of the mine. */
    val essence_rock = find("blankrunestone")
    val mine_exit = find("essencemine_portal_2")
}

object RunecraftNpcs : NpcReferences() {
    /** The Mage of Zamorak with the Talk-to, Trade and Teleport options. */
    val mage_of_zamorak = find("rcu_zammy_mage1b")
    val dark_mage = find("rcu_zammy_mage2")
    val aubury = find("aubury_3op")
}

object RunecraftInvs : InvReferences() {
    /** The cache's rune shop, which is the Mage of Zamorak's Battle Runes here. */
    val rune_shop = find("runeshop")
}

object RunecraftSeqs : SeqReferences() {
    val runecraft = find("human_runecraft")
}

object RunecraftSpotanims : SpotanimReferences() {
    val runecraft = find("runecrafting")
}

/** The varbits that hold what is in each essence pouch, and which kind of essence it is. */
object RunecraftVarBits : VarBitReferences() {
    val small_pouch = find("small_essence_pouch")
    val medium_pouch = find("medium_essence_pouch")
    val large_pouch = find("large_essence_pouch")
    val giant_pouch = find("giant_essence_pouch")
    val colossal_pouch = find("colossal_essence_pouch")

    val small_pouch_type = find("small_essence_pouch_type")
    val medium_pouch_type = find("medium_essence_pouch_type")
    val large_pouch_type = find("large_essence_pouch_type")
    val giant_pouch_type = find("giant_essence_pouch_type")
    val colossal_pouch_type = find("colossal_essence_pouch_type")
}
