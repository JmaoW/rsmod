package org.rsmod.content.areas.misc.isleofsouls.teams

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The exact wording of every team message. A change here is a change to what players read. */
class TeamMessagesTest {
    private fun b(text: Any) = "<col=147194>$text</col>"

    private fun g(text: Any) = "<col=1a6b1a>$text</col>"

    private fun r(text: Any) = "<col=8b1a1a>$text</col>"

    private fun o(text: Any) = "<col=7a7467>$text</col>"

    @Test
    fun `login names the team and its partners without mentioning sharing`() {
        assertEquals(
            "Welcome to the league. You are on ${b("Alpha")} with ${b("Bob")}.",
            TeamMessages.login("Alpha", listOf("Bob")),
        )
        assertEquals(
            "Welcome to the league. You are on ${b("Alpha")} with ${b("Bob")}, ${b("Carol")}.",
            TeamMessages.login("Alpha", listOf("Bob", "Carol")),
        )
        assertEquals(
            "Welcome to the league. You are on ${b("Alpha")}.",
            TeamMessages.login("Alpha", emptyList()),
        )
    }

    @Test
    fun `myteam summary and teammates`() {
        assertEquals(
            "Your team, ${b("Alpha")}, has earned ${b("1,340")} League Points " +
                "from ${b(12)} tasks.",
            TeamMessages.teamSummary("Alpha", 1340, 12),
        )
        assertEquals(
            "Your team, ${b("Alpha")}, has earned ${b(1)} League Point from ${b(1)} task.",
            TeamMessages.teamSummary("Alpha", 1, 1),
        )
        assertEquals("Teammates: ${b("Bob")}.", TeamMessages.teammates(listOf("Bob")))
        assertEquals(
            "Teammates: ${b("Bob")}, ${b("Carol")}.",
            TeamMessages.teammates(listOf("Bob", "Carol")),
        )
        assertEquals("You have no teammates yet.", TeamMessages.teammates(emptyList()))
        assertEquals(
            "You are not on a league team. Ask an organiser to add you.",
            TeamMessages.NOT_ON_TEAM,
        )
    }

    @Test
    fun `teams lists a header and one line per team with no team numbers`() {
        assertEquals(b("League Teams"), TeamMessages.TEAMS_HEADER)
        assertEquals(b("League Teams"), TeamMessages.teamsHeader(frozen = false))
        assertEquals(
            "${b("League Teams")} ${r("(frozen)")}",
            TeamMessages.teamsHeader(frozen = true),
        )
        assertEquals(
            "${b("Alpha")} - ${b(340)} pts, ${b(12)} tasks - ${g("Alice")}, ${o("Bob")}",
            TeamMessages.teamLine(
                "Alpha",
                340,
                12,
                listOf(MemberName("Alice", online = true), MemberName("Bob", online = false)),
            ),
        )
        assertEquals(
            "${b("Gamma")} - ${b(0)} pts, ${b(0)} tasks - ${o("no players")}",
            TeamMessages.teamLine("Gamma", 0, 0, emptyList()),
        )
        assertEquals(
            "${b("Solo")} - ${b(10)} pts, ${b(1)} task - ${g("Alice")}",
            TeamMessages.teamLine("Solo", 10, 1, listOf(MemberName("Alice", online = true))),
        )
        assertEquals("There are no league teams yet.", TeamMessages.NO_TEAMS)
    }

    @Test
    fun `joining and leaving notices`() {
        assertEquals(
            "You are now on ${b("Beta")} with ${b("Carol")}. " +
                "Your league progress now matches your team's.",
            TeamMessages.joined("Beta", listOf("Carol")),
        )
        assertEquals(
            "You are now on ${b("Beta")}. Your league progress now matches your team's.",
            TeamMessages.joined("Beta", emptyList()),
        )
        assertEquals("You are no longer on a league team.", TeamMessages.LEFT_TEAM)
    }

