package org.rsmod.content.skills.hunter.configs

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

typealias hunter_objs = HunterObjs

typealias hunter_locs = HunterLocs

typealias hunter_seqs = HunterSeqs

typealias hunter_timers = HunterTimers

object HunterSeqs : SeqReferences() {
    val lay_trap = find("human_laytrap")
    val lay_small = find("hunting_setting_trap_small")
    val lay_sapling = find("hunting_setting_sapling_trap")
    val bait = find("hunting_baiting_small_traps")
    val take = find("human_hunting_dismantle_net")
    val net = find("ii_human_net_throw")
    val old_net = find("ii_human_net_throw_oldnet")
    val butterfly_net = find("human_butterflynet_swing")
    val hands = find("human_catch")
    val tease = find("hunting_teasing_animal")
    val jump = find("agility_pyramid_gap_jump")
    val falcon_attack = find("human_falconer_attack")
    val pick_up = find("human_pickupfloor")
    val birdhouse = find("birdhouse_make")
}

object HunterTimers : TimerReferences() {
    /** Drives a hunter's traps and birdhouses: ticks on every player who has some. */
    val tick = find("hunter_tick")
}

/** Server-side state kept on the player. Needs `packCache` and a line in `varp.sym`. */
object HunterVarps : VarpReferences() {
    /**
     * The minute (see [org.rsmod.content.skills.hunter.scripts.Birdhouses]) each house was filled.
     */
    val birdhouse_a_filled = find("hunter_birdhouse_a_filled")
    val birdhouse_b_filled = find("hunter_birdhouse_b_filled")
    val birdhouse_c_filled = find("hunter_birdhouse_c_filled")
    val birdhouse_d_filled = find("hunter_birdhouse_d_filled")

    /** How many seeds (up to ten) each house holds. */
    val birdhouse_a_seeds = find("hunter_birdhouse_a_seeds")
    val birdhouse_b_seeds = find("hunter_birdhouse_b_seeds")
    val birdhouse_c_seeds = find("hunter_birdhouse_c_seeds")
    val birdhouse_d_seeds = find("hunter_birdhouse_d_seeds")

    /** The client's own state for each of the four birdhouse spots. */
    val birdhouse_a = find("birdhouse_transmit_a")
    val birdhouse_b = find("birdhouse_transmit_b")
    val birdhouse_c = find("birdhouse_transmit_c")
    val birdhouse_d = find("birdhouse_transmit_d")
}

/** The pit state of each pitfall, which decides what the pit looks like to its player. */
object HunterPitBits : VarBitReferences() {
    val pit1 = find("hunt_pitfall_state1")
    val pit2 = find("hunt_pitfall_state2")
    val pit3 = find("hunt_pitfall_state3")
    val pit4 = find("hunt_pitfall_state4")
    val pit5 = find("hunt_pitfall_state5")
    val pit6 = find("hunt_pitfall_state6")
    val pit7 = find("hunt_pitfall_state7")
    val pit8 = find("hunt_pitfall_state8")
    val pit9 = find("hunt_pitfall_state9")
    val pit10 = find("hunt_pitfall_state10")
    val pit11 = find("hunt_pitfall_state11")
    val pit12 = find("hunt_pitfall_state12")
    val pit13 = find("hunt_pitfall_state13")
    val pit14 = find("hunt_pitfall_state14")
    val pit15 = find("hunt_pitfall_state15")
    val pit16 = find("hunt_pitfall_state16")
    val pit17 = find("hunt_pitfall_state17")
    val pit18 = find("hunt_pitfall_state18")
    val pit19 = find("hunt_pitfall_state19")
    val pit20 = find("hunt_pitfall_state20")
    val pit21 = find("hunt_pitfall_state21")
    val pit22 = find("hunt_pitfall_state22")
    val pit23 = find("hunt_pitfall_state23")
    val pit24 = find("hunt_pitfall_state24")
    val pit25 = find("hunt_pitfall_state25")

