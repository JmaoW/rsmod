package org.rsmod.content.areas.misc.isleofsouls.event

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.pvp.PvpSettings
import org.rsmod.content.areas.misc.isleofsouls.pvp.PvpZone
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTier

class EventSettingsTest {
    @Test
    fun `an empty file or a missing file gives the defaults`() {
        for (settings in listOf(EventSettings.parse("{}"), EventSettings())) {
            assertNull(settings.baseXpRate)
            assertEquals(5.0, settings.xpBase)
            assertEquals(mapOf(2 to 8.0, 5 to 12.0, 7 to 16.0), settings.xpByRelicTier)
            assertEquals(2.0, settings.combatXpBonus)
            assertEquals(3, settings.combatXpBonusTier)
            assertEquals(mapOf(1 to 5.0, 4 to 20.0), settings.dropByRelicTier)
            assertEquals(750, settings.relicPointThresholds[1])
            assertEquals(400, settings.taskPoints["master"])
            assertEquals(2, settings.masteryPointsPerTask)
            assertEquals(10.0, settings.revenantEtherMultiplier)
            assertEquals(10.0, settings.zulrahScaleMultiplier)
            assertFalse(settings.gauntletNodesDeplete)
            assertEquals(200, settings.autosaveIntervalTicks)
            assertEquals(50, settings.autosaveRequestDelayTicks)
        }
        val dir = Files.createTempDirectory("event-settings")
        assertEquals(5.0, EventSettings.load(dir).xpBase)
    }

