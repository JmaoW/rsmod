package org.rsmod.content.areas.misc.isleofsouls.inferno

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfernoWavesTest {
    private val full = InfernoWaves.load(InfernoMode.Full)

    private fun count(waves: InfernoWaves, monster: InfernoMonster): Int =
        waves.waves.sumOf { wave -> wave.monsters.count { it == monster } }

    private fun reject(text: String): String =
        assertThrows(InfernoWaveException::class.java) { InfernoWaves.parse(text, "test.toml") }
            .message!!

    @Test
    fun `the full file has the real 69 waves ending with Zuk`() {
        assertEquals(69, full.count)
        assertEquals(
            listOf(
                InfernoMonster.Nibbler,
                InfernoMonster.Nibbler,
                InfernoMonster.Nibbler,
                InfernoMonster.Bat,
            ),
            full[1].monsters,
        )
        assertEquals(listOf(InfernoMonster.Zuk), full[69].monsters)
        assertTrue(full[69].boss)
        assertFalse(full[68].boss)
    }

    @Test
    fun `the full file holds as many of each monster as the wiki counts`() {
        assertEquals(210, count(full, InfernoMonster.Nibbler))
        assertEquals(48, count(full, InfernoMonster.Bat))
        assertEquals(40, count(full, InfernoMonster.Blob))
        assertEquals(36, count(full, InfernoMonster.Melee))
        assertEquals(34, count(full, InfernoMonster.Ranger))
        assertEquals(33, count(full, InfernoMonster.Mager))
        assertEquals(4, count(full, InfernoMonster.Jad))
        assertEquals(1, count(full, InfernoMonster.Zuk))
    }

    @Test
    fun `there are three nibblers a wave, six in waves 3, 8, 17 and 34, none after wave 66`() {
        for (wave in full.waves) {
            val expected =
                when {
                    wave.number > 66 -> 0
                    wave.number in setOf(3, 8, 17, 34) -> 6
                    else -> 3
                }
            assertEquals(
                expected,
                wave.monsters.count { it == InfernoMonster.Nibbler },
                "wave ${wave.number}",
            )
        }
    }

    @Test
    fun `each new monster first appears the wave after a six nibbler wave`() {
        fun first(monster: InfernoMonster) = full.waves.first { monster in it.monsters }.number

        assertEquals(4, first(InfernoMonster.Blob))
        assertEquals(9, first(InfernoMonster.Melee))
        assertEquals(18, first(InfernoMonster.Ranger))
        assertEquals(35, first(InfernoMonster.Mager))
        assertEquals(67, first(InfernoMonster.Jad))
    }

    @Test
    fun `waves 67 and 68 are one Jad and three Jads`() {
        assertEquals(listOf(InfernoMonster.Jad), full[67].monsters)
        assertEquals(List(3) { InfernoMonster.Jad }, full[68].monsters)
        assertEquals(67, full.firstJadWave)
    }

    @Test
    fun `the shortened file is just Zuk for now`() {
        val waves = InfernoWaves.load(InfernoMode.Shortened)

        assertEquals(1, waves.count)
        assertEquals(listOf(InfernoMonster.Zuk), waves[1].monsters)
        assertTrue(waves[1].boss)
        assertNull(waves.firstJadWave)
    }

    @Test
    fun `the full shortened list parses`() {
        val waves = InfernoWaves.parse(FULL_SHORTENED, "test waves")

        assertEquals(9, waves.count)
        assertEquals(listOf(InfernoMonster.Jad), waves[7].monsters)
        assertEquals(List(3) { InfernoMonster.Jad }, waves[8].monsters)
        assertEquals(listOf(InfernoMonster.Zuk), waves[9].monsters)
        assertEquals(7, waves.firstJadWave)
        assertTrue(waves.waves.dropLast(3).none { InfernoMonster.Jad in it.monsters })
    }

    @Test
    fun `a file with no Jad has no first Jad wave`() {
        val waves =
            InfernoWaves.parse(
                """
                [[wave]]
                number = 1
                monsters = ["zuk"]
                boss = true
                """
                    .trimIndent()
            )
        assertNull(waves.firstJadWave)
    }

    @Test
    fun `an unknown monster is rejected`() {
        val message = reject("[[wave]]\nnumber = 1\nmonsters = [\"dragon\"]\nboss = true\n")
        assertTrue("dragon" in message, message)
    }

    @Test
    fun `the boss wave must be Zuk alone`() {
        val message = reject("[[wave]]\nnumber = 1\nmonsters = [\"jad\"]\nboss = true\n")
        assertTrue("TzKal-Zuk alone" in message, message)
    }

    @Test
    fun `Zuk cannot be in an ordinary wave`() {
        val text =
            "[[wave]]\nnumber = 1\nmonsters = [\"zuk\", \"bat\"]\n\n" +
                "[[wave]]\nnumber = 2\nmonsters = [\"zuk\"]\nboss = true\n"
        assertTrue("only be in the boss wave" in reject(text))
    }

    @Test
    fun `the settings read the Inferno mode and ignore the rest of the file`() {
        assertEquals(InfernoMode.Full, InfernoSettings().mode)
        assertEquals(InfernoMode.Full, InfernoSettings.parse("{}").mode)
        assertEquals(
            InfernoMode.Shortened,
            InfernoSettings.parse("""{ "fightCaveMode": "full", "infernoMode": "Shortened" }""")
                .mode,
        )
        assertThrows(IllegalArgumentException::class.java) {
            InfernoSettings.parse("""{ "infernoMode": "tiny" }""")
        }
    }
}

/**
 * The full shortened Inferno (the event file is just Zuk while he is being tested): six waves that
 * show every monster once, a lone Jad, three Jads, then Zuk.
 */
internal val FULL_SHORTENED: String = buildString {
    val waves =
        listOf(
            listOf("nibbler", "nibbler", "nibbler", "bat"),
            listOf("nibbler", "nibbler", "nibbler", "blob", "bat"),
            listOf("nibbler", "nibbler", "nibbler", "melee", "blob"),
            listOf("nibbler", "nibbler", "nibbler", "ranger", "melee"),
            listOf("nibbler", "nibbler", "nibbler", "mager", "ranger"),
            listOf("nibbler", "nibbler", "nibbler", "mager", "ranger", "melee", "blob"),
            listOf("jad"),
            listOf("jad", "jad", "jad"),
            listOf("zuk"),
        )
    waves.forEachIndexed { i, monsters ->
        val list = monsters.joinToString(", ") { "\"$it\"" }
        append("[[wave]]\nnumber = ${i + 1}\nmonsters = [$list]\n")
        if (i == waves.lastIndex) append("boss = true\n")
        append("\n")
    }
}
