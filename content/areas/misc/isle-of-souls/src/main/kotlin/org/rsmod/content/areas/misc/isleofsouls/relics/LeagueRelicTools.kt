package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList

/**
 * Relics that hand out an item. Their effects only work while the [item] is in the inventory or
 * equipped, and the item is given back if it goes missing.
 */
enum class RelicItem(val tier: Int, val option: Int, val item: ObjType) {
    PowerMiner(0, 1, objs.league_trailblazer_pickaxe),
    Lumberjack(0, 2, objs.league_trailblazer_axe),
    AnimalWrangler(0, 3, objs.league_trailblazer_harpoon),
    CornerCutter(1, 1, isle_objs.sages_greaves),
    FriendlyForager(1, 2, isle_objs.foragers_pouch),
    TotalRecall(5, 1, isle_objs.crystal_of_echoes),
    BankersNote(5, 2, isle_objs.bankers_note),
    Grimoire(6, 2, isle_objs.arcane_grimoire),
    Guardian(7, 2, isle_objs.guardian_horn),
}

/** Hands out the relic items and tells the relic effects whether they are active. */
class LeagueRelicTools
@Inject
constructor(private val relics: LeagueRelicTracker, private val objTypes: ObjTypeList) {
    /** Whether [relic] is the chosen relic of its tier and its item is carried or equipped. */
    fun isActive(player: Player, relic: RelicItem): Boolean =
        isChosen(player, relic) && holds(player, relic.item)

    /**
     * Gives the player the item of every chosen relic they do not have anywhere. It goes to the
     * bank when the inventory is full.
     */
    fun ProtectedAccess.grantMissing() {
        for (relic in RelicItem.entries) {
            if (!isChosen(player, relic) || holds(player, relic.item) || relic.item in bank) {
                continue
            }
            val added = invAdd(inv, relic.item).success || invAdd(bank, relic.item).success
            if (added) {
                mes("You receive the ${LeagueChat.blue(objTypes[relic.item].name)}.")
            }
        }
    }

    /** Whether [relic] is the chosen relic of its tier, whether or not its item is carried. */
    fun isChosen(player: Player, relic: RelicItem): Boolean =
        relics.has(player, relic.tier, relic.option)

    private fun holds(player: Player, item: ObjType): Boolean =
        item in player.inv || item in player.worn
}
