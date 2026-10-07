package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.stat.herbloreLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.entity.Player
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjType

/**
 * The gathering effect of the Friendly Forager relic: gathering a resource finds a random grimy
 * herb, limited to herbs the player's Herblore level plus [LEVEL_BONUS] can clean, and gives a
 * little Herblore experience.
 *
 * There is no herb sack to store herbs in, so they are sent to the bank. A herb is skipped when the
 * bank has no room for it.
 */
class LeagueForager
@Inject
constructor(
    private val tools: LeagueRelicTools,
    private val random: GameRandom,
    private val xpMods: XpModifiers,
    private val invTypes: InvTypeList,
) {
    private class Herb(val type: ObjType, val level: Int, val xp: Double)

    fun onGather(player: Player) {
        if (!tools.isActive(player, RelicItem.FriendlyForager)) {
            return
        }
        val maxLevel = player.herbloreLvl + LEVEL_BONUS
        val candidates = herbs.filter { it.level <= maxLevel }
        if (candidates.isEmpty()) {
            return
        }
        val herb = candidates[random.of(candidates.size)]
        val bank = player.invMap.getOrPut(invTypes[invs.bank])
        if (!player.invAdd(bank, herb.type).success) {
            return
        }
        player.statAdvance(stats.herblore, herb.xp * xpMods.get(player, stats.herblore))
    }

    private companion object {
        const val LEVEL_BONUS = 25

        /** Every grimy herb with the Herblore level and experience for cleaning it. */
        val herbs =
            listOf(
                Herb(isle_objs.grimy_guam, 3, 2.5),
                Herb(isle_objs.grimy_marrentill, 5, 3.8),
                Herb(isle_objs.grimy_tarromin, 11, 5.0),
                Herb(isle_objs.grimy_harralander, 20, 6.3),
                Herb(isle_objs.grimy_ranarr, 25, 7.5),
                Herb(isle_objs.grimy_toadflax, 30, 8.0),
                Herb(isle_objs.grimy_irit, 40, 8.8),
                Herb(isle_objs.grimy_avantoe, 48, 10.0),
                Herb(isle_objs.grimy_kwuarm, 54, 11.3),
                Herb(isle_objs.grimy_snapdragon, 59, 11.8),
                Herb(isle_objs.grimy_cadantine, 65, 12.5),
                Herb(isle_objs.grimy_lantadyme, 67, 13.1),
                Herb(isle_objs.grimy_dwarf_weed, 70, 13.8),
                Herb(isle_objs.grimy_torstol, 75, 15.0),
            )
    }
}
