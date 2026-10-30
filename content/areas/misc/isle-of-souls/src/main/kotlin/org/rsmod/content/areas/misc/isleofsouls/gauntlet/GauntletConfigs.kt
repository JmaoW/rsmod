package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.varbit.VarBitType

typealias gauntlet_objs = GauntletObjs

typealias gauntlet_locs = GauntletLocs

typealias gauntlet_npcs = GauntletNpcs

typealias gauntlet_varbits = GauntletVarBits

typealias gauntlet_varps = GauntletVarps

typealias gauntlet_interfaces = GauntletInterfaces

typealias gauntlet_seqs = GauntletSeqs

typealias gauntlet_components = GauntletComponents

typealias gauntlet_spotanims = GauntletSpotanims

object GauntletObjs : ObjReferences() {
    val sceptre = find("gauntlet_sceptre")
    val axe = find("gauntlet_axe")
    val pickaxe = find("gauntlet_pickaxe")
    val harpoon = find("gauntlet_harpoon")
    val teleport_crystal = find("gauntlet_teleport_crystal")

    val sceptre_hm = find("gauntlet_sceptre_hm")
    val axe_hm = find("gauntlet_axe_hm")
    val pickaxe_hm = find("gauntlet_pickaxe_hm")
    val harpoon_hm = find("gauntlet_harpoon_hm")
    val teleport_crystal_hm = find("gauntlet_teleport_crystal_hm")

    /** The pestle and mortar is the same item in both versions. */
    val pestle = find("gauntlet_pestle")
}

object GauntletLocs : LocReferences() {
    /** The portal on the isle; the real one stands in Prifddinas. "Enter". */
    val portal = find("gauntlet_lobby_entrance")

    /** The lobby's platform back up: "Channel". */
    val lobby_exit = find("gauntlet_lobby_exit")

    /** The lobby's entrance in its two forms: "Enter", and "Enter" with "Enter-corrupted". */
    val entrance = find("gauntlet_entrance_hm_disabled")
    val entrance_corrupted = find("gauntlet_entrance_hm_enabled")

    /** The platform in the start room: "Exit" and "Quick-exit". */
    val exit = find("gauntlet_exit")
    val exit_hm = find("gauntlet_exit_hm")

    /**
     * The nodes on the sides of every room ("Light"), in the two shapes the map has, and what each
     * becomes once the passage it stands in is lit. The symbols beside them are lit by the same
     * passages.
     */
    val node_a = find("prif_gauntlet_door_wall_unlit_01")
    val node_b = find("prif_gauntlet_door_wall_unlit_02")
    val node_a_lit = find("prif_gauntlet_door_wall_lit_01")
    val node_b_lit = find("prif_gauntlet_door_wall_lit_02")
    val symbol = find("prif_gauntlet_wall_arch_blue_unlit")
    val symbol_lit = find("prif_gauntlet_wall_arch_blue_lit")

    val node_a_hm = find("prif_gauntlet_door_wall_unlit_01_hm")
    val node_b_hm = find("prif_gauntlet_door_wall_unlit_02_hm")
    val node_a_lit_hm = find("prif_gauntlet_door_wall_lit_01_hm")
    val node_b_lit_hm = find("prif_gauntlet_door_wall_lit_02_hm")
    val symbol_hm = find("prif_gauntlet_wall_arch_red_unlit_hm")
    val symbol_lit_hm = find("prif_gauntlet_wall_arch_red_lit_hm")

    /** The start room's stations (the Corrupted ones end in `_hm`). */
    val tools = find("gauntlet_tools")
    val sceptre_holder = find("gauntlet_sceptre")
    val sink = find("gauntlet_sink")
    val range = find("gauntlet_range")
    val bowl = find("gauntlet_singing_bowl")
    val book = find("gauntlet_book")
    val book_egniol = find("gauntlet_book_2")
    val tools_hm = find("gauntlet_tools_hm")
    val sceptre_holder_hm = find("gauntlet_sceptre_hm")
    val sink_hm = find("gauntlet_sink_hm")
    val range_hm = find("gauntlet_range_hm")
    val bowl_hm = find("gauntlet_singing_bowl_hm")
    val book_hm = find("gauntlet_book_hm")
    val book_egniol_hm = find("gauntlet_book_2_hm")

