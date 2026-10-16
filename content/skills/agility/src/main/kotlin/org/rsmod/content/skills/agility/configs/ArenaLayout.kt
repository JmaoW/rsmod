package org.rsmod.content.skills.agility.configs

// Generated from the cache's Brimhaven Agility Arena (map squares around 2752-2812, 9536-9598,
// level 3) and its collision. Pillar indices are `row * 5 + column`: column 0 is the west and row 0
// the south. Do not edit by hand; the layout is the game's.

/** The obstacles and traps that join two neighbouring pillars. */
enum class ArenaKind {
    Ledge,
    Log,
    Rope,
    Bars,
    Hands,
    Plank,
    Swing,
    Pillar,
    LowWall,
    Pads,
    Spikes,
    Saw,
    Darts,
    Blade,
}

/** A loc a player clicks to start an obstacle. */
class ArenaClick(val loc: String, val x: Int, val z: Int)

/** One way across an edge: the platform tile at each end, on level 3. */
class ArenaLane(val from: Tile, val to: Tile)

/** A middle piece of a plank, which is swapped between sound and broken. */
class PlankPiece(val x: Int, val z: Int, val angle: Int)

/**
 * A crossing between the pillars [a] and [b]. [trap] is the pair of tiles either side of the
 * invisible wall in the middle of a corridor trap. [blade] is the two floor tiles of a timed blade.
 * [planks] holds each lane's middle pieces, for the three lanes of a plank.
 */
class ArenaEdge(
    val a: Int,
    val b: Int,
    val kind: ArenaKind,
    val horizontal: Boolean,
    val lanes: List<ArenaLane>,
    val clicks: List<ArenaClick>,
    val trap: List<Tile>? = null,
    val blade: List<Tile>? = null,
    val planks: List<List<PlankPiece>>? = null,
)

/** A ticket dispenser on top of [pillar]. */
class ArenaDispenser(val index: Int, val pillar: Int, val x: Int, val z: Int, val loc: String)

/** The ground floor under a pillar: a climbing [rope] and the tiles a player can land on. */
class ArenaPit(val pillar: Int, val rope: Tile, val landing: List<Tile>)

object ArenaLayout {
    val columns = intArrayOf(2761, 2772, 2783, 2794, 2805)
    val rows = intArrayOf(9546, 9557, 9568, 9579, 9590)

    /** The pillar with the exit ladder. */
    const val LADDER_PILLAR = 24

    /** Where the exit ladder is, and where a player who climbs down stands. */
    val ladder = Tile(2805, 9590, 3)
    val entry = Tile(2805, 9589, 3)

    fun centre(pillar: Int): Tile = Tile(columns[pillar % 5], rows[pillar / 5], 3)

    val dispensers: List<ArenaDispenser> =
        listOf(
            ArenaDispenser(0, 0, 2761, 9546, "agility_ticketpillar"),
            ArenaDispenser(1, 1, 2772, 9546, "agility_ticketpillar"),
            ArenaDispenser(2, 2, 2783, 9546, "agility_ticketpillar"),
            ArenaDispenser(3, 3, 2794, 9546, "agility_ticketpillar"),
            ArenaDispenser(4, 4, 2805, 9546, "agility_ticketpillar"),
            ArenaDispenser(5, 5, 2761, 9557, "agility_ticketpillar"),
            ArenaDispenser(6, 6, 2772, 9557, "agility_ticketpillar"),
            ArenaDispenser(7, 7, 2783, 9557, "agilityarena_poisondarts"),
            ArenaDispenser(8, 8, 2794, 9557, "agilityarena_poisondarts"),
            ArenaDispenser(9, 9, 2805, 9557, "agility_ticketpillar"),
            ArenaDispenser(10, 10, 2761, 9568, "agility_ticketpillar"),
            ArenaDispenser(11, 11, 2772, 9568, "agilityarena_poisondarts"),
            ArenaDispenser(12, 12, 2783, 9568, "agilityarena_poisondarts"),
            ArenaDispenser(13, 13, 2794, 9568, "agilityarena_poisondarts"),
            ArenaDispenser(14, 14, 2805, 9568, "agility_ticketpillar"),
            ArenaDispenser(15, 15, 2761, 9579, "agility_ticketpillar"),
            ArenaDispenser(16, 16, 2772, 9579, "agility_ticketpillar"),
            ArenaDispenser(17, 17, 2783, 9579, "agility_ticketpillar"),
            ArenaDispenser(18, 18, 2794, 9579, "agilityarena_poisondarts"),
            ArenaDispenser(19, 19, 2805, 9579, "agility_ticketpillar"),
            ArenaDispenser(20, 20, 2761, 9590, "agility_ticketpillar"),
            ArenaDispenser(21, 21, 2772, 9590, "agility_ticketpillar"),
            ArenaDispenser(22, 22, 2783, 9590, "agility_ticketpillar"),
            ArenaDispenser(23, 23, 2794, 9590, "agility_ticketpillar"),
        )

