package org.rsmod.content.areas.misc.isleofsouls.pets

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

typealias pet_varps = PetVarps

typealias pet_varbits = PetVarBits

typealias pet_objs = PetMiscObjs

object PetVarps : VarpReferences() {
    /** The item of the pet following the player (its current form), 0 for none. Saved. */
    val follower = find("isle_pet_follower")

    /**
     * Every pet the player has received, by its bit in the insurance enum: bits 0-30 in the first,
     * 31-61 in the second, 62 on in the third (the same split as the client's bitmasks). Saved.
     */
    val insured =
        listOf(find("isle_pet_insured_1"), find("isle_pet_insured_2"), find("isle_pet_insured_3"))

    /** The metamorphoses unlocked by items or feats ([PetUnlock]). Saved. */
    val unlocks = find("isle_pet_unlocks")

    /** The rift guardian's colours unlocked by crafting (by form index), and bit 31 = locked. */
    val rift_unlocks = find("isle_pet_rift_unlocks")

    /** The client's follower: the npc's index, so only the owner sees its options. */
    val follower_npc = find("follower_npc")

    // The pet insurance screen (interface 148) reads these.
    val insurance_transmit = find("pet_insurance_perm_transmit")
    val insurance_bitmasks =
        listOf(
            find("pet_insurance_bitmask1"),
            find("pet_insurance_bitmask2"),
            find("pet_insurance_bitmask3"),
        )
    val insurance_owned =
        listOf(
            find("pet_insurance_owned_bitmask1"),
            find("pet_insurance_owned_bitmask2"),
            find("pet_insurance_owned_bitmask3"),
        )
}

internal object PetVarpBuilder : VarpBuilder() {
    init {
        build("isle_pet_follower") { permanent = true }
        build("isle_pet_insured_1") { permanent = true }
        build("isle_pet_insured_2") { permanent = true }
        build("isle_pet_insured_3") { permanent = true }
        build("isle_pet_unlocks") { permanent = true }
        build("isle_pet_rift_unlocks") { permanent = true }
    }
}

object PetVarBits : VarBitReferences() {
    /** The pet picked on the insurance screen, its insurance bit + 1 (0 = none). */
    val reclaim = find("pet_insurance_reclaim")
}

object PetInterfaces : InterfaceReferences() {
    val insurance = find("pet_insurance")
}

object PetComponents : ComponentReferences() {
    val frame = find("pet_insurance:frame")

    /** The pet rows: each pet has five children, its row box at [PetInsurance.rowChild]. */
    val pets = find("pet_insurance:pets")

    /** The "Confirm" button the screen builds (child 0) once a pet is picked. */
    val confirm = find("pet_insurance:confirm_click")
}

object PetSeqs : SeqReferences() {
    val pickup = find("human_pickupfloor")

    // The emotes of Wisp, Butch and Yami, in the order the wiki lists them.
    val wisp_melee = find("npc_whisperer_01_attack_melee_01")
    val wisp_bind = find("npc_whisperer_01_attack_screech_01_end_pet")
    val wisp_special = find("npc_whisperer_01_teleport_02_pet")
    val butch_attack = find("npc_vardorvis_01_melee_01")
    val butch_respawn = find("npc_vardorvis_01_spawn_01")
    val butch_strangle = find("npc_vardorvis_01_entangle_end")
    val butch_dash = find("npc_vardorvis_01_dash_01")
    val yami_click = find("npc_yama_noncombat_click01")
    val yami_summon = find("npc_yama_noncombat_summon01")
    val yami_spawn = find("npc_yama_noncombat_spawn01")
    val yami_idle = find("npc_yama_noncombat_idle01")
}

object PetMiscNpcs : NpcReferences() {
    val probita = find("pet_insurance_broker")
}

/** The items used on pets to change or unlock their looks. */
object PetMiscObjs : ObjReferences() {
    val mole_claw = find("mole_claw")
    val mole_skin = find("mole_skin")
    val spirit_flakes = find("spirit_flakes")
    val funky_shaped_log = find("forestry_funky_shaped_log")
    val golden_pheasant_egg = find("forestry_pheasant_pet_egg")
    val fox_whistle = find("forestry_fox_pet_whistle")
    val dark_acorn = find("dark_acorn")
    val calcified_acorn = find("calcified_acorn")
    val acorn = find("acorn")
    val crystal_acorn = find("crystal_tree_seed")
    val dragonfruit_tree_seed = find("dragonfruit_tree_seed")
    val guam_seed = find("guam_seed")
    val white_lily_seed = find("white_lily_seed")
    val redwood_tree_seed = find("redwood_tree_seed")
    val white_berries = find("white_berries")
    val redberries = find("redberries")
    val poison_ivy_berries = find("poisonivy_berries")
    val blue_firelighter = find("gnomish_firelighter_blue")
    val green_firelighter = find("gnomish_firelighter_green")
    val purple_firelighter = find("trail_gnomish_firelighter_purple")
    val white_firelighter = find("trail_gnomish_firelighter_white")
    val metamorphic_dust = find("raids_challenge_morph")
    val sanguine_dust = find("tob_hardmode_dust")
    val orange_egg_sac = find("hosdun_orange_egg_sac")
    val blue_egg_sac = find("hosdun_blue_egg_sac")
    val parasitic_egg = find("nightmare_challenge_morph")
    val guardians_eye = find("guardians_eye")
    val remnant_akkha = find("toa_pet_morph_akkha")
    val remnant_baba = find("toa_pet_morph_baba")
    val remnant_kephri = find("toa_pet_morph_kephri")
    val remnant_zebak = find("toa_pet_morph_zebak")
    val ancient_remnant = find("toa_pet_morph_wardens")
    val charged_ice = find("muspah_pet_morph")
    val coagulated_venom = find("araxxor_pet_morph")

    // Rock golem looks.
    val rock = find("rock")
    val tin_ore = find("tin_ore")
    val copper_ore = find("copper_ore")
    val iron_ore = find("iron_ore")
    val blurite_ore = find("blurite_ore")
    val silver_ore = find("silver_ore")
    val coal = find("coal")
    val gold_ore = find("gold_ore")
    val mithril_ore = find("mithril_ore")
    val granite_500g = find("enakh_granite_tiny")
    val granite_2kg = find("enakh_granite_small")
    val granite_5kg = find("enakh_granite_medium")
    val adamantite_ore = find("adamantite_ore")
    val runite_ore = find("runite_ore")
    val amethyst = find("amethyst")
    val lovakite_ore = find("lovakite_ore")
    val elemental_ore = find("elemental_workshop_ore")
    val daeyalt_ore = find("sang_jas_ore")

    // Beaver looks.
    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val willow_logs = find("willow_logs")
    val teak_logs = find("teak_logs")
    val maple_logs = find("maple_logs")
    val mahogany_logs = find("mahogany_logs")
    val arctic_pine_logs = find("arctic_pine_log")
    val yew_logs = find("yew_logs")
    val magic_logs = find("magic_logs")
    val redwood_logs = find("redwood_logs")
}
