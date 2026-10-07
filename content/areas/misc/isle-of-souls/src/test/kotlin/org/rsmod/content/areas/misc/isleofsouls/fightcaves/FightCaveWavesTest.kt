package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightCaveWavesTest {
    private val boss =
        """
        [[wave]]
        number = 2
        monsters = ["tztok_jad"]
        boss = true
        """
            .trimIndent()

    private fun reject(text: String): String =
        assertThrows(FightCaveWaveException::class.java) { FightCaveWaves.parse(text, "test.toml") }
            .message!!

    @Test
    fun `the full file has the real 63 waves ending with Jad`() {
        val waves = FightCaveWaves.load(FightCaveMode.Full)

        assertEquals(63, waves.count)
        assertEquals(listOf(FightCaveMonster.TzKih), waves[1].monsters)
        assertEquals(listOf(FightCaveMonster.Jad), waves[63].monsters)
        assertTrue(waves[63].boss)
        assertFalse(waves[62].boss)
    }

    @Test
    fun `every full wave is made of monsters worth the wave number`() {
        val waves = FightCaveWaves.load(FightCaveMode.Full)

        for (wave in waves.waves.dropLast(1)) {
            assertEquals(wave.number, wave.monsters.sumOf { it.points }, "wave ${wave.number}")
            assertTrue(wave.monsters.size <= 6, "wave ${wave.number} has too many monsters")
        }
    }

    @Test
    fun `the shortened file loads with eight waves and Jad`() {
        val waves = FightCaveWaves.load(FightCaveMode.Shortened)

        assertEquals(9, waves.count)
        assertEquals(listOf(FightCaveMonster.Jad), waves[9].monsters)
        assertTrue(waves.waves.dropLast(1).none { FightCaveMonster.Jad in it.monsters })
    }

    @Test
    fun `an unknown monster is rejected`() {
        val message =
            reject(
                """
                [[wave]]
                number = 1
                monsters = ["tz_kih", "dragon"]
                """
                    .trimIndent() + "\n" + boss.replace("2", "2")
            )
        assertTrue("dragon" in message, message)
    }

    @Test
    fun `a wave with no monsters is rejected`() {
        val message = reject("[[wave]]\nnumber = 1\nmonsters = []\n")
        assertTrue("no monsters" in message, message)
    }

    @Test
    fun `wave numbers must rise by one`() {
        val text =
            """
            [[wave]]
            number = 1
            monsters = ["tz_kih"]

            [[wave]]
            number = 3
            monsters = ["tztok_jad"]
            boss = true
            """
                .trimIndent()
        assertTrue("wave 3" in reject(text))
    }

    @Test
    fun `the last wave must be the boss`() {
        val message = reject("[[wave]]\nnumber = 1\nmonsters = [\"tz_kih\"]\n")
        assertTrue("boss" in message, message)
    }

    @Test
    fun `only the last wave can be a boss wave`() {
        val text =
            """
            [[wave]]
            number = 1
            monsters = ["tztok_jad"]
            boss = true

            """
                .trimIndent() + "\n" + boss
        assertTrue("only the last" in reject(text))
    }

    @Test
    fun `a file with no waves is rejected`() {
        assertTrue("no [[wave]]" in reject("title = \"nothing\""))
    }

    @Test
    fun `settings default to the full cave and accept shortened`() {
        assertEquals(FightCaveMode.Full, FightCaveSettings().mode)
        assertEquals(FightCaveMode.Full, FightCaveSettings.parse("{}").mode)
        assertEquals(
            FightCaveMode.Shortened,
            FightCaveSettings.parse("""{ "fightCaveMode": "Shortened" }""").mode,
        )
        assertThrows(IllegalArgumentException::class.java) {
            FightCaveSettings.parse("""{ "fightCaveMode": "huge" }""")
        }
    }
}
