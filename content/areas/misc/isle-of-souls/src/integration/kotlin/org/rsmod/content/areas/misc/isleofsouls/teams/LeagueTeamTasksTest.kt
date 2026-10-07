package org.rsmod.content.areas.misc.isleofsouls.teams

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueSkillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskBitVars
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskVars
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

class LeagueTeamTasksTest {
    /** One shared [LeagueTeams] for the test and for the scripts it loads. */
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps @Inject constructor(val teams: LeagueTeams, val tracker: LeagueTaskTracker)

    private val dummyLoc =
        BoundLocInfo(
            coords = CoordGrid(0, 50, 50, 0, 0),
            entity = LocEntity(1, LocShape.CentrepieceStraight.id, LocAngle.West.id),
            layer = 0,
            width = 1,
            length = 1,
            forceApproachFlags = 0,
        )

    private fun Player.points() = vars[LeagueTaskVars.points_completed]

    private fun Player.claimed() = vars[LeagueTaskVars.points_claimed]

    private fun Player.totalTasks() = vars[LeagueTaskBitVars.total_tasks_completed]

    private fun GameTestState.runTeamTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(Deps::class, TeamsModule, LeagueSkillTasks::class) { deps ->
            val alice = player
            alice.username = "alice"
            alice.displayName = "Alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.displayName = "Bob"
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            body(deps, alice, bob)
        }

