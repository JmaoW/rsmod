package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How the raid level, the size of the party and a path's level make the tombs' monsters tougher.
 *
 * Wiki: every 5 raid levels add 2% (additive) to hitpoints, defence, accuracy and damage, with
 * damage capped at +150%. The 2nd and 3rd player each add 90% of a boss's base hitpoints, the 4th
 * and later add 60%. The first path level adds 8% to most monsters' hitpoints and damage, each
 * further level 5% up to six (8, 13, 18, 23, 28, 33).
 *
 * How the three combine is the wiki's silent part: they are multiplied here, as the TSPS version
 * did (it matched the hitpoints OpenRune captured in a live raid).
 */
object ToaScaling {
    /** Wiki: damage is capped at +150% from the raid level. */
    const val DAMAGE_CAP = 2.5

    /** The multiplier on hitpoints, defence and accuracy from the raid level alone. */
    fun raidFactor(raidLevel: Int): Double = 1.0 + raidLevel / 250.0

    /** The multiplier from the number of players (1-8). */
    fun partyFactor(players: Int): Double {
        val size = players.coerceAtLeast(1)
        val second = (min(size, 3) - 1).coerceAtLeast(0)
        val later = (size - 3).coerceAtLeast(0)
        return 1.0 + 0.9 * second + 0.6 * later
    }

    /** The bonus a path level adds to hitpoints and damage, as a fraction. */
    fun pathBonus(pathLevel: Int): Double =
        when {
            pathLevel <= 0 -> 0.0
            else -> 0.08 + 0.05 * (min(pathLevel, 6) - 1)
        }

    /**
     * A monster's hitpoints, rounded the way the wiki's calculator does: to 10 above 300, to 5
     * above 100, to the nearest one below that.
     */
    fun hitpoints(base: Int, raidLevel: Int, players: Int, pathLevel: Int): Int {
        val scaled =
            base * raidFactor(raidLevel) * partyFactor(players) * (1.0 + pathBonus(pathLevel))
        val step =
            when {
                scaled > 300 -> 10.0
                scaled > 100 -> 5.0
                else -> 1.0
            }
        return ((scaled / step).roundToInt() * step).roundToInt().coerceAtLeast(1)
    }

    /** The multiplier on a monster's attack and defence rolls. */
    fun rollFactor(raidLevel: Int): Double = raidFactor(raidLevel)

    /** The multiplier on a monster's damage. */
    fun damageFactor(raidLevel: Int, pathLevel: Int): Double =
        min(raidFactor(raidLevel), DAMAGE_CAP) * (1.0 + pathBonus(pathLevel))
}
