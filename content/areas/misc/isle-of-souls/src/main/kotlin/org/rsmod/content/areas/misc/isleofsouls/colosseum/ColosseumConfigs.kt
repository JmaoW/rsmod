package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.builders.proj.ProjAnimBuilder
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.proj.ProjAnimReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.movement.MoveRestrict
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType

typealias colosseum_npcs = ColosseumNpcs

typealias colosseum_locs = ColosseumLocs

typealias colosseum_objs = ColosseumObjs

typealias colosseum_seqs = ColosseumSeqs

typealias colosseum_spotanims = ColosseumSpotanims

typealias colosseum_projanims = ColosseumProjAnims

typealias colosseum_varps = ColosseumVarps

typealias colosseum_varbits = ColosseumVarBits

typealias colosseum_interfaces = ColosseumInterfaces

typealias colosseum_components = ColosseumComponents

typealias colosseum_invs = ColosseumInvs

typealias colosseum_queues = ColosseumQueues

object ColosseumNpcs : NpcReferences() {
    /** Minimus in the lobby (talk, quiver exchange) and in the arena (Start-wave, Leave). */
    val minimus_lobby = find("colosseum_master_outside")
    val minimus = find("colosseum_master")
    val gloria = find("colosseum_glory")

    val berserker = find("colosseum_warbander_melee_male")
    val seer = find("colosseum_warbander_mage_male")
    val archer = find("colosseum_warbander_ranged_female")
    val shaman = find("colosseum_standard_mager")
    val jaguar = find("colosseum_jaguar_warrior")
    val javelin = find("colosseum_javelin_colossus")
    val manticore = find("colosseum_manticore")
    val shockwave = find("colosseum_shockwave_colossus")
    val minotaur = find("colosseum_minotaur")

    /** The Minotaur that Red Flag sends: it finds its way round the pillars. */
    val minotaur_routefind = find("colosseum_minotaur_routefind")

    /** Sol Heredit on his seat above the north gate, and the one who fights (1,500 HP, size 5). */
    val sol_seated = find("colosseum_boss_seated")
    val sol = find("colosseum_sol_p1")

    /** Sol Heredit's light crystals, and the modifiers' bees, totems and Solarflare orb. */
    val crystal = find("colosseum_beam_crystal")
    val bees = find("colosseum_modifier_bees")
    val totem = find("colosseum_healing_totem")
    val solar_flare = find("colosseum_solar_flare")

    val guard_m_1 = find("colosseum_lobby_guard_static_m_1")
    val guard_m_2 = find("colosseum_lobby_guard_static_m_2")
    val guard_f_1 = find("colosseum_lobby_guard_static_f_1")
    val guard_f_2 = find("colosseum_lobby_guard_static_f_2")
}

object ColosseumLocs : LocReferences() {
    /** The arch in Civitas illa Fortis; the isle has a copy of it. "Enter" goes to the lobby. */
    val entrance_outside = find("colosseum_entrance_outside")

    /** The lobby's stairs back up, and the tunnel into the arena. */
    val stairs = find("colosseum_exit_lobby")
    val tunnel = find("colosseum_entrance")
    val bank = find("colosseum_bank")
    val scoreboard = find("colosseum_scoreboard")

    /** The rewards chest (3x3) that comes out when a run is over. */
    val reward_chest = find("colosseum_reward")

    /** Molten sand: hurts whoever stands on it, does not block. */
    val molten_sand = find("colosseum_holy_fire")

    /** The blocker under the seated Sol Heredit (capture). */
    val seat_blocker = find("invisible_type8_blocking_size4")

    /** The shield-bearing gladiators who close off the middle for Sol Heredit's wave. */
    val shield_1 = find("shield_combatant_1")
    val shield_2 = find("shield_combatant_2")
    val shield_3 = find("shield_combatant_3")
    val shield_4 = find("shield_combatant_4")
    val shield_5 = find("shield_combatant_5")
    val shield_6 = find("shield_combatant_6")

    /** A Shrine of Ralos, where Dizana's quiver is blessed. The lobby has a copy of it. */
    val ralos_shrine = find("ralos_shrine")

