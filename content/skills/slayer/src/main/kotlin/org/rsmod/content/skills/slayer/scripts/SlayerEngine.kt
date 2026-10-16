package org.rsmod.content.skills.slayer.scripts

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.slayer.configs.MasterTask
import org.rsmod.content.skills.slayer.configs.SlayerBlocked
import org.rsmod.content.skills.slayer.configs.SlayerMaster
import org.rsmod.content.skills.slayer.configs.SlayerParams
import org.rsmod.content.skills.slayer.configs.SlayerTables
import org.rsmod.content.skills.slayer.configs.SlayerTaskDef
import org.rsmod.content.skills.slayer.configs.SlayerVarBits
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.content.skills.slayer.configs.StatReq
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.stat.StatTypeList
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.game.type.varp.VarpType

/** A task a player has: [task], [remaining] kills of [amount], given by [master]. */
class SlayerAssignment(
    val task: SlayerTaskDef,
    val master: SlayerMaster?,
    val remaining: Int,
    val amount: Int,
)

/** Why a master will not give a task right now. */
enum class SlayerRefusal {
    /** The player's combat level is below the master's requirement. */
    Combat,
    /** The player's Slayer level is below the master's requirement. */
    SlayerLevel,
    /** The player already has a task. */
    HasTask,
    /** Every task the master has is blocked, locked or out of the player's range. */
    NoTask,
}

object SlayerEvents {
    /** A master gave [player] a new task. */
    class Assigned(
        val player: Player,
        val master: SlayerMaster,
        val task: SlayerTaskDef,
        val amount: Int,
    ) : UnboundEvent

    /**
     * [player] killed a superior monster, [npc]. [onTask] is whether their assignment was what the
     * superior belongs to (it also counts as a kill for it then).
     */
    class SuperiorKilled(val player: Player, val npc: Npc, val onTask: Boolean) : UnboundEvent

    /** A superior appeared for [player] in place of the [base] monster they killed. */
    class SuperiorSpawned(val player: Player, val superior: Npc, val base: Npc) : UnboundEvent

    /** [player] made a Slayer helmet. */
    class HelmAssembled(val player: Player) : UnboundEvent

    /** [player] looked at their task with an enchanted gem. */
    class Checked(val player: Player) : UnboundEvent

    /**
     * [player] killed [npc], a monster of [task], and got Slayer XP for it ([xp] before mods).
     * [onTask] is whether it counted for their assignment ([remaining] kills are left then); a
     * player can also be treated as on task for every monster they could be assigned (the Slayer
     * Master relic), which gives the XP but does not count the kill.
     */
    class Kill(
        val player: Player,
        val npc: Npc,
        val task: SlayerTaskDef,
        val remaining: Int,
        val xp: Double,
        val onTask: Boolean = true,
    ) : UnboundEvent

    /** [player] finished a task for [master] and was given [points]. */
    class Completed(
        val player: Player,
        val master: SlayerMaster?,
        val task: SlayerTaskDef,
        val amount: Int,
        val streak: Int,
        val points: Int,
    ) : UnboundEvent
}

/**
 * Rules content can change without the Slayer module knowing it: the relics register here. Each
 * list holds functions of the player.
 */
class SlayerHooks {
    /** Unlock bits a player has without buying them. */
    val unlocked = ArrayList<(Player, Int) -> Boolean>()

    /** Multipliers on the points a finished task gives (they multiply together). */
    val pointMultipliers = ArrayList<(Player) -> Int>()

    /** Whether cancelling, blocking and the store's items cost nothing. */
    val free = ArrayList<(Player) -> Boolean>()

    /** Whether the player counts as on task for every monster they could be assigned. */
    val alwaysOnTask = ArrayList<(Player) -> Boolean>()

    /** The `1 in N` chance of a superior; the smallest N given wins. A `null` gives no opinion. */
    val superiorOdds = ArrayList<(Player) -> Int?>()
}

/**
 * Slayer tasks: who may be given what, the player's current task, kills, Slayer XP, points and the
 * unlock bits. Everything is stored in the cache's own varps and varbits ([SlayerVarps],
 * [SlayerVarBits]) so the client's Slayer screens show it.
 */
