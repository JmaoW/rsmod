package org.rsmod.content.areas.misc.isleofsouls.tasks

import com.github.michaelbull.logging.InlineLogger
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.VarpSync
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfClose
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varbits
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varps
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The two parts of the League Tasks window that depend on the server.
 *
 * **Filters.** The window's five dropdowns (tier, type, area, skill, completed) store the picked
 * entry in a client-side varbit, and the task list redraws from the client's var-transmit trigger
 * on varps `league_general` and `league_general_2`. The client only queues that trigger for varp
 * packets from the server (a clientscript's own `set_varbit` never does), so a pick changed the
 * dropdown's label but left the list as it was. `[proc,script3211]`, the proc that stores a pick,
 * is patched (see [LeagueTaskScripts]) to send `leaguefilter <enum id> <entry index>`. This script
 * stores the pick in the matching varbit, which sends the varp and makes the list redraw.
 *
 * **Search.** Clicking the search bar calls `chatdefault_stopinput`, which turns off the chatbox's
 * key handler and sets two search flags in `league_temp_noprotect_extra`. The window only undoes
 * that itself when the search ends (Enter, Esc, a second click, or a var change). Closing the
 * window mid-search left the chatbox deaf, so typing went nowhere, and the stale search flags came
 * back the next time the window opened.
 */
class LeagueTaskWindow : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onCommand("leaguefilter") {
            desc = "Set a Tasks window filter: <dropdown enum id> <entry index>"
            cheat {
                logger.info { "leaguefilter ${args.joinToString(" ")} from $player" }
                val varbit = filterVarBit(args[0].toInt()) ?: return@cheat
                val entry = args[1].toInt()
                try {
                    VarPlayerIntMapSetter.set(player, varbit, entry)
                } catch (_: IllegalArgumentException) {
                    // The pick does not fit the filter's bits: ignore it, as for an unknown enum.
                }
            }
        }

        onIfClose(isle_interfaces.league_tasks) { player.endSearch() }
    }

    /** Gives the chatbox its key handler back and clears the search flags on the client. */
    private fun Player.endSearch() {
        ClientScripts.chatDefaultRestoreInput(this)
        // Neither flag is set by the server, so this packet carries the flags as zero.
        val flags = isle_varps.league_temp_noprotect_extra
        VarpSync.writeVarp(this, flags, vars[flags])
    }

    private fun filterVarBit(enumId: Int): VarBitType? =
        when (enumId) {
            TIER_ENUM -> isle_varbits.league_task_filter_tier
            COMPLETED_ENUM -> isle_varbits.league_task_filter_completed
            TYPE_ENUM -> isle_varbits.league_task_filter_type
            AREA_ENUM -> isle_varbits.league_task_filter_area
            SKILL_ENUM -> isle_varbits.league_task_filter_skill
            else -> null
        }

    /** The dropdown entry enums, as `[proc,script3211]` receives them. */
    companion object {
        const val TIER_ENUM = 2676
        const val COMPLETED_ENUM = 2677
        const val TYPE_ENUM = 3413
        const val AREA_ENUM = 3414
        const val SKILL_ENUM = 4105
    }
}
