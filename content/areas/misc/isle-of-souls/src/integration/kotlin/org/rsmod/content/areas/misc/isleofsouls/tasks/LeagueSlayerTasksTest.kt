package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerTables
import org.rsmod.content.skills.slayer.scripts.SlayerEvents
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid

class LeagueSlayerTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.monster(taskId: Int): Npc {
        val type =
            npcTypes.values.first { it.param(SlayerParams.task_id) == taskId && it.vislevel > 0 }
        return spawnNpc(CoordGrid(0, 50, 50, 34, 33), type)
    }

    private fun GameTestScope.kill(taskId: Int, onTask: Boolean = true) {
        val task = SlayerTables.default.tasks.getValue(taskId)
        eventBus.publish(SlayerEvents.Kill(player, monster(taskId), task, 5, 10.0, onTask))
    }

    private fun GameTestScope.superior(onTask: Boolean) =
        eventBus.publish(SlayerEvents.SuperiorKilled(player, monster(41), onTask))

    @Test
    fun GameTestState.`checking the task with a gem completes its task`() =
        runGameTest(LeagueSlayerTasks::class) {
            assertFalse(done(319))
            eventBus.publish(SlayerEvents.Checked(player))
            assertTrue(done(319))
        }

    @Test
    fun GameTestState.`finishing a task and making a helmet complete theirs`() =
        runGameTest(LeagueSlayerTasks::class) {
            val task = SlayerTables.default.tasks.getValue(41)
            eventBus.publish(SlayerEvents.Completed(player, null, task, 10, 1, 8))
            assertTrue(done(1524))
            eventBus.publish(SlayerEvents.HelmAssembled(player))
            assertTrue(done(917))
        }

    @Test
    fun GameTestState.`an abyssal demon on its task completes the abyssal demon task`() =
        runGameTest(LeagueSlayerTasks::class) {
            kill(48)
            assertFalse(done(328), "A bloodveld is not an abyssal demon")
            kill(42, onTask = false)
            assertFalse(done(328), "Off task does not count")
            kill(42)
            assertTrue(done(328))
        }

    @Test
    fun GameTestState.`250 kills on task complete the creatures task`() =
        runGameTest(LeagueSlayerTasks::class) {
            repeat(249) { kill(41) }
            assertFalse(done(320))
            kill(41)
            assertTrue(done(320))
        }

    @Test
    fun GameTestState.`superiors count for their ladders`() =
        runGameTest(LeagueSlayerTasks::class) {
            superior(onTask = false)
            assertTrue(done(322))
            assertFalse(done(96), "Off task is not an encounter")
            repeat(9) { superior(onTask = true) }
            assertTrue(done(326), "Ten superiors")
            assertFalse(done(327))
            repeat(10) { superior(onTask = true) }
            assertTrue(done(327), "Twenty superiors")
        }

    @Test
    fun GameTestState.`Slayer levels and experience complete the milestones`() =
        runGameTest(LeagueSkillTasks::class) {
            val task = SlayerTables.default.tasks.getValue(41)
            player.stats[stats.slayer] = 99
            eventBus.publish(SlayerEvents.Kill(player, monster(41), task, 5, 10.0, true))
            assertTrue(done(254), "Level 99 Slayer")
            player.statMap.setXP(stats.slayer, 25_000_000)
            eventBus.publish(SlayerEvents.Kill(player, monster(41), task, 5, 10.0, true))
            assertTrue(done(255), "25 million")
            assertFalse(done(1474))
        }
}
