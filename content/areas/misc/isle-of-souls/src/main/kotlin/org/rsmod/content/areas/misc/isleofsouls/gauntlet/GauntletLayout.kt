package org.rsmod.content.areas.misc.isleofsouls.gauntlet

/**
 * The plan of one maze, made before any of it is drawn: a 7x7 grid of rooms, each 2x2 chunks (16x16
 * tiles). The Hunllef's room is in the middle, the start room beside it on a random side (wiki),
 * and the six demi-bosses wait in the middle three rooms of each side of the rim: the bear, the
 * dragon and the dark beast, twice each (the wiki lists the three kinds; two of each is what TSPS
 * found in the reference server).
 *
 * Pure: a random source goes in and a plan comes out, so tests can fix it.
 */
object GauntletGrid {
    const val SIZE = 7
    const val CENTRE = 3
    const val ROOM_CHUNKS = 2
    const val ROOM_TILES = ROOM_CHUNKS * 8

    /** The template chunks (regular version): rooms by exits, then the start and boss rooms. */
    const val MIDDLE_CHUNK_X = 232
    const val EDGE_CHUNK_X = 234
    const val CORNER_CHUNK_X = 236
    const val FIRST_VARIANT_CHUNK_Z = 704
    const val VARIANTS = 4
    const val START_CHUNK_X = 238
    const val START_CHUNK_Z = 708
    const val BOSS_CHUNK_X = 238
    const val BOSS_CHUNK_Z = 710

    /** The floor of the maze is on this level; the other three are copied but empty. */
    const val PLAY_LEVEL = 1
}

enum class RoomKind {
    Start,
    Boss,

    /** Four exits: the inside of the maze. */
    Middle,

    /** Three exits: the rim. */
    Edge,

    /** Two exits: the corners. */
    Corner,
}

enum class DemiBoss {
    Bear,
    Dragon,
    DarkBeast,
}

/** The four ways out of a room, as grid steps. */
enum class Side(val dx: Int, val dz: Int) {
    North(0, 1),
    East(1, 0),
    South(0, -1),
    West(-1, 0);

    val opposite: Side
        get() = entries.first { it.dx == -dx && it.dz == -dz }
}

class GauntletRoom(
    val x: Int,
    val z: Int,
    val kind: RoomKind,
    /** The south-west chunk of the template (regular version). */
    val templateChunkX: Int,
    val templateChunkZ: Int,
    /** Quarter turns clockwise. */
    val rotation: Int,
    val demiBoss: DemiBoss?,
) {
    var lit: Boolean = false

    /** Set once the room has been stocked with resources (or left bare). */
    var stocked: Boolean = false

    /** Set once the room's monsters have been put in it. */
    var populated: Boolean = false

    /** Whether the room is to hold monsters: set when it is stocked, filled by the monsters. */
    var monsters: Boolean = false

    /** The room's number in the 49 `gauntlet_room_N_found` varbits. */
    val index: Int
        get() = z * GauntletGrid.SIZE + x

    override fun toString(): String = "GauntletRoom($x,$z $kind rot=$rotation demi=$demiBoss)"
}

class GauntletLayout(val rooms: List<List<GauntletRoom>>, val startX: Int, val startZ: Int) {
    val start: GauntletRoom
        get() = checkNotNull(room(startX, startZ))

    val boss: GauntletRoom
        get() = checkNotNull(room(GauntletGrid.CENTRE, GauntletGrid.CENTRE))

    fun room(x: Int, z: Int): GauntletRoom? = rooms.getOrNull(x)?.getOrNull(z)

    fun all(): List<GauntletRoom> = rooms.flatten()

