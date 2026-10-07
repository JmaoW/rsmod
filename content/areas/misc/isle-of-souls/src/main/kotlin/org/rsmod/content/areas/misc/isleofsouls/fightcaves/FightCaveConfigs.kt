package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias cave_npcs = FightCaveNpcs

typealias cave_locs = FightCaveLocs

typealias cave_objs = FightCaveObjs

typealias cave_seqs = FightCaveSeqs

typealias cave_spotanims = FightCaveSpotanims

typealias cave_timers = FightCaveTimers

typealias cave_queues = FightCaveQueues

object FightCaveNpcs : NpcReferences() {
    val tz_kih = find("tzhaar_fightcave_swarm_1a")
    val tz_kek = find("tzhaar_fightcave_swarm_2a")
    val tz_kek_spawn = find("tzhaar_fightcave_swarm_2spawn")
    val tok_xil = find("tzhaar_fightcave_swarm_3a")
    val yt_mejkot = find("tzhaar_fightcave_swarm_4a")
    val ket_zek = find("tzhaar_fightcave_swarm_5a")
    val jad = find("tzhaar_fightcave_swarm_boss")
    val yt_hurkot = find("tzhaar_fightcave_swarm_boss_cleric")

    /** TzHaar-Mej-Jal: stands by the entrance and, later, takes fire capes for the Inferno. */
    val gatekeeper = find("tzhaar_fightcave_master")
}

object FightCaveLocs : LocReferences() {
    val entrance = find("tzhaar_fightcave_wall_entrance")
    val exit = find("tzhaar_fightcave_wall_exit")
}

object FightCaveObjs : ObjReferences() {
    val fire_cape = find("tzhaar_cape_fire")
    val tokkul = find("tzhaar_token")
    val jad_pet = find("jad_pet")
}

object FightCaveSeqs : SeqReferences() {
    val tz_kih_attack = find("firebat_attack")
    val tz_kih_death = find("firebat_death")

    val tz_kek_attack = find("lavabeast_attack")
    val tz_kek_death = find("lavabeast_death")

    val tok_xil_melee = find("magmaquris_punch")
    val tok_xil_ranged = find("magmaquris_spine_attack")
    val tok_xil_death = find("magmaquris_death")

    val yt_mejkot_attack = find("lizard_cleric_attack")
    val yt_mejkot_heal = find("lizard_cleric_heal")
    val yt_mejkot_death = find("lizard_cleric_death")

    val ket_zek_melee = find("igniferum_attack")
    val ket_zek_magic = find("igniferum_ranged")
    val ket_zek_death = find("igniferum_death")

    val jad_melee = find("lordmagmus_attack")
    val jad_magic = find("lordmagmus_fire")
    val jad_ranged = find("lordmagmus_smash")
    val jad_death = find("lordmagmus_death")
}

object FightCaveSpotanims : SpotanimReferences() {
    val tok_xil_spine = find("tzhaar_spine_attack")
    val ket_zek_fireball = find("tzhaar_fire_launch_travel")
    val ket_zek_impact = find("tzhaar_fire_launch_impact")
    val jad_magic_cast = find("tzhaar_fire_spit_launch")
    val jad_magic_ball = find("tzhaar_fire_spit_travel")
    val jad_ranged_rock = find("tzhaar_rock_smash")
    val heal = find("tzhaar_heal")
}

object FightCaveTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("fight_cave_tick")
}

object FightCaveQueues : QueueReferences() {
    /** Shows TzHaar-Mej-Jal's send-off once a player has been put into the cave. */
    val enter = find("fight_cave_enter")

    /** Shows TzHaar-Mej-Jal's congratulations when Jad has died. */
    val win = find("fight_cave_win")
}

/**
 * Gives the cave monsters what they need to chase a player across the whole cave and, for the ones
 * with a ranged or magic attack, to stop and shoot from a distance. The engine keeps a chase going
 * only while the target is within `maxRange + attackRange` of where the npc spawned, and reads the
 * distance a npc attacks from out of its type, so both are set here. Needs `packCache`.
 */
internal object FightCaveNpcEdits : NpcEditor() {
    init {
        for (type in
            listOf(
                cave_npcs.tz_kih,
                cave_npcs.tz_kek,
                cave_npcs.tz_kek_spawn,
                cave_npcs.yt_mejkot,
                cave_npcs.yt_hurkot,
            )) {
            edit(type) { chase() }
        }
        edit(cave_npcs.tok_xil) {
            chase()
            attackRange = TOK_XIL_RANGE
        }
        edit(cave_npcs.ket_zek) {
            chase()
            attackRange = KET_ZEK_RANGE
        }
        edit(cave_npcs.jad) {
            chase()
            attackRange = JAD_RANGE
        }
    }

    private fun org.rsmod.api.type.script.dsl.NpcPluginBuilder.chase() {
        maxRange = CAVE_LEASH
        giveChase = true
        defaultMode = NpcMode.None
    }

    /** Further than any two tiles of the cave are apart. */
    const val CAVE_LEASH = 70
    const val TOK_XIL_RANGE = 8
    const val KET_ZEK_RANGE = 10
    const val JAD_RANGE = 14
}
