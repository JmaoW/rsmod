package org.rsmod.content.other.special.attacks.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.AreaTargets
import org.rsmod.content.other.special.attacks.support.RangedKit
import org.rsmod.content.other.special.attacks.support.RangedSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.spot.SpotanimType

/**
 * The thrown weapons: Chainhit (rune throwing axe), Momentum Throw (dragon throwing axe), Duality
 * (dragon knives), Hamstring (Morrigan's throwing axes) and Phantom Strike (Morrigan's javelins).
 */
class ThrownSpecials
@Inject
constructor(
    private val kit: RangedKit,
    private val areas: AreaTargets,
    private val dots: SpecialDots,
    private val worldQueues: WorldQueueList,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged(sobjs.rune_thrownaxe, Chainhit(manager, kit, areas))

        val momentum = MomentumThrow(manager, kit)
        registerRanged(sobjs.dragon_thrownaxe, momentum)
        registerRanged(sobjs.br_dragon_thrownaxe, momentum)

        val duality = Duality(manager, kit, poisoned = false)
        registerRanged(sobjs.dragon_knife, duality)
        registerRanged(sobjs.br_dragon_knife, duality)
        val poisonedDuality = Duality(manager, kit, poisoned = true)
        registerRanged(sobjs.dragon_knife_p, poisonedDuality)
        registerRanged(sobjs.dragon_knife_p_plus, poisonedDuality)
        registerRanged(sobjs.dragon_knife_p_plus_plus, poisonedDuality)

        registerRanged(sobjs.morrigans_thrownaxe, Hamstring(manager, kit, worldQueues, bounty = false))
        registerRanged(sobjs.morrigans_thrownaxe_bh, Hamstring(manager, kit, worldQueues, bounty = true))

        val phantom = PhantomStrike(manager, kit, dots, bleed = 100, accuracy = 1.0)
        registerRanged(sobjs.morrigans_javelin, phantom)
        registerRanged(sobjs.br_morrigans_javelin, phantom)
        registerRanged(
            sobjs.morrigans_javelin_bh,
            PhantomStrike(manager, kit, dots, bleed = 75, accuracy = 1.5),
        )
    }

    /**
     * The axe hits the target and then chains to other targets near it, at ten percent of the
     * special attack bar for each one.
     */
    private class Chainhit(manager: SpecialAttackManager, kit: RangedKit, private val areas: AreaTargets) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.chainhit)
            spotanim(fx_spots.sp_attack_chainhit_launch_spotanim, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.sp_attack_chainhit_travel_spotanim, projanims.thrown)
            hitRanged(shot, target, attack, rollRanged(target, attack).damage, proj)

            // Each further target costs ten percent more, and the first five percent of the weapon's
            // own cost is taken once this returns.
            val spare = manager.specialEnergy(this) - COST
            val affordable = (spare / CHAIN_COST).coerceAtLeast(0)
            if (affordable > 0) {
                val others =
                    with(areas) { around(target, radius = CHAIN_RADIUS, limit = affordable) }
                for (other in others) {
                    val chained = fire(shot, other, fx_spots.sp_attack_chainhit_travel_spotanim, projanims.thrown)
                    hitRanged(shot, other, attack, rollRanged(other, attack).damage, chained)
                    manager.takeSpecialEnergy(this, CHAIN_COST)
                }
            }
            return resume(target)
        }

        private companion object {
            /** What the weapon costs on its own: ten percent. */
            const val COST = 100
            const val CHAIN_COST = 100
            const val CHAIN_RADIUS = 3
        }
    }

    /** A fast throw with 25% more accuracy: the next attack comes two ticks sooner. */
    private class MomentumThrow(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.human_dragon_taxe_spec)
            spotanim(fx_spots.dragon_taxe_launch_spec, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.dragon_taxe_travel_spec, projanims.thrown)
            hitRanged(shot, target, attack, rollRanged(target, attack, accuracy = 1.25).damage, proj)
            slowNextAttack(-2)
            return resume(target)
        }
    }

    /** Two knives, thrown together and rolled on their own. */
    private class Duality(manager: SpecialAttackManager, kit: RangedKit, private val poisoned: Boolean) :
        RangedSpecial(manager, kit) {
        private val travel: SpotanimType =
            if (poisoned) fx_spots.dragon_tknife_travel_spec_p else fx_spots.dragon_tknife_travel_spec

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack, count = 2) ?: return false
            anim(if (poisoned) fx_seqs.human_dragon_tknives_spec_poison else fx_seqs.human_dragon_tknives_spec)
            val first = fire(shot, target, travel, projanims.doublearrow_one)
            val second = fire(shot, target, travel, projanims.doublearrow_two)
            hitRanged(shot, target, attack, rollRanged(target, attack).damage, first)
            hitRanged(shot, target, attack, rollRanged(target, attack).damage, second, silent = true)
            return resume(target)
        }
    }

    /**
     * Hits for at least 20% of the max hit (half of it for the Bounty Hunter axe, which also hits
     * with 150% accuracy). A player that is hit loses run energy six times as fast for a minute,
     * and the next attack comes a tick sooner.
     */
    private class Hamstring(
        manager: SpecialAttackManager,
        kit: RangedKit,
        private val worldQueues: WorldQueueList,
        private val bounty: Boolean,
    ) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.weapon_morrigans_throwingaxe_special01)
            spotanim(fx_spots.morrigans_taxe_spotanim, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.morrigans_taxe_projanim, projanims.thrown)
            val roll =
                rollRanged(
                    target,
                    attack,
                    accuracy = if (bounty) 1.5 else 1.0,
                    minHit = if (bounty) 0.5 else 0.2,
                )
            hitRanged(shot, target, attack, roll.damage, proj)
            if (roll.landed) {
                if (target is Player) {
                    hamstring(target)
                }
                slowNextAttack(-1)
            }
            return resume(target)
        }

        private fun hamstring(target: Player) {
            target.mes("You have been hamstrung!")
            var ticks = 0
            fun drain() {
                if (ticks++ >= DURATION || target.slotId == PathingEntity.INVALID_SLOT) {
                    return
                }
                if (target.pendingStepCount > 1) {
                    target.runEnergy = (target.runEnergy - EXTRA_DRAIN).coerceAtLeast(0)
                    UpdateRun.energy(target, target.runEnergy)
                }
                worldQueues.add(1) { drain() }
            }
            worldQueues.add(1) { drain() }
        }

        private companion object {
            /** A minute. */
            const val DURATION = 100

            /** Five times what running costs a tick, on top of it: six times as fast. */
            const val EXTRA_DRAIN = 400
        }
    }

    /**
     * Hits as normal, and a player target bleeds that damage again (all of it, or [bleed] percent)
     * over the next few ticks. A npc just takes the hit.
     */
    private class PhantomStrike(
        manager: SpecialAttackManager,
        kit: RangedKit,
        private val dots: SpecialDots,
        private val bleed: Int,
        private val accuracy: Double,
    ) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.weapon_morrigans_javelin_special01)
            spotanim(fx_spots.morrigans_javelin_spotanim, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.morrigans_javelin_projanim, projanims.thrown)
            val roll = rollRanged(target, attack, accuracy = accuracy)
            hitRanged(shot, target, attack, roll.damage, proj)
            if (roll.landed && target is Player) {
                dots.bleed(target, player, damage = roll.damage * bleed / 100, chunk = CHUNK, interval = 1)
            }
            return resume(target)
        }

        private companion object {
            const val CHUNK = 10
        }
    }
}
