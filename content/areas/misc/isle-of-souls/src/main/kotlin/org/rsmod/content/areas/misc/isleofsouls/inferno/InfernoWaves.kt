package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.rsmod.content.areas.misc.isleofsouls.waves.Wave
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveFile
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveList

/**
 * The monsters of the Inferno. [key] is the name used in the wave files. Nibblers, bats, blobs,
 * melee monsters, rangers and magers fill the ordinary waves; Jad and TzKal-Zuk are the last ones.
 */
enum class InfernoMonster(val key: String) {
    Nibbler("nibbler"),
    Bat("bat"),
    Blob("blob"),
    Melee("melee"),
    Ranger("ranger"),
    Mager("mager"),
    Jad("jad"),
    Zuk("zuk");

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): InfernoMonster? = byKey[key]
    }
}

/** One wave of a run. A boss wave is the last one: beating it wins the run. */
typealias InfernoWave = Wave<InfernoMonster>

/** Which wave file a run uses. Chosen when the server starts, never by players. */
enum class InfernoMode(val resource: String, val key: String) {
    Full("inferno_waves_full.toml", "full"),
    Shortened("inferno_waves_shortened.toml", "shortened");

    companion object {
        fun fromKey(key: String): InfernoMode? =
            entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

class InfernoWaveException(message: String) : IllegalArgumentException(message)

/** The ordered waves of one mode. Build one with [InfernoWaves.parse]. */
class InfernoWaves(waves: List<InfernoWave>) : WaveList<InfernoMonster>(waves) {
    /** The first wave that has a Jad in it: the pillars fall when it starts. */
    val firstJadWave: Int?
        get() = waves.firstOrNull { InfernoMonster.Jad in it.monsters }?.number

    companion object {
        /**
         * Reads a wave file. Rejects an unknown monster key, an empty wave, wave numbers that do
         * not rise by one, and a file whose last wave is not the only boss wave. The boss wave must
         * be TzKal-Zuk alone, and Zuk may appear nowhere else.
         */
        fun parse(text: String, source: String = "wave file"): InfernoWaves {
            val waves =
                WaveFile.parse(text, source, InfernoMonster::fromKey) { InfernoWaveException(it) }
            if (waves.last().monsters != listOf(InfernoMonster.Zuk)) {
                throw InfernoWaveException("$source: the boss wave must be TzKal-Zuk alone.")
            }
            if (waves.dropLast(1).any { InfernoMonster.Zuk in it.monsters }) {
                throw InfernoWaveException("$source: TzKal-Zuk can only be in the boss wave.")
            }
            return InfernoWaves(waves)
        }

        /** Loads the bundled wave file for [mode]. */
        fun load(mode: InfernoMode): InfernoWaves {
            val path = "/org/rsmod/content/areas/misc/isleofsouls/inferno/${mode.resource}"
            val stream =
                InfernoWaves::class.java.getResourceAsStream(path)
                    ?: throw InfernoWaveException("Missing wave file: $path")
            return parse(stream.readBytes().decodeToString(), mode.resource)
        }
    }
}
