package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.content.skills.herblore.scripts.HerbloreMod
import org.rsmod.content.skills.herblore.scripts.HerbloreModifiers
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Herblore effects of the Friendly Forager relic: secondary ingredients have a 90% chance not
 * to be used up, and potions are made with four doses instead of three.
 */
class LeagueHerblore
@Inject
constructor(private val tools: LeagueRelicTools, private val modifiers: HerbloreModifiers) :
    PluginScript(), HerbloreMod {
    override fun ScriptContext.startup() {
        modifiers.register(this@LeagueHerblore)
    }

    override fun extraDoses(player: Player): Int = if (isActive(player)) EXTRA_DOSES else 0

    override fun secondarySaveChance(player: Player): Int = if (isActive(player)) SAVE_CHANCE else 0

    private fun isActive(player: Player): Boolean =
        tools.isActive(player, RelicItem.FriendlyForager)

    private companion object {
        const val EXTRA_DOSES = 1
        const val SAVE_CHANCE = 90
    }
}
