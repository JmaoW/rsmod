package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.AttackBuff
import org.rsmod.api.combat.manager.AttackBuffs
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.AreaTargets
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.DamageGuards
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.MoonSet
import org.rsmod.content.other.special.attacks.support.SoulStacks
import org.rsmod.content.other.special.attacks.support.SpecialDots
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.SpecialPlayerEvents
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.content.other.special.attacks.support.TargetStatus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.obj.ObjType

/**
 * The special attacks of the rarer weapons: the Keris partisans, the brine sabre, the moon sets'
 * spear and macuahuitl, the thunder khopesh, the noxious halberd, the soulflame horn, the
 * Soulreaper axe, the Sunlight spear, the bone dagger and the vampyrebane flails.
 */
class ExoticSpecials
@Inject
constructor(
    private val areas: AreaTargets,
    private val status: TargetStatus,
    private val guards: DamageGuards,
    private val dots: SpecialDots,
    private val buffs: AttackBuffs,
    private val stacks: SoulStacks,
    private val bonuses: WornBonuses,
    private val worldQueues: WorldQueueList,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        registerMelee(sobjs.keris_partisan_corruption, WrathOfAmascut(manager, guards))
        registerInstant(sobjs.keris_partisan_sun) { tumekensLight() }

        registerMelee(sobjs.olaf2_brine_sabre, Liquify(manager))
        registerMelee(sobjs.frostmoon_spear, BreakShackles(manager, status))
        registerMelee(sobjs.br_frostmoon_spear, BreakShackles(manager, status))
        registerMelee(sobjs.dual_macuahuitl, BloodInfusion(manager))
        registerMelee(sobjs.br_dual_macuahuitl, BloodInfusion(manager))
        registerMelee(sobjs.thunder_khopesh, LingeringLightning(manager, areas, worldQueues))

        registerInstant(sobjs.noxious_halberd) { virulence() }
        registerInstant(sobjs.soulflame_horn) { entice(manager) }

        registerMelee(sobjs.soulreaper, Behead(manager, stacks))
        registerMelee(sobjs.weapon_of_sol, SolSlam(manager, areas, bonuses))

        val backstab = Backstab(manager)
        registerMelee(sobjs.dttd_bone_dagger, backstab)
        registerMelee(sobjs.dttd_bone_dagger_p, backstab)
        registerMelee(sobjs.dttd_bone_dagger_p_plus, backstab)
        registerMelee(sobjs.dttd_bone_dagger_p_plus_plus, backstab)

        val retainer = Retainer(manager, status)
        for (rod in RETAINER_WEAPONS) {
            registerMelee(rod, retainer)
        }
    }

    /**
     * 100% more accuracy and 25% more damage from a swing that takes half the time, and a hit
     * leaves the target taking 25% more damage for six seconds.
     */
    private class WrathOfAmascut(manager: SpecialAttackManager, private val guards: DamageGuards) :
        MeleeSpecial(manager) {
        private val fx =
            SpecialFx(
                fx_seqs.toa_keris_partisan_special01,
                targetSpot = fx_spots.keris_partisan_special01,
            )

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.playAll(this, target)
            val roll = rollMelee(target, attack, accuracy = 2.0, damage = 1.25)
            hitMelee(target, attack, roll)
            if (roll.accurate) {
                guards.makeVulnerable(target, ticks = VULNERABLE_TICKS, percent = 25)
            }
            slowNextAttack(-((actionDelay - mapClock) / 2))
            return resume(target)
        }

        private companion object {
            /** Six seconds. */
            const val VULNERABLE_TICKS = 10
        }
    }

    /**
     * Costs 50 Prayer points. Raises Hitpoints to 20% above the maximum, cures poison and venom,
     * restores every lowered level except Prayer and restores all the run energy.
     */
    private fun ProtectedAccess.tumekensLight(): Boolean {
        if (player.stat(stats.prayer) < TUMEKENS_LIGHT_PRAYER) {
            mes("You need 50 Prayer points to use Tumeken's Light.")
            return false
        }
        anim(fx_seqs.toa_keris_partisan_special01)
        spotanim(fx_spots.keris_partisan_special01, height = 96, slot = constants.spotanim_slot_combat)
        statSub(stats.prayer, constant = TUMEKENS_LIGHT_PRAYER, percent = 0)

        publish(SpecialPlayerEvents.CureSickness(player))
        for (stat in RESTORED_STATS) {
            statRestore(stat)
        }
        val maxHitpoints = player.statBase(stats.hitpoints)
        val overheal = maxHitpoints + maxHitpoints / 5
        val missing = overheal - player.stat(stats.hitpoints)
        if (missing > 0) {
            statAdd(stats.hitpoints, constant = missing, percent = 0)
        }
        player.runEnergy = MAX_RUN_ENERGY
        UpdateRun.energy(player, player.runEnergy)
        return true
    }

    /** Double the chance to hit; the damage dealt raises Strength, Attack and Defence by a quarter. */
    private class Liquify(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.olaf2_brine_sabre_special, fx_spots.olaf2_brine_sabre_special_spot)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 2.0)
            hitMelee(target, attack, roll)
            val boost = roll.damage / 4
            if (boost > 0) {
                for (stat in listOf(stats.strength, stats.attack, stats.defence)) {
                    statBoost(stat, constant = boost, percent = 0)
                }
            }
            return resume(target)
        }
    }

    /**
     * Needs the whole Frostmoon (blue moon) set. The target's binds make the hit more accurate and
     * harder, up to half again as much with the longest, and the hit breaks them.
     */
    private class BreakShackles(manager: SpecialAttackManager, private val status: TargetStatus) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.ice_shatter_special_attack, fx_spots.special_spell_spear_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (!MoonSet.Blue.isWornBy(player)) {
                return refuse("This special attack requires the full Frostmoon set to be worn.")
            }
            fx.play(this)
            val remaining = (target.frozenUntil - mapClock).coerceAtLeast(0)
            val bonus = 1.0 + minOf(remaining, MAX_BIND) / (MAX_BIND * 2.0)
            val roll = rollMelee(target, attack, accuracy = bonus, damage = bonus)
            hitMelee(target, attack, roll)
            if (remaining > 0 && roll.accurate) {
                status.release(target)
            }
            return resume(target)
        }

        private companion object {
            /** The longest bind that counts, in ticks (a minute: a Barrage freeze is 32). */
            const val MAX_BIND = 32
        }
    }

    /**
     * Needs the whole Bloodrager (blood moon) set. Costs a quarter of the current Hitpoints and
     * hits twice with 25% more accuracy and damage.
     */
    private class BloodInfusion(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        private val fx = SpecialFx(spot = fx_spots.special_dual_macuahuitl_spotanim)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            if (!MoonSet.Blood.isWornBy(player)) {
                return refuse("This special attack requires the full Bloodrager set to be worn.")
            }
            manager.playWeaponFx(this, attack)
            fx.play(this)
            val cost = player.stat(stats.hitpoints) / 4
            if (cost > 0) {
                statSub(stats.hitpoints, constant = cost, percent = 0)
            }
            repeat(2) { index ->
                val roll = rollMelee(target, attack, accuracy = 1.25, damage = 1.25)
                hitMelee(target, attack, roll, delay = 1 + index)
            }
            return resume(target)
        }
    }

    /**
     * Hits the target twice. If either hit lands, lightning strikes where the target stands and
     * hurts the targets around it.
     */
    private class LingeringLightning(
        manager: SpecialAttackManager,
        private val areas: AreaTargets,
        private val worldQueues: WorldQueueList,
    ) : MeleeSpecial(manager) {
        private val fx =
            SpecialFx(fx_seqs.human_special_khopesh, targetSpot = fx_spots.fx_khopesh_lightning_special)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            fx.play(this)
            var struck = false
            repeat(2) { index ->
                val roll = rollMelee(target, attack)
                hitMelee(target, attack, roll, delay = 1 + index)
                struck = struck || roll.accurate
            }
            if (struck) {
                lightning(target, attack)
            }
            return resume(target)
        }

        private fun ProtectedAccess.lightning(target: PathingEntity, attack: CombatAttack.Melee) {
            val strike = target.coords
            target.spotanim(fx_spots.fx_khopesh_lightning_special_extra, height = 0)
            val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, 1.0)
            val victims =
                with(areas) { around(target, center = strike, radius = 1, limit = MAX_VICTIMS) }
            for (victim in victims) {
                val damage = random.of(0..max(0, maxHit / 2))
                manager.giveCombatXp(this, victim, attack, damage)
                manager.queueTypelessHit(this, victim, damage, delay = 2)
            }
        }

        private companion object {
            const val MAX_VICTIMS = 8
        }
    }

    /**
     * Burns up the poison or venom the player has to boost their next melee attack by the damage
     * its next hit would have dealt.
     */
    private fun ProtectedAccess.virulence(): Boolean {
        val event = SpecialPlayerEvents.CureSickness(player)
        publish(event)
        val damage = event.damage
        if (damage <= 0) {
            mes("You need to be poisoned or envenomed to use Virulence.")
            return false
        }
        spotanim(fx_spots.vfx_noxious_halberd_spec, height = 96, slot = constants.spotanim_slot_combat)
        buffs.give(player, AttackBuff(flatDamage = damage, until = mapClock + VIRULENCE_TICKS))
        return true
    }

    /**
     * Gives the player and the allies close to them 100% more accuracy on their next melee hit, for
     * ten percent of the special attack bar more for each ally.
     */
    private fun ProtectedAccess.entice(manager: SpecialAttackManager): Boolean {
        anim(fx_seqs.soulflame_horn_blow_01)
        spotanim(fx_spots.vfx_soulflame_horn_spotanim01, height = 96, slot = constants.spotanim_slot_combat)
        val buff = AttackBuff(accuracy = 2.0, until = mapClock + ENTICE_TICKS)
        buffs.give(player, buff)
        val spare = manager.specialEnergy(this) - ENTICE_BASE_COST
        var paid = 0
        for (ally in with(areas) { alliesAround(player, ENTICE_RADIUS) }) {
            if (paid + ALLY_COST > spare) {
                break
            }
            buffs.give(ally, buff)
            ally.spotanim(fx_spots.vfx_soulflame_horn_impact_spotanim01, height = 96)
            paid += ALLY_COST
        }
        if (paid > 0) {
            manager.takeSpecialEnergy(this, paid)
        }
        return true
    }

    /**
     * Consumes every soul stack: 6% more accuracy and damage for each, and 8 Hitpoints healed for
     * each. Needs at least one stack, which costs no special attack energy.
     */
    private class Behead(manager: SpecialAttackManager, private val stacks: SoulStacks) :
        MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.dark_spec_player)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            val count = stacks.consume(player)
            if (count == 0) {
                return refuse("You need at least one soul stack to use Behead.")
            }
            manager.playWeaponFx(this, attack)
            fx.play(this)
            val bonus = 1.0 + 0.06 * count
            val roll = rollMelee(target, attack, accuracy = bonus, damage = bonus)
            hitMelee(target, attack, roll)
            statHeal(stats.hitpoints, constant = count * HEAL_PER_STACK, percent = 0)
            return resume(target)
        }

        private companion object {
            const val HEAL_PER_STACK = 8
        }
    }

    /**
     * Strikes everything within three tiles (a 7x7 area), the target included, with damage
     * that grows 1% for every point of prayer bonus the player wears.
     */
    private class SolSlam(
        manager: SpecialAttackManager,
        private val areas: AreaTargets,
        private val bonuses: WornBonuses,
    ) : MeleeSpecial(manager) {
        private val fx = SpecialFx(fx_seqs.vmq4_arkan_blade_special)

        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val damage = 1.0 + max(0, bonuses.prayerBonus(player)) / 100.0
            val everyone = ArrayList<PathingEntity>()
            everyone += target
            everyone += with(areas) { around(target, center = player.coords, radius = 3, limit = LIMIT) }
            for (victim in everyone) {
                hitMelee(victim, attack, rollMelee(victim, attack, damage = damage))
            }
            return resume(target)
        }

        private companion object {
            const val LIMIT = 24
        }
    }

    /**
     * A strike with much more accuracy on a target that is not fighting anyone, which the damage
     * dealt then takes off the target's Defence.
     */
    private class Backstab(manager: SpecialAttackManager) : MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val unsuspecting = target.isUnsuspecting()
            val roll = rollMelee(target, attack, accuracy = if (unsuspecting) 3.0 else 1.0)
            hitMelee(target, attack, roll)
            if (roll.landed) {
                TargetStats.lower(target, CombatSkill.Defence, roll.damage)
            }
            return resume(target)
        }
    }

    /**
     * A normal strike, and a vampyre juvenile or juvinate with half its hitpoints or fewer is
     * trapped: held still for a minute.
     */
    private class Retainer(manager: SpecialAttackManager, private val status: TargetStatus) :
        MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            manager.playWeaponFx(this, attack)
            val roll = rollMelee(target, attack)
            hitMelee(target, attack, roll)
            if (target is Npc && target.isTrappable()) {
                status.stun(target, TRAP_TICKS)
                mes("You trap the vampyre.")
            }
            return resume(target)
        }

        private fun Npc.isTrappable(): Boolean {
            val name = visType.name.lowercase()
            val vampyre = "juvenile" in name || "juvinate" in name
            return vampyre && hitpoints * 2 <= baseHitpointsLvl
        }

        private companion object {
            const val TRAP_TICKS = 100
        }
    }

    private companion object {
        const val TUMEKENS_LIGHT_PRAYER = 50
        const val MAX_RUN_ENERGY = 10_000
        const val VIRULENCE_TICKS = 16
        const val ENTICE_TICKS = 16
        const val ENTICE_RADIUS = 6
        const val ENTICE_BASE_COST = 250
        const val ALLY_COST = 100

        val RETAINER_WEAPONS: List<ObjType> =
            listOf(
                sobjs.burgh_rod_command_final_1,
                sobjs.burgh_rod_command_final_2,
                sobjs.burgh_rod_command_final_3,
                sobjs.burgh_rod_command_final_4,
                sobjs.burgh_rod_command_final_5,
                sobjs.burgh_rod_command_final_6,
                sobjs.burgh_rod_command_final_7,
                sobjs.burgh_rod_command_final_8,
                sobjs.burgh_rod_command_final_9,
                sobjs.burgh_rod_command_final_10,
                sobjs.ivandis_flail,
                sobjs.blisterwood_flail,
            )

        val RESTORED_STATS =
            listOf(
                stats.attack,
                stats.strength,
                stats.defence,
                stats.ranged,
                stats.magic,
            )

        /** Whether the target is not in a fight with anyone. */
        fun PathingEntity.isUnsuspecting(): Boolean =
            when (this) {
                is Npc -> !isInCombat()
                is Player -> !isInCombat()
            }
    }
}