    val shields
        get() = listOf(shield_1, shield_2, shield_3, shield_4, shield_5, shield_6)
}

object ColosseumObjs : ObjReferences() {
    val sunfire_splinters = find("sunfiresplinter")
    val quiver_uncharged = find("dizanas_quiver_uncharged")
    val quiver_charged = find("dizanas_quiver_charged")
    val quiver_blessed = find("dizanas_quiver_infinite")
    val sunfire_helm = find("sunfire_helm")
    val sunfire_body = find("sunfire_body")
    val sunfire_legs = find("sunfire_legs")
    val echo_crystal = find("echo_crystal")
    val guardian_boots = find("guardian_boots")
    val echo_boots = find("echo_boots")
    val tonalztics_uncharged = find("tonalztics_of_ralos_uncharged")
    val tonalztics_charged = find("tonalztics_of_ralos_charged")
    val pet = find("solhereditpet")

    val rune_platebody = find("rune_platebody")
    val death_rune = find("deathrune")
    val chaos_rune = find("chaosrune")
    val steel_cannonball = find("mcannonball")
    val rune_kiteshield_noted = find("cert_rune_kiteshield")
    val rune_chainbody = find("rune_chainbody")
    val onyx_bolts = find("xbows_crossbow_bolts_runite_tipped_onyx")
    val snapdragon_seed = find("snapdragon_seed")
    val dragon_platelegs = find("dragon_platelegs")
    val dragon_platelegs_noted = find("cert_dragon_platelegs")
    val earth_orb_noted = find("cert_earth_orb")
    val rune_2h_sword_noted = find("cert_rune_2h_sword")
    val gold_ore_noted = find("cert_gold_ore")
    val dragon_bolts_unf = find("dragon_bolts_unfeathered")
    val ranarr_seed = find("ranarr_seed")
    val dragon_arrowtips = find("dragon_arrowheads")
    val adamantite_ore_noted = find("cert_adamantite_ore")
    val runite_ore_noted = find("cert_runite_ore")
    val rune_warhammer_noted = find("cert_rune_warhammer")
    val dragon_plateskirt_noted = find("cert_dragon_plateskirt")
    val uncut_onyx = find("uncut_onyx")

    val ring_of_dueling_8 = find("ring_of_dueling_8")
    val ring_of_dueling_7 = find("ring_of_dueling_7")
    val ring_of_dueling_6 = find("ring_of_dueling_6")
    val ring_of_dueling_5 = find("ring_of_dueling_5")
    val ring_of_dueling_4 = find("ring_of_dueling_4")
    val ring_of_dueling_3 = find("ring_of_dueling_3")
    val ring_of_dueling_2 = find("ring_of_dueling_2")
    val ring_of_dueling_1 = find("ring_of_dueling_1")

    /** From most charges to fewest. */
    val rings_of_dueling
        get() =
            listOf(
                ring_of_dueling_8,
                ring_of_dueling_7,
                ring_of_dueling_6,
                ring_of_dueling_5,
                ring_of_dueling_4,
                ring_of_dueling_3,
                ring_of_dueling_2,
                ring_of_dueling_1,
            )
}

