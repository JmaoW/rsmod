package org.rsmod.api.combat.mastery

import jakarta.inject.Inject
import org.rsmod.game.entity.Player

/** The three combat styles that have a mastery path. */
public enum class MasteryStyle {
    Melee,
    Ranged,
    Magic,
}

/**
 * Supplies a player's unlocked mastery tier for each combat style. Content modules that own the
 * mastery state (such as a league) register an implementation through a set binding, so combat code
 * can ask about masteries without depending on that content.
 */
public interface CombatMasteryProvider {
    /** The highest mastery tier (`0` to [CombatMasteries.MAX_TIER]) unlocked for [style]. */
    public fun tier(player: Player, style: MasteryStyle): Int
}

/**
 * The single place combat code asks about masteries. With no provider registered every answer is
 * `0`, so worlds without masteries behave exactly as before.
 */
public class CombatMasteries
@Inject
constructor(private val providers: Set<CombatMasteryProvider>) {
    /** The highest tier [player] has unlocked for [style], from `0` to [MAX_TIER]. */
    public fun tier(player: Player, style: MasteryStyle): Int {
        val highest = providers.maxOfOrNull { it.tier(player, style) } ?: return 0
        return highest.coerceIn(0, MAX_TIER)
    }

    /** Whether [player] has unlocked [tier] (1 to [MAX_TIER]) or better for [style]. */
    public fun has(player: Player, style: MasteryStyle, tier: Int): Boolean =
        tier(player, style) >= tier

    /**
     * The universal passive tier: the highest tier reached in any style. Passive `n` is active when
     * this is `n` or more.
     */
    public fun passiveTier(player: Player): Int = MasteryStyle.entries.maxOf { tier(player, it) }

    /** Whether the universal passive for [tier] (1 to [MAX_TIER]) is active for [player]. */
    public fun hasPassive(player: Player, tier: Int): Boolean = passiveTier(player) >= tier

    public companion object {
        public const val MAX_TIER: Int = 6
    }
}
