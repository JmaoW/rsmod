package org.rsmod.content.areas.misc.isleofsouls.cox

import kotlin.math.sqrt
import org.rsmod.game.entity.Npc

/** The party's figures the raid's monsters are scaled by, taken when the raid starts. */
data class CoxScalingInputs(
    val size: Int = 1,
    val maxCombat: Int = 126,
    val maxHitpoints: Int = 99,
    val averageMining: Int = 1,
    val challenge: Boolean = false,
)

/** A monster's base figures as the Wiki's DPS calculator holds them (levels at the base scale). */
data class CoxBase(
    val hitpoints: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
    /** Its Magic level defends (counts as Defence) rather than attacks. */
    val magicDefends: Boolean = false,
    /** Scaled as a lone monster: scavenger beasts and vespine soldiers. */
    val single: Boolean = false,
)

/**
 * The raid's scaling, as the Wiki's DPS calculator (weirdgloop/osrs-dps-calc,
 * `src/lib/scaling/ChambersOfXeric.ts`) does it:
 * - levels scale with the party's highest Hitpoints level (55 + 44 x hp / 99), hitpoints with its
 *   highest combat level (60-126);
 * - with n raiders, offence x (100 + 7 isqrt(n-1) + (n-1))%, defence x (100 + isqrt(n-1) +
 *   7(n-1)/10)%, hitpoints x (1 + n / 2);
 * - Challenge Mode adds 50% to offence, defence and hitpoints (Tekton's defence 20% under four
 *   raiders, 35% from four; the glowing crystal keeps its hitpoints and defence);
 * - scavenger beasts and vespine soldiers scale alone, by the highest combat and Hitpoints only;
 * - the Great Olm's hitpoints have their own formula ([olmHitpoints]).
 *
 * Levels of 1 stay 1. Max hits grow with the scaled offence ([maxHit]): not a sourced rule, the
 * Wiki gives them at the base scale only.
 */
object CoxScaling {
    data class Scaled(
        val hitpoints: Int,
        val attack: Int,
        val strength: Int,
        val defence: Int,
        val ranged: Int,
        val magic: Int,
        val offence: Int,
        val baseOffence: Int,
    )

    fun scale(
        base: CoxBase,
        inputs: CoxScalingInputs,
        tekton: Boolean = false,
        crystal: Boolean = false,
    ): Scaled {
        val offensives = buildList {
            add(base.attack)
            add(base.strength)
            add(base.ranged)
            if (!base.magicDefends) add(base.magic)
        }
        val defensives = buildList {
            add(base.defence)
            if (base.magicDefends) add(base.magic)
        }
        val baseOffence = offensives.filter { it != 1 }.maxOrNull() ?: 1
        val baseDefence = defensives.filter { it != 1 }.maxOrNull() ?: 1
        var offence: Int
        var defence: Int
        var hp: Int
        if (base.single) {
            var hpScaler = inputs.maxCombat.coerceIn(60, 126)
            var statScaler = inputs.maxHitpoints.coerceIn(55, 99)
            if (inputs.challenge) {
                statScaler += statScaler * CM_PERCENT / 100
                hpScaler += hpScaler * CM_PERCENT / 100
            }
            hp = (base.hitpoints * hpScaler / 126).coerceAtLeast(5)
            offence = (baseOffence * statScaler / 99).coerceAtLeast(1)
            defence = (baseDefence * statScaler / 99).coerceAtLeast(1)
        } else {
            val others = (inputs.size.coerceIn(1, 100)) - 1
            val combat = inputs.maxCombat.coerceIn(60, 126)
            val highestHp = (55 + 44 * inputs.maxHitpoints / 99).coerceIn(55, 99)
            offence = baseOffence * highestHp / 99
            defence = baseDefence * highestHp / 99
            hp = base.hitpoints * combat / 126
            val root = sqrt(others.toDouble()).toInt()
            offence = offence * (100 + root * 7 + others) / 100
            defence = defence * (100 + root + others * 7 / 10) / 100
            hp += hp * (inputs.size.coerceIn(1, 100) * 50 / 100)
            if (inputs.challenge) {
                offence += offence * CM_PERCENT / 100
                if (!crystal) hp += hp * CM_PERCENT / 100
                if (tekton) {
                    defence += defence * (if (inputs.size < 4) 20 else 35) / 100
                } else if (!crystal) {
                    defence += defence * CM_PERCENT / 100
                }
            }
            hp = hp.coerceIn(50, 30000)
            offence = offence.coerceIn(50, 5000)
            defence = defence.coerceIn(50, 20000)
        }
        fun off(level: Int) = if (level == 1) 1 else offence
        fun def(level: Int) = if (level == 1) 1 else defence
        return Scaled(
            hitpoints = hp,
            attack = off(base.attack),
            strength = off(base.strength),
            defence = def(base.defence),
            ranged = off(base.ranged),
            magic = if (base.magicDefends) def(base.magic) else off(base.magic),
            offence = offence,
            baseOffence = baseOffence,
        )
    }

