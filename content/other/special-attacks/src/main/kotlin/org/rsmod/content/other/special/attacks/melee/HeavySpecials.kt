package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.stat
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.AreaTargets
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * The crushing weapons: Shatter (dragon mace), Smash (both warhammers), Pulverize (Elder maul),
 * Quick Smash (granite maul), Hammer Blow (granite hammer), Penance (abyssal bludgeon), Sunder
 * (Barrelchest anchor), Favour of the War God (ancient mace) and Shield Bash (Dinh's bulwark).
 */
class HeavySpecials @Inject constructor(private val areas: AreaTargets) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val shatter = Shatter(manager, defenceFactor = 1.0)
        registerMelee(sobjs.dragon_mace, shatter)
        registerMelee(sobjs.bh_dragon_mace_corrupted, shatter)
        // The imbued mace also treats the target's Defence as if it were 60%.
        registerMelee(sobjs.bh_dragon_mace_imbue, Shatter(manager, defenceFactor = 0.6))

        val smash = DragonWarhammer(manager)
        registerMelee(sobjs.dragon_warhammer, smash)
        registerMelee(sobjs.br_dragon_warhammer, smash)
        registerMelee(sobjs.dragon_warhammer_ornament, smash)
        registerMelee(sobjs.bh_dragon_warhammer_corrupted, smash)

        val statius = StatiusWarhammer(manager, defenceDrain = 30)
        registerMelee(sobjs.statius_warhammer, statius)
        registerMelee(sobjs.br_statius_warhammer, statius)
        registerMelee(sobjs.statius_warhammer_bh, StatiusWarhammer(manager, defenceDrain = 75))

        val pulverize = Pulverize(manager)
        registerMelee(sobjs.elder_maul, pulverize)
        registerMelee(sobjs.br_elder_maul, pulverize)
        registerMelee(sobjs.elder_maul_ornament, pulverize)

        val quickSmash = QuickSmash(manager)
        registerMelee(sobjs.granite_maul, quickSmash)
        registerMelee(sobjs.granite_maul_pretty, quickSmash)
        registerMelee(sobjs.br_granite_maul, quickSmash)
        registerMelee(sobjs.granite_maul_plus, quickSmash)
        registerMelee(sobjs.granite_maul_pretty_plus, quickSmash)

        registerMelee(sobjs.granite_hammer, HammerBlow(manager))
        registerMelee(sobjs.abyssal_bludgeon, Penance(manager))

        registerMelee(sobjs.brain_anchor, Sunder(manager, damage = 1.1, quickened = false))
        registerMelee(sobjs.bh_brain_anchor_imbue, Sunder(manager, damage = 1.25, quickened = true))

        registerMelee(sobjs.ancient_goblin_mace, FavourOfTheWarGod(manager))

        val bash = ShieldBash(manager, areas)
        registerMelee(sobjs.dinhs_bulwark, bash)
        registerMelee(sobjs.dinhs_bulwark_ornament, bash)
    }

    /** 50% more damage and 25% more accuracy for one hit. */
    private class Shatter(manager: SpecialAttackManager, private val defenceFactor: Double) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.shatter, fx_spots.sp_attack_shatter_spotanim, spotHeight = 92)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll =
                rollMelee(
                    target,
                    attack,
                    accuracy = 1.25,
                    damage = 1.5,
                    defenceFactor = defenceFactor,
                )
            hitMelee(target, attack, roll)
            return resume(target)
        }
    }

    /** 50% more damage, and a hit takes 30% off the target's Defence. */
    private class DragonWarhammer(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.dragon_warhammer_sa_player,
                targetSpot = fx_spots.dragon_warhammer_sa_spotanim,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, damage = 1.5)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                TargetStats.lowerPercent(target, CombatSkill.Defence, percent = 30)
            }
            return resume(target)
        }
    }

    /**
     * A hit deals at least 25% of the max hit, and up to 125% of it; a hit takes [defenceDrain]
     * percent off the target's Defence.
     */
    private class StatiusWarhammer(manager: SpecialAttackManager, private val defenceDrain: Int) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.dragon_warhammer_sa_player,
                targetSpot = fx_spots.statius_hammer_sa_spotanim,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val base = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val roll = rollMelee(target, attack, damage = 1.25, minFlat = base / 4)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                TargetStats.lowerPercent(target, CombatSkill.Defence, percent = defenceDrain)
            }
            return resume(target)
        }
    }

    /** 25% more accuracy, and a hit takes 35% off the target's Defence. */
    private class Pulverize(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.human_elder_maul_spec,
                fx_spots.spotanim_elder_maul_special,
                targetSpot = fx_spots.spotanim_elder_maul_special_impact,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 1.25)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                TargetStats.lowerPercent(target, CombatSkill.Defence, percent = 35)
            }
            return resume(target)
        }
    }

    /** An extra attack in the same breath: two ordinary rolls, both landing at once. */
    private class QuickSmash(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.slayer_granite_maul_special_attack, fx_spots.sp_attack_maul_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            repeat(2) { hitMelee(target, attack, rollMelee(target, attack)) }
            return resume(target)
        }
    }

    /** 50% more accuracy, and a hit always deals 5 more damage. */
    private class HammerBlow(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(spot = fx_spots.granite_hammer_sa_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 1.5, bonus = 5)
            hitMelee(target, attack, roll)
            return resume(target)
        }
    }

    /** 0.5% more damage for every Prayer point used. */
    private class Penance(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.abyssal_bludgeon_special_attack,
                targetSpot = fx_spots.abyssal_miasma_spotanim_bludgeon,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val missing = (player.statBase(stats.prayer) - player.stat(stats.prayer)).coerceAtLeast(0)
            val roll = rollMelee(target, attack, damage = 1.0 + missing * 0.005)
            hitMelee(target, attack, roll)
            return resume(target)
        }
    }

    /**
     * Double accuracy, a higher max hit, and the damage dealt takes a tenth of itself off the
     * target's Defence, Attack, Magic and Ranged levels in turn. The imbued anchor also quickens
     * the next swing by two ticks.
     */
    private class Sunder(
        manager: SpecialAttackManager,
        private val damage: Double,
        private val quickened: Boolean,
    ) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.brain_player_anchor_special_attack,
                targetSpot = fx_spots.brain_anchor_special_attack_spot,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 2.0, damage = damage)
            hitMelee(target, attack, roll)
            var remaining = roll.damage / 10
            for (skill in DRAIN_ORDER) {
                if (remaining <= 0) {
                    break
                }
                remaining -= TargetStats.lower(target, skill, remaining)
            }
            if (quickened) {
                slowNextAttack(-2)
            }
            return resume(target)
        }

        private companion object {
            val DRAIN_ORDER =
                listOf(
                    CombatSkill.Defence,
                    CombatSkill.Attack,
                    CombatSkill.Magic,
                    CombatSkill.Ranged,
                )
        }
    }

    /**
     * Goes through Protect from Melee and takes Prayer points off a player's target equal to the
     * damage, giving them to the attacker, who can go above their level by that much.
     */
    private class FavourOfTheWarGod(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val roll = rollMelee(target, attack)
            hitMelee(target, attack, roll, ignoreProtection = true)
            if (roll.landed) {
                if (target is Player) {
                    TargetStats.lower(target, CombatSkill.Prayer, roll.damage, minimum = 0)
                }
                val base = player.statBase(stats.prayer)
                val room = (base + roll.damage) - player.stat(stats.prayer)
                if (room > 0) {
                    statAdd(stats.prayer, constant = minOf(roll.damage, room), percent = 0)
                }
            }
            return resume(target)
        }
    }

    /**
     * Hits everything within five tiles, in multi-combat as well as the target, with 20% more
     * accuracy.
     */
    private class ShieldBash(manager: SpecialAttackManager, private val areas: AreaTargets) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.human_dinhs_bulwark_bash)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            hitMelee(target, attack, rollMelee(target, attack, accuracy = 1.2))
            val others = with(areas) { around(target, center = player.coords, radius = RADIUS, limit = LIMIT) }
            for (other in others) {
                hitMelee(other, attack, rollMelee(other, attack, accuracy = 1.2))
            }
            return resume(target)
        }

        private companion object {
            const val RADIUS = 5
            const val LIMIT = 12
        }
    }
}
