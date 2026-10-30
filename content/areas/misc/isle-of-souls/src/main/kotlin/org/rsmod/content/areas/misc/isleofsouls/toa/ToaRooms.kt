package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.map.CoordGrid

/**
 * The four paths off the Nexus. [hud] is the number the HUD's path varbit takes while a player is
 * on the path (1-4, read from the HUD's draw script: it switches on `toa_client_current_path`), and
 * [column] is the path's column of the packed region (see [ToaPacking.columns]).
 *
 * Where a player lands in the Nexus after a path is the tile west of [back] (spread over [spread]
 * more tiles); [door] is the doorway they came through, in real coordinates.
 */
enum class ToaPath(
    val label: String,
    val hud: Int,
    val column: Int,
    val door: CoordGrid,
    val back: CoordGrid,
    val spread: Int,
) {
    Scabaras("Scabaras", 1, 0, CoordGrid(3559, 5155, 0), CoordGrid(3558, 5154, 0), 0),
    Het("Het", 2, 1, CoordGrid(3539, 5146, 0), CoordGrid(3541, 5146, 0), 2),
    Apmeken("Apmeken", 3, 2, CoordGrid(3562, 5146, 0), CoordGrid(3559, 5146, 0), 2),
    Crondis("Crondis", 4, 3, CoordGrid(3541, 5155, 0), CoordGrid(3544, 5154, 0), 0);

    /** The puzzle room, then the boss: the rooms are entered in this order. */
    val puzzle: ToaRoom
        get() = ToaRoom.entries.first { it.path == this && it.kind == RoomKind.Puzzle }

    val boss: ToaRoom
        get() = ToaRoom.entries.first { it.path == this && it.kind == RoomKind.Boss }

    /** The index of the path in [ToaRaid.pathLevels]. */
    val index: Int
        get() = ordinal
}

enum class RoomKind {
    Nexus,
    Puzzle,
    Boss,
    WardensFirst,
    WardensFinal,
    Vault,
}

/** A rectangle of the real map, inclusive, on one level. */
class ToaBox(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val level: Int) {
    operator fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.x in minX..maxX && coords.z in minZ..maxZ
}

/**
 * The rooms, in real coordinates (the packed region translates them). [spawn] is where a player
 * arrives, spread by up to [spreadX] or [spreadZ] tiles as the room needs. [challenge] is the spot
 * the room's teleport crystal or a won puzzle puts a player on, [bounds] the floor of the fight or
 * puzzle (with [excluded] corners that are not part of it), and [osmumten] where he stands once the
 * challenge is won.
 */
