package org.rsmod.content.areas.misc.isleofsouls.toa

/** What a tile does to the caster statue beam. */
sealed interface HetBlocker {
    /** Nothing: the beam flies on. */
    data object Open : HetBlocker

    /** A wall, a pillar or a broken-down mirror: the beam stops. */
    data object Wall : HetBlocker

    /** A mirror turned [angle] (0-3): it may turn the beam on. */
    data class Mirror(val angle: Int) : HetBlocker
}

/** One tile of the beam: where it is, which way it runs there and whether it ends. */
class HetBeamTile(val x: Int, val z: Int, val shape: Shape) {
    enum class Shape {
        Horizontal,
        Vertical,
        SouthEast,
        SouthWest,
        NorthWest,
        NorthEast,
        End,
    }
}

class HetBeamResult(val tiles: List<HetBeamTile>, val hitsShield: Boolean)

/**
 * The beam of Het's puzzle room. It leaves the caster statue to the west and runs one tile at a
 * time until it meets something. A mirror faces one of the four diagonals (its angle: north-east,
 * south-east, south-west, north-west) and passes the beam on, turned a quarter, along the side it
 * was not hit from; hit on its back it stops there like a wall. Whatever it ends on, the shielded
 * statue is the goal.
 */
object HetBeam {
    private const val MAX_LENGTH = 200

    /** The diagonal a mirror turned [angle] faces, as (x, z) signs. */
    fun facing(angle: Int): Pair<Int, Int> =
        when (angle and 3) {
            0 -> 1 to 1
            1 -> 1 to -1
            2 -> -1 to -1
            else -> -1 to 1
        }

    /**
     * The way a beam travelling (dx, dz) leaves a mirror turned [angle], or `null` when the
     * mirror's back is to it.
     */
    fun turn(angle: Int, dx: Int, dz: Int): Pair<Int, Int>? {
        val (fx, fz) = facing(angle)
        val incomingX = -dx
        val incomingZ = -dz
        if (incomingX == fx && incomingZ == 0) {
            return 0 to fz
        }
        if (incomingX == 0 && incomingZ == fz) {
            return fx to 0
        }
        return null
    }

    private fun cornerShape(angle: Int): HetBeamTile.Shape =
        when (angle and 3) {
            0 -> HetBeamTile.Shape.NorthEast
            1 -> HetBeamTile.Shape.SouthEast
            2 -> HetBeamTile.Shape.SouthWest
            else -> HetBeamTile.Shape.NorthWest
        }

    /**
     * Follows the beam from (startX, startZ), one step west first. [blocker] says what stands on a
     * tile and [shield] whether a tile belongs to the shielded statue (or to anything else that
     * ends the beam without a wall, such as the caster itself or the seal: [stops]).
     */
    fun trace(
        startX: Int,
        startZ: Int,
        blocker: (Int, Int) -> HetBlocker,
        shield: (Int, Int) -> Boolean,
        stops: (Int, Int) -> Boolean,
    ): HetBeamResult {
        val tiles = ArrayList<HetBeamTile>()
        var x = startX
        var z = startZ
        var dx = -1
        var dz = 0
        var hitsShield = false
        for (step in 0 until MAX_LENGTH) {
            x += dx
            z += dz
            val onShield = shield(x, z)
            val hit = blocker(x, z)
            if (hit is HetBlocker.Open && !onShield && !stops(x, z)) {
                val shape =
                    if (dz == 0) HetBeamTile.Shape.Horizontal else HetBeamTile.Shape.Vertical
                tiles += HetBeamTile(x, z, shape)
                continue
            }
            if (hit is HetBlocker.Mirror) {
                val turned = turn(hit.angle, dx, dz)
                if (turned != null) {
                    tiles += HetBeamTile(x, z, cornerShape(hit.angle))
                    dx = turned.first
                    dz = turned.second
                    continue
                }
            }
            hitsShield = onShield
            tiles += HetBeamTile(x, z, HetBeamTile.Shape.End)
            break
        }
        return HetBeamResult(tiles, hitsShield)
    }
}
