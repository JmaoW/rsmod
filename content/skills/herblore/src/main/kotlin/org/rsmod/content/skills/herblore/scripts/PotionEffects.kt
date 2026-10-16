package org.rsmod.content.skills.herblore.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.EnumMap
import java.util.WeakHashMap
import org.rsmod.api.combat.mastery.CombatMasteryEffects
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.api.player.hit.takeInstantHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statBoost
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.stats.plugin.StatBoostHolds
import org.rsmod.content.skills.herblore.configs.HerbloreTimers
import org.rsmod.content.skills.herblore.configs.PotionEffect
import org.rsmod.content.skills.herblore.configs.PotionVars
import org.rsmod.content.skills.herblore.configs.TimedEffect
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.stat.StatType

/** What a running antifire potion does against dragonfire: the super potions are stronger. */
enum class AntifireLevel {
    None,
    Regular,
    Super,
}

/**
 * The potion effects that keep running after the potion is drunk: protection from poison and
 * dragonfire, goading, stamina, the stat boosts a divine potion holds, and prayer regeneration.
 * Each is a count of ticks left, run down by a soft timer that only exists while the player has an
 * effect running.
 *
 * Poison and venom read the protection from [hasPoisonProtection]. Dragonfire reads [antifire]
 * (`content/other/dragonfire`). Goading likewise is only recorded, see [isGoading]. Antipoison is
 * saved on log-out (its ticks are kept in [PotionVars]) and picked up again on log-in; the rest is
 * not saved, so it ends on logout.
 */