object ColosseumSeqs : SeqReferences() {
    val berserker_attack = find("npc_fremennik_warbander_melee_human_sword_stab")
    val berserker_defend = find("npc_fremennik_warband_melee_human_unarmed_def")
    val berserker_death = find("npc_fremennik_warband_melee_human_death")
    val seer_attack = find("npc_fremennik_warbander_mage_zaros_vertical_casting_walkmerge")
    val seer_defend = find("npc_fremennik_warband_mage_human_unarmed_def")
    val seer_death = find("npc_fremennik_warband_mage_human_death")
    val archer_attack = find("npc_fremennik_warbander_archer_att_colosseum")
    val archer_defend = find("npc_fremennik_warband_archer_human_unarmed_def")
    val archer_death = find("npc_fremennik_warband_archer_human_death")
    val shaman_attack = find("npc_serpent_mager_casting")
    val shaman_death = find("npc_serpent_mager_death")
    val jaguar_attack = find("npc_jaguar_ranger_claws_attack")
    val jaguar_defend = find("npc_jaguar_human_unarmed_def")
    val jaguar_death = find("npc_jaguar_human_death")
    val javelin_attack = find("npc_colossi_javelin_01_range_attack")
    val javelin_artillery = find("npc_colossi_javelin_01_artillery_attack")
    val javelin_death = find("npc_colossi_javelin_01_death")
    val manticore_charge = find("npc_manticore_01_triple_charge")
    val manticore_throw = find("npc_manticore_01_triple_throw")
    val manticore_death = find("npc_manticore_01_death")
    val manticore_explode = find("npc_manticore_01_death_explode")
    val shockwave_attack = find("npc_colossi_shockwave_01_clapattack")
    val shockwave_death = find("npc_colossi_shockwave_01_death")
    val minotaur_attack = find("npc_minotaur_boss_attack_melee")
    val minotaur_death = find("npc_minotaur_boss_death")

    /** The shaman has no block of its own (tsps: -1): defending with its stand means no flinch. */
    val shaman_ready = find("human_staffready")

    /** The Manticore (two of them, a pose each) and the Minotaur as they appear (guesses). */
    val manticore_spawn_1 = find("npc_manticore_01_spawn_01")
    val manticore_spawn_2 = find("npc_manticore_01_spawn_02")
    val minotaur_spawn = find("npc_minotaur_boss_spawn")

    /** Bees! swarms appearing, stinging and breaking up. */
    val bees_spawn = find("npc_colosseum_bees_spawn_01")
    val bees_attack = find("npc_colosseum_bees_attack_01")
    val bees_despawn = find("npc_colosseum_bees_despawn_01")

    /** The gladiators and the shaman as they appear, and Minimus as he does. */
    val human_spawn = find("npc_human_staffready_spawn")
    val minimus_spawn = find("npc_human_ready_spawn")

    val sol_jump = find("npc_colossi_finalboss_01_arena_jump")
    val sol_land = find("npc_colossi_finalboss_01_arena_land")
    val sol_spear = find("npc_colossi_finalboss_01_melee_attack_telegraph")
    val sol_grapple = find("npc_colossi_finalboss_01_grapple_attack_telegraph")
    val sol_shield = find("npc_colossi_finalboss_01_shieldslam_telegraph")
    val sol_triple = find("npc_colossi_finalboss_tripleattack")
    val sol_triple_short = find("npc_colossi_finalboss_tripleattack_shorter")
    val sol_death = find("npc_colossi_finalboss_01_death")

    val crystal_spawn = find("npc_colosseum_crystal_spawn_01")
    val crystal_despawn = find("npc_colosseum_crystal_despawn_01")
    val crystal_charge = find("npc_colosseum_crystal_charge_01")
    val crystal_attack = find("npc_colosseum_crystal_attack_01")
    val totem_attack = find("npc_colosseum_totem_attack_01")
    val chest_appear = find("chest_colosseum01_reward01_spawn_01")
}

object ColosseumSpotanims : SpotanimReferences() {
    val seer_travel = find("fireblast_travel")
    val seer_impact = find("fireblast_impact")
    val archer_travel = find("iron_arrow_travel")
    val shaman_cast = find("watersurge_casting")
    val shaman_travel = find("watersurge_travel")
    val shaman_impact = find("watersurge_impact")
    val javelin_travel = find("npc_colossi_javelin_01_spearhead")
    val artillery_fire = find("npc_colossi_javelin_01_artillery_fire")
    val artillery_shadow = find("gargboss_debris_shadow_90")
    val artillery_land = find("npc_colossi_javelin_01_artillery_slow")
    val orb_magic = find("vfx_manticore_01_projectile_magic_01")
    val orb_magic_impact = find("vfx_manticore_01_projectile_impact_magic_01")
    val orb_ranged = find("vfx_manticore_01_projectile_ranged_01")
    val orb_ranged_impact = find("vfx_manticore_01_projectile_impact_ranged_01")
    val orb_melee = find("vfx_manticore_01_projectile_melee_01")
    val orb_melee_impact = find("vfx_manticore_01_projectile_impact_melee_01")
    val shockwave_travel = find("vfx_vardorvis_head_projectile_magic_01")

