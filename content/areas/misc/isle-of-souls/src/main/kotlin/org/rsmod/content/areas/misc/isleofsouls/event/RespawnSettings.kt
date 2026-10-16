package org.rsmod.content.areas.misc.isleofsouls.event

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import org.rsmod.api.repo.RespawnSpeed
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueFiles

/**
 * The respawn part of `.data/league/event-settings.json` (the Fight Cave and Inferno read the same
 * file):
 * ```
 * { "respawnSpeed": 3.0 }
 * ```
 *
 * [speed] is how many times faster everything respawns: npcs, rocks, trees, stalls, crops and
 * ground item spawns. `1.0` (or leaving the key out) turns the speedup off. The file is read once
 * when the server starts; `::respawnspeed <n>` changes it live until the next restart.
 */
class RespawnSettings(val speed: Double = RespawnSpeed.OFF) {
    companion object {
        /** Reads the settings file from [dir]. A missing file means the speedup is off. */
        fun load(dir: Path = LeagueFiles.DATA_DIR): RespawnSettings {
            val file = dir.resolve(FightCaveSettings.FILE)
            if (!Files.exists(file)) {
                return RespawnSettings()
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

        fun parse(text: String): RespawnSettings {
            val root = ObjectMapper().readTree(text)
            if (root == null || !root.isObject) {
                throw IllegalArgumentException("the file must hold a JSON object")
            }
            val raw = root.get("respawnSpeed") ?: return RespawnSettings()
            if (!raw.isNumber || !raw.asDouble().isFinite() || raw.asDouble() <= 0.0) {
                throw IllegalArgumentException("respawnSpeed must be a number above 0, not $raw")
            }
            return RespawnSettings(raw.asDouble())
        }
    }
}