    /** The resource nodes and what is left of them once spent. */
    val rock = find("gauntlet_rock")
    val rock_spent = find("gauntlet_rock_depleted")
    val tree = find("gauntlet_tree")
    val tree_spent = find("gauntlet_tree_depleted")
    val pond = find("gauntlet_pond")
    val pond_spent = find("gauntlet_pond_depleted")
    val herb = find("gauntlet_herb")
    val herb_spent = find("gauntlet_herb_depleted")
    val fibre = find("gauntlet_fibre")
    val fibre_spent = find("gauntlet_fibre_depleted")
    val rock_hm = find("gauntlet_rock_hm")
    val rock_spent_hm = find("gauntlet_rock_depleted_hm")
    val tree_hm = find("gauntlet_tree_hm")
    val tree_spent_hm = find("gauntlet_tree_depleted_hm")
    val pond_hm = find("gauntlet_pond_hm")
    val pond_spent_hm = find("gauntlet_pond_depleted_hm")
    val herb_hm = find("gauntlet_herb_hm")
    val herb_spent_hm = find("gauntlet_herb_depleted_hm")
    val fibre_hm = find("gauntlet_fibre_hm")
    val fibre_spent_hm = find("gauntlet_fibre_depleted_hm")

    /** The lobby's scoreboard ("Read") and reward chest (shut, "Open"). */
    val scoreboard = find("gauntlet_scoreboard")
    val chest = find("gauntlet_chest_closed")

    /** The Hunllef room's floor tiles: plain, warning (blue / crimson) and hit (orange). */
    val floor = find("prif_gauntlet_floor_tile_01")
    val floor_warning = find("prif_gauntlet_floor_tile_01_warning")
    val floor_hit = find("prif_gauntlet_floor_tile_01_hit")
    val floor_hm = find("prif_gauntlet_floor_tile_01_hm")
    val floor_warning_hm = find("prif_gauntlet_floor_tile_01_warning_hm")
    val floor_hit_hm = find("prif_gauntlet_floor_tile_01_hit_hm")

    /** The Hunllef's barriers: "Pass" and "Quick-pass" before the fight, "Escape" during it. */
    val barrier = find("gauntlet_blockade_enter")
    val barrier_escape = find("gauntlet_blockade_escape")
    val barrier_hm = find("gauntlet_blockade_enter_hm")
    val barrier_escape_hm = find("gauntlet_blockade_escape_hm")
}

object GauntletNpcs : NpcReferences() {
    /** Bryn, in the lobby. */
    val bryn = find("gauntlet_instructor")

    /** The nine crystalline monsters (the Corrupted ones end in `_hm`). */
    val rat = find("crystal_rat")
    val spider = find("crystal_spider")
    val bat = find("crystal_bat")
    val unicorn = find("crystal_unicorn")
    val scorpion = find("crystal_scorpion")
    val wolf = find("crystal_wolf")
    val bear = find("crystal_bear")
    val dragon = find("crystal_dragon")
    val dark_beast = find("crystal_dark_beast")
    val rat_hm = find("crystal_rat_hm")
    val spider_hm = find("crystal_spider_hm")
    val bat_hm = find("crystal_bat_hm")
    val unicorn_hm = find("crystal_unicorn_hm")
    val scorpion_hm = find("crystal_scorpion_hm")
    val wolf_hm = find("crystal_wolf_hm")
    val bear_hm = find("crystal_bear_hm")
    val dragon_hm = find("crystal_dragon_hm")
    val dark_beast_hm = find("crystal_dark_beast_hm")

    /** The Hunllef in the form that protects against melee, ranged and magic, and its tornado. */
    val hunllef_melee = find("crystal_hunllef_melee")
    val hunllef_ranged = find("crystal_hunllef_ranged")
    val hunllef_magic = find("crystal_hunllef_magic")
    val tornado = find("crystal_hunllef_crystals")
    val hunllef_melee_hm = find("crystal_hunllef_melee_hm")
    val hunllef_ranged_hm = find("crystal_hunllef_ranged_hm")
    val hunllef_magic_hm = find("crystal_hunllef_magic_hm")
    val tornado_hm = find("crystal_hunllef_crystals_hm")
}

