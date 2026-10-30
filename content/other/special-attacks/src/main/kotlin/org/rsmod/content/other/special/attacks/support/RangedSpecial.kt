package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.refs.categories
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.quiver
import org.rsmod.api.specials.SpecialAttackManager
import org.rsmod.api.specials.combat.RangedSpecialAttack
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.spot.SpotanimType

/**
 * The ammunition and projectile a ranged attack is made with: what a weapon needs checked before
 * its special can be made, found the way a normal ranged attack finds it.
 */
class Shot(
    val weapon: UnpackedObjType,
    val ammo: UnpackedObjType?,
    val thrown: Boolean,
    val travel: SpotanimType,
    val projanim: ProjAnimType,
    val launch: SpotanimType?,
) {
    /** The ammunition obj the hit carries; a thrown weapon is its own ammunition. */
    val hitAmmo: UnpackedObjType?
        get() = if (thrown) null else ammo
}

/** What ranged special attacks need to look at and use up a player's ammunition. */
class RangedKit @Inject constructor(val ammunition: RangedAmmoManager, val objTypes: ObjTypeList)

/**
 * A ranged special attack that treats both kinds of target alike: [perform] is the whole attack,
 * including firing the projectiles ([fire]) and using up the ammunition ([hitRanged]).
 */
abstract class RangedSpecial(protected val manager: SpecialAttackManager, protected val kit: RangedKit) :
    RangedSpecialAttack {
    final override suspend fun ProtectedAccess.attack(
        target: Npc,
        attack: CombatAttack.Ranged,
    ): Boolean = perform(target, attack)

    final override suspend fun ProtectedAccess.attack(
        target: Player,
        attack: CombatAttack.Ranged,
    ): Boolean = perform(target, attack)

    protected abstract fun ProtectedAccess.perform(
        target: PathingEntity,
        attack: CombatAttack.Ranged,
    ): Boolean

    /**
     * Finds the ammunition and projectile for the attack, as a normal ranged attack does, and makes
     * sure there is enough of it for [count] shots.
     *
     * @return `null` if the attack cannot be made (combat is stopped and no energy is taken).
     */
    protected fun ProtectedAccess.prepare(attack: CombatAttack.Ranged, count: Int = 1): Shot? {
        val weapon = kit.objTypes[attack.weapon]
        val quiverType = kit.objTypes.getOrNull(player.quiver)
        val thrown = weapon.isCategoryType(categories.throwing_weapon)

        if (!kit.ammunition.attemptAmmoUsage(player, weapon, quiverType)) {
            manager.stopCombat(this)
            return null
        }

        val ammo = if (thrown) weapon else quiverType
        val travel = ammo?.paramOrNull(params.proj_travel)
        val projanim = weapon.paramOrNull(params.proj_type)
        if (ammo == null || travel == null || projanim == null) {
            manager.stopCombat(this)
            mes("You are unable to fire your ammunition.")
            return null
        }

        val have = if (thrown) player.righthandCount() else player.quiver?.count ?: 0
        if (have < count) {
            manager.stopCombat(this)
            mes("You need to have at least $count in your quiver for this special attack.")
            return null
        }

        val launch = ammo.paramOrNull(params.proj_launch)
        return Shot(weapon, quiverType, thrown, travel, projanim, launch)
    }

    private fun Player.righthandCount(): Int =
        worn[org.rsmod.game.type.obj.Wearpos.RightHand.slot]?.count ?: 0

    /** Sends a projectile at [target] and returns it, for the cycles it takes to land. */
    protected fun ProtectedAccess.fire(
        shot: Shot,
        target: PathingEntity,
        travel: SpotanimType = shot.travel,
        projanim: ProjAnimType = shot.projanim,
    ): ProjAnim = manager.spawnProjectile(this, target, travel, projanim)

    /**
     * Rolls a ranged attack on [target].
     *
     * @param accuracy multiplies the attack roll.
     * @param damage multiplies the max hit.
     * @param guaranteed if the attack cannot miss.
     * @param minHit the fraction of the max hit that a successful roll deals at least.
     */
    protected fun ProtectedAccess.rollRanged(
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        accuracy: Double = 1.0,
        damage: Double = 1.0,
        guaranteed: Boolean = false,
        minHit: Double = 0.0,
        minFlat: Int = 0,
    ): MeleeRoll {
        val maxHit =
            manager.calculateRangedMaxHit(this, target, attack.type, attack.style, damage, 0)
        val accurate =
            guaranteed ||
                manager.rollRangedAccuracy(
                    source = this,
                    target = target,
                    attackType = attack.type,
                    attackStyle = attack.style,
                    blockType = attack.type,
                    multiplier = accuracy,
                )
        if (!accurate) {
            return MeleeRoll(false, 0, maxHit)
        }
        val floor = max(minFlat, (maxHit * minHit).toInt())
        val rolled =
            if (floor > 0) {
                random.of(floor..max(floor, maxHit))
            } else {
                manager.rollRangedMaxHit(this, target, attack.type, attack.style, damage, 0)
            }
        return MeleeRoll(true, rolled, maxHit)
    }

    /**
     * Gives the xp and queues the hit of a shot that has been [fire]d, and uses up the ammunition
     * it was made with. The hit lands when the projectile does.
     */
    protected fun ProtectedAccess.hitRanged(
        shot: Shot,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        damage: Int,
        proj: ProjAnim,
        ammoCount: Int = 1,
        silent: Boolean = false,
    ): Hit {
        manager.giveCombatXp(this, target, attack, damage)
        repeat(ammoCount) { useAmmo(shot, target, proj.serverCycles) }
        return if (silent) {
            manager.queueRangedDamage(this, target, shot.hitAmmo, damage, proj.serverCycles)
        } else {
            manager.queueRangedHit(
                this,
                target,
                shot.hitAmmo,
                damage,
                proj.clientCycles,
                proj.serverCycles,
            )
        }
    }

    protected fun ProtectedAccess.useAmmo(shot: Shot, target: PathingEntity, dropDelay: Int) {
        if (shot.thrown) {
            kit.ammunition.useThrownWeapon(player, shot.weapon, target.coords, dropDelay)
        } else if (shot.ammo != null) {
            kit.ammunition.useQuiverAmmo(player, shot.ammo, target.coords, dropDelay)
        }
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

    /**
     * Lengthens the wait for the next attack by [ticks] (or shortens it, if negative). The wait is
     * what the engine set for the weapon before the special began.
     */
    protected fun ProtectedAccess.slowNextAttack(ticks: Int) {
        val remaining = (actionDelay - mapClock).coerceAtLeast(0)
        manager.setNextAttackDelay(this, (remaining + ticks).coerceAtLeast(1))
    }
}