@Singleton
class PotionEffects
@Inject
constructor(
    private val holds: StatBoostHolds,
    private val masteries: CombatMasteryEffects,
    private val hitProcessor: InstantPlayerHitProcessor,
) {
    private class State {
        val ticks = EnumMap<TimedEffect, Int>(TimedEffect::class.java)
        val divineStats = HashSet<StatType>()
        var regenPoints = 0
        var regenCountdown = 0
        var overload: OverloadState? = null
        var enhanceCountdown = 0
        var menaphiteCountdown = 0

        fun idle(): Boolean = ticks.isEmpty() && regenPoints == 0
    }

    /** A running overload: the boost it keeps up and the hits it still has to deal. */
    private class OverloadState(val constant: Int, val percent: Int) {
        var hitsLeft = PotionEffect.OVERLOAD_HITS
        var refreshCountdown = PotionEffect.OVERLOAD_REFRESH
    }

    private val states = WeakHashMap<Player, State>()

    fun hasPoisonProtection(player: Player): Boolean = ticksLeft(player, TimedEffect.Antipoison) > 0

    fun hasDragonfireProtection(player: Player): Boolean = antifire(player) != AntifireLevel.None

    /** How well the potions the player is under protect from dragonfire. */
    fun antifire(player: Player): AntifireLevel =
        when {
            ticksLeft(player, TimedEffect.SuperAntifire) > 0 ||
                ticksLeft(player, TimedEffect.ExtendedSuperAntifire) > 0 -> AntifireLevel.Super
            ticksLeft(player, TimedEffect.Antifire) > 0 ||
                ticksLeft(player, TimedEffect.ExtendedAntifire) > 0 -> AntifireLevel.Regular
            else -> AntifireLevel.None
        }

    fun isGoading(player: Player): Boolean = ticksLeft(player, TimedEffect.Goading) > 0

    fun ticksLeft(player: Player, effect: TimedEffect): Int =
        states[player]?.ticks?.get(effect) ?: 0

    /** Starts [effect] for [ticks], unless it already has longer to run. */
    fun start(player: Player, effect: TimedEffect, ticks: Int) {
        val state = state(player)
        state.ticks.merge(effect, ticks, ::maxOf)
        saveAntipoison(player, state)
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

    /**
     * Boosts the combat stats and keeps the boost up for five minutes, dealing the damage of the
     * overload on the next ticks. A potion that is still running is drunk again from the start.
     */
    fun startOverload(player: Player, constant: Int, percent: Int) {
        val state = state(player)
        boostOverload(player, constant, percent)
        for (stat in OVERLOAD_STATS) {
            holds.hold(player, stat)
        }
        state.overload = OverloadState(constant, percent)
        state.ticks[TimedEffect.Overload] = PotionEffect.OVERLOAD_TICKS
    }

    private fun boostOverload(player: Player, constant: Int, percent: Int) {
        for (stat in OVERLOAD_STATS) {
            player.statBoost(stat, constant, percent)
        }
    }

    /** One prayer point every [PotionEffect.ENHANCE_INTERVAL] ticks for [ticks]. */
    fun startPrayerEnhance(player: Player, ticks: Int) {
        val state = state(player)
        if (TimedEffect.PrayerEnhance !in state.ticks) {
            state.enhanceCountdown = PotionEffect.ENHANCE_INTERVAL
        }
        state.ticks.merge(TimedEffect.PrayerEnhance, ticks, ::maxOf)
    }

    /** Restores the combat stats now and every [PotionEffect.MENAPHITE_INTERVAL] ticks after. */
    fun startMenaphite(player: Player) {
        val state = state(player)
        restoreCombatStats(player)
        state.menaphiteCountdown = PotionEffect.MENAPHITE_INTERVAL
        state.ticks[TimedEffect.Menaphite] = PotionEffect.MENAPHITE_TICKS
    }

    /** One prayer point, unless prayer is full. The fifth mastery passive raises prayer gain. */
    private fun healPrayer(player: Player) {
        if (player.stat(stats.prayer) < player.statBase(stats.prayer)) {
            player.statHeal(stats.prayer, constant = masteries.prayerGain(player, 1), percent = 0)
        }
    }

    /** Raises the combat stats and hitpoints 6 plus 16% of the way back to their base level. */
    private fun restoreCombatStats(player: Player) {
        for (stat in MENAPHITE_STATS) {
            if (player.stat(stat) < player.statBase(stat)) {
                player.statHeal(stat, 6, 16)
            }
        }
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

        tickOverload(player, state)
        if (TimedEffect.PrayerEnhance in state.ticks && --state.enhanceCountdown <= 0) {
            healPrayer(player)
            state.enhanceCountdown = PotionEffect.ENHANCE_INTERVAL
        }
        if (TimedEffect.Menaphite in state.ticks && --state.menaphiteCountdown <= 0) {
            restoreCombatStats(player)
            state.menaphiteCountdown = PotionEffect.MENAPHITE_INTERVAL
        }

        if (state.regenPoints > 0 && --state.regenCountdown <= 0) {
            // The fifth combat mastery passive raises all prayer point gain.
            healPrayer(player)
            state.regenPoints--
            state.regenCountdown = PotionEffect.REGEN_INTERVAL
        }

        saveAntipoison(player, state)
        if (state.idle()) {
            states.remove(player)
            player.clearSoftTimer(HerbloreTimers.effects)
        }
    }

    /** Keeps the antipoison ticks in a saved varp, so they carry over a log-out. */
    private fun saveAntipoison(player: Player, state: State) {
        val left = state.ticks[TimedEffect.Antipoison] ?: 0
        if (player.vars[PotionVars.antipoison_ticks] != left) {
            VarPlayerIntMapSetter.set(player, PotionVars.antipoison_ticks, left)
        }
    }

    private fun end(player: Player, state: State, effect: TimedEffect) {
        when (effect) {
            TimedEffect.Stamina -> VarPlayerIntMapSetter.set(player, varbits.stamina_active, 0)
            TimedEffect.Divine -> endDivine(player, state)
            TimedEffect.Overload -> endOverload(player, state)
            else -> {}
        }
        player.mes(effect.message)
    }

    /** Refreshes the boost every so often, and deals the hits in the first few ticks. */
    private fun tickOverload(player: Player, state: State) {
        val overload = state.overload ?: return
        if (overload.hitsLeft > 0 && player.stat(stats.hitpoints) > PotionEffect.OVERLOAD_DAMAGE) {
            overload.hitsLeft--
            player.takeInstantHit(
                HitType.Typeless,
                PotionEffect.OVERLOAD_DAMAGE,
                hitProcessor,
                hitmark_groups.regular_damage,
            )
        }
        if (--overload.refreshCountdown <= 0) {
            boostOverload(player, overload.constant, overload.percent)
            overload.refreshCountdown = PotionEffect.OVERLOAD_REFRESH
        }
    }

    /** Lowers the combat stats back to their base level, and heals what the overload cost. */
    private fun endOverload(player: Player, state: State) {
        state.overload = null
        for (stat in OVERLOAD_STATS) {
            holds.release(player, stat)
            val over = player.stat(stat) - player.statBase(stat)
            if (over > 0) {
                player.statSub(stat, constant = over, percent = 0)
            }
        }
        if (player.stat(stats.hitpoints) < player.statBase(stats.hitpoints)) {
            player.statHeal(stats.hitpoints, PotionEffect.OVERLOAD_HEAL, 0)
        }
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

    /** Ends what is not saved for a player who logs in, and picks antipoison back up. */
    fun reset(player: Player) {
        states.remove(player)
        VarPlayerIntMapSetter.set(player, varbits.stamina_active, 0)
        val left = player.vars[PotionVars.antipoison_ticks]
        if (left > 0) {
            start(player, TimedEffect.Antipoison, left)
        }
    }

    private companion object {
        val OVERLOAD_STATS =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)
        val MENAPHITE_STATS = OVERLOAD_STATS + stats.hitpoints
    }
}
