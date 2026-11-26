package org.rsmod.api.drops

import jakarta.inject.Inject
import org.rsmod.api.random.GameRandom

/** Rolls [DropTable]s into the items they hand out. */
public class DropRoller @Inject constructor(private val random: GameRandom) {
    /**
     * Rolls [table] once.
     *
     * [multiplier] makes every [DropEntry.boosted] entry that many times as likely (the league's
     * relics use this). Other entries, and entries that are always dropped, are not affected. If
     * the boost makes a main table add up to more than 100%, its entries are scaled back down in
     * proportion so one of them is always hit.
     *
     * [chanceMultipliers] gives single items (by obj id) their own factor instead of [multiplier],
     * whether or not their entry is boosted, at any depth of the table.
     */
    public fun roll(
        table: DropTable,
        multiplier: Double = 1.0,
        chanceMultipliers: Map<Int, Double> = emptyMap(),
    ): List<DropResult> {
        require(multiplier > 0.0) { "Drop multiplier must be positive: $multiplier" }
        require(chanceMultipliers.values.all { it > 0.0 }) {
            "Drop chance multipliers must be positive: $chanceMultipliers"
        }
        val results = ArrayList<DropResult>()
        objMultipliers = chanceMultipliers
        try {
            collect(table, multiplier, results, depth = 0)
        } finally {
            objMultipliers = emptyMap()
        }
        return results
    }

    /** The item factors of the roll in progress (rolls never overlap: the game is one thread). */
    private var objMultipliers: Map<Int, Double> = emptyMap()

    private fun collect(
        table: DropTable,
        multiplier: Double,
        results: MutableList<DropResult>,
        depth: Int,
        boosted: Boolean = false,
    ) {
        check(depth <= MAX_DEPTH) { "Drop table `${table.name}` is nested too deeply." }

        for (stack in table.always) {
            results += stack.roll(boosted)
        }

        repeat(table.mainRolls) { rollMain(table.main, multiplier, results, depth, boosted) }

        for (entry in table.tertiary) {
            val chance = minOf(1.0, entry.effectiveChance(multiplier))
            if (random.randomDouble() < chance) {
                results.add(entry, depth, boosted)
            }
        }
    }

    private fun rollMain(
        entries: List<DropEntry>,
        multiplier: Double,
        results: MutableList<DropResult>,
        depth: Int,
        boosted: Boolean,
    ) {
        if (entries.isEmpty()) {
            return
        }
        val total = entries.sumOf { it.effectiveChance(multiplier) }
        val divisor = maxOf(1.0, total)
        val roll = random.randomDouble()
        var cumulative = 0.0
        for (entry in entries) {
            cumulative += entry.effectiveChance(multiplier) / divisor
            if (roll < cumulative) {
                results.add(entry, depth, boosted)
                return
            }
        }
    }

    private fun MutableList<DropResult>.add(entry: DropEntry, depth: Int, inherited: Boolean) {
        val boosted = inherited || entry.boosted
        when (val outcome = entry.outcome) {
            is DropOutcome.Item -> this += outcome.stack.roll(boosted)
            // The parent entry's chance was already multiplied: the nested table is rolled as is.
            is DropOutcome.Nested -> collect(outcome.table, 1.0, this, depth + 1, boosted)
        }
    }

    private fun DropEntry.effectiveChance(multiplier: Double): Double {
        val item = outcome as? DropOutcome.Item
        val own = item?.let { objMultipliers[it.stack.obj.id] }
        return when {
            own != null -> chance * own
            boosted -> chance * multiplier
            else -> chance
        }
    }

    private fun DropStack.roll(boosted: Boolean): DropResult =
        DropResult(obj, random.of(min, max), boosted)

    private companion object {
        const val MAX_DEPTH = 8
    }
}
