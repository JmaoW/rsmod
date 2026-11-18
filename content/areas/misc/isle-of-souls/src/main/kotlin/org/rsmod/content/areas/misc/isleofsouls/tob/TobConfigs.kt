package org.rsmod.content.areas.misc.isleofsouls.tob

import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.varbit.VarBitType

typealias tob_locs = TobLocs

typealias tob_npcs = TobNpcs

typealias tob_objs = TobObjs

typealias tob_interfaces = TobInterfaces

typealias tob_components = TobComponents

typealias tob_varbits = TobVarBits

typealias tob_varps = TobVarps

typealias tob_invs = TobInvs

typealias tob_seqs = TobSeqs

object TobInterfaces : InterfaceReferences() {
    /** The party HUD: names, each member's health, the room's bar and the fades. */
    val hud = find("tob_hud")

    /** The notice board's list of parties. */
    val party_list = find("tob_partylist")

    /** One party: members, applicants and the leader's preferences. */
    val party_details = find("tob_partydetails")

    /** The supply chests after the Bloat and Sotetseg. */
    val supplies = find("tob_midway_stores")

    /** A monumental chest's (or the Rewards Chest's) loot. */
    val chests = find("tob_chests")
}

object TobComponents : ComponentReferences() {
    /** The party list's Refresh, My party and Friends/Clan buttons resume children 0-2 of this. */
    val party_list_buttons = find("tob_partylist:contents")

    /** The list's 45 rows, each with a "View party" op (enum 1937). */
    val party_list_rows = (0 until 45).map { find("tob_partylist:%02d".format(it)) }

    /** Every button of the party panel resumes a child of this (enum 1939, scripts 2319/2330). */
    val details_buttons = find("tob_partydetails:universe")

    val chests_bank = find("tob_chests:bank")
    val chests_inventory = find("tob_chests:inventory")
    val chests_discard = find("tob_chests:discard")
    val chests_items = find("tob_chests:items")

    val supplies_items = find("tob_midway_stores:items")
}

object TobInvs : InvReferences() {
    /** What a player's monumental chest holds until it is taken (the loot interface draws it). */
    val chests = find("tob_chests")
}

/** The loot waits with the player through a logout, for the Rewards Chest in Ver Sinhaza. */
internal object TobInvEdits : InvEditor() {
    init {
        edit(tob_invs.chests) {
            scope = InvScope.Perm
            protect = false
        }
    }
}

object TobVarBits : VarBitReferences() {
    /** 0 without a party, 1 in a party in Ver Sinhaza, 2 inside the theatre. */
    val party_status = find("tob_client_partystatus")

    /** The viewer's own slot on the HUD, counting from 1. */
    val party_slot = find("tob_client_partyslot")

    /** Each slot: 0 empty, 1-27 health, 30 dead, 31 in another room. */
    val member_slots: List<VarBitType> = (0..4).map { find("tob_client_p$it") }

    /** The room's bar: 0 none, 1 regular, 2 greyed, 3 cyan; its value and maximum (1000). */
    val bar_type = find("tob_client_waveprogress_type")
    val bar_value = find("tob_client_waveprogress_val")
    val bar_max = find("tob_client_waveprogress_max")

    /** The monumental chests: 0 someone else's, 1 theirs with a unique, 2 mine, 3 mine, unique. */
    val vault_chests: List<VarBitType> = (0..4).map { find("tob_treasureroom_chest_$it") }

    val supply_points = find("tob_midwaychest_points")
    val supply_stamina = find("tob_midwaychest_staminapot")

    /** The Rewards Chest by the bank in Ver Sinhaza shows while this is set. */
    val should_have_loot = find("tob_should_have_loot")
    val list_filter = find("tob_lobby_friends_filter")
}

object TobVarps : VarpReferences() {
    /** -1 outside a party, 0 in one: the party list's "My party" button. */
    val my_controller = find("tob_mycontroller")

    /** The game's own completion count (Normal mode). */
    val completions = find("total_completed_theatreofblood")

    /** The collection log's count, which this server keeps alongside. */
    val clog_completions = find("clog_kc_theatre_of_blood_completions")
}

object TobLocs : LocReferences() {
    val entrance = find("tob_surface_raid_entrance")
    val notice_board = find("tob_surface_notice_board")
    val retrieval_chest = find("tob_surface_gravestone_chest")
    val rewards_chest = find("tob_rewards_chest_lobby_multi")

    val barrier = find("tob_arena_barrier")
    val passage = find("tob_dungeon_walkway_exit_clickbox")
    val xarpus_exit = find("tob_dungeon_xarpus_arena_door_exit")
    val treasure_door = find("tob_dungeon_verzik_throne_door_opened")
    val orator = find("tob_male_orator")
    val orator_seated = find("tob_female_orator")
    val supply_closed = find("tob_midway_chest_closed")
    val supply_open = find("tob_midway_chest_open")
    val teleport_crystal = find("tob_treasureroom_teleportout")
    val vault_stairs = find("tob_treasureroom_stairsup")
    val vault_chests = (0..4).map { find("tob_treasureroom_chest_loc$it") }
    val vault_chest_open = find("tob_treasureroom_chest_open")

    /** The isle's way to Ver Sinhaza: a portal in the Soul Wars lobby. */
    val isle_portal = find("1v1arena_portal_lower")
}

object TobNpcs : NpcReferences() {
    val stranger = find("tob_stranger")
}

object TobObjs : ObjReferences() {
    val escape_crystal = find("tob_teleport")
    val dawnbringer = find("verzik_special_weapon")
    val coins = find("coins")
    val onion = find("onion")
}

object TobSeqs : SeqReferences() {
    /** The dead's stance in the jail (RuneLite's TOB_PURGATORY_STANCE). */
    val purgatory = find("tob_purgatory_stance")
    val open_chest = find("human_openchest")
}
