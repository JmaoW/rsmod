package org.rsmod.content.areas.misc.isleofsouls.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LeagueChatTest {
    @Test
    fun `colours wrap text in colour tags`() {
        assertEquals("<col=147194>x</col>", LeagueChat.blue("x"))
        assertEquals("<col=1a6b1a>x</col>", LeagueChat.green("x"))
        assertEquals("<col=8b1a1a>x</col>", LeagueChat.red("x"))
        assertEquals("<col=7a7467>x</col>", LeagueChat.grey("x"))
    }

    @Test
    fun `numbers get thousands separators`() {
        assertEquals("<col=147194>340</col>", LeagueChat.number(340))
        assertEquals("<col=147194>1,240</col>", LeagueChat.number(1240))
        assertEquals("<col=147194>25,000</col>", LeagueChat.number(25_000))
    }

    @Test
    fun `points are one blue phrase with the singular for one`() {
        assertEquals("<col=147194>340 League Points</col>", LeagueChat.points(340))
        assertEquals("<col=147194>1,240 League Points</col>", LeagueChat.points(1240))
        assertEquals("<col=147194>1 League Point</col>", LeagueChat.points(1))
        assertEquals("<col=147194>0 League Points</col>", LeagueChat.points(0))
    }

    @Test
    fun `counts colour the number and pluralise the noun`() {
        assertEquals("<col=147194>12</col> tasks", LeagueChat.counted(12, "task"))
        assertEquals("<col=147194>1</col> task", LeagueChat.counted(1, "task"))
        assertEquals("<col=147194>0</col> tasks", LeagueChat.counted(0, "task"))
    }

    @Test
    fun `task progress colours only the label`() {
        assertEquals(
            "<col=147194>Task Progress:</col> Mine 30 Iron Ore: 15/30",
            LeagueChat.taskProgress("Mine 30 Iron Ore", 15, 30),
        )
    }

    @Test
    fun `commands are highlighted wherever they appear`() {
        assertEquals(
            "Run <col=147194>::teamreload</col> first, or <col=147194>::teams</col>.",
            LeagueChat.commands("Run ::teamreload first, or ::teams."),
        )
        assertEquals("No commands here.", LeagueChat.commands("No commands here."))
    }

    @Test
    fun `login names become title case display names`() {
        assertEquals("Alice", LeagueChat.titleCase("alice"))
        assertEquals("Some Name", LeagueChat.titleCase("some_name"))
        assertEquals("Iron Bob", LeagueChat.titleCase("iron-bob"))
        assertEquals("Mixed Case", LeagueChat.titleCase("  MIXED   case "))
        assertEquals("", LeagueChat.titleCase(""))
    }
}
