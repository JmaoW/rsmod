package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.combat.manager.PlayerPoisonEvents
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.type.hitmark.HitmarkTypeGroup

/** The kinds of damage over time, each with its own hitsplat. A target has one of each at most. */
enum class DotKind(val hitmark: HitmarkTypeGroup) {
    Poison(hitmark_groups.poison_damage),
    Burn(hitmark_groups.burn),
    Bleed(hitmark_groups.bleed),
}

/**
 * Damage over time from special attacks: poison, burn and bleed. Each tick of one is a typeless hit
 * that no prayer or armour reduces, from the player who started it, and it stops when the target
 * dies or goes, the attacker logs out, or the damage runs out. A new dose of a kind replaces the old
 * one only if it has more to deal.
 */
@Singleton
class SpecialDots
@Inject
constructor(
    private val worldQueues: WorldQueueList,
    private val manager: PlayerAttackManager,
    private val eventBus: EventBus,
) {
    private class Dot(
        val kind: DotKind,
        val source: Player,
        val interval: Int,
        val hits: Int,
        val damageAt: (Int) -> Int,
    ) {
        var done = 0

        val remaining: Int
            get() = (done until hits).sumOf(damageAt)
    }

    private val active = WeakHashMap<PathingEntity, MutableMap<DotKind, Dot>>()

    /** Whether [target] has a damage over time of [kind] going. */
    fun has(target: PathingEntity, kind: DotKind): Boolean = active[target]?.containsKey(kind) == true

    /** The damage [target] has yet to take from its [kind] damage over time. */
    fun remaining(target: PathingEntity, kind: DotKind): Int =
        active[target]?.get(kind)?.remaining ?: 0

    /** The damage the next hit of the [kind] damage over time of [target] deals, `0` if none. */
    fun nextHit(target: PathingEntity, kind: DotKind): Int =
        active[target]?.get(kind)?.let { it.damageAt(it.done) } ?: 0

    /** Takes away the [kind] damage over time of [target], returning what it had left to deal. */
    fun consume(target: PathingEntity, kind: DotKind): Int {
        val dot = active[target]?.remove(kind) ?: return 0
        return dot.remaining
    }

    /**
     * Poisons [target] for [source], as the wiki describes poison: it hits every 30 ticks, first
     * for [startDamage], and the damage drops by 1 after every 5 hits until it is gone.
     *
     * A player is poisoned with their own poison (the orb, antipoison and the rest), exactly as an
     * npc's poison would, whoever [source] is.
     *
     * @return `false` if the target is immune, protected or already has a poison at least as strong.
     */
    fun poison(target: PathingEntity, source: Player, startDamage: Int): Boolean {
        if (target is Player) {
            val event = PlayerPoisonEvents.Apply(source, target, startDamage)
            eventBus.publish(event)
            return event.applied
        }
        if (target is Npc && target.visType.param(params.poison_immunity) != 0) {
            return false
        }
        return start(
            target,
            Dot(DotKind.Poison, source, POISON_INTERVAL, startDamage * POISON_STEP) {
                startDamage - it / POISON_STEP
            },
        )
    }

    /** Burns [target] for [damage] in all, 1 every [interval] ticks. */
    fun burn(target: PathingEntity, source: Player, damage: Int, interval: Int): Boolean =
        start(target, Dot(DotKind.Burn, source, interval, damage) { 1 })

    /** Bleeds [target] for [damage] in all, up to [chunk] of it every [interval] ticks. */
    fun bleed(
        target: PathingEntity,
        source: Player,
        damage: Int,
        chunk: Int,
        interval: Int,
    ): Boolean {
        if (damage <= 0) {
            return false
        }
        val hits = (damage + chunk - 1) / chunk
        return start(
            target,
            Dot(DotKind.Bleed, source, interval, hits) { index ->
                if (index == hits - 1) damage - chunk * (hits - 1) else chunk
            },
        )
    }

    private fun start(target: PathingEntity, dot: Dot): Boolean {
        val dots = active.getOrPut(target) { HashMap() }
        val current = dots[dot.kind]
        if (current != null && current.remaining >= dot.remaining) {
            return false
        }
        dots[dot.kind] = dot
        schedule(target, dot)
        return true
    }

    private fun schedule(target: PathingEntity, dot: Dot) {
        worldQueues.add(dot.interval) { tick(target, dot) }
    }

    private fun tick(target: PathingEntity, dot: Dot) {
        val dots = active[target]
        // Replaced by a stronger dose, consumed, or the target is gone.
        if (dots == null || dots[dot.kind] !== dot) {
            return
        }
        if (target.isGone() || dot.source.slotId == PathingEntity.INVALID_SLOT) {
            dots.remove(dot.kind)
            return
        }
        val damage = dot.damageAt(dot.done)
        manager.queueTypelessHit(dot.source, target, damage, 1, dot.kind.hitmark)
        dot.done++
        if (dot.done >= dot.hits) {
            dots.remove(dot.kind)
        } else {
            schedule(target, dot)
        }
    }

    private fun PathingEntity.isGone(): Boolean =
        slotId == PathingEntity.INVALID_SLOT ||
            when (this) {
                is Npc -> hitpoints <= 0
                is Player -> hitpoints <= 0
            }

    companion object {
        /** Ticks between poison hits. */
        const val POISON_INTERVAL = 30

        /** Poison hits it takes for the damage to drop by one. */
        const val POISON_STEP = 5
    }
}
