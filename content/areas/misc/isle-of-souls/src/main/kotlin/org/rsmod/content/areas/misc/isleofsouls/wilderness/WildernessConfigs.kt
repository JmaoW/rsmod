package org.rsmod.content.areas.misc.isleofsouls.wilderness

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias wild_npcs = WildNpcs

typealias wild_locs = WildLocs

typealias wild_objs = WildObjs

typealias wild_seqs = WildSeqs

typealias wild_spotanims = WildSpotanims

typealias wild_timers = WildTimers

typealias wild_synths = WildSynths

object WildNpcs : NpcReferences() {
    val callisto = find("callisto")
    val artio = find("callisto_singles")
    val venenatis = find("venenatis")
    val spindel = find("venenatis_singles")
    val vetion = find("vetion")
    val vetion_enraged = find("vetion_2")
    val calvarion = find("vetion_single")
    val calvarion_enraged = find("vetion_2_single")

    /** The two hellhounds of Vet'ion's first and second forms, and of Calvar'ion's. */
    val vetion_hound = find("vetion_hellhound_jnr")
    val vetion_hound_greater = find("vetion_hellhound_snr")
    val calvarion_hound = find("vetion_hellhound_jnr_singles")
    val calvarion_hound_greater = find("vetion_hellhound_snr_singles")

    val venenatis_spiderling = find("venenatis_spiderling")
    val spindel_spiderling = find("spindel_spiderling")

    /** The Escape Caves' own bears and spiders (they hit for typeless damage). */
    val cave_bear = find("wbr_bear")
    val cave_spider = find("wbr_cave_poisonspider")
}

object WildLocs : LocReferences() {
    // Doors in from the surface, put in the Revenant Caves.
    val callisto_entrance = find("wild_callisto_entrance01")
    val venenatis_entrance = find("wild_venanatis_entrance01")
    val vetion_entrance = find("wild_vetion_entrance01")
    val artio_entrance = find("wild_callisto_singles_entrance01")
    val spindel_entrance = find("wild_venenatis_singles_entrance01")
    val calvarion_entrance = find("wild_vetion_singles_entrance01")
    val escape_entrance = find("wild_cave_exit01")

    // Ways out of the three big lairs, into the Escape Caves, and out of the singles lairs.
    val callisto_exit = find("wild_callisto_exit01")
    val venenatis_exit = find("wild_venanatis_exit")
    val vetion_exit = find("wild_vetion_exit01")

    // The Escape Caves' four ways out to the surface.
    val escape_exit_south = find("wild_boss_escape_cave_exit01")
    val escape_exit_side = find("wild_boss_escape_cave_exit02")
    val escape_exit_centre = find("wild_boss_escape_cave_exit03")

    // The scenery of the sticky web Venenatis spits out.
    val web_centre = find("wbr_venenatis_web_centre")
    val web_edge = find("wbr_venenatis_web_edge")
    val web_corner = find("wbr_venenatis_web_corner")

    /** The bear trap Callisto and Artio lay on the floor. */
    val callisto_trap = find("callisto_trap_loc")
}

object WildObjs : ObjReferences() {
    val voidwaker_hilt = find("wbr_voidwaker_hilt")
    val voidwaker_blade = find("wbr_voidwaker_blade")
    val voidwaker_gem = find("wbr_voidwaker_gem")
    val voidwaker = find("voidwaker")
}

object WildSeqs : SeqReferences() {
    val callisto_melee = find("npc_callisto_attack_melee01")
    val callisto_ranged = find("npc_callisto_attack_ranged01")
    val callisto_magic = find("npc_callisto_attack_magic01")
    val callisto_roar = find("npc_callisto_enraged01")
    val callisto_death = find("npc_callisto_death01")
    val callisto_spawn = find("npc_callisto_spawn01")
    val callisto_idle = find("npc_callisto_idle01")

    /** A bear trap snapping shut. */
    val trap_spring = find("trap_bear01_activate")

    val venenatis_melee = find("npc_venenatis_melee_01")
    val venenatis_ranged = find("npc_venenatis_ranged_01")
    val venenatis_magic = find("npc_venenatis_magic_01")
    val venenatis_death = find("npc_venenatis_death_01")
    val venenatis_idle = find("npc_venenatis_idle_01")

    /**
     * The shield bash. The cache's "attack_melee" is a sword swipe; the shield motion the game uses
     * for the bash is the one filed as the parry (the user saw it play when he was hit).
     */
    val vetion_melee = find("npc_vetion_defend_parry_01")
    val vetion_melee_enraged = find("npc_vetion_defend_parry_02")
    val vetion_magic = find("npc_vetion_attack_magic_01")
    val vetion_magic_enraged = find("npc_vetion_attack_magic_02")
    val vetion_enrage = find("npc_vetion_enrage_01")
    /** What he does when he calls his hounds (the cache files it as a shield block). */
    val vetion_summon = find("npc_vetion_defend_shield_01")
    val vetion_summon_enraged = find("npc_vetion_defend_shield_02")

    /** Standing still: he has no block animation, so a hit just plays this. */
    val vetion_idle = find("npc_vetion_idle_01")
    val vetion_idle_enraged = find("npc_vetion_idle_02")
    val vetion_death = find("npc_vetion_death_01")
    val vetion_death_enraged = find("npc_vetion_death_02")
    val hound_death = find("wbr_vention_hellhound_death")

    /** A player thrown back by Callisto's orb. */
    val player_knockback = find("human_troll_flyback_withsound")
}

object WildSpotanims : SpotanimReferences() {
    val callisto_ranged_projectile = find("fx_callisto_ranged_projectile")
    val callisto_ranged_impact = find("fx_callisto_ranged_impact")
    val callisto_ranged_cast = find("fx_callisto_ranged_spotanim")
    val callisto_roar = find("fx_callisto_enrage")
    val callisto_roar_small = find("fx_callisto_enrage_small")
    val callisto_trap = find("fx_callisto_trap")