    @Test
    fun `roster changes`() {
        assertEquals(
            "${g("Created:")} ${b("The Miners")} (team 4).",
            TeamMessages.created("The Miners", 4, saved = true),
        )
        assertEquals(
            "${g("Added:")} ${b("Erin")} is now on ${b("Alpha")} (team 1).",
            TeamMessages.added("Erin", "Alpha", 1, saved = true),
        )
        assertEquals(
            "${g("Moved:")} ${b("Alice")} is now on ${b("Beta")} (team 3), " +
                "leaving ${b("Alpha")} (team 1).",
            TeamMessages.moved("Alice", "Beta", 3, "Alpha", 1, saved = true),
        )
        assertEquals(
            "${b("Alice")} is already on ${b("Alpha")} (team 1).",
            TeamMessages.alreadyOn("Alice", "Alpha", 1, saved = true),
        )
        assertEquals(
            "${g("Removed:")} ${b("Alice")} left ${b("Alpha")} (team 1). " +
                "They keep their current progress as their own.",
            TeamMessages.removed("Alice", "Alpha", 1, saved = true),
        )
        assertEquals("${b("Alice")} is not on a team.", TeamMessages.notOnATeam("Alice"))
    }

    @Test
    fun `a change that could not be saved says so in red`() {
        assertEquals(
            "${g("Created:")} ${b("The Miners")} (team 4). " +
                r("Not saved to teams.json; see the server log."),
            TeamMessages.created("The Miners", 4, saved = false),
        )
    }

    @Test
    fun `refusals are red, lower case and highlight commands`() {
        assertEquals(
            "${r("Not changed:")} teams.json was edited outside the server. " +
                "Run ${b("::teamreload")} first.",
            TeamMessages.refused(
                "Not changed:",
                "teams.json was edited outside the server. Run ::teamreload first.",
            ),
        )
        assertEquals(
            "${r("Not changed:")} team 4 already exists.",
            TeamMessages.refused("Not changed:", "Team 4 already exists."),
        )
        assertEquals(
            "${r("Not changed:")} there is no team 9. Make it with ${b("::teamnew")} first.",
            TeamMessages.refused("Not changed:", "there is no team 9. Make it with ::teamnew first"),
        )
    }

    @Test
    fun `reloading`() {
        assertEquals(
            "${g("Reloaded:")} ${b(3)} teams; ${b(5)} online players synced.",
            TeamMessages.reloaded(3, 5),
        )
        assertEquals(
            "${g("Reloaded:")} ${b(1)} team; ${b(1)} online player synced.",
            TeamMessages.reloaded(1, 1),
        )
        assertEquals(
            "${r("Not reloaded:")} team id 1 is used more than once. The current teams are unchanged.",
            TeamMessages.notReloaded("Team id 1 is used more than once."),
        )
    }

    @Test
    fun `resetting asks first and then reports`() {
        assertEquals(
            "This erases ${b("Alpha")}'s tasks, points and counters. " +
                "Type ${b("::teamreset 1 confirm")} to do it.",
            TeamMessages.resetPrompt("Alpha", 1),
        )
        assertEquals(
            "This erases team 7's tasks, points and counters. " +
                "Type ${b("::teamreset 7 confirm")} to do it.",
            TeamMessages.resetPrompt(null, 7),
        )
        assertEquals(
            "This erases ${r("every")} team's tasks, points and counters. " +
                "Type ${b("::teamreset all confirm")} to do it.",
            TeamMessages.resetAllPrompt(),
        )
        assertEquals(
            "${g("Cleared:")} ${b(12)} tasks for ${b("Alpha")} (team 1). " +
                "The old progress was backed up first.",
            TeamMessages.cleared(12, "Alpha", 1),
        )
        assertEquals(
            "${g("Cleared:")} ${b(3)} tasks for team 7. The old progress was backed up first.",
            TeamMessages.cleared(3, null, 7),
        )
        assertEquals(
            "${g("Cleared:")} ${b(31)} tasks across every team. " +
                "The old progress was backed up first.",
            TeamMessages.clearedAll(31),
        )
    }

