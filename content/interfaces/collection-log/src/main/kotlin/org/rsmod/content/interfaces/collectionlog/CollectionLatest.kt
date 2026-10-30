package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * The overview's "Latest Collections": the [SIZE] newest slots filled, newest first. Each is an obj
 * id in one varp (`-1` for none) and the runeday it was obtained in another.
 */
@Singleton
class CollectionLatest {
    /** Puts [obj] in front, obtained on [day], pushing the oldest one off the end. */
    fun push(player: Player, obj: Int, day: Int) {
        val items = IntArray(SIZE) { player.vars[clog_varps.latest_items[it]] }
        val dates = IntArray(SIZE) { player.vars[clog_varps.latest_dates[it]] }
        shift(items, dates, obj, day)
        write(player, items, dates)
    }

    /**
     * Makes an unused entry read `-1`, as the client expects, for a player whose varps are still
     * the default `0` (no obj has id 0 in the log).
     */
    fun normalise(player: Player) {
        for (i in 0 until SIZE) {
            val varp = clog_varps.latest_items[i]
            if (player.vars[varp] == 0) {
                VarPlayerIntMapSetter.set(player, varp, EMPTY)
            }
        }
    }

    private fun write(player: Player, items: IntArray, dates: IntArray) {
        for (i in 0 until SIZE) {
            VarPlayerIntMapSetter.set(player, clog_varps.latest_items[i], items[i])
            VarPlayerIntMapSetter.set(player, clog_varps.latest_dates[i], dates[i])
        }
    }

    companion object {
        const val SIZE = 12
        const val EMPTY = -1

        /** Moves every entry down one place and puts [obj] and [day] at the front. */
        fun shift(items: IntArray, dates: IntArray, obj: Int, day: Int) {
            for (i in items.size - 1 downTo 1) {
                items[i] = items[i - 1]
                dates[i] = dates[i - 1]
            }
            items[0] = obj
            dates[0] = day
        }
    }
}
