package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.feet
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.agility.scripts.Agility
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object CornerCutterTimers : TimerReferences() {
    /** Ticks on every player and pays out the greaves' running experience. */
    val run_xp = find("agility_run_xp")
}

/**
 * The Corner Cutter relic (tier 2), which gives Sage's greaves:
 * - while the greaves are worn, running earns Agility experience: `10 + floor(level * 2 / 3)` about
 *   every ten ticks, counted when the player has moved two tiles in a tick (the Wiki's rule),
 *   before the league multipliers;
 * - every agility check succeeds;
 * - a finished course counts as two laps and gives 25% more of the course's experience;
 * - a mark of grace comes down with a stack of 10,000 coins.
 *
 * The Wiki also doubles pyramid tops, hallowed marks, Brimhaven vouchers and Prifddinas crystal
 * shards; nothing on the isle gives any of them. The agility skill asks each time through its
 * modifier event, so it knows nothing of the relic.
 */
class LeagueCornerCutter
@Inject
constructor(private val tools: LeagueRelicTools, private val xpMods: XpModifiers) : PluginScript() {
    private class Track(var coords: CoordGrid, var nextPay: Int)

    private val tracks = WeakHashMap<Player, Track>()

    fun isActive(player: Player): Boolean = tools.isChosen(player, RelicItem.CornerCutter)

    override fun ScriptContext.startup() {
        onEvent<Agility.Modifiers> {
            if (!isActive(player)) {
                return@onEvent
            }
            guaranteed = true
            completions = LAP_COMPLETIONS
            lapBonusPercent = LAP_BONUS_PERCENT
            markCoins = MARK_COINS
        }

        onPlayerLogin { player.softTimer(CornerCutterTimers.run_xp, 1) }
        onPlayerSoftTimer(CornerCutterTimers.run_xp) { tick(player) }
    }

    private fun tick(player: Player) {
        val now = player.currentMapClock
        val track = tracks.getOrPut(player) { Track(player.coords, now) }
        val moved = player.coords.chebyshevDistance(track.coords)
        track.coords = player.coords
        if (moved < RUN_DISTANCE || now < track.nextPay) {
            return
        }
        if (!isActive(player) || !player.feet.isType(isle_objs.sages_greaves)) {
            return
        }
        track.nextPay = now + PAY_TICKS
        player.statAdvance(
            stats.agility,
            runXp(player.agilityLvl) * xpMods.get(player, stats.agility),
        )
    }

    companion object {
        const val LAP_COMPLETIONS = 2
        const val LAP_BONUS_PERCENT = 25
        const val MARK_COINS = 10_000

        /** Tiles in a tick that count as running, and ticks between payments. */
        const val RUN_DISTANCE = 2
        const val PAY_TICKS = 10

        fun runXp(level: Int): Double = 10.0 + (level * 2 / 3)
    }
}
