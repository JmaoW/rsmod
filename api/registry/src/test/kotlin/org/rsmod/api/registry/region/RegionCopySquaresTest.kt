package org.rsmod.api.registry.region

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.factory.locRegistryFactory
import org.rsmod.api.testing.factory.locTypeListFactory
import org.rsmod.api.testing.factory.regionRegistryFactory
import org.rsmod.game.map.LocZoneStorage
import org.rsmod.game.map.xtea.XteaMap
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap

class RegionCopySquaresTest {
    /** Map square (50, 50) has a key; (52, 69) has none, as in the event's `xteas.json`. */
    private val xteas = XteaMap(mapOf(MapSquareKey(50, 50) to IntArray(4)))
    private val keyedZone = ZoneKey(50 * 8, 50 * 8, 0)
    private val keylessZone = ZoneKey(52 * 8, 69 * 8, 0)

    @Test
    fun `only squares with a key can be copied`() {
        val squares = RegionCopySquares(xteas)
        assertTrue(squares.isCopyable(keyedZone))
        assertTrue(squares.isCopyable(keyedZone.translate(7, 7)))
        assertFalse(squares.isCopyable(keylessZone))
    }

    @Test
    fun `without any keys every square can be copied`() {
        val squares = RegionCopySquares(XteaMap(emptyMap()))
        assertTrue(squares.isCopyable(keylessZone))
    }

    @Test
    fun `a region leaves out zones from squares without a key`() {
        val collision = CollisionFlagMap()
        val locZones = LocZoneStorage()
        val registry =
            regionRegistryFactory.create(
                collision = collision,
                locReg = locRegistryFactory.createNormal(collision, locZones),
                locTypes = locTypeListFactory.createDefault(),
                locZones = locZones,
                copySquares = RegionCopySquares(xteas),
            )
        val region = (registry.registerSmall() as RegionRegistryResult.Add.Success).region
        val first = ZoneKey.from(region.southWest)
        val second = first.translate(1, 0)

        registry.registerZone(region, first, RegionZoneCopy(keyedZone, 0, null))
        registry.registerZone(region, second, RegionZoneCopy(keylessZone, 0, null))

        assertNotEquals(RegionZoneCopy.NULL, registry[first])
        assertEquals(keyedZone, registry[first].normalZone())
        assertEquals(RegionZoneCopy.NULL, registry[second])
    }
}
