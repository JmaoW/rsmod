package org.rsmod.content.areas.misc.isleofsouls.miningguild

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Prospector Percy, who takes golden nuggets for the prospector outfit, and Yarsul, who sells
 * pickaxes for coins, both standing in the Mining Guild.
 */
class MiningGuildShopScript
@Inject
constructor(
    private val shops: Shops,
    private val operationMap: ShopOperationMap,
    private val nuggetOperations: NuggetShopOperations,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup {
            registerCurrency()
            placeNpcs()
        }

        onOpNpc1(MiningGuildNpcs.percy) { talkToPercy(it.npc) }
        onOpNpc3(MiningGuildNpcs.percy) { player.openNuggetShop(it.npc) }

        onOpNpc1(MiningGuildNpcs.yarsul) { talkToYarsul(it.npc) }
        onOpNpc3(MiningGuildNpcs.yarsul) { player.openPickaxeShop(it.npc) }
    }

    /**
     * Teaches the shop code to take golden nuggets. Runs when the server starts rather than as the
     * script loads, because a currency's id is only known once the type references are resolved.
     */
    fun registerCurrency() {
        operationMap.register(MiningGuildCurrencies.golden_nuggets, nuggetOperations)
    }

    /** Puts Percy and Yarsul in the guild, where they wander about. Runs when the server starts. */
    fun placeNpcs() {
        place(MiningGuildNpcs.percy, PERCY)
        place(MiningGuildNpcs.yarsul, YARSUL)
    }

    private fun place(type: org.rsmod.game.type.npc.NpcType, coords: CoordGrid) {
        val npc = Npc(npcTypes[type], coords)
        npcRepo.add(npc, Int.MAX_VALUE)
    }

    private fun Player.openNuggetShop(npc: Npc) {
        shops.open(
            this,
            npc,
            "Prospector Percy's Nugget Shop",
            MiningGuildInvs.nugget_shop,
            currency = MiningGuildCurrencies.golden_nuggets,
        )
    }

    private fun Player.openPickaxeShop(npc: Npc) {
        shops.open(this, npc, "Yarsul's Prodigious Pickaxes", MiningGuildInvs.pickaxe_shop)
    }

    private suspend fun ProtectedAccess.talkToPercy(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Well met, miner! Prospector Percy, at your service.")
            val choice =
                choice3(
                    "What do you sell?",
                    1,
                    "Where do golden nuggets come from?",
                    2,
                    "Nothing, thanks.",
                    3,
                )
            when (choice) {
                1 -> {
                    chatPlayer(quiz, "What do you sell?")
                    chatNpc(
                        neutral,
                        "Prospector clothing, and I take golden nuggets for it. " +
                            "Wear the whole set and every ore you mine is worth that bit more.",
                    )
                    player.openNuggetShop(npc)
                }
                2 -> {
                    chatPlayer(quiz, "Where do golden nuggets come from?")
                    chatNpc(
                        neutral,
                        "Mine any ore in this guild and a golden nugget turns up with it, every time.",
                    )
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.talkToYarsul(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Welcome to the Mining Guild! Looking for a new pickaxe?")
            val choice = choice2("Yes, please.", 1, "No, thanks.", 2)
            when (choice) {
                1 -> {
                    chatPlayer(happy, "Yes, please.")
                    player.openPickaxeShop(npc)
                }
                else -> chatPlayer(neutral, "No, thanks.")
            }
        }
    }

    companion object {
        /** Beside the guild's bank chest (3012, 9718), which is used from its east side. */
        val PERCY = CoordGrid(3015, 9719, 0)

        /** In front of the table at the mine's far end, where the user stood. */
        val YARSUL = CoordGrid(3031, 9746, 0)
    }
}
