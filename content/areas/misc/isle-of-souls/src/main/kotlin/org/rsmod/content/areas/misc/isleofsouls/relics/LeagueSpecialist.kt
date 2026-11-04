package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.script.onEvent
import org.rsmod.api.specials.SpecialAccuracy
import org.rsmod.api.specials.SpecialAccuracyRule
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.specials.energy.SpecialEnergyCosts
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Specialist relic (tier 7 in the code, "tier 8" in the plan and on the wiki):
 * - Every special attack costs [COST]% energy, whatever it normally costs.
 * - Special attacks are twice as accurate (+100%).
 * - Each failed special attack accuracy roll gives back [MISS_REFUND]% energy. For a ranged special
 *   attack that rolls its damage in one go, a hit of 0 counts as a miss.
 * - Killing an npc restores [KILL_RESTORE]% energy, however it was killed.
 */
class LeagueSpecialist
@Inject
constructor(private val relics: LeagueRelicTracker, private val energy: SpecialAttackEnergy) :
    PluginScript(), SpecialAccuracyRule {
    override fun ScriptContext.startup() {
        active = this@LeagueSpecialist
        SpecialAccuracy.rule = this@LeagueSpecialist
        if (!costRegistered) {
            costRegistered = true
            SpecialEnergyCosts.register { player, cost -> active?.cost(player, cost) ?: cost }
        }
        onEvent<NpcDeathEvents.Killed> {
            val killer = hero ?: return@onEvent
            if (isChosen(killer)) {
                energy.giveSpecialEnergy(killer, KILL_RESTORE * ENERGY_PER_PERCENT)
            }
        }
    }

    fun isChosen(player: Player): Boolean = relics.has(player, TIER, OPTION)

    fun cost(player: Player, cost: Int): Int =
        if (isChosen(player)) COST * ENERGY_PER_PERCENT else cost

    override fun multiplier(player: Player): Double =
        if (isChosen(player)) ACCURACY_MULTIPLIER else 1.0

    override fun missed(player: Player) {
        if (isChosen(player)) {
            energy.giveSpecialEnergy(player, MISS_REFUND * ENERGY_PER_PERCENT)
        }
    }

    companion object {
        const val TIER = 7
        const val OPTION = 1

        const val COST = 20
        const val MISS_REFUND = 10
        const val KILL_RESTORE = 15
        const val ACCURACY_MULTIPLIER = 2.0

        /** Special attack energy is kept in tenths of a percent. */
        private const val ENERGY_PER_PERCENT = 10

        /** The cost rule is registered once and asks the script of the current server. */
        @Volatile private var active: LeagueSpecialist? = null
        @Volatile private var costRegistered = false
    }
}
