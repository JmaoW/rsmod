package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.movement.MoveRestrict
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType

typealias inferno_npcs = InfernoNpcs

typealias inferno_locs = InfernoLocs

typealias inferno_objs = InfernoObjs

typealias inferno_seqs = InfernoSeqs

typealias inferno_spotanims = InfernoSpotanims

typealias inferno_timers = InfernoTimers

typealias inferno_queues = InfernoQueues

typealias inferno_vars = InfernoVars

typealias inferno_varbits = InfernoVarBits

object InfernoNpcs : NpcReferences() {
    val nibbler = find("inferno_nibbler")
    val bat = find("inferno_creature_harpie")
    val blob = find("inferno_creature_splitter")
    val blob_range = find("inferno_creature_splitter_range")
    val blob_mage = find("inferno_creature_splitter_mage")
    val blob_melee = find("inferno_creature_splitter_melee")
    val melee = find("inferno_creature_melee")
    val ranger = find("inferno_creature_ranger")
    val mager = find("inferno_creature_mager")

    /** The ranger and mager of the Zuk fight's minion sets. */
    val ranger_final = find("inferno_ranger_finalwave")
    val mager_final = find("inferno_mager_finalwave")

    /** JalTok-Jad and his Yt-HurKot healers; the `_final` ones are for the Zuk fight. */
    val jad = find("inferno_jad")
    val jad_healer = find("inferno_jad_healer")
    val jad_final = find("inferno_jad_finalwave")
    val jad_healer_final = find("inferno_jad_healer_finalwave")

    /** TzKal-Zuk, the Ancestral Glyph that shields the players, and the Jal-MejJak healers. */
    val zuk = find("inferno_tzkalzuk_placeholder")
    val glyph = find("inferno_moving_safespot")
    val zuk_healer = find("inferno_zuk_healer")

    /**
     * The "Rocky support" a pillar's health bar hangs on (an invisible 3x3 npc), and the rubble
     * that plays the collapse when a pillar is gone.
     */
    val pillar = find("inferno_invisible_3x3")
    val pillar_rubble = find("inferno_safespot_dying")
}

object InfernoLocs : LocReferences() {
    /** A hole in the lobby plaza with a "Jump-in" option. */
    val entrance = find("inferno_entrance_op")

    /** The way out at the south end of the arena ("Exit" and "Quick-exit"). */
    val exit = find("inferno_exit")
}

object InfernoObjs : ObjReferences() {
    /** The reward: untradeable, the best melee cape. */
    val infernal_cape = find("infernal_cape")

    /** Jal-Nib-Rek, the Inferno pet. */
    val pet = find("infernopet")
}

object InfernoPillarLocs : LocReferences() {
    /**
     * The three pillars. Each is a 3x3 loc without a model of its own: it shows one of four rocks
     * depending on its own varbit (see [InfernoVarBits]), the way the real Inferno does it.
     */
    val pillars =
        listOf(find("inferno_safespot1"), find("inferno_safespot2"), find("inferno_safespot3"))
}

object InfernoVarBits : VarBitReferences() {
    /**
     * How much damage each pillar has taken, `0` to `255`. The pillar locs show their first rock
     * for `0..63`, then the next three for each further 64.
     */
    val pillar_damage =
        listOf(
            find("inferno_safespot1_health"),
            find("inferno_safespot2_health"),
            find("inferno_safespot3_health"),
        )

    /** `1` hides the roof of Zuk's prison (the multiloc shows no roof for a value above 0). */
    val prison_roof_hidden = find("inferno_prisonroof_hidden")
}

/**
 * The pieces of Zuk's prison in the north wall that the map has in their closed state ("state1"),
 * and what each becomes when he breaks out (the cache names the later states).
 */
object InfernoPrisonLocs : LocReferences() {
    /** The glyph set into the wall: it goes, and the glyph npc takes over. */
    val glyph_closed = find("inferno_collapsing_wall_safespot_state1")

    val side_right_closed = find("inferno_collapsing_wall_side_right_state1")
    val side_right_cracking = find("inferno_collapsing_wall_side_right_state2")
    val side_right_rubble = find("inferno_collapsing_wall_side_right_state3")

    val side_left_closed = find("inferno_collapsing_wall_side_left_state1")
    val side_left_cracking = find("inferno_collapsing_wall_side_left_state2")
    val side_left_rubble = find("inferno_collapsing_wall_side_left_state3")

    val corner_right_closed = find("inferno_collapsing_adjacent_wall_floor_corner_right_state1")
    val corner_right_open = find("inferno_prison_wall_floor_corner_right_state2")
    val corner_left_closed = find("inferno_collapsing_adjacent_wall_floor_corner_left_state1")
    val corner_left_open = find("inferno_prison_wall_floor_corner_left_state2")

