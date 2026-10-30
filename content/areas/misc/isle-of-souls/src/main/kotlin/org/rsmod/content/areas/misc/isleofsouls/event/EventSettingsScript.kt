package org.rsmod.content.areas.misc.isleofsouls.event

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.game.process.player.AutosaveSettings
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerInit
import org.rsmod.content.areas.misc.isleofsouls.gauntlet.GauntletNode
import org.rsmod.content.areas.misc.isleofsouls.revenant.ether_objs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Applies [EventSettings] when the server starts: it becomes [EventSettings.current], which the
 * relics, tasks and masteries read, and the settings that live elsewhere are pushed to where they
 * are used (the Gauntlet's node flag and the autosave timing). The revenant ether boost and the XP
 * rate are applied here too, as they are only a hook each.
 */
class EventSettingsScript @Inject constructor(private val autosave: AutosaveSettings) :
    PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        apply(EventSettings.load())

        // Revenants drop the wiki's amount of ether; the event multiplies it.
        onEvent<NpcDropEvents.Modify> {
            countMultipliers[ether_objs.ether] = EventSettings.current.revenantEtherMultiplier
        }

        // The XP rate is saved on the account, so the file's value replaces it at every login: that
        // is the only way a change here reaches characters made before it.
        onPlayerInit { EventSettings.current.baseXpRate?.let { player.xpRate = it } }
    }

    /** Makes [settings] the event's settings and pushes the ones that live elsewhere. */
    fun apply(settings: EventSettings) {
        EventSettings.current = settings
        GauntletNode.NODES_DEPLETE = settings.gauntletNodesDeplete
        autosave.interval = settings.autosaveIntervalTicks
        autosave.requestDelay = settings.autosaveRequestDelayTicks
        for (key in settings.unknownKeys) {
            logger.warn { "event-settings.json: unknown setting \"$key\" (a typo?); it is ignored" }
        }
        logger.info { "Event settings: ${summary(settings)}" }
    }

    private fun summary(settings: EventSettings): String =
        "base XP rate ${settings.baseXpRate ?: "unchanged"}, " +
            "XP ${settings.xpBase}x ${settings.xpByRelicTier}, " +
            "drops ${settings.dropByRelicTier}, " +
            "ether ${settings.revenantEtherMultiplier}x, " +
            "gauntlet nodes deplete ${settings.gauntletNodesDeplete}, " +
            "autosave ${settings.autosaveIntervalTicks}/${settings.autosaveRequestDelayTicks}, " +
            "pvp ${settings.pvp}"
}