    @Test
    fun `every key is read`() {
        val settings =
            EventSettings.parse(
                """
                {
                  "baseXpRate": 1.5,
                  "xpMultiplier": { "base": 2, "byRelicTier": { "6": 9.5, "1": 3 },
                                    "combatBonus": 1.5, "combatBonusTier": 4 },
                  "dropMultiplier": { "byRelicTier": { "2": 7 } },
                  "relicPointThresholds": [0, 1, 2, 3, 4, 5, 6, 7],
                  "taskPoints": { "hard": 100 },
                  "masteryPointsPerTask": 3,
                  "revenantEtherMultiplier": 4,
                  "zulrahScaleMultiplier": 2.5,
                  "gauntletNodesDeplete": true,
                  "autosave": { "intervalTicks": 100, "requestDelayTicks": 20 }
                }
                """
            )
        assertEquals(1.5, settings.baseXpRate)
        assertEquals(2.0, settings.xpBase)
        assertEquals(listOf(1 to 3.0, 6 to 9.5), settings.xpByRelicTier.toList())
        assertEquals(1.5, settings.combatXpBonus)
        assertEquals(4, settings.combatXpBonusTier)
        assertEquals(mapOf(2 to 7.0), settings.dropByRelicTier)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), settings.relicPointThresholds)
        assertEquals(100, settings.taskPoints["hard"])
        assertEquals(10, settings.taskPoints["easy"])
        assertEquals(3, settings.masteryPointsPerTask)
        assertEquals(4.0, settings.revenantEtherMultiplier)
        assertEquals(2.5, settings.zulrahScaleMultiplier)
        assertEquals(true, settings.gauntletNodesDeplete)
        assertEquals(100, settings.autosaveIntervalTicks)
        assertEquals(20, settings.autosaveRequestDelayTicks)
    }

    @Test
    fun `every pvp zone is closed unless the file opens it`() {
        for (settings in
            listOf(EventSettings.parse("{}"), EventSettings.parse("""{ "pvp": {} }"""))) {
            assertEquals(PvpSettings(), settings.pvp)
            assertTrue(PvpZone.entries.none { settings.pvp.isOpen(it) })
        }
        val some =
            EventSettings.parse("""{ "pvp": { "soulWarsArena": true, "escapeCaves": true } }""")
        assertEquals(PvpSettings(soulWarsArena = true, escapeCaves = true), some.pvp)
        assertTrue(some.pvp.isOpen(PvpZone.SoulWarsArena))
        assertFalse(some.pvp.isOpen(PvpZone.RevenantCaves))
        assertFalse(some.pvp.isOpen(PvpZone.WildernessBossLairs))
        assertTrue(some.pvp.isOpen(PvpZone.EscapeCavesZone))
        assertTrue(some.unknownKeys.isEmpty())
    }

    @Test
    fun `a misspelt pvp key is reported and a non-boolean is refused`() {
        val typo = EventSettings.parse("""{ "pvp": { "revenantCave": true } }""")
        assertEquals(listOf("pvp.revenantCave"), typo.unknownKeys)
        assertFalse(typo.pvp.revenantCaves)

        assertThrows(IllegalArgumentException::class.java) {
            EventSettings.parse("""{ "pvp": { "revenantCaves": "yes" } }""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventSettings.parse("""{ "pvp": true }""")
        }
    }

    @Test
    fun `task points follow the settings`() {
        val before = EventSettings.current
        try {
            assertEquals(10, LeagueTier.Easy.points)
            assertEquals(400, LeagueTier.Master.points)
            EventSettings.current =
                EventSettings.parse("""{ "taskPoints": { "easy": 1, "master": 99 } }""")
            assertEquals(1, LeagueTier.Easy.points)
            assertEquals(30, LeagueTier.Medium.points)
            assertEquals(99, LeagueTier.Master.points)
        } finally {
            EventSettings.current = before
        }
    }

    @Test
    fun `the keys of the other settings are not unknown`() {
        val settings =
            EventSettings.parse(
                """{ "fightCaveMode": "full", "infernoMode": "full", "respawnSpeed": 2 }"""
            )
        assertEquals(emptyList<String>(), settings.unknownKeys)
    }

    @Test
    fun `a misspelt key is reported`() {
        val settings =
            EventSettings.parse(
                """{ "respawnSpeeed": 2, "autosave": { "interval": 5 }, "xpMultiplier": { "bse": 1 } }"""
            )
        assertEquals(
            setOf("respawnSpeeed", "xpMultiplier.bse", "autosave.interval"),
            settings.unknownKeys.toSet(),
        )
    }

    @Test
    fun `bad values are errors`() {
        val bad =
            listOf(
                "[]",
                """{ "baseXpRate": 0 }""",
                """{ "baseXpRate": "fast" }""",
                """{ "xpMultiplier": { "base": -1 } }""",
                """{ "xpMultiplier": { "byRelicTier": { "9": 2 } } }""",
                """{ "xpMultiplier": { "byRelicTier": { "0": 2 } } }""",
                """{ "xpMultiplier": { "byRelicTier": { "two": 2 } } }""",
                """{ "xpMultiplier": { "byRelicTier": { "2": 0 } } }""",
                """{ "xpMultiplier": { "combatBonusTier": 9 } }""",
                """{ "xpMultiplier": [] }""",
                """{ "dropMultiplier": { "byRelicTier": [] } }""",
                """{ "relicPointThresholds": [0, 1, 2] }""",
                """{ "relicPointThresholds": [0, 5, 4, 3, 2, 1, 0, 0] }""",
                """{ "relicPointThresholds": [0, 1, 2, 3, 4, 5, 6, 7.5] }""",
                """{ "taskPoints": { "legendary": 5 } }""",
                """{ "taskPoints": { "easy": -5 } }""",
                """{ "masteryPointsPerTask": 1.5 }""",
                """{ "masteryPointsPerTask": -1 }""",
                """{ "revenantEtherMultiplier": 0 }""",
                """{ "zulrahScaleMultiplier": 0 }""",
                """{ "gauntletNodesDeplete": "yes" }""",
                """{ "autosave": { "intervalTicks": 0 } }""",
                """{ "autosave": { "requestDelayTicks": 2.5 } }""",
            )
        for (text in bad) {
            assertThrows(IllegalArgumentException::class.java, { EventSettings.parse(text) }, text)
        }
    }
}
