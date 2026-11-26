package org.rsmod.content.areas.misc.isleofsouls.cox

import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * Where a raid's rooms sit in its large region (40 by 40 zones), and how a tile of a room's source
 * on the cache's map becomes a tile of the raid and back.
 * - Room (gx, gz) of a floor takes zones (BASE + 4 gx .. +3, BASE + 4 gz .. +3) on its floor's
 *   plane (3, 2, 1), copied from the room's version turned clockwise `rotation` quarter turns.
 * - Every other zone inside the floors' grid, and a ring of one room round it, is the plain wall
 *   zone (416, 647), turned at random (as TSPS does; it only shows as rock).
 * - The Great Olm's chamber, 5 by 7 zones from (402, 713), is copied unturned on planes 0 and 1 at
 *   zones (0, 33).
 *
 * "Template" tiles are the cache's own tiles in the room's chosen version (the Wiki's, TSPS's and
 * this code's room tiles are all given that way).
 */
object CoxMap {
    const val BASE_ZONE = 4
    private const val WALL_ZONE_X = 416
    private const val WALL_ZONE_Z = 647
    const val OLM_SOURCE_ZONE_X = 402
    const val OLM_SOURCE_ZONE_Z = 713
    const val OLM_WIDTH = 5
    const val OLM_LENGTH = 7
    const val OLM_ZONE_X = 0
    const val OLM_ZONE_Z = 33
    private const val MAX_GRID_ZONES = 32

    fun template(layout: CoxLayout, random: (Int) -> Int): RegionStaticTemplate =
        RegionTemplate.createLarge {
            val used = HashSet<Triple<Int, Int, Int>>()
            for (room in layout.rooms) {
                for (zx in 0 until CoxRoomType.ROOM_ZONES) {
                    for (zz in 0 until CoxRoomType.ROOM_ZONES) {
                        val source =
                            ZoneKey(
                                room.type.zoneX(room.variant) + zx,
                                room.type.zoneZ + zz,
                                room.type.sourceLevel,
                            )
                        val (rx, rz) = turnZone(zx, zz, room.rotation)
                        val x = BASE_ZONE + room.gridX * CoxRoomType.ROOM_ZONES + rx
                        val z = BASE_ZONE + room.gridZ * CoxRoomType.ROOM_ZONES + rz
                        this[x, z, room.plane] = RegionZoneCopy(source, room.rotation, null)
                        used += Triple(x, z, room.plane)
                    }
                }
            }
            for (floor in 0 until layout.floors) {
                val rooms = layout.floor(floor)
                val plane = CoxLayout.FLOOR_PLANES[floor]
                val maxX = rooms.maxOf { it.gridX }
                val maxZ = rooms.maxOf { it.gridZ }
                val fromX = BASE_ZONE - CoxRoomType.ROOM_ZONES
                val toX = BASE_ZONE + (maxX + 2) * CoxRoomType.ROOM_ZONES
                val fromZ = BASE_ZONE - CoxRoomType.ROOM_ZONES
                val toZ = minOf(MAX_GRID_ZONES, BASE_ZONE + (maxZ + 2) * CoxRoomType.ROOM_ZONES)
                for (x in fromX.coerceAtLeast(0) until toX.coerceAtMost(40)) {
                    for (z in fromZ.coerceAtLeast(0) until toZ) {
                        if (Triple(x, z, plane) in used) continue
                        val wall = ZoneKey(WALL_ZONE_X, WALL_ZONE_Z, 0)
                        this[x, z, plane] = RegionZoneCopy(wall, random(4), null)
                        used += Triple(x, z, plane)
                    }
                }
            }
            for (level in 0..1) {
                for (x in 0 until OLM_WIDTH) {
                    for (z in 0 until OLM_LENGTH) {
                        if (Triple(OLM_ZONE_X + x, OLM_ZONE_Z + z, level) in used) continue
                        val source = ZoneKey(OLM_SOURCE_ZONE_X + x, OLM_SOURCE_ZONE_Z + z, level)
                        this[OLM_ZONE_X + x, OLM_ZONE_Z + z, level] =
                            RegionZoneCopy(source, 0, null)
                    }
                }
            }
        }

    /** Where zone (zx, zz) of a 4 by 4 room lands when the room is turned [rotation] times. */
    fun turnZone(zx: Int, zz: Int, rotation: Int): Pair<Int, Int> = turn(zx, zz, rotation, 4, 1)

    /**
     * Turns the room-local tile ([x], [z]) of something [size] tiles square clockwise [rotation]
     * times inside a room [length] tiles across: the south-west tile it lands on.
     */
    fun turn(x: Int, z: Int, rotation: Int, length: Int, size: Int): Pair<Int, Int> {
        val last = length - 1
        val far = size - 1
        return when (rotation and 3) {
            1 -> z to (last - x - far)
            2 -> (last - x - far) to (last - z - far)
            3 -> (last - z - far) to x
            else -> x to z
        }
    }

    /** The raid tile of the template tile [template] in [room], for something [size] square. */
    fun toRegion(
        room: CoxPlacement,
        template: CoordGrid,
        southWest: CoordGrid,
        size: Int = 1,
    ): CoordGrid {
        val lx = template.x - room.type.sourceX(room.variant)
        val lz = template.z - room.type.sourceZ
        val (rx, rz) = turn(lx, lz, room.rotation, CoxRoomType.ROOM_TILES, size)
        return CoordGrid(
            southWest.x + (BASE_ZONE + room.gridX * CoxRoomType.ROOM_ZONES) * 8 + rx,
            southWest.z + (BASE_ZONE + room.gridZ * CoxRoomType.ROOM_ZONES) * 8 + rz,
            room.plane,
        )
    }

    /** The template tile of [coords] (a raid tile inside [room]). */
    fun toTemplate(room: CoxPlacement, coords: CoordGrid, southWest: CoordGrid): CoordGrid {
        val rx = coords.x - southWest.x - (BASE_ZONE + room.gridX * CoxRoomType.ROOM_ZONES) * 8
        val rz = coords.z - southWest.z - (BASE_ZONE + room.gridZ * CoxRoomType.ROOM_ZONES) * 8
        val (lx, lz) = turn(rx, rz, (4 - room.rotation) and 3, CoxRoomType.ROOM_TILES, 1)
        return CoordGrid(room.type.sourceX(room.variant) + lx, room.type.sourceZ + lz, 0)
    }

    /** The room [coords] (a raid tile) is in. */
    fun roomAt(layout: CoxLayout, coords: CoordGrid, southWest: CoordGrid): CoxPlacement? {
        val dx = coords.x - southWest.x - BASE_ZONE * 8
        val dz = coords.z - southWest.z - BASE_ZONE * 8
        if (dx < 0 || dz < 0) return null
        val gx = dx / CoxRoomType.ROOM_TILES
        val gz = dz / CoxRoomType.ROOM_TILES
        return layout.rooms.firstOrNull {
            it.plane == coords.level && it.gridX == gx && it.gridZ == gz
        }
    }

    /** A loc's angle in the raid when it faces [angle] in the room's template. */
    fun turnAngle(angle: Int, room: CoxPlacement): Int = (angle + room.rotation) and 3

    // The Great Olm's chamber
    // ---------------------------------------------------------------------------------------------

    private const val OLM_SOURCE_X = OLM_SOURCE_ZONE_X * 8
    private const val OLM_SOURCE_Z = OLM_SOURCE_ZONE_Z * 8

    fun isOlmChamber(coords: CoordGrid, southWest: CoordGrid): Boolean {
        val dx = coords.x - southWest.x - OLM_ZONE_X * 8
        val dz = coords.z - southWest.z - OLM_ZONE_Z * 8
        return coords.level <= 1 && dx in 0 until OLM_WIDTH * 8 && dz in 0 until OLM_LENGTH * 8
    }

    /** The raid tile of the Olm chamber's cache tile [real]. */
    fun olmToRegion(real: CoordGrid, southWest: CoordGrid): CoordGrid =
        CoordGrid(
            southWest.x + OLM_ZONE_X * 8 + real.x - OLM_SOURCE_X,
            southWest.z + OLM_ZONE_Z * 8 + real.z - OLM_SOURCE_Z,
            real.level,
        )

    fun olmToReal(coords: CoordGrid, southWest: CoordGrid): CoordGrid =
        CoordGrid(
            OLM_SOURCE_X + coords.x - southWest.x - OLM_ZONE_X * 8,
            OLM_SOURCE_Z + coords.z - southWest.z - OLM_ZONE_Z * 8,
            coords.level,
        )
}
