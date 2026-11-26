package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.UpdateInventory
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainSidePair
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The raid's storage units (Wiki): every unit in a raid is one unit built up together, from tiny
 * (25 private items, no shared space) through small (Construction 30), medium (60), large (90) and
 * massive (99), 2 mallignum root planks and 150 XP a tier, 100 points a tier. Private / shared
 * space: 30 / 250, 60 / 500, 90 / 1,000, 120 / 1,500.
 *
 * Interfaces (this client's scripts 404, 1589-1624): private 271 (inv `raids_privatestorage`),
 * shared 550 (inv `raids_sharedstorage`), side 551 (the player's inventory; Store or Donate by
 * varbit `raids_usingprivatestorage`). Items are dynamic children of `:items` with ops 1 one, 2
 * five, 3 ten, 4 all, 5 X.
 *
 * The shared storage is the raid's: each viewer's copy of the inv is rewritten from it. Raid-only
 * items in private storage are gone once the raider leaves; the rest waits (the mountain's unit).
 * Kindling can't be stored (since December 2023). Not like OSRS: the build menu is a chat choice
 * and any raid-only item may go in shared storage (the cache's list, enum 1666, isn't read).
 */
@Singleton
class CoxStorageUnits @Inject constructor() {
    private val tiers = HashMap<Int, Int>()
    private val shared = HashMap<Int, LinkedHashMap<Int, Int>>()

    fun tier(raid: CoxRaid): Int = tiers[raid.id] ?: 0

    fun setTier(raid: CoxRaid, tier: Int) {
        tiers[raid.id] = tier
    }

    fun shared(raid: CoxRaid): LinkedHashMap<Int, Int> =
        shared.getOrPut(raid.id) { LinkedHashMap() }

    fun forget(raid: CoxRaid) {
        tiers.remove(raid.id)
        shared.remove(raid.id)
    }

    companion object {
        val PRIVATE_SPACE = listOf(25, 30, 60, 90, 120)
        val SHARED_SPACE = listOf(0, 250, 500, 1000, 1500)
        val LEVELS = listOf(0, 30, 60, 90, 99)

        /** Planks to build up to each tier from tiny (Wiki's table: 2, 4, 6, 8). */
        val PLANKS = listOf(0, 2, 4, 6, 8)
        val NAMES = listOf("Tiny", "Small", "Medium", "Large", "Massive")
        const val XP_PER_TIER = 150.0
        const val POINTS_PER_TIER = 100

        /**
         * The hotspots on the cache's map, by room and version (raid template tiles), and the two
         * in the Great Olm's chamber.
         */
        val HOTSPOTS: Map<CoxRoomType, List<CoordGrid?>> =
            mapOf(
                CoxRoomType.FloorEnd to
                    listOf(CoordGrid(3287, 5168, 0), CoordGrid(3308, 5164, 0), null),
                CoxRoomType.IceDemon to
                    listOf(
                        CoordGrid(3285, 5366, 0),
                        CoordGrid(3310, 5356, 0),
                        CoordGrid(3336, 5362, 0),
                    ),
                CoxRoomType.Thieving to
                    listOf(
                        CoordGrid(3274, 5388, 0),
                        CoordGrid(3316, 5395, 0),
                        CoordGrid(3348, 5400, 0),
                    ),
                CoxRoomType.ResourcesA to
                    listOf(
                        CoordGrid(3275, 5465, 0),
                        CoordGrid(3312, 5463, 0),
                        CoordGrid(3335, 5458, 0),
                    ),
                CoxRoomType.ResourcesB to
                    listOf(
                        CoordGrid(3276, 5465, 0),
                        CoordGrid(3317, 5456, 0),
                        CoordGrid(3342, 5456, 0),
                    ),
            )
        val OLM_HOTSPOTS = listOf(CoordGrid(3228, 5727, 0), CoordGrid(3237, 5727, 0))
    }
}

class CoxStorage
@Inject
constructor(
    private val raids: CoxRaids,
    private val units: CoxStorageUnits,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val invTypes: InvTypeList,
    private val eventBus: EventBus,
    private val xpMods: XpModifiers,
    private val access: ProtectedAccessLauncher,
) : PluginScript() {
    private val tierLocs: List<LocType>
        get() =
            listOf(
                cox_locs.storage_hotspot,
                cox_locs.storage_small,
                cox_locs.storage_medium,
                cox_locs.storage_large,
                cox_locs.storage_massive,
            )

    override fun ScriptContext.startup() {
        // Tiny: Private, Build. Small to large: Shared, Private, Upgrade. Massive: Shared, Private.
        onOpLoc1(cox_locs.storage_hotspot) { openPrivate() }
        onOpLoc2(cox_locs.storage_hotspot) { build() }
        for (unit in
            listOf(
                cox_locs.storage_small,
                cox_locs.storage_medium,
                cox_locs.storage_large,
                cox_locs.storage_massive,
            )) {
            onOpLoc1(unit) { openShared() }
            onOpLoc2(unit) { openPrivate() }
        }
        for (unit in
            listOf(cox_locs.storage_small, cox_locs.storage_medium, cox_locs.storage_large)) {
            onOpLoc3(unit) { build() }
        }
        onOpLoc2(cox_locs.lobby_storage) { openPrivate() }

        onIfModalButton(cox_components.private_items) { withdrawPrivate(it.comsub, it.op) }
        onIfModalButton(cox_components.shared_items) { withdrawShared(it.comsub, it.op) }
        onIfModalButton(cox_components.side_items) { store(it.comsub, it.op) }
        onIfModalButton(cox_components.private_to_shared) { openShared() }
        onIfModalButton(cox_components.shared_to_private) { openPrivate() }
        onIfModalButton(cox_components.private_withdraw_all) { withdrawAllPrivate() }
        onIfModalButton(cox_components.private_bank_all) { depositAllPrivate() }
        onIfModalButton(cox_components.side_dismiss) {
            VarPlayerIntMapSetter.set(player, cox_varbits.shared_warning_dismissed, 1)
        }

        onEvent<CoxEvents.PlayerLeft> { clearRaidItems(player) }
        onEvent<CoxEvents.RaidEnded> { units.forget(raid) }
    }

    private fun privateInv(player: Player): Inventory =
        player.invMap.getOrPut(invTypes[cox_invs.private])

    private fun sharedInv(player: Player): Inventory =
        player.invMap.getOrPut(invTypes[cox_invs.shared])

    // Opening
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.openPrivate() {
        VarPlayerIntMapSetter.set(player, cox_varbits.using_private, 1)
        player.ifOpenMainSidePair(
            cox_interfaces.storage_private,
            cox_interfaces.storage_side,
            -1,
            -1,
            eventBus,
        )
        redrawPrivate(player)
    }

    private fun ProtectedAccess.openShared() {
        val raid = raids.raidOf(player) ?: return
        if (units.tier(raid) == 0) {
            mes("The storage unit must be built before items can be shared.")
            return
        }
        VarPlayerIntMapSetter.set(player, cox_varbits.using_private, 0)
        player.ifOpenMainSidePair(
            cox_interfaces.storage_shared,
            cox_interfaces.storage_side,
            -1,
            -1,
            eventBus,
        )
        redrawShared(player, raid)
    }

    private fun redrawPrivate(player: Player) {
        val inv = privateInv(player)
        UpdateInventory.updateInvFull(player, inv)
        player.ifSetEvents(cox_components.private_items, 0 until inv.size, *ITEM_EVENTS)
        player.ifSetEvents(cox_components.side_items, 0 until player.inv.size, *ITEM_EVENTS)
    }

    private fun redrawShared(player: Player, raid: CoxRaid) {
        val inv = sharedInv(player)
        val contents = units.shared(raid).entries.toList()
        for (slot in inv.indices) {
            inv[slot] =
                contents.getOrNull(slot)?.let { e -> objTypes[e.key]?.let { InvObj(it, e.value) } }
        }
        UpdateInventory.updateInvFull(player, inv)
        player.ifSetEvents(cox_components.shared_items, 0 until inv.size, *ITEM_EVENTS)
        player.ifSetEvents(cox_components.side_items, 0 until player.inv.size, *ITEM_EVENTS)
    }

    private fun redrawSharedForAll(raid: CoxRaid) {
        for (member in raid.active) {
            if (member.player.ui.containsModal(cox_interfaces.storage_shared)) {
                redrawShared(member.player, raid)
            }
        }
    }

    // Moving items
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.amount(op: IfButtonOp, stack: Int): Int =
        when (op) {
            IfButtonOp.Op1 -> 1
            IfButtonOp.Op2 -> 5
            IfButtonOp.Op3 -> 10
            IfButtonOp.Op4 -> stack
            IfButtonOp.Op5 -> countDialog()
            else -> 0
        }.coerceAtMost(stack)

    private suspend fun ProtectedAccess.withdrawPrivate(slot: Int, op: IfButtonOp) {
        val storage = privateInv(player)
        val obj = storage[slot] ?: return
        val type = objTypes[obj]
        val wanted = amount(op, obj.count)
        if (wanted <= 0) return
        val fits = if (type.stackable) wanted else minOf(wanted, player.inv.freeSpace())
        if (fits <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        player.invDel(storage, type, fits, slot = slot)
        player.invAdd(player.inv, type, fits)
        redrawPrivate(player)
    }

    private suspend fun ProtectedAccess.withdrawShared(slot: Int, op: IfButtonOp) {
        val raid = raids.raidOf(player) ?: return
        val contents = units.shared(raid)
        val entry = contents.entries.toList().getOrNull(slot) ?: return
        val type = objTypes[entry.key] ?: return
        val wanted = amount(op, entry.value)
        if (wanted <= 0) return
        val fits = if (type.stackable) wanted else minOf(wanted, player.inv.freeSpace())
        if (fits <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        if (!player.invAdd(player.inv, type, fits).success) return
        val left = entry.value - fits
        if (left <= 0) contents.remove(entry.key) else contents[entry.key] = left
        redrawSharedForAll(raid)
    }

    private suspend fun ProtectedAccess.store(slot: Int, op: IfButtonOp) {
        val obj = player.inv[slot] ?: return
        val type = objTypes[obj]
        if (type.id == objTypes[cox_objs.kindling].id) {
            mes("Kindling can't be stored.")
            return
        }
        val carried = player.inv.count(type)
        val wanted = amount(op, carried)
        if (wanted <= 0) return
        val raid = raids.raidOf(player)
        if (player.vars[cox_varbits.using_private] == 1) {
            val storage = privateInv(player)
            val tier = raid?.let(units::tier) ?: 0
            val space = CoxStorageUnits.PRIVATE_SPACE[tier] - storage.occupiedSpace()
            val moving =
                if (type.stackable || storage.count(type) > 0) wanted else minOf(wanted, space)
            if (moving <= 0 || (space <= 0 && storage.count(type) == 0)) {
                mes("Your private storage is full.")
                return
            }
            player.invDel(player.inv, type, moving)
            player.invAdd(storage, type, moving)
            redrawPrivate(player)
        } else {
            raid ?: return
            if (!CoxRaids.isRaidItem(type)) {
                mes("Only items found in the Chambers may be shared.")
                return
            }
            val contents = units.shared(raid)
            val used = contents.values.sum()
            val space = CoxStorageUnits.SHARED_SPACE[units.tier(raid)] - used
            val moving = minOf(wanted, space)
            if (moving <= 0) {
                mes("The shared storage is full.")
                return
            }
            player.invDel(player.inv, type, moving)
            contents.merge(type.id, moving, Int::plus)
            redrawSharedForAll(raid)
        }
    }

    private fun ProtectedAccess.withdrawAllPrivate() {
        val storage = privateInv(player)
        for (slot in storage.indices) {
            val obj = storage[slot] ?: continue
            val type = objTypes[obj]
            val fits = if (type.stackable) obj.count else minOf(obj.count, player.inv.freeSpace())
            if (fits <= 0) continue
            player.invDel(storage, type, fits, slot = slot)
            player.invAdd(player.inv, type, fits)
        }
        redrawPrivate(player)
    }

    private fun ProtectedAccess.depositAllPrivate() {
        val raid = raids.raidOf(player)
        val storage = privateInv(player)
        val tier = raid?.let(units::tier) ?: 0
        for (slot in player.inv.indices) {
            val obj = player.inv[slot] ?: continue
            val type = objTypes[obj]
            if (type.id == objTypes[cox_objs.kindling].id) continue
            if (
                storage.occupiedSpace() >= CoxStorageUnits.PRIVATE_SPACE[tier] &&
                    storage.count(type) == 0
            )
                break
            player.invDel(player.inv, type, obj.count, slot = slot)
            player.invAdd(storage, type, obj.count)
        }
        redrawPrivate(player)
    }

    private fun clearRaidItems(player: Player) {
        val storage = privateInv(player)
        for (slot in storage.indices) {
            val obj = storage[slot] ?: continue
            if (CoxRaids.isRaidItem(objTypes[obj])) storage[slot] = null
        }
    }

    // Building
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.build() {
        val raid = raids.raidOf(player) ?: return
        val current = units.tier(raid)
        if (current >= 4) {
            mes("The storage unit can't be upgraded any further.")
            return
        }
        val hammer =
            listOf(cox_objs.hammer, cox_objs.dragon_warhammer, cox_objs.elder_maul).any {
                it in player.inv || it in player.worn
            }
        if (!hammer) {
            mes("You need a hammer to build the storage unit.")
            return
        }
        val con = player.statBase(stats.construction)
        val options = (current + 1..4).filter { con >= CoxStorageUnits.LEVELS[it] }
        if (options.isEmpty()) {
            mes(
                "You need a Construction level of ${CoxStorageUnits.LEVELS[current + 1]} to build a bigger storage unit."
            )
            return
        }
        var chosen = -1
        startDialogue {
            val labels =
                options.map {
                    val planks = CoxStorageUnits.PLANKS[it] - CoxStorageUnits.PLANKS[current]
                    "${CoxStorageUnits.NAMES[it]} storage unit ($planks planks)" to it
                }
            chosen =
                when (labels.size) {
                    1 ->
                        choice2(
                            labels[0].first,
                            labels[0].second,
                            "Cancel.",
                            -1,
                            title = "Build what?",
                        )
                    2 ->
                        choice3(
                            labels[0].first,
                            labels[0].second,
                            labels[1].first,
                            labels[1].second,
                            "Cancel.",
                            -1,
                            title = "Build what?",
                        )
                    3 ->
                        choice4(
                            labels[0].first,
                            labels[0].second,
                            labels[1].first,
                            labels[1].second,
                            labels[2].first,
                            labels[2].second,
                            "Cancel.",
                            -1,
                            title = "Build what?",
                        )
                    else ->
                        choice5(
                            labels[0].first,
                            labels[0].second,
                            labels[1].first,
                            labels[1].second,
                            labels[2].first,
                            labels[2].second,
                            labels[3].first,
                            labels[3].second,
                            "Cancel.",
                            -1,
                            title = "Build what?",
                        )
                }
        }
        if (chosen <= current) return
        val planks = CoxStorageUnits.PLANKS[chosen] - CoxStorageUnits.PLANKS[current]
        if (player.inv.count(objTypes[cox_objs.plank]) < planks) {
            mes("You need $planks mallignum root planks to build that.")
            return
        }
        anim(cox_seqs.hammer)
        delay(2)
        invDel(inv, cox_objs.plank, planks)
        statAdvance(
            stats.construction,
            CoxStorageUnits.XP_PER_TIER *
                (chosen - current) *
                xpMods.get(player, stats.construction),
        )
        units.setTier(raid, chosen)
        raids.addPoints(raid, player, CoxStorageUnits.POINTS_PER_TIER * (chosen - current))
        swapUnits(raid, current, chosen)
        mes("You build a ${CoxStorageUnits.NAMES[chosen].lowercase()} storage unit.")
    }

    /** Every unit of the raid becomes the new tier. */
    private fun swapUnits(raid: CoxRaid, from: Int, to: Int) {
        val old = locTypes[tierLocs[from]]
        val new = locTypes[tierLocs[to]]
        val tiles = ArrayList<CoordGrid>()
        for (room in raid.rooms) {
            val template = CoxStorageUnits.HOTSPOTS[room.type]?.getOrNull(room.variant) ?: continue
            tiles += raid.regionOf(room, template)
        }
        for (real in CoxStorageUnits.OLM_HOTSPOTS) tiles += raid.olmTile(real)
        for (tile in tiles) {
            val loc = locRepo.findExact(tile, old) ?: continue
            locRepo.change(loc, new, Int.MAX_VALUE)
        }
    }

    private companion object {
        val ITEM_EVENTS =
            arrayOf(IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5, IfEvent.Op10)
    }
}
