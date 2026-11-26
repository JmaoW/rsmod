package org.rsmod.content.areas.misc.isleofsouls.cox

/**
 * What the event file (`event-settings.json`, the `cox` object) sets for the Chambers of Xeric.
 * - `partyCap` (1-100): the most raiders a party may have (OSRS allows 100).
 * - `uniqueMultiplier`: multiplies the unique chance (capped at 100%), on top of the relic drop
 *   boost.
 * - `teamUniques`: when one raider gets a unique, everyone else who started the raid gets one of
 *   their own (the league rule the Tombs of Amascut and the Theatre of Blood use).
 * - `teamUniquesFromPoints`: team uniques only for raids with at least this many total points.
 */
data class CoxSettings(
    val partyCap: Int = DEFAULT_PARTY_CAP,
    val uniqueMultiplier: Double = 1.0,
    val teamUniques: Boolean = true,
    val teamUniquesFromPoints: Int = 0,
) {
    companion object {
        const val DEFAULT_PARTY_CAP = 100
        const val MAX_PARTY_CAP = 100
        val KEYS = setOf("partyCap", "uniqueMultiplier", "teamUniques", "teamUniquesFromPoints")
    }
}
