package org.rsmod.content.skills.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.MasterTask
import org.rsmod.content.skills.slayer.configs.SlayerBlocked
import org.rsmod.content.skills.slayer.configs.SlayerMaster
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerRefusal
import org.rsmod.content.skills.slayer.scripts.SlayerScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Gives the test the engine the injector built, since a test cannot ask the injector itself. */
class SlayerEngineHolder @Inject constructor(engine: SlayerEngine) : PluginScript() {
    init {
        held = engine
    }

    override fun ScriptContext.startup() {}

    companion object {
        lateinit var held: SlayerEngine
    }
}

// The holders above are static, so the tests in a class must not run side by side.
@Execution(ExecutionMode.SAME_THREAD)
class SlayerEngineTest {
    private val engine: SlayerEngine
        get() = SlayerEngineHolder.held

    private fun master(key: String): SlayerMaster = checkNotNull(engine.tables.master(key))

    private fun entry(master: SlayerMaster, taskId: Int): MasterTask =
        master.tasks.first { it.task.id == taskId }

    private fun GameTestScope.prepare(combat: Int, slayer: Int) {
        player.stats[stats.slayer] = slayer
        player.statMap.setXP(stats.slayer, 0)
        player.appearance.combatLevel = combat
        engine.cancel(player)
        for (master in engine.tables.masters.values) {
            for (varbit in SlayerBlocked.slots(master)) {
                player.withProtectedAccess { vars[varbit] = 0 }
            }
        }
        player.withProtectedAccess {
            vars[SlayerVarps.unlocks] = 0
            vars[SlayerVarps.unlocks_high] = 0
            vars[SlayerVarBits.points] = 0
        }
    }

    private fun GameTestScope.monster(taskId: Int): Npc {
        val type: UnpackedNpcType =
            npcTypes.values.first { it.param(SlayerParams.task_id) == taskId && it.vislevel > 0 }
        return spawnNpc(CoordGrid(0, 50, 50, 0, 0), type)
    }

