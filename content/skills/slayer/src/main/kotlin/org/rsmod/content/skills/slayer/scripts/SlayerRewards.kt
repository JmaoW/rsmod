package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.slayer.configs.RewardItem
import org.rsmod.content.skills.slayer.configs.SlayerBlocked
import org.rsmod.content.skills.slayer.configs.SlayerInterfaces
import org.rsmod.content.skills.slayer.configs.SlayerMaster
import org.rsmod.content.skills.slayer.configs.SlayerScreenVarps
import org.rsmod.content.skills.slayer.configs.SlayerUnlock
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

/** How a purchase or an action at the rewards screen went. */
enum class RewardResult {
    Done,
    AlreadyOwned,
    NotEnoughPoints,
    NoTask,
    BlockListFull,
    NotBlocked,
    NoSpace,
    NotForSale,

    /** A perk that stays bought was switched off ([Disabled]) or off and on again ([Toggled]). */
    Disabled,
    Toggled,
}

/**
 * The Slayer rewards store. The screen itself is the cache's `slayer_rewards` interface: its four
 * tabs (Unlock, Extend, Buy and Tasks) are drawn by the client from cache tables and read their
 * state from the player's varps, so this class only has to
 * - put that state in place before the screen opens ([open], [refresh]), and
 * - answer the screen's confirmations ([confirmUnlock], [extendAll], [buyItem], [cancelTask],
 *   [blockTask] and [unblock]); [SlayerRewardsScript] routes the clicks here.
 *
 * Costs, names and descriptions are the cache's, shown by the client. Things that
 * [SlayerEngine.isFree] says cost nothing (the Slayer Master relic) are free here; the client shows
 * the same by itself since it reads the relic selection.
 */
