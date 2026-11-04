package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskVars
import org.rsmod.game.entity.Player
import org.rsmod.game.type.stat.StatType

/** The outcome of confirming a relic choice. */
enum class RelicResult {
    Selected,
    NothingViewed,
    AlreadyUnlocked,
    TierAlreadyChosen,
    PreviousTierMissing,
    NotEnoughPoints,
}

/**
 * Tracks which league relics a player has chosen. Choices live in the client's own
 * `league_relic_selection_N` varbits, so the Relics screen and Leagues tab read them directly.
 *
 * A relic's effect checks [has], which also counts the extra relic picked with Reloaded (stored in
 * the client's `league_relic_selection_other_N` varbits). The tier passives go by [selected].
 */
class LeagueRelicTracker @Inject constructor() {
    /** The 1-based option chosen for [tier], or `0` if none. */
    fun selected(player: Player, tier: Int): Int = player.vars[RelicVarBits.selection[tier]]

    /** The extra option Reloaded picked in [tier], or `0`. */
    fun other(player: Player, tier: Int): Int = player.vars[RelicVarBits.other[tier]]

    /** Whether [player] has relic [option] of [tier], chosen normally or with Reloaded. */
    fun has(player: Player, tier: Int, option: Int): Boolean =
        selected(player, tier) == option || other(player, tier) == option

    /** The tier of the relic Reloaded picked, or `null` if it has not picked one. */
    fun reloadedTier(player: Player): Int? =
        RelicVarBits.other.indices.firstOrNull { other(player, it) != 0 }

    /** The relic whose card was last clicked, ready for [confirm]. */
    fun viewedChoice(player: Player): RelicChoice? =
        RelicData.choiceForCard(player.vars[RelicVarBits.last_viewed] - 1)

    /** Clears every relic choice and the viewed relic. Used by dev commands. */
    fun reset(player: Player) {
        for (selection in RelicVarBits.selection + RelicVarBits.other) {
            VarPlayerIntMapSetter.set(player, selection, 0)
        }
        VarPlayerIntMapSetter.set(player, RelicVarBits.last_viewed, 0)
    }

    /**
     * The state code the client's relic view expects for [choice]. It decides whether the Select
     * button is active, and which message `league_relic_not_available` shows otherwise.
     */
    fun viewState(player: Player, choice: RelicChoice): Int {
        val current = selected(player, choice.tier)
        return when {
            has(player, choice.tier, choice.option) -> STATE_UNLOCKED
            canReload(player, choice) -> STATE_SELECTABLE
            current != 0 -> STATE_TIER_CHOSEN
            choice.tier > 0 && selected(player, choice.tier - 1) == 0 -> STATE_PREVIOUS_TIER
            player.vars[LeagueTaskVars.points_claimed] < RelicData.thresholds[choice.tier] ->
                STATE_NOT_ENOUGH_POINTS
            else -> STATE_SELECTABLE
        }
    }

    /** Remembers [card] as the relic being viewed, ready for [confirm]. */
    fun view(player: Player, card: Int) {
        if (RelicData.choiceForCard(card) == null) {
            return
        }
        VarPlayerIntMapSetter.set(player, RelicVarBits.last_viewed, card + 1)
    }

    /** Applies the relic last passed to [view], if the player is allowed to choose it. */
    fun confirm(player: Player): RelicResult {
        val viewed = player.vars[RelicVarBits.last_viewed]
        val choice = RelicData.choiceForCard(viewed - 1) ?: return RelicResult.NothingViewed
        val current = selected(player, choice.tier)
        val points = player.vars[LeagueTaskVars.points_claimed]

        if (!has(player, choice.tier, choice.option) && canReload(player, choice)) {
            VarPlayerIntMapSetter.set(player, RelicVarBits.other[choice.tier], choice.option)
            player.requestAutosave()
            return RelicResult.Selected
        }

        val failure =
            when {
                has(player, choice.tier, choice.option) -> RelicResult.AlreadyUnlocked
                current != 0 -> RelicResult.TierAlreadyChosen
                choice.tier > 0 && selected(player, choice.tier - 1) == 0 ->
                    RelicResult.PreviousTierMissing
                points < RelicData.thresholds[choice.tier] -> RelicResult.NotEnoughPoints
                else -> null
            }
        if (failure != null) {
            return failure
        }

        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[choice.tier], choice.option)
        player.requestAutosave()
        return RelicResult.Selected
    }

    /**
     * Whether Reloaded lets [player] take [choice] as their extra relic: they have Reloaded, have
     * not used it yet, and [choice] is from an earlier tier than Reloaded's.
     */
    private fun canReload(player: Player, choice: RelicChoice): Boolean =
        choice.tier < RELOADED_TIER &&
            selected(player, RELOADED_TIER) == RELOADED_OPTION &&
            reloadedTier(player) == null

    /**
     * The league XP multiplier for [stat]. It starts at 5x and is raised by the passives of tiers 2
     * (8x), 5 (12x) and 7 (16x) once a relic from that tier is chosen. Choosing a tier 3 relic also
     * doubles XP for combat skills, multiplicatively. The numbers are [EventSettings]'.
     */
    fun xpMultiplier(player: Player, stat: StatType): Double {
        val settings = EventSettings.current
        var multiplier = settings.xpBase
        for ((tier, value) in settings.xpByRelicTier) {
            if (selected(player, tier - 1) != 0) {
                multiplier = value
            }
        }
        val bonusTier = settings.combatXpBonusTier - 1
        if (selected(player, bonusTier) != 0 && combatStats.any { it.isType(stat) }) {
            multiplier *= settings.combatXpBonus
        }
        return multiplier
    }

    /**
     * How many times as likely monster drops are for [player]: 5x once a tier 1 relic is chosen,
     * and 20x once a tier 4 relic is chosen (replacing the 5x, not stacking with it). The numbers
     * are [EventSettings]'.
     */
    fun dropMultiplier(player: Player): Double {
        var multiplier = 1.0
        for ((tier, value) in EventSettings.current.dropByRelicTier) {
            if (selected(player, tier - 1) != 0) {
                multiplier = value
            }
        }
        return multiplier
    }

    private val combatStats =
        listOf(
            stats.attack,
            stats.defence,
            stats.strength,
            stats.hitpoints,
            stats.ranged,
            stats.prayer,
            stats.magic,
        )

    companion object {
        /** Reloaded is tier 3 in the code ("tier 4" in the plan and on the wiki), option 2. */
        const val RELOADED_TIER = 3
        const val RELOADED_OPTION = 2

        // State codes read by `league_relic_expanded_view` and `league_relic_not_available`.
        private const val STATE_NOT_ENOUGH_POINTS = 0
        private const val STATE_UNLOCKED = 1
        private const val STATE_SELECTABLE = 2
        private const val STATE_PREVIOUS_TIER = 3
        private const val STATE_TIER_CHOSEN = 4
    }
}
