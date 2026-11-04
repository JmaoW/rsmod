package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.min

/** The uniques the chest can hold. */
enum class ToaUnique(val label: String) {
    OsmumtensFang("Osmumten's fang"),
    Lightbearer("Lightbearer"),
    ElidinisWard("Elidinis' ward"),
    MasoriMask("Masori mask"),
    MasoriBody("Masori body"),
    MasoriChaps("Masori chaps"),
    TumekensShadow("Tumeken's shadow");

    val isMasori: Boolean
        get() = this == MasoriMask || this == MasoriBody || this == MasoriChaps
}

/**
 * One line of the common table: the item's display name, its divisor and quantity range from the
 * wiki, and whether it comes noted.
 */
class ToaCommonDrop(
    val name: String,
    val divisor: Int,
    val min: Int,
    val max: Int,
    val noted: Boolean,
)

/**
 * The reward chest's arithmetic, from the wiki's page for the chest (Tombs of Amascut): the chance
 * of a unique, which one, the common table, the pet and the fossilised dung. Everything here is
 * pure; the dice come in as a `roll(n)` that returns `0 until n`.
 */
object ToaLootMath {
    // The unique
    // -----------------------------------------------------------------------------------

    /** The raid level scaled for the unique chance: levels above 310 count for less and less. */
    fun uniqueLevel(raidLevel: Int): Double =
        when {
            raidLevel <= 310 -> raidLevel.toDouble()
            raidLevel <= 430 -> 310.0 + (raidLevel - 310) / 3.0
            else -> 350.0 + (raidLevel - 430) / 6.0
        }

    /** The chance of a unique, as a fraction: 1% per (10,500 - 20 x level) points, at most 55%. */
    fun uniqueChance(lootPoints: Int, raidLevel: Int): Double {
        val perPercent = 10_500.0 - 20.0 * uniqueLevel(raidLevel)
        return min(lootPoints / perPercent / 100.0, MAX_UNIQUE_CHANCE).coerceAtLeast(0.0)
    }

    const val MAX_UNIQUE_CHANCE = 0.55

    /** The weights the wiki's table gives each unique, out of 24 below raid level 350. */
    private val BASE =
        mapOf(
            ToaUnique.OsmumtensFang to 7.0,
            ToaUnique.Lightbearer to 7.0,
            ToaUnique.ElidinisWard to 3.0,
            ToaUnique.MasoriMask to 2.0,
            ToaUnique.MasoriBody to 2.0,
            ToaUnique.MasoriChaps to 2.0,
            ToaUnique.TumekensShadow to 1.0,
        )

    /**
     * The wiki's table of rates at raid level 350, 400, 450 and 500 and above, as the odds `1/x` of
     * each unique (fang, Lightbearer, ward, each Masori piece, shadow).
     */
    private val TIERS: List<Pair<Int, DoubleArray>> =
        listOf(
            350 to doubleArrayOf(3.67, 3.67, 7.33, 11.0, 22.0),
            400 to doubleArrayOf(4.75, 3.8, 6.33, 9.5, 19.0),
            450 to doubleArrayOf(4.5, 4.5, 6.0, 9.0, 18.0),
            500 to doubleArrayOf(5.5, 4.71, 5.5, 8.25, 16.5),
        )

    /** How likely each unique is, once a unique has dropped; the weights add up to 1. */
    fun uniqueWeights(raidLevel: Int): Map<ToaUnique, Double> {
        val tier = TIERS.lastOrNull { raidLevel >= it.first }?.second
        val raw: Map<ToaUnique, Double> =
            if (tier == null) {
                BASE.mapValues { it.value / 24.0 }
            } else {
                mapOf(
                    ToaUnique.OsmumtensFang to 1.0 / tier[0],
                    ToaUnique.Lightbearer to 1.0 / tier[1],
                    ToaUnique.ElidinisWard to 1.0 / tier[2],
                    ToaUnique.MasoriMask to 1.0 / tier[3],
                    ToaUnique.MasoriBody to 1.0 / tier[3],
                    ToaUnique.MasoriChaps to 1.0 / tier[3],
                    ToaUnique.TumekensShadow to 1.0 / tier[4],
                )
            }
        val sum = raw.values.sum()
        return raw.mapValues { it.value / sum }
    }

