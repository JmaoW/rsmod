package org.rsmod.content.areas.misc.isleofsouls.dungeons

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.content.skills.agility.configs.ShortcutKind
import org.rsmod.content.skills.agility.configs.Step
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

/** The objects the dungeons' Agility crossings are clicked on. */
object DungeonCrossLocs : LocReferences() {
    val taverley_pipe = find("taverly_dungeon_pipe_sc")
    val taverley_spikes = find("taverly_dungeon_floor_spikes_sc")
    val taverley_railing = find("deepdungeonlooserailing")
    val sewer_pipe = find("varrock_dungeon_pipe_sc")
    val brimhaven_pipe = find("karam_dungeon_pipe")
    val brimhaven_entrance_pipe = find("karam_dungeon_pipe2")
    val brimhaven_stone_west_1 = find("karam_dungeon_stone1")
    val brimhaven_stone_west_2 = find("karam_dungeon_stone2")
    val brimhaven_stone_end = find("karamja_dungeon_stepping_stone_end")
    val catacombs_crack = find("zeah_cata_crack")
    val catacombs_stone = find("zeah_cata_stepstone")
    val fremennik_floor = find("slayer_dungeon_floor_spikes_sc")
    val fremennik_crack = find("slayer_dungeon_2_sc_wall_crack")
    val kalphite_crevice = find("kalphite_wall_shortcut")
    val lizardman_north = find("lizardman_cave_tunnel_north")
    val lizardman_south = find("lizardman_cave_tunnel_south")
    val lizardman_east = find("lizardman_cave_tunnel_east")
    val lizardman_west = find("lizardman_cave_tunnel_west")
    val karuulm_stepover = find("karuulm_safe_stepover")
    val karuulm_lava_gap = find("karuulm_lava_jump")
    val karuulm_tunnel = find("karuulm_wall_tunnel")
    val karuulm_hydra_rocks = find("karuulm_hydra_room_entrance_climb")
    val revenant_pillar = find("wild_cave_agility_jump")
}

/**
 * An object that starts a [DungeonCrossing] when it is used, and where it stands. [shape] and
 * [angle] are how the cache's map has it (a wall piece, a ground decoration), which a test needs to
 * stand a copy in the same way.
 */
data class DungeonClick(
    val type: LocType,
    val coords: CoordGrid,
    val shape: LocShape = LocShape.CentrepieceStraight,
    val angle: LocAngle = LocAngle.West,
)

/**
 * A shortcut inside a dungeon, crossed in place over the real map's objects: a pipe, a run of
 * stepping stones, a squeeze through a crack. Using any of [clicks] takes the player from the end
 * ([a] or [b]) they are nearer to across to the other, for [xp]. [levelAtoB] and [levelBtoA] are
 * the Agility levels it asks in each direction (some shortcuts ask for nothing one way).
 *
 * [kind] chooses the steps and animations (see the agility module's `Shortcuts`); a crossing that
 * has [squeeze] set squeezes through a gap instead. [stones] are the tiles hopped across and [via]
 * is the middle of a pipe.
 */
class DungeonCrossing(
    val key: String,
    val name: String,
    val kind: ShortcutKind,
    val a: Tile,
    val b: Tile,
    val levelAtoB: Int,
    val levelBtoA: Int,
    val xp: Double,
    val clicks: List<DungeonClick>,
    val stones: List<Tile> = emptyList(),
    val via: Tile? = null,
    val squeeze: Boolean = false,
) {
    /** The steps that take a player from [from] to [to]. */
    fun squeezeSteps(from: Tile, to: Tile): List<Step> {
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        val dir =
            when {
                dx > 0 -> 1536
                dx < 0 -> 512
                dz > 0 -> 1024
                else -> 0
            }
        return listOf(
            Step.Face(to),
            Step.Msg("You squeeze through the gap."),
            Step.Move(to, "human_pipesqueeze", 30, 90, 4, dir),
        )
    }
}