object GauntletSpotanims : SpotanimReferences() {
    val dragon_travel = find("crystal_dragon_magic_travel")
    val dragon_impact = find("crystal_dragon_magic_impact")
    val dragon_travel_hm = find("crystal_dragon_magic_travel_hm")
    val dragon_impact_hm = find("crystal_dragon_magic_impact_hm")
    val dark_beast_travel = find("crystal_dark_beast_range_travel")
    val dark_beast_travel_hm = find("crystal_dark_beast_range_travel_hm")

    /** The Hunllef's ranged and magic shots, its prayer-disabling shot, and where they land. */
    val hunllef_range = find("crystal_hunllef_range_travel")
    val hunllef_magic = find("crystal_hunllef_magic_travel")
    val hunllef_magic_impact = find("crystal_hunllef_magic_impact")
    val hunllef_prayer = find("crystal_hunllef_prayer_travel")
    val hunllef_prayer_impact = find("crystal_hunllef_prayer_impact")
    val hunllef_range_hm = find("crystal_hunllef_range_travel_hm")
    val hunllef_magic_hm = find("crystal_hunllef_magic_travel_hm")
    val hunllef_magic_impact_hm = find("crystal_hunllef_magic_impact_hm")
    val hunllef_prayer_hm = find("crystal_hunllef_prayer_travel_hm")
    val hunllef_prayer_impact_hm = find("crystal_hunllef_prayer_impact_hm")

    /** A tornado catching a player. */
    val tornado_hit = find("crystal_hunllef_crystals_hit")
}

object GauntletInterfaces : InterfaceReferences() {
    /** The preparation timer on the heads-up display. */
    val overlay = find("gauntlet_overlay")

    /** The book of crystal singing recipes. */
    val recipes = find("gauntlet_recipes")

    /** The lobby's scoreboard. */
    val scoreboard = find("gauntlet_scoreboard")
}

object GauntletComponents : ComponentReferences() {
    private fun panel(suffix: String, title: String): List<ComponentType> =
        listOf(
            find("gauntlet_scoreboard:$title"),
            find("gauntlet_scoreboard:personal_completions_header$suffix"),
            find("gauntlet_scoreboard:personal_completions$suffix"),
            find("gauntlet_scoreboard:global_completions_header$suffix"),
            find("gauntlet_scoreboard:global_completions$suffix"),
            find("gauntlet_scoreboard:personal_deaths_header$suffix"),
            find("gauntlet_scoreboard:personal_deaths$suffix"),
            find("gauntlet_scoreboard:global_deaths_header$suffix"),
            find("gauntlet_scoreboard:global_deaths$suffix"),
            find("gauntlet_scoreboard:personal_time_header$suffix"),
            find("gauntlet_scoreboard:personal_time$suffix"),
            find("gauntlet_scoreboard:global_time_header$suffix"),
            find("gauntlet_scoreboard:global_time$suffix"),
        )

    /** A panel's lines: the title, then a label and a value for each of six figures. */
    val regular: List<ComponentType> = panel("", "regular_title")
    val corrupted: List<ComponentType> = panel("_hm", "hm_title")
}

object GauntletSeqs : SeqReferences() {
    val mine = find("human_mining_crystal_pickaxe")
    val mine_hm = find("human_mining_gauntlet_pickaxe_hm")
    val chop = find("human_woodcutting_crystal_axe")
    val chop_hm = find("human_woodcutting_gauntlet_axe_hm")
    val harpoon = find("human_harpoon_crystal")
    val harpoon_hm = find("human_harpoon_gauntlet_hm")
    val pick = find("picking_low")
    val fill = find("human_pickupfloor")
    val cook = find("human_cooking")
    val grind = find("human_herbing_grind")
    val mix = find("human_herbing_vial")

    /** The Hunllef: its two ordinary attacks, the tornado summon and the style change. */
    val hunllef_ranged = find("hunllef_attack_ranged")
    val hunllef_stomp = find("hunllef_attack_melee")
    val hunllef_special = find("hunllef_attack_special")
    val hunllef_transition = find("hunllef_attack_transition_magic")

    /**
     * What the Hunllef does when hit (nothing: it stays on its idle) and how it dies, in two parts
     * one after the other. From the animations seen on it in the real game.
     */
    val hunllef_ready = find("hunllef_ready")
    val hunllef_death_a = find("hunllef_death_part_a")
    val hunllef_death_b = find("hunllef_death_part_b")

