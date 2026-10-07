package org.rsmod.content.areas.misc.isleofsouls.event

import jakarta.inject.Inject
import org.rsmod.api.config.refs.invs
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statBase
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType

/**
 * Puts the trimmed skillcape and its hood in the player's bank when they reach level 99 in a skill,
 * and the max cape and hood when that makes every skill level 99. An item the bank has no room for
 * is skipped.
 */
class LeagueSkillcapes @Inject constructor(private val invTypes: InvTypeList) {
    private class Cape(val cape: ObjType, val hood: ObjType)

    /** [stat] of [player] has just gone up a level. */
    fun onLevelUp(player: Player, stat: StatType, level: Int) {
        if (level < MAX_LEVEL) {
            return
        }
        val reward = capes[stat] ?: return
        val bank = player.invMap.getOrPut(invTypes[invs.bank])
        if (give(player, bank, reward)) {
            player.mes(
                "Your ${LeagueChat.blue("trimmed skillcape")} and hood have been placed in your bank."
            )
        }
        if (capes.keys.all { player.statBase(it) >= MAX_LEVEL } && give(player, bank, maxCape)) {
            player.mes(
                "Your ${LeagueChat.blue("max cape")} and hood have been placed in your bank."
            )
        }
    }

    /** Whether at least one of the cape and hood was added to [bank]. */
    private fun give(player: Player, bank: Inventory, reward: Cape): Boolean {
        val gotCape = player.invAdd(bank, reward.cape).success
        val gotHood = player.invAdd(bank, reward.hood).success
        return gotCape || gotHood
    }

    private companion object {
        const val MAX_LEVEL = 99

        val maxCape = Cape(objs.max_cape_inv, objs.max_hood)

        val capes: Map<StatType, Cape> =
            mapOf(
                stats.attack to Cape(objs.attack_skillcape_t, objs.attack_hood),
                stats.defence to Cape(objs.defence_skillcape_t, objs.defence_hood),
                stats.strength to Cape(objs.strength_skillcape_t, objs.strength_hood),
                stats.hitpoints to Cape(objs.hitpoints_skillcape_t, objs.hitpoints_hood),
                stats.ranged to Cape(objs.ranged_skillcape_t, objs.ranged_hood),
                stats.prayer to Cape(objs.prayer_skillcape_t, objs.prayer_hood),
                stats.magic to Cape(objs.magic_skillcape_t, objs.magic_hood),
                stats.cooking to Cape(objs.cooking_skillcape_t, objs.cooking_hood),
                stats.woodcutting to Cape(objs.woodcutting_skillcape_t, objs.woodcutting_hood),
                stats.fletching to Cape(objs.fletching_skillcape_t, objs.fletching_hood),
                stats.fishing to Cape(objs.fishing_skillcape_t, objs.fishing_hood),
                stats.firemaking to Cape(objs.firemaking_skillcape_t, objs.firemaking_hood),
                stats.crafting to Cape(objs.crafting_skillcape_t, objs.crafting_hood),
                stats.smithing to Cape(objs.smithing_skillcape_t, objs.smithing_hood),
                stats.mining to Cape(objs.mining_skillcape_t, objs.mining_hood),
                stats.herblore to Cape(objs.herblore_skillcape_t, objs.herblore_hood),
                stats.agility to Cape(objs.agility_skillcape_t, objs.agility_hood),
                stats.thieving to Cape(objs.thieving_skillcape_t, objs.thieving_hood),
                stats.slayer to Cape(objs.slayer_skillcape_t, objs.slayer_hood),
                stats.farming to Cape(objs.farming_skillcape_t, objs.farming_hood),
                stats.runecrafting to Cape(objs.runecrafting_skillcape_t, objs.runecrafting_hood),
                stats.hunter to Cape(objs.hunter_skillcape_t, objs.hunter_hood),
                stats.construction to Cape(objs.construction_skillcape_t, objs.construction_hood),
            )
    }
}
