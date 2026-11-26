package org.rsmod.content.areas.misc.isleofsouls.cox

/**
 * What a Chambers of Xeric raid is made of and how its rooms are laid out.
 *
 * Every room is a 32 by 32 tile (4 by 4 zone) block of the cache's raid map. The cache keeps three
 * versions of most rooms side by side, at zone x 408, 412 and 416: the way on turns west, goes
 * straight on (north) or turns east, always for a party that came in from the south. A raid copies
 * each room turned so that its way in faces the room before it.
 *
 * Sources: the room rows, the 26 layout patterns and the two combat rotations are the ones the
 * RuneLite raids plugin and TSPS (`docs/chambers-of-xeric.md`) use; the cache's map agrees with the
 * room rows and versions (checked with the map tools in `docs/isle_of_souls_cox_plan.md`).
 * Challenge Mode's three fixed floors are the Wiki's.
 */
enum class CoxRoomKind {
    Start,
    End,
    Scavengers,
    Combat,
    Resources,
    Puzzle,
}

enum class CoxRoomType(
    val label: String,
    val zoneZ: Int,
    val sourceLevel: Int,
    val kind: CoxRoomKind,
    /** The room's versions that exist (the floor's end room has no east-turning one). */
    val variants: Int = 3,
) {
    FloorEnd("Floor end", 644, 0, CoxRoomKind.End, variants = 2),
    FloorStart("Floor start", 712, 0, CoxRoomKind.Start),
    RaidStart("Start", 648, 0, CoxRoomKind.Start),
    SmallScavengers("Scavengers", 652, 0, CoxRoomKind.Scavengers),
    Shamans("Shamans", 656, 0, CoxRoomKind.Combat),
    Vasa("Vasa", 660, 0, CoxRoomKind.Combat),
    Vanguards("Vanguards", 664, 0, CoxRoomKind.Combat),
    IceDemon("Ice Demon", 668, 0, CoxRoomKind.Puzzle),
    Thieving("Thieving", 672, 0, CoxRoomKind.Puzzle),
    ResourcesA("Farming", 680, 0, CoxRoomKind.Resources),
    LargeScavengers("Scavengers", 652, 1, CoxRoomKind.Scavengers),
    Mystics("Mystics", 656, 1, CoxRoomKind.Combat),
    Tekton("Tekton", 660, 1, CoxRoomKind.Combat),
    Muttadiles("Muttadiles", 664, 1, CoxRoomKind.Combat),
    Tightrope("Tightrope", 668, 1, CoxRoomKind.Puzzle),
    ResourcesB("Farming", 680, 1, CoxRoomKind.Resources),
    Guardians("Guardians", 656, 2, CoxRoomKind.Combat),
    Vespula("Vespula", 660, 2, CoxRoomKind.Combat),
    Crabs("Crabs", 668, 2, CoxRoomKind.Puzzle);

    /** The zone x of the version: 0 turns west, 1 straight on, 2 turns east. */
    fun zoneX(variant: Int): Int = VARIANT_ZONE_X[variant]

    /** The south-west tile of the version on the cache's map. */
    fun sourceX(variant: Int): Int = zoneX(variant) * 8

    val sourceZ: Int
        get() = zoneZ * 8

    companion object {
        val VARIANT_ZONE_X = listOf(408, 412, 416)
        const val ROOM_TILES = 32
        const val ROOM_ZONES = 4

        val PUZZLES = listOf(IceDemon, Thieving, Tightrope, Crabs)
        val SCAVENGERS = listOf(SmallScavengers, LargeScavengers)
        val RESOURCES = listOf(ResourcesA, ResourcesB)
    }
}

/** A compass direction a path leaves a room by (and the next room is entered travelling). */
enum class CoxDirection(val dx: Int, val dz: Int) {
    North(0, 1),
    East(1, 0),
    South(0, -1),
    West(-1, 0);

    /** Quarter turns clockwise from north: how far a room entered this way is turned. */
    val rotation: Int
        get() = ordinal

    fun left(): CoxDirection = entries[(ordinal + 3) % 4]

    fun right(): CoxDirection = entries[(ordinal + 1) % 4]

    companion object {
        fun of(char: Char): CoxDirection =
            when (char) {
                'N' -> North
                'E' -> East
                'S' -> South
                'W' -> West
                else -> error("Not a direction: $char")
            }
    }
}

/**
 * One room of one raid: which room, which version, where it sits in the floor's grid (in rooms),
 * the floor (0 the top) and how far it is turned. The plane it is copied onto is [floor]'s.
 */
data class CoxPlacement(
    val index: Int,
    val type: CoxRoomType,
    val floor: Int,
    val gridX: Int,
    val gridZ: Int,
    val rotation: Int,
    val variant: Int,
) {
    val plane: Int
        get() = CoxLayout.FLOOR_PLANES[floor]
}

/** A raid's rooms, floor by floor, and the pattern they came from. */
class CoxLayout(val pattern: String?, val challenge: Boolean, val rooms: List<CoxPlacement>) {
    val floors: Int
        get() = rooms.maxOf { it.floor } + 1

