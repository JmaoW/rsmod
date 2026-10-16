package org.rsmod.content.skills.slayer.configs

/** A stat requirement as the cache holds it: [level] in the stat with cache id [statId]. */
data class StatReq(val level: Int, val statId: Int) {
    companion object {
        const val SLAYER = 18
        const val MAGIC = 6

        fun parse(text: String): List<StatReq> =
            if (text.isBlank()) {
                emptyList()
            } else {
                text.split(',').map {
                    val (level, stat) = it.split(':')
                    StatReq(level.toInt(), stat.toInt())
                }
            }
    }
}

/**
 * One kind of task (`slayer_task` in the cache). A task such as metal dragons covers the monsters
 * of several task ids, which are the [members].
 */
class SlayerTaskDef(
    val id: Int,
    val name: String,
    val members: Set<Int>,
    val combat: Int,
    val stats: List<StatReq>,
    val statsAny: List<StatReq>,
    val leaguesMin: Int?,
    val leaguesMax: Int?,
    val extendBit: Int?,
    val extendMin: Int,
    val extendMax: Int,
    val spawns: Int,
) {
    /** The name in lower case, as used in "kill 126 aberrant spectres". */
    val lowerName: String
        get() = name.lowercase()

    /** Whether a monster whose own task id is [npcTaskId] counts for this task. */
    fun covers(npcTaskId: Int): Boolean = npcTaskId in members
}

/** One task a master can hand out, with the master's own weight and amounts. */
class MasterTask(
    val task: SlayerTaskDef,
    val weight: Int,
    val min: Int,
    val max: Int,
    val unlockBit: Int?,
)

/** Slayer reward points for finishing a task, by the task streak it ends on. */
class PointsTable(
    private val every: Int,
    private val tenth: Int,
    private val fiftieth: Int,
    private val hundredth: Int,
    private val twoFiftieth: Int,
    private val thousandth: Int,
) {
    fun forStreak(streak: Int): Int =
        when {
            streak <= 0 -> every
            streak % 1000 == 0 -> thousandth
            streak % 250 == 0 -> twoFiftieth
            streak % 100 == 0 -> hundredth
            streak % 50 == 0 -> fiftieth
            streak % 10 == 0 -> tenth
            else -> every
        }
}

class SlayerMaster(
    val key: String,
    val masterId: Int,
    val combat: Int,
    val slayer: Int,
    val blockCost: Int,
    val points: PointsTable,
    val tasks: List<MasterTask>,
) {
    val displayName: String
        get() = key.replaceFirstChar { it.uppercase() }
}

/** A superior monster: [npc] appears in place of a monster of the task with id [task]. */
class SuperiorDef(val npc: Int, val task: Int, val name: String)

/** The kinds of unlock in the rewards store. */
enum class UnlockKind {
    Task,
    Extend,
    Perk,
}

/**
 * One thing the rewards store sells for points. [works] is whether it does anything on the isle (a
 * perk for a monster that is not here, for example, does not).
 */
class SlayerUnlock(
    val bit: Int,
    val name: String,
    val cost: Int,
    val refundable: Boolean,
    val kind: UnlockKind,
    val works: Boolean,
    val description: String,
    /** The rewards screen's tab (0 Unlock, 1 Extend), or -1 for entries the screen never lists. */
    val tab: Int = -1,
    /** Its place in that tab. */
    val position: Int = -1,
)

/** An item the store sells: [obj] is its cache name, bought [quantity] at a time. */
class RewardItem(val obj: String, val quantity: Int, val cost: Int)

/**
 * The Slayer tables, generated from the cache by `tools/slayer/build_data.py` into the module's
 * resources. The cache's own masters, weights, amounts and requirements are used as they are; see
 * `docs/isle_of_souls_slayer_plan.md`.
 */
