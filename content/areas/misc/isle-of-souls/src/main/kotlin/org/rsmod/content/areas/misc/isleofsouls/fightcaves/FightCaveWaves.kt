package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.content.areas.misc.isleofsouls.waves.Wave
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveFile
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveList

/**
 * The monsters of the Fight Cave. [key] is the name used in the wave files, [points] the value the
 * real game's wave rule gives it (wave `n` is made of monsters worth `n` points in total) and
 * [level] the combat level that decides the order they spawn in.
 */
enum class FightCaveMonster(val key: String, val points: Int, val level: Int) {
    TzKih("tz_kih", points = 1, level = 22),
    TzKek("tz_kek", points = 3, level = 45),
    TokXil("tok_xil", points = 7, level = 90),
    YtMejKot("yt_mejkot", points = 15, level = 180),
    KetZek("ket_zek", points = 31, level = 360),
    Jad("tztok_jad", points = 0, level = 702);

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): FightCaveMonster? = byKey[key]
    }
}

/** One wave of a run. A boss wave is the last one: beating it wins the run. */
typealias FightCaveWave = Wave<FightCaveMonster>

/** Which wave file a run uses. Chosen when the server starts, never by players. */
enum class FightCaveMode(val resource: String, val key: String) {
    Full("fight_caves_waves_full.toml", "full"),
    Shortened("fight_caves_waves_shortened.toml", "shortened");

    companion object {
        fun fromKey(key: String): FightCaveMode? =
            entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

class FightCaveWaveException(message: String) : IllegalArgumentException(message)

/** The ordered waves of one mode. Build one with [FightCaveWaves.parse]. */
class FightCaveWaves(waves: List<FightCaveWave>) : WaveList<FightCaveMonster>(waves) {
    companion object {
        /**
         * Reads a wave file. Rejects an unknown monster key, an empty wave, wave numbers that do
         * not rise by one, and a file whose last wave is not the only boss wave, which must be
         * TzTok-Jad alone.
         */
        fun parse(text: String, source: String = "wave file"): FightCaveWaves {
            val waves =
                WaveFile.parse(text, source, FightCaveMonster::fromKey) {
                    FightCaveWaveException(it)
                }
            if (waves.last().monsters != listOf(FightCaveMonster.Jad)) {
                throw FightCaveWaveException("$source: the boss wave must be TzTok-Jad alone.")
            }
            return FightCaveWaves(waves)
        }

        /** Loads the bundled wave file for [mode]. */
        fun load(mode: FightCaveMode): FightCaveWaves {
            val path = "/org/rsmod/content/areas/misc/isleofsouls/fightcaves/${mode.resource}"
            val stream =
                FightCaveWaves::class.java.getResourceAsStream(path)
                    ?: throw FightCaveWaveException("Missing wave file: $path")
            return parse(stream.readBytes().decodeToString(), mode.resource)
        }
    }
}
