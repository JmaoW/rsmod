package org.rsmod.content.areas.misc.isleofsouls.event

import com.fasterxml.jackson.databind.JsonNode
import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson

/**
 * Whether the event's standings are frozen. Freezing is manual: it takes a snapshot of the
 * standings at that moment and every ranking display shows the snapshot until an admin unfreezes.
 * Teams keep playing and earning in the meantime; only the official standings stand still.
 *
 * The state is saved to `event.json` so a restart does not unfreeze. Until [load] is called nothing
 * touches the disk. Everything here runs on the game thread.
 */
class LeagueEvent(private val clock: () -> Long) {
    @Inject constructor() : this(System::currentTimeMillis)

    private var snapshot: List<TeamStanding>? = null
    private var dir: Path? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** When the standings were frozen, in epoch milliseconds, or `null` when they are live. */
    var frozenAt: Long? = null
        private set

    val frozen: Boolean
        get() = snapshot != null

    /** The standings as they were when frozen, or `null` while they are live. */
    fun frozenStandings(): List<TeamStanding>? = snapshot

    /**
     * Starts saving to [dir] and reads the saved state. A file that exists but cannot be read stops
     * the server: guessing "not frozen" could quietly unfreeze a finished event.
     */
    fun load(dir: Path) {
        this.dir = dir
        val file = dir.resolve(FILE)
        if (!Files.exists(file)) {
            return
        }
        try {
            val state = parse(Files.readString(file))
            snapshot = state?.second
            frozenAt = state?.first
        } catch (e: Exception) {
            throw IllegalStateException(
                "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                    "Fix or move the file and start the server again.",
                e,
            )
        }
        if (frozen) {
            logger.info { "The league standings are frozen (saved in $FILE)." }
        }
    }

    /**
     * Freezes the standings as [standings]. Returns `false`, changing nothing, if already frozen.
     */
    fun freeze(standings: List<TeamStanding>): Boolean {
        if (frozen) {
            return false
        }
        snapshot = standings.toList()
        frozenAt = clock()
        save()
        changed()
        return true
    }

    /** Resumes live standings. Returns `false` if they were not frozen. */
    fun unfreeze(): Boolean {
        if (!frozen) {
            return false
        }
        snapshot = null
        frozenAt = null
        save()
        changed()
        return true
    }

    /** Runs [listener] after every freeze and unfreeze. */
    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    private fun changed() {
        for (listener in listeners) {
            try {
                listener()
            } catch (e: Exception) {
                logger.error(e) { "A league event listener failed." }
            }
        }
    }

    private fun save() {
        val file = dir?.resolve(FILE) ?: return
        try {
            LeagueFiles.writeAtomically(file, toJson())
        } catch (e: IOException) {
            logger.error(e) { "Could not save the event state to $file." }
        }
    }

    private fun toJson(): String {
        val root = teamJson.createObjectNode()
        val standings = snapshot
        root.put("frozen", standings != null)
        if (standings != null) {
            root.put("frozenAt", frozenAt)
            val array = root.putArray("standings")
            for (standing in standings) {
                val node = array.addObject()
                node.put("teamId", standing.teamId)
                node.put("name", standing.name)
                node.put("points", standing.points)
                node.put("tasks", standing.tasks)
                standing.score.reachedAt?.let { node.put("reachedAt", it) }
                node.put("rank", standing.rank)
            }
        }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    private companion object {
        const val FILE = "event.json"

        private val logger = InlineLogger()

        /** The frozen time and standings, or `null` when the file says the event is not frozen. */
        fun parse(json: String): Pair<Long, List<TeamStanding>>? {
            val root = teamJson.readTree(json)
            require(root != null && root.isObject) { "The file needs to be a JSON object." }
            val frozen = root.get("frozen")
            require(frozen != null && frozen.isBoolean) {
                "The file needs a true/false \"frozen\"."
            }
            if (!frozen.booleanValue()) {
                return null
            }

            val at = root.get("frozenAt")
            require(at != null && at.isIntegralNumber) {
                "A frozen event needs a \"frozenAt\" time."
            }
            val array = root.get("standings")
            require(array != null && array.isArray) { "A frozen event needs a \"standings\" list." }
            return at.longValue() to array.map(::parseStanding)
        }

        private fun parseStanding(node: JsonNode): TeamStanding {
            fun int(field: String): Int {
                val value = node.get(field)
                require(value != null && value.isInt) {
                    "A standing needs a whole-number \"$field\"."
                }
                return value.intValue()
            }

            val name = node.get("name")
            require(name != null && name.isTextual) { "A standing needs a \"name\"." }
            val reached = node.get("reachedAt")
            require(reached == null || reached.isIntegralNumber) { "\"reachedAt\" must be a time." }
            val score =
                TeamScore(
                    teamId = int("teamId"),
                    name = name.asText(),
                    points = int("points"),
                    tasks = int("tasks"),
                    reachedAt = reached?.longValue(),
                )
            return TeamStanding(score, int("rank"))
        }
    }
}
