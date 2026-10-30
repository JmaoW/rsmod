@file:OptIn(UncheckedType::class)

package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenSide
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.stat.StatType
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.map.CoordGrid

/** The raid's own supplies: what the helpful spirit hands out, and the honey locusts. */
object ToaSupplyObjs : ObjReferences() {
    val bag = find("toa_midraidloot_bag")
    val nectar = (4 downTo 1).map { find("toa_supply_heal_$it") }
    val tears = (4 downTo 1).map { find("toa_supply_prayer_$it") }
    val ambrosia = (2 downTo 1).map { find("toa_supply_panicheal_$it") }
    val salts = (2 downTo 1).map { find("toa_supply_stats_$it") }
    val scarab = (2 downTo 1).map { find("toa_supply_prayer_overtime_$it") }
    val adrenaline = (2 downTo 1).map { find("toa_supply_energy_$it") }
    val silk = (2 downTo 1).map { find("toa_supply_heal_overtime_$it") }
    val locust = find("toa_honey_locust")

    /** Each supply's doses, most first. */
    val chains: List<List<ObjType>> =
        listOf(nectar, tears, ambrosia, salts, scarab, adrenaline, silk)
}

object ToaSupplyInterfaces : InterfaceReferences() {
    /** The helpful spirit's three bundles: Life, Chaos and Power. */
    val bundles = find("toa_midraid_loot")
    val bag = find("toa_midraidloot_bag")
}

object ToaSupplyComponents : ComponentReferences() {
    val choose =
        listOf(
            find("toa_midraid_loot:select_button_1"),
            find("toa_midraid_loot:select_button_2"),
            find("toa_midraid_loot:select_button_3"),
        )
    val bundle_items =
        listOf(
            find("toa_midraid_loot:bundle_1"),
            find("toa_midraid_loot:bundle_2"),
            find("toa_midraid_loot:bundle_3"),
        )
    val bag_items = find("toa_midraidloot_bag:items")
}

object ToaSupplyInvs : InvReferences() {
    val bundles =
        listOf(
            find("toa_midraidloot_bundle1"),
            find("toa_midraidloot_bundle2"),
            find("toa_midraidloot_bundle3"),
        )
    val bag = find("toa_midraidloot_bag")
}

object ToaSupplyNpcs : NpcReferences() {
    /** Akila, the helpful spirit: "Claim". */
    val spirit = find("toa_midraidloot_trader")
}

object ToaSupplySeqs : SeqReferences() {
    val consume = find("human_eat")
}

/**
 * Special attack costs in the raid: Overly Draining makes every one cost 100%, and liquid
 * adrenaline halves the cost while it lasts (wiki). Kept apart from the injected classes because
 * the engine's rule is registered once per run.
 */
object ToaSpecialCosts {
    val overlyDraining: MutableMap<Player, Boolean> = WeakHashMap()
    val adrenalineUntil: MutableMap<Player, Int> = WeakHashMap()
    var clock: () -> Int = { 0 }

    fun cost(player: Player, cost: Int): Int {
        var result = cost
        if (overlyDraining[player] == true) {
            result = FULL
        }
        val until = adrenalineUntil[player]
        if (until != null && clock() < until) {
            result /= 2
        }
        return result
    }

    const val FULL = 1000
}

/**
 * The helpful spirit, the supplies bag and the supplies themselves.
 *
 * The spirit comes to the Nexus after the second and fourth paths and offers three bundles once to
 * each player (wiki): Life (5 nectar, 5 tears, 1-3 silk dressings, 1-3 blessed crystal scarabs and
 * 1-2 ambrosia; no dressings On a Diet, with two more scarabs and an ambrosia instead), Chaos (1-8
 * nectar, 0-6 tears, 0-2 salts and a rare ambrosia and liquid adrenaline, 1 in 8 each from TSPS)
 * and Power (2 salts, 1 adrenaline). The help invocations scale the amounts to 66%, 33% or 10%,
 * keeping one of each. The chosen bundle goes into the supplies bag (Open, Withdraw 1, Withdraw
 * All, Resupply).
 *
 * The supplies only work inside the raid, and every one of them is taken away on leaving it.
 */
