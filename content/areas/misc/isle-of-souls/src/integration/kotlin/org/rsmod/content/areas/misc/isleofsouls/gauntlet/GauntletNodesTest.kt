package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Nodes and the teleport crystal. The test world has no map, so the nodes and symbols that every
 * room template holds in the real map are put into the template chunks by hand, at the places the
 * cache has them (read with `tools/dungeons/census.py`; every room of both templates has them at
 * the same room-relative tiles).
 */
class GauntletNodesTest {
    private object NodesModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(Gauntlet::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val gauntlet: Gauntlet,
        val teams: LeagueTeams,
        val collision: CollisionFlagMap,
        val locRepo: LocRepository,
        val locTypes: LocTypeList,
        val nodes: GauntletNodes,
    )

    private class Piece(val x: Int, val z: Int, val angle: Int)

    private val nodeA = listOf(Piece(0, 9, 2), Piece(6, 0, 1), Piece(9, 14, 3), Piece(14, 6, 0))
    private val nodeB = listOf(Piece(0, 6, 0), Piece(6, 14, 1), Piece(9, 0, 3), Piece(14, 9, 2))
    private val symbols =
        listOf(
            Piece(1, 2, 3),
            Piece(1, 10, 3),
            Piece(2, 1, 2),
            Piece(2, 14, 0),
            Piece(10, 1, 2),
            Piece(10, 14, 0),
            Piece(14, 2, 1),
            Piece(14, 10, 1),
        )

    private fun GameTestScope.addTemplates(deps: Deps) {
        val collision = deps.collision
        val origins = ArrayList<Pair<Int, Int>>()
        for (offset in listOf(0, 8)) {
            for (chunkX in listOf(232, 234, 236)) {
                for (chunkZ in listOf(704, 706, 708, 710)) {
                    origins += (chunkX + offset) to chunkZ
                }
            }
            origins += (238 + offset) to 708
            origins += (238 + offset) to 710
        }
        for ((chunkX, chunkZ) in origins) {
            for (level in 0..3) {
                for (x in 0 until 2) {
                    for (z in 0 until 2) {
                        collision.allocateIfAbsent((chunkX + x) * 8, (chunkZ + z) * 8, level)
                    }
                }
            }
            val corrupted = chunkX >= 240
            fun put(piece: Piece, type: LocType) {
                val coords = CoordGrid(chunkX * 8 + piece.x, chunkZ * 8 + piece.z, 1)
                placeMapLoc(coords, type, LocShape.CentrepieceStraight, LocAngle[piece.angle])
            }
            nodeA.forEach {
                put(it, if (corrupted) gauntlet_locs.node_a_hm else gauntlet_locs.node_a)
            }
            nodeB.forEach {
                put(it, if (corrupted) gauntlet_locs.node_b_hm else gauntlet_locs.node_b)
            }
            symbols.forEach {
                put(it, if (corrupted) gauntlet_locs.symbol_hm else gauntlet_locs.symbol)
            }
        }
        collision.allocateIfAbsent(Gauntlet.LOBBY.x, Gauntlet.LOBBY.z, Gauntlet.LOBBY.level)
    }

