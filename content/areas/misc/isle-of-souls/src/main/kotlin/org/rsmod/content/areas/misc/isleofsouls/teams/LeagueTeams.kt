package org.rsmod.content.areas.misc.isleofsouls.teams

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList

/**
 * What an admin roster change did: the team the player left, and whether `teams.json` was saved.
 */
class Assignment(val previous: Team?, val saved: Boolean)

/** A team member's name as it should be shown in chat, and whether they are logged in. */
class MemberName(val name: String, val online: Boolean)

/**
 * The teams for the league event, and each team's shared task progress.
 *
 * There are two files in the data directory (see [load]), kept apart on purpose so that editing the
 * roster can never touch progress:
 * - `teams.json` is the roster. An admin writes it; the admin commands update it too.
 * - `team-progress.json` is the shared progress. Only the server writes it, and only when a task or
 *   counter changes or an admin runs an explicit reset (which keeps a backup copy first).
 *
 * Until [load] is called nothing touches the disk, so tests and tools can use it in memory.
 * Everything here runs on the game thread.
 */
class LeagueTeams(private val players: PlayerList, private val clock: () -> Long) {
    @Inject constructor(players: PlayerList) : this(players, System::currentTimeMillis)

    private var roster = TeamRoster()
    private var progress = TeamProgressStore()
    private var dir: Path? = null
    private val listeners = mutableListOf<() -> Unit>()

    // The roster file's modified time as of our last read or write. A different time means someone
    // edited it by hand, and saving would silently throw that edit away.
    private var rosterStamp: FileTime? = null

    /** The folder the event data is saved in, or `null` before [load]. */
    val dataDir: Path?
        get() = dir

    val teams: Collection<Team>
        get() = roster.teams

    fun team(id: Int): Team? = roster[id]

    fun teamOf(player: Player): Team? = roster.teamOf(player.username)

    fun teamOf(username: String): Team? = roster.teamOf(username)

    /** Every logged-in player on [team]. */
    fun onlineMembers(team: Team): List<Player> = players.filter { teamOf(it)?.id == team.id }

    fun onlinePlayer(username: String): Player? {
        val key = TeamRoster.normalize(username)
        return players.firstOrNull { TeamRoster.normalize(it.username) == key }
    }

    /**
     * How to show a player in chat. A name written in `teams.json` wins; otherwise the display name
     * the player has in game if they are logged in; otherwise their login name in title case.
     */
    fun nameOf(username: String): String {
        val key = TeamRoster.normalize(username)
        roster.displayName(key)?.let {
            return it
        }
        onlinePlayer(key)?.displayName?.takeIf(String::isNotBlank)?.let {
            return it
        }
        return LeagueChat.titleCase(key)
    }

    fun nameOf(player: Player): String =
        roster.displayName(player.username)
            ?: player.displayName.takeIf(String::isNotBlank)
            ?: LeagueChat.titleCase(player.username)

    /** The members of [team] as they should be shown, in roster order, minus [except]. */
    fun memberNames(team: Team, except: Player? = null): List<MemberName> {
        val skip = except?.let { TeamRoster.normalize(it.username) }
        return team.members
            .filter { it != skip }
            .map { MemberName(nameOf(it), online = onlinePlayer(it) != null) }
    }

    /**
     * Starts saving to [dir] and reads whatever is already there. A file that exists but cannot be
     * read stops the server on purpose: carrying on with an empty roster or empty progress would
     * quietly overwrite the real thing on the next save.
     */
    fun load(dir: Path) {
        this.dir = dir
        Files.createDirectories(dir)

        val rosterFile = dir.resolve(ROSTER_FILE)
        if (Files.exists(rosterFile)) {
            roster = readOrFail(rosterFile, TeamRoster::parse)
            rosterStamp = Files.getLastModifiedTime(rosterFile)
        } else {
            logger.info {
                "No team roster at ${rosterFile.toAbsolutePath()}; starting without teams."
            }
        }

        val progressFile = dir.resolve(PROGRESS_FILE)
        if (Files.exists(progressFile)) {
            progress = readOrFail(progressFile, TeamProgressStore::parse)
        }

        val completed = progress.teamIds.sumOf { progress.completed(it).size }
        logger.info { "Loaded ${roster.teams.size} league teams with $completed completed tasks." }
    }

    /**
     * Re-reads `teams.json` after a hand edit. Returns `null` on success, or why it was rejected; a
     * rejected file leaves the current roster untouched. Progress is never touched.
     */
    fun reloadRoster(): String? {
        val file = dir?.resolve(ROSTER_FILE) ?: return "Teams are not saved to disk."
        if (!Files.exists(file)) {
            return "There is no teams.json yet."
        }
        return try {
            roster = TeamRoster.parse(Files.readString(file))
            rosterStamp = Files.getLastModifiedTime(file)
            changed()
            null
        } catch (e: Exception) {
            e.message?.lineSequence()?.first()?.trim()?.ifEmpty { null }
                ?: "the file could not be read"
        }
    }

