package org.rsmod.content.areas.misc.isleofsouls.cox

import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.inv.InvScope

typealias cox_interfaces = CoxInterfaces

typealias cox_components = CoxComponents

typealias cox_invs = CoxInvs

typealias cox_varbits = CoxVarBits

typealias cox_varps = CoxVarps

typealias cox_locs = CoxLocs

typealias cox_npcs = CoxNpcs

typealias cox_objs = CoxObjs

typealias cox_seqs = CoxSeqs

typealias cox_spots = CoxSpots

object CoxInterfaces : InterfaceReferences() {
    val party_list = find("raids_lobby_partylist")
    val party_details = find("raids_lobby_partydetails")
    val sidepanel = find("raids_sidepanel")
    val overlay = find("raids_overlay")
    val rewards = find("raids_rewards")
    val storage_private = find("raids_storage_private")
    val storage_shared = find("raids_storage_shared")
    val storage_side = find("raids_storage_side")
}

object CoxComponents : ComponentReferences() {
    /**
     * The board's Refresh (child 0) and Make party / My party (child 1) resume this (enum 1538).
     */
    val list_buttons = find("raids_lobby_partylist:contents")

    /** Each of the 40 rows resumes this with the row as the child (script 1568). */
    val list_rows = find("raids_lobby_partylist:list")

    /** Every party page button resumes a child of this (enum 1539). */
    val details_buttons = find("raids_lobby_partydetails:contents")

    /** The leader's "Start raid" (script 1540, op 1). */
    val sidepanel_start = find("raids_sidepanel:start")

    val rewards_items = find("raids_rewards:items")

    val private_items = find("raids_storage_private:items")
    val private_to_shared = find("raids_storage_private:shared")
    val private_withdraw_all = find("raids_storage_private:withdraw")
    val private_bank_all = find("raids_storage_private:bank")
    val shared_items = find("raids_storage_shared:items")
    val shared_to_private = find("raids_storage_shared:private")
    val side_items = find("raids_storage_side:items")
    val side_dismiss = find("raids_storage_side:dismiss")
}

object CoxInvs : InvReferences() {
    val rewards = find("raids_rewards")
    val shared = find("raids_sharedstorage")
    val private = find("raids_privatestorage")
}

/** The loot and the private storage wait through a logout. */
internal object CoxInvEdits : InvEditor() {
    init {
        edit(cox_invs.rewards) {
            scope = InvScope.Perm
            protect = false
        }
        edit(cox_invs.private) {
            scope = InvScope.Perm
            protect = false
        }
    }
}

object CoxVarBits : VarBitReferences() {
    // The party page.
    val lobby_size = find("raids_lobby_partysize")
    val lobby_combat = find("raids_lobby_mincombat")
    val lobby_total = find("raids_lobby_minskilltotal")
    val challenge_mode = find("raids_challenge_mode")
    val scaling = find("raids_scaling")
    val channel_member = find("raids_client_channelmember")

    // The side panel and overlay.
    val is_leader = find("raids_client_isleader")
    val party_size = find("raids_client_partysize")
    val party_size_scaled = find("raids_client_partysize_scaled")
    val highest_combat = find("raids_client_highestcombat")
    val progress = find("raids_client_progress")
    val party_score = find("raids_client_partyscore")
    val timer = find("raids_timer")
    val in_dungeon = find("raids_client_indungeon")
    val map_pool = find("raids_map_pool_selected")

    // Storage and rewards.
    val using_private = find("raids_usingprivatestorage")
    val shared_warning_dismissed = find("raids_sharedstorage_warning_dismissed")
    val reward_indicator = find("raids_client_rewardindicator")
    val needs_reward_book = find("raids_client_needsrewardbook")
    val itemloss_warning = find("raids_itemloss_warning")
}

object CoxVarps : VarpReferences() {
    /** -1 without a party: the board's button then reads "Make party" (script 1565). */
    val party_holder = find("raids_party_groupholder")

    /** The raider's own points on the overlay. */
    val player_score = find("raids_playerscore")