class SlayerTables(
    val tasks: Map<Int, SlayerTaskDef>,
    val masters: Map<String, SlayerMaster>,
    val npcXp: Map<Int, Double>,
    val superiors: List<SuperiorDef>,
    val unlocks: List<SlayerUnlock> = emptyList(),
    val rewardItems: List<RewardItem> = emptyList(),
) {
    fun unlock(bit: Int): SlayerUnlock? = unlocks.firstOrNull { it.bit == bit }

    fun master(key: String): SlayerMaster? = masters[key.lowercase()]

    fun masterById(id: Int): SlayerMaster? = masters.values.firstOrNull { it.masterId == id }

    companion object {
        /** The cache's ids for the masters the isle uses. */
        val MASTER_IDS = mapOf("turael" to 1, "vannaka" to 3, "duradel" to 5)

        private const val PATH = "/org/rsmod/content/skills/slayer/"

        val default: SlayerTables by lazy { load { name -> resource(name) } }

        private fun resource(name: String): List<List<String>> {
            val stream =
                SlayerTables::class.java.getResourceAsStream(PATH + name)
                    ?: error("Missing slayer resource `$name`.")
            return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).filter { it.isNotBlank() }.map { it.split('\t') }.toList()
            }
        }

        /** Builds the tables from the rows of each file (without its header line). */
        fun load(read: (String) -> List<List<String>>): SlayerTables {
            fun String.int() = toInt()

            fun String.intOrNull() = if (isBlank()) null else toInt()

            val tasks =
                read("tasks.tsv")
                    .map { c ->
                        val id = c[0].int()
                        SlayerTaskDef(
                            id = id,
                            name = c[1],
                            members = c[2].split(',').map { it.int() }.toSet(),
                            combat = c[3].int(),
                            stats = StatReq.parse(c[4]),
                            statsAny = StatReq.parse(c[5]),
                            leaguesMin = c[6].intOrNull(),
                            leaguesMax = c[7].intOrNull(),
                            extendBit = c[8].intOrNull(),
                            extendMin = c[9].intOrNull() ?: 0,
                            extendMax = c[10].intOrNull() ?: 0,
                            spawns = c[11].int(),
                        )
                    }
                    .associateBy { it.id }
            val byMaster = HashMap<String, MutableList<MasterTask>>()
            for (c in read("master_tasks.tsv")) {
                val task = tasks.getValue(c[1].int())
                byMaster
                    .getOrPut(c[0]) { ArrayList() }
                    .add(
                        MasterTask(
                            task,
                            c[2].int(),
                            c[3].int(),
                            c[4].int(),
                            c.getOrNull(5)?.intOrNull(),
                        )
                    )
            }
            val masters =
                read("masters.tsv")
                    .map { c ->
                        val key = c[0]
                        SlayerMaster(
                            key = key,
                            masterId = MASTER_IDS.getValue(key),
                            combat = c[1].int(),
                            slayer = c[2].int(),
                            blockCost = c[3].int(),
                            points =
                                PointsTable(
                                    c[4].int(),
                                    c[5].int(),
                                    c[6].int(),
                                    c[7].int(),
                                    c[8].int(),
                                    c[9].int(),
                                ),
                            tasks = byMaster[key].orEmpty(),
                        )
                    }
                    .associateBy { it.key }
            val xp = read("npc_xp.tsv").associate { it[0].int() to it[1].toDouble() }
            val superiors =
                read("superiors.tsv").map { SuperiorDef(it[0].int(), it[1].int(), it[2]) }
            val unlocks =
                read("unlocks.tsv").map { c ->
                    SlayerUnlock(
                        bit = c[0].int(),
                        name = c[1],
                        cost = c[2].int(),
                        refundable = c[3] == "1",
                        kind =
                            when (c[4]) {
                                "task" -> UnlockKind.Task
                                "extend" -> UnlockKind.Extend
                                else -> UnlockKind.Perk
                            },
                        works = c[5] == "1",
                        description = c[8],
                        tab = c[6].int(),
                        position = c[7].int(),
                    )
                }
            val rewardItems =
                read("reward_items.tsv").map { RewardItem(it[0], it[1].int(), it[2].int()) }
            return SlayerTables(tasks, masters, xp, superiors, unlocks, rewardItems)
        }
    }
}
