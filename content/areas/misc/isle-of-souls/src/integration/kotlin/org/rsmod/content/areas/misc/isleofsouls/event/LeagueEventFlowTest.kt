package org.rsmod.content.areas.misc.isleofsouls.event

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.drops.DropResult
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeamCommands
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.cheat.CheatCommandMap
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.mod.ModLevelTypeList
import org.rsmod.map.CoordGrid

/** The league event end to end: announcements, lead changes, freezing and the scoreboard files. */
class LeagueEventFlowTest {
    @TempDir lateinit var dir: Path

    private object EventModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueEvent::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueStandings::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueAnnouncer::class.java).`in`(Scopes.SINGLETON)
            bind(LeagueScoreboard::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val event: LeagueEvent,
        val standings: LeagueStandings,
        val announcer: LeagueAnnouncer,
        val scoreboard: LeagueScoreboard,
        val tracker: LeagueTaskTracker,
        val commands: CheatCommandMap,
        val modLevels: ModLevelTypeList,
    )

    private fun b(text: Any) = "<col=147194>$text</col>"

    private val league = b("League:")

    private val alpha = "${b("Alice")} of ${b("Alpha")}"

    /** Broadcast chat (type 14) a player has received, oldest first. */
    private fun Player.broadcasts(): List<String> =
        (client as CaptureClient)
            .filterIsInstance<MessageGame>()
            .filter { it.type == 14 }
            .map { it.message }

    private fun Player.levelBroadcasts(): List<String> =
        broadcasts().filter { "has reached level" in it }

    private fun Player.chat(): List<String> =
        (client as CaptureClient).filterIsInstance<MessageGame>().map { it.message }

    private fun Player.clearChat() = (client as CaptureClient).clearOutgoing()

    private fun List<Player>.clearChat() = forEach { it.clearChat() }

    /** alice (admin) and bob are on Alpha, carol is on Beta, dave has no team. */
    private fun GameTestState.runEventTest(
        body: GameTestScope.(Deps, Player, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(
            Deps::class,
            EventModule,
            LeagueEventScript::class,
            LeagueTeamCommands::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            listOf(alice to "Alice", bob to "Bob", carol to "Carol", dave to "Dave").forEach {
                (member, name) ->
                member.displayName = name
                member.modLevel = deps.modLevels.default()
            }
            alice.modLevel = deps.modLevels.values.first { it.internalName == "admin" }

            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("carol", 2)
            deps.standings.syncLeader()
            listOf(alice, bob, carol, dave).clearChat()
            body(deps, alice, bob, carol, dave)
        }

    @Test
    fun GameTestState.`an elite task is announced to every player, then the new lead`() =
        runEventTest { deps, alice, bob, carol, dave ->
            deps.tracker.complete(alice, 258) // elite, 200 points

            for (watcher in listOf(alice, bob, carol, dave)) {
                val lines = watcher.broadcasts()
                assertEquals(2, lines.size)
                assertTrue(lines[0].startsWith("$league $alpha has completed the elite task: "))
                assertEquals(
                    "$league ${b("Alpha")} has taken the lead with ${b("200 League Points")}.",
                    lines[1],
                )
            }
        }

    @Test
    fun GameTestState.`only the first team to complete a hard task is announced`() =
        runEventTest { deps, alice, _, carol, dave ->
            deps.tracker.complete(alice, 309) // hard, 80 points

            val first = dave.broadcasts()
            assertTrue(
                first[0].startsWith("$league $alpha is the first to complete the hard task: ")
            )
            listOf(alice, carol, dave).clearChat()

            deps.tracker.complete(carol, 309)

            // Beta did not take the lead (Alpha got there first) and the task is no longer a first.
            assertEquals(emptyList<String>(), dave.broadcasts())
        }

    @Test
    fun GameTestState.`the lead is announced each time it changes hands`() =
        runEventTest { deps, alice, _, carol, dave ->
            deps.tracker.complete(alice, 306) // medium 30: Alpha leads
            dave.clearChat()

            deps.tracker.complete(carol, 309) // hard 80: Beta overtakes
            assertTrue(dave.broadcasts().last().contains("${b("Beta")} has taken the lead with"))
            assertTrue(dave.broadcasts().last().contains(b("80 League Points")))
            dave.clearChat()

            deps.tracker.complete(alice, 258) // elite 200: Alpha 230 retakes it
            assertEquals(
                "$league ${b("Alpha")} has taken the lead with ${b("230 League Points")}.",
                dave.broadcasts().last(),
            )
            dave.clearChat()

            // Extending a lead is not a change.
            deps.tracker.complete(alice, 289)
            assertFalse(dave.broadcasts().any { it.contains("has taken the lead") })
        }

    @Test
    fun GameTestState.`freezing tells everyone and the freeze is announced with the top teams`() =
        runEventTest { deps, alice, bob, carol, dave ->
            deps.tracker.complete(alice, 306)
            listOf(alice, bob, carol, dave).clearChat()

            deps.commands.execute(alice, "leaguefreeze", emptyList())

            assertTrue(deps.event.frozen)
            val expected =
                listOf(
                    "$league The standings are now frozen.",
                    "1. ${b("Alpha")} - ${b("30 League Points")}",
                    "2. ${b("Beta")} - ${b("0 League Points")}",
                )
            for (watcher in listOf(alice, bob, carol, dave)) {
                assertEquals(expected, watcher.broadcasts())
            }
            assertTrue(alice.chat().last().startsWith("<col=1a6b1a>Frozen:</col>"))
        }

    @Test
    fun GameTestState.`a second freeze and a freeze by a regular player change nothing`() =
        runEventTest { deps, alice, _, _, dave ->
            deps.commands.execute(dave, "leaguefreeze", emptyList())
            assertFalse(deps.event.frozen)

            deps.commands.execute(alice, "leaguefreeze", emptyList())
            alice.clearChat()
            deps.commands.execute(alice, "leaguefreeze", emptyList())

            assertEquals(
                listOf("<col=8b1a1a>Not changed:</col> the standings are already frozen."),
                alice.chat(),
            )
        }

    @Test
    fun GameTestState.`while frozen the standings stand still and the lead is not announced`() =
        runEventTest { deps, alice, _, carol, dave ->
            deps.tracker.complete(alice, 306) // Alpha 30
            deps.commands.execute(alice, "leaguefreeze", emptyList())
            dave.clearChat()

            deps.tracker.complete(carol, 309) // Beta 80 would lead, but it is frozen

            assertFalse(dave.broadcasts().any { it.contains("has taken the lead") })
            deps.commands.execute(dave, "teams", emptyList())
            val teamsLines = dave.chat().filterNot { it in dave.broadcasts() }
            assertEquals("${b("League Teams")} <col=8b1a1a>(frozen)</col>", teamsLines.first())
            assertTrue(teamsLines[1].startsWith(b("Alpha")))
            assertTrue(teamsLines[1].contains(b(30)))
            assertTrue(teamsLines[2].startsWith(b("Beta")))
            assertTrue(teamsLines[2].contains("${b(0)} pts"))
            // The team record itself kept counting.
            assertEquals(80, deps.tracker.points(checkNotNull(deps.teams.team(2))))
        }

    @Test
    fun GameTestState.`unfreezing announces it and catches up on a lead change made meanwhile`() =
        runEventTest { deps, alice, _, carol, dave ->
            deps.tracker.complete(alice, 306)
            deps.commands.execute(alice, "leaguefreeze", emptyList())
            deps.tracker.complete(carol, 309)
            dave.clearChat()

            deps.commands.execute(alice, "leagueunfreeze", emptyList())

            assertFalse(deps.event.frozen)
            assertEquals(
                listOf(
                    "$league The standings are live again.",
                    "$league ${b("Beta")} has taken the lead with ${b("80 League Points")}.",
                ),
                dave.broadcasts(),
            )
            alice.clearChat()
            deps.commands.execute(alice, "leagueunfreeze", emptyList())
            assertEquals(
                listOf("<col=8b1a1a>Not changed:</col> the standings are not frozen."),
                alice.chat(),
            )
        }

    @Test
    fun GameTestState.`a full reset lifts a leftover freeze`() =
        runEventTest { deps, alice, _, _, _ ->
            deps.commands.execute(alice, "leaguefreeze", emptyList())
            alice.clearChat()

            deps.commands.execute(alice, "teamreset", listOf("all", "confirm"))

            assertFalse(deps.event.frozen)
            assertTrue(
                alice.chat().contains("The standings were frozen; the freeze was lifted too.")
            )
        }

    @Test
    fun GameTestState.`a death is announced to everyone`() =
        runEventTest { deps, _, bob, carol, _ ->
            deps.standings.syncLeader()
            eventBus.publish(PlayerDeathEvents.Died(bob))

            assertEquals(
                listOf("$league ${b("Bob")} of ${b("Alpha")} has died."),
                carol.broadcasts(),
            )
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`reaching level 99 is announced and level 98 is not`() =
        runEventTest { _, alice, _, carol, _ ->
            alice.stats[stats.mining] = 98
            alice.engineQueueAdvanceStat(stats.mining)
            advance()
            // The level tasks that the level completes are announced too; only look at the
            // level announcements.
            assertEquals(emptyList<String>(), carol.levelBroadcasts())

            alice.stats[stats.mining] = 99
            alice.engineQueueAdvanceStat(stats.mining)
            advance()

            assertEquals(
                listOf("$league $alpha has reached level ${b(99)} in ${b("Mining")}."),
                carol.levelBroadcasts(),
            )
        }

    @OptIn(InternalApi::class)
    @Test
    fun GameTestState.`a level up completes the level tasks for the whole team`() =
        runEventTest { deps, alice, bob, _, _ ->
            assertFalse(deps.tracker.isComplete(bob, 191))

            alice.stats[stats.mining] = 5
            alice.engineQueueAdvanceStat(stats.mining)
            advance()

            // The first level up and "first level 5" are shared with Bob.
            assertTrue(deps.tracker.isComplete(alice, 191))
            assertTrue(deps.tracker.isComplete(alice, 192))
            assertTrue(deps.tracker.isComplete(bob, 191))
            assertTrue(deps.tracker.isComplete(bob, 192))
            assertFalse(deps.tracker.isComplete(bob, 193))
        }

    @Test
    fun GameTestState.`a rare drop is announced only if it is on the list`() =
        runEventTest { deps, alice, _, carol, _ ->
            dir.resolve("announcements.json").writeText("""{ "drops": { "objs": ["logs"] } }""")
            deps.announcer.load(dir)

            deps.announcer.drop(alice, objs.logs, 1)
            deps.announcer.drop(alice, objs.willow_logs, 1)

            assertEquals(
                listOf("$league $alpha has received a rare drop: ${b("Logs")}."),
                carol.broadcasts(),
            )
        }

    @Test
    fun GameTestState.`boosted drops are announced and so are pets`() =
        runEventTest { deps, alice, _, carol, _ ->
            deps.announcer.load(dir)

            deps.announcer.drop(alice, objs.logs, 1, boosted = true)
            deps.announcer.drop(alice, objs.willow_logs, 1, boosted = false)

            assertEquals(
                listOf("$league $alpha has received a rare drop: ${b("Logs")}."),
                carol.broadcasts(),
            )

            dir.resolve("announcements.json")
                .writeText("""{ "drops": { "boosted": false, "pets": false } }""")
            assertEquals(null, deps.announcer.reload())
            deps.announcer.drop(alice, objs.logs, 1, boosted = true)
            assertEquals(1, carol.broadcasts().size)
        }

    @Test
    fun GameTestState.`the drops a monster rolls are announced to everyone`() =
        runEventTest { deps, alice, _, carol, _ ->
            dir.resolve("announcements.json").writeText("""{ "drops": { "objs": ["logs"] } }""")
            deps.announcer.load(dir)
            val type =
                npcTypeFactory.create {
                    name = "Test monster"
                    hitpoints = 10
                    defaultMode = NpcMode.None
                }
            val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), type)

            eventBus.publish(
                NpcDropEvents.Rolled(
                    npc,
                    alice,
                    listOf(DropResult(objs.logs, 2), DropResult(objs.willow_logs, 1)),
                )
            )

            assertEquals(listOf(AnnouncementMessages.drop(alpha, "Logs", 2)), carol.broadcasts())
        }

    @Test
    fun GameTestState
        .`the announcement settings file is written on first start and can be reloaded`() =
        runEventTest { deps, alice, _, _, dave ->
            deps.announcer.load(dir)
            val file = dir.resolve("announcements.json")
            assertTrue(file.exists())
            assertEquals(AnnouncementConfig(), AnnouncementConfig.parse(file.readText()))

            file.writeText("""{ "deaths": false }""")
            assertEquals(null, deps.announcer.reload())
            assertFalse(deps.announcer.config.deaths)

            file.writeText("""{ "death": false }""")
            assertTrue(deps.announcer.reload()!!.contains("\"death\""))
            assertFalse(deps.announcer.config.deaths)

            alice.clearChat()
            deps.commands.execute(alice, "announcereload", emptyList())
            assertTrue(alice.chat().single().startsWith("<col=8b1a1a>Not reloaded:</col>"))
            deps.commands.execute(dave, "announcereload", emptyList())
        }

    @Test
    fun GameTestState.`the scoreboard files follow the standings`() =
        runEventTest { deps, alice, _, _, _ ->
            deps.scoreboard.start(dir)
            val json = dir.resolve("leaderboard.json")
            val html = dir.resolve("leaderboard.html")
            assertTrue(json.exists() && html.exists())

            deps.tracker.complete(alice, 306)

            val text = json.readText()
            assertTrue(text.contains("\"name\" : \"Alpha\""))
            assertTrue(text.contains("\"points\" : 30"))
            assertTrue(text.contains("\"Alice\"") && text.contains("\"Bob\""))
            assertTrue(html.readText().contains("Alpha"))

            deps.commands.execute(alice, "leaguefreeze", emptyList())
            assertTrue(json.readText().contains("\"frozen\" : true"))
        }
}
