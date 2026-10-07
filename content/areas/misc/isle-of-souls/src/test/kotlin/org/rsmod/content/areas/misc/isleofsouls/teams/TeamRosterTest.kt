package org.rsmod.content.areas.misc.isleofsouls.teams

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamRosterTest {
    private val sample =
        """
        {
          "teams": [
            { "id": 1, "name": "Alpha", "players": ["Alice", " bob "] },
            { "id": 2, "players": ["carol"] }
          ]
        }
        """
            .trimIndent()

    @Test
    fun `parse reads teams and normalises player names`() {
        val roster = TeamRoster.parse(sample)

        assertEquals(listOf(1, 2), roster.teams.map(Team::id))
        assertEquals("Alpha", roster[1]?.name)
        assertEquals("Team 2", roster[2]?.name)
        assertEquals(setOf("alice", "bob"), roster[1]?.members)
        assertEquals(1, roster.teamOf("ALICE")?.id)
        assertEquals(2, roster.teamOf("Carol ")?.id)
        assertNull(roster.teamOf("dave"))
    }

    @Test
    fun `an empty roster parses`() {
        assertTrue(TeamRoster.parse("""{ "teams": [] }""").teams.isEmpty())
    }

    @Test
    fun `a player on two teams is rejected naming both teams`() {
        val json = """{"teams":[{"id":1,"players":["a"]},{"id":2,"players":["A"]}]}"""
        val error = assertThrows(IllegalArgumentException::class.java) { TeamRoster.parse(json) }
        assertTrue(error.message!!.contains("team 1") && error.message!!.contains("team 2"))
    }

    @Test
    fun `a repeated team id is rejected`() {
        val json = """{"teams":[{"id":1},{"id":1}]}"""
        assertThrows(IllegalArgumentException::class.java) { TeamRoster.parse(json) }
    }

    @Test
    fun `bad shapes are rejected with an argument error`() {
        val bad =
            listOf(
                """{}""",
                """{"teams": {}}""",
                """{"teams":[{"name":"no id"}]}""",
                """{"teams":[{"id":"1"}]}""",
                """{"teams":[{"id":1,"players":"alice"}]}""",
                """{"teams":[{"id":1,"players":[""]}]}""",
                """{"teams":[{"id":1,"players":[5]}]}""",
            )
        for (json in bad) {
            assertThrows(IllegalArgumentException::class.java, { TeamRoster.parse(json) }, json)
        }
    }

    @Test
    fun `assign moves a player and reports the team they left`() {
        val roster = TeamRoster.parse(sample)

        val left = roster.assign("Alice", 2)

        assertEquals(1, left?.id)
        assertEquals(2, roster.teamOf("alice")?.id)
        assertEquals(setOf("bob"), roster[1]?.members)
        assertEquals(setOf("carol", "alice"), roster[2]?.members)
    }

    @Test
    fun `assign to an unknown team fails and changes nothing`() {
        val roster = TeamRoster.parse(sample)

        assertThrows(IllegalArgumentException::class.java) { roster.assign("alice", 9) }

        assertEquals(1, roster.teamOf("alice")?.id)
    }

    @Test
    fun `remove takes a player off their team`() {
        val roster = TeamRoster.parse(sample)

        assertEquals(1, roster.remove("BOB")?.id)

        assertNull(roster.teamOf("bob"))
        assertNull(roster.remove("bob"))
    }

    @Test
    fun `json round trips`() {
        val roster = TeamRoster.parse(sample)

        val again = TeamRoster.parse(roster.toJson())

        assertEquals(roster.teams.map(Team::toString), again.teams.map(Team::toString))
    }

    @Test
    fun `a player can be written as an object with a display name`() {
        val roster =
            TeamRoster.parse(
                """
                {"teams":[{"id":1,"players":["alice",{"login":"Mcgee","name":"McGee"},{"login":"dave"}]}]}
                """
                    .trimIndent()
            )

        assertEquals(setOf("alice", "mcgee", "dave"), roster[1]?.members)
        assertEquals("McGee", roster.displayName("MCGEE"))
        assertNull(roster.displayName("alice"))
        assertNull(roster.displayName("dave"))
    }

    @Test
    fun `display names survive a round trip and only write objects where needed`() {
        val roster = TeamRoster.parse(sample)
        roster.assign("iron_bob", 1, displayName = "iron Bob")

        val json = roster.toJson()
        val again = TeamRoster.parse(json)

        assertEquals("iron Bob", again.displayName("iron_bob"))
        assertNull(again.displayName("alice"))
        assertTrue(json.contains("\"alice\""))
        assertTrue(json.contains("\"name\" : \"iron Bob\""))
    }

    @Test
    fun `a display name follows a player who moves team but not one who is removed`() {
        val roster = TeamRoster.parse(sample)
        roster.assign("alice", 1, displayName = "ALice")

        roster.assign("alice", 2)
        assertEquals("ALice", roster.displayName("alice"))

        roster.remove("alice")
        assertNull(roster.displayName("alice"))
    }

    @Test
    fun `bad player entries are rejected`() {
        val bad =
            listOf(
                """{"teams":[{"id":1,"players":[{"name":"No Login"}]}]}""",
                """{"teams":[{"id":1,"players":[{"login":"","name":"x"}]}]}""",
                """{"teams":[{"id":1,"players":[{"login":"a","name":5}]}]}""",
            )
        for (json in bad) {
            assertThrows(IllegalArgumentException::class.java, { TeamRoster.parse(json) }, json)
        }
    }
}
