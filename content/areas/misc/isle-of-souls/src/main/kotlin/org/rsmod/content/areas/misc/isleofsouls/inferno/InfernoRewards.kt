package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.event.AnnouncementMessages
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.masteries.MasteryKills
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.league_progress
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/** The league tasks that the Inferno feeds. */
class InfernoCompletions
@Inject
constructor(private val tracker: LeagueTaskTracker, private val masteryTasks: LeagueMasteryTasks) {
    /** [player] gave up their first fire cape. */
    fun sacrificed(player: Player) {
        tracker.complete(player, TASK_SACRIFICE)
    }

    /**
     * [player] was inside the Inferno when TzKal-Zuk died. The run counts towards the "complete the
     * Inferno N times" tasks only when [countRun] is set: those counters are shared by a team, so
     * the rewards set it for one member of each team, not for all of them.
     */
    fun completed(player: Player, countRun: Boolean = true) {
        masteryTasks.complete(player, MasteryKills.SLOT_ZUK)
        if (!countRun) {
            return
        }
        tracker.addProgress(player, TASK_COMPLETE_FIVE, league_progress.inferno_5, 5)
        tracker.addProgress(player, TASK_COMPLETE_TEN, league_progress.inferno_10, 10)
        tracker.addProgress(player, TASK_COMPLETE_FIFTEEN, league_progress.inferno_15, 15)
    }

    companion object {
        const val TASK_SACRIFICE = 661
        const val TASK_COMPLETE_FIVE = 667
        const val TASK_COMPLETE_TEN = 668
        const val TASK_COMPLETE_FIFTEEN = 669
    }
}

/** What the Inferno gives everyone who is inside when TzKal-Zuk dies. */
class InfernoRewards
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val announcer: LeagueAnnouncer,
    private val teams: LeagueTeams,
    private val completions: InfernoCompletions,
    private val collectionLog: CollectionLog,
) {
    /**
     * Gives each of [players] the infernal cape, the Tokkul and a chance at the pet, finishes their
     * tasks and tells the isle which team won.
     */
    fun win(players: List<Player>, run: InfernoRun) {
        // The "complete the Inferno" counters belong to the team: count the run once for each.
        val counted = HashSet<Any>()
        for (player in players) {
            val countRun = counted.add(teams.teamOf(player) ?: player.username)
            give(player, inferno_objs.infernal_cape, 1)
            give(player, cave_objs.tokkul, TOKKUL_REWARD)
            petRoll(player)
            player.mes("Congratulations! You have defeated TzKal-Zuk and completed the Inferno.")
            completions.completed(player, countRun)
            player.strongQueue(inferno_queues.win, 1)
        }

        val first = players.firstOrNull() ?: return
        val who = AnnouncementMessages.who(teams.nameOf(first), teams.teamOf(first)?.name)
        announcer.broadcast(
            "${LeagueChat.blue("League:")} $who has defeated TzKal-Zuk in the Inferno" +
                (if (run.mode == InfernoMode.Shortened) " (shortened)" else "") +
                "."
        )
    }

    /**
     * One chance in [PET_ODDS] of Jal-Nib-Rek, for a win and for each infernal cape handed in.
     * Returns whether the pet came.
     */
    fun petRoll(player: Player): Boolean {
        if (random.of(PET_ODDS) != 0) {
            return false
        }
        give(player, inferno_objs.pet, 1)
        announcer.pet(player, inferno_objs.pet)
        player.mes("You have a funny feeling like you're being followed.")
        return true
    }

    private fun give(player: Player, obj: ObjType, count: Int) {
        player.invAddOrDrop(objRepo, obj, count = count)
        collectionLog.receive(player, obj.id, count)
    }

    companion object {
        /** The wiki: the most Tokkul the Inferno gives (for the full clear). */
        const val TOKKUL_REWARD = 16_440

        /** The wiki: the pet is a 1 in 100 chance, with no Slayer task for Zuk to improve it. */
        const val PET_ODDS = 100
    }
}
