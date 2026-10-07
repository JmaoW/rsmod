package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.crafting.configs.CraftingObjs
import org.rsmod.content.skills.crafting.scripts.Crafting
import org.rsmod.game.type.obj.ObjType

class LeagueCraftingTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.crafted(product: ObjType, quantity: Int = 1) =
        eventBus.publish(Crafting.ItemCrafted(player, product, quantity))

    @Test
    fun GameTestState.`crafting the right item completes its one-off task`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                listOf(
                    331 to CraftingObjs.ball_of_wool,
                    332 to CraftingObjs.sapphire,
                    333 to CraftingObjs.leather_body,
                    1350 to CraftingObjs.red_topaz,
                    335 to CraftingObjs.emerald_ring,
                    336 to CraftingObjs.ruby_amulet_u,
                    337 to CraftingObjs.dragonstone_amulet,
                    339 to CraftingObjs.onyx_amulet_u,
                    1351 to CraftingObjs.green_dhide_shield,
                    1352 to CraftingObjs.black_dhide_shield,
                    981 to CraftingObjs.air_battlestaff,
                    913 to CraftingObjs.ochre_snelm_round,
                    1572 to CraftingObjs.bowl,
                )
            for ((id, product) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                crafted(product)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`a different item does not complete another item's task`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted(CraftingObjs.emerald)
            crafted(CraftingObjs.sapphire_ring)
            crafted(CraftingObjs.leather_gloves)

            assertFalse(done(335))
            assertFalse(done(333))
            // The sapphire task is for the cut gem only.
            assertFalse(done(332))
        }

    @Test
    fun GameTestState.`counted crafting tasks complete at their goal`() =
        runGameTest(LeagueSkillTasks::class) {
            val counted =
                listOf(
                    Triple(334, CraftingObjs.unpowered_orb, 100),
                    Triple(338, CraftingObjs.blue_dhide_body, 30),
                    Triple(1353, CraftingObjs.empty_light_orb, 100),
                )
            for ((id, product, goal) in counted) {
                repeat(goal - 1) { crafted(product) }
                assertFalse(done(id), "Task $id should need $goal.")
                crafted(product)
                assertTrue(done(id), "Task $id should complete at $goal.")
            }
        }

    @Test
    fun GameTestState.`a batch counts every item in it`() =
        runGameTest(LeagueSkillTasks::class) {
            crafted(CraftingObjs.unpowered_orb, quantity = 99)
            assertFalse(done(334))
            crafted(CraftingObjs.unpowered_orb, quantity = 1)
            assertTrue(done(334))
        }

    @Test
    fun GameTestState.`crafting milestones complete from crafting actions`() =
        runGameTest(LeagueSkillTasks::class) {
            player.stats[stats.crafting] = 98
            crafted(CraftingObjs.leather_gloves)
            assertFalse(done(250))

            player.stats[stats.crafting] = 99
            crafted(CraftingObjs.leather_gloves)
            assertTrue(done(250))
            assertFalse(done(251))

            player.statMap.setXP(stats.crafting, 25_000_000)
            crafted(CraftingObjs.leather_gloves)
            assertTrue(done(251))
            assertFalse(done(1472))
            assertFalse(done(1495))
        }
}
