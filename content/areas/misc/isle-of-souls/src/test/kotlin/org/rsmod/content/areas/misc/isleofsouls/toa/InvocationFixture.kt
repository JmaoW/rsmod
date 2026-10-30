package org.rsmod.content.areas.misc.isleofsouls.toa

/**
 * The invocation board as the cache has it (enum 4664 and the structs it lists, read with the
 * census tools), so the tests can build the catalog without a cache.
 */
object InvocationFixture {
    private class Row(
        val struct: Int,
        val bit: Int,
        val name: String,
        val category: Int,
        val level: Int,
        val magnitude: Int?,
        val requires: Int?,
    )

    private fun row(
        struct: Int,
        bit: Int,
        name: String,
        category: Int,
        level: Int,
        magnitude: Int?,
        requires: Int?,
    ) = Row(struct, bit, name, category, level, magnitude, requires)

    private val rows =
        listOf(
            row(417, 1, "Try Again", 3, 5, 10, null),
            row(418, 2, "Persistence", 3, 10, 5, null),
            row(419, 3, "Softcore Run", 3, 15, 3, null),
            row(420, 4, "Hardcore Run", 3, 25, 1, null),
            row(421, 5, "Walk for It", 4, 10, 4000, null),
            row(422, 6, "Jog for It", 4, 15, 3500, null),
            row(423, 7, "Run for It", 4, 20, 3000, null),
            row(424, 8, "Sprint for It", 4, 25, 2500, null),
            row(425, 9, "Need Some Help?", 5, 15, 66, null),
            row(426, 10, "Need Less Help?", 5, 25, 33, null),
            row(427, 11, "No Help Needed", 5, 40, 10, null),
            row(428, 12, "Walk the Path", 9, 50, null, null),
            row(429, 13, "Pathseeker", 6, 15, 1, null),
            row(430, 14, "Pathfinder", 6, 40, 2, null),
            row(431, 15, "Pathmaster", 6, 50, 3, null),
            row(432, 16, "Quiet Prayers", 7, 20, 10, null),
            row(433, 17, "Deadly Prayers", 7, 20, null, null),
            row(434, 18, "On a Diet", 8, 15, null, null),
            row(435, 19, "Dehydration", 8, 30, null, null),
            row(436, 20, "Overly Draining", 8, 15, null, null),
            row(437, 21, "Lively Larvae", 11, 5, null, null),
            row(438, 22, "More Overlords", 11, 15, null, null),
            row(439, 23, "Blowing Mud", 11, 10, null, null),
            row(440, 24, "Medic!", 11, 15, null, null),
            row(441, 25, "Aerial Assault", 11, 10, null, null),
            row(442, 26, "Not Just a Head", 12, 15, null, null),
            row(444, 27, "Arterial Spray", 12, 10, null, 442),
            row(445, 28, "Blood Thinners", 12, 5, null, 442),
            row(447, 29, "Upset Stomach", 12, 15, null, null),
            row(448, 30, "Double Trouble", 10, 20, null, null),
            row(449, 31, "Keep Back", 10, 10, null, null),
            row(542, 32, "Stay Vigilant", 10, 15, null, null),
            row(543, 33, "Feeling Special?", 10, 20, null, null),
            row(603, 34, "Mind the Gap!", 13, 10, null, null),
            row(752, 35, "Gotta Have Faith", 13, 10, null, null),
            row(949, 36, "Jungle Japes", 13, 5, null, null),
            row(1275, 37, "Shaking Things Up", 13, 10, null, null),
            row(1276, 38, "Boulderdash", 13, 10, null, null),
            row(1278, 39, "Ancient Haste", 14, 10, null, null),
            row(1688, 40, "Acceleration", 14, 10, null, null),
            row(2874, 41, "Penetration", 14, 10, null, null),
            row(2933, 42, "Overclocked", 14, 10, null, null),
            row(2934, 43, "Overclocked 2", 14, 10, null, 2933),
            row(2971, 44, "Insanity", 14, 50, null, 2934),
            row(5892, 45, "Blazing Tombs I", 15, 200, null, null),
            row(5893, 46, "Blazing Tombs II", 15, 200, null, null),
        )

    val catalog: InvocationCatalog by lazy {
        val board = rows.mapIndexed { slot, row -> slot to row.struct }.toMap()
        val params =
            rows.associate { row ->
                row.struct to
                    buildMap<Int, Any> {
                        put(InvocationCatalog.PARAM_BIT, row.bit)
                        put(InvocationCatalog.PARAM_NAME, row.name)
                        put(InvocationCatalog.PARAM_CATEGORY, row.category)
                        put(InvocationCatalog.PARAM_LEVEL, row.level)
                        row.magnitude?.let { put(InvocationCatalog.PARAM_MAGNITUDE, it) }
                        row.requires?.let { put(InvocationCatalog.PARAM_REQUIRES, it) }
                    }
            }
        InvocationCatalog.build(
            structs = { params[it] },
            enums = {
                if (it == InvocationCatalog.ENUM) board.mapKeys { e -> e.key as Any } else null
            },
        )
    }
}
