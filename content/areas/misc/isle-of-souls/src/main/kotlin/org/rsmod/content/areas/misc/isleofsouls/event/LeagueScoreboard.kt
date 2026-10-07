package org.rsmod.content.areas.misc.isleofsouls.event

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson

/** One line of the scoreboard. */
data class ScoreboardRow(
    val rank: Int,
    val name: String,
    val points: Int,
    val tasks: Int,
    val players: List<String>,
)

/** Everything the scoreboard shows. [frozenAt] is set while the standings are frozen. */
data class ScoreboardData(val frozenAt: Long?, val updatedAt: Long, val rows: List<ScoreboardRow>)

/**
 * A very basic live scoreboard for a second monitor or a stream. Whenever the standings change the
 * server rewrites two files next to the roster: `leaderboard.html`, a self-contained page that
 * refreshes itself every few seconds (open it from disk in a browser), and `leaderboard.json` with
 * the same data. It shows the official standings, so a freeze shows here too.
 */
class LeagueScoreboard
@Inject
constructor(
    private val teams: LeagueTeams,
    private val standings: LeagueStandings,
    private val event: LeagueEvent,
) {
    private var dir: Path? = null

    /** Starts writing to [dir] and rewrites the files after every change from now on. */
    fun start(dir: Path) {
        this.dir = dir
        teams.onChange(::refresh)
        event.onChange(::refresh)
        refresh()
    }

    fun refresh() {
        val folder = dir ?: return
        val data = current()
        try {
            LeagueFiles.writeAtomically(folder.resolve(JSON_FILE), ScoreboardRender.json(data))
            LeagueFiles.writeAtomically(folder.resolve(HTML_FILE), ScoreboardRender.html(data))
        } catch (e: IOException) {
            logger.error(e) { "Could not write the scoreboard to $folder." }
        }
    }

    fun current(): ScoreboardData {
        val rows =
            standings.official().map { standing ->
                val players =
                    teams.team(standing.teamId)?.let(teams::memberNames).orEmpty().map { it.name }
                ScoreboardRow(
                    standing.rank,
                    standing.name,
                    standing.points,
                    standing.tasks,
                    players,
                )
            }
        return ScoreboardData(event.frozenAt, System.currentTimeMillis(), rows)
    }

    private companion object {
        const val JSON_FILE = "leaderboard.json"
        const val HTML_FILE = "leaderboard.html"
        private val logger = InlineLogger()
    }
}

/** Turns [ScoreboardData] into the two files. Pure, so it can be tested without a server. */
object ScoreboardRender {
    private val clockTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun json(data: ScoreboardData): String {
        val root = teamJson.createObjectNode()
        root.put("frozen", data.frozenAt != null)
        data.frozenAt?.let { root.put("frozenAt", it) }
        root.put("updatedAt", data.updatedAt)
        val rows = root.putArray("teams")
        for (row in data.rows) {
            val node = rows.addObject()
            node.put("rank", row.rank)
            node.put("name", row.name)
            node.put("points", row.points)
            node.put("tasks", row.tasks)
            val players = node.putArray("players")
            row.players.forEach(players::add)
        }
        return teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    fun html(data: ScoreboardData): String {
        val banner =
            if (data.frozenAt == null) {
                ""
            } else {
                "<p class=\"frozen\">Standings frozen at ${time(data.frozenAt)}</p>"
            }
        val body =
            if (data.rows.isEmpty()) {
                "<tr><td colspan=\"5\" class=\"empty\">No teams yet.</td></tr>"
            } else {
                data.rows.joinToString("\n") { row ->
                    "<tr><td class=\"rank\">${row.rank}</td><td class=\"team\">${escape(row.name)}</td>" +
                        "<td class=\"num\">${row.points}</td><td class=\"num\">${row.tasks}</td>" +
                        "<td>${escape(row.players.joinToString(", "))}</td></tr>"
                }
            }
        return """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="refresh" content="10">
            <title>League standings</title>
            <style>
            body { font-family: system-ui, sans-serif; background: #14181c; color: #e8e6df; margin: 2rem; }
            h1 { font-weight: 500; margin: 0 0 1rem; }
            table { border-collapse: collapse; width: 100%; max-width: 60rem; }
            th, td { text-align: left; padding: 0.55rem 0.9rem; border-bottom: 1px solid #2a3138; }
            th { color: #8a949c; font-weight: 500; font-size: 0.85rem; }
            td.rank { width: 3rem; color: #8a949c; }
            td.team { font-weight: 600; color: #20c2fe; }
            td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; }
            td.empty { color: #8a949c; }
            .frozen { color: #f0b429; margin: 0 0 1rem; }
            .updated { color: #8a949c; font-size: 0.85rem; margin-top: 1rem; }
            </style>
            </head>
            <body>
            <h1>League standings</h1>
            $banner
            <table>
            <tr><th>#</th><th>Team</th><th class="num">Points</th><th class="num">Tasks</th><th>Players</th></tr>
            $body
            </table>
            <p class="updated">Updated ${time(data.updatedAt)}</p>
            </body>
            </html>
            """
            .trimIndent()
    }

    private fun time(millis: Long): String = clockTime.format(Instant.ofEpochMilli(millis))

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
