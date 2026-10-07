package org.rsmod.content.areas.misc.isleofsouls.event

/**
 * A team's score at one moment. [reachedAt] is when the team earned its latest points (the time of
 * its most recent completed task, in epoch milliseconds), or `null` if it has none yet.
 */
data class TeamScore(
    val teamId: Int,
    val name: String,
    val points: Int,
    val tasks: Int,
    val reachedAt: Long?,
)

/** A [score] with its [rank]. Teams with identical scores share a rank. */
data class TeamStanding(val score: TeamScore, val rank: Int) {
    val teamId: Int
        get() = score.teamId

    val name: String
        get() = score.name

    val points: Int
        get() = score.points

    val tasks: Int
        get() = score.tasks
}

/**
 * The ranking rules for the event.
 * 1. More League Points ranks higher.
 * 2. On equal points, the team that reached them first ranks higher, so if several teams finish
 *    every task the first to finish wins.
 * 3. Teams still level (for example everyone on zero) are listed by name and share a rank.
 */
object TeamRanking {
    private val order =
        compareByDescending<TeamScore> { it.points }
            .thenBy(nullsLast()) { it.reachedAt }
            .thenBy { it.name.lowercase() }
            .thenBy { it.teamId }

    fun rank(scores: List<TeamScore>): List<TeamStanding> {
        val sorted = scores.sortedWith(order)
        val standings = ArrayList<TeamStanding>(sorted.size)
        for ((index, score) in sorted.withIndex()) {
            val previous = standings.lastOrNull()
            val level =
                previous != null &&
                    previous.points == score.points &&
                    previous.score.reachedAt == score.reachedAt
            standings += TeamStanding(score, if (level) previous!!.rank else index + 1)
        }
        return standings
    }

    /** The team in front, or `null` while nobody has any points. */
    fun leader(standings: List<TeamStanding>): TeamStanding? =
        standings.firstOrNull()?.takeIf { it.points > 0 }
}
