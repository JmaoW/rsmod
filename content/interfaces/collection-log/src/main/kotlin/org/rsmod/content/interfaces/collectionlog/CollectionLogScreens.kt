package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.utils.time.runeday
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent

/**
 * Opens, switches and redraws the collection log and its overview. The client draws both screens
 * itself from the cache and the varps and inventory this module keeps; what it cannot do alone is
 * react to a click that needs the server's data, so the server answers it with the client scripts
 * the real game runs:
 * * `collection_draw_tabs_all` (2388, the tab index) redraws the tab buttons and shows the selected
 *   tab's list.
 * * `collection_draw_list` (2730, tab index, the tab's container, row background, row text and
 *   scrollbar components, the tab struct, and the selected row or `-1`) draws a tab's category rows
 *   and, for the selected row, the items of that category.
 */
@Singleton
class CollectionLogScreens
@Inject
constructor(
    private val eventBus: EventBus,
    private val log: CollectionLog,
    private val kills: CollectionKillCounts,
    private val latest: CollectionLatest,
) {
    /** Sends everything the screens read that is not sent by the engine. */
    fun sync(player: Player) {
        latest.normalise(player)
        log.recount(player)
        syncRuneday(player)
    }

    /**
     * Sets the day the client counts "obtained N days ago" from. Called on login and each time a
     * screen opens, so a session that crosses midnight is right too.
     */
    fun syncRuneday(player: Player) {
        VarPlayerIntMapSetter.set(player, clog_varbits.current_runeday, runeday())
    }

    fun openOverview(player: Player) {
        player.ifCloseOverlay(interfaces.collection, eventBus)
        player.ifOpenOverlay(clog_interfaces.overview, eventBus)
    }

    /** Opens the log on [tab], with the first category of the tab selected. */
    fun openLog(player: Player, tab: Int = 0) {
        player.ifCloseOverlay(clog_interfaces.overview, eventBus)
        player.ifOpenOverlay(interfaces.collection, eventBus)
        selectTab(player, tab)
    }

    fun closeLog(player: Player) {
        player.ifCloseOverlay(interfaces.collection, eventBus)
    }

    fun closeOverview(player: Player) {
        player.ifCloseOverlay(clog_interfaces.overview, eventBus)
    }

    /** Lets the client send the clicks on the log's tabs, rows and menu. */
    fun enableLogEvents(player: Player) {
        val tabs = log.catalog.tabs
        for (tab in tabs) {
            val rows = clog_components.rows[tab.index]
            player.ifSetEvents(rows, 0 until tab.categories.size, IfEvent.Op1)
        }
        player.ifSetEvents(clog_components.log_burger_menu, MENU_CHILDREN, IfEvent.Op1)
        syncRuneday(player)
    }

    /** Lets the client send the clicks on the overview's menu and section buttons. */
    fun enableOverviewEvents(player: Player) {
        player.ifSetEvents(clog_components.overview_burger_menu, MENU_CHILDREN, IfEvent.Op1)
        val sections = 0 until CollectionCatalog.TAB_COUNT
        player.ifSetEvents(clog_components.overview_sections, sections, IfEvent.Op1)
        player.ifSetEvents(clog_components.overview_sections_click, sections, IfEvent.Op1)
        syncRuneday(player)
    }

    /** Shows tab [index] with its first category selected, as the real log does. */
    fun selectTab(player: Player, index: Int) {
        if (index !in log.catalog.tabs.indices) {
            return
        }
        player.runClientScript(DRAW_TABS, index)
        selectRow(player, index, row = 0)
    }

    fun selectRow(player: Player, tabIndex: Int, row: Int) {
        val category = log.catalog.tabs.getOrNull(tabIndex)?.categories?.getOrNull(row) ?: return
        kills.show(player, category.struct)
        drawList(player, tabIndex, selectedRow = row)
    }

    private fun drawList(player: Player, tabIndex: Int, selectedRow: Int) {
        val tab = log.catalog.tabs[tabIndex]
        player.runClientScript(
            DRAW_LIST,
            tabIndex,
            tab.container,
            tab.background,
            tab.text,
            tab.scrollbar,
            tab.struct,
            selectedRow,
        )
    }

    private companion object {
        const val DRAW_TABS = 2388
        const val DRAW_LIST = 2730

        /** The menu's entries are children of its frame; this is more than it ever creates. */
        val MENU_CHILDREN = 0..15
    }
}
