package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings

/** A combat mastery: the [tier] (1 to 6) unlocked on the [style] path. */
data class MasteryChoice(val style: MasteryStyle, val tier: Int) {
    /** The name the client shows, such as `Melee II`. */
    val name: String
        get() = "${style.name} ${MasteryData.numerals[tier - 1]}"
}

/**
 * The Raging Echoes combat mastery layout, taken from the cache (enums `5690` to `5695`). The
 * client reads each mastery's name, effect text and icon from its struct, and draws the grid from
 * the `league_combat_mastery_*_progress` varbits.
 */
object MasteryData {
    val numerals = listOf("I", "II", "III", "IV", "V", "VI")

    /** Mastery struct ids by tier, in the client's path order (melee, ranged, magic). */
    val masteryStructs: Map<MasteryStyle, List<Int>> =
        mapOf(
            MasteryStyle.Melee to listOf(1165, 1166, 1167, 1168, 1169, 1170),
            MasteryStyle.Ranged to listOf(1171, 1172, 1173, 1174, 1175, 1176),
            MasteryStyle.Magic to listOf(1159, 1160, 1161, 1162, 1163, 1164),
        )

    /**
     * Universal passive struct ids by tier. Tier 3 is accuracy and tier 4 is damage reduction, the
     * order the cache uses.
     */
    val passiveStructs = listOf(1153, 1154, 1156, 1155, 1157, 1158)

    /** The clickzone layer holds one child per mastery: three paths of six tiers each. */
    const val MASTERY_COUNT = 18

    /** The number of mastery tasks (cache enum `5695`). */
    const val TASK_COUNT = 10

    /** Mastery points each completed task is worth ([EventSettings]' `masteryPointsPerTask`). */
    val pointsPerTask: Int
        get() = EventSettings.current.masteryPointsPerTask

    /** Mastery points one unlock costs. */
    const val UNLOCK_COST = 1

    /** The mastery behind clickzone child [card] (`path * 6 + tier - 1`), or `null` if none. */
    fun choiceForCard(card: Int): MasteryChoice? {
        if (card !in 0 until MASTERY_COUNT) {
            return null
        }
        val tiers = CombatMasteries.MAX_TIER
        return MasteryChoice(MasteryStyle.entries[card / tiers], card % tiers + 1)
    }

    fun cardOf(choice: MasteryChoice): Int =
        choice.style.ordinal * CombatMasteries.MAX_TIER + choice.tier - 1

    fun structOf(choice: MasteryChoice): Int =
        masteryStructs.getValue(choice.style)[choice.tier - 1]

    fun passiveStructOf(choice: MasteryChoice): Int = passiveStructs[choice.tier - 1]

    /** The mastery task descriptions, from the cache, in slot order (slot `1` is the first). */
    val taskNames =
        listOf(
            "Defeat a Giant",
            "Defeat 10 monsters with a combat level of 100 or more",
            "Defeat Scurrius",
            "Defeat a monster with a Slayer requirement of 55 or more",
            "Defeat TzTok-Jad in the Fight Caves",
            "Reach a combat level of 100",
            "Defeat an Echo Boss",
            "Defeat two unique Echo Bosses",
            "Defeat three unique Echo Bosses",
            "Defeat TzKal-Zuk",
        )
}
