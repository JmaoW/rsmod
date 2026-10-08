package org.rsmod.api.combat.mastery

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

/**
 * The damage and accuracy effects of combat masteries. Combat code calls these where it rolls
 * damage and accuracy, and every function returns its input unchanged for a player who has not
 * unlocked the matching mastery, so nothing changes in worlds without masteries.
 *
 * The numbers follow the in-game text of each mastery. Where the text leaves room, the choice made
 * is noted on the function.
 */
@Singleton
public class CombatMasteryEffects
@Inject
constructor(private val masteries: CombatMasteries, private val random: GameRandom) {
    /** Per-player timing state. It is transient and is dropped when the player is. */
    private class Timing {
        var rangedTick = Int.MIN_VALUE
        var rangedStreak = 0
        var magicTick = Int.MIN_VALUE
        var magicPreviousTick = Int.MIN_VALUE
        var rangedHits = 0
        var healCarry = 0
        var prayerCarry = 0
    }

    private val timings = WeakHashMap<Player, Timing>()

    private fun timing(player: Player): Timing = timings.getOrPut(player) { Timing() }

    /**
     * Passive 3: "Accuracy with all styles is increased by 100%". The attack roll is doubled, so
     * this multiplies the accuracy multiplier combat code already passes to its formulas.
     */
    public fun accuracyMultiplier(player: Player): Double =
        if (masteries.hasPassive(player, ACCURACY_PASSIVE_TIER)) ACCURACY_PASSIVE_MULTIPLIER
        else 1.0

    /**
     * Passive 1: "95% chance to save weapon charges, ammunition, and runes used for spells." Rolls
     * once for one use of any of them, and returns `true` if it is saved.
     */
    public fun savesResource(player: Player): Boolean {
        if (!masteries.hasPassive(player, SAVE_PASSIVE_TIER)) {
            return false
        }
        return random.of(PERCENT) < SAVE_CHANCE
    }

    /**
     * Passive 2: "Healing from all sources is increased by 20%." Returns the hitpoints to restore
     * for a heal of [base]. The part of the bonus too small to round up is carried over to the
     * player's next heal, so a one point regeneration tick is sometimes two.
     */
    public fun healAmount(player: Player, base: Int): Int {
        if (base <= 0 || !masteries.hasPassive(player, HEAL_PASSIVE_TIER)) {
            return base
        }
        val timing = timing(player)
        val total = base * (PERCENT + HEAL_BONUS) + timing.healCarry
        timing.healCarry = total % PERCENT
        return total / PERCENT
    }

    /**
     * Passive 4: "Damage taken is reduced by 15%." Returns [damage] after the reduction, rounded
     * down.
     */
    public fun reduceDamageTaken(player: Player, damage: Int): Int {
        if (damage <= 0 || !masteries.hasPassive(player, DAMAGE_REDUCTION_PASSIVE_TIER)) {
            return damage
        }
        return damage * (PERCENT - DAMAGE_REDUCTION) / PERCENT
    }

    /**
     * Passive 5: "Prayer Point gain from all sources is increased by 25%." Returns the points to
     * restore for a gain of [base], carrying over the part of the bonus too small to round up.
     */
    public fun prayerGain(player: Player, base: Int): Int {
        if (base <= 0 || !masteries.hasPassive(player, PRAYER_PASSIVE_TIER)) {
            return base
        }
        val timing = timing(player)
        val total = base * (PERCENT + PRAYER_BONUS) + timing.prayerCarry
        timing.prayerCarry = total % PERCENT
        return total / PERCENT
    }

    /**
     * Passive 6: "Attacks with all styles now have 60% Prayer penetration." The percent of a
     * target's protection prayer that [player]'s attacks ignore. No npc protects with a prayer yet,
     * so nothing reads this so far.
     */
    public fun prayerPenetration(player: Player): Int =
        if (masteries.hasPassive(player, PENETRATION_PASSIVE_TIER)) PRAYER_PENETRATION else 0

    /**
     * Melee IV: "Melee hits have a 5% chance to heal 40% of damage dealt." Returns the hitpoints to
     * restore for a melee hit that dealt [damage], or `0`.
     */
    public fun meleeHitHeal(player: Player, damage: Int): Int {
        if (damage <= 0 || !masteries.has(player, MasteryStyle.Melee, HEAL_ON_HIT_TIER)) {
            return 0
        }
        if (random.of(PERCENT) >= MELEE_HEAL_CHANCE) {
            return 0
        }
        return healAmount(player, damage * MELEE_HEAL_PERCENT / PERCENT)
    }

    /**
     * Ranged IV: "Every 5th Ranged hit, heal 5 hitpoints." Counts a ranged hit that dealt [damage]
     * and returns the hitpoints to restore, or `0`.
     */
    public fun rangedHitHeal(player: Player, damage: Int): Int {
        if (damage <= 0 || !masteries.has(player, MasteryStyle.Ranged, HEAL_ON_HIT_TIER)) {
            return 0
        }
        val timing = timing(player)
        timing.rangedHits++
        if (timing.rangedHits < RANGED_HEAL_EVERY) {
            return 0
        }
        timing.rangedHits = 0
        return healAmount(player, RANGED_HEAL)
    }

    /**
     * Magic IV: "When you roll above 90% of your max hit with Magic, heal 10% of damage dealt."
     * Returns the hitpoints to restore for a magic hit of [damage] against a [maxHit], or `0`.
     * [dealt] is the part of [damage] the target had hitpoints for, which the heal is based on.
     */
    public fun magicHitHeal(player: Player, damage: Int, maxHit: Int, dealt: Int = damage): Int {
        if (damage <= 0 || !masteries.has(player, MasteryStyle.Magic, HEAL_ON_HIT_TIER)) {
            return 0
        }
        if (damage * PERCENT <= maxHit * MAGIC_BIG_HIT_PERCENT) {
            return 0
        }
        return healAmount(player, dealt * MAGIC_HEAL_PERCENT / PERCENT)
    }

    /**
     * The attack delay, in ticks, for [style] after the attack rate masteries, where [ticks] is the
     * weapon's own delay.
     *
     * Tier 3: "attack rate set to 80%, rounding down." Tier 5: "attack rate set to 50%, rounded
     * down above 5t, rounded up below 4t." Tier 5 replaces tier 3. A delay of 5 or more ticks
     * rounds down at 50%, and a shorter one rounds up, so 5 becomes 2, 4 becomes 2 and 3 becomes
     * 2. The delay is never less than one tick.
     */
    public fun attackDelay(player: Player, style: MasteryStyle, ticks: Int): Int {
        val tier = masteries.tier(player, style)
        val scaled =
            when {
                tier >= HALF_RATE_TIER ->
                    if (ticks >= HALF_RATE_ROUND_DOWN_FROM) ticks / 2 else (ticks + 1) / 2
                tier >= FAST_RATE_TIER -> ticks * FAST_RATE_PERCENT / PERCENT
                else -> ticks
            }
        return max(1, scaled)
    }

    /**
     * Melee I: "Melee hits have a 25% chance to roll damage twice and take the highest result."
     * Rolls a melee hit from `1` to [maxHit].
     */
    public fun rollMelee(player: Player, maxHit: Int): Int {
        val first = random.of(1..maxHit)
        if (!masteries.has(player, MasteryStyle.Melee, 1)) {
            return first
        }
        if (random.of(PERCENT) >= MELEE_REROLL_CHANCE) {
            return first
        }
        return max(first, random.of(1..maxHit))
    }

    /**
     * Melee II: "Melee hits have a 10% chance to generate an echo hit. (Additional melee hit, 50%
     * max-hit, respects Accuracy, PvM only)". Melee VI: "Your chance to generate an echo increases
     * to 20%, and your echoes can generate additional echoes (Up to 8 times in a row)."
     *
     * Rolls the echoes of one melee hit against an npc and returns the damage of each, in order. A
     * missed echo is `0`. An echo rolls from `1` to half of [maxHit], and [accurate] makes its
     * accuracy roll. Below tier 2 there are none, and below tier 6 there is at most one.
     */
    public fun rollEchoes(player: Player, maxHit: Int, accurate: () -> Boolean): List<Int> {
        val tier = masteries.tier(player, MasteryStyle.Melee)
        if (tier < ECHO_TIER) {
            return emptyList()
        }
        val chance = if (tier >= ECHO_CHAIN_TIER) ECHO_CHAIN_CHANCE else ECHO_CHANCE
        val limit = if (tier >= ECHO_CHAIN_TIER) ECHO_CHAIN_LIMIT else 1
        val echoMaxHit = max(1, maxHit / 2)
        val echoes = ArrayList<Int>(limit)
        while (echoes.size < limit && random.of(PERCENT) < chance) {
            echoes += if (accurate()) random.of(1..echoMaxHit) else 0
        }
        return echoes
    }

    /**
     * Ranged II: "Each subsequent Ranged attack has its max hit increased by an additional 5%.
     * Resets after +20%." The first attack of a streak has no bonus, then +5%, +10%, +15% and +20%,
     * after which the next attack starts over. A streak also starts over if the player has not made
     * a ranged attack for [RANGED_STREAK_TIMEOUT] ticks. Several rolls in the same tick (a double
     * shot) count as one attack.
     */
    public fun rangedMaxHitMultiplier(player: Player): Double {
        if (!masteries.has(player, MasteryStyle.Ranged, 2)) {
            return 1.0
        }
        val timing = timing(player)
        val tick = player.currentMapClock
        if (tick != timing.rangedTick) {
            val idle =
                timing.rangedTick == Int.MIN_VALUE ||
                    tick - timing.rangedTick > RANGED_STREAK_TIMEOUT
            timing.rangedStreak =
                if (idle || timing.rangedStreak >= RANGED_STREAK_STEPS) 0
                else timing.rangedStreak + 1
            timing.rangedTick = tick
        }
        return 1.0 + timing.rangedStreak * RANGED_STREAK_BONUS
    }

    /**
     * Ranged I: "Damage rolls beneath 30% of max hit with Ranged are increased to 30%." Rolls a
     * ranged hit from `1` to [maxHit].
     */
    public fun rollRanged(player: Player, maxHit: Int): Int {
        val roll = random.of(1..maxHit)
        if (!masteries.has(player, MasteryStyle.Ranged, 1)) {
            return roll
        }
        return max(roll, maxHit * RANGED_FLOOR_PERCENT / PERCENT)
    }

    /** Ranged VI: "Never miss with Ranged (PvM only.)". [pvm] is whether the target is an npc. */
    public fun neverMissRanged(player: Player, pvm: Boolean): Boolean =
        pvm && masteries.has(player, MasteryStyle.Ranged, 6)

    /**
     * The magic max hit multiplier for a hit on a target with [targetHitpoints] left.
     *
     * Magic II: "Magic max hit is increased by 5% per tick in-between your attacks (Up to +40%)."
     * The ticks counted are those since the previous magic attack, so a normal five tick cast is
     * +25%, and a first attack, or one after a long pause, gets the full +40%. Several rolls in the
     * same tick count as one attack.
     *
     * Magic VI: "Max hit with Magic is increased by 1% for every 100 Hitpoints remaining on target
     * (Up to 10%)." The two effects multiply.
     */
    public fun magicMaxHitMultiplier(player: Player, targetHitpoints: Int): Double {
        var multiplier = 1.0
        if (masteries.has(player, MasteryStyle.Magic, 2)) {
            val timing = timing(player)
            val tick = player.currentMapClock
            if (tick != timing.magicTick) {
                timing.magicPreviousTick = timing.magicTick
                timing.magicTick = tick
            }
            val gap =
                if (timing.magicPreviousTick == Int.MIN_VALUE) {
                    MAGIC_GAP_CAP
                } else {
                    tick - timing.magicPreviousTick
                }
            val percent = min(gap.coerceAtLeast(0), MAGIC_GAP_CAP) * MAGIC_GAP_PERCENT
            multiplier *= 1.0 + percent / PERCENT.toDouble()
        }
        if (masteries.has(player, MasteryStyle.Magic, 6)) {
            val percent = min(targetHitpoints / HITPOINTS_PER_PERCENT, MAGIC_HITPOINTS_CAP)
            multiplier *= 1.0 + percent / PERCENT.toDouble()
        }
        return multiplier
    }

    /** Scales [range]'s top by [multiplier], keeping the bottom. Returns [range] for `1.0`. */
    public fun scale(range: IntRange, multiplier: Double): IntRange {
        if (multiplier == 1.0) {
            return range
        }
        return range.first..max(range.first, (range.last * multiplier).toInt())
    }

    /**
     * Applies the magic mastery effects to a rolled magic hit of [damage], where [maxHit] is the
     * max hit it was rolled against and [targetHitpoints] the target's hitpoints before the hit.
     *
     * Magic VI: "On a successful Magic hit, If your target has less health than your max hit, you
     * max hit." Magic I: "When you roll above 90% of your max hit with Magic, damage is increased
     * by 50%." The max hit from Magic VI counts as a roll above 90%.
     */
    public fun adjustMagicDamage(
        player: Player,
        damage: Int,
        maxHit: Int,
        targetHitpoints: Int,
    ): Int {
        var result = damage
        if (
            result > 0 && targetHitpoints < maxHit && masteries.has(player, MasteryStyle.Magic, 6)
        ) {
            result = max(result, maxHit)
        }
        if (
            masteries.has(player, MasteryStyle.Magic, 1) &&
                result * PERCENT > maxHit * MAGIC_BIG_HIT_PERCENT
        ) {
            result += result / 2
        }
        return result
    }

    public companion object {
        private const val PERCENT = 100

        private const val ACCURACY_PASSIVE_TIER = 3
        private const val ACCURACY_PASSIVE_MULTIPLIER = 2.0

        private const val SAVE_PASSIVE_TIER = 1
        private const val SAVE_CHANCE = 95
        private const val HEAL_PASSIVE_TIER = 2
        private const val HEAL_BONUS = 20
        private const val DAMAGE_REDUCTION_PASSIVE_TIER = 4
        private const val DAMAGE_REDUCTION = 15
        private const val PRAYER_PASSIVE_TIER = 5
        private const val PRAYER_BONUS = 25
        private const val PENETRATION_PASSIVE_TIER = 6
        private const val PRAYER_PENETRATION = 60

        private const val HEAL_ON_HIT_TIER = 4
        private const val MELEE_HEAL_CHANCE = 5
        private const val MELEE_HEAL_PERCENT = 40
        private const val RANGED_HEAL_EVERY = 5
        private const val RANGED_HEAL = 5
        private const val MAGIC_HEAL_PERCENT = 10

        private const val FAST_RATE_TIER = 3
        private const val FAST_RATE_PERCENT = 80
        private const val HALF_RATE_TIER = 5
        private const val HALF_RATE_ROUND_DOWN_FROM = 5

        private const val MELEE_REROLL_CHANCE = 25

        private const val ECHO_TIER = 2
        private const val ECHO_CHANCE = 10
        private const val ECHO_CHAIN_TIER = 6
        private const val ECHO_CHAIN_CHANCE = 20
        private const val ECHO_CHAIN_LIMIT = 8

        private const val RANGED_FLOOR_PERCENT = 30
        private const val RANGED_STREAK_STEPS = 4
        private const val RANGED_STREAK_BONUS = 0.05
        private const val RANGED_STREAK_TIMEOUT = 8

        private const val MAGIC_BIG_HIT_PERCENT = 90
        private const val MAGIC_GAP_PERCENT = 5
        private const val MAGIC_GAP_CAP = 8
        private const val HITPOINTS_PER_PERCENT = 100
        private const val MAGIC_HITPOINTS_CAP = 10
    }
}
