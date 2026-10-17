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
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerUnlocks
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerEvents
import org.rsmod.content.skills.slayer.scripts.SlayerScript
import org.rsmod.content.skills.slayer.scripts.SlayerSuperiors
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SuperiorsHolder
@Inject
constructor(superiors: SlayerSuperiors, engine: SlayerEngine, repo: NpcRepository) :
    PluginScript() {
    init {
        heldSuperiors = superiors
        heldEngine = engine
        heldRepo = repo
    }

    override fun ScriptContext.startup() {
        onEvent<SlayerEvents.SuperiorKilled> { killed += 1 }
        onEvent<SlayerEvents.SuperiorSpawned> { spawned += 1 }
    }

    companion object {
        lateinit var heldSuperiors: SlayerSuperiors
        lateinit var heldEngine: SlayerEngine
        lateinit var heldRepo: NpcRepository
        var killed = 0
        var spawned = 0
    }
}

// The holders above are static, so the tests in a class must not run side by side.
@Execution(ExecutionMode.SAME_THREAD)
class SlayerSuperiorsTest {
    private val engine
        get() = SuperiorsHolder.heldEngine

    private val superiors
        get() = SuperiorsHolder.heldSuperiors

    private val scripts =
        arrayOf(SuperiorsHolder::class, SlayerScript::class, SlayerSuperiors::class)

    private val spot = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.prepare(unlocked: Boolean) {
        SuperiorsHolder.killed = 0
        SuperiorsHolder.spawned = 0
        player.stats[stats.slayer] = 99
        player.appearance.combatLevel = 126
        engine.cancel(player)
        player.withProtectedAccess {
            vars[SlayerVarps.unlocks] = 0
            vars[SlayerVarps.unlocks_high] = 0
            vars[SlayerVarBits.points] = 0
        }
        if (unlocked) {
            engine.setUnlocked(player, SlayerUnlocks.BIGGER_AND_BADDER, true)
        }
        player.teleport(spot)
        advance(12)
        val vannaka = checkNotNull(engine.tables.master("vannaka"))
        random.next = 40
        engine.assign(player, vannaka, vannaka.tasks.first { it.task.id == 41 })
    }

    private fun GameTestScope.spectre(): Npc {
        val type =
            npcTypes.values.first {
                it.param(SlayerParams.task_id) == 41 &&
                    it.vislevel > 0 &&
                    it.name.equals("Aberrant spectre", ignoreCase = true)
            }
        return spawnNpc(spot.translateX(1), type)
    }

    private fun GameTestScope.superiorsNear(): List<Npc> =
        SuperiorsHolder.heldRepo
            .findAll(spot.translateX(1))
            .filter { it.visType.id in engine.superiorIds }
            .toList()

    @Test
    fun GameTestState.`the odds are one in 200 unless a hook asks for better`() =
        runGameTest(*scripts) {
            prepare(unlocked = true)
            assertEquals(200, superiors.odds(player))
            engine.hooks.superiorOdds += { 50 }
            engine.hooks.superiorOdds += { null }
            assertEquals(50, superiors.odds(player))
        }

    @Test
    fun GameTestState.`a superior needs Bigger and Badder`() =
        runGameTest(*scripts) {
            prepare(unlocked = false)
            random.next = 0
            eventBus.publish(NpcDeathEvents.Killed(spectre(), player))
            assertTrue(superiorsNear().isEmpty(), "Not unlocked")
            assertEquals(0, SuperiorsHolder.spawned)
        }

    @Test
    fun GameTestState.`a task kill can bring up the superior`() =
        runGameTest(*scripts) {
            prepare(unlocked = true)
            val spectre = spectre()
            val before = engine.current(player)!!.remaining
            random.next = 0 // The superior roll succeeds.
            eventBus.publish(NpcDeathEvents.Killed(spectre, player))
            assertEquals(before - 1, engine.current(player)!!.remaining)
            assertEquals(1, SuperiorsHolder.spawned)
            val superior = superiorsNear().firstOrNull()
            assertNotNull(superior, "An Abhorrent spectre should be waiting")
            assertEquals("Abhorrent spectre", superior!!.visType.name)

            // The superior counts for the task and says so.
            eventBus.publish(NpcDeathEvents.Killed(superior, player))
            assertEquals(before - 2, engine.current(player)!!.remaining)
            assertEquals(1, SuperiorsHolder.killed)
        }

    @Test
    fun GameTestState.`a missed roll brings nothing up`() =
        runGameTest(*scripts) {
            prepare(unlocked = true)
            random.next = 1
            eventBus.publish(NpcDeathEvents.Killed(spectre(), player))
            assertEquals(0, SuperiorsHolder.spawned)
            assertTrue(superiorsNear().isEmpty())
        }

    @Test
    fun GameTestState.`a monster that is not the task has no superior`() =
        runGameTest(*scripts) {
            prepare(unlocked = true)
            val bloodveld =
                npcTypes.values.first { it.param(SlayerParams.task_id) == 48 && it.vislevel > 0 }
            random.next = 0
            eventBus.publish(NpcDeathEvents.Killed(spawnNpc(spot.translateX(1), bloodveld), player))
            assertEquals(0, SuperiorsHolder.spawned)
        }

    @Test
    fun GameTestState.`every base monster maps to a superior in the cache`() =
        runGameTest(*scripts) {
            val missing =
                SlayerSuperiors.BASE_TO_SUPERIOR.filter { (_, name) ->
                    npcTypes.values.none {
                        it.id in engine.superiorIds && it.name.equals(name, ignoreCase = true)
                    }
                }
            assertTrue(missing.isEmpty(), "No such superior in the cache: $missing")
            assertFalse(SlayerSuperiors.BASE_TO_SUPERIOR.isEmpty())
            assertNull(null)
        }

    @Test
    fun GameTestState.`the shared superior drops use the wiki odds`() =
        runGameTest(*scripts) {
            prepare(unlocked = true)
            val superior =
                npcTypes.values.first {
                    it.name == "Abhorrent spectre" && it.id in engine.superiorIds
                }
            val npc = spawnNpc(spot.translateX(1), superior)
            random.next = 0 // Every roll hits.
            random.then = 0
            random.then = 0
            random.then = 0
            superiors.tertiary(player, npc)
        }
}
