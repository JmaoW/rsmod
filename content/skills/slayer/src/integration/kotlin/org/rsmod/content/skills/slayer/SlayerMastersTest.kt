package org.rsmod.content.skills.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldOpScript
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerNpcs
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerShop
import org.rsmod.content.skills.slayer.configs.SlayerSpots
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerMasters
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerMastersHolder
@Inject
constructor(masters: SlayerMasters, engine: SlayerEngine, repo: NpcRepository) : PluginScript() {
    init {
        heldMasters = masters
        heldEngine = engine
        heldRepo = repo
    }

    override fun ScriptContext.startup() {}

    companion object {
        lateinit var heldMasters: SlayerMasters
        lateinit var heldEngine: SlayerEngine
        lateinit var heldRepo: NpcRepository
    }
}

// The holders above are static, so the tests in a class must not run side by side.
@Execution(ExecutionMode.SAME_THREAD)
class SlayerMastersTest {
    private val engine: SlayerEngine
        get() = SlayerMastersHolder.heldEngine

    private fun GameTestScope.prepare(combat: Int, slayer: Int) {
        player.stats[stats.slayer] = slayer
        player.appearance.combatLevel = combat
        engine.cancel(player)
        player.withProtectedAccess {
            vars[SlayerVarBits.points] = 0
            vars[SlayerVarps.unlocks] = 0
            vars[SlayerVarps.unlocks_high] = 0
        }
        player.clearInv()
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        advance(12) // A new player counts as just out of combat until the clock has run on.
    }

    private fun GameTestScope.master(type: NpcType): Npc =
        spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcTypes[type])

    private fun GameTestScope.npcLine() {
        player.resumePauseButton(components.chat_left_pbutton)
        advance(2)
    }

    private fun GameTestScope.playerLine() {
        player.resumePauseButton(components.chat_right_pbutton)
        advance(2)
    }

    private fun GameTestScope.pick(option: Int) {
        player.resumePauseButton(components.chatmenu_pbutton, option)
        advance(2)
    }

    @Test
    fun GameTestState.`Turael and Duradel are placed on the isle`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            SlayerMastersHolder.heldMasters.place()
            val repo = SlayerMastersHolder.heldRepo
            for ((tile, type) in
                listOf(
                    SlayerSpots.turael to SlayerNpcs.turael,
                    SlayerSpots.duradel to SlayerNpcs.duradel,
                )) {
                val there = repo.findAll(tile).toList()
                assertTrue(there.any { it.type.id == npcTypes[type].id }, "No master at $tile")
            }
        }

    @Test
    fun GameTestState.`the Assignment option gives a task`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 70)
            val vannaka = master(SlayerNpcs.vannaka)
            player.opNpc3(vannaka)
            advance(3)
            assertNotNull(engine.current(player), "Vannaka gave nothing")
            assertEquals(3, player.vars[SlayerVarBits.master])
        }

    @Test
    fun GameTestState.`talking to a master and asking for an assignment gives one`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 70)
            val vannaka = master(SlayerNpcs.vannaka)
            player.opNpc1(vannaka)
            advance(3)
            npcLine() // The greeting.
            pick(1) // "I need an assignment."
            playerLine()
            assertNotNull(engine.current(player))
        }

    @Test
    fun GameTestState.`a master turns away a player below the combat level`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 39, slayer = 70)
            val vannaka = master(SlayerNpcs.vannaka)
            player.opNpc3(vannaka)
            advance(3)
            assertNull(engine.current(player))
        }

    @Test
    fun GameTestState.`Turael takes a task he could not give and starts a new one`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 90)
            val duradel = checkNotNull(engine.tables.master("duradel"))
            val abyssal = duradel.tasks.first { it.task.id == 42 }
            random.next = 130
            engine.assign(player, duradel, abyssal)
            player.withProtectedAccess { vars[SlayerVarBits.streak] = 12 }

            val turael = master(SlayerNpcs.turael)
            player.opNpc1(turael)
            advance(3)
            npcLine() // The greeting.
            pick(1) // Assignment.
            playerLine()
            npcLine() // The current task.
            pick(1) // An easier task.
            playerLine()
            npcLine() // The streak ends.

            val current = checkNotNull(engine.current(player))
            val tasks = checkNotNull(engine.tables.master("turael")).tasks
            assertTrue(tasks.any { it.task.id == current.task.id })
            assertEquals(0, engine.streak(player))
        }

    @Test
    fun GameTestState.`cancelling with another master costs points`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 70)
            val vannaka = checkNotNull(engine.tables.master("vannaka"))
            random.next = 20
            engine.assign(player, vannaka, vannaka.tasks.first())
            player.withProtectedAccess { vars[SlayerVarBits.points] = 100 }

            val npc = master(SlayerNpcs.vannaka)
            player.opNpc3(npc)
            advance(3)
            npcLine() // The current task.
            pick(1) // Cancel.
            playerLine()

            assertNull(engine.current(player))
            assertEquals(70, engine.points(player))
        }

    @Test
    fun GameTestState.`cancelling without the points keeps the task`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 70)
            val vannaka = checkNotNull(engine.tables.master("vannaka"))
            random.next = 20
            engine.assign(player, vannaka, vannaka.tasks.first())

            val npc = master(SlayerNpcs.vannaka)
            player.opNpc3(npc)
            advance(3)
            npcLine()
            pick(1)
            playerLine()

            assertNotNull(engine.current(player))
            assertEquals(0, engine.points(player))
        }

    @Test
    fun GameTestState.`the enchanted gem says what is left`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class, HeldOpScript::class) {
            prepare(combat = 100, slayer = 70)
            val vannaka = checkNotNull(engine.tables.master("vannaka"))
            val spectres = vannaka.tasks.first { it.task.id == 41 }
            random.next = 77
            engine.assign(player, vannaka, spectres)
            player.invAdd(player.inv, SlayerObjs.gem, 1)

            val slot = player.inv.indexOfFirst { it?.id == objTypes[SlayerObjs.gem].id }
            check(slot >= 0) { "The gem is not in the inventory: ${player.inv.filterNotNull()}" }
            // Activate is the first option, so Check is the third button option.
            eventBus.publish(
                IfOverlayButton(
                    player,
                    components.inv_items,
                    slot,
                    objTypes[SlayerObjs.gem],
                    IfButtonOp.Op3,
                )
            )
            // The client message capture is per tick, so look right after the click.
            assertMessageSent("Your current assignment is aberrant spectres; only 77 more to go.")
        }

    @Test
    fun GameTestState.`trading opens the shared equipment shop`() =
        runGameTest(SlayerMastersHolder::class, SlayerMasters::class) {
            prepare(combat = 100, slayer = 70)
            val npc = master(SlayerNpcs.duradel)
            player.opNpc4(npc)
            advance(3)
            val shop = checkNotNull(player.openedShop) { "No shop opened" }
            val have = (0 until shop.inv.size).mapNotNull { shop.inv[it]?.id }.toSet()
            for (item in SlayerShop.stock) {
                assertTrue(objTypes[item.obj].id in have, "Missing ${objTypes[item.obj].name}")
            }
        }
}
