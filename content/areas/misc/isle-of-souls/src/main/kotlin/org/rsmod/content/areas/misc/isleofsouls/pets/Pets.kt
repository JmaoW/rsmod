package org.rsmod.content.areas.misc.isleofsouls.pets

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.refs.invs
import org.rsmod.api.game.process.npc.NpcFollowWalker
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.npc.isSuccess
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.content.skills.agility.scripts.Arena
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

/**
 * The isle's pets: the one way a pet is handed out ([award]), who owns what, and the follower each
 * player has out. Everything follows the OSRS wiki (`Pet`, `Call follower`, `Probita`):
 * - A pet the player does not own appears as their follower ("You have a funny feeling like you're
 *   being followed."); with a follower already out it goes into the inventory ("You feel something
 *   weird sneaking into your backpack."); with no space either it is lost and Probita keeps it.
 * - A pet the player already owns (following, in the inventory or in the bank) is a duplicate: "You
 *   have a funny feeling like you would have been followed...", and nothing is given.
 * - Every pet received is insured: Probita can give it back for free once it is lost.
 * - Only one follower at a time. It walks after its owner, jumps to them when it falls behind (a
 *   teleport, a ladder, an instance), leaves at logout and comes back at login, and is hidden in
 *   the Brimhaven Agility Arena.
 *
 * Deaths on the isle never take items (the engine's death has no item loss), so a pet is never lost
 * by dying: a following pet runs back to its owner and one in the inventory stays there.
 */
