package org.rsmod.content.skills.magic.spell.attacks.arceuus

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias arceuus_seqs = ArceuusSpellSeqs

typealias arceuus_spotanims = ArceuusSpellSpotanims

object ArceuusSpellSeqs : SeqReferences() {
    val cast_grasp = find("human_spellcast_grasp")
    val cast_demonbane = find("human_spellcast_demonbane")
}

object ArceuusSpellSpotanims : SpotanimReferences() {
    val ghostly_grasp_cast = find("ghostly_grasp_cast_spotanim")
    val ghostly_grasp_hit = find("ghostly_grasp_hit_spotanim")
    val skeletal_grasp_cast = find("skeletal_grasp_cast_spotanim")
    val skeletal_grasp_hit = find("skeletal_grasp_hit_spotanim")
    val undead_grasp_cast = find("undead_grasp_cast_spotanim")
    val undead_grasp_hit = find("undead_grasp_hit_spotanim")
    val inferior_demonbane_cast = find("inferior_demonbane_cast_spotanim")
    val inferior_demonbane_hit = find("inferior_demonbane_hit_spotanim")
    val superior_demonbane_cast = find("superior_demonbane_cast_spotanim")
    val superior_demonbane_hit = find("superior_demonbane_hit_spotanim")
    val dark_demonbane_cast = find("dark_demonbane_cast_spotanim")
    val dark_demonbane_hit = find("dark_demonbane_hit_spotanim")
}
