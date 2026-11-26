package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import kotlin.system.measureTimeMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.registry.region.RegionRegistryResult
import org.rsmod.api.registry.zone.ZonePlayerActivityBitSet
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Step 0 of the Tombs of Amascut plan (docs/isle_of_souls_toa_plan.md): every raid gets one large
 * region holding a copy of all the rooms, packed by [ToaPacking]. These tests pin the facts the
 * design relies on. The test world has no map, so the wall flags and locs that the real tombs have
 * are put in by hand at real coordinates before the region is built (a region copies its source
 * zones when it is created).
 */
class ToaRegionProbeTest {
    class Deps
    @Inject
    constructor(
        val repo: RegionRepository,
        val registry: RegionRegistry,
        val collision: CollisionFlagMap,
        val zoneActivity: ZonePlayerActivityBitSet,
    )

    /** Real tiles that get a wall flag and a loc, one or more in each column and on level 1. */
    private val marked =
        listOf(
            CoordGrid(3516, 5270, 0), // scabaras, the sliver in map square 54
            CoordGrid(3550, 5161, 0), // main hall
            CoordGrid(3662, 5275, 0), // het statue
            CoordGrid(3699, 5406, 1), // akkha exit, level 1
            CoordGrid(3803, 5269, 0), // apmeken roof support
            CoordGrid(3807, 5176, 1), // wardens spawn, level 1
            CoordGrid(3955, 5278, 0), // crondis exit
            CoordGrid(3935, 5168, 1), // final wardens arena
        )

