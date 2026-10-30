package org.rsmod.content.other.special.attacks.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.RangedKit
import org.rsmod.content.other.special.attacks.support.RangedSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.content.other.special.attacks.support.TargetStatus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity

/**
 * The bows: Snapshot (magic shortbow), Powershot (magic longbow), Soulshot (the Daganoth cave
 * shortbow) and Scorching shackles (the scorching bow).
 */
class BowSpecials
@Inject
constructor(
    private val kit: RangedKit,
    private val status: TargetStatus,
    private val dots: SpecialDots,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val snapshot = Snapshot(manager, kit)
        registerRanged(sobjs.magic_shortbow, snapshot)
        registerRanged(sobjs.magic_shortbow_i, snapshot)
        registerRanged(sobjs.br_magic_bow, snapshot)

        val powershot = Powershot(manager, kit)
        registerRanged(sobjs.magic_longbow, powershot)
        registerRanged(sobjs.trail_composite_bow_magic, powershot)

        registerRanged(sobjs.daganoth_cave_magic_shortbow, Soulshot(manager, kit))
        registerRanged(sobjs.scorching_bow, ScorchingShackles(manager, kit, status, dots))
    }

    /** Two arrows in quick succession, each with reduced accuracy. */
    private class Snapshot(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack, count = 2) ?: return false
            anim(fx_seqs.snapshot)
            spotanim(fx_spots.sp_attack_glow_arrow_launch, height = 96, slot = constants.spotanim_slot_combat)
            val first = fire(shot, target, fx_spots.sp_attack_glow_arrow_travel, projanims.doublearrow_one)
            val second = fire(shot, target, fx_spots.sp_attack_glow_arrow_travel, projanims.doublearrow_two)
            hitRanged(shot, target, attack, rollRanged(target, attack, accuracy = 0.7).damage, first)
            hitRanged(
                shot,
                target,
                attack,
                rollRanged(target, attack, accuracy = 0.7).damage,
                second,
                silent = true,
            )
            return resume(target)
        }
    }

    /** An arrow that cannot miss. */
    private class Powershot(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            manager.playWeaponFx(this, attack)
            spotanim(shot.launch, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target)
            val roll = rollRanged(target, attack, guaranteed = true)
            hitRanged(shot, target, attack, roll.damage, proj)
            return resume(target)
        }
    }

    /** An arrow that cannot miss, and takes the damage off the target's Magic level. */
    private class Soulshot(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            manager.playWeaponFx(this, attack)
            spotanim(shot.launch, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target)
            val roll = rollRanged(target, attack, guaranteed = true)
            hitRanged(shot, target, attack, roll.damage, proj)
            TargetStats.lower(target, CombatSkill.Magic, roll.damage)
            return resume(target)
        }
    }

    /**
     * 30% more accuracy. A hit on a demon binds it for twelve seconds and burns it; anything else
     * is just hit.
     */
    private class ScorchingShackles(
        manager: SpecialAttackManager,
        kit: RangedKit,
        private val status: TargetStatus,
        private val dots: SpecialDots,
    ) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.human_weapon_bow_scorched_01_spec)
            spotanim(fx_spots.vfx_human_scorching_bow_special_attack_01, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.vfx_scorching_bow_projectile)
            val roll = rollRanged(target, attack, accuracy = 1.3)
            hitRanged(shot, target, attack, roll.damage, proj)
            val demon = target is Npc && target.visType.param(params.demon) != 0
            if (roll.accurate && demon) {
                if (status.bind(target, BIND_TICKS)) {
                    target.spotanim(fx_spots.vfx_scorching_bow_end_spotanim, height = 96)
                }
                dots.burn(target, player, damage = BURN_DAMAGE, interval = BURN_INTERVAL)
            }
            return resume(target)
        }

        private companion object {
            /** Twelve seconds. */
            const val BIND_TICKS = 20
            const val BURN_DAMAGE = 5
            const val BURN_INTERVAL = 4
        }
    }
}
