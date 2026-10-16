package org.rsmod.content.skills.slayer.configs

import org.rsmod.api.config.refs.params
import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.game.type.varbit.VarBitType

/**
 * The cache's own Slayer state. The client's Slayer interfaces (the rewards screen, the enchanted
 * gem and the task list) read these, so using them keeps those screens working.
 */
object SlayerVarps : VarpReferences() {
    /** Kills left on the current task. */
    val count = find("slayer_count")

    /** The current task's id (`slayer_task:id` in the cache; 0 with no task). */
    val target = find("slayer_target")

    /** How many kills the current task was assigned with. */
    val count_original = find("slayer_count_original")

    /** Bit set of bought unlocks, bits 0 to 31 (see [SlayerUnlocks]). */
    val unlocks = find("slayer_rewards_unlocks")

    /** Bit set of bought unlocks, bits 32 and up. */
    val unlocks_high = find("slayer_rewards_unlocks1")
}

/**
 * Generic interface varps. The server fills them before it opens an interface so the interface's
 * clientscripts can read them; the rewards screen reads its current and stored task from them.
 */
object SlayerScreenVarps : VarpReferences() {
    /** Kills left on the current task, or `-1` for a task with no count. */
    val amount = find("if1")

    /** The current task's id (`98` is a boss task). */
    val task = find("if2")

    /** The current boss task's id in the boss list (only with task `98`). */
    val boss = find("if3")

    /** Task storage, which the isle does not sell: the stored task's kills left, id and boss. */
    val stored_amount = find("if4")
    val stored_task = find("if5")
    val stored_boss = find("if6")
}

object SlayerVarBits : VarBitReferences() {
    /** The master who gave the current task (the cache's master id). */
    val master = find("slayer_master")

    /** Slayer reward points. */
    val points = find("slayer_points")

    /** Tasks completed in a row. */
    val streak = find("slayer_tasks_completed")

    /** The master whose block list the rewards screen shows (the cache's master id). */
    val master_in_focus = find("slayer_master_in_focus")

    /** The perks that can be switched off and on again without buying them again. */
    val toggle_off_superiors = find("slayer_toggleoff_superiormobs")
    val toggle_off_fossil_wyvern_block = find("slayer_toggleoff_fossilwyvernblock")
    val toggle_off_longer_revenants = find("slayer_toggleoff_longerrevenants")
}

/**
 * The blocked tasks. Each master has six slots of its own, each holding the id of one blocked task
 * or `0`; the rewards screen shows the list of the master the player is talking to.
 */
object SlayerBlocked : VarBitReferences() {
    private val turael = List(SLOTS) { find("slayer_blocked_turael_${it + 1}") }
    private val vannaka = List(SLOTS) { find("slayer_blocked_vannaka_${it + 1}") }
    private val duradel = List(SLOTS) { find("slayer_blocked_duradel_${it + 1}") }

    fun slots(master: SlayerMaster): List<VarBitType> =
        when (master.key) {
            "turael" -> turael
            "vannaka" -> vannaka
            "duradel" -> duradel
            else -> error("No block list for ${master.key}.")
        }

    const val SLOTS = 6
}

object SlayerInterfaces : InterfaceReferences() {
    /** The rewards screen: every tab is drawn by the client from cache tables. */
    val rewards = find("slayer_rewards")
}

object SlayerComponents : ComponentReferences() {
    /**
     * Where every "Confirm" of the Unlock, Extend and Tasks tabs lives. The client draws one button
     * into it for the thing being confirmed and its child index says which: an unlock's bit,
     * [CANCEL_CHILD], [BLOCK_CHILD], or an [UNBLOCK_FIRST_CHILD] plus the block slot.
     */
    val confirm_button = find("slayer_rewards:confirm_button")

    /** The Buy tab's items. Child `n` is the store's `n`th item; ops 2 to 5 buy 1, 5, 10, 50. */
    val buy_items = find("slayer_rewards:buy_items")

    /**
     * Where the Unlock and Extend tabs draw their rows. The "Unlock" or "Disable" of a row is an op
     * on a child, which is how the server learns that a confirmation belongs to an unlock.
     */
    val unlock_contents = find("slayer_rewards:unlock_contents")
    val extend_contents = find("slayer_rewards:extend_contents")

    /** The Tasks tab's six block slots, each with an "Unblock" op on a child. */
    val task_slots = List(6) { find("slayer_rewards:tasks_slot_${it + 1}") }

    const val CANCEL_CHILD = 58
    const val BLOCK_CHILD = 59

    /** The block slots (1 to 6) are children 3 to 8. */
    const val UNBLOCK_FIRST_CHILD = 3
    const val EXTEND_ALL_CHILD = 66
}

/** The Slayer task an npc counts for (npc param 50 in the cache, named in the shared config). */
object SlayerParams {
    val task_id = params.slayer_task_id
}
