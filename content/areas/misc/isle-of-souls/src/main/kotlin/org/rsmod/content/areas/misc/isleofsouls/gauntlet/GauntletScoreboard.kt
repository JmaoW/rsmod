package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles
import org.rsmod.content.areas.misc.isleofsouls.teams.teamJson
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * The lobby scoreboard (interface 639): the reader's completions, deaths and best time for the
 * Gauntlet and the Corrupted Gauntlet beside the world's (wiki: "It shows the reader's completions,
 * deaths, and best times ... Players can also use the scoreboard to view global stats").
 *
 * The world's totals are kept in `.data/league/gauntlet-scoreboard.json`; a player's own are saved
 * with them (`isle_gauntlet_done`, `_deaths`, `_best` and their Corrupted twins).
 */
@Singleton
class GauntletScoreboard @Inject constructor(private val eventBus: EventBus) {
    private val logger = InlineLogger()

    /** One version's totals across everyone who has played. */
    class Totals(var completions: Int = 0, var deaths: Int = 0, var bestTicks: Int = 0)

    /** Where the totals are kept. Tests point this somewhere else. */
    var file: Path? = LeagueFiles.DATA_DIR.resolve(FILE)
        set(value) {
            field = value
            loaded = false
        }

    private var loaded = false
    private val totals = mapOf(GauntletMode.Regular to Totals(), GauntletMode.Corrupted to Totals())

    fun totals(mode: GauntletMode): Totals {
        load()
        return totals.getValue(mode)
    }

    /** A run was won in [ticks] ticks. */
    fun completion(mode: GauntletMode, ticks: Int) {
        val entry = totals(mode)
        entry.completions++
        if (ticks > 0 && (entry.bestTicks == 0 || ticks < entry.bestTicks)) {
            entry.bestTicks = ticks
        }
        save()
    }

    /** A player died in a maze. */
    fun death(mode: GauntletMode) {
        totals(mode).deaths++
        save()
    }

    /** Opens the scoreboard for [player]. */
    fun read(player: Player) {
        player.ifOpenMainModal(gauntlet_interfaces.scoreboard, eventBus)
        for (mode in GauntletMode.entries) {
            val world = totals(mode)
            val corrupted = mode.corrupted
            val lines =
                listOf(
                    if (corrupted) "The Corrupted Gauntlet" else "The Gauntlet",
                    "Your Completions:",
                    count(
                        player.vars[if (corrupted) gauntlet_varps.done_hm else gauntlet_varps.done]
                    ),
                    "Global Completions:",
                    count(world.completions),
                    "Your Deaths:",
                    count(
                        player.vars[
                                if (corrupted) gauntlet_varps.deaths_hm else gauntlet_varps.deaths]
                    ),
                    "Global Deaths:",
                    count(world.deaths),
                    "Your Best Time:",
                    time(
                        player.vars[if (corrupted) gauntlet_varps.best_hm else gauntlet_varps.best]
                    ),
                    "Global Best Time:",
                    time(world.bestTicks),
                )
            val components =
                if (corrupted) gauntlet_components.corrupted else gauntlet_components.regular
            for ((component, text) in components.zip(lines)) {
                player.ifSetText(component, text)
            }
        }
    }

    private fun count(value: Int): String = "%,d".format(value)

    private fun load() {
        if (loaded) {
            return
        }
        loaded = true
        for (entry in totals.values) {
            entry.completions = 0
            entry.deaths = 0
            entry.bestTicks = 0
        }
        val path = file ?: return
        if (!Files.exists(path)) {
            return
        }
        try {
            val root = teamJson.readTree(Files.readString(path))
            for ((mode, entry) in totals) {
                val node = root.path(mode.key)
                entry.completions = node.path("completions").asInt(0)
                entry.deaths = node.path("deaths").asInt(0)
                entry.bestTicks = node.path("bestTicks").asInt(0)
            }
        } catch (e: IOException) {
            logger.error(e) { "Could not read the Gauntlet scoreboard from $path." }
        }
    }

    private fun save() {
        val path = file ?: return
        try {
            Files.createDirectories(path.parent)
            val root = teamJson.createObjectNode()
            for ((mode, entry) in totals) {
                val node = root.putObject(mode.key)
                node.put("completions", entry.completions)
                node.put("deaths", entry.deaths)
                node.put("bestTicks", entry.bestTicks)
            }
            LeagueFiles.writeAtomically(
                path,
                teamJson.writerWithDefaultPrettyPrinter().writeValueAsString(root),
            )
        } catch (e: IOException) {
            logger.error(e) { "Could not save the Gauntlet scoreboard to $path." }
        }
    }

    companion object {
        const val FILE = "gauntlet-scoreboard.json"

        /** m:ss.cc, as the game shows times (a tick is 0.6 seconds); `-` for no time. */
        fun time(ticks: Int): String {
            if (ticks <= 0) {
                return "-"
            }
            val centis = ticks * 60
            val minutes = centis / 6000
            val seconds = (centis % 6000) / 100
            val rest = centis % 100
            return "%d:%02d.%02d".format(minutes, seconds, rest)
        }
    }
}
