package org.rsmod.content.areas.misc.isleofsouls.instances

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

class TeamInstancesTest {
    private object InstancesModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps @Inject constructor(val teams: LeagueTeams, val instances: TeamInstances)

    private val type = TestPatchInstance.type

    /** alice and bob are on team 1, dave on team 2, carol has no team. */
    private fun GameTestState.runInstanceTest(
        body: GameTestScope.(Deps, Player, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(Deps::class, InstancesModule) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("dave", 2)
            deps.instances.register(type)
            body(deps, alice, bob, carol, dave)
        }

    private fun TeamInstances.regionUidOf(player: Player) =
        checkNotNull(instanceOf(player)).region.uid

    @Test
    fun GameTestState.`entering puts the player at the entry in their own copy of the map`() =
        runInstanceTest { deps, alice, _, _, _ ->
            assertEquals(EnterResult.Entered, deps.instances.enter(alice, type))

            val instance = checkNotNull(deps.instances.instanceOf(alice))
            assertEquals(instance.region.normal[type.entry], alice.coords)
            assertTrue(RegionRegistry.inWorkingArea(alice.coords))
        }

    @Test
    fun GameTestState.`teammates are put into the same instance`() =
        runInstanceTest { deps, alice, bob, _, _ ->
            deps.instances.enter(alice, type)
            deps.instances.enter(bob, type)

            assertEquals(deps.instances.regionUidOf(alice), deps.instances.regionUidOf(bob))
            val instance = checkNotNull(deps.instances.instanceOf(alice))
            assertEquals(setOf(alice, bob), deps.instances.playersIn(instance).toSet())
            assertEquals(1, deps.instances.live().size)
        }

    @Test
    fun GameTestState.`other teams and lone players get instances of their own`() =
        runInstanceTest { deps, alice, _, carol, dave ->
            deps.instances.enter(alice, type)
            deps.instances.enter(carol, type)
            deps.instances.enter(dave, type)

            val uids = listOf(alice, carol, dave).map { deps.instances.regionUidOf(it) }
            assertEquals(3, uids.toSet().size)
            assertEquals(3, deps.instances.live().size)
        }

    @Test
    fun GameTestState.`a player can leave and rejoin while a teammate keeps it alive`() =
        runInstanceTest { deps, alice, bob, _, _ ->
            deps.instances.enter(alice, type)
            deps.instances.enter(bob, type)
            val uid = deps.instances.regionUidOf(alice)

            assertTrue(deps.instances.leave(alice))
            assertEquals(type.exit, alice.coords)
            assertNull(deps.instances.instanceOf(alice))
            assertEquals(1, deps.instances.live().size)

            assertEquals(EnterResult.Entered, deps.instances.enter(alice, type))
            assertEquals(uid, deps.instances.regionUidOf(alice))
        }

    @Test
    fun GameTestState.`once everyone has left the next entry is a fresh copy`() =
        runInstanceTest { deps, alice, bob, _, _ ->
            deps.instances.enter(alice, type)
            deps.instances.enter(bob, type)
            val first = deps.instances.regionUidOf(alice)

            deps.instances.leave(alice)
            deps.instances.leave(bob)
            assertTrue(deps.instances.live().isEmpty())

            deps.instances.enter(bob, type)
            assertNotEquals(first, deps.instances.regionUidOf(bob))
        }

    @Test
    fun GameTestState.`the engine tracks the region and frees it once everyone has left`() =
        runInstanceTest { deps, alice, bob, _, _ ->
            deps.instances.enter(alice, type)
            deps.instances.enter(bob, type)
            val instance = checkNotNull(deps.instances.instanceOf(alice))

            advance(3)
            assertEquals(instance.region.uid, alice.regionUid)
            assertEquals(instance.region.uid, bob.regionUid)
            removeInactiveRegions()
            assertTrue(instance.registered)

            deps.instances.leave(alice)
            deps.instances.leave(bob)
            advance(3)
            removeInactiveRegions()

            assertFalse(instance.registered)
            assertTrue(deps.instances.live().isEmpty())
        }

    @Test
    fun GameTestState.`entering an instance you are already in does nothing`() =
        runInstanceTest { deps, alice, _, _, _ ->
            deps.instances.enter(alice, type)
            val coords = alice.coords

            assertEquals(EnterResult.AlreadyInside, deps.instances.enter(alice, type))
            assertEquals(coords, alice.coords)
        }

    @Test
    fun GameTestState.`leaving when not in an instance does nothing`() =
        runInstanceTest { deps, alice, _, _, _ ->
            val coords = alice.coords

            assertFalse(deps.instances.leave(alice))
            assertEquals(coords, alice.coords)
        }

    @Test
    fun GameTestState.`an instance type can only be registered once`() =
        runInstanceTest { deps, _, _, _, _ ->
            assertThrows(IllegalArgumentException::class.java) { deps.instances.register(type) }
            assertEquals(type, deps.instances.type("patch"))
            assertNull(deps.instances.type("missing"))
        }

    @Test
    fun GameTestState.`a player saved inside an instance is moved out when the account loads`() =
        runInstanceTest { deps, _, _, carol, _ ->
            val applier = TeamInstanceLoginApplier(deps.instances)
            carol.coords = CoordGrid(RegionRegistry.START_COORD_X + 40, 100, 0)

            applier.apply(carol, TeamInstanceLoginSegment)

            assertEquals(type.exit, carol.coords)
        }

    @Test
    fun GameTestState.`a player on the normal map is left where they are on login`() =
        runInstanceTest { deps, _, _, carol, _ ->
            val applier = TeamInstanceLoginApplier(deps.instances)
            val coords = carol.coords

            applier.apply(carol, TeamInstanceLoginSegment)

            assertEquals(coords, carol.coords)
            assertNotNull(deps.instances.fallbackExit())
        }
}
