package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GauntletLayoutTest {
    private fun plan(seed: Int): GauntletLayout {
        val random = Random(seed)
        return GauntletLayout.plan { random.nextInt(it) }
    }

    @Test
    fun `rooms are Hunllef in the middle and start beside it`() {
        repeat(50) { seed ->
            val layout = plan(seed)
            assertEquals(RoomKind.Boss, layout.boss.kind)
            assertEquals(RoomKind.Start, layout.start.kind)
            val distance =
                kotlin.math.abs(layout.start.x - GauntletGrid.CENTRE) +
                    kotlin.math.abs(layout.start.z - GauntletGrid.CENTRE)
            assertEquals(1, distance)
            assertEquals(1, layout.all().count { it.kind == RoomKind.Start })
            assertEquals(1, layout.all().count { it.kind == RoomKind.Boss })
        }
    }

    @Test
    fun `every start side comes up`() {
        val sides = (0 until 200).map { plan(it).let { l -> l.startX to l.startZ } }.toSet()
        assertEquals(4, sides.size)
    }

    @Test
    fun `rim rooms have fewer exits and face outward`() {
        assertEquals(RoomKind.Corner, GauntletLayout.kindAt(0, 0))
        assertEquals(RoomKind.Corner, GauntletLayout.kindAt(6, 6))
        assertEquals(RoomKind.Edge, GauntletLayout.kindAt(0, 3))
        assertEquals(RoomKind.Edge, GauntletLayout.kindAt(4, 6))
        assertEquals(RoomKind.Middle, GauntletLayout.kindAt(2, 5))

        // Fixed turns on the rim, one per corner and side.
        assertEquals(0, GauntletLayout.rotationAt(6, 6) { error("rim rooms are never random") })
        assertEquals(2, GauntletLayout.rotationAt(0, 0) { error("rim rooms are never random") })
        assertEquals(3, GauntletLayout.rotationAt(0, 6) { error("rim rooms are never random") })
        assertEquals(1, GauntletLayout.rotationAt(6, 0) { error("rim rooms are never random") })
        assertEquals(3, GauntletLayout.rotationAt(0, 4) { error("rim rooms are never random") })
        assertEquals(1, GauntletLayout.rotationAt(6, 2) { error("rim rooms are never random") })
        assertEquals(2, GauntletLayout.rotationAt(3, 0) { error("rim rooms are never random") })
        assertEquals(0, GauntletLayout.rotationAt(3, 6) { error("rim rooms are never random") })
        // Inside rooms turn at random.
        assertEquals(2, GauntletLayout.rotationAt(2, 2) { 2 })
    }

    @Test
    fun `six demi-boss rooms on the rim, each kind twice, none in a corner`() {
        repeat(50) { seed ->
            val layout = plan(seed)
            val demi = layout.all().filter { it.demiBoss != null }
            assertEquals(6, demi.size)
            for (kind in DemiBoss.entries) {
                assertEquals(2, demi.count { it.demiBoss == kind })
            }
            for (room in demi) {
                assertTrue(GauntletLayout.isDemiBossRoom(room.x, room.z), "$room")
                assertEquals(RoomKind.Edge, room.kind, "$room")
            }
        }
        assertEquals(
            12,
            (0..6).sumOf { x -> (0..6).count { z -> GauntletLayout.isDemiBossRoom(x, z) } },
        )
    }

    @Test
    fun `every room draws from the template chunks of its kind`() {
        repeat(30) { seed ->
            val layout = plan(seed)
            for (room in layout.all()) {
                when (room.kind) {
                    RoomKind.Boss -> {
                        assertEquals(GauntletGrid.BOSS_CHUNK_X, room.templateChunkX)
                        assertEquals(GauntletGrid.BOSS_CHUNK_Z, room.templateChunkZ)
                    }
                    RoomKind.Start -> {
                        assertEquals(GauntletGrid.START_CHUNK_X, room.templateChunkX)
                        assertEquals(GauntletGrid.START_CHUNK_Z, room.templateChunkZ)
                    }
                    else -> {
                        val expectedX =
                            when (room.kind) {
                                RoomKind.Middle -> GauntletGrid.MIDDLE_CHUNK_X
                                RoomKind.Edge -> GauntletGrid.EDGE_CHUNK_X
                                else -> GauntletGrid.CORNER_CHUNK_X
                            }
                        assertEquals(expectedX, room.templateChunkX)
                        assertTrue(room.templateChunkZ in listOf(704, 706, 708, 710), "$room")
                    }
                }
                assertTrue(room.rotation in 0..3)
            }
        }
    }

    @Test
    fun `rooms start dark and are numbered row by row`() {
        val layout = plan(1)
        assertTrue(layout.all().none { it.lit })
        assertEquals(0, layout.room(0, 0)?.index)
        assertEquals(8, layout.room(1, 1)?.index)
        assertEquals(48, layout.room(6, 6)?.index)
        assertNull(layout.room(7, 0))
        assertEquals(49, layout.all().map { it.index }.toSet().size)
    }
}
