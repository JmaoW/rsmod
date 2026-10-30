package org.rsmod.content.areas.misc.isleofsouls.wilderness

import org.rsmod.game.loc.LocAngle
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

/** The three bosses and their weaker singles-plus versions share a family of mechanics. */
enum class WildFamily {
    Callisto,
    Venenatis,
    Vetion,
}

/** A rectangle of tiles on one level. */
data class WildRect(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val level: Int) {
    operator fun contains(coords: CoordGrid): Boolean =
        coords.level == level && coords.x in minX..maxX && coords.z in minZ..maxZ

    fun shift(dx: Int, dz: Int) = WildRect(minX + dx, minZ + dz, maxX + dx, maxZ + dz, level)

    fun tiles(): List<CoordGrid> =
        (minZ..maxZ).flatMap { z -> (minX..maxX).map { x -> CoordGrid(x, z, level) } }
}

private fun CoordGrid.shift(dx: Int, dz: Int) = CoordGrid(x + dx, z + dz, level)

/**
 * The way into a lair from the Revenant Caves: a copy of the real surface entrance object, stood in
 * the big cavern beside the cave's southern arrival. [sw] is the south-west tile of its footprint
 * (after turning it by [angle]) and [front] a floor tile beside it that players land on when they
 * leave a lair by it.
 */
class WildEntrance(
    val type: LocType,
    val sw: CoordGrid,
    val angle: LocAngle,
    val front: CoordGrid,
    val width: Int,
    val length: Int,
)

/** The real maps' coordinates, read from the cache with `tools/dungeons/census.py`. */
private object WildGeo {
    // Callisto's Den: a small oval cave on the surface level, exit at the south end.
    val CALLISTO_ARENA = WildRect(3351, 10317, 3369, 10336, 0)
    val CALLISTO_AREA = WildRect(3351, 10315, 3369, 10336, 0)
    val CALLISTO_ARRIVAL = CoordGrid(3360, 10333, 0)
    val CALLISTO_SPAWN = CoordGrid(3358, 10320, 0)
    val CALLISTO_EXIT = CoordGrid(3358, 10315, 0)

    // The Silk Chasm: a square platform on level 2 with a 3-wide passage down to the exit.
    val SILK_ARENA = WildRect(3415, 10195, 3431, 10213, 2)
    val SILK_AREA = WildRect(3415, 10183, 3431, 10213, 2)
    val SILK_ARRIVAL = CoordGrid(3423, 10210, 2)
    val SILK_SPAWN = CoordGrid(3421, 10201, 2)
    val SILK_EXIT = CoordGrid(3422, 10183, 2)

    // Vet'ion's Rest: the same on level 1.
    val VETION_ARENA = WildRect(3287, 10195, 3303, 10209, 1)
    val VETION_AREA = WildRect(3287, 10190, 3303, 10209, 1)
    val VETION_ARRIVAL = CoordGrid(3295, 10206, 1)
    val VETION_SPAWN = CoordGrid(3294, 10200, 1)
    val VETION_EXIT = CoordGrid(3294, 10190, 1)
}

/**
 * One Wilderness boss lair (OSRS Wiki: Callisto's Den, Silk Chasm, Vet'ion's Rest and their singles
 * versions Hunter's End, Web Chasm and Skeletal Tomb). The lairs are the real maps, untouched: the
 * three big ones lie under the Wilderness and hold a multi-combat fight, the three small ones are
 * copies of them far to the west with a weaker boss and the singles-plus rule.
 *
 * @property arena the floor the fight is fought on. [area] adds the corridor to the exit.
 * @property arrival where a player who comes in lands: the north end of the arena (wiki).
 * @property bossSpawn the south-west tile the boss stands on, in front of the exit (wiki).
 * @property exit the object that leads out, and where it stands.
 */
