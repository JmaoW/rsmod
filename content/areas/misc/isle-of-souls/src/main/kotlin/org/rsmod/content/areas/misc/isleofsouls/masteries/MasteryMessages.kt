package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.blue
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.counted
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat.red

/** The chat messages for combat masteries. */
object MasteryMessages {
    fun taskCompleted(slot: Int, points: Int): String {
        val task = MasteryData.taskNames.getOrNull(slot - 1) ?: "a mastery task"
        return "${blue("Combat Mastery:")} $task. You have earned ${counted(points, "mastery point")}."
    }

    /**
     * The chat message for the outcome of confirming a mastery, whose [choice] was being viewed.
     */
    fun of(result: MasteryResult, choice: MasteryChoice?): String =
        when (result) {
            MasteryResult.Unlocked ->
                if (choice == null) {
                    "You have unlocked a new Combat Mastery."
                } else {
                    "You have unlocked a new Combat Mastery: ${blue(choice.name)}."
                }
            MasteryResult.NothingViewed -> notYet("select a Combat Mastery to view first.")
            MasteryResult.AlreadyUnlocked ->
                notYet("you have already unlocked this Combat Mastery.")
            MasteryResult.PreviousTierMissing -> {
                val path = choice?.style?.name?.lowercase() ?: "same"
                notYet("unlock the previous $path Combat Mastery first.")
            }
            MasteryResult.NotEnoughPoints ->
                notYet("you have not earned enough Combat Mastery points to unlock that.")
        }

    private fun notYet(reason: String): String = "${red("Not yet:")} $reason"
}
