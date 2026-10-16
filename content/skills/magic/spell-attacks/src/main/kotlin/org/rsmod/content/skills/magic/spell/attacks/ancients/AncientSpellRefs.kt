package org.rsmod.content.skills.magic.spell.attacks.ancients

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences

typealias ancient_seqs = AncientSpellSeqs

typealias ancient_spotanims = AncientSpellSpotanims

typealias ancient_synths = AncientSpellSynths

object AncientSpellSeqs : SeqReferences() {
    /** Rush and Blitz. */
    val cast_single = find("zaros_casting")

    /** Burst and Barrage. */
    val cast_area = find("zaros_vertical_casting")
}

object AncientSpellSpotanims : SpotanimReferences() {
    val smoke_rush_travel = find("smoke_rush_travel")
    val smoke_rush_impact = find("smoke_rush_impact")
    val smoke_blitz_travel = find("smoke_blitz_travel")
    val smoke_blitz_impact = find("smoke_blitz_impact")
    val smoke_burst_travel = find("smoke_burst_travel")
    val smoke_burst_impact = find("smoke_burst_impact")
    val smoke_barrage_travel = find("smoke_barrage_travel")
    val smoke_barrage_impact = find("smoke_barrage_impact")

    val shadow_rush_travel = find("shadow_rush_travel")
    val shadow_rush_impact = find("shadow_rush_impact")
    val shadow_blitz_travel = find("shadow_blitz_travel")
    val shadow_blitz_impact = find("shadow_blitz_impact")
    val shadow_burst_impact = find("shadow_burst_impact")
    val shadow_barrage_impact = find("shadow_barrage_impact")

    val blood_rush_travel = find("blood_rush_travel")
    val blood_rush_impact = find("blood_rush_impact")
    val blood_blitz_travel = find("blood_blitz_travel")
    val blood_blitz_impact = find("blood_blitz_impact")
    val blood_burst_impact = find("spell_blood_burst_impact")
    val blood_barrage_impact = find("spell_blood_barrage_impact")

    val ice_rush_travel = find("ice_rush_travel")
    val ice_rush_impact = find("ice_rush_impact")
    val ice_blitz_travel = find("ice_blitz_travel")
    val ice_blitz_impact = find("ice_blitz_impact")
    val ice_burst_travel = find("ice_burst_travel")
    val ice_burst_impact = find("ice_burst_impact")
    val ice_barrage_travel = find("ice_barrage_travel")
    val ice_barrage_impact = find("ice_barrage_impact")
}

object AncientSpellSynths : SynthReferences() {
    val smoke_cast = find("smoke_cast")
    val smoke_rush_impact = find("smoke_rush_impact")
    val smoke_burst_impact = find("smoke_burst_impact")
    val smoke_blitz_impact = find("smoke_blitz_impact")
    val smoke_barrage_impact = find("smoke_barrage_impact")

    val shadow_cast = find("shadow_cast")
    val shadow_rush_impact = find("shadow_rush_impact")
    val shadow_burst_impact = find("shadow_burst_impact")
    val shadow_blitz_impact = find("shadow_blitz_impact")
    val shadow_barrage_impact = find("shadow_barrage_impact")

    val blood_cast = find("blood_cast")
    val blood_rush_impact = find("blood_rush_impact")
    val blood_burst_impact = find("blood_burst_impact")
    val blood_blitz_impact = find("blood_blitz_impact")
    val blood_barrage_impact = find("blood_barrage_impact")

    val ice_cast = find("ice_cast")
    val ice_rush_impact = find("ice_rush_impact")
    val ice_burst_impact = find("ice_burst_impact")
    val ice_blitz_impact = find("ice_blitz_impact")
    val ice_barrage_impact = find("ice_barrage_impact")
}
