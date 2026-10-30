package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

/**
 * One run of the tombs map that is copied into a raid's region as a block: the four paths each sit
 * in their own column of map squares (Scabaras, Het, Apmeken, Crondis), with the Nexus, vault and
 * Wardens along the bottom row and each path's puzzle and boss rooms above them.
 *
 * [realZoneX] is the first zone column on the real map, [zoneWidth] how many zone columns are
 * copied, and [regionZoneX] where that block starts in the raid's region.
 */
class ToaColumn(val name: String, val realZoneX: Int, val zoneWidth: Int, val regionZoneX: Int) {
    val realMinX: Int
        get() = realZoneX * ZoneGrid.LENGTH

    val realMaxX: Int
        get() = (realZoneX + zoneWidth) * ZoneGrid.LENGTH - 1

    /** How far a real x on this column moves west when the raid's region is built. */
    val shiftX: Int
        get() = (realZoneX - regionZoneX) * ZoneGrid.LENGTH

    operator fun contains(realX: Int): Boolean = realX in realMinX..realMaxX
}

/**
 * How the tombs fit into one raid: a large region (40x40 zones), holding a copy of every room.
 *
 * The real tombs span x 3520-3967 and z 5120-5439, which is bigger than a large region only because
 * the map squares between the four paths are empty. Packing the four path columns closer together
 * (keeping a two-zone gap, so rooms of neighbouring paths never come into view of each other) makes
 * it fit. The rows are copied unchanged, so a real z is the region's south-west z plus `realZ -
 * REAL_MIN_Z`, and only x depends on the column.
 *
 * Every other piece of ToA code works on real coordinates and converts through [toRegion] or the
 * region's own `normal` lookup, so the data from the cache and the wiki can be used as it is.
 */
object ToaPacking {
    /** First zone row of the tombs (z 5120). */
    const val REAL_ZONE_Z: Int = 640

    /** Zone rows copied: the five map square rows 80-84, gaps included, so rows stay put. */
    const val ZONE_ROWS: Int = 40

    const val REAL_MIN_Z: Int = REAL_ZONE_Z * ZoneGrid.LENGTH
    const val REAL_MAX_Z: Int = (REAL_ZONE_Z + ZONE_ROWS) * ZoneGrid.LENGTH - 1

    /** Zones between two packed columns. */
    const val GAP_ZONES: Int = 2

    /**
     * Scabaras starts one zone west of map square 55 because its puzzle room has a few locs on the
     * last tiles of square 54 (x 3516-3519).
     */
    val columns: List<ToaColumn> = buildList {
        var regionZoneX = 0
        for ((name, realZoneX, width) in
            listOf(
                Triple("Scabaras", 439, 9),
                Triple("Het", 456, 8),
                Triple("Apmeken", 472, 8),
                Triple("Crondis", 488, 8),
            )) {
            add(ToaColumn(name, realZoneX, width, regionZoneX))
            regionZoneX += width + GAP_ZONES
        }
    }

    /** Zone columns the packed layout uses, gaps between columns included. */
    val usedZoneColumns: Int
        get() = columns.last().let { it.regionZoneX + it.zoneWidth }

    /** The region template: every column, all four levels, copied unrotated. */
    fun template(): RegionStaticTemplate =
        RegionTemplate.createLarge {
            for (column in columns) {
                copyAllLevels(column.realZoneX, REAL_ZONE_Z) {
                    zoneWidth = column.zoneWidth
                    zoneLength = ZONE_ROWS
                    regionZoneX = column.regionZoneX
                    regionZoneZ = 0
                }
            }
        }

    /** The column of the tombs that holds real x [realX], if there is one. */
    fun columnAt(realX: Int): ToaColumn? = columns.firstOrNull { realX in it }

    /** Whether [real] lies on the part of the real map the raid copies. */
    fun isCopied(real: CoordGrid): Boolean =
        real.z in REAL_MIN_Z..REAL_MAX_Z && columnAt(real.x) != null

    /**
     * Where the real tile [real] is in a region whose south-west corner is [regionSouthWest], or
     * `null` for a tile outside the copied columns (the lobby, for example).
     */
    fun toRegion(real: CoordGrid, regionSouthWest: CoordGrid): CoordGrid? {
        if (real.z !in REAL_MIN_Z..REAL_MAX_Z) {
            return null
        }
        val column = columnAt(real.x) ?: return null
        return CoordGrid(
            regionSouthWest.x + real.x - column.shiftX,
            regionSouthWest.z + real.z - REAL_MIN_Z,
            real.level,
        )
    }

    /**
     * How far in from the south-west corner of the client's scene the real game puts the map square
     * of the room you are in. Client plugins (the RuneLite Tombs of Amascut plugin finds the
     * Scabaras puzzles at fixed scene tiles) rely on it.
     */
    const val SCENE_SQUARE_OFFSET: Int = 2 * ZoneGrid.LENGTH

    /**
     * The south-west corner of the build area for a player standing on the region tile copied from
     * the real tile [real]: the scene starts [SCENE_SQUARE_OFFSET] tiles south-west of the real map
     * square the player is in, as it does in the real game. The corner may lie just outside the
     * copied columns (in the padding round the region), where the client sees nothing.
     */
    fun sceneAnchor(real: CoordGrid, regionSouthWest: CoordGrid): CoordGrid? {
        if (real.z !in REAL_MIN_Z..REAL_MAX_Z) {
            return null
        }
        val column = columnAt(real.x) ?: return null
        val squareX = (real.x shr 6) shl 6
        val squareZ = (real.z shr 6) shl 6
        return CoordGrid(
            regionSouthWest.x + squareX - SCENE_SQUARE_OFFSET - column.shiftX,
            regionSouthWest.z + squareZ - SCENE_SQUARE_OFFSET - REAL_MIN_Z,
            0,
        )
    }

    /** The reverse of [toRegion]: the real tile that [regionCoords] is a copy of. */
    fun toReal(regionCoords: CoordGrid, regionSouthWest: CoordGrid): CoordGrid? {
        val relativeX = regionCoords.x - regionSouthWest.x
        val relativeZ = regionCoords.z - regionSouthWest.z
        if (relativeZ !in 0 until ZONE_ROWS * ZoneGrid.LENGTH) {
            return null
        }
        val column =
            columns.firstOrNull {
                val start = it.regionZoneX * ZoneGrid.LENGTH
                relativeX in start until start + it.zoneWidth * ZoneGrid.LENGTH
            } ?: return null
        return CoordGrid(relativeX + column.shiftX, REAL_MIN_Z + relativeZ, regionCoords.level)
    }
}
