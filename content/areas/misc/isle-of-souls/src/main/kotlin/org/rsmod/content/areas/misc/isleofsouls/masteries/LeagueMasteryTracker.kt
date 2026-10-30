package org.rsmod.content.areas.misc.isleofsouls.masteries

import jakarta.inject.Inject
import org.rsmod.api.combat.mastery.CombatMasteries
import org.rsmod.api.combat.mastery.CombatMasteryProvider
import org.rsmod.api.combat.mastery.MasteryStyle
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/** The outcome of confirming a mastery unlock. */
enum class MasteryResult {
    Unlocked,
    NothingViewed,
    AlreadyUnlocked,
    PreviousTierMissing,
    NotEnoughPoints,
}

/**
 * Tracks which combat masteries a player has unlocked and how many mastery points they can spend.
 *
 * Unlocked tiers live in the client's own `league_combat_mastery_*_progress` varbits. Points are
 * never stored: they are worked out from the finished mastery tasks (the `point_unlock` varbits)
 * and the tiers already unlocked, then copied into the two varbits the client shows. Mastery points
 * are separate from League points.
 *
 * This is also the [CombatMasteryProvider] that tells combat code which tiers are unlocked.
 */
class LeagueMasteryTracker @Inject constructor() : CombatMasteryProvider {
    override fun tier(player: Player, style: MasteryStyle): Int =
        player.vars[MasteryVarBits.progress(style)]

    /** Tiers unlocked across all three paths, which is also the points spent. */
    fun unlocked(player: Player): Int = MasteryStyle.entries.sumOf { tier(player, it) }

    /** Mastery tasks [player] has finished, as the `point_unlock` varbits show them. */
    fun completedTasks(player: Player): Int =
        MasteryVarBits.point_unlock.count { player.vars[it] >= 1 }

    /** Points earned from finished tasks so far, spent or not. */
    fun pointsEarned(player: Player): Int = completedTasks(player) * MasteryData.pointsPerTask

    /** Points [player] can spend now. */
    fun pointsToSpend(player: Player): Int =
        (pointsEarned(player) - unlocked(player) * MasteryData.UNLOCK_COST).coerceAtLeast(0)

    /** Copies the worked-out totals into the varbits the client shows. */
    fun syncPoints(player: Player) {
        VarPlayerIntMapSetter.set(player, MasteryVarBits.points_earned, completedTasks(player))
        VarPlayerIntMapSetter.set(player, MasteryVarBits.points_to_spend, pointsToSpend(player))
    }

    /** The mastery whose click zone was last clicked, ready for [confirm]. */
    fun viewedChoice(player: Player): MasteryChoice? =
        MasteryData.choiceForCard(player.vars[MasteryVarBits.last_viewed] - 1)

    /** Remembers [card] as the mastery being viewed, ready for [confirm]. */
    fun view(player: Player, card: Int) {
        if (MasteryData.choiceForCard(card) == null) {
            return
        }
        VarPlayerIntMapSetter.set(player, MasteryVarBits.last_viewed, card + 1)
    }

    /** Unlocks the mastery last passed to [view], if the player is allowed to. */
    fun confirm(player: Player): MasteryResult {
        val choice = viewedChoice(player) ?: return MasteryResult.NothingViewed
        val current = tier(player, choice.style)

        val failure =
            when {
                choice.tier <= current -> MasteryResult.AlreadyUnlocked
                choice.tier > current + 1 -> MasteryResult.PreviousTierMissing
                pointsToSpend(player) < MasteryData.UNLOCK_COST -> MasteryResult.NotEnoughPoints
                else -> null
            }
        if (failure != null) {
            return failure
        }

        VarPlayerIntMapSetter.set(player, MasteryVarBits.progress(choice.style), choice.tier)
        syncPoints(player)
        player.requestAutosave()
        return MasteryResult.Unlocked
    }

    /** Sets the unlocked tier of one path directly. Used by dev commands. */
    fun setTier(player: Player, style: MasteryStyle, tier: Int) {
        val clamped = tier.coerceIn(0, CombatMasteries.MAX_TIER)
        VarPlayerIntMapSetter.set(player, MasteryVarBits.progress(style), clamped)
        syncPoints(player)
    }

    /** Clears every unlocked tier and the viewed mastery. Used by dev commands. */
    fun reset(player: Player) {
        for (style in MasteryStyle.entries) {
            VarPlayerIntMapSetter.set(player, MasteryVarBits.progress(style), 0)
        }
        VarPlayerIntMapSetter.set(player, MasteryVarBits.last_viewed, 0)
        syncPoints(player)
    }
}
