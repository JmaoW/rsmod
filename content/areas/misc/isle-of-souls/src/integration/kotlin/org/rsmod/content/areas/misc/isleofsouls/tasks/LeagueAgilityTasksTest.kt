package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.agility.configs.AgilityObjs
import org.rsmod.content.skills.agility.configs.ArenaObjs
import org.rsmod.content.skills.agility.scripts.Agility
import org.rsmod.content.skills.agility.scripts.Arena
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos

class LeagueAgilityTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    @Test
    fun GameTestState
        .`tagging a dispenser completes the ticket task and thirty in a row the streak task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(done(638))
            eventBus.publish(Arena.Tagged(player, 1, 0))
            assertTrue(done(638))

            for (streak in 2..29) {
                eventBus.publish(Arena.Tagged(player, streak, 0))
            }
            assertFalse(done(641))
            eventBus.publish(Arena.Tagged(player, 30, 0))
            assertTrue(done(641))
        }

    @Test
    fun GameTestState.`a broken streak starts the count again`() =
        runGameTest(LeagueSkillTasks::class) {
            for (streak in 1..29) {
                eventBus.publish(Arena.Tagged(player, streak, 0))
            }
            // A dispenser was missed: the next tag is the first of a new streak.
            eventBus.publish(Arena.Tagged(player, 1, 0))
            assertFalse(done(641))
            for (streak in 2..29) {
                eventBus.publish(Arena.Tagged(player, streak, 0))
            }
            assertFalse(done(641))
            eventBus.publish(Arena.Tagged(player, 30, 0))
            assertTrue(done(641))
        }

    @Test
    fun GameTestState.`wearing a pirate hook completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(done(1576))
            player.worn[Wearpos.RightHand.slot] = InvObj(ArenaObjs.pirate_hook)
            eventBus.publish(
                HeldEquipEvents.WearposChange(
                    player,
                    Wearpos.RightHand,
                    objTypes[ArenaObjs.pirate_hook],
                )
            )
            assertTrue(done(1576))
        }

    @Test
    fun GameTestState.`picking up a mark of grace completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(done(386))
            eventBus.publish(Agility.MarkTaken(player))
            assertTrue(done(386))
        }

    @Test
    fun GameTestState.`wearing the six graceful pieces completes the outfit task`() =
        runGameTest(LeagueSkillTasks::class) {
            val pieces =
                listOf(
                    Wearpos.Hat to AgilityObjs.graceful_hood_worn,
                    Wearpos.Back to AgilityObjs.graceful_cape_worn,
                    Wearpos.Torso to AgilityObjs.graceful_top_worn,
                    Wearpos.Legs to AgilityObjs.graceful_legs_worn,
                    Wearpos.Hands to AgilityObjs.graceful_gloves_worn,
                    Wearpos.Feet to AgilityObjs.graceful_boots_worn,
                )
            for ((index, piece) in pieces.withIndex()) {
                val (pos, obj) = piece
                player.worn[pos.slot] = InvObj(obj)
                eventBus.publish(HeldEquipEvents.WearposChange(player, pos, objTypes[obj]))
                assertEquals(index == pieces.lastIndex, done(769), "after piece ${index + 1}")
            }
        }
}