    private fun GameTestScope.kill(npc: Npc) {
        eventBus.publish(NpcDeathEvents.Killed(npc, player))
    }

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.slayer) / 10.0

    private fun names(entries: List<MasterTask>) = entries.map { it.task.name }

    @Test
    fun GameTestState.`the three masters are loaded from the cache data`() =
        runGameTest(SlayerEngineHolder::class) {
            val tables = engine.tables
            assertEquals(setOf("turael", "vannaka", "duradel"), tables.masters.keys)
            assertEquals(24, master("turael").tasks.size)
            assertTrue(master("vannaka").tasks.size > 30)
            assertEquals(40, master("vannaka").combat)
            assertEquals(100, master("duradel").combat)
            assertEquals(50, master("duradel").slayer)
        }

    @Test
    fun GameTestState.`a master gives only tasks the player meets the requirements for`() =
        runGameTest(SlayerEngineHolder::class) {
            val turael = master("turael")
            prepare(combat = 20, slayer = 1)
            val low = names(engine.eligible(player, turael))
            assertTrue("Monkeys" in low)
            assertFalse("Banshees" in low, "Banshees need 15 Slayer")
            assertFalse("Cave Bugs" in low, "Cave bugs need 7 Slayer")

            player.stats[stats.slayer] = 15
            val higher = names(engine.eligible(player, turael))
            assertTrue("Banshees" in higher)
        }

    @Test
    fun GameTestState.`the Leagues window keeps strong players off low tasks`() =
        runGameTest(SlayerEngineHolder::class) {
            val turael = master("turael")
            prepare(combat = 120, slayer = 99)
            val tasks = engine.eligible(player, turael)
            assertTrue(tasks.isNotEmpty())
            assertFalse("Rats" in names(tasks), "Rats stop at combat 50")
            assertTrue(tasks.all { (it.task.leaguesMax ?: Int.MAX_VALUE) >= 120 })

            // A weak player still gets them.
            player.appearance.combatLevel = 30
            assertTrue("Rats" in names(engine.eligible(player, turael)))
        }

    @Test
    fun GameTestState.`a window that would leave nothing is ignored`() =
        runGameTest(SlayerEngineHolder::class) {
            val turael = master("turael")
            prepare(combat = 120, slayer = 99)
            // Kalphite is the only Turael task without a ceiling.
            player.withProtectedAccess { vars[SlayerBlocked.slots(turael)[0]] = 53 }
            val tasks = names(engine.eligible(player, turael))
            assertTrue(tasks.size > 1, "Fell back to the whole list: $tasks")
            assertFalse("Kalphite" in tasks, "A blocked task is never given")
        }

    @Test
    fun GameTestState.`a master wants the combat level and Slayer level`() =
        runGameTest(SlayerEngineHolder::class) {
            prepare(combat = 39, slayer = 1)
            assertEquals(SlayerRefusal.Combat, engine.canUse(player, master("vannaka")))
            player.appearance.combatLevel = 40
            assertNull(engine.canUse(player, master("vannaka")))

            player.appearance.combatLevel = 100
            assertEquals(SlayerRefusal.SlayerLevel, engine.canUse(player, master("duradel")))
            player.stats[stats.slayer] = 50
            assertNull(engine.canUse(player, master("duradel")))
        }

    @Test
    fun GameTestState.`assigning writes the task and refuses a second one`() =
        runGameTest(SlayerEngineHolder::class) {
            val vannaka = master("vannaka")
            prepare(combat = 100, slayer = 70)
            val spectres = entry(vannaka, 41)
            random.next = 25 // The amount.

            val (given, refusal) = engine.assign(player, vannaka, spectres)
            assertNull(refusal)
            assertNotNull(given)
            assertEquals(25, given!!.amount)
            assertEquals(41, player.vars[SlayerVarps.target])
            assertEquals(25, player.vars[SlayerVarps.count])
            assertEquals(vannaka.masterId, player.vars[SlayerVarBits.master])

            assertEquals(SlayerRefusal.HasTask, engine.assign(player, vannaka).second)
        }

    @Test
    fun GameTestState.`a rolled task has an amount in the masters range`() =
        runGameTest(SlayerEngineHolder::class) {
            val turael = master("turael")
            prepare(combat = 20, slayer = 1)
            val (given, _) = engine.assign(player, turael)
            val task = turael.tasks.first { it.task.id == given!!.task.id }
            assertTrue(
                given!!.amount in task.min..task.max,
                "${given.amount} for ${task.task.name}",
            )
        }

    @Test
    fun GameTestState.`killing the task monster counts down and gives experience`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            val vannaka = master("vannaka")
            prepare(combat = 100, slayer = 70)
            random.next = 10
            engine.assign(player, vannaka, entry(vannaka, 41))

            kill(monster(41))
            assertEquals(9, player.vars[SlayerVarps.count])
            assertEquals(90.0, fineXp(), "Aberrant spectres give 90 Slayer XP")

            kill(monster(48)) // A bloodveld, not the task.
            assertEquals(9, player.vars[SlayerVarps.count])
        }

    @Test
    fun GameTestState.`finishing a task gives points and a streak`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            val vannaka = master("vannaka")
            prepare(combat = 100, slayer = 70)
            random.next = 2
            engine.assign(player, vannaka, entry(vannaka, 41))

            kill(monster(41))
            kill(monster(41))

            assertNull(engine.current(player))
            assertEquals(0, player.vars[SlayerVarps.target])
            assertEquals(1, engine.streak(player))
            assertEquals(8, engine.points(player), "Vannaka gives 8 points a task")
            assertMessageSent(
                "You've completed 1 tasks in a row and received 8 points, giving you a total of 8; " +
                    "return to a Slayer master."
            )
        }

    @Test
    fun GameTestState.`every tenth task gives more points and Turael gives none`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            val vannaka = master("vannaka")
            prepare(combat = 100, slayer = 70)
            player.withProtectedAccess { vars[SlayerVarBits.streak] = 9 }
            random.next = 1
            engine.assign(player, vannaka, entry(vannaka, 41))
            kill(monster(41))
            assertEquals(10, engine.streak(player))
            assertEquals(40, engine.points(player))

            val turael = master("turael")
            random.next = 1
            engine.assign(player, turael, entry(turael, 6)) // Cows.
            kill(monster(6))
            assertEquals(11, engine.streak(player), "Turael's tasks still count for the streak")
            assertEquals(40, engine.points(player), "but give no points")
        }

    @Test
    fun GameTestState.`a group task counts every member`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            val duradel = master("duradel")
            prepare(combat = 120, slayer = 80)
            random.next = 40
            engine.assign(player, duradel, entry(duradel, 127)) // Metal dragons.

            kill(monster(58)) // A bronze dragon.
            kill(monster(109)) // A rune dragon.
            assertEquals(38, player.vars[SlayerVarps.count])
        }

    @Test
    fun GameTestState.`a revenant task is on task for the revenants, from Vannaka and Duradel`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            prepare(combat = 120, slayer = 80)
            for (key in listOf("vannaka", "duradel")) {
                val master = master(key)
                random.next = 40
                engine.cancel(player)
                engine.assign(player, master, entry(master, 107)) // Revenants.
                assertTrue(engine.isOnTask(player, monster(107)), key)
            }
        }

    @Test
    fun GameTestState.`the cave kraken task is on offer and counts the kraken`() =
        runGameTest(SlayerEngineHolder::class, SlayerScript::class) {
            val duradel = master("duradel")
            assertTrue("Cave Kraken" in names(duradel.tasks), "Whirlpools are the krakens' spawn")
            prepare(combat = 120, slayer = 90)
            player.stats[stats.magic] = 99
            random.next = 100
            engine.assign(player, duradel, entry(duradel, 92))
            kill(monster(92))
            assertEquals(99, player.vars[SlayerVarps.count])
        }

    @Test
    fun GameTestState.`an extended task asks for more kills once bought`() =
        runGameTest(SlayerEngineHolder::class) {
            val vannaka = master("vannaka")
            prepare(combat = 100, slayer = 70)
            val spectres = entry(vannaka, 41)
            val bit = checkNotNull(spectres.task.extendBit)
            assertEquals(spectres.min..spectres.max, engine.amountRange(player, spectres))

            engine.setUnlocked(player, bit, true)
            assertTrue(engine.isUnlocked(player, bit))
            assertEquals(
                spectres.task.extendMin..spectres.task.extendMax,
                engine.amountRange(player, spectres),
            )
            engine.setUnlocked(player, bit, false)
            assertFalse(engine.isUnlocked(player, bit))
        }

    @Test
    fun GameTestState.`unlock bits above 31 live in the second varp`() =
        runGameTest(SlayerEngineHolder::class) {
            prepare(combat = 100, slayer = 70)
            engine.setUnlocked(player, 3, true)
            engine.setUnlocked(player, 40, true)
            assertEquals(1 shl 3, player.vars[SlayerVarps.unlocks])
            assertEquals(1 shl 8, player.vars[SlayerVarps.unlocks_high])
            assertTrue(engine.isUnlocked(player, 3))
            assertTrue(engine.isUnlocked(player, 40))
            assertFalse(engine.isUnlocked(player, 41))
        }

    @Test
    fun GameTestState.`cancelling drops the task and the streak`() =
        runGameTest(SlayerEngineHolder::class) {
            val turael = master("turael")
            prepare(combat = 20, slayer = 1)
            player.withProtectedAccess { vars[SlayerVarBits.streak] = 7 }
            random.next = 20
            engine.assign(player, turael, entry(turael, 6))
            engine.cancel(player)
            assertNull(engine.current(player))
            assertEquals(0, engine.streak(player))
        }

    @Test
    fun GameTestState.`a locked task is not offered until its unlock is bought`() =
        runGameTest(SlayerEngineHolder::class) {
            val duradel = master("duradel")
            prepare(combat = 100, slayer = 99) // Inside the Leagues window of every locked task.
            val locked = duradel.tasks.first { it.unlockBit != null }
            assertFalse(
                locked.task.name in names(engine.eligible(player, duradel)),
                locked.task.name,
            )
            engine.setUnlocked(player, locked.unlockBit!!, true)
            assertTrue(locked.task.name in names(engine.eligible(player, duradel)))
        }

    @Test
    fun GameTestState.`every monster of an offered task gives Slayer experience`() =
        runGameTest(SlayerEngineHolder::class) {
            val offered = engine.tables.masters.values.flatMap { it.tasks }.map { it.task }
            val missing = mutableListOf<String>()
            for (type in npcTypes.values) {
                val taskId = type.param(SlayerParams.task_id)
                if (taskId == 0 || type.vislevel == 0 || type.hitpoints == 0) continue
                if (offered.none { it.covers(taskId) }) continue
                val tenths = type.param(params.slayer_experience)
                if (engine.slayerXp(type.id, tenths, type.hitpoints) <= 0.0) {
                    missing += "${type.name} (${type.id})"
                }
            }
            assertTrue(missing.isEmpty(), "No Slayer XP for: $missing")
        }
}
