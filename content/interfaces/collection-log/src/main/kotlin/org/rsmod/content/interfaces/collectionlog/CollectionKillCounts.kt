package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varp.VarpType

/**
 * The kill counts (and completion counts) the collection log shows under a category. Each line the
 * client can show has its own saved counter, see [CollectionKillCountData]. The client reads the
 * three varps `collection_category_count`, `_count2` and `_count3` for whichever category is on
 * screen, so [show] copies the category's counters across before it is drawn.
 */
@Singleton
class CollectionKillCounts {
    /** Adds [amount] to [counter] for [player]. The count stops at the largest int. */
    fun add(player: Player, counter: VarpType, amount: Int = 1) {
        if (amount <= 0) {
            return
        }
        val total = (player.vars[counter].toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong())
        VarPlayerIntMapSetter.set(player, counter, total.toInt())
    }

    fun get(player: Player, counter: VarpType): Int = player.vars[counter]

    /**
     * Adds one to every counter that [npcName] (any letter case) is a kill of, such as Zulrah
     * kills. Returns whether any counter matched.
     */
    fun addKill(player: Player, npcName: String): Boolean {
        val counters = CollectionKillSources.countersFor(npcName)
        for (counter in counters) {
            add(player, counter)
        }
        return counters.isNotEmpty()
    }

    /**
     * Puts the counters of the category [struct] into the client's three kill-count varps. A line
     * the category does not have is set to `0`.
     */
    fun show(player: Player, struct: Int) {
        val counters = CollectionKillCountData.byCategory[struct]
        for (slot in clog_varps.kill_lines.indices) {
            val counter = counters?.getOrNull(slot)
            val value = if (counter == null) 0 else player.vars[counter]
            VarPlayerIntMapSetter.set(player, clog_varps.kill_lines[slot], value)
        }
    }
}
