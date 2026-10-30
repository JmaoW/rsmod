package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

/** A rectangle of tiles on one level (every level when [level] is null). */
data class DungeonArea(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val level: Int? = null,
) {
    operator fun contains(coords: CoordGrid): Boolean =
        (level == null || coords.level == level) && coords.x in minX..maxX && coords.z in minZ..maxZ
}

/** An object inside a dungeon that leads out of it, standing on [coords]. */
data class DungeonExit(val type: LocType, val coords: CoordGrid, val climb: Boolean)

/** An object that moves a player from one place inside the dungeons to another. */
data class DungeonLink(
    val type: LocType,
    val coords: CoordGrid,
    val dest: CoordGrid,
    /** The Agility level it asks for, if any. */
    val agility: Int = 0,
    /** Whether the player climbs (a vine, a ladder) rather than walks. */
    val climb: Boolean = false,
)

/**
 * A piece of scenery placed beside a dungeon's entrance to make it look right (Karuulm's elevator
 * is a platform, posts and a control panel). Placed at [dx], [dz] from the entrance.
 */
data class DungeonProp(
    val type: LocType,
    val dx: Int,
    val dz: Int,
    val angle: LocAngle,
    val shape: LocShape = LocShape.CentrepieceStraight,
)

/**
 * One dungeon the isle gives access to. The real dungeon map is in the cache untouched: [entrance]
 * is a copy of the real entrance object placed on the isle, [arrival] is where the player lands in
 * the real map, and every way out ([exits], plus the generic dungeon ladders inside [areas]) leads
 * back to [front], the tile in front of the entrance. To move an entrance, change [entrance],
 * [angle] and [front]; the exits follow.
 *
 * The entrances stand in two rows on the open field south of the lobby plaza (through the passage
 * at x 2208-2212, z 2825-2836), clear of the plaza's own content. Each [angle] is the one whose
 * open side (the cache's approach flags) faces [front].
 */
