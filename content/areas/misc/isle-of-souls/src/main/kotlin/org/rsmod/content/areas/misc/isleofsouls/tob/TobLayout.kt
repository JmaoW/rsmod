package org.rsmod.content.areas.misc.isleofsouls.tob

import org.rsmod.api.repo.region.RegionStaticTemplate
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid

/**
 * The theatre's map squares (x 3136-3391, z 4224-4479, every level: the rooms and the Shadow Realm)
 * are 32 by 32 zones, so a whole copy fits one large region (40 by 40) unchanged. A raid's region
 * holds it from its south-west corner, so a real tile is the region's south-west plus its offset
 * from (3136, 4224).
 *
 * Every other piece of ToB code works on real coordinates and converts here, so the cache's and the
 * wiki's tiles can be used as they are.
 */
object TobLayout {
    const val REAL_MIN_X: Int = 3136
    const val REAL_MIN_Z: Int = 4224
    const val ZONES: Int = 32
    const val SIZE: Int = ZONES * ZoneGrid.LENGTH

    private const val REAL_ZONE_X = REAL_MIN_X / ZoneGrid.LENGTH
    private const val REAL_ZONE_Z = REAL_MIN_Z / ZoneGrid.LENGTH

    fun template(): RegionStaticTemplate =
        RegionTemplate.createLarge {
            copyAllLevels(REAL_ZONE_X, REAL_ZONE_Z) {
                zoneWidth = ZONES
                zoneLength = ZONES
                regionZoneX = 0
                regionZoneZ = 0
            }
        }

    fun isTheatre(real: CoordGrid): Boolean =
        real.x - REAL_MIN_X in 0 until SIZE && real.z - REAL_MIN_Z in 0 until SIZE

    fun toRegion(real: CoordGrid, southWest: CoordGrid): CoordGrid? {
        if (!isTheatre(real)) {
            return null
        }
        return CoordGrid(
            southWest.x + real.x - REAL_MIN_X,
            southWest.z + real.z - REAL_MIN_Z,
            real.level,
        )
    }

    fun toReal(coords: CoordGrid, southWest: CoordGrid): CoordGrid? {
        val dx = coords.x - southWest.x
        val dz = coords.z - southWest.z
        if (dx !in 0 until SIZE || dz !in 0 until SIZE) {
            return null
        }
        return CoordGrid(REAL_MIN_X + dx, REAL_MIN_Z + dz, coords.level)
    }
}

/** A box of real tiles on one level. */
data class TobBox(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int, val level: Int = 0) {
    operator fun contains(real: CoordGrid): Boolean =
        real.level == level && real.x in minX..maxX && real.z in minZ..maxZ

    val tiles: List<CoordGrid>
        get() = (minX..maxX).flatMap { x -> (minZ..maxZ).map { z -> CoordGrid(x, z, level) } }
}

/** How a room's barrier is crossed: the tiles of its line, and which side is the fight. */
class TobBarrier(
    /** The barrier's tiles (a straight line along x or z). */
    val tiles: List<CoordGrid>,
    /** The fight is on the side of larger coordinates (east or north) when true. */
    val fightIsGreater: Boolean,
) {
    val alongX: Boolean = tiles.map { it.z }.distinct().size == 1

    /** Whether [real] (a tile beside the barrier) is on the fight's side. */
    fun onFightSide(real: CoordGrid): Boolean {
        val line = if (alongX) tiles.first().z else tiles.first().x
        val at = if (alongX) real.z else real.x
        return if (fightIsGreater) at > line else at < line
    }

    /** The tile just across the barrier from [real]. */
    fun across(real: CoordGrid): CoordGrid {
        val line = if (alongX) tiles.first().z else tiles.first().x
        return if (alongX) {
            CoordGrid(real.x, if (real.z > line) line - 1 else line + 1, real.level)
        } else {
            CoordGrid(if (real.x > line) line - 1 else line + 1, real.z, real.level)
        }
    }
}

/** A loc a room places when the party arrives (the Vyre Orator, a supply chest). */
class TobPlacedLoc(val real: CoordGrid, val angle: LocAngle)

/**
 * The rooms in order. Tiles were read off the cache's map (`docs/isle_of_souls_tob_plan.md` lists
 * how) and TSPS's rsprox captures; the jail tiles are the wall alcoves the dead wait in.
 */