    val pits: List<ArenaPit> =
        listOf(
            ArenaPit(
                0,
                Tile(2761, 9545, 0),
                listOf(
                    Tile(2760, 9545, 0),
                    Tile(2760, 9546, 0),
                    Tile(2760, 9547, 0),
                    Tile(2761, 9546, 0),
                    Tile(2761, 9547, 0),
                    Tile(2762, 9545, 0),
                    Tile(2762, 9546, 0),
                    Tile(2762, 9547, 0),
                ),
            ),
            ArenaPit(
                1,
                Tile(2772, 9545, 0),
                listOf(
                    Tile(2771, 9545, 0),
                    Tile(2771, 9546, 0),
                    Tile(2771, 9547, 0),
                    Tile(2772, 9546, 0),
                    Tile(2772, 9547, 0),
                    Tile(2773, 9545, 0),
                    Tile(2773, 9546, 0),
                    Tile(2773, 9547, 0),
                ),
            ),
            ArenaPit(
                2,
                Tile(2783, 9545, 0),
                listOf(
                    Tile(2782, 9545, 0),
                    Tile(2782, 9546, 0),
                    Tile(2782, 9547, 0),
                    Tile(2783, 9546, 0),
                    Tile(2783, 9547, 0),
                    Tile(2784, 9545, 0),
                    Tile(2784, 9546, 0),
                    Tile(2784, 9547, 0),
                ),
            ),
            ArenaPit(
                3,
                Tile(2794, 9545, 0),
                listOf(
                    Tile(2793, 9545, 0),
                    Tile(2794, 9546, 0),
                    Tile(2794, 9547, 0),
                    Tile(2795, 9545, 0),
                    Tile(2795, 9546, 0),
                    Tile(2795, 9547, 0),
                ),
            ),
            ArenaPit(
                4,
                Tile(2805, 9545, 0),
                listOf(
                    Tile(2804, 9545, 0),
                    Tile(2804, 9546, 0),
                    Tile(2804, 9547, 0),
                    Tile(2805, 9546, 0),
                    Tile(2805, 9547, 0),
                    Tile(2806, 9545, 0),
                    Tile(2806, 9546, 0),
                    Tile(2806, 9547, 0),
                ),
            ),
            ArenaPit(
                5,
                Tile(2760, 9557, 0),
                listOf(
                    Tile(2760, 9556, 0),
                    Tile(2760, 9558, 0),
                    Tile(2761, 9556, 0),
                    Tile(2761, 9557, 0),
                    Tile(2761, 9558, 0),
                    Tile(2762, 9556, 0),
                    Tile(2762, 9557, 0),
                    Tile(2762, 9558, 0),
                ),
            ),
            ArenaPit(
                6,
                Tile(2771, 9557, 0),
                listOf(
                    Tile(2771, 9556, 0),
                    Tile(2771, 9558, 0),
                    Tile(2772, 9556, 0),
                    Tile(2772, 9557, 0),
                    Tile(2772, 9558, 0),
                    Tile(2773, 9556, 0),
                    Tile(2773, 9557, 0),
                    Tile(2773, 9558, 0),
                ),
            ),
            ArenaPit(
                7,
                Tile(2783, 9556, 0),
                listOf(
                    Tile(2782, 9556, 0),
                    Tile(2782, 9557, 0),
                    Tile(2782, 9558, 0),
                    Tile(2783, 9557, 0),
                    Tile(2783, 9558, 0),
                    Tile(2784, 9556, 0),
                    Tile(2784, 9557, 0),
                    Tile(2784, 9558, 0),
                ),
            ),
            ArenaPit(
                8,
                Tile(2795, 9557, 0),
                listOf(
                    Tile(2793, 9556, 0),
                    Tile(2793, 9557, 0),
                    Tile(2793, 9558, 0),
                    Tile(2794, 9556, 0),
                    Tile(2794, 9557, 0),
                    Tile(2794, 9558, 0),
                    Tile(2795, 9556, 0),
                    Tile(2795, 9558, 0),
                ),
            ),
            ArenaPit(
                9,
                Tile(2806, 9557, 0),
                listOf(
                    Tile(2804, 9556, 0),
                    Tile(2804, 9557, 0),
                    Tile(2804, 9558, 0),
                    Tile(2805, 9556, 0),
                    Tile(2805, 9557, 0),
                    Tile(2805, 9558, 0),
                    Tile(2806, 9556, 0),
                    Tile(2806, 9558, 0),
                ),
            ),
            ArenaPit(
                10,
                Tile(2760, 9568, 0),
                listOf(
                    Tile(2760, 9567, 0),
                    Tile(2760, 9569, 0),
                    Tile(2761, 9567, 0),
                    Tile(2761, 9568, 0),
                    Tile(2761, 9569, 0),
                    Tile(2762, 9567, 0),
                ),
            ),
            ArenaPit(
                11,
                Tile(2771, 9568, 0),
                listOf(
                    Tile(2771, 9567, 0),
                    Tile(2771, 9569, 0),
                    Tile(2772, 9567, 0),
                    Tile(2772, 9568, 0),
                    Tile(2772, 9569, 0),
                    Tile(2773, 9567, 0),
                    Tile(2773, 9568, 0),
                    Tile(2773, 9569, 0),
                ),
            ),
            ArenaPit(
                12,
                Tile(2784, 9568, 0),
                listOf(
                    Tile(2782, 9567, 0),
                    Tile(2782, 9568, 0),
                    Tile(2782, 9569, 0),
                    Tile(2783, 9567, 0),
                    Tile(2783, 9568, 0),
                    Tile(2783, 9569, 0),
                    Tile(2784, 9567, 0),
                    Tile(2784, 9569, 0),
                ),
            ),
            ArenaPit(
                13,
                Tile(2795, 9568, 0),
                listOf(
                    Tile(2793, 9568, 0),
                    Tile(2793, 9569, 0),
                    Tile(2794, 9568, 0),
                    Tile(2794, 9569, 0),
                    Tile(2795, 9567, 0),
                    Tile(2795, 9569, 0),
                ),
            ),
            ArenaPit(
                14,
                Tile(2806, 9568, 0),
                listOf(
                    Tile(2804, 9567, 0),
                    Tile(2804, 9568, 0),
                    Tile(2804, 9569, 0),
                    Tile(2805, 9567, 0),
                    Tile(2805, 9568, 0),
                    Tile(2805, 9569, 0),
                    Tile(2806, 9567, 0),
                    Tile(2806, 9569, 0),
                ),
            ),
            ArenaPit(
                15,
                Tile(2760, 9579, 0),
                listOf(
                    Tile(2760, 9578, 0),
                    Tile(2760, 9580, 0),
                    Tile(2761, 9578, 0),
                    Tile(2761, 9579, 0),
                    Tile(2761, 9580, 0),
                    Tile(2762, 9578, 0),
                    Tile(2762, 9579, 0),
                    Tile(2762, 9580, 0),
                ),
            ),
            ArenaPit(
                16,
                Tile(2771, 9579, 0),
                listOf(
                    Tile(2771, 9578, 0),
                    Tile(2771, 9580, 0),
                    Tile(2772, 9578, 0),
                    Tile(2772, 9579, 0),
                    Tile(2772, 9580, 0),
                    Tile(2773, 9578, 0),
                    Tile(2773, 9579, 0),
                    Tile(2773, 9580, 0),
                ),
            ),
            ArenaPit(
                17,
                Tile(2783, 9580, 0),
                listOf(
                    Tile(2782, 9578, 0),
                    Tile(2782, 9579, 0),
                    Tile(2782, 9580, 0),
                    Tile(2783, 9578, 0),
                    Tile(2783, 9579, 0),
                    Tile(2784, 9578, 0),
                    Tile(2784, 9579, 0),
                    Tile(2784, 9580, 0),
                ),
            ),
            ArenaPit(
                18,
                Tile(2795, 9579, 0),
                listOf(
                    Tile(2793, 9578, 0),
                    Tile(2793, 9579, 0),
                    Tile(2793, 9580, 0),
                    Tile(2794, 9578, 0),
                    Tile(2794, 9579, 0),
                    Tile(2794, 9580, 0),
                    Tile(2795, 9578, 0),
                    Tile(2795, 9580, 0),
                ),
            ),
            ArenaPit(
                19,
                Tile(2806, 9579, 0),
                listOf(
                    Tile(2804, 9578, 0),
                    Tile(2804, 9579, 0),
                    Tile(2804, 9580, 0),
                    Tile(2805, 9578, 0),
                    Tile(2805, 9579, 0),
                    Tile(2805, 9580, 0),
                    Tile(2806, 9578, 0),
                    Tile(2806, 9580, 0),
                ),
            ),
            ArenaPit(
                20,
                Tile(2761, 9591, 0),
                listOf(
                    Tile(2760, 9589, 0),
                    Tile(2760, 9590, 0),
                    Tile(2760, 9591, 0),
                    Tile(2761, 9589, 0),
                    Tile(2761, 9590, 0),
                    Tile(2762, 9589, 0),
                    Tile(2762, 9590, 0),
                    Tile(2762, 9591, 0),
                ),
            ),
            ArenaPit(
                21,
                Tile(2772, 9591, 0),
                listOf(
                    Tile(2771, 9589, 0),
                    Tile(2771, 9590, 0),
                    Tile(2771, 9591, 0),
                    Tile(2772, 9589, 0),
                    Tile(2772, 9590, 0),
                    Tile(2773, 9589, 0),
                    Tile(2773, 9590, 0),
                    Tile(2773, 9591, 0),
                ),
            ),
            ArenaPit(
                22,
                Tile(2783, 9591, 0),
                listOf(
                    Tile(2782, 9589, 0),
                    Tile(2782, 9590, 0),
                    Tile(2782, 9591, 0),
                    Tile(2783, 9589, 0),
                    Tile(2783, 9590, 0),
                    Tile(2784, 9589, 0),
                    Tile(2784, 9590, 0),
                    Tile(2784, 9591, 0),
                ),
            ),
            ArenaPit(
                23,
                Tile(2794, 9591, 0),
                listOf(
                    Tile(2793, 9589, 0),
                    Tile(2793, 9590, 0),
                    Tile(2793, 9591, 0),
                    Tile(2794, 9589, 0),
                    Tile(2794, 9590, 0),
                    Tile(2795, 9589, 0),
                    Tile(2795, 9590, 0),
                    Tile(2795, 9591, 0),
                ),
            ),
            ArenaPit(
                24,
                Tile(2805, 9591, 0),
                listOf(
                    Tile(2804, 9589, 0),
                    Tile(2804, 9590, 0),
                    Tile(2804, 9591, 0),
                    Tile(2805, 9589, 0),
                    Tile(2805, 9590, 0),
                    Tile(2806, 9589, 0),
                    Tile(2806, 9590, 0),
                    Tile(2806, 9591, 0),
                ),
            ),
        )