    /** Adds an empty team. Throws [IllegalArgumentException] if [id] is taken. */
    fun createTeam(id: Int, name: String): Assignment {
        requireRosterUnchanged()
        roster.create(id, name)
        return Assignment(previous = null, saved = saveRoster()).also { changed() }
    }

    /**
     * Moves [username] onto team [teamId], leaving any team they were on. A non-blank [displayName]
     * is written to the roster for them; otherwise any existing one is kept.
     */
    fun assign(username: String, teamId: Int, displayName: String? = null): Assignment {
        requireRosterUnchanged()
        val previous = roster.assign(username, teamId, displayName)
        return Assignment(previous, saved = saveRoster()).also { changed() }
    }

    /** Takes [username] off their team. */
    fun remove(username: String): Assignment {
        requireRosterUnchanged()
        val previous = roster.remove(username)
        return Assignment(previous, saved = saveRoster()).also { changed() }
    }

    fun isComplete(team: Team, taskId: Int): Boolean = progress.isComplete(team.id, taskId)

    /** Task id to completion time for [team], in completion order. */
    fun completed(team: Team): Map<Int, Long> = progress.completed(team.id)

    /** Records [taskId] for the whole team. Returns `false` if the team already had it. */
    fun complete(team: Team, taskId: Int): Boolean {
        if (!progress.complete(team.id, taskId, clock())) {
            return false
        }
        saveProgress()
        changed()
        return true
    }

    /** Whether any team has completed [taskId]. Used to spot the first team to do a task. */
    fun completedByAnyTeam(taskId: Int): Boolean =
        progress.teamIds.any { progress.isComplete(it, taskId) }

    fun counter(team: Team, name: String): Int = progress.counter(team.id, name)

    /** Adds one to a team counter, up to [cap]. Returns the new total. */
    fun incrementCounter(team: Team, name: String, cap: Int): Int {
        val value = progress.increment(team.id, name, cap)
        saveProgress()
        return value
    }

    /** Sets a team counter back to zero. */
    fun resetCounter(team: Team, name: String) {
        progress.reset(team.id, name)
        saveProgress()
    }

    /** Clears one team's progress. Returns how many completed tasks that removed. */
    fun resetProgress(teamId: Int): Int {
        backupProgress()
        val cleared = progress.clear(teamId)
        saveProgress()
        changed()
        return cleared
    }

    /** Clears every team's progress. Returns how many completed tasks that removed. */
    fun resetAllProgress(): Int {
        backupProgress()
        val cleared = progress.teamIds.toList().sumOf(progress::clear)
        saveProgress()
        changed()
        return cleared
    }

    /**
     * Runs [listener] after anything that can change the standings or the roster: a completed task,
     * a roster change, or a reset. Counter increments do not count, as they happen constantly.
     */
    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    private fun changed() {
        for (listener in listeners) {
            try {
                listener()
            } catch (e: Exception) {
                logger.error(e) { "A league change listener failed." }
            }
        }
    }

    private fun requireRosterUnchanged() {
        val file = dir?.resolve(ROSTER_FILE) ?: return
        val stamp = rosterStamp
        if (stamp != null && Files.exists(file) && Files.getLastModifiedTime(file) != stamp) {
            error("teams.json was edited outside the server. Run ::teamreload first.")
        }
    }

    private fun saveRoster(): Boolean {
        val file = dir?.resolve(ROSTER_FILE) ?: return true
        return try {
            LeagueFiles.writeAtomically(file, roster.toJson())
            rosterStamp = Files.getLastModifiedTime(file)
            true
        } catch (e: IOException) {
            logger.error(e) { "Could not save the team roster to $file." }
            false
        }
    }

    private fun saveProgress() {
        val file = dir?.resolve(PROGRESS_FILE) ?: return
        try {
            LeagueFiles.writeAtomically(file, progress.toJson())
        } catch (e: IOException) {
            logger.error(e) { "Could not save team progress to $file." }
        }
    }

    private fun backupProgress() {
        val file = dir?.resolve(PROGRESS_FILE) ?: return
        if (!Files.exists(file)) {
            return
        }
        val stamp = LocalDateTime.now().format(BACKUP_STAMP)
        val backup = file.resolveSibling("team-progress.$stamp.bak.json")
        try {
            Files.copy(file, backup, REPLACE_EXISTING)
            logger.info { "Backed up team progress to $backup." }
        } catch (e: IOException) {
            logger.error(e) { "Could not back up team progress to $backup." }
        }
    }

    private fun <T> readOrFail(file: Path, parse: (String) -> T): T =
        try {
            parse(Files.readString(file))
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                    "Fix or move the file and start the server again.",
                e,
            )
        }

    companion object {
        const val ROSTER_FILE = "teams.json"
        const val PROGRESS_FILE = "team-progress.json"

        private val logger = InlineLogger()
        private val BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
