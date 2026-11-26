package org.rsmod.content.areas.misc.isleofsouls.tob

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

/** One player of a raid: their slot in the party and how the raid has gone for them. */
class TobMember(val player: Player, val slot: Int) {
    /** The room the player last walked into. */
    var room: TobRoom? = null

    /** Crossed into the current room's fight (the barrier). */
    var inFight: Boolean = false

    /** Dead, waiting in the room's jail until the room is won or the party falls. */
    var jailed: Boolean = false

    /** The real tile they wait on while jailed, and whether they have taken the jail's stance. */
    var jail: CoordGrid? = null
    var stanced: Boolean = false

    /** Where they crossed into the fight (the jailed come back there) and where they fell. */
    var beforeBarrier: CoordGrid? = null
    var deathTile: CoordGrid? = null

    var deaths: Int = 0
    val deathsIn: MutableMap<TobRoom, Int> = HashMap()
    val damageIn: MutableMap<TobRoom, Int> = HashMap()

    /** Has walked out of the raid (or been sent out of it). */
    var left: Boolean = false

    /** The supply chests' points, and whether this chest's stamina potion is bought. */
    var supplyPoints: Int = 0
    var staminaBought: Boolean = false

    /** Died in both rooms since the last chest: the chest gives an onion and nothing else. */
    var onionOnly: Boolean = false

    /** The amount of the supply shop's last Buy-X. */
    var lastBuy: Int = 1

    /** The loot rolled when Verzik fell, until they open their chest (or leave the raid). */
    var unclaimed: TobUnclaimed? = null

    val damage: Int
        get() = damageIn.values.sum()
}

/**
 * One party's run through the theatre: its region (a copy of the whole theatre), its members, the
 * room it has reached and how it is going. Created by [TobRaids]; everything here runs on the game
 * thread.
 */
class TobRaid(val id: Int, val region: Region, val startedCycle: Int) {
    val members: MutableList<TobMember> = ArrayList()

    val active: List<TobMember>
        get() = members.filter { !it.left }

    /** The first member still in the raid leads. */
    val leader: TobMember?
        get() = members.firstOrNull { !it.left }

    /** The furthest room the party has reached. */
    var room: TobRoom = TobRoom.Maiden

    /** Rooms whose fight is won. */
    val completed: MutableSet<TobRoom> = LinkedHashSet()

    /** The room's bar on the HUD, set by the room's content each tick. */
    var bar: TobBar = TobBar.NONE

    var totalDeaths: Int = 0

    /** Ticks spent in each room's fight, added up as the rooms are won. */
    var totalTicks: Int = 0

    var failed: Boolean = false
    var won: Boolean = false
    var ended: Boolean = false
    var endCycle: Int = -1

    /** A unique dropped: the chests glow purple. */
    var uniqueFound: Boolean = false
    var uniqueFinder: Player? = null

    fun member(player: Player): TobMember? = members.firstOrNull { it.player === player }

    fun isLeader(player: Player): Boolean = leader?.player === player

    fun regionOf(real: CoordGrid): CoordGrid =
        checkNotNull(TobLayout.toRegion(real, region.southWest)) { "$real is not in the theatre." }

    fun realOf(coords: CoordGrid): CoordGrid? = TobLayout.toReal(coords, region.southWest)

    fun roomAt(coords: CoordGrid): TobRoom? = realOf(coords)?.let(TobRoom::at)

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.x < region.northEast.x &&
            coords.z >= region.southWest.z &&
            coords.z < region.northEast.z
}

/** Why a player stopped being part of a raid. */
enum class TobExit {
    Resigned,
    Failed,
    Completed,
    EscapeCrystal,
    LoggedOut,

    /** Something else moved them out of the theatre. */
    Left,
}

/** What came of a player asking to go into the theatre from Ver Sinhaza. */
enum class TobEntry {
    Entered,
    NoParty,
    LeaderFirst,
    NotInRaid,
    AlreadyInside,
    RewardWaiting,
    NoSpace,
}

object TobEvents {
    /** [player] walked into [room] (the room is built the first time). */
    data class RoomEntered(val raid: TobRaid, val room: TobRoom, val player: Player) : UnboundEvent

    /** The leader started [room]'s fight. */
    data class FightStarted(val raid: TobRaid, val room: TobRoom) : UnboundEvent

    data class RoomCompleted(val raid: TobRaid, val room: TobRoom) : UnboundEvent

    /** Verzik fell: the loot is rolled and the treasure room opens. */
    data class RaidWon(val raid: TobRaid) : UnboundEvent

    data class RaidEnded(val raid: TobRaid) : UnboundEvent

    data class PlayerLeft(val raid: TobRaid, val player: Player) : UnboundEvent
}