    /**
     * Picks the unique, weighted as the wiki's table. Wiki: a unique that is out of level for the
     * raid needs another 1 in 50; if that fails the roll gives a tertiary instead (`null`). The
     * ward, Masori and shadow are out of level below 150, and every unique below 50 (as TSPS had
     * it).
     */
    fun pickUnique(raidLevel: Int, roll: (Int) -> Int): ToaUnique? {
        val chosen = weightedPick(uniqueWeights(raidLevel), roll)
        if (isOutOfLevel(chosen, raidLevel) && roll(OUT_OF_LEVEL_ODDS) != 0) {
            return null
        }
        return chosen
    }

    /**
     * The unique a teammate gets when the raid gives everyone one (the league's team uniques): a
     * normal [pickUnique], and if that misses on the out-of-level roll, another pick among the
     * uniques the raid level allows, so the teammate always gets one. Below level 50 nothing is in
     * level, so the fang and Lightbearer are used.
     */
    fun pickTeamUnique(raidLevel: Int, roll: (Int) -> Int): ToaUnique {
        pickUnique(raidLevel, roll)?.let {
            return it
        }
        val weights = uniqueWeights(raidLevel)
        val allowed =
            weights
                .filterKeys { !isOutOfLevel(it, raidLevel) }
                .ifEmpty {
                    weights.filterKeys {
                        it == ToaUnique.OsmumtensFang || it == ToaUnique.Lightbearer
                    }
                }
        val sum = allowed.values.sum()
        return weightedPick(allowed.mapValues { it.value / sum }, roll)
    }

    private fun isOutOfLevel(unique: ToaUnique, raidLevel: Int): Boolean =
        raidLevel < 50 ||
            (raidLevel < 150 &&
                (unique == ToaUnique.ElidinisWard ||
                    unique.isMasori ||
                    unique == ToaUnique.TumekensShadow))

    /** Picks from [weights], which add up to 1. */
    private fun weightedPick(weights: Map<ToaUnique, Double>, roll: (Int) -> Int): ToaUnique {
        var point = roll(WEIGHT_PRECISION) / WEIGHT_PRECISION.toDouble()
        for ((unique, weight) in weights) {
            if (point < weight) {
                return unique
            }
            point -= weight
        }
        return weights.keys.last()
    }

    private const val WEIGHT_PRECISION = 1_000_000
    private const val OUT_OF_LEVEL_ODDS = 50

    // The common table ----------------------------------------------------------------------------

