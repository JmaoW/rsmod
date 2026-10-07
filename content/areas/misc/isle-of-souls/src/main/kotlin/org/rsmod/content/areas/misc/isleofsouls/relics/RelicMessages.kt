package org.rsmod.content.areas.misc.isleofsouls.relics

import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.blue
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.number
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.red

/** The chat message for the outcome of confirming a relic choice. */
object RelicMessages {
    /**
     * [choice] is the relic that was being viewed, [points] the League Points the player's total
     * stands at, and [onTeam] whether that total is shared with a team.
     */
    fun of(result: RelicResult, choice: RelicChoice?, points: Int, onTeam: Boolean): String =
        when (result) {
            RelicResult.Selected ->
                if (choice == null) {
                    "You have unlocked a new Relic."
                } else {
                    "You have unlocked a new Relic: ${blue(choice.name)}."
                }
            RelicResult.NothingViewed -> notYet("select a Relic to view first.")
            RelicResult.AlreadyUnlocked -> notYet("you have already unlocked this Relic.")
            RelicResult.TierAlreadyChosen ->
                notYet("you have already unlocked a Relic from this tier.")
            RelicResult.PreviousTierMissing ->
                notYet("unlock a Relic from the previous tier first.")
            RelicResult.NotEnoughPoints -> {
                val needed = choice?.let { RelicData.thresholds[it.tier] } ?: 0
                val who = if (onTeam) "your team has" else "you have"
                notYet(
                    "$who ${number(points)} of the ${number(needed)} League Points " +
                        "needed for a Relic from this tier."
                )
            }
        }

    private fun notYet(reason: String): String = "${red("Not yet:")} $reason"
}
