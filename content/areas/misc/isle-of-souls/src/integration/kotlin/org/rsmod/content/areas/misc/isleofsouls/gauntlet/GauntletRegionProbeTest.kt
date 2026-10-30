package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.util.RegionRotations
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Step 0 of the Gauntlet plan (docs/isle_of_souls_gauntlet_plan.md): the maze is drawn room by room
 * as the player lights it, so the engine must accept new zones in a region that is already live.
 * These tests pin the facts the design relies on. The test world has no map, so the "normal" zone
 * that stands in for a template chunk gets its collision by hand.
 */
class GauntletRegionProbeTest {
    class Deps
    @Inject
    constructor(
        val repo: RegionRepository,
        val registry: RegionRegistry,
        val collision: CollisionFlagMap,
    )

    /** A room is 2x2 zones. This is the math `copy { rotation = r }` uses for a whole block. */
    private fun RegionRegistry.light(
        regionSouthWest: ZoneKey,
        region: org.rsmod.game.region.Region,
        roomX: Int,
        roomZ: Int,
        template: ZoneKey,
        rotation: Int,
    ) {
        for (x in 0 until 2) {
            for (z in 0 until 2) {
                val (rx, rz) = RegionRotations.translateZone(rotation, x, z, 2, 2)
                val regionZone =
                    ZoneKey(
                        regionSouthWest.x + roomX * 2 + rx,
                        regionSouthWest.z + roomZ * 2 + rz,
                        template.level,
                    )
                val normal = ZoneKey(template.x + x, template.z + z, template.level)
                registerZone(region, regionZone, RegionZoneCopy(normal, rotation, null))
            }
        }
    }

    @Test
    fun GameTestState.`a 7x7 maze of 2x2 chunk rooms fits a small region`() {
        // 14 zones across is 112 tiles; a small region is 128 (16 zones), so no large region.
        assertEquals(16, RegionRepository.SMALL_REGION_ZONE_LENGTH)
        assertTrue(7 * 2 <= RegionRepository.SMALL_REGION_ZONE_LENGTH)
    }

    @Test
    fun GameTestState.`rooms can be lit into a live region and stay rotated`() =
        runInjectedGameTest(Deps::class) { deps ->
            val region = checkNotNull(deps.repo.add(RegionTemplate.create {}))
            val sw = region.southWestZone

            // Nothing is drawn yet: every zone is blank and unwalkable.
            for (zone in region.toZoneList(level = 0)) {
                assertEquals(RegionZoneCopy.NULL, deps.registry[zone])
                assertFalse(deps.collision.isZoneValid(zone))
            }

            // The template chunk (here a stand-in at zone 240,704) has one wall flag 1 tile east
            // and 2 tiles north of its south-west corner.
            val template = ZoneKey(240, 704, 0)
            for (x in 0 until 2) {
                for (z in 0 until 2) {
                    val base = ZoneKey(template.x + x, template.z + z, 0).toCoords()
                    deps.collision.allocateIfAbsent(base.x, base.z, 0)
                }
            }
            val templateBase = template.toCoords()
            deps.collision.add(templateBase.x + 1, templateBase.z + 2, 0, CollisionFlag.BLOCK_WALK)

            // Light the room at grid (3,3) turned once (90 degrees clockwise).
            deps.registry.light(sw, region, roomX = 3, roomZ = 3, template, rotation = 1)

            val roomBase = ZoneKey(sw.x + 6, sw.z + 6, 0).toCoords()
            // translateCoords for rotation 1 on a 16-tile room: (x, z) -> (z, 15 - x).
            val expectedX = roomBase.x + 2
            val expectedZ = roomBase.z + 15 - 1
            assertTrue(deps.collision[expectedX, expectedZ, 0] and CollisionFlag.BLOCK_WALK != 0)
            assertEquals(
                0,
                deps.collision[roomBase.x + 1, roomBase.z + 2, 0] and CollisionFlag.BLOCK_WALK,
            )

            // The four zones of the room are registered, everything else is still blank.
            val lit =
                region.toZoneList(level = 0).filter { deps.registry[it] != RegionZoneCopy.NULL }
            assertEquals(4, lit.size)
            for (zone in lit) {
                assertTrue(deps.collision.isZoneValid(zone))
            }

            // A second room, lit a tick later, joins the first.
            deps.registry.light(sw, region, roomX = 4, roomZ = 3, template, rotation = 0)
            assertEquals(
                8,
                region.toZoneList(level = 0).count { deps.registry[it] != RegionZoneCopy.NULL },
            )

            // A room can only be lit once.
            assertThrows(IllegalStateException::class.java) {
                deps.registry.light(sw, region, roomX = 4, roomZ = 3, template, rotation = 0)
            }
        }
}