enum class Dungeon(
    val key: String,
    val title: String,
    val entranceType: LocType,
    val entrance: CoordGrid,
    val angle: LocAngle,
    val front: CoordGrid,
    val climbIn: Boolean,
    val arrival: CoordGrid,
    val areas: List<DungeonArea>,
    val exits: List<DungeonExit>,
    val shape: LocShape = LocShape.CentrepieceStraight,
    /**
     * Whether the entrance is already part of the isle's map, so it is not placed: [entrance] and
     * [angle] only say where it stands and which way it faces, and [front] is the open tile beside
     * it.
     */
    val onMap: Boolean = false,
    /** Scenery placed round the entrance, relative to it. */
    val props: List<DungeonProp> = emptyList(),
    /** Other objects of the entrance that also lead in (Karuulm's elevator platform). */
    val alsoEnter: List<LocType> = emptyList(),
) {
    TAVERLEY(
        key = "taverley",
        title = "Taverley Dungeon",
        entranceType = DungeonLocs.taverley_entrance,
        entrance = CoordGrid(2215, 2788, 0),
        angle = LocAngle.West,
        front = CoordGrid(2216, 2788, 0),
        climbIn = true,
        arrival = CoordGrid(2884, 9798, 0),
        areas = listOf(DungeonArea(2816, 9760, 3007, 9983)),
        exits = emptyList(),
    ),
    EDGEVILLE(
        key = "edgeville",
        title = "Edgeville Dungeon",
        entranceType = DungeonLocs.edgeville_entrance,
        entrance = CoordGrid(2192, 2817, 0),
        angle = LocAngle.North,
        front = CoordGrid(2192, 2818, 0),
        climbIn = true,
        arrival = CoordGrid(3097, 9868, 0),
        areas = listOf(DungeonArea(3070, 9790, 3159, 9983)),
        exits = emptyList(),
        // A floor trapdoor is a ground decoration in the cache: other shapes have no model.
        shape = LocShape.GroundDecor,
    ),
    VARROCK(
        key = "varrock",
        title = "Varrock Sewers",
        entranceType = DungeonLocs.varrock_entrance,
        entrance = CoordGrid(2234, 2816, 0),
        angle = LocAngle.West,
        front = CoordGrid(2233, 2816, 0),
        climbIn = true,
        arrival = CoordGrid(3237, 9858, 0),
        areas = listOf(DungeonArea(3160, 9790, 3300, 9960)),
        exits =
            listOf(DungeonExit(DungeonLocs.varrock_manhole_ladder, CoordGrid(3237, 9858, 0), true)),
    ),
    REVENANT_CAVES(
        key = "revenant",
        title = "Revenant Caves",
        entranceType = DungeonLocs.revenant_entrance,
        entrance = CoordGrid(2190, 2811, 0),
        angle = LocAngle.East,
        front = CoordGrid(2192, 2813, 0),
        climbIn = false,
        arrival = CoordGrid(3217, 10058, 0),
        areas = listOf(DungeonArea(3145, 10040, 3270, 10240)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.revenant_exit_low, CoordGrid(3218, 10058, 0), true),
                DungeonExit(DungeonLocs.revenant_exit_high, CoordGrid(3244, 10215, 0), true),
            ),
    ),
    STRONGHOLD_SLAYER_CAVE(
        key = "stronghold",
        title = "Stronghold Slayer Cave",
        entranceType = DungeonLocs.stronghold_entrance,
        entrance = CoordGrid(2195, 2811, 0),
        angle = LocAngle.East,
        front = CoordGrid(2196, 2813, 0),
        climbIn = false,
        arrival = CoordGrid(2429, 9824, 0),
        areas = listOf(DungeonArea(2368, 9728, 2495, 9855)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.stronghold_exit_left, CoordGrid(2430, 9825, 0), false),
                DungeonExit(DungeonLocs.stronghold_exit_right, CoordGrid(2430, 9824, 0), false),
            ),
    ),
    BRIMHAVEN(
        key = "brimhaven",
        title = "Brimhaven Dungeon",
        entranceType = DungeonLocs.brimhaven_entrance,
        entrance = CoordGrid(2243, 2781, 0),
        angle = LocAngle.East,
        front = CoordGrid(2245, 2785, 0),
        climbIn = false,
        arrival = CoordGrid(2713, 9564, 0),
        areas = listOf(DungeonArea(2630, 9344, 2751, 9600)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.brimhaven_exit, CoordGrid(2714, 9564, 0), false),
                DungeonExit(DungeonLocs.brimhaven_backdoor_exit, CoordGrid(2734, 9477, 0), false),
            ),
    ),
    KARAMJA(
        key = "karamja",
        title = "Karamja Dungeon and Mor Ul Rek",
        entranceType = DungeonLocs.karamja_entrance,
        entrance = CoordGrid(2253, 2955, 0),
        angle = LocAngle.West,
        front = CoordGrid(2253, 2957, 0),
        climbIn = true,
        arrival = CoordGrid(2856, 9568, 0),
        areas =
            listOf(
                DungeonArea(2816, 9536, 2880, 9727),
                // Mor Ul Rek, reached through the cave at the dungeon's east end.
                DungeonArea(2432, 5056, 2559, 5183),
            ),
        exits =
            listOf(
                DungeonExit(DungeonLocs.karamja_rope, CoordGrid(2856, 9569, 0), true),
                // Crandor's rope: the Karamja Dungeon's map is joined to it.
                DungeonExit(DungeonLocs.crandor_rope, CoordGrid(2833, 9657, 0), true),
            ),
    ),
    KRAKEN_COVE(
        key = "kraken",
        title = "Kraken Cove",
        entranceType = DungeonLocs.kraken_entrance,
        entrance = CoordGrid(2200, 2809, 0),
        angle = LocAngle.East,
        front = CoordGrid(2201, 2811, 0),
        climbIn = false,
        arrival = CoordGrid(2276, 9988, 0),
        areas = listOf(DungeonArea(2240, 9984, 2303, 10047)),
        exits = listOf(DungeonExit(DungeonLocs.kraken_exit, CoordGrid(2276, 9987, 0), false)),
    ),
    SMOKE_DEVIL_DUNGEON(
        key = "smoke",
        title = "Smoke Devil Dungeon",
        entranceType = DungeonLocs.smoke_entrance,
        entrance = CoordGrid(2209, 2811, 0),
        angle = LocAngle.East,
        front = CoordGrid(2210, 2813, 0),
        climbIn = false,
        arrival = CoordGrid(2404, 9415, 0),
        areas = listOf(DungeonArea(2304, 9408, 2431, 9471)),
        exits = listOf(DungeonExit(DungeonLocs.smoke_exit, CoordGrid(2404, 9414, 0), false)),
    ),
    CATACOMBS(
        key = "catacombs",
        title = "Catacombs of Kourend",
        entranceType = DungeonLocs.catacombs_entrance,
        entrance = CoordGrid(2233, 2866, 0),
        // The Kourend statue, as it stands in the centre of Kourend: the plinth on level 0 and the
        // three pieces stacked above it, all facing South. The pieces are static map locs, see
        // `CacheEnrichmentMaps.mapLocs`.
        angle = LocAngle.South,
        front = CoordGrid(2238, 2868, 0),
        climbIn = false,
        arrival = CoordGrid(1665, 10051, 0),
        areas = listOf(DungeonArea(1600, 9984, 1727, 10111)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.catacombs_exit_01, CoordGrid(1617, 10102, 0), true),
                DungeonExit(DungeonLocs.catacombs_exit_02, CoordGrid(1650, 9986, 1), true),
                DungeonExit(DungeonLocs.catacombs_exit_03, CoordGrid(1726, 9993, 0), true),
                DungeonExit(DungeonLocs.catacombs_exit_04, CoordGrid(1719, 10102, 0), true),
                DungeonExit(DungeonLocs.catacombs_exit_giants_den, CoordGrid(1639, 10047, 0), true),
                DungeonExit(DungeonLocs.catacombs_exit_vine, CoordGrid(1666, 10051, 0), true),
            ),
    ),
    FORTHOS(
        key = "forthos",
        title = "Forthos Dungeon",
        entranceType = DungeonLocs.forthos_entrance,
        entrance = CoordGrid(2196, 2818, 0),
        angle = LocAngle.North,
        front = CoordGrid(2196, 2819, 0),
        climbIn = true,
        arrival = CoordGrid(1831, 9974, 0),
        areas = listOf(DungeonArea(1792, 9856, 1855, 9983)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.forthos_exit_ladder, CoordGrid(1830, 9974, 0), true),
                DungeonExit(DungeonLocs.forthos_exit_stairs, CoordGrid(1798, 9967, 0), true),
            ),
    ),
    HAM_HIDEOUT(
        key = "ham",
        title = "H.A.M. Hideout",
        entranceType = DungeonLocs.ham_entrance,
        entrance = CoordGrid(2165, 2802, 0),
        angle = LocAngle.West,
        front = CoordGrid(2165, 2803, 0),
        climbIn = true,
        arrival = CoordGrid(3149, 9652, 0),
        areas = listOf(DungeonArea(3136, 9600, 3199, 9663)),
        exits = listOf(DungeonExit(DungeonLocs.ham_exit, CoordGrid(3149, 9653, 0), true)),
        // A floor trapdoor is a ground decoration in the cache: other shapes have no model.
        shape = LocShape.GroundDecor,
    ),
    SLAYER_TOWER_BASEMENT(
        key = "slayer",
        title = "Slayer Tower Basement",
        entranceType = DungeonLocs.slayer_basement_entrance,
        entrance = CoordGrid(1936, 9453, 0),
        angle = LocAngle.North,
        front = CoordGrid(1936, 9452, 0),
        climbIn = true,
        // The basement is on level 3 of its map square.
        arrival = CoordGrid(3412, 9932, 3),
        areas = listOf(DungeonArea(3392, 9920, 3455, 9983, level = 3)),
        exits =
            listOf(DungeonExit(DungeonLocs.slayer_basement_exit, CoordGrid(3412, 9931, 3), true)),
    ),
    CRASH_SITE_CAVERN(
        key = "crash",
        title = "Crash Site Cavern",
        entranceType = DungeonLocs.crash_site_entrance,
        entrance = CoordGrid(2213, 2810, 0),
        angle = LocAngle.East,
        front = CoordGrid(2214, 2812, 0),
        climbIn = false,
        arrival = CoordGrid(2128, 5647, 0),
        areas = listOf(DungeonArea(2048, 5632, 2175, 5695)),
        exits = listOf(DungeonExit(DungeonLocs.crash_site_exit, CoordGrid(2129, 5647, 0), true)),
    ),
    FREMENNIK_SLAYER_DUNGEON(
        key = "fremennik",
        title = "Fremennik Slayer Dungeon",
        entranceType = DungeonLocs.fremennik_entrance,
        entrance = CoordGrid(2186, 2800, 0),
        angle = LocAngle.North,
        front = CoordGrid(2185, 2801, 0),
        climbIn = false,
        arrival = CoordGrid(2808, 10002, 0),
        areas = listOf(DungeonArea(2688, 9984, 2815, 10047)),
        exits = listOf(DungeonExit(DungeonLocs.fremennik_exit, CoordGrid(2809, 10001, 0), false)),
    ),
    KALPHITE_LAIR(
        key = "kalphite",
        title = "Kalphite Lair",
        entranceType = DungeonLocs.kalphite_entrance,
        entrance = CoordGrid(2186, 2804, 0),
        angle = LocAngle.West,
        front = CoordGrid(2186, 2803, 0),
        climbIn = true,
        // The lair is on level 2 of its map square; the queen's chamber below it is level 0.
        arrival = CoordGrid(3484, 9510, 2),
        areas = listOf(DungeonArea(3456, 9472, 3519, 9535)),
        exits = listOf(DungeonExit(DungeonLocs.kalphite_rope, CoordGrid(3483, 9510, 2), true)),
    ),
    MOS_LE_HARMLESS_CAVE(
        key = "mos",
        title = "Mos Le'Harmless Cave",
        entranceType = DungeonLocs.mos_entrance,
        entrance = CoordGrid(2189, 2800, 0),
        angle = LocAngle.South,
        front = CoordGrid(2190, 2800, 0),
        climbIn = false,
        arrival = CoordGrid(3748, 9373, 0),
        areas = listOf(DungeonArea(3712, 9344, 3839, 9471)),
        exits =
            listOf(
                DungeonExit(DungeonLocs.mos_exit_left, CoordGrid(3749, 9374, 0), false),
                DungeonExit(DungeonLocs.mos_exit_right, CoordGrid(3749, 9373, 0), false),
                // The two staircases at the cave's far end climb to the real Mos Le'Harmless.
                DungeonExit(DungeonLocs.mos_stairs, CoordGrid(3814, 9462, 0), false),
                DungeonExit(DungeonLocs.mos_stairs, CoordGrid(3829, 9462, 0), false),
            ),
    ),
    ISLE_OF_SOULS_DUNGEON(
        key = "souls",
        title = "Isle of Souls Dungeon",
        entranceType = DungeonLocs.isle_dungeon_entrance,
        entrance = CoordGrid(2308, 2918, 0),
        angle = LocAngle.South,
        front = CoordGrid(2310, 2919, 0),
        climbIn = false,
        arrival = CoordGrid(2167, 9308, 0),
        areas = listOf(DungeonArea(2112, 9280, 2175, 9343)),
        exits = listOf(DungeonExit(DungeonLocs.isle_dungeon_exit, CoordGrid(2168, 9307, 0), false)),
        onMap = true,
    ),
    LIZARDMAN_CAVES(
        key = "lizardman",
        title = "Lizardman Caves",
        entranceType = DungeonLocs.lizardman_entrance,
        entrance = CoordGrid(2223, 2807, 0),
        angle = LocAngle.North,
        front = CoordGrid(2224, 2806, 0),
        climbIn = false,
        arrival = CoordGrid(1305, 9973, 0),
        areas = listOf(DungeonArea(1280, 9940, 1340, 9990)),
        exits = listOf(DungeonExit(DungeonLocs.lizardman_exit, CoordGrid(1305, 9974, 0), false)),
    ),
    KARUULM_SLAYER_DUNGEON(
        key = "karuulm",
        title = "Karuulm Slayer Dungeon",
        // The elevator controls; the platform, posts and pillars round them are [props], and the
        // platform's centre is the way out's landing.
        entranceType = DungeonLocs.karuulm_controls,
        entrance = CoordGrid(2231, 2806, 0),
        angle = LocAngle.East,
        front = CoordGrid(2232, 2806, 0),
        climbIn = false,
        arrival = CoordGrid(1311, 10189, 0),
        areas = listOf(DungeonArea(1230, 10130, 1400, 10300)),
        exits = listOf(DungeonExit(DungeonLocs.karuulm_exit, CoordGrid(1311, 10185, 0), false)),
        props =
            listOf(
                DungeonProp(DungeonLocs.elevator_pillar, -2, 0, LocAngle.West),
                DungeonProp(DungeonLocs.elevator_pillar, -1, -1, LocAngle.West),
                DungeonProp(DungeonLocs.elevator_pillar, -1, 1, LocAngle.West),
                DungeonProp(DungeonLocs.elevator_wall, -1, 0, LocAngle.East),
                DungeonProp(DungeonLocs.elevator_post, 0, -1, LocAngle.North),
                DungeonProp(DungeonLocs.elevator_post, 0, 1, LocAngle.East),
                DungeonProp(DungeonLocs.elevator_post, 2, -1, LocAngle.West),
                DungeonProp(DungeonLocs.elevator_post, 2, 1, LocAngle.South),
                DungeonProp(
                    DungeonLocs.elevator_side_west,
                    0,
                    0,
                    LocAngle.East,
                    LocShape.GroundDecor,
                ),
                DungeonProp(
                    DungeonLocs.elevator_centre,
                    1,
                    0,
                    LocAngle.South,
                    LocShape.GroundDecor,
                ),
                DungeonProp(DungeonLocs.elevator_side, 2, 0, LocAngle.West, LocShape.GroundDecor),
                DungeonProp(DungeonLocs.elevator_side, 1, -1, LocAngle.North, LocShape.GroundDecor),
                DungeonProp(DungeonLocs.elevator_side, 1, 1, LocAngle.South, LocShape.GroundDecor),
                DungeonProp(
                    DungeonLocs.elevator_corner,
                    0,
                    -1,
                    LocAngle.East,
                    LocShape.GroundDecor,
                ),
                DungeonProp(
                    DungeonLocs.elevator_corner,
                    0,
                    1,
                    LocAngle.South,
                    LocShape.GroundDecor,
                ),
                DungeonProp(
                    DungeonLocs.elevator_corner,
                    2,
                    -1,
                    LocAngle.North,
                    LocShape.GroundDecor,
                ),
                DungeonProp(DungeonLocs.elevator_corner, 2, 1, LocAngle.West, LocShape.GroundDecor),
            ),
        alsoEnter = listOf(DungeonLocs.elevator_centre),
    ),
    GOD_WARS_DUNGEON(
        key = "godwars",
        title = "God Wars Dungeon",
        // The hole north of Trollheim, in the state with its rope already tied.
        entranceType = DungeonLocs.godwars_entrance,
        entrance = CoordGrid(2236, 2804, 0),
        angle = LocAngle.West,
        front = CoordGrid(2237, 2803, 0),
        climbIn = true,
        // The entrance ledge is on level 2 of the dungeon; the rope on it leads back up.
        arrival = CoordGrid(2882, 5310, 2),
        areas = listOf(DungeonArea(2816, 5184, 2943, 5375)),
        exits = listOf(DungeonExit(DungeonLocs.godwars_rope, CoordGrid(2881, 5311, 2), true)),
    );

    companion object {
        /** The dungeon whose map area holds [coords], if any. */
        fun containing(coords: CoordGrid): Dungeon? =
            entries.firstOrNull { dungeon -> dungeon.areas.any { coords in it } }

        fun byKey(key: String): Dungeon? = entries.firstOrNull { it.key.equals(key, true) }

        /**
         * Objects that carry a player from one place inside the dungeons to another: the cave
         * between the Karamja Dungeon and Mor Ul Rek, one object at each end, and Brimhaven's
         * stairs and vine between its two levels.
         */
        val links: List<DungeonLink> =
            listOf(
                DungeonLink(
                    DungeonLocs.mor_ul_rek_in,
                    CoordGrid(2863, 9571, 0),
                    CoordGrid(2479, 5175, 0),
                ),
                DungeonLink(
                    DungeonLocs.mor_ul_rek_out,
                    CoordGrid(2479, 5176, 0),
                    CoordGrid(2862, 9571, 0),
                ),
                DungeonLink(
                    DungeonLocs.brimhaven_stairs_1_up,
                    CoordGrid(2648, 9592, 0),
                    CoordGrid(2643, 9594, 2),
                ),
                DungeonLink(
                    DungeonLocs.brimhaven_stairs_1_down,
                    CoordGrid(2644, 9593, 2),
                    CoordGrid(2650, 9591, 0),
                ),
                DungeonLink(
                    DungeonLocs.brimhaven_stairs_2_up,
                    CoordGrid(2635, 9514, 0),
                    CoordGrid(2637, 9510, 2),
                ),
                DungeonLink(
                    DungeonLocs.brimhaven_stairs_2_down,
                    CoordGrid(2635, 9511, 2),
                    CoordGrid(2637, 9517, 0),
                ),
                // The north vine: 87 Agility to climb up to the baby green dragons.
                DungeonLink(
                    DungeonLocs.brimhaven_vine_up,
                    CoordGrid(2672, 9583, 0),
                    CoordGrid(2671, 9583, 2),
                    agility = 87,
                    climb = true,
                ),
                DungeonLink(
                    DungeonLocs.brimhaven_vine_down,
                    CoordGrid(2671, 9583, 2),
                    CoordGrid(2673, 9583, 0),
                    climb = true,
                ),
                // Karuulm: the stairs between its three levels, each end on its own object. They
                // are steps, so the player walks up them without a climbing animation.
                DungeonLink(
                    DungeonLocs.karuulm_stairs,
                    CoordGrid(1330, 10205, 0),
                    CoordGrid(1334, 10205, 1),
                ),
                DungeonLink(
                    DungeonLocs.karuulm_stairs_top,
                    CoordGrid(1330, 10205, 1),
                    CoordGrid(1329, 10205, 0),
                ),
                DungeonLink(
                    DungeonLocs.karuulm_stairs,
                    CoordGrid(1314, 10188, 1),
                    CoordGrid(1318, 10188, 2),
                ),
                DungeonLink(
                    DungeonLocs.karuulm_stairs_top,
                    CoordGrid(1314, 10188, 2),
                    CoordGrid(1313, 10188, 1),
                ),
                // Karuulm's two ends of the mysterious pipe: 88 Agility, either way.
                DungeonLink(
                    DungeonLocs.karuulm_pipe,
                    CoordGrid(1316, 10214, 0),
                    CoordGrid(1345, 10231, 0),
                    agility = 88,
                    climb = true,
                ),
                DungeonLink(
                    DungeonLocs.karuulm_pipe,
                    CoordGrid(1346, 10231, 0),
                    CoordGrid(1315, 10214, 0),
                    agility = 88,
                    climb = true,
                ),
            )
    }
}
