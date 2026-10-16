package org.rsmod.content.skills.slayer

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerBlocked
import org.rsmod.content.skills.slayer.configs.SlayerComponents
import org.rsmod.content.skills.slayer.configs.SlayerInterfaces
import org.rsmod.content.skills.slayer.configs.SlayerNpcs
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerScreenVarps
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.scripts.RewardResult
import org.rsmod.content.skills.slayer.scripts.SlayerEngine
import org.rsmod.content.skills.slayer.scripts.SlayerMasters
import org.rsmod.content.skills.slayer.scripts.SlayerRewards
import org.rsmod.content.skills.slayer.scripts.SlayerRewardsScript
import org.rsmod.content.skills.slayer.scripts.SlayerScript
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class SlayerRewardsHolder @Inject constructor(rewards: SlayerRewards, engine: SlayerEngine) :
    PluginScript() {
    init {
        heldRewards = rewards
        heldEngine = engine
    }

    override fun ScriptContext.startup() {}

    companion object {
        lateinit var heldRewards: SlayerRewards
        lateinit var heldEngine: SlayerEngine
    }
}

// The holders above are static, so the tests in a class must not run side by side.
@Execution(ExecutionMode.SAME_THREAD)
class SlayerRewardsTest {
    private val rewards
        get() = SlayerRewardsHolder.heldRewards

    private val engine
        get() = SlayerRewardsHolder.heldEngine

    private val vannaka
        get() = checkNotNull(engine.tables.master("vannaka"))

    private val turael
        get() = checkNotNull(engine.tables.master("turael"))

    private fun GameTestScope.prepare(points: Int) {
        player.stats[stats.slayer] = 99
        player.appearance.combatLevel = 126
        engine.cancel(player)
        player.clearInv()
        player.withProtectedAccess {
            vars[SlayerVarBits.points] = points
            vars[SlayerVarBits.streak] = 0
            vars[SlayerVarBits.toggle_off_superiors] = 0
            vars[SlayerVarps.unlocks] = 0
            vars[SlayerVarps.unlocks_high] = 0
            for (master in engine.tables.masters.values) {
                for (varbit in SlayerBlocked.slots(master)) {
                    vars[varbit] = 0
                }
            }
        }
        player.teleport(CoordGrid(0, 50, 50, 34, 31))
        advance(12)
    }

    private fun GameTestScope.assignVannaka(taskId: Int = 41) {
        random.next = 30
        engine.assign(player, vannaka, vannaka.tasks.first { it.task.id == taskId })
    }

    /** Opens the screen as [master] and lets the open event enable the screen's clicks. */
    private fun GameTestScope.openScreen(
        master: org.rsmod.content.skills.slayer.configs.SlayerMaster
    ) {
        player.withProtectedAccess { rewards.open(this, master) }
        advance()
    }

    private fun GameTestScope.confirm(child: Int) {
        player.ifButton(SlayerComponents.confirm_button, child)
        advance()
    }

    // --- The actions ---------------------------------------------------------------------------

