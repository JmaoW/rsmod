package org.rsmod.api.combat

import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType

/** The damage type of a [NpcAttack]: it decides the accuracy and max hit rolls and the hit type. */
public enum class NpcAttackStyle {
    Melee,
    Ranged,
    Magic,
}

/**
 * Describes one attack that a npc makes against a player. Use [NpcAttacks] to perform it.
 *
 * Anything left `null` falls back to the npc's own configuration: its `attack_anim` and
 * `attack_sound` params for [anim] and [sound], and the standard accuracy and max hit formulas for
 * its stats for [alwaysHits] and [maxHit].
 *
 * @param projectile The spotanim of the projectile that flies from the npc to the target. When set
 *   with [projanim], the hit lands when the projectile does.
 * @param projanim The projectile's flight settings (speed, arc, and so on).
 * @param castSpot A graphic played on the npc as it attacks.
 * @param castHeight How high above the npc's feet [castSpot] is drawn.
 * @param impactSpot A graphic played on the target when the projectile lands.
 * @param maxHit Overrides the max hit from the formulas. Bosses with fixed damage use this.
 * @param minHit The lowest damage a hit that lands can roll. Set it equal to [maxHit] (with
 *   [alwaysHits]) for an attack that always does the same damage.
 * @param hitDelay How many ticks the hit takes to land when there is no projectile (a boulder
 *   falling on the target, for example). Ignored when a projectile is set: it decides.
 * @param minHitDelay The hit never lands sooner than this many ticks after the attack, even when a
 *   projectile would arrive earlier (a short flight at close range, for example).
 * @param alwaysHits Skips the accuracy roll.
 * @param meleeType The melee damage type, used for accuracy against the target's defence bonus.
 * @param extraHits How many more hits follow on the next ticks after the first lands (a burning
 *   breath that hits four times has `3`). Each rolls its own damage and tells the impact listener.
 * @param typeless Makes the hit typeless, so no protection prayer touches it. The script that makes
 *   the attack sets the damage itself when the hit lands (dragonfire does).
 */
public data class NpcAttack(
    val style: NpcAttackStyle,
    val anim: SeqType? = null,
    val sound: SynthType? = null,
    val castSpot: SpotanimType? = null,
    val projectile: SpotanimType? = null,
    val projanim: ProjAnimType? = null,
    val impactSpot: SpotanimType? = null,
    val maxHit: Int? = null,
    val minHit: Int = 0,
    val hitDelay: Int? = null,
    val minHitDelay: Int? = null,
    val alwaysHits: Boolean = false,
    val meleeType: MeleeAttackType = MeleeAttackType.Crush,
    val typeless: Boolean = false,
    val extraHits: Int = 0,
    val castHeight: Int = 0,
) {
    init {
        require((projectile == null) == (projanim == null)) {
            "A projectile needs both a `projectile` spotanim and a `projanim`: $this"
        }
        require(maxHit == null || maxHit >= 0) { "maxHit cannot be negative: $this" }
        require(minHit >= 0 && (maxHit == null || minHit <= maxHit)) {
            "minHit must be between 0 and maxHit: $this"
        }
        require(extraHits >= 0) { "extraHits cannot be negative: $this" }
        require(hitDelay == null || hitDelay >= 1) { "hitDelay must be at least 1: $this" }
        require(minHitDelay == null || minHitDelay >= 1) { "minHitDelay must be at least 1: $this" }
    }
}