    val sol_land = find("npc_colossi_colossi_01_land")
    val sol_death = find("npc_colossi_finalboss_01_death")
    val sol_triple = find("npc_colossi_colossi_tripleattack_01_telegraph")
    val sol_triple_short = find("npc_colossi_colossi_tripleattack_01_telegraph_shorter")
    val sol_smoke_1 = find("spotanim_colossi_finalboss_01_melee")
    val sol_smoke_2 = find("spotanim_colossi_finalboss_02_melee")
    val sol_smoke_3 = find("spotanim_colossi_finalboss_03_melee")
    val sol_smoke_4 = find("spotanim_colossi_finalboss_04_melee")
    val sunfire_beam = find("vfx_colosseum_sunfire_lightning_01_beam_01")
    val crystal_charge_1 = find("vfx_colosseum_crystal_charge_01_beam_01")
    val crystal_charge_2 = find("vfx_colosseum_crystal_charge_01_beam_02")
    val crystal_charge_3 = find("vfx_colosseum_crystal_charge_01_beam_03")
    val crystal_charge_4 = find("vfx_colosseum_crystal_charge_01_beam_04")
    val crystal_beam_1 = find("vfx_colosseum_crystal_attack_01_beam_01")
    val crystal_beam_2 = find("vfx_colosseum_crystal_attack_01_beam_02")
    val crystal_beam_3 = find("vfx_colosseum_crystal_attack_01_beam_03")
    val crystal_beam_4 = find("vfx_colosseum_crystal_attack_01_beam_04")
    val crystal_impact = find("vfx_colosseum_crystal_attack_01_impact_01")
    val totem_travel = find("vfx_colosseum_totem_projectile_01")
    val totem_impact = find("vfx_colosseum_totem_impact_01")
    val explode_human = find("vfx_colosseum_human_explosion_01")
    val explode_manticore = find("vfx_colosseum_manticore_explosion_01")
    val explode_colossus = find("vfx_colosseum_colossi_explosion_01")
    val explode_minotaur = find("vfx_colosseum_minotaur_explosion_01")
    val explode_sol = find("vfx_colosseum_finalboss_explosion_01")

    val sol_smokes
        get() = listOf(sol_smoke_1, sol_smoke_2, sol_smoke_3, sol_smoke_4)
}

/** The flights of the Colosseum's projectiles (see [ColosseumProjAnimBuilder]). */
object ColosseumProjAnims : ProjAnimReferences() {
    val seer = find("colosseum_seer")
    val archer = find("colosseum_archer")
    val shaman = find("colosseum_shaman")
    val javelin = find("colosseum_javelin")
    val shockwave = find("colosseum_shockwave")
    val orb = find("colosseum_orb")
    val totem = find("colosseum_totem")
}

/**
 * The Colosseum's projectile flights, from tsps' notes on the live game (its heights are a quarter
 * of the client's, so they are multiplied back). The engine's flight is `delay + lengthAdjustment
 * + stepMultiplier * distance` client cycles. Needs `packCache` (new `projanim.sym` ids).
 */
internal object ColosseumProjAnimBuilder : ProjAnimBuilder() {
    init {
        flight("colosseum_seer", 43, 31, start = 10, base = 30, perTile = 0)
        flight("colosseum_archer", 41, 36, start = 10, base = 30, perTile = 0)
        flight("colosseum_shaman", 43, 36, start = 67, base = 67, perTile = 4)
        flight("colosseum_javelin", 70, 36, start = 58, base = 58, perTile = 3)
        flight("colosseum_shockwave", 45, 31, start = 51, base = 51, perTile = 5)
        flight("colosseum_orb", 50, 31, start = 0, base = 25, perTile = 0)
        flight("colosseum_totem", 40, 40, start = 20, base = 40, perTile = 0)
    }