    val join_right_closed = find("inferno_collapsing_adjacent_corner_join_right_state1")
    val join_right_open = find("inferno_prison_wall_corner_join_right_state2")

    /**
     * The map's left join is an ordinary wall piece, not a collapsing one (it looks like the right
     * one's closed state), so it has to be taken out and the open left join put in its place.
     */
    val join_left_static = find("inferno_wall_edge_large_06")
    val join_left_open = find("inferno_prison_wall_corner_join_left_state2")
}

object InfernoSeqs : SeqReferences() {
    val nibbler_attack = find("jalnib_attack")
    val nibbler_death = find("jalnib_death")
    val nibbler_defend = find("jalnib_defend")

    val bat_attack = find("jalmejrah_attack")
    val bat_death = find("jalmejrah_death")
    val bat_defend = find("jalmejrah_defend")

    val blob_melee = find("jalak_attack_melee")
    val blob_magic = find("jalak_attack_magic")
    val blob_ranged = find("jalak_attack_ranged")
    val blob_death = find("jalak_death")
    val blob_defend = find("jalak_defend")

    val melee_attack = find("jalimkot_attack")
    val melee_death = find("jalimkot_death")
    val melee_defend = find("jalimkot_defend")
    val melee_dig_down = find("jalimkot_digdown")
    val melee_dig_up = find("jalimkot_digup")

    val ranger_melee = find("jalxil_attack_melee")
    val ranger_ranged = find("jalxil_attack_ranged")
    val ranger_death = find("jalxil_death")
    val ranger_defend = find("jalxil_defend")

    val mager_magic = find("jalakxil_attack_magic")
    val mager_melee = find("jalakxil_attack_melee")
    val mager_death = find("jalakxil_death")
    val mager_revive = find("jalakxil_resurrect")

    val jad_melee = find("jaltokjad_attack_melee")
    val jad_magic = find("jaltokjad_attack_magic")
    val jad_ranged = find("jaltokjad_attack_ranged")
    val jad_death = find("jaltokjad_death")
    val jad_defend = find("jaltokjad_defend")

    /** Yt-HurKot uses the lizard cleric's animations, as the Fight Cave's healers do. */
    val healer_defend = find("lizard_cleric_defend")

    val zuk_spawn = find("zuk_spawn")
    val zuk_attack = find("zuk_attack")
    val zuk_death = find("zuk_death")
    val zuk_defend = find("zuk_defend")

    /** Jal-MejJak shares the dagannoth water creature's animations. */
    val mejjak_attack = find("dagannoth_water_creature_attack")
    val mejjak_death = find("dagannoth_water_creature_death")
    val mejjak_defend = find("dagannoth_water_creature_defend")

    /** The side walls of Zuk's prison crumbling when he breaks out. */
    val wall_collapse_right = find("inferno_collapsing_wall_side_right")
    val wall_collapse_left = find("inferno_collapsing_wall_side_left")

    /** The rubble of a fallen pillar crumbling. */
    val pillar_collapse = find("safe_spot_distructible_pillar_collapse")
}

object InfernoSpotanims : SpotanimReferences() {
    val bat_shot = find("inferno_harpie_proj")
    val blob_ranged = find("inferno_splitter_range")
    val blob_magic = find("inferno_splitter_mage")
    val baby_ranged = find("inferno_babysplitter_range")
    val baby_magic = find("inferno_babysplitter_mage")
    val ranger_shot = find("inferno_xil_projectile")
    val mager_shot = find("inferno_zek_projectile")
    val zuk_shot = find("inferno_zuk_projectile")

    /**
     * A Jal-MejJak's lava ball in flight. A guess: the cache has no Inferno one, so this is the
     * Wilderness meteor.
     */
    val lava_ball = find("wild_falloff_meteor_flying")

    /** What a lava ball looks like where it lands. A guess: the cache has no Inferno one. */
    val lava_splash = find("wild_falloff_meteor_blast")
}

object InfernoTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("inferno_tick")
}

object InfernoQueues : QueueReferences() {
    /** Shows the gatekeeper's send-off once a player has been put into the Inferno. */
    val enter = find("inferno_enter")

    /** Shows the gatekeeper's congratulations when TzKal-Zuk has died. */
    val win = find("inferno_win")
}

object InfernoVars : VarpReferences() {
    /** `1` once the player has given up a fire cape: the Inferno is theirs for good. */
    val unlocked = find("isle_inferno_unlocked")
}

