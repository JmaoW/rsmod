package org.rsmod.content.skills.runecraft.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.runecraft.configs.Pouch
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.content.skills.runecraft.configs.RuneAltar
import org.rsmod.content.skills.runecraft.configs.RuneAltars
import org.rsmod.content.skills.runecraft.configs.RunecraftInvs
import org.rsmod.content.skills.runecraft.configs.RunecraftLocs
import org.rsmod.content.skills.runecraft.configs.RunecraftNpcs
import org.rsmod.content.skills.runecraft.configs.RunecraftObjs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Abyss' outer ring (monsters and the skill obstacles) and the Abyssal Nexus passage. The
//   Mage here drops a player into the inner ring, and nobody loses prayer points or is skulled
// - the quest locks on the cosmic, law, death and blood rifts, which the league does not have
// - the soul rift, which needs Kourend's Soul Altar and dark essence (phase 2 of the plan)
//
// The Mage of Zamorak on the isle teleports players into the middle of the Abyss, beside the Dark
// Mage, and sells runes. Every rift in the Abyss leads to an altar room, and the portal in each
// room (or the one the isle adds to the Astral Altar) leads back to the Mage. The Dark Mage hands
// out essence pouches as the player reaches their levels, and the abyssal needle for the colossal
// one. The cache has no rift for blood, astral or wrath, so the isle adds those (see [RiftEdits]).
class Abyss @Inject constructor(private val sites: RunecraftSites, private val shops: Shops) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(RunecraftNpcs.mage_of_zamorak) { talkToMage(it.npc) }
        onOpNpc3(RunecraftNpcs.mage_of_zamorak) { openShop(it.npc) }
        onOpNpc4(RunecraftNpcs.mage_of_zamorak) { enterAbyss() }

        onOpNpc1(RunecraftNpcs.dark_mage) { talkToDarkMage(it.npc) }
        onOpNpc3(RunecraftNpcs.dark_mage) {
            mes("The Dark Mage smiles. Your pouches never wear out here, so they need no repairs.")
        }

        for (altar in RuneAltars.all) {
            onOpLoc1(altar.rift) { enterAltar(altar) }
            val exit = altar.exitPortal
            if (exit != null) {
                onOpLoc1(exit) { goHome() }
            }
        }
        onOpLoc1(RunecraftLocs.soul_rift) {
            mes("The rift is silent. The Soul Altar cannot be reached from here yet.")
        }
        // The exit that the isle adds to the essence mine and to the Astral Altar.
        onOpLoc1(RunecraftLocs.mine_exit) { goHome() }
    }

    private fun ProtectedAccess.enterAbyss() {
        telejump(sites.abyssArrival)
        mes("You are teleported into the Abyss!")
        publish(AbyssEntered(player))
    }

    private fun ProtectedAccess.enterAltar(altar: RuneAltar) {
        telejump(sites.landing(altar.inside))
        publish(AltarEntered(player, altar))
    }

    private fun ProtectedAccess.goHome() {
        telejump(sites.home)
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, "Mage of Zamorak's Battle Runes", RunecraftInvs.rune_shop)
    }

    private suspend fun ProtectedAccess.talkToMage(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Welcome. The Zamorak Magical Institute can open the Abyss for you.")
            val choice =
                choice3(
                    "Teleport me to the Abyss.",
                    1,
                    "What do you sell?",
                    2,
                    "Nothing, thanks.",
                    3,
                )
            when (choice) {
                1 -> {
                    chatPlayer(neutral, "Teleport me to the Abyss.")
                    chatNpc(happy, "Senventior disthine molenko... off you go!")
                    access.enterAbyss()
                }
                2 -> {
                    chatPlayer(quiz, "What do you sell?")
                    chatNpc(neutral, "Runes, for those who have not made their own.")
                    access.openShop(npc)
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }

    private suspend fun ProtectedAccess.talkToDarkMage(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Welcome to the heart of the Abyss. The rifts lead to every altar.")
            val choice =
                choice4(
                    "Do you have an essence pouch for me?",
                    1,
                    "I need an abyssal needle.",
                    2,
                    "Take me back to the Mage.",
                    3,
                    "Nothing, thanks.",
                    4,
                )
            when (choice) {
                1 -> {
                    chatPlayer(quiz, "Do you have an essence pouch for me?")
                    val given = access.givePouches()
                    if (given.isEmpty()) {
                        chatNpc(neutral, "You have every pouch you are ready for.")
                    } else {
                        val names = given.joinToString(", ") { it.name }
                        chatNpc(
                            happy,
                            "Take the $names ${if (given.size == 1) "pouch" else "pouches"}.",
                        )
                    }
                }
                2 -> {
                    chatPlayer(quiz, "I need an abyssal needle.")
                    chatNpc(neutral, access.needleReply())
                }
                3 -> {
                    chatPlayer(neutral, "Take me back to the Mage.")
                    access.goHome()
                }
                else -> chatPlayer(neutral, "Nothing, thanks.")
            }
        }
    }

    /** Gives every pouch the player has the level for and does not yet own. */
    fun ProtectedAccess.givePouches(): List<Pouch> {
        val level = player.runecraftingLvl
        val given = ArrayList<Pouch>()
        for (pouch in Pouches.standard) {
            if (level >= pouch.level && !owns(pouch) && invAdd(inv, pouch.obj, 1).success) {
                given += pouch
            }
        }
        if (given.isNotEmpty()) {
            publish(PouchesGiven(player, given))
        }
        return given
    }

    /** Hands over the abyssal needle once the player has all four pouches and the levels. */
    fun ProtectedAccess.needleReply(): String {
        val level = player.runecraftingLvl
        return when {
            owns(Pouches.colossal) -> "You already own a colossal pouch."
            invTotal(inv, RunecraftObjs.abyssal_needle) > 0 -> "You already have a needle."
            Pouches.standard.any { !owns(it) } ->
                "Come back with a small, medium, large and giant pouch."
            level < Pouches.colossal.level || player.craftingLvl < COLOSSAL_CRAFTING_LEVEL ->
                "You need Runecraft ${Pouches.colossal.level} and Crafting $COLOSSAL_CRAFTING_LEVEL."
            invAdd(inv, RunecraftObjs.abyssal_needle, 1).failure ->
                "Make some room in your inventory first."
            else -> "Use it on your pouches to stitch them into a colossal one."
        }
    }

    private fun ProtectedAccess.owns(pouch: Pouch): Boolean =
        invTotal(inv, pouch.obj) > 0 || invTotal(bank, pouch.obj) > 0

    /** Published when a player has been teleported into the Abyss. */
    class AbyssEntered(val player: Player) : UnboundEvent

    /** Published when a player has gone through a rift into [altar]'s room. */
    class AltarEntered(val player: Player, val altar: RuneAltar) : UnboundEvent

    /** Published when the Dark Mage has handed out [pouches]. */
    class PouchesGiven(val player: Player, val pouches: List<Pouch>) : UnboundEvent

    private companion object {
        const val COLOSSAL_CRAFTING_LEVEL = 56
    }
}
