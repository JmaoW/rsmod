package org.rsmod.content.areas.misc.isleofsouls.waves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveFileTest {
    private class Boom(message: String) : RuntimeException(message)

    private val monsters = mapOf("a" to 1, "b" to 2)

    private fun parse(text: String): List<Wave<Int>> =
        WaveFile.parse(text, "test.toml", monsters::get) { Boom(it) }

    private fun reject(text: String): String =
        assertThrows(Boom::class.java) { parse(text) }.message!!

    private fun wave(number: Int, vararg keys: String, boss: Boolean = false): String =
        "[[wave]]\nnumber = $number\nmonsters = [${keys.joinToString { "\"$it\"" }}]\n" +
            (if (boss) "boss = true\n" else "") +
            "\n"

    @Test
    fun `a valid file reads the waves, their monsters and the boss flag`() {
        val waves = parse(wave(1, "a", "b") + wave(2, "b", boss = true))

        assertEquals(2, waves.size)
        assertEquals(listOf(1, 2), waves[0].monsters)
        assertFalse(waves[0].boss)
        assertTrue(waves[1].boss)
    }

    @Test
    fun `an error comes out as the exception the game asked for`() {
        assertTrue("no [[wave]]" in reject("nothing = 1"))
    }

    @Test
    fun `an unknown key, an empty wave and a number gap are rejected`() {
        assertTrue("`zzz`" in reject(wave(1, "zzz") + wave(2, "a", boss = true)))
        assertTrue("no monsters" in reject(wave(1) + wave(2, "a", boss = true)))
        assertTrue("expected" in reject(wave(2, "a", boss = true)))
    }

    @Test
    fun `only the last wave can be the boss and it must be one`() {
        assertTrue("only the last" in reject(wave(1, "a", boss = true) + wave(2, "a", boss = true)))
        assertTrue("must be the boss" in reject(wave(1, "a") + wave(2, "a")))
    }

    @Test
    fun `a wave list is indexed from one`() {
        val list = WaveList(listOf(Wave(1, listOf(7)), Wave(2, listOf(8), boss = true)))

        assertEquals(2, list.count)
        assertEquals(listOf(8), list[2].monsters)
    }
}
