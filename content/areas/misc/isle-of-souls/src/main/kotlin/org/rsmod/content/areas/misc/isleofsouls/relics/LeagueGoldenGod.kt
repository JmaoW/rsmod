package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.shops.ShopNoting
import org.rsmod.api.shops.config.ShopComponents
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.miningguild.MiningGuildInvs
import org.rsmod.content.skills.magic.utility.scripts.AlchemyModifiers
import org.rsmod.content.skills.prayer.configs.PrayerBones
import org.rsmod.content.skills.prayer.scripts.Altars
import org.rsmod.content.skills.runecraft.configs.RunecraftInvs
import org.rsmod.game.entity.Player
import org.rsmod.game.shop.Shop
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Golden God relic (tier 3 in the code, "tier 4" in the plan and on the wiki), which changes
 * three things:
 * - Alchemy: High and Low Alchemy need no runes and no Magic level, pay 15% more coins, keep the
 *   item 65% of the time, and carry on casting by themselves on a stack (see [Alchemy]).
 * - Prayer: [COINS_PER_OFFERING] coins used on an altar give the experience of a dragon bone
 *   offering, with the altar's own multiplier and the chaos altar's saving.
 * - Shops: what is bought from [NOTED_SHOPS] is handed over noted when it can be. The shop screen
 *   has a "Noted items" button, which the client shows by itself while the relic is chosen, that
 *   switches this off and on (see [LeagueGoldenGodScript]).
 */
class LeagueGoldenGod @Inject constructor(private val relics: LeagueRelicTracker) :
    AlchemyModifiers, ShopNoting {
    fun isActive(player: Player): Boolean = relics.has(player, TIER, OPTION)

    override fun isFree(player: Player): Boolean = isActive(player)

    override fun coinPercent(player: Player): Int = if (isActive(player)) COIN_PERCENT else 100

    override fun keepChance(player: Player): Int = if (isActive(player)) KEEP_CHANCE else 0

    override fun autoRecast(player: Player): Boolean = isActive(player)

    override fun shouldNote(player: Player, shop: Shop): Boolean =
        isActive(player) &&
            !isNotingDisabled(player) &&
            NOTED_SHOPS.any { it.id == shop.inv.type.id }

    /** Whether the player has switched the shop's "Noted items" button off. */
    fun isNotingDisabled(player: Player): Boolean =
        player.vars[RelicVarBits.shop_noted_disabled] == 1

    companion object {
        const val TIER = 3
        const val OPTION = 1

        /** The percent of the usual coins alchemy pays. */
        const val COIN_PERCENT = 115

        /** The percent chance alchemy does not use the item up. */
        const val KEEP_CHANCE = 65

        /** The coins one offering at an altar uses up, for the experience of a dragon bone. */
        const val COINS_PER_OFFERING = 20_000

        /**
         * The shops that hand over what is bought as notes. These are the shops the isle has; a new
         * shop only needs to be added here.
         */
        val NOTED_SHOPS =
            listOf(
                RunecraftInvs.rune_shop,
                RunecraftInvs.battle_rune_shop,
                MiningGuildInvs.pickaxe_shop,
                MiningGuildInvs.nugget_shop,
            )
    }
}

/**
 * The parts of the Golden God relic that are not a number: the coin offering and the shop button.
 */
class LeagueGoldenGodScript @Inject constructor(private val golden: LeagueGoldenGod) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<Altars.OfferRule> {
            if (golden.isActive(player) && obj.id == objs.coins.id) {
                count = LeagueGoldenGod.COINS_PER_OFFERING
                experienceLike = PrayerBones.dragon
            }
        }

        // The button's one option is "Toggle". The client draws the button from the same varbit.
        onIfModalButton(ShopComponents.note_button) {
            if (it.op != IfButtonOp.Op1 || !golden.isActive(player)) {
                return@onIfModalButton
            }
            val disable = !golden.isNotingDisabled(player)
            VarPlayerIntMapSetter.set(
                player,
                RelicVarBits.shop_noted_disabled,
                if (disable) 1 else 0,
            )
            val state = if (disable) LeagueChat.red("off") else LeagueChat.green("on")
            player.mes("Items you buy from shops are noted when they can be: $state.")
        }
    }
}