    private fun flight(
        name: String,
        startHeight: Int,
        endHeight: Int,
        start: Int,
        base: Int,
        perTile: Int,
    ) {
        build(name) {
            this.startHeight = startHeight * HEIGHT_SCALE
            this.endHeight = endHeight * HEIGHT_SCALE
            this.delay = start
            this.angle = ANGLE
            this.lengthAdjustment = base - start
            this.progress = PROGRESS
            this.stepMultiplier = perTile
        }
    }

    private const val HEIGHT_SCALE = 4
    private const val ANGLE = 16
    private const val PROGRESS = 64
}

object ColosseumVarps : VarpReferences() {
    /** The best run's Glory: the title, the bank chest and the Fortis Salute read it. */
    val glory = find("colosseum_glory")

    /** This run's Glory so far, the wave's start time and the damage taken in it. */
    val current_glory = find("colosseum_current_glory")
    val wave_start_time = find("colosseum_wave_start_time")
    val wave_damage_taken = find("colosseum_wave_damage_taken")
    val last_modifier_glory = find("colosseum_last_modifier_glory")
    val last_wave_duration = find("colosseum_last_wave_duration")
    val total_duration = find("colosseum_total_duration")
    val waves_completed = find("total_colosseum_waves_completed")

    /** The collection log's Sol Heredit kill count: he skips his greeting once beaten. */
    val sol_kills = find("clog_kc_sol_heredit_kills")

    /**
     * Echo boots' "Toggle-activity": 1 while the recoil is switched off (ours, not the cache's).
     */
    val echo_off = find("isle_echo_boots_recoil_off")
}

internal object ColosseumVarpBuilder : VarpBuilder() {
    init {
        build("isle_echo_boots_recoil_off") { permanent = true }
    }
}

object ColosseumVarBits : VarBitReferences() {
    /** The modifier picked on the intermission screen, 1 to 3 (0 for none). */
    val selected_modifier = find("colosseum_selected_modifier")
    val highest_wave = find("colosseum_highest_wave")

    /** "Don't ask again" on the tunnel's warning. */
    val entry_warning = find("colosseum_entry_warning")

    /** Minimus has given his introduction (the tunnel stays shut until then). */
    val master_intro = find("colosseum_master_intro")
    val gloria_met = find("colosseum_gloria_met")

    /** How many times Sol Heredit was faced and not beaten: his greeting depends on it. */
    val sol_failures = find("colosseum_sol_failures")

    /** The tier of each tiered modifier, which the intermission screen reads. */
    val tier_mantimayhem = find("colosseum_modifier_mantimayhem_stacks_client")
    val tier_reentry = find("colosseum_modifier_reentry_stacks_client")
    val tier_bees = find("colosseum_modifier_toxicity_stacks_client")
    val tier_volatility = find("colosseum_modifier_volatility_stacks_client")
    val tier_blasphemy = find("colosseum_modifier_blasphemy_stacks_client")
    val tier_relentless = find("colosseum_modifier_relentless_stacks_client")
    val tier_doom = find("colosseum_modifier_doom_stacks_client")
    val tier_solarflare = find("colosseum_modifier_solarflare_stacks_client")
    val tier_myopia = find("colosseum_modifier_myopia_stacks_client")
    val tier_frailty = find("colosseum_modifier_frailty_stacks_client")

    /**
     * Charges in Dizana's quiver and the Tonalztics of Ralos, and the splinters put to blessing.
     */
    val quiver_charges = find("charges_dizanas_quiver_quantity")
    val quiver_blessing = find("charges_dizanas_quiver_blessing")
    val tonalztics_charges = find("charges_tonalztics_of_ralos_quantity")
    val echo_charges = find("charges_echo_boots_quantity")
}

object ColosseumInterfaces : InterfaceReferences() {
    val intermission = find("colosseum_intermission_2")
    val reward_chest = find("colosseum_reward_chest")
}