    val completions = find("total_completed_xericchambers")
    val challenge_completions = find("total_completed_xericchambers_challenge")
    val clog_completions = find("clog_kc_chambers_of_xeric_completions")
    val clog_challenge_completions = find("clog_kc_chambers_of_xeric_cm_completions")
}

object CoxLocs : LocReferences() {
    // Mount Quidamortem.
    val recruiting_board = find("raids_party_recruitment")
    val entrance = find("raids_entrance_steps")
    val lobby_storage = find("raids_storage_lobby")
    val lobby_bank = find("tox_bank_chest")
    val score_board = find("raids_challenge_scores")
    val minecart = find("lovakengj_minecart")

    /** The isle's way to the mountain (Corporeal Beast's private portal, unused on the isle). */
    val portal = find("corp_privateportal_outside")

    /** The mountain's way back to the isle. */
    val portal_back = find("corp_privateportal_inside")

    // Getting around inside.
    val passage = find("raids_doorway")
    val exit_steps = find("raids_exit_steps")
    val exit_steps_multi = find("raids_exit_steps_multi")
    val hole_down = find("raids_descentto2")
    val hole_down_lower = find("raids_descentto1")
    val boss_hole = find("raids_bossentrance")
    val rope_up = find("raids_ascentto3")
    val rope_up_lower = find("raids_ascentto2")
    val boss_rope = find("raids_bossexit")
    val inside_bank = find("raids_bank_chest_lobby_working")
    val energy_well = find("raids_energy_pool")

    // Exit crystals.
    val crystal_green = find("raids_blockage_green")
    val crystal_orange = find("raids_blockage_orange")
    val crystal_purple = find("raids_blockage_purple")
    val crystal_purple_small = find("raids_blockage_purple_small")

    // Storage.
    val storage_hotspot = find("raids_storage_hotspot")
    val storage_small = find("raids_storage_1")
    val storage_medium = find("raids_storage_2")
    val storage_large = find("raids_storage_3")
    val storage_massive = find("raids_storage_4")

    // Resources.
    /** A plain fire, lit from kindling; the Cooking module cooks on it. */
    val fire = find("fire")
    val fishing_spot = find("raids_floor_water_edge1_fishing")
    val sapling = find("raids_woodsource_roots")
    val sapling_depleted = find("raids_woodsource_roots_depleted")
    val patch_empty = find("raids_patch_empty")
    val weeds = find("raids_weeds")
    val gourd_tree = find("raids_gourd_tree")
    val geyser = find("raids_geyser")
    val old_tools = find("raids_farming_tools")
    val patch_golpar_seed = find("raids_patch_golpar_seed")
    val patch_buchu_seed = find("raids_patch_buchu_seed")
    val patch_noxifer_seed = find("raids_patch_noxifer_seed")
    val patch_golpar_1 = find("raids_patch_golpar_growth1")
    val patch_buchu_1 = find("raids_patch_buchu_growth1")
    val patch_noxifer_1 = find("raids_patch_noxifer_growth1")
    val patch_golpar_2 = find("raids_patch_golpar_growth2")
    val patch_buchu_2 = find("raids_patch_buchu_growth2")
    val patch_noxifer_2 = find("raids_patch_noxifer_growth2")
    val patch_golpar_3 = find("raids_patch_golpar_growth3")
    val patch_buchu_3 = find("raids_patch_buchu_growth3")
    val patch_noxifer_3 = find("raids_patch_noxifer_growth3")
    val patch_golpar_grown = find("raids_patch_golpar_fullygrown")
    val patch_buchu_grown = find("raids_patch_buchu_fullygrown")
    val patch_noxifer_grown = find("raids_patch_noxifer_fullygrown")

    // Tools lying in rooms.
    val icedemon_axe = find("raids_icedemon_axe")
    val icedemon_tinderbox = find("raids_icedemon_tinderbox")
    val crabs_hammer = find("raids_lasercrabs_hammer")
    val guardians_pickaxe = find("raids_stoneguardians_pickaxe")

