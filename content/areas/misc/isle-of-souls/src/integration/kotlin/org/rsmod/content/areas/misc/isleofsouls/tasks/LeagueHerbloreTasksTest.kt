package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.herblore.configs.Herb
import org.rsmod.content.skills.herblore.configs.Herbs
import org.rsmod.content.skills.herblore.configs.Potion
import org.rsmod.content.skills.herblore.configs.Potions
import org.rsmod.content.skills.herblore.scripts.Drinking
import org.rsmod.content.skills.herblore.scripts.Herblore

class LeagueHerbloreTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.clean(herb: Herb) =
        eventBus.publish(Herblore.HerbCleaned(player, herb))

    private fun GameTestScope.make(potion: Potion, bonusDose: Boolean = false) =
        eventBus.publish(Herblore.PotionMade(player, potion, doses = 3, bonusDose = bonusDose))

    @Test
    fun GameTestState.`cleaning the first herbs completes the one-off tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(done(391))
            assertFalse(done(395))

            clean(Herbs.guam)
            assertTrue(done(391))
            assertFalse(done(395))

            clean(Herbs.avantoe)
            assertTrue(done(395))
        }

    @Test
    fun GameTestState.`counted herb tasks complete at their goal`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(24) { clean(Herbs.guam) }
            assertFalse(done(1784))
            clean(Herbs.guam)
            assertTrue(done(1784))

            repeat(14) { clean(Herbs.tarromin) }
            assertFalse(done(1785))
            clean(Herbs.tarromin)
            assertTrue(done(1785))

            repeat(49) { clean(Herbs.ranarr) }
            assertFalse(done(1786))
            clean(Herbs.ranarr)
            assertTrue(done(1786))

            repeat(49) { clean(Herbs.cadantine) }
            assertFalse(done(1788))
            clean(Herbs.cadantine)
            assertTrue(done(1788))

            repeat(49) { clean(Herbs.lantadyme) }
            assertFalse(done(1789))
            clean(Herbs.lantadyme)
            assertTrue(done(1789))

            // The avantoe task for 100 is separate from the one for the first one.
            repeat(99) { clean(Herbs.avantoe) }
            assertFalse(done(1787))
            clean(Herbs.avantoe)
            assertTrue(done(1787))
        }

    @Test
    fun GameTestState.`making a potion completes its task the first time`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                listOf(
                    392 to Potions.attack,
                    1366 to Potions.antipoison,
                    1046 to Potions.combat,
                    397 to Potions.saradomin_brew,
                    399 to Potions.super_combat,
                    974 to Potions.extended_antifire,
                )
            for ((id, potion) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                make(potion)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`counted potion tasks complete at their goal`() =
        runGameTest(LeagueSkillTasks::class) {
            val counted =
                listOf(
                    Triple(394, Potions.prayer, 30),
                    Triple(778, Potions.stamina, 20),
                    Triple(777, Potions.ranging, 20),
                    Triple(856, Potions.magic, 20),
                    Triple(1738, Potions.goading, 50),
                    Triple(1739, Potions.prayer_regeneration, 100),
                    Triple(1762, Potions.divine_super_attack, 25),
                    Triple(1763, Potions.divine_super_strength, 25),
                    Triple(1380, Potions.divine_super_combat, 100),
                    Triple(1381, Potions.divine_ranging, 100),
                )
            for ((id, potion, goal) in counted) {
                repeat(goal - 1) { make(potion) }
                assertFalse(done(id), "Task $id should need $goal.")
                make(potion)
                assertTrue(done(id), "Task $id should complete at $goal.")
            }
        }

    @Test
    fun GameTestState.`a potion with an extra dose completes the four dose task`() =
        runGameTest(LeagueSkillTasks::class) {
            make(Potions.attack)
            assertFalse(done(396))

            make(Potions.attack, bonusDose = true)
            assertTrue(done(396))
        }

    @Test
    fun GameTestState.`drinking a strength potion completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Drinking.PotionDrunk(player, Potions.attack))
            assertFalse(done(393))

            eventBus.publish(Drinking.PotionDrunk(player, Potions.strength))
            assertTrue(done(393))
        }

    @Test
    fun GameTestState.`herblore milestones complete from herblore actions`() =
        runGameTest(LeagueSkillTasks::class) {
            player.stats[stats.herblore] = 98
            clean(Herbs.guam)
            assertFalse(done(246))

            player.stats[stats.herblore] = 99
            clean(Herbs.guam)
            assertTrue(done(246))
            assertFalse(done(247))

            player.statMap.setXP(stats.herblore, 25_000_000)
            make(Potions.attack)
            assertTrue(done(247))
            assertFalse(done(1470))
            assertEquals(false, done(1493))
        }
}
