package org.rsmod.content.areas.misc.isleofsouls.colosseum

/**
 * The Fortis Colosseum's part of `event-settings.json` (its `colosseum` object):
 * ```
 * "colosseum": {
 *   "mode": "full",
 *   "shortenedWaves": [1, 4, 7, 10, 12],
 *   "bankChestGlory": 0,
 *   "ringGlory": 0
 * }
 * ```
 * - `mode`: `full` plays the real 12 waves, `shortened` only [shortenedWaves]. Each wave keeps its
 *   real number, so its monsters, loot and Glory are the real wave's.
 * - `bankChestGlory`: the best Glory a player needs to use the lobby's bank chest. The real game's
 *   is 2,000 (Brawler); the event turns it off with 0.
 * - `ringGlory`: the best Glory the ring of dueling's Colosseum teleport needs. The real game's is
 *   12,000 (Hero); the event turns it off with 0.
 */
data class ColosseumSettings(
    val mode: ColosseumMode = ColosseumMode.Full,
    val shortenedWaves: List<Int> = DEFAULT_SHORTENED,
    val bankChestGlory: Int = 0,
    val ringGlory: Int = 0,
) {
    /** The real wave numbers a run plays, in order. */
    val waves: List<Int>
        get() =
            when (mode) {
                ColosseumMode.Full -> (1..ColosseumWaves.FINAL_WAVE).toList()
                ColosseumMode.Shortened -> shortenedWaves
            }

    companion object {
        val DEFAULT_SHORTENED = listOf(1, 4, 7, 10, 12)

        /** The real game's Glory gates, for turning them back on. */
        const val REAL_BANK_CHEST_GLORY = 2_000
        const val REAL_RING_GLORY = 12_000

        val KEYS = setOf("mode", "shortenedWaves", "bankChestGlory", "ringGlory")

        /** Checks a shortened list: rising wave numbers from 1 to 12 that end on Sol Heredit. */
        fun validShortened(waves: List<Int>): Boolean =
            waves.isNotEmpty() &&
                waves.all { it in 1..ColosseumWaves.FINAL_WAVE } &&
                waves.zipWithNext().all { (a, b) -> a < b } &&
                waves.last() == ColosseumWaves.FINAL_WAVE
    }
}
