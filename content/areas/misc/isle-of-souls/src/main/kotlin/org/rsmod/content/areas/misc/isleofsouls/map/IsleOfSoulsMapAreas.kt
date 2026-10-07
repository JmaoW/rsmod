package org.rsmod.content.areas.misc.isleofsouls.map

import org.rsmod.api.type.builders.map.area.MapAreaBuilder
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_areas
import org.rsmod.game.area.polygon.VertexCoord
import org.rsmod.map.square.MapSquareKey

/**
 * Packs the `isle_of_souls` area. The island is covered by axis-aligned rectangles (absolute,
 * inclusive tile bounds) generated from the island's land tiles in the cache terrain, so the area
 * follows the coastline instead of overlapping neighbouring islands.
 *
 * Map squares that are entirely inside the island are packed with `mapSquare` rather than polygons.
 * A map square covered on all four levels would need 256 zone entries, which the area encoder
 * cannot represent (the zone count is written as a single byte). Every rectangle below is therefore
 * clipped to a single map square that is only partially covered.
 *
 * _Note: map areas are only packed by the Gradle `packCache` task, not on normal server startup._
 */
object IsleOfSoulsMapAreas : MapAreaBuilder() {
    override fun onPackMapTask() {
        for (mapSquare in FULL_MAP_SQUARES) {
            area(isle_areas.isle_of_souls) { mapSquare(mapSquare) }
        }
        area(isle_areas.isle_of_souls) {
            for (rect in PARTIAL_RECTS) {
                polygon(levels = 0..3) {
                    vertex(rect.vertex(rect.x0, rect.z0))
                    vertex(rect.vertex(rect.x1, rect.z0))
                    vertex(rect.vertex(rect.x1, rect.z1))
                    vertex(rect.vertex(rect.x0, rect.z1))
                }
            }
        }
    }
}

private class Rect(val x0: Int, val z0: Int, val x1: Int, val z1: Int) {
    fun vertex(x: Int, z: Int) = VertexCoord(x / 64, z / 64, x % 64, z % 64)
}

private val FULL_MAP_SQUARES =
    listOf(MapSquareKey(33, 45), MapSquareKey(34, 44), MapSquareKey(34, 45), MapSquareKey(35, 45))

private val PARTIAL_RECTS =
    listOf(
        Rect(2104, 2840, 2111, 2847),
        Rect(2088, 2848, 2111, 2855),
        Rect(2080, 2856, 2111, 2863),
        Rect(2080, 2864, 2111, 2879),
        Rect(2088, 2880, 2111, 2887),
        Rect(2080, 2888, 2111, 2903),
        Rect(2088, 2904, 2111, 2935),
        Rect(2088, 2936, 2111, 2943),
        Rect(2088, 2944, 2111, 2959),
        Rect(2096, 2960, 2111, 2967),
        Rect(2160, 2776, 2175, 2783),
        Rect(2152, 2784, 2175, 2791),
        Rect(2144, 2792, 2175, 2799),
        Rect(2136, 2800, 2175, 2807),
        Rect(2136, 2808, 2175, 2815),
        Rect(2144, 2816, 2175, 2823),
        Rect(2128, 2824, 2175, 2831),
        Rect(2112, 2832, 2175, 2839),
        Rect(2112, 2840, 2175, 2847),
        Rect(2112, 2848, 2175, 2855),
        Rect(2112, 2856, 2175, 2863),
        Rect(2112, 2864, 2175, 2879),
        Rect(2112, 2944, 2175, 2959),
        Rect(2112, 2960, 2175, 2967),
        Rect(2112, 2968, 2175, 2975),
        Rect(2128, 2976, 2175, 2983),
        Rect(2144, 2984, 2175, 2991),
        Rect(2176, 2768, 2239, 2775),
        Rect(2176, 2776, 2239, 2783),
        Rect(2176, 2784, 2239, 2791),
        Rect(2176, 2792, 2239, 2799),
        Rect(2176, 2800, 2239, 2807),
        Rect(2176, 2808, 2239, 2815),
        Rect(2176, 2944, 2239, 2959),
        Rect(2176, 2960, 2239, 2967),
        Rect(2176, 2968, 2239, 2975),
        Rect(2176, 2976, 2239, 2983),
        Rect(2176, 2984, 2239, 2991),
        Rect(2176, 2992, 2223, 2999),
        Rect(2184, 3000, 2215, 3007),
        Rect(2240, 2776, 2255, 2783),
        Rect(2280, 2776, 2303, 2783),
        Rect(2240, 2784, 2303, 2791),
        Rect(2240, 2792, 2295, 2799),
        Rect(2240, 2800, 2295, 2807),
        Rect(2240, 2808, 2287, 2815),
        Rect(2240, 2816, 2287, 2823),
        Rect(2240, 2824, 2287, 2831),
        Rect(2240, 2832, 2287, 2839),
        Rect(2240, 2840, 2287, 2847),
        Rect(2240, 2848, 2303, 2855),
        Rect(2240, 2856, 2303, 2863),
        Rect(2240, 2864, 2303, 2879),
        Rect(2240, 2944, 2303, 2959),
        Rect(2240, 2960, 2303, 2967),
        Rect(2240, 2968, 2303, 2975),
        Rect(2240, 2976, 2279, 2983),
        Rect(2256, 2984, 2271, 2991),
        Rect(2304, 2856, 2311, 2863),
        Rect(2304, 2864, 2335, 2879),
        Rect(2304, 2880, 2335, 2887),
        Rect(2304, 2888, 2335, 2903),
        Rect(2304, 2904, 2343, 2935),
        Rect(2304, 2936, 2327, 2943),
        Rect(2304, 2944, 2327, 2959),
        Rect(2304, 2960, 2327, 2967),
        Rect(2304, 2968, 2311, 2975),
    )
