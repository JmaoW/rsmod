package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.runecraft.configs.PouchKind
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.content.skills.runecraft.configs.RuneAltars
import org.rsmod.content.skills.runecraft.scripts.Abyss
import org.rsmod.content.skills.runecraft.scripts.EssenceMine
import org.rsmod.content.skills.runecraft.scripts.EssencePouches
import org.rsmod.content.skills.runecraft.scripts.Runecrafting

class LeagueRunecraftTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.crafted(altar: String, essence: Int, runes: Int = essence) {
        val rune = RuneAltars.all.first { it.name == altar }
        eventBus.publish(Runecrafting.RunesCrafted(player, rune, essence, runes))
    }

    @Test
    fun GameTestState.`visiting the mine and mining essence complete their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(EssenceMine.MineVisited(player))
            assertTrue(tracker().isComplete(player, 364))

            eventBus.publish(EssenceMine.EssenceMined(player, pure = false))
            assertTrue(tracker().isComplete(player, 1801))
            assertFalse(tracker().isComplete(player, 1613))

            repeat(25) { eventBus.publish(EssenceMine.EssenceMined(player, pure = true)) }
            assertTrue(tracker().isComplete(player, 1613))
        }

    @Test
    fun GameTestState.`the first air fire and body runes complete their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted("air", 1)
            assertTrue(tracker().isComplete(player, 774))
            assertFalse(tracker().isComplete(player, 854))

            crafted("fire", 2)
            assertTrue(tracker().isComplete(player, 854))

            crafted("body", 1)
            assertTrue(tracker().isComplete(player, 775))
        }

    @Test
    fun GameTestState.`four runes from one essence completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted("air", essence = 2, runes = 8)
            assertFalse(tracker().isComplete(player, 368))

            crafted("air", essence = 1, runes = 4)
            assertTrue(tracker().isComplete(player, 368))
        }

    @Test
    fun GameTestState.`essence totals complete at 200 and 2500`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted("air", 199)
            assertFalse(tracker().isComplete(player, 367))
            crafted("mind", 1)
            assertTrue(tracker().isComplete(player, 367))
            assertFalse(tracker().isComplete(player, 370))

            crafted("water", 2300)
            assertTrue(tracker().isComplete(player, 370))
        }

    @Test
    fun GameTestState.`rune counts are kept for each altar`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted("water", 49)
            assertFalse(tracker().isComplete(player, 462))
            // Another altar's runes do not count towards water.
            crafted("nature", 49)
            assertFalse(tracker().isComplete(player, 462))
            assertFalse(tracker().isComplete(player, 648))

            crafted("water", 1)
            assertTrue(tracker().isComplete(player, 462))
            crafted("nature", 1)
            assertTrue(tracker().isComplete(player, 648))

            crafted("blood", 99)
            assertFalse(tracker().isComplete(player, 1603))
            crafted("blood", 1)
            assertTrue(tracker().isComplete(player, 1603))
        }

    @Test
    fun GameTestState.`combination runes and lava at the fire altar complete`() =
        runGameTest(LeagueSkillTasks::class) {
            val mist = RuneAltars.combos.first { it.level == 6 }
            eventBus.publish(Runecrafting.ComboRunesCrafted(player, RuneAltars.air, mist, 3))
            assertTrue(tracker().isComplete(player, 369))
            assertFalse(tracker().isComplete(player, 855))

            val lava = RuneAltars.combos.first { it.level == 23 }
            eventBus.publish(Runecrafting.ComboRunesCrafted(player, RuneAltars.earth, lava, 1))
            assertFalse(tracker().isComplete(player, 855))
            eventBus.publish(Runecrafting.ComboRunesCrafted(player, RuneAltars.fire, lava, 1))
            assertTrue(tracker().isComplete(player, 855))
        }

    @Test
    fun GameTestState.`locating the abyss and filling pouches complete their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Runecrafting.TalismanLocated(player))
            assertTrue(tracker().isComplete(player, 366))

            eventBus.publish(Abyss.AltarEntered(player, RuneAltars.air))
            assertTrue(tracker().isComplete(player, 982))

            eventBus.publish(
                EssencePouches.PouchFilled(player, Pouches.large, PouchKind.Pure, 9, 9)
            )
            assertTrue(tracker().isComplete(player, 983))
            assertFalse(tracker().isComplete(player, 984))

            eventBus.publish(
                EssencePouches.PouchFilled(player, Pouches.giant, PouchKind.Pure, 12, 12)
            )
            assertTrue(tracker().isComplete(player, 984))
        }

    @Test
    fun GameTestState.`runecraft level and experience milestones complete`() =
        runGameTest(LeagueSkillTasks::class) {
            player.stats[stats.runecrafting] = 99
            crafted("air", 1)
            assertTrue(tracker().isComplete(player, 238))
            assertFalse(tracker().isComplete(player, 239))

            player.statMap.setXP(stats.runecrafting, 25_000_000)
            crafted("air", 1)
            assertTrue(tracker().isComplete(player, 239))
            assertEquals(false, tracker().isComplete(player, 1466))
        }
}
