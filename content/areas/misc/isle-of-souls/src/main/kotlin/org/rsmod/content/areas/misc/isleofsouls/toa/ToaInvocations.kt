package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.type.TypeListMap

/**
 * Where an invocation sits in the board. Only one invocation of the first four categories can be on
 * at a time. The numbers are what the cache stores in struct param 1161.
 */
enum class InvocationCategory(val value: Int, val exclusive: Boolean = false) {
    Attempts(3, exclusive = true),
    TimeLimit(4, exclusive = true),
    HelpfulSpirit(5, exclusive = true),
    PathLevel(6, exclusive = true),
    Prayer(7),
    Restoration(8),
    Paths(9),
    Akkha(10),
    Kephri(11),
    Zebak(12),
    BaBa(13),
    Wardens(14),
    Blazing(15);

    companion object {
        fun of(value: Int): InvocationCategory? = entries.firstOrNull { it.value == value }
    }
}

/**
 * One invocation, as the cache has it: the struct holds its name (param 1160), the bit the party
 * panel reads (1159), its category (1161), the raid level it is worth (1162), what it was written
 * to do in numbers (1299: attempts, a time in ticks, a percentage, a path level) and the invocation
 * it needs first (1346).
 */
class Invocation(
    val struct: Int,
    /** Position in the party panel's list: the enum index of the board. */
    val slot: Int,
    /** The bit in the bitmaps the panel is given. */
    val bit: Int,
    val name: String,
    val category: InvocationCategory?,
    val level: Int,
    val magnitude: Int?,
    val requiresStruct: Int?,
    val description: String,
) {
    /** The Blazing Tombs pair belong to a different event; they cannot be switched on here. */
    val offered: Boolean
        get() = category != InvocationCategory.Blazing
}

/** The invocation names the raid code looks for. */
object Inv {
    const val TRY_AGAIN = "Try Again"
    const val PERSISTENCE = "Persistence"
    const val SOFTCORE_RUN = "Softcore Run"
    const val HARDCORE_RUN = "Hardcore Run"
    const val WALK_FOR_IT = "Walk for It"
    const val JOG_FOR_IT = "Jog for It"
    const val RUN_FOR_IT = "Run for It"
    const val SPRINT_FOR_IT = "Sprint for It"
    const val NEED_SOME_HELP = "Need Some Help?"
    const val NEED_LESS_HELP = "Need Less Help?"
    const val NO_HELP_NEEDED = "No Help Needed"
    const val WALK_THE_PATH = "Walk the Path"
    const val PATHSEEKER = "Pathseeker"
    const val PATHFINDER = "Pathfinder"
    const val PATHMASTER = "Pathmaster"
    const val QUIET_PRAYERS = "Quiet Prayers"
    const val DEADLY_PRAYERS = "Deadly Prayers"
    const val ON_A_DIET = "On a Diet"
    const val DEHYDRATION = "Dehydration"
    const val OVERLY_DRAINING = "Overly Draining"
    const val LIVELY_LARVAE = "Lively Larvae"
    const val MORE_OVERLORDS = "More Overlords"
    const val BLOWING_MUD = "Blowing Mud"
    const val MEDIC = "Medic!"
    const val AERIAL_ASSAULT = "Aerial Assault"
    const val NOT_JUST_A_HEAD = "Not Just a Head"
    const val ARTERIAL_SPRAY = "Arterial Spray"
    const val BLOOD_THINNERS = "Blood Thinners"
    const val UPSET_STOMACH = "Upset Stomach"
    const val DOUBLE_TROUBLE = "Double Trouble"
    const val KEEP_BACK = "Keep Back"
    const val STAY_VIGILANT = "Stay Vigilant"
    const val FEELING_SPECIAL = "Feeling Special?"
    const val MIND_THE_GAP = "Mind the Gap!"
    const val GOTTA_HAVE_FAITH = "Gotta Have Faith"
    const val JUNGLE_JAPES = "Jungle Japes"
    const val SHAKING_THINGS_UP = "Shaking Things Up"
    const val BOULDERDASH = "Boulderdash"
    const val ANCIENT_HASTE = "Ancient Haste"
    const val ACCELERATION = "Acceleration"
    const val PENETRATION = "Penetration"
    const val OVERCLOCKED = "Overclocked"
    const val OVERCLOCKED_2 = "Overclocked 2"
    const val INSANITY = "Insanity"
}

