package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.type.refs.synth.SynthReferences

typealias toa_synths = ToaSynths

/**
 * The raid's sound effects, ids from TSPS (OpenRune captures). Most have no Jagex name in this
 * cache's symbols, so the `toa_*` names were added to `synth.sym` by hand (needs `packCache`).
 */
object ToaSynths : SynthReferences() {
    // Akkha
    val akkha_style = find("toa_akkha_style_change")
    val akkha_symbol = find("toa_akkha_symbol")
    val akkha_quadrant = find("toa_akkha_quadrant")
    val akkha_trail = find("toa_akkha_trail_orb")
    val akkha_burn = find("toa_akkha_burn")
    val akkha_ranged = find("toa_akkha_ranged")
    val akkha_magic = find("toa_akkha_magic")

    // Zebak
    val zebak_magic = find("toa_zebak_magic")
    val zebak_ranged = find("toa_zebak_ranged")
    val zebak_enrage = find("toa_zebak_enrage")
    val zebak_barrage = find("blood_barrage_impact")
    val zebak_jugs = find("toa_zebak_jugs")
    val zebak_roar_push = find("toa_zebak_roar_push")
    val zebak_rumbling = find("toa_zebak_rumbling")
    val zebak_wave_hit = find("toa_zebak_wave_hit")

    // Ba-Ba
    val baba_slam = find("darkbow_shadow_impact")
    val baba_rubble = find("toa_baba_rubble")
    val baba_throw_rock = find("toa_baba_throw_rock")
    val baba_rock_impact = find("toa_baba_rock_impact")
    val baba_slip = find("toa_baba_slip")
    val baba_boulders = find("toa_baba_boulders")
    val baba_flyback = find("toa_baba_flyback")
    val baba_collision = find("toa_baba_collision")

    // The Wardens
    val wardens_start = find("toa_wardens_start")
    val wardens_trap_land = find("toa_wardens_trap_land")
    val wardens_elidinis_ball = find("toa_wardens_elidinis_ball")
    val wardens_tumeken_ball = find("toa_wardens_tumeken_ball")
    val wardens_ball_land = find("toa_wardens_ball_land")
    val wardens_entombed = find("toa_wardens_entombed")
    val wardens_core_return = find("toa_wardens_core_return")
    val wardens_revive = find("selfheal")
    val wardens_thunder = find("toa_wardens_thunder")
    val wardens_skull_send = find("toa_wardens_skull_send")
    val wardens_skull_fail = find("toa_wardens_skull_fail")
    val wardens_last_phase = find("toa_wardens_last_phase")
    val wardens_divine_melee = find("windblast_hit")
    val wardens_divine_magic = find("waterblast_hit")
    val wardens_divine_ranged = find("earthblast_hit")

    // Puzzle rooms
    val het_beam = find("toa_het_beam")
    val het_roof = find("toa_het_roof")
    val het_walls = find("toa_het_walls")
    val het_end = find("toa_het_end")
    val het_statue = find("found_gem")
    val het_pick = find("pick")
    val het_mirror_rotate = find("toa_het_mirror_rotate")
    val het_push = find("toa_het_push")
    val het_place = find("put_down")
    val het_wall_mined = find("toa_het_wall_mined")
    val het_seal_hit = find("toa_het_seal_hit")
    val apmeken_sight = find("toa_apmeken_sight")
    val apmeken_repair = find("toa_apmeken_repair")
    val apmeken_vent = find("toa_apmeken_vent")
    val water_pour = find("toa_water_pour")
    val take = find("pick2")
    val crondis_fill = find("toa_crondis_fill")
    val crondis_empty = find("toa_crondis_empty")
    val crondis_water_palm = find("toa_crondis_water_palm")
    val crondis_palm_grow = find("toa_crondis_palm_grow")
    val crondis_palm_shrink = find("toa_crondis_palm_shrink")
    val puzzle_done = het_statue
    val scabaras_flip = find("toa_scabaras_flip")
    val scabaras_pressure = find("toa_scabaras_pressure")
    val scabaras_rockfall = find("toa_scabaras_rockfall")
    val scabaras_rumbling = find("toa_scabaras_rumbling")
    val scabaras_tile = find("toa_scabaras_tile")
    val scabaras_match_fail = find("toa_scabaras_match_fail")
    val scabaras_sum_fail = find("toa_scabaras_sum_fail")
    val scabaras_button = find("toa_scabaras_button")
    val scabaras_memory_fail = find("toa_scabaras_memory_fail")
    val scabaras_tablet = find("toa_scabaras_tablet")
    val scabaras_obelisk_hit = find("toa_scabaras_obelisk_hit")
}
