package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerScript
import org.rsmod.content.skills.slayer.scripts.SlayerSuperiors
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerRelicHolder @Inject constructor(engine: SlayerEngine, superiors: SlayerSuperiors) :
    PluginScript() {
    init {
        heldEngine = engine
        heldSuperiors = superiors
    }

    override fun ScriptContext.startup() {}

    companion object {
        lateinit var heldEngine: SlayerEngine
        lateinit var heldSuperiors: SlayerSuperiors
    }
}

class LeagueSlayerRelicsTest {
    private val engine
        get() = SlayerRelicHolder.heldEngine

    private val scripts =
        arrayOf(
            SlayerRelicHolder::class,
            LeagueSlayerRelics::class,
            SlayerScript::class,
            SlayerSuperiors::class,
        )

    private val spot = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.choose(tier: Int, option: Int) {
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[tier], option)
    }

    private fun GameTestScope.prepare() {
        player.stats[stats.slayer] = 99
        player.statMap.setXP(stats.slayer, 0)
        player.appearance.combatLevel = 126
        engine.cancel(player)
        player.withProtectedAccess {
            vars[SlayerVarBits.points] = 0
            vars[SlayerVarps.unlocks] = 0
            vars[SlayerVarps.unlocks_high] = 0
            for (varp in SlayerKillCounters.varps) {
                vars[varp] = 0
            }
        }
        for (tier in 0 until RelicData.tierCount) {
            choose(tier, 0)
        }
        player.teleport(spot)
        advance(12)
    }

    private fun GameTestScope.monster(taskId: Int): Npc {
        val type =
            npcTypes.values.first { it.param(SlayerParams.task_id) == taskId && it.vislevel > 0 }
        return spawnNpc(spot.translateX(1), type)
    }

    private fun GameTestScope.finishVannakaTask() {
        val vannaka = checkNotNull(engine.tables.master("vannaka"))
        random.next = 1
        engine.assign(player, vannaka, vannaka.tasks.first { it.task.id == 41 })
        eventBus.publish(NpcDeathEvents.Killed(monster(41), player))
    }

    private fun GameTestScope.xp(): Double = player.statMap.getFineXP(stats.slayer) / 10.0

    @Test
    fun GameTestState.`without a relic nothing changes`() =
        runGameTest(*scripts) {
            prepare()
            finishVannakaTask()
            assertEquals(8, engine.points(player))
            assertFalse(engine.isUnlocked(player, SlayerSuperiors.UNLOCK))
            assertFalse(engine.isFree(player))
            assertEquals(200, SlayerRelicHolder.heldSuperiors.odds(player))
        }

    @Test
    fun GameTestState.`passives give five times the points, free superiors and better odds`() =
        runGameTest(*scripts) {
            prepare()
            choose(LeagueSlayerRelics.PASSIVES_TIER, 2)
            finishVannakaTask()
            assertEquals(40, engine.points(player))
            assertTrue(engine.isUnlocked(player, SlayerSuperiors.UNLOCK))
            assertFalse(engine.isUnlocked(player, 0), "Only Bigger and Badder is free")
            assertEquals(50, SlayerRelicHolder.heldSuperiors.odds(player))
            assertFalse(engine.isFree(player))
        }

    @Test
    fun GameTestState.`the Slayer Master unlocks everything and makes the store free`() =
        runGameTest(*scripts) {
            prepare()
            choose(LeagueSlayerRelics.MASTER_TIER, LeagueSlayerRelics.MASTER_OPTION)
            assertTrue(engine.isFree(player))
            for (unlock in engine.tables.unlocks) {
                assertTrue(engine.isUnlocked(player, unlock.bit), unlock.name)
            }
            assertFalse(engine.hasBit(player, 0), "Nothing was bought")
        }

    @Test
    fun GameTestState.`the Slayer Master is on task for every monster they could be given`() =
        runGameTest(*scripts) {
            prepare()
            choose(LeagueSlayerRelics.MASTER_TIER, LeagueSlayerRelics.MASTER_OPTION)
            assertTrue(engine.current(player) == null, "No task at all")
            eventBus.publish(NpcDeathEvents.Killed(monster(48), player))
            assertTrue(xp() > 0.0, "Slayer XP without a task")
        }

    @Test
    fun GameTestState.`the hundredth kill of a kind pays a bonus once`() =
        runGameTest(*scripts) {
            prepare()
            choose(LeagueSlayerRelics.MASTER_TIER, LeagueSlayerRelics.MASTER_OPTION)
            val slot = checkNotNull(SlayerKillCounters.slot(engine, 48))
            val varp = SlayerKillCounters.varps[slot / SlayerKillCounters.PER_VARP]
            val shift = (slot % SlayerKillCounters.PER_VARP) * SlayerKillCounters.BITS
            player.withProtectedAccess { vars[varp] = 99 shl shift }

            val before = xp()
            eventBus.publish(NpcDeathEvents.Killed(monster(48), player))
            val gained = xp() - before
            assertTrue(gained >= 1_000, "The 100th kill should pay at least 1,000, paid $gained")

            val again = xp()
            eventBus.publish(NpcDeathEvents.Killed(monster(48), player))
            assertTrue(xp() - again < 1_000, "Only the 100th pays")
        }

    @Test
    fun GameTestState.`the bonus scales from 1000 at level 1 to 15000 at level 99`() =
        runGameTest(*scripts) {
            assertEquals(1_000, LeagueSlayerRelics.bonusXp(1))
            assertEquals(15_000, LeagueSlayerRelics.bonusXp(99))
            assertTrue(LeagueSlayerRelics.bonusXp(50) in 7_000..9_000)
        }
}