    /**
     * The animations of the creature each monster is made of (the cache has no hit animation for
     * any of them: they stay on their idle when hit).
     */
    val rat_ready = find("giant_rat_update_ready")
    val rat_attack = find("giant_rat_update_attack")
    val rat_death = find("giant_rat_update_death_fast")
    val spider_ready = find("sote_gauntlet_spider_idle")
    val spider_attack = find("sote_gauntlet_spider_attack_melee")
    val spider_death = find("sote_gauntlet_spider_death")
    val bat_ready = find("bat_rework_ready")
    val bat_attack = find("bat_rework_attack")
    val bat_death = find("bat_rework_death")
    val unicorn_ready = find("unicorn_rework_ready")
    val unicorn_attack = find("unicorn_rework_attack")
    val unicorn_death = find("unicorn_rework_death")
    val scorpion_ready = find("scorpion_update_ready")
    val scorpion_attack = find("scorpion_update_attack_tail")
    val scorpion_death = find("scorpion_update_death")
    val wolf_ready = find("dog_update_wolf_ready")
    val wolf_attack = find("dog_update_wolf_attack")
    val wolf_death = find("dog_update_wolf_death_quick")
    val bear_ready = find("bear_rework_ready")
    val bear_attack = find("bear_rework_attack")
    val bear_death = find("bear_rework_death")
    val dragon_ready = find("dragon_ready")
    val dragon_attack = find("dragon_firebreath_all_attack")
    val dragon_death = find("dragon_death")
    val dark_beast_ready = find("dark_beast_update_ready")
    val dark_beast_attack = find("dark_beast_update_attack")
    val dark_beast_death = find("dark_beast_update_death")
}

object GauntletVarBits : VarBitReferences() {
    /** Turns the barrier from Pass to Escape and the timer to "Final Encounter". */
    val boss_started = find("gauntlet_boss_started")

    /** Swaps the world map for the maze map. */
    val in_gauntlet = find("player_in_gauntlet")

    /** The reward chest holds something. */
    val reward_available = find("gauntlet_reward_available")

    /** The maze map: where the player is, where the start room is, which version. */
    val current_room_x = find("gauntlet_current_room_x")
    val current_room_z = find("gauntlet_current_room_z")
    val start = find("gauntlet_start")
    val corrupted = find("gauntlet_corrupted")

    /** One bit per room of the 7x7 grid, numbered `z * 7 + x`: the room has been lit. */
    val room_found: List<VarBitType> = List(49) { find("gauntlet_room_${it}_found") }
}

object GauntletVarps : VarpReferences() {
    /**
     * The client's completion counts: the entrance offers "Enter-corrupted" once one is above 0.
     */
    val client_done = find("total_completed_gauntlet")
    val client_done_hm = find("total_completed_gauntlet_hm")

    /** Server-side, saved with the player. */
    val bryn = find("isle_gauntlet_bryn")
    val done = find("isle_gauntlet_done")
    val done_hm = find("isle_gauntlet_done_hm")

    /** 1 from the moment a player steps into a maze until a run takes them out again. */
    val in_run = find("isle_gauntlet_in_run")

    /**
     * The reward waiting in the lobby chest: 0 none, else the kind (1 Hunllef killed, 2 incomplete,
     * 3 junk) plus 8 for the Corrupted version.
     */
    val reward = find("isle_gauntlet_reward")

    /** Deaths and best times (in ticks, 0 for none yet) for each version. */
    val deaths = find("isle_gauntlet_deaths")
    val deaths_hm = find("isle_gauntlet_deaths_hm")
    val best = find("isle_gauntlet_best")
    val best_hm = find("isle_gauntlet_best_hm")

    /** The Corrupted Gauntlet completions, for the tasks that count them. */
    val kc = find("clog_kc_gauntlet_completion_count")
    val kc_hm = find("clog_kc_corrupted_gauntlet_completion_count")
}

/** Server-only Gauntlet variables. Saved with the player. Needs `packCache`. */
internal object GauntletVarpBuilder : VarpBuilder() {
    init {
        build("isle_gauntlet_bryn") {}
        build("isle_gauntlet_done") {}
        build("isle_gauntlet_done_hm") {}
        build("isle_gauntlet_in_run") {}
        build("isle_gauntlet_reward") {}
        build("isle_gauntlet_deaths") {}
        build("isle_gauntlet_deaths_hm") {}
        build("isle_gauntlet_best") {}
        build("isle_gauntlet_best_hm") {}
    }
}