    val all =
        listOf(
            pit1,
            pit2,
            pit3,
            pit4,
            pit5,
            pit6,
            pit7,
            pit8,
            pit9,
            pit10,
            pit11,
            pit12,
            pit13,
            pit14,
            pit15,
            pit16,
            pit17,
            pit18,
            pit19,
            pit20,
            pit21,
            pit22,
            pit23,
            pit24,
            pit25,
        )
}

/** Everything hunting gives out, uses or looks at. The names are the cache's internal names. */
object HunterObjs : ObjReferences() {
    // Traps and what they are set with.
    val bird_snare = find("hunting_ojibway_bird_snare")
    val box_trap = find("hunting_box_trap")
    val magic_box = find("magic_imp_box")
    val imp_in_a_box_full = find("magic_imp_box_full")
    val imp_in_a_box_half = find("magic_imp_box_half")
    val rope = find("rope")
    val small_net = find("net")
    val knife = find("knife")
    val teasing_stick = find("hunting_teasing_stick")
    val hunters_spear = find("hg_hunter_spear")
    val torch_lit = find("torch_lit")

    val logs = find("logs")
    val oak_logs = find("oak_logs")
    val willow_logs = find("willow_logs")
    val teak_logs = find("teak_logs")
    val maple_logs = find("maple_logs")
    val mahogany_logs = find("mahogany_logs")
    val yew_logs = find("yew_logs")
    val magic_logs = find("magic_logs")
    val redwood_logs = find("redwood_logs")

    // Bait.
    val tar_swamp = find("swamp_tar")
    val tar_guam = find("salamander_tar_green")
    val tar_marrentill = find("salamander_tar_orange")
    val tar_tarromin = find("salamander_tar_red")
    val tar_harralander = find("salamander_tar_black")
    val tar_irit = find("salamander_tar_mountain")
    val spicy_tomato = find("bowl_spicytomato")
    val spicy_minced_meat = find("bowl_spicymeat")
    val raw_beef = find("raw_beef")
    val raw_rainbow_fish = find("hunting_raw_fish_special")
    val barley = find("barley")
    val jerboa_tail = find("hunting_jerboa_tail")
    val red_bead = find("red_bead")
    val yellow_bead = find("yellow_bead")
    val black_bead = find("black_bead")
    val white_bead = find("white_bead")

    // What creatures give.
    val bones = find("bones")
    val big_bones = find("big_bones")
    val raw_bird_meat = find("spit_raw_bird_meat")
    val feather_red = find("hunting_jungle_feather")
    val feather_yellow = find("hunting_desert_feather")
    val feather_orange = find("hunting_woodland_feather")
    val feather_blue = find("hunting_polar_feather")
    val feather_stripy = find("hunting_stripy_bird_feather")
    val feather = find("feather")
    val ferret = find("hunting_ferret")
    val chinchompa = find("chinchompa_captured")
    val chinchompa_red = find("chinchompa_big_captured")
    val chinchompa_black = find("chinchompa_black")
    val kebbit_claws = find("huntingbeast_claws")
    val barb_tail_harpoon = find("hunting_barbed_harpoon")
    val kebbit_spike = find("huntingbeast_spike")
    val kebbit_teeth = find("huntingbeast_sabreteeth")
    val swamp_lizard = find("green_salamander")
    val orange_salamander = find("orange_salamander")
    val red_salamander = find("red_salamander")
    val black_salamander = find("black_salamander")
    val tecu_salamander_young = find("immature_mountain_salamander")
    val tecu_salamander = find("mountain_salamander")
    val larupia_fur = find("hunting_fur_jaguar_perfect")
    val tatty_larupia_fur = find("hunting_fur_jaguar_shabby")
    val graahk_fur = find("hunting_fur_leopard_perfect")
    val tatty_graahk_fur = find("hunting_fur_leopard_shabby")
    val kyatt_fur = find("hunting_fur_tiger_perfect")
    val tatty_kyatt_fur = find("hunting_fur_tiger_shabby")
    val raw_larupia = find("hunting_larupia_meat")
    val raw_graahk = find("hunting_graahk_meat")
    val raw_kyatt = find("hunting_kyatt_meat")
    val raw_pyre_fox = find("hunting_fennecfox_meat")
    val fox_fur = find("hunting_fennecfox_fur")
    val sunlight_antler = find("hunting_antelopesun_horn")
    val sunlight_fur = find("hunting_antelopesun_fur")
    val raw_sunlight_antelope = find("hunting_antelopesun_meat")
    val sunfire_splinters = find("sunfiresplinter")
    val moonlight_antler = find("hunting_antelopemoon_horn")
    val moonlight_fur = find("hunting_antelopemoon_fur")
    val raw_moonlight_antelope = find("hunting_antelopemoon_meat")