    /** Wiki: the common table, in the order it lists them. The cache of runes comes last. */
    val common: List<ToaCommonDrop> =
        listOf(
            ToaCommonDrop("Coins", 1, 1_126, 51_285, noted = false),
            ToaCommonDrop("Death rune", 20, 57, 2_564, noted = false),
            ToaCommonDrop("Soul rune", 40, 28, 1_282, noted = false),
            ToaCommonDrop("Gold ore", 90, 12, 569, noted = true),
            ToaCommonDrop("Dragon dart tip", 100, 11, 512, noted = false),
            ToaCommonDrop("Mahogany logs", 180, 6, 284, noted = true),
            ToaCommonDrop("Sapphire", 200, 5, 256, noted = true),
            ToaCommonDrop("Emerald", 250, 4, 205, noted = true),
            ToaCommonDrop("Gold bar", 250, 4, 205, noted = true),
            ToaCommonDrop("Potato cactus", 250, 4, 205, noted = true),
            ToaCommonDrop("Raw shark", 250, 4, 205, noted = true),
            ToaCommonDrop("Ruby", 300, 3, 170, noted = true),
            ToaCommonDrop("Diamond", 400, 2, 128, noted = true),
            ToaCommonDrop("Raw manta ray", 450, 2, 113, noted = true),
            ToaCommonDrop("Cactus spine", 600, 1, 85, noted = true),
            ToaCommonDrop("Dragonstone", 600, 1, 85, noted = true),
            ToaCommonDrop("Battlestaff", 1_100, 1, 46, noted = true),
            ToaCommonDrop("Coconut milk", 1_100, 1, 46, noted = true),
            ToaCommonDrop("Lily of the sands", 1_100, 1, 46, noted = true),
            ToaCommonDrop("Toadflax seed", 2_000, 1, 36, noted = false),
            ToaCommonDrop("Ranarr seed", 2_500, 1, 28, noted = false),
            ToaCommonDrop("Torstol seed", 3_200, 1, 23, noted = false),
            ToaCommonDrop("Snapdragon seed", 3_200, 1, 23, noted = false),
            ToaCommonDrop("Dragon med helm", 4_000, 1, 12, noted = true),
            ToaCommonDrop("Magic seed", 6_500, 1, 7, noted = false),
            ToaCommonDrop("Blood essence", 7_500, 1, 6, noted = false),
            ToaCommonDrop("Cache of runes", 0, 1, 1, noted = false),
        )

    /** Each line of the table is 3 in 27 (the wiki's "3 x 1/27"). */
    const val COMMON_ODDS = 27
    const val COMMON_WEIGHT = 3

    /**
     * How many of [drop] a roll gives. Wiki: points divided by the divisor, and from raid level 300
     * times (1.15 + 0.01 x (level - 300) / 5). The cache of runes is always one.
     */
    fun commonQuantity(drop: ToaCommonDrop, lootPoints: Int, raidLevel: Int): Int {
        if (drop.divisor == 0) {
            return 1
        }
        var quantity = lootPoints / drop.divisor.toDouble()
        if (raidLevel >= 300) {
            quantity *= 1.15 + 0.01 * (raidLevel - 300) / 5.0
        }
        // A hair is added so that a product like 13,499.9999 (from 1.35) is not cut short.
        return (quantity + 1e-6).toInt().coerceAtLeast(1)
    }

    /**
     * Wiki: below 1,500 reward points the chest gives fossilised dung instead of the table. These
     * are the player's own loot points (the starting 5,000 taken off), as TSPS has it.
     */
    fun givesDung(lootPoints: Int): Boolean = lootPoints < DUNG_BELOW

    const val DUNG_BELOW = 1_500

    // The rest -----------------------------------------------------------------------------------

    /** The pet's chance, as a fraction: 1% per (350,000 - 700 x level) points. */
    fun petChance(lootPoints: Int, raidLevel: Int): Double {
        val level =
            when {
                raidLevel <= 400 -> raidLevel.toDouble()
                raidLevel <= 550 -> 400.0 + (raidLevel - 400) / 3.0
                else -> 450.0
            }
        return lootPoints / (350_000.0 - 700.0 * level) / 100.0
    }

    /** The elite clue's chance: 1% for every 2,000 points, up to 25%. */
    fun eliteClueChance(lootPoints: Int): Double = min(lootPoints / 2_000 * 0.01, 0.25)

    /**
     * The chance of the Thread of Elidinis: 1/10, rising to 3/10 at 15 completions, then fixed at
     * 1/50 once a thread has been taken.
     */
    fun threadChance(completions: Int, hasThread: Boolean): Double =
        if (hasThread) 1.0 / 50.0 else 0.1 + 0.2 * min(completions, 15) / 15.0

    /** One keris jewel's chance: 1/50, rising to 3/50 at 75 completions. */
    fun jewelChance(completions: Int): Double = (1.0 + 2.0 * min(completions, 75) / 75.0) / 50.0
}