    companion object {
        /** Corners have two exits, the rest of the rim three, and the inside four. */
        fun kindAt(x: Int, z: Int): RoomKind {
            val last = GauntletGrid.SIZE - 1
            val rimX = x == 0 || x == last
            val rimZ = z == 0 || z == last
            return when {
                rimX && rimZ -> RoomKind.Corner
                rimX || rimZ -> RoomKind.Edge
                else -> RoomKind.Middle
            }
        }

        /**
         * Quarter turns that put a rim room's closed sides outward. Rooms inside the maze may face
         * any way, so they are turned at random. [pick] is a random number below its argument.
         */
        fun rotationAt(x: Int, z: Int, pick: (Int) -> Int): Int {
            val last = GauntletGrid.SIZE - 1
            return when {
                x == last && z == last -> 0
                x == 0 && z == 0 -> 2
                x == 0 && z == last -> 3
                x == last && z == 0 -> 1
                x == 0 -> 3
                x == last -> 1
                z == 0 -> 2
                z == last -> 0
                else -> pick(4)
            }
        }

        /** Demi-bosses wait in the three middle rooms of each side of the rim. */
        fun isDemiBossRoom(x: Int, z: Int): Boolean {
            val last = GauntletGrid.SIZE - 1
            val rimX = x == 0 || x == last
            val rimZ = z == 0 || z == last
            return when {
                rimX && rimZ -> false
                rimX -> z in 2..4
                rimZ -> x in 2..4
                else -> false
            }
        }

        fun plan(pick: (Int) -> Int): GauntletLayout {
            val side = Side.entries[pick(Side.entries.size)]
            val startX = GauntletGrid.CENTRE + side.dx
            val startZ = GauntletGrid.CENTRE + side.dz

            // Six demi-boss rooms, each kind twice, chosen first so the rooms can be built whole.
            val candidates =
                (0 until GauntletGrid.SIZE)
                    .flatMap { x -> (0 until GauntletGrid.SIZE).map { z -> x to z } }
                    .filter { (x, z) -> isDemiBossRoom(x, z) }
                    .toMutableList()
            val demiAt = HashMap<Pair<Int, Int>, DemiBoss>()
            for (kind in DemiBoss.entries + DemiBoss.entries) {
                demiAt[candidates.removeAt(pick(candidates.size))] = kind
            }

            val grid =
                List(GauntletGrid.SIZE) { x ->
                    List(GauntletGrid.SIZE) { z ->
                        buildRoom(x, z, startX, startZ, demiAt[x to z], pick)
                    }
                }
            return GauntletLayout(grid, startX, startZ)
        }

        private fun buildRoom(
            x: Int,
            z: Int,
            startX: Int,
            startZ: Int,
            demiBoss: DemiBoss?,
            pick: (Int) -> Int,
        ): GauntletRoom {
            if (x == GauntletGrid.CENTRE && z == GauntletGrid.CENTRE) {
                return GauntletRoom(
                    x,
                    z,
                    RoomKind.Boss,
                    GauntletGrid.BOSS_CHUNK_X,
                    GauntletGrid.BOSS_CHUNK_Z,
                    rotation = 0,
                    demiBoss = null,
                )
            }
            if (x == startX && z == startZ) {
                return GauntletRoom(
                    x,
                    z,
                    RoomKind.Start,
                    GauntletGrid.START_CHUNK_X,
                    GauntletGrid.START_CHUNK_Z,
                    rotation = 0,
                    demiBoss = null,
                )
            }
            val kind = kindAt(x, z)
            val chunkX =
                when (kind) {
                    RoomKind.Middle -> GauntletGrid.MIDDLE_CHUNK_X
                    RoomKind.Edge -> GauntletGrid.EDGE_CHUNK_X
                    else -> GauntletGrid.CORNER_CHUNK_X
                }
            val variant = pick(GauntletGrid.VARIANTS)
            return GauntletRoom(
                x,
                z,
                kind,
                chunkX,
                GauntletGrid.FIRST_VARIANT_CHUNK_Z + 2 * variant,
                rotationAt(x, z, pick),
                demiBoss,
            )
        }
    }
}
