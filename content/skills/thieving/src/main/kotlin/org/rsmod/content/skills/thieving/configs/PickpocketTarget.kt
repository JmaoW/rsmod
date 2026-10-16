package org.rsmod.content.skills.thieving.configs

import kotlin.math.floor
import kotlin.math.max
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.ObjType

/**
 * One thing a thief can come away with. [weight] is the chance of this item out of everything the
 * table can give (the tables are rolled by relative weight, so they need not add up to one). Two
 * Master Farmer seeds instead get a chance that rises with the thief's Farming level: [weightAt]
 * returns `farmingBase + farmingPerLevel * level` for those.
 */
class Drop(
    val obj: ObjType,
    val amount: IntRange,
    val weight: Double = 1.0,
    private val farmingBase: Double = 0.0,
    private val farmingPerLevel: Double = 0.0,
) {
    fun weightAt(farmingLevel: Int): Double =
        if (farmingPerLevel == 0.0) {
            weight
        } else {
            max(0.0, farmingBase + farmingPerLevel * farmingLevel)
        }
}

/**
 * The coin pouch a target gives in place of coins. Opening one gives a random number of coins from
 * [coins].
 */
class CoinPouch(val obj: ObjType, val coins: IntRange)

/**
 * A kind of npc that can be pickpocketed. The numbers are from the OSRS Wiki: [level] and [xp] to
 * pick the pocket, [stun] the damage taken when caught, and [low] and [high] the chance (out of
 * 256) to succeed at level 1 and level 99, with the chance at every level in between worked out
 *      from the two (see [chance]).
 *
 * Loot is the [always] items, then each of the [first] rolls in turn (a hit gives that item and
 * skips the table), then one roll on the weighted [table]. A coin drop becomes the target's [pouch]
 * when it has one.
 */
class PickpocketTarget(
    val key: String,
    /** How the target is named in messages, as in "You attempt to pick the man's pocket". */
    val name: String,
    val npcs: List<String>,
    val npcIds: List<Int> = emptyList(),
    val option: String = "Pickpocket",
    val properNames: Boolean = false,
    val needsFireCape: Boolean = false,
    val catchLine: String = "What do you think you're doing?",
    val level: Int,
    val xp: Double,
    val stun: IntRange,
    val low: Int,
    val high: Int,
    val pouch: CoinPouch? = null,
    val always: List<Drop> = emptyList(),
    val first: List<Drop> = emptyList(),
    val table: List<Drop> = emptyList(),
) {
    /** Whether the cache npc [type] is this target and has the option to rob it. */
    fun matches(type: UnpackedNpcType): Boolean {
        if (npcs.none { it.equals(type.name, ignoreCase = true) }) {
            return false
        }
        if (npcIds.isNotEmpty() && type.id !in npcIds) {
            return false
        }
        if (opIndex(type) < 0) {
            return false
        }
        // Villagers and Pollnivneach's men are lured and knocked out instead; that is not here.
        return key == MENAPHITE_THUG || type.op.none { it.equals(LURE, ignoreCase = true) }
    }

    /** The zero based option slot that robs this npc, or `-1`. */
    fun opIndex(type: UnpackedNpcType): Int = type.op.indexOfFirst { option.equals(it, true) }

    /** "the man's" or, for a named npc, "Agnar's", as in "pick ___ pocket". */
    fun possessive(npcName: String): String = if (properNames) "$npcName's" else "the $name's"

    /**
     * The chance (0 to 1) that a thief of [level] succeeds, from the Wiki's interpolation of the
     * [low] and [high] chances. Each of the [multipliers] (gloves of silence, the Thieving cape)
     * scales the two ends first and is rounded down, one after the other.
     */
    fun chance(level: Int, multipliers: List<Double> = emptyList()): Double {
        var lo = low
        var hi = high
        for (multiplier in multipliers) {
            lo = floor(lo * multiplier).toInt()
            hi = floor(hi * multiplier).toInt()
        }
        val capped = level.coerceIn(1, 99)
        val value = floor(lo * (99 - capped) / 98.0 + hi * (capped - 1) / 98.0 + 0.5).toInt()
        return ((value + 1) / 256.0).coerceIn(0.0, 1.0)
    }

    private companion object {
        const val LURE = "Lure"
        const val MENAPHITE_THUG = "menaphite_thug"
    }
}