class SlayerRewards
@Inject
constructor(private val engine: SlayerEngine, private val objTypes: ObjTypeList) {
    // --- Opening -------------------------------------------------------------------------------

    /** Opens the rewards screen for [master], whose block list and prices it shows. */
    fun open(access: ProtectedAccess, master: SlayerMaster) {
        grantFromHooks(access.player)
        engine.set(access.player, SlayerVarBits.master_in_focus, master.masterId)
        refresh(access.player)
        access.ifOpenMainModal(SlayerInterfaces.rewards)
    }

    /** The master whose list the screen is showing. */
    fun focus(player: Player): SlayerMaster? =
        engine.tables.masterById(player.vars[SlayerVarBits.master_in_focus])

    /**
     * Writes the task into the generic interface varps the screen's Tasks tab reads. The client
     * redraws the tab whenever they change, so this is also what keeps it current after a cancel.
     */
    fun refresh(player: Player) {
        val current = engine.current(player)
        engine.set(player, SlayerScreenVarps.amount, current?.remaining ?: 0)
        engine.set(player, SlayerScreenVarps.task, current?.task?.id ?: 0)
        engine.set(player, SlayerScreenVarps.boss, 0)
        engine.set(player, SlayerScreenVarps.stored_amount, 0)
        engine.set(player, SlayerScreenVarps.stored_task, 0)
        engine.set(player, SlayerScreenVarps.stored_boss, 0)
    }

    /**
     * The screen shows what is in the unlock varps, so anything a hook grants (the relics) is
     * written there too; otherwise it would show as locked while working.
     */
    private fun grantFromHooks(player: Player) {
        for (unlock in engine.tables.unlocks) {
            if (unlock.bit in UNAVAILABLE) {
                continue
            }
            if (!engine.hasBit(player, unlock.bit) && engine.isUnlocked(player, unlock.bit)) {
                engine.setUnlocked(player, unlock.bit, true)
            }
        }
    }

    // --- Tasks ---------------------------------------------------------------------------------

    /** Cancels the current task for [CANCEL_COST] points. The streak is kept. */
    fun cancelTask(player: Player): RewardResult {
        if (engine.current(player) == null) {
            return RewardResult.NoTask
        }
        if (!pay(player, CANCEL_COST)) {
            return RewardResult.NotEnoughPoints
        }
        engine.drop(player)
        refresh(player)
        return RewardResult.Done
    }

    /**
     * Blocks the current task for the price [master] asks, so [master] does not give it again until
     * it is unblocked. The task is dropped; the streak is kept.
     */
    fun blockTask(player: Player, master: SlayerMaster): RewardResult {
        val current = engine.current(player) ?: return RewardResult.NoTask
        val slot = SlayerBlocked.slots(master).indexOfFirst { player.vars[it] == 0 }
        if (slot < 0) {
            return RewardResult.BlockListFull
        }
        if (!pay(player, master.blockCost)) {
            return RewardResult.NotEnoughPoints
        }
        engine.setBlocked(player, master, slot, current.task.id)
        engine.drop(player)
        refresh(player)
        return RewardResult.Done
    }

    /** Unblocks whatever is in [master]'s block slot [slot] (0 to 5). Free. */
    fun unblock(player: Player, master: SlayerMaster, slot: Int): RewardResult {
        val slots = SlayerBlocked.slots(master)
        if (slot !in slots.indices || player.vars[slots[slot]] == 0) {
            return RewardResult.NotBlocked
        }
        engine.setBlocked(player, master, slot, 0)
        return RewardResult.Done
    }

    /** Unblocks the task with id [taskId] from [master]'s list. */
    fun unblockTask(player: Player, master: SlayerMaster, taskId: Int): RewardResult {
        val slot = SlayerBlocked.slots(master).indexOfFirst { player.vars[it] == taskId }
        return if (slot < 0) RewardResult.NotBlocked else unblock(player, master, slot)
    }

    // --- Unlocks -------------------------------------------------------------------------------

    /**
     * What a click on "Confirm" for the unlock with [bit] means. A perk the player has is switched
     * off if it can be (without a refund), or off and on if it is one of the toggles; one they do
     * not have is bought.
     */
    fun confirmUnlock(player: Player, bit: Int): RewardResult {
        val unlock = engine.tables.unlock(bit) ?: return RewardResult.NotForSale
        if (bit in UNAVAILABLE) {
            return RewardResult.NotForSale
        }
        if (!engine.hasBit(player, bit)) {
            return buyUnlock(player, unlock)
        }
        val toggle = TOGGLES[bit]
        return when {
            toggle != null -> {
                engine.set(player, toggle, if (player.vars[toggle] == 0) 1 else 0)
                RewardResult.Toggled
            }
            unlock.refundable -> {
                engine.setUnlocked(player, bit, false)
                RewardResult.Disabled
            }
            else -> RewardResult.AlreadyOwned
        }
    }

    fun buyUnlock(player: Player, unlock: SlayerUnlock): RewardResult {
        if (engine.hasBit(player, unlock.bit)) {
            return RewardResult.AlreadyOwned
        }
        if (!pay(player, unlock.cost)) {
            return RewardResult.NotEnoughPoints
        }
        engine.setUnlocked(player, unlock.bit, true)
        return RewardResult.Done
    }

    /** The task extensions that "Extend everything" would buy now. */
    fun extensionsLeft(player: Player): List<SlayerUnlock> =
        engine.tables.unlocks.filter {
            it.tab == EXTEND_TAB &&
                it.position >= 0 &&
                it.bit !in EXTEND_ALL_EXCLUDED &&
                !engine.hasBit(player, it.bit)
        }

    /** What "Extend everything" costs: one extension at its price, several at 95% of the sum. */
    fun extendAllCost(player: Player): Int {
        val left = extensionsLeft(player)
        val sum = left.sumOf { it.cost }
        return when {
            engine.isFree(player) -> 0
            left.size > 1 -> maxOf(sum * EXTEND_ALL_PERCENT / 100, 2)
            else -> sum
        }
    }

    /** Buys every extension the player does not have yet, in one go. */
    fun extendAll(player: Player): RewardResult {
        val left = extensionsLeft(player)
        if (left.isEmpty()) {
            return RewardResult.AlreadyOwned
        }
        val cost = extendAllCost(player)
        if (engine.points(player) < cost) {
            return RewardResult.NotEnoughPoints
        }
        engine.spendPoints(player, cost)
        for (unlock in left) {
            engine.setUnlocked(player, unlock.bit, true)
        }
        return RewardResult.Done
    }

    // --- Items ---------------------------------------------------------------------------------

    /** What [item] costs [player] per purchase: the relic makes pouches, sacks and bags free. */
    fun itemCost(player: Player, item: RewardItem): Int =
        if (engine.isFree(player) && item.obj in FREE_ITEMS) 0 else item.cost

    /**
     * Buys [sets] purchases of [item] ([RewardItem.quantity] each). It is all or nothing: it needs
     * the points and the room for all of it.
     */
    fun buyItem(player: Player, item: RewardItem, sets: Int = 1): RewardResult {
        val total = itemCost(player, item).toLong() * sets
        if (engine.points(player) < total) {
            return RewardResult.NotEnoughPoints
        }
        val type = objTypes.values.first { it.internalName == item.obj }
        val added = player.invAdd(player.inv, type, item.quantity * sets)
        if (!added.success) {
            return RewardResult.NoSpace
        }
        engine.spendPoints(player, total.toInt())
        return RewardResult.Done
    }

    /** Whether the toggleable perk with unlock [bit] is currently switched off. */
    fun isSwitchedOff(player: Player, bit: Int): Boolean =
        TOGGLES[bit]?.let { player.vars[it] == 1 } ?: false

    /** [item] bought [sets] times, as a phrase: "250 Broad bolts". */
    fun describe(item: RewardItem, sets: Int = 1): String {
        val type = objTypes.values.first { it.internalName == item.obj }
        val count = item.quantity * sets
        return if (count > 1) "$count ${type.name}" else type.name
    }

    /** The shop's [index]th item, as the cache's enum lists them. */
    fun item(index: Int): RewardItem? = engine.tables.rewardItems.getOrNull(index)

    fun message(result: RewardResult, success: String): String =
        when (result) {
            RewardResult.Done,
            RewardResult.Disabled,
            RewardResult.Toggled -> success
            RewardResult.AlreadyOwned -> "You already have that."
            RewardResult.NotEnoughPoints -> "You do not have enough Slayer reward points."
            RewardResult.NoTask -> "You do not have a Slayer task."
            RewardResult.BlockListFull -> "Your block list is full. Unblock a task first."
            RewardResult.NotBlocked -> "That task is not blocked."
            RewardResult.NoSpace -> "You do not have enough inventory space."
            RewardResult.NotForSale -> "That is not available on Isle of Souls."
        }

    private fun pay(player: Player, cost: Int): Boolean =
        engine.isFree(player) || engine.spendPoints(player, cost)

    companion object {
        /** Reward points to cancel a task. */
        const val CANCEL_COST = 30

        /** The Extend tab. */
        private const val EXTEND_TAB = 1

        /** "Extend everything" costs this percent of the extensions' prices added up. */
        private const val EXTEND_ALL_PERCENT = 95

        /**
         * The unlocks the cache's price for "Extend everything" leaves out in Leagues (they come
         * from an area instead), so the server leaves them out of what it buys as well.
         */
        val EXTEND_ALL_EXCLUDED = setOf(9, 16, 17, 18, 19, 21, 22, 30, 34, 40, 41, 43, 47, 50)

        /**
         * Unlocks that are listed on the screen but cannot be bought: Task Storage changes the
         * Tasks tab to show store buttons, which would do nothing here.
         */
        val UNAVAILABLE = setOf(51)

        /** The store items the Slayer Master relic gives away. */
        val FREE_ITEMS = setOf("slayer_herb_sack", "bh_rune_pouch", "looting_bag")

        /** The unlocks that can be switched off and on again without buying them again. */
        private val TOGGLES =
            mapOf(
                35 to SlayerVarBits.toggle_off_superiors,
                43 to SlayerVarBits.toggle_off_fossil_wyvern_block,
                53 to SlayerVarBits.toggle_off_longer_revenants,
            )
    }
}