    // Puzzles.
    val brazier_unlit = find("raids_icedemon_brazier_unlit")
    val brazier_lit = find("raids_icedemon_brazier_lit")
    val thieving_chest = find("raids_thievingchest_closed")
    val thieving_chest_open = find("raids_thievingchest_open")
    val trough_empty = find("raids_thievingchest_foodtrough_empty")
    val trough_full = find("raids_thievingchest_foodtrough_full")
    val creature_keeper = find("raids_thievingchest_creaturekeeper")
    val tightrope = find("raids_tightrope_end")
    val tightrope_barrier = find("raids_tightrope_barrier")
    val keystone = find("raids_tightrope_keystone_loc")
    val crab_big_1 = find("raids_lasercrabs_bigcrystal_1")
    val crab_big_2 = find("raids_lasercrabs_bigcrystal_2")
    val crab_big_3 = find("raids_lasercrabs_bigcrystal_3")
    val crab_big_4 = find("raids_lasercrabs_bigcrystal_4")
    val crab_big_5 = find("raids_lasercrabs_bigcrystal_5")
    val crab_small_black = find("raids_lasercrabs_smallcrystal_black")
    val crab_small_cyan = find("raids_lasercrabs_smallcrystal_cyan")
    val crab_small_magenta = find("raids_lasercrabs_smallcrystal_magenta")
    val crab_small_yellow = find("raids_lasercrabs_smallcrystal_yellow")
    val crab_small_white = find("raids_lasercrabs_smallcrystal_white")

    // Combat rooms.
    val tekton_fire = find("raids_tekton_fire")
    val tekton_anvil = find("raids_tekton_anvil")
    val tekton_walkblocker = find("raids_tekton_walkblocker")
    val vasa_fire = find("raids_vasanistirio_fire")
    val vasa_crystal_on = find("raids_vasanistirio_crystal_on")
    val vasa_crystal_off = find("raids_vasanistirio_crystal_off")
    val mutt_tendrils = find("raids_dogodile_tendrils_entrance")
    val shaman_tendrils = find("raids_shaman_tendrils_entrance")
    val vespula_tendrils = find("raids_vespula_tendrils_entrance")
    val vespula_herb = find("raids_vespula_herb")
    val vespula_herb_empty = find("raids_vespula_herb_empty")
    val vespula_boil = find("raids_vespula_boil_blocking")
    val vespula_boil_burst = find("raids_vespula_boil_burst")
    val mystic_mark = find("raids_skeletalmystics_symbol")
    val mystic_portal_start = find("raids_mystics_portal_start")
    val mystic_portal_end = find("raids_mystics_portal_end")
    val invisible_blocker = find("invisible_type8_blocking")
    val corridor_rocks = find("raids_corridor_rocks")
    val corridor_rocks_cleared = find("raids_corridor_rocks_cleared")
    val corridor_roots = find("raids_corridor_roots")
    val corridor_roots_cleared = find("raids_corridor_roots_cleared")

    // The Great Olm.
    val olm_barrier = find("raids_olm_barrier")
    val olm_head_cave = find("olm_head_cave")
    val olm_left_cave = find("olm_hand_left_cave")
    val olm_right_cave = find("olm_hand_right_cave")
    val olm_acid_pool = find("olm_acid_pool")
    val olm_crystal_small = find("olm_crystal_attack_small")
    val olm_crystal_large = find("olm_crystal_attack_large")
    val olm_bomb = find("olm_crystal_bomb")
    val reward_chest = find("raids_reward_chest")
    val reward_crystal = find("raids_reward_crystal")
    val lootbeam_basic = find("raids_reward_lootbeam_basic")
    val lootbeam_special = find("raids_reward_lootbeam_special")
    val lootbeam_kit = find("raids_reward_lootbeam_kit")
    val lootbeam_dust = find("raids_reward_lootbeam_dust")
}

object CoxNpcs : NpcReferences() {
    val captain_rimor = find("raids_temple_captain")

    val scavenger_a = find("raids_scavenger_beast_a")
    val scavenger_b = find("raids_scavenger_beast_b")
    val bat_types = (0..6).map { find("raids_bat_$it") }
    val fishing_snake = find("raids_fishing_snake")

