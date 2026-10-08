package org.rsmod.api.combat.mastery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Player

class CombatMasteryEffectsTest {
    /** Returns the queued numbers in order, whatever bounds it is asked for. */
    private class ScriptedRandom(vararg values: Int) : GameRandom {
        private val queue = ArrayDeque(values.toList())

        val remaining: Int
            get() = queue.size

        override fun of(maxExclusive: Int): Int = queue.removeFirst()

        /** The bounds of every ranged roll asked for, in order. */
        val bounds = mutableListOf<IntRange>()

        override fun of(minInclusive: Int, maxInclusive: Int): Int {
            bounds += minInclusive..maxInclusive
            return queue.removeFirst()
        }

        override fun randomDouble(): Double = queue.removeFirst() / 100.0
    }

    private class Tiers(var melee: Int = 0, var ranged: Int = 0, var magic: Int = 0) :
        CombatMasteryProvider {
        override fun tier(player: Player, style: MasteryStyle): Int =
            when (style) {
                MasteryStyle.Melee -> melee
                MasteryStyle.Ranged -> ranged
                MasteryStyle.Magic -> magic
            }
    }

    private val player = Player()

    private fun effects(tiers: Tiers = Tiers(), vararg rolls: Int): CombatMasteryEffects =
        CombatMasteryEffects(CombatMasteries(setOf(tiers)), ScriptedRandom(*rolls))

    private fun Player.at(tick: Int) = apply { currentMapClock = tick }

    @Test
    fun `accuracy is doubled by the third passive tier only`() {
        assertEquals(1.0, effects(Tiers(melee = 2)).accuracyMultiplier(player))
        assertEquals(2.0, effects(Tiers(melee = 3)).accuracyMultiplier(player))
        assertEquals(2.0, effects(Tiers(magic = 6)).accuracyMultiplier(player))
    }

