package org.rsmod.content.other.special.attacks.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.BoltBoost
import org.rsmod.api.combat.manager.BoltEffectRegistry
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
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
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity

/**
 * The crossbows and ballistas: Armadyl Eye (Armadyl crossbow), Evoke (Zaryte crossbow), Annihilate
 * (dragon crossbow) and Concentrated Shot (both ballistas).
 *
 * Armadyl Eye doubles the chance of the effect of an enchanted bolt and Evoke makes it work if the
 * shot hits (see `EnchantedBolts`); both shoot with double accuracy.
 */
class CrossbowSpecials
@Inject
constructor(
    private val kit: RangedKit,
    private val areas: AreaTargets,
    private val bolts: BoltEffectRegistry,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val armadylEye =
            DoubleAccuracy(
                manager,
                kit,
                bolts,
                BoltBoost.Doubled,
                fx_seqs.xbows_human_fire_and_reload,
                fx_spots.acb_specialattack,
            )
        registerRanged(sobjs.acb, armadylEye)
        registerRanged(sobjs.br_acb, armadylEye)

        val evoke =
            DoubleAccuracy(
                manager,
                kit,
                bolts,
                BoltBoost.Guaranteed,
                fx_seqs.zcb_attack,
                fx_spots.zcb_specialattack,
            )
        registerRanged(sobjs.zaryte_xbow, evoke)
        registerRanged(sobjs.br_zaryte_xbow, evoke)

        val annihilate = Annihilate(manager, kit, areas)
        registerRanged(sobjs.xbows_crossbow_dragon, annihilate)
        registerRanged(sobjs.bh_xbows_crossbow_dragon_corrupted, annihilate)

        val light = ConcentratedShot(manager, kit, heavy = false)
        registerRanged(sobjs.light_ballista, light)
        registerRanged(sobjs.br_light_ballista, light)
        val heavy = ConcentratedShot(manager, kit, heavy = true)
        registerRanged(sobjs.heavy_ballista, heavy)
        registerRanged(sobjs.heavy_ballista_ornament, heavy)
        registerRanged(sobjs.br_heavy_ballista, heavy)
    }

    /** A bolt shot with double the accuracy, and a [boost] for the effect of an enchanted bolt. */
    private class DoubleAccuracy(
        manager: SpecialAttackManager,
        kit: RangedKit,
        private val bolts: BoltEffectRegistry,
        private val boost: BoltBoost,
        private val seq: org.rsmod.game.type.seq.SeqType,
        private val travel: org.rsmod.game.type.spot.SpotanimType,
    ) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            anim(seq)
            val proj = fire(shot, target, travel, projanims.bolt)
            // The damage roll of the manager is the one that applies the effect of enchanted bolts.
            bolts.boost(player, boost)
            val damage = manager.rollRangedDamage(this, target, attack, accuracyMultiplier = 2.0)
            hitRanged(shot, target, attack, damage, proj)
            return resume(target)
        }
    }

    /**
     * 20% more damage. In multi-combat the targets next to the target are hit as well, for 20% less
     * damage than the target takes.
     */
    private class Annihilate(manager: SpecialAttackManager, kit: RangedKit, private val areas: AreaTargets) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            manager.playWeaponFx(this, attack)
            val proj = fire(shot, target, fx_spots.dragon_crossbowbolt_travel, projanims.bolt)
            val roll = rollRanged(target, attack, damage = 1.2)
            hitRanged(shot, target, attack, roll.damage, proj)
            if (roll.accurate) {
                target.spotanim(fx_spots.dragon_crossbowbolt_travel, height = 96, delay = proj.clientCycles)
            }
            val others = with(areas) { around(target, radius = 1, limit = MAX_OTHERS) }
            for (other in others) {
                val bolt = fire(shot, other, fx_spots.dragon_crossbowbolt_travel, projanims.bolt)
                val damage = rollRanged(other, attack, damage = 1.2 * 0.8).damage
                // The bolt that flew was the one the target's shot used up.
                manager.giveCombatXp(this, other, attack, damage)
                manager.queueRangedHit(this, other, shot.hitAmmo, damage, bolt.clientCycles, bolt.serverCycles)
            }
            return resume(target)
        }

        private companion object {
            const val MAX_OTHERS = 8
        }
    }

    /**
     * 25% more accuracy and damage from a ballista, which takes two and four-tenths seconds longer
     * to fire than usual.
     */
    private class ConcentratedShot(manager: SpecialAttackManager, kit: RangedKit, private val heavy: Boolean) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            val pvn = target is Npc
            anim(
                when {
                    heavy && pvn -> fx_seqs.ballista02_special_attack_pvn
                    heavy -> fx_seqs.ballista02_special_attack
                    pvn -> fx_seqs.ballista_special_attack_pvn
                    else -> fx_seqs.ballista_special_attack
                }
            )
            spotanim(fx_spots.ballista_special, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target)
            val roll = rollRanged(target, attack, accuracy = 1.25, damage = 1.25)
            hitRanged(shot, target, attack, roll.damage, proj)
            slowNextAttack(EXTRA_DELAY)
            return resume(target)
        }

        private companion object {
            /** 2.4 seconds. */
            const val EXTRA_DELAY = 4
        }
    }
}
