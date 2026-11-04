package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.script.onEvent
import org.rsmod.content.skills.thieving.scripts.Pickpocketing
import org.rsmod.content.skills.thieving.scripts.StallThieving
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Dodgy Deals relic (tier 2): while it is chosen,
 * - pickpocketing also robs every npc of the same kind within an 11 by 11 square, for extra loot
 *   (not extra experience);
 * - every thieving check succeeds, so a pickpocket never fails and stalls are always robbed;
 * - a pickpocket or a theft from a stall is repeated by itself until something stops it (a full
 *   inventory, too many coin pouches, a click somewhere else);
 * - what is pickpocketed comes noted;
 * - up to three times as many coin pouches can be carried;
 * - stalls never empty.
 *
 * The thieving skill asks each time through the modifier events, so it knows nothing of the relic.
 */
class LeagueDodgyDeals @Inject constructor(private val relics: LeagueRelicTracker) :
    PluginScript() {
    fun isActive(player: Player): Boolean = relics.has(player, TIER, OPTION)

    override fun ScriptContext.startup() {
        onEvent<Pickpocketing.Modifiers> {
            if (!isActive(player)) {
                return@onEvent
            }
            guaranteed = true
            area = true
            repeat = true
            noted = true
            pouchMultiplier = POUCH_MULTIPLIER
        }

        onEvent<StallThieving.Modifiers> {
            if (!isActive(player)) {
                return@onEvent
            }
            keepStall = true
            repeat = true
        }
    }

    companion object {
        const val TIER = 1
        const val OPTION = 3

        /** How many times as many coin pouches can be carried. */
        const val POUCH_MULTIPLIER = 3
    }
}
