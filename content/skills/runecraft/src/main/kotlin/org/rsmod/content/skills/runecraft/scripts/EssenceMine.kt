package org.rsmod.content.skills.runecraft.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.runecraft.configs.RunecraftInvs
import org.rsmod.content.skills.runecraft.configs.RunecraftLocs
import org.rsmod.content.skills.runecraft.configs.RunecraftNpcs
import org.rsmod.content.skills.runecraft.configs.RunecraftObjs
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the other wizards who teleport to the mine (Sedridor, Cromperty, Distentor, Brimstail)
// - the Rune Mysteries quest, which the real game wants before the mine can be used
//
// Aubury on the isle sells runes (see [RuneShopEdits]) and teleports players to the rune essence
// mine. The big rock there is an ordinary Mining ore (see [EssenceRockEdits]) that never runs out;
// this makes every swing succeed, and gives pure essence instead of rune essence from Mining level
// 30, as the real game does.
class EssenceMine
@Inject
constructor(
    private val sites: RunecraftSites,
    private val eventBus: EventBus,
    private val shops: Shops,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(RunecraftNpcs.aubury) { talkToAubury(it.npc) }
        onOpNpc3(RunecraftNpcs.aubury) { openShop(it.npc) }
        onOpNpc4(RunecraftNpcs.aubury) { teleportToMine() }

        onEvent<Mining.MineRoll> {
            if (rock.isType(RunecraftLocs.essence_rock)) {
                success = true
                if (player.miningLvl >= PURE_ESSENCE_MINING_LEVEL) {
                    product = RunecraftObjs.pure_essence
                }
            }
        }
        onEvent<Mining.MineOre> {
            if (rock.isType(RunecraftLocs.essence_rock)) {
                keepRock = true
                eventBus.publish(
                    EssenceMined(player, pure = player.miningLvl >= PURE_ESSENCE_MINING_LEVEL)
                )
            }
        }
    }

    private fun ProtectedAccess.teleportToMine() {
        telejump(sites.mineArrival)
        publish(MineVisited(player))
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, "Aubury's Rune Shop", RunecraftInvs.rune_shop)
    }

    private suspend fun ProtectedAccess.talkToAubury(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Do you want to buy any runes? Or perhaps visit the essence mine?")
            val choice =
                choice3(
                    "Yes please!",
                    1,
                    "Teleport me to the essence mine.",
                    2,
                    "Oh, it's a rune shop. No thank you, then.",
                    3,
                )
            when (choice) {
                1 -> {
                    chatPlayer(happy, "Yes please!")
                    access.openShop(npc)
                }
                2 -> {
                    chatPlayer(neutral, "Teleport me to the essence mine.")
                    chatNpc(happy, "Senventior disthine molenko!")
                    access.teleportToMine()
                }
                else -> {
                    chatPlayer(neutral, "Oh, it's a rune shop. No thank you, then.")
                    chatNpc(
                        neutral,
                        "Well, if you find someone who does want to buy runes, send them my way.",
                    )
                }
            }
        }
    }

    /** Published when a player has been teleported to the rune essence mine. */
    class MineVisited(val player: Player) : UnboundEvent

    /** Published for each essence mined; [pure] when it was pure essence. */
    class EssenceMined(val player: Player, val pure: Boolean) : UnboundEvent

    companion object {
        /** Members with this much Mining get pure essence from the rocks, as in the real game. */
        const val PURE_ESSENCE_MINING_LEVEL = 30
    }
}
