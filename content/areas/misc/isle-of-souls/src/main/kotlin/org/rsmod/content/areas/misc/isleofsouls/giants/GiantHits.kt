package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueImpactHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

/**
 * The hits the giants deal. Obor's ranged attacks are only halved by Protect from Missiles (wiki),
 * so these let a protection prayer take off only part of the damage; every other attack lets the
 * prayer stop it. [reduction] is the percent of the damage the matching prayer takes off.
 */
class GiantHits
@Inject
constructor(private val accuracy: AccuracyFormulae, private val random: GameRandom) {
    /**
     * An attack rolled against [victim]'s defence: it hits for 0 to [maxHit] if the roll lands and
     * 0 otherwise, landing [delay] ticks from now. Returns the damage rolled before any prayer.
     */
    fun strike(
        npc: Npc,
        victim: Player,
        style: NpcAttackStyle,
        maxHit: Int,
        delay: Int,
        reduction: Int = FULL,
        meleeType: MeleeAttackType = MeleeAttackType.Crush,
        onImpact: NpcImpactListener? = null,
    ): Int {
        if (!victim.isValidTarget()) {
            return 0
        }
        val lands =
            when (style) {
                NpcAttackStyle.Melee -> accuracy.rollMeleeAccuracy(npc, victim, meleeType, random)
                NpcAttackStyle.Ranged -> accuracy.rollRangedAccuracy(npc, victim, random)
                NpcAttackStyle.Magic -> accuracy.rollMagicAccuracy(npc, victim, random)
            }
        val damage = if (lands) random.of(0..maxHit) else 0
        val type = hitType(style)
        victim.queueImpactHit(
            npc,
            maxOf(1, delay),
            type,
            minOf(damage, victim.hitpoints),
            modifier =
                PlayerHitModifier { target ->
                    if (protects(target, this.type)) {
                        this.damage = this.damage * (100 - reduction) / 100
                    }
                    onImpact?.onImpact(target, this)
                },
        )
        return damage
    }

    private fun hitType(style: NpcAttackStyle): HitType =
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

    companion object {
        /** The protection prayer takes all of the damage: what an ordinary npc attack does. */
        const val FULL = 100

        /** Obor's ranged attacks do half damage through Protect from Missiles (wiki). */
        const val HALF = 50
    }
}