    @Test
    fun GameTestState.`a task completed by one teammate is completed for both`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303)

            for (member in listOf(alice, bob)) {
                assertTrue(deps.tracker.isComplete(member, 303))
                assertEquals(1 shl 15, member.vars[LeagueTaskVars.completed[9]])
                assertEquals(1, member.totalTasks())
                assertEquals(10, member.points())
                assertEquals(10, member.claimed())
            }
        }

    @Test
    fun GameTestState.`the player who did it and their teammate get different messages`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303)

            assertMessageSent(
                "Congratulations, you've completed an easy task: " +
                    "<col=147194>Mine some Copper Ore</col>.",
                alice,
            )
            assertMessageSent(
                "Congratulations, your teammate <col=147194>Alice</col> has completed an easy " +
                    "task: <col=147194>Mine some Copper Ore</col>.",
                bob,
            )
            val teamTotal =
                "You have earned <col=147194>10 League Points</col>. " +
                    "Your team now has <col=147194>10 League Points</col>."
            assertMessageSent(teamTotal, alice)
            assertMessageSent(teamTotal, bob)
            val popups = (bob.client as CaptureClient).filterIsInstance<RunClientScript>()
            assertEquals(1, popups.count { it.id == 3343 })
        }

    @Test
    fun GameTestState.`only the player who made progress is told about it`() =
        runTeamTest { deps, alice, bob ->
            val name = "Mine 50 Iron Ore"
            deps.tracker.addProgress(alice, 306, LeagueProgressVarps.iron_ore, 50)
            deps.tracker.addProgress(bob, 306, LeagueProgressVarps.iron_ore, 50)

            val label = "<col=147194>Task Progress:</col>"
            assertMessageSent("$label $name: 1/50", alice)
            assertMessageNotSent("$label $name: 2/50", alice)
            assertMessageSent("$label $name: 2/50", bob)
            assertMessageNotSent("$label $name: 1/50", bob)
            assertEquals(2, alice.vars[LeagueProgressVarps.iron_ore])
            assertEquals(2, bob.vars[LeagueProgressVarps.iron_ore])
        }

    @Test
    fun GameTestState.`the pop-up tells a teammate who completed the task`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303)

            fun popupBody(player: Player): String =
                (player.client as CaptureClient)
                    .filterIsInstance<RunClientScript>()
                    .single { it.id == 3343 }
                    .values[2]
                    as String

            assertEquals(
                "Task Completed: <col=ffffff>Mine some Copper Ore</col><br><br>" +
                    "Points Earned: <col=ffffff>10</col>",
                popupBody(alice),
            )
            assertEquals(
                "Task Completed: <col=ffffff>Mine some Copper Ore</col><br>" +
                    "Completed by <col=ffffff>Alice</col><br><br>" +
                    "Points Earned: <col=ffffff>10</col>",
                popupBody(bob),
            )
        }

    @Test
    fun GameTestState.`a display name written in the roster is used for the teammate`() =
        runTeamTest { deps, alice, bob ->
            deps.teams.assign("alice", 1, displayName = "ALice")
            deps.tracker.complete(alice, 303)

            assertMessageSent(
                "Congratulations, your teammate <col=147194>ALice</col> has completed an easy " +
                    "task: <col=147194>Mine some Copper Ore</col>.",
                bob,
            )
        }

    @Test
    fun GameTestState.`a repeat completion by the other teammate awards nothing`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303)
            deps.tracker.complete(bob, 303)

            assertEquals(10, alice.points())
            assertEquals(10, bob.points())
            assertEquals(1, bob.totalTasks())
        }

    @Test
    fun GameTestState.`mining by either teammate completes the skill task for both`() =
        runTeamTest { deps, alice, bob ->
            val copper = objTypes[objs.copper_ore]

            eventBus.publish(Mining.MineOre(bob, dummyLoc, copper))

            assertTrue(deps.tracker.isComplete(alice, 303))
            assertTrue(deps.tracker.isComplete(bob, 303))
            assertEquals(10, alice.points())
        }

    @Test
    fun GameTestState.`counters are a shared total across both teammates`() =
        runTeamTest { deps, alice, bob ->
            val tin = objTypes[objs.tin_ore]

            repeat(3) { eventBus.publish(Mining.MineOre(alice, dummyLoc, tin)) }
            eventBus.publish(Mining.MineOre(bob, dummyLoc, tin))
            assertFalse(deps.tracker.isComplete(alice, 1371))
            assertEquals(4, alice.vars[LeagueProgressVarps.tin_ore])
            assertEquals(4, bob.vars[LeagueProgressVarps.tin_ore])

            eventBus.publish(Mining.MineOre(bob, dummyLoc, tin))

            assertTrue(deps.tracker.isComplete(alice, 1371))
            assertTrue(deps.tracker.isComplete(bob, 1371))
            assertEquals(5, alice.vars[LeagueProgressVarps.tin_ore])
        }

    @Test
    fun GameTestState.`points are the sum of the team's completed tasks on both players`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303) // easy 10
            deps.tracker.complete(bob, 306) // medium 30
            deps.tracker.complete(alice, 309) // hard 80

            for (member in listOf(alice, bob)) {
                assertEquals(120, member.points())
                assertEquals(120, member.claimed())
                assertEquals(3, member.totalTasks())
            }
            val team = checkNotNull(deps.teams.team(1))
            assertEquals(120, deps.tracker.points(team))
            assertEquals(3, deps.tracker.taskCount(team))
        }

    @Test
    fun GameTestState.`a teammate who was offline gets the progress when synced`() =
        runTeamTest { deps, alice, _ ->
            deps.tracker.complete(alice, 303)
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.assign("carol", 1)

            assertEquals(0, carol.points())
            assertTrue(deps.tracker.syncPlayer(carol))

            assertTrue(deps.tracker.isComplete(carol, 303))
            assertEquals(1 shl 15, carol.vars[LeagueTaskVars.completed[9]])
            assertEquals(10, carol.points())
            assertEquals(1, carol.totalTasks())
        }

    @Test
    fun GameTestState.`joining a team replaces a player's own progress with the team's`() =
        runTeamTest { deps, alice, _ ->
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            deps.tracker.complete(dave, 303) // dave is not on a team yet
            assertEquals(10, dave.points())
            deps.tracker.complete(alice, 306) // the team's task

            deps.teams.assign("dave", 1)
            assertTrue(deps.tracker.syncPlayer(dave))

            assertEquals(30, dave.points())
            assertEquals(1, dave.totalTasks())
            assertEquals(1 shl 18, dave.vars[LeagueTaskVars.completed[9]]) // only task 306
            assertTrue(deps.tracker.isComplete(dave, 306))
            assertFalse(deps.tracker.isComplete(dave, 303))
        }

    @Test
    fun GameTestState.`players with no team are not affected by a team's progress`() =
        runTeamTest { deps, alice, _ ->
            val carol = registerPlayer(player = Player().apply { username = "carol" })

            deps.tracker.complete(alice, 303)
            deps.tracker.complete(carol, 306)

            assertEquals(1 shl 18, carol.vars[LeagueTaskVars.completed[9]]) // only her 306
            assertEquals(30, carol.points())
            assertFalse(deps.tracker.syncPlayer(carol))
            assertEquals(10, alice.points())
        }

    @Test
    fun GameTestState.`clearing a player's own varps does not touch the team record`() =
        runTeamTest { deps, alice, _ ->
            deps.tracker.complete(alice, 303)

            deps.tracker.reset(alice)

            assertEquals(0, alice.points())
            assertTrue(deps.tracker.isComplete(alice, 303))
            deps.tracker.syncPlayer(alice)
            assertEquals(10, alice.points())
        }

    @Test
    fun GameTestState.`resetting a team clears its members once they are synced`() =
        runTeamTest { deps, alice, bob ->
            deps.tracker.complete(alice, 303)
            deps.tracker.addProgress(alice, 306, LeagueProgressVarps.iron_ore, 50)

            deps.teams.resetProgress(1)
            for (member in deps.teams.onlineMembers(checkNotNull(deps.teams.team(1)))) {
                deps.tracker.syncPlayer(member)
            }

            for (member in listOf(alice, bob)) {
                assertEquals(0, member.points())
                assertEquals(0, member.totalTasks())
                assertEquals(0, member.vars[LeagueTaskVars.completed[9]])
                assertEquals(0, member.vars[LeagueProgressVarps.iron_ore])
                assertFalse(deps.tracker.isComplete(member, 303))
            }
        }
}
