package org.rsmod.content.areas.misc.isleofsouls.colosseum

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.map.CoordGrid

/**
 * The Fortis Colosseum's places, in ordinary map coordinates. The lobby under the Colosseum is the
 * real map's (every player shares it); each team fights in its own copy of the arena's map square
 * (28_48), as live OSRS does.
 *
 * Tiles marked (capture) are tsps' live rsprox capture; (RSPS) are Offline_Scape's, which tsps took
 * over; the rest are the cache's or the wiki's.
 */
object ColosseumArena {
    /** The arena's map square (28_48) as a block of 8x8 zones, copied on every level. */
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = TEMPLATE_ZONE_X, copyZoneZ = TEMPLATE_ZONE_Z) {
                zoneWidth = 8
                zoneLength = 8
            }
        }

    /** The south-west tile of the copied map square. */
    val templateSouthWest = CoordGrid(TEMPLATE_ZONE_X * 8, TEMPLATE_ZONE_Z * 8, 0)

    // The isle and the lobby ---------------------------------------------------------------------

    /**
     * The entrance on the isle: a copy of the arch in Civitas illa Fortis, on the field south of
     * the plaza among the dungeon entrances. A temporary spot; the user moves it later.
     */
    val isleEntrance = CoordGrid(2227, 2820, 0)

    /** Where the lobby's stairs and the ring of dueling put a player: in front of the arch. */
    val isleArrival = CoordGrid(2227, 2819, 0)

    /** Where the arch puts you in the lobby (capture). */
    val lobbyArrival = CoordGrid(1799, 9506, 0)

    /** Where a player who dies or leaves the arena wakes up: beside Minimus (capture). */
    val lobbyRespawn = CoordGrid(1804, 9508, 0)

    /** The lobby's people (capture: Minimus; the rest round him and the scoreboard). */
    val lobbyMinimus = CoordGrid(1805, 9508, 0)
    val lobbyGloria = CoordGrid(1806, 9512, 0)
    val lobbyGuards =
        listOf(
            CoordGrid(1809, 9504, 0),
            CoordGrid(1809, 9508, 0),
            CoordGrid(1801, 9503, 0),
            CoordGrid(1801, 9510, 0),
        )

    /**
     * Where the copy of a Shrine of Ralos stands in the lobby, for blessing Dizana's quiver. The
     * real shrine is in Varlamore, which the isle does not have; this spot is a guess.
     */
    val lobbyShrine = CoordGrid(1798, 9511, 0)

    /** The lobby's tiles, for telling who is in it. */
    val lobbySouthWest = CoordGrid(1792, 9496, 0)
    val lobbyNorthEast = CoordGrid(1815, 9520, 0)

    fun inLobby(coords: CoordGrid): Boolean =
        coords.level == 0 &&
            coords.x in lobbySouthWest.x..lobbyNorthEast.x &&
            coords.z in lobbySouthWest.z..lobbyNorthEast.z

    // The arena -----------------------------------------------------------------------------------

    /** Where a team arrives, south of the middle (capture), and walks up to Minimus. */
    val start = CoordGrid(1824, 3094, 0)
    val arrivalWalk = CoordGrid(1824, 3104, 0)
    val minimusStart = CoordGrid(1824, 3106, 0)

    /** The seated Sol Heredit, above the north gate, on his blocking loc (capture). */
    val solSeat = CoordGrid(1823, 3123, 0)

    /** The arena floor, both corners included. */
    val floorSouthWest = CoordGrid(1806, 3088, 0)
    val floorNorthEast = CoordGrid(1842, 3128, 0)

    /**
     * The 12 fixed spawn points, south-west tiles (Supalosa's line-of-sight tool, via tsps). A
     * wave's other monsters take distinct ones at least [SPAWN_EXCLUSION] tiles from every player.
     */
    val spawnPoints: List<CoordGrid> =
        listOf(
            CoordGrid(1811, 3104, 0),
            CoordGrid(1817, 3106, 0),
            CoordGrid(1811, 3109, 0),
            CoordGrid(1821, 3109, 0),
            CoordGrid(1827, 3109, 0),
            CoordGrid(1825, 3114, 0),
            CoordGrid(1821, 3103, 0),
            CoordGrid(1827, 3103, 0),
            CoordGrid(1824, 3099, 0),
            CoordGrid(1832, 3107, 0),
            CoordGrid(1836, 3109, 0),
            CoordGrid(1836, 3104, 0),
        )
    const val SPAWN_EXCLUSION = 4

    /** The Fremennik trio forms round a random tile in this box (RSPS, from captures). */
    val trioSouthWest = CoordGrid(1821, 3105, 0)
    val trioNorthEast = CoordGrid(1827, 3111, 0)

    /** The gates reinforcements come through: the row in front of each, x 1823 to 1826. */
    const val NORTH_GATE_Z = 3122
    const val SOUTH_GATE_Z = 3091
    const val GATE_WEST_X = 1823
    const val GATE_WIDTH = 4

    /** A player is on the north half of the arena from this row. */
    const val NORTH_HALF_FROM = 3108

    /** The four 3x3 pillars, south-west tiles (the arena's collision in the cache). */
    val pillars: List<CoordGrid> =
        listOf(
            CoordGrid(1816, 3113, 0),
            CoordGrid(1831, 3113, 0),
            CoordGrid(1816, 3098, 0),
            CoordGrid(1831, 3098, 0),
        )
    const val PILLAR_SIZE = 3

    /** Where the rewards chest comes out (3x3, turned east) and Minimus waits beside it (RSPS). */
    val chest = CoordGrid(1829, 3105, 0)
    const val CHEST_ANGLE = 1
    val minimusByChest = CoordGrid(1830, 3103, 0)

    // Sol Heredit's wave (RSPS tiles) -------------------------------------------------------------

    /** Where he lands (south-west of his 5x5) and where the players are put. */
    val solLanding = CoordGrid(1823, 3108, 0)
    val solPlayerStart = CoordGrid(1825, 3103, 0)

    /** Inside the barricade: where the fight happens. */
    val solFloorSouthWest = CoordGrid(1818, 3100, 0)
    val solFloorNorthEast = CoordGrid(1831, 3113, 0)

    /** The barricade's corners; its gladiators stand along the edges between the pillars. */
    val barricadeSouthWest = CoordGrid(1817, 3099, 0)
    val barricadeNorthEast = CoordGrid(1832, 3114, 0)

    fun inSolFloor(coords: CoordGrid): Boolean =
        coords.x in solFloorSouthWest.x..solFloorNorthEast.x &&
            coords.z in solFloorSouthWest.z..solFloorNorthEast.z

    /**
     * The corners of the barricaded floor the Solarflare orb circles in wave 12, as 3x3 "pillars"
     * (tsps estimate).
     */
    val solFlareCorners: List<CoordGrid> =
        listOf(
            CoordGrid(1819, 3101, 0),
            CoordGrid(1827, 3101, 0),
            CoordGrid(1819, 3109, 0),
            CoordGrid(1827, 3109, 0),
        )

    // Moving between the map and a team's copy ------------------------------------------------

    /** [normal], a tile of the real arena, in [instance]'s copy of it. */
    fun toInstance(instance: TeamInstance, normal: CoordGrid): CoordGrid {
        val sw = instance.region.southWest
        return CoordGrid(
            sw.x + (normal.x - templateSouthWest.x),
            sw.z + (normal.z - templateSouthWest.z),
            normal.level,
        )
    }

    /** [coords], a tile in [instance]'s copy, as the real arena's tile. */
    fun toNormal(instance: TeamInstance, coords: CoordGrid): CoordGrid {
        val sw = instance.region.southWest
        return CoordGrid(
            templateSouthWest.x + (coords.x - sw.x),
            templateSouthWest.z + (coords.z - sw.z),
            coords.level,
        )
    }

    private const val TEMPLATE_ZONE_X = 224
    private const val TEMPLATE_ZONE_Z = 384
}

/** The arena as a kind of team instance. Registered by `ColosseumScript`. */
object ColosseumInstance {
    const val NAME = "colosseum"

    val type =
        TeamInstanceType(
            name = NAME,
            template = ColosseumArena.template,
            entry = ColosseumArena.start,
            exit = ColosseumArena.lobbyRespawn,
        )
}