    @Test
    fun GameTestState.`an unlock costs its points and is then owned`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 200)
            val smasher = checkNotNull(engine.tables.unlock(0))
            assertEquals(120, smasher.cost)
            assertEquals(RewardResult.Done, rewards.buyUnlock(player, smasher))
            assertEquals(80, engine.points(player))
            assertTrue(engine.isUnlocked(player, 0))
            assertEquals(RewardResult.AlreadyOwned, rewards.buyUnlock(player, smasher))
            assertEquals(80, engine.points(player), "Nothing is charged twice")
        }

    @Test
    fun GameTestState.`an unlock needs the points`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 50)
            val smasher = checkNotNull(engine.tables.unlock(0))
            assertEquals(RewardResult.NotEnoughPoints, rewards.buyUnlock(player, smasher))
            assertFalse(engine.isUnlocked(player, 0))
            assertEquals(50, engine.points(player))
        }

    @Test
    fun GameTestState.`cancelling a task costs 30 points and keeps the streak`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 100)
            assertEquals(RewardResult.NoTask, rewards.cancelTask(player))
            assignVannaka()
            player.withProtectedAccess { vars[SlayerVarBits.streak] = 5 }
            assertEquals(RewardResult.Done, rewards.cancelTask(player))
            assertEquals(70, engine.points(player))
            assertEquals(5, engine.streak(player), "The cache says neither option resets it")
            assertTrue(engine.current(player) == null)
        }

    @Test
    fun GameTestState.`blocking costs the master's price and keeps the task away`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 100)
            assignVannaka(41)
            assertEquals(RewardResult.Done, rewards.blockTask(player, vannaka))
            assertEquals(40, engine.points(player), "Vannaka asks 60")
            assertEquals(listOf(41), engine.blocked(player, vannaka))
            assertTrue(engine.blocked(player, turael).isEmpty(), "Each master has its own list")
            assertFalse(
                vannaka.tasks.first { it.task.id == 41 } in engine.eligible(player, vannaka)
            )

            assertEquals(RewardResult.Done, rewards.unblockTask(player, vannaka, 41))
            assertTrue(engine.blocked(player, vannaka).isEmpty())
            assertEquals(RewardResult.NotBlocked, rewards.unblockTask(player, vannaka, 41))
        }

    @Test
    fun GameTestState.`the block list holds six tasks`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 1000)
            for ((slot, varbit) in SlayerBlocked.slots(vannaka).withIndex()) {
                player.withProtectedAccess { vars[varbit] = 100 + slot }
            }
            assignVannaka(41)
            assertEquals(RewardResult.BlockListFull, rewards.blockTask(player, vannaka))
            assertEquals(1000, engine.points(player))
        }

    @Test
    fun GameTestState.`the store sells its items for points`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 100)
            val ring = engine.tables.rewardItems.first { it.obj == "slayer_ring_8" }
            assertEquals(75, ring.cost)
            assertEquals(RewardResult.Done, rewards.buyItem(player, ring))
            assertTrue(SlayerObjs.ring in player.inv)
            assertEquals(25, engine.points(player))
            assertEquals(RewardResult.NotEnoughPoints, rewards.buyItem(player, ring))
        }

    @Test
    fun GameTestState
        .`a hook that makes the store free charges nothing for what the relic frees`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 0)
            engine.hooks.free += { true }
            assignVannaka()
            assertEquals(RewardResult.Done, rewards.cancelTask(player))
            val smasher = checkNotNull(engine.tables.unlock(0))
            assertEquals(RewardResult.Done, rewards.buyUnlock(player, smasher))
            val items = engine.tables.rewardItems
            assertEquals(
                RewardResult.Done,
                rewards.buyItem(player, items.first { it.obj == "looting_bag" }),
            )
            assertEquals(
                RewardResult.NotEnoughPoints,
                rewards.buyItem(player, items.first { it.obj == "slayer_ring_8" }),
                "Only pouches, sacks and bags are free",
            )
            assertEquals(0, engine.points(player))
        }

    @Test
    fun GameTestState.`a hook can grant unlocks and multiply points`() =
        runGameTest(SlayerRewardsHolder::class, SlayerScript::class) {
            prepare(points = 0)
            assertFalse(engine.isUnlocked(player, 35))
            engine.hooks.unlocked += { _, bit -> bit == 35 }
            assertTrue(engine.isUnlocked(player, 35))
            assertFalse(engine.hasBit(player, 35), "The unlock was never bought")

            engine.hooks.pointMultipliers += { 5 }
            random.next = 1
            engine.assign(player, vannaka, vannaka.tasks.first { it.task.id == 41 })
            val npc = spectre()
            eventBus.publish(org.rsmod.api.death.NpcDeathEvents.Killed(npc, player))
            assertEquals(8 * 5, engine.points(player), "Vannaka's 8 points, five times over")
        }

    @Test
    fun GameTestState.`extend everything costs 95 percent of what is left`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 5000)
            val left = rewards.extensionsLeft(player)
            assertTrue(left.size > 10, "Most extensions are left: ${left.map { it.name }}")
            assertTrue(left.none { it.bit in SlayerRewards.EXTEND_ALL_EXCLUDED })
            val sum = left.sumOf { it.cost }
            assertEquals(sum * 95 / 100, rewards.extendAllCost(player))

            assertEquals(RewardResult.Done, rewards.extendAll(player))
            assertEquals(5000 - sum * 95 / 100, engine.points(player))
            assertTrue(left.all { engine.hasBit(player, it.bit) })
            assertTrue(rewards.extensionsLeft(player).isEmpty())
            assertEquals(RewardResult.AlreadyOwned, rewards.extendAll(player))
        }

    @Test
    fun GameTestState.`one extension left costs its own price`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 5000)
            val left = rewards.extensionsLeft(player)
            for (unlock in left.drop(1)) {
                player.withProtectedAccess { engine.setUnlocked(player, unlock.bit, true) }
            }
            assertEquals(left.first().cost, rewards.extendAllCost(player))
        }

    @Test
    fun GameTestState.`a perk can be switched off, a toggle off and on, without a refund`() =
        runGameTest(SlayerRewardsHolder::class) {
            prepare(points = 1000)
            // Bigger and Badder (35) is a toggle; Hot Stuff (18) is refundable; Smasher (0) is not.
            assertEquals(RewardResult.Done, rewards.confirmUnlock(player, 35))
            assertEquals(950, engine.points(player))
            assertEquals(RewardResult.Toggled, rewards.confirmUnlock(player, 35))
            assertTrue(engine.hasBit(player, 35), "Switching it off keeps it bought")
            assertTrue(rewards.isSwitchedOff(player, 35))
            assertEquals(RewardResult.Toggled, rewards.confirmUnlock(player, 35))
            assertFalse(rewards.isSwitchedOff(player, 35))

            assertEquals(RewardResult.Done, rewards.confirmUnlock(player, 18))
            assertEquals(RewardResult.Disabled, rewards.confirmUnlock(player, 18))
            assertFalse(engine.hasBit(player, 18))
            assertEquals(850, engine.points(player), "Nothing is refunded")

            assertEquals(RewardResult.Done, rewards.confirmUnlock(player, 0))
            assertEquals(RewardResult.AlreadyOwned, rewards.confirmUnlock(player, 0))
        }

    // --- The screen ----------------------------------------------------------------------------

    @Test
    fun GameTestState.`opening the screen shows the task and the master's lists`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 100)
            assignVannaka(41)
            openScreen(vannaka)

            assertTrue(player.ui.containsModal(SlayerInterfaces.rewards))
            assertEquals(vannaka.masterId, player.vars[SlayerVarBits.master_in_focus])
            assertEquals(player.vars[SlayerVarps.count], player.vars[SlayerScreenVarps.amount])
            assertEquals(41, player.vars[SlayerScreenVarps.task])
            assertEquals(0, player.vars[SlayerScreenVarps.stored_task])
        }

    @Test
    fun GameTestState.`a hook that grants an unlock shows it as bought when the screen opens`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 0)
            engine.hooks.unlocked += { _, bit -> bit == 35 }
            assertFalse(engine.hasBit(player, 35))
            openScreen(vannaka)
            assertTrue(engine.hasBit(player, 35))
        }

    @Test
    fun GameTestState.`confirming an unlock on the screen buys it`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 200)
            openScreen(vannaka)
            confirm(0)
            assertTrue(engine.hasBit(player, 0))
            assertEquals(80, engine.points(player))
            assertMessagesSent("You have unlocked Gargoyle Smasher.")
        }

    @Test
    fun GameTestState.`confirming without the points says so and charges nothing`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 10)
            openScreen(vannaka)
            confirm(0)
            assertFalse(engine.hasBit(player, 0))
            assertEquals(10, engine.points(player))
            assertMessagesSent("You do not have enough Slayer reward points.")
        }

    @Test
    fun GameTestState.`cancel and block are the confirm button's children 58 and 59`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 200)
            assignVannaka(41)
            openScreen(vannaka)
            confirm(SlayerComponents.CANCEL_CHILD)
            assertTrue(engine.current(player) == null)
            assertEquals(170, engine.points(player))
            assertEquals(0, player.vars[SlayerScreenVarps.task], "The Tasks tab is redrawn")

            assignVannaka(41)
            confirm(SlayerComponents.BLOCK_CHILD)
            assertEquals(110, engine.points(player))
            assertEquals(listOf(41), engine.blocked(player, vannaka))
        }

    @Test
    fun GameTestState.`unblocking a slot only unblocks after its Unblock op was clicked`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 1000)
            player.withProtectedAccess { vars[SlayerBlocked.slots(vannaka)[0]] = 41 }
            openScreen(vannaka)

            // Child 3 is both block slot 1 and unlock bit 3. With no Unblock click first, it is
            // the unlock.
            confirm(3)
            assertEquals(listOf(41), engine.blocked(player, vannaka))
            assertTrue(engine.hasBit(player, 3))

            player.ifButton(SlayerComponents.task_slots[0], 0)
            advance()
            confirm(3)
            assertTrue(engine.blocked(player, vannaka).isEmpty())
        }

    @Test
    fun GameTestState.`extend everything is child 66`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 5000)
            openScreen(vannaka)
            confirm(SlayerComponents.EXTEND_ALL_CHILD)
            assertTrue(rewards.extensionsLeft(player).isEmpty())
        }

    @Test
    fun GameTestState.`the buy tab buys sets of the store's items`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 400)
            openScreen(vannaka)
            // Item 0 is the Slayer ring (75), item 1 250 broad bolts (35).
            player.ifButton(SlayerComponents.buy_items, 0, IfButtonOp.Op2)
            advance()
            assertTrue(SlayerObjs.ring in player.inv)
            assertEquals(325, engine.points(player))

            player.ifButton(SlayerComponents.buy_items, 1, IfButtonOp.Op3) // Five sets.
            advance()
            assertEquals(325 - 5 * 35, engine.points(player))
            assertEquals(1250, player.inv.count(objTypes[SlayerObjs.broad_bolts]))

            player.ifButton(SlayerComponents.buy_items, 1, IfButtonOp.Op5) // Fifty: too dear.
            advance()
            assertEquals(150, engine.points(player))
        }

    @Test
    fun GameTestState.`the Rewards option opens the screen`() =
        runGameTest(SlayerRewardsHolder::class, SlayerMasters::class, SlayerRewardsScript::class) {
            prepare(points = 100)
            val master = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcTypes[SlayerNpcs.vannaka])
            player.opNpc5(master)
            advance(3)
            assertTrue(player.ui.containsModal(SlayerInterfaces.rewards))
            assertEquals(vannaka.masterId, player.vars[SlayerVarBits.master_in_focus])
        }

    @Test
    fun GameTestState.`asking Vannaka about reward points opens the screen`() =
        runGameTest(SlayerRewardsHolder::class, SlayerMasters::class, SlayerRewardsScript::class) {
            prepare(points = 100)
            val master = spawnNpc(CoordGrid(0, 50, 50, 36, 31), npcTypes[SlayerNpcs.vannaka])
            player.opNpc1(master)
            advance(3)
            player.resumePauseButton(components.chat_left_pbutton) // The greeting.
            advance(2)
            player.resumePauseButton(components.chatmenu_pbutton, 4) // Spend my reward points.
            advance(2)
            player.resumePauseButton(components.chat_right_pbutton)
            advance(2)
            assertTrue(player.ui.containsModal(SlayerInterfaces.rewards))
        }

    @Test
    fun GameTestState.`task storage cannot be bought and a relic does not grant it`() =
        runGameTest(SlayerRewardsHolder::class, SlayerRewardsScript::class) {
            prepare(points = 1000)
            assertEquals(RewardResult.NotForSale, rewards.confirmUnlock(player, 51))
            assertEquals(1000, engine.points(player))

            engine.hooks.unlocked += { _, _ -> true }
            openScreen(vannaka)
            assertFalse(engine.hasBit(player, 51), "Its buttons would do nothing")
            assertTrue(engine.hasBit(player, 0), "Everything else is shown as bought")
        }

    private fun GameTestScope.spectre(): Npc {
        val type =
            npcTypes.values.first {
                it.param(org.rsmod.content.skills.slayer.configs.SlayerParams.task_id) == 41 &&
                    it.vislevel > 0
            }
        return spawnNpc(CoordGrid(0, 50, 50, 34, 33), type)
    }
}
