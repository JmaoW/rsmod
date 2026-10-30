package org.rsmod.content.areas.misc.isleofsouls.lobby

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

/**
 * The Soul Wars barriers can be passed and the team portals link the waiting areas to the arena.
 */
class SoulWarsTest {
    class Deps @Inject constructor(val script: SoulWarsScript)

    private fun GameTestState.runSoulWarsTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, SoulWarsScript::class) { deps -> body(deps) }

    /** A barrier as the cache's map has it: its type, where it stands and which edge it is on. */
    private class Barrier(val type: LocType, val x: Int, val z: Int, val angle: LocAngle)

    private val barriers =
        listOf(
            Barrier(SoulWarsLocs.main_waiting_area_barrier, 2199, 2842, LocAngle.East),
            Barrier(SoulWarsLocs.clan_waiting_area_barrier, 2220, 2842, LocAngle.West),
            Barrier(SoulWarsLocs.blue_graveyard_barrier, 2135, 2905, LocAngle.East),
            Barrier(SoulWarsLocs.blue_graveyard_barrier, 2140, 2899, LocAngle.North),
            Barrier(SoulWarsLocs.blue_graveyard_barrier, 2140, 2911, LocAngle.South),
            Barrier(SoulWarsLocs.neutral_western_barrier, 2162, 2896, LocAngle.North),
            Barrier(SoulWarsLocs.neutral_western_barrier, 2162, 2900, LocAngle.South),
            Barrier(SoulWarsLocs.neutral_eastern_barrier, 2253, 2923, LocAngle.North),
            Barrier(SoulWarsLocs.neutral_eastern_barrier, 2253, 2927, LocAngle.South),
            Barrier(SoulWarsLocs.red_graveyard_barrier, 2274, 2913, LocAngle.North),
            Barrier(SoulWarsLocs.red_graveyard_barrier, 2274, 2925, LocAngle.South),
            Barrier(SoulWarsLocs.red_graveyard_barrier, 2279, 2919, LocAngle.West),
            // The team-coloured ones with no copy in the map are handled the same way.
            Barrier(SoulWarsLocs.blue_eastern_barrier, 2150, 2905, LocAngle.East),
            Barrier(SoulWarsLocs.blue_western_barrier, 2150, 2908, LocAngle.North),
            Barrier(SoulWarsLocs.red_eastern_barrier, 2260, 2905, LocAngle.South),
            Barrier(SoulWarsLocs.red_western_barrier, 2260, 2908, LocAngle.West),
        )

    private fun Barrier.coords() = CoordGrid(x, z, 0)

    /** The two tiles either side of the wall, in the order (one side, other side). */
    private fun Barrier.sides(): Pair<CoordGrid, CoordGrid> =
        when (angle) {
            LocAngle.West -> coords().translate(-1, 0) to coords()
            LocAngle.North -> coords() to coords().translate(0, 1)
            LocAngle.East -> coords() to coords().translate(1, 0)
            LocAngle.South -> coords().translate(0, -1) to coords()
        }

    private fun GameTestScope.place(barrier: Barrier): BoundLocInfo {
        allocZoneCollision(barrier.coords())
        return placeMapLoc(barrier.coords(), barrier.type, LocShape.WallStraight, barrier.angle)
    }

    @Test
    fun GameTestState.`every barrier can be passed from either side`() = runSoulWarsTest { _ ->
        for (barrier in barriers) {
            val loc = place(barrier)
            val (a, b) = barrier.sides()
            for ((from, to) in listOf(a to b, b to a)) {
                player.placeAt(from)
                player.opLoc1(loc)
                advance(ticks = 4)
                assertEquals(to, player.coords, "${barrier.type} at ${barrier.coords()} from $from")
            }
        }
    }

    @Test
    fun GameTestState.`every barrier in the cache is covered`() = runSoulWarsTest { _ ->
        assertEquals(SoulWarsLocs.barriers.toSet(), barriers.map { it.type }.toSet())
    }

    @Test
    fun GameTestState.`each team's portal is placed in its waiting area`() =
        runSoulWarsTest { deps ->
            for (portal in SoulWarsPortals.all) {
                allocZoneCollision(portal.lobbyPortal)
            }
            deps.script.placePortals()

            for (portal in SoulWarsPortals.all) {
                val loc = findLocs(portal.lobbyPortal).firstOrNull { it.id == portal.type.id }
                assertNotNull(loc, "${portal.name} portal not placed")
                assertEquals(portal.lobbyPortal, loc!!.coords)
            }
            // The blue one is in the west (blue) waiting area and the red one in the east.
            assertTrue(SoulWarsPortals.blue.lobbyPortal.x < 2199)
            assertTrue(SoulWarsPortals.red.lobbyPortal.x > 2220)
        }

    @Test
    fun GameTestState.`a lobby portal leads to the arena and the arena one leads back`() =
        runSoulWarsTest { deps ->
            for (portal in SoulWarsPortals.all) {
                allocZoneCollision(portal.lobbyPortal)
                allocZoneCollision(portal.lobbyArrival)
                allocZoneCollision(portal.arenaPortal)
                allocZoneCollision(portal.arenaArrival)
            }
            deps.script.placePortals()

            for (portal in SoulWarsPortals.all) {
                val lobby =
                    BoundLocInfo(
                        checkNotNull(
                            findLocs(portal.lobbyPortal).firstOrNull { it.id == portal.type.id }
                        ),
                        locTypes[portal.type],
                    )
                player.placeAt(portal.lobbyArrival)
                player.opLoc1(lobby)
                advance(ticks = 6)
                assertEquals(portal.arenaArrival, player.coords, "${portal.name} into the arena")

                // The arena's own portal is part of the map.
                val arena = placeMapLoc(portal.arenaPortal, portal.type, portal.shape, portal.angle)
                player.opLoc1(arena)
                advance(ticks = 6)
                assertEquals(portal.lobbyArrival, player.coords, "${portal.name} back to the lobby")
            }
        }

    @Test
    fun GameTestState.`a team's portal does nothing where it has no link`() = runSoulWarsTest { _ ->
        val portal = SoulWarsPortals.blue
        val elsewhere = CoordGrid(2200, 2820, 0)
        allocZoneCollision(elsewhere)
        // The tutorial area has copies of the portals at other coordinates.
        val stray = placeMapLoc(elsewhere, portal.type, portal.shape, portal.angle)
        val start = elsewhere.translate(1, 0)
        player.placeAt(start)
        player.opLoc1(stray)
        advance(ticks = 6)
        assertEquals(start, player.coords)
    }
}
