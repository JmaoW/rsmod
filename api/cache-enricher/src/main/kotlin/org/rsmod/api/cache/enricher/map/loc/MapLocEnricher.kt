package org.rsmod.api.cache.enricher.map.loc

import io.netty.buffer.ByteBuf
import org.openrs2.buffer.use
import org.openrs2.cache.Cache
import org.openrs2.crypto.SymmetricKey
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.loc.MapLocDefinition
import org.rsmod.api.cache.map.loc.MapLocListDecoder
import org.rsmod.api.cache.map.loc.MapLocListEncoder
import org.rsmod.api.cache.util.toInlineBuf
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.xtea.XteaMap
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey

/** A static loc to add to the `l` file of the map square [coords] is in. */
public data class MapLocSpawn(
    val id: Int,
    val coords: CoordGrid,
    val shape: LocShape,
    val angle: LocAngle,
)

/**
 * Adds static locs to the `l` files of a cache. The client draws a loc on a level above the
 * player's only when it comes from its own map files, as the Kourend statue's pieces do in the
 * centre of Kourend; locs spawned by the server there are never shown from the ground.
 */
public object MapLocEnricher {
    public fun encodeAll(cache: Cache, spawns: List<MapLocSpawn>, xteaMap: XteaMap) {
        for ((square, squareSpawns) in spawns.groupBy { MapSquareKey.from(it.coords) }) {
            val group = "l${square.x}_${square.z}"
            val keyArray = xteaMap[square] ?: error("Missing xtea key for map square $group")
            val key = SymmetricKey.fromIntArray(keyArray)
            val buf = cache.read(Js5Archives.MAPS, group, file = 0, key).use(ByteBuf::toInlineBuf)
            val definition = MapLocListDecoder.decode(buf)
            for (spawn in squareSpawns) {
                val coords = spawn.coords
                val localX = coords.x and (MapSquareGrid.LENGTH - 1)
                val localZ = coords.z and (MapSquareGrid.LENGTH - 1)
                val packedCoords = (coords.level shl 12) or (localX shl 6) or localZ
                val attributes = (spawn.shape.id shl 2) or spawn.angle.id
                val added = MapLocDefinition(spawn.id, packedCoords, attributes).packed
                if (added !in definition.spawns) {
                    definition.spawns.add(added)
                }
            }
            MapLocListEncoder.encodeAll(cache, mapOf(square to definition), xteaMap)
        }
    }
}
