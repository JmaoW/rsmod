package org.rsmod.content.areas.misc.isleofsouls.event

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskCompleted
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.stat.StatType

/**
 * Sends the league's server-wide announcements. What gets announced comes from the
 * [AnnouncementConfig] in `announcements.json`; the functions here are called by whatever notices
 * the thing: task completions, level-ups, deaths, and (once drop tables exist) rare drops.
 *
 * To announce something new, add a function here and a message to [AnnouncementMessages], a switch
 * in [AnnouncementConfig], and call it from the place that sees the event.
 */
class LeagueAnnouncer
@Inject
constructor(
    private val players: PlayerList,
    private val teams: LeagueTeams,
    private val objTypes: ObjTypeList,
) {
    var config: AnnouncementConfig = AnnouncementConfig()
        private set

    private var dir: Path? = null

    /**
     * Reads `announcements.json` from [dir], writing one with the default settings the first time
     * so there is something to edit. A file that exists but is invalid stops the server.
     */
    fun load(dir: Path) {
        this.dir = dir
        val file = dir.resolve(FILE)
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(dir)
                LeagueFiles.writeAtomically(file, config.toJson())
            } catch (e: IOException) {
                logger.error(e) { "Could not write the default $FILE." }
            }
            return
        }
        config =
            try {
                AnnouncementConfig.parse(Files.readString(file))
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                        "Fix or move the file and start the server again.",
                    e,
                )
            }
    }

    /** Re-reads the file. Returns `null` on success, or why it was rejected (nothing changes). */
    fun reload(): String? {
        val file = dir?.resolve(FILE) ?: return "Announcements are not saved to disk."
        if (!Files.exists(file)) {
            return "there is no $FILE yet"
        }
        return try {
            config = AnnouncementConfig.parse(Files.readString(file))
            null
        } catch (e: Exception) {
            e.message?.lineSequence()?.first()?.trim() ?: "the file could not be read"
        }
    }

    /** Sends [text] to every player online. */
    fun broadcast(text: String) {
        for (player in players) {
            player.mes(text, ChatType.Broadcast)
        }
    }

    fun taskCompleted(event: LeagueTaskCompleted) {
        val kind = config.forTask(event.taskId, event.tier, event.firstTeamToComplete) ?: return
        val who = who(event.player)
        broadcast(
            when (kind) {
                TaskAnnouncement.Completed ->
                    AnnouncementMessages.taskCompleted(who, event.tier, event.taskName)
                TaskAnnouncement.FirstTeam ->
                    AnnouncementMessages.firstToComplete(who, event.tier, event.taskName)
            }
        )
    }

    fun levelReached(player: Player, stat: StatType, level: Int) {
        if (!config.announcesLevel(level)) {
            return
        }
        val skill = LeagueChat.titleCase(stat.internalNameValue)
        broadcast(AnnouncementMessages.levelReached(who(player), level, skill))
    }

    fun died(player: Player) {
        if (config.deaths) {
            broadcast(AnnouncementMessages.death(who(player)))
        }
    }

    /**
     * Announces a rare drop if [obj] is on the configured list. Nothing calls this yet: the engine
     * has no drop tables, so the code that rolls drops should call it when it hands one out.
     */
    fun drop(player: Player, obj: ObjType, count: Int) {
        val type = objTypes[obj]
        if (!config.announcesDrop(type.internalName)) {
            return
        }
        broadcast(AnnouncementMessages.drop(who(player), type.name, count))
    }

    fun leadTaken(team: TeamStanding) {
        if (config.leadChanges) {
            broadcast(AnnouncementMessages.leadTaken(team.name, team.points))
        }
    }

    /** The freeze announcement: a line saying so, then the top [TOP_COUNT] teams. */
    fun frozen(standings: List<TeamStanding>) {
        for (line in AnnouncementMessages.frozen(standings.take(TOP_COUNT))) {
            broadcast(line)
        }
    }

    fun unfrozen() {
        broadcast(AnnouncementMessages.unfrozen())
    }

    private fun who(player: Player): String =
        AnnouncementMessages.who(teams.nameOf(player), teams.teamOf(player)?.name)

    companion object {
        const val FILE = "announcements.json"
        private const val TOP_COUNT = 3
        private val logger = InlineLogger()
    }
}
