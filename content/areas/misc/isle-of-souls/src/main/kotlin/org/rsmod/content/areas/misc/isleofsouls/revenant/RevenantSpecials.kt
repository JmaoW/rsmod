package org.rsmod.content.areas.misc.isleofsouls.revenant

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.player.worn.RevenantEther
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.RangedKit
import org.rsmod.content.other.special.attacks.support.RangedSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.StaffSpecial
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.game.entity.PathingEntity

/**
 * The special attacks of the revenant weapons (OSRS Wiki): Swarm (Webweaver bow), Bear Down (Ursine
 * chainmace) and Condemn (Accursed sceptre). Like any attack with these weapons, a special spends
 * one ether and cannot be made when the weapon has none to spend.
 */
class RevenantSpecials
@Inject
constructor(private val kit: RangedKit, private val dots: SpecialDots) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerRanged(ether_objs.webweaver, Swarm(manager, kit, dots))
        registerMelee(ether_objs.ursine, BearDown(manager, dots))
        val condemn = Condemn(manager)
        registerMagic(ether_objs.accursed, condemn)
        registerMagic(ether_objs.accursed_a, condemn)
    }

    /** Four arrows in quick succession for reduced damage; the first to land poisons. */
    private class Swarm(
        manager: SpecialAttackManager,
        kit: RangedKit,
        private val dots: SpecialDots,
    ) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            if (RevenantEther.usable(player.righthand) <= 0) {
                return refuse("Your bow has no ether to fire with.")
            }
            anim(fx_seqs.human_special01_webweaver)
            spotanim(
                RevenantWeaponSpotanims.arrow_launch_webweaver,
                height = 96,
                slot = constants.spotanim_slot_combat,
            )
            var poisoned = false
            repeat(ARROWS) {
                val proj =
                    manager.spawnProjectile(
                        this,
                        target,
                        RevenantWeaponSpotanims.arrow_travel_webweaver,
                        projanims.arrow,
                    )
                val roll = rollRanged(target, attack, damage = ARROW_DAMAGE)
                manager.giveCombatXp(this, target, attack, roll.damage)
                manager.queueRangedHit(
                    this,
                    target,
                    null,
                    roll.damage,
                    proj.clientCycles,
                    proj.serverCycles + it,
                )
                if (roll.landed && !poisoned) {
                    poisoned = dots.poison(target, player, POISON_DAMAGE)
                }
            }
            EtherCharges.spendWeaponEther(player)
            return resume(target)
        }

        private companion object {
            const val ARROWS = 4
            const val ARROW_DAMAGE = 0.5
            const val POISON_DAMAGE = 4
        }
    }

    /**
     * Double accuracy. A hit also bleeds the target for half its damage again over the next few
     * ticks.
     */
    private class BearDown(manager: SpecialAttackManager, private val dots: SpecialDots) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.human_special_ursine, targetSpot = fx_spots.fx_ursine01_special)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (RevenantEther.usable(player.righthand) <= 0) {
                return refuse("Your weapon has no ether to fight with.")
            }
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 2.0)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                dots.bleed(
                    target,
                    player,
                    damage = roll.damage / 2,
                    chunk = BLEED_CHUNK,
                    interval = 1,
                )
            }
            EtherCharges.spendWeaponEther(player)
            return resume(target)
        }

        private companion object {
            const val BLEED_CHUNK = 4
        }
    }

    /**
     * A spell with 50% more accuracy and damage. It lowers the target's Magic and Defence levels,
     * by 15% of their base levels at the most.
     */
    private class Condemn(manager: SpecialAttackManager) : StaffSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.human_special_accursed,
                targetSpot = RevenantWeaponSpotanims.sceptre_impact,
            )

        override suspend fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (RevenantEther.usable(player.righthand) <= 0) {
                return refuse("Your sceptre has no ether to cast with.")
            }
            fx.playAll(this, target)
            val base = maxOf(0, player.magicLvl / 3 - ACCURSED_OFFSET)
            val roll = rollStaff(target, attack, base, accuracy = 1.5, damage = 1.5)
            hitStaff(target, attack, roll.damage)
            if (roll.accurate) {
                for (skill in listOf(CombatSkill.Magic, CombatSkill.Defence)) {
                    val floor = TargetStats.baseLevel(target, skill) * 85 / 100
                    val excess = TargetStats.level(target, skill) - floor
                    TargetStats.lower(target, skill, excess)
                }
            }
            EtherCharges.spendWeaponEther(player)
            return resume(target)
        }

        private companion object {
            /** The accursed sceptre's max hit is a third of the Magic level less 6. */
            const val ACCURSED_OFFSET = 6
        }
    }
}
