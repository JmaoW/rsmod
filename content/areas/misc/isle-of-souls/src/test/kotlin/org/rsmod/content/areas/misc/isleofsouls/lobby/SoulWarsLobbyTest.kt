package org.rsmod.content.areas.misc.isleofsouls.lobby

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.GameRandom
import org.rsmod.map.CoordGrid

class SoulWarsLobbyTest {
    /** Always rolls [roll], so a test can walk through every possible result. */
    private class FixedRandom(private val roll: Int) : GameRandom {
        override fun of(maxExclusive: Int): Int = roll % maxExclusive

        override fun of(minInclusive: Int, maxInclusive: Int): Int = minInclusive

        override fun randomDouble(): Double = 0.0
    }

    @Test
    fun `each graveyard is the 3 by 7 patch inside its gravestones`() {
        for (graveyard in listOf(SoulWarsLobby.blue, SoulWarsLobby.red)) {
            assertEquals(3, graveyard.maxX - graveyard.minX + 1)
            assertEquals(7, graveyard.maxZ - graveyard.minZ + 1)
            assertEquals(21, graveyard.tiles.size)
        }
    }

    @Test
    fun `the blue graveyard sits between its gravestones at x 2204 and 2208`() {
        assertEquals(2205, SoulWarsLobby.blue.minX)
        assertEquals(2207, SoulWarsLobby.blue.maxX)
        assertEquals(2847, SoulWarsLobby.blue.minZ)
        assertEquals(2853, SoulWarsLobby.blue.maxZ)
    }

    @Test
    fun `the red graveyard sits between its gravestones at x 2212 and 2216`() {
        assertEquals(2213, SoulWarsLobby.red.minX)
        assertEquals(2215, SoulWarsLobby.red.maxX)
        assertEquals(2847, SoulWarsLobby.red.minZ)
        assertEquals(2853, SoulWarsLobby.red.maxZ)
    }

    @Test
    fun `there are 42 distinct spawn tiles on level 0`() {
        val tiles = SoulWarsLobby.spawnTiles
        assertEquals(42, tiles.size)
        assertEquals(42, tiles.toSet().size)
        assertTrue(tiles.all { it.level == 0 })
    }

    @Test
    fun `every spawn tile can be rolled and nothing else`() {
        val rolled =
            SoulWarsLobby.spawnTiles.indices
                .map { SoulWarsLobby.randomSpawn(FixedRandom(it)) }
                .toSet()
        assertEquals(SoulWarsLobby.spawnTiles.toSet(), rolled)
    }

    @Test
    fun `the first and last tiles are the graveyards' corners`() {
        assertEquals(CoordGrid(2205, 2847, 0), SoulWarsLobby.randomSpawn(FixedRandom(0)))
        assertEquals(CoordGrid(2215, 2853, 0), SoulWarsLobby.randomSpawn(FixedRandom(41)))
    }
}
