package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.market.MarketPrices
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.api.shops.operation.ObjCurrencyShopOperations
import org.rsmod.api.shops.operation.ShopOperationMap
import org.rsmod.api.shops.restock.ShopRestockProcess
import org.rsmod.content.skills.agility.configs.AgilityObjs
import org.rsmod.content.skills.agility.configs.AgilityVarps
import org.rsmod.content.skills.agility.configs.Courses
import org.rsmod.content.skills.agility.configs.GraceCurrencies
import org.rsmod.content.skills.agility.configs.GraceInvs
import org.rsmod.content.skills.agility.configs.GraceNpcs
import org.rsmod.content.skills.agility.configs.GraceObjs
import org.rsmod.content.skills.agility.configs.GraceSpot
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Grace's shop, which takes marks of grace instead of coins. */
class MarkShopOperations
@Inject
constructor(objTypes: ObjTypeList, restockProcess: ShopRestockProcess, marketPrices: MarketPrices) :
    ObjCurrencyShopOperations(
        objTypes,
        restockProcess,
        marketPrices,
        AgilityObjs.mark_of_grace,
        "mark of grace",
    )

/**
 * Grace, who sells the graceful outfit for marks of grace and amylase packs, and the packs' "Open"
 * option, which gives the hundred crystals inside.
 */
class Grace
@Inject
constructor(
    private val shops: Shops,
    private val operationMap: ShopOperationMap,
    private val operations: MarkShopOperations,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup {
            registerCurrency()
            placeGrace()
        }
        onOpNpc1(GraceNpcs.grace) { talkTo(it.npc) }
        onOpNpc2(GraceNpcs.grace) { player.openShop(it.npc) }
        onOpNpc3(GraceNpcs.grace) { player.openShop(it.npc) }
        onOpNpc4(GraceNpcs.grace) { viewLaps(player) }
        onOpNpc5(GraceNpcs.grace) { toggleCounter(player) }
        onOpHeld1(GraceObjs.amylase_pack) { openPack() }
    }

    /** Teaches the shop code to take marks of grace. Runs when the server starts. */
    fun registerCurrency() {
        operationMap.register(GraceCurrencies.marks, operations)
    }

    fun placeGrace() {
        npcRepo.add(Npc(npcTypes[GraceNpcs.grace], GraceSpot.tile), Int.MAX_VALUE)
    }

    private fun Player.openShop(npc: Npc) {
        shops.open(
            this,
            npc,
            "Grace's Graceful Clothing",
            GraceInvs.shop,
            currency = GraceCurrencies.marks,
        )
    }

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        startDialogue(npc) {
            chatNpc(happy, "Hello there. Have you been collecting marks of grace?")
            val choice =
                choice3(
                    "What do you sell?",
                    1,
                    "How do I get marks of grace?",
                    2,
                    "Nothing, thanks.",
                    3,
                )
            when (choice) {
                1 -> {
                    chatPlayer(quiz, "What do you sell?")
                    chatNpc(
                        neutral,
                        "The graceful outfit, and packs of amylase for stamina potions. " +
                            "Wear the whole set and you will run further and weigh less.",
                    )
                    player.openShop(npc)
                }
                2 -> {
                    chatPlayer(quiz, "How do I get marks of grace?")
                    chatNpc(
                        neutral,
                        "Run a rooftop course. Now and then a mark falls on the roofs " +
                            "as you start a lap. There is no course on this isle, though.",
                    )
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }

    /** Grace "View Laps": how many laps of each course the player has run. */
    private fun viewLaps(player: Player) {
        player.mes("Your lap counts:")
        for (course in Courses.all) {
            val varp = AgilityVarps.laps(course.key) ?: continue
            player.mes("${course.shortName}: <col=ff0000>${player.vars[varp]}</col>")
        }
    }

    /** Grace "Toggle Counter": turns the lap count message after a lap off and on again. */
    private fun toggleCounter(player: Player) {
        val off = player.vars[AgilityVarps.lap_counter_off] == 0
        VarPlayerIntMapSetter.set(player, AgilityVarps.lap_counter_off, if (off) 1 else 0)
        player.mes(
            if (off) "Your lap count will no longer be shown when you finish a lap."
            else "Your lap count will be shown when you finish a lap."
        )
    }

    private fun ProtectedAccess.openPack() {
        if (invTotal(inv, GraceObjs.amylase_pack) == 0) {
            return
        }
        invDel(inv, GraceObjs.amylase_pack, 1)
        invAdd(inv, GraceObjs.amylase, 100)
        mes("You open the pack and find 100 amylase crystals.")
    }
}