    val tekton_waiting = find("raids_tekton_waiting")
    val tekton_walking = find("raids_tekton_walking_standard")
    val tekton_fighting = find("raids_tekton_fighting_standard")
    val tekton_walking_enraged = find("raids_tekton_walking_enraged")
    val tekton_fighting_enraged = find("raids_tekton_fighting_enraged")
    val tekton_hammering = find("raids_tekton_hammering")

    val vasa_dormant = find("raids_vasanistirio_dormant")
    val vasa_walking = find("raids_vasanistirio_walking")
    val vasa_healing = find("raids_vasanistirio_healing")
    val vasa_crystal = find("raids_vasanistirio_crystal")

    val vanguard_dormant = find("raids_vanguard_dormant")
    val vanguard_walking = find("raids_vanguard_walking")
    val vanguard_melee = find("raids_vanguard_melee")
    val vanguard_ranged = find("raids_vanguard_ranged")
    val vanguard_magic = find("raids_vanguard_magic")

    val vespula_flying = find("raids_vespula_flying")
    val vespula_enraged = find("raids_vespula_enraged")
    val vespula_walking = find("raids_vespula_walking")
    val vespula_portal = find("raids_vespula_portal")
    val grub_healthy = find("raids_vespula_caterpillar_healthy")
    val grub_sickly = find("raids_vespula_caterpillar_sickly")
    val grub_infected = find("raids_vespula_caterpillar_infected")
    val grub_dead = find("raids_vespula_caterpillar_dead")
    val soldier_flying = find("raids_vespula_vespine_flying")
    val soldier_walking = find("raids_vespula_vespine_walking")

    val guardian_left = find("raids_stoneguardians_left")
    val guardian_right = find("raids_stoneguardians_right")
    val guardian_left_dead = find("raids_stoneguardians_left_dead")
    val guardian_right_dead = find("raids_stoneguardians_right_dead")

    val shaman_a = find("raids_lizardshaman_a")
    val shaman_b = find("raids_lizardshaman_b")
    val shaman_spawn = find("raids_lizardshaman_blocker")

    val mystic_a = find("raids_skeletonmystic_a")
    val mystic_b = find("raids_skeletonmystic_b")
    val mystic_c = find("raids_skeletonmystic_c")

    val mutt_submerged = find("raids_dogodile_submerged")
    val mutt_small = find("raids_dogodile_junior")
    val mutt_large = find("raids_dogodile")
    val meat_tree = find("raids_dogodile_meat_tree")

    val tightrope_ranger = find("raids_tightrope_ranger")
    val tightrope_mage = find("raids_tightrope_mage")

    val crab_grey = find("raids_lasercrabs_crab_grey")
    val crab_red = find("raids_lasercrabs_crab_red")
    val crab_green = find("raids_lasercrabs_crab_green")
    val crab_blue = find("raids_lasercrabs_crab_blue")
    val crab_orb_white = find("raids_lasercrabs_energy_white")
    val crab_orb_red = find("raids_lasercrabs_energy_red")
    val crab_orb_green = find("raids_lasercrabs_energy_green")
    val crab_orb_blue = find("raids_lasercrabs_energy_blue")

    val ice_demon_frozen = find("raids_icedemon_noncombat")
    val ice_demon = find("raids_icedemon_combat")
    val icefiend = find("raids_icefiend")

    val thieving_beast = find("raids_thievingchest_beast_active")
    val thieving_beast_sleeping = find("raids_thievingchest_beast_sleeping")

    val olm_head_spawning = find("olm_head_spawning")
    val olm_head = find("olm_head")
    val olm_left_spawning = find("olm_hand_left_spawning")
    val olm_left = find("olm_hand_left")
    val olm_left_dying = find("olm_hand_left_dying")
    val olm_right_spawning = find("olm_hand_right_spawning")
    val olm_right = find("olm_hand_right")
    val olm_right_dying = find("olm_hand_right_dying")
    val olm_firewall = find("olm_firewall_npc")
    val olmlet = find("raids_olm_pet")
}