    // Big cat clothing, which softens a big cat's claws.
    val kyatt_hat = find("hunting_hat_tiger")
    val kyatt_top = find("hunting_torso_tiger")
    val kyatt_legs = find("hunting_trousers_tiger")
    val graahk_headdress = find("hunting_hat_leopard")
    val graahk_top = find("hunting_torso_leopard")
    val graahk_legs = find("hunting_trousers_leopard")
    val larupia_hat = find("hunting_hat_jaguar")
    val larupia_top = find("hunting_torso_jaguar")
    val larupia_legs = find("hunting_trousers_jaguar")

    // Butterflies, moths and implings.
    val butterfly_net = find("hunting_butterfly_net")
    val magic_butterfly_net = find("ii_magic_butterfly_net")
    val butterfly_jar = find("butterfly_jar")
    val ruby_harvest_jar = find("butterfly_jar_ruby")
    val sapphire_glacialis_jar = find("butterfly_jar_glacialis")
    val snowy_knight_jar = find("butterfly_jar_snowy")
    val black_warlock_jar = find("butterfly_jar_warlock")
    val sunlight_moth_jar = find("butterfly_jar_sunmoth")
    val moonlight_moth_jar = find("butterfly_jar_moonmoth")
    val impling_jar = find("ii_impling_jar")
    val baby_impling_jar = find("ii_captured_impling_1")
    val young_impling_jar = find("ii_captured_impling_2")
    val gourmet_impling_jar = find("ii_captured_impling_3")
    val earth_impling_jar = find("ii_captured_impling_4")
    val essence_impling_jar = find("ii_captured_impling_5")
    val eclectic_impling_jar = find("ii_captured_impling_6")
    val nature_impling_jar = find("ii_captured_impling_7")
    val magpie_impling_jar = find("ii_captured_impling_8")
    val ninja_impling_jar = find("ii_captured_impling_9")
    val dragon_impling_jar = find("ii_captured_impling_10")
    val lucky_impling_jar = find("ii_captured_impling_11")
    val crystal_impling_jar = find("ii_captured_impling_12")

    // Falconry.
    val falconers_glove = find("falcon_gloves")
    val falconers_glove_bird = find("falcon_on_gloves")
    val spotted_kebbit_fur = find("huntingbeast_speedy_fur")
    val dark_kebbit_fur = find("huntingbeast_silent_fur")
    val dashing_kebbit_fur = find("huntingbeast_speedy2_fur")

    // Birdhouses.
    val clockwork = find("poh_clockwork_mechanism")
    val hammer = find("hammer")
    val chisel = find("chisel")
    val birdhouse = find("birdhouse_normal")
    val oak_birdhouse = find("birdhouse_oak")
    val willow_birdhouse = find("birdhouse_willow")
    val teak_birdhouse = find("birdhouse_teak")
    val maple_birdhouse = find("birdhouse_maple")
    val mahogany_birdhouse = find("birdhouse_mahogany")
    val yew_birdhouse = find("birdhouse_yew")
    val magic_birdhouse = find("birdhouse_magic")
    val redwood_birdhouse = find("birdhouse_redwood")
    val bird_nest_red = find("bird_nest_egg_red")
    val bird_nest_green = find("bird_nest_egg_green")
    val bird_nest_blue = find("bird_nest_egg_blue")
    val bird_nest_seeds = find("bird_nest_seeds")
    val bird_nest_ring = find("bird_nest_ring")

    // Loot worth a task of its own.
    val strung_dragonstone_amulet = find("strung_dragonstone_amulet")
}