/**
 * Every Agility crossing in the dungeons. Levels come from the OSRS Wiki; the experience is the
 * Wiki's where it has one and zero where it gives none (stepping stones, cracks).
 */
object DungeonCrossings {
    private fun t(x: Int, z: Int, level: Int = 0) = Tile(x, z, level)

    private fun click(
        type: LocType,
        x: Int,
        z: Int,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
        level: Int = 0,
    ) = DungeonClick(type, CoordGrid(x, z, level), shape, angle)

    private fun stone(type: LocType, x: Int, z: Int) =
        click(type, x, z, shape = LocShape.GroundDecor)

    /** Taverley Dungeon: from the entrance room to the blue dragons (70 Agility, either way). */
    val taverleyPipe =
        DungeonCrossing(
            key = "taverley_pipe",
            name = "Obstacle pipe",
            kind = ShortcutKind.Pipe,
            a = t(2886, 9799),
            b = t(2892, 9799),
            levelAtoB = 70,
            levelBtoA = 70,
            xp = 10.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.taverley_pipe, 2887, 9799, angle = LocAngle.West),
                    click(DungeonCrossLocs.taverley_pipe, 2890, 9799, angle = LocAngle.East),
                ),
            via = t(2889, 9799),
        )

    /** Taverley Dungeon: the spiked floor to the poison spiders (80 Agility). */
    val taverleySpikes =
        DungeonCrossing(
            key = "taverley_spikes",
            name = "Strange floor",
            kind = ShortcutKind.LowWall,
            a = t(2880, 9813),
            b = t(2878, 9813),
            levelAtoB = 80,
            levelBtoA = 80,
            xp = 10.0,
            clicks = listOf(click(DungeonCrossLocs.taverley_spikes, 2879, 9813)),
        )

    /** Taverley Dungeon: the loose railing from the magic axes to the lesser demons (63). */
    val taverleyRailing =
        DungeonCrossing(
            key = "taverley_railing",
            name = "Loose railing",
            kind = ShortcutKind.LowWall,
            a = t(2936, 9810),
            b = t(2935, 9810),
            levelAtoB = 63,
            levelBtoA = 63,
            xp = 8.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.taverley_railing,
                        2935,
                        9810,
                        LocShape.WallStraight,
                        LocAngle.East,
                    )
                ),
            squeeze = true,
        )

    /** The pipe between the Edgeville Dungeon and the Varrock Sewers (51 Agility). */
    val sewerPipe =
        DungeonCrossing(
            key = "sewer_pipe",
            name = "Obstacle pipe",
            kind = ShortcutKind.Pipe,
            a = t(3149, 9906),
            b = t(3155, 9906),
            levelAtoB = 51,
            levelBtoA = 51,
            xp = 10.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.sewer_pipe, 3150, 9906, angle = LocAngle.East),
                    click(DungeonCrossLocs.sewer_pipe, 3153, 9906, angle = LocAngle.West),
                ),
            via = t(3152, 9906),
        )

    /** Brimhaven Dungeon: the pipe near the entrance (22 Agility one way, none the other). */
    val brimhavenEntrancePipe =
        DungeonCrossing(
            key = "brimhaven_entrance_pipe",
            name = "Pipe",
            kind = ShortcutKind.Pipe,
            a = t(2655, 9566),
            b = t(2655, 9573),
            levelAtoB = 22,
            levelBtoA = 0,
            xp = 8.5,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.brimhaven_entrance_pipe,
                        2655,
                        9567,
                        angle = LocAngle.South,
                    ),
                    click(
                        DungeonCrossLocs.brimhaven_entrance_pipe,
                        2655,
                        9571,
                        angle = LocAngle.North,
                    ),
                ),
            via = t(2655, 9570),
        )

    /** Brimhaven Dungeon: from the red dragons to the black demons (34 Agility; none back). */
    val brimhavenDragonPipe =
        DungeonCrossing(
            key = "brimhaven_dragon_pipe",
            name = "Pipe",
            kind = ShortcutKind.Pipe,
            a = t(2698, 9492),
            b = t(2698, 9500),
            levelAtoB = 0,
            levelBtoA = 34,
            xp = 10.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.brimhaven_pipe, 2698, 9493, angle = LocAngle.South),
                    click(DungeonCrossLocs.brimhaven_pipe, 2698, 9498, angle = LocAngle.North),
                ),
            via = t(2698, 9496),
        )

    /** Brimhaven Dungeon: the western stepping stones (12 Agility from the north, none back). */
    val brimhavenWestStones =
        DungeonCrossing(
            key = "brimhaven_west_stones",
            name = "Stepping stones",
            kind = ShortcutKind.Stones,
            a = t(2649, 9562),
            b = t(2647, 9557),
            levelAtoB = 12,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    stone(DungeonCrossLocs.brimhaven_stone_west_1, 2649, 9561),
                    stone(DungeonCrossLocs.brimhaven_stone_west_2, 2647, 9558),
                ),
            stones = listOf(t(2649, 9561), t(2648, 9560), t(2647, 9559), t(2647, 9558)),
        )

    /** Brimhaven Dungeon: the eastern stepping stones to the red dragons (56 Agility). */
    val brimhavenEastStones =
        DungeonCrossing(
            key = "brimhaven_east_stones",
            name = "Stepping stones",
            kind = ShortcutKind.Stones,
            a = t(2682, 9548),
            b = t(2690, 9547),
            levelAtoB = 56,
            levelBtoA = 56,
            xp = 0.0,
            clicks =
                listOf(
                    stone(DungeonCrossLocs.brimhaven_stone_end, 2684, 9548),
                    stone(DungeonCrossLocs.brimhaven_stone_end, 2688, 9547),
                ),
            stones = listOf(t(2684, 9548), t(2686, 9548), t(2688, 9547)),
        )

    /** Brimhaven Dungeon: the southern run of eastern stepping stones (56 Agility). */
    val brimhavenSouthStones =
        DungeonCrossing(
            key = "brimhaven_south_stones",
            name = "Stepping stones",
            kind = ShortcutKind.Stones,
            a = t(2695, 9533),
            b = t(2697, 9525),
            levelAtoB = 56,
            levelBtoA = 56,
            xp = 0.0,
            clicks =
                listOf(
                    stone(DungeonCrossLocs.brimhaven_stone_end, 2695, 9531),
                    stone(DungeonCrossLocs.brimhaven_stone_end, 2696, 9527),
                ),
            stones = listOf(t(2695, 9531), t(2695, 9529), t(2696, 9527)),
        )

    /** Catacombs of Kourend: the southern crevice between the cyclops and the ankou (17). */
    val catacombsSouthCrack =
        DungeonCrossing(
            key = "catacombs_south_crack",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1646, 10000),
            b = t(1648, 10009),
            levelAtoB = 17,
            levelBtoA = 17,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.catacombs_crack, 1646, 10001, angle = LocAngle.South),
                    click(DungeonCrossLocs.catacombs_crack, 1648, 10008, angle = LocAngle.North),
                ),
            squeeze = true,
        )

    /** Catacombs of Kourend: the northern crevice to the warped jellies (34). */
    val catacombsNorthCrack =
        DungeonCrossing(
            key = "catacombs_north_crack",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1706, 10078),
            b = t(1716, 10056),
            levelAtoB = 34,
            levelBtoA = 34,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.catacombs_crack, 1706, 10077, angle = LocAngle.North),
                    click(DungeonCrossLocs.catacombs_crack, 1716, 10057, angle = LocAngle.South),
                ),
            squeeze = true,
        )

    /** Catacombs of Kourend: the stepping stones to the steel and brutal red dragons (28). */
    val catacombsStones =
        DungeonCrossing(
            key = "catacombs_stones",
            name = "Stepping stones",
            kind = ShortcutKind.Stones,
            a = t(1610, 10062),
            b = t(1613, 10069),
            levelAtoB = 28,
            levelBtoA = 28,
            xp = 0.0,
            clicks =
                listOf(
                    stone(DungeonCrossLocs.catacombs_stone, 1610, 10063),
                    stone(DungeonCrossLocs.catacombs_stone, 1610, 10064),
                    stone(DungeonCrossLocs.catacombs_stone, 1611, 10064),
                    stone(DungeonCrossLocs.catacombs_stone, 1611, 10065),
                    stone(DungeonCrossLocs.catacombs_stone, 1611, 10066),
                    stone(DungeonCrossLocs.catacombs_stone, 1612, 10066),
                    stone(DungeonCrossLocs.catacombs_stone, 1613, 10066),
                    stone(DungeonCrossLocs.catacombs_stone, 1613, 10067),
                    stone(DungeonCrossLocs.catacombs_stone, 1613, 10068),
                ),
            stones =
                listOf(
                    t(1610, 10063),
                    t(1610, 10064),
                    t(1611, 10064),
                    t(1611, 10065),
                    t(1611, 10066),
                    t(1612, 10066),
                    t(1613, 10066),
                    t(1613, 10067),
                    t(1613, 10068),
                ),
        )

    /**
     * Fremennik Slayer Dungeon: the strange floor from the cave crawlers to the pyrefiends (43
     * Agility since May 2024). The floor is a run of spikes, jumped across tile by tile.
     */
    val fremennikFloor =
        DungeonCrossing(
            key = "fremennik_floor",
            name = "Strange floor",
            kind = ShortcutKind.Stones,
            a = t(2768, 10002),
            b = t(2775, 10003),
            levelAtoB = 43,
            levelBtoA = 43,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.fremennik_floor, 2769, 10002, angle = LocAngle.East),
                    click(DungeonCrossLocs.fremennik_floor, 2774, 10003, angle = LocAngle.East),
                ),
            stones = listOf(t(2770, 10002), t(2772, 10002), t(2773, 10003)),
        )

    /** Fremennik Slayer Dungeon: the crevice from the basilisks to the turoths (61 Agility). */
    val fremennikCrack =
        DungeonCrossing(
            key = "fremennik_crack",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(2730, 10008),
            b = t(2735, 10008),
            levelAtoB = 61,
            levelBtoA = 61,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.fremennik_crack,
                        2731,
                        10008,
                        LocShape.WallStraight,
                        LocAngle.West,
                    ),
                    click(
                        DungeonCrossLocs.fremennik_crack,
                        2734,
                        10008,
                        LocShape.WallStraight,
                        LocAngle.East,
                    ),
                ),
            squeeze = true,
        )

    /**
     * Kalphite Lair: the crevice from the entrance cave to the room above the queen's chamber (86
     * Agility; the Elite Desert Diary it also asks for in the real game is dropped).
     */
    val kalphiteCrevice =
        DungeonCrossing(
            key = "kalphite_crevice",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(3500, 9510, 2),
            b = t(3506, 9505, 2),
            levelAtoB = 86,
            levelBtoA = 86,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.kalphite_crevice,
                        3501,
                        9510,
                        LocShape.WallStraight,
                        LocAngle.West,
                        level = 2,
                    ),
                    click(
                        DungeonCrossLocs.kalphite_crevice,
                        3506,
                        9506,
                        LocShape.WallStraight,
                        LocAngle.South,
                        level = 2,
                    ),
                ),
            squeeze = true,
        )

    /** Lizardman Caves: the first crevice, from the entrance cave to the room below it. */
    val lizardmanSouthRoom =
        DungeonCrossing(
            key = "lizardman_south_room",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1305, 9957),
            b = t(1305, 9953),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.lizardman_south,
                        1305,
                        9956,
                        LocShape.WallStraight,
                        LocAngle.North,
                    ),
                    click(
                        DungeonCrossLocs.lizardman_north,
                        1305,
                        9954,
                        LocShape.WallStraight,
                        LocAngle.South,
                    ),
                ),
            squeeze = true,
        )

    /** Lizardman Caves: the crevice from the entrance cave to the eastern room below it. */
    val lizardmanEastRoom =
        DungeonCrossing(
            key = "lizardman_east_room",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1318, 9960),
            b = t(1318, 9956),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.lizardman_south,
                        1318,
                        9959,
                        LocShape.WallStraight,
                        LocAngle.North,
                    ),
                    click(
                        DungeonCrossLocs.lizardman_north,
                        1318,
                        9957,
                        LocShape.WallStraight,
                        LocAngle.South,
                    ),
                ),
            squeeze = true,
        )

    /** Lizardman Caves: the crevice to the western room. */
    val lizardmanWestRoom =
        DungeonCrossing(
            key = "lizardman_west_room",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1299, 9959),
            b = t(1295, 9959),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.lizardman_west,
                        1298,
                        9959,
                        LocShape.WallStraight,
                        LocAngle.East,
                    ),
                    click(
                        DungeonCrossLocs.lizardman_east,
                        1296,
                        9959,
                        LocShape.WallStraight,
                        LocAngle.West,
                    ),
                ),
            squeeze = true,
        )

    /** Lizardman Caves: the crevice to the far eastern room. */
    val lizardmanFarRoom =
        DungeonCrossing(
            key = "lizardman_far_room",
            name = "Crevice",
            kind = ShortcutKind.LowWall,
            a = t(1319, 9966),
            b = t(1323, 9966),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.lizardman_east,
                        1320,
                        9966,
                        LocShape.WallStraight,
                        LocAngle.West,
                    ),
                    click(
                        DungeonCrossLocs.lizardman_west,
                        1322,
                        9966,
                        LocShape.WallStraight,
                        LocAngle.East,
                    ),
                ),
            squeeze = true,
        )

    /**
     * Karuulm Slayer Dungeon: the rocks and gaps between its chambers. The Wiki asks nothing for
     * any of them, so they open to everyone.
     */
    val karuulmWestRocks =
        DungeonCrossing(
            key = "karuulm_west_rocks",
            name = "Rocks",
            kind = ShortcutKind.LowWall,
            a = t(1303, 10205),
            b = t(1301, 10205),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.karuulm_stepover, 1302, 10205, angle = LocAngle.South)
                ),
        )

    val karuulmEastRocks =
        DungeonCrossing(
            key = "karuulm_east_rocks",
            name = "Rocks",
            kind = ShortcutKind.LowWall,
            a = t(1320, 10205),
            b = t(1322, 10205),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.karuulm_stepover, 1321, 10205, angle = LocAngle.North)
                ),
        )

    val karuulmNorthRocks =
        DungeonCrossing(
            key = "karuulm_north_rocks",
            name = "Rocks",
            kind = ShortcutKind.LowWall,
            a = t(1311, 10214),
            b = t(1311, 10216),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(click(DungeonCrossLocs.karuulm_stepover, 1311, 10215, angle = LocAngle.West)),
        )

    val karuulmWestGap =
        DungeonCrossing(
            key = "karuulm_west_gap",
            name = "Lava gap",
            kind = ShortcutKind.LowWall,
            a = t(1270, 10175),
            b = t(1270, 10170),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(click(DungeonCrossLocs.karuulm_lava_gap, 1269, 10171, angle = LocAngle.West)),
        )

    val karuulmHydraGap =
        DungeonCrossing(
            key = "karuulm_hydra_gap",
            name = "Lava gap",
            kind = ShortcutKind.LowWall,
            a = t(1308, 10252),
            b = t(1308, 10257),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(click(DungeonCrossLocs.karuulm_lava_gap, 1307, 10253, angle = LocAngle.West)),
        )

    /** The tunnel through the wall on the dungeon's upper level (level 1). */
    val karuulmTunnel =
        DungeonCrossing(
            key = "karuulm_tunnel",
            name = "Tunnel",
            kind = ShortcutKind.LowWall,
            a = t(1329, 10238, 1),
            b = t(1336, 10238, 1),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.karuulm_tunnel,
                        1330,
                        10238,
                        angle = LocAngle.West,
                        level = 1,
                    )
                ),
            squeeze = true,
        )

    /** The rocks in the corridor to the Alchemical Hydra's doors. */
    val karuulmHydraRocks =
        DungeonCrossing(
            key = "karuulm_hydra_rocks",
            name = "Rocks",
            kind = ShortcutKind.LowWall,
            a = t(1351, 10250),
            b = t(1351, 10252),
            levelAtoB = 0,
            levelBtoA = 0,
            xp = 0.0,
            clicks =
                listOf(
                    click(DungeonCrossLocs.karuulm_hydra_rocks, 1351, 10251, angle = LocAngle.East)
                ),
        )

    /**
     * One of the Revenant Caves' five pillars, a single rock in a band of chasm that is jumped to
     * and then off again, three tiles each way. The cave's map holds the pillar on level 1 over a
     * bridge flag, which puts it on level 0 in game. The Wiki's levels and experience are by
     * pillar: 65 (6.5 xp), 75 (7.5) and 89 (8.9). It also has a failure that still crosses; that is
     * not built.
     */
    private fun revenantPillar(
        key: String,
        x: Int,
        z: Int,
        alongZ: Boolean,
        level: Int,
        xp: Double,
    ): DungeonCrossing {
        val dx = if (alongZ) 0 else 3
        val dz = if (alongZ) 3 else 0
        return DungeonCrossing(
            key = key,
            name = "Pillar",
            kind = ShortcutKind.Pillar,
            a = t(x - dx, z - dz),
            b = t(x + dx, z + dz),
            levelAtoB = level,
            levelBtoA = level,
            xp = xp,
            clicks =
                listOf(
                    click(
                        DungeonCrossLocs.revenant_pillar,
                        x,
                        z,
                        angle = if (alongZ) LocAngle.West else LocAngle.North,
                    )
                ),
            stones = listOf(t(x, z)),
        )
    }

    /** Revenant Caves, between the revenant goblin and the southern revenant ork (65). */
    val revenantSouthPillar = revenantPillar("revenant_south_pillar", 3220, 10086, true, 65, 6.5)

    /** Revenant Caves, west of the northern revenant ork (75). */
    val revenantWestPillar = revenantPillar("revenant_west_pillar", 3200, 10136, false, 75, 7.5)

    /** Revenant Caves, the two in the north-western area (75). */
    val revenantNorthPillar = revenantPillar("revenant_north_pillar", 3180, 10209, true, 75, 7.5)

    val revenantNorthWestPillar =
        revenantPillar("revenant_northwest_pillar", 3202, 10196, false, 75, 7.5)

    /** Revenant Caves, between the northern revenant ork and the eastern pyrefiend (89). */
    val revenantEastPillar = revenantPillar("revenant_east_pillar", 3241, 10145, false, 89, 8.9)

    val all: List<DungeonCrossing> =
        listOf(
            taverleyPipe,
            taverleySpikes,
            taverleyRailing,
            sewerPipe,
            brimhavenEntrancePipe,
            brimhavenDragonPipe,
            brimhavenWestStones,
            brimhavenEastStones,
            brimhavenSouthStones,
            catacombsSouthCrack,
            catacombsNorthCrack,
            catacombsStones,
            fremennikFloor,
            fremennikCrack,
            kalphiteCrevice,
            lizardmanSouthRoom,
            lizardmanEastRoom,
            lizardmanWestRoom,
            lizardmanFarRoom,
            karuulmWestRocks,
            karuulmEastRocks,
            karuulmNorthRocks,
            karuulmWestGap,
            karuulmHydraGap,
            karuulmTunnel,
            karuulmHydraRocks,
            revenantSouthPillar,
            revenantWestPillar,
            revenantNorthPillar,
            revenantNorthWestPillar,
            revenantEastPillar,
        )
}
