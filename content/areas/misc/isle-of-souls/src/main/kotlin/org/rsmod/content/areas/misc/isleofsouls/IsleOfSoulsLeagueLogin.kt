package org.rsmod.content.areas.misc.isleofsouls

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onPlayerInit
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varbits
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_varps
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Makes the client treat this world as a league world so the journal shows its Leagues tab.
 *
 * The client's `league_world` clientscript tests the world flags cached in `map_flags_cached`
 * (seasonal bit set, deadman bit clear), and `side_journal_switchtab` only configures the leagues
 * tab once `league_tutorial_completed` is at least 3.
 *
 * The tutorial value is set to the finished state rather than the minimum: while it is below the
 * league's stage count (12 for every league but type 3), the client's `in_league_tutorial` is true
 * and it lays out the main modal for the tutorial, which clips league screens.
 */
class IsleOfSoulsLeagueLogin : PluginScript() {
    override fun ScriptContext.startup() {
        onPlayerInit {
            val flags = player.vars[isle_varps.map_flags_cached]
            val worldFlags = (flags or SEASONAL_FLAG) and DEADMAN_FLAG.inv()
            VarPlayerIntMapSetter.set(player, isle_varps.map_flags_cached, worldFlags)
            VarPlayerIntMapSetter.set(player, isle_varbits.league_type, LEAGUE_TYPE)
            VarPlayerIntMapSetter.set(
                player,
                isle_varbits.league_tutorial_completed,
                LEAGUE_TUTORIAL_COMPLETE,
            )
        }
    }

    private companion object {
        const val SEASONAL_FLAG = 1 shl 30
        const val DEADMAN_FLAG = 1 shl 29
        const val LEAGUE_TYPE = 5
        const val LEAGUE_TUTORIAL_COMPLETE = 12
    }
}
