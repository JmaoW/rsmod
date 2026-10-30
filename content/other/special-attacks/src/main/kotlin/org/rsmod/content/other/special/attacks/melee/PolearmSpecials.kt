package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.combat.commons.CombatAttack
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
import org.rsmod.content.other.special.attacks.support.DamageGuards
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.TargetStatus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The spears, halberds and whips: Sweep (dragon halberd), Shove (the dragon and Zamorakian
 * spears), Unleash (dragon hasta), Spear Wall (Vesta's spear), Energy Drain (abyssal whip) and
 * Binding Tentacle (abyssal tentacle).
 */
class PolearmSpecials
@Inject
constructor(
    private val areas: AreaTargets,
    private val status: TargetStatus,
    private val guards: DamageGuards,
    private val dots: SpecialDots,
    private val collision: CollisionFlagMap,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val sweep = Sweep(manager, areas)
        registerMelee(sobjs.dragon_halberd, sweep)
        registerMelee(sobjs.crystal_halberd, sweep)
        registerMelee(sobjs.crystal_halberd_2500, sweep)
        registerMelee(sobjs.bh_dragon_halberd_corrupted, sweep)

        val shove = Shove(manager, status, collision)
        registerMelee(sobjs.dragon_spear, shove)
        registerMelee(sobjs.dragon_spear_p, shove)
        registerMelee(sobjs.dragon_spear_p_plus, shove)
        registerMelee(sobjs.dragon_spear_p_plus_plus, shove)
        registerMelee(sobjs.tbwt_dragon_spear_kp, shove)
        registerMelee(sobjs.zamorak_spear, shove)
        registerMelee(sobjs.zamorak_hasta, shove)
        registerMelee(sobjs.bh_dragon_spear_corrupted, shove)
        registerMelee(sobjs.bh_dragon_spear_p_corrupted, shove)
        registerMelee(sobjs.bh_dragon_spear_p_plus_corrupted, shove)
        registerMelee(sobjs.bh_dragon_spear_p_plus_plus_corrupted, shove)

        val unleash = Unleash(manager)
        registerMelee(sobjs.brut_dragon_spear, unleash)
        registerMelee(sobjs.brut_dragon_spear_p, unleash)
        registerMelee(sobjs.brut_dragon_spear_p_plus, unleash)
        registerMelee(sobjs.brut_dragon_spear_p_plus_plus, unleash)
        registerMelee(sobjs.brut_dragon_spear_kp, unleash)

        registerMelee(sobjs.vestas_spear, SpearWall(manager, areas, guards, twoHits = false))
        registerMelee(sobjs.vestas_spear_bh, SpearWall(manager, areas, guards, twoHits = true))

        val whip = EnergyDrain(manager)
        registerMelee(sobjs.abyssal_whip, whip)
        registerMelee(sobjs.abyssal_whip_lava, whip)
        registerMelee(sobjs.abyssal_whip_ice, whip)
        registerMelee(sobjs.br_abyssal_whip, whip)
        registerMelee(sobjs.league_3_whip, whip)

        val tentacle = BindingTentacle(manager, status, dots)
        registerMelee(sobjs.abyssal_tentacle, tentacle)
        registerMelee(sobjs.league_3_whip_tentacle, tentacle)
    }

    /**
     * 10% more damage. A small target (a player, or a npc one tile wide) may have its neighbours
     * hit as well in multi-combat; a large npc may be hit a second time at 25% less accuracy.
     */
    private class Sweep(manager: SpecialAttackManager, private val areas: AreaTargets) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.dragon_halberd_special_attack, fx_spots.lunar_enchant_dragonstone)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            hitMelee(target, attack, rollMelee(target, attack, damage = 1.1))
            val large = target is Npc && target.visType.size > 1
            if (large) {
                hitMelee(target, attack, rollMelee(target, attack, accuracy = 0.75, damage = 1.1))
                return resume(target)
            }
            val neighbours = with(areas) { around(target, radius = 1, limit = MAX_NEIGHBOURS) }
            for (other in neighbours) {
                hitMelee(other, attack, rollMelee(other, attack, damage = 1.1))
            }
            return resume(target)
        }

        private companion object {
            const val MAX_NEIGHBOURS = 9
        }
    }

    /**
     * Knocks the target back a tile and stuns it for three seconds, and deals no damage. A npc
     * that is more than one tile wide is too large to push.
     */
    private class Shove(
        manager: SpecialAttackManager,
        private val status: TargetStatus,
        private val collision: CollisionFlagMap,
    ) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.shove, fx_spots.druidicspirit_bloom_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (target is Npc && target.visType.size > 1) {
                return refuse("That creature is too large to knock back!")
            }
            fx.play(this)
            status.stun(target, STUN_TICKS)
            target.anim(fx_seqs.human_unarmedblock)
            push(target)
            slowNextAttack(1)
            return resume(target)
        }

        /** One tile directly away from the attacker, if nothing is in the way. */
        private fun ProtectedAccess.push(target: PathingEntity) {
            val dx = (target.coords.x - coords.x).sign
            val dz = (target.coords.z - coords.z).sign
            if (dx == 0 && dz == 0) {
                return
            }
            val dest = target.coords.translate(dx, dz)
            if (mapBlocked(dest) || !lineOfWalk(target.coords, dest)) {
                return
            }
            PathingEntityCommon.telejump(target, collision, dest)
        }

        private companion object {
            /** Three seconds. */
            const val STUN_TICKS = 5
        }
    }

    /**
     * Spends the whole special attack bar, not just the five percent it needs: 5% more accuracy
     * and 2.5% more strength for every five percent used.
     */
    private class Unleash(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val energy = manager.specialEnergy(this)
            val steps = (energy / STEP).coerceAtLeast(1)
            val roll =
                rollMelee(
                    target,
                    attack,
                    accuracy = 1.0 + 0.05 * steps,
                    damage = 1.0 + 0.025 * steps,
                )
            hitMelee(target, attack, roll, ignoreProtection = false)
            // The engine takes the five percent the weapon needs once this returns.
            val extra = energy - STEP
            if (extra > 0) {
                manager.takeSpecialEnergy(this, extra)
            }
            return resume(target)
        }

        private companion object {
            /** Five percent of the bar. */
            const val STEP = 50
        }
    }

    /**
     * Half the damage, but it reaches up to sixteen targets around the player, and the player
     * cannot be hit by melee for the next five seconds. The Bounty Hunter spear hits twice, the
     * second time for between half and three quarters of the first.
     */
    private class SpearWall(
        manager: SpecialAttackManager,
        private val areas: AreaTargets,
        private val guards: DamageGuards,
        private val twoHits: Boolean,
    ) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.human_spear_wall_spec, fx_spots.vestas_spear_sa_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val everyone = ArrayList<PathingEntity>()
            everyone += target
            everyone += with(areas) { around(target, center = player.coords, radius = 1, limit = LIMIT - 1) }
            for (victim in everyone) {
                val roll = rollMelee(victim, attack, damage = 0.5)
                hitMelee(victim, attack, roll)
                if (twoHits && victim === target) {
                    val second = (roll.damage * random.of(50..75)) / 100
                    hitMelee(victim, attack, second)
                }
            }
            guards.immuneToMelee(player, IMMUNITY_TICKS)
            return resume(target)
        }

        private companion object {
            const val LIMIT = 16

            /** Five seconds. */
            const val IMMUNITY_TICKS = 8
        }
    }

    /**
     * 25% more accuracy. A hit on a player takes a tenth of their run energy and gives it to the
     * attacker; a npc has none to take.
     */
    private class EnergyDrain(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.slayer_abyssal_whip_attack, targetSpot = fx_spots.sp_attack_abyssal_whip)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 1.25)
            hitMelee(target, attack, roll)
            if (roll.accurate) {
                fx.playOn(target)
                if (roll.landed && target is Player) {
                    siphon(target)
                }
            }
            return resume(target)
        }

        private fun ProtectedAccess.siphon(target: Player) {
            val amount = target.runEnergy / DIVISOR
            if (amount <= 0) {
                return
            }
            target.runEnergy -= amount
            UpdateRun.energy(target, target.runEnergy)
            target.mes("You feel drained!")
            player.runEnergy = minOf(MAX_RUN_ENERGY, player.runEnergy + amount)
            UpdateRun.energy(player, player.runEnergy)
        }

        private companion object {
            const val DIVISOR = 10
            const val MAX_RUN_ENERGY = 10_000
        }
    }

    /** 25% more accuracy; binds the target for five seconds and may poison it. */
    private class BindingTentacle(
        manager: SpecialAttackManager,
        private val status: TargetStatus,
        private val dots: SpecialDots,
    ) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.slayer_abyssal_whip_attack, targetSpot = fx_spots.bind_impact)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 1.25)
            hitMelee(target, attack, roll)
            if (roll.accurate) {
                if (status.bind(target, BIND_TICKS)) {
                    fx.playOn(target)
                }
                if (random.randomBoolean(POISON_CHANCE)) {
                    dots.poison(target, player, POISON_DAMAGE)
                }
            }
            return resume(target)
        }

        private companion object {
            /** Five seconds. */
            const val BIND_TICKS = 8
            const val POISON_CHANCE = 2
            const val POISON_DAMAGE = 4
        }
    }
}
