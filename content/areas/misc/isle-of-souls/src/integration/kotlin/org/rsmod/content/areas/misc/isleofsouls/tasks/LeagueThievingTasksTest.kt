package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.thieving.configs.PickpocketTargets
import org.rsmod.content.skills.thieving.configs.Stalls
import org.rsmod.content.skills.thieving.configs.ThievingObjs
import org.rsmod.content.skills.thieving.scripts.CoinPouches
import org.rsmod.content.skills.thieving.scripts.Pickpocketing
import org.rsmod.content.skills.thieving.scripts.StallThieving
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid

class LeagueThievingTasksTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.npcOf(key: String): Npc {
        val target = PickpocketTargets.byKey(key)
        return spawnNpc(coords, npcTypes.values.first { target.matches(it) })
    }

    private fun GameTestScope.robbed(
        key: String,
        loot: List<ObjType> = emptyList(),
        victims: Int = 1,
    ) {
        val target = PickpocketTargets.byKey(key)
        val items = loot.map { Pickpocketing.Loot(it, 1) }
        eventBus.publish(Pickpocketing.Pickpocketed(player, target, npcOf(key), items, victims))
    }

    private fun GameTestScope.stole(key: String, item: ObjType) =
        eventBus.publish(StallThieving.Stolen(player, Stalls.byKey(key), item, 1))

    @Test
    fun GameTestState.`pickpocketing each target completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                mapOf(
                    "man" to 273,
                    "master_farmer" to 86,
                    "ham" to 454,
                    "guard" to 455,
                    "desert_bandit" to 850,
                    "hero" to 685,
                )
            for ((key, id) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                robbed(key)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`a pickpocket only completes the task for its own target`() =
        runGameTest(LeagueSkillTasks::class) {
            robbed("farmer")
            for (id in listOf(273, 86, 454, 455, 850, 685)) {
                assertFalse(done(id), "Task $id should stay incomplete.")
            }
        }

    @Test
    fun GameTestState.`fifty pickpockets of a kind complete its task and every victim counts`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                mapOf("knight" to 684, "elf_lletya" to 526, "vyre" to 911, "menaphite_thug" to 851)
            for ((key, id) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                robbed(key, victims = 24)
                assertFalse(done(id), "Task $id needs 50.")
                robbed(key, victims = 26)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`both kinds of elf count towards the same fifty`() =
        runGameTest(LeagueSkillTasks::class) {
            robbed("elf_lletya", victims = 25)
            assertFalse(done(526))
            robbed("elf_prifddinas", victims = 25)
            assertTrue(done(526))
        }

    @Test
    fun GameTestState.`the right loot completes the loot tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            robbed("cave_goblin")
            assertFalse(done(458))
            robbed("cave_goblin", listOf(ThievingObjs.bullseye_lantern_unlit))
            assertTrue(done(458))

            robbed("gnome", listOf(ThievingObjs.king_worm))
            assertTrue(done(1541))

            robbed("wealthy_citizen", listOf(ThievingObjs.varlamore_thieving_house_key))
            assertTrue(done(1699))
            assertFalse(done(1700))
            repeat(14) {
                robbed("wealthy_citizen", listOf(ThievingObjs.varlamore_thieving_house_key))
            }
            assertTrue(done(1700))
        }

    @Test
    fun GameTestState.`each stall completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            val o = ThievingObjs
            val tasks =
                listOf(
                    Triple("bakery", o.chocolate_slice, 275),
                    Triple("tea", o.cup_of_tea, 457),
                    Triple("silk", o.silk, 1071),
                    Triple("fruit", o.golovanova_top, 76),
                    Triple("fur", o.grey_wolf_fur, 1817),
                    Triple("silver", o.silver_ore, 1818),
                    Triple("spice", o.spicespot, 1819),
                    Triple("fish", o.raw_salmon, 1834),
                    Triple("crossbow", o.xbows_crossbow_stock_wood, 1835),
                    Triple("crafting", o.chisel, 1844),
                    Triple("gem", o.uncut_sapphire, 279),
                )
            for ((key, item, id) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                stole(key, item)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`only the named item completes an item task`() =
        runGameTest(LeagueSkillTasks::class) {
            stole("bakery", ThievingObjs.cake)
            stole("fruit", ThievingObjs.banana)
            stole("crossbow", ThievingObjs.bolt)
            stole("crafting", ThievingObjs.hammer)
            stole("silver", ThievingObjs.silver_ore)
            stole("gem", ThievingObjs.uncut_sapphire)
            for (id in listOf(275, 76, 1835, 1844, 1769, 1768)) {
                assertFalse(done(id), "Task $id should stay incomplete.")
            }

            stole("silver", ThievingObjs.tiara)
            assertTrue(done(1769))
            stole("gem", ThievingObjs.uncut_diamond)
            assertTrue(done(1768))
        }

    @Test
    fun GameTestState.`opening pouches together completes the pouch tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(CoinPouches.PouchesOpened(player, count = 27, coins = 900))
            assertFalse(done(274))
            assertTrue(done(277))

            eventBus.publish(CoinPouches.PouchesOpened(player, count = 28, coins = 84))
            assertTrue(done(274))
        }

    @Test
    fun GameTestState.`the thieving milestones follow the level and the experience`() =
        runGameTest(LeagueSkillTasks::class) {
            robbed("man")
            assertFalse(done(248))

            player.stats[stats.thieving] = 99
            robbed("man")
            assertTrue(done(248))
            assertFalse(done(249))
        }
}
