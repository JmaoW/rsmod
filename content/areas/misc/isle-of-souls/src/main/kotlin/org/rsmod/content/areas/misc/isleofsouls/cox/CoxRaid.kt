package org.rsmod.content.areas.misc.isleofsouls.cox

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

/** One raider: their points, deaths, and where in the raid they are. */
class CoxMember(val player: Player, val slot: Int) {
    /** Was in the raid when the leader started it (only they earn points and loot). */
    var started: Boolean = false

    var points: Int = 0
    var deaths: Int = 0
    var left: Boolean = false

    /** The room the player last stood in. */
    var room: CoxPlacement? = null

    /** Damage dealt per room, for the room's point cap. */
    val damageIn: MutableMap<Int, Int> = HashMap()

    /** Points given for food and potions they made (the caps). */
    var foodPoints: Int = 0
    var potionPoints: Int = 0
}

/** The raid's progress as the side panel tells it (varbit `raids_client_progress`). */
enum class CoxStage(val value: Int) {
    Waiting(0),
    Upper(1),
    Middle(2),
    Lower(3),
    Olm(4),
    Done(5),
}

/**
 * One party's raid: its region, rooms, raiders, points and how far it has got. Created by
 * [CoxRaids] the first time a party member walks in; everything runs on the game thread.
 */
class CoxRaid(
    val id: Int,
    val region: Region,
    val layout: CoxLayout,
    val party: CoxParty,
    val createdCycle: Int,
) {
    val challenge: Boolean
        get() = layout.challenge

    val members: MutableList<CoxMember> = ArrayList()

    val active: List<CoxMember>
        get() = members.filter { !it.left }

    /** The raiders who were there when it began (points, scaling and loot go by them). */
    val raiders: List<CoxMember>
        get() = members.filter { it.started }

    var started: Boolean = false
    var startCycle: Int = -1
    var stage: CoxStage = CoxStage.Waiting

    /** When each level was reached (for the "level complete" messages). */
    var stageCycle: Int = -1

    /** The rooms whose way on is open. */
    val opened: MutableSet<Int> = HashSet()

    /** Points that belong to nobody any more (a raider who left). The team total keeps them. */
    var droppedPoints: Int = 0

    var olmDefeated: Boolean = false
    var completed: Boolean = false
    var completeCycle: Int = -1
    var ended: Boolean = false

    /** The party's figures the monsters are scaled by, fixed when the raid starts. */
    var scaling: CoxScalingInputs = CoxScalingInputs()

    val totalPoints: Int
        get() = members.sumOf { it.points } + droppedPoints

    val southWest: CoordGrid
        get() = region.southWest

    fun member(player: Player): CoxMember? = members.firstOrNull { it.player === player }

    fun leader(): Player? = party.leader

    fun isLeader(player: Player): Boolean = party.leader === player

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.x < region.northEast.x &&
            coords.z >= region.southWest.z &&
            coords.z < region.northEast.z

    fun roomAt(coords: CoordGrid): CoxPlacement? =
        if (contains(coords)) CoxMap.roomAt(layout, coords, region.southWest) else null

    fun inOlmChamber(coords: CoordGrid): Boolean =
        contains(coords) && CoxMap.isOlmChamber(coords, region.southWest)

    fun regionOf(room: CoxPlacement, template: CoordGrid, size: Int = 1): CoordGrid =
        CoxMap.toRegion(room, template, region.southWest, size)

    fun templateOf(room: CoxPlacement, coords: CoordGrid): CoordGrid =
        CoxMap.toTemplate(room, coords, region.southWest)

    fun olmTile(real: CoordGrid): CoordGrid = CoxMap.olmToRegion(real, region.southWest)

    fun olmReal(coords: CoordGrid): CoordGrid = CoxMap.olmToReal(coords, region.southWest)

    val rooms: List<CoxPlacement>
        get() = layout.rooms
}

/** Why a raider stopped being part of a raid. */
enum class CoxExit {
    Steps,
    Completed,
    LoggedOut,
    Disbanded,

    /** Something else moved them out (a teleport). */
    Left,
}

enum class CoxEntry {
    Entered,
    NoParty,
    LeaderFirst,
    AlreadyStarted,
    NoSpace,
}

object CoxEvents {
    /** The raid's map is built and its rooms can be set up. */
    data class RaidBuilt(val raid: CoxRaid) : UnboundEvent

    /** The leader started the raid. */
    data class RaidStarted(val raid: CoxRaid) : UnboundEvent

    /** [player] walked into [room] for the first time (the room is set up then if not before). */
    data class RoomEntered(val raid: CoxRaid, val room: CoxPlacement, val player: Player) :
        UnboundEvent

    data class RoomCompleted(val raid: CoxRaid, val room: CoxPlacement) : UnboundEvent

    /** The Great Olm fell: the loot is rolled and the chest appears. */
    data class OlmDefeated(val raid: CoxRaid) : UnboundEvent

    data class RaidEnded(val raid: CoxRaid) : UnboundEvent

    data class PlayerLeft(val raid: CoxRaid, val player: Player) : UnboundEvent
}
