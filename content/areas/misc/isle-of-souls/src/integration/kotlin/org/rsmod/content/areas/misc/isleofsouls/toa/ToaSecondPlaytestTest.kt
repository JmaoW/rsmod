package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PoisonVars
import org.rsmod.content.areas.misc.isleofsouls.zulrah.ZulrahVars
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid

/** Fixes from the second 2026-10-29 playtest. */
class ToaSecondPlaytestTest {
    @Test
    fun GameTestState
        .`beating a path boss restores health and prayer and cures poison and venom`() =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class) { run ->
            val room = run.runtime() as BaBaRuntime
            room.start()
            val alice = run.alice
            alice.stats[stats.hitpoints] = 1
            alice.stats[stats.prayer] = 0
            VarPlayerIntMapSetter.set(alice, PoisonVars.damage, 6)
            VarPlayerIntMapSetter.set(alice, ZulrahVars.venom, 6)
            room.complete()
            assertEquals(alice.statBase(stats.hitpoints), alice.stat(stats.hitpoints))
            assertEquals(alice.statBase(stats.prayer), alice.stat(stats.prayer))
            assertEquals(0, alice.vars[PoisonVars.damage], "poison cured")
            assertEquals(0, alice.vars[ZulrahVars.venom], "venom cured")
        }

    @Test
    fun GameTestState.`the obelisk falls even when two hits in flight take its last hitpoint`() =
        runRoomTest(ToaRoom.WardensFirst, WardensScript::class, PlayerHitScript::class) { run ->
            run.stand(run.alice, CoordGrid(3800, 5160, 1))
            tickRoom(run)
            val room = run.runtime() as WardensRuntime
            room.fightEnabled = false
            room.setReady(run.alice)
            tickRoom(run, 10)
            val obelisk = checkNotNull(room.obelisk)
            // Both hits passed the queue-time check; together they ran its health out.
            obelisk.hitpoints = 0
            room.hitpointsGone(obelisk)
            tickRoom(run, 3)
            assertEquals(1, room.phase, "the Warden comes out")
            assertTrue(obelisk.hitpoints >= 1)
        }

    @Test
    fun GameTestState.`akkha crawls rather than stepping and waiting`() =
        runRoomTest(ToaRoom.HetBoss, AkkhaScript::class) { run ->
            val room = run.runtime() as AkkhaRuntime
            assertEquals(MoveSpeed.Crawl, checkNotNull(room.akkha).defaultMoveSpeed)
        }

    @Test
    fun GameTestState.`a hit landing on dazed kephri does nothing`() =
        runRoomTest(ToaRoom.ScabarasBoss, KephriScript::class, PlayerHitScript::class) { run ->
            val room = run.runtime() as KephriRuntime
            room.start()
            val boss = checkNotNull(room.kephri)
            room.landingDamage(boss, boss.hitpoints)
            tickRoom(run)
            assertTrue(room.isDazed)
            // A hit thrown before she was dazed lands now: it must not down her again.
            boss.hitpoints = 0
            room.hitpointsGone(boss)
            tickRoom(run)
            assertEquals(1, room.phase)
            assertFalse(boss.hitpoints < 1)
        }
}
