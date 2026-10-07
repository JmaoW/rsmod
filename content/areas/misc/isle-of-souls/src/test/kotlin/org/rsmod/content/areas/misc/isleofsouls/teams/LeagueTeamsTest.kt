package org.rsmod.content.areas.misc.isleofsouls.teams

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

class LeagueTeamsTest {
    @TempDir lateinit var dir: Path

    private var now = 1_000L
    private val players = PlayerList()

    private fun teams(): LeagueTeams = LeagueTeams(players) { now }

    private fun loaded(): LeagueTeams = teams().also { it.load(dir) }

    private fun roster(vararg players: String) {
        val names = players.joinToString(",") { "\"$it\"" }
        dir.resolve("teams.json")
            .writeText("""{"teams":[{"id":1,"name":"Alpha","players":[$names]}]}""")
    }

    private fun login(name: String): Player {
        val player = Player()
        player.username = name
        players[checkNotNull(players.nextFreeSlot())] = player
        return player
    }

    @Test
    fun `loading with no files starts empty and writes nothing`() {
        val teams = loaded()

        assertTrue(teams.teams.isEmpty())
        assertFalse(dir.resolve("teams.json").exists())
        assertFalse(dir.resolve("team-progress.json").exists())
    }

    @Test
    fun `the roster and progress survive a restart`() {
        roster("alice", "bob")
        val first = loaded()
        val team = checkNotNull(first.team(1))
        first.assign("carol", 1)
        assertTrue(first.complete(team, 303))
        first.incrementCounter(team, "isle_count_tin_ore", cap = 5)

        val second = loaded()
        val reloaded = checkNotNull(second.team(1))

        assertEquals(setOf("alice", "bob", "carol"), reloaded.members)
        assertTrue(second.isComplete(reloaded, 303))
        assertEquals(1, second.counter(reloaded, "isle_count_tin_ore"))
    }

    @Test
    fun `completion time comes from the clock and a repeat does not change it`() {
        roster("alice")
        val teams = loaded()
        val team = checkNotNull(teams.team(1))

        now = 5_000
        assertTrue(teams.complete(team, 303))
        now = 9_000
        assertFalse(teams.complete(team, 303))

        assertEquals(mapOf(303 to 5_000L), teams.completed(team))
    }

    @Test
    fun `a roster file that cannot be read stops loading and is left alone`() {
        val file = dir.resolve("teams.json")
        file.writeText("{ not json")

        val error = assertThrows(IllegalStateException::class.java) { teams().load(dir) }

        assertTrue(error.message!!.contains("teams.json"))
        assertEquals("{ not json", file.readText())
    }

    @Test
    fun `a progress file that cannot be read stops loading and is left alone`() {
        val file = dir.resolve("team-progress.json")
        file.writeText("garbage")

        assertThrows(IllegalStateException::class.java) { teams().load(dir) }

        assertEquals("garbage", file.readText())
    }

    @Test
    fun `a hand edit made while running is not overwritten by a command`() {
        roster("alice")
        val teams = loaded()

        roster("alice", "bob")
        Files.setLastModifiedTime(
            dir.resolve("teams.json"),
            FileTime.fromMillis(System.currentTimeMillis() + 60_000),
        )

        assertThrows(IllegalStateException::class.java) { teams.assign("carol", 1) }
        assertNull(teams.teamOf("carol"))
        assertTrue(dir.resolve("teams.json").readText().contains("bob"))

        // After a reload the edit is live and commands work again.
        assertNull(teams.reloadRoster())
        assertEquals(1, teams.teamOf("bob")?.id)
        teams.assign("carol", 1)
        assertEquals(1, teams.teamOf("carol")?.id)
        assertTrue(dir.resolve("teams.json").readText().contains("carol"))
    }

    @Test
    fun `a bad reload is refused and keeps the current roster`() {
        roster("alice")
        val teams = loaded()

        dir.resolve("teams.json").writeText("""{"teams":[{"id":1,"players":["x"]},{"id":1}]}""")

        val problem = teams.reloadRoster()

        assertTrue(problem!!.contains("more than once"))
        assertEquals(1, teams.teamOf("alice")?.id)
    }

    @Test
    fun `reloading the roster never touches progress`() {
        roster("alice")
        val teams = loaded()
        teams.complete(checkNotNull(teams.team(1)), 303)

        roster("bob")
        assertNull(teams.reloadRoster())

        assertTrue(teams.isComplete(Team(1, "Alpha"), 303))
        assertNull(teams.teamOf("alice"))
    }

    @Test
    fun `resetting progress backs up the old file first`() {
        roster("alice")
        val teams = loaded()
        val team = checkNotNull(teams.team(1))
        teams.complete(team, 303)
        teams.complete(team, 304)

        assertEquals(2, teams.resetProgress(1))

        assertTrue(teams.completed(team).isEmpty())
        val backups = dir.listDirectoryEntries("team-progress.*.bak.json")
        assertEquals(1, backups.size)
        assertTrue(backups.single().readText().contains("303"))
        assertFalse(loaded().isComplete(team, 303))
    }

    @Test
    fun `resetting everything clears every team`() {
        roster("alice")
        val teams = loaded()
        teams.createTeam(2, "Beta")
        teams.complete(checkNotNull(teams.team(1)), 1)
        teams.complete(checkNotNull(teams.team(2)), 2)

        assertEquals(2, teams.resetAllProgress())

        assertTrue(teams.completed(checkNotNull(teams.team(1))).isEmpty())
        assertTrue(teams.completed(checkNotNull(teams.team(2))).isEmpty())
    }

    @Test
    fun `players are matched to their team by login name`() {
        roster("alice", "bob")
        val teams = loaded()
        val alice = login("Alice")
        val bob = login("bob")
        val carol = login("carol")

        assertEquals(1, teams.teamOf(alice)?.id)
        assertNull(teams.teamOf(carol))
        assertEquals(listOf(alice, bob), teams.onlineMembers(checkNotNull(teams.team(1))))
        val others = teams.memberNames(checkNotNull(teams.team(1)), except = alice)
        assertEquals(listOf("Bob"), others.map { it.name })
        assertTrue(others.single().online)
    }

    @Test
    fun `names come from the roster then the in game name then title case`() {
        dir.resolve("teams.json")
            .writeText(
                """{"teams":[{"id":1,"players":[{"login":"mcgee","name":"McGee"},"iron_bob","zed_x"]}]}"""
            )
        val teams = loaded()
        val mcgee = login("mcgee")
        mcgee.displayName = "Something Else"
        val bob = login("iron_bob")
        bob.displayName = "Iron Bobby"

        assertEquals("McGee", teams.nameOf("mcgee"))
        assertEquals("McGee", teams.nameOf(mcgee))
        assertEquals("Iron Bobby", teams.nameOf("IRON_BOB"))
        assertEquals("Iron Bobby", teams.nameOf(bob))
        assertEquals("Zed X", teams.nameOf("zed_x"))
        assertEquals("Nobody Here", teams.nameOf("nobody_here"))

        val labels = teams.memberNames(checkNotNull(teams.team(1)))
        assertEquals(listOf("McGee", "Iron Bobby", "Zed X"), labels.map { it.name })
        assertEquals(listOf(true, true, false), labels.map { it.online })
    }

    @Test
    fun `an in memory instance never touches the disk`() {
        val teams = teams()
        teams.createTeam(1, "Solo")
        teams.assign("alice", 1)
        teams.complete(checkNotNull(teams.team(1)), 303)

        assertTrue(dir.listDirectoryEntries().isEmpty())
    }
}
