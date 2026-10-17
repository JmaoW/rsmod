package org.rsmod.content.areas.misc.isleofsouls.kalphite

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.game.entity.npc.NpcMode

typealias kalphite_npcs = KalphiteNpcs

typealias kalphite_locs = KalphiteLocs

typealias kalphite_seqs = KalphiteSeqs

typealias kalphite_spotanims = KalphiteSpotanims

typealias kalphite_timers = KalphiteTimers

typealias kalphite_varbits = KalphiteVarbits

object KalphiteNpcs : NpcReferences() {
    /** The crawling first form. It is also the type the drop table and the tasks know her by. */
    val queen = find("kalphite_queen")

    /** The airborne second form. She is the same npc as the first, shown as this type. */
    val airborne = find("kalphite_flyingqueen")

    /** The workers that hatch from the cocoons in her chamber. */
    val worker = find("kalphite_worker_chamber")
}

object KalphiteLocs : LocReferences() {
    /**
     * The rope down into the chamber, in the form it takes once a rope is tied to it: "Climb-down"
     * and "Look-inside". The cache's entrance shows this form to a player whose
     * [KalphiteVarbits.entrance] is 1.
     */
    val entrance = find("kalphite_chamber_entrance_withrope_normal")

    /** The same, with a normal and a private option; the isle only has the private one. */
    val entrance_private = find("kalphite_chamber_entrance_withrope_private")

    /** The rope inside the chamber: "Climb-up". */
    val exit = find("kalphite_chamber_exit")
}

object KalphiteSeqs : SeqReferences() {
    /** Crawling form: the spine shot and the magic glow share one animation, the jaws are melee. */
    val crawling_ranged = find("kalphite_update_queen_ranged_attack")
    val crawling_melee = find("kalphite_update_queen_jaws_attack")

    /** Airborne form: tsps plays the first for melee and ranged and the second for magic. */
    val airborne_ranged = find("kalphite_update_flying_queen_ranged_attack")
    val airborne_magic = find("kalphite_update_flying_queen_stinger_attack")

    /** The crawling form splits open and the airborne queen rises out of it. */
    val emerge = find("kalphite_update_flying_queen_emerging")

    val airborne_death = find("kalphite_update_flying_queen_death")

    /** A worker's jaws. */
    val worker_attack = find("kalphite_update_jaws_attack")
}

object KalphiteSpotanims : SpotanimReferences() {
    /** The glow on her as she casts, the glow that flies, and where it lands. */
    val magic_cast = find("kalphite_queen_glow")
    val magic_travel = find("kalphite_glow_travel")
    val magic_impact = find("kalphite_glow_impact")

    /** The barbed spine of her ranged attack. */
    val spine = find("kalphite_queen_spine")

    /** The shell cracking as the second form comes out. */
    val emerge = find("kalphite_update_flying_queen_emerge_spotanim")
}

object KalphiteTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("kalphite_queen_tick")
}

object KalphiteVarbits : VarBitReferences() {
    /**
     * Which form of the chamber entrance a player sees: 0 is the bare tunnel (a rope must be used
     * on it), 1 is the roped tunnel with "Climb-down", 2 the one with a normal and a private
     * option.
     */
    val entrance = find("kalphite_chamber_entrance")
}

/**
 * What the Kalphite Queen and her workers need to fight. She is moved and aimed by [KalphiteQueen]
 * (she never hunts or retaliates on her own); the workers chase across the whole chamber and bite
 * with their jaws. Needs `packCache`.
 */
internal object KalphiteNpcEdits : NpcEditor() {
    init {
        for (type in listOf(kalphite_npcs.queen, kalphite_npcs.airborne)) {
            edit(type) { defaultMode = NpcMode.None }
        }
        edit(kalphite_npcs.worker) {
            maxRange = CHAMBER_LEASH
            giveChase = true
            defaultMode = NpcMode.None
            attackRange = 1
            param[params.attack_anim] = kalphite_seqs.worker_attack
        }
    }

    /** Further than any two tiles of the chamber are apart, so a chase is never dropped. */
    const val CHAMBER_LEASH = 60
}
