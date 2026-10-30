package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
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
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity

/** Powerstab (dragon 2h sword) and Weaken (Darklight, Arclight and Emberlight). */
class MiscMeleeSpecials @Inject constructor(private val areas: AreaTargets) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val powerstab = Powerstab(manager, areas)
        registerMelee(sobjs.dragon_2h_sword, powerstab)
        registerMelee(sobjs.br_dragon_2h, powerstab)
        registerMelee(sobjs.bh_dragon_2h_sword_corrupted, powerstab)

        val weaken = Weaken(manager, demonMultiplier = 2)
        registerMelee(sobjs.darklight, weaken)
        registerMelee(sobjs.arclight, weaken)
        registerMelee(sobjs.arclight_inactive, weaken)
        registerMelee(sobjs.emberlight, Weaken(manager, demonMultiplier = 3))
    }

    /** Hits the target and up to fourteen other enemies around the player. */
    private class Powerstab(manager: SpecialAttackManager, private val areas: AreaTargets) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.dragon_two_handed_sword, fx_spots.dragon_two_handed_sword_blast)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            hitMelee(target, attack, rollMelee(target, attack))
            val others = with(areas) { around(target, center = player.coords, radius = 1, limit = LIMIT - 1) }
            for (other in others) {
                hitMelee(other, attack, rollMelee(other, attack))
            }
            return resume(target)
        }

        private companion object {
            const val LIMIT = 15
        }
    }

    /**
     * A hit drains the target's Attack, Strength and Defence by 5% of their level plus 1, two
     * times that (three for Emberlight) against demons.
     */
    private class Weaken(manager: SpecialAttackManager, private val demonMultiplier: Int) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.dark_spec_player, fx_spots.dark_spec_spot)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                val demon = target is Npc && target.visType.param(params.demon) != 0
                val multiplier = if (demon) demonMultiplier else 1
                for (skill in DRAINED) {
                    TargetStats.lowerBasePercent(
                        target,
                        skill,
                        percent = 5 * multiplier,
                        flat = multiplier,
                    )
                }
            }
            return resume(target)
        }

        private companion object {
            val DRAINED = listOf(CombatSkill.Attack, CombatSkill.Strength, CombatSkill.Defence)
        }
    }
}
