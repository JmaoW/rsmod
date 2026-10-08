package org.rsmod.content.areas.misc.isleofsouls.masteries

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.capture.CaptureClient
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveCompletions
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.util.ParamMap
import org.rsmod.game.type.util.ParamMapBuilder
import org.rsmod.map.CoordGrid

class LeagueMasteryTest {
    /** One shared [LeagueTeams] for the test and for the scripts it loads. */
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val masteries: LeagueMasteryTracker,
        val tasks: LeagueMasteryTasks,
        val completions: FightCaveCompletions,
    )

    private fun GameTestState.runMasteryTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            TeamsModule,
            LeagueMasteryScript::class,
            LeagueMasteryKills::class,
        ) {
            body(it)
        }

    private fun Player.toSpend() = vars[MasteryVarBits.points_to_spend]

    private fun Player.earned() = vars[MasteryVarBits.points_earned]

    private fun Deps.unlock(player: Player, style: MasteryStyle, tier: Int): MasteryResult {
        masteries.view(player, MasteryData.cardOf(MasteryChoice(style, tier)))
        return masteries.confirm(player)
    }

    private fun Player.messages(): List<String> =
        (client as CaptureClient).filterIsInstance<MessageGame>().map { it.message }

    @Test
    fun GameTestState.`a finished task is worth two points`() = runMasteryTest { deps ->
        assertEquals(0, player.toSpend())

        assertTrue(deps.tasks.complete(player, 6))
        assertEquals(1, player.earned())
        assertEquals(2, player.toSpend())
        assertEquals(1, player.vars[MasteryVarBits.point_unlock[5]])

        // A task counts once, and a slot that is not a task does nothing.
        assertFalse(deps.tasks.complete(player, 6))
        assertFalse(deps.tasks.complete(player, 0))
        assertFalse(deps.tasks.complete(player, 11))
        assertEquals(2, player.toSpend())
        assertEquals(
            listOf(
                "<col=147194>Combat Mastery:</col> Reach a combat level of 100. " +
                    "You have earned <col=147194>2</col> mastery points."
            ),
            player.messages(),
        )
    }

    @Test
    fun GameTestState.`unlocking spends a point and needs the previous tier`() =
        runMasteryTest { deps ->
            assertEquals(MasteryResult.NothingViewed, deps.masteries.confirm(player))
            assertEquals(MasteryResult.NotEnoughPoints, deps.unlock(player, MasteryStyle.Melee, 1))

            deps.tasks.complete(player, 6)
            assertEquals(
                MasteryResult.PreviousTierMissing,
                deps.unlock(player, MasteryStyle.Melee, 2),
            )
            assertEquals(MasteryResult.Unlocked, deps.unlock(player, MasteryStyle.Melee, 1))
            assertEquals(1, deps.masteries.tier(player, MasteryStyle.Melee))
            assertEquals(1, player.toSpend())
            assertEquals(MasteryResult.AlreadyUnlocked, deps.unlock(player, MasteryStyle.Melee, 1))

            // Another path starts at tier 1 on its own, and the last point goes there.
            assertEquals(
                MasteryResult.PreviousTierMissing,
                deps.unlock(player, MasteryStyle.Magic, 2),
            )
            assertEquals(MasteryResult.Unlocked, deps.unlock(player, MasteryStyle.Magic, 1))
            assertEquals(0, player.toSpend())
            assertEquals(MasteryResult.NotEnoughPoints, deps.unlock(player, MasteryStyle.Melee, 2))
            assertEquals(1, deps.masteries.tier(player, MasteryStyle.Melee))
            // Points earned count tasks, not points, so the bar and the 10 tasks line up.
            assertEquals(1, player.earned())
        }

    @Test
    fun GameTestState.`all eighteen masteries cost eighteen of the twenty points`() =
        runMasteryTest { deps ->
            for (slot in 1..MasteryData.TASK_COUNT) {
                assertTrue(deps.tasks.complete(player, slot))
            }
            assertEquals(MasteryData.TASK_COUNT, player.earned())
            assertEquals(20, player.toSpend())

            for (style in MasteryStyle.entries) {
                for (tier in 1..CombatMasteries.MAX_TIER) {
                    assertEquals(MasteryResult.Unlocked, deps.unlock(player, style, tier))
                }
            }
            assertEquals(2, player.toSpend())
        }

    @Test
    fun GameTestState.`masteries are offered to combat code through the provider`() =
        runMasteryTest { deps ->
            val combat = CombatMasteries(setOf(deps.masteries))
            deps.masteries.setTier(player, MasteryStyle.Ranged, 3)
            deps.masteries.setTier(player, MasteryStyle.Melee, 1)

            assertEquals(3, combat.tier(player, MasteryStyle.Ranged))
            assertEquals(0, combat.tier(player, MasteryStyle.Magic))
            assertEquals(3, combat.passiveTier(player))
            assertTrue(combat.hasPassive(player, 3))
            assertFalse(combat.hasPassive(player, 4))

            deps.masteries.reset(player)
            assertEquals(0, combat.passiveTier(player))
        }

    @Test
    fun GameTestState.`clicking a mastery asks the client to draw it`() = runMasteryTest { deps ->
        player.ifOpenMain(isle_interfaces.league_combat_mastery)

        // Card 7 is the second tier of the ranged path (cards go melee, ranged, magic).
        player.ifButton(MasteryComponents.clickzones, 7)
        advance()

        val expanded =
            (player.client as CaptureClient).filterIsInstance<RunClientScript>().filter {
                it.id == 7674
            }
        assertEquals(1, expanded.size)
        val args = expanded.single().values
        assertEquals(19, args.size)
        assertEquals(MasteryViewComponents.view_one.packed, args[2])
        assertEquals(MasteryViewComponents.loading.packed, args[3])
        assertEquals(MasteryViewComponents.select_button.packed, args[8])
        assertEquals(MasteryViewComponents.confirm.packed, args[10])
        assertEquals(MasteryViewComponents.confirm_button.packed, args[13])
        assertEquals(1172, args[17]) // Ranged II
        assertEquals(1154, args[18]) // tier 2 passive
        assertEquals(8, deps.masteries.viewedChoice(player)?.let { MasteryData.cardOf(it) + 1 })
    }

    @Test
    fun GameTestState.`confirming through the screen unlocks and redraws the grid`() =
        runMasteryTest { deps ->
            deps.tasks.complete(player, 6)
            player.ifOpenMain(isle_interfaces.league_combat_mastery)

            player.ifButton(MasteryComponents.clickzones, 12) // Magic I
            advance()
            player.ifButton(MasteryComponents.confirm_button)
            advance()

            assertEquals(1, deps.masteries.tier(player, MasteryStyle.Magic))
            assertTrue(
                "You have unlocked a new Combat Mastery: <col=147194>Magic I</col>." in
                    player.messages()
            )
            val draws =
                (player.client as CaptureClient).filterIsInstance<RunClientScript>().filter {
                    it.id == 7661
                }
            assertEquals(1, draws.size)
            assertEquals(listOf(0, 6), draws.single().values.toList())
        }

    @Test
    fun GameTestState.`confirming without points tells the player and changes nothing`() =
        runMasteryTest { deps ->
            player.ifOpenMain(isle_interfaces.league_combat_mastery)
            player.ifButton(MasteryComponents.clickzones, 0)
            advance()
            player.ifButton(MasteryComponents.confirm_button)
            advance()

            assertEquals(0, deps.masteries.tier(player, MasteryStyle.Melee))
            assertTrue(
                "<col=8b1a1a>Not yet:</col> you have not earned enough Combat Mastery points " +
                    "to unlock that." in player.messages()
            )
            val draws =
                (player.client as CaptureClient).filterIsInstance<RunClientScript>().filter {
                    it.id == 7661
                }
            assertTrue(draws.isEmpty())
        }

    @Test
    fun GameTestState.`a task finished by one teammate is finished for both`() =
        runMasteryTest { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)

            assertTrue(deps.tasks.complete(alice, 3))
            for (member in listOf(alice, bob)) {
                assertTrue(deps.tasks.isComplete(member, 3))
                assertEquals(1, member.vars[MasteryVarBits.point_unlock[2]])
                assertEquals(1, member.earned())
                assertEquals(2, member.toSpend())
            }
            // Doing it again, from either player, adds nothing.
            assertFalse(deps.tasks.complete(bob, 3))

            // Each spends their own points, in their own order.
            assertEquals(MasteryResult.Unlocked, deps.unlock(alice, MasteryStyle.Melee, 1))
            assertEquals(1, alice.toSpend())
            assertEquals(2, bob.toSpend())
            assertEquals(0, deps.masteries.tier(bob, MasteryStyle.Melee))
        }

    @Test
    fun GameTestState.`a teammate who was offline gets the task when synced`() =
        runMasteryTest { deps ->
            val alice = player
            alice.username = "alice"
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            assertTrue(deps.tasks.complete(alice, 8))

            // Bob joins the team later, and brings no mastery varbits of his own.
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.assign("bob", 1)
            assertEquals(0, bob.earned())
            deps.tasks.sync(bob)

            assertEquals(1, bob.earned())
            assertEquals(2, bob.toSpend())
            assertEquals(1, bob.vars[MasteryVarBits.point_unlock[7]])
        }

    @Test
    fun GameTestState.`resetting clears the tiers and the finished tasks`() =
        runMasteryTest { deps ->
            deps.tasks.complete(player, 1)
            assertEquals(MasteryResult.Unlocked, deps.unlock(player, MasteryStyle.Ranged, 1))

            deps.masteries.reset(player)
            deps.tasks.reset(player)

            assertEquals(0, deps.masteries.tier(player, MasteryStyle.Ranged))
            assertFalse(deps.tasks.isComplete(player, 1))
            assertEquals(0, player.earned())
            assertEquals(0, player.toSpend())
            assertEquals(null, deps.masteries.viewedChoice(player))
        }

    private fun GameTestScope.kill(player: Player, type: UnpackedNpcType) {
        val npc: Npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), type)
        eventBus.publish(NpcDeathEvents.Killed(npc, player))
    }

    @Test
    fun GameTestState.`defeating a giant completes the first task`() = runMasteryTest { deps ->
        kill(player, level("Giant rat", 28))
        kill(player, level("Giant spider", 28))
        assertFalse(deps.tasks.isComplete(player, 1))

        kill(player, level("Hill Giant", 28))
        assertTrue(deps.tasks.isComplete(player, 1))
    }

    @Test
    fun GameTestState.`ten kills of combat level one hundred or more complete the second task`() =
        runMasteryTest { deps ->
            repeat(5) { kill(player, level("Goblin", 2)) }
            repeat(9) { kill(player, level("Dragon", 100)) }
            assertFalse(deps.tasks.isComplete(player, 2))
            assertEquals(9, player.vars[LeagueProgressVarps.mastery_kills_100])

            kill(player, level("Dragon", 150))

            assertTrue(deps.tasks.isComplete(player, 2))
            assertEquals(10, player.vars[LeagueProgressVarps.mastery_kills_100])
            assertEquals(1, player.vars[MasteryVarBits.point_unlock[1]])
        }

    @Test
    fun GameTestState.`a team kill of Scurrius completes the third task for both players`() =
        runMasteryTest { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)

            kill(bob, level("Scurrius", 62))

            assertTrue(deps.tasks.isComplete(alice, 3))
            assertTrue(deps.tasks.isComplete(bob, 3))
            assertEquals(2, alice.vars[MasteryVarBits.points_to_spend])
        }

    @Test
    fun GameTestState.`team kills are shared towards ten high level kills`() =
        runMasteryTest { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)

            repeat(5) { kill(alice, level("Dragon", 100)) }
            repeat(5) { kill(bob, level("Dragon", 100)) }

            assertTrue(deps.tasks.isComplete(alice, 2))
            assertTrue(deps.tasks.isComplete(bob, 2))
        }

    @Test
    fun GameTestState.`a monster that needs Slayer fifty five completes the fourth task`() =
        runMasteryTest { deps ->
            kill(player, slayerMonster("Cockatrice", 25))
            assertFalse(deps.tasks.isComplete(player, 4))

            kill(player, slayerMonster("Turoth", 55))

            assertTrue(deps.tasks.isComplete(player, 4))
        }

    @Test
    fun GameTestState.`a kill nobody is credited for completes nothing`() = runMasteryTest { deps ->
        val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), level("Hill Giant", 28))
        eventBus.publish(NpcDeathEvents.Killed(npc, null))

        assertFalse(deps.tasks.isComplete(player, 1))
    }

    @Test
    fun GameTestState.`finishing the Fight Caves completes the Jad task`() =
        runMasteryTest { deps ->
            assertFalse(deps.tasks.isComplete(player, 5))

            deps.completions.completed(player)

            assertTrue(deps.tasks.isComplete(player, 5))
        }

    private companion object {
        fun level(name: String, level: Int) =
            npcTypeFactory.create {
                this.name = name
                this.vislevel = level
                hitpoints = 100
                defaultMode = NpcMode.None
            }

        private fun buildParams(init: ParamMapBuilder.() -> Unit): ParamMap =
            ParamMapBuilder().apply(init).toParamMap()

        fun slayerMonster(name: String, requirement: Int) =
            npcTypeFactory.create {
                this.name = name
                this.vislevel = 40
                hitpoints = 100
                defaultMode = NpcMode.None
                paramMap = buildParams { this[params.slayer_levelrequire] = requirement }
            }
    }
}
