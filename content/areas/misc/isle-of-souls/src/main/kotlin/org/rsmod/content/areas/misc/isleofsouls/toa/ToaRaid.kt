package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

/** Where a raid is. */
enum class RaidStage {
    /** In the Nexus, or on a path. */
    Paths,

    /** Fighting the Wardens. */
    Wardens,

    /** The Wardens have fallen; the loot is in the vault. */
    Vault,

    /** Everyone has left, or the raid failed. */
    Ended,
}

/** One player of a raid: where they stand in the party list and how the raid has gone for them. */
class ToaMember(val player: Player, val slot: Int) {
    /** Dead and waiting for the room to be won or lost. */
    var ghost: Boolean = false
    var deaths: Int = 0
    var damageDone: Int = 0
    var damageTaken: Int = 0

    /** Has walked out of the raid (or been sent out of it). */
    var left: Boolean = false

    /** The helpful spirit has supplies for them. */
    var canClaimSupplies: Boolean = false

    /** The room last entered, for the HUD's "elsewhere" orb. */
    var room: ToaRoom? = null
}

/**
 * One party's run through the tombs: its region (a packed copy of every room), the invocations it
 * started with, the points, and how far it has got. Created by [ToaRaids]; everything here runs on
 * the game thread.
 */
class ToaRaid(
    val id: Int,
    val region: Region,
    /** The party's invocations when it entered; the raid does not follow later changes. */
    val settings: InvocationSet,
    val startedCycle: Int,
    /** Paths to clear before the Wardens open (the event can shorten the raid). */
    val pathsRequired: Int,
) {
    val members: MutableList<ToaMember> = ArrayList()

    /** The party leader at the start; the next member leads if they leave. */
    val leader: ToaMember?
        get() = members.firstOrNull { !it.left }

    val raidLevel: Int = settings.raidLevel
    val mode: ToaMode = ToaMode.of(raidLevel)

    var stage: RaidStage = RaidStage.Paths

    /** The path chosen at the Nexus and not yet cleared. */
    var currentPath: ToaPath? = null

    val completed: LinkedHashSet<ToaPath> = LinkedHashSet()

    /** The level of each path, by [ToaPath.ordinal]. */
    val pathLevels: IntArray = IntArray(ToaPath.entries.size) { settings.startingPathLevel }

    val points: ToaPoints = ToaPoints { members.count { !it.left } }

    /** Deaths of any player, and wipes (every member of a challenge dead at once). */
    var totalDeaths: Int = 0
    var teamWipes: Int = 0

    /** The raid failed: no attempts were left. */
    var failed: Boolean = false

    /** The cycle the first path was entered (the timer starts there) and the raid ended. */
    var challengeStartCycle: Int = -1
    var endCycle: Int = -1

    /** The Warden the final room is fought against: the one that fell in the first room. */
    var finalWarden: Warden = Warden.Tumeken

    /** A unique dropped for someone: the vault's flames and sarcophagus turn purple. */
    var uniqueFound: Boolean = false

    /** The raid level once a missed target time has cost its penalty; set at the end. */
    var finalRaidLevel: Int = raidLevel

    val active: List<ToaMember>
        get() = members.filter { !it.left }

    fun member(player: Player): ToaMember? = members.firstOrNull { it.player === player }

    fun isLeader(player: Player): Boolean = leader?.player === player

    // Coordinates
    // ----------------------------------------------------------------------------------

    /** Where the real tile [real] lies in this raid's region. */
    fun regionOf(real: CoordGrid): CoordGrid =
        checkNotNull(ToaPacking.toRegion(real, region.southWest)) { "$real is not in the tombs." }

    /** The real tile that [coords] in this raid's region is a copy of, if it is in a room. */
    fun realOf(coords: CoordGrid): CoordGrid? = ToaPacking.toReal(coords, region.southWest)

    fun roomAt(coords: CoordGrid): ToaRoom? = realOf(coords)?.let(ToaRoom::at)

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.x < region.northEast.x &&
            coords.z >= region.southWest.z &&
            coords.z < region.northEast.z

    // Progress
    // -------------------------------------------------------------------------------------

    val wardensOpen: Boolean
        get() = completed.size >= pathsRequired

    /** Attempts left before the raid fails, or `null` if the invocations allow unlimited. */
    val attemptsLeft: Int?
        get() = settings.attempts?.let { (it - teamWipes).coerceAtLeast(0) }

    /** Time since the first path was entered, in ticks, or 0 before it. */
    fun elapsed(now: Int): Int =
        if (challengeStartCycle < 0) 0
        else (if (endCycle >= 0) endCycle else now) - challengeStartCycle

    /**
     * The raid level the loot is worked out at if the raid ends at cycle [now]: a missed target
     * time costs its raid levels (wiki: 20, 30, 40 or 50).
     */
    fun levelAt(now: Int): Int {
        val limit = settings.timeLimitTicks
        if (limit != null && elapsed(now) > limit) {
            return (raidLevel - settings.timePenalty).coerceAtLeast(0)
        }
        return raidLevel
    }

    /** Walk the Path levels up two random open paths after one clear, one after the next two. */
    fun levelUpAfterClear(pick: (Int) -> Int) {
        if (!settings.walkThePath) {
            return
        }
        val amount =
            when (completed.size) {
                1 -> 2
                2,
                3 -> 1
                else -> 0
            }
        repeat(amount) {
            val open = ToaPath.entries.filter { it !in completed }
            if (open.isNotEmpty()) {
                pathLevels[open[pick(open.size)].ordinal]++
            }
        }
    }
}
