package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.api.script.onOpNpc5
import org.rsmod.api.shops.Shops
import org.rsmod.content.skills.slayer.configs.SlayerInvs
import org.rsmod.content.skills.slayer.configs.SlayerMaster
import org.rsmod.content.skills.slayer.configs.SlayerNpcs
import org.rsmod.content.skills.slayer.configs.SlayerObjs
import org.rsmod.content.skills.slayer.configs.SlayerShop
import org.rsmod.content.skills.slayer.configs.SlayerSpots
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The three Slayer masters: Turael, Vannaka and Duradel. Turael and Duradel are placed here;
 * Vannaka is the map's own spawn in the Varrock sewers. Each has the cache's four options:
 * "Talk-to", "Assignment", "Trade" (the shared Slayer Equipment shop) and "Rewards" (the rewards
 * screen, [SlayerRewards]). The enchanted gem checks the task.
 */
class SlayerMasters
@Inject
constructor(
    private val engine: SlayerEngine,
    private val shops: Shops,
    private val rewards: SlayerRewards,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    /** A master's key and npc type; [tile] is where [place] spawns him, if the map does not. */
    private class Post(val key: String, val npc: NpcType, val tile: CoordGrid? = null)

    // Vannaka has no tile: the map already spawns him in the Varrock sewers.
    private val posts =
        listOf(
            Post("turael", SlayerNpcs.turael, SlayerSpots.turael),
            Post("vannaka", SlayerNpcs.vannaka),
            Post("duradel", SlayerNpcs.duradel, SlayerSpots.duradel),
        )

    override fun ScriptContext.startup() {
        onGameStartup { place() }
        for (post in posts) {
            val master = { checkNotNull(engine.tables.master(post.key)) }
            onOpNpc1(post.npc) { talk(it.npc, master()) }
            onOpNpc3(post.npc) { startDialogue(it.npc) { assignment(master()) } }
            onOpNpc4(post.npc) { openShop(it.npc) }
            onOpNpc5(post.npc) { rewards.open(this, master()) }
        }
        onOpHeld1(SlayerObjs.gem) { check() }
        onOpHeld2(SlayerObjs.gem) { check() }
    }

    fun place() {
        for (post in posts) {
            val tile = post.tile ?: continue
            npcRepo.add(Npc(npcTypes[post.npc], tile), Int.MAX_VALUE)
        }
    }

    // --- Options -------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.talk(npc: Npc, master: SlayerMaster) {
        startDialogue(npc) {
            chatNpc(neutral, greeting(master))
            when (
                choice5(
                    "I need an assignment.",
                    1,
                    "How am I doing?",
                    2,
                    "Do you have anything for sale?",
                    3,
                    "I'd like to spend my reward points.",
                    5,
                    "Goodbye.",
                    4,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "I need an assignment.")
                    assignment(master)
                }
                2 -> {
                    chatPlayer(quiz, "How am I doing?")
                    progress()
                }
                3 -> {
                    chatPlayer(quiz, "Do you have anything for sale?")
                    chatNpc(neutral, "Slayer equipment and a few rewards. Take a look.")
                    access.openShop(npc)
                }
                5 -> {
                    chatPlayer(quiz, "I'd like to spend my reward points.")
                    rewards.open(access, master)
                }
                else -> chatPlayer(neutral, "Goodbye.")
            }
        }
    }

    private fun greeting(master: SlayerMaster): String =
        when (master.key) {
            "turael" ->
                "Hello there. I'm Turael. I give tasks to anyone, however green, though " +
                    "they're easy ones and earn no reward points."
            "vannaka" ->
                "I'm Vannaka. I give tasks to those who can fight (combat level " +
                    "${master.combat} or more), and I pay in reward points."
            else ->
                "I am Duradel. I only assign tasks to the hardened (combat level " +
                    "${master.combat}, Slayer ${master.slayer}), and I pay the best points."
        }

    /** "Assignment": a new task, or a word about the one the player already has. */
    private suspend fun Dialogue.assignment(master: SlayerMaster) {
        val current = engine.current(player)
        if (current != null) {
            hasTask(master)
            return
        }
        when (engine.canUse(player, master)) {
            SlayerRefusal.Combat ->
                chatNpc(
                    sad,
                    "You're not ready for my tasks. Come back with a combat level of " +
                        "${master.combat}.",
                )
            SlayerRefusal.SlayerLevel ->
                chatNpc(
                    sad,
                    "You need a Slayer level of ${master.slayer} for my tasks. Practise first.",
                )
            else -> giveTask(master)
        }
    }

    private suspend fun Dialogue.giveTask(master: SlayerMaster) {
        val (assignment, refusal) = engine.assign(player, master)
        if (assignment == null) {
            chatNpc(
                sad,
                "I have nothing suitable for you at the moment. ($refusal) Try another master.",
            )
            return
        }
        chatNpc(
            happy,
            "Your new task is to kill ${assignment.amount} ${assignment.task.lowerName}.",
        )
    }

    private suspend fun Dialogue.hasTask(master: SlayerMaster) {
        val current = checkNotNull(engine.current(player))
        chatNpc(
            neutral,
            "You're currently assigned to kill ${current.task.lowerName}; " +
                "only ${current.remaining} more to go.",
        )
        if (master.key == TURAEL) {
            when (choice2("Can you give me an easier task?", 1, "I'll get on with it.", 2)) {
                1 -> {
                    chatPlayer(quiz, "Can you give me an easier task?")
                    swapForEasier(master)
                }
                else -> chatPlayer(neutral, "I'll get on with it.")
            }
        } else {
            when (
                choice2(
                    "Cancel my task (${if (engine.isFree(player)) "free" else "$CANCEL_COST points"}).",
                    1,
                    "I'll get on with it.",
                    2,
                )
            ) {
                1 -> {
                    chatPlayer(quiz, "Cancel my task.")
                    cancel(master)
                }
                else -> chatPlayer(neutral, "I'll get on with it.")
            }
        }
    }

    /**
     * Turael changes a task he could not have given himself for one he can, which ends the streak.
     * One he could have given is refused.
     */
    private suspend fun Dialogue.swapForEasier(turael: SlayerMaster) {
        val current = checkNotNull(engine.current(player))
        if (turael.tasks.any { it.task.id == current.task.id }) {
            chatNpc(neutral, "That's one of mine already. You can manage it.")
            return
        }
        chatNpc(neutral, "Very well, but your streak of tasks ends.")
        engine.cancel(player)
        giveTask(turael)
    }

    private suspend fun Dialogue.cancel(master: SlayerMaster) {
        if (rewards.cancelTask(player) != RewardResult.Done) {
            chatNpc(
                sad,
                "You need $CANCEL_COST reward points to cancel a task. You have " +
                    "${engine.points(player)}.",
            )
            return
        }
        chatNpc(neutral, "Done. Ask again when you're ready.")
    }

    private suspend fun Dialogue.progress() {
        val current = engine.current(player)
        val task =
            if (current == null) "You have no task."
            else "You're assigned to kill ${current.task.lowerName}, ${current.remaining} to go."
        chatNpc(
            neutral,
            "$task You've completed ${engine.streak(player)} tasks in a row and have " +
                "${engine.points(player)} reward points.",
        )
    }

    private fun ProtectedAccess.openShop(npc: Npc) {
        shops.open(player, npc, SlayerShop.TITLE, SlayerInvs.shop)
    }

    /** The enchanted gem's "Activate" and "Check": what the task is and how much is left. */
    private fun ProtectedAccess.check() {
        val current = engine.current(player)
        mes(
            if (current == null) {
                "You do not have a Slayer task. Talk to a Slayer master."
            } else {
                engine.describe(current)
            }
        )
        engine.checked(player)
    }

    companion object {
        const val TURAEL = "turael"

        /** Reward points to cancel a task with a master other than Turael. */
        const val CANCEL_COST = SlayerRewards.CANCEL_COST
    }
}
