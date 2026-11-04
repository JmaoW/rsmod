package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.player.stat.ProductionBatch
import org.rsmod.api.player.stat.ProductionBatchRule
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Production Master relic (tier 4 in the code, "tier 5" in the plan and on the wiki): a
 * production job is done all at once instead of one action at a time. The skills apply it through
 * [ProductionBatch]: smelting, smithing, fletching (ammo capped at 10 sets per action), cleaning
 * herbs and making potions whose secondary does not stack, cooking, pies, pizzas and wine, and
 * every crafting job (leather, gems, glass, jewellery, pottery, battlestaves, spinning).
 */
class LeagueProductionMaster @Inject constructor(private val relics: LeagueRelicTracker) :
    PluginScript(), ProductionBatchRule {
    override fun ScriptContext.startup() {
        ProductionBatch.rule = this@LeagueProductionMaster
    }

    override fun batches(player: Player): Boolean = relics.has(player, TIER, OPTION)

    companion object {
        const val TIER = 4
        const val OPTION = 2
    }
}
