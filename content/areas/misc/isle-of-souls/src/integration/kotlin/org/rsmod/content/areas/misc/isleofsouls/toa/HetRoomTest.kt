package org.rsmod.content.areas.misc.isleofsouls.toa

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Path of Het: the seal, the walls and mirrors, the beam, and winning the room. */
class HetRoomTest {
    private object ToaModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: ToaParties,
        val raids: ToaRaids,
        val rooms: ToaRoomRegistry,
        val collision: CollisionFlagMap,
    )

    private fun GameTestScope.allocate(deps: Deps) {
        for (column in ToaPacking.columns) {
            for (zx in column.realZoneX until column.realZoneX + column.zoneWidth) {
                for (zz in
                    ToaPacking.REAL_ZONE_Z until ToaPacking.REAL_ZONE_Z + ToaPacking.ZONE_ROWS) {
                    for (level in 0 until CoordGrid.LEVEL_COUNT) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
        }
    }

    private class HetRun(
        val deps: Deps,
        val alice: Player,
        val raid: ToaRaid,
        val room: HetPuzzleRuntime,
    ) {
        fun at(x: Int, z: Int) = CoordGrid(x, z, 0)

        fun stand(player: Player, real: CoordGrid) {
            PathingEntityCommon.telejump(player, deps.collision, raid.regionOf(real))
        }
    }

    private fun GameTestState.runHetTest(body: GameTestScope.(HetRun) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ToaModule,
            ToaVaultScript::class,
            HetScript::class,
            NpcHitScript::class,
            PlayerHitScript::class,
        ) { deps ->
            allocate(deps)
            val alice = player
            alice.displayName = "Alice"
            PathingEntityCommon.telejump(alice, deps.collision, ToaLobby.RETURN)
            deps.collision.allocateIfAbsent(ToaLobby.RETURN.x, ToaLobby.RETURN.z, 0)
            deps.parties.create(alice)
            // The moves between rooms fade the screen first; these tests move at once.
            deps.raids.fadeTicks = 0
            deps.raids.enterFromLobby(alice)
            deps.raids.choosePath(alice, ToaPath.Het)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            val room = deps.rooms.ensure(raid, ToaRoom.HetPuzzle) as HetPuzzleRuntime
            body(HetRun(deps, alice, raid, room))
        }

    private fun GameTestScope.tick(run: HetRun, ticks: Int = 1) {
        repeat(ticks) {
            advance()
            run.deps.raids.tick()
            run.deps.rooms.tick()
        }
    }

    @Test
    fun GameTestState.`the seal stands in the room with the health of the party`() =
        runHetTest { run ->
            val seal = checkNotNull(run.room.sealNpc)
            assertEquals(HetMining.sealHitpoints[0], seal.hitpoints)
            assertEquals(het_npcs.seal.id, seal.id)
            assertTrue(run.room.owns(seal))
            assertNotNull(run.room.placedLoc(HetPuzzleRuntime.CASTER))
            assertNotNull(run.room.placedLoc(HetPuzzleRuntime.SHIELD))
            assertFalse(run.room.canDestroySeal)
        }

    @Test
    fun GameTestState.`the walls rise when the challenge starts and settle`() = runHetTest { run ->
        run.room.start()
        assertTrue(run.room.isStarted)
        val layout =
            HetData.layouts.first { layout -> layout.any { it.piece == HetPiece.MirrorMovable } }
        // The room picks any of the five; whichever it picked has pieces standing.
        val standing =
            HetData.layouts.flatten().count { run.room.placedAt(run.at(it.x, it.z)) != null }
        assertTrue(standing > 0)
        assertNotNull(layout)
        tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
        val anyRising =
            HetData.layouts.flatten().any { run.room.placedAt(run.at(it.x, it.z))?.rising == true }
        assertFalse(anyRising)
    }

    @Test
    fun GameTestState.`the seal cannot be hurt until the beam has struck the shielded statue`() =
        runHetTest { run ->
            run.room.start()
            val seal = checkNotNull(run.room.sealNpc)
            val before = seal.hitpoints
            run.room.damageSeal(run.alice, 20)
            assertEquals(before, seal.hitpoints)
        }

    @Test
    fun GameTestState.`mirrors carry the beam round the seal to the shielded statue`() =
        runHetTest { run ->
            run.room.start()
            tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
            run.room.clearLayout()
            // West from the caster, up, east along the top of the seal, then down into the statue.
            run.room.put(run.at(3673, 5280), HetPiece.MirrorMovable, 0)
            run.room.put(run.at(3673, 5283), HetPiece.MirrorMovable, 1)
            run.room.put(run.at(3683, 5283), HetPiece.MirrorMovable, 2)

            run.room.fireBeam()
            tick(run)
            assertTrue(run.room.canDestroySeal)
            assertNotNull(run.room.sealNpc?.transmog)
            // The walls came down while the seal is open.
            assertNull(run.room.placedAt(run.at(3673, 5280)))
        }

    @Test
    fun GameTestState.`a mirror turned the wrong way sends the beam nowhere`() = runHetTest { run ->
        run.room.start()
        tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
        run.room.clearLayout()
        run.room.put(run.at(3673, 5280), HetPiece.MirrorMovable, 2)
        run.room.fireBeam()
        tick(run)
        assertFalse(run.room.canDestroySeal)
    }

    @Test
    fun GameTestState.`a dirty mirror blocks the beam until it is cleaned`() = runHetTest { run ->
        run.room.start()
        tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
        run.room.clearLayout()
        run.room.put(run.at(3673, 5280), HetPiece.MirrorFixed, 0, dirty = true)
        run.room.put(run.at(3673, 5283), HetPiece.MirrorMovable, 1)
        run.room.put(run.at(3683, 5283), HetPiece.MirrorMovable, 2)
        run.room.fireBeam()
        tick(run)
        assertFalse(run.room.canDestroySeal)

        assertTrue(run.room.clean(run.at(3673, 5280)))
        run.room.fireBeam()
        tick(run)
        assertTrue(run.room.canDestroySeal)
    }

    @Test
    fun GameTestState.`a pickaxe hit is a real hit with a health bar on the seal`() =
        runHetTest { run ->
            run.room.start()
            tick(run)
            run.room.weaken()
            val seal = checkNotNull(run.room.sealNpc)
            val before = seal.hitpoints
            run.room.damageSeal(run.alice, 7)
            tick(run, 2)
            assertEquals(before - 7, seal.hitpoints)
        }

    @Test
    fun GameTestState.`mining the weakened seal down wins the room`() = runHetTest { run ->
        run.room.start()
        tick(run)
        run.room.weaken()
        val seal = checkNotNull(run.room.sealNpc)
        var swings = 0
        while (!run.room.isCompleted && swings < 200) {
            // One window is not enough for the whole seal: the beam has to strike again.
            if (!run.room.canDestroySeal) {
                run.room.weaken()
            }
            run.room.damageSeal(run.alice, 7)
            swings++
            // The hit lands a tick later, with its hitsplat and health bar.
            tick(run, 2)
            // The test world has no script that routes hits through the room, so say it by hand.
            if (seal.hitpoints <= 1) {
                run.room.hitpointsGone(seal)
                tick(run)
            }
        }
        assertTrue(run.room.isCompleted)
        assertTrue(swings * 7 >= HetMining.sealHitpoints[0])
        assertTrue(run.deps.raids.isWon(run.raid, ToaRoom.HetPuzzle))
        assertEquals(1, seal.hitpoints)
    }

    @Test
    fun GameTestState.`the seal closes again and the walls come back when the window runs out`() =
        runHetTest { run ->
            run.room.start()
            tick(run)
            run.room.weaken()
            assertTrue(run.room.canDestroySeal)
            tick(run, HetPuzzleRuntime.WEAKENED_TICKS + 1)
            assertFalse(run.room.canDestroySeal)
            assertNull(run.room.sealNpc?.transmog)
            val standing =
                HetData.layouts.flatten().count { run.room.placedAt(run.at(it.x, it.z)) != null }
            assertTrue(standing > 0)
        }

    @Test
    fun GameTestState.`a movable mirror is picked up, put down, turned and pushed`() =
        runHetTest { run ->
            run.room.start()
            tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
            run.room.clearLayout()
            val spot = run.at(3672, 5276)
            run.room.put(spot, HetPiece.MirrorMovable, 0)

            run.room.rotate(spot, clockwise = true)
            assertEquals(1, run.room.placedAt(spot)?.angle)
            run.room.rotate(spot, clockwise = false)
            run.room.rotate(spot, clockwise = false)
            assertEquals(3, run.room.placedAt(spot)?.angle)

            assertTrue(run.room.push(spot, from = run.at(3671, 5276)))
            assertNull(run.room.placedAt(spot))
            val pushed = run.at(3673, 5276)
            assertNotNull(run.room.placedAt(pushed))

            assertTrue(run.room.pickUp(run.alice, pushed))
            assertNull(run.room.placedAt(pushed))
            assertTrue(het_objs.mirror in run.alice.inv)

            run.stand(run.alice, run.at(3670, 5276))
            assertTrue(run.room.placeMirror(run.alice, run.at(3670, 5276)))
            assertFalse(het_objs.mirror in run.alice.inv)
            assertNotNull(run.room.placedAt(run.at(3670, 5276)))
        }

    @Test
    fun GameTestState.`a wall that was mined crumbles and a fixed wall does not`() =
        runHetTest { run ->
            run.room.start()
            tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
            run.room.clearLayout()
            val soft = run.at(3672, 5276)
            val hard = run.at(3672, 5277)
            run.room.put(soft, HetPiece.BreakInner, 0)
            run.room.put(hard, HetPiece.SolidInner, 0)
            assertTrue(run.room.isBreakable(soft))
            assertFalse(run.room.isBreakable(hard))
            assertTrue(run.room.breakWall(soft))
            assertNull(run.room.placedAt(soft))
            assertFalse(run.room.breakWall(hard))
        }

    @Test
    fun GameTestState.`each new layout replaces the last one cleanly`() = runHetTest { run ->
        run.room.start()
        tick(run, HetPuzzleRuntime.RISE_TICKS + 1)
        val all = HetData.layouts.flatten().map { run.at(it.x, it.z) }.toSet()
        val seen = HashSet<Int>()
        repeat(HetData.layouts.size + 1) { rotation ->
            val standing = all.filter { run.room.placedAt(it) != null }.toSet()
            val shown =
                HetData.layouts.indexOfFirst { layout ->
                    layout.map { run.at(it.x, it.z) }.toSet() == standing
                }
            assertTrue(shown >= 0, "rotation $rotation: the pieces standing are not one layout")
            seen += shown
            // Nothing of an earlier layout is left: no loc, no rubble and no collision.
            for (tile in all - standing) {
                assertNull(run.room.placedLoc(tile, LocShape.CentrepieceStraight), "$tile")
                assertNull(run.room.placedLoc(tile, LocShape.CentrepieceDiagonal), "$tile")
                val region = run.raid.regionOf(tile)
                val flags = run.deps.collision[region.x, region.z, region.level]
                assertEquals(0, flags and CollisionFlag.LOC, "rotation $rotation: $tile blocks")
            }
            // Mine one wall, so its rubble has to go with the layout.
            val soft = HetData.layouts[shown].first { it.piece == HetPiece.BreakInner }
            assertTrue(run.room.breakWall(run.at(soft.x, soft.z)))
            run.room.weaken()
            tick(run, HetPuzzleRuntime.WEAKENED_TICKS + HetPuzzleRuntime.RISE_TICKS + 2)
        }
        assertEquals(HetData.layouts.size, seen.size)
    }

    @Test
    fun GameTestState.`a wipe puts the room back with a fresh seal`() = runHetTest { run ->
        run.room.start()
        tick(run)
        run.room.weaken()
        run.room.damageSeal(run.alice, 30)
        run.room.reset()
        assertFalse(run.room.isStarted)
        val seal = checkNotNull(run.room.sealNpc)
        assertEquals(HetMining.sealHitpoints[0], seal.hitpoints)
        assertFalse(run.room.canDestroySeal)
        assertEquals(
            0,
            HetData.layouts.flatten().count { run.room.placedAt(run.at(it.x, it.z)) != null },
        )
    }
}