object CoxObjs : ObjReferences() {
    val kindling = find("raids_wood")
    val gourd_vial = find("raids_vial_empty")
    val gourd_water = find("raids_vial_water")
    val cave_worms = find("raids_fishingbait")
    val burnt_fish = find("raids_fish_burnt")
    val burnt_bat = find("raids_bat_burnt")
    val raw_fish = (0..6).map { find("raids_fish${it}_raw") }
    val cooked_fish = (0..6).map { find("raids_fish${it}_cooked") }
    val raw_bat = (0..6).map { find("raids_bat${it}_raw") }
    val cooked_bat = (0..6).map { find("raids_bat${it}_cooked") }
    val keystone = find("raids_tightrope_keystone")
    val grubs = find("raids_thievingchest_grubs")
    val vespula_herb = find("raids_vespula_herb")
    val plank = find("raids_plank")
    val grimy_golpar = find("raids_grimy_golpar")
    val golpar = find("raids_golpar")
    val golpar_seed = find("raids_seed_golpar")
    val grimy_buchu = find("raids_grimy_buchuleaf")
    val buchu = find("raids_buchuleaf")
    val buchu_seed = find("raids_seed_buchuleaf")
    val grimy_noxifer = find("raids_grimy_noxifer")
    val noxifer = find("raids_noxifer")
    val noxifer_seed = find("raids_seed_noxifer")
    val stinkhorn = find("raids_stinkhorn_mushroom")
    val endarkened_juice = find("raids_endarkened_juice")
    val cicely = find("raids_cicely")

    // Tools.
    val bronze_axe = find("bronze_axe")
    val iron_pickaxe = find("iron_pickaxe")
    val hammer = find("hammer")
    val tinderbox = find("tinderbox")
    val rake = find("rake")
    val spade = find("spade")
    val seed_dibber = find("dibber")
    val fishing_rod = find("fishing_rod")
    val butterfly_net = find("hunting_butterfly_net")
    val magic_butterfly_net = find("ii_magic_butterfly_net")
    val dragon_warhammer = find("dragon_warhammer")
    val elder_maul = find("elder_maul")

    // Rewards.
    val dexterous_scroll = find("raids_prayerscroll")
    val arcane_scroll = find("raids_prayerscroll_augury")
    val twisted_buckler = find("twisted_buckler")
    val dragon_hunter_crossbow = find("dragonhunter_xbow")
    val dinhs_bulwark = find("dinhs_bulwark")
    val ancestral_hat = find("ancestral_hat")
    val ancestral_top = find("ancestral_robe_top")
    val ancestral_bottom = find("ancestral_robe_bottom")
    val dragon_claws = find("dragon_claws")
    val kodai_insignia = find("kodai_insignia")
    val twisted_bow = find("twisted_bow")
    val olmlet = find("olmpet")
    val dark_journal = find("raids_reward_book")
    val ancient_tablet = find("raids_spellunlock_tablet")
    val torn_scroll = find("raids_prayerscroll_preserve")
    val dark_relic = find("raids_ancient_relic")
    val twisted_kit = find("ancestral_robes_twisted_kit")
    val metamorphic_dust = find("raids_challenge_morph")
    val xeric_talisman = find("xeric_talisman")
    val xeric_talisman_empty = find("xeric_talisman_empty")
    val lizardman_fang = find("lizardman_fang")
    val lockpick = find("lockpick")
}

object CoxSeqs : SeqReferences() {
    val climb_down = find("human_pickupfloor")
    val climb_up = find("human_reachforladder")
    val fishing = find("human_fish_onspot")
    val cooking = find("human_firecooking")
    val woodcutting = find("human_woodcutting_bronze_axe")
    val firemaking = find("human_createfire")
    val light_brazier = find("human_light_torch")
    val herbing = find("human_herbing_vial")
    val net = find("ii_human_net_throw_oldnet")
    val rake = find("farming_raking")
    val dibbing = find("farming_seed_dibbing")
    val picking = find("picking_low")
    val pickup_table = find("human_pickuptable")
    val hammer = find("human_hammer_hit")
    val open_chest = find("human_openchest")
    val crystal_smash = find("raids_crystal_smashing")

