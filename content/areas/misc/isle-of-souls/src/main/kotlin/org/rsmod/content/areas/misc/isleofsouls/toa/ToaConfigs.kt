package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.game.type.varp.VarpType

typealias toa_locs = ToaLocs

typealias toa_interfaces = ToaInterfaces

typealias toa_components = ToaComponents

typealias toa_varbits = ToaVarBits

typealias toa_varps = ToaVarps

typealias toa_npcs = ToaNpcs

typealias toa_invs = ToaInvs

object ToaInterfaces : InterfaceReferences() {
    /** The raid's heads-up display: party health orbs, raid level, path levels, the timer. */
    val hud = find("toa_hud")

    /** The lobby's party overlay: who is in the party. */
    val lobby = find("toa_lobby")

    /** The obelisk's list of recruiting parties. */
    val party_list = find("toa_partylist")

    /** The panel for one party: members, applicants, invocations, summary. */
    val party_details = find("toa_partydetails")

    /** The invocation board's read-only list. */
    val invocations = find("toa_invocations")
    val scoreboard = find("toa_scoreboard")
    val chests = find("toa_chests")
}

object ToaComponents : ComponentReferences() {
    /** The party list's buttons (refresh, my party, filter) and its rows are children of these. */
    val party_list_buttons = find("toa_partylist:contents")
    val party_list_rows = find("toa_partylist:list")

    /** The details panel's pause buttons, children 0-97: tabs, members, applicants, invocations. */
    val details_buttons = find("toa_partydetails:pausebuttons")
    val details_reward_info = find("toa_partydetails:raid_level_info")
    val details_presets = find("toa_partydetails:presets_button_click")

    val lobby_names = find("toa_lobby:names")

    /** The reward interface: its three buttons and the loot grid. */
    val chests_bank = find("toa_chests:bank")
    val chests_inventory = find("toa_chests:inventory")
    val chests_discard = find("toa_chests:discard")
    val chests_items = find("toa_chests:items")
}

object ToaInvs : InvReferences() {
    /** What a player's chest holds until it is taken; the reward interface draws this inventory. */
    val chests = find("toa_chests")
}

/** The reward inventory is saved with the player, so loot waits for a player who logs out. */
internal object ToaInvEdits : InvEditor() {
    init {
        edit(toa_invs.chests) {
            scope = InvScope.Perm
            protect = false
        }
    }
}

object ToaVarBits : VarBitReferences() {
    /** 0 when the cavities hold nothing, else the stored pickaxe's place in the list plus one. */
    val pickaxe_stored = find("toa_pickaxe_stored")

    /** 0 outside a party, 1 in one; the lobby overlay shows the names when set. */
    val party_status = find("toa_client_partystatus")
    val partylist_filter = find("toa_partylist_filter")
    val preset_selected = find("toa_preset_selected")
    val scoreboard_tab = find("toa_scoreboard_tab")

    /**
     * The raid HUD's raid level, current path (0 nexus, 1-4 a path, 5-6 the Wardens) and levels.
     */
    val raid_level = find("toa_client_raid_level")
    val current_path = find("toa_client_current_path")
    val level_scabaras = find("toa_client_scabaras_level")
    val level_het = find("toa_client_het_level")
    val level_apmeken = find("toa_client_apmeken_level")
    val level_crondis = find("toa_client_crondis_level")

    /** Each party slot's health orb on the HUD, and which slot is the viewer's. */
    val party_slot = find("toa_client_partyslot")
    val member_orbs: List<VarBitType> = (0..7).map { find("toa_client_p$it") }

    /** Set when the raid chest holds a reward for the player. */
    val should_have_loot = find("toa_should_have_loot")

    /** The vault: the sarcophagus (and its barrier and glow) turn purple when it holds a unique. */
    val vault_sarcophagus = find("toa_vault_sarcophagus")
    val vault_chests: List<VarBitType> = (0..7).map { find("toa_vault_chest_$it") }

    fun pathLevel(path: ToaPath): VarBitType =
        when (path) {
            ToaPath.Scabaras -> level_scabaras
            ToaPath.Het -> level_het
            ToaPath.Apmeken -> level_apmeken
            ToaPath.Crondis -> level_crondis
        }
}

object ToaVarps : VarpReferences() {
    /** The pickaxe the statue cavities keep for a player, shown by [ToaVarBits.pickaxe_stored]. */
    val pickaxe_storage = find("toa_pickaxe_storage")

    /** Where the lobby shows a player's party: 0 for one they are in, -1 for none. */
    val current_party = find("toa_mycontroller")

    /** Each player's share of the reward points, which the chest interface reads. */
    val personal_contribution = find("toa_personal_contribution")

    /** The saved invocation presets: five of three words each. */
    val presets: List<List<VarpType>> =
        (1..5).map { slot -> listOf("a", "b", "c").map { find("toa_invocations_preset_$slot$it") } }

    /**
     * Per mode (Entry, Normal, Expert): completions, which are the collection log's own kill
     * counts, and attempts and deaths, saved with the player.
     */
    val completions: List<VarpType> =
        listOf(
            find("clog_kc_tombs_of_amascut_entry_completions"),
            find("clog_kc_tombs_of_amascut_completions"),
            find("clog_kc_tombs_of_amascut_expert_completions"),
        )
    val attempts: List<VarpType> = ToaMode.entries.map { find("isle_toa_attempts_${it.key}") }
    val deaths: List<VarpType> = ToaMode.entries.map { find("isle_toa_deaths_${it.key}") }
}

