package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.game.entity.PathingEntity

/**
 * The claws that strike several times: Slice and Dice (dragon claws, four hits) and Burning
 * barrage (bone claws, three hits that may burn).
 *
 * Neither rolls each hit on its own. The accuracy is rolled hit by hit until one lands, and the
 * damage is rolled once, from a range that depends on which roll landed, and shared out between the
 * hits that follow: a landing first roll leaves most of the damage in the first hit, a later one
 * puts it in the later hits.
 */
class ClawSpecials @Inject constructor(private val dots: SpecialDots) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val sliceAndDice = SliceAndDice(manager)
        registerMelee(sobjs.dragon_claws, sliceAndDice)
        registerMelee(sobjs.br_dragon_claws, sliceAndDice)
        registerMelee(sobjs.dragon_claws_ornament, sliceAndDice)
        registerMelee(sobjs.bh_dragon_claws_corrupted, sliceAndDice)
        registerMelee(sobjs.deadman_blighted_dragon_claws, sliceAndDice)

        registerMelee(sobjs.bone_claws, BurningBarrage(manager, dots))
    }

    /**
     * How the damage of the first accurate roll is spread over the hits.
     *
     * @property min the smallest damage the shared roll can be, as a share of the max hit.
     * @property max the largest damage the shared roll can be, as a share of the max hit.
     * @property shares how much of the rolled damage each hit gets.
     * @property bonuses damage added to each hit after its share is worked out.
     */
    private class Branch(
        val min: Double,
        val max: Double,
        val shares: DoubleArray,
        val bonuses: IntArray,
    )

    /** Rolls the cascade: the damage of each hit, in order. */
    private abstract class Cascade(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        protected abstract val branches: List<Branch>

        /** Hit patterns for when every accuracy roll fails, picked from at random. */
        protected open val allMiss: List<IntArray>
            get() = listOf(IntArray(branches.first().shares.size))

        protected fun ProtectedAccess.rollCascade(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): IntArray {
            val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            for (branch in branches) {
                val accurate =
                    manager.rollMeleeAccuracy(
                        source = this,
                        target = target,
                        attackType = attack.type,
                        attackStyle = attack.style,
                        blockType = attack.type,
                        multiplier = 1.0,
                    )
                if (!accurate) {
                    continue
                }
                val low = (maxHit * branch.min).toInt()
                val high = max(low, (maxHit * branch.max).toInt())
                val rolled = random.of(low..high)
                return IntArray(branch.shares.size) { hit ->
                    max(0, (rolled * branch.shares[hit]).toInt() + branch.bonuses[hit])
                }
            }
            return random.pick(allMiss)
        }
    }

    private class SliceAndDice(manager: SpecialAttackManager) : Cascade(manager) {
        private val fx =
            SpecialFx(fx_seqs.human_dragon_claws_spec, fx_spots.dragon_claws_spot, spotHeight = 100)

        override val branches =
            listOf(
                Branch(0.5, 1.0, doubleArrayOf(1.0, 0.5, 0.25, 0.25), intArrayOf(0, 0, 0, 1)),
                Branch(0.375, 0.875, doubleArrayOf(0.0, 1.0, 0.5, 0.5), intArrayOf(0, 0, 0, 1)),
                Branch(0.25, 0.75, doubleArrayOf(0.0, 0.0, 1.0, 1.0), intArrayOf(0, 0, 0, 1)),
                Branch(0.25, 1.25, doubleArrayOf(0.0, 0.0, 0.0, 1.0), intArrayOf(0, 0, 0, 0)),
            )

        // Without a single landing roll, a third of the time nothing hits and the rest of the time
        // two of the hits deal 1.
        override val allMiss =
            listOf(
                intArrayOf(0, 0, 0, 0),
                intArrayOf(0, 0, 0, 0),
                intArrayOf(1, 1, 0, 0),
                intArrayOf(0, 0, 1, 1),
                intArrayOf(1, 0, 1, 0),
                intArrayOf(0, 1, 0, 1),
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val hits = rollCascade(target, attack)
            for ((index, damage) in hits.withIndex()) {
                hitMelee(target, attack, damage, delay = if (index < 2) 1 else 2)
            }
            return resume(target)
        }
    }

    private class BurningBarrage(manager: SpecialAttackManager, private val dots: SpecialDots) :
        Cascade(manager) {
        private val fx =
            SpecialFx(fx_seqs.human_weapon_burning_claws_02_spec, fx_spots.vfx_burning_claws_spec_02)

        override val branches =
            listOf(
                Branch(0.75, 1.75, doubleArrayOf(0.25, 0.25, 0.5), intArrayOf(0, 0, 0)),
                Branch(0.5, 1.5, doubleArrayOf(0.5, 0.5, 0.0), intArrayOf(-1, -1, 2)),
                Branch(0.25, 1.25, doubleArrayOf(0.0, 0.0, 1.0), intArrayOf(1, 1, -2)),
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val hits = rollCascade(target, attack)
            for ((index, damage) in hits.withIndex()) {
                hitMelee(target, attack, damage, delay = if (index < 2) 1 else 2)
                if (damage > 0 && random.randomBoolean(BURN_CHANCE)) {
                    dots.burn(target, player, damage = BURN_DAMAGE, interval = BURN_INTERVAL)
                }
            }
            return resume(target)
        }

        private companion object {
            /** Each hit that lands has a one in two chance to burn. */
            const val BURN_CHANCE = 2
            const val BURN_DAMAGE = 10
            const val BURN_INTERVAL = 4
        }
    }
}