    val tekton_rise = find("tekton_avil_transition_to_idle")
    val tekton_rise_enraged = find("tekton_avil_transition_to_idle_enraged")
    val tekton_stance = find("tekton_idle_transition_to_ready")
    val tekton_stance_enraged = find("tekton_idle_transition_to_ready_enraged")
    val tekton_leave = find("tekton_ready_transition_to_idle")
    val tekton_leave_enraged = find("tekton_ready_transition_to_idle_enraged")
    val tekton_to_anvil = find("tekton_idle_transition_to_anvil")

    val vasa_spawn = find("vasa_spawn")
    val vasa_stun_spawn = find("vasa_stun_spawn")
    val vasa_attack = find("vasa_attack")
    val vasa_heal_spawn = find("vasa_heal_spawn")
    val vasa_healed = find("vasa_healed")
    val vasa_death = find("vasa_death")

    val vanguard_spawn = find("vanguard_spawn")
    val vanguard_heal = find("vanguard_heal")
    val vanguard_melee = find("vanguard_attack_melee")
    val vanguard_ranged = find("vanguard_attack_ranged")
    val vanguard_magic = find("vanguard_attack_magic")
    val vanguard_rise_melee = find("vanguard_idle_basic_idle_melee_transition")
    val vanguard_fall_melee = find("vanguard_idle_melee_idle_basic_transition")
    val vanguard_rise_ranged = find("vanguard_idle_basic_idle_ranged_transition")
    val vanguard_fall_ranged = find("vanguard_idle_ranged_idle_basic_transition")
    val vanguard_rise_magic = find("vanguard_idle_basic_idle_magic_transition")
    val vanguard_fall_magic = find("vanguard_idle_magic_idle_basic_transition")

    val vespula_sting = find("vespula_attack_melee_flying")
    val vespula_death = find("vespula_death_flying")
    val vespine_hatch = find("vespine_hatch")

    val guardian_attack = find("dragon_halberd_special_attack")
    val guardian_stomp = find("emote_stampfeet")
    val guardian_sweep = find("human_scythe_sweep")
    val knocked_back = find("eadgar_knockout")
    val guardian_crumble = find("human_death")

    val shaman_ranged = find("shay_lizard_warrior_attack_ranged")
    val shaman_melee = find("shayzien_lizard_boss_tail_swipe")
    val shaman_jump = find("shayzien_lizard_boss_jump")
    val shaman_land = find("shayzien_lizard_boss_land")
    val shaman_death = find("shay_lizard_warrior_death")
    val shaman_spawn_explode = find("shayzien_lizard_boss_hands_to_head")

    val mystic_cast = find("skeleton_update_mage_casting_swansong")
    val mystic_melee = find("skeleton_update_attack_weapon")
    val mystic_death = find("skeleton_update_death")

    val mutt_emerge = find("dohgadyle_emerge")
    val mutt_bite = find("dohgadyle_bite")
    val mutt_ranged = find("dohgadyle_bark")
    val mutt_magic = find("dohgadyle_lazor")
    val mutt_stomp = find("dohgadyle_slam")

    val demon_cast = find("demon_casting")
    val demon_death = find("demon_death")

    val crab_smash = find("human_hammer_hit")
    val crab_bite = find("horror_crab_pincer_attack")
    val scavenger_attack = find("skavid_attack")
    val scavenger_death = find("skavid_death")
    val mage_cast = find("human_caststrike_staff")
    val ranger_shoot = find("human_bow")

    val olm_head_spawn = find("olm_head_spawn")
    val olm_head_idle_front = find("olm_head_idle_front")
    val olm_head_idle_left = find("olm_head_idle_left")
    val olm_head_idle_right = find("olm_head_idle_right")
    val olm_head_attack_front = find("olm_head_attack_acid_front")
    val olm_head_attack_left = find("olm_head_attack_acid_left")
    val olm_head_attack_right = find("olm_head_attack_acid_right")
    val olm_head_attack_front_enraged = find("olm_head_attack_front_enraged")
    val olm_head_death = find("olm_head_death_front")
    val olm_head_spawn_enraged = find("olm_head_spawn_enraged")
    val olm_right_spawn = find("olm_hand_right_spawn")
    val olm_right_death = find("olm_hand_right_death")
    val olm_left_spawn = find("olm_hand_left_spawn")
    val olm_left_death = find("olm_hand_left_death")
    val olm_left_cast_stun = find("olm_hand_left_cast_stun")
    val olm_left_cast_heal = find("olm_hand_left_cast_heal")
    val olm_left_cast_shock = find("olm_hand_left_cast_earthshock")
    val olm_left_cast_quake = find("olm_hand_left_cast_earthquake")
    val olm_left_clench = find("olm_hand_left_gripping_idle")
}

