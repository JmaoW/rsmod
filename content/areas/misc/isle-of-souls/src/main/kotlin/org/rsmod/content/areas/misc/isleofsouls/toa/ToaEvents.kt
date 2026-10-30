package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The Jaltevas pyramid lobby, and the way in and out of it. The lobby is a real part of the map.
 */
object ToaLobby {
    const val MIN_X = 3340
    const val MAX_X = 3377
    const val MIN_Z = 9100
    const val MAX_Z = 9132

    /** Where a player arrives from outside, beside the lobby's exit. */
    val ENTRANCE = CoordGrid(3359, 9128, 0)

    /** Where a player is put after leaving a raid: in front of the raid entry. */
    val RETURN = CoordGrid(3358, 9113, 0)

    fun contains(coords: CoordGrid): Boolean =
        coords.level == 0 && coords.x in MIN_X..MAX_X && coords.z in MIN_Z..MAX_Z
}

/** What happens inside a raid that room scripts react to. */
class ToaEvents {
    /**
     * The first player of [room] arrived; its challenge can start once the room's content wants.
     */
    data class RoomEntered(val raid: ToaRaid, val room: ToaRoom, val player: Player) : UnboundEvent

    /** Every member of a challenge died; the room is to be put back as it was at the start. */
    data class RoomReset(val raid: ToaRaid, val room: ToaRoom) : UnboundEvent

    /** A challenge room was won (the dead come back); [raid] already counts it. */
    data class RoomCompleted(val raid: ToaRaid, val room: ToaRoom) : UnboundEvent

    /** A path was cleared. */
    data class PathCompleted(val raid: ToaRaid, val path: ToaPath) : UnboundEvent

    /** The Wardens fell: the raid is won and the vault opens. */
    data class RaidWon(val raid: ToaRaid) : UnboundEvent

    /** [player] stopped being part of [raid] (left, failed, logged out or was moved out). */
    data class PlayerLeft(val raid: ToaRaid, val player: Player) : UnboundEvent

    /** The raid is over (won, failed or abandoned) and its region is no longer needed. */
    data class RaidEnded(val raid: ToaRaid) : UnboundEvent
}
