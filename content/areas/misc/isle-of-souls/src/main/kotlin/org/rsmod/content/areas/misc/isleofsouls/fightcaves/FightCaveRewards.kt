package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.event.AnnouncementMessages
import org.rsmod.content.areas.misc.isleofsouls.event.LeagueAnnouncer
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType

/** What the cave gives everyone who is inside when Jad dies. */
class FightCaveRewards
@Inject
constructor(
    private val objRepo: ObjRepository,
    private val random: GameRandom,
    private val announcer: LeagueAnnouncer,
    private val teams: LeagueTeams,
    private val completions: FightCaveCompletions,
) {
    fun win(players: List<Player>, run: FightCaveRun) {
        for (player in players) {
            give(player, cave_objs.fire_cape, 1)
            give(player, cave_objs.tokkul, TOKKUL_REWARD)
            if (random.of(PET_ODDS) == 0) {
                give(player, cave_objs.jad_pet, 1)
                player.mes("You have a funny feeling like you're being followed.")
            }
            player.mes("Congratulations! You have defeated TzTok-Jad and completed the Fight Cave.")
            completions.completed(player)
            player.strongQueue(cave_queues.win, 1)
        }

        val first = players.firstOrNull() ?: return
        val who = AnnouncementMessages.who(teams.nameOf(first), teams.teamOf(first)?.name)
        announcer.broadcast(
            "${LeagueChat.blue("League:")} $who has defeated TzTok-Jad in the Fight Cave" +
                (if (run.mode == FightCaveMode.Shortened) " (shortened)" else "") +
                "."
        )
    }

    private fun give(player: Player, obj: ObjType, count: Int) {
        player.invAddOrDrop(objRepo, obj, count = count)
    }

    private companion object {
        const val TOKKUL_REWARD = 8032

        /** The pet is a 1 in this many chance for each player. */
        const val PET_ODDS = 200
    }
}