    /** A max hit given at the base scale, grown with the scaled offence. */
    fun maxHit(base: Int, scaled: Scaled): Int =
        if (base <= 0) 0
        else ((base * (scaled.offence + 9)) / (scaled.baseOffence + 9)).coerceAtLeast(1)

    /**
     * The Great Olm (Wiki): head 800 + 400k, claws 600 + 300k, with k = min(n-1, 50) - 3 x
     * (min(n, 50) / 8).
     */
    fun olmHitpoints(size: Int, head: Boolean): Int {
        val n = size.coerceIn(1, 100)
        val k = minOf(n - 1, 50) - 3 * (minOf(n, 50) / 8)
        return if (head) 800 + 400 * k else 600 + 300 * k
    }

    /** Puts [scaled] onto [npc]: its levels and hitpoints. */
    fun apply(npc: Npc, scaled: Scaled, keepHitpoints: Boolean = false) {
        npc.baseAttackLvl = scaled.attack
        npc.attackLvl = scaled.attack
        npc.baseStrengthLvl = scaled.strength
        npc.strengthLvl = scaled.strength
        npc.baseDefenceLvl = scaled.defence
        npc.defenceLvl = scaled.defence
        npc.baseRangedLvl = scaled.ranged
        npc.rangedLvl = scaled.ranged
        npc.baseMagicLvl = scaled.magic
        npc.magicLvl = scaled.magic
        if (!keepHitpoints) {
            npc.baseHitpointsLvl = scaled.hitpoints
            npc.hitpoints = scaled.hitpoints
        }
    }

    private const val CM_PERCENT = 50

    /** The monsters' base figures (Wiki DPS calculator data). */
    object Bases {
        val abyssal_portal = CoxBase(250, 1, 1, 176, 1, 176, magicDefends = true)
        val deathly_mage = CoxBase(120, 1, 1, 155, 1, 210)
        val deathly_ranger = CoxBase(120, 1, 1, 155, 210, 155, magicDefends = true)
        val glowing_crystal = CoxBase(120, 1, 1, 100, 1, 100)
        val olm_head = CoxBase(800, 250, 250, 150, 250, 250)
        val olm_left_claw = CoxBase(600, 250, 250, 175, 250, 175, magicDefends = true)
        val olm_right_claw = CoxBase(600, 250, 250, 175, 250, 87, magicDefends = true)
        val guardian = CoxBase(250, 140, 140, 100, 1, 1)
        val ice_demon = CoxBase(140, 1, 1, 160, 390, 390)
        val shaman = CoxBase(190, 130, 130, 210, 130, 130)
        val muttadile_large = CoxBase(250, 250, 250, 220, 250, 250)
        val muttadile_small = CoxBase(250, 150, 150, 138, 150, 1)
        val scavenger = CoxBase(30, 120, 120, 45, 1, 1, single = true)
        val mystic = CoxBase(160, 140, 140, 187, 1, 140)
        val tekton = CoxBase(300, 390, 390, 205, 1, 205, magicDefends = true)
        val vanguard = CoxBase(180, 150, 150, 160, 150, 150)
        val vasa = CoxBase(300, 1, 1, 175, 230, 230)
        val vespine_soldier = CoxBase(100, 150, 150, 40, 1, 40, magicDefends = true, single = true)
        val vespula = CoxBase(200, 150, 150, 88, 150, 88, magicDefends = true)
    }
}
