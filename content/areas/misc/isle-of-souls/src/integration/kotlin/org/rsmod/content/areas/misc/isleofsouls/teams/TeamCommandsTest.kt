package org.rsmod.content.areas.misc.isleofsouls.teams

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.type.mod.ModLevelTypeList

/** Runs the team commands the way a player types them and checks the chat they get back. */
class TeamCommandsTest {
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val tracker: LeagueTaskTracker,
        val commands: CheatCommandMap,
        val modLevels: ModLevelTypeList,
    )

    private fun b(text: Any) = "<col=147194>$text</col>"

    private fun g(text: Any) = "<col=1a6b1a>$text</col>"

    private fun o(text: Any) = "<col=7a7467>$text</col>"

    private fun Player.chat(): List<String> =
        (client as CaptureClient).filterIsInstance<MessageGame>().map { it.message }

    private fun Player.clearChat() = (client as CaptureClient).clearOutgoing()

    /**
     * alice (admin) and bob are on Alpha, carol is on Beta with the offline erin, dave has no team.
     * Alpha has completed one medium task (30 points).
     */
    private fun GameTestState.runCommandTest(
        body: GameTestScope.(Deps, Player, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(Deps::class, TeamsModule, LeagueTeamCommands::class) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            for ((member, name) in listOf(alice to "Alice", bob to "Bob", carol to "Carol")) {
                member.displayName = name
            }
            dave.displayName = "Dave"
            for (member in listOf(alice, bob, carol, dave)) {
                member.modLevel = deps.modLevels.default()
            }
            alice.modLevel = deps.modLevels.values.first { it.internalName == "admin" }

            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("carol", 2)
            deps.teams.assign("erin", 2)
            deps.tracker.complete(alice, 306)
            for (member in listOf(alice, bob, carol, dave)) {
                member.clearChat()
            }
            body(deps, alice, bob, carol, dave)
        }

    @Test
    fun GameTestState.`teams works for any player and ranks teams by points`() =
        runCommandTest { deps, _, _, _, dave ->
            assertTrue(deps.commands.execute(dave, "teams", emptyList()))

            assertEquals(
                listOf(
                    b("League Teams"),
                    "${b("Alpha")} - ${b(30)} pts, ${b(1)} task - ${g("Alice")}, ${g("Bob")}",
                    "${b("Beta")} - ${b(0)} pts, ${b(0)} tasks - ${g("Carol")}, ${o("Erin")}",
                ),
                dave.chat(),
            )
        }

    @Test
    fun GameTestState.`myteam shows the team and its teammates without online status`() =
        runCommandTest { deps, alice, _, _, dave ->
            deps.commands.execute(alice, "myteam", emptyList())
            deps.commands.execute(dave, "myteam", emptyList())

            assertEquals(
                listOf(
                    "Your team, ${b("Alpha")}, has earned ${b(30)} League Points from ${b(1)} task.",
                    "Teammates: ${b("Bob")}.",
                ),
                alice.chat(),
            )
            assertEquals(
                listOf("You are not on a league team. Ask an organiser to add you."),
                dave.chat(),
            )
        }

    @Test
    fun GameTestState.`an admin moving a player tells both of them`() =
        runCommandTest { deps, alice, bob, _, _ ->
            deps.commands.execute(alice, "teamset", listOf("2", "bob"))

            assertEquals(
                listOf(
                    "${g("Moved:")} ${b("Bob")} is now on ${b("Beta")} (team 2), " +
                        "leaving ${b("Alpha")} (team 1)."
                ),
                alice.chat(),
            )
            assertEquals(
                listOf(
                    "You are now on ${b("Beta")} with ${b("Carol")}, ${b("Erin")}. " +
                        "Your league progress now matches your team's."
                ),
                bob.chat(),
            )
            assertEquals(2, deps.teams.teamOf(bob)?.id)
        }

    @Test
    fun GameTestState.`admin commands do nothing for a regular player`() =
        runCommandTest { deps, _, _, _, dave ->
            deps.commands.execute(dave, "teamset", listOf("1", "dave"))
            deps.commands.execute(dave, "teamreset", listOf("all", "confirm"))

            assertEquals(emptyList<String>(), dave.chat())
            assertEquals(null, deps.teams.teamOf(dave))
        }

    @Test
    fun GameTestState.`resetting asks first then clears and reports`() =
        runCommandTest { deps, alice, _, _, _ ->
            deps.commands.execute(alice, "teamreset", listOf("1"))
            deps.commands.execute(alice, "teamreset", listOf("1", "confirm"))

            assertEquals(
                listOf(
                    "This erases ${b("Alpha")}'s tasks, points and counters. " +
                        "Type ${b("::teamreset 1 confirm")} to do it.",
                    "${g("Cleared:")} ${b(1)} task for ${b("Alpha")} (team 1). " +
                        "The old progress was backed up first.",
                ),
                alice.chat(),
            )
            assertEquals(0, deps.tracker.taskCount(checkNotNull(deps.teams.team(1))))
        }

    @Test
    fun GameTestState.`bad arguments get a usage line`() = runCommandTest { deps, alice, _, _, _ ->
        deps.commands.execute(alice, "teamset", listOf("not-a-number", "bob"))

        assertEquals(
            listOf("${b("Usage:")} ::teamset team-id player - example: ::teamset 3 some player"),
            alice.chat(),
        )
    }
}