    /** Allocates the collision of every zone the template copies, as the real map has them. */
    private fun GameTestScope.seed(deps: Deps) {
        for (column in ToaPacking.columns) {
            for (zx in column.realZoneX until column.realZoneX + column.zoneWidth) {
                for (zz in
                    ToaPacking.REAL_ZONE_Z until ToaPacking.REAL_ZONE_Z + ToaPacking.ZONE_ROWS) {
                    for (level in 0 until CoordGrid.LEVEL_COUNT) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
        }
        for (tile in marked) {
            deps.collision.add(tile.x, tile.z, tile.level, CollisionFlag.BLOCK_WALK)
            placeMapLoc(tile, toa_locs.exit_door, LocShape.CentrepieceStraight, LocAngle.West)
        }
    }

    private fun GameTestScope.build(deps: Deps): Region {
        seed(deps)
        return checkNotNull(deps.repo.add(ToaPacking.template())) { "no large region was free" }
    }

    @Test
    fun GameTestState.`the packed template builds a large region`() =
        runInjectedGameTest(Deps::class) { deps ->
            val region = build(deps)
            assertEquals(320, region.northEast.x - region.southWest.x)
            assertEquals(320, region.northEast.z - region.southWest.z)
            assertEquals(RegionRepository.LARGE_REGION_ZONE_LENGTH, region.zoneLength)
            assertTrue(region.slot != Region.INVALID_SLOT)
        }

    @Test
    fun GameTestState.`copied zones are registered and the gaps are blank`() =
        runInjectedGameTest(Deps::class) { deps ->
            val region = build(deps)
            val sw = region.southWestZone
            var copied = 0
            var keyless = 0
            for (level in 0 until CoordGrid.LEVEL_COUNT) {
                for (column in ToaPacking.columns) {
                    for (dx in 0 until column.zoneWidth) {
                        for (dz in 0 until ToaPacking.ZONE_ROWS) {
                            val zone = ZoneKey(sw.x + column.regionZoneX + dx, sw.z + dz, level)
                            val copy = deps.registry[zone]
                            val normal =
                                ZoneKey(column.realZoneX + dx, ToaPacking.REAL_ZONE_Z + dz, 0)
                            if (MapSquareKey.from(normal.toCoords()) !in xteaRepository) {
                                // A map square without an xtea key is left out (RegionCopySquares).
                                assertEquals(RegionZoneCopy.NULL, copy, "$zone")
                                keyless++
                                continue
                            }
                            assertNotEquals(RegionZoneCopy.NULL, copy, "$zone")
                            assertEquals(column.realZoneX + dx, copy.normalX)
                            assertEquals(ToaPacking.REAL_ZONE_Z + dz, copy.normalZ)
                            assertEquals(level, copy.normalLevel)
                            assertEquals(0, copy.rotation)
                            assertTrue(deps.collision.isZoneValid(zone))
                            copied++
                        }
                    }
                }
            }
            assertEquals(33 * 40 * 4, copied + keyless)

            // The two zone columns after each path stay blank, so rooms never see each other.
            for (column in ToaPacking.columns.dropLast(1)) {
                for (gap in 0 until ToaPacking.GAP_ZONES) {
                    val x = sw.x + column.regionZoneX + column.zoneWidth + gap
                    for (dz in 0 until ToaPacking.ZONE_ROWS) {
                        val zone = ZoneKey(x, sw.z + dz, 0)
                        assertEquals(RegionZoneCopy.NULL, deps.registry[zone])
                        assertFalse(deps.collision.isZoneValid(zone))
                    }
                }
            }
        }

    @Test
    fun GameTestState.`collision and locs follow the packing`() =
        runInjectedGameTest(Deps::class) { deps ->
            val region = build(deps)
            for (real in marked) {
                val inside = checkNotNull(ToaPacking.toRegion(real, region.southWest))
                // The engine's own lookup agrees with the packing maths.
                assertEquals(inside, region.normal[real], "region.normal for $real")
                assertEquals(real, deps.registry.normalizeCoords(inside), "normalize for $inside")

                val flags = deps.collision[inside.x, inside.z, inside.level]
                assertTrue(flags and CollisionFlag.BLOCK_WALK != 0, "wall flag at $inside")

                // The whole neighbourhood matches the source tile for tile, which also covers the
                // footprint of the loc placed on the marked tile.
                for (dx in -3..3) {
                    for (dz in -3..3) {
                        val source = deps.collision[real.x + dx, real.z + dz, real.level]
                        val copy = deps.collision[inside.x + dx, inside.z + dz, inside.level]
                        assertEquals(
                            source and CollisionFlag.BLOCK_WALK,
                            copy and CollisionFlag.BLOCK_WALK,
                            "flags at offset ($dx, $dz) of $real",
                        )
                    }
                }

                val locs = findLocs(inside).toList()
                assertTrue(
                    locs.any { it.id == toa_locs.exit_door.id },
                    "loc copied to $inside, found ${locs.map { it.id to it.coords }}",
                )
            }
        }

    @Test
    fun GameTestState.`two raids are separate and free their slots`() =
        runInjectedGameTest(Deps::class) { deps ->
            seed(deps)
            // A region that nobody stands in is reclaimed as soon as another is created, so each
            // is flagged as occupied the way entering a raid does.
            val first = checkNotNull(deps.repo.add(ToaPacking.template()))
            deps.zoneActivity.flag(first.southWestZone)
            val second = checkNotNull(deps.repo.add(ToaPacking.template()))
            deps.zoneActivity.flag(second.southWestZone)
            assertNotEquals(first.slot, second.slot)
            assertNotEquals(first.southWest, second.southWest)

            val real = marked[2]
            val inFirst = checkNotNull(ToaPacking.toRegion(real, first.southWest))
            val inSecond = checkNotNull(ToaPacking.toRegion(real, second.southWest))
            locDel(findLocs(inFirst).first { it.id == toa_locs.exit_door.id })
            assertFalse(findLocs(inFirst).any { it.id == toa_locs.exit_door.id })
            assertTrue(findLocs(inSecond).any { it.id == toa_locs.exit_door.id })

            val removed = deps.registry.unregister(first)
            assertTrue(removed is RegionRegistryResult.Delete.RemoveLarge, "$removed")
            assertFalse(deps.collision.isZoneValid(ZoneKey.from(inFirst)))
            assertTrue(deps.collision.isZoneValid(ZoneKey.from(inSecond)))
            val third = checkNotNull(deps.repo.add(ToaPacking.template()))
            assertNotEquals(Region.INVALID_SLOT, third.slot)

            // An empty region (the third, never entered) is reclaimed by the next one.
            checkNotNull(deps.repo.add(ToaPacking.template()))
            assertEquals(Region.INVALID_SLOT, third.slot)
        }

    @Test
    fun GameTestState.`building a raid is cheap`() =
        runInjectedGameTest(Deps::class) { deps ->
            seed(deps)
            val millis = measureTimeMillis {
                repeat(5) {
                    val region = checkNotNull(deps.repo.add(ToaPacking.template()))
                    deps.zoneActivity.flag(region.southWestZone)
                }
            }
            println("ToA probe: 5 packed regions built in ${millis}ms")
            assertTrue(millis < 10_000, "five regions took ${millis}ms")
        }

    @Test
    fun GameTestState.`a player inside keeps the region alive`() =
        runInjectedGameTest(Deps::class) { deps ->
            val region = build(deps)
            val dest = checkNotNull(ToaPacking.toRegion(CoordGrid(3698, 5279, 0), region.southWest))
            // The engine only marks a zone as occupied when it next processes the player, so the
            // zone is flagged the way the Gauntlet does when it moves someone in.
            PathingEntityCommon.telejump(player, deps.collision, dest)
            deps.zoneActivity.flag(ZoneKey.from(dest))
            advance(2)
            assertEquals(dest, player.coords)

            // Allocating another large region reclaims empty ones; ours has a player in it.
            repeat(3) {
                val other = checkNotNull(deps.repo.add(ToaPacking.template()))
                assertNotEquals(region.slot, other.slot)
            }
            assertTrue(region.slot != Region.INVALID_SLOT, "region was reclaimed under the player")
        }
}
