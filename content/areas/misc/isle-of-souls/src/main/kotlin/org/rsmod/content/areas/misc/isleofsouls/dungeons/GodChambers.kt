package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.player.isValidTarget
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid

/** A general's chamber: a rectangle of floor on one level. */
class GodChamber(val level: Int, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
    fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.x in minX..maxX && coords.z in minZ..maxZ

    /** Whether no player who can be attacked is standing in the chamber. */
    fun isEmpty(players: PlayerList): Boolean =
        players.none { it.isValidTarget() && contains(it.coords) }
}

/** The chambers, measured from the cache floor (flood fill from each door). */
object GodChambers {
    /** Armadyl's: 19 by 13 tiles. */
    val armadyl = GodChamber(level = 2, minX = 2824, maxX = 2842, minZ = 5296, maxZ = 5308)

    /** Bandos': 13 by 19 tiles. */
    val bandos = GodChamber(level = 2, minX = 2864, maxX = 2876, minZ = 5351, maxZ = 5369)
    /**
     * Saradomin's: 19 by 18 tiles, on level 0 (the encampment is on the dungeon's bottom level).
     */
    val saradomin = GodChamber(level = 0, minX = 2889, maxX = 2907, minZ = 5258, maxZ = 5275)

    /** Zamorak's: 19 by 14 tiles. */
    val zamorak = GodChamber(level = 2, minX = 2918, maxX = 2936, minZ = 5318, maxZ = 5331)
}
