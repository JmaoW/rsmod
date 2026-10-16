package org.rsmod.content.skills.magic.spell.attacks.standard

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences

typealias special_seqs = SpecialSpellSeqs

typealias special_spotanims = SpecialSpellSpotanims

/** The cast animations of the standard spellbook's non-elemental combat spells. */
object SpecialSpellSeqs : SeqReferences() {
    val cast_confuse = find("human_castconfuse")
    val cast_confuse_staff = find("human_castconfuse_staff")
    val cast_weaken = find("human_castweaken")
    val cast_weaken_staff = find("human_castweaken_staff")
    val cast_curse = find("human_castcurse")
    val cast_curse_staff = find("human_castcurse_staff")
    val cast_enfeeble = find("human_castenfeeble")
    val cast_enfeeble_staff = find("human_castenfeeble_staff")
    val cast_stun = find("human_caststun")
    val cast_stun_staff = find("human_caststun_staff")
    val cast_entangle = find("human_castentangle")
    val cast_entangle_staff = find("human_castentangle_staff")
    val cast_crumble = find("human_castcrumbleundead")
    val cast_crumble_staff = find("human_castcrumbleundead_staff")
    val cast_iban = find("human_castibanblast")
    val cast_god = find("human_casting")
    val cast_magic_dart = find("slayer_magicdart_cast")
}

object SpecialSpellSpotanims : SpotanimReferences() {
    val confuse_casting = find("confuse_casting")
    val confuse_travel = find("confuse_travel")
    val confuse_impact = find("confuse_impact")
    val weaken_casting = find("weaken_casting")
    val weaken_travel = find("weaken_travel")
    val weaken_impact = find("weaken_impact")
    val curse_casting = find("curse_casting")
    val curse_travel = find("curse_travel")
    val curse_impact = find("curse_impact")
    val vulnerability_casting = find("vulnerability_casting")
    val vulnerability_travel = find("vulnerability_travel")
    val vulnerability_impact = find("vulnerability_impact")
    val enfeeble_casting = find("enfeeble_casting")
    val enfeeble_travel = find("enfeeble_travel")
    val enfeeble_impact = find("enfeeble_impact")
    val stun_casting = find("stun_casting")
    val stun_travel = find("stun_travel")
    val stun_impact = find("stun_impact")
    val entangle_casting = find("entangle_casting")
    val entangle_travel = find("entangle_travel")
    val entangle_impact = find("entangle_impact")
    val snare_impact = find("snare_impact")
    val bind_impact = find("bind_impact")
    val crumble_casting = find("crumbleundead_casting")
    val crumble_travel = find("crumbleundead_travel")
    val crumble_impact = find("crumbleundead_impact")
    val iban_casting = find("ibanblast_casting")
    val iban_travel = find("ibanblast_travel")
    val iban_impact = find("ibanblast_impact")
    val magic_dart_travel = find("slayer_magicdart_travel")
    val magic_dart_impact = find("slayer_magicdart_impact")
    val saradomin_strike = find("saradomin_lightning")
    val claws_of_guthix = find("gunthix_claw")
    val flames_of_zamorak = find("zamorak_flame")
}