    /** The orb of Callisto's knockback: the wind blast projectile (rsprox capture). */
    val orb_projectile = find("windblast_travel")

    val venenatis_ranged_projectile = find("fx_venenatis_ranged_projectile")
    val venenatis_ranged_impact = find("fx_venenatis_ranged_impact")
    val venenatis_magic_projectile = find("fx_venenatis_magic_projectile")
    val venenatis_magic_impact = find("fx_venenatis_magic_impact")
    val venenatis_web_projectile = find("fx_venenatis_web_projectile")

    /** The sparks round his weapon while he swings (a cast effect on him, not on the ground). */
    val vetion_bolt = find("fx_vetion_attack_magic_01")
    val vetion_bolt_enraged = find("fx_vetion_attack_magic_02")
    /** The glowing circle on the ground, then the lightning strike on it: 4 ticks in all. */
    val vetion_bolt_impact = find("fx_vetion_attack_magic_impact_01")
    val vetion_bolt_impact_enraged = find("fx_vetion_attack_magic_impact_02")
    /** The smoke that bursts off the bash's tiles (found with `::bashfx dust`, the user's pick). */
    val vetion_bash = find("zebak_roar_wave_dust")

    /** The dark circle that marks a bash tile for the 4 ticks before it lands (a cache shadow). */
    val vetion_shadow = find("gargboss_debris_shadow_120")
}

/** Sounds of the bears, with the ids rsprox captured (names are in `synth.sym`). */
object WildSynths : SynthReferences() {
    val callisto_ranged_impact = find("wild_callisto_ranged_impact")
    val callisto_trap_wave = find("wild_callisto_trap_wave")
    val callisto_trap_spring = find("wild_callisto_trap_spring")
    val callisto_trap_clear = find("wild_callisto_trap_clear")
    val callisto_orb_hit_a = find("wild_callisto_orb_hit_a")
    val callisto_orb_hit_b = find("wild_callisto_orb_hit_b")
}

object WildTimers : TimerReferences() {
    /** Drives one boss or cave creature: it ticks every game tick on the npc itself. */
    val tick = find("wild_boss_tick")
}

/**
 * What the Wilderness bosses and the Escape Caves' creatures need to fight. The cache holds their
 * stats, sizes and defence bonuses; the fights move and aim them from [WildBosses], so the engine's
 * own hunting and retaliation are off. Needs `packCache`.
 */
internal object WildernessNpcEdits : NpcEditor() {
    init {
        val bosses =
            listOf(
                wild_npcs.callisto,
                wild_npcs.artio,
                wild_npcs.venenatis,
                wild_npcs.spindel,
                wild_npcs.vetion,
                wild_npcs.vetion_enraged,
                wild_npcs.calvarion,
                wild_npcs.calvarion_enraged,
            )
        val respawn =
            mapOf(
                wild_npcs.callisto to 11,
                wild_npcs.artio to 11,
                wild_npcs.venenatis to 10,
                wild_npcs.spindel to 9,
                wild_npcs.vetion to 15,
                wild_npcs.vetion_enraged to 15,
                wild_npcs.calvarion to 14,
                wild_npcs.calvarion_enraged to 14,
            )
        for (type in bosses) {
            edit(type) {
                defaultMode = NpcMode.None
                heroCount = HERO_SLOTS
                respawnRate = respawn.getValue(type)
            }
        }
        // He has no block animation (the cache's shield block is what he does when he calls his
        // hounds), so being hit plays his idle one instead of a player's flinch.
        for (type in listOf(wild_npcs.vetion, wild_npcs.calvarion)) {
            edit(type) { param[params.defend_anim] = wild_seqs.vetion_idle }
        }
        for (type in listOf(wild_npcs.vetion_enraged, wild_npcs.calvarion_enraged)) {
            edit(type) { param[params.defend_anim] = wild_seqs.vetion_idle_enraged }
        }
        // Neither has Callisto or Artio: the default is a player's block, which is the wrong
        // skeleton for a bear and made the model glitch when he was hit.
        for (type in listOf(wild_npcs.callisto, wild_npcs.artio)) {
            edit(type) { param[params.defend_anim] = wild_seqs.callisto_idle }
        }
        // She has no block animation either, so being hit plays her idle one: the default is a
        // player's block, which showed up as a stray flinch on her.
        for (type in listOf(wild_npcs.venenatis, wild_npcs.spindel)) {
            edit(type) { param[params.defend_anim] = wild_seqs.venenatis_idle }
        }
        // Things the bosses call in fight with the default melee, but are aimed by the fight.
        for (type in
            listOf(
                wild_npcs.vetion_hound,
                wild_npcs.vetion_hound_greater,
                wild_npcs.calvarion_hound,
                wild_npcs.calvarion_hound_greater,
                wild_npcs.venenatis_spiderling,
                wild_npcs.spindel_spiderling,
            )) {
            edit(type) {
                defaultMode = NpcMode.None
                maxRange = LAIR_LEASH
                giveChase = true
            }
        }
        for (type in listOf(wild_npcs.cave_bear, wild_npcs.cave_spider)) {
            edit(type) {
                defaultMode = NpcMode.None
                maxRange = CAVE_LEASH
                giveChase = true
            }
        }
    }

    /** Players that can hold a share of the damage on one boss: the wiki pays out the top ten. */
    const val HERO_SLOTS = 16

    /** Further than any two tiles of a lair are apart, so a chase is never dropped. */
    const val LAIR_LEASH = 40

    /** The Escape Caves' creatures "pursue players until they leave the caves" (wiki). */
    const val CAVE_LEASH = 80
}
