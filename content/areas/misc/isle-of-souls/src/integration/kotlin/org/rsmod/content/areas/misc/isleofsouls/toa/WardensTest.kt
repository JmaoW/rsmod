package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.varps
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** Amascut's Promise: the obelisk, the Warden and its core, and the final Warden. */
class WardensTest {
    private fun GameTestState.runFirst(body: GameTestScope.(ToaTestRun, WardensRuntime) -> Unit) =
        runRoomTest(ToaRoom.WardensFirst, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5160, 1))
            tickRoom(run)
            val room = run.runtime() as WardensRuntime
            room.fightEnabled = false
            body(run, room)
        }

    private fun GameTestState.runFinal(
        body: GameTestScope.(ToaTestRun, WardensFinalRuntime) -> Unit
    ) =
        runRoomTest(ToaRoom.WardensFinal, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3936, 5162, 1))
            tickRoom(run, 2)
            val room = run.runtime() as WardensFinalRuntime
            room.fightEnabled = false
            body(run, room)
        }

    @Test
    fun GameTestState.`the fight begins once everyone is ready`() = runFirst { run, room ->
        assertTrue(!room.isStarted)
        room.setReady(run.alice)
        assertTrue(room.isStarted)
        assertEquals(wardens_npcs.obelisk.id, checkNotNull(room.obelisk).visType.id)
    }

    @Test
    fun GameTestState.`orbs charge the wardens' platforms`() = runFirst { run, room ->
        room.setReady(run.alice)
        tickRoom(run, 20)
        assertTrue(room.percentOf(Warden.Elidinis) > 0)
        assertTrue(room.percentOf(Warden.Tumeken) > 0)
    }

    @Test
    fun GameTestState.`the obelisk falls and the more charged warden rises`() =
        runFirst { run, room ->
            room.setReady(run.alice)
            tickRoom(run, 10)
            val obelisk = checkNotNull(room.obelisk)
            room.modifyHit(obelisk, TestHits.hit(HitType.Ranged, obelisk.hitpoints + 10), run.alice)
            tickRoom(run, 3)
            assertEquals(1, room.phase)
            assertNotNull(room.fighting)
            assertNotNull(room.fightingWarden)
            assertEquals(checkNotNull(room.fightingWarden).other, run.raid.finalWarden)
        }

    @Test
    fun GameTestState.`only the unprayed style breaks the shield, and the core passes five fold`() =
        runFirst { run, room ->
            room.setReady(run.alice)
            val obelisk = checkNotNull(room.obelisk)
            room.modifyHit(obelisk, TestHits.hit(HitType.Ranged, obelisk.hitpoints + 10), run.alice)
            tickRoom(run, 25)
            val warden = checkNotNull(room.fighting)
            val magicWeak = room.fightingWarden == Warden.Elidinis
            val wrong = TestHits.hit(if (magicWeak) HitType.Ranged else HitType.Magic, 30)
            room.modifyHit(warden, wrong, run.alice)
            assertEquals(0, wrong.damage)
            val melee = TestHits.hit(HitType.Melee, 30)
            room.modifyHit(warden, melee, run.alice)
            assertEquals(0, melee.damage, "it always prays melee")
            val right =
                TestHits.hit(if (magicWeak) HitType.Magic else HitType.Ranged, warden.hitpoints + 5)
            room.modifyHit(warden, right, run.alice)
            tickRoom(run, 10)
            assertTrue(room.isDown)
            val core = checkNotNull(room.core)
            val before = warden.hitpoints
            VarPlayerIntMapSetter.set(run.alice, varps.com_maxhit, 40)
            val coreHit = TestHits.hit(HitType.Melee, 5)
            room.modifyHit(core, coreHit, run.alice)
            assertEquals(40, coreHit.damage, "the core shows the hit")
            assertEquals(core.baseHitpointsLvl, core.hitpoints, "and never runs out of health")
            assertEquals(before - 200, warden.hitpoints, "a 40 max hit with melee, five fold")
        }

    @Test
    fun GameTestState.`the final warden sends siphons at eighty percent`() = runFinal { run, room ->
        val boss = checkNotNull(room.warden)
        assertTrue(room.isStarted)
        val hit = TestHits.hit(HitType.Magic, boss.hitpoints)
        room.modifyHit(boss, hit, run.alice)
        assertEquals(boss.hitpoints - boss.baseHitpointsLvl * 80 / 100, hit.damage)
        boss.hitpoints -= hit.damage
        tickRoom(run, 6)
        assertTrue(room.skullsOut)
        assertTrue(room.siphonCount > 0)
        val blocked = TestHits.hit(HitType.Magic, 50)
        room.modifyHit(boss, blocked, run.alice)
        assertEquals(0, blocked.damage, "nothing hurts it while the skulls are out")
    }

    @Test
    fun GameTestState.`at five percent the final warden heals and enrages`() =
        runFinal { run, room ->
            val boss = checkNotNull(room.warden)
            val max = boss.baseHitpointsLvl
            // Past the four siphon thresholds without the siphons (as the tests can).
            repeat(4) {
                room.modifyHit(boss, TestHits.hit(HitType.Magic, boss.hitpoints), run.alice)
            }
            boss.hitpoints = max * 6 / 100
            room.modifyHit(boss, TestHits.hit(HitType.Magic, boss.hitpoints), run.alice)
            tickRoom(run)
            assertEquals(3, room.phase)
            assertTrue(boss.hitpoints >= max * 20 / 100)
        }

    @Test
    fun GameTestState.`the final warden's fall wins the raid through the crystal`() =
        runFinal { run, room ->
            room.skipToEnd()
            assertTrue(room.isCompleted)
            tickRoom(run, WardensFinalRuntime.CRYSTAL_TICKS + 1)
            room.useCrystal()
            assertEquals(RaidStage.Vault, run.raid.stage)
        }
}
