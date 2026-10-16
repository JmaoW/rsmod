package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.routefinder.flag.CollisionFlag

class InfernoRunTest {
    private object InfernoModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Inferno::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val inferno: Inferno,
        val unlock: InfernoUnlock,
        val script: InfernoScript,
    )

    private class Cast(val alice: Player, val bob: Player, val carol: Player)

    /** alice and bob share team 1 and an Inferno; carol is a lone player. */
    private fun GameTestState.runInfernoTest(body: GameTestScope.(Deps, Cast) -> Unit) =
        runInjectedGameTest(Deps::class, InfernoModule, InfernoScript::class) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.inferno.waveLoader = { InfernoWaves.parse(TEST_WAVES, "test waves") }
            body(deps, Cast(alice, bob, carol))
        }

    @Test
    fun GameTestState.`entering starts a run in the players' own copy of the arena`() =
        runInfernoTest { deps, cast ->
            assertEquals(EnterResult.Entered, deps.inferno.enter(cast.alice))
            val run = checkNotNull(deps.inferno.runOf(cast.alice))

            assertEquals(InfernoMode.Full, run.mode)
            assertEquals(2, run.waves.count)
            assertEquals(0, run.waveNumber)
            assertEquals(WaveState.WaitingForNextWave, run.state)
            assertTrue(run.instance.contains(cast.alice.coords))
            assertEquals(InfernoInstance.type, run.instance.type)
            assertEquals(1, deps.inferno.liveRuns().size)
        }

    @Test
    fun GameTestState.`a teammate joins the same run and a stranger gets their own`() =
        runInfernoTest { deps, cast ->
            deps.inferno.enter(cast.alice)
            assertEquals(EnterResult.Entered, deps.inferno.enter(cast.bob))
            deps.inferno.enter(cast.carol)

            assertTrue(deps.inferno.runOf(cast.alice) === deps.inferno.runOf(cast.bob))
            assertNotEquals(deps.inferno.runOf(cast.alice), deps.inferno.runOf(cast.carol))
            assertEquals(2, deps.inferno.liveRuns().size)
        }

    @Test
    fun GameTestState.`entering twice is refused`() = runInfernoTest { deps, cast ->
        deps.inferno.enter(cast.alice)

        assertEquals(EnterResult.AlreadyInside, deps.inferno.enter(cast.alice))
    }

    @Test
    fun GameTestState.`leaving sends the player to the plaza and ends an empty run`() =
        runInfernoTest { deps, cast ->
            deps.inferno.enter(cast.alice)

            assertTrue(deps.inferno.leave(cast.alice))
            advance(ticks = 2)

            assertTrue(deps.inferno.liveRuns().isEmpty())
            assertNull(deps.inferno.runOf(cast.alice))
            assertEquals(InfernoArena.exit, cast.alice.coords)
        }

    @Test
    fun GameTestState.`the run keeps going while a teammate is still inside`() =
        runInfernoTest { deps, cast ->
            deps.inferno.enter(cast.alice)
            deps.inferno.enter(cast.bob)

            deps.inferno.leave(cast.bob)

            assertEquals(1, deps.inferno.liveRuns().size)
            assertNull(deps.inferno.runOf(cast.bob))
        }

    @Test
    fun GameTestState.`the first wave spawns after the pause and the run follows the wave file`() =
        runInfernoTest { deps, cast ->
            deps.inferno.enter(cast.alice)
            val run = checkNotNull(deps.inferno.runOf(cast.alice))

            advanceUntil(
                predicate = { run.waveNumber == 1 },
                timeoutTicks = 40,
                timeoutMessage = { "wave 1 never started" },
            )
            assertEquals(WaveState.InWave, run.state)
        }

    @Test
    fun GameTestState.`the Inferno starts locked`() = runInfernoTest { deps, cast ->
        assertFalse(deps.unlock.isUnlocked(cast.alice))
    }

    @Test
    fun GameTestState.`giving up a fire cape from the pack unlocks it for good`() =
        runInfernoTest { deps, cast ->
            cast.alice.inv[0] = InvObj(cave_objs.fire_cape)
            assertTrue(deps.unlock.hasFireCape(cast.alice))

            assertTrue(deps.unlock.sacrifice(cast.alice))

            assertTrue(deps.unlock.isUnlocked(cast.alice))
            assertFalse(deps.unlock.hasFireCape(cast.alice))
            assertFalse(deps.unlock.sacrifice(cast.alice))
            assertTrue(deps.unlock.isUnlocked(cast.alice))
        }

    @Test
    fun GameTestState.`a worn fire cape can be given up too`() = runInfernoTest { deps, cast ->
        cast.alice.worn[Wearpos.Back.slot] = InvObj(cave_objs.fire_cape)

        assertTrue(deps.unlock.sacrifice(cast.alice))

        assertTrue(deps.unlock.isUnlocked(cast.alice))
        assertFalse(deps.unlock.hasFireCape(cast.alice))
    }

    @Test
    fun GameTestState.`only one cape is taken`() = runInfernoTest { deps, cast ->
        cast.alice.inv[0] = InvObj(cave_objs.fire_cape)
        cast.alice.inv[1] = InvObj(cave_objs.fire_cape)

        assertTrue(deps.unlock.sacrifice(cast.alice))

        assertEquals(1, cast.alice.inv.count(cave_objs.fire_cape))
    }

    @Test
    fun GameTestState.`without a cape nothing is unlocked`() = runInfernoTest { deps, cast ->
        assertFalse(deps.unlock.sacrifice(cast.alice))
        assertFalse(deps.unlock.isUnlocked(cast.alice))
    }

    @Test
    fun GameTestState.`the arena floor is open and walled in`() = runGameTest {
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        val sw = InfernoArena.floorSouthWest
        val ne = InfernoArena.floorNorthEast
        for (z in sw.z..ne.z) {
            for (x in sw.x..ne.x) {
                assertEquals(0, collision[x, z, 0] and blocked, "arena tile $x,$z is blocked")
            }
        }
        // One tile out on every side is rock.
        for (x in sw.x - 1..ne.x + 1) {
            assertNotEquals(0, collision[x, sw.z - 1, 0] and blocked, "south edge $x")
            assertNotEquals(0, collision[x, ne.z + 1, 0] and blocked, "north edge $x")
        }
        for (z in sw.z - 1..ne.z + 1) {
            assertNotEquals(0, collision[sw.x - 1, z, 0] and blocked, "west edge $z")
            assertNotEquals(0, collision[ne.x + 1, z, 0] and blocked, "east edge $z")
        }
    }

    @Test
    fun GameTestState.`players start on the arena floor, inside the copied block`() = runGameTest {
        val sw = InfernoArena.floorSouthWest
        val ne = InfernoArena.floorNorthEast
        val start = InfernoArena.start
        assertTrue(start.x in sw.x..ne.x && start.z in sw.z..ne.z, "start $start")
        assertTrue((start.x shr 3) in 280..287, "start zone x ${start.x shr 3}")
        assertTrue((start.z shr 3) in 664..671, "start zone z ${start.z shr 3}")
    }

    @Test
    fun GameTestState.`the way out is on open floor`() = runGameTest {
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        val tile = InfernoArena.exit
        assertEquals(0, collision[tile.x, tile.z, tile.level] and blocked, "$tile is blocked")
    }

    private companion object {
        /** Two waves, so a test can watch the run start without playing a whole Inferno. */
        val TEST_WAVES =
            """
            [[wave]]
            number = 1
            monsters = ["bat"]

            [[wave]]
            number = 2
            monsters = ["zuk"]
            boss = true
            """
                .trimIndent()
    }
}
