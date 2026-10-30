package org.rsmod.content.skills.fishing.scripts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishingSpotSpawnsTest {
    @Test
    fun `lists every spot of the isle`() {
        val counts = FishingSpotSpawns.SPOTS.groupingBy { it.label }.eachCount()
        assertEquals(
            mapOf(
                "Shark" to 1,
                "Monkfish" to 1,
                "Anglerfish" to 1,
                "Salmon" to 2,
                "Karambwan" to 2,
                "Karambwanji" to 2,
            ),
            counts,
        )
    }

    @Test
    fun `no two spots share a tile`() {
        val tiles = FishingSpotSpawns.SPOTS.map { it.coords }
        assertEquals(tiles.size, tiles.toSet().size)
    }

    @Test
    fun `spots stand on the ground floor`() {
        assertTrue(FishingSpotSpawns.SPOTS.all { it.coords.level == 0 })
    }
}
