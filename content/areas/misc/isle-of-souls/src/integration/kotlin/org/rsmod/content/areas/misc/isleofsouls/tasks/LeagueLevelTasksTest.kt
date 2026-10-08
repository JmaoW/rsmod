package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.type.stat.StatType

class LeagueLevelTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.levelTasks() =
        LeagueLevelTasks(
            tracker(),
            LeagueMasteryTasks(LeagueTeams(players), LeagueMasteryTracker()),
        )

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private val everyStat: List<StatType> =
        listOf(
            stats.attack,
            stats.defence,
            stats.strength,
            stats.hitpoints,
            stats.ranged,
            stats.prayer,
            stats.magic,
            stats.cooking,
            stats.woodcutting,
            stats.fletching,
            stats.fishing,
            stats.firemaking,
            stats.crafting,
            stats.smithing,
            stats.mining,
            stats.herblore,
            stats.agility,
            stats.thieving,
            stats.slayer,
            stats.farming,
            stats.runecrafting,
            stats.hunter,
            stats.construction,
        )

    private fun GameTestScope.setAll(level: Int) {
        for (stat in everyStat) {
            player.stats[stat] = level
        }
    }

    @Test
    fun GameTestState.`a level up completes the first level up task and nothing else`() =
        runGameTest {
            setAll(1)
            player.stats[stats.hitpoints] = 10
            levelTasks().check(player)
            assertFalse(done(191))

            levelTasks().onLevelUp(player)

            assertTrue(done(191))
            assertFalse(done(192))
            assertEquals(10, player.vars[LeagueTaskVars.points_completed])
        }

    @Test
    fun GameTestState.`first level 5 skips agility, hitpoints and runecrafting`() = runGameTest {
        setAll(1)
        for (stat in listOf(stats.agility, stats.hitpoints, stats.runecrafting)) {
            player.stats[stat] = 9
        }
        levelTasks().check(player)
        assertFalse(done(192))
        assertFalse(done(193))

        player.stats[stats.mining] = 5
        levelTasks().check(player)
        assertTrue(done(192))
        assertFalse(done(193))

        player.stats[stats.runecrafting] = 10
        levelTasks().check(player)
        // Runecrafting counts towards 10, hitpoints and agility do not.
        assertTrue(done(193))
    }

    @Test
    fun GameTestState.`first level 10 does not count agility`() = runGameTest {
        setAll(1)
        player.stats[stats.agility] = 10
        levelTasks().check(player)
        assertFalse(done(193))
        assertFalse(done(194))
    }

    @Test
    fun GameTestState.`the first level tasks follow the highest skill`() = runGameTest {
        setAll(1)
        player.stats[stats.woodcutting] = 70
        levelTasks().check(player)

        for (task in 192..199) {
            assertTrue(done(task), "task $task")
        }
        for (task in 200..202) {
            assertFalse(done(task), "task $task")
        }

        player.stats[stats.woodcutting] = 95
        levelTasks().check(player)
        for (task in 200..202) {
            assertTrue(done(task), "task $task")
        }
    }

    @Test
    fun GameTestState.`total level tasks complete at their totals`() = runGameTest {
        setAll(1)
        player.stats[stats.hitpoints] = 10
        assertEquals(32, everyStat.sumOf { player.stats[it] })
        levelTasks().check(player)
        assertFalse(done(203))

        // 32 + 68 = 100.
        player.stats[stats.mining] = 1 + 68
        levelTasks().check(player)
        assertTrue(done(203))
        assertFalse(done(204))
    }

    @Test
    fun GameTestState.`every skill at 99 gives every level task up to base level 95`() =
        runGameTest {
            setAll(99)
            levelTasks().check(player)

            for (task in 192..225) {
                assertTrue(done(task), "task $task")
            }
            assertTrue(done(214), "total level 2277")
        }

    @Test
    fun GameTestState.`base level needs every skill`() = runGameTest {
        setAll(20)
        player.stats[stats.construction] = 4
        levelTasks().check(player)
        assertFalse(done(215))

        player.stats[stats.construction] = 5
        levelTasks().check(player)
        assertTrue(done(215))
        assertFalse(done(216))

        player.stats[stats.construction] = 20
        levelTasks().check(player)
        assertTrue(done(216))
        assertTrue(done(217))
        assertFalse(done(218))
    }

    @Test
    fun GameTestState.`combat level tasks complete from the combat level`() = runGameTest {
        setAll(1)
        player.stats[stats.hitpoints] = 10
        levelTasks().check(player)
        assertFalse(done(401))

        for (stat in listOf(stats.attack, stats.strength, stats.defence, stats.hitpoints)) {
            player.stats[stat] = 20
        }
        levelTasks().check(player)
        assertTrue(done(401))
        assertFalse(done(403))

        setAll(99)
        levelTasks().check(player)
        assertTrue(done(408), "combat level 126")
    }
}
