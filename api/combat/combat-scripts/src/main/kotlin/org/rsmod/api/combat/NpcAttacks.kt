package org.rsmod.api.combat

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.player.combatPlayDefendAnim
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.combat.npc.attackingPlayer
import org.rsmod.api.combat.npc.lastAttack
import org.rsmod.api.combat.player.aggressiveNpc
import org.rsmod.api.combat.player.lastCombat
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Called when a npc's hit lands on [target], after protection prayers have changed [hit]. [hit]'s
 * damage is what the target takes.
 */
public fun interface NpcImpactListener {
    public fun onImpact(target: Player, hit: HitBuilder)
}

/**
 * Lets a npc attack players with melee, ranged or magic, with the standard accuracy and max hit
 * formulas, protection prayers, and (for ranged and magic) a projectile that the hit waits for.
 *
 * This only makes the attack. The script that calls it decides **when** the npc attacks (set
 * `actionDelay` from the npc's attack rate) and **what** it does (which [NpcAttack]).
 */
public class NpcAttacks
@Inject
constructor(
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val random: GameRandom,
    private val worldRepo: WorldRepository,
    private val objTypes: ObjTypeList,
    private val targeting: NpcTargeting,
) {
    /**
     * Makes [npc] attack with [attack], on the players its [TargetingStyle] picks given the player
     * it is fighting, [primary]. Returns the players that were attacked. [onImpact] is told when
     * each hit lands.
     */
    public fun perform(
        npc: Npc,
        primary: Player,
        attack: NpcAttack,
        onImpact: NpcImpactListener? = null,
    ): List<Player> {
        val targets = targeting.select(npc, primary).filter { it.isValidTarget() }
        for ((index, target) in targets.withIndex()) {
            attack(npc, target, attack, playAnim = index == 0, onImpact = onImpact)
        }
        return targets
    }

    /**
     * Makes [npc] attack the single player [target]. Returns `false` (and does nothing) if the
     * target cannot be attacked. [onImpact] is told when the hit lands.
     */
    public fun attack(
        npc: Npc,
        target: Player,
        attack: NpcAttack,
        playAnim: Boolean = true,
        onImpact: NpcImpactListener? = null,
    ): Boolean {
        if (!target.isValidTarget()) {
            return false
        }

        if (playAnim) {
            val anim = attack.anim ?: npc.visType.param(params.attack_anim)
            npc.anim(anim)
            attack.castSpot?.let { npc.spotanim(it) }
        }
        val sound = attack.sound ?: npc.visType.paramOrNull(params.attack_sound)
        sound?.let(target::soundSynth)

        var hitDelay = attack.hitDelay ?: MELEE_HIT_DELAY
        var clientDelay = (hitDelay - MELEE_HIT_DELAY) * CLIENT_CYCLES_PER_TICK
        val projectile = attack.projectile
        val projanim = attack.projanim
        if (projectile != null && projanim != null) {
            val flight = worldRepo.projAnim(npc, target, projectile, projanim).durations
            hitDelay = maxOf(1, flight.serverDelay)
            clientDelay = flight.clientDelay
        }
        val minDelay = attack.minHitDelay
        if (minDelay != null && hitDelay < minDelay) {
            hitDelay = minDelay
            clientDelay = maxOf(clientDelay, (hitDelay - MELEE_HIT_DELAY) * CLIENT_CYCLES_PER_TICK)
        }

        // Damage is capped to the target's hitpoints now, not on impact, so a player who eats
        // while the hit is in flight survives it ("tick-eating"). The impact modifier can only
        // lower the damage from here.
        val damage = minOf(rollDamage(npc, target, attack), target.hitpoints)

        npc.lastAttack = npc.currentMapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = npc.currentMapClock
        target.aggressiveNpc = npc.uid

        // Retaliation is queued before the hit so that the hit is the last queue entry. The hit is
        // modified when it lands, not now, so a player who switches their protection prayer in
        // response to the attack animation is protected.
        target.queueCombatRetaliate(npc)
        target.queueImpactHit(
            npc,
            hitDelay,
            attack.style.hitType(),
            damage,
            modifier = impactModifier(onImpact),
        )
        // An animation delay is limited to one byte, which a long flight (Jad across the cave)
        // can exceed. The block animation then plays a little early rather than throwing.
        target.combatPlayDefendAnim(objTypes, minOf(clientDelay, MAX_ANIM_DELAY))
        attack.impactSpot?.let { target.spotanim(it, delay = clientDelay) }
        return true
    }

    private fun impactModifier(onImpact: NpcImpactListener?): PlayerHitModifier {
        if (onImpact == null) {
            return StandardPlayerHitModifier
        }
        return PlayerHitModifier { target ->
            with(StandardPlayerHitModifier) { modify(target) }
            onImpact.onImpact(target, this)
        }
    }

    private fun rollDamage(npc: Npc, target: Player, attack: NpcAttack): Int {
        val hit =
            attack.alwaysHits ||
                when (attack.style) {
                    NpcAttackStyle.Melee ->
                        accuracy.rollMeleeAccuracy(npc, target, attack.meleeType, random)
                    NpcAttackStyle.Ranged -> accuracy.rollRangedAccuracy(npc, target, random)
                    NpcAttackStyle.Magic -> accuracy.rollMagicAccuracy(npc, target, random)
                }
        if (!hit) {
            return 0
        }
        val maxHit =
            attack.maxHit
                ?: when (attack.style) {
                    NpcAttackStyle.Melee -> maxHits.getMeleeMaxHit(npc, target, attack.meleeType)
                    NpcAttackStyle.Ranged -> maxHits.getRangedMaxHit(npc, target)
                    NpcAttackStyle.Magic -> maxHits.getMagicMaxHit(npc, target)
                }
        return random.of(0..maxHit)
    }

    private fun NpcAttackStyle.hitType(): HitType =
        when (this) {
            NpcAttackStyle.Melee -> HitType.Melee
            NpcAttackStyle.Ranged -> HitType.Ranged
            NpcAttackStyle.Magic -> HitType.Magic
        }

    private companion object {
        /** Melee hits land on the next cycle, as in [NvPCombat]. */
        const val MELEE_HIT_DELAY = 1

        /** The client counts graphic delays in 20ms cycles: 30 of them make a game tick. */
        const val CLIENT_CYCLES_PER_TICK = 30

        /** The largest delay `PathingEntity.anim` accepts. */
        const val MAX_ANIM_DELAY = 254
    }
}
