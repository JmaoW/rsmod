package org.rsmod.content.other.dragonfire

import org.rsmod.content.skills.herblore.scripts.AntifireLevel

/** How many combinations of protection a [Breath] has a row for. */
const val BREATH_ROWS = 12

/**
 * What stands between a player and a breath of dragonfire: a shield that blocks it (the anti-dragon
 * shield, a dragonfire shield, a dragonfire ward or an ancient wyvern shield), Protect from Magic,
 * and the antifire potion they are under.
 */
data class DragonfireProtection(
    val shield: Boolean = false,
    val prayer: Boolean = false,
    val potion: AntifireLevel = AntifireLevel.None,
) {
    /** The row of the wiki's tables for this combination, see [Breath]. */
    internal val row: Int
        get() {
            val base = (if (shield) 1 else 0) + (if (prayer) 2 else 0)
            return when (potion) {
                AntifireLevel.None -> base
                AntifireLevel.Regular -> 4 + base * 2
                AntifireLevel.Super -> 5 + base * 2
            }
        }

    companion object {
        val NONE = DragonfireProtection()
    }
}

/**
 * The breaths of dragonfire and the most each can hit for under every [DragonfireProtection] (OSRS
 * Wiki, "Dragonfire"). Each breath has a row per combination, in the Wiki's order:
 *
 * nothing, shield, prayer, shield + prayer, antifire, super antifire, shield + antifire, shield +
 * super antifire, prayer + antifire, prayer + super antifire, shield + prayer + antifire, shield +
 * prayer + super antifire.
 *
 * Two maximums are kept for each combination. The breath rolls the dragon's Magic accuracy against
 * the player's Magic defence: when the player is **burnt** (the roll is lost) the [burnt] row
 * applies, when they resist some of it the [resisted] row does. Breaths that do not use the roll
 * have the same row for both.
 *
 * The drake's volcanic breath is not here: it works differently, see [DrakeBreath].
 */
enum class Breath(private val burnt: IntArray, private val resisted: IntArray = burnt) {
    /** Green, blue, red and black dragons (also lava and reanimated) and the brutal dragons. */
    Chromatic(
        burnt = intArrayOf(50, 5, 10, 5, 35, 0, 0, 0, 0, 0, 0, 0),
        resisted = intArrayOf(30, 5, 10, 5, 15, 0, 0, 0, 0, 0, 0, 0),
    ),

    /** The metal dragons: Protect from Magic does nothing against them. */
    Metallic(
        burnt = intArrayOf(50, 5, 50, 5, 35, 0, 0, 0, 35, 0, 0, 0),
        resisted = intArrayOf(30, 5, 30, 5, 15, 0, 0, 0, 15, 0, 0, 0),
    ),

    /** The King Black Dragon's plain fire. */
    KingBlackDragon(intArrayOf(65, 15, 20, 15, 50, 0, 0, 0, 5, 0, 0, 0)),

    /** The King Black Dragon's poison, ice and shock breaths: all three share these numbers. */
    KingBlackDragonSpecial(intArrayOf(50, 10, 15, 10, 50, 50, 10, 10, 15, 15, 10, 10)),
    Elvarg(intArrayOf(70, 10, 55, 7, 55, 0, 7, 0, 40, 0, 4, 0)),

    /**
     * Vorkath's fiery, venomous and corrupting breaths (OSRS Wiki, "Dragonfire/Vorkath"): an
     * antifire potion alone does much less against it, only a shield with a super antifire potion
     * stops it.
     */
    Vorkath(
        burnt = intArrayOf(80, 20, 30, 20, 70, 60, 10, 0, 20, 10, 10, 0),
        resisted = intArrayOf(50, 20, 30, 20, 40, 30, 10, 0, 20, 10, 10, 0),
    );

    init {
        require(burnt.size == BREATH_ROWS && resisted.size == BREATH_ROWS) {
            "A breath needs $BREATH_ROWS rows: $this"
        }
    }

    /** The most this breath hits for. [burntRoll] is `true` when the player lost the roll. */
    fun maxHit(protection: DragonfireProtection, burntRoll: Boolean): Int =
        (if (burntRoll) burnt else resisted)[protection.row]
}

/**
 * A drake's volcanic breath (OSRS Wiki, "Drake"). It is treated as dragonfire, but Protect from
 * Magic does nothing to it. A player it strikes is hit [HITS] times, once a tick, each for
 * [UNPROTECTED] damage. A shield, or an antifire potion, lowers each hit to [REDUCED]. A shield
 * with an antifire potion, or a super antifire potion alone, stops it.
 */
object DrakeBreath {
    const val HITS = 4
    val UNPROTECTED = 6..8
    val REDUCED = 0..4
    val NEGATED = 0..0

    fun damage(protection: DragonfireProtection): IntRange =
        when {
            protection.potion == AntifireLevel.Super -> NEGATED
            protection.shield && protection.potion != AntifireLevel.None -> NEGATED
            protection.shield || protection.potion == AntifireLevel.Regular -> REDUCED
            else -> UNPROTECTED
        }
}
