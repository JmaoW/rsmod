package org.rsmod.content.areas.misc.isleofsouls.pvp

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.PvpRules
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.player.output.ClientScripts
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

typealias pvp_timers = PvpTimers

object PvpTimers : TimerReferences() {
    /** Checks every logged-in player each tick: the Attack option and the skull's timer. */
    val tick = find("pvp_zone_tick")
}

/**
 * Player-versus-player combat on the isle. Players can only fight inside the zones the event
 * settings open ([PvpSettings]); everywhere else the game is as it was, with no Attack option on
 * players. Teammates (same league team) can never attack each other.
 * - **Rule:** attacker and target must both stand in an open zone, checked on every swing
 *   ([PvpRules]), so closing a zone or walking out of it ends a fight in progress.
 * - **Option:** the client's Attack option on players (player op 2) is sent while the player stands
 *   in an open zone and taken away when they leave. A timer looks at every player each tick, which
 *   also covers teleports, deaths and logging in.
 * - **Overlay:** the Wilderness overlay (skull, level, combat range) shows while the player is in
 *   an open zone: the server sets [PvpVarps.overlay] and runs the client's transmit script, see
 *   [PvpOverlayScripts]. It shows [OVERLAY_LEVEL], whose combat range covers every level.
 * - **Skull:** an attack gives the attacker the [PvpSkull], refreshed by each attack, unless it
 *   answers an attack the target started ([PvpFights]).
 * - **Death:** nothing is dropped when a player dies, to a player or not; [PvpSkull] ends with it.
 */
class PvpScript
@Inject
constructor(
    private val rules: PvpRules,
    private val teams: LeagueTeams,
    private val skull: PvpSkull,
    private val fights: PvpFights,
) : PluginScript() {
    /** Whether the Attack option is currently on the client of each player. */
    private val optionShown = WeakHashMap<Player, Boolean>()

    override fun ScriptContext.startup() {
        rules.register { attacker, target -> denial(attacker, target) }
        rules.onAttack { attacker, target -> attacked(attacker, target) }

        onPlayerLogin {
            // The login sequence has just sent the player ops with no Attack in them.
            optionShown.remove(player)
            player.softTimer(pvp_timers.tick, 1)
        }
        onPlayerSoftTimer(pvp_timers.tick) { tick(player) }
        onEvent<PlayerDeathEvents.Died> { skull.clear(player) }
    }

    /**
     * Skulls [attacker] for attacking [target], unless they are answering an attack by [target].
     */
    fun attacked(attacker: Player, target: Player) {
        val retaliation = fights.record(attacker, target)
        if (!retaliation) {
            skull.refresh(attacker)
        }
    }

    /** The message explaining why [attacker] may not attack [target], or `null` if they may. */
    fun denial(attacker: Player, target: Player): String? {
        if (attacker === target) {
            return null
        }
        val settings = EventSettings.current.pvp
        val here = PvpZones.open(attacker.coords, settings)
        val there = PvpZones.open(target.coords, settings)
        if (here == null || there == null) {
            return "You can't attack other players here."
        }
        val team = teams.teamOf(attacker)
        if (team != null && team.id == teams.teamOf(target)?.id) {
            return "You can't attack your own teammates."
        }
        return null
    }

    /** Keeps the Attack option and the skull timer right for [player]. */
    fun tick(player: Player) {
        val open = PvpZones.open(player.coords, EventSettings.current.pvp) != null
        if ((optionShown[player] ?: false) != open) {
            optionShown[player] = open
            val op = if (open) "Attack" else null
            MiscOutput.setPlayerOp(player, slot = 2, op = op, priority = open)
            overlay(player, open)
        }
        skull.tick(player)
    }

    /** Shows or hides the Wilderness overlay on the client. */
    private fun overlay(player: Player, show: Boolean) {
        VarPlayerIntMapSetter.set(player, pvp_varps.overlay, if (show) OVERLAY_LEVEL else 0)
        ClientScripts.combatWildernessTransmit(player, if (show) 1 else 0)
    }

    companion object {
        /** The level the overlay shows: 126 is the most, and its range then covers every level. */
        const val OVERLAY_LEVEL = 126
    }
}
