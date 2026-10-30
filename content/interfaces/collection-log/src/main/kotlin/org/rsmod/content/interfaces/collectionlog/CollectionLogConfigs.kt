package org.rsmod.content.interfaces.collectionlog

import org.rsmod.api.type.editors.inv.InvEditor
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.inv.InvScope
import org.rsmod.game.type.inv.InvStackType

typealias clog_interfaces = CollectionLogInterfaces

typealias clog_components = CollectionLogComponents

typealias clog_invs = CollectionLogInvs

typealias clog_varps = CollectionLogVarps

typealias clog_varbits = CollectionLogVarBits

object CollectionLogInterfaces : InterfaceReferences() {
    val overview = find("collection_overview")
}

object CollectionLogComponents : ComponentReferences() {
    /** The five tab buttons of the log, in tab order (bosses, raids, clues, minigames, other). */
    val tabs =
        listOf(
            find("collection:boss_tab"),
            find("collection:raid_tab"),
            find("collection:clue_tab"),
            find("collection:minigame_tab"),
            find("collection:other_tab"),
        )

    /** Each tab's category rows are children of one of these, the child index being the row. */
    val rows =
        listOf(
            find("collection:boss_background"),
            find("collection:raid_background"),
            find("collection:clue_background"),
            find("collection:minigame_background"),
            find("collection:other_background"),
        )

    val log_close = find("collection:close")
    val log_burger_menu = find("collection:burger_menu_frame")

    val overview_close = find("collection_overview:close")
    val overview_burger_menu = find("collection_overview:burger_menu_frame")
    val overview_sections = find("collection_overview:subsection_buttons")
    val overview_sections_click = find("collection_overview:subsection_buttons_click")
}

object CollectionLogInvs : InvReferences() {
    /** The log: one slot per obj received, the count being how many times it was received. */
    val log = find("collection_transmit")
}

object CollectionLogVarps : VarpReferences() {
    val count = find("collection_count")
    val count_max = find("collection_count_max")

    /**
     * The total the journal's summary tab shows as "Collections Logged" (and the overview's rank).
     */
    val count_highscores = find("collection_count_highscores")

    /** Slots filled and available for each tab, in tab order. */
    val tab_counts =
        listOf(
            find("collection_count_bosses"),
            find("collection_count_raids"),
            find("collection_count_clues"),
            find("collection_count_minigames"),
            find("collection_count_other"),
        )
    val tab_maxes =
        listOf(
            find("collection_count_bosses_max"),
            find("collection_count_raids_max"),
            find("collection_count_clues_max"),
            find("collection_count_minigames_max"),
            find("collection_count_other_max"),
        )

    /** The three varps the log reads its kill-count lines from, whichever category is shown. */
    val kill_lines =
        listOf(
            find("collection_category_count"),
            find("collection_category_count2"),
            find("collection_category_count3"),
        )

    /** The overview's "Latest Collections": an obj id (or -1) and the runeday it was obtained. */
    val latest_items =
        listOf(
            find("collection_overview_last_item0"),
            find("collection_overview_last_item1"),
            find("collection_overview_last_item2"),
            find("collection_overview_last_item3"),
            find("collection_overview_last_item4"),
            find("collection_overview_last_item5"),
            find("collection_overview_last_item6"),
            find("collection_overview_last_item7"),
            find("collection_overview_last_item8"),
            find("collection_overview_last_item9"),
            find("collection_overview_last_item10"),
            find("collection_overview_last_item11"),
        )
    val latest_dates =
        listOf(
            find("collection_overview_last_item0_date"),
            find("collection_overview_last_item1_date"),
            find("collection_overview_last_item2_date"),
            find("collection_overview_last_item3_date"),
            find("collection_overview_last_item4_date"),
            find("collection_overview_last_item5_date"),
            find("collection_overview_last_item6_date"),
            find("collection_overview_last_item7_date"),
            find("collection_overview_last_item8_date"),
            find("collection_overview_last_item9_date"),
            find("collection_overview_last_item10_date"),
            find("collection_overview_last_item11_date"),
        )
}

object CollectionLogVarBits : VarBitReferences() {
    /** Days since the RuneScape epoch. The overview works out "obtained 3 days ago" from it. */
    val current_runeday = find("current_runeday")
}

/**
 * The log is stored in the inventory the client reads it from. The cache sizes it at 500 slots; the
 * log needs a slot for each of its 1,613 distinct objs.
 */
internal object CollectionLogInvEdits : InvEditor() {
    init {
        edit(clog_invs.log) {
            scope = InvScope.Perm
            stack = InvStackType.Always
            size = LOG_INV_SIZE
            protect = false
        }
    }
}

internal const val LOG_INV_SIZE = 2048
