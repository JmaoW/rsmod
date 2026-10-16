package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.map.CoordGrid

class LeagueKillTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.kill(name: String, hero: Boolean = true) {
        val type =
            npcTypeFactory.create {
                this.name = name
                vislevel = 10
                hitpoints = 10
                defaultMode = NpcMode.None
            }
        val npc = spawnNpc(CoordGrid(0, 50, 50, 0, 0), type)
        eventBus.publish(NpcDeathEvents.Killed(npc, if (hero) player else null))
    }

    @Test
    fun GameTestState.`killing a monster completes its task`() =
        runGameTest(LeagueKillTasks::class) {
            assertFalse(done(63))
            kill("Goblin")
            assertTrue(done(63))
            assertFalse(done(61))
        }

    @Test
    fun GameTestState.`the monster name is matched in any letter case`() =
        runGameTest(LeagueKillTasks::class) {
            kill("MOSS GIANT")
            assertTrue(done(61))
        }

    @Test
    fun GameTestState.`one kill can finish several tasks`() =
        runGameTest(LeagueKillTasks::class) {
            kill("Lesser demon")

            assertTrue(done(62))
            assertTrue(done(466))
        }

    @Test
    fun GameTestState.`a kill nobody is credited for completes nothing`() =
        runGameTest(LeagueKillTasks::class) {
            kill("Goblin", hero = false)

            assertFalse(done(63))
        }

    @Test
    fun GameTestState.`a counted task needs all its kills`() =
        runGameTest(LeagueKillTasks::class) {
            repeat(4) { kill("Bunny") }
            assertFalse(done(1580))
            assertEquals(4, player.vars[LeagueProgressVarps.kill_bunny_5])

            kill("Bunny")
            assertTrue(done(1580))
            assertEquals(5, player.vars[LeagueProgressVarps.kill_bunny_5])
        }

    @Test
    fun GameTestState.`a monster with a similar name does not count`() =
        runGameTest(LeagueKillTasks::class) {
            kill("Giant rat")
            kill("Goblin guard")

            assertFalse(done(1554))
            assertFalse(done(64))
            assertFalse(done(63))
        }
}