object CoxSpots : SpotanimReferences() {
    val stunned = find("stunned_thieving")
    val smoke = find("smokepuff_large")
    val fire_cast = find("fireblast_casting")
    val fire_travel = find("fireblast_travel")
    val fire_impact = find("fireblast_impact")
    val splash = find("failedspell_impact")
    val arrow_launch = find("rune_arrow_launch")
    val arrow_travel = find("rune_arrow_travel")
    val curse_travel = find("curse_travel")
    val curse_impact = find("curse_impact")
    val mystic_purple = find("skeleton_big_casting_purple")
    val mystic_red = find("skeleton_big_casting_red")
    val meteor = find("wild_falloff_meteor_blast")
    val rock_splash = find("castlewars_catapult_splash")
    val rockfall = find("motherlode_rockfall")
    val spit = find("lizardman_spit")
    val acid = find("lizardshaman_spit_acid")
    val acid_splash = find("lizardshaman_acid_splash")
    val spawn_explode = find("lizardshaman_spawn_explode")
    val dragon_fire = find("dragon_ranged_fire_attack")
    val ice_travel = find("ice_burst_travel")
    val ice_impact = find("ice_blitz_impact")
    val iceball_travel = find("raids_icedemon_iceball_travel")
    val iceball_hit = find("raids_icedemon_iceball_hit")
    val crab_shatter = find("raids_lasercrabs_shattercrystal")
    val vasa_magic_travel = find("raids_vasanistirio_magic_travel")
    val vasa_magic_impact = find("raids_vasanistirio_magic_impact")
    val vasa_range_travel = find("raids_vasanistirio_range_travel")
    val vasa_range_impact = find("raids_vasanistirio_range_impact")
    val vanguard_magic = find("raids_vanguard_magic")
    val vanguard_range = find("raids_vanguard_range_0")
    val swirl_low = find("raids_vanguard_swirl_low_health_spotanim")
    val swirl_high = find("raids_vanguard_swirl_high_health_spotanim")
    val vespula_poison = find("raids_vespula_poison")
    val vespine_hatch = find("raids_vespula_vespine_hatch")
    val portal_attack = find("raids_vespula_portal_attack")
    val vespine_heal = find("raids_vespula_vespine_healportal")
    val zeds = find("troll_zeds")
    val wind_impact = find("windbolt_impact")

    val olm_firebreath = find("olm_firebreath_travel")
    val olm_range_proj = find("olm_generic_range_proj")
    val olm_mage_proj = find("olm_weak_mage_proj")
    val olm_mage_impact = find("olm_weak_mage_impact")
    val olm_range_sphere = find("olm_weak_range_proj")
    val olm_range_sphere_impact = find("olm_weak_range_impact")
    val olm_melee_sphere = find("olm_weak_melee_proj")
    val olm_melee_sphere_impact = find("olm_weak_melee_impact")
    val olm_firewall = find("olm_firewall_travel")
    val olm_burn = find("olm_burnwithme_travel")
    val olm_burn_spot = find("olm_burnwithme_pl_spot")
    val olm_bomb_travel = find("olm_crystal_bomb_travel")
    val olm_bomb_explode = find("olm_crystal_bomb_explode")
    val olm_crystal_shatter = find("olm_crystalwave_shatter")
    val olm_acid = find("olm_acid_spit")
    val olm_heal_spit = find("olm_healme_spit")
    val olm_heal = find("olm_healme_spotanim")
    val olm_shockwave = find("olm_shockwave")
    val olm_falling_crystal = find("olm_crystalrock_falling")
    val olm_crystal_explode = find("olm_crystal_explode")
    val olm_swap = (0..3).map { find("olm_playerswap_$it") }
    val olm_central_shatter = find("raids_olm_central_shattercrystal")
}