    val edges: List<ArenaEdge> =
        listOf(
            ArenaEdge(
                0,
                1,
                ArenaKind.Ledge,
                true,
                listOf(ArenaLane(Tile(2763, 9546, 3), Tile(2770, 9546, 3))),
                listOf(
                    ArenaClick("agilityarena_ledgebalance", 2764, 9546),
                    ArenaClick("agilityarena_ledgebalance2", 2769, 9546),
                ),
            ),
            ArenaEdge(
                5,
                6,
                ArenaKind.Plank,
                true,
                listOf(
                    ArenaLane(Tile(2763, 9556, 3), Tile(2770, 9556, 3)),
                    ArenaLane(Tile(2763, 9557, 3), Tile(2770, 9557, 3)),
                    ArenaLane(Tile(2763, 9558, 3), Tile(2770, 9558, 3)),
                ),
                listOf(
                    ArenaClick("agilityarena_plank", 2769, 9557),
                    ArenaClick("agilityarena_plank2", 2769, 9556),
                    ArenaClick("agilityarena_plank3", 2764, 9556),
                    ArenaClick("agilityarena_plank3", 2764, 9557),
                    ArenaClick("agilityarena_plank3", 2764, 9558),
                    ArenaClick("agilityarena_plank3", 2769, 9558),
                ),
                planks =
                    listOf(
                        listOf(
                            PlankPiece(2765, 9556, 1),
                            PlankPiece(2766, 9556, 1),
                            PlankPiece(2767, 9556, 3),
                            PlankPiece(2768, 9556, 1),
                        ),
                        listOf(
                            PlankPiece(2765, 9557, 3),
                            PlankPiece(2766, 9557, 1),
                            PlankPiece(2767, 9557, 3),
                            PlankPiece(2768, 9557, 3),
                        ),
                        listOf(
                            PlankPiece(2765, 9558, 3),
                            PlankPiece(2766, 9558, 1),
                            PlankPiece(2767, 9558, 1),
                            PlankPiece(2768, 9558, 1),
                        ),
                    ),
            ),
            ArenaEdge(
                10,
                11,
                ArenaKind.Swing,
                true,
                listOf(ArenaLane(Tile(2763, 9568, 3), Tile(2770, 9568, 3))),
                listOf(
                    ArenaClick("agilityarena_ropeswing", 2766, 9569),
                    ArenaClick("agilityarena_ropeswing", 2767, 9567),
                ),
            ),
            ArenaEdge(
                15,
                16,
                ArenaKind.Log,
                true,
                listOf(ArenaLane(Tile(2763, 9579, 3), Tile(2770, 9579, 3))),
                listOf(
                    ArenaClick("agilityarena_logbalance1", 2764, 9579),
                    ArenaClick("agilityarena_logbalance3", 2769, 9579),
                ),
            ),
            ArenaEdge(
                20,
                21,
                ArenaKind.Ledge,
                true,
                listOf(ArenaLane(Tile(2763, 9590, 3), Tile(2770, 9590, 3))),
                listOf(
                    ArenaClick("agilityarena_ledgebalance", 2764, 9590),
                    ArenaClick("agilityarena_ledgebalance2", 2769, 9590),
                ),
            ),
            ArenaEdge(
                1,
                2,
                ArenaKind.Bars,
                true,
                listOf(ArenaLane(Tile(2773, 9546, 3), Tile(2782, 9546, 3))),
                listOf(
                    ArenaClick("agilityarena_monkeybars_end", 2774, 9545),
                    ArenaClick("agilityarena_monkeybars_end", 2781, 9545),
                ),
            ),
            ArenaEdge(
                6,
                7,
                ArenaKind.Saw,
                true,
                listOf(ArenaLane(Tile(2774, 9557, 3), Tile(2781, 9557, 3))),
                listOf(),
                trap = listOf(Tile(2777, 9557, 3), Tile(2778, 9557, 3)),
            ),
            ArenaEdge(
                11,
                12,
                ArenaKind.Darts,
                true,
                listOf(ArenaLane(Tile(2774, 9568, 3), Tile(2781, 9568, 3))),
                listOf(),
                trap = listOf(Tile(2777, 9568, 3), Tile(2778, 9568, 3)),
            ),
            ArenaEdge(
                16,
                17,
                ArenaKind.Saw,
                true,
                listOf(ArenaLane(Tile(2774, 9579, 3), Tile(2781, 9579, 3))),
                listOf(),
                trap = listOf(Tile(2777, 9579, 3), Tile(2778, 9579, 3)),
            ),
            ArenaEdge(
                21,
                22,
                ArenaKind.LowWall,
                true,
                listOf(ArenaLane(Tile(2776, 9590, 3), Tile(2779, 9590, 3))),
                listOf(ArenaClick("agilityarena_lowwall", 2777, 9590)),
            ),
            ArenaEdge(
                2,
                3,
                ArenaKind.Hands,
                true,
                listOf(ArenaLane(Tile(2785, 9544, 3), Tile(2792, 9544, 3))),
                listOf(
                    ArenaClick("agilityarena_handholds", 2785, 9544),
                    ArenaClick("agilityarena_handholds", 2792, 9544),
                ),
            ),
            ArenaEdge(
                7,
                8,
                ArenaKind.Darts,
                true,
                listOf(ArenaLane(Tile(2785, 9557, 3), Tile(2792, 9557, 3))),
                listOf(),
                trap = listOf(Tile(2788, 9557, 3), Tile(2789, 9557, 3)),
            ),
            ArenaEdge(
                12,
                13,
                ArenaKind.Pillar,
                true,
                listOf(ArenaLane(Tile(2785, 9568, 3), Tile(2792, 9568, 3))),
                listOf(
                    ArenaClick("agilityarena_pillar_top", 2786, 9568),
                    ArenaClick("agilityarena_pillar_top", 2791, 9568),
                ),
            ),
            ArenaEdge(
                17,
                18,
                ArenaKind.Blade,
                true,
                listOf(ArenaLane(Tile(2785, 9579, 3), Tile(2792, 9579, 3))),
                listOf(),
                blade = listOf(Tile(2788, 9579, 3), Tile(2789, 9579, 3)),
            ),
            ArenaEdge(
                22,
                23,
                ArenaKind.Hands,
                true,
                listOf(ArenaLane(Tile(2785, 9592, 3), Tile(2792, 9592, 3))),
                listOf(
                    ArenaClick("agilityarena_handholds", 2785, 9592),
                    ArenaClick("agilityarena_handholds", 2792, 9592),
                ),
            ),
            ArenaEdge(
                3,
                4,
                ArenaKind.Ledge,
                true,
                listOf(ArenaLane(Tile(2796, 9546, 3), Tile(2803, 9546, 3))),
                listOf(
                    ArenaClick("agilityarena_ledgebalance", 2797, 9546),
                    ArenaClick("agilityarena_ledgebalance2", 2802, 9546),
                ),
            ),
            ArenaEdge(
                8,
                9,
                ArenaKind.Pads,
                true,
                listOf(ArenaLane(Tile(2796, 9557, 3), Tile(2803, 9557, 3))),
                listOf(),
                trap = listOf(Tile(2799, 9557, 3), Tile(2800, 9557, 3)),
            ),
            ArenaEdge(
                13,
                14,
                ArenaKind.Spikes,
                true,
                listOf(ArenaLane(Tile(2796, 9568, 3), Tile(2803, 9568, 3))),
                listOf(),
                trap = listOf(Tile(2799, 9568, 3), Tile(2800, 9568, 3)),
            ),
            ArenaEdge(
                18,
                19,
                ArenaKind.Pads,
                true,
                listOf(ArenaLane(Tile(2796, 9579, 3), Tile(2803, 9579, 3))),
                listOf(),
                trap = listOf(Tile(2799, 9579, 3), Tile(2800, 9579, 3)),
            ),
            ArenaEdge(
                23,
                24,
                ArenaKind.Plank,
                true,
                listOf(
                    ArenaLane(Tile(2796, 9589, 3), Tile(2803, 9589, 3)),
                    ArenaLane(Tile(2796, 9590, 3), Tile(2803, 9590, 3)),
                    ArenaLane(Tile(2796, 9591, 3), Tile(2803, 9591, 3)),
                ),
                listOf(
                    ArenaClick("agilityarena_plank2", 2797, 9589),
                    ArenaClick("agilityarena_plank3", 2797, 9590),
                    ArenaClick("agilityarena_plank3", 2797, 9591),
                    ArenaClick("agilityarena_plank3", 2802, 9589),
                    ArenaClick("agilityarena_plank3", 2802, 9590),
                    ArenaClick("agilityarena_plank3", 2802, 9591),
                ),
                planks =
                    listOf(
                        listOf(
                            PlankPiece(2798, 9589, 3),
                            PlankPiece(2799, 9589, 1),
                            PlankPiece(2800, 9589, 1),
                            PlankPiece(2801, 9589, 3),
                        ),
                        listOf(
                            PlankPiece(2798, 9590, 3),
                            PlankPiece(2799, 9590, 1),
                            PlankPiece(2800, 9590, 3),
                            PlankPiece(2801, 9590, 3),
                        ),
                        listOf(
                            PlankPiece(2798, 9591, 3),
                            PlankPiece(2799, 9591, 3),
                            PlankPiece(2800, 9591, 1),
                            PlankPiece(2801, 9591, 1),
                        ),
                    ),
            ),
            ArenaEdge(
                0,
                5,
                ArenaKind.Pillar,
                false,
                listOf(ArenaLane(Tile(2761, 9548, 3), Tile(2761, 9555, 3))),
                listOf(
                    ArenaClick("agilityarena_pillar_top", 2761, 9549),
                    ArenaClick("agilityarena_pillar_top", 2761, 9554),
                ),
            ),
            ArenaEdge(
                5,
                10,
                ArenaKind.Hands,
                false,
                listOf(ArenaLane(Tile(2759, 9559, 3), Tile(2759, 9566, 3))),
                listOf(
                    ArenaClick("agilityarena_handholds", 2759, 9559),
                    ArenaClick("agilityarena_handholds", 2759, 9566),
                ),
            ),
            ArenaEdge(
                10,
                15,
                ArenaKind.Spikes,
                false,
                listOf(ArenaLane(Tile(2761, 9570, 3), Tile(2761, 9577, 3))),
                listOf(),
                trap = listOf(Tile(2761, 9573, 3), Tile(2761, 9574, 3)),
            ),
            ArenaEdge(
                15,
                20,
                ArenaKind.Blade,
                false,
                listOf(ArenaLane(Tile(2761, 9581, 3), Tile(2761, 9588, 3))),
                listOf(),
                blade = listOf(Tile(2761, 9584, 3), Tile(2761, 9585, 3)),
            ),
            ArenaEdge(
                1,
                6,
                ArenaKind.Spikes,
                false,
                listOf(ArenaLane(Tile(2772, 9548, 3), Tile(2772, 9555, 3))),
                listOf(),
                trap = listOf(Tile(2772, 9551, 3), Tile(2772, 9552, 3)),
            ),
            ArenaEdge(
                6,
                11,
                ArenaKind.Rope,
                false,
                listOf(ArenaLane(Tile(2772, 9559, 3), Tile(2772, 9566, 3))),
                listOf(
                    ArenaClick("agilityarena_ropebalance", 2772, 9559),
                    ArenaClick("agilityarena_ropebalance", 2772, 9566),
                ),
            ),
            ArenaEdge(
                11,
                16,
                ArenaKind.Bars,
                false,
                listOf(ArenaLane(Tile(2772, 9569, 3), Tile(2772, 9578, 3))),
                listOf(
                    ArenaClick("agilityarena_monkeybars_end", 2771, 9570),
                    ArenaClick("agilityarena_monkeybars_end", 2771, 9577),
                ),
            ),
            ArenaEdge(
                16,
                21,
                ArenaKind.Pads,
                false,
                listOf(ArenaLane(Tile(2772, 9581, 3), Tile(2772, 9588, 3))),
                listOf(),
                trap = listOf(Tile(2772, 9584, 3), Tile(2772, 9585, 3)),
            ),
            ArenaEdge(
                2,
                7,
                ArenaKind.Blade,
                false,
                listOf(ArenaLane(Tile(2783, 9548, 3), Tile(2783, 9555, 3))),
                listOf(),
                blade = listOf(Tile(2783, 9551, 3), Tile(2783, 9552, 3)),
            ),
            ArenaEdge(
                7,
                12,
                ArenaKind.LowWall,
                false,
                listOf(ArenaLane(Tile(2783, 9561, 3), Tile(2783, 9564, 3))),
                listOf(ArenaClick("agilityarena_lowwall", 2783, 9562)),
            ),
            ArenaEdge(
                12,
                17,
                ArenaKind.Saw,
                false,
                listOf(ArenaLane(Tile(2783, 9570, 3), Tile(2783, 9577, 3))),
                listOf(),
                trap = listOf(Tile(2783, 9573, 3), Tile(2783, 9574, 3)),
            ),
            ArenaEdge(
                17,
                22,
                ArenaKind.Rope,
                false,
                listOf(ArenaLane(Tile(2783, 9581, 3), Tile(2783, 9588, 3))),
                listOf(
                    ArenaClick("agilityarena_ropebalance", 2783, 9581),
                    ArenaClick("agilityarena_ropebalance", 2783, 9588),
                ),
            ),
            ArenaEdge(
                3,
                8,
                ArenaKind.Rope,
                false,
                listOf(ArenaLane(Tile(2794, 9548, 3), Tile(2794, 9555, 3))),
                listOf(
                    ArenaClick("agilityarena_ropebalance", 2794, 9548),
                    ArenaClick("agilityarena_ropebalance", 2794, 9555),
                ),
            ),
            ArenaEdge(
                8,
                13,
                ArenaKind.Bars,
                false,
                listOf(ArenaLane(Tile(2794, 9558, 3), Tile(2794, 9567, 3))),
                listOf(
                    ArenaClick("agilityarena_monkeybars_end", 2793, 9559),
                    ArenaClick("agilityarena_monkeybars_end", 2793, 9566),
                ),
            ),
            ArenaEdge(
                13,
                18,
                ArenaKind.Darts,
                false,
                listOf(ArenaLane(Tile(2794, 9570, 3), Tile(2794, 9577, 3))),
                listOf(),
                trap = listOf(Tile(2794, 9573, 3), Tile(2794, 9574, 3)),
            ),
            ArenaEdge(
                18,
                23,
                ArenaKind.Log,
                false,
                listOf(ArenaLane(Tile(2794, 9581, 3), Tile(2794, 9588, 3))),
                listOf(
                    ArenaClick("agilityarena_logbalance1", 2794, 9587),
                    ArenaClick("agilityarena_logbalance3", 2794, 9582),
                ),
            ),
            ArenaEdge(
                4,
                9,
                ArenaKind.Log,
                false,
                listOf(ArenaLane(Tile(2805, 9548, 3), Tile(2805, 9555, 3))),
                listOf(
                    ArenaClick("agilityarena_logbalance1", 2805, 9554),
                    ArenaClick("agilityarena_logbalance3", 2805, 9549),
                ),
            ),
            ArenaEdge(
                9,
                14,
                ArenaKind.LowWall,
                false,
                listOf(ArenaLane(Tile(2805, 9561, 3), Tile(2805, 9564, 3))),
                listOf(ArenaClick("agilityarena_lowwall", 2805, 9562)),
            ),
            ArenaEdge(
                14,
                19,
                ArenaKind.Pillar,
                false,
                listOf(ArenaLane(Tile(2805, 9570, 3), Tile(2805, 9577, 3))),
                listOf(
                    ArenaClick("agilityarena_pillar_top", 2805, 9571),
                    ArenaClick("agilityarena_pillar_top", 2805, 9576),
                ),
            ),
            ArenaEdge(
                19,
                24,
                ArenaKind.Swing,
                false,
                listOf(ArenaLane(Tile(2805, 9581, 3), Tile(2805, 9588, 3))),
                listOf(
                    ArenaClick("agilityarena_ropeswing", 2804, 9584),
                    ArenaClick("agilityarena_ropeswing", 2806, 9585),
                ),
            ),
        )
}