/** Every loc a trap passes through. The names are the cache's internal names. */
object HunterLocs : LocReferences() {
    // Bird snare.
    val snare_idle = find("hunting_ojibway_trap")
    val snare_failing = find("hunting_ojibway_trap_failing")
    val snare_broken = find("hunting_ojibway_trap_broken")
    val snare_catching_jungle = find("hunting_ojibway_trap_trapping_jungle")
    val snare_catching_desert = find("hunting_ojibway_trap_trapping_desert")
    val snare_catching_woodland = find("hunting_ojibway_trap_trapping_woodland")
    val snare_catching_polar = find("hunting_ojibway_trap_trapping_polar")
    val snare_catching_coloured = find("hunting_ojibway_trap_trapping_coloured")
    val snare_full_jungle = find("hunting_ojibway_trap_full_jungle")
    val snare_full_desert = find("hunting_ojibway_trap_full_desert")
    val snare_full_woodland = find("hunting_ojibway_trap_full_woodland")
    val snare_full_polar = find("hunting_ojibway_trap_full_polar")
    val snare_full_coloured = find("hunting_ojibway_trap_full_coloured")

    // Box trap.
    val box_idle = find("hunting_boxtrap_empty")
    val box_failing = find("hunting_boxtrap_failing")
    val box_failed = find("hunting_boxtrap_failed")
    val box_catching_chinchompa_n = find("hunting_boxtrap_trapping_chinchompa_n")
    val box_catching_chinchompa_e = find("hunting_boxtrap_trapping_chinchompa_e")
    val box_catching_chinchompa_s = find("hunting_boxtrap_trapping_chinchompa_s")
    val box_catching_chinchompa_w = find("hunting_boxtrap_trapping_chinchompa_w")
    val box_catching_big_n = find("hunting_boxtrap_trapping_chinchompa_big_n")
    val box_catching_big_e = find("hunting_boxtrap_trapping_chinchompa_big_e")
    val box_catching_big_s = find("hunting_boxtrap_trapping_chinchompa_big_s")
    val box_catching_big_w = find("hunting_boxtrap_trapping_chinchompa_big_w")
    val box_catching_black_n = find("hunting_boxtrap_trapping_chinchompa_black_n")
    val box_catching_black_e = find("hunting_boxtrap_trapping_chinchompa_black_e")
    val box_catching_black_s = find("hunting_boxtrap_trapping_chinchompa_black_s")
    val box_catching_black_w = find("hunting_boxtrap_trapping_chinchompa_black_w")
    val box_catching_ferret_n = find("hunting_boxtrap_trapping_ferret_n")
    val box_catching_ferret_e = find("hunting_boxtrap_trapping_ferret_e")
    val box_catching_ferret_s = find("hunting_boxtrap_trapping_ferret_s")
    val box_catching_ferret_w = find("hunting_boxtrap_trapping_ferret_w")
    val box_catching_jerboa_n = find("hunting_boxtrap_trapping_jerboa_n")
    val box_catching_jerboa_e = find("hunting_boxtrap_trapping_jerboa_e")
    val box_catching_jerboa_s = find("hunting_boxtrap_trapping_jerboa_s")
    val box_catching_jerboa_w = find("hunting_boxtrap_trapping_jerboa_w")
    val box_full_chinchompa = find("hunting_boxtrap_full_chinchompa")
    val box_full_big = find("hunting_boxtrap_full_chinchompa_big")
    val box_full_black = find("hunting_boxtrap_full_chinchompa_black")
    val box_full_ferret = find("hunting_boxtrap_full_ferret")
    val box_full_jerboa = find("hunting_boxtrap_full_jerboa")

    // Magic box.
    val magic_idle = find("hunting_imptrap_empty")
    val magic_catching = find("hunting_imptrap_trapping")
    val magic_failed = find("hunting_imptrap_failed")
    val magic_full = find("hunting_imptrap_full")

