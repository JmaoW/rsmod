package org.rsmod.content.areas.misc.isleofsouls.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The exact wording of every server-wide announcement and every event admin reply. */
class AnnouncementMessagesTest {
    private fun b(text: Any) = "<col=147194>$text</col>"

    private fun g(text: Any) = "<col=1a6b1a>$text</col>"

    private fun r(text: Any) = "<col=8b1a1a>$text</col>"

    private val league = b("League:")

    @Test
    fun `who names the player and their team`() {
        assertEquals("${b("Alice")} of ${b("Alpha")}", AnnouncementMessages.who("Alice", "Alpha"))
        assertEquals(b("Alice"), AnnouncementMessages.who("Alice", null))
    }

    @Test
    fun `tasks`() {
        val who = AnnouncementMessages.who("Alice", "Alpha")
        assertEquals(
            "$league $who has completed the elite task: ${b("Reach level 99 Mining")}.",
            AnnouncementMessages.taskCompleted(who, "elite", "Reach level 99 Mining"),
        )
        assertEquals(
            "$league $who is the first to complete the hard task: ${b("Mine Adamantite")}.",
            AnnouncementMessages.firstToComplete(who, "hard", "Mine Adamantite"),
        )
    }

    @Test
    fun `levels, deaths and drops`() {
        val who = AnnouncementMessages.who("Alice", "Alpha")
        assertEquals(
            "$league $who has reached level ${b(99)} in ${b("Mining")}.",
            AnnouncementMessages.levelReached(who, 99, "Mining"),
        )
        assertEquals("$league $who has died.", AnnouncementMessages.death(who))
        assertEquals(
            "$league $who has received a rare drop: ${b("Abyssal whip")}.",
            AnnouncementMessages.drop(who, "Abyssal whip", 1),
        )
        assertEquals(
            "$league $who has received a rare drop: ${b(3)} x ${b("Dragon bones")}.",
            AnnouncementMessages.drop(who, "Dragon bones", 3),
        )
    }

    @Test
    fun `the lead`() {
        assertEquals(
            "$league ${b("Alpha")} has taken the lead with ${b("1,340 League Points")}.",
            AnnouncementMessages.leadTaken("Alpha", 1340),
        )
    }

    @Test
    fun `freezing announces itself then the top teams`() {
        val top =
            TeamRanking.rank(
                listOf(TeamScore(1, "Alpha", 340, 12, 5), TeamScore(2, "Beta", 290, 10, 9))
            )

        assertEquals(
            listOf(
                "$league The standings are now frozen.",
                "1. ${b("Alpha")} - ${b("340 League Points")}",
                "2. ${b("Beta")} - ${b("290 League Points")}",
            ),
            AnnouncementMessages.frozen(top),
        )
        assertEquals("$league The standings are live again.", AnnouncementMessages.unfrozen())
    }

    @Test
    fun `admin replies`() {
        assertEquals(
            "${g("Frozen:")} the standings are locked. Run ${b("::leagueunfreeze")} to make them live again.",
            EventMessages.frozen(),
        )
        assertEquals(
            "${r("Not changed:")} the standings are already frozen.",
            EventMessages.alreadyFrozen(),
        )
        assertEquals("${g("Unfrozen:")} the standings are live again.", EventMessages.unfrozen())
        assertEquals(
            "${r("Not changed:")} the standings are not frozen.",
            EventMessages.notFrozen(),
        )
        assertEquals(
            "The standings were frozen; the freeze was lifted too.",
            EventMessages.freezeCleared(),
        )
        assertEquals(
            "${g("Reloaded:")} the announcement settings.",
            EventMessages.announcementsReloaded(),
        )
        assertEquals(
            "${r("Not reloaded:")} unknown setting \"death\". The current settings are unchanged.",
            EventMessages.announcementsNotReloaded("unknown setting \"death\""),
        )
    }

    @Test
    fun `every message is plain ascii so the client can draw it`() {
        val who = AnnouncementMessages.who("Alice", "Alpha")
        val top = TeamRanking.rank(listOf(TeamScore(1, "Alpha", 340, 12, 5)))
        val messages =
            listOf(
                AnnouncementMessages.taskCompleted(who, "elite", "Task"),
                AnnouncementMessages.firstToComplete(who, "hard", "Task"),
                AnnouncementMessages.levelReached(who, 99, "Mining"),
                AnnouncementMessages.death(who),
                AnnouncementMessages.drop(who, "Abyssal whip", 2),
                AnnouncementMessages.leadTaken("Alpha", 340),
                AnnouncementMessages.unfrozen(),
                EventMessages.frozen(),
                EventMessages.alreadyFrozen(),
                EventMessages.unfrozen(),
                EventMessages.notFrozen(),
                EventMessages.freezeCleared(),
                EventMessages.announcementsReloaded(),
                EventMessages.announcementsNotReloaded("reason"),
            ) + AnnouncementMessages.frozen(top)
        for (message in messages) {
            assertTrue(message.all { it.code < 128 }) { "Non-ASCII character in: $message" }
        }
    }
}