enum class ToaRoom(
    val label: String,
    val kind: RoomKind,
    val path: ToaPath?,
    val spawn: CoordGrid,
    val spreadX: Int = 0,
    val spreadZ: Int = 0,
    val challenge: CoordGrid? = null,
    val bounds: ToaBox? = null,
    val excluded: List<ToaBox> = emptyList(),
    val osmumten: CoordGrid? = null,
) {
    Nexus("Beneath Cursed Sands", RoomKind.Nexus, null, CoordGrid(3550, 5161, 0), spreadX = 2),
    ScabarasPuzzle(
        "Test of Isolation",
        RoomKind.Puzzle,
        ToaPath.Scabaras,
        CoordGrid(3522, 5279, 0),
        spreadZ = 2,
        challenge = CoordGrid(3575, 5280, 0),
        bounds = ToaBox(3533, 5268, 3574, 5292, 0),
    ),
    ScabarasBoss(
        "A Mother's Curse",
        RoomKind.Boss,
        ToaPath.Scabaras,
        CoordGrid(3535, 5408, 0),
        spreadZ = 2,
        challenge = CoordGrid(3544, 5408, 0),
        bounds = ToaBox(3543, 5400, 3559, 5416, 0),
        osmumten = CoordGrid(3558, 5408, 0),
    ),
    HetPuzzle(
        "Test of Strength",
        RoomKind.Puzzle,
        ToaPath.Het,
        CoordGrid(3698, 5279, 0),
        spreadZ = 2,
        challenge = CoordGrid(3667, 5280, 0),
        bounds = ToaBox(3670, 5267, 3690, 5293, 0),
    ),
    HetBoss(
        "Sands of Time",
        RoomKind.Boss,
        ToaPath.Het,
        CoordGrid(3698, 5406, 1),
        spreadZ = 2,
        challenge = CoordGrid(3689, 5408, 1),
        bounds = ToaBox(3670, 5395, 3691, 5419, 1),
        osmumten = CoordGrid(3673, 5407, 1),
    ),
    ApmekenPuzzle(
        "Test of Companionship",
        RoomKind.Puzzle,
        ToaPath.Apmeken,
        CoordGrid(3792, 5279, 0),
        spreadZ = 2,
        challenge = CoordGrid(3814, 5280, 0),
        bounds = ToaBox(3797, 5267, 3819, 5293, 0),
    ),
    ApmekenBoss(
        "Ape-ex Predator",
        RoomKind.Boss,
        ToaPath.Apmeken,
        CoordGrid(3790, 5407, 0),
        spreadZ = 2,
        challenge = CoordGrid(3800, 5408, 0),
        bounds = ToaBox(3796, 5399, 3823, 5418, 0),
        osmumten = CoordGrid(3817, 5408, 0),
    ),
    CrondisPuzzle(
        "Test of Resourcefulness",
        RoomKind.Puzzle,
        ToaPath.Crondis,
        CoordGrid(3954, 5279, 0),
        spreadZ = 2,
        challenge = CoordGrid(3943, 5280, 0),
        bounds = ToaBox(3923, 5250, 3949, 5311, 0),
    ),
    CrondisBoss(
        "Jaws of Gluttony",
        RoomKind.Boss,
        ToaPath.Crondis,
        CoordGrid(3958, 5407, 0),
        spreadZ = 2,
        challenge = CoordGrid(3941, 5408, 0),
        bounds = ToaBox(3904, 5387, 3962, 5429, 0),
        // The entrance corridor east of the arena is not part of the fight.
        excluded = listOf(ToaBox(3957, 5404, 3960, 5414, 0), ToaBox(3952, 5406, 3957, 5410, 0)),
        osmumten = CoordGrid(3928, 5408, 0),
    ),
    WardensFirst(
        "Amascut's Promise",
        RoomKind.WardensFirst,
        null,
        CoordGrid(3807, 5176, 1),
        spreadX = 2,
        challenge = CoordGrid(3808, 5166, 1),
        bounds = ToaBox(3792, 5137, 3825, 5171, 1),
    ),
    WardensFinal(
        "Amascut's Promise",
        RoomKind.WardensFinal,
        null,
        CoordGrid(3935, 5168, 1),
        spreadX = 2,
        challenge = CoordGrid(3936, 5157, 1),
        bounds = ToaBox(3924, 5151, 3947, 5166, 1),
    ),
    Vault("Laid to Rest", RoomKind.Vault, null, CoordGrid(3680, 5170, 0));

    /** Whether the real tile [real] is on the floor of the room's challenge. */
    fun inChallenge(real: CoordGrid): Boolean {
        val box = bounds ?: return false
        return real in box && excluded.none { real in it }
    }

    /** A puzzle or a boss: the room's challenge is done once and stays done. */
    val isChallenge: Boolean
        get() = kind == RoomKind.Puzzle || kind == RoomKind.Boss

    /** Anywhere the party can fail: the challenges and the Wardens. */
    val hasFight: Boolean
        get() = kind != RoomKind.Nexus && kind != RoomKind.Vault

    companion object {
        /** The row of map squares that holds the Nexus, vault and Wardens (real z 5120-5183). */
        private const val HUB_MAX_Z = 5183
        private const val PUZZLE_MAX_Z = 5311

        /**
         * The room a real tile belongs to: the hub row splits by path column into the Nexus, the
         * vault and the two Wardens rooms, and the rows above are each path's puzzle and boss.
         */
        fun at(real: CoordGrid): ToaRoom? {
            val column = ToaPacking.columnAt(real.x) ?: return null
            val index = ToaPacking.columns.indexOf(column)
            if (real.z < ToaPacking.REAL_MIN_Z || real.z > ToaPacking.REAL_MAX_Z) {
                return null
            }
            val path = ToaPath.entries.first { it.column == index }
            return when {
                real.z <= HUB_MAX_Z ->
                    when (index) {
                        0 -> Nexus
                        1 -> Vault
                        2 -> WardensFirst
                        else -> WardensFinal
                    }
                real.z <= PUZZLE_MAX_Z -> path.puzzle
                else -> path.boss
            }
        }
    }
}
