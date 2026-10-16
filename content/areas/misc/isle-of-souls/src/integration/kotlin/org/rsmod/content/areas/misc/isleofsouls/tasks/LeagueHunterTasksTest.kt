package org.rsmod.content.areas.misc.isleofsouls.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.righthand
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.hunter.configs.HunterCreatures
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.scripts.Birdhouses
import org.rsmod.content.skills.hunter.scripts.Falconer
import org.rsmod.content.skills.hunter.scripts.HunterCatching
import org.rsmod.content.skills.hunter.scripts.HunterTraps
import org.rsmod.game.inv.InvObj

class LeagueHunterTasksTest {
    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestScope.done(taskId: Int) = tracker().isComplete(player, taskId)

    private fun GameTestScope.trapped(key: String, catches: Int = 1) =
        eventBus.publish(HunterTraps.Trapped(player, HunterCreatures[key], catches, emptyList()))

    @Test
    fun GameTestState.`each creature trapped completes its own task`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                mapOf(
                    "crimson_swift" to 380,
                    "wild_kebbit" to 1369,
                    "sabre_toothed_kebbit" to 1833,
                    "chinchompa" to 1614,
                    "black_chinchompa" to 977,
                    "swamp_lizard" to 912,
                    "red_salamander" to 694,
                    "black_salamander" to 976,
                    "spined_larupia" to 691,
                    "sabre_toothed_kyatt" to 578,
                )
            for ((key, id) in tasks) {
                assertFalse(done(id), "Task $id should start incomplete.")
                trapped(key)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`a swamp lizard or any salamander completes the either-or task`() =
        runGameTest(LeagueSkillTasks::class) {
            for (key in listOf("orange_salamander", "tecu_salamander", "swamp_lizard")) {
                assertFalse(done(382), key)
                trapped(key)
                assertTrue(done(382), key)
                tracker().reset(player)
            }
        }

    @Test
    fun GameTestState.`counted catches complete at their number`() =
        runGameTest(LeagueSkillTasks::class) {
            val tasks =
                mapOf(
                    "orange_salamander" to (859 to 30),
                    "chinchompa" to (1615 to 50),
                    "carnivorous_chinchompa" to (692 to 150),
                    "sunlight_antelope" to (1697 to 25),
                    "moonlight_antelope" to (1696 to 50),
                    "tecu_salamander" to (1720 to 100),
                )
            for ((key, pair) in tasks) {
                val (id, goal) = pair
                assertFalse(done(id), "Task $id should start incomplete.")
                repeat(goal - 1) { trapped(key) }
                assertFalse(done(id), "Task $id needs $goal.")
                trapped(key)
                assertTrue(done(id), "Task $id should complete.")
            }
        }

    @Test
    fun GameTestState.`a doubled catch counts twice`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(74) { trapped("carnivorous_chinchompa", catches = 2) }
            assertFalse(done(692))
            trapped("carnivorous_chinchompa", catches = 2)
            assertTrue(done(692))
        }

    @Test
    fun GameTestState.`red chinchompas feed both counts and every chinchompa feeds the 500`() =
        runGameTest(LeagueSkillTasks::class) {
            trapped("carnivorous_chinchompa", catches = 150)
            assertTrue(done(692))
            assertFalse(done(1377))
            trapped("carnivorous_chinchompa", catches = 150)
            assertTrue(done(1377))

            assertFalse(done(1616))
            trapped("chinchompa", catches = 100)
            trapped("black_chinchompa", catches = 100)
            assertTrue(done(1616))
        }

    @Test
    fun GameTestState.`implings complete their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            val named =
                mapOf("Baby impling" to 379, "Dragon impling" to 384, "Crystal impling" to 519)
            for ((name, id) in named) {
                assertFalse(done(id), name)
                eventBus.publish(HunterCatching.Caught(player, name, true))
                assertTrue(done(id), name)
            }
            assertFalse(done(445))
            repeat(50) { eventBus.publish(HunterCatching.Caught(player, "Young impling", true)) }
            assertTrue(done(445))
        }

    @Test
    fun GameTestState.`a lucky impling only counts when it is caught barehanded`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(HunterCatching.Caught(player, "Lucky impling", true))
            assertFalse(done(385))
            eventBus.publish(HunterCatching.Caught(player, "Lucky impling", false))
            assertTrue(done(385))
        }

    @Test
    fun GameTestState.`a butterfly is not an impling`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(HunterCatching.Caught(player, "Ruby harvest", true))
            for (id in listOf(379, 384, 385, 445, 519)) {
                assertFalse(done(id), "Task $id should stay incomplete.")
            }
        }

    @Test
    fun GameTestState.`a crystal impling's dragonstone amulet completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(
                HunterCatching.Looted(
                    player,
                    "Young impling",
                    hunter_objs.strung_dragonstone_amulet,
                )
            )
            assertFalse(done(1513))
            eventBus.publish(HunterCatching.Looted(player, "Crystal impling", hunter_objs.bones))
            assertFalse(done(1513))
            eventBus.publish(
                HunterCatching.Looted(
                    player,
                    "Crystal impling",
                    hunter_objs.strung_dragonstone_amulet,
                )
            )
            assertTrue(done(1513))
        }

    @Test
    fun GameTestState.`birdhouses complete their tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            eventBus.publish(Birdhouses.Built(player, "yew"))
            assertFalse(done(440))
            eventBus.publish(Birdhouses.Built(player, "magic"))
            assertTrue(done(440))

            repeat(19) { eventBus.publish(Birdhouses.Collected(player, "oak", 0)) }
            assertFalse(done(439))
            eventBus.publish(Birdhouses.Collected(player, "oak", 0))
            assertTrue(done(439))
        }

    @Test
    fun GameTestState.`a wielded stack of black chinchompas completes the stack tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            val hunter = LeagueHunterTasks(tracker())
            player.righthand =
                InvObj(objTypes.values.first { it.internalName == "chinchompa_black" }, 99)
            hunter.checkGear(player)
            assertFalse(done(978))

            player.righthand =
                InvObj(objTypes.values.first { it.internalName == "chinchompa_black" }, 250)
            hunter.checkGear(player)
            assertTrue(done(978))
            assertTrue(done(979))
            assertFalse(done(980))
        }

    @Test
    fun GameTestState.`a wielded tecu salamander completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            val hunter = LeagueHunterTasks(tracker())
            player.righthand =
                InvObj(objTypes.values.first { it.internalName == "mountain_salamander" })
            hunter.checkGear(player)
            assertTrue(done(1722))
        }

    @Test
    fun GameTestState.`hunter experience completes the milestone tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            player.xpRate = 1.0
            player.statMap.setXP(stats.hunter, 25_000_000)
            eventBus.publish(
                Falconer.Hunted(
                    player,
                    org.rsmod.content.skills.hunter.configs.Falconry.kebbits.first(),
                )
            )
            assertTrue(done(257))
            assertFalse(done(1475))
        }
}
