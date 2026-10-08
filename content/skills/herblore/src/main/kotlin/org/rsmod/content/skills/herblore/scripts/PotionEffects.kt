package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.EnumMap
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.stats.plugin.StatBoostHolds
import org.rsmod.content.skills.herblore.configs.HerbloreTimers
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/**
 * The potion effects that keep running after the potion is drunk: protection from poison and
 * dragonfire, goading, stamina, the stat boosts a divine potion holds, and prayer regeneration.
 * Each is a count of ticks left, run down by a soft timer that only exists while the player has an
 * effect running.
 *
 * Nothing on the isle poisons a player or breathes fire yet, so the protections only record that
 * they are active: content that adds them can ask [hasPoisonProtection] and
 * [hasDragonfireProtection]. Goading likewise is only recorded, see [isGoading]. The state is not
 * saved, so it ends on logout.
 */
@Singleton
class PotionEffects
@Inject
constructor(private val holds: StatBoostHolds, private val masteries: CombatMasteryEffects) {
    private class State {
        val ticks = EnumMap<TimedEffect, Int>(TimedEffect::class.java)
        val divineStats = HashSet<StatType>()
        var regenPoints = 0
        var regenCountdown = 0

        fun idle(): Boolean = ticks.isEmpty() && regenPoints == 0
    }

    private val states = WeakHashMap<Player, State>()

    fun hasPoisonProtection(player: Player): Boolean = ticksLeft(player, TimedEffect.Antipoison) > 0

    fun hasDragonfireProtection(player: Player): Boolean =
        ticksLeft(player, TimedEffect.Antifire) > 0 ||
            ticksLeft(player, TimedEffect.ExtendedAntifire) > 0

    fun isGoading(player: Player): Boolean = ticksLeft(player, TimedEffect.Goading) > 0

    fun ticksLeft(player: Player, effect: TimedEffect): Int =
        states[player]?.ticks?.get(effect) ?: 0

    /** Starts [effect] for [ticks], unless it already has longer to run. */
    fun start(player: Player, effect: TimedEffect, ticks: Int) {
        val state = state(player)
        state.ticks.merge(effect, ticks, ::maxOf)
    }

    /** Holds the boost of [changed] stats for [PotionEffect.DIVINE_TICKS] without wearing off. */
    fun startDivine(player: Player, changed: Collection<StatType>) {
        val state = state(player)
        for (stat in changed) {
            holds.hold(player, stat)
            state.divineStats += stat
        }
        state.ticks[TimedEffect.Divine] = PotionEffect.DIVINE_TICKS
    }

    /** Adds the prayer points of a dose of prayer regeneration potion. */
    fun addRegeneration(player: Player) {
        val state = state(player)
        if (state.regenPoints == 0) {
            state.regenCountdown = PotionEffect.REGEN_INTERVAL
        }
        state.regenPoints += PotionEffect.REGEN_POINTS
    }

    private fun state(player: Player): State {
        val state = states.getOrPut(player) { State() }
        player.softTimer(HerbloreTimers.effects, 1)
        return state
    }

    /** Runs one tick of every effect [player] has, ending the ones that are over. */
    fun tick(player: Player) {
        val state = states[player]
        if (state == null) {
            player.clearSoftTimer(HerbloreTimers.effects)
            return
        }

        for ((effect, ticks) in state.ticks.entries.map { it.key to it.value }) {
            if (ticks > 1) {
                state.ticks[effect] = ticks - 1
            } else {
                state.ticks.remove(effect)
                end(player, state, effect)
            }
        }

        if (state.regenPoints > 0 && --state.regenCountdown <= 0) {
            // The fifth combat mastery passive raises all prayer point gain.
            player.statHeal(stats.prayer, constant = masteries.prayerGain(player, 1), percent = 0)
            state.regenPoints--
            state.regenCountdown = PotionEffect.REGEN_INTERVAL
        }

        if (state.idle()) {
            states.remove(player)
            player.clearSoftTimer(HerbloreTimers.effects)
        }
    }

    private fun end(player: Player, state: State, effect: TimedEffect) {
        when (effect) {
            TimedEffect.Stamina -> VarPlayerIntMapSetter.set(player, varbits.stamina_active, 0)
            TimedEffect.Divine -> endDivine(player, state)
            else -> {}
        }
        player.mes(effect.message)
    }

    /** Releases the held stats and lowers each back to its base level. */
    private fun endDivine(player: Player, state: State) {
        for (stat in state.divineStats) {
            holds.release(player, stat)
            val over = player.stat(stat) - player.statBase(stat)
            if (over > 0) {
                player.statSub(stat, constant = over, percent = 0)
            }
        }
        state.divineStats.clear()
    }

    /** Ends everything for a player who logs in, since none of it is saved. */
    fun reset(player: Player) {
        states.remove(player)
        VarPlayerIntMapSetter.set(player, varbits.stamina_active, 0)
    }
}
