package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles

/**
 * Event settings that the host changes by editing `.data/league/event-settings.json`:
 * ```
 * { "fightCaveMode": "shortened" }
 * ```
 *
 * The file is read once when the server starts and every key is optional. Players never see a
 * choice: the mode decides which wave file every run uses.
 */
class FightCaveSettings(val mode: FightCaveMode = DEFAULT_MODE) {
    companion object {
        val DEFAULT_MODE = FightCaveMode.Full
        const val FILE = "event-settings.json"

        /**
         * Reads [FILE] from [dir]. A missing file means the defaults; an invalid one is an error.
         */
        fun load(dir: Path = LeagueFiles.DATA_DIR): FightCaveSettings {
            val file = dir.resolve(FILE)
            if (!Files.exists(file)) {
                return FightCaveSettings()
            }
            return try {
                parse(Files.readString(file))
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Could not read ${file.toAbsolutePath()}: ${e.message}. " +
                        "Fix or move the file and start the server again.",
                    e,
                )
            }
        }

        fun parse(text: String): FightCaveSettings {
            val root = ObjectMapper().readTree(text)
            if (root == null || !root.isObject) {
                throw IllegalArgumentException("the file must hold a JSON object")
            }
            val raw = root.get("fightCaveMode") ?: return FightCaveSettings()
            val mode =
                FightCaveMode.fromKey(raw.asText())
                    ?: throw IllegalArgumentException(
                        "fightCaveMode must be \"full\" or \"shortened\", not ${raw}"
                    )
            return FightCaveSettings(mode)
        }
    }
}