@Singleton
class SlayerEngine
@Inject
constructor(
    private val random: GameRandom,
    private val statTypes: StatTypeList,
    private val xpMods: XpModifiers,
    private val eventBus: EventBus,
) {
    val tables: SlayerTables
        get() = SlayerTables.default

    val hooks = SlayerHooks()

    /** Whether cancelling, blocking and the store cost [player] nothing. */
    fun isFree(player: Player): Boolean = hooks.free.any { it(player) }

    // --- The player's state --------------------------------------------------------------------

    fun current(player: Player): SlayerAssignment? {
        val id = player.vars[SlayerVarps.target]
        val remaining = player.vars[SlayerVarps.count]
        if (id == 0 || remaining <= 0) {
            return null
        }
        val task = tables.tasks[id] ?: return null
        val master = tables.masterById(player.vars[SlayerVarBits.master])
        return SlayerAssignment(task, master, remaining, player.vars[SlayerVarps.count_original])
    }

    fun points(player: Player): Int = player.vars[SlayerVarBits.points]

    fun streak(player: Player): Int = player.vars[SlayerVarBits.streak]

    fun addPoints(player: Player, points: Int) {
        set(player, SlayerVarBits.points, points(player) + points)
    }

    /** Takes [cost] points if the player has them. */
    fun spendPoints(player: Player, cost: Int): Boolean {
        if (points(player) < cost) {
            return false
        }
        set(player, SlayerVarBits.points, points(player) - cost)
        return true
    }

    // --- Unlocks and blocks --------------------------------------------------------------------

    /**
     * The unlock bits of the rewards screen. Bits 0 to 31 are in one varp and the rest in the next;
     * that split is a reading of the cache that the rewards screen (step 4) confirms.
     */
    fun isUnlocked(player: Player, bit: Int): Boolean =
        hasBit(player, bit) || hooks.unlocked.any { it(player, bit) }

    /** Whether the player bought the unlock, ignoring anything that grants it. */
    fun hasBit(player: Player, bit: Int): Boolean {
        val varp = if (bit < Int.SIZE_BITS) SlayerVarps.unlocks else SlayerVarps.unlocks_high
        return (player.vars[varp] ushr (bit % Int.SIZE_BITS)) and 1 == 1
    }

    fun setUnlocked(player: Player, bit: Int, unlocked: Boolean) {
        val varp = if (bit < Int.SIZE_BITS) SlayerVarps.unlocks else SlayerVarps.unlocks_high
        val mask = 1 shl (bit % Int.SIZE_BITS)
        val value = player.vars[varp]
        set(player, varp, if (unlocked) value or mask else value and mask.inv())
    }

    /** The ids of the tasks [master] will not give [player]; each master has its own list. */
    fun blocked(player: Player, master: SlayerMaster): List<Int> =
        SlayerBlocked.slots(master).map { player.vars[it] }.filter { it != 0 }

    fun isBlocked(player: Player, master: SlayerMaster, taskId: Int): Boolean =
        taskId in blocked(player, master)

    /** Puts [taskId] (or `0` to clear it) in [master]'s block slot [slot], from 0 to 5. */
    fun setBlocked(player: Player, master: SlayerMaster, slot: Int, taskId: Int) {
        set(player, SlayerBlocked.slots(master)[slot], taskId)
    }

    // --- Choosing a task -----------------------------------------------------------------------

    fun canUse(player: Player, master: SlayerMaster): SlayerRefusal? =
        when {
            player.combatLevel < master.combat -> SlayerRefusal.Combat
            player.statBase(stats.slayer) < master.slayer -> SlayerRefusal.SlayerLevel
            current(player) != null -> SlayerRefusal.HasTask
            else -> null
        }

    /**
     * The tasks [master] could give [player] now. The Leagues combat window (the cache's
     * `leagues_min_comlevel` and `leagues_max_comlevel`) keeps strong players off low tasks; if it
     * would leave nothing, it is ignored.
     */
    fun eligible(player: Player, master: SlayerMaster): List<MasterTask> {
        val open = master.tasks.filter { meetsRequirements(player, master, it) }
        val windowed = open.filter { inLeaguesWindow(player.combatLevel, it.task) }
        return windowed.ifEmpty { open }
    }

    fun meetsRequirements(player: Player, master: SlayerMaster, entry: MasterTask): Boolean {
        val task = entry.task
        if (player.combatLevel < task.combat) {
            return false
        }
        if (task.stats.any { !meets(player, it) }) {
            return false
        }
        if (task.statsAny.isNotEmpty() && task.statsAny.none { meets(player, it) }) {
            return false
        }
        if (entry.unlockBit != null && !isUnlocked(player, entry.unlockBit)) {
            return false
        }
        return !isBlocked(player, master, task.id)
    }

    private fun meets(player: Player, req: StatReq): Boolean {
        val type = statTypes[req.statId] ?: return true
        return player.statBase(type) >= req.level
    }

    private fun inLeaguesWindow(combat: Int, task: SlayerTaskDef): Boolean =
        (task.leaguesMin == null || combat >= task.leaguesMin) &&
            (task.leaguesMax == null || combat <= task.leaguesMax)

    /** How many kills [entry] asks for: the master's range, or the extended one once bought. */
    fun amountRange(player: Player, entry: MasterTask): IntRange {
        val task = entry.task
        val bit = task.extendBit
        if (bit != null && isUnlocked(player, bit) && task.extendMax > 0) {
            return task.extendMin..task.extendMax
        }
        return entry.min..entry.max
    }

    /**
     * Gives [player] a task from [master], or says why not. [forced] picks the task instead of
     * rolling (for commands and tests); its requirements are not checked.
     */
    fun assign(
        player: Player,
        master: SlayerMaster,
        forced: MasterTask? = null,
    ): Pair<SlayerAssignment?, SlayerRefusal?> {
        canUse(player, master)?.let {
            return null to it
        }
        val entry = forced ?: pick(eligible(player, master)) ?: return null to SlayerRefusal.NoTask
        val range = amountRange(player, entry)
        val amount = random.of(range.first, range.last)
        give(player, master, entry.task, amount)
        eventBus.publish(SlayerEvents.Assigned(player, master, entry.task, amount))
        return SlayerAssignment(entry.task, master, amount, amount) to null
    }

    private fun pick(entries: List<MasterTask>): MasterTask? {
        val total = entries.sumOf { it.weight }
        if (total <= 0) {
            return null
        }
        var roll = random.of(total)
        for (entry in entries) {
            roll -= entry.weight
            if (roll < 0) {
                return entry
            }
        }
        return entries.last()
    }

    /** Writes a task into the player's state. */
    fun give(player: Player, master: SlayerMaster?, task: SlayerTaskDef, amount: Int) {
        set(player, SlayerVarps.target, task.id)
        set(player, SlayerVarps.count, amount)
        set(player, SlayerVarps.count_original, amount)
        set(player, SlayerVarBits.master, master?.masterId ?: 0)
    }

    /** Drops the current task and ends the streak. Charging for it is up to the caller. */
    fun cancel(player: Player) {
        clearTask(player)
        set(player, SlayerVarBits.streak, 0)
    }

    /**
     * Drops the current task and keeps the streak, which is what cancelling or blocking at the
     * rewards screen does ("Neither option will reset your current tally of completed Slayer tasks"
     * in the cache). Charging for it is up to the caller.
     */
    fun drop(player: Player) {
        clearTask(player)
    }

    private fun clearTask(player: Player) {
        set(player, SlayerVarps.target, 0)
        set(player, SlayerVarps.count, 0)
        set(player, SlayerVarps.count_original, 0)
        set(player, SlayerVarBits.master, 0)
    }

    fun helmAssembled(player: Player) {
        eventBus.publish(SlayerEvents.HelmAssembled(player))
    }

    /** The player used an enchanted gem to check their task. */
    fun checked(player: Player) {
        eventBus.publish(SlayerEvents.Checked(player))
    }

    fun describe(assignment: SlayerAssignment): String =
        "Your current assignment is ${assignment.task.lowerName}; " +
            "only ${assignment.remaining} more to go."

    // --- Kills ---------------------------------------------------------------------------------

    /** The ids of the superior monsters. */
    val superiorIds: Set<Int> by lazy { tables.superiors.map { it.npc }.toSet() }

    /** Whether [npc] is what [player]'s current assignment is about. */
    fun isOnTask(player: Player, npc: Npc): Boolean {
        val current = current(player) ?: return false
        return current.task.covers(npc.visType.param(SlayerParams.task_id))
    }

    /**
     * Whether the hooks treat [player] as on task for a monster of [type]: they are always on task
     * and the monster is one a master could assign them and they could harm.
     */
    fun countsAsOnTask(player: Player, type: UnpackedNpcType): Boolean {
        if (hooks.alwaysOnTask.none { it(player) }) {
            return false
        }
        val taskId = type.param(SlayerParams.task_id)
        if (
            taskId == 0 ||
                (tables.tasks[taskId] ?: tables.tasks.values.firstOrNull { it.covers(taskId) }) ==
                    null
        ) {
            return false
        }
        val own = type.param(params.slayer_levelrequire)
        return player.statBase(stats.slayer) >= own
    }

    fun publish(event: UnboundEvent) {
        eventBus.publish(event)
    }

    /**
     * [player] killed [npc]: counts it if it is what they were sent to kill, and gives the Slayer
     * XP. A player the hooks make "always on task" also gets the XP for any monster they could be
     * given.
     */
    fun onKill(player: Player, npc: Npc) {
        val type = npc.visType
        val taskId = type.param(SlayerParams.task_id)
        if (taskId == 0) {
            return
        }
        val current = current(player)
        val onTask = current != null && current.task.covers(taskId)
        val task: SlayerTaskDef
        if (onTask) {
            task = checkNotNull(current).task
        } else {
            if (hooks.alwaysOnTask.none { it(player) }) {
                return
            }
            task =
                tables.tasks[taskId]
                    ?: tables.tasks.values.firstOrNull { it.covers(taskId) }
                    ?: return
            if (player.statBase(stats.slayer) < SlayerRequirements.requiredLevel(npc)) {
                return
            }
        }
        val base = slayerXp(type.id, type.param(params.slayer_experience), type.hitpoints)
        val xp = base * xpMods.get(player, stats.slayer)
        player.statAdvance(stats.slayer, xp)

        var remaining = 0
        if (current != null && onTask) {
            remaining = current.remaining - 1
            set(player, SlayerVarps.count, remaining)
        }
        eventBus.publish(SlayerEvents.Kill(player, npc, task, remaining, base, onTask))
        if (current != null && onTask && remaining <= 0) {
            complete(player, current)
        }
    }

    /**
     * The Slayer XP of one kill: the npc's own value (kept in tenths), else the one the cache data
     * gives, else its hitpoints, which is what a monster's Slayer XP is in almost every case.
     */
    fun slayerXp(npcId: Int, tenths: Int, hitpoints: Int): Double =
        when {
            tenths > 0 -> tenths / 10.0
            else -> tables.npcXp[npcId] ?: hitpoints.toDouble()
        }

    private fun complete(player: Player, finished: SlayerAssignment) {
        val master = finished.master
        val streak = streak(player) + 1
        set(player, SlayerVarBits.streak, streak)
        val multiplier = hooks.pointMultipliers.fold(1) { total, f -> total * f(player) }
        val points = (master?.points?.forStreak(streak) ?: 0) * multiplier
        addPoints(player, points)
        clearTask(player)
        player.mes(
            "You've completed $streak tasks in a row and received $points points, " +
                "giving you a total of ${points(player)}; return to a Slayer master."
        )
        eventBus.publish(
            SlayerEvents.Completed(player, master, finished.task, finished.amount, streak, points)
        )
    }

    // --- Varps ---------------------------------------------------------------------------------

    fun set(player: Player, varp: VarpType, value: Int) {
        VarPlayerIntMapSetter.set(player, varp, value)
    }

    /** Sets a varbit, held to what its bits can store. */
    fun set(player: Player, varbit: VarBitType, value: Int) {
        val bits = varbit.bits
        val max = (1L shl (bits.last - bits.first + 1)) - 1
        VarPlayerIntMapSetter.set(
            player,
            varbit,
            value.coerceIn(0, max.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()),
        )
    }
}