@Singleton
class ToaSupplies
@Inject
constructor(
    private val invTypes: InvTypeList,
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val random: GameRandom,
    private val eventBus: EventBus,
) {
    /** One raid's current offer: the spirit and the three bundles. */
    private class Offer(val npc: Npc, val bundles: List<List<InvObj>>)

    private val offers = HashMap<Int, Offer>()

    private class Effects {
        var salts = 0
        var scarab = 0
        var silk = 0
    }

    private val effects = HashMap<Int, Effects>()

    /** When each player may next drink or eat a raid supply. */
    private val ready = WeakHashMap<Player, Int>()

    // The spirit
    // ----------------------------------------------------------------------------------------

    /** After the second and fourth paths the spirit comes with supplies for everyone. */
    fun pathCompleted(raid: ToaRaid, now: Int) {
        val done = raid.completed.size
        if (done != 2 && done != 4) {
            return
        }
        offers.remove(raid.id)?.let { npcRepo.del(it.npc, Int.MAX_VALUE) }
        val npc = Npc(npcTypes[ToaSupplyNpcs.spirit], raid.regionOf(SPIRIT_TILE))
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        offers[raid.id] = Offer(npc, rollBundles(raid.settings))
        for (member in raid.active) {
            member.canClaimSupplies = true
            member.player.mes("<col=0000b2>A helpful spirit has arrived with some supplies.</col>")
        }
    }

    fun raidEnded(raid: ToaRaid) {
        offers.remove(raid.id)?.let { npcRepo.del(it.npc, Int.MAX_VALUE) }
    }

    /** The three bundles for this offer, shrunk by the help invocations (at least one of each). */
    fun rollBundles(settings: InvocationSet): List<List<InvObj>> {
        val percent = settings.supplyPercent
        val diet = settings.isActive(Inv.ON_A_DIET)
        fun scaled(amount: Int, minimum: Int = 1): Int =
            if (amount <= 0) 0 else maxOf(minimum, amount * percent / 100)
        val life = ArrayList<InvObj>()
        life += InvObj(ToaSupplyObjs.nectar[0], scaled(5))
        life += InvObj(ToaSupplyObjs.tears[0], scaled(5))
        life += InvObj(ToaSupplyObjs.ambrosia[0], scaled(random.of(1, 2) + if (diet) 1 else 0))
        life += InvObj(ToaSupplyObjs.scarab[0], scaled(random.of(1, 3) + if (diet) 2 else 0))
        if (!diet) {
            life += InvObj(ToaSupplyObjs.silk[0], scaled(random.of(1, 3)))
        }
        val rare = { if (random.of(CHAOS_RARE) == 0) scaled(1) else 0 }
        val chaos =
            listOf(
                InvObj(ToaSupplyObjs.nectar[0], scaled(random.of(1, 8))),
                InvObj(ToaSupplyObjs.tears[0], scaled(random.of(0, 6))),
                InvObj(ToaSupplyObjs.salts[0], scaled(random.of(0, 2))),
                InvObj(ToaSupplyObjs.ambrosia[0], rare()),
                InvObj(ToaSupplyObjs.adrenaline[0], rare()),
            )
        val power =
            listOf(
                InvObj(ToaSupplyObjs.salts[0], scaled(2)),
                InvObj(ToaSupplyObjs.adrenaline[0], scaled(1)),
            )
        return listOf(life, chaos, power).map { bundle -> bundle.filter { it.count > 0 } }
    }

    fun isSpirit(raid: ToaRaid, npc: Npc): Boolean = offers[raid.id]?.npc === npc

    /** "Claim": shows the three bundles. */
    fun claim(raid: ToaRaid, player: Player) {
        val member = raid.member(player) ?: return
        val offer = offers[raid.id]
        if (offer == null || !member.canClaimSupplies) {
            player.mes(
                "The spirit gives you a strange look. You've clearly claimed all you can for now."
            )
            return
        }
        for ((index, bundle) in offer.bundles.withIndex()) {
            val inv = inventory(player, ToaSupplyInvs.bundles[index])
            inv.objs.fill(null)
            for ((slot, obj) in bundle.withIndex()) {
                inv[slot] = obj
            }
            UpdateInventory.updateInvFull(player, inv)
        }
        player.ifOpenMainModal(ToaSupplyInterfaces.bundles, eventBus)
        for ((index, button) in ToaSupplyComponents.choose.withIndex()) {
            player.ifSetEvents(button, 0..9, IfEvent.Op1)
            player.ifSetEvents(ToaSupplyComponents.bundle_items[index], 2..8, IfEvent.Op1)
        }
    }

    /** "Choose": the bundle goes into the supplies bag (a new bag if there is none). */
    fun choose(raid: ToaRaid, player: Player, index: Int) {
        val member = raid.member(player) ?: return
        val offer = offers[raid.id] ?: return
        if (!member.canClaimSupplies) {
            return
        }
        val items =
            offer.bundles.getOrNull(index)?.flatMap { obj -> List(obj.count) { InvObj(obj.id, 1) } }
                ?: return
        val bag = inventory(player, ToaSupplyInvs.bag)
        val hasBag = player.inv.any { it?.id == ToaSupplyObjs.bag.id }
        if (!hasBag) {
            if (!player.inv.hasFreeSpace()) {
                player.mes("You need at least one inventory spot for a supply bag.")
                return
            }
            bag.objs.fill(null)
            player.invAdd(player.inv, ToaSupplyObjs.bag)
        }
        if (bag.freeSpace() < items.size) {
            player.mes("You need more space in your supply bag for additional supplies.")
            return
        }
        for (obj in items) {
            val slot = bag.objs.indexOfFirst { it == null }
            bag[slot] = obj
        }
        member.canClaimSupplies = false
        player.ifCloseModals(eventBus)
    }

    // The bag
    // ------------------------------------------------------------------------------------------

    fun bag(player: Player): Inventory = inventory(player, ToaSupplyInvs.bag)

    private fun inventory(player: Player, type: org.rsmod.game.type.inv.InvType): Inventory =
        player.invMap.getOrPut(invTypes[type])

    /** "Open": the bag's own panel beside the inventory. */
    fun openBag(player: Player) {
        val bag = bag(player)
        UpdateInventory.updateInvFull(player, bag)
        player.ifOpenSide(ToaSupplyInterfaces.bag, eventBus)
        player.ifSetEvents(
            ToaSupplyComponents.bag_items,
            0 until bag.size,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op10,
        )
    }

    /**
     * Takes up to [count] of the supply in [slot] of the bag, or of the first ones with no slot.
     */
    fun withdraw(player: Player, slot: Int?, count: Int) {
        val bag = bag(player)
        val id = if (slot != null) bag[slot]?.id ?: return else null
        var taken = 0
        while (taken < count) {
            val from =
                when {
                    id == null -> bag.objs.indexOfFirst { it != null }
                    slot != null && bag[slot]?.id == id -> slot
                    else -> bag.objs.indexOfFirst { it?.id == id }
                }
            if (from < 0) {
                break
            }
            val obj = bag[from] ?: break
            val last = bag.objs.count { it != null } == 1
            if (!player.inv.hasFreeSpace() && !last) {
                player.mes(
                    "You do not have enough space in your inventory to withdraw your supplies."
                )
                break
            }
            bag[from] = null
            if (last) {
                player.invDel(player.inv, ToaSupplyObjs.bag, 1, strict = false)
            }
            player.invAdd(player.inv, obj.id, 1)
            taken++
        }
        afterChange(player)
    }

    fun drop(player: Player, slot: Int) {
        val bag = bag(player)
        bag[slot] ?: return
        bag[slot] = null
        afterChange(player)
    }

    /** A supply used on the bag goes back in (not honey locusts). */
    fun store(player: Player, obj: ObjType) {
        if (ToaSupplyObjs.chains.none { chain -> chain.any { it.id == obj.id } }) {
            return
        }
        val bag = bag(player)
        val slot = bag.objs.indexOfFirst { it == null }
        if (slot < 0) {
            player.mes("Your supply bag is full.")
            return
        }
        if (player.invDel(player.inv, obj, 1).failure) {
            return
        }
        bag[slot] = InvObj(obj.id, 1)
        afterChange(player)
    }

    /**
     * "Resupply" (wiki): tops up partly used supplies in the inventory from the bag, e.g. smelling
     * salts (1) becomes (2); it never adds new items.
     */
    fun resupply(player: Player) {
        val bag = bag(player)
        var changed = false
        for (slot in player.inv.indices) {
            val held = player.inv[slot] ?: continue
            val chain =
                ToaSupplyObjs.chains.firstOrNull { c -> c.any { it.id == held.id } } ?: continue
            var have = chain.size - chain.indexOfFirst { it.id == held.id }
            if (have >= chain.size) {
                continue
            }
            val before = have
            for (bagSlot in bag.indices) {
                if (have >= chain.size) {
                    break
                }
                val stored = bag[bagSlot] ?: continue
                val index = chain.indexOfFirst { it.id == stored.id }
                if (index < 0) {
                    continue
                }
                val storedDoses = chain.size - index
                val moved = minOf(chain.size - have, storedDoses)
                have += moved
                val left = storedDoses - moved
                bag[bagSlot] = if (left > 0) InvObj(chain[chain.size - left].id, 1) else null
            }
            if (have != before) {
                player.inv[slot] = InvObj(chain[chain.size - have].id, 1)
                changed = true
            }
        }
        if (changed) {
            afterChange(player)
        }
    }

    private fun afterChange(player: Player) {
        val bag = bag(player)
        if (bag.isEmpty()) {
            player.invDel(player.inv, ToaSupplyObjs.bag, 1, strict = false)
            player.mes("The bag disintegrates.")
            player.ifCloseModals(eventBus)
        }
        UpdateInventory.updateInvFull(player, bag)
    }

    // Using a supply
    // ------------------------------------------------------------------------------------------

    /** Whether [player] may use a supply now; one every 3 ticks (as a potion). */
    fun takeTurn(player: Player, now: Int): Boolean {
        if (now < (ready[player] ?: Int.MIN_VALUE)) {
            return false
        }
        ready[player] = now + DELAY
        return true
    }

    /** Tears of Elidinis: stats and prayer back, and some prayer to allies beside (wiki). */
    fun tears(raid: ToaRaid, player: Player) {
        for (stat in RESTORED) {
            heal(player, stat, 3, 25)
        }
        heal(player, stats.prayer, 10, 25)
        val at = player.coords
        for (member in raid.active) {
            val other = member.player
            if (other === player || member.ghost) {
                continue
            }
            if (
                maxOf(
                    kotlin.math.abs(other.coords.x - at.x),
                    kotlin.math.abs(other.coords.z - at.z),
                ) > 1
            ) {
                continue
            }
            if (other.stat(stats.prayer) >= other.statBase(stats.prayer)) {
                continue
            }
            heal(other, stats.prayer, 10, 10)
            other.mes("${player.displayName} has restored some of your prayer points.")
        }
    }

    /** Smelling salts: an overload-like boost every 15 seconds for 8 minutes (wiki). */
    fun salts(player: Player) {
        effectsOf(player).salts = SALTS_TICKS
        boostCombat(player)
        player.runEnergy = (player.runEnergy + RUN_QUARTER).coerceAtMost(MAX_RUN)
        player.mes("You crush the salts. Your heart rate increases.")
    }

    private fun boostCombat(player: Player) {
        for (stat in COMBAT) {
            // floor(base * 16 / 100) + 11, never lowering a stronger boost.
            val target = player.statBase(stat) + player.statBase(stat) * 16 / 100 + 11
            val gain = target - player.stat(stat)
            if (gain > 0) {
                player.statAdd(stat, gain, 0)
            }
        }
    }

    /** Blessed crystal scarab: 8 prayer every 4 ticks, 9 times (wiki). */
    fun scarab(player: Player) {
        effectsOf(player).scarab = SCARAB_TICKS
        player.mes("You crack the blessed crystal scarab.")
    }

    /** Liquid adrenaline: special attacks cost half for 150 seconds (wiki). */
    fun adrenaline(player: Player, now: Int) {
        ToaSpecialCosts.adrenalineUntil[player] = now + ADRENALINE_TICKS
        player.mes(
            "You drink some of the potion, reducing the energy cost of your special attacks."
        )
    }

    /** Silk dressing: 5 every 5 ticks, 20 times, up to 5 over the maximum (wiki). */
    fun silk(player: Player) {
        effectsOf(player).silk = SILK_TICKS
        player.mes("You apply the silk dressing.")
    }

    /**
     * Honey locust: 20 hitpoints (overhealing), a prayer potion's worth of prayer and a super
     * restore's worth of stats (wiki). In Expert, more than were handed out on the last failure fly
     * away.
     */
    fun locust(raid: ToaRaid, player: Player, healed: Int) {
        val max = player.baseHitpointsLvl
        val cap = max + LOCUST_HEAL
        val more = minOf(LOCUST_HEAL - healed, cap - player.hitpoints)
        if (more > 0) {
            player.statAdd(stats.hitpoints, more, 0)
        }
        heal(player, stats.prayer, 7, 25)
        for (stat in RESTORED) {
            heal(player, stat, 8, 25)
        }
        if (raid.mode == ToaMode.Expert) {
            val held =
                player.inv
                    .filterNotNull()
                    .filter { it.id == ToaSupplyObjs.locust.id }
                    .sumOf { it.count }
            if (held > locustsFor(ToaMode.Expert, wardens = false)) {
                player.invDel(player.inv, ToaSupplyObjs.locust, held - 1, strict = false)
                player.mes(
                    "Some of your honey locusts fly away under the immense pressure of the tomb."
                )
            }
        }
    }

    private fun heal(player: Player, stat: StatType, constant: Int, percent: Int) {
        if (player.stat(stat) < player.statBase(stat)) {
            player.statHeal(stat, constant, percent)
        }
    }

    private fun effectsOf(player: Player): Effects =
        effects.getOrPut(player.accountId) { Effects() }

    /** The lasting effects, every tick, for a player still in a raid. */
    fun tick(player: Player, now: Int) {
        val state = effects[player.accountId] ?: return
        if (state.salts > 0) {
            state.salts--
            if (state.salts == SALTS_REFRESH) {
                player.mes("<col=ef1020>Your smelling salts effect has almost run out!</col>")
            }
            if (state.salts == 0) {
                for (stat in COMBAT) {
                    if (player.stat(stat) > player.statBase(stat)) {
                        player.statSub(stat, player.stat(stat) - player.statBase(stat), 0)
                    }
                }
                player.mes("<col=ff3045>The boost from the smelling salts has worn off!</col>")
            } else if (state.salts % SALTS_REFRESH == 0) {
                boostCombat(player)
                player.runEnergy = (player.runEnergy + RUN_QUARTER).coerceAtMost(MAX_RUN)
            }
        }
        if (state.scarab > 0) {
            state.scarab--
            if (state.scarab % SCARAB_INTERVAL == 0) {
                val prayer = player.stat(stats.prayer)
                val gain = minOf(SCARAB_PRAYER, player.statBase(stats.prayer) - prayer)
                if (gain > 0) {
                    player.statHeal(stats.prayer, gain, 0)
                }
            }
        }
        if (state.silk > 0) {
            state.silk--
            if (state.silk % SILK_INTERVAL == 0) {
                val cap = player.baseHitpointsLvl + SILK_OVERHEAL
                if (player.hitpoints < cap) {
                    player.statAdd(stats.hitpoints, minOf(SILK_HEAL, cap - player.hitpoints), 0)
                }
            }
        }
        val until = ToaSpecialCosts.adrenalineUntil[player]
        if (until != null && now >= until) {
            ToaSpecialCosts.adrenalineUntil.remove(player)
            player.mes(
                "<col=ff3045>The energy cost of your special attacks has returned to normal.</col>"
            )
        }
        if (state.salts == 0 && state.scarab == 0 && state.silk == 0) {
            effects.remove(player.accountId)
        }
    }

    // Leaving
    // ------------------------------------------------------------------------------------------

    /**
     * Everything the raid gave is taken away when a player leaves it, or logs in outside one: the
     * supplies, the bag and its contents, honey locusts and the rooms' own tools. Lasting effects
     * end (a salts boost falls back to the base).
     */
    fun strip(player: Player) {
        val state = effects.remove(player.accountId)
        if (state != null && state.salts > 0) {
            for (stat in COMBAT) {
                if (player.stat(stat) > player.statBase(stat)) {
                    player.statSub(stat, player.stat(stat) - player.statBase(stat), 0)
                }
            }
        }
        ToaSpecialCosts.adrenalineUntil.remove(player)
        ToaSpecialCosts.overlyDraining.remove(player)
        bag(player).objs.fill(null)
        val raidItems = HashSet<Int>()
        ToaSupplyObjs.chains.forEach { chain -> chain.forEach { raidItems += it.id } }
        raidItems += ToaSupplyObjs.bag.id
        raidItems += ToaSupplyObjs.locust.id
        raidItems += apmeken_objs.potion.id
        raidItems += crondis_objs.container.id
        raidItems += het_objs.mirror.id
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            if (obj.id in raidItems) {
                player.invDel(player.inv, obj.id, obj.count, slot = slot, strict = false)
            }
        }
    }

    // Honey locusts
    // ---------------------------------------------------------------------------------------

    /**
     * A failed room hands out honey locusts as a catch-up (none On a Diet); the final room gives
     * more. The amounts are guesses: the wiki only says they vary by room, and that Expert gives
     * two.
     */
    fun locustsFor(mode: ToaMode, wardens: Boolean): Int =
        when {
            mode == ToaMode.Expert -> if (wardens) 3 else 2
            wardens -> 5
            else -> 3
        }

    fun roomFailed(raid: ToaRaid, room: ToaRoom) {
        if (raid.settings.isActive(Inv.ON_A_DIET)) {
            return
        }
        val wardens = room == ToaRoom.WardensFirst || room == ToaRoom.WardensFinal
        val amount = locustsFor(raid.mode, wardens)
        for (member in raid.active) {
            if (member.room == room) {
                member.player.invAdd(member.player.inv, ToaSupplyObjs.locust, amount)
            }
        }
    }

    companion object {
        val SPIRIT_TILE = CoordGrid(3548, 5154, 0)
        const val CHAOS_RARE = 8
        const val DELAY = 3
        const val SALTS_TICKS = 800
        const val SALTS_REFRESH = 25
        const val SCARAB_TICKS = 36
        const val SCARAB_INTERVAL = 4
        const val SCARAB_PRAYER = 8
        const val SILK_TICKS = 100
        const val SILK_INTERVAL = 5
        const val SILK_HEAL = 5
        const val SILK_OVERHEAL = 5
        const val ADRENALINE_TICKS = 250
        const val LOCUST_HEAL = 20
        const val RUN_QUARTER = 2_500
        const val MAX_RUN = 10_000

        private val COMBAT: List<StatType> =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)

        /** Every stat a restore brings back, which is all but Hitpoints and Prayer. */
        private val RESTORED: List<StatType> =
            listOf(
                stats.attack,
                stats.defence,
                stats.strength,
                stats.ranged,
                stats.magic,
                stats.cooking,
                stats.woodcutting,
                stats.fletching,
                stats.fishing,
                stats.firemaking,
                stats.crafting,
                stats.smithing,
                stats.mining,
                stats.herblore,
                stats.agility,
                stats.thieving,
                stats.slayer,
                stats.farming,
                stats.runecrafting,
                stats.hunter,
                stats.construction,
            )
    }
}