    // Deadfall, set on the boulders left lying about.
    val boulder = find("hunting_deadfall_boulder")
    val deadfall_idle = find("hunting_deadfall_trap")
    val deadfall_failing = find("hunting_deadfall_failing")
    val deadfall_catching_claw = find("hunting_deadfall_trapping_claw")
    val deadfall_catching_spike = find("hunting_deadfall_trapping_spike")
    val deadfall_catching_sabre = find("hunting_deadfall_trapping_sabre")
    val deadfall_catching_barbed = find("hunting_deadfall_trapping_barbed")
    val deadfall_catching_fennec = find("hunting_deadfall_trapping_fennec")
    val deadfall_full_claw = find("hunting_deadfall_full_claw")
    val deadfall_full_spike = find("hunting_deadfall_full_spike")
    val deadfall_full_sabre = find("hunting_deadfall_full_sabre")
    val deadfall_full_barbed = find("hunting_deadfall_full_barbed")
    val deadfall_full_fennec = find("hunting_deadfall_full_fennec")

    // Net traps, set on young trees.
    val tree_swamp = find("hunting_sapling_up_swamp")
    val tree_orange = find("hunting_sapling_up_orange")
    val tree_red = find("hunting_sapling_up_red")
    val tree_black = find("hunting_sapling_up_black")
    val tree_mountain = find("hunting_sapling_up_mountain")
    val tree_set_swamp = find("hunting_sapling_set_swamp")
    val tree_set_orange = find("hunting_sapling_set_orange")
    val tree_set_red = find("hunting_sapling_set_red")
    val tree_set_black = find("hunting_sapling_set_black")
    val tree_set_mountain = find("hunting_sapling_set_mountain")
    val net_set_swamp = find("hunting_sapling_net_set_swamp")
    val net_set_orange = find("hunting_sapling_net_set_orange")
    val net_set_red = find("hunting_sapling_net_set_red")
    val net_set_black = find("hunting_sapling_net_set_black")
    val net_set_mountain = find("hunting_sapling_net_set_mountain")
    val net_catching_swamp = find("hunting_sapling_catching_green")
    val net_catching_orange = find("hunting_sapling_catching_orange")
    val net_catching_red = find("hunting_sapling_catching_red")
    val net_catching_black = find("hunting_sapling_catching_black")
    val net_catching_mountain = find("hunting_sapling_catching_mountain")
    val net_failing_swamp = find("hunting_sapling_failing_swamp")
    val net_failing_orange = find("hunting_sapling_failing_orange")
    val net_failing_red = find("hunting_sapling_failing_red")
    val net_failing_black = find("hunting_sapling_failing_black")
    val net_failing_mountain = find("hunting_sapling_failing_mountain")
    val net_failed_swamp = find("hunting_sapling_failed_swamp")
    val net_failed_orange = find("hunting_sapling_failed_orange")
    val net_failed_red = find("hunting_sapling_failed_red")
    val net_failed_black = find("hunting_sapling_failed_black")
    val net_failed_mountain = find("hunting_sapling_failed_mountain")
    val net_full_swamp = find("hunting_sapling_full_green")
    val net_full_orange = find("hunting_sapling_full_orange")
    val net_full_red = find("hunting_sapling_full_red")
    val net_full_black = find("hunting_sapling_full_black")
    val net_full_mountain = find("hunting_sapling_full_mountain")

    // Pitfalls: the interactive parts of a pit (what its player sees is decided by a varbit).
    val pit_empty = find("hunting_pitfall_invis_empty")
    val pit_set = find("hunting_pitfall_invis_set")
    val pit_collapsed = find("hunting_pitfall_invis_catching")
    val pit_full_kyatt = find("hunter_pitfall_full_kyatt")
    val pit_full_larupia = find("hunter_pitfall_full_larupia")
    val pit_full_graahk = find("hunter_pitfall_full_graahk")
    val pit_full_any = find("hunter_pitfall_full")

    // Birdhouse spots, as the player sees them.
    val birdhouse_space = find("birdhouse_not_built")
}

/** The server-only varps above. The client's own birdhouse varps are already in the cache. */
internal object HunterVarpBuilder : VarpBuilder() {
    init {
        for (spot in listOf("a", "b", "c", "d")) {
            build("hunter_birdhouse_${spot}_filled") {}
            build("hunter_birdhouse_${spot}_seeds") {}
        }
    }
}
