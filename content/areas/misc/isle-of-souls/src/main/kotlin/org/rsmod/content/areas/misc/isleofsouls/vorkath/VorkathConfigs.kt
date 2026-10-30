package org.rsmod.content.areas.misc.isleofsouls.vorkath

import org.rsmod.api.type.builders.proj.ProjAnimBuilder
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.proj.ProjAnimReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.entity.npc.NpcMode

typealias vorkath_npcs = VorkathNpcs

typealias vorkath_locs = VorkathLocs

typealias vorkath_objs = VorkathObjs

typealias vorkath_seqs = VorkathSeqs

typealias vorkath_spotanims = VorkathSpotanims

typealias vorkath_projanims = VorkathProjAnims

typealias vorkath_timers = VorkathTimers

object VorkathNpcs : NpcReferences() {
    /** Vorkath asleep in the middle of the crater: "Poke". */
    val sleeping = find("vorkath_sleeping")

    /** The no-op form Vorkath is while it rises, between the poke and the fight. */
    val rising = find("vorkath_sleeping_noop")

    /**
     * Vorkath awake (level 732, the post-quest form). The drop table, the kill tasks and the
     * collection log know it by its name.
     */
    val boss = find("vorkath")

    /** The zombified spawn of the freezing special (level 64, the post-quest form). */
    val spawn = find("vorkath_spawn")
}

object VorkathLocs : LocReferences() {
    /**
     * Torfinn's boat at Ungael ("Fremennik Boat", "Travel"). The isle has a copy of it as the way
     * into the crater.
     */
    val boat = find("ungael_boat")

    /** The ice chunks at the south of the crater ("Climb-over"): the way back out. */
    val ice_chunks = find("ungael_crater_entrance")

    /** A pool of the acid special. */
    val acid = find("vorkath_acid")
}

object VorkathObjs : ObjReferences() {
    val head = find("vorkath_head")
    val accumulator = find("anma_50_reward")
    val assembler = find("avas_assembler")
    val mithril_arrow = find("mithril_arrow")
    val dragonbone_necklace = find("dragonbone_necklace")
}

object VorkathSeqs : SeqReferences() {
    /** The player's poke (rsprox captures: 827). */
    val poke = find("human_pickupfloor")

    /** Vorkath rising from its sleep (7 ticks). */
    val wake = find("ds2_vorkath_spawn")
    val melee = find("ds2_vorkath_attack_melee")

    /** Its ranged, magic and dragonfire attacks and the icy breath. */
    val breathe = find("ds2_vorkath_ranged")

    /** Spitting the acid pools. */
    val acid = find("ds2_vorkath_acid")

    /** Launching the fireball, and the spawn, high into the air. */
    val lob = find("ds2_vorkath_ranged_up")
    val death = find("ds2_vorkath_death")
}

object VorkathSpotanims : SpotanimReferences() {
    val ranged_travel = find("vorkath_ranged_travel")
    val ranged_impact = find("vorkath_ranged_impact")
    val magic_travel = find("vorkath_magic_travel")
    val magic_impact = find("vorkath_magic_impact")

    /** The breaths (rsprox captures: 393, 1470, 1471 and 395, landing as 1466, 1472, 1473, 369). */
    val fire_travel = find("dragon_ranged_fire_attack")
    val fire_impact = find("firesurge_impact")
    val venom_travel = find("dragon_ranged_venom_attack")
    val venom_impact = find("dragon_venom_impact")
    val corrupt_travel = find("dragon_ranged_corrupting_attack")
    val corrupt_impact = find("dragon_corrupting_impact")
    val ice_travel = find("dragon_ranged_ice_attack")
    val freeze_impact = find("ice_barrage_impact")

    /** The fireball that has to be dodged, and where it lands. */
    val fireball_travel = find("vorkath_area_travel")

    /**
     * The shadow on the tile the fireball will land on. The cache has no shadow of Vorkath's own:
     * this is the Grotesque Guardians' falling-debris shadow that lasts 150 client cycles, the
     * fireball's 5-tick flight.
     */
    val fireball_shadow = find("gargboss_debris_shadow_150")