/**
 * Every invocation, read once from the cache the way the client's board does: enum 4664 lists the
 * structs in board order.
 */
class InvocationCatalog(val all: List<Invocation>) {
    private val byName: Map<String, Invocation> = all.associateBy { it.name }
    private val byBit: Map<Int, Invocation> = all.associateBy { it.bit }
    private val byStruct: Map<Int, Invocation> = all.associateBy { it.struct }

    fun byName(name: String): Invocation? = byName[name]

    fun byBit(bit: Int): Invocation? = byBit[bit]

    fun bySlot(slot: Int): Invocation? = all.getOrNull(slot)

    /** What [invocation] needs switched on first. */
    fun requirement(invocation: Invocation): Invocation? =
        invocation.requiresStruct?.let { byStruct[it] }

    /** The invocations that switch off when [invocation] does. */
    fun dependants(invocation: Invocation): List<Invocation> =
        all.filter { it.requiresStruct == invocation.struct }

    companion object {
        const val ENUM = 4664
        const val PARAM_BIT = 1159
        const val PARAM_NAME = 1160
        const val PARAM_CATEGORY = 1161
        const val PARAM_LEVEL = 1162
        const val PARAM_DESCRIPTION = 1262
        const val PARAM_MAGNITUDE = 1299
        const val PARAM_REQUIRES = 1346

        fun build(
            structs: (Int) -> Map<Int, Any>?,
            enums: (Int) -> Map<Any, Any?>?,
        ): InvocationCatalog {
            val board = enums(ENUM) ?: error("Invocation enum missing: $ENUM")
            val list = ArrayList<Invocation>(board.size)
            for (slot in 0 until board.size) {
                val struct = (board[slot] as Number).toInt()
                val params = structs(struct) ?: error("Invocation struct missing: $struct")
                list +=
                    Invocation(
                        struct = struct,
                        slot = slot,
                        bit = (params[PARAM_BIT] as Number).toInt(),
                        name = params[PARAM_NAME] as String,
                        category =
                            InvocationCategory.of((params[PARAM_CATEGORY] as Number).toInt()),
                        level = (params[PARAM_LEVEL] as Number).toInt(),
                        magnitude = (params[PARAM_MAGNITUDE] as? Number)?.toInt(),
                        requiresStruct = (params[PARAM_REQUIRES] as? Number)?.toInt(),
                        description = params[PARAM_DESCRIPTION] as? String ?: "",
                    )
            }
            return InvocationCatalog(list)
        }
    }
}

/** Reads the [InvocationCatalog] from the loaded cache the first time it is needed. */
@Singleton
class InvocationCatalogProvider @Inject constructor(private val types: TypeListMap) {
    val catalog: InvocationCatalog by lazy {
        InvocationCatalog.build(
            structs = { id -> types.structs.types[id]?.paramMap?.primitiveMap },
            enums = { id -> types.enums.types[id]?.primitiveMap },
        )
    }
}

/** What turning an invocation on or off did. */
sealed interface InvocationToggle {
    data class On(val invocation: Invocation) : InvocationToggle

    data class Off(val invocation: Invocation) : InvocationToggle

    /** It cannot be switched on until [needs] is. */
    data class NeedsOther(val needs: Invocation) : InvocationToggle

    data object NotOffered : InvocationToggle
}

/**
 * A choice of invocations: the three 31-bit words the party panel is given. The panel's init script
 * reads exactly these, so a preset and the panel share the one form.
 */
class InvocationSet(private val catalog: InvocationCatalog, val words: IntArray = IntArray(WORDS)) {
    constructor(
        catalog: InvocationCatalog,
        w0: Int,
        w1: Int,
        w2: Int,
    ) : this(catalog, intArrayOf(w0, w1, w2))

    init {
        require(words.size == WORDS) { "An invocation set has $WORDS words." }
    }

    fun copy(): InvocationSet = InvocationSet(catalog, words.copyOf())

    /** The invocation called [name] on this board, if there is one. */
    fun byName(name: String): Invocation? = catalog.byName(name)

    fun load(other: IntArray) {
        for (i in 0 until WORDS) {
            words[i] = other.getOrElse(i) { 0 }
        }
    }