    fun floor(floor: Int): List<CoxPlacement> = rooms.filter { it.floor == floor }

    fun next(room: CoxPlacement): CoxPlacement? = rooms.getOrNull(room.index + 1)

    fun previous(room: CoxPlacement): CoxPlacement? = rooms.getOrNull(room.index - 1)

    /** How many rooms wide and long the floors are, the largest of them. */
    val gridWidth: Int
        get() = rooms.maxOf { it.gridX } + 1

    val gridLength: Int
        get() = rooms.maxOf { it.gridZ } + 1

    override fun toString(): String =
        (0 until floors).joinToString(" | ") { f -> floor(f).joinToString(", ") { it.type.label } }

    companion object {
        /** Floors are copied onto planes 3, 2 and (Challenge Mode) 1; the Olm's chamber is on 0. */
        val FLOOR_PLANES = listOf(3, 2, 1)

        /**
         * The patterns: room kinds (# start, * end, S scavengers, C combat, F resources, P puzzle)
         * for both floors, then the way the path goes into each room (the first room's letter is a
         * placeholder).
         */
        val PATTERNS =
            listOf(
                "#FSCCP*#PCSCF* - #WNWSWN#ESEENW",
                "#FSCCS*#PCPSF* - #WSEEEN#WSWNWS",
                "#FSCPC*#CSCPF* - #WNWWSE#EENWWW",
                "#SCCFC*#PSCSF* - #EEENWW#WSEEEN",
                "#SCCFP*#CCSPF* - #NESEEN#WSWNWS",
                "#SCFCP*#CCSPF* - #ESEENW#ESWWNW",
                "#SCFCP*#CSCFS* - #ENEESW#ENWWSW",
                "#SCFPC*#CSPCF* - #WSWWNE#WSEENE",
                "#SCFPC*#PCCSF* - #WSEENE#WWWSEE",
                "#SCFPC*#SCPCF* - #NESENE#WSWWNE",
                "#SCPFC*#CCPSF* - #NWWWSE#WNEESE",
                "#SCPFC*#CSPCF* - #NEEESW#WWNEEE",
                "#SCPFC*#CSPSF* - #WWSEEE#NWSWWN",
                "#SCSPF*#CCSPF* - #ESWWNW#ESENES",
                "#SFCCP*#CSCPF* - #WNEESE#NWSWWN",
                "#SFCCS*#PCPSF* - #ENWWSW#ENESEN",
                "#SPCFC*#CSPCF* - #WWNEEE#WSWNWS",
                "#SPCFC*#SCCPF* - #ESENES#WWWNEE",
                "#SPSFP*#CCCSF* - #NWSWWN#ESEENW",
                "#SCFCP*#CSCPF* - #ENESEN#WWWSEE",
                "#SCPFC*#PCSCF* - #WNEEES#NWSWNW",
                "#FSPCC*#PSCCF* - #WWWSEE#ENWWSW",
                "#FSCCP*#PCSCF* - #ENWWWS#NEESEN",
                "#SCPFC*#CCSSF* - #NEESEN#WSWWNE",
                "#SFCCPC*#PCSCPF* - #WSEENES#WWWNEEE",
                "#SCFCPC*#CSPCSF* - #ESWWNWS#NESENES",
            )

        /** The two orders combat rooms come in (Wiki); a raid takes a run from either. */
        val ROTATIONS =
            listOf(
                listOf(
                    CoxRoomType.Tekton,
                    CoxRoomType.Vasa,
                    CoxRoomType.Guardians,
                    CoxRoomType.Mystics,
                    CoxRoomType.Shamans,
                    CoxRoomType.Muttadiles,
                    CoxRoomType.Vanguards,
                    CoxRoomType.Vespula,
                ),
                listOf(
                    CoxRoomType.Tekton,
                    CoxRoomType.Muttadiles,
                    CoxRoomType.Guardians,
                    CoxRoomType.Vespula,
                    CoxRoomType.Shamans,
                    CoxRoomType.Vasa,
                    CoxRoomType.Vanguards,
                    CoxRoomType.Mystics,
                ),
            )

        /** Challenge Mode's floors (Wiki): each room and the way the path leaves it. */
        val CHALLENGE_FLOORS: List<List<Pair<CoxRoomType, CoxDirection>>> =
            listOf(
                listOf(
                    CoxRoomType.RaidStart to CoxDirection.West,
                    CoxRoomType.Tekton to CoxDirection.West,
                    CoxRoomType.Crabs to CoxDirection.North,
                    CoxRoomType.SmallScavengers to CoxDirection.East,
                    CoxRoomType.IceDemon to CoxDirection.East,
                    CoxRoomType.Shamans to CoxDirection.East,
                    CoxRoomType.ResourcesB to CoxDirection.South,
                    CoxRoomType.FloorEnd to CoxDirection.South,
                ),
                listOf(
                    CoxRoomType.FloorStart to CoxDirection.West,
                    CoxRoomType.Vanguards to CoxDirection.West,
                    CoxRoomType.Thieving to CoxDirection.West,
                    CoxRoomType.SmallScavengers to CoxDirection.North,
                    CoxRoomType.Vespula to CoxDirection.East,
                    CoxRoomType.ResourcesA to CoxDirection.East,
                    CoxRoomType.Tightrope to CoxDirection.East,
                    CoxRoomType.FloorEnd to CoxDirection.East,
                ),
                listOf(
                    CoxRoomType.FloorStart to CoxDirection.West,
                    CoxRoomType.Guardians to CoxDirection.West,
                    CoxRoomType.Vasa to CoxDirection.West,
                    CoxRoomType.SmallScavengers to CoxDirection.South,
                    CoxRoomType.Mystics to CoxDirection.East,
                    CoxRoomType.Muttadiles to CoxDirection.East,
                    CoxRoomType.ResourcesB to CoxDirection.East,
                    CoxRoomType.FloorEnd to CoxDirection.East,
                ),
            )

        /** A normal raid: a random pattern, a run of a random rotation, two of the puzzles. */
        fun normal(random: (Int) -> Int): CoxLayout {
            val code = PATTERNS[random(PATTERNS.size)]
            val (kinds, ways) = code.split(" - ")
            val rotation = ROTATIONS[random(ROTATIONS.size)]
            var combatAt = random(rotation.size)
            val puzzles = CoxRoomType.PUZZLES.toMutableList()
            repeat(2) { puzzles.removeAt(random(puzzles.size)) }
            val scavengers = CoxRoomType.SCAVENGERS.toMutableList()
            val resources = CoxRoomType.RESOURCES.toMutableList()
            fun take(pool: MutableList<CoxRoomType>, all: List<CoxRoomType>): CoxRoomType =
                if (pool.isEmpty()) all[random(all.size)] else pool.removeAt(random(pool.size))
            val types =
                kinds.mapIndexed { index, char ->
                    when (char) {
                        '#' -> if (index == 0) CoxRoomType.RaidStart else CoxRoomType.FloorStart
                        '*' -> CoxRoomType.FloorEnd
                        'S' -> take(scavengers, CoxRoomType.SCAVENGERS)
                        'F' -> take(resources, CoxRoomType.RESOURCES)
                        'P' -> take(puzzles, CoxRoomType.PUZZLES)
                        'C' -> rotation[combatAt++ % rotation.size]
                        else -> error("Unknown room kind $char in $code")
                    }
                }
            val half = types.size / 2
            val floors =
                listOf(0 until half, half until types.size).map { range ->
                    range.map { i ->
                        // The pattern gives the way into each room; a room is left the way the
                        // next one is entered (the floor's last room by its hole, any way).
                        val leave = ways.getOrNull(i + 1)?.takeIf { i + 1 < range.last + 1 }
                        types[i] to (leave?.let(CoxDirection::of) ?: CoxDirection.South)
                    }
                }
            return lay(code, challenge = false, floors, random)
        }

        fun challenge(): CoxLayout = lay(null, challenge = true, CHALLENGE_FLOORS) { 0 }

        /**
         * Lays each floor out as a path on a grid, starting at its first room. A room is copied
         * turned to face the way the path came in, as the version whose way out turns the way the
         * path goes on.
         */
        fun lay(
            pattern: String?,
            challenge: Boolean,
            floors: List<List<Pair<CoxRoomType, CoxDirection>>>,
            random: (Int) -> Int,
        ): CoxLayout {
            val rooms = ArrayList<CoxPlacement>()
            for ((floor, path) in floors.withIndex()) {
                var x = 0
                var z = 0
                // The first room is always copied unturned: the party climbs in from the south.
                var entering = CoxDirection.North
                val placed = ArrayList<Triple<Int, Int, Pair<CoxRoomType, Int>>>()
                val turns = ArrayList<Int>()
                for ((type, leave) in path) {
                    // The floor's end room is the one room whose versions differ by what they
                    // hold, not by the way out: the first has the hole down to the next floor,
                    // the second the hole down to the Great Olm (cache map).
                    val variant =
                        when {
                            type == CoxRoomType.FloorEnd -> if (floor == floors.size - 1) 1 else 0
                            leave == entering.left() -> 0
                            leave == entering -> 1
                            else -> 2
                        }
                    placed += Triple(x, z, type to variant)
                    turns += entering.rotation
                    x += leave.dx
                    z += leave.dz
                    entering = leave
                }
                val minX = placed.minOf { it.first }
                val minZ = placed.minOf { it.second }
                for ((i, entry) in placed.withIndex()) {
                    val (px, pz, typed) = entry
                    rooms +=
                        CoxPlacement(
                            index = rooms.size,
                            type = typed.first,
                            floor = floor,
                            gridX = px - minX,
                            gridZ = pz - minZ,
                            rotation = turns[i],
                            variant = typed.second,
                        )
                }
            }
            return CoxLayout(pattern, challenge, rooms)
        }
    }
}
