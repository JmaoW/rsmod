package org.rsmod.content.skills.magic.utility.configs

import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias arceuus_npcs = ArceuusNpcs

typealias arceuus_objs = ArceuusObjs

typealias arceuus_seqs = ArceuusSeqs

typealias arceuus_spotanims = ArceuusSpotanims

/** The thralls the Resurrect spells summon. */
object ArceuusNpcs : NpcReferences() {
    val ghost_lesser = find("arceuus_thrall_ghost_lesser")
    val ghost_superior = find("arceuus_thrall_ghost_superior")
    val ghost_greater = find("arceuus_thrall_ghost_greater")
    val skeleton_lesser = find("arceuus_thrall_skeleton_lesser")
    val skeleton_superior = find("arceuus_thrall_skeleton_superior")
    val skeleton_greater = find("arceuus_thrall_skeleton_greater")
    val zombie_lesser = find("arceuus_thrall_zombie_lesser")
    val zombie_superior = find("arceuus_thrall_zombie_superior")
    val zombie_greater = find("arceuus_thrall_zombie_greater")
}

object ArceuusObjs : ObjReferences() {
    val fiendish_ashes = find("fiendish_ashes")
    val vile_ashes = find("vile_ashes")
    val malicious_ashes = find("malicious_ashes")
    val abyssal_ashes = find("abyssal_ashes")
    val infernal_ashes = find("infernal_ashes")
}

object ArceuusSeqs : SeqReferences() {
    val resurrect = find("human_spellcast_resurrect")
    val mark_of_darkness = find("mark_of_darkness")
    val death_charge = find("death_charge")
    val offering = find("human_cast_offering")
    val degrime = find("human_spellcast_degrime")
    val necromancy = find("arceuus_necromancy_anim")

    /** The thralls' own attack and appearance animations, which their npcs do not carry. */
    val thrall_ghost_attack = find("ghost_update_tendrill_attack")
    val thrall_skeleton_attack = find("skeleton_update_champion_attack")
    val thrall_zombie_attack = find("zombie_update_attack_normal")
    val thrall_ghost_spawn = find("ghost_update_thrall_spawn")
    val thrall_skeleton_spawn = find("skeleton_update_thrall_spawn")
}

object ArceuusSpotanims : SpotanimReferences() {
    val mark_of_darkness = find("mark_of_darkness_cast_spotanim")
    val death_charge = find("death_charge_cast_spotanim")
}