enum class TobRoom(
    val label: String,
    val wave: Int,
    /** Where the party arrives, and how far (east, north) arrivals are spread. */
    val arrival: CoordGrid,
    val spreadX: Int,
    val spreadZ: Int,
    /** The whole room, walkways included: a player standing here is in the room. */
    val bounds: TobBox,
    /** The fight's floor, inside the barriers. */
    val arena: TobBox?,
    val barriers: List<TobBarrier>,
    /** The way on, once the room is won (the passage, Xarpus's door, the treasure room). */
    val exits: List<CoordGrid>,
    val jails: List<CoordGrid>,
    val orator: TobPlacedLoc?,
    val oratorSeated: Boolean,
    val supplyChest: TobPlacedLoc?,
    val music: Int,
) {
    Maiden(
        label = "The Maiden of Sugadinti",
        wave = 1,
        arrival = CoordGrid(3219, 4459, 0),
        spreadX = 2,
        spreadZ = 2,
        bounds = TobBox(3155, 4418, 3229, 4463),
        arena = TobBox(3161, 4436, 3184, 4457),
        barriers = listOf(TobBarrier(line(3185, 4445, 3185, 4448), fightIsGreater = false)),
        exits = listOf(CoordGrid(3176, 4422, 0), CoordGrid(3177, 4422, 0)),
        jails =
            listOf(
                CoordGrid(3166, 4460, 0),
                CoordGrid(3167, 4460, 0),
                CoordGrid(3166, 4433, 0),
                CoordGrid(3167, 4433, 0),
            ),
        orator = TobPlacedLoc(CoordGrid(3192, 4447, 0), LocAngle.West),
        oratorSeated = false,
        supplyChest = null,
        music = 570,
    ),
    Bloat(
        label = "The Pestilent Bloat",
        wave = 2,
        arrival = CoordGrid(3321, 4447, 0),
        spreadX = 1,
        spreadZ = 2,
        bounds = TobBox(3264, 4430, 3328, 4466),
        arena = TobBox(3288, 4440, 3303, 4455),
        barriers =
            listOf(
                TobBarrier(line(3304, 4446, 3304, 4449), fightIsGreater = false),
                TobBarrier(line(3287, 4446, 3287, 4449), fightIsGreater = true),
            ),
        exits = listOf(CoordGrid(3269, 4447, 0), CoordGrid(3269, 4448, 0)),
        jails =
            listOf(
                CoordGrid(3295, 4459, 0),
                CoordGrid(3296, 4459, 0),
                CoordGrid(3295, 4436, 0),
                CoordGrid(3296, 4436, 0),
            ),
        orator = TobPlacedLoc(CoordGrid(3309, 4445, 0), LocAngle.East),
        oratorSeated = true,
        supplyChest = TobPlacedLoc(CoordGrid(3270, 4449, 0), LocAngle.South),
        music = 578,
    ),
    Nylocas(
        label = "The Nylocas",
        wave = 3,
        arrival = CoordGrid(3295, 4282, 0),
        spreadX = 2,
        spreadZ = 1,
        bounds = TobBox(3280, 4236, 3312, 4285),
        arena = TobBox(3288, 4241, 3303, 4254),
        barriers = listOf(TobBarrier(line(3295, 4255, 3296, 4255), fightIsGreater = false)),
        exits = listOf(CoordGrid(3303, 4275, 0), CoordGrid(3303, 4276, 0)),
        jails =
            listOf(
                CoordGrid(3290, 4257, 0),
                CoordGrid(3287, 4254, 0),
                CoordGrid(3287, 4243, 0),
                CoordGrid(3304, 4243, 0),
            ),
        orator = TobPlacedLoc(CoordGrid(3296, 4262, 0), LocAngle.West),
        oratorSeated = false,
        supplyChest = null,
        music = 580,
    ),
    Sotetseg(
        label = "Sotetseg",
        wave = 4,
        // The capture's arrival (3294, 4315) is walled off in the static map; the walkway beside
        // it.
        arrival = CoordGrid(3291, 4318, 0),
        spreadX = 1,
        spreadZ = 2,
        bounds = TobBox(3270, 4290, 3296, 4335),
        arena = TobBox(3273, 4306, 3286, 4332),
        barriers = listOf(TobBarrier(line(3278, 4307, 3281, 4307), fightIsGreater = true)),
        exits = listOf(CoordGrid(3279, 4293, 0), CoordGrid(3280, 4293, 0)),
        jails =
            listOf(
                CoordGrid(3270, 4313, 0),
                CoordGrid(3270, 4314, 0),
                CoordGrid(3289, 4313, 0),
                CoordGrid(3289, 4314, 0),
            ),
        orator = TobPlacedLoc(CoordGrid(3281, 4301, 0), LocAngle.North),
        oratorSeated = false,
        supplyChest = TobPlacedLoc(CoordGrid(3281, 4294, 0), LocAngle.East),
        music = 584,
    ),
    Xarpus(
        label = "Xarpus",
        wave = 5,
        arrival = CoordGrid(3170, 4375, 1),
        spreadX = 1,
        spreadZ = 1,
        bounds = TobBox(3150, 4370, 3190, 4402, 1),
        arena = TobBox(3163, 4380, 3177, 4394, 1),
        barriers =
            listOf(
                TobBarrier(line(3169, 4379, 3171, 4379, 1), fightIsGreater = true),
                TobBarrier(line(3169, 4395, 3171, 4395, 1), fightIsGreater = false),
            ),
        exits =
            listOf(CoordGrid(3169, 4400, 1), CoordGrid(3170, 4400, 1), CoordGrid(3171, 4400, 1)),
        jails =
            listOf(
                CoordGrid(3157, 4383, 1),
                CoordGrid(3157, 4391, 1),
                CoordGrid(3183, 4383, 1),
                CoordGrid(3183, 4387, 1),
                CoordGrid(3183, 4391, 1),
            ),
        orator = TobPlacedLoc(CoordGrid(3169, 4376, 1), LocAngle.East),
        oratorSeated = false,
        supplyChest = null,
        music = 567,
    ),
    Verzik(
        label = "The Final Challenge",
        wave = 6,
        arrival = CoordGrid(3166, 4303, 0),
        spreadX = 4,
        spreadZ = 1,
        bounds = TobBox(3150, 4296, 3186, 4326),
        arena = TobBox(3154, 4303, 3182, 4322),
        barriers = emptyList(),
        exits =
            listOf(CoordGrid(3167, 4324, 0), CoordGrid(3168, 4324, 0), CoordGrid(3169, 4324, 0)),
        jails =
            listOf(
                CoordGrid(3157, 4325, 0),
                CoordGrid(3159, 4325, 0),
                CoordGrid(3161, 4325, 0),
                CoordGrid(3175, 4325, 0),
                CoordGrid(3177, 4325, 0),
            ),
        orator = null,
        oratorSeated = false,
        supplyChest = null,
        music = 566,
    ),
    Vault(
        label = "Verzik Vitur's Vault",
        wave = 7,
        arrival = CoordGrid(3236, 4308, 0),
        spreadX = 2,
        spreadZ = 1,
        bounds = TobBox(3220, 4304, 3252, 4334),
        arena = null,
        barriers = emptyList(),
        exits = emptyList(),
        jails = emptyList(),
        orator = null,
        oratorSeated = false,
        supplyChest = null,
        music = 582,
    );

    val next: TobRoom?
        get() = entries.getOrNull(ordinal + 1)

    val hasFight: Boolean
        get() = this != Vault

    fun inArena(real: CoordGrid): Boolean = arena?.contains(real) == true

    companion object {
        fun at(real: CoordGrid): TobRoom? = entries.firstOrNull { real in it.bounds }
    }
}

private fun line(x0: Int, z0: Int, x1: Int, z1: Int, level: Int = 0): List<CoordGrid> =
    (x0..x1).flatMap { x -> (z0..z1).map { z -> CoordGrid(x, z, level) } }

/** Ver Sinhaza: where parties form, and where the theatre puts you when you leave. */
object VerSinhaza {
    /** A box round the town (TSPS's estimate), for the party HUD and leaving the party. */
    val bounds = TobBox(3631, 3190, 3710, 3239)

    /** Outside the theatre's doors. */
    val OUTSIDE = CoordGrid(3677, 3217, 0)

    /** Where the isle's portal puts you: by the notice board. */
    val ARRIVAL = CoordGrid(3664, 3216, 0)

    fun contains(coords: CoordGrid): Boolean = coords in bounds
}

/** The isle's way to the theatre. */
object TobIsle {
    /** A portal in the Soul Wars lobby, west of the Colosseum's arch (moved later by the user). */
    val PORTAL = CoordGrid(2221, 2821, 0)

    /** Where Ver Sinhaza's way home puts you: in front of the portal. */
    val RETURN = CoordGrid(2221, 2820, 0)
}
