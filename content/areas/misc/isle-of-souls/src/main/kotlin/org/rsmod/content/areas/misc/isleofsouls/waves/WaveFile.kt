package org.rsmod.content.areas.misc.isleofsouls.waves

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper

/** One wave of a run. A [boss] wave is the last one: beating it wins the run. */
data class Wave<M>(val number: Int, val monsters: List<M>, val boss: Boolean = false)

/** The ordered waves of one mode. */
open class WaveList<M>(val waves: List<Wave<M>>) {
    val count: Int
        get() = waves.size

    /** The wave with the one-based [number]. */
    operator fun get(number: Int): Wave<M> = waves[number - 1]
}

/** Reads wave files, which every wave-based fight shares. */
object WaveFile {
    private val mapper = TomlMapper()

    /**
     * Reads a wave file made of `[[wave]]` entries with a `number`, a list of `monsters` (looked up
     * with [resolve]) and an optional `boss`. Rejects an unknown monster key, an empty wave, wave
     * numbers that do not rise by one, and a file whose last wave is not the only boss wave. Errors
     * are thrown as the exception [fail] makes, so each game keeps its own exception type.
     */
    fun <M> parse(
        text: String,
        source: String,
        resolve: (String) -> M?,
        fail: (String) -> RuntimeException,
    ): List<Wave<M>> {
        val root: JsonNode =
            try {
                mapper.readTree(text)
            } catch (e: Exception) {
                throw fail("$source is not valid TOML: ${e.message}")
            }
        val entries = root.get("wave")
        if (entries == null || !entries.isArray || entries.isEmpty) {
            throw fail("$source has no [[wave]] entries.")
        }

        val waves = ArrayList<Wave<M>>()
        for (entry in entries) {
            val number = entry.get("number")?.takeIf { it.isInt }?.asInt()
            if (number == null) {
                throw fail("$source: a wave has no whole-number `number`.")
            }
            val expected = waves.size + 1
            if (number != expected) {
                throw fail("$source: wave $number comes where wave $expected is expected.")
            }
            val names = entry.get("monsters")
            if (names == null || !names.isArray || names.isEmpty) {
                throw fail("$source: wave $number has no monsters.")
            }
            val monsters =
                names.map { name ->
                    resolve(name.asText())
                        ?: throw fail(
                            "$source: wave $number has an unknown monster `${name.asText()}`."
                        )
                }
            val boss = entry.get("boss")?.asBoolean() ?: false
            waves += Wave(number, monsters, boss)
        }

        if (!waves.last().boss) {
            throw fail("$source: the last wave must be the boss wave.")
        }
        if (waves.dropLast(1).any { it.boss }) {
            throw fail("$source: only the last wave can be the boss wave.")
        }
        return waves
    }
}
