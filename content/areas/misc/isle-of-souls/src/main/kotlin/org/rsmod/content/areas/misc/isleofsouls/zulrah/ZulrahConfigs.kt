package org.rsmod.content.areas.misc.isleofsouls.zulrah

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.builders.varp.VarpBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.api.type.script.dsl.NpcPluginBuilder
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.seq.SeqType

typealias zulrah_npcs = ZulrahNpcs

typealias zulrah_locs = ZulrahLocs

typealias zulrah_objs = ZulrahObjs

typealias zulrah_seqs = ZulrahSeqs

typealias zulrah_spotanims = ZulrahSpotanims

typealias zulrah_timers = ZulrahTimers

typealias zulrah_vars = ZulrahVars

object ZulrahNpcs : NpcReferences() {
    /** Zulrah in her three forms: serpentine (green), magma (red) and tanzanite (blue). */
    val green = find("snakeboss_boss_ranged")
    val red = find("snakeboss_boss_melee")
    val blue = find("snakeboss_boss_magic")

    /** Her snakelings: the first fights in melee, the second shoots magic. */
    val snakeling_melee = find("snakeboss_minion_melee")
    val snakeling_magic = find("snakeboss_minion_magic")

    fun of(form: ZulrahForm): NpcType =
        when (form) {
            ZulrahForm.Green -> green
            ZulrahForm.Red -> red
            ZulrahForm.Blue -> blue
        }
}

object ZulrahLocs : LocReferences() {
    /** The sacrificial boat on the lobby plaza: "Board" takes a team to Zulrah. */
    val boat = find("snakeboss_boat_1op")

    /** The Zul-andra teleport scroll fixed to the shrine floor: the way out of the lair. */
    val exit = find("snakeboss_exit")

    /** A 3x3 cloud of venom. */
    val cloud = find("snakeboss_poisoncloud")
}

object ZulrahObjs : ObjReferences() {
    val scales = find("snakeboss_scale")
    val tanzanite_fang = find("blowpipe_fang")
    val magic_fang = find("magic_fang")
    val serpentine_visage = find("serpentine_visage")
    val uncut_onyx = find("uncut_onyx")
    val jar_of_swamp = find("jar_of_swamp")
    val pet = find("snakepet")
    val pet_orange = find("snakepet_orange")
    val pet_blue = find("snakepet_blue")
    val serpentine_helm_charged = find("serpentine_helm_charged")
}

object ZulrahSeqs : SeqReferences() {
    val spawn = find("snakeboss_spawn")
    val dive = find("snakeboss_sinkfast")
    val surface = find("snakeboss_emergefast")

    /** One shot (ranged or magic) and the three-spit barrage that makes venom clouds. */
    val shoot = find("snakeboss_attack_acidx1")
    val barrage = find("snakeboss_attack_acidx3")

    /** The tail swing, to the player's side of her. */
    val tail_left = find("snakeboss_attack_tail_left")
    val tail_right = find("snakeboss_attack_tail_right")
    val defend = find("snakeboss_defend")

    /** The player thrown back by the tail (animation 1157, as tsps and Near-Reality use). */
    val knockback = find("human_troll_flyback")
    val death = find("snakeboss_death")

    /** The snakeling's (the cache calls it the pet's) animations. */
    val snakeling_spawn = find("snakeboss_pet_spawn")
    val snakeling_attack = find("snakeboss_pet_attack")
    val snakeling_defend = find("snakeboss_pet_defend")
    val snakeling_death = find("snakeboss_pet_death")
}

object ZulrahSpotanims : SpotanimReferences() {
    /** The green orb she shoots (ranged). A guess from the cache's names; check in the client. */
    val ranged_shot = find("snakeboss_orb")

    /** The blue ball she casts (magic). */
    val magic_shot = find("snakeboss_fireball")

    /** The orb that lands as a venom cloud. */
    val cloud_shot = find("snakeboss_double_orb")

    /** The orb that lands as a snakeling. */
    val snakeling_shot = find("snakeboss_egg")

    /** A magic snakeling's spell. */
    val snakeling_spell = find("snakeboss_minion_spell")
}

object ZulrahTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("zulrah_tick")

    /** Venom: ticks on the poisoned player every 30 game ticks. */
    val venom = find("zulrah_venom")
}

object ZulrahVars : VarpReferences() {
    /**
     * The player's venom: `0` for none, otherwise the damage of the next venom hit (6 up to 20).
     * Poison, the weaker sickness, has its own varps (`PlayerPoison`).
     */
    val venom = find("isle_venom")

    /** The client's poison orb: a value from 1,000,000 up shows venom. */
    val poison_orb = find("poison")
}

/** Server-only Zulrah variables. */
internal object ZulrahVarpBuilder : VarpBuilder() {
    init {
        // Saved on log-out, so venom comes back on login (see PlayerVenom.resume).
        build("isle_venom") { permanent = true }
    }
}

/**
 * Gives Zulrah and her snakelings what they need to fight. Zulrah never walks or hunts (the fight
 * moves and aims her), flinches when hit, and does not turn on a player who hits her. The
 * snakelings chase across the whole lair; the magic one stops once it is in range to cast. Each
 * type is edited once only. Needs `packCache`.
 */
internal object ZulrahNpcEdits : NpcEditor() {
    init {
        for (form in ZulrahForm.entries) {
            edit(zulrah_npcs.of(form), zulrah_seqs.defend) { defaultMode = NpcMode.None }
        }
        edit(zulrah_npcs.snakeling_melee, zulrah_seqs.snakeling_defend) {
            chase()
            attackRange = 1
        }
        edit(zulrah_npcs.snakeling_magic, zulrah_seqs.snakeling_defend) {
            chase()
            attackRange = SNAKELING_MAGIC_RANGE
        }
    }

    private fun edit(type: NpcType, defend: SeqType, init: NpcPluginBuilder.() -> Unit) {
        edit(type) {
            init()
            param[params.defend_anim] = defend
        }
    }

    private fun NpcPluginBuilder.chase() {
        maxRange = LAIR_LEASH
        giveChase = true
        defaultMode = NpcMode.None
    }

    /** Further than any two tiles of the lair are apart, so a chase is never dropped. */
    const val LAIR_LEASH = 40

    /** The Near-Reality server: a magic snakeling casts from up to 6 tiles away. */
    const val SNAKELING_MAGIC_RANGE = 6
}