    @Test
    fun `without the mastery a melee hit is a single roll`() {
        val random = ScriptedRandom(4, 99, 9)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers())), random)

        assertEquals(4, effects.rollMelee(player, 10))
        assertEquals(2, random.remaining)
    }

    @Test
    fun `melee one rolls twice a quarter of the time and keeps the highest`() {
        // First roll 4, chance roll 24 (under 25), second roll 9.
        assertEquals(9, effects(Tiers(melee = 1), 4, 24, 9).rollMelee(player, 10))
        // The second roll is lower, so the first stands.
        assertEquals(7, effects(Tiers(melee = 1), 7, 0, 2).rollMelee(player, 10))
        // Chance roll 25 is not under 25: no second roll.
        val random = ScriptedRandom(4, 25, 9)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 1))), random)
        assertEquals(4, effects.rollMelee(player, 10))
        assertEquals(1, random.remaining)
    }

    @Test
    fun `ranged one lifts low rolls to thirty percent of the max hit`() {
        val tiers = Tiers(ranged = 1)
        assertEquals(6, effects(tiers, 2).rollRanged(player, 20))
        assertEquals(6, effects(tiers, 6).rollRanged(player, 20))
        assertEquals(11, effects(tiers, 11).rollRanged(player, 20))
        // A max hit under 4 has a floor of 0, so the roll itself is kept.
        assertEquals(1, effects(tiers, 1).rollRanged(player, 3))
        assertEquals(2, effects(Tiers(), 2).rollRanged(player, 20))
    }

    @Test
    fun `ranged two adds five percent per attack and starts over after twenty`() {
        val effects = effects(Tiers(ranged = 2))
        val multipliers = (0..5).map { effects.rangedMaxHitMultiplier(player.at(10 + it * 3)) }

        assertEquals(
            listOf(1.0, 1.05, 1.1, 1.15, 1.2, 1.0),
            multipliers.map { Math.round(it * 100) / 100.0 },
        )
    }

    @Test
    fun `ranged two counts several rolls in one tick as one attack`() {
        val effects = effects(Tiers(ranged = 2))

        assertEquals(1.0, effects.rangedMaxHitMultiplier(player.at(10)), 0.0001)
        assertEquals(1.05, effects.rangedMaxHitMultiplier(player.at(13)), 0.0001)
        assertEquals(1.05, effects.rangedMaxHitMultiplier(player.at(13)), 0.0001)
        assertEquals(1.1, effects.rangedMaxHitMultiplier(player.at(16)), 0.0001)
    }

    @Test
    fun `ranged two starts over after a long pause`() {
        val effects = effects(Tiers(ranged = 2))
        effects.rangedMaxHitMultiplier(player.at(10))
        effects.rangedMaxHitMultiplier(player.at(13))
        effects.rangedMaxHitMultiplier(player.at(16))

        assertEquals(1.0, effects.rangedMaxHitMultiplier(player.at(60)), 0.0001)
        assertEquals(1.05, effects.rangedMaxHitMultiplier(player.at(63)), 0.0001)
    }

    @Test
    fun `ranged six never misses npcs only`() {
        val effects = effects(Tiers(ranged = 6))
        assertTrue(effects.neverMissRanged(player, pvm = true))
        assertFalse(effects.neverMissRanged(player, pvm = false))
        assertFalse(effects(Tiers(ranged = 5)).neverMissRanged(player, pvm = true))
    }

    @Test
    fun `magic two adds five percent per tick since the last attack up to forty`() {
        val effects = effects(Tiers(magic = 2))

        // No previous attack: the full bonus.
        assertEquals(1.4, effects.magicMaxHitMultiplier(player.at(10), 500), 0.0001)
        // Five ticks later: +25%. A second roll in the same tick sees the same bonus.
        assertEquals(1.25, effects.magicMaxHitMultiplier(player.at(15), 500), 0.0001)
        assertEquals(1.25, effects.magicMaxHitMultiplier(player.at(15), 500), 0.0001)
        // Two ticks later: +10%.
        assertEquals(1.1, effects.magicMaxHitMultiplier(player.at(17), 500), 0.0001)
        // A long pause is capped at +40%.
        assertEquals(1.4, effects.magicMaxHitMultiplier(player.at(100), 500), 0.0001)
    }

    @Test
    fun `magic six adds one percent per hundred target hitpoints up to ten`() {
        val effects = effects(Tiers(magic = 6))

        // Magic two is also unlocked at tier six, and its bonus multiplies in. A first attack
        // gets its full 40%, and under a hundred hitpoints there is no bonus from magic six.
        val two = 1.4
        assertEquals(two, effects.magicMaxHitMultiplier(player.at(10), 99), 0.0001)
        assertEquals(two * 1.02, effects.magicMaxHitMultiplier(player.at(40), 250), 0.0001)
        assertEquals(two * 1.1, effects.magicMaxHitMultiplier(player.at(80), 5_000), 0.0001)
    }

    @Test
    fun `scaling keeps the bottom of the range`() {
        val effects = effects()
        assertEquals(3..100, effects.scale(3..100, 1.0))
        assertEquals(3..140, effects.scale(3..100, 1.4))
    }

    @Test
    fun `magic one adds half again to rolls above ninety percent of the max hit`() {
        val one = effects(Tiers(magic = 1))
        assertEquals(136, one.adjustMagicDamage(player, 91, 100, 500))
        assertEquals(90, one.adjustMagicDamage(player, 90, 100, 500))
        assertEquals(40, one.adjustMagicDamage(player, 40, 100, 500))
        assertEquals(91, effects().adjustMagicDamage(player, 91, 100, 500))
    }

    @Test
    fun `magic six maxes a hit that would kill`() {
        val six = effects(Tiers(magic = 6))

        // Target has 50 left against a max hit of 100: the hit becomes a max hit, which also counts
        // as a roll above ninety percent for magic one.
        assertEquals(150, six.adjustMagicDamage(player, 10, 100, 50))
        // A target with at least the max hit left is not affected by that half.
        assertEquals(10, six.adjustMagicDamage(player, 10, 100, 100))
        // A miss stays a miss.
        assertEquals(0, six.adjustMagicDamage(player, 0, 100, 50))
    }

    @Test
    fun `attack rate is untouched below tier three`() {
        for (tier in 0..2) {
            val effects = effects(Tiers(melee = tier))
            for (ticks in 1..8) {
                assertEquals(ticks, effects.attackDelay(player, MasteryStyle.Melee, ticks))
            }
        }
    }

    @Test
    fun `tier three sets the attack rate to eighty percent rounded down`() {
        val effects = effects(Tiers(ranged = 3))
        val expected = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 3, 5 to 4, 6 to 4, 7 to 5, 8 to 6)
        for ((ticks, delay) in expected) {
            assertEquals(
                delay,
                effects.attackDelay(player, MasteryStyle.Ranged, ticks),
                "$ticks ticks",
            )
        }
        // Tier four changes nothing about the rate.
        assertEquals(4, effects(Tiers(ranged = 4)).attackDelay(player, MasteryStyle.Ranged, 5))
    }

    @Test
    fun `tier five sets the attack rate to half, down from five ticks and up below`() {
        val effects = effects(Tiers(magic = 5))
        val expected = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 2, 5 to 2, 6 to 3, 7 to 3, 8 to 4)
        for ((ticks, delay) in expected) {
            assertEquals(
                delay,
                effects.attackDelay(player, MasteryStyle.Magic, ticks),
                "$ticks ticks",
            )
        }
        assertEquals(2, effects(Tiers(magic = 6)).attackDelay(player, MasteryStyle.Magic, 5))
    }

    @Test
    fun `attack rate only follows the matching path`() {
        val effects = effects(Tiers(melee = 5, ranged = 3))

        assertEquals(2, effects.attackDelay(player, MasteryStyle.Melee, 4))
        assertEquals(3, effects.attackDelay(player, MasteryStyle.Ranged, 4))
        assertEquals(4, effects.attackDelay(player, MasteryStyle.Magic, 4))
    }

    @Test
    fun `no echoes below melee two`() {
        val random = ScriptedRandom(0, 5)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 1))), random)

        assertEquals(emptyList<Int>(), effects.rollEchoes(player, 20) { true })
        assertEquals(2, random.remaining)
    }

    @Test
    fun `melee two echoes a tenth of the time for up to half the max hit`() {
        // Chance roll 9 (under 10), the accuracy roll passes, and the damage roll is 4.
        val random = ScriptedRandom(9, 4)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 2))), random)

        assertEquals(listOf(4), effects.rollEchoes(player, 21) { true })
        assertEquals(listOf(1..10), random.bounds)
        assertEquals(0, random.remaining)

        // Chance roll 10 is not under 10: no echo, and no accuracy roll.
        var asked = false
        assertEquals(
            emptyList<Int>(),
            effects(Tiers(melee = 2), 10).rollEchoes(player, 21) {
                asked = true
                true
            },
        )
        assertFalse(asked)
    }

    @Test
    fun `a missed echo does nothing, and tier two never chains`() {
        // The chance roll passes, the accuracy roll fails, so no damage roll is made. The single
        // echo is all tier two allows, so no second chance roll is made either.
        val random = ScriptedRandom(0, 0)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 2))), random)

        assertEquals(listOf(0), effects.rollEchoes(player, 20) { false })
        assertEquals(1, random.remaining)
        assertTrue(random.bounds.isEmpty())
    }

    @Test
    fun `melee six echoes a fifth of the time and chains until a chance roll fails`() {
        // Chance 19 (echo, damage 5), chance 0 (echo, damage 7), chance 20 (stop).
        val random = ScriptedRandom(19, 5, 0, 7, 20)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 6))), random)

        assertEquals(listOf(5, 7), effects.rollEchoes(player, 40) { true })
        assertEquals(0, random.remaining)
        assertEquals(listOf(1..20, 1..20), random.bounds)
    }

    @Test
    fun `an echo chain stops at eight echoes`() {
        // Ten echoes' worth of rolls are queued, but only eight are used.
        val rolls = IntArray(20) { if (it % 2 == 0) 0 else 3 }
        val random = ScriptedRandom(*rolls)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 6))), random)

        assertEquals(List(8) { 3 }, effects.rollEchoes(player, 10) { true })
        assertEquals(4, random.remaining)
    }

    @Test
    fun `an echo always rolls at least one`() {
        val random = ScriptedRandom(0, 1)
        val effects = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 2))), random)

        effects.rollEchoes(player, 1) { true }

        assertEquals(listOf(1..1), random.bounds)
    }

    @Test
    fun `resources are saved ninety five percent of the time with the first passive`() {
        // 94 is under 95 (saved), 95 is not.
        assertTrue(effects(Tiers(melee = 1), 94).savesResource(player))
        assertFalse(effects(Tiers(melee = 1), 95).savesResource(player))
        // Without the passive nothing is saved, and no roll is made.
        val random = ScriptedRandom(0)
        val none = CombatMasteryEffects(CombatMasteries(setOf(Tiers())), random)
        assertFalse(none.savesResource(player))
        assertEquals(1, random.remaining)
    }

    @Test
    fun `healing is twenty percent higher, carrying the part that rounds away`() {
        val effects = effects(Tiers(magic = 2))

        // Five hitpoints is exactly 6.
        assertEquals(6, effects.healAmount(player, 5))
        // One hitpoint is 1.2: 1, 1, 1, 1, then the carried 0.2s add up to 2.
        val ones = List(5) { effects.healAmount(player, 1) }
        assertEquals(listOf(1, 1, 1, 1, 2), ones)
        // Nothing to heal stays nothing, and the passive does not apply below tier two.
        assertEquals(0, effects.healAmount(player, 0))
        assertEquals(5, effects(Tiers(magic = 1)).healAmount(player, 5))
    }

    @Test
    fun `damage taken is fifteen percent lower, rounded down, with the fourth passive`() {
        val effects = effects(Tiers(ranged = 4))

        assertEquals(0, effects.reduceDamageTaken(player, 0))
        assertEquals(0, effects.reduceDamageTaken(player, 1))
        assertEquals(1, effects.reduceDamageTaken(player, 2))
        assertEquals(17, effects.reduceDamageTaken(player, 20))
        assertEquals(85, effects.reduceDamageTaken(player, 100))
        assertEquals(20, effects(Tiers(ranged = 3)).reduceDamageTaken(player, 20))
    }

    @Test
    fun `prayer gain is twenty five percent higher with the fifth passive`() {
        val effects = effects(Tiers(melee = 5))

        assertEquals(35, effects.prayerGain(player, 28))
        // One point is 1.25: 1, 1, 1, then the carried quarters make 2.
        assertEquals(listOf(1, 1, 1, 2), List(4) { effects.prayerGain(player, 1) })
        assertEquals(28, effects(Tiers(melee = 4)).prayerGain(player, 28))
    }

    @Test
    fun `prayer penetration is sixty percent with the sixth passive`() {
        assertEquals(60, effects(Tiers(magic = 6)).prayerPenetration(player))
        assertEquals(0, effects(Tiers(magic = 5)).prayerPenetration(player))
    }

    @Test
    fun `melee four heals forty percent of the damage five percent of the time`() {
        // Chance roll 4 (under 5): heal 40% of 30, which is 12. Tier four also has the healing
        // passive, so it is 20% more: 14.
        assertEquals(14, effects(Tiers(melee = 4), 4).meleeHitHeal(player, 30))
        assertEquals(0, effects(Tiers(melee = 4), 5).meleeHitHeal(player, 30))
        // No damage, or tier three, makes no roll.
        val random = ScriptedRandom(0)
        val tierThree = CombatMasteryEffects(CombatMasteries(setOf(Tiers(melee = 3))), random)
        assertEquals(0, tierThree.meleeHitHeal(player, 30))
        assertEquals(1, random.remaining)
        assertEquals(0, effects(Tiers(melee = 4)).meleeHitHeal(player, 0))
    }

    @Test
    fun `ranged four heals five hitpoints on every fifth hit that deals damage`() {
        val effects = effects(Tiers(ranged = 4))

        val heals = List(10) { effects.rangedHitHeal(player, 7) }

        // Five hitpoints, plus the 20% from the healing passive that tier four includes.
        assertEquals(listOf(0, 0, 0, 0, 6, 0, 0, 0, 0, 6), heals)
        // A hit that deals nothing is not counted.
        assertEquals(0, effects.rangedHitHeal(player, 0))
        assertEquals(0, effects.rangedHitHeal(player, 7))
    }

    @Test
    fun `magic four heals a tenth of a hit above ninety percent of the max hit`() {
        val effects = effects(Tiers(magic = 4))

        // A tenth of 91 is 9, and the healing passive raises it to 10.
        assertEquals(10, effects.magicHitHeal(player, 91, 100))
        assertEquals(0, effects.magicHitHeal(player, 90, 100))
        assertEquals(0, effects.magicHitHeal(player, 0, 100))
        assertEquals(0, effects(Tiers(magic = 3)).magicHitHeal(player, 100, 100))
    }

    @Test
    fun `heals from masteries are also raised by the healing passive`() {
        // Magic four is tier four, so the healing passive (tier two) is on too: 10 becomes 12.
        assertEquals(12, effects(Tiers(magic = 4)).magicHitHeal(player, 100, 100))
    }
}
