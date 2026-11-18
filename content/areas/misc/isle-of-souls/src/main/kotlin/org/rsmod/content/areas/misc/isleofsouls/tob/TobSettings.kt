package org.rsmod.content.areas.misc.isleofsouls.tob

/**
 * What the event file (`event-settings.json`, the `tob` object) sets for the Theatre of Blood.
 * - `partyCap` (1-8): the most players a party may have. The real theatre takes five; the client's
 *   party screen, HUD and vault chests only draw five, so players six to eight are listed in chat
 *   and share the vault's chests (see `docs/isle_of_souls_tob_plan.md`).
 * - `uniqueMultiplier`: multiplies the chance of a unique (capped at 100%), on top of the relic
 *   drop boost.
 * - `teamUniques`: when one player gets a unique, everyone else in the raid gets one of their own
 *   too (the league rule the Tombs of Amascut use).
 */
data class TobSettings(
    val partyCap: Int = DEFAULT_PARTY_CAP,
    val uniqueMultiplier: Double = 1.0,
    val teamUniques: Boolean = true,
) {
    companion object {
        const val DEFAULT_PARTY_CAP = 8
        const val MAX_PARTY_CAP = 8
        val KEYS = setOf("partyCap", "uniqueMultiplier", "teamUniques")
    }
}
