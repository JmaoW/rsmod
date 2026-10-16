package org.rsmod.content.other.food

import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.game.type.stat.StatType

internal object FoodSeqs : SeqReferences() {
    val eat = find("human_eat")
}

internal object FoodTimers : TimerReferences() {
    /** Counts down the second heal of a hunter meat. */
    val delayed_heal = find("food_delayed_heal")
}

/** How a food delays the player. Each kind has its own cooldown, so one of each can be eaten. */
internal enum class FoodKind(
    /** Ticks added to the player's next attack. */
    val attackDelay: Int
) {
    Standard(attackDelay = 3),

    /** Combo food can be eaten on the same tick as standard food. */
    Combo(attackDelay = 2),
}

/** What a food heals. */
internal sealed interface Heal {
    class Fixed(val amount: Int) : Heal

    /** A random whole number from [min] to [max], both included. */
    class Roll(val min: Int, val max: Int) : Heal

    /** [flat] plus [percent] percent of the player's base Hitpoints level. */
    class OfLevel(val flat: Int, val percent: Int) : Heal

    /** The wiki's formula in `Foods.anglerfishHeal`, which can raise hitpoints above the base. */
    data object Anglerfish : Heal
}

/** [constant] plus [percent] percent of the base level of [stat]. */
internal class StatAmount(val stat: StatType, val constant: Int, val percent: Int = 0)

/** The effects of a food besides healing. */
internal class FoodEffects(
    /** Raised towards the boost's own cap, like a potion. */
    val boosts: List<StatAmount> = emptyList(),
    val drains: List<StatAmount> = emptyList(),
    /** Raised towards the base level, never past it. */
    val restores: List<StatAmount> = emptyList(),
    /** Whole percent of run energy restored. */
    val runEnergy: Int = 0,
    /** Drains this much from one random skill (Hitpoints excluded) that is above level 2. */
    val randomDrain: Int = 0,
) {
    fun isEmpty(): Boolean =
        boosts.isEmpty() &&
            drains.isEmpty() &&
            restores.isEmpty() &&
            runEnergy == 0 &&
            randomDrain == 0

    companion object {
        val None = FoodEffects()
    }
}

/** The second heal of a hunter meat, [ticks] after eating. A newer one replaces a pending one. */
internal class DelayedHeal(val ticks: Int, val heal: Int, val effects: FoodEffects)

/** One weighted result of a food that has several, like a kebab. */
internal class Outcome(
    val weight: Int,
    val heal: Heal? = null,
    val effects: FoodEffects = FoodEffects.None,
    val message: String? = null,
    /** Sent when the outcome drained a stat, `{skill}` being the skill's name. */
    val drainMessage: String? = null,
)

/**
 * One food or drink. Items are found by the names the cache gives them, like the OSRS wiki does, so
 * every obj with a name (a minigame's copy of a shark, say) is eaten the same way.
 */
internal class FoodDef(
    val name: String,
    /** The item name of each bite, first to last. */
    val bites: List<String> = listOf(name),
    /**
     * When above `1`, the bites all share [name] and are told apart by their ids, lowest first
     * being the first bite.
     */
    val sameNameBites: Int = 1,
    val kind: FoodKind = FoodKind.Standard,
    /** Ticks before the next bite can be taken, per bite. A missing value is [STANDARD_DELAY]. */
    val eatDelays: List<Int> = emptyList(),
    val heal: Heal? = null,
    val effects: FoodEffects = FoodEffects.None,
    val then: DelayedHeal? = null,
    val outcomes: List<Outcome> = emptyList(),
    /** The name of the item left once the last bite is gone, such as `Pie dish`. */
    val leaves: String? = null,
    /** The eat message of each bite. A missing value uses [message], or a default. */
    val messages: List<String> = emptyList(),
    val message: String? = null,
    /** Sent after a heal. `null` sends nothing. */
    val healMessage: String? = HEAL_MESSAGE,
    /** Sent after the eat message and any heal message. */
    val afterMessage: String? = null,
    /** `"eat"` or `"drink"`, which decides the inventory option and the default message. */
    val verb: String = "eat",
    /** Refuses to be eaten, and tells the player why. */
    val refuse: String? = null,
) {
    val biteCount: Int
        get() = if (sameNameBites > 1) sameNameBites else bites.size

    fun eatDelay(bite: Int): Int = eatDelays.getOrNull(bite) ?: STANDARD_DELAY

    companion object {
        const val STANDARD_DELAY = 3
        const val HEAL_MESSAGE = "It heals some health."
    }
}