/** Server-only Inferno variables. Saved with the player. */
internal object InfernoVarpBuilder : VarpBuilder() {
    init {
        build("isle_inferno_unlocked") {}
    }
}

/**
 * Gives the Inferno monsters what they need to chase a player across the whole arena and, for the
 * ones with a ranged or magic attack, to stop and shoot from a distance. The engine keeps a chase
 * going only while the target is within `maxRange + attackRange` of where the npc spawned, and
 * reads the distance a npc attacks from out of its type, so both are set here. They also get the
 * animation they flinch with when a player hits them: the cache's npcs have no `defend_anim`, so
 * without it they would flinch with the default human block. (The Jal-Zek has no block animation in
 * the cache, so `npc_anims.toml` gives it its idle, which means it does not flinch.) Each type is
 * edited once only. Needs `packCache`.
 */
internal object InfernoNpcEdits : NpcEditor() {
    init {
        edit(inferno_npcs.nibbler, inferno_seqs.nibbler_defend) { chase() }
        edit(inferno_npcs.melee, inferno_seqs.melee_defend) { chase() }
        edit(inferno_npcs.blob_melee, inferno_seqs.blob_defend) { chase() }
        edit(inferno_npcs.bat, inferno_seqs.bat_defend) {
            chase()
            attackRange = BAT_RANGE
        }
        edit(inferno_npcs.blob, inferno_seqs.blob_defend) {
            chase()
            attackRange = BLOB_RANGE
        }
        for (type in listOf(inferno_npcs.blob_range, inferno_npcs.blob_mage)) {
            edit(type, inferno_seqs.blob_defend) {
                chase()
                attackRange = BABY_BLOB_RANGE
            }
        }
        edit(inferno_npcs.ranger, inferno_seqs.ranger_defend) {
            chase()
            attackRange = RANGER_RANGE
        }
        edit(inferno_npcs.mager) {
            chase()
            attackRange = MAGER_RANGE
        }
        edit(inferno_npcs.jad, inferno_seqs.jad_defend) {
            chase()
            attackRange = JAD_RANGE
        }
        // Zuk's set and his Jad never walk: from anywhere in the arena they are already in range.
        edit(inferno_npcs.ranger_final, inferno_seqs.ranger_defend) {
            chase()
            attackRange = ZUK_SET_RANGE
        }
        edit(inferno_npcs.mager_final) {
            chase()
            attackRange = ZUK_SET_RANGE
        }
        edit(inferno_npcs.jad_final, inferno_seqs.jad_defend) {
            chase()
            attackRange = ZUK_SET_RANGE
        }
        for (type in listOf(inferno_npcs.jad_healer, inferno_npcs.jad_healer_final)) {
            edit(type, inferno_seqs.healer_defend) { chase() }
        }
        // Zuk, his glyph and his healers never move or hunt by themselves; the fight moves them.
        // Zuk never turns: he faces the arena the whole fight, whoever he fires at or is hit by.
        edit(inferno_npcs.zuk, inferno_seqs.zuk_defend) {
            defaultMode = NpcMode.None
            rotationFlag = false
            respawnDir = south
        }
        edit(inferno_npcs.zuk_healer, inferno_seqs.mejjak_defend) { defaultMode = NpcMode.None }
        // The glyph slides along the lava in front of Zuk, which is blocked terrain.
        edit(inferno_npcs.glyph) {
            defaultMode = NpcMode.None
            moveRestrict = MoveRestrict.Blocked
        }
    }

    private fun edit(type: NpcType, defend: SeqType, init: NpcPluginBuilder.() -> Unit) {
        edit(type) {
            init()
            param[params.defend_anim] = defend
        }
    }

    private fun NpcPluginBuilder.chase() {
        maxRange = ARENA_LEASH
        giveChase = true
        defaultMode = NpcMode.None
    }

    /** Further than any two tiles of the arena are apart, so Zuk's set never walks. */
    const val ZUK_SET_RANGE = 40

    /** Further than any two tiles of the arena are apart. */
    const val ARENA_LEASH = 70

    /** The wiki: "4 squares". */
    const val BAT_RANGE = 4

    /** The wiki gives no range for the blob; this is a guess. */
    const val BLOB_RANGE = 8

    /** The wiki: "15 tiles". */
    const val BABY_BLOB_RANGE = 15

    /** The wiki gives no range for the ranger; the Fight Cave's Tok-Xil uses 8. */
    const val RANGER_RANGE = 8

    /** The wiki gives no range for Jad; the Fight Cave's Jad uses 14. */
    const val JAD_RANGE = 14

    /** The wiki gives no range for the mager; the Fight Cave's Ket-Zek uses 10. */
    const val MAGER_RANGE = 10
}
