package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.XpBonus
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.areas.misc.isleofsouls.tasks.leagueStats
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Equilibrium relic (tier 3 in the code, "tier 4" in the plan and on the wiki): every xp gain
 * gives extra xp equal to [PERCENT]% of the player's total level, or [LOWEST_PERCENT]% when the
 * skill is the one with the least xp. The extra xp is added after every multiplier, so neither the
 * league nor the realm rate multiplies it, and it is added once per gain (each xp drop).
 */
class LeagueEquilibrium @Inject constructor(private val relics: LeagueRelicTracker) :
    PluginScript(), XpBonus {
    override fun ScriptContext.startup() {
        PlayerSkillXP.bonus = this@LeagueEquilibrium
    }

    fun isActive(player: Player): Boolean = relics.has(player, TIER, OPTION)

    override fun bonus(player: Player, stat: StatType, xp: Double): Double {
        if (!isActive(player)) {
            return 0.0
        }
        val stats = leagueStats
        val total = stats.sumOf { player.statBase(it) }
        val lowest = stats.minOf { player.statMap.getFineXP(it) }
        val percent = if (player.statMap.getFineXP(stat) == lowest) LOWEST_PERCENT else PERCENT
        return total * percent / 100.0
    }

    companion object {
        const val TIER = 3
        const val OPTION = 3

        const val PERCENT = 10
        const val LOWEST_PERCENT = 20
    }
}
