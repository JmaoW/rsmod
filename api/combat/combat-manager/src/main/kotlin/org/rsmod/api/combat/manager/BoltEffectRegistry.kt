package org.rsmod.api.combat.manager

import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.Hit

/**
 * What an enchanted bolt does to the damage roll of the ranged attack that fires it. The roll is
 * [PlayerAttackManager.rollRangedDamage]; the effect is worked out by whatever owns the bolts (see
 * [BoltEffectRegistry]).
 *
 * @property alwaysHit the shot hits whatever the accuracy roll says.
 * @property maxHitMultiplier multiplies the max hit of the shot.
 * @property extraDamage is added to the max hit of the shot (the `boltSpecDamage` of the formulas).
 * @property fixedDamage if set, the shot deals exactly this much and no damage is rolled.
 */
public class BoltAdjust(
    public val alwaysHit: Boolean = false,
    public val maxHitMultiplier: Double = 1.0,
    public val extraDamage: Int = 0,
    public val fixedDamage: Int? = null,
)

/** An enchanted bolt on its way to [PlayerAttackManager.rollRangedDamage]'s target. */
public interface BoltShot {
    /**
     * Rolls the effects that work whether or not the shot would hit (and make it hit when they do).
     *
     * @return `null` if none worked.
     */
    public fun rollBypass(): BoltAdjust?

    /**
     * Rolls the effects that only work on a shot that hits. Called after the accuracy roll, and
     * only if [rollBypass] gave nothing.
     *
     * @return `null` if none worked.
     */
    public fun rollOnHit(): BoltAdjust?

    /**
     * The shot has been queued as [hit] on [target]. Runs the rest of the effect that worked:
     * poison, drains, healing and the graphic, timed to the hit.
     */
    public fun landed(target: PathingEntity, hit: Hit, clientDelay: Int, hitDelay: Int)
}

/** Gives [PlayerAttackManager] the enchanted bolt effects of the player firing. */
public interface BoltEffectHandler {
    /**
     * @param boost what a special attack did to the effect of this shot, if anything.
     * @return the shot [source] is firing at [target], or `null` if it is not an enchanted bolt.
     */
    public fun begin(source: Player, target: PathingEntity, boost: BoltBoost?): BoltShot?
}

/** How a special attack changes the bolt effect of the shot it fires. */
public enum class BoltBoost {
    /** The effect works if the shot hits. */
    Guaranteed,

    /** The chance of the effect is doubled. */
    Doubled,
}

/**
 * Where the owner of the enchanted bolt effects registers itself, so that the damage roll of every
 * crossbow shot can ask it without the combat code knowing about gems, dragonfire or poison. With
 * no [handler], bolts are plain.
 */
@Singleton
public class BoltEffectRegistry {
    public var handler: BoltEffectHandler? = null

    private val boosts = WeakHashMap<Player, BoltBoost>()

    /** Boosts the effect of the next enchanted bolt [player] fires. */
    public fun boost(player: Player, boost: BoltBoost) {
        boosts[player] = boost
    }

    /** The boost of the shot [player] is firing, which that shot uses up whatever it is. */
    public fun takeBoost(player: Player): BoltBoost? = boosts.remove(player)
}
