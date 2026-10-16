package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.map.CoordGrid

class InfernoPillarTest {
    @Test
    fun `a pillar shows a new look at three quarters, a half and a quarter of its health`() {
        assertEquals(0, InfernoPillar.stageFor(255))
        assertEquals(0, InfernoPillar.stageFor(192))
        assertEquals(1, InfernoPillar.stageFor(191))
        assertEquals(1, InfernoPillar.stageFor(128))
        assertEquals(2, InfernoPillar.stageFor(127))
        assertEquals(2, InfernoPillar.stageFor(64))
        assertEquals(3, InfernoPillar.stageFor(63))
        assertEquals(3, InfernoPillar.stageFor(1))
    }

    @Test
    fun `a pillar covers nine tiles and is next to the ring of sixteen round them`() {
        val pillar = InfernoPillar(CoordGrid(2261, 5342, 0), 0)

        val covered = (2261..2263).flatMap { x -> (5342..5344).map { z -> CoordGrid(x, z, 0) } }
        assertTrue(covered.all { pillar.covers(it) })
        assertTrue(covered.none { pillar.isNextTo(it) })

        val ring =
            (2260..2264)
                .flatMap { x -> (5341..5345).map { z -> CoordGrid(x, z, 0) } }
                .filter { !pillar.covers(it) }
        assertEquals(16, ring.size)
        assertTrue(ring.all { pillar.isNextTo(it) })

        assertFalse(pillar.isNextTo(CoordGrid(2259, 5343, 0)))
        assertFalse(pillar.isNextTo(CoordGrid(2262, 5346, 0)))
        assertFalse(pillar.isNextTo(CoordGrid(2262, 5343, 1)))
    }

    @Test
    fun `the middle of a pillar and a fresh pillar's health`() {
        val pillar = InfernoPillar(CoordGrid(2261, 5342, 0), 0)

        assertEquals(CoordGrid(2262, 5343, 0), pillar.centre)
        assertEquals(255, pillar.hitpoints)
        assertEquals(0, pillar.damageTaken)
        assertTrue(pillar.alive)
        pillar.hitpoints = 0
        assertFalse(pillar.alive)
        assertEquals(255, pillar.damageTaken)
    }

    @Test
    fun `the pillars stand on the floor and apart from each other`() {
        val sw = InfernoArena.floorSouthWest
        val ne = InfernoArena.floorNorthEast
        val pillars = InfernoArena.pillarSouthWest.mapIndexed { i, site -> InfernoPillar(site, i) }
        assertEquals(3, pillars.size)
        for (pillar in pillars) {
            val far = CoordGrid(pillar.southWest.x + 2, pillar.southWest.z + 2, 0)
            assertTrue(pillar.southWest.x >= sw.x && pillar.southWest.z >= sw.z)
            assertTrue(far.x <= ne.x && far.z <= ne.z)
        }
        for (a in pillars) {
            for (b in pillars) {
                if (a !== b) {
                    assertFalse(a.covers(b.southWest), "pillars ${a.index} and ${b.index} overlap")
                }
            }
        }
    }

    @Test
    fun `nothing spawns on a pillar`() {
        val pillars = InfernoArena.pillarSouthWest.mapIndexed { i, site -> InfernoPillar(site, i) }
        fun clear(origin: CoordGrid, size: Int, what: String) {
            for (dz in 0 until size) {
                for (dx in 0 until size) {
                    val tile = CoordGrid(origin.x + dx, origin.z + dz, origin.level)
                    assertTrue(pillars.none { it.covers(tile) }, "$what at $origin is on a pillar")
                }
            }
        }
        InfernoArena.spawnZones.forEach { clear(it, 4, "a spawn zone") }
        clear(InfernoArena.nibblerSpawn, 3, "the nibblers")
        clear(InfernoArena.jadSingle, 5, "a lone Jad")
        InfernoArena.jadTriangle.forEach { clear(it, 5, "a Jad of three") }
        clear(InfernoArena.reviveSpot, 4, "a revived monster")
    }
}
