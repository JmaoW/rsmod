package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.time.runeday
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.util.UncheckedType

/** Published when [player] fills a slot of the collection log for the first time. */
data class CollectionLogSlotFilled(
    val player: Player,
    /** The obj as the log lists it. */
    val obj: Int,
    /** Every category that lists [obj]; a slot is filled in each. */
    val categories: List<CollectionCategory>,
) : UnboundEvent

/**
 * A player's collection log. It is item-based: a slot fills whenever the player is given the item
 * from a drop or reward, whatever the source. [receive] is the only way in.
 *
 * It is kept in the inventory the client reads it from (`collection_transmit`), one slot for each
 * distinct obj with how many were received as the count, so the engine saves and sends it like any
 * other inventory.
 */
@OptIn(UncheckedType::class)
@Singleton
class CollectionLog
@Inject
constructor(
    private val provider: CollectionCatalogProvider,
    private val objTypes: ObjTypeList,
    private val invTypes: InvTypeList,
    private val latest: CollectionLatest,
    private val eventBus: EventBus,
) {
    val catalog: CollectionCatalog
        get() = provider.catalog

    /** The log inventory of [player]. */
    fun inventory(player: Player): Inventory = player.invMap.getOrPut(invTypes[clog_invs.log])

    /** How many of [obj] [player] has received. */
    fun owned(player: Player, obj: Int): Int {
        val entry = catalog.entryOf(obj) ?: return 0
        return countOf(inventory(player), entry.key)
    }

    /**
     * Gives [player] [count] of [obj] in the log. Notes and placeholders count as the item they
     * stand for. Returns `true` if this filled a slot that was empty, `false` if the item is not in
     * the log or the player already had it. A [quiet] slot does not tell the player.
     */
    fun receive(player: Player, obj: Int, count: Int = 1, quiet: Boolean = false): Boolean {
        if (count <= 0) {
            return false
        }
        val entry = catalog.entryOf(baseObj(obj)) ?: return false
        val inv = inventory(player)
        var free = -1
        for (slot in inv.indices) {
            val existing = inv[slot]
            if (existing == null) {
                if (free == -1) {
                    free = slot
                }
            } else if (existing.id == entry.key) {
                val total = (existing.count.toLong() + count).coerceAtMost(Int.MAX_VALUE.toLong())
                inv[slot] = InvObj(entry.key, total.toInt(), existing.vars)
                return false
            }
        }
        if (free == -1) {
            return false
        }

        inv[free] = InvObj(entry.key, count)
        recount(player)
        latest.push(player, entry.listed, runeday())
        if (!quiet) {
            player.mes(newItemMessage(entry.listed))
        }
        eventBus.publish(CollectionLogSlotFilled(player, entry.listed, entry.categories))
        return true
    }

    /** Recomputes every count varp of [player] from the log, and sends them. */
    fun recount(player: Player) {
        val counts = slotCounts(player)
        var total = 0
        for (tab in counts.indices) {
            total += counts[tab]
            VarPlayerIntMapSetter.set(player, clog_varps.tab_counts[tab], counts[tab])
            VarPlayerIntMapSetter.set(player, clog_varps.tab_maxes[tab], catalog.tabSlots[tab])
        }
        VarPlayerIntMapSetter.set(player, clog_varps.count, total)
        VarPlayerIntMapSetter.set(player, clog_varps.count_highscores, total)
        VarPlayerIntMapSetter.set(player, clog_varps.count_max, catalog.slots)
    }

    /** The slots [player] has filled in each tab. */
    fun slotCounts(player: Player): IntArray {
        val owned = ownedKeys(inventory(player))
        return IntArray(catalog.tabs.size) { tab ->
            catalog.tabs[tab].categories.sumOf { filled(it, owned) }
        }
    }

    /** The slots [player] has filled in [category]. */
    fun filled(player: Player, category: CollectionCategory): Int =
        filled(category, ownedKeys(inventory(player)))

    /** The slots [player] has filled across the categories named by [structs]. */
    fun filled(player: Player, structs: Collection<Int>): Int {
        val owned = ownedKeys(inventory(player))
        return structs.sumOf { struct -> catalog.category(struct)?.let { filled(it, owned) } ?: 0 }
    }

    /** Empties the log of [player] and its counts. For testing commands. */
    fun clear(player: Player) {
        val inv = inventory(player)
        for (slot in inv.indices) {
            inv[slot] = null
        }
        recount(player)
    }

    private fun filled(category: CollectionCategory, owned: Set<Int>): Int =
        category.objs.count { obj -> (catalog.entryOf(obj)?.key ?: obj) in owned }

    private fun ownedKeys(inv: Inventory): Set<Int> {
        val keys = HashSet<Int>()
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            keys += obj.id
        }
        return keys
    }

    private fun countOf(inv: Inventory, key: Int): Int {
        for (slot in inv.indices) {
            val obj = inv[slot] ?: continue
            if (obj.id == key) {
                return obj.count
            }
        }
        return 0
    }

    /** The obj [obj] stands for, as a note or placeholder is the item it was made from. */
    private fun baseObj(obj: Int): Int {
        val type = objTypes.types[obj] ?: return obj
        return objTypes.uncert(objTypes.unplacehold(type)).id
    }

    private fun newItemMessage(obj: Int): String {
        val name = objTypes.types[obj]?.name ?: "item"
        return "New item added to your collection log: <col=ef1020>$name</col>"
    }
}
