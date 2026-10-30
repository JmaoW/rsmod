package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** Path of Apmeken: the baboon waves and Apmeken's Sight, then Ba-Ba. */
class ApmekenPathTest {
    private fun GameTestState.runPuzzle(
        body: GameTestScope.(ToaTestRun, ApmekenPuzzleRuntime) -> Unit
    ) =
        runRoomTest(ToaRoom.ApmekenPuzzle, ApmekenScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3808, 5280, 0))
            tickRoom(run)
            body(run, run.runtime() as ApmekenPuzzleRuntime)
        }

    private fun GameTestState.runBaba(body: GameTestScope.(ToaTestRun, BaBaRuntime) -> Unit) =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5408, 0))
            tickRoom(run)
            val room = run.runtime() as BaBaRuntime
            room.fightEnabled = false
            body(run, room)
        }

    @Test
    fun GameTestState.`the first wave comes a few ticks after the start`() =
        runPuzzle { run, room ->
            room.start()
            assertEquals(0, room.baboonCount)
            tickRoom(run, ApmekenPuzzleRuntime.FIRST_WAVE_DELAY + 1)
            assertEquals(1, room.wave)
            assertEquals(3, room.baboonCount, "wave one is two brawlers and a shaman")
        }

    @Test
    fun GameTestState.`clearing every wave wins the room`() = runPuzzle { run, room ->
        room.pickSpecial = { 0 }
        room.start()
        var guard = 0
        while (!room.isCompleted && guard++ < 40) {
            tickRoom(run, ApmekenPuzzleRuntime.FIRST_WAVE_DELAY)
            for (npc in room.baboonNpcs()) {
                kill(run, npc)
            }
        }
        assertTrue(room.isCompleted, "eight waves cleared")
        assertEquals(ApmekenPuzzleRuntime.WAVE_COUNT, room.wave)
    }

    @Test
    fun GameTestState.`the first player into the room holds the sight`() = runPuzzle { run, room ->
        room.start()
        tickRoom(run, 3)
        assertEquals(run.alice, room.sight)
    }

    @Test
    fun GameTestState.`neutralised vents spare the party and missed ones hurt it`() =
        runPuzzle { run, room ->
            room.nextHazard = { ApmekenPuzzleRuntime.Hazard.Vents }
            room.start()
            tickRoom(run, 3)
            room.sense(listOf(run.alice))
            run.alice.invAdd(run.alice.inv, apmeken_objs.potion, 5)
            run.stand(run.alice, ApmekenPuzzleRuntime.VENTS[0])
            tickRoom(run)
            room.pour(run.alice)
            val before = run.alice.hitpoints
            room.judge(ApmekenPuzzleRuntime.Hazard.Vents, listOf(run.alice))
            tickRoom(run, 2)
            assertEquals(before, run.alice.hitpoints, "one vent is all a solo party needs")

            room.sense(listOf(run.alice))
            room.judge(ApmekenPuzzleRuntime.Hazard.Vents, listOf(run.alice))
            tickRoom(run, 2)
            assertTrue(run.alice.hitpoints < before, "the fumes ignite")
        }

    @Test
    fun GameTestState.`a support needs a hammer and repairing a sound one hurts`() =
        runPuzzle { run, room ->
            room.nextHazard = { ApmekenPuzzleRuntime.Hazard.Vents }
            room.start()
            tickRoom(run, 3)
            room.repair(run.alice, ApmekenPuzzleRuntime.ROOFS[0])
            val before = run.alice.hitpoints
            run.alice.invAdd(run.alice.inv, apmeken_objs.hammer, 1)
            room.repair(run.alice, ApmekenPuzzleRuntime.ROOFS[0])
            tickRoom(run, 2)
            assertTrue(run.alice.hitpoints < before, "nothing was wrong with the roof")
        }

    @Test
    fun GameTestState.`the weak style always lands its max hit on a baboon`() =
        runPuzzle { run, room ->
            room.pickSpecial = { 0 }
            room.start()
            tickRoom(run, ApmekenPuzzleRuntime.FIRST_WAVE_DELAY + 1)
            val brawler = room.baboonNpcs().first { it.type.id == apmeken_npcs.brawler.id }
            VarPlayerIntMapSetter.set(run.alice, varps.com_maxhit, 17)
            val magic = TestHits.hit(HitType.Magic, 3)
            room.modifyHit(brawler, magic, run.alice)
            assertEquals(17, magic.damage)
            val ranged = TestHits.hit(HitType.Ranged, 3)
            room.modifyHit(brawler, ranged, run.alice)
            assertEquals(3, ranged.damage)
        }

    @Test
    fun GameTestState.`ba-ba has the party's health and shows it at the top`() =
        runBaba { run, room ->
            val boss = checkNotNull(room.baba)
            assertEquals(ToaScaling.hitpoints(380, run.raid.raidLevel, 1, 0), boss.hitpoints)
            room.start()
            tickRoom(run)
            assertEquals(baba_npcs.baba.id, run.alice.vars[ToaBossBarVars.npc])
        }

    @Test
    fun GameTestState.`the boulders roll at two thirds of her health`() = runBaba { run, room ->
        room.start()
        val boss = checkNotNull(room.baba)
        boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
        tickRoom(run, 30)
        assertTrue(room.inBoulderPhase || room.boulderCount > 0, "the boulders come")
        assertTrue(room.boulderCount > 0)
    }

    @Test
    fun GameTestState.`ranged always lands its max hit on the cracked boulder`() =
        runBaba { run, room ->
            room.start()
            val boss = checkNotNull(room.baba)
            boss.hitpoints = boss.baseHitpointsLvl * 60 / 100
            tickRoom(run, 16)
            val cracked =
                room.boulderNpcs().firstOrNull { it.type.id == baba_npcs.boulder_cracked.id }
            assertNotNull(cracked)
            VarPlayerIntMapSetter.set(run.alice, varps.com_maxhit, 30)
            val hit = TestHits.hit(HitType.Ranged, 2)
            room.modifyHit(checkNotNull(cracked), hit, run.alice)
            assertEquals(30, hit.damage)
        }

    @Test
    fun GameTestState.`ba-ba's next boulder phase and death wait for the current boulders`() =
        runBaba { run, room ->
            room.start()
            val boss = checkNotNull(room.baba)
            val max = boss.baseHitpointsLvl
            boss.hitpoints = max * 60 / 100
            tickRoom(run)
            assertTrue(room.inBoulderPhase)
            assertEquals(0, room.landingDamage(boss, max), "held until her special begins")
            var held = 0
            while (room.holdsThreshold && held++ < 10) {
                tickRoom(run)
            }
            assertFalse(room.holdsThreshold, "she leaps for her throne")
            val lower = max * 33 / 100
            assertEquals(boss.hitpoints - lower, room.landingDamage(boss, max), "down to 33%")
            boss.hitpoints = lower
            tickRoom(run)
            assertTrue(room.inBoulderPhase, "the first boulders still roll")
            assertEquals(0, room.landingDamage(boss, 50), "no lower until they stop")
            // Out of the first boulder phase, the second begins at once.
            var waited = 0
            while (room.boulderPhases < 2 && waited++ < 150) {
                tickRoom(run)
                run.alice.stats[stats.hitpoints] = 99
            }
            assertEquals(2, room.boulderPhases)
            held = 0
            while (room.holdsThreshold && held++ < 10) {
                tickRoom(run)
                run.alice.stats[stats.hitpoints] = 99
            }
            assertTrue(room.inBoulderPhase)
            assertEquals(boss.hitpoints - 1, room.landingDamage(boss, max), "1 hp in the last one")
            boss.hitpoints = 1
            room.hitpointsGone(boss)
            assertFalse(room.isCompleted, "she cannot die while her boulders roll")
            waited = 0
            while (room.inBoulderPhase && waited++ < 150) {
                tickRoom(run)
                run.alice.stats[stats.hitpoints] = 99
            }
            assertFalse(room.inBoulderPhase)
            // Npc health creeps back up by one now and then: pin it for the check.
            boss.hitpoints = 1
            assertEquals(1, room.landingDamage(boss, 50), "then the last hitpoint can go")
        }

    @Test
    fun GameTestState.`a wipe brings ba-ba back at full health`() = runBaba { run, room ->
        room.start()
        val first = checkNotNull(room.baba)
        first.hitpoints = 10
        room.reset()
        val second = checkNotNull(room.baba)
        assertFalse(first === second)
        assertEquals(second.baseHitpointsLvl, second.hitpoints)
        assertFalse(room.isStarted)
    }
}
