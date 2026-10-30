package org.rsmod.api.cache.enricher

import jakarta.inject.Inject
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.enricher.map.area.AreaCacheEnricher
import org.rsmod.api.cache.enricher.map.area.EnrichedAreaConfig
import org.rsmod.api.cache.enricher.map.loc.MapLocEnricher
import org.rsmod.api.cache.enricher.map.loc.MapLocSpawn
import org.rsmod.api.cache.enricher.map.tile.MapTileRuleEnricher
import org.rsmod.api.cache.enricher.map.tile.TileRule
import org.rsmod.api.cache.map.area.MapAreaDecoder
import org.rsmod.api.cache.map.area.MapAreaDefinition
import org.rsmod.api.cache.map.area.MapAreaEncoder
import org.rsmod.api.cache.util.EncoderContext
import org.rsmod.api.cache.util.readOrNull
import org.rsmod.api.cache.util.toInlineBuf
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.xtea.XteaMap
import org.rsmod.game.type.dbtable.DbTableTypeList
import org.rsmod.game.type.param.ParamTypeList
import org.rsmod.game.type.varp.VarpTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareKey

public class CacheEnrichmentMaps
@Inject
constructor(
    private val paramTypes: ParamTypeList,
    private val varpTypes: VarpTypeList,
    private val dbTableTypes: DbTableTypeList,
    private val areaEnrichments: Set<AreaCacheEnricher>,
    private val xteaMap: XteaMap,
) {
    public fun encodeAll(dest: Cache) {
        val ctx = encoderContext()
        val areas = areaEnrichments.flatMap(AreaCacheEnricher::generate).collect(dest)
        MapAreaEncoder.encodeAll(dest, areas, ctx)
        encodeClientMaps(dest)
    }

    /**
     * Writes the terrain and static loc edits into [dest]. The game cache is the server's copy; the
     * client is served from its own cache, so the packer calls this for that cache too.
     */
    public fun encodeClientMaps(dest: Cache) {
        MapTileRuleEnricher.encodeAll(dest, tileRules)
        MapLocEnricher.encodeAll(dest, mapLocs, xteaMap)
    }

    /**
     * The Kourend statue is a 5x5 loc (the plinth on level 0, its three pieces on levels 1 to 3)
     * with its south-west corner on [KOUREND_STATUE]. The tiles are copied from the statue in the
     * centre of Kourend:
     * - Levels 1 to 3 have the visible-below rule over the whole 5x5, or the pieces above the
     *   plinth are not drawn from the ground.
     * - A loc is drawn at the average height of four tiles near its middle, and a tile on an upper
     *   level is 240 units above the same tile one level down unless it has a height of its own.
     *   The centre's pieces are stacked 272, 224 and 240 apart (heights 34 on level 1, 28 on level
     *   2 and 30 on level 3), not 240 each, or there is a thin gap between the pieces.
     */
    private val tileRules: List<TileRule> =
        (1..3).flatMap { level ->
            (0..5).flatMap { dx ->
                (0..6).mapNotNull { dz ->
                    val rule =
                        if (dx <= 4 && dz <= 4) MapTileRuleEnricher.TILE_RULE_VISIBLE_BELOW else 0
                    val height =
                        when (level) {
                            1 -> if (dz >= 1) 34 else null
                            2 -> if (dz >= 1) 28 else null
                            else -> if (dz <= 5) 30 else null
                        }
                    if (rule == 0 && height == null) {
                        null
                    } else {
                        TileRule(KOUREND_STATUE.translate(dx, dz, level), rule, height)
                    }
                }
            }
        }

    /**
     * The Kourend statue's three pieces (bottom, middle, top: locs 14193 to 14195), stacked on the
     * catacombs entrance's plinth. They are static map locs because the client only draws upper
     * level locs from its own map files.
     */
    private val mapLocs: List<MapLocSpawn> =
        (1..3).map { level ->
            MapLocSpawn(
                id = 14192 + level,
                coords = KOUREND_STATUE.translateLevel(level),
                shape = LocShape.CentrepieceStraight,
                angle = LocAngle.South,
            )
        }

    private fun Iterable<EnrichedAreaConfig>.collect(
        cache: Cache
    ): Map<MapSquareKey, MapAreaDefinition> {
        val merged = loadCacheAreas(cache)
        for (config in this) {
            merged.merge(config.square, config.areas, MapAreaDefinition::merge)
        }
        return merged
    }

    private fun Iterable<EnrichedAreaConfig>.loadCacheAreas(
        cache: Cache
    ): MutableMap<MapSquareKey, MapAreaDefinition> {
        val areas = mutableMapOf<MapSquareKey, MapAreaDefinition>()
        for (config in this) {
            val (mx, mz) = config.square
            val buffer = cache.readOrNull(Js5Archives.MAPS, "a${mx}_$mz") ?: continue
            val area = MapAreaDecoder.decode(buffer.toInlineBuf())
            areas[config.square] = area
        }
        return areas
    }

    private fun encoderContext(): EncoderContext {
        return EncoderContext.server(
            params = paramTypes.filterTransmitKeys(),
            varps = varpTypes.filterTransmitKeys(),
            tables = dbTableTypes.filterTransmitKeys(),
        )
    }

    private companion object {
        /**
         * The tile the Kourend statue's plinth stands on (level 0). Keep in sync with `Dungeon`.
         */
        val KOUREND_STATUE = CoordGrid(2233, 2866, 0)
    }
}
