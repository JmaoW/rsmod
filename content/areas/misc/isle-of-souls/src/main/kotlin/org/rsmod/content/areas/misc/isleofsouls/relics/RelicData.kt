package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings

/** A relic choice: the [tier] it belongs to and its 1-based [option] within that tier. */
data class RelicChoice(val tier: Int, val option: Int, val name: String)

/**
 * The Raging Echoes relic layout, taken from the cache (struct param `870` of the league 5 theme
 * struct and the tier structs below it). Option numbers are the keys of each tier's options enum,
 * which is the value the client expects in the `league_relic_selection_N` varbits.
 */
object RelicData {
    /** League points needed before a relic from each tier can be chosen ([EventSettings]'). */
    val thresholds: List<Int>
        get() = EventSettings.current.relicPointThresholds

    /** Relic names per tier, in option order (option `1` is the first entry). */
    val names: List<List<String>> =
        listOf(
            listOf("Power Miner", "Lumberjack", "Animal Wrangler"),
            listOf("Corner Cutter", "Friendly Forager", "Dodgy Deals"),
            listOf("Clue Compass", "Bank Heist", "Fairy's Flight"),
            listOf("Golden God", "Reloaded", "Equilibrium"),
            listOf("Treasure Arbiter", "Production Master", "Slayer Master"),
            listOf("Total Recall", "Banker's Note"),
            listOf("Pocket Kingdom", "Grimoire", "Overgrown"),
            listOf("Specialist", "Guardian", "Last Stand"),
        )

    /**
     * Cache struct id of each relic, per tier and in option order. The client reads a relic's name,
     * description and icon from this struct.
     */
    val relicStructs: List<List<Int>> =
        listOf(
            listOf(1117, 1119, 1118),
            listOf(1123, 1124, 1125),
            listOf(1120, 1121, 1725),
            listOf(1131, 1127, 1736),
            listOf(1128, 1723, 1129),
            listOf(1130, 4719),
            listOf(1126, 1132, 1133),
            listOf(1134, 4728, 1135),
        )

    /**
     * Cache struct id holding each tier's passive effect text (param `2045` of the tier struct).
     */
    val passiveStructs: IntArray = intArrayOf(1144, 1145, 1146, 1148, 1149, 1150, 1151, 1152)

    val tierCount: Int
        get() = names.size

    /** First card index of each tier. Cards are numbered tier by tier, matching the client. */
    private val firstCard: IntArray =
        IntArray(names.size + 1).also { offsets ->
            for (tier in names.indices) {
                offsets[tier + 1] = offsets[tier] + names[tier].size
            }
        }

    val cardCount: Int
        get() = firstCard.last()

    /** Maps the client's relic card index (the `clickzones` child index) to its choice. */
    fun choiceForCard(card: Int): RelicChoice? {
        if (card !in 0 until cardCount) {
            return null
        }
        val tier = firstCard.indexOfLast { it <= card }
        val option = card - firstCard[tier] + 1
        return RelicChoice(tier, option, names[tier][option - 1])
    }
}
