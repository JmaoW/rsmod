package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.areas.misc.isleofsouls.pets.Pets
import org.rsmod.content.interfaces.bank.openBank
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The way in and the lobby under the Colosseum: the arch on the isle and the stairs back up,
 * Minimus (his introduction, which opens the tunnel, and the quiver exchange), Gloria, the bank
 * chest with its Glory gate (off for the event, see [ColosseumSettings]), the scoreboard, and a
 * copy of a Shrine of Ralos for blessing Dizana's quiver.
 */
class ColosseumLobbyScript
@Inject
constructor(
    private val colosseum: Colosseum,
    private val completions: ColosseumCompletions,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val random: GameRandom,
    private val eventBus: EventBus,
    private val objRepo: ObjRepository,
    private val objTypes: ObjTypeList,
    private val pets: Pets,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placeLobby() }

        onOpLoc1(colosseum_locs.entrance_outside) { telejump(ColosseumArena.lobbyArrival) }
        onOpLoc1(colosseum_locs.stairs) { telejump(ColosseumArena.isleArrival) }
        onOpLoc1(colosseum_locs.bank) { useBank() }
        onOpLoc3(colosseum_locs.bank) { mes("You have nothing to collect.") }
        onOpLoc1(colosseum_locs.scoreboard) { readScoreboard() }

        onOpNpc1(colosseum_npcs.minimus_lobby) { talkToMinimus(it.npc) }
        onOpNpcU(colosseum_npcs.minimus_lobby, colosseum_objs.quiver_uncharged) {
            exchangeQuiver(it.npc)
        }
        onOpNpc1(colosseum_npcs.gloria) { talkToGloria(it.npc) }

        for (quiver in listOf(colosseum_objs.quiver_uncharged, colosseum_objs.quiver_charged)) {
            onOpLocU(colosseum_locs.ralos_shrine, quiver) { blessQuiver() }
        }
    }

    /** The isle's copy of the arch, the shrine, and the lobby's people. */
    private fun placeLobby() {
        locRepo.add(
            ColosseumArena.isleEntrance,
            locTypes[colosseum_locs.entrance_outside],
            Int.MAX_VALUE,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        locRepo.add(
            ColosseumArena.lobbyShrine,
            locTypes[colosseum_locs.ralos_shrine],
            Int.MAX_VALUE,
            LocAngle.South,
            LocShape.CentrepieceStraight,
        )
        spawn(colosseum_npcs.minimus_lobby, ColosseumArena.lobbyMinimus)
        spawn(colosseum_npcs.gloria, ColosseumArena.lobbyGloria)
        val guards =
            listOf(
                colosseum_npcs.guard_m_1,
                colosseum_npcs.guard_m_2,
                colosseum_npcs.guard_f_1,
                colosseum_npcs.guard_f_2,
            )
        for ((index, coords) in ColosseumArena.lobbyGuards.withIndex()) {
            spawn(guards[index % guards.size], coords)
        }
    }

    private fun spawn(type: NpcType, coords: CoordGrid) {
        npcRepo.add(Npc(npcTypes[type], coords), Int.MAX_VALUE)
    }

    // The bank chest and the scoreboard ---------------------------------------------------------

    private suspend fun ProtectedAccess.useBank() {
        val needed = colosseum.settings.bankChestGlory
        if (player.vars[colosseum_varps.glory] < needed) {
            minimusSays("Oi, get yer hands off that! It's reserved for survivors of the Colosseum.")
            mesbox("You do not have enough Glory to use that.")
            return
        }
        player.openBank(eventBus)
        completions.bankChest(player)
    }

    private fun ProtectedAccess.readScoreboard() {
        val glory = player.vars[colosseum_varps.glory]
        mes("Your best Glory: ${"%,d".format(glory)} (${Colosseum.rankOf(glory)}).")
        mes(
            "Highest wave: ${player.vars[colosseum_varbits.highest_wave]}. " +
                "Waves completed: ${player.vars[colosseum_varps.waves_completed]}."
        )
    }

    // Minimus -------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.minimusSays(text: String) {
        startDialogue {
            chatNpcSpecific("Minimus", npcTypes[colosseum_npcs.minimus_lobby], neutral, text)
        }
    }

    /** The transcript's first meeting (which opens the tunnel) and the questions after it. */
    private suspend fun ProtectedAccess.talkToMinimus(npc: Npc) {
        val rank = colosseum.rankOf(player)
        startDialogue(npc) {
            chatPlayer(neutral, "Hello there.")
            chatNpc(neutral, "Welcome, $rank.")
            if (player.vars[colosseum_varbits.master_intro] == 0) {
                chatPlayer(quiz, "What is this place?")
                chatNpc(happy, "This place...? Why, this is the great Fortis Colosseum!")
                chatNpc(
                    neutral,
                    "Adventurers come from near and far to test their might within these walls.",
                )
                chatPlayer(quiz, "How do I participate?")
                chatNpc(
                    neutral,
                    "Just head through that tunnel behind me and we'll get the show started! " +
                        "Make sure you've got everything you need. Once you're in you can't " +
                        "leave until the end of a wave!",
                )
                vars[colosseum_varbits.master_intro] = 1
                chatNpc(neutral, "Come and ask if you have any other questions.")
            }
            while (true) {
                val choice =
                    choice4(
                        "Can you tell me about modifiers?",
                        1,
                        "How do I get rewarded?",
                        2,
                        "What is Glory?",
                        3,
                        "Goodbye.",
                        4,
                    )
                when (choice) {
                    1 -> {
                        chatNpc(
                            neutral,
                            "Every time you complete a wave, I'll offer you a choice of three " +
                                "modifiers.",
                        )
                        chatNpc(
                            neutral,
                            "Each modifier will increase the challenge for the rest of the run - " +
                                "So choose wisely! Some can be offered again, growing in power.",
                        )
                    }
                    2 -> {
                        chatNpc(
                            neutral,
                            "After each wave I'll show you the rewards you have earned, and what " +
                                "you'll earn from the next. You can claim them and leave, or " +
                                "risk the lot for more.",
                        )
                        chatNpc(
                            neutral,
                            "Die or leave before claiming them and you lose the lot. Finish the " +
                                "final challenge and you get Dizana's Quiver.",
                        )
                    }
                    3 -> {
                        chatNpc(neutral, "It's the measure of your progress inside the Colosseum.")
                        chatNpc(
                            neutral,
                            "Speak to Gloria over there if you want to know the details.",
                        )
                    }
                    else -> {
                        chatPlayer(neutral, "Goodbye.")
                        chatNpc(neutral, "Farewell, $rank.")
                        return@startDialogue
                    }
                }
            }
        }
    }

    /**
     * A spare Dizana's quiver for 4,000 sunfire splinters, or for one more 1 in 200 chance at Smol
     * Heredit (wiki; a player who has the pet is only offered the splinters).
     */
    private suspend fun ProtectedAccess.exchangeQuiver(npc: Npc) {
        val hasPet = pets.owns(player, pets.pet("smol_heredit"))
        startDialogue(npc) {
            chatPlayer(neutral, "I have a spare Dizana's quiver (uncharged).")
            chatNpc(
                neutral,
                "You want to offer it for some Sunfire splinters?" +
                    (if (hasPet) ""
                    else " I could also try to convince Smol Heredit to follow you.") +
                    " But be aware, you will not get the quiver back.",
            )
            val choice =
                if (hasPet) {
                    choice2("Yes, trade it for 4,000 Sunfire Splinters.", 1, "No.", 3)
                } else {
                    choice3(
                        "Yes, trade it for 4,000 Sunfire Splinters.",
                        1,
                        "Yes, trade it for a chance at Smol Heredit.",
                        2,
                        "No.",
                        3,
                        title = "Trade Dizana's Quiver?",
                    )
                }
            if (choice == 3 || colosseum_objs.quiver_uncharged !in inv) {
                return@startDialogue
            }
            invDel(inv, colosseum_objs.quiver_uncharged)
            if (choice == 1) {
                invAddOrDrop(
                    objRepo,
                    colosseum_objs.sunfire_splinters,
                    ColosseumLoot.QUIVER_SPLINTERS,
                )
                chatNpc(neutral, "A fine trade. Here are your splinters.")
            } else if (pets.roll(ColosseumLoot.PET_ODDS)) {
                colosseum.givePet(player)
            } else {
                chatNpc(neutral, "No luck this time, I'm afraid. He just isn't interested.")
            }
        }
    }

    // Gloria --------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.talkToGloria(npc: Npc) {
        val glory = player.vars[colosseum_varps.glory]
        val rank = Colosseum.rankOf(glory)
        vars[colosseum_varbits.gloria_met] = 1
        startDialogue(npc) {
            chatNpc(
                neutral,
                "Every wave you clear earns you Glory: more for clearing it quickly, without being " +
                    "hurt, and with harder modifiers. Only your best run counts.",
            )
            chatNpc(neutral, "Your best run so far earned you ${"%,d".format(glory)} Glory.")
            val next = Colosseum.RANKS.lastOrNull { it.first > glory }
            if (next == null) {
                chatNpc(happy, "You are a Grand Champion! The whole of Varlamore knows your name.")
            } else {
                chatNpc(
                    neutral,
                    "That makes you a $rank. Reach ${"%,d".format(next.first)} Glory to become a " +
                        "${next.second}.",
                )
            }
        }
    }

    // Blessing the quiver ------------------------------------------------------------------------

    /**
     * Using Dizana's quiver on the Shrine of Ralos with 150,000 sunfire splinters makes the blessed
     * quiver, whose sunfire never runs out (wiki). The charges already in the quiver count towards
     * the splinters.
     */
    private fun ProtectedAccess.blessQuiver() {
        val charges = player.vars[colosseum_varbits.quiver_charges]
        val charged = colosseum_objs.quiver_charged in inv
        val counted = if (charged) charges else 0
        val needed = BLESSING_SPLINTERS - counted
        val have = inv.count(objTypes[colosseum_objs.sunfire_splinters])
        if (have < needed) {
            mes(
                "You need ${"%,d".format(BLESSING_SPLINTERS)} sunfire splinters, counting the " +
                    "quiver's charges, to bless the quiver."
            )
            return
        }
        invDel(inv, colosseum_objs.sunfire_splinters, needed)
        val quiver = if (charged) colosseum_objs.quiver_charged else colosseum_objs.quiver_uncharged
        invReplace(inv, quiver, 1, colosseum_objs.quiver_blessed)
        if (charged) {
            vars[colosseum_varbits.quiver_charges] = 0
        }
        mes("Ralos blesses your quiver. Its sunfire will never run out.")
    }

    companion object {
        const val BLESSING_SPLINTERS = 150_000
    }
}