    /** alice and bob are teammates; alice is briefed and has completed the Gauntlet once. */
    private fun GameTestState.runNodesTest(
        pick: (Int) -> Int = { 0 },
        body: GameTestScope.(Deps, Player) -> Unit,
    ) =
        runInjectedGameTest(Deps::class, NodesModule) { deps ->
            addTemplates(deps)
            deps.gauntlet.pick = pick
            val alice = player
            alice.username = "alice"
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.bryn, 1)
            body(deps, alice)
        }

    /** All locs of a room on one side that are of [types]. */
    private fun Deps.onSide(
        run: GauntletRun,
        room: GauntletRoom,
        side: Side,
        types: List<LocType>,
    ): List<LocInfo> {
        val sw = run.region.southWestZone
        val ids = types.map { it.id }.toSet()
        val found = ArrayList<LocInfo>()
        for (dx in 0 until 2) {
            for (dz in 0 until 2) {
                val zone = ZoneKey(sw.x + room.x * 2 + dx, sw.z + room.z * 2 + dz, 1)
                for (loc in locRepo.findAll(zone)) {
                    // Static (unlit) locs come back at their template angle; the room is turned.
                    val angle =
                        if (loc.id in unlitIds) (loc.angleId + room.rotation) and 3 else loc.angleId
                    if (
                        loc.id in ids &&
                            nodes.sideOf(run, room, loc.coords, locTypes.getValue(loc.id), angle) ==
                                side
                    ) {
                        found += loc
                    }
                }
            }
        }
        return found
    }

    private val unlit = listOf(gauntlet_locs.node_a, gauntlet_locs.node_b, gauntlet_locs.symbol)
    private val unlitIds by lazy { unlit.map { it.id }.toSet() }
    private val lit =
        listOf(gauntlet_locs.node_a_lit, gauntlet_locs.node_b_lit, gauntlet_locs.symbol_lit)

    @Test
    fun GameTestState.`each side of a room has two nodes and two symbols`() =
        runNodesTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val room = run.layout.start
            // The start room's side towards the boss room is lit already; the others are not.
            for (side in Side.entries) {
                val dark = deps.onSide(run, room, side, unlit).size
                val glowing = deps.onSide(run, room, side, lit).size
                if (run.layout.room(room.x + side.dx, room.z + side.dz) === run.layout.boss) {
                    assertEquals(0, dark, "$side")
                    assertEquals(4, glowing, "$side")
                } else {
                    assertEquals(4, dark, "$side")
                    assertEquals(0, glowing, "$side")
                }
            }
        }

    @Test
    fun GameTestState
        .`lighting a node draws the room beyond and lights the passage on both sides`() =
        runNodesTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val start = run.layout.start
            val side =
                Side.entries.first { s ->
                    val next = run.layout.room(start.x + s.dx, start.z + s.dz)
                    next != null && next !== run.layout.boss
                }
            val next = checkNotNull(run.layout.room(start.x + side.dx, start.z + side.dz))
            assertTrue(!next.lit)
            val node = deps.onSide(run, start, side, listOf(gauntlet_locs.node_a)).first()
            val type = deps.locTypes.getValue(node.id)

            assertEquals(
                Gauntlet.LightResult.Lit,
                deps.gauntlet.lightNode(alice, node.coords, type, node.angleId),
            )

            assertTrue(next.lit)
            assertEquals(1, alice.vars[gauntlet_varbits.room_found[next.index]])
            assertTrue(alice.pendingRegionRebuild)
            for ((room, onSide) in listOf(start to side, next to side.opposite)) {
                assertEquals(4, deps.onSide(run, room, onSide, lit).size, "$room $onSide")
                assertEquals(0, deps.onSide(run, room, onSide, unlit).size, "$room $onSide")
            }
            // The next room's other sides are still dark.
            for (other in Side.entries.filter { it != side.opposite }) {
                assertEquals(0, deps.onSide(run, next, other, lit).size, "$other")
            }
            // Lighting the same node again does nothing.
            assertEquals(
                Gauntlet.LightResult.AlreadyLit,
                deps.gauntlet.lightNode(alice, node.coords, type, node.angleId),
            )
        }

    @Test
    fun GameTestState.`a turned room lights the same side the player lit from`() =
        runNodesTest(pick = { n -> if (n == 4) 1 else 0 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val start = run.layout.start
            // East was picked for the start side; the room beyond it is an inside room, turned.
            val east = checkNotNull(run.layout.room(start.x + 1, start.z))
            assertEquals(1, east.rotation)
            val node = deps.onSide(run, start, Side.East, listOf(gauntlet_locs.node_b)).first()
            deps.gauntlet.lightNode(
                alice,
                node.coords,
                deps.locTypes.getValue(node.id),
                node.angleId,
            )

            // `findAll` gives a static loc its template angle; once a passage of the turned room is
            // lit its locs are drawn at that angle plus the room's rotation.
            val onward =
                Side.entries.first { s ->
                    val beyond = run.layout.room(east.x + s.dx, east.z + s.dz)
                    s != Side.West && beyond != null && !beyond.lit
                }
            val templateAngles =
                deps.onSide(run, east, onward, unlit).associate { it.coords to it.angleId }
            val onwardNode = deps.onSide(run, east, onward, listOf(gauntlet_locs.node_a)).first()
            deps.gauntlet.lightNode(
                alice,
                onwardNode.coords,
                deps.locTypes.getValue(onwardNode.id),
                onwardNode.angleId,
            )
            val litNow = deps.onSide(run, east, onward, lit)
            assertEquals(templateAngles.keys, litNow.map { it.coords }.toSet())
            for (loc in litNow) {
                val expected = (templateAngles.getValue(loc.coords) + east.rotation) and 3
                assertEquals(expected, loc.angleId, "angle at ${loc.coords}")
            }

            assertEquals(4, deps.onSide(run, east, Side.West, lit).size)
            assertEquals(0, deps.onSide(run, east, Side.West, unlit).size)
            // The two sides that were not lit are still dark.
            assertEquals(
                8,
                deps.onSide(run, east, Side.North, unlit).size +
                    deps.onSide(run, east, Side.East, unlit).size +
                    deps.onSide(run, east, Side.South, unlit).size,
            )
        }

    @Test
    fun GameTestState.`nodes need the sceptre and the maze's edge has nothing beyond`() =
        runNodesTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val start = run.layout.start
            val side =
                Side.entries.first { s ->
                    val next = run.layout.room(start.x + s.dx, start.z + s.dz)
                    next != null && next !== run.layout.boss
                }
            val node = deps.onSide(run, start, side, listOf(gauntlet_locs.node_a)).first()
            val type = deps.locTypes.getValue(node.id)

            alice.worn.fillNulls()
            assertEquals(
                Gauntlet.LightResult.NoSceptre,
                deps.gauntlet.lightNode(alice, node.coords, type, node.angleId),
            )
            assertTrue(run.layout.all().count { it.lit } == 2)
        }

    @Test
    fun GameTestState.`the teleport crystal returns a player to the start room once`() =
        runNodesTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))

            // In the start room it does nothing and is kept.
            assertEquals(
                Gauntlet.CrystalResult.InStartRoom,
                deps.gauntlet.useTeleportCrystal(alice),
            )
            assertTrue(GauntletMode.Regular.teleportCrystal in alice.inv)

            // Elsewhere it takes a player back and is used up.
            alice.coords = run.roomTile(run.layout.boss, 3, 3)
            assertEquals(Gauntlet.CrystalResult.Teleported, deps.gauntlet.useTeleportCrystal(alice))
            assertEquals(run.layout.start, run.roomAt(alice.coords))
            assertTrue(GauntletMode.Regular.teleportCrystal !in alice.inv)

            // Once the fight has begun it is no help.
            alice.invDel(alice.inv, GauntletMode.Regular.teleportCrystal, 1)
            deps.gauntlet.startBossPhase(run, forced = false)
            assertEquals(Gauntlet.CrystalResult.BossPhase, deps.gauntlet.useTeleportCrystal(alice))
        }

    @Test
    fun GameTestState.`the corrupted maze uses the corrupted nodes`() =
        runNodesTest { deps, alice ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.done, 1)
            deps.gauntlet.enter(alice, GauntletMode.Corrupted)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val room = run.layout.start
            val towardsBoss =
                Side.entries.first {
                    run.layout.room(room.x + it.dx, room.z + it.dz) === run.layout.boss
                }

            val litHm =
                listOf(
                    gauntlet_locs.node_a_lit_hm,
                    gauntlet_locs.node_b_lit_hm,
                    gauntlet_locs.symbol_lit_hm,
                )
            assertEquals(4, deps.onSide(run, room, towardsBoss, litHm).size)
            assertEquals(0, deps.onSide(run, room, towardsBoss, lit).size)
        }
}
