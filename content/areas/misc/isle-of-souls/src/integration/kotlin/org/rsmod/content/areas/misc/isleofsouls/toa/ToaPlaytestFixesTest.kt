package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

/** Fixes from the 2026-10-29 playtest: boss drops on the floor, Ba-Ba's thresholds. */
class ToaPlaytestFixesTest {
    @Test
    fun GameTestState.`a won boss room drops its book and trophy in front of Osmumten`() =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class) { run ->
            val room = run.runtime() as BaBaRuntime
            room.start()
            room.damageDealt(run.alice, checkNotNull(room.baba), 10)
            room.complete()
            // Osmumten stands on the east side and looks west, across the arena.
            val osmumten = checkNotNull(ToaRoom.ApmekenBoss.osmumten)
            val front = run.raid.regionOf(CoordGrid(osmumten.x - 1, osmumten.z, osmumten.level))
            val dropped = objRepo.findAll(front).map { it.type }.toList()
            assertTrue(ToaTrophies.Objs.banana.id in dropped, "the trophy: $dropped")
            assertTrue(ToaTrophies.Objs.book_baba.id in dropped, "the book: $dropped")
            assertEquals(0, run.alice.inv.count(ToaTrophies.Objs.banana))
            assertEquals(0, run.alice.inv.count(ToaTrophies.Objs.book_baba))
        }

    @Test
    fun GameTestState.`Ba-Ba cannot be hit past a boulder threshold before its phase`() =
        runRoomTest(ToaRoom.ApmekenBoss, BaBaScript::class) { run ->
            val room = run.runtime() as BaBaRuntime
            room.start()
            val boss = checkNotNull(room.baba)
            val floor = boss.baseHitpointsLvl * 66 / 100
            val hit = TestHits.hit(HitType.Ranged, boss.hitpoints)
            room.modifyHit(boss, hit, run.alice)
            assertEquals(boss.hitpoints - floor, hit.damage, "stops at 66%")
        }

    @Test
    fun `Akkha's Osmumten stands on the west side of the arena`() {
        val osmumten = checkNotNull(ToaRoom.HetBoss.osmumten)
        val bounds = checkNotNull(ToaRoom.HetBoss.bounds)
        assertTrue(osmumten.x - bounds.minX < 5, "$osmumten is not on the west side")
    }
}
