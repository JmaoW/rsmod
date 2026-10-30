package org.rsmod.content.areas.misc.isleofsouls.dagannothkings

import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias dk_npcs = DkNpcs

typealias dk_locs = DkLocs

typealias dk_seqs = DkSeqs

typealias dk_spotanims = DkSpotanims

typealias dk_timers = DkTimers

object DkNpcs : NpcReferences() {
    /** Dagannoth Rex, the melee king. The drop table and the tasks know them all by name. */
    val rex = find("dagcave_melee_boss")

    /** Dagannoth Prime, the magic king. */
    val prime = find("dagcave_magic_boss")

    /** Dagannoth Supreme, the ranged king. */
    val supreme = find("dagcave_ranged_boss")

    /**
     * The spinolyps in the water round the lair. The cache has three of them, each with a
     * "suspicious water" twin (the `_invisible` types) that it turns into when it dives; which twin
     * belongs to which is read from the spawn list, where the same spot holds one or the other.
     */
    val spinolyp_a = find("dagannoth_water_creature_1x1")
    val spinolyp_b = find("dagannoth_water_creature_1x12")
    val spinolyp_c = find("dagannoth_water_creature_1x13")
    val water_a = find("dagannoth_water_creature_1x1_invisible")
    val water_b = find("dagannoth_water_creature_1x1_invisible2")
    val water_c = find("dagannoth_water_creature_1x1_invisible3")
}

object DkLocs : LocReferences() {
    /**
     * The Kings' ladder down: "Standard", "Slayer" and "Peek". The real one is on the fifth level
     * of the Waterbirth Island Dungeon; the isle has a copy of it on the field south of the plaza.
     * This is the "normal" child of the multiloc `dagexp_bossroomladder_down`, which shows it
     * without the "Private" option.
     */
    val entrance = find("dagexp_bossroomladder_down_normal")

    /** The Kings' ladder up, inside the lair: "Climb-up". */
    val exit = find("dagexp_bossroomladder_up")
}

object DkSeqs : SeqReferences() {
    val rex_attack = find("dagannoth_meganoth_attack_melee")
    val prime_attack = find("dagannoth_meganoth_attack_mage")
    val supreme_attack = find("dagannoth_meganoth_attack_range")
    val king_death = find("dagannoth_meganoth_death")

    val spinolyp_attack = find("dagannoth_water_creature_attack")
    val spinolyp_dive = find("dagannoth_water_creature_go_down")
    val spinolyp_surface = find("dagannoth_water_creature_spring_up")
    val spinolyp_death = find("dagannoth_water_creature_death")

    val climb_ladder = find("human_reachforladder")
}

object DkSpotanims : SpotanimReferences() {
    /** Prime's attack "resembles Water Wave" (wiki). */
    val prime_travel = find("waterwave_travel")
    val prime_impact = find("waterwave_impact")

    /** Supreme's spine. */
    val supreme_travel = find("dagannoth_spine_spotanim_travel")

    /** A spinolyp's magic "takes the appearance of Water Strike" (wiki). */
    val spinolyp_magic_travel = find("waterstrike_travel")
    val spinolyp_magic_impact = find("waterstrike_impact")

    /** A spinolyp's ranged attack "resembles the one used by ranged-using dagannoth" (wiki). */
    val spinolyp_ranged_travel = find("horror_spines_travel")
}

object DkTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("dk_tick")
}

/**
 * What the kings and spinolyps need in a team's lair. The kings are aimed by [DagannothKings] (the
 * run picks their targets, as with the other team bosses); these edits let them chase across their
 * side of the lair, shoot from a distance (Prime and Supreme), and count every player's damage for
 * the drops. The spinolyps never move. Needs `packCache`.
 */
internal object DkNpcEdits : NpcEditor() {
    init {
        for (king in DkKing.entries) {
            edit(king.type) {
                defaultMode = NpcMode.None
                maxRange = DkKing.LEASH
                wanderRange = DkKing.WANDER
                giveChase = true
                heroCount = HERO_SLOTS
                if (king.ranged) {
                    attackRange = DkKing.ATTACK_RANGE
                }
            }
        }
        for (spot in DkSpinolyps.kinds) {
            edit(spot.visible) {
                defaultMode = NpcMode.None
                wanderRange = 0
                attackRange = DkSpinolyps.RANGE
            }
            edit(spot.submerged) {
                defaultMode = NpcMode.None
                wanderRange = 0
            }
        }
    }

    /** How many players' damage is kept to decide who gets the drops. */
    const val HERO_SLOTS = 16
}
