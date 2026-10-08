package org.rsmod.content.areas.misc.isleofsouls.masteries

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasteryKillsTest {
    @Test
    fun `giants are the monsters named giant, not the ones called giant something`() {
        for (name in
            listOf("Hill Giant", "Moss giant", "Fire giant", "Ice giant", " Cave giant ")) {
            assertTrue(MasteryKills.isGiant(name), name)
        }
        for (name in listOf("Giant rat", "Giant spider", "Giant bat", "Goblin", "Giant Mole")) {
            assertFalse(MasteryKills.isGiant(name), name)
        }
    }

    @Test
    fun `scurrius is matched by name`() {
        assertTrue(MasteryKills.isScurrius("Scurrius"))
        assertTrue(MasteryKills.isScurrius("scurrius "))
        assertFalse(MasteryKills.isScurrius("Scurrius spawn"))
    }
}
