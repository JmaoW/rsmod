package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.player.hit.events.PlayerHitEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.stat.StatType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

typealias last_stand_timers = LastStandTimers

object LastStandTimers : TimerReferences() {
    /** Runs every tick while Last Stand is active or the stats it boosted are draining. */
    val tick = find("league_last_stand_tick")
}

/**
 * The Last Stand relic (tier 7 in the code, "tier 8" in the plan and on the wiki). A hit that would
 * kill the player leaves them on 1 hitpoint instead and starts the effect:
 * - Attack, Strength, Defence, Ranged and Magic jump to 255, then drain [DRAIN_PER_TICK] a tick
 *   until they are [BOOST_ABOVE_BASE] above the base level.
 * - For [DURATION_TICKS] ticks no hit can take the player below 1 hitpoint, so the hits taken in
 *   that time do nothing.
 * - When those ticks end, the player is healed by the damage they dealt during them (up to their
 *   base Hitpoints).
 *
 * It cannot trigger again for [COOLDOWN_TICKS] ticks; dying resets that. The wiki also says food
 * cannot heal above 1 hitpoint during the effect, which is not enforced here.
 */
class LeagueLastStand
@Inject
constructor(
    private val relics: LeagueRelicTracker,
    private val mapClock: MapClock,
    private val playerList: PlayerList,
) : PluginScript() {
    private class State {
        var endsAt: Int = 0
        var readyAt: Int = 0
        var dealt: Int = 0
        var draining: Boolean = false
        var healPending: Boolean = false
    }

    private val states = WeakHashMap<Player, State>()

    fun isChosen(player: Player): Boolean = relics.has(player, TIER, OPTION)

    /** Whether the effect is running for [player]: no hit can take them below 1 hitpoint. */
    fun isActive(player: Player): Boolean {
        val state = states[player] ?: return false
        return mapClock.cycle < state.endsAt
    }

    override fun ScriptContext.startup() {
        onEvent<PlayerHitEvents.Landing> { land(player, this) }
        onEvent<NpcHitEvents.Landing> {
            if (hit.isFromPlayer) {
                hit.resolvePlayerSource(playerList)?.let { dealt(it, minOf(damage, npc.hitpoints)) }
            }
        }
        onEvent<PlayerDeathEvents.Died> { states.remove(player) }
        onPlayerSoftTimer(last_stand_timers.tick) { tick(player) }
    }

    private fun land(player: Player, landing: PlayerHitEvents.Landing) {
        if (landing.hit.isFromPlayer) {
            landing.hit.resolvePlayerSource(playerList)?.let {
                dealt(it, minOf(landing.damage, player.hitpoints))
            }
        }
        if (!isChosen(player)) {
            return
        }
        val state = states.getOrPut(player) { State() }
        val now = mapClock.cycle
        if (now < state.endsAt) {
            landing.damage = minOf(landing.damage, player.hitpoints - 1).coerceAtLeast(0)
            return
        }
        if (landing.damage < player.hitpoints || now < state.readyAt) {
            return
        }
        landing.damage = player.hitpoints - 1
        state.endsAt = now + DURATION_TICKS
        state.readyAt = now + COOLDOWN_TICKS
        state.dealt = 0
        state.draining = true
        state.healPending = true
        for (stat in boosted) {
            player.statAdd(stat, constant = MAX_LEVEL, percent = 0)
        }
        player.mes(LeagueChat.blue("Your Last Stand relic keeps you alive!"))
        player.softTimer(last_stand_timers.tick, 1)
    }

    private fun dealt(attacker: Player, damage: Int) {
        if (damage <= 0) {
            return
        }
        val state = states[attacker] ?: return
        if (mapClock.cycle < state.endsAt) {
            state.dealt += damage
        }
    }

    private fun tick(player: Player) {
        val state = states[player]
        if (state == null) {
            player.clearSoftTimer(last_stand_timers.tick)
            return
        }
        if (state.draining) {
            state.draining = drain(player)
        }
        if (state.healPending && mapClock.cycle >= state.endsAt) {
            val heal = state.dealt
            player.statHeal(stats.hitpoints, constant = heal, percent = 0)
            player.mes("Your Last Stand ends, healing you for ${LeagueChat.blue("$heal")}.")
            state.healPending = false
        }
        if (!state.draining && !state.healPending) {
            player.clearSoftTimer(last_stand_timers.tick)
        }
    }

    /** Drains each boosted stat towards its floor. Returns whether any is still above it. */
    private fun drain(player: Player): Boolean {
        var above = false
        for (stat in boosted) {
            val floor = player.statBase(stat) + BOOST_ABOVE_BASE
            val excess = player.stat(stat) - floor
            if (excess > 0) {
                player.statSub(stat, constant = minOf(excess, DRAIN_PER_TICK), percent = 0)
                above = above || excess > DRAIN_PER_TICK
            }
        }
        return above
    }

    companion object {
        const val TIER = 7
        const val OPTION = 3

        const val DURATION_TICKS = 16
        const val COOLDOWN_TICKS = 300
        const val DRAIN_PER_TICK = 16
        const val BOOST_ABOVE_BASE = 15
        const val MAX_LEVEL = 255

        private val boosted: List<StatType> =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
    }
}