@Singleton
class Pets
@Inject
constructor(
    private val npcRegistry: NpcRegistry,
    private val npcTypes: NpcTypeList,
    private val invTypes: InvTypeList,
    private val walker: NpcFollowWalker,
    private val collectionLog: CollectionLog,
    private val announcer: LeagueAnnouncer,
    private val random: GameRandom,
) {
    /** A pet that is out. [npc] is `null` while it is hidden (the Agility Arena). */
    class Follower(val owner: Player, var pet: Pet, var form: PetForm) {
        var npc: Npc? = null
        var nextEmote: Int = 0
        var emoteIndex: Int = 0

        /** The tiles the owner walked off, for [NpcFollowWalker]. */
        val trail = NpcFollowWalker.Trail()
    }

    private val followers = WeakHashMap<Player, Follower>()

    // Catalog lookups
    // ---------------------------------------------------------------------------------------------

    val catalog: List<Pet>
        get() = PetCatalog.pets

    private val byObj: Map<Int, Pair<Pet, PetForm>> by lazy {
        buildMap { catalog.forEach { pet -> pet.forms.forEach { put(it.obj.id, pet to it) } } }
    }

    private val byNpc: Map<Int, Pair<Pet, PetForm>> by lazy {
        buildMap { catalog.forEach { pet -> pet.forms.forEach { put(it.npc.id, pet to it) } } }
    }

    private val byKey: Map<String, Pet> by lazy { catalog.associateBy { it.key } }

    fun pet(key: String): Pet = byKey[key] ?: error("No pet `$key`.")

    fun petOrNull(key: String): Pet? = byKey[key]

    /** The pet and form of an item, if it is one of a pet's looks. */
    fun ofObj(objId: Int): Pair<Pet, PetForm>? = byObj[objId]

    fun ofObj(obj: ObjType): Pair<Pet, PetForm>? = byObj[obj.id]

    fun isPet(objId: Int): Boolean = objId in byObj

    /** The pet and form of a follower npc type. */
    fun ofNpc(npcId: Int): Pair<Pet, PetForm>? = byNpc[npcId]

    // Odds
    // ---------------------------------------------------------------------------------------------

    /** The event's pet odds factor (`petOdds` in event-settings.json; 1 = the game's own odds). */
    val oddsMultiplier: Double
        get() = EventSettings.current.petOdds

    /** One roll at a pet that the game gives with a chance of 1 in [oneIn], scaled by the event. */
    fun roll(oneIn: Double): Boolean {
        if (oneIn <= 0.0) {
            return true
        }
        return random.randomDouble() < minOf(1.0, oddsMultiplier / oneIn)
    }

    /** As [roll], for a whole "1 in [oneIn]" (rolled as one pick of that many, like the game). */
    fun roll(oneIn: Int): Boolean {
        val scaled = Math.round(oneIn / oddsMultiplier).toInt().coerceAtLeast(1)
        return random.of(scaled) == 0
    }

    // Ownership
    // ---------------------------------------------------------------------------------------------

    fun bank(player: Player): Inventory = player.invMap.getOrPut(invTypes[invs.bank])

    /** Where [player] keeps [pet], as the insurance screen codes it: 1 bank, 2 inventory, 3 out. */
    fun location(player: Player, pet: Pet): Int {
        if (followers[player]?.pet == pet) {
            return LOCATION_FOLLOWER
        }
        if (pet.forms.any { it.obj in player.inv || it.obj in player.worn }) {
            return LOCATION_INVENTORY
        }
        if (pet.forms.any { it.obj in bank(player) }) {
            return LOCATION_BANK
        }
        return 0
    }

    fun owns(player: Player, pet: Pet): Boolean = location(player, pet) != 0

    fun isInsured(player: Player, pet: Pet): Boolean {
        val (varp, bit) = insuredBit(pet)
        return player.vars[varp] and (1 shl bit) != 0
    }

    fun insure(player: Player, pet: Pet) {
        val (varp, bit) = insuredBit(pet)
        VarPlayerIntMapSetter.set(player, varp, player.vars[varp] or (1 shl bit))
    }

    /** Pets [player] has received and does not have anywhere: Probita can hand these back. */
    fun reclaimable(player: Player): List<Pet> =
        catalog.filter { isInsured(player, it) && !owns(player, it) }

    /**
     * Probita hands [pet] back to [player] (into the inventory, in its usual look). Returns `false`
     * when it is not theirs to reclaim or there is no inventory space.
     */
    fun reclaim(player: Player, pet: Pet): Boolean {
        if (!isInsured(player, pet) || owns(player, pet)) {
            return false
        }
        return player.invAdd(player.inv, pet.base.obj, 1).success
    }

    private fun insuredBit(pet: Pet) =
        when {
            pet.bit < 31 -> pet_varps.insured[0] to pet.bit
            pet.bit < 62 -> pet_varps.insured[1] to pet.bit - 31
            else -> pet_varps.insured[2] to pet.bit - 62
        }

    // Handing out a pet
    // ---------------------------------------------------------------------------------------------

    /**
     * Gives [pet] to [player] as [form] (its usual look unless the source decides, such as the rock
     * golem's ore). [log] adds it to the collection log; drop-table pets are logged by the drop
     * itself, so their caller passes `false`. Announces it to the world when the event says so.
     */
    fun award(player: Player, pet: Pet, form: PetForm = pet.base, log: Boolean = true): PetAward {
        if (log) {
            collectionLog.receive(player, form.obj.id, 1)
        }
        if (owns(player, pet) && pet.key !in MAIN_DROP_PETS) {
            player.mes(red("You have a funny feeling like you would have been followed..."))
            announcer.petDuplicate(player, form.obj)
            return PetAward.Duplicate
        }
        insure(player, pet)
        if (followers[player] == null && canFollow(player) && spawn(player, pet, form)) {
            player.mes(red("You have a funny feeling like you're being followed."))
            announcer.pet(player, form.obj)
            return PetAward.Followed
        }
        if (player.invAdd(player.inv, form.obj, 1).success) {
            player.mes(red("You feel something weird sneaking into your backpack."))
            announcer.petBackpack(player, form.obj)
            return PetAward.Backpack
        }
        player.mes(red("You have a funny feeling like you would have been followed..."))
        announcer.petLost(player, form.obj)
        return PetAward.Lost
    }

    /** Unlocks a metamorphosis for [player] for good (an item used, a feat). */
    fun unlock(player: Player, unlock: PetUnlock) {
        VarPlayerIntMapSetter.set(
            player,
            pet_varps.unlocks,
            player.vars[pet_varps.unlocks] or (1 shl unlock.bit),
        )
    }

    // Followers
    // ---------------------------------------------------------------------------------------------

    fun followerOf(player: Player): Follower? = followers[player]

    fun hasFollower(player: Player): Boolean = followers[player] != null

    /** The follower whose npc is [npc], and its owner. */
    fun followerOfNpc(npc: Npc): Follower? = followers.values.firstOrNull { it.npc === npc }

    /** Whether a pet may walk beside [player] where they are now (not in the Agility Arena). */
    fun canFollow(player: Player): Boolean = !hiddenAt(player.coords)

    fun hiddenAt(coords: CoordGrid): Boolean = Arena.inArena(coords)

    /**
     * Brings [pet] out as [player]'s follower. Returns `false` if there was no room for it. Any
     * follower already out is put away first (the caller has checked that it may be).
     */
    fun spawn(player: Player, pet: Pet, form: PetForm): Boolean {
        dismiss(player)
        val follower = Follower(player, pet, form)
        followers[player] = follower
        VarPlayerIntMapSetter.set(player, pet_varps.follower, form.obj.id)
        if (!canFollow(player)) {
            hideNpc(follower)
            return true
        }
        if (!showNpc(follower)) {
            followers.remove(player)
            VarPlayerIntMapSetter.set(player, pet_varps.follower, 0)
            return false
        }
        return true
    }

    /** Puts [player]'s follower away (logout, pick-up). Its saved state is cleared if [forget]. */
    fun dismiss(player: Player, forget: Boolean = true) {
        val follower = followers.remove(player) ?: return
        removeNpc(follower)
        if (forget) {
            VarPlayerIntMapSetter.set(player, pet_varps.follower, 0)
        }
        sendFollowerIndex(player, null)
    }

    /**
     * Changes [player]'s follower into [form] (a metamorphosis). Only the look changes: the same
     * npc is transmogged, so it keeps its tile, its walk, the way it faces and its trail.
     */
    fun transform(player: Player, form: PetForm) {
        val follower = followers[player] ?: return
        follower.form = form
        VarPlayerIntMapSetter.set(player, pet_varps.follower, form.obj.id)
        val npc = follower.npc ?: return
        val type = npcTypes[form.npc]
        if (type.size != npc.type.size) {
            // A look of another size cannot be a transmog (the npc's footprint is fixed): bring the
            // new look out on the same tile instead.
            val coords = npc.coords
            removeNpc(follower)
            showNpc(follower, coords)
            return
        }
        if (npc.type.isType(type)) {
            npc.resetTransmog()
        } else {
            npc.transmog(type, Int.MAX_VALUE)
        }
    }

    /** Brings back the follower saved on [player] (login). */
    fun restore(player: Player) {
        val objId = player.vars[pet_varps.follower]
        if (objId == 0) {
            return
        }
        val (pet, form) =
            ofObj(objId)
                ?: run {
                    VarPlayerIntMapSetter.set(player, pet_varps.follower, 0)
                    return
                }
        if (!spawn(player, pet, form)) {
            // No room for it: keep it saved, the next login tries again.
            VarPlayerIntMapSetter.set(player, pet_varps.follower, objId)
        }
    }

    /**
     * The Call follower button. `true` when the follower came; otherwise [message] is why not.
     * Wiki: it only comes when 5 or more tiles away (or out of sight behind a wall).
     */
    fun call(player: Player): String? {
        val follower = followers[player] ?: return "You do not have a follower."
        val npc = follower.npc ?: return "You do not have a follower."
        if (
            npc.coords.level == player.coords.level && npc.isWithinDistance(player, CALL_RANGE - 1)
        ) {
            return "Your follower is already close enough."
        }
        jumpBeside(follower, npc)
        return null
    }

    /** Puts the pet on a free tile next to its owner (on theirs if all four are blocked). */
    private fun jumpBeside(follower: Follower, npc: Npc) {
        walker.jumpBeside(npc, follower.owner, follower.trail)
        follow(npc, follower.owner)
    }

    /**
     * Brings the pet's npc out: on [coords], or by default on a free tile beside its owner (a
     * dropped, new or returning pet appears next to them).
     */
    private fun showNpc(follower: Follower, coords: CoordGrid? = null): Boolean {
        val owner = follower.owner
        val type = npcTypes[follower.form.npc]
        val npc = Npc(type, coords ?: walker.tileBeside(owner, type.size))
        follow(npc, owner)
        if (!npcRegistry.add(npc).isSuccess()) {
            return false
        }
        follower.npc = npc
        follower.trail.clear()
        sendFollowerIndex(owner, npc)
        return true
    }

    private fun hideNpc(follower: Follower) {
        removeNpc(follower)
        sendFollowerIndex(follower.owner, null)
    }

    private fun removeNpc(follower: Follower) {
        val npc = follower.npc ?: return
        follower.npc = null
        if (npc.slotId != PathingEntity.INVALID_SLOT) {
            npcRegistry.del(npc)
        }
    }

    /**
     * The pet faces its owner the whole time, like a target. Its steps are taken by
     * [NpcFollowWalker] in [lateCycle], not by the engine's npc modes.
     */
    private fun follow(npc: Npc, owner: Player) {
        walker.claim(npc)
        npc.facePlayer(owner)
    }

    private fun sendFollowerIndex(player: Player, npc: Npc?) {
        VarPlayerIntMapSetter.set(player, pet_varps.follower_npc, npc?.slotId ?: -1)
    }

    /**
     * Once a tick, after players have moved (`GameLifecycle.LateCycle`, through
     * [NpcFollowWalker.lateCycle], which [PetScript] registers it with): keeps every follower
     * behind its owner, or hidden where pets may not go. Moving after the owner is what keeps the
     * pet off their tile: it sees where they ended up this tick, not where they were.
     */
    fun lateCycle() {
        if (followers.isEmpty()) {
            return
        }
        for (follower in followers.values.toList()) {
            tick(follower)
        }
    }

    private fun tick(follower: Follower) {
        val owner = follower.owner
        if (followers[owner] !== follower) {
            return
        }
        if (owner.slotId == PathingEntity.INVALID_SLOT) {
            dismiss(owner, forget = false)
            return
        }
        val npc = follower.npc
        when {
            !canFollow(owner) && npc != null -> {
                hideNpc(follower)
                owner.mes(red("Your pet has been hidden as you enter this area."))
            }
            canFollow(owner) && npc == null -> showNpc(follower)
            npc != null && npc.slotId == PathingEntity.INVALID_SLOT -> showNpc(follower)
            npc != null -> keepUp(follower, npc)
        }
    }

    private fun keepUp(follower: Follower, npc: Npc) {
        follow(npc, follower.owner)
        walker.walk(npc, follower.owner, follower.trail, LEASH_RANGE)
    }

    companion object {
        const val LOCATION_BANK = 1
        const val LOCATION_INVENTORY = 2
        const val LOCATION_FOLLOWER = 3

        /** Call follower only works from this many tiles away (wiki). */
        const val CALL_RANGE = 5

        /** A follower further away than this jumps to its owner. */
        const val LEASH_RANGE = 12

        /**
         * Pets that are a boss's main drop rather than a tertiary roll: a second one is really
         * given (wiki: the abyssal orphan and Smol Heredit).
         */
        val MAIN_DROP_PETS = setOf("abyssal_orphan", "smol_heredit")

        fun red(text: String): String = "<col=ff0000>$text</col>"
    }
}