enum class WildLair(
    val key: String,
    val title: String,
    val family: WildFamily,
    val multi: Boolean,
    val boss: NpcType,
    val arena: WildRect,
    val area: WildRect,
    val arrival: CoordGrid,
    val bossSpawn: CoordGrid,
    val exitType: LocType,
    val exitCoords: CoordGrid,
    val entrance: WildEntrance,
) {
    CALLISTO(
        key = "callisto",
        title = "Callisto's Den",
        family = WildFamily.Callisto,
        multi = true,
        boss = wild_npcs.callisto,
        arena = WildGeo.CALLISTO_ARENA,
        area = WildGeo.CALLISTO_AREA,
        arrival = WildGeo.CALLISTO_ARRIVAL,
        bossSpawn = WildGeo.CALLISTO_SPAWN,
        exitType = wild_locs.callisto_exit,
        exitCoords = WildGeo.CALLISTO_EXIT,
        entrance =
            WildEntrance(
                wild_locs.callisto_entrance,
                CoordGrid(3148, 10113, 0),
                LocAngle.East,
                CoordGrid(3152, 10115, 0),
                width = 5,
                length = 4,
            ),
    ),
    HUNTERS_END(
        key = "hunters",
        title = "Hunter's End",
        family = WildFamily.Callisto,
        multi = false,
        boss = wild_npcs.artio,
        arena = WildGeo.CALLISTO_ARENA.shift(-1600, 1216),
        area = WildGeo.CALLISTO_AREA.shift(-1600, 1216),
        arrival = WildGeo.CALLISTO_ARRIVAL.shift(-1600, 1216),
        bossSpawn = WildGeo.CALLISTO_SPAWN.shift(1 - 1600, 1216),
        exitType = wild_locs.callisto_exit,
        exitCoords = WildGeo.CALLISTO_EXIT.shift(-1600, 1216),
        entrance =
            WildEntrance(
                wild_locs.artio_entrance,
                CoordGrid(3200, 10055, 0),
                LocAngle.East,
                CoordGrid(3201, 10058, 0),
                width = 3,
                length = 3,
            ),
    ),
    SILK_CHASM(
        key = "silk",
        title = "Silk Chasm",
        family = WildFamily.Venenatis,
        multi = true,
        boss = wild_npcs.venenatis,
        arena = WildGeo.SILK_ARENA,
        area = WildGeo.SILK_AREA,
        arrival = WildGeo.SILK_ARRIVAL,
        bossSpawn = WildGeo.SILK_SPAWN,
        exitType = wild_locs.venenatis_exit,
        exitCoords = WildGeo.SILK_EXIT,
        entrance =
            WildEntrance(
                wild_locs.venenatis_entrance,
                CoordGrid(3163, 10169, 0),
                LocAngle.West,
                CoordGrid(3165, 10168, 0),
                width = 4,
                length = 2,
            ),
    ),
    WEB_CHASM(
        key = "web",
        title = "Web Chasm",
        family = WildFamily.Venenatis,
        multi = false,
        boss = wild_npcs.spindel,
        arena = WildGeo.SILK_ARENA.shift(-1792, 1344),
        area = WildGeo.SILK_AREA.shift(-1792, 1344),
        arrival = WildGeo.SILK_ARRIVAL.shift(-1792, 1344),
        bossSpawn = WildGeo.SILK_SPAWN.shift(-1792, 1344),
        exitType = wild_locs.venenatis_exit,
        exitCoords = WildGeo.SILK_EXIT.shift(-1792, 1344),
        entrance =
            WildEntrance(
                wild_locs.spindel_entrance,
                CoordGrid(3229, 10094, 0),
                LocAngle.North,
                CoordGrid(3228, 10095, 0),
                width = 3,
                length = 3,
            ),
    ),
    VETIONS_REST(
        key = "vetion",
        title = "Vet'ion's Rest",
        family = WildFamily.Vetion,
        multi = true,
        boss = wild_npcs.vetion,
        arena = WildGeo.VETION_ARENA,
        area = WildGeo.VETION_AREA,
        arrival = WildGeo.VETION_ARRIVAL,
        bossSpawn = WildGeo.VETION_SPAWN,
        exitType = wild_locs.vetion_exit,
        exitCoords = WildGeo.VETION_EXIT,
        entrance =
            WildEntrance(
                wild_locs.vetion_entrance,
                CoordGrid(3252, 10167, 0),
                LocAngle.North,
                CoordGrid(3251, 10168, 0),
                width = 4,
                length = 2,
            ),
    ),
    SKELETAL_TOMB(
        key = "tomb",
        title = "Skeletal Tomb",
        family = WildFamily.Vetion,
        multi = false,
        boss = wild_npcs.calvarion,
        arena = WildGeo.VETION_ARENA.shift(-1408, 1344),
        area = WildGeo.VETION_AREA.shift(-1408, 1344),
        arrival = WildGeo.VETION_ARRIVAL.shift(-1408, 1344),
        bossSpawn = WildGeo.VETION_SPAWN.shift(-1408, 1344),
        exitType = wild_locs.vetion_exit,
        exitCoords = WildGeo.VETION_EXIT.shift(-1408, 1344),
        entrance =
            WildEntrance(
                wild_locs.calvarion_entrance,
                CoordGrid(3229, 10081, 0),
                LocAngle.South,
                CoordGrid(3229, 10080, 0),
                width = 2,
                length = 1,
            ),
    );

    /** Whether a player at [coords] stands inside the lair (arena or corridor). */
    fun contains(coords: CoordGrid): Boolean = coords in area

    /** The size of the boss: the cache gives it, this is only for the places it is needed first. */
    val bossSize: Int
        get() =
            when (this) {
                CALLISTO -> 5
                SILK_CHASM,
                WEB_CHASM -> 4
                else -> 3
            }

    companion object {
        fun byKey(key: String): WildLair? = entries.firstOrNull { it.key == key }

        fun containing(coords: CoordGrid): WildLair? = entries.firstOrNull { it.contains(coords) }
    }
}

