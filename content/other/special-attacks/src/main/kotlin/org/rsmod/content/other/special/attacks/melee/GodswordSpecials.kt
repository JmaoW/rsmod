package org.rsmod.content.other.special.attacks.melee

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.SpecialAttackMap
import org.rsmod.api.specials.SpecialAttackRepository
import org.rsmod.content.other.special.attacks.configs.fx_seqs
import org.rsmod.content.other.special.attacks.configs.fx_spots
import org.rsmod.content.other.special.attacks.configs.sobjs
import org.rsmod.content.other.special.attacks.support.CombatSkill
import org.rsmod.content.other.special.attacks.support.MeleeRoll
import org.rsmod.content.other.special.attacks.support.MeleeSpecial
import org.rsmod.content.other.special.attacks.support.SpecialFx
import org.rsmod.content.other.special.attacks.support.TargetStats
import org.rsmod.content.other.special.attacks.support.TargetStatus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

/**
 * The five godswords. Each makes an attack with double accuracy and an effect of its god: the
 * Armadyl godsword hits harder, Bandos drains, Saradomin heals, Zamorak freezes and the Ancient
 * godsword marks its target for a blood sacrifice. The echo godsword does all of them at once.
 */
class GodswordSpecials
@Inject
constructor(
    private val status: TargetStatus,
    private val worldQueues: WorldQueueList,
    private val attackManager: PlayerAttackManager,
) : SpecialAttackMap {
    override fun SpecialAttackRepository.register(manager: SpecialAttackManager) {
        val god = Effects(status, worldQueues, attackManager)

        val armadyl = Godsword(manager, god, ARMADYL, Variant.Standard)
        registerMelee(sobjs.ags, armadyl)
        registerMelee(sobjs.br_ags, armadyl)
        registerMelee(sobjs.deadman_blighted_ags, armadyl)
        registerMelee(sobjs.deadman_ags, armadyl)
        registerMelee(sobjs.agsg, Godsword(manager, god, ARMADYL, Variant.Ornate))

        val bandos = Godsword(manager, god, BANDOS, Variant.Standard)
        registerMelee(sobjs.bgs, bandos)
        registerMelee(sobjs.bgsg, Godsword(manager, god, BANDOS, Variant.Ornate))

        val saradomin = Godsword(manager, god, SARADOMIN, Variant.Standard)
        registerMelee(sobjs.sgs, saradomin)
        registerMelee(sobjs.sgsg, Godsword(manager, god, SARADOMIN, Variant.Ornate))

        val zamorak = Godsword(manager, god, ZAMORAK, Variant.Standard)
        registerMelee(sobjs.zgs, zamorak)
        registerMelee(sobjs.zgsg, Godsword(manager, god, ZAMORAK, Variant.Ornate))

        val ancient = Godsword(manager, god, ANCIENT, Variant.Standard)
        registerMelee(sobjs.ancient_godsword, ancient)
        registerMelee(sobjs.br_ancient_godsword, ancient)

        registerMelee(sobjs.echo_godsword, Godsword(manager, god, ECHO, Variant.Standard))
    }

    enum class Variant {
        Standard,
        Ornate,
    }

    /** What a godsword's special is: how hard it hits, how it looks and what else it does. */
    class God(
        val damage: Double,
        val standard: SpecialFx,
        val ornate: SpecialFx,
        val effects: Set<Effect>,
    )

    enum class Effect {
        Judgement,
        Warstrike,
        HealingBlade,
        IceCleave,
        BloodSacrifice,
    }

    private class Godsword(
        manager: SpecialAttackManager,
        private val effects: Effects,
        private val god: God,
        private val variant: Variant,
    ) : MeleeSpecial(manager) {
        override fun ProtectedAccess.perform(
            target: PathingEntity,
            attack: CombatAttack.Melee,
        ): Boolean {
            val fx = if (variant == Variant.Ornate) god.ornate else god.standard
            fx.play(this)
            val roll = rollMelee(target, attack, accuracy = 2.0, damage = god.damage)
            hitMelee(target, attack, roll)
            effects.apply(this, god.effects, target, roll)
            return resume(target)
        }
    }

    /** The effects themselves, shared by the five godswords and the echo godsword. */
    class Effects(
        private val status: TargetStatus,
        private val worldQueues: WorldQueueList,
        private val attackManager: PlayerAttackManager,
    ) {
        internal fun apply(
            access: ProtectedAccess,
            effects: Set<Effect>,
            target: PathingEntity,
            roll: MeleeRoll,
        ) {
            for (effect in effects) {
                when (effect) {
                    Effect.Judgement -> Unit
                    Effect.Warstrike -> warstrike(target, roll.damage)
                    Effect.HealingBlade -> healingBlade(access, roll.damage)
                    Effect.IceCleave -> if (roll.accurate) iceCleave(target)
                    Effect.BloodSacrifice -> if (roll.landed) bloodSacrifice(access.player, target)
                }
            }
        }

        /**
         * Drains the target by the damage dealt, a stat at a time: Defence, Strength, Prayer, Attack,
         * Magic and Ranged, until the damage is spent or there is nothing left to drain.
         */
        private fun warstrike(target: PathingEntity, damage: Int) {
            var remaining = damage
            for (skill in WARSTRIKE_ORDER) {
                if (remaining <= 0) {
                    return
                }
                val minimum = if (skill == CombatSkill.Prayer) 0 else 1
                remaining -= TargetStats.lower(target, skill, remaining, minimum)
            }
        }

        /** Gives back half the damage as Hitpoints and a quarter as Prayer, at least 10 and 5. */
        private fun healingBlade(access: ProtectedAccess, damage: Int) {
            access.statHeal(stats.hitpoints, constant = max(10, damage / 2), percent = 0)
            access.statHeal(stats.prayer, constant = max(5, damage / 4), percent = 0)
        }

        /** Freezes the target for 20 seconds. */
        private fun iceCleave(target: PathingEntity) {
            status.bind(target, ICE_CLEAVE_TICKS)
        }

        /**
         * Marks the target. A few seconds later, unless it has got away, it takes 25 damage and the
         * attacker is healed by what was dealt (at most 15 against a player).
         */
        private fun bloodSacrifice(attacker: Player, target: PathingEntity) {
            if (target is Player) {
                target.mes("You have been marked for blood sacrifice.")
            }
            worldQueues.add(SACRIFICE_DELAY) {
                val gone =
                    attacker.slotId == PathingEntity.INVALID_SLOT ||
                        target.slotId == PathingEntity.INVALID_SLOT
                if (gone || attacker.hitpoints <= 0 || target.hitpointsLeft() <= 0) {
                    return@add
                }
                val distance =
                    max(
                        abs(attacker.coords.x - target.coords.x),
                        abs(attacker.coords.z - target.coords.z),
                    )
                if (distance >= ESCAPE_DISTANCE) {
                    if (target is Player) {
                        target.mes("You have escaped the blood sacrifice.")
                    }
                    return@add
                }
                if (target is Player) {
                    target.mes("You have been sacrificed.")
                }
                target.spotanim(fx_spots.spell_blood_barrage_impact, height = 92)
                val dealt = minOf(SACRIFICE_DAMAGE, target.hitpointsLeft())
                attackManager.queueTypelessHit(attacker, target, SACRIFICE_DAMAGE, delay = 1)
                val heal = if (target is Player) minOf(dealt, PVP_HEAL_CAP) else dealt
                attacker.statHeal(stats.hitpoints, constant = heal, percent = 0)
            }
        }

        private fun PathingEntity.hitpointsLeft(): Int =
            when (this) {
                is Npc -> hitpoints
                is Player -> hitpoints
            }
    }

    private companion object {
        val WARSTRIKE_ORDER =
            listOf(
                CombatSkill.Defence,
                CombatSkill.Strength,
                CombatSkill.Prayer,
                CombatSkill.Attack,
                CombatSkill.Magic,
                CombatSkill.Ranged,
            )

        /** Twenty seconds. */
        const val ICE_CLEAVE_TICKS = 33

        /** Blood Sacrifice lands after five seconds... */
        const val SACRIFICE_DELAY = 8

        /** ...unless the target has got this far away. */
        const val ESCAPE_DISTANCE = 5
        const val SACRIFICE_DAMAGE = 25
        const val PVP_HEAL_CAP = 15

        val ARMADYL =
            God(
                damage = 1.375,
                standard =
                    SpecialFx(fx_seqs.ags_special_player, fx_spots.dh_sword_update_armadyl_special_spotanim),
                ornate =
                    SpecialFx(
                        fx_seqs.ags_special_ornate_player,
                        fx_spots.dh_sword_update_armadyl_special_spotanim,
                    ),
                effects = setOf(Effect.Judgement),
            )
        val BANDOS =
            God(
                damage = 1.21,
                standard =
                    SpecialFx(fx_seqs.bgs_special_player, fx_spots.dh_sword_update_bandos_special_spotanim),
                ornate =
                    SpecialFx(
                        fx_seqs.bgs_special_ornate_player,
                        fx_spots.dh_sword_update_bandos_special_spotanim,
                    ),
                effects = setOf(Effect.Warstrike),
            )
        val SARADOMIN =
            God(
                damage = 1.1,
                standard =
                    SpecialFx(fx_seqs.sgs_special_player, fx_spots.dh_sword_update_saradomin_special_spotanim),
                ornate =
                    SpecialFx(
                        fx_seqs.sgs_special_ornate_player,
                        fx_spots.dh_sword_update_saradomin_special_spotanim,
                    ),
                effects = setOf(Effect.HealingBlade),
            )
        val ZAMORAK =
            God(
                damage = 1.1,
                standard =
                    SpecialFx(fx_seqs.zgs_special_player, fx_spots.dh_sword_update_zamorak_special_spotanim),
                ornate =
                    SpecialFx(
                        fx_seqs.zgs_special_ornate_player,
                        fx_spots.dh_sword_update_zamorak_special_spotanim,
                    ),
                effects = setOf(Effect.IceCleave),
            )
        val ANCIENT =
            God(
                damage = 1.1,
                standard = SpecialFx(fx_seqs.ngs_special_player, fx_spots.ngs_special_spotanim),
                ornate = SpecialFx(fx_seqs.ngs_special_player, fx_spots.ngs_special_spotanim),
                effects = setOf(Effect.BloodSacrifice),
            )

        /** The echo godsword hits as hard as the hardest and has every other godsword's effect. */
        val ECHO =
            God(
                damage = 1.375,
                standard = SpecialFx(fx_seqs.the_godsword_special_attack),
                ornate = SpecialFx(fx_seqs.the_godsword_special_attack),
                effects =
                    setOf(
                        Effect.Judgement,
                        Effect.Warstrike,
                        Effect.HealingBlade,
                        Effect.IceCleave,
                        Effect.BloodSacrifice,
                    ),
            )
    }
}
