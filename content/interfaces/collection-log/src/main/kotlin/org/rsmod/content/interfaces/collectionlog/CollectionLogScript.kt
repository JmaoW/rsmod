package org.rsmod.content.interfaces.collectionlog

import jakarta.inject.Inject
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.startInvTransmit
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Wires the collection log: the log inventory is sent at login, every drop a hero is given fills
 * its slots, every kill raises the kill counts, and the log screen answers its clicks.
 */
class CollectionLogScript
@Inject
constructor(
    private val log: CollectionLog,
    private val kills: CollectionKillCounts,
    private val screens: CollectionLogScreens,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerLogin {
            player.startInvTransmit(log.inventory(player))
            screens.sync(player)
        }

        onEvent<NpcDropEvents.Rolled> {
            for (drop in drops) {
                log.receive(hero, drop.obj.id, drop.count)
            }
        }
        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            kills.addKill(hero, npc.visType.name)
        }

        onIfOpen(interfaces.collection) { screens.enableLogEvents(player) }
        // Clicking the search bar turns off the chatbox's key handler and sets the search flags,
        // and only ending the search undoes that. Closing the log mid-search would leave the
        // chatbox deaf, so end it: `collection_toggle_search` with 1 runs the client's own
        // search-close path (restores the chatbox input, clears the flags, hides the keyboard).
        onIfClose(interfaces.collection) { player.runClientScript(TOGGLE_SEARCH, CLOSE_SEARCH) }
        for ((index, tab) in clog_components.tabs.withIndex()) {
            onIfOverlayButton(tab) { screens.selectTab(player, index) }
        }
        for ((tab, rows) in clog_components.rows.withIndex()) {
            onIfOverlayButton(rows) { screens.selectRow(player, tab, comsub) }
        }
        onIfOverlayButton(clog_components.log_burger_menu) { screens.openOverview(player) }
        onIfOverlayButton(clog_components.log_close) { screens.closeLog(player) }
    }

    private companion object {
        const val TOGGLE_SEARCH = 4084
        const val CLOSE_SEARCH = 1
    }
}