object ColosseumComponents : ComponentReferences() {
    val mod_button_1 = find("colosseum_intermission_2:mod_button_1")
    val mod_button_2 = find("colosseum_intermission_2:mod_button_2")
    val mod_button_3 = find("colosseum_intermission_2:mod_button_3")
    val confirm = find("colosseum_intermission_2:right_button")
    val left_loot = find("colosseum_intermission_2:left_loot")
    val right_loot = find("colosseum_intermission_2:right_loot")
    val mod_list = find("colosseum_intermission_2:tab_content_1_mod_list_content")
    val tab_content = find("colosseum_intermission_2:tab_content_2_content")

    val chest_value = find("colosseum_reward_chest:colosseum_reward_value")
    val chest_bank = find("colosseum_reward_chest:bank")
    val chest_take = find("colosseum_reward_chest:inventory")
    val chest_discard = find("colosseum_reward_chest:discard")
    val chest_items = find("colosseum_reward_chest:items")
}

object ColosseumInvs : InvReferences() {
    /** The loot earned so far, the next wave's, and the last wave's. */
    val rewards = find("colosseum_rewards")
    val future = find("colosseum_rewards_future")
    val previous = find("colosseum_rewards_previous")
}

object ColosseumQueues : QueueReferences() {
    /** Sol Heredit's greeting before he jumps down, shown to each player in the arena. */
    val sol_greeting = find("colosseum_sol_greeting")
}

/**
 * What the Colosseum's npcs need in a team's arena. The waves' monsters never wander or hunt by
 * themselves: [Colosseum] aims them every tick. The warband, the bees and the Solarflare orb walk
 * through other npcs. The human monsters get their own block and death animations (the cache's
 * types only have examines). Needs `packCache`.
 */
internal object ColosseumNpcEdits : NpcEditor() {
    init {
        edit(colosseum_npcs.berserker, colosseum_seqs.berserker_defend, passThru = true)
        edit(colosseum_npcs.seer, colosseum_seqs.seer_defend, passThru = true)
        edit(colosseum_npcs.archer, colosseum_seqs.archer_defend, passThru = true)
        edit(colosseum_npcs.shaman, colosseum_seqs.shaman_ready) { attackRange = SHAMAN_RANGE }
        edit(colosseum_npcs.jaguar, colosseum_seqs.jaguar_defend)
        edit(colosseum_npcs.javelin, null) { attackRange = COLOSSUS_RANGE }
        edit(colosseum_npcs.manticore, null) { attackRange = COLOSSUS_RANGE }
        edit(colosseum_npcs.shockwave, null) { attackRange = COLOSSUS_RANGE }
        edit(colosseum_npcs.minotaur, null)
        edit(colosseum_npcs.minotaur_routefind, null)
        edit(colosseum_npcs.sol, null) { heroCount = HERO_SLOTS }
        edit(colosseum_npcs.bees, null, passThru = true)
        edit(colosseum_npcs.solar_flare, null, passThru = true)
        edit(colosseum_npcs.crystal, null, passThru = true)
        edit(colosseum_npcs.totem, null) { moveRestrict = MoveRestrict.NoMove }
        for (type in listOf(colosseum_npcs.minimus, colosseum_npcs.sol_seated)) {
            edit(type) {
                defaultMode = NpcMode.None
                wanderRange = 0
            }
        }
    }

    private fun edit(
        type: NpcType,
        defend: SeqType?,
        passThru: Boolean = false,
        init: NpcPluginBuilder.() -> Unit = {},
    ) {
        edit(type) {
            defaultMode = NpcMode.None
            wanderRange = 0
            maxRange = ARENA_LEASH
            giveChase = true
            if (passThru) {
                moveRestrict = MoveRestrict.PassThru
            }
            if (defend != null) {
                param[params.defend_anim] = defend
            }
            init()
        }
    }

    /** Further than any two tiles of the arena are apart. */
    const val ARENA_LEASH = 60

    /** The wiki: the shaman attacks from 10 tiles, the colossi and the Manticore from 15. */
    const val SHAMAN_RANGE = 10
    const val COLOSSUS_RANGE = 15

    /** How many players' damage is kept to decide who killed a monster. */
    const val HERO_SLOTS = 16
}