    /** The acid special's pools and its rapid fire, which lands as a fire blast (131). */
    val acid_travel = find("vorkath_acid_travel")
    val rapid_travel = find("vorkath_area_small_travel")
    val rapid_impact = find("fireblast_impact")

    /** The spawn in the air, and its explosion (1460). */
    val spawn_travel = find("vorkath_spawn_travel")
    val spawn_explosion = find("watersurge_impact")
}

/**
 * The flights of Vorkath's projectiles, as rsprox captures show them (see
 * [VorkathProjAnimBuilder]).
 */
object VorkathProjAnims : ProjAnimReferences() {
    /** Its ranged, magic and dragonfire shots, and the icy breath. */
    val shot = find("vorkath_shot")

    /** The fireball, high in the air, landing 5 ticks after the launch. */
    val fireball = find("vorkath_fireball")

    /** The acid lobbed at the pools' tiles. */
    val acid = find("vorkath_acid")

    /** One of the rapid-fire shots, which land a tick later. */
    val rapid = find("vorkath_rapid")

    /** The spawn, thrown to where it lands. */
    val spawn = find("vorkath_spawn")
}

/**
 * Vorkath's projectile flights (tsps' notes from rsprox captures, heights back in capture units).
 * The engine's flight is `delay + lengthAdjustment + stepMultiplier * distance` client cycles, so
 * `lengthAdjustment` is `base - start`. The fireball's 30-cycle start is the tick between the
 * launch animation and the projectile. Needs `packCache` (new `projanim.sym` ids).
 */
internal object VorkathProjAnimBuilder : ProjAnimBuilder() {
    init {
        flight("vorkath_shot", 140, 124, start = 30, base = 70, perTile = 5, 14, 128)
        flight("vorkath_fireball", 340, 36, start = 30, base = 150, perTile = 0, 46, 0)
        flight("vorkath_acid", 340, 0, start = 32, base = 90, perTile = 0, 46, 0)
        flight("vorkath_rapid", 140, 32, start = 0, base = 30, perTile = 0, 22, 0)
        flight("vorkath_spawn", 340, 0, start = 32, base = 120, perTile = 0, 46, 0)
    }

    private fun flight(
        name: String,
        startHeight: Int,
        endHeight: Int,
        start: Int,
        base: Int,
        perTile: Int,
        angle: Int,
        progress: Int,
    ) {
        build(name) {
            this.startHeight = startHeight
            this.endHeight = endHeight
            this.delay = start
            this.angle = angle
            this.lengthAdjustment = base - start
            this.progress = progress
            this.stepMultiplier = perTile
        }
    }
}

object VorkathTimers : TimerReferences() {
    /** Drives a run: every player inside has it ticking each game tick. */
    val tick = find("vorkath_tick")
}

/**
 * What Vorkath and its spawn need in a team's crater. Vorkath never moves and never picks a fight
 * on its own: [Vorkath] chooses its target and its attacks every tick. The spawn walks up to the
 * player it was thrown at. Every player's damage on Vorkath is kept to decide who gets the drops.
 * Needs `packCache`.
 */
internal object VorkathNpcEdits : NpcEditor() {
    init {
        edit(vorkath_npcs.boss) {
            defaultMode = NpcMode.None
            wanderRange = 0
            heroCount = HERO_SLOTS
        }
        edit(vorkath_npcs.sleeping) {
            defaultMode = NpcMode.None
            wanderRange = 0
        }
        edit(vorkath_npcs.rising) {
            defaultMode = NpcMode.None
            wanderRange = 0
        }
        edit(vorkath_npcs.spawn) {
            defaultMode = NpcMode.None
            wanderRange = 0
            maxRange = CRATER_LEASH
            giveChase = true
        }
    }

    /** How many players' damage is kept to decide who gets the drops. */
    const val HERO_SLOTS = 16

    /** Further than any two tiles of the crater are apart, so the spawn never gives up. */
    const val CRATER_LEASH = 40
}
