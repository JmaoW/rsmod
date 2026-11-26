package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.npc.NpcFollowWalker
import org.rsmod.api.player.events.FollowerCall
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld5
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.script.onOpNpcU
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.areas.misc.isleofsouls.pets.PetMetamorph.ItemUse
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The pet items and follower npcs: "Drop" brings a pet out, the follower's options (Talk-to,
 * Pick-up, Metamorphosis, Locking, Emote), items used on a pet (following or in the inventory), the
 * Call follower button, and the follower leaving at logout and coming back at login.
 */
class PetScript
@Inject
constructor(
    private val pets: Pets,
    private val metamorph: PetMetamorph,
    private val talk: PetTalk,
    private val objTypes: ObjTypeList,
    private val walker: NpcFollowWalker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin { pets.restore(player) }
        onPlayerLogout { pets.dismiss(player, forget = false) }
        // Pets walk first, right behind their owner, ahead of a thrall and a guardian.
        walker.register(NpcFollowWalker.Rank.Pet) { pets.lateCycle() }
        onEvent<GameLifecycle.LateCycle> { walker.lateCycle() }
        onEvent<FollowerCall> {
            handled = true
            pets.call(player)?.let { player.mes(it) }
        }

        for (pet in pets.catalog) {
            for (form in pet.forms) {
                onOpHeld5(form.obj) { drop(it.slot, it.type) }
                onOpHeldU(form.obj) { useOnHeld(it.first, it.firstSlot, it.second, it.secondSlot) }
                onOpNpcU(form.npc) { useOnFollower(it.npc, it.objType, it.invSlot) }
                for ((op, text) in form.ops) {
                    registerNpcOp(op, text, form)
                }
            }
            if (pet.key == "beaver" || pet.key == "phoenix") {
                for (form in pet.forms) {
                    onOpHeld3(form.obj) { metamorphHeld(it.slot, it.type) }
                }
            }
        }
    }

    private fun ScriptContext.registerNpcOp(op: Int, text: String, form: PetForm) {
        val action: suspend ProtectedAccess.(Npc) -> Unit =
            when (text.lowercase()) {
                "talk-to" -> { npc -> talkTo(npc) }
                "pick-up" -> { npc -> pickUp(npc) }
                "metamorphosis",
                "metamorph" -> { npc -> metamorphose(npc) }
                "locking" -> { npc -> toggleLock(npc) }
                "emote" -> { npc -> emote(npc) }
                else -> return
            }
        when (op) {
            1 -> onOpNpc1(form.npc) { action(it.npc) }
            2 -> onOpNpc2(form.npc) { action(it.npc) }
            3 -> onOpNpc3(form.npc) { action(it.npc) }
            4 -> onOpNpc4(form.npc) { action(it.npc) }
            5 -> onOpNpc5(form.npc) { action(it.npc) }
        }
    }

    /** The follower [npc] when it is [player]'s own; other players' pets cannot be handled. */
    private fun ProtectedAccess.own(npc: Npc): Pets.Follower? {
        val follower = pets.followerOfNpc(npc) ?: return null
        return follower.takeIf { it.owner === player }
    }

    // Drop: the pet comes out
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.drop(slot: Int, type: UnpackedObjType) {
        val (pet, form) = pets.ofObj(type.id) ?: return
        if (pets.hasFollower(player)) {
            mes("You already have a follower.")
            return
        }
        if (!pets.canFollow(player)) {
            mes("You can't have a follower here.")
            return
        }
        if (!invDel(inv, type, count = 1, slot = slot).success) {
            return
        }
        if (!pets.spawn(player, pet, form)) {
            invAdd(inv, type, count = 1, slot = slot)
            mes("There is no room for your pet here.")
        }
    }

    // The follower's options
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.talkTo(npc: Npc) {
        val follower = own(npc) ?: return
        talk.talk(this, npc, follower.pet, follower.form)
    }

    private fun ProtectedAccess.pickUp(npc: Npc) {
        val follower = own(npc) ?: return
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        anim(PetSeqs.pickup)
        val obj = follower.form.obj
        pets.dismiss(player)
        invAdd(inv, obj, count = 1)
    }

    private suspend fun ProtectedAccess.metamorphose(npc: Npc) {
        val follower = own(npc) ?: return
        val next = metamorph.next(player, follower.pet, follower.form)
        if (next == null) {
            mes("Your ${follower.pet.name.lowercase()} doesn't seem able to change yet.")
            return
        }
        if (follower.form.label == "gold" && follower.pet.key == "baby_chinchompa") {
            var sure = false
            startDialogue(npc) {
                sure =
                    choice2(
                        "Yes, metamorphose it.",
                        true,
                        "No, keep it golden.",
                        false,
                        title = "Lose the gold colour? It may never come back.",
                    )
            }
            if (!sure || pets.followerOf(player) !== follower) {
                return
            }
        }
        pets.transform(player, next)
    }

    private fun ProtectedAccess.toggleLock(npc: Npc) {
        own(npc) ?: return
        val locked = player.vars[pet_varps.rift_unlocks] and RIFT_LOCK_BIT != 0
        VarPlayerIntMapSetter.set(
            player,
            pet_varps.rift_unlocks,
            player.vars[pet_varps.rift_unlocks] xor RIFT_LOCK_BIT,
        )
        if (locked) {
            mes("Your rift guardian will now change colour at the altars again.")
        } else {
            mes("Your rift guardian will now keep its colour.")
        }
    }

    private fun ProtectedAccess.emote(npc: Npc) {
        val follower = own(npc) ?: return
        val emotes = EMOTES[follower.pet.key] ?: return
        val clock = player.currentMapClock
        if (clock < follower.nextEmote) {
            mes(Pets.red("You must wait a moment before your pet can do another emote."))
            return
        }
        val seq = emotes[follower.emoteIndex % emotes.size]()
        follower.emoteIndex++
        follower.nextEmote = clock + EMOTE_COOLDOWN
        npc.anim(seq)
    }

    // Items used on pets
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.useOnFollower(npc: Npc, item: UnpackedObjType, slot: Int) {
        val follower = own(npc) ?: return
        val use = metamorph.useItem(player, follower.pet, follower.form, item, held(item))
        apply(use, item, slot) { form -> pets.transform(player, form) }
    }

    private fun ProtectedAccess.useOnHeld(
        first: UnpackedObjType,
        firstSlot: Int,
        second: UnpackedObjType,
        secondSlot: Int,
    ) {
        val firstPet = pets.ofObj(first.id)
        val (petEntry, petSlot, item, itemSlot) =
            if (firstPet != null) Quad(firstPet, firstSlot, second, secondSlot)
            else Quad(pets.ofObj(second.id) ?: return, secondSlot, first, firstSlot)
        val (pet, form) = petEntry
        if (pets.isPet(item.id)) {
            mes("Nothing interesting happens.")
            return
        }
        val use = metamorph.useItem(player, pet, form, item, held(item))
        apply(use, item, itemSlot) { next -> invReplace(inv, petSlot, 1, next.obj) }
    }

    private fun ProtectedAccess.metamorphHeld(slot: Int, type: UnpackedObjType) {
        val (pet, form) = pets.ofObj(type.id) ?: return
        val next = metamorph.next(player, pet, form)
        if (next == null) {
            mes("Your ${pet.name.lowercase()} doesn't seem able to change yet.")
            return
        }
        invReplace(inv, slot, 1, next.obj)
    }

    private fun ProtectedAccess.held(item: UnpackedObjType): Int = invTotal(inv, item)

    private fun ProtectedAccess.apply(
        use: ItemUse,
        item: UnpackedObjType,
        slot: Int,
        change: (PetForm) -> Unit,
    ) {
        when (use) {
            ItemUse.Nothing -> mes("Nothing interesting happens.")
            ItemUse.Already -> mes("It's already that type.")
            is ItemUse.NotEnough -> mes(use.message)
            is ItemUse.Change -> {
                if (use.consume > 0) {
                    val removed =
                        if (use.consume == 1) invDel(inv, item, count = 1, slot = slot)
                        else invDel(inv, item, count = use.consume)
                    if (!removed.success) {
                        return
                    }
                }
                use.unlock?.let { metamorph.unlock(player, it) }
                use.form?.let(change)
                use.message?.let { mes(it) }
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        const val RIFT_LOCK_BIT = 1 shl 31

        /** Ticks between two emotes (wiki: "a short cooldown of several seconds"). */
        const val EMOTE_COOLDOWN = 8

        private val EMOTES =
            mapOf(
                "wisp" to
                    listOf({ PetSeqs.wisp_melee }, { PetSeqs.wisp_bind }, { PetSeqs.wisp_special }),
                "butch" to
                    listOf(
                        { PetSeqs.butch_attack },
                        { PetSeqs.butch_respawn },
                        { PetSeqs.butch_strangle },
                        { PetSeqs.butch_dash },
                    ),
                "yami" to
                    listOf(
                        { PetSeqs.yami_click },
                        { PetSeqs.yami_summon },
                        { PetSeqs.yami_spawn },
                        { PetSeqs.yami_idle },
                    ),
            )
    }
}
