package org.rsmod.content.other.special.attacks.ranged

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varobjs
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.api.player.righthand
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.DotKind
import org.rsmod.content.other.special.attacks.support.MoonSet
import org.rsmod.content.other.special.attacks.support.RangedKit
import org.rsmod.content.other.special.attacks.support.RangedSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.utils.bits.getBits
import org.rsmod.utils.bits.withBits

/**
 * The special attacks of the rarer ranged weapons: Division (Tonalztics of Ralos), Eclipse
 * (eclipse atlatl), Snipe (Dorgeshuun crossbow) and Toxic Siphon (toxic blowpipe).
 */
class RareRangedSpecials
@Inject
constructor(private val kit: RangedKit, private val dots: SpecialDots) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val division = Division(manager, kit)
        registerRanged(sobjs.tonalztics_of_ralos_charged, division)
        registerRanged(sobjs.tonalztics_of_ralos_uncharged, division)

        val eclipse = Eclipse(manager, kit, dots)
        registerRanged(sobjs.eclipse_atlatl, eclipse)
        registerRanged(sobjs.br_eclipse_atlatl, eclipse)

        registerRanged(sobjs.dttd_bone_crossbow, Snipe(manager, kit))

        val siphon = ToxicSiphon(manager, kit)
        registerRanged(sobjs.toxic_blowpipe_loaded, siphon)
        registerRanged(sobjs.toxic_blowpipe_loaded_ornament, siphon)
    }

    /**
     * Two shots of a piercing blade. Each that lands takes a tenth of the target's Magic level off
     * its Defence. The blade needs no ammunition (its charges are the weapon's own).
     */
    private class Division(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            anim(fx_seqs.human_glaive_ralos01_charged_special)
            spotanim(fx_spots.vfx_glaive_charged_special, height = 96, slot = constants.spotanim_slot_combat)
            repeat(2) { index ->
                val proj =
                    manager.spawnProjectile(
                        this,
                        target,
                        fx_spots.projanim_glaive_01_special,
                        if (index == 0) projanims.doublearrow_one else projanims.doublearrow_two,
                    )
                val roll = rollRanged(target, attack)
                manager.giveCombatXp(this, target, attack, roll.damage)
                manager.queueRangedHit(
                    this,
                    target,
                    null,
                    roll.damage,
                    proj.clientCycles,
                    proj.serverCycles,
                )
                if (roll.landed) {
                    val magic = TargetStats.level(target, CombatSkill.Magic)
                    TargetStats.lower(target, CombatSkill.Defence, magic / 10)
                }
            }
            return resume(target)
        }
    }

    /**
     * Needs the whole Eclipse set. 50% more accuracy; the burn on the target is spent and added to
     * the max hit.
     */
    private class Eclipse(manager: SpecialAttackManager, kit: RangedKit, private val dots: SpecialDots) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            if (!MoonSet.Eclipse.isWornBy(player)) {
                return refuse("This special attack requires the full Eclipse set to be worn.")
            }
            val shot = prepare(attack) ?: return false
            anim(fx_seqs.human_special_atlatl_01)
            spotanim(fx_spots.special_atlatl_cast_spotanim, height = 96, slot = constants.spotanim_slot_combat)
            val proj = fire(shot, target, fx_spots.vfx_atlatl_projectile_01, projanims.thrown)
            val burn = dots.consume(target, DotKind.Burn)
            val roll = rollRanged(target, attack, accuracy = 1.5)
            val damage = if (roll.accurate) random.of(0..(roll.maxHit + burn)) else 0
            hitRanged(shot, target, attack, damage, proj)
            if (roll.accurate) {
                target.spotanim(fx_spots.special_atlatl_impact_spotanim, height = 96, delay = proj.clientCycles)
            }
            return resume(target)
        }
    }

    /**
     * Much more accuracy on a target that is not fighting anyone; the damage dealt takes that much
     * off its Defence.
     */
    private class Snipe(manager: SpecialAttackManager, kit: RangedKit) : RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val shot = prepare(attack) ?: return false
            manager.playWeaponFx(this, attack)
            val proj = fire(shot, target)
            val unsuspecting =
                when (target) {
                    is Npc -> !target.isInCombat()
                    is Player -> !target.isInCombat()
                }
            val roll = rollRanged(target, attack, accuracy = if (unsuspecting) 3.0 else 1.0)
            hitRanged(shot, target, attack, roll.damage, proj)
            if (roll.landed) {
                TargetStats.lower(target, CombatSkill.Defence, roll.damage)
            }
            return resume(target)
        }
    }

    /**
     * 100% more accuracy and 50% more damage from the blowpipe, which gives the shooter back half
     * of what it deals. It uses a dart and a scale from what the blowpipe holds.
     */
    private class ToxicSiphon(manager: SpecialAttackManager, kit: RangedKit) :
        RangedSpecial(manager, kit) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Ranged,
        ): Boolean {
            val blowpipe = player.righthand ?: return false
            val darts = blowpipe.vars.getBits(varobjs.snakeboss_blowpipe_dartcount.bits)
            val scales = blowpipe.vars.getBits(varobjs.snakeboss_blowpipe_flakes.bits)
            if (darts <= 0) {
                return refuse("Your blowpipe has no darts in it.")
            }
            if (scales <= 0) {
                return refuse("Your blowpipe has no Zulrah's scales in it.")
            }
            anim(fx_seqs.toxic_blowpipe_special_updated)
            spotanim(fx_spots.toxic_blowpipe_specialattack, height = 96, slot = constants.spotanim_slot_combat)
            val proj =
                manager.spawnProjectile(this, target, fx_spots.toxic_blowpipe_specialattack, projanims.thrown)
            val roll = rollRanged(target, attack, accuracy = 2.0, damage = 1.5)
            manager.giveCombatXp(this, target, attack, roll.damage)
            manager.queueRangedHit(this, target, null, roll.damage, proj.clientCycles, proj.serverCycles)
            spend(blowpipe, darts, scales)
            if (roll.damage > 0) {
                statHeal(stats.hitpoints, constant = roll.damage / 2, percent = 0)
            }
            return resume(target)
        }

        private fun ProtectedAccess.spend(blowpipe: InvObj, darts: Int, scales: Int) {
            var vars = blowpipe.vars.withBits(varobjs.snakeboss_blowpipe_dartcount.bits, darts - 1)
            vars = vars.withBits(varobjs.snakeboss_blowpipe_flakes.bits, scales - 1)
            player.worn[Wearpos.RightHand.slot] = blowpipe.copy(vars = vars)
        }
    }
}
