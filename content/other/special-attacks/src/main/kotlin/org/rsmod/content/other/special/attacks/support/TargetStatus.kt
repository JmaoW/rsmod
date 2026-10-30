package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.api.spells.attack.NpcFreezeImmunity
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Holding a target still, for the special attacks that bind or stun. A bound npc cannot move but
 * can still fight; a stunned one cannot do either. After a bind ends the target is immune to
 * another for a short while, as it is for the binding spells.
 */
@Singleton
class TargetStatus @Inject constructor(private val immunity: NpcFreezeImmunity) {
    /** Whether [target] can be bound right now. Only npcs resist, by type or by a recent bind. */
    fun canBind(target: PathingEntity): Boolean =
        when (target) {
            is Npc ->
                target.visType.param(params.freeze_resistance) < FULL_RESISTANCE &&
                    !immunity.isImmune(target, target.currentMapClock)
            is Player -> true
        }

    /** Binds [target] for [ticks]. @return `false` if it could not be bound. */
    fun bind(target: PathingEntity, ticks: Int): Boolean {
        if (!canBind(target)) {
            return false
        }
        target.freeze(ticks)
        if (target is Npc) {
            immunity.immuneUntil(target, target.currentMapClock + ticks + IMMUNITY_TICKS)
        }
        return true
    }

    /** Frees [target] from a bind, and from the immunity a bind leaves. */
    fun release(target: PathingEntity) {
        target.frozenUntil = Int.MIN_VALUE
        if (target is Npc) {
            immunity.clear(target)
        }
    }

    /**
     * Stuns [target] for [ticks]: a stunned npc does nothing at all, a stunned player cannot move
     * or attack.
     */
    fun stun(target: PathingEntity, ticks: Int) {
        when (target) {
            is Npc -> {
                target.delay = target.currentMapClock + ticks
                target.freeze(ticks)
            }
            is Player -> {
                target.freeze(ticks)
                target.actionDelay = maxOf(target.actionDelay, target.currentMapClock + ticks)
            }
        }
    }

    private companion object {
        const val FULL_RESISTANCE = 100
        const val IMMUNITY_TICKS = 5
    }
}