/** Server-only Tombs of Amascut variables. Saved with the player. Needs `packCache`. */
internal object ToaVarpBuilder : VarpBuilder() {
    init {
        for (mode in ToaMode.entries) {
            build("isle_toa_attempts_${mode.key}") {}
            build("isle_toa_deaths_${mode.key}") {}
        }
    }
}

object ToaLocs : LocReferences() {
    /** The pyramid's small stair door ("Enter"), which the isle's entrance is made from. */
    val pyramid_entry = find("toa_entrance_open")

    /**
     * The lobby: obelisk ("Inspect"), invocation board ("Read"), the way into the tombs and out.
     */
    val grouping_board = find("toa_grouping_board")
    val invocation_board = find("toa_invocation_board")
    val lobby_entry = find("toa_lobby_raid_entry")
    val lobby_exit = find("toa_lobby_exit")
    val lobby_scoreboard = find("toa_scoreboard")

    /** The lobby's bank camel ("Bank"): a multi-loc, so its ops arrive on the parent. */
    val bank_camel = find("toa_bank_camel_parent")

    /** The doors that lead out of a room: "Leave". */
    val exit_door = find("toa_door_exit")
    val exit_wardens = find("toa_door_exit_wardens")

    /** The Nexus's doors: "Enter" or "Quick-Enter". The closed and completed forms follow in id. */
    val nexus_scabaras = find("toa_nexus_scabaras_door")
    val nexus_het = find("toa_nexus_het_door")
    val nexus_apmeken = find("toa_nexus_apmeken_door")
    val nexus_crondis = find("toa_nexus_crondis_door")
    val nexus_wardens = find("toa_nexus_wardens_door")

    /** The dimmed path doors still offer "Enter"; only the shut Wardens door has no options. */
    val nexus_scabaras_unselected = find("toa_nexus_scabaras_door_unselected")
    val nexus_het_unselected = find("toa_nexus_het_door_unselected")
    val nexus_apmeken_unselected = find("toa_nexus_apmeken_door_unselected")
    val nexus_crondis_unselected = find("toa_nexus_crondis_door_unselected")
    val nexus_wardens_open = find("toa_nexus_wardens_door_open")

    /** The vault's chests, as each player sees them: theirs, someone else's, opened, emptied. */
    val vault_chest_mine = find("toa_vault_chest_mine_standard")
    val vault_chest_other = find("toa_vault_chest_notmine_standard")
    val vault_chest_open =
        listOf(
            find("toa_vault_chest_open"),
            find("toa_vault_chest_open02"),
            find("toa_vault_chest_open03"),
        )
    val vault_chest_empty =
        listOf(
            find("toa_vault_chest_empty"),
            find("toa_vault_chest_empty02"),
            find("toa_vault_chest_empty03"),
        )

    /** Osmumten's sarcophagus: shut, glowing with a unique (Open), and opened (Search). */
    val sarcophagus_rare = find("toa_vault_sarcophagus_closed_rare")
    val sarcophagus_open = find("toa_vault_sarcophagus_open")

    /** The lobby's chest for a raid's loot that was not taken: "Claim" while it holds something. */
    val rewards_niche = find("toa_rewards_chest_lobby_open")

    /** Where a pickaxe is stored in Het rooms. */
    val het_statue = find("toa_het_statue_parent")

    /** The barrier into a puzzle or the way out of a won one: "Pass", "Quick-Pass". */
    val path_barrier = find("toa_path_barrier")

    /** The doors from a won puzzle room to its boss: "Enter", "Quick-Enter". */
    val continue_door = find("toa_door_continue")
    val continue_scabaras = find("toa_scabaras_continue")
    val continue_apmeken = find("toa_path_apmeken_continue")
    val continue_crondis = find("toa_path_crondis_continue")

    /** The boss rooms teleport crystals: "Use", "Quick-Use". */
    val crystal_akkha = find("akkha_teleport")
    val crystal_baba = find("baba_teleport")
    val crystal_kephri = find("kephri_teleport")
    val crystal_zebak = find("zebak_teleport")

    /** The ways out of the tombs from inside a path: "Exit", "Leave". */
    val exit_akkha = find("toa_entrance_akkha01")
    val exit_baba = find("toa_entrance_baba02")
    val exit_kephri = find("toa_entrance_kephri_main")
    val exit_zebak = find("toa_zebak_exit")
    val exit_crondis = find("toa_crondis_exit")

    /** The crocodile-room water source on Crondis's path: "Fill". */
    val crondis_waterfall = find("toa_crondis_water_source")
}

object ToaNpcs : NpcReferences() {
    /** What a dead player looks like until their room is won or lost. */
    val ghost = find("toa_player_ghost")

    /** Osmumten, who stands in a room once its challenge is won ("Talk-to", "Proceed"). */
    val osmumten_vis = find("toa_osmumten_vis")
    val osmumten_kephri = find("toa_osmumten_kephri")
    val osmumten_akkha = find("toa_osmumten_akkha")
    val osmumten_baba = find("toa_osmumten_baba")
    val osmumten_zebak = find("toa_osmumten_zebak")

    /** Osmumten, who sees the party out of the vault. */
    val osmumten_vault = find("toa_osmumten_vault")
}
