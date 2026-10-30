package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.game.process.player.BuildAreaAnchors
import org.rsmod.api.game.process.player.PlayerBuildAreaProcessor
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/** Path of Scabaras: the four chambers and the matching tiles, then Kephri. */
class ScabarasPathTest {
    private fun GameTestState.runPuzzle(
        body: GameTestScope.(ToaTestRun, ScabarasPuzzleRuntime) -> Unit
    ) =
        runRoomTest(ToaRoom.ScabarasPuzzle, ScabarasScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3545, 5280, 0))
            tickRoom(run)
            body(run, run.runtime() as ScabarasPuzzleRuntime)
        }

    private fun GameTestState.runKephri(body: GameTestScope.(ToaTestRun, KephriRuntime) -> Unit) =
        runRoomTest(ToaRoom.ScabarasBoss, KephriScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3545, 5402, 0))
            tickRoom(run)
            val room = run.runtime() as KephriRuntime
            room.fightEnabled = false
            body(run, room)
        }

    /** Solves the puzzle in [index]'s chamber by hand (the room knows the answers). */
    private fun GameTestScope.solveChamber(
        run: ToaTestRun,
        room: ScabarasPuzzleRuntime,
        puzzle: ScabarasPuzzleRuntime.Puzzle,
    ) {
        val base = room.baseOf(puzzle)
        when (puzzle) {
            ScabarasPuzzleRuntime.Puzzle.Light -> {
                // A flip changes only its own plate: flip every unlit one.
                for ((index, offset) in ScabarasPuzzleRuntime.LIGHT_OFFSETS.withIndex()) {
                    if (!room.isLit(index)) {
                        room.flip(run.alice, base.translate(offset.first, offset.second))
                        tickRoom(run, ScabarasPuzzleRuntime.FLIP_TICKS + 1)
                    }
                }
            }
            ScabarasPuzzleRuntime.Puzzle.Sum -> {
                var left = room.sumGoal
                while (left > 0 && !room.isSolved(puzzle)) {
                    val value = minOf(9, left)
                    val (dx, dz) = ScabarasPuzzleRuntime.SUM_OFFSETS[value - 1][0]
                    room.stepOn(run.alice, base.translate(dx, dz))
                    left -= value
                }
            }
            ScabarasPuzzleRuntime.Puzzle.Pillar -> {
                for (index in room.obeliskAnswer) {
                    room.hitObelisk(run.alice, index)
                }
            }
            ScabarasPuzzleRuntime.Puzzle.Memory -> {
                room.pressButton(run.alice)
                tickRoom(run, ScabarasPuzzleRuntime.SEQUENCE_LENGTH + 1)
                val sequence = room.memorySequence() ?: return
                for (index in sequence) {
                    val (dx, dz) = ScabarasPuzzleRuntime.MEMORY_OFFSETS[index]
                    room.stepOn(run.alice, base.translate(dx, dz))
                }
            }
        }
    }

    @Test
    fun GameTestState.`an east chamber waits for the west one on the other half`() =
        runPuzzle { run, room ->
            room.start()
            tickRoom(run)
            val east = room.rotation[1]
            val west = room.rotation[2]
            solveChamber(run, room, east)
            assertFalse(room.isSolved(east), "it won't function yet")
            solveChamber(run, room, west)
            assertTrue(room.isSolved(west))
            solveChamber(run, room, east)
            assertTrue(room.isSolved(east))
        }

    @Test
    fun GameTestState.`all four chambers and the matching tiles win the room`() =
        runPuzzle { run, room ->
            room.start()
            tickRoom(run)
            for (index in listOf(0, 2, 1, 3)) {
                solveChamber(run, room, room.rotation[index])
            }
            for (puzzle in ScabarasPuzzleRuntime.Puzzle.entries) {
                assertTrue(room.isSolved(puzzle), "$puzzle")
            }
            val north = room.matchKeys(north = true)
            val south = room.matchKeys(north = false)
            for (key in 0 until 9) {
                if (room.isCompleted) break
                val n = ScabarasPuzzleRuntime.MATCH_OFFSETS[north.indexOf(key)]
                val s = ScabarasPuzzleRuntime.MATCH_OFFSETS[south.indexOf(key)]
                room.turnTile(
                    run.alice,
                    ScabarasPuzzleRuntime.MATCH_BASES[1].translate(n.first, n.second),
                )
                room.turnTile(
                    run.alice,
                    ScabarasPuzzleRuntime.MATCH_BASES[0].translate(s.first, s.second),
                )
                tickRoom(run, 2)
            }
            assertTrue(room.isCompleted)
        }

    @Test
    fun GameTestState.`scarabs come to both halves of the room`() = runPuzzle { run, room ->
        room.start()
        tickRoom(run, ScabarasPuzzleRuntime.SCARAB_FIRST + 1)
        assertEquals(2, room.scarabCount)
    }

    @Test
    fun GameTestState.`kephri is dazed when her health runs out and her children come`() =
        runKephri { run, room ->
            room.start()
            val boss = checkNotNull(room.kephri)
            val landed = room.landingDamage(boss, boss.hitpoints + 50)
            assertEquals(boss.hitpoints - 1, landed)
            boss.hitpoints = 1
            tickRoom(run)
            assertTrue(room.isDazed)
            assertEquals(1, room.phase)
            assertEquals(0, room.landingDamage(boss, 50), "a hit still in flight does nothing")
            tickRoom(run)
            assertEquals(1, room.phase, "and does not down her a second time")
            tickRoom(run, 10)
            assertTrue(room.swarmCount > 0)
            assertEquals(1, room.overlordCount, "a spitting scarab joins")
            val dazedHit = TestHits.hit(HitType.Melee, 30)
            room.modifyHit(boss, dazedHit, run.alice)
            assertEquals(0, dazedHit.damage, "nothing hurts her while she is dazed")
        }

    @Test
    fun GameTestState.`kephri wakes with what her swarms restored`() = runKephri { run, room ->
        room.start()
        val boss = checkNotNull(room.kephri)
        room.landingDamage(boss, boss.hitpoints)
        boss.hitpoints = 1
        tickRoom(run, 120)
        assertFalse(room.isDazed)
        assertTrue(boss.hitpoints > 1, "the swarms that reached her healed her")
    }

    @Test
    fun GameTestState.`the third down is the last stand and the fourth her death`() =
        runKephri { run, room ->
            room.start()
            val boss = checkNotNull(room.kephri)
            repeat(2) { round ->
                room.landingDamage(boss, boss.hitpoints)
                // Her children hurt: keep the player standing through the long wait.
                repeat(12) {
                    tickRoom(run, 10)
                    run.alice.stats[stats.hitpoints] = 99
                }
                assertFalse(
                    room.isDazed,
                    "round $round: phase ${room.phase}, swarms ${room.swarmCount}, hp ${boss.hitpoints} " +
                        room.debugIntermission,
                )
            }
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run)
            assertEquals(3, room.phase)
            assertEquals(kephri_npcs.enraged.id, boss.visType.id)
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run)
            assertTrue(room.isCompleted)
            assertTrue(ToaPath.Scabaras in run.raid.completed)
            assertNotNull(run.raid)
        }

    // What the RuneLite Tombs of Amascut plugin reads (see the reference notes on it).
    // ------------------------------------------------------------------------------------------

    /** Solves the west chamber an east [puzzle] waits for, if it is in an east one. */
    private fun GameTestScope.unlock(
        run: ToaTestRun,
        room: ScabarasPuzzleRuntime,
        puzzle: ScabarasPuzzleRuntime.Puzzle,
    ) {
        when (room.rotation.indexOf(puzzle)) {
            1 -> solveChamber(run, room, room.rotation[2])
            3 -> solveChamber(run, room, room.rotation[0])
        }
    }

    @Test
    fun GameTestState.`the room is loaded at the real game's scene offset`() =
        runPuzzle { run, room ->
            val anchors = BuildAreaAnchors()
            anchors.register { run.deps.raids.sceneAnchor(it.coords) }
            PlayerBuildAreaProcessor(anchors).process(run.alice)
            val area = run.alice.buildArea
            // The plugin looks for each puzzle's north-west plate at one of these scene tiles.
            val starts = setOf(36 to 56, 36 to 44, 53 to 56, 53 to 44)
            for (puzzle in
                listOf(ScabarasPuzzleRuntime.Puzzle.Light, ScabarasPuzzleRuntime.Puzzle.Sum)) {
                val corner = room.baseOf(puzzle).translate(1, 5)
                val tile = run.raid.regionOf(corner)
                val scene = (tile.x - area.x) to (tile.z - area.z)
                assertTrue(scene in starts, "$puzzle corner at $scene")
            }
            val light = room.baseOf(ScabarasPuzzleRuntime.Puzzle.Light).translate(1, 5)
            assertEquals(
                scabaras_locs.light_plate.id,
                room.placedLoc(light, LocShape.GroundDecor)?.id,
            )
            val sum = room.baseOf(ScabarasPuzzleRuntime.Puzzle.Sum).translate(1, 5)
            assertEquals(
                scabaras_locs.sum_plates[8].id,
                room.placedLoc(sum, LocShape.GroundDecor)?.id,
            )
        }

    @Test
    fun GameTestState.`memory glows lie on the floor plate`() = runPuzzle { run, room ->
        room.start()
        tickRoom(run)
        val memory = ScabarasPuzzleRuntime.Puzzle.Memory
        unlock(run, room, memory)
        room.pressButton(run.alice)
        tickRoom(run, ScabarasPuzzleRuntime.SEQUENCE_LENGTH + 1)
        val sequence = checkNotNull(room.memorySequence())
        val base = room.baseOf(memory)
        val tiles =
            sequence.map {
                val (dx, dz) = ScabarasPuzzleRuntime.MEMORY_OFFSETS[it]
                base.translate(dx, dz)
            }
        room.stepOn(run.alice, tiles[0])
        assertEquals(
            scabaras_locs.memory_stepped.id,
            room.placedLoc(tiles[0], LocShape.CentrepieceStraight)?.id,
        )
        assertEquals(
            scabaras_locs.memory_plate.id,
            room.placedLoc(tiles[0], LocShape.GroundDecor)?.id,
        )
        for (tile in tiles.drop(1)) {
            room.stepOn(run.alice, tile)
        }
        assertTrue(room.isSolved(memory))
        for ((dx, dz) in ScabarasPuzzleRuntime.MEMORY_OFFSETS) {
            val tile = base.translate(dx, dz)
            assertEquals(
                scabaras_locs.memory_shown.id,
                room.placedLoc(tile, LocShape.CentrepieceStraight)?.id,
            )
            assertEquals(
                scabaras_locs.memory_plate.id,
                room.placedLoc(tile, LocShape.GroundDecor)?.id,
            )
        }
    }

    @Test
    fun GameTestState.`matching tiles show the matching symbols and their glows`() =
        runPuzzle { run, room ->
            room.start()
            tickRoom(run)
            for (index in listOf(0, 2, 1, 3)) {
                solveChamber(run, room, room.rotation[index])
            }
            val north = room.matchKeys(north = true)
            val south = room.matchKeys(north = false)
            fun northTile(key: Int): CoordGrid {
                val (dx, dz) = ScabarasPuzzleRuntime.MATCH_OFFSETS[north.indexOf(key)]
                return ScabarasPuzzleRuntime.MATCH_BASES[1].translate(dx, dz)
            }
            fun southTile(key: Int): CoordGrid {
                val (dx, dz) = ScabarasPuzzleRuntime.MATCH_OFFSETS[south.indexOf(key)]
                return ScabarasPuzzleRuntime.MATCH_BASES[0].translate(dx, dz)
            }
            val key =
                (0 until 9).first {
                    room.placedLoc(northTile(it), LocShape.CentrepieceStraight) == null
                }
            room.turnTile(run.alice, northTile(key))
            assertEquals(
                scabaras_locs.match_symbols[key].id,
                room.placedLoc(northTile(key), LocShape.GroundDecor)?.id,
            )
            room.turnTile(run.alice, southTile(key))
            tickRoom(run, 2)
            for (tile in listOf(northTile(key), southTile(key))) {
                assertEquals(
                    scabaras_locs.match_glows[key].id,
                    room.placedLoc(tile, LocShape.CentrepieceStraight)?.id,
                )
            }
        }

    @Test
    fun GameTestState.`challenge messages use the names and times the plugin reads`() =
        runKephri { _, room ->
            assertEquals("Kephri", room.challengeName())
            val line = room.completionLines(100).single()
            assertTrue(line.startsWith("Challenge complete: Kephri. Duration: "), line)
            assertTrue(line.contains(". Total: <col=ef1020>"), line)
        }
}
