package org.rsmod.content.other.special.attacks.support

import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.combat.MagicSpecialAttack
import org.rsmod.api.specials.combat.MeleeSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

/**
 * A melee special attack that treats both kinds of target alike: [perform] is the whole attack.
 * Whatever a special does that only means something against one kind of target (draining a player's
 * run energy, say) is up to [perform] to check for.
 *
 * The rolls and hits are the same as a normal attack's, with the special's multipliers on top.
 */
abstract class MeleeSpecial(protected val manager: SpecialAttackManager) : MeleeSpecialAttack {
    final override suspend fun ProtectedAccess.attack(
        target: Npc,
        attack: CombatAttack.Melee,
    ): Boolean = perform(target, attack)

    final override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Melee,
    ): Boolean = perform(target, attack)

    /** Returns `true` if the attack was made, so that the engine takes the special energy. */
    protected abstract fun ProtectedAccess.perform(
        target: PathingEntity,
        attack: CombatAttack.Melee,
    ): Boolean

    /**
     * Rolls a melee attack on [target].
     *
     * @param accuracy multiplies the attack roll.
     * @param damage multiplies the max hit.
     * @param blockType what the target defends with, if not the attack's own type.
     * @param defenceFactor scales the target's Defence level for the accuracy roll.
     * @param minHit the fraction of the max hit that a successful roll deals at least.
     * @param minFlat the damage a successful roll deals at least.
     * @param bonus damage added to a successful roll after everything else.
     */
    protected fun ProtectedAccess.rollMelee(
        target: PathingEntity,
        attack: CombatAttack.Melee,
        accuracy: Double = 1.0,
        damage: Double = 1.0,
        blockType: MeleeAttackType? = attack.type,
        defenceFactor: Double = 1.0,
        minHit: Double = 0.0,
        minFlat: Int = 0,
        bonus: Int = 0,
    ): MeleeRoll {
        val maxHit = manager.calculateMeleeMaxHit(this, target, attack.type, attack.style, damage)
        val accurate =
            manager.rollMeleeAccuracy(
                source = this,
                target = target,
                attackType = attack.type,
                attackStyle = attack.style,
                blockType = blockType,
                multiplier = accuracy,
                defenceFactor = defenceFactor,
            )
        if (!accurate) {
            return MeleeRoll(false, 0, maxHit)
        }
        val floor = max(minFlat, (maxHit * minHit).toInt())
        val rolled =
            if (floor > 0) {
                random.of(floor..max(floor, maxHit))
            } else {
                manager.rollMeleeMaxHit(this, target, attack.type, attack.style, damage)
            }
        return MeleeRoll(true, rolled + bonus, maxHit)
    }

    /** Gives the combat xp for [roll] and queues its hit, which lands after [delay] cycles. */
    protected fun ProtectedAccess.hitMelee(
        target: PathingEntity,
        attack: CombatAttack.Melee,
        roll: MeleeRoll,
        delay: Int = 1,
        ignoreProtection: Boolean = false,
    ): Hit = hitMelee(target, attack, roll.damage, delay, ignoreProtection)

    protected fun ProtectedAccess.hitMelee(
        target: PathingEntity,
        attack: CombatAttack.Melee,
        damage: Int,
        delay: Int = 1,
        ignoreProtection: Boolean = false,
    ): Hit {
        manager.giveCombatXp(this, target, attack, damage)
        return manager.queueMeleeHit(this, target, damage, delay, ignoreProtection)
    }

    /** Keeps the fight going after the attack and tells the engine to take the energy. */
    protected fun ProtectedAccess.resume(target: PathingEntity): Boolean {
        manager.continueCombat(this, target)
        return true
    }

    /**
     * Lengthens the wait for the next attack by [ticks] (or shortens it, if negative). The wait is
     * what the engine set for the weapon before the special began.
     */
    protected fun ProtectedAccess.slowNextAttack(ticks: Int) {
        val remaining = (actionDelay - mapClock).coerceAtLeast(0)
        manager.setNextAttackDelay(this, (remaining + ticks).coerceAtLeast(1))
    }

    /** The attack the special replaces failed before it began: no energy is taken. */
    protected fun ProtectedAccess.refuse(message: String): Boolean {
        mes(message)
        manager.stopCombat(this)
        return false
    }
}

/**
 * A magic special attack made with a staff (the staff's own spell, not a spell from the book): the
 * same as a melee special, with its rolls and hit being a magic attack's.
 */
abstract class StaffSpecial(protected val manager: SpecialAttackManager) : MagicSpecialAttack {
    final override suspend fun ProtectedAccess.attack(
        target: Npc,
        attack: CombatAttack.Staff,
    ): Boolean = perform(target, attack)

    final override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Staff,
    ): Boolean = perform(target, attack)

    protected abstract suspend fun ProtectedAccess.perform(
        target: PathingEntity,
        attack: CombatAttack.Staff,
    ): Boolean

    /**
     * Rolls a magic attack on [target].
     *
     * @param baseMaxHit the max hit of the attack before the player's gear and the other
     *   multipliers.
     * @param accuracy multiplies the attack roll.
     * @param damage multiplies the max hit.
     * @param guaranteed if the attack cannot miss.
     * @param minHit the fraction of the max hit that a successful roll deals at least.
     */
    protected fun ProtectedAccess.rollStaff(
        target: PathingEntity,
        attack: CombatAttack.Staff,
        baseMaxHit: Int,
        accuracy: Double = 1.0,
        damage: Double = 1.0,
        guaranteed: Boolean = false,
        minHit: Double = 0.0,
    ): MeleeRoll {
        val maxHit = manager.calculateStaffMaxHit(this, target, baseMaxHit, damage)
        val accurate =
            guaranteed || manager.rollStaffAccuracy(this, target, attack.style, accuracy)
        if (!accurate) {
            return MeleeRoll(false, 0, maxHit)
        }
        val floor = (maxHit * minHit).toInt()
        val rolled =
            if (floor > 0) {
                random.of(floor..max(floor, maxHit))
            } else {
                manager.rollStaffMaxHit(this, target, baseMaxHit, damage)
            }
        return MeleeRoll(true, rolled, maxHit)
    }

    /**
     * Gives the xp for [damage] and queues it as a magic hit. The hit lands after the cycles a
     * magic spell takes to reach [target].
     */
    protected fun ProtectedAccess.hitStaff(
        target: PathingEntity,
        attack: CombatAttack.Staff,
        damage: Int,
    ): Hit {
        manager.giveCombatXp(this, target, attack, damage)
        val distance = player.coords.chebyshevDistance(target.coords)
        val hitDelay = 1 + (3 + distance) / 3
        return manager.queueMagicHit(this, target, damage, hitDelay * CLIENT_CYCLES, hitDelay)
    }

    protected fun ProtectedAccess.resume(target: PathingEntity): Boolean {
        manager.continueCombat(this, target)
        return true
    }

    /** The attack the special replaces failed before it began: no energy is taken. */
    protected fun ProtectedAccess.refuse(message: String): Boolean {
        mes(message)
        manager.stopCombat(this)
        return false
    }

    private companion object {
        /** Client cycles in one game cycle. */
        const val CLIENT_CYCLES = 30
    }
}
