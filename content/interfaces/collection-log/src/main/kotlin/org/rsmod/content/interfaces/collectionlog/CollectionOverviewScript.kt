package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The collection log overview: the screen the journal's summary tab opens first. The client draws
 * the rank, progress bars and the "Latest Collections" from the count varps and the latest-items
 * varps (see [CollectionLatest]); the server enables its clicks. Each section button opens the log
 * on that tab, and the menu switches to the log.
 */
class CollectionOverviewScript
@Inject
constructor(private val screens: CollectionLogScreens, private val log: CollectionLog) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onIfOpen(clog_interfaces.overview) {
            log.recount(player)
            screens.enableOverviewEvents(player)
        }
        onIfOverlayButton(clog_components.overview_close) { screens.closeOverview(player) }
        onIfOverlayButton(clog_components.overview_burger_menu) { screens.openLog(player) }
        onIfOverlayButton(clog_components.overview_sections) { screens.openLog(player, comsub) }
        onIfOverlayButton(clog_components.overview_sections_click) {
            screens.openLog(player, comsub)
        }
    }
}
