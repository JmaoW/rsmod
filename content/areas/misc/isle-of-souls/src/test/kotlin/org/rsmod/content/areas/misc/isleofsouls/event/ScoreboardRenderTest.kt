package org.rsmod.content.areas.misc.isleofsouls.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson

class ScoreboardRenderTest {
    private val rows =
        listOf(
            ScoreboardRow(1, "Alpha", 340, 12, listOf("Alice", "Bob")),
            ScoreboardRow(2, "Beta & <Co>", 290, 10, listOf("Carol")),
        )

    private fun data(frozenAt: Long? = null, rows: List<ScoreboardRow> = this.rows) =
        ScoreboardData(frozenAt, updatedAt = 1_759_650_000_000, rows = rows)

    @Test
    fun `json lists every team with its rank, score and players`() {
        val root = teamJson.readTree(ScoreboardRender.json(data()))

        assertFalse(root.get("frozen").booleanValue())
        assertEquals(1_759_650_000_000, root.get("updatedAt").longValue())
        val teams = root.get("teams")
        assertEquals(2, teams.size())
        assertEquals("Alpha", teams[0].get("name").asText())
        assertEquals(1, teams[0].get("rank").intValue())
        assertEquals(340, teams[0].get("points").intValue())
        assertEquals(12, teams[0].get("tasks").intValue())
        assertEquals(listOf("Alice", "Bob"), teams[0].get("players").map { it.asText() })
    }

    @Test
    fun `json says when the standings are frozen`() {
        val root = teamJson.readTree(ScoreboardRender.json(data(frozenAt = 1_759_650_500_000)))

        assertTrue(root.get("frozen").booleanValue())
        assertEquals(1_759_650_500_000, root.get("frozenAt").longValue())
    }

    @Test
    fun `html shows the teams in order and refreshes itself`() {
        val html = ScoreboardRender.html(data())

        assertTrue(html.contains("<meta http-equiv=\"refresh\" content=\"10\">"))
        assertTrue(html.indexOf("Alpha") < html.indexOf("Beta"))
        assertTrue(html.contains("<td class=\"num\">340</td><td class=\"num\">12</td>"))
        assertTrue(html.contains("Alice, Bob"))
        assertFalse(html.contains("Standings frozen at"))
    }

    @Test
    fun `html escapes team and player names`() {
        val html = ScoreboardRender.html(data())

        assertTrue(html.contains("Beta &amp; &lt;Co&gt;"))
        assertFalse(html.contains("<Co>"))
    }

    @Test
    fun `html shows a frozen banner`() {
        assertTrue(
            ScoreboardRender.html(data(frozenAt = 1_759_650_500_000))
                .contains("Standings frozen at")
        )
    }

    @Test
    fun `html copes with no teams`() {
        assertTrue(ScoreboardRender.html(data(rows = emptyList())).contains("No teams yet."))
    }
}
