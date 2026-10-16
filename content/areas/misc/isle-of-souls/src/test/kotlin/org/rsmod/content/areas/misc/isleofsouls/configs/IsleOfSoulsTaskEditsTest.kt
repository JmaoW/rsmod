package org.rsmod.content.areas.misc.isleofsouls.configs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskList

class IsleOfSoulsTaskEditsTest {
    @Test
    fun `edited task names match the server task list`() {
        for (edit in IsleOfSoulsTaskTexts.edits) {
            val task = LeagueTaskList.byId[edit.taskId]
            assertNotNull(task, "task ${edit.taskId} is not in the task list")
            val name = edit.name ?: continue
            assertEquals(name, task!!.name, "task ${edit.taskId}")
        }
    }

    @Test
    fun `edited text no longer names an area`() {
        val areas =
            listOf(
                "Wilderness",
                "Tirannwn",
                "Prifddinas",
                "Kandarin",
                "Karamja",
                "Kourend",
                "Varlamore",
                "Lumbridge",
                "Sophanem",
                "Necropolis",
                "Shayzien",
                "Darkmeyer",
                "Desert",
                "Piscatoris",
                "Stonecutter",
            )
        for (edit in IsleOfSoulsTaskTexts.edits) {
            for (text in listOfNotNull(edit.name, edit.description)) {
                // The spell is called that; it is not the area.
                val checked = text.replace("Kourend Castle Teleport", "Castle Teleport")
                assertTrue(areas.none { it in checked }, "task ${edit.taskId}: $text")
            }
        }
    }

    @Test
    fun `dropped duplicate tasks are gone from the task list`() {
        assertTrue(89 !in LeagueTaskList.byId)
        assertTrue(645 !in LeagueTaskList.byId)
    }
}
