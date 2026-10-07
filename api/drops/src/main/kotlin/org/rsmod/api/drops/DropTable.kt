package org.rsmod.api.drops

import org.rsmod.game.type.obj.ObjType

/** A stack of [obj] whose size is rolled between [min] and [max] (both inclusive). */
public data class DropStack(val obj: ObjType, val min: Int, val max: Int) {
    init {
        require(min >= 1) { "Drop quantity must be at least 1: $this" }
        require(max >= min) { "Drop max quantity cannot be lower than min: $this" }
    }
}

/** What an entry of a [DropTable] hands out when it is hit. */
public sealed interface DropOutcome {
    public data class Item(val stack: DropStack) : DropOutcome

    /** Rolls another table, such as the rare drop table. */
    public data class Nested(val table: DropTable) : DropOutcome
}

/**
 * One line of a [DropTable]. [chance] is the probability of the entry as a value between `0.0` and
 * `1.0` (for example, `3/128` is `0.0234375`).
 *
 * Only [boosted] entries are affected by a roll's drop rate multiplier: those are the rare items
 * that the league's relics make more likely.
 */
public data class DropEntry(
    val chance: Double,
    val outcome: DropOutcome,
    val boosted: Boolean = false,
) {
    init {
        require(chance > 0.0 && chance <= 1.0) { "Drop chance must be in (0, 1]: $chance" }
    }
}

/**
 * The drops of one monster, split the way the OSRS wiki does it:
 * - [always] is handed out on every kill.
 * - [main] is a single roll: at most one entry is hit, using each entry's [DropEntry.chance]. Any
 *   probability not covered by an entry means "nothing". Monsters that roll the table more than
 *   once (the Grotesque Guardians, for example) have a [mainRolls] above `1`.
 * - [tertiary] entries each get their own independent roll (clue scrolls, pets, and so on).
 */
public class DropTable(
    public val name: String,
    public val always: List<DropStack> = emptyList(),
    public val main: List<DropEntry> = emptyList(),
    public val tertiary: List<DropEntry> = emptyList(),
    public val mainRolls: Int = 1,
) {
    init {
        require(mainRolls >= 1) { "Drop table `$name` must roll its main table at least once." }
        val total = main.sumOf { it.chance }
        require(total <= 1.0 + SUM_TOLERANCE) {
            "Main drops of `$name` add up to more than 100%: $total"
        }
    }

    public val isEmpty: Boolean
        get() = always.isEmpty() && main.isEmpty() && tertiary.isEmpty()

    private companion object {
        /** Wiki tables that are meant to total 100% can be off by a fraction after rounding. */
        const val SUM_TOLERANCE = 0.0001
    }
}

/** An item that was rolled from a [DropTable]. */
public data class DropResult(val obj: ObjType, val count: Int)
