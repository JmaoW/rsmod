package org.rsmod.content.generic.npcs.abyssal

import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

internal typealias abyssal_npcs = AbyssalNpcs

internal typealias abyssal_seqs = AbyssalSeqs

internal typealias abyssal_spotanims = AbyssalSpotanims

object AbyssalNpcs : NpcReferences() {
    val slayer_abyssal = find("slayer_abyssal")
    val slayer_abyssal_strongholdcave = find("slayer_abyssal_strongholdcave")
    val arceuus_reanimated_abyssal = find("arceuus_reanimated_abyssal")
    val kourend_abyssal = find("kourend_abyssal")
    val superior_abyssal_demon = find("superior_abyssal_demon")
    val wild_cave_abyssal = find("wild_cave_abyssal")
    val deadman_breach_superior_abyssal_demon = find("deadman_breach_superior_abyssal_demon")
    val league_superior_abyssal_demon = find("league_superior_abyssal_demon")
}

object AbyssalSeqs : SeqReferences() {
    val teleport = find("abyssal_demon_teleport")
}

object AbyssalSpotanims : SpotanimReferences() {
    val teleport = find("abyssal_demon_teleport")
}
