package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.PrayerLocks
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The sword family: Wild Stab (dragon sword), Sever (dragon scimitar), Feint (Vesta's longsword),
 * Eviscerate (Osmumten's fang), Flames of Ralos (Arkan blade), Disrupt (Voidwaker), Saradomin's
 * Lightning (both Saradomin swords) and Impale (rune claws).
 */
class SwordSpecials
@Inject
constructor(private val prayerLocks: PrayerLocks, private val dots: SpecialDots) :
    SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val wildStab = WildStab(manager)
        registerMelee(sobjs.dragon_shortsword, wildStab)
        registerMelee(sobjs.br_dragon_sword, wildStab)
        registerMelee(sobjs.bh_dragon_shortsword_corrupted, wildStab)

        val sever = Sever(manager, prayerLocks)
        registerMelee(sobjs.dragon_scimitar, sever)
        registerMelee(sobjs.dragon_scimitar_ornament, sever)
        registerMelee(sobjs.br_dragon_scimitar, sever)
        registerMelee(sobjs.bh_dragon_scimitar_corrupted, sever)

        val feint = Feint(manager)
        registerMelee(sobjs.vestas_longsword, feint)
        registerMelee(sobjs.br_vestas_longsword, feint)
        registerMelee(sobjs.bh_vestas_longsword, feint)
        registerMelee(sobjs.vestas_longsword_bh, feint)

        val eviscerate = Eviscerate(manager)
        registerMelee(sobjs.osmumtens_fang, eviscerate)
        registerMelee(sobjs.osmumtens_fang_ornament, eviscerate)

        registerMelee(sobjs.arkan_blade, FlamesOfRalos(manager, dots))
        val disrupt = Disrupt(manager)
        registerMelee(sobjs.voidwaker, disrupt)
        registerMelee(sobjs.br_voidwaker, disrupt)
        registerMelee(sobjs.deadman_blighted_voidwaker, disrupt)
        registerMelee(sobjs.deadman_voidwaker, disrupt)

        registerMelee(sobjs.saradomin_sword, SaradominsLightning(manager, blessed = false))
        val blessed = SaradominsLightning(manager, blessed = true)
        registerMelee(sobjs.blessed_saradomin_sword, blessed)
        registerMelee(sobjs.blessed_saradomin_sword_degraded, blessed)

        registerMelee(sobjs.rune_claws, Impale(manager))
    }

    /** 25% more accuracy and damage, and it goes through Protect from Melee. */
    private class WildStab(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.human_dragon_sword_spec, fx_spots.dragon_sword_spec_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 1.25, damage = 1.25)
            hitMelee(target, attack, roll, ignoreProtection = true)
            return resume(target)
        }
    }

    /**
     * More accuracy against a slash defence. A player it hits cannot use the protection prayers for
     * five seconds (an npc has none to take away).
     */
    private class Sever(manager: SpecialAttackManager, private val prayerLocks: PrayerLocks) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.sp_attack_dragon_scimitar,
                targetSpot = fx_spots.sp_attack_dragon_scimitar_trail_spotanim,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 1.25, blockType = MeleeAttackType.Slash)
            hitMelee(target, attack, roll)
            if (roll.accurate && target is Player) {
                prayerLocks.lock(target, PRAYER_LOCK_TICKS)
            }
            return resume(target)
        }

        private companion object {
            /** Five seconds. */
            const val PRAYER_LOCK_TICKS = 8
        }
    }

    /**
     * The target defends as if its Defence level were a quarter of what it is, and a hit deals at
     * least 20% of the max hit, up to 120% of it.
     */
    private class Feint(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.human_staff_spike)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val base = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val roll =
                rollMelee(
                    target,
                    attack,
                    damage = 1.2,
                    defenceFactor = 0.25,
                    minFlat = (base * 0.2).toInt(),
                )
            hitMelee(target, attack, roll)
            return resume(target)
        }
    }

    /** An attack with the weapon's true maximum hit, rolled between 15% and all of it. */
    private class Eviscerate(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.weapon_sword_osmumten03_special,
                targetSpot = fx_spots.spotanim_weapon_sword_osmumten_special,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 1.5, minHit = 0.15)
            hitMelee(target, attack, roll)
            return resume(target)
        }
    }

    /** 50% more accuracy and damage; a hit burns the target for 10 damage over 24 seconds. */
    private class FlamesOfRalos(manager: SpecialAttackManager, private val dots: SpecialDots) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.vmq4_arkan_blade_special,
                targetSpot = fx_spots.vmq4_arkan_blade_special_spotanim,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 1.5, damage = 1.5)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                dots.burn(target, player, damage = BURN_DAMAGE, interval = BURN_INTERVAL)
            }
            return resume(target)
        }

        private companion object {
            const val BURN_DAMAGE = 10

            /** 24 seconds over 10 hits. */
            const val BURN_INTERVAL = 4
        }
    }

    /**
     * A bolt of lightning that cannot miss: it deals between half and one and a half times the
     * melee max hit, as magic damage.
     */
    private class Disrupt(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.human_special_voidwaker, fx_spots.fx_voidwaker02_special)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            target.spotanim(fx_spots.fx_voidwaker_impact, height = 124)
            val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val damage = random.of((maxHit / 2)..(maxHit + maxHit / 2))
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueMagicHit(this, target, damage, clientDelay = 0, hitDelay = 1)
            return resume(target)
        }
    }

    /**
     * The Saradomin swords call down lightning. The sword deals 10% more melee damage and 1 to 16
     * magic damage on top (the blessed sword just raises the max hit by 25%).
     */
    private class SaradominsLightning(manager: SpecialAttackManager, private val blessed: Boolean) :
        MeleeSpecial(manager) {
        private val fx =
            if (blessed) {
                SpecialFx(
                    fx_seqs.blessed_saradomin_sword_special_player,
                    targetSpot = fx_spots.godwars_saradomin_sword_attack_spot,
                )
            } else {
                SpecialFx(
                    fx_seqs.saradomin_sword_special_player,
                    fx_spots.dh_sword_update_saradomin_god_special_spotanim,
                    targetSpot = fx_spots.godwars_armadyl_bolt_hit_spotanim,
                )
            }

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            if (blessed) {
                val roll =
                    rollMelee(target, attack, damage = 1.25, blockType = MeleeAttackType.Slash)
                hitMelee(target, attack, roll)
                return resume(target)
            }
            val roll = rollMelee(target, attack, damage = 1.1, blockType = MeleeAttackType.Slash)
            hitMelee(target, attack, roll)
            if (roll.accurate) {
                val lightning = random.of(LIGHTNING_MIN..LIGHTNING_MAX)
                manager.queueMagicHit(this, target, lightning, clientDelay = 0, hitDelay = 1)
            }
            return resume(target)
        }

        private companion object {
            const val LIGHTNING_MIN = 1
            const val LIGHTNING_MAX = 16
        }
    }

    /** 10% more Attack and Strength, at the cost of a slower swing (two ticks). */
    private class Impale(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val roll = rollMelee(target, attack, accuracy = 1.1, damage = 1.1)
            hitMelee(target, attack, roll)
            slowNextAttack(EXTRA_DELAY)
            return resume(target)
        }

        private companion object {
            const val EXTRA_DELAY = 2
        }
    }
}
