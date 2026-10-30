package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.type.refs.loc.LocReferences

/**
 * The cache's own objects that open and close the dungeons: the entrance objects copied onto the
 * isle, the objects inside the dungeons that lead back out, and the links inside a dungeon group.
 */
object DungeonLocs : LocReferences() {
    // Entrances placed on the isle. Each one is the type used at the real entrance, or the
    // nearest object with the same look where the real one is a hidden or multi-state loc.
    val taverley_entrance = find("ladder_outside_to_underground")
    val edgeville_entrance = find("trapdoor_open")
    val varrock_entrance = find("manholeopen")
    val revenant_entrance = find("wild_cave_entrance_high")
    val stronghold_entrance = find("slayer_cave_entrance")
    val brimhaven_entrance = find("dungeon_tree_open")
    val karamja_entrance = find("volcano_entrance")
    val kraken_entrance = find("slayer_cave_kraken_maincave_entrance")
    val smoke_entrance = find("smokedevil_cave_entrance")
    val catacombs_entrance = find("zeah_kourend_statue_plinth")
    val forthos_entrance = find("hosdun_entrance_ladder_hole")
    val ham_entrance = find("osf_trapdoor_open")
    val slayer_basement_entrance = find("slayer_tower_dungeon_laddertop")
    val crash_site_entrance = find("mm2_cavern_entrance")
    val fremennik_entrance = find("slayer_dungeon_entrance")
    val kalphite_entrance = find("kalphite_burrow_entrance_withrope")
    val mos_entrance = find("harmless_cave_entrance_left")
    val lizardman_entrance = find("lizardman_cave_entrance")
    val karuulm_controls = find("brimstone_dungeon_entry")
    // The open state of the hole (the other state asks for a rope the isle does not make you
    // bring).
    val godwars_entrance = find("godwars_entrance_groundhole02")

    // Already on the isle's map, so the cache's own object is used where it stands.
    val isle_dungeon_entrance = find("sw_dungeon_entrance")

    // Ways out. The generic ladder is not handled by object: the dungeon-ladder script publishes an
    // event first, and [DungeonScript] redirects it.
    val generic_ladder = find("ladder_from_cellar")
    val revenant_exit_low = find("wild_cave_exit_low")
    val revenant_exit_high = find("wild_cave_exit_high")
    val stronghold_exit_left = find("slayer_cave_exit_left")
    val stronghold_exit_right = find("slayer_cave_exit_right")
    val varrock_manhole_ladder = find("fai_varrock_manhole_ladder")
    val brimhaven_exit = find("karam_dungeon_exit")
    val brimhaven_backdoor_exit = find("brimhaven_backdoor_exit")
    val karamja_rope = find("climbing_rope2")
    val crandor_rope = find("dragon_slayer_qip_climbing_rope")
    val kraken_exit = find("slayer_cave_kraken_maincave_exit")
    val smoke_exit = find("slayer_cave_passage_smokedevil_exit")
    val catacombs_exit_01 = find("zeah_cata_exit_01")
    val catacombs_exit_02 = find("zeah_cata_exit_02")
    val catacombs_exit_03 = find("zeah_cata_exit_03")
    val catacombs_exit_04 = find("zeah_cata_exit_04")
    val catacombs_exit_giants_den = find("zeah_cata_exit_giants_den")
    val catacombs_exit_vine = find("cata_exit")
    val forthos_exit_ladder = find("hosdun_entrance_ladder")
    val forthos_exit_stairs = find("hosdun_entrance_spiral_stairs")
    val ham_exit = find("osf_ham_ladder")
    val slayer_basement_exit = find("slayer_tower_dungeon_ladderbottom")
    val crash_site_exit = find("mm2_cave_boss_exit")
    val fremennik_exit = find("slayer_dungeon_exit")
    val kalphite_rope = find("kalphite_burrow_exit")
    val mos_exit_left = find("harmless_dungeon_exit_left")
    val mos_exit_right = find("harmless_dungeon_exit_right")
    val mos_stairs = find("harmless_black_spiral_stairs")
    val isle_dungeon_exit = find("sw_dungeon_exit")
    val lizardman_exit = find("lizardman_cave_exit")
    val karuulm_exit = find("brimstone_dungeon_exit")
    val godwars_rope = find("godwars_entrance_rope_bottom")

    // The pieces of Karuulm's elevator, copied round its controls.
    val elevator_pillar = find("brimstone_elevator_pillar")
    val elevator_wall = find("brimstone_elevator_control_wall")
    val elevator_post = find("brimstone_elevator_post")
    val elevator_centre = find("brimstone_elevator_central_tile")
    val elevator_side = find("brimstone_elevator_side1_tile")
    val elevator_side_west = find("brimstone_elevator_side2_tile")
    val elevator_corner = find("brimstone_elevator_corner_tile")

    // The cave between the Karamja Dungeon and Mor Ul Rek, one object at each end.
    val mor_ul_rek_in = find("tzhaar_karamjadungeon_wall_entrance")
    val mor_ul_rek_out = find("tzhaar_karamjadungeon_wall_exit")

    // Brimhaven's two stairs between its levels, and the north vine.
    val brimhaven_stairs_1_up = find("karam_dungeon_cavestairs")
    val brimhaven_stairs_1_down = find("karam_dungeon_cavestairs_lev2_down")
    val brimhaven_stairs_2_up = find("karam_dungeon_cavestairs2")
    val brimhaven_stairs_2_down = find("karam_dungeon_cavestairs_lev2_down2")
    val brimhaven_vine_up = find("karam_cavewall_vine_climbable_bottom")
    val brimhaven_vine_down = find("karam_cavewall_vine_climbable_top")

    // Karuulm's stairs between its levels and the pipe that cuts across the first chambers.
    val karuulm_stairs = find("karuulm_wall_stairs")
    val karuulm_stairs_top = find("karuulm_wall_stairs_top")
    val karuulm_pipe = find("karuulm_dungeon_shortcut_pipe_active")
}
