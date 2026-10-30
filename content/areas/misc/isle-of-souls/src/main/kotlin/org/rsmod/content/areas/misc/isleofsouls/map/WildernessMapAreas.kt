package org.rsmod.content.areas.misc.isleofsouls.map

import org.rsmod.api.config.refs.areas
import org.rsmod.api.type.builders.map.area.MapAreaBuilder
import org.rsmod.map.square.MapSquareKey

/**
 * Packs the `wilderness` area: the Revenant Caves, the Escape Caves and the boss lairs, and the
 * three lairs' singles copies. It decides where revenants and other aggressive npcs go for players
 * of any combat level (an npc skips a player more than twice its level outside the Wilderness). The
 * revenant weapons' bonus does not depend on it: it works everywhere.
 *
 * The caves lie under the real Wilderness, in the map squares x 49 to 53 and z 156 to 161 (tiles x
 * 3136 to 3455, z 9984 to 10367); the singles lairs were copied far to the west (squares 25, 27 and
 * 29, z 180). Whole map squares are used, so nothing else may be built in them.
 *
 * _Note: map areas are only packed by the Gradle `packCache` task, not on normal server startup._
 */
object WildernessMapAreas : MapAreaBuilder() {
    override fun onPackMapTask() {
        for (square in SQUARES) {
            area(areas.wilderness) { mapSquare(square) }
        }
    }

    private val SQUARES: List<MapSquareKey> =
        (49..53).flatMap { x -> (156..161).map { z -> MapSquareKey(x, z) } } +
            listOf(MapSquareKey(25, 180), MapSquareKey(27, 180), MapSquareKey(29, 180))
}
