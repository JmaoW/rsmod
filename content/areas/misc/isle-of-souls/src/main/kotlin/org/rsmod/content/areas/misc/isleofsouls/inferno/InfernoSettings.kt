package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles

/**
 * The Inferno's part of `.data/league/event-settings.json` (the Fight Cave reads the same file):
 * ```
 * { "fightCaveMode": "shortened", "infernoMode": "shortened" }
 * ```
 *
 * The file is read once when the server starts and every key is optional. Players never see a
 * choice: the mode decides which wave file every run uses.
 */
class InfernoSettings(val mode: InfernoMode = DEFAULT_MODE) {
    companion object {
        val DEFAULT_MODE = InfernoMode.Full

        /** Reads the settings file from [dir]. A missing file means the defaults. */
        fun load(dir: Path = LeagueFiles.DATA_DIR): InfernoSettings {
            val file = dir.resolve(FightCaveSettings.FILE)
            if (!Files.exists(file)) {
                return InfernoSettings()
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

        fun parse(text: String): InfernoSettings {
            val root = ObjectMapper().readTree(text)
            if (root == null || !root.isObject) {
                throw IllegalArgumentException("the file must hold a JSON object")
            }
            val raw = root.get("infernoMode") ?: return InfernoSettings()
            val mode =
                InfernoMode.fromKey(raw.asText())
                    ?: throw IllegalArgumentException(
                        "infernoMode must be \"full\" or \"shortened\", not $raw"
                    )
            return InfernoSettings(mode)
        }
    }
}
