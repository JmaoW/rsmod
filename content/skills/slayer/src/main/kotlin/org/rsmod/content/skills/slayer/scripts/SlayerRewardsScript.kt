package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import java.util.Collections
import java.util.WeakHashMap
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOpen
import org.rsmod.content.skills.slayer.configs.SlayerBlocked
import org.rsmod.content.skills.slayer.configs.SlayerComponents
import org.rsmod.content.skills.slayer.configs.SlayerInterfaces
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Answers the clicks of the Slayer rewards screen (see [SlayerRewards]). The client draws the whole
 * screen and does the browsing itself; it only talks to the server when something is confirmed:
 * - "Confirm" on any tab draws one button into [SlayerComponents.confirm_button], whose child index
 *   says what is being confirmed: an unlock's bit, cancel, block, extend everything, or a block
 *   slot to unblock;
 * - the Buy tab's "Buy 1/5/10/50" are ops 2 to 5 of an item in [SlayerComponents.buy_items].
 *
 * The one thing the confirm button cannot tell apart is unlocks 3 to 8 from block slots 1 to 6,
 * which share child indices. The "Unblock" of a slot and the "Unlock" of a row come first, so those
 * are watched to know which kind of confirmation is coming.
 */
class SlayerRewardsScript
@Inject
constructor(private val rewards: SlayerRewards, private val engine: SlayerEngine) : PluginScript() {
    /** Players whose last click, before a confirmation, was "Unblock" on a block slot. */
    private val unblocking: MutableSet<Player> =
        Collections.newSetFromMap(WeakHashMap<Player, Boolean>())

    override fun ScriptContext.startup() {
        onIfOpen(SlayerInterfaces.rewards) { enableEvents(player) }

        onIfModalButton(SlayerComponents.unlock_contents) { unblocking -= player }
        onIfModalButton(SlayerComponents.extend_contents) { unblocking -= player }
        for (slot in SlayerComponents.task_slots) {
            onIfModalButton(slot) { unblocking += player }
        }

        onIfModalButton(SlayerComponents.confirm_button) { confirm(it.comsub) }
        onIfModalButton(SlayerComponents.buy_items) { buy(it.comsub, it.op) }
    }

    /**
     * The client only reports clicks on children the server asked for. Child indices are generous:
     * the lists are drawn one child after another, a handful per row.
     */
    private fun enableEvents(player: Player) {
        player.ifSetEvents(SlayerComponents.confirm_button, 0..CONFIRM_CHILDREN, IfEvent.Op1)
        player.ifSetEvents(
            SlayerComponents.buy_items,
            0..SHOP_CHILDREN,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
        )
        player.ifSetEvents(SlayerComponents.unlock_contents, 0..ROW_CHILDREN, IfEvent.Op1)
        player.ifSetEvents(SlayerComponents.extend_contents, 0..ROW_CHILDREN, IfEvent.Op1)
        for (slot in SlayerComponents.task_slots) {
            player.ifSetEvents(slot, 0..SLOT_CHILDREN, IfEvent.Op1)
        }
    }

    private fun ProtectedAccess.confirm(child: Int) {
        val master = rewards.focus(player) ?: return
        val wasUnblocking = unblocking.remove(player)
        val slot = child - SlayerComponents.UNBLOCK_FIRST_CHILD
        when {
            child == SlayerComponents.CANCEL_CHILD ->
                mes(rewards.message(rewards.cancelTask(player), "Your Slayer task is cancelled."))
            child == SlayerComponents.BLOCK_CHILD ->
                mes(
                    rewards.message(
                        rewards.blockTask(player, master),
                        "Your Slayer task is blocked.",
                    )
                )
            child == SlayerComponents.EXTEND_ALL_CHILD ->
                mes(rewards.message(rewards.extendAll(player), "You have extended every task."))
            wasUnblocking && slot in 0 until SlayerBlocked.SLOTS -> {
                val taskId = player.vars[SlayerBlocked.slots(master)[slot]]
                val name = engine.tables.tasks[taskId]?.name ?: "That task"
                mes(rewards.message(rewards.unblock(player, master, slot), "$name is unblocked."))
            }
            else -> unlock(child)
        }
        rewards.refresh(player)
    }

    private fun ProtectedAccess.unlock(bit: Int) {
        val name = engine.tables.unlock(bit)?.name ?: "That"
        val result = rewards.confirmUnlock(player, bit)
        val success =
            when (result) {
                RewardResult.Disabled -> "$name is switched off."
                RewardResult.Toggled ->
                    "$name is switched ${if (rewards.isSwitchedOff(player, bit)) "off" else "on"}."
                else -> "You have unlocked $name."
            }
        mes(rewards.message(result, success))
    }

    private fun ProtectedAccess.buy(index: Int, op: IfButtonOp) {
        val item = rewards.item(index) ?: return
        val sets =
            when (op) {
                IfButtonOp.Op2 -> 1
                IfButtonOp.Op3 -> 5
                IfButtonOp.Op4 -> 10
                IfButtonOp.Op5 -> 50
                else -> return
            }
        // The pouch, sack and bag are only sold one at a time.
        if (sets != 1 && item.obj in SlayerRewards.FREE_ITEMS) {
            return
        }
        val result = rewards.buyItem(player, item, sets)
        mes(rewards.message(result, "You buy ${rewards.describe(item, sets)}."))
        rewards.refresh(player)
    }

    private companion object {
        /** Unlock bits go up to 66 ("Extend everything"), and the block slots are low. */
        const val CONFIRM_CHILDREN = 127

        /** The store sells six things; room for more. */
        const val SHOP_CHILDREN = 31

        /** A row of the Unlock and Extend tabs is about eight children; 60 rows is plenty. */
        const val ROW_CHILDREN = 511

        const val SLOT_CHILDREN = 15
    }
}
