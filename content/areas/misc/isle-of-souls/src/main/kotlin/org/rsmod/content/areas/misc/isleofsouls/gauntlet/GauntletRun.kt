package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.region.Region
import org.rsmod.map.CoordGrid

enum class GauntletStage {
    /** Gathering and crafting; the timer is counting down. */
    Prep,

    /** The Hunllef is awake. */
    Boss,

    /** Nobody is left in the maze. */
    Ended,
}

/** Why a player stopped taking part in a run. */
enum class GauntletExit {
    /** The start room's platform. */
    Exit,

    /** The barrier's Escape option during the fight. */
    Escape,

    /** Killed in the maze. */
    Died,

    /** Moved out of the maze by something else (a teleport). */
    Left,

    /** Logged out inside. */
    Logout,

    /** The run was won and the team is taken to the lobby. */
    Completed,
}

/**
 * One team's attempt at one version of the Gauntlet: a maze (a [Region] that gains its rooms as
 * they are lit), the plan of that maze, the clock, and who is taking part. Everything here runs on
 * the game thread.
 *
 * A player who dies or leaves cannot come back to this maze ([barred]); the run ends when nobody is
 * left in it.
 */
class GauntletRun(
    val owner: String,
    val mode: GauntletMode,
    val region: Region,
    val layout: GauntletLayout,
    val startCycle: Int,
) {
    /** Players inside the maze right now. */
    val members: MutableList<Player> = ArrayList()

    /** Account ids that went in and are out again for good. */
    val barred: MutableSet<Int> = HashSet()

    var stage: GauntletStage = GauntletStage.Prep

    /** Ticks of preparation left. */
    var prepLeft: Int = mode.prepTicks

    /** When the boss phase began, or `0`. */
    var bossCycle: Int = 0

    /** How much is left in each resource node that has been gathered from, by its coordinates. */
    val nodeLeft: MutableMap<CoordGrid, Int> = HashMap()

    /** The Hunllef, from the moment the run is made. */
    var hunllef: HunllefFight? = null

    /** The Hunllef died: the team is taken out at [endAt]. */
    var won: Boolean = false
    var endAt: Int = 0

    /** The monsters of the maze that are alive. */
    val monsters: MutableList<Npc> = ArrayList()

    /** What each player (by account id) has done, for the drops that depend on it. */
    private val states: MutableMap<Int, GauntletPlayerState> = HashMap()

    fun state(player: Player): GauntletPlayerState =
        states.getOrPut(player.accountId) { GauntletPlayerState() }

    /** Points earned by each player (by account id), for the reward when the run ends. */
    val points: MutableMap<Int, Int> = HashMap()

    fun addPoints(player: Player, amount: Int) {
        if (stage != GauntletStage.Ended) {
            points.merge(player.accountId, amount, Int::plus)
        }
    }

    /** A tile of [room], counted from its south-west corner, on [level]. */
    fun roomTile(
        room: GauntletRoom,
        offsetX: Int,
        offsetZ: Int,
        level: Int = GauntletGrid.PLAY_LEVEL,
    ): CoordGrid =
        CoordGrid(
            region.southWest.x + room.x * GauntletGrid.ROOM_TILES + offsetX,
            region.southWest.z + room.z * GauntletGrid.ROOM_TILES + offsetZ,
            level,
        )

    /** The room a tile is in, or `null` outside the maze. */
    fun roomAt(coords: CoordGrid): GauntletRoom? {
        if (!contains(coords)) {
            return null
        }
        val x = (coords.x - region.southWest.x) / GauntletGrid.ROOM_TILES
        val z = (coords.z - region.southWest.z) / GauntletGrid.ROOM_TILES
        return layout.room(x, z)
    }

    fun contains(coords: CoordGrid): Boolean =
        coords.x >= region.southWest.x &&
            coords.x < region.northEast.x &&
            coords.z >= region.southWest.z &&
            coords.z < region.northEast.z

    /** Where a player appears on entering: the middle of the start room. */
    fun startTile(pick: (Int) -> Int): CoordGrid =
        roomTile(layout.start, START_OFFSET + pick(2), START_OFFSET + pick(2))

    /** Where a player is put when the Hunllef's room opens. */
    fun arenaTile(index: Int): CoordGrid =
        roomTile(layout.boss, ARENA_START_X + index, ARENA_START_Z)

    companion object {
        /** Five or six tiles in each way from the start room's corner (the open floor). */
        const val START_OFFSET = 5

        /** Inside the Hunllef's arena, clear of its footprint. */
        const val ARENA_START_X = 4
        const val ARENA_START_Z = 4
    }
}
