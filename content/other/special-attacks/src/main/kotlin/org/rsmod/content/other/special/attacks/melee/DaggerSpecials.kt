package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.MeleeRoll
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.PrayerLocks
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The daggers: Puncture (dragon dagger), Abyssal Puncture (abyssal dagger), the candle's Celebrate
 * and the Hunllef-piercing crystal dagger of the echo Gauntlet.
 */
class DaggerSpecials @Inject constructor(private val prayerLocks: PrayerLocks) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val puncture = Puncture(manager)
        registerMelee(sobjs.dragon_dagger, puncture)
        registerMelee(sobjs.dragon_dagger_p, puncture)
        registerMelee(sobjs.dragon_dagger_p_plus, puncture)
        registerMelee(sobjs.dragon_dagger_p_plus_plus, puncture)
        registerMelee(sobjs.br_dragon_dagger, puncture)
        registerMelee(sobjs.bh_dragon_dagger_corrupted, puncture)
        registerMelee(sobjs.bh_dragon_dagger_p_corrupted, puncture)
        registerMelee(sobjs.bh_dragon_dagger_p_plus_corrupted, puncture)
        registerMelee(sobjs.bh_dragon_dagger_p_plus_plus_corrupted, puncture)

        val abyssal = AbyssalPuncture(manager, damage = 0.85)
        registerMelee(sobjs.abyssal_dagger, abyssal)
        registerMelee(sobjs.abyssal_dagger_p, abyssal)
        registerMelee(sobjs.abyssal_dagger_p_plus, abyssal)
        registerMelee(sobjs.abyssal_dagger_p_plus_plus, abyssal)

        // The Bounty Hunter imbued daggers lose 5% of the damage per hit instead of 15%.
        val imbued = AbyssalPuncture(manager, damage = 0.95)
        registerMelee(sobjs.bh_abyssal_dagger_imbue, imbued)
        registerMelee(sobjs.bh_abyssal_dagger_p_imbue, imbued)
        registerMelee(sobjs.bh_abyssal_dagger_p_plus_imbue, imbued)
        registerMelee(sobjs.bh_abyssal_dagger_p_plus_plus_imbue, imbued)

        registerMelee(sobjs.osb10_dragon_candle, Celebrate(manager))
        registerMelee(sobjs.echo_gauntlet_crystal_dagger_t3, CrystallineSeverance(manager, prayerLocks))
    }

    /** Two quick slashes, each with 15% more accuracy and damage. */
    private class Puncture(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.puncture, fx_spots.sp_attack_puncture_spotanim, 92)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            repeat(2) {
                val roll = rollMelee(target, attack, accuracy = 1.15, damage = 1.15)
                hitMelee(target, attack, roll)
            }
            return resume(target)
        }
    }

    /**
     * Two hits for 25% more accuracy and (by default) 15% less damage each. The first rolls for
     * accuracy; if it deals damage the second cannot miss, otherwise the second rolls too.
     */
    private class AbyssalPuncture(manager: SpecialAttackManager, private val damage: Double) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.abyssal_dagger_special, fx_spots.abyssal_dagger_special_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val first = rollMelee(target, attack, accuracy = 1.25, damage = damage)
            hitMelee(target, attack, first)
            val second =
                if (first.landed) {
                    // Guaranteed to land: it only has the damage left to roll.
                    val rolled = manager.rollMeleeMaxHit(this, target, attack.type, attack.style, damage)
                    MeleeRoll(true, rolled, first.maxHit)
                } else {
                    rollMelee(target, attack, accuracy = 1.25, damage = damage)
                }
            hitMelee(target, attack, second)
            return resume(target)
        }
    }

    /** The dragon candle's party trick: an ordinary hit with some confetti. */
    private class Celebrate(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.fx_dagger01_candle01_special, fx_spots.fx_dagger01_candle01_special)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            hitMelee(target, attack, rollMelee(target, attack))
            return resume(target)
        }
    }

    /**
     * The crystal dagger's attack, made for the Hunllef: it hits normally and, on a player, takes
     * the protection prayers away for a few seconds. Against anything else it is an ordinary hit.
     */
    private class CrystallineSeverance(
        manager: SpecialAttackManager,
        private val prayerLocks: PrayerLocks,
    ) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.puncture, fx_spots.sp_attack_puncture_spotanim, 92)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 1.25)
            hitMelee(target, attack, roll)
            if (roll.accurate && target is Player) {
                prayerLocks.lock(target, PRAYER_LOCK_TICKS)
            }
            return resume(target)
        }

        private companion object {
            const val PRAYER_LOCK_TICKS = 8
        }
    }
}