/**
 * The Escape Caves: the multi-combat tunnels between the three big lairs. A lair's exit lands the
 * player at its own mouth in the caves ([arrivalOf]); the caves' four ways out to the surface all
 * lead back to the Revenant Caves, and the cave's entrance there lands on one of the four at
 * random.
 */
object EscapeCaves {
    val area = WildRect(3328, 10240, 3391, 10303, 0)

    /** The floor beside each of the four exits to the surface (wiki: four entrances). */
    val fronts =
        listOf(
            CoordGrid(3359, 10248, 0),
            CoordGrid(3338, 10287, 0),
            CoordGrid(3380, 10287, 0),
            CoordGrid(3361, 10272, 0),
        )

    /** The exits to the surface, as (object, where it stands). */
    val exits =
        listOf(
            Pair(wild_locs.escape_exit_south, CoordGrid(3358, 10244, 0)),
            Pair(wild_locs.escape_exit_side, CoordGrid(3336, 10287, 0)),
            Pair(wild_locs.escape_exit_side, CoordGrid(3382, 10287, 0)),
            Pair(wild_locs.escape_exit_centre, CoordGrid(3361, 10273, 0)),
        )

    /** Each big lair opens into the caves at its own tunnel mouth. */
    fun arrivalOf(lair: WildLair): CoordGrid? =
        when (lair) {
            WildLair.CALLISTO -> CoordGrid(3361, 10296, 0)
            WildLair.VETIONS_REST -> CoordGrid(3342, 10255, 0)
            WildLair.SILK_CHASM -> CoordGrid(3376, 10256, 0)
            else -> null
        }

    /** The entrance object in the Revenant Caves (and the place the exits lead to). */
    val entrance =
        WildEntrance(
            wild_locs.escape_entrance,
            CoordGrid(3218, 10143, 0),
            LocAngle.West,
            CoordGrid(3219, 10142, 0),
            width = 3,
            length = 3,
        )
}
