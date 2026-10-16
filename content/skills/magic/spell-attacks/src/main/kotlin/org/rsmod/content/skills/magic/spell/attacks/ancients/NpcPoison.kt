package org.rsmod.content.skills.magic.spell.attacks.ancients

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList

/**
 * Poison on npcs, for the Smoke spells. A poisoned npc takes a hit every 30 ticks from the player
 * who poisoned it; the first hit is the spell's starting damage and the damage drops by one for
 * every five hits, until the poison runs out (the wiki's rule: severity is five times the starting
 * damage and drops by one with each hit). Poison hits give no experience. A new poison replaces the
 * old one only if it is stronger.
 */
@Singleton
class NpcPoison
@Inject
constructor(private val worldQueues: WorldQueueList, private val modifier: NpcHitModifier) {
    private class State(var severity: Int, val source: Player)

    private val poisoned = WeakHashMap<Npc, State>()

    fun isPoisoned(npc: Npc): Boolean = npc in poisoned

    /** The damage of the next poison hit on [npc], or `0` if it is not poisoned. */
    fun nextDamage(npc: Npc): Int = poisoned[npc]?.let { damageOf(it.severity) } ?: 0

    /**
     * Poisons [npc] on behalf of [source] with [startDamage].
     *
     * @return `false` if the npc is immune or already has a poison at least as strong.
     */
    fun poison(npc: Npc, source: Player, startDamage: Int): Boolean {
        if (npc.visType.param(params.poison_immunity) != 0) {
            return false
        }
        val severity = startDamage * HITS_PER_STEP
        val current = poisoned[npc]
        if (current != null && current.severity >= severity) {
            return false
        }
        val state = State(severity, source)
        poisoned[npc] = state
        schedule(npc, state)
        return true
    }

    private fun schedule(npc: Npc, state: State) {
        worldQueues.add(INTERVAL) { tick(npc, state) }
    }

    private fun tick(npc: Npc, state: State) {
        // A newer poison took over, or the npc is gone.
        if (poisoned[npc] !== state) {
            return
        }
        val gone = npc.hitpoints <= 0 || npc.slotId == PathingEntity.INVALID_SLOT
        val sourceGone = state.source.slotId == PathingEntity.INVALID_SLOT
        if (gone || sourceGone) {
            poisoned.remove(npc)
            return
        }

        npc.queueHit(
            source = state.source,
            delay = 1,
            type = HitType.Typeless,
            damage = damageOf(state.severity),
            modifier = modifier,
            hitmark = hitmark_groups.poison_damage,
        )
        state.severity--
        if (state.severity <= 0) {
            poisoned.remove(npc)
        } else {
            schedule(npc, state)
        }
    }

    private fun damageOf(severity: Int): Int = (severity + HITS_PER_STEP - 1) / HITS_PER_STEP

    companion object {
        /** Ticks between poison hits. */
        const val INTERVAL = 30

        /** Poison hits it takes for the damage to drop by one. */
        const val HITS_PER_STEP = 5
    }
}
