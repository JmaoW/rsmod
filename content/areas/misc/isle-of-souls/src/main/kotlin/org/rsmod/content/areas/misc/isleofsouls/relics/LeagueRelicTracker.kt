package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
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
 * The XP multiplier passives (see [xpMultiplier]) and the tier 1 relics (see
 * [LeagueGatheringRelics]) have an effect so far; the other relics are recorded but do not do
 * anything yet.
 */
class LeagueRelicTracker @Inject constructor() {
    /** The 1-based option chosen for [tier], or `0` if none. */
    fun selected(player: Player, tier: Int): Int = player.vars[RelicVarBits.selection[tier]]

    /** The relic whose card was last clicked, ready for [confirm]. */
    fun viewedChoice(player: Player): RelicChoice? =
        RelicData.choiceForCard(player.vars[RelicVarBits.last_viewed] - 1)

    /** Clears every relic choice and the viewed relic. Used by dev commands. */
    fun reset(player: Player) {
        for (selection in RelicVarBits.selection) {
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
            current == choice.option -> STATE_UNLOCKED
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

        val failure =
            when {
                current == choice.option -> RelicResult.AlreadyUnlocked
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
     * The league XP multiplier for [stat]. It starts at 5x and is raised by the passives of tiers 2
     * (8x), 5 (12x) and 7 (16x) once a relic from that tier is chosen. Choosing a tier 3 relic also
     * doubles XP for combat skills, multiplicatively.
     */
    fun xpMultiplier(player: Player, stat: StatType): Double {
        var multiplier = BASE_XP_MULTIPLIER
        for ((tier, value) in XP_MULTIPLIER_TIERS) {
            if (selected(player, tier) != 0) {
                multiplier = value
            }
        }
        if (selected(player, COMBAT_BONUS_TIER) != 0 && combatStats.any { it.isType(stat) }) {
            multiplier *= COMBAT_BONUS
        }
        return multiplier
    }

    /**
     * How many times as likely monster drops are for [player]: 5x once a tier 1 relic is chosen,
     * and 20x once a tier 4 relic is chosen (replacing the 5x, not stacking with it).
     */
    fun dropMultiplier(player: Player): Double {
        var multiplier = 1.0
        for ((tier, value) in DROP_MULTIPLIER_TIERS) {
            if (selected(player, tier) != 0) {
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

    private companion object {
        // State codes read by `league_relic_expanded_view` and `league_relic_not_available`.
        const val STATE_NOT_ENOUGH_POINTS = 0
        const val STATE_UNLOCKED = 1
        const val STATE_SELECTABLE = 2
        const val STATE_PREVIOUS_TIER = 3
        const val STATE_TIER_CHOSEN = 4

        const val BASE_XP_MULTIPLIER = 5.0
        const val COMBAT_BONUS = 2.0
        const val COMBAT_BONUS_TIER = 2

        /** Tier index to the XP multiplier it raises the league to; later entries take priority. */
        val XP_MULTIPLIER_TIERS = listOf(1 to 8.0, 4 to 12.0, 6 to 16.0)

        /** Tier index to the drop multiplier it raises the league to; later entries win. */
        val DROP_MULTIPLIER_TIERS = listOf(0 to 5.0, 3 to 20.0)
    }
}
