package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.game.process.player.RunDrainMod
import org.rsmod.api.stats.xpmod.XpMod
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/** Applies the league XP multiplier from the relic tiers a player has chosen. */
class LeagueXpMod @Inject constructor(private val relics: LeagueRelicTracker) : XpMod {
    // `XpModifiers` adds every modifier to a base of 1.0, so a 5x multiplier is a +4.0 modifier.
    override fun Player.modifier(stat: StatType): Double = relics.xpMultiplier(this, stat) - 1.0
}

/** The tier 1 passive: once any tier 1 relic is chosen, running never uses up run energy. */
class LeagueRunDrainMod @Inject constructor(private val relics: LeagueRelicTracker) : RunDrainMod {
    override fun Player.preventsRunDrain(): Boolean = relics.selected(this, 0) != 0
}
