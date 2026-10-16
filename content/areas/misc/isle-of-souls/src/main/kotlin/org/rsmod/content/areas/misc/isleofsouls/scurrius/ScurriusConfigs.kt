package org.rsmod.content.areas.misc.isleofsouls.scurrius

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias scurrius_npcs = ScurriusNpcs

typealias scurrius_locs = ScurriusLocs

typealias scurrius_objs = ScurriusObjs

typealias scurrius_seqs = ScurriusSeqs

typealias scurrius_spotanims = ScurriusSpotanims

typealias scurrius_timers = ScurriusTimers

object ScurriusNpcs : NpcReferences() {
    /**
     * Scurrius in the free private instance: 500 hitpoints and the solo max hits (the cache has no
     * stats of its own for the public room's `rat_boss_normal`, which the isle does not use).
     */
    val boss = find("rat_boss_instance")

    /** The public room's Scurrius. Nothing spawns it; it is listed so a stray one still works. */
    val boss_public = find("rat_boss_normal")

    /** The level 46 giant rats he summons. */
    val rat = find("rat_boss_giant_rat")

    /**
     * An npc the lair never spawns: the non-MVP drop table is stored on it (`isle.toml`), since the
     * drop tables are looked up by npc type.
     */
    val non_mvp_drops = find("rat_boss_giant_rat_patrol_control")
}

object ScurriusLocs : LocReferences() {
    /** The broken bars east of the sewer entrance: "Climb-through (normal / private)", "Peek". */
    val entrance = find("rat_boss_entrance")

    /** The sewage water on the west side of the lair: "Cross" and "Quick-escape". */
    val exit = find("rat_boss_exit")

    /** The piles of food that Scurrius eats from, and players too ("Eat"). */
    val food_pile = find("rat_boss_food_pile")
    val food_pile_other = find("rat_boss_food_pile2")
}

object ScurriusObjs : ObjReferences() {
    val rat_bone_mace = find("rat_bone_mace")
    val rat_bone_bow = find("rat_bone_bow")
    val rat_bone_staff = find("rat_bone_staff")
    val big_bones = find("big_bones")
}

object ScurriusSeqs : SeqReferences() {
    val spawn = find("npc_rat_boss_spawn_01")
    val idle = find("npc_rat_boss_idle_01")

    /** The tail swipe has two animations (left and right tail, as the cache names them). */
    val tail_one = find("npc_rat_boss_attack_melee_01")
    val tail_two = find("npc_rat_boss_attack_melee_02")
    val ranged = find("npc_rat_boss_attack_ranged_01")
    val magic = find("npc_rat_boss_attack_magic_01")
    val stomp = find("npc_rat_boss_attack_stomp_01")
    val summon = find("npc_rat_boss_attack_summon_01")

    /** The same attacks while he is eating (his head is down at the food pile). */
    val ranged_eating = find("npc_rat_boss_feeding_attack_ranged_01")
    val magic_eating = find("npc_rat_boss_feeding_attack_magic_01")
    val stomp_eating = find("npc_rat_boss_feeding_attack_stomp_01")
    val summon_eating = find("npc_rat_boss_feeding_attack_summon_01")

    /** One bite of the food pile. */
    val eat_one = find("npc_rat_boss_feeding_idle_01")
    val eat_two = find("npc_rat_boss_feeding_idle_02")

    /** His death. The cache gives it no frame data, so its length is a constant (`DEATH_TICKS`). */
    val death = find("npc_rat_boss_death_01")

    /** The roar as he goes to the middle for the last third of the fight. */
    val enrage = find("npc_rat_boss_enrage_01")
}

object ScurriusSpotanims : SpotanimReferences() {
    /** The fur ball (ranged) and its impact. */
    val fur_ball = find("vfx_rat_boss_proj_ranged_01")
    val fur_impact = find("vfx_rat_boss_proj_ranged_impact_01")

    /** The bolt of electricity (magic) and its impact. */
    val bolt = find("vfx_rat_boss_proj_magic_01")
    val bolt_impact = find("vfx_rat_boss_proj_magic_impact_01")

    /** The falling bricks: a shadow on the ground that the rubble lands on. */
    val bricks = find("vfx_rat_boss_falling_debris_01")
}

object ScurriusTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("scurrius_tick")
}

/**
 * What Scurrius and his rats need to fight. He is moved and aimed by [Scurrius] (never hunts or
 * chases on his own, never turns on a player who hits him) and has no flinch animation, so the
 * cache's idle is used for it. The rats chase across the whole lair. Needs `packCache`.
 */
internal object ScurriusNpcEdits : NpcEditor() {
    init {
        for (type in listOf(scurrius_npcs.boss, scurrius_npcs.boss_public)) {
            edit(type) {
                defaultMode = NpcMode.None
                param[params.defend_anim] = scurrius_seqs.idle
            }
        }
        edit(scurrius_npcs.rat) {
            maxRange = LAIR_LEASH
            giveChase = true
            defaultMode = NpcMode.None
            attackRange = 1
        }
    }

    /** Further than any two tiles of the lair are apart, so a chase is never dropped. */
    const val LAIR_LEASH = 40
}
