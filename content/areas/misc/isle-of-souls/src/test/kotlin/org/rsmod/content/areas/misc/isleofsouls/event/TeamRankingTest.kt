package org.rsmod.content.areas.misc.isleofsouls.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TeamRankingTest {
    private fun score(id: Int, name: String, points: Int, reachedAt: Long?, tasks: Int = 0) =
        TeamScore(id, name, points, tasks, reachedAt)

    private fun TeamRanking.names(scores: List<TeamScore>) = rank(scores).map { it.name }

    @Test
    fun `more points rank higher`() {
        val standings =
            TeamRanking.rank(
                listOf(
                    score(1, "Alpha", 30, 100),
                    score(2, "Beta", 340, 500),
                    score(3, "Gamma", 80, 50),
                )
            )

        assertEquals(listOf("Beta", "Gamma", "Alpha"), standings.map { it.name })
        assertEquals(listOf(1, 2, 3), standings.map { it.rank })
    }

    @Test
    fun `equal points go to the team that got there first`() {
        val standings =
            TeamRanking.rank(listOf(score(1, "Alpha", 100, 2_000), score(2, "Beta", 100, 1_000)))

        assertEquals(listOf("Beta", "Alpha"), standings.map { it.name })
        assertEquals(listOf(1, 2), standings.map { it.rank })
    }

    @Test
    fun `if several teams finish everything the first to finish wins`() {
        val finished = 5_000
        val standings =
            TeamRanking.rank(
                listOf(
                    score(1, "Late", finished, 9_000),
                    score(2, "Early", finished, 4_000),
                    score(3, "Middle", finished, 6_000),
                    score(4, "Nearly", finished - 10, 1_000),
                )
            )

        assertEquals(listOf("Early", "Middle", "Late", "Nearly"), standings.map { it.name })
    }

    @Test
    fun `teams with no points are listed by name and share a rank`() {
        val standings =
            TeamRanking.rank(
                listOf(
                    score(1, "beta", 0, null),
                    score(2, "Alpha", 0, null),
                    score(3, "Gamma", 10, 5),
                )
            )

        assertEquals(listOf("Gamma", "Alpha", "beta"), standings.map { it.name })
        assertEquals(listOf(1, 2, 2), standings.map { it.rank })
    }

    @Test
    fun `a team with points but the same time as another shares its rank`() {
        val standings = TeamRanking.rank(listOf(score(1, "Alpha", 10, 7), score(2, "Beta", 10, 7)))

        assertEquals(listOf(1, 1), standings.map { it.rank })
    }

    @Test
    fun `an empty field ranks to nothing`() {
        assertEquals(emptyList<TeamStanding>(), TeamRanking.rank(emptyList()))
        assertNull(TeamRanking.leader(emptyList()))
    }

    @Test
    fun `the leader is the top team once anyone has points`() {
        assertEquals(
            "Beta",
            TeamRanking.leader(
                    TeamRanking.rank(listOf(score(1, "Alpha", 10, 1), score(2, "Beta", 20, 2)))
                )
                ?.name,
        )
        assertNull(TeamRanking.leader(TeamRanking.rank(listOf(score(1, "Alpha", 0, null)))))
    }
}
