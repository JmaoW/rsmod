package org.rsmod.api.cache.enricher.map.tile

import java.io.ByteArrayOutputStream
import org.openrs2.cache.Cache
import org.rsmod.api.cache.Js5Archives
import org.rsmod.api.cache.map.tile.MapTileByteDefinition
import org.rsmod.api.cache.map.tile.MapTileByteEncoder
import org.rsmod.api.cache.util.readOrNull
import org.rsmod.map.CoordGrid
import org.rsmod.map.square.MapSquareGrid
import org.rsmod.map.square.MapSquareKey

/** A rule (a `TILE_RULE_*` bit) to add to the tile at [coords] in its map square's `m` file. */
public data class TileRule(val coords: CoordGrid, val rule: Int = 0, val height: Int? = null)

/**
 * Adds tile rules to the `m` files of the game cache, so a tile's upper levels are drawn the way
 * they are elsewhere in the cache. The tile stream is copied as it is, with one rule opcode added
 * just before the terminator of each tile that needs one. A tile that already has a rule is an
 * error, since the cache keeps only one rule per tile.
 */
public object MapTileRuleEnricher {
    /** Rule 8 (VISIBLE_BELOW): the scenery on this level is drawn over the level below. */
    public const val TILE_RULE_VISIBLE_BELOW: Int = 0x8

    private const val TILES_PER_LEVEL = MapSquareGrid.LENGTH * MapSquareGrid.LENGTH
    private val TILE_COUNT = CoordGrid.LEVEL_COUNT * TILES_PER_LEVEL

    public fun encodeAll(cache: Cache, rules: List<TileRule>) {
        for ((square, squareRules) in rules.groupBy { MapSquareKey.from(it.coords) }) {
            val group = "m${square.x}_${square.z}"
            val buf =
                cache.readOrNull(Js5Archives.MAPS, group) ?: error("Missing map square $group")
            val data = ByteArray(buf.readableBytes()).also { buf.getBytes(buf.readerIndex(), it) }
            val updated = addRules(data, squareRules)
            MapTileByteEncoder.encodeAll(cache, mapOf(square to MapTileByteDefinition(updated)))
        }
    }

    internal fun addRules(data: ByteArray, rules: List<TileRule>): ByteArray {
        val pending =
            rules
                .filter { it.rule != 0 }
                .associate { tileIndex(it.coords) to it.rule }
                .toMutableMap()
        val heights =
            rules
                .mapNotNull { rule -> rule.height?.let { tileIndex(rule.coords) to it } }
                .toMap()
                .toMutableMap()
        val out = ByteArrayOutputStream(data.size + 4 * rules.size)
        var p = 0
        var tile = 0
        while (tile < TILE_COUNT) {
            while (true) {
                val op = u16(data, p)
                when {
                    op == 0 || op == 1 -> {
                        pending.remove(tile)?.let { rule -> writeU16(out, 49 + rule) }
                        val height = heights.remove(tile)
                        if (height != null) {
                            // A height is the tile's last opcode, so it replaces the terminator.
                            check(op == 0) {
                                "Tile $tile already has a height; cannot add another."
                            }
                            writeU16(out, 1)
                            out.write(height)
                            p += 2
                        } else {
                            val length = if (op == 1) 3 else 2
                            out.write(data, p, length)
                            p += length
                        }
                        tile++
                        break
                    }
                    op <= 49 -> {
                        out.write(data, p, 4)
                        p += 4
                    }
                    else -> {
                        check(op !in 50..81 || tile !in pending) {
                            "Tile $tile already has a rule; cannot add another."
                        }
                        out.write(data, p, 2)
                        p += 2
                    }
                }
            }
        }
        check(pending.isEmpty()) { "Tiles not found in map square: ${pending.keys}" }
        check(heights.isEmpty()) { "Tiles not found in map square: ${heights.keys}" }
        out.write(data, p, data.size - p)
        return out.toByteArray()
    }

    /** The order the `m` file lists its tiles in: level, then x, then z. */
    private fun tileIndex(coords: CoordGrid): Int {
        val x = coords.x and (MapSquareGrid.LENGTH - 1)
        val z = coords.z and (MapSquareGrid.LENGTH - 1)
        return coords.level * TILES_PER_LEVEL + x * MapSquareGrid.LENGTH + z
    }

    private fun u16(data: ByteArray, p: Int): Int =
        ((data[p].toInt() and 0xFF) shl 8) or (data[p + 1].toInt() and 0xFF)

    private fun writeU16(out: ByteArrayOutputStream, value: Int) {
        out.write(value shr 8)
        out.write(value)
    }
}