    fun clear() {
        words.fill(0)
    }

    fun isEmpty(): Boolean = words.all { it == 0 }

    fun isActive(invocation: Invocation): Boolean =
        words[wordOf(invocation.bit)] and maskOf(invocation.bit) != 0

    fun isActive(name: String): Boolean = catalog.byName(name)?.let(::isActive) ?: false

    fun active(): List<Invocation> = catalog.all.filter(::isActive)

    val activeCount: Int
        get() = active().size

    /** The sum of the levels of everything switched on. */
    val raidLevel: Int
        get() = active().sumOf { it.level }

    val mode: ToaMode
        get() = ToaMode.of(raidLevel)

    private fun set(invocation: Invocation, on: Boolean) {
        val word = wordOf(invocation.bit)
        val mask = maskOf(invocation.bit)
        words[word] = if (on) words[word] or mask else words[word] and mask.inv()
    }

    /**
     * Flips [invocation] the way the board does: an exclusive category swaps, a dependant follows
     * the invocation it needs off, and cannot be switched on before it.
     */
    fun toggle(invocation: Invocation): InvocationToggle {
        if (!invocation.offered) {
            return InvocationToggle.NotOffered
        }
        if (isActive(invocation)) {
            switchOff(invocation)
            return InvocationToggle.Off(invocation)
        }
        val needs = catalog.requirement(invocation)
        if (needs != null && !isActive(needs)) {
            return InvocationToggle.NeedsOther(needs)
        }
        val category = invocation.category
        if (category != null && category.exclusive) {
            for (other in catalog.all) {
                if (other.category == category) {
                    set(other, false)
                }
            }
        }
        set(invocation, true)
        return InvocationToggle.On(invocation)
    }

    private fun switchOff(invocation: Invocation) {
        for (dependant in catalog.dependants(invocation)) {
            if (isActive(dependant)) {
                switchOff(dependant)
            }
        }
        set(invocation, false)
    }

    // What the invocations mean for a raid
    // ---------------------------------------------------------

    private fun activeIn(category: InvocationCategory): Invocation? =
        active().firstOrNull { it.category == category }

    /** Attempts before the raid fails (a team wipe uses one), or `null` for no limit. */
    val attempts: Int?
        get() = activeIn(InvocationCategory.Attempts)?.magnitude

    /** The target time in ticks, or `null` for none. */
    val timeLimitTicks: Int?
        get() = activeIn(InvocationCategory.TimeLimit)?.magnitude

    /** Raid levels lost by missing the target time (wiki: 20, 30, 40, 50). */
    val timePenalty: Int
        get() = activeIn(InvocationCategory.TimeLimit)?.let { it.level * 2 } ?: 0

    /** The share of the helpful spirit's supplies on offer, in percent. */
    val supplyPercent: Int
        get() = activeIn(InvocationCategory.HelpfulSpirit)?.magnitude ?: 100

    /** The level every path starts at (0-3). */
    val startingPathLevel: Int
        get() = activeIn(InvocationCategory.PathLevel)?.magnitude ?: 0

    /** Whether completing a path levels up the others. */
    val walkThePath: Boolean
        get() = isActive(Inv.WALK_THE_PATH)

    companion object {
        const val WORDS = 3
        const val BITS_PER_WORD = 31

        /** The panel packs bit [bit] into word `0` for 1-30, `1` for 31-61 and `2` after that. */
        fun wordOf(bit: Int): Int =
            when {
                bit > 61 -> 2
                bit > 30 -> 1
                else -> 0
            }

        fun maskOf(bit: Int): Int = 1 shl (bit % BITS_PER_WORD)
    }
}

/** Wiki: Entry 0-149, Normal 150-299, Expert 300 and above. */
enum class ToaMode(val label: String, val lowest: Int) {
    Entry("Entry", 0),
    Normal("Normal", 150),
    Expert("Expert", 300);

    /** The raid only counts for the league's completion tasks in Normal and Expert. */
    val countsForTasks: Boolean
        get() = this != Entry

    /** The lower-case name the saved variables use. */
    val key: String
        get() = name.lowercase()

    companion object {
        fun of(raidLevel: Int): ToaMode =
            when {
                raidLevel >= Expert.lowest -> Expert
                raidLevel >= Normal.lowest -> Normal
                else -> Entry
            }
    }
}
