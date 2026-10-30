package org.rsmod.content.areas.misc.isleofsouls.tasks

import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_interfaces
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.areas.misc.isleofsouls.miningguild.GoldenNuggetFound
import org.rsmod.content.areas.misc.isleofsouls.miningguild.MiningGuildEntered
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.content.skills.firemaking.scripts.Firemaking
import org.rsmod.content.skills.fishing.scripts.Fishing
import org.rsmod.content.skills.fletching.scripts.Fletching
import org.rsmod.content.skills.mining.configs.ProspectorObjs
import org.rsmod.content.skills.mining.scripts.Mining
import org.rsmod.content.skills.smithing.scripts.Smelting
import org.rsmod.content.skills.smithing.scripts.Smithing
import org.rsmod.content.skills.woodcutting.scripts.Woodcutting
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class LeagueSkillTasksTest {
    private val dummyLoc =
        BoundLocInfo(
            coords = CoordGrid(0, 50, 50, 0, 0),
            entity = LocEntity(1, LocShape.CentrepieceStraight.id, LocAngle.West.id),
            layer = 0,
            width = 1,
            length = 1,
            forceApproachFlags = 0,
        )

    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun Player.points() = vars[LeagueTaskVars.points_completed]

    private fun Player.claimed() = vars[LeagueTaskVars.points_claimed]

    private fun Player.totalTasks() = vars[LeagueTaskBitVars.total_tasks_completed]

    @Test
    fun GameTestState.`mining copper completes the task once and awards points`() =
        runGameTest(LeagueSkillTasks::class) {
            val copper = objTypes[objs.copper_ore]
            assertFalse(tracker().isComplete(player, 303))

            eventBus.publish(Mining.MineOre(player, dummyLoc, copper))
            assertTrue(tracker().isComplete(player, 303))
            assertEquals(1, player.totalTasks())
            assertEquals(10, player.points())
            assertEquals(10, player.claimed())

            // A repeat must not award anything more.
            eventBus.publish(Mining.MineOre(player, dummyLoc, copper))
            assertEquals(1, player.totalTasks())
            assertEquals(10, player.points())
        }

    @Test
    fun GameTestState.`counted mining tasks complete at their goal`() =
        runGameTest(LeagueSkillTasks::class) {
            val tin = objTypes[objs.tin_ore]
            repeat(4) { eventBus.publish(Mining.MineOre(player, dummyLoc, tin)) }
            assertFalse(tracker().isComplete(player, 1371))

            eventBus.publish(Mining.MineOre(player, dummyLoc, tin))
            assertTrue(tracker().isComplete(player, 1371))

            val iron = objTypes[objs.iron_ore]
            repeat(49) { eventBus.publish(Mining.MineOre(player, dummyLoc, iron)) }
            assertFalse(tracker().isComplete(player, 306))
            eventBus.publish(Mining.MineOre(player, dummyLoc, iron))
            assertTrue(tracker().isComplete(player, 306))

            // Copper (10) is not mined here, so only tin (easy, 10) and iron (medium, 30).
            assertEquals(40, player.points())
        }

    @Test
    fun GameTestState.`runite ore tasks complete wherever the ore is mined`() =
        runGameTest(LeagueSkillTasks::class) {
            val runite = objTypes[objs.runite_ore]
            assertFalse(tracker().isComplete(player, 985))

            eventBus.publish(Mining.MineOre(player, dummyLoc, runite))
            assertTrue(tracker().isComplete(player, 985))
            assertFalse(tracker().isComplete(player, 1379))

            repeat(198) { eventBus.publish(Mining.MineOre(player, dummyLoc, runite)) }
            assertFalse(tracker().isComplete(player, 1379))
            eventBus.publish(Mining.MineOre(player, dummyLoc, runite))
            assertTrue(tracker().isComplete(player, 1379))
        }

    @Test
    fun GameTestState.`mining tasks that named a place complete anywhere`() =
        runGameTest(LeagueSkillTasks::class) {
            val gold = objTypes[objs.gold_ore]
            repeat(24) { eventBus.publish(Mining.MineOre(player, dummyLoc, gold)) }
            assertFalse(tracker().isComplete(player, 1578))
            eventBus.publish(Mining.MineOre(player, dummyLoc, gold))
            assertTrue(tracker().isComplete(player, 1578))

            // The 20 mithril task has its own count, apart from "Mine 50 Mithril Ore".
            val mithril = objTypes[objs.mithril_ore]
            repeat(20) { eventBus.publish(Mining.MineOre(player, dummyLoc, mithril)) }
            assertTrue(tracker().isComplete(player, 1694))
            assertFalse(tracker().isComplete(player, 1823))
        }

    @Test
    fun GameTestState.`woodcutting tasks that named a place complete anywhere`() =
        runGameTest(LeagueSkillTasks::class) {
            fun chop(obj: ObjType, times: Int) =
                repeat(times) { eventBus.publish(Woodcutting.CutLogs(player, dummyLoc, obj)) }

            chop(objTypes[objs.magic_logs], 19)
            assertFalse(tracker().isComplete(player, 1719))
            chop(objTypes[objs.magic_logs], 1)
            assertTrue(tracker().isComplete(player, 1719))
            assertFalse(tracker().isComplete(player, 1765))
            chop(objTypes[objs.magic_logs], 30)
            assertTrue(tracker().isComplete(player, 1765))
            assertFalse(tracker().isComplete(player, 295))

            chop(objTypes[objs.yew_logs], 15)
            assertTrue(tracker().isComplete(player, 1607))
            chop(objTypes[objs.teak_logs], 100)
            assertTrue(tracker().isComplete(player, 1385))
            chop(objTypes[objs.maple_logs], 100)
            assertTrue(tracker().isComplete(player, 1579))
        }

    @Test
    fun GameTestState.`woodcutting tasks complete from cut logs`() =
        runGameTest(LeagueSkillTasks::class) {
            val logs = objTypes[objs.logs]
            eventBus.publish(Woodcutting.CutLogs(player, dummyLoc, logs))
            assertTrue(tracker().isComplete(player, 289))

            val willow = objTypes[objs.willow_logs]
            repeat(99) { eventBus.publish(Woodcutting.CutLogs(player, dummyLoc, willow)) }
            assertFalse(tracker().isComplete(player, 292))
            eventBus.publish(Woodcutting.CutLogs(player, dummyLoc, willow))
            assertTrue(tracker().isComplete(player, 292))
        }

    @Test
    fun GameTestState.`firemaking tasks complete from lit logs`() =
        runGameTest(LeagueSkillTasks::class) {
            val coords = CoordGrid(0, 50, 50, 0, 0)
            fun light(obj: ObjType) = eventBus.publish(Firemaking.LogLit(player, obj, coords))

            light(objTypes[objs.logs])
            assertTrue(tracker().isComplete(player, 371))
            assertFalse(tracker().isComplete(player, 372))
            light(objTypes[objs.oak_logs])
            assertTrue(tracker().isComplete(player, 372))
            light(objTypes[objs.magic_logs])
            assertTrue(tracker().isComplete(player, 378))

            val willow = objTypes[objs.willow_logs]
            repeat(99) { light(willow) }
            assertFalse(tracker().isComplete(player, 374))
            light(willow)
            assertTrue(tracker().isComplete(player, 374))

            val maple = objTypes[objs.maple_logs]
            repeat(24) { light(maple) }
            assertFalse(tracker().isComplete(player, 1821))
            light(maple)
            assertTrue(tracker().isComplete(player, 1821))

            val yew = objTypes[objs.yew_logs]
            repeat(100) { light(yew) }
            assertTrue(tracker().isComplete(player, 377))

            // Chopping the same logs must not count as burning them.
            assertFalse(tracker().isComplete(player, 292))
        }

    @Test
    fun GameTestState.`firemaking level 99 milestone completes when a log is lit`() =
        runGameTest(LeagueSkillTasks::class) {
            val coords = CoordGrid(0, 50, 50, 0, 0)
            val logs = objTypes[objs.logs]
            player.stats[stats.firemaking] = 98
            eventBus.publish(Firemaking.LogLit(player, logs, coords))
            assertFalse(tracker().isComplete(player, 266))

            player.stats[stats.firemaking] = 99
            eventBus.publish(Firemaking.LogLit(player, logs, coords))
            assertTrue(tracker().isComplete(player, 266))
            assertFalse(tracker().isComplete(player, 267))
        }

    @Test
    fun GameTestState.`finding a gem while mining completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(tracker().isComplete(player, 305))
            eventBus.publish(Mining.GemFound(player, objTypes[isle_objs.uncut_sapphire]))
            assertTrue(tracker().isComplete(player, 305))
        }

    @Test
    fun GameTestState.`smelting tasks complete from smelted bars`() =
        runGameTest(LeagueSkillTasks::class) {
            fun smelt(obj: ObjType) = eventBus.publish(Smelting.BarSmelted(player, obj))

            smelt(objTypes[isle_objs.bronze_bar])
            assertTrue(tracker().isComplete(player, 347))
            assertFalse(tracker().isComplete(player, 348))
            smelt(objTypes[isle_objs.iron_bar])
            assertTrue(tracker().isComplete(player, 348))
            smelt(objTypes[isle_objs.steel_bar])
            assertTrue(tracker().isComplete(player, 352))
            assertFalse(tracker().isComplete(player, 354))
            smelt(objTypes[isle_objs.runite_bar])
            assertTrue(tracker().isComplete(player, 354))
        }

    @Test
    fun GameTestState.`smithing tasks complete from smithed items`() =
        runGameTest(LeagueSkillTasks::class) {
            fun smith(obj: ObjType, bar: ObjType, quantity: Int = 1) =
                eventBus.publish(Smithing.ItemSmithed(player, obj, bar, 1, quantity))
            val bronze = objTypes[isle_objs.bronze_bar]

            smith(objTypes[isle_objs.bronze_full_helm], bronze)
            assertTrue(tracker().isComplete(player, 1373))
            assertFalse(tracker().isComplete(player, 1374))
            smith(objTypes[isle_objs.bronze_plateskirt], bronze)
            assertTrue(tracker().isComplete(player, 1374))
            smith(objTypes[isle_objs.steel_platebody], objTypes[isle_objs.steel_bar])
            assertTrue(tracker().isComplete(player, 351))

            // Any item made from runite counts, but nothing else does.
            assertFalse(tracker().isComplete(player, 1265))
            smith(objTypes[isle_objs.steel_platebody], objTypes[isle_objs.steel_bar])
            assertFalse(tracker().isComplete(player, 1265))
            smith(objTypes[isle_objs.bronze_full_helm], objTypes[isle_objs.runite_bar])
            assertTrue(tracker().isComplete(player, 1265))
        }

    @Test
    fun GameTestState.`stacks of smithed items each count towards a total`() =
        runGameTest(LeagueSkillTasks::class) {
            val bar = objTypes[isle_objs.iron_bar]
            val arrowtips = objTypes[isle_objs.iron_arrowheads]
            repeat(9) { eventBus.publish(Smithing.ItemSmithed(player, arrowtips, bar, 1, 15)) }
            assertFalse(tracker().isComplete(player, 349))
            eventBus.publish(Smithing.ItemSmithed(player, arrowtips, bar, 1, 15))
            assertTrue(tracker().isComplete(player, 349))

            // One smith of ten steel bolts is already the whole task.
            val bolts = objTypes[isle_objs.steel_bolts_unf]
            eventBus.publish(
                Smithing.ItemSmithed(player, bolts, objTypes[isle_objs.steel_bar], 1, 10)
            )
            assertTrue(tracker().isComplete(player, 1375))

            val mithrilBolts = objTypes[isle_objs.mithril_bolts_unf]
            val mithril = isle_objs.mithril_bar
            repeat(24) {
                eventBus.publish(
                    Smithing.ItemSmithed(player, mithrilBolts, objTypes[mithril], 1, 10)
                )
            }
            assertFalse(tracker().isComplete(player, 1824))
            eventBus.publish(Smithing.ItemSmithed(player, mithrilBolts, objTypes[mithril], 1, 10))
            assertTrue(tracker().isComplete(player, 1824))
        }

    @Test
    fun GameTestState.`fletching one-off tasks complete from the item made`() =
        runGameTest(LeagueSkillTasks::class) {
            val expected =
                listOf(
                    310 to isle_objs.arrow_shaft,
                    311 to isle_objs.oak_shortbow,
                    313 to isle_objs.willow_shortbow_u,
                    317 to isle_objs.rune_crossbow,
                    1361 to isle_objs.magic_shield,
                )
            for ((task, item) in expected) {
                assertFalse(tracker().isComplete(player, task))
                eventBus.publish(Fletching.ItemFletched(player, objTypes[item], 1))
                assertTrue(tracker().isComplete(player, task))
            }
        }

    @Test
    fun GameTestState.`batches of fletched items each count towards a total`() =
        runGameTest(LeagueSkillTasks::class) {
            val arrows = objTypes[isle_objs.iron_arrow]
            repeat(9) { eventBus.publish(Fletching.ItemFletched(player, arrows, 15)) }
            assertFalse(tracker().isComplete(player, 312))
            eventBus.publish(Fletching.ItemFletched(player, arrows, 15))
            assertTrue(tracker().isComplete(player, 312))

            val stocks = objTypes[isle_objs.oak_stock]
            repeat(24) { eventBus.publish(Fletching.ItemFletched(player, stocks, 1)) }
            assertFalse(tracker().isComplete(player, 1357))
            eventBus.publish(Fletching.ItemFletched(player, stocks, 1))
            assertTrue(tracker().isComplete(player, 1357))

            // Shafts come in batches of up to a hundred, and the auto-fletching axe counts too.
            val shafts = objTypes[isle_objs.arrow_shaft]
            repeat(66) { eventBus.publish(Fletching.ItemFletched(player, shafts, 15)) }
            assertFalse(tracker().isComplete(player, 1358))
            eventBus.publish(Fletching.ItemFletched(player, shafts, 15))
            assertTrue(tracker().isComplete(player, 1358))

            val longbows = objTypes[isle_objs.magic_longbow_u]
            repeat(199) { eventBus.publish(Fletching.ItemFletched(player, longbows, 1)) }
            assertFalse(tracker().isComplete(player, 1360))
            eventBus.publish(Fletching.ItemFletched(player, longbows, 1))
            assertTrue(tracker().isComplete(player, 1360))
        }

    @Test
    fun GameTestState.`broad ammo, a redwood shield and the high level counts complete tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(tracker().isComplete(player, 315))
            eventBus.publish(Fletching.ItemFletched(player, objTypes[isle_objs.broad_bolts], 10))
            assertTrue(tracker().isComplete(player, 315))

            assertFalse(tracker().isComplete(player, 1362))
            eventBus.publish(Fletching.ItemFletched(player, objTypes[isle_objs.redwood_shield], 1))
            assertTrue(tracker().isComplete(player, 1362))

            val longbows = objTypes[isle_objs.maple_longbow_u]
            repeat(99) { eventBus.publish(Fletching.ItemFletched(player, longbows, 1)) }
            assertFalse(tracker().isComplete(player, 700))
            eventBus.publish(Fletching.ItemFletched(player, longbows, 1))
            assertTrue(tracker().isComplete(player, 700))

            val javelins = objTypes[isle_objs.dragon_javelin]
            repeat(6) { eventBus.publish(Fletching.ItemFletched(player, javelins, 15)) }
            assertFalse(tracker().isComplete(player, 1364))
            eventBus.publish(Fletching.ItemFletched(player, javelins, 15))
            assertTrue(tracker().isComplete(player, 1364))

            val darts = objTypes[isle_objs.dragon_dart]
            repeat(19) { eventBus.publish(Fletching.ItemFletched(player, darts, 10)) }
            assertFalse(tracker().isComplete(player, 1365))
            eventBus.publish(Fletching.ItemFletched(player, darts, 10))
            assertTrue(tracker().isComplete(player, 1365))
        }

    @Test
    fun GameTestState.`fletching level 99 milestone completes when something is fletched`() =
        runGameTest(LeagueSkillTasks::class) {
            val shaft = objTypes[isle_objs.arrow_shaft]
            player.stats[stats.fletching] = 98
            eventBus.publish(Fletching.ItemFletched(player, shaft, 15))
            assertFalse(tracker().isComplete(player, 252))

            player.stats[stats.fletching] = 99
            eventBus.publish(Fletching.ItemFletched(player, shaft, 15))
            assertTrue(tracker().isComplete(player, 252))
            assertFalse(tracker().isComplete(player, 253))
        }

    @Test
    fun GameTestState.`smithing level 99 milestone completes when a bar is smelted`() =
        runGameTest(LeagueSkillTasks::class) {
            val bar = objTypes[isle_objs.bronze_bar]
            player.stats[stats.smithing] = 98
            eventBus.publish(Smelting.BarSmelted(player, bar))
            assertFalse(tracker().isComplete(player, 260))

            player.stats[stats.smithing] = 99
            eventBus.publish(Smelting.BarSmelted(player, bar))
            assertTrue(tracker().isComplete(player, 260))
            assertFalse(tracker().isComplete(player, 261))
        }

    @Test
    fun GameTestState.`level 99 milestone completes on the next skill action`() =
        runGameTest(LeagueSkillTasks::class) {
            val copper = objTypes[objs.copper_ore]
            player.stats[stats.mining] = 98
            eventBus.publish(Mining.MineOre(player, dummyLoc, copper))
            assertFalse(tracker().isComplete(player, 258))

            player.stats[stats.mining] = 99
            eventBus.publish(Mining.MineOre(player, dummyLoc, copper))
            assertTrue(tracker().isComplete(player, 258))
        }

    @Test
    fun GameTestState.`completion bits use the task id as varp index and bit`() =
        runGameTest(LeagueSkillTasks::class) {
            // Task 1371 -> varp index 42, bit 27.
            tracker().complete(player, 1371)
            assertEquals(1 shl 27, player.vars[LeagueTaskVars.completed[42]])
            // Task 303 -> varp index 9, bit 15 (no other bit set in that varp).
            tracker().complete(player, 303)
            assertEquals(1 shl 15, player.vars[LeagueTaskVars.completed[9]])
        }

    @Test
    fun GameTestState.`reset clears bits points and counters`() =
        runGameTest(LeagueSkillTasks::class) {
            tracker().complete(player, 303)
            tracker().addProgress(player, 306, LeagueProgressVarps.iron_ore, 50)
            tracker().reset(player)

            assertFalse(tracker().isComplete(player, 303))
            assertEquals(0, player.totalTasks())
            assertEquals(0, player.points())
            assertEquals(0, player.vars[LeagueProgressVarps.iron_ore])
        }

    @Test
    fun GameTestState.`completing a task shows the notification pop-up`() =
        runGameTest(LeagueSkillTasks::class) {
            tracker().complete(player, 303)

            assertTrue(player.ui.containsOverlay(isle_interfaces.notification_display))
            val popup = client.filterIsInstance<RunClientScript>().filter { it.id == 3343 }
            assertEquals(1, popup.size)
            assertEquals(
                listOf<Any>(0x20C2FE, "League Task Complete!"),
                popup.single().values.take(2),
            )
            assertEquals(
                "Task Completed: <col=ffffff>Mine some Copper Ore</col><br><br>" +
                    "Points Earned: <col=ffffff>10</col>",
                popup.single().values[2],
            )
        }

    @Test
    fun GameTestState.`leagues tab is refreshed only while it is open`() =
        runGameTest(LeagueSkillTasks::class) {
            // Tab closed: no panel update script is sent.
            tracker().complete(player, 303)
            assertEquals(0, client.filterIsInstance<RunClientScript>().count { it.id == 3226 })

            // Tab open: the next completion re-runs `league_side_panel_update_bar`.
            player.ifOpenOverlay(
                isle_interfaces.league_side_panel,
                components.toplevel_target_side2,
            )
            tracker().complete(player, 304)
            val updates = client.filterIsInstance<RunClientScript>().filter { it.id == 3226 }
            assertEquals(1, updates.size)
            assertEquals(player.vars[LeagueTaskVars.points_claimed], updates.single().values[1])
        }

    @Test
    fun GameTestState.`completing a task sends both league chat messages`() =
        runGameTest(LeagueSkillTasks::class) {
            tracker().complete(player, 303) // easy
            tracker().complete(player, 306) // medium
            tracker().complete(player, 309) // hard
            tracker().complete(player, 258) // elite
            val messages = client.filterIsInstance<MessageGame>().map { it.message }

            assertEquals(
                "Congratulations, you've completed an easy task: " +
                    "<col=147194>Mine some Copper Ore</col>.",
                messages[0],
            )
            assertEquals(
                "You have earned <col=147194>10 League Points</col>. " +
                    "You now have <col=147194>10 League Points</col> in total.",
                messages[1],
            )
            assertTrue(messages[2].startsWith("Congratulations, you've completed a medium task: "))
            assertTrue(messages[4].startsWith("Congratulations, you've completed a hard task: "))
            assertTrue(messages[6].startsWith("Congratulations, you've completed an elite task: "))
            // Points accumulate: 10 + 30 + 80 + 200.
            assertTrue(messages[7].contains("<col=147194>320 League Points</col> in total."))
        }

    private fun GameTestScope.caught(fish: ObjType) {
        val spot = spawnNpc(CoordGrid(0, 50, 50, 34, 31), npcTypes.values.first())
        val item = objTypes[fish]
        eventBus.publish(Fishing.FishCaught(player, spot, item, item))
    }

    private fun GameTestScope.cooked(raw: ObjType, done: ObjType) {
        eventBus.publish(Cooking.Cooked(player, objTypes[raw], objTypes[done]))
    }

    @Test
    fun GameTestState.`catching a fish for the first time completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            caught(isle_objs.raw_shrimp)
            assertTrue(tracker().isComplete(player, 340))
            assertFalse(tracker().isComplete(player, 341))

            caught(isle_objs.raw_herring)
            caught(isle_objs.raw_anchovies)
            caught(isle_objs.raw_salmon)
            caught(isle_objs.raw_karambwanji)
            caught(isle_objs.raw_monkfish)
            for (id in listOf(341, 342, 1548, 644, 696)) {
                assertTrue(tracker().isComplete(player, id), "task $id")
            }
        }

    @Test
    fun GameTestState.`counted fishing tasks complete at their goal`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(99) { caught(isle_objs.raw_lobster) }
            assertFalse(tracker().isComplete(player, 343))
            caught(isle_objs.raw_lobster)
            assertTrue(tracker().isComplete(player, 343))

            // Salmon counts towards both "Catch 50 Salmon" and the one-off task.
            repeat(50) { caught(isle_objs.raw_salmon) }
            assertTrue(tracker().isComplete(player, 345))
            assertTrue(tracker().isComplete(player, 1548))

            repeat(10) { caught(isle_objs.raw_pike) }
            assertTrue(tracker().isComplete(player, 1827))
            repeat(24) { caught(isle_objs.raw_sardine) }
            assertFalse(tracker().isComplete(player, 1825))
            caught(isle_objs.raw_sardine)
            assertTrue(tracker().isComplete(player, 1825))
        }

    @Test
    fun GameTestState.`anglerfish tasks complete from catching and cooking`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(149) { caught(isle_objs.raw_anglerfish) }
            assertFalse(tracker().isComplete(player, 1609))
            caught(isle_objs.raw_anglerfish)
            assertTrue(tracker().isComplete(player, 1609))

            repeat(99) { cooked(isle_objs.raw_anglerfish, isle_objs.cooked_anglerfish) }
            assertFalse(tracker().isComplete(player, 1349))
            cooked(isle_objs.raw_anglerfish, isle_objs.cooked_anglerfish)
            assertTrue(tracker().isComplete(player, 1349))
        }

    @Test
    fun GameTestState.`cooking tasks complete from cooked food`() =
        runGameTest(LeagueSkillTasks::class) {
            cooked(isle_objs.raw_shrimp, isle_objs.cooked_shrimp)
            assertTrue(tracker().isComplete(player, 296))
            // One cook so far: the "5 pieces of food" task counts any food.
            assertFalse(tracker().isComplete(player, 298))
            repeat(4) { cooked(isle_objs.raw_lobster, isle_objs.cooked_lobster) }
            assertTrue(tracker().isComplete(player, 298))

            repeat(20) { cooked(isle_objs.raw_pike, isle_objs.cooked_pike) }
            assertTrue(tracker().isComplete(player, 1828))
            repeat(10) { cooked(isle_objs.raw_sardine, isle_objs.cooked_sardine) }
            assertTrue(tracker().isComplete(player, 1826))
        }

    @Test
    fun GameTestState.`cooking tasks that named a place complete anywhere`() =
        runGameTest(LeagueSkillTasks::class) {
            cooked(isle_objs.cooked_rabbit, isle_objs.cooked_rabbit)
            assertTrue(tracker().isComplete(player, 514))

            // 20 sharks is its own count, apart from "Cook 100 Sharks".
            repeat(19) { cooked(isle_objs.raw_shark, isle_objs.cooked_shark) }
            assertFalse(tracker().isComplete(player, 1587))
            cooked(isle_objs.raw_shark, isle_objs.cooked_shark)
            assertTrue(tracker().isComplete(player, 1587))
            assertFalse(tracker().isComplete(player, 302))

            // Food cooked on a fire is not "on a range".
            assertFalse(tracker().isComplete(player, 450))
            eventBus.publish(
                Cooking.Cooked(
                    player,
                    objTypes[isle_objs.raw_shrimp],
                    objTypes[isle_objs.cooked_shrimp],
                    onRange = true,
                )
            )
            assertTrue(tracker().isComplete(player, 450))
        }

    @Test
    fun GameTestState.`pies count towards both pie tasks`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(20) { cooked(isle_objs.redberry_pie, isle_objs.redberry_pie) }
            assertTrue(tracker().isComplete(player, 1348))
            assertFalse(tracker().isComplete(player, 1347))

            repeat(40) { cooked(isle_objs.meat_pie, isle_objs.meat_pie) }
            repeat(40) { cooked(isle_objs.apple_pie, isle_objs.apple_pie) }
            assertTrue(tracker().isComplete(player, 1347))
        }

    @Test
    fun GameTestState.`burning food completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(tracker().isComplete(player, 297))
            val raw = objTypes[isle_objs.raw_shrimp]
            eventBus.publish(Cooking.Burnt(player, raw, raw))
            assertTrue(tracker().isComplete(player, 297))
        }

    @Test
    fun GameTestState.`karambwan in a row resets when one burns`() =
        runGameTest(LeagueSkillTasks::class) {
            val raw = objTypes[isle_objs.raw_karambwan]
            repeat(19) { cooked(isle_objs.raw_karambwan, isle_objs.cooked_karambwan) }
            eventBus.publish(Cooking.Burnt(player, raw, raw))
            assertEquals(0, player.vars[LeagueProgressVarps.cook_karambwan_row])

            repeat(19) { cooked(isle_objs.raw_karambwan, isle_objs.cooked_karambwan) }
            assertFalse(tracker().isComplete(player, 1395))
            cooked(isle_objs.raw_karambwan, isle_objs.cooked_karambwan)
            assertTrue(tracker().isComplete(player, 1395))

            // The separate "Cook 100 Karambwans" total was not reset by the burn.
            assertEquals(39, player.vars[LeagueProgressVarps.cook_karambwan])
        }

    @Test
    fun GameTestState.`fishing and cooking level 99 milestones complete`() =
        runGameTest(LeagueSkillTasks::class) {
            player.stats[stats.fishing] = 99
            caught(isle_objs.raw_shrimp)
            assertTrue(tracker().isComplete(player, 262))

            player.stats[stats.cooking] = 98
            cooked(isle_objs.raw_shrimp, isle_objs.cooked_shrimp)
            assertFalse(tracker().isComplete(player, 264))
            player.stats[stats.cooking] = 99
            cooked(isle_objs.raw_shrimp, isle_objs.cooked_shrimp)
            assertTrue(tracker().isComplete(player, 264))
        }

    @Test
    fun GameTestState.`wearing all four prospector pieces completes the outfit task`() =
        runGameTest(LeagueSkillTasks::class) {
            fun wear(pos: Wearpos, obj: ObjType) {
                player.worn[pos.slot] = InvObj(obj)
                eventBus.publish(HeldEquipEvents.WearposChange(player, pos, objTypes[obj]))
            }

            wear(Wearpos.Hat, ProspectorObjs.helmet)
            wear(Wearpos.Torso, ProspectorObjs.jacket)
            wear(Wearpos.Legs, ProspectorObjs.legs)
            assertFalse(tracker().isComplete(player, 772))

            wear(Wearpos.Feet, ProspectorObjs.boots)
            assertTrue(tracker().isComplete(player, 772))
            assertEquals(80, player.points())
        }

    @Test
    fun GameTestState.`twenty golden nuggets complete the nugget task`() =
        runGameTest(LeagueSkillTasks::class) {
            repeat(19) { eventBus.publish(GoldenNuggetFound(player)) }
            assertFalse(tracker().isComplete(player, 771))
            assertEquals(19, player.vars[LeagueProgressVarps.golden_nuggets])

            eventBus.publish(GoldenNuggetFound(player))
            assertTrue(tracker().isComplete(player, 771))
            assertEquals(30, player.points())
        }

    @Test
    fun GameTestState.`entering the mining guild completes its task`() =
        runGameTest(LeagueSkillTasks::class) {
            assertFalse(tracker().isComplete(player, 1845))
            eventBus.publish(MiningGuildEntered(player))
            assertTrue(tracker().isComplete(player, 1845))
            assertEquals(30, player.points())
        }
}
