package org.rsmod.content.areas.misc.isleofsouls.wilderness

import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * The hits the Wilderness bosses deal. Most of their attacks are an ordinary roll against the
 * player's defence, but several break the usual rules: Callisto's melee is only halved by Protect
 * from Melee, Venenatis hits through prayers while her spiderlings live, and the lightning and the
 * orb ignore prayer or need it all. [hit] takes the damage and how much of it the matching
 * protection prayer takes off.
 */
class WildHits(private val d: WildBosses) {
    /**
     * Queues one hit on [victim], landing [delay] ticks from now. If the protection prayer of the
     * hit's [type] is up, [reduction] percent of the damage is taken off it (100 stops it, which is
     * what an ordinary npc attack does). Typeless hits ignore prayers.
     */
    fun hit(
        npc: Npc,
        victim: Player,
        type: HitType,
        damage: Int,
        delay: Int,
        reduction: Int = FULL,
        retaliate: Boolean = false,
        onImpact: NpcImpactListener? = null,
    ) {
        if (!victim.isValidTarget()) {
            return
        }
        val capped = minOf(damage, victim.hitpoints)
        if (retaliate) {
            victim.queueCombatRetaliate(npc)
        }
        victim.queueImpactHit(
            npc,
            maxOf(1, delay),
            type,
            capped,
            modifier =
                PlayerHitModifier { target ->
                    if (protects(target, this.type)) {
                        this.damage = this.damage * (100 - reduction) / 100
                    }
                    onImpact?.onImpact(target, this)
                },
        )
    }

    /** A hit that neither misses nor can be prayed against. */
    fun typeless(npc: Npc, victim: Player, damage: Int, delay: Int) {
        hit(npc, victim, HitType.Typeless, damage, delay)
    }

    /**
     * An attack rolled against [victim]'s defence: it hits for [minHit] to [maxHit] if the roll
     * lands (or always, with [alwaysHits]) and 0 otherwise. Returns the damage rolled before any
     * prayer.
     */
    fun strike(
        npc: Npc,
        victim: Player,
        style: NpcAttackStyle,
        minHit: Int,
        maxHit: Int,
        delay: Int,
        reduction: Int = FULL,
        meleeType: MeleeAttackType = MeleeAttackType.Crush,
        alwaysHits: Boolean = false,
        retaliate: Boolean = false,
        onImpact: NpcImpactListener? = null,
    ): Int {
        val lands =
            alwaysHits ||
                when (style) {
                    NpcAttackStyle.Melee ->
                        d.accuracy.rollMeleeAccuracy(npc, victim, meleeType, d.random)
                    NpcAttackStyle.Ranged -> d.accuracy.rollRangedAccuracy(npc, victim, d.random)
                    NpcAttackStyle.Magic -> d.accuracy.rollMagicAccuracy(npc, victim, d.random)
                }
        val damage = if (lands) d.random.of(minHit..maxHit) else 0
        hit(npc, victim, hitType(style), damage, delay, reduction, retaliate, onImpact)
        return damage
    }

    fun hitType(style: NpcAttackStyle): HitType =
        when (style) {
            NpcAttackStyle.Melee -> HitType.Melee
            NpcAttackStyle.Ranged -> HitType.Ranged
            NpcAttackStyle.Magic -> HitType.Magic
        }

    private fun protects(target: Player, type: HitType): Boolean =
        when (type) {
            HitType.Melee -> target.vars[varbits.protect_from_melee] == 1
            HitType.Ranged -> target.vars[varbits.protect_from_missiles] == 1
            HitType.Magic -> target.vars[varbits.protect_from_magic] == 1
            else -> false
        }

    /** Whether [victim] has the protection prayer against [style] up. */
    fun isProtected(victim: Player, style: NpcAttackStyle): Boolean =
        protects(victim, hitType(style))

    fun drainPrayer(player: Player, points: Int) {
        val now = player.stat(stats.prayer)
        if (now > 0) {
            player.statSub(stats.prayer, minOf(points, now), 0)
        }
    }

    fun drainRun(player: Player, amount: Int) {
        player.runEnergy = maxOf(0, player.runEnergy - amount)
    }

    companion object {
        /** The protection prayer takes all of the damage: what an ordinary npc attack does. */
        const val FULL = 100
    }
}
