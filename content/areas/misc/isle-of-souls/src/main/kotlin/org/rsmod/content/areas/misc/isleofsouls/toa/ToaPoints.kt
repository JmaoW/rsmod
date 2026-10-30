package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.min

/**
 * The reward points of one raid. Wiki (Chest (Tombs of Amascut)): the team starts on 5,000; each
 * point of damage is a room point for most monsters (a multiplier per monster, see [Multiplier]), a
 * player's room points are capped at 20,000 a room; when a room is completed they are added to the
 * team's total, capped at 64,000, and the player with the most gets a bonus of 300 times the team
 * size. A death costs 20% of the total, at least 1,000. At the end the starting 5,000 is taken off
 * again.
 *
 * Whether a death costs the team or the player the 20% is not in the wiki's text; it is the team's
 * total here.
 */
class ToaPoints(private val teamSize: () -> Int) {
    /** Points per damage dealt to the kinds of monster the wiki lists. */
    object Multiplier {
        const val DEFAULT = 1.0
        const val WARDEN_CORE = 0.0
        const val APMEKEN_BABOON = 1.2
        const val BABA = 2.0
        const val ZEBAK = 1.5
        const val KEPHRI_SCARAB = 0.5
        const val HET_SEAL = 2.5
        const val OBELISK = 1.5
        const val WARDENS_P2 = 2.0
        const val WARDENS_P3 = 2.5
    }

    var total: Int = START
        private set

    private val room = HashMap<Int, Double>()
    private val contribution = HashMap<Int, Int>()

    /** [player] dealt [damage] to something worth [multiplier] points a point of damage. */
    fun damage(player: Int, damage: Int, multiplier: Double = Multiplier.DEFAULT) {
        if (damage <= 0 || multiplier <= 0.0) {
            return
        }
        val points = room.getOrDefault(player, 0.0) + damage * multiplier
        room[player] = min(points, ROOM_CAP.toDouble())
    }

    /** A player's room points so far in the room being fought. */
    fun roomPoints(player: Int): Int = room.getOrDefault(player, 0.0).toInt()

    /**
     * The room was completed: everyone's room points join the total and the best earner gets the
     * bonus. Returns the player who earned it, if anyone dealt damage.
     */
    fun completeRoom(): Int? {
        var added = 0
        var best: Int? = null
        var bestPoints = 0
        for ((player, points) in room) {
            val whole = points.toInt()
            added += whole
            contribution.merge(player, whole, Int::plus)
            if (whole > bestPoints) {
                bestPoints = whole
                best = player
            }
        }
        room.clear()
        if (best != null) {
            added += MVP_PER_PLAYER * teamSize().coerceAtLeast(1)
            contribution.merge(best, MVP_PER_PLAYER * teamSize().coerceAtLeast(1), Int::plus)
        }
        total = min(total + added, TOTAL_CAP)
        return best
    }

    /** A flat award, such as a puzzle completion. */
    fun award(player: Int, points: Int) {
        if (points <= 0) {
            return
        }
        contribution.merge(player, points, Int::plus)
        total = min(total + points, TOTAL_CAP)
    }

    /** A death: 20% of the total goes, at least 1,000. */
    fun death() {
        val lost = maxOf(DEATH_MINIMUM, total / 5)
        total = (total - lost).coerceAtLeast(0)
    }

    /** What the loot and the purple chance are worked out from. */
    val lootPoints: Int
        get() = (total - START).coerceAtLeast(0)

    /** A player's own share of what the team earned, for splitting a unique between players. */
    fun personal(player: Int): Int = contribution.getOrDefault(player, 0)

    companion object {
        const val START = 5_000
        const val ROOM_CAP = 20_000
        const val TOTAL_CAP = 64_000
        const val MVP_PER_PLAYER = 300
        const val DEATH_MINIMUM = 1_000
    }
}
