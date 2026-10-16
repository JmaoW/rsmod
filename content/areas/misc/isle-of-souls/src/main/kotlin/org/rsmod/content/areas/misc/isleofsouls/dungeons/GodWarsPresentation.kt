package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.type.builders.proj.ProjAnimBuilder
import org.rsmod.api.type.refs.proj.ProjAnimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc

/**
 * The sounds of the God Wars generals, their bodyguards and the dungeon's crossings. The ids come
 * from captures of the real game (rsprox); the cache has no names for sounds, so the names are in
 * `synth.sym`. Which of two bodyguards that attack on the same tick owns a sound is a guess.
 */
object GodWarsSynths : SynthReferences() {
    val graardor_smash = find("godwars_graardor_smash")
    val graardor_death = find("godwars_graardor_death")
    val strongstack_attack = find("godwars_strongstack_attack")
    val steelwill_attack = find("godwars_steelwill_attack")
    val grimspike_attack = find("godwars_grimspike_attack")
    val sergeant_death = find("godwars_sergeant_death")

    val kree_attack = find("godwars_kree_attack")
    val kree_death = find("godwars_kree_death")
    val skree_attack = find("godwars_skree_attack")
    val geerin_attack = find("godwars_geerin_attack")

    val zilyana_melee = find("godwars_zilyana_melee")
    val zilyana_magic = find("godwars_zilyana_magic")
    val zilyana_death = find("godwars_zilyana_death")
    val starlight_attack = find("godwars_starlight_attack")
    val growler_attack = find("godwars_growler_attack")
    val bree_attack = find("godwars_bree_attack")

    val kril_melee = find("godwars_kril_melee")
    val kril_magic = find("godwars_kril_magic")
    val kril_death = find("godwars_kril_death")
    val tstanon_attack = find("godwars_tstanon_attack")

    val gong_door = find("godwars_gong_door")
    val grapple_fire = find("godwars_grapple_fire")
    val grapple_swing = find("godwars_grapple_swing")
    val grapple_land = find("godwars_grapple_land")
    val swim = find("godwars_swim")
    val rope_tie = find("godwars_rope_tie")
}

/** The flight of each general's and bodyguard's projectile, as captured. */
object GodWarsProjAnims : ProjAnimReferences() {
    val graardor_slam = find("godwars_graardor_slam")
    val steelwill_sonic = find("godwars_steelwill_sonic")

    /** Sergeant Grimspike's axes and Zakl'n Gritch's snowballs fly the same way. */
    val goblin_axe = find("godwars_goblin_axe")
    val kree_wind = find("godwars_kree_wind")
    val skree_bolt = find("godwars_skree_bolt")
    val geerin_spear = find("godwars_geerin_spear")
    val growler_spell = find("godwars_growler_spell")
    val bree_arrow = find("godwars_bree_arrow")
    val kril_flames = find("godwars_kril_flames")
    val balfrug_fireball = find("godwars_balfrug_fireball")
}

/**
 * The projectiles of the God Wars bosses. A captured projectile has a launch cycle (`delay`), a
 * flight of `base + perTile * distance` client cycles, an arc (`angle`), a `progress` and start and
 * end heights; the engine works the flight out as `delay + lengthAdjustment + stepMultiplier *
 * distance`, so `lengthAdjustment` is `base - delay`. Needs `packCache` (new `projanim.sym` ids).
 */
internal object GodWarsProjAnimBuilder : ProjAnimBuilder() {
    init {
        flight("godwars_graardor_slam", 0, 0, start = 31, base = 60, perTile = 5, 9, 200)
        flight("godwars_steelwill_sonic", 90, 124, start = 30, base = 46, perTile = 10, 16, 64)
        flight("godwars_goblin_axe", 120, 146, start = 30, base = 46, perTile = 5, 15, 11)
        flight("godwars_kree_wind", 172, 124, start = 51, base = 60, perTile = 8, 15, 64)
        flight("godwars_skree_bolt", 400, 124, start = 31, base = 46, perTile = 10, 16, 64)
        flight("godwars_geerin_spear", 250, 146, start = 41, base = 46, perTile = 5, 15, 11)
        flight("godwars_growler_spell", 50, 70, start = 35, base = 26, perTile = 10, 5, 100)
        flight("godwars_bree_arrow", 240, 146, start = 30, base = 46, perTile = 5, 15, 150)
        flight("godwars_kril_flames", 0, 0, start = 50, base = 56, perTile = 0, 16, 155)
        flight("godwars_balfrug_fireball", 90, 124, start = 30, base = 26, perTile = 10, 16, 64)
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

/**
 * Puts a general or bodyguard back as it was when nobody is left in its room (the Wiki: "If all
 * players leave, his health and stats will be reset"): full hitpoints and every combat stat back to
 * its base, so a lowered Defence does not carry over to the next group.
 */
fun Npc.resetForEmptyRoom() {
    hitpoints = baseHitpointsLvl
    attackLvl = baseAttackLvl
    strengthLvl = baseStrengthLvl
    defenceLvl = baseDefenceLvl
    rangedLvl = baseRangedLvl
    magicLvl = baseMagicLvl
}
