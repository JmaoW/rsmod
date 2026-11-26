package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpcU
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Probita, the pet insurance bureau, moved from East Ardougne into the Soul Wars lobby. Her
 * dialogue is the wiki transcript; "Check" and "I've lost a pet" open the game's own pet insurance
 * screen (interface 148). Every pet a player has received is insured, and one they no longer have
 * anywhere (following, inventory or bank) can be reclaimed for free. Talking to her also insures
 * any pet the player has with them that is not on record yet (wiki: "visit Probita with your
 * pets").
 *
 * The screen is drawn by the client (clientscript 6126, decoded from the cache): the server sends
 * the insured and owned bitmasks and where each owned pet is. Picking "Reclaim" on a pet row sets
 * varbit `pet_insurance_reclaim` (the pet's bit + 1), which brings up the Confirm button.
 */
class Probita
@Inject
constructor(
    private val pets: Pets,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { npcRepo.add(Npc(npcTypes[PetMiscNpcs.probita], SPAWN), Int.MAX_VALUE) }
        onOpNpc1(PetMiscNpcs.probita) { talk(it.npc) }
        onOpNpc3(PetMiscNpcs.probita) { openInsurance() }
        onOpNpcU(PetMiscNpcs.probita) { useItemOn(it.npc, it.objType.id) }
        onIfModalButton(PetComponents.pets) { pickPet(it.comsub) }
        onIfModalButton(PetComponents.confirm) { confirmReclaim() }
        // The frame's close button (if the client sends it rather than closing by itself).
        onIfModalButton(PetComponents.frame) { ifClose() }
    }

    // Dialogue (wiki transcript)
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        insureCarried(player)
        var open = false
        startDialogue(npc) {
            chatNpc(happy, "Welcome to the pet insurance bureau. How can I help you?")
            open = mainOptions()
        }
        if (open) {
            openInsurance()
        }
    }

    /** The transcript's first menu. `true` when it ends by opening the insurance screen. */
    private suspend fun Dialogue.mainOptions(): Boolean {
        while (true) {
            val pick =
                choice4(
                    "Can you tell me about pets and pet insurance?",
                    1,
                    "I've lost a pet. Have you got it?",
                    2,
                    "I have a pet that I'd like to insure.",
                    3,
                    "More options...",
                    4,
                )
            when (pick) {
                1 -> {
                    chatPlayer(quiz, "Can you tell me about pets and pet insurance?")
                    chatNpc(
                        happy,
                        "Certainly! You can find pets while out on your adventures. They're " +
                            "very rare, but you may be lucky enough to find one.",
                    )
                    chatNpc(
                        happy,
                        "You have a chance to find them when defeating bosses, playing " +
                            "minigames, and taking part in skilling activities.",
                    )
                    chatNpc(
                        happy,
                        "If you do find one, they will be automatically insured against loss, " +
                            "for free!",
                    )
                    chatNpc(
                        happy,
                        "If you then somehow lose your pet, you can reclaim it here for free " +
                            "also. The service used to cost Reclaim tokens worth 1,000,000 coins " +
                            "each. If you have any of those, I can refund you.",
                    )
                    chatPlayer(neutral, "I see, thank you.")
                }
                2 -> {
                    chatPlayer(worried, "I've lost a pet. Have you got it?")
                    chatNpc(happy, "If you've lost your pet, it will be reclaimable here!")
                    chatNpc(neutral, "Let's have a look...")
                    return true
                }
                3 -> {
                    chatPlayer(neutral, "I have a pet that I'd like to insure.")
                    chatNpc(
                        happy,
                        "You don't need to pay to insure your pet with me. They're " +
                            "automatically insured for free as soon as you find them!",
                    )
                    chatNpc(
                        neutral,
                        "If you lose a pet somehow, you can come here and reclaim them. It used " +
                            "to cost reclaim tokens, although we're now subsidised by the Grand " +
                            "Exchange and the service is free. If you have any reclaim tokens " +
                            "left, let us know and we will buy them back.",
                    )
                }
                else ->
                    when (moreOptions()) {
                        More.Open -> return true
                        More.End -> return false
                        More.Previous -> Unit
                    }
            }
        }
    }

    private enum class More {
        Open,
        End,
        Previous,
    }

    private suspend fun Dialogue.moreOptions(): More {
        val pick =
            choice4(
                "What pets have I insured?",
                1,
                "My pet is still lost. Could you look again?",
                2,
                "Maybe another time.",
                3,
                "Previous options...",
                4,
            )
        return when (pick) {
            1 -> {
                chatPlayer(quiz, "What pets have I insured?")
                chatNpc(happy, "Every pet you've ever found is automatically insured here!")
                chatNpc(neutral, "Here, take a look...")
                More.Open
            }
            2 -> {
                chatPlayer(worried, "My pet is still lost. Could you look again?")
                chatNpc(
                    neutral,
                    "It's possible there was an error in our records. Let me check again for you.",
                )
                access.mes(
                    "<col=0a5d00>If you believe you have some pets that are not showing on your " +
                        "insurance record, please visit Probita with your pets and she will " +
                        "automatically insure them for you.</col>"
                )
                More.Open
            }
            3 -> {
                chatPlayer(neutral, "Maybe another time.")
                More.End
            }
            else -> More.Previous
        }
    }

    private suspend fun ProtectedAccess.useItemOn(npc: Npc, obj: Int) {
        val text =
            when {
                pets.isPet(obj) ->
                    "You don't need to pay to insure your pet with me. They're automatically " +
                        "insured for free as soon as you find them!"
                obj == COINS -> "If you keep waving money at me, I might take it!"
                else -> "This is a pet insurance bureau. I'm not insuring that."
            }
        startDialogue(npc) { chatNpc(neutral, text) }
    }

    /** Any pet [player] has with them (or in the bank) that is not on record is insured now. */
    private fun insureCarried(player: Player) {
        for (pet in pets.catalog) {
            if (!pets.isInsured(player, pet) && pets.owns(player, pet)) {
                pets.insure(player, pet)
            }
        }
    }

    // The pet insurance screen
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.openInsurance() {
        insureCarried(player)
        VarPlayerIntMapSetter.set(player, pet_varbits.reclaim, 0)
        ifOpenMainModal(PetInterfaces.insurance)
        player.ifSetEvents(PetComponents.pets, 0..PET_CHILDREN, IfEvent.Op1)
        player.ifSetEvents(PetComponents.confirm, 0..0, IfEvent.Op1)
        draw(player)
    }

    /** Sends the bitmasks and locations and runs the screen's draw script. */
    private fun ProtectedAccess.draw(player: Player) {
        val insured = IntArray(3)
        val owned = IntArray(3)
        val locations = IntArray(LOCATION_INTS)
        for (pet in pets.catalog) {
            val (index, bit) = split(pet.bit)
            if (pets.isInsured(player, pet)) {
                insured[index] = insured[index] or (1 shl bit)
            }
            val location = pets.location(player, pet)
            if (location != 0) {
                owned[index] = owned[index] or (1 shl bit)
                val word = pet.bit / PETS_PER_INT
                val shift = (pet.bit % PETS_PER_INT) * 4
                locations[word] = locations[word] or (location shl shift)
            }
        }
        for (i in 0 until 3) {
            VarPlayerIntMapSetter.set(player, pet_varps.insurance_bitmasks[i], insured[i])
            VarPlayerIntMapSetter.set(player, pet_varps.insurance_owned[i], owned[i])
        }
        val args = ArrayList<Any>()
        insured.forEach { args += it }
        owned.forEach { args += it }
        locations.forEach { args += it }
        runClientScript(DRAW_SCRIPT, *args.toTypedArray())
    }

    /** "Reclaim" on a pet's row (the first of its five children, after the six headings). */
    private fun ProtectedAccess.pickPet(comsub: Int) {
        val offset = comsub - FIRST_ROW
        if (offset < 0 || offset % CHILDREN_PER_PET != 0) {
            return
        }
        val pet = pets.catalog.getOrNull(offset / CHILDREN_PER_PET) ?: return
        if (pet !in pets.reclaimable(player)) {
            return
        }
        VarPlayerIntMapSetter.set(player, pet_varbits.reclaim, pet.bit + 1)
    }

    private fun ProtectedAccess.confirmReclaim() {
        val picked = player.vars[pet_varbits.reclaim]
        val pet = pets.catalog.getOrNull(picked - 1) ?: return
        if (pet !in pets.reclaimable(player)) {
            VarPlayerIntMapSetter.set(player, pet_varbits.reclaim, 0)
            return
        }
        if (!pets.reclaim(player, pet)) {
            mes("You don't have enough inventory space.")
            return
        }
        VarPlayerIntMapSetter.set(player, pet_varbits.reclaim, 0)
        mes("Probita hands your ${pet.name.lowercase()} back to you.")
        draw(player)
    }

    private fun split(bit: Int): Pair<Int, Int> =
        when {
            bit < 31 -> 0 to bit
            bit < 62 -> 1 to bit - 31
            else -> 2 to bit - 62
        }

    companion object {
        /** Beside the lobby's walkway, south of the two graveyards. */
        val SPAWN = CoordGrid(2210, 2841, 0)

        const val COINS = 995

        /** Clientscript `pet_insurance` draw (decoded: 15 ints, three bitmasks twice, 9 places). */
        const val DRAW_SCRIPT = 6126

        /** The list starts with six heading children; each pet then has five. */
        const val FIRST_ROW = 6
        const val CHILDREN_PER_PET = 5
        const val PET_CHILDREN = 1_000
        const val PETS_PER_INT = 8
        const val LOCATION_INTS = 9
    }
}