    @Test
    fun `league reset`() {
        assertEquals(
            "${r("Not cleared:")} your tasks and points belong to ${b("Alpha")}. " +
                "Your relic choices were cleared; use ${b("::teamreset 1 confirm")} " +
                "to clear the team.",
            TeamMessages.leagueResetBlocked("Alpha", 1),
        )
        assertEquals(
            "${g("Cleared:")} your league tasks, points and relic choices.",
            TeamMessages.leagueResetDone(),
        )
    }

    @Test
    fun `usage lines`() {
        assertEquals(
            "${b("Usage:")} ::teamnew id name - example: ::teamnew 3 The Miners",
            TeamMessages.usage("::teamnew id name", "::teamnew 3 The Miners"),
        )
        assertEquals(
            "${b("Usage:")} ::instance type, or ::instance leave",
            TeamMessages.usage("::instance type, or ::instance leave"),
        )
    }

    @Test
    fun `instances`() {
        assertEquals("You enter your team's ${b("Patch")} instance.", TeamMessages.entered("Patch"))
        assertEquals("You leave the instance.", TeamMessages.left())
        assertEquals("You are not in an instance.", TeamMessages.notInInstance())
        assertEquals(
            "${r("Not entered:")} you are already in that instance.",
            TeamMessages.notEntered("you are already in that instance"),
        )
        assertEquals("${b("Live instances")} - 2", TeamMessages.instancesHeader(2))
        assertEquals(
            "${b("Alpha")} (team 1) - ${b("Patch")}: ${g("Alice")}, ${g("Bob")}",
            TeamMessages.instanceLine("${b("Alpha")} (team 1)", "Patch", listOf("Alice", "Bob")),
        )
        assertEquals("There are no live instances.", TeamMessages.NO_INSTANCES)
    }

    @Test
    fun `every message is plain ascii so the client can draw it`() {
        val members = listOf(MemberName("Alice", online = true), MemberName("Bob", online = false))
        val messages =
            listOf(
                TeamMessages.login("Alpha", listOf("Bob")),
                TeamMessages.teamSummary("Alpha", 1340, 12),
                TeamMessages.teammates(listOf("Bob")),
                TeamMessages.teamLine("Alpha", 340, 12, members),
                TeamMessages.teamLine("Gamma", 0, 0, emptyList()),
                TeamMessages.joined("Beta", listOf("Carol")),
                TeamMessages.created("The Miners", 4, saved = false),
                TeamMessages.moved("Alice", "Beta", 3, "Alpha", 1, saved = true),
                TeamMessages.removed("Alice", "Alpha", 1, saved = true),
                TeamMessages.reloaded(3, 5),
                TeamMessages.notReloaded("Team id 1 is used more than once."),
                TeamMessages.refused(
                    "Not changed:",
                    "teams.json was edited. Run ::teamreload first.",
                ),
                TeamMessages.resetPrompt("Alpha", 1),
                TeamMessages.resetAllPrompt(),
                TeamMessages.cleared(12, "Alpha", 1),
                TeamMessages.clearedAll(31),
                TeamMessages.leagueResetBlocked("Alpha", 1),
                TeamMessages.leagueResetDone(),
                TeamMessages.usage("::teamnew id name", "::teamnew 3 The Miners"),
                TeamMessages.entered("Patch"),
                TeamMessages.instancesHeader(2),
                TeamMessages.instanceLine("Alpha (team 1)", "Patch", listOf("Alice")),
                TeamMessages.NOT_ON_TEAM,
                TeamMessages.NO_TEAMS,
                TeamMessages.LEFT_TEAM,
                TeamMessages.NO_INSTANCES,
            )
        for (message in messages) {
            assertTrue(message.all { it.code < 128 }) { "Non-ASCII character in: $message" }
        }
    }
}
