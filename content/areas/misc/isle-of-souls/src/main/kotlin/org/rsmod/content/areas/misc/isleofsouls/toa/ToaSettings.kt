package org.rsmod.content.areas.misc.isleofsouls.toa

/**
 * What the event file (`event-settings.json`, the `toa` object) sets for the Tombs of Amascut.
 * - `pathsRequired` (1-4): the paths a raid must clear before the Wardens open. The real raid asks
 *   for all four; fewer makes a shorter run for the event.
 * - `uniqueMultiplier`: multiplies the chance of a unique at the chest (capped at 100%), for tuning
 *   how often the gear tasks can be reached.
 * - `teamUniquesFromLevel`: from this raid level up, a unique for one player gives everyone else in
 *   the raid a unique of their own too (Raging Echoes did this from 500; 0 = every raid).
 */
data class ToaSettings(
    val pathsRequired: Int = 4,
    val uniqueMultiplier: Double = 1.0,
    val teamUniquesFromLevel: Int = 0,
) {
    companion object {
        val KEYS = setOf("pathsRequired", "uniqueMultiplier", "teamUniquesFromLevel")
    }
}
