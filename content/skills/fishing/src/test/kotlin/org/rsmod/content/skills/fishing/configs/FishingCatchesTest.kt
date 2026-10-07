package org.rsmod.content.skills.fishing.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishingCatchesTest {
    @Test
    fun `resolve spots from their option text`() {
        assertEquals(
            SpotMethods(FishingMethod.SmallNet, FishingMethod.SeaBait),
            FishingSpots.fromOps("Small Net", "Bait"),
        )
        assertEquals(
            SpotMethods(FishingMethod.SmallNet, FishingMethod.SeaBait),
            FishingSpots.fromOps("Net", "Bait"),
        )
        assertEquals(
            SpotMethods(FishingMethod.FlyRod, FishingMethod.RiverBait),
            FishingSpots.fromOps("Lure", "Bait"),
        )
        assertEquals(
            SpotMethods(FishingMethod.LobsterPot, FishingMethod.Harpoon),
            FishingSpots.fromOps("Cage", "Harpoon"),
        )
        assertEquals(
            SpotMethods(FishingMethod.BigNet, FishingMethod.SharkHarpoon),
            FishingSpots.fromOps("Big Net", "Harpoon"),
        )
        assertEquals(
            SpotMethods(FishingMethod.BigNet, FishingMethod.SharkHarpoon),
            FishingSpots.fromOps("Net", "Harpoon"),
        )
    }

    @Test
    fun `ignore spots with unsupported options`() {
        assertNull(FishingSpots.fromOps("Use-rod", null))
        assertNull(FishingSpots.fromOps("Cast", null))
        assertNull(FishingSpots.fromOps("Harpoon", null))
        assertNull(FishingSpots.fromOps("Small Net", "Big Net"))
        assertNull(FishingSpots.fromOps(null, null))
    }

    @Test
    fun `every method has a catchable pool`() {
        for (method in FishingMethod.entries) {
            assertTrue(FishingCatches.pool(method).isNotEmpty(), "No catches for $method.")
        }
    }

    @Test
    fun `eligible fish are ordered from highest to lowest level`() {
        val levels = FishingCatches.eligible(FishingMethod.BigNet, 99).map { it.level }
        assertEquals(listOf(46, 23, 16), levels)
        assertEquals(
            listOf(15, 1),
            FishingCatches.eligible(FishingMethod.SmallNet, 15).map { it.level },
        )
        assertEquals(
            listOf(1),
            FishingCatches.eligible(FishingMethod.SmallNet, 14).map { it.level },
        )
    }

    @Test
    fun `nothing is eligible below the lowest level`() {
        assertTrue(FishingCatches.eligible(FishingMethod.LobsterPot, 39).isEmpty())
        assertEquals(40, FishingCatches.minLevel(FishingMethod.LobsterPot))
        assertEquals(35, FishingCatches.minLevel(FishingMethod.Harpoon))
        assertEquals(76, FishingCatches.minLevel(FishingMethod.SharkHarpoon))
    }

    @Test
    fun `catch values match the wiki`() {
        val expected =
            mapOf(
                FishingCatches.shrimp to listOf(1, 10, 48, 256),
                FishingCatches.sardine to listOf(5, 20, 32, 192),
                FishingCatches.herring to listOf(10, 30, 24, 128),
                FishingCatches.anchovies to listOf(15, 40, 24, 128),
                FishingCatches.mackerel to listOf(16, 20, 5, 65),
                FishingCatches.trout to listOf(20, 50, 32, 192),
                FishingCatches.cod to listOf(23, 45, 4, 55),
                FishingCatches.pike to listOf(25, 60, 16, 96),
                FishingCatches.salmon to listOf(30, 70, 16, 96),
                FishingCatches.tuna to listOf(35, 80, 8, 64),
                FishingCatches.lobster to listOf(40, 90, 6, 95),
                FishingCatches.bass to listOf(46, 100, 3, 40),
                FishingCatches.swordfish to listOf(50, 100, 4, 48),
                FishingCatches.monkfish to listOf(62, 120, 48, 90),
                FishingCatches.karambwan to listOf(65, 50, 5, 160),
                FishingCatches.shark to listOf(76, 110, 3, 40),
                FishingCatches.karambwanji to listOf(5, 5, 100, 250),
            )
        for ((fish, values) in expected) {
            assertEquals(values, listOf(fish.level, fish.xp.toInt(), fish.low, fish.high))
        }
    }
}
