package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

/** Path of Crondis: the palm and its water, then Zebak. */
class CrondisPathTest {
    private fun GameTestState.runPuzzle(
        body: GameTestScope.(ToaTestRun, CrondisPuzzleRuntime) -> Unit
    ) =
        runRoomTest(ToaRoom.CrondisPuzzle, CrondisScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3936, 5276, 0))
            tickRoom(run)
            val room = run.runtime() as CrondisPuzzleRuntime
            room.trapsEnabled = false
            body(run, room)
        }

    private fun GameTestState.runZebak(body: GameTestScope.(ToaTestRun, ZebakRuntime) -> Unit) =
        runRoomTest(ToaRoom.CrondisBoss, ZebakScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3934, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as ZebakRuntime
            room.fightEnabled = false
            body(run, room)
        }

    @Test
    fun GameTestState.`a solo palm needs 175 water and grows as it is watered`() =
        runPuzzle { run, room ->
            room.start()
            assertEquals(CrondisPuzzleRuntime.WATER_FIRST, room.goal)
            run.alice.invAdd(run.alice.inv, crondis_objs.container, 1)
            room.fill(run.alice, CrondisPuzzleRuntime.WATERFALLS[0], dry = false)
            assertEquals(100, room.waterOf(run.alice))
            room.waterPalm(run.alice)
            assertEquals(100, room.watered)
            assertEquals(0, room.waterOf(run.alice))
            assertFalse(room.isCompleted)
        }

    @Test
    fun GameTestState.`the palm's health bar shows the water given`() = runPuzzle { run, room ->
        room.start()
        tickRoom(run)
        val alice = run.alice
        assertEquals(crondis_npcs.palm[0].id, alice.vars[ToaBossBarVars.npc])
        assertEquals(1, alice.vars[ToaBossBarBits.boss], "shown as a boss bar")
        assertEquals(0, alice.vars[ToaBossBarBits.hp], "no water yet, not the palm's 1 hp")
        assertEquals(CrondisPuzzleRuntime.WATER_FIRST, alice.vars[ToaBossBarBits.base])
        alice.invAdd(alice.inv, crondis_objs.container, 1)
        room.fill(alice, CrondisPuzzleRuntime.WATERFALLS[0], dry = false)
        room.waterPalm(alice)
        tickRoom(run)
        assertEquals(100, alice.vars[ToaBossBarBits.hp])
        room.reset()
        tickRoom(run)
        assertEquals(-1, alice.vars[ToaBossBarVars.npc], "a reset takes the bar away")
        assertEquals(0, alice.vars[ToaBossBarBits.boss])
    }

    @Test
    fun GameTestState.`a filled waterfall runs dry for a while`() = runPuzzle { run, room ->
        room.start()
        run.alice.invAdd(run.alice.inv, crondis_objs.container, 1)
        val waterfall = CrondisPuzzleRuntime.WATERFALLS[1]
        room.fill(run.alice, waterfall, dry = false)
        room.waterPalm(run.alice)
        room.fill(run.alice, waterfall, dry = false)
        assertEquals(0, room.waterOf(run.alice), "the waterfall is dry")
        room.fill(run.alice, CrondisPuzzleRuntime.WATERFALLS[2], dry = false)
        assertEquals(100, room.waterOf(run.alice), "another one still runs")
    }

    @Test
    fun GameTestState.`watering the palm to its goal wins the room`() = runPuzzle { run, room ->
        room.start()
        run.alice.invAdd(run.alice.inv, crondis_objs.container, 1)
        for (waterfall in CrondisPuzzleRuntime.WATERFALLS.take(2)) {
            room.fill(run.alice, waterfall, dry = false)
            room.waterPalm(run.alice)
        }
        assertTrue(room.isCompleted)
        assertFalse(crondis_objs.container in run.alice.inv, "the container goes with the room")
    }

    @Test
    fun GameTestState.`crocodiles come in waves and drink from a watered palm`() =
        runPuzzle { run, room ->
            room.start()
            run.alice.invAdd(run.alice.inv, crondis_objs.container, 1)
            room.fill(run.alice, CrondisPuzzleRuntime.WATERFALLS[0], dry = false)
            room.waterPalm(run.alice)
            tickRoom(run, CrondisPuzzleRuntime.CROC_WAVE.last + 2)
            assertTrue(room.crocodileCount >= 1)
            tickRoom(run, 80)
            assertTrue(room.watered < 100, "they drank some of it")
        }

    @Test
    fun GameTestState.`zebak's specials queue at the thresholds and he enrages at a quarter`() =
        runZebak { run, room ->
            room.start()
            val boss = checkNotNull(room.zebak)
            boss.hitpoints = boss.baseHitpointsLvl * 80 / 100
            tickRoom(run)
            assertFalse(room.enraged)
            boss.hitpoints = boss.baseHitpointsLvl * 20 / 100
            tickRoom(run)
            assertTrue(room.enraged)
            assertEquals(zebak_npcs.enraged.id, boss.visType.id)
        }

    @Test
    fun GameTestState.`a swimmer cannot hurt zebak and climbs out on the rock steps`() =
        runZebak { run, room ->
            room.start()
            val boss = checkNotNull(room.zebak)
            assertFalse(room.isSwimming(run.alice))
            val hit = TestHits.hit(HitType.Ranged, 20)
            room.modifyHit(boss, hit, run.alice)
            assertEquals(20, hit.damage)
            room.climbOut(run.alice, ZebakRuntime.CLIMBING_ROCKS[0])
            assertFalse(room.isSwimming(run.alice))
        }

    @Test
    fun GameTestState.`zebak appears facing east over the island`() = runZebak { _, room ->
        assertEquals(Direction.East, checkNotNull(room.zebak).respawnDir)
    }

    @Test
    fun GameTestState.`a wave crosses the island edge wall and pushes a player in its path`() =
        runZebak { run, room ->
            room.start()
            val column = ZebakRuntime.WAVE_SOUTH.x + 11
            // The map's invisible wall on the north side of the water row the waves rise from.
            val edge = run.raid.regionOf(CoordGrid(column, ZebakRuntime.WAVE_SOUTH.z, 0))
            run.deps.collision.add(edge.x, edge.z, edge.level, CollisionFlag.WALL_NORTH)
            val start = CoordGrid(column, 5408, 0)
            run.stand(run.alice, start)
            tickRoom(run)
            // The gap sits in the far west columns, so this column has a wave.
            room.spawnWave(south = true, gap = 0, holes = 1)
            val wave = room.waveNpcs().first { run.real(it).x == column }
            tickRoom(run, 3)
            assertTrue(run.real(wave).z > ZebakRuntime.WAVE_SOUTH.z + 1, "it got past the wall")
            tickRoom(run, 12)
            assertTrue(run.real(run.alice).z > start.z, "the player was pushed north")
        }

    @Test
    fun GameTestState.`zebak's death completes the path`() = runZebak { run, room ->
        room.start()
        val boss = checkNotNull(room.zebak)
        room.hitpointsGone(boss)
        tickRoom(run)
        assertTrue(room.isCompleted)
        assertTrue(ToaPath.Crondis in run.raid.completed)
    }

    @Test
    fun GameTestState.`killing enraged zebak completes the path and leaves him in the room`() =
        runZebak { run, room ->
            room.start()
            val boss = checkNotNull(room.zebak)
            boss.hitpoints = boss.baseHitpointsLvl * 20 / 100
            tickRoom(run)
            assertEquals(zebak_npcs.enraged.id, boss.visType.id)
            // The death queue goes by the enraged form: it must reach the room, not the generic
            // npc death (which removed him mid-room).
            kill(run, boss)
            assertTrue(room.isCompleted)
            assertTrue(boss.isSlotAssigned)
        }
}
