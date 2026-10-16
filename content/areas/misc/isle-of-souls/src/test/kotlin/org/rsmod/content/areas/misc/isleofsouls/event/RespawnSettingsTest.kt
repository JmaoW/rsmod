package org.rsmod.content.areas.misc.isleofsouls.event

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RespawnSettingsTest {
    @Test
    fun `a missing key leaves the speedup off`() {
        assertEquals(1.0, RespawnSettings.parse("""{ "fightCaveMode": "shortened" }""").speed)
    }

    @Test
    fun `the speed is read from the shared event settings file`() {
        assertEquals(3.0, RespawnSettings.parse("""{ "respawnSpeed": 3.0 }""").speed)
        assertEquals(2.0, RespawnSettings.parse("""{ "respawnSpeed": 2 }""").speed)
    }

    @Test
    fun `a missing file leaves the speedup off`() {
        val dir = Files.createTempDirectory("respawn-settings")
        assertEquals(1.0, RespawnSettings.load(dir).speed)
    }

    @Test
    fun `a speed that is not a number above zero is an error`() {
        for (bad in listOf("0", "-1", "\"fast\"", "null")) {
            assertThrows(IllegalArgumentException::class.java) {
                RespawnSettings.parse("""{ "respawnSpeed": $bad }""")
            }
        }
    }
}
