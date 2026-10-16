package org.rsmod.content.areas.misc.isleofsouls.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnouncementConfigTest {
    @Test
    fun `the defaults announce elite and master tasks, first hard tasks, 99s, deaths and the lead`() {
        val config = AnnouncementConfig()

        assertEquals(TaskAnnouncement.Completed, config.forTask(258, "elite", firstTeam = false))
        assertEquals(TaskAnnouncement.Completed, config.forTask(1, "master", firstTeam = true))
        assertEquals(TaskAnnouncement.FirstTeam, config.forTask(309, "hard", firstTeam = true))
        assertNull(config.forTask(309, "hard", firstTeam = false))
        assertNull(config.forTask(303, "easy", firstTeam = true))
        assertTrue(config.announcesLevel(99))
        assertFalse(config.announcesLevel(98))
        assertTrue(config.deaths)
        assertTrue(config.leadChanges)
    }

    @Test
    fun `a first completion is announced as such rather than twice`() {
        val config =
            AnnouncementConfig(
                announceTiers = setOf("elite"),
                firstCompletionTiers = setOf("elite"),
            )

        assertEquals(TaskAnnouncement.FirstTeam, config.forTask(5, "elite", firstTeam = true))
        assertEquals(TaskAnnouncement.Completed, config.forTask(5, "elite", firstTeam = false))
    }

    @Test
    fun `individual tasks can be picked by id`() {
        val config =
            AnnouncementConfig(
                announceTiers = emptySet(),
                firstCompletionTiers = emptySet(),
                announceTaskIds = setOf(303),
                firstCompletionTaskIds = setOf(306),
            )

        assertEquals(TaskAnnouncement.Completed, config.forTask(303, "easy", firstTeam = false))
        assertEquals(TaskAnnouncement.FirstTeam, config.forTask(306, "medium", firstTeam = true))
        assertNull(config.forTask(306, "medium", firstTeam = false))
        assertNull(config.forTask(309, "hard", firstTeam = true))
    }

    @Test
    fun `drops match by internal name ignoring case`() {
        val config = AnnouncementConfig(dropObjs = setOf("abyssal_whip"))

        assertTrue(config.announcesDrop("Abyssal_Whip"))
        assertFalse(config.announcesDrop("bones"))
        assertFalse(config.announcesDrop(null))
    }

    @Test
    fun `boosted drops and pets are announced by default and can be switched off`() {
        val config = AnnouncementConfig()
        assertTrue(config.announcesDrop(boosted = true, pet = false))
        assertTrue(config.announcesDrop(boosted = false, pet = true))
        assertFalse(config.announcesDrop(boosted = false, pet = false))

        val off = AnnouncementConfig.parse("""{ "drops": { "boosted": false, "pets": false } }""")
        assertFalse(off.announcesDrop(boosted = true, pet = true))
    }

    @Test
    fun `the default settings round trip through the file format`() {
        val config =
            AnnouncementConfig(
                leadChanges = false,
                announceTiers = setOf("hard", "elite"),
                firstCompletionTiers = emptySet(),
                announceTaskIds = setOf(258, 259),
                firstCompletionTaskIds = setOf(1371),
                levels = setOf(50, 99),
                deaths = false,
                dropObjs = setOf("abyssal_whip"),
                dropBoosted = false,
                dropPets = false,
            )

        assertEquals(config, AnnouncementConfig.parse(config.toJson()))
        assertEquals(AnnouncementConfig(), AnnouncementConfig.parse(AnnouncementConfig().toJson()))
    }

    @Test
    fun `anything left out keeps its default`() {
        val config =
            AnnouncementConfig.parse("""{ "deaths": false, "levels": { "announce": [99, 50] } }""")

        assertEquals(AnnouncementConfig(deaths = false, levels = setOf(99, 50)), config)
    }

    @Test
    fun `an empty object is the defaults`() {
        assertEquals(AnnouncementConfig(), AnnouncementConfig.parse("{}"))
    }

    @Test
    fun `a misspelt setting is rejected so it cannot silently switch something off`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                AnnouncementConfig.parse("""{ "death": false }""")
            }
        assertTrue(error.message!!.contains("\"death\""))

        assertThrows(IllegalArgumentException::class.java) {
            AnnouncementConfig.parse("""{ "tasks": { "annouceTiers": [] } }""")
        }
    }

    @Test
    fun `bad values are rejected with an argument error`() {
        val bad =
            listOf(
                "[]",
                """{ "deaths": "yes" }""",
                """{ "tasks": [] }""",
                """{ "tasks": { "announceTiers": ["legendary"] } }""",
                """{ "tasks": { "announceIds": ["303"] } }""",
                """{ "levels": { "announce": "99" } }""",
                """{ "drops": { "objs": [5] } }""",
            )
        for (json in bad) {
            assertThrows(
                IllegalArgumentException::class.java,
                { AnnouncementConfig.parse(json) },
                json,
            )
        }
    }
}
