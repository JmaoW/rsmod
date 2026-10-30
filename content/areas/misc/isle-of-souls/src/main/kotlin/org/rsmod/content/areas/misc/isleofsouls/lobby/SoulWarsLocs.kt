package org.rsmod.content.areas.misc.isleofsouls.lobby

import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

/** The Soul Wars objects the isle uses: the team portals and every barrier a player passes. */
object SoulWarsLocs : LocReferences() {
    val blue_exit_portal = find("soul_wars_blue_exit_portal")
    val red_exit_portal = find("soul_wars_red_exit_portal")

    // The lobby's two waiting areas.
    val main_waiting_area_barrier = find("soul_wars_main_waiting_area_barrier")
    val clan_waiting_area_barrier = find("soul_wars_clan_waiting_area_barrier")

    // The arena's graveyard and capture-point barriers.
    val blue_eastern_barrier = find("soul_wars_blue_eastern_barrier")
    val blue_western_barrier = find("soul_wars_blue_western_barrier")
    val blue_graveyard_barrier = find("soul_wars_blue_graveyard_barrier")
    val red_eastern_barrier = find("soul_wars_red_eastern_barrier")
    val red_western_barrier = find("soul_wars_red_western_barrier")
    val red_graveyard_barrier = find("soul_wars_red_graveyard_barrier")
    val neutral_eastern_barrier = find("soul_wars_neutral_eastern_barrier")
    val neutral_western_barrier = find("soul_wars_neutral_western_barrier")

    val barriers: List<LocType> =
        listOf(
            main_waiting_area_barrier,
            clan_waiting_area_barrier,
            blue_eastern_barrier,
            blue_western_barrier,
            blue_graveyard_barrier,
            red_eastern_barrier,
            red_western_barrier,
            red_graveyard_barrier,
            neutral_eastern_barrier,
            neutral_western_barrier,
        )
}

/**
 * One team's pair of portals. The lobby's waiting area holds [lobbyPortal] and the arena's team
 * graveyard holds [arenaPortal] (already part of the cache's map); using either takes the player to
 * the tile in front of the other, [arenaArrival] or [lobbyArrival].
 *
 * The portals are three tiles long. Turned to face east or west (`LocAngle.North`, as the arena's
 * own are), a portal's coordinates are its southernmost tile and it covers the two tiles north.
 */
class SoulWarsPortal(
    val name: String,
    val type: LocType,
    val lobbyPortal: CoordGrid,
    val lobbyArrival: CoordGrid,
    val arenaPortal: CoordGrid,
    val arenaArrival: CoordGrid,
) {
    val shape = LocShape.CentrepieceStraight
    val angle = LocAngle.North
}

object SoulWarsPortals {
    /**
     * The blue waiting area is the west one (vine rails, the "main" barrier) and the red one the
     * east (bone rails, the "clan" barrier). Coordinates are read from the cache: the arena's
     * portals stand on the inner wall of each team's graveyard (blue at x=2143, z=2904-2906; red at
     * x=2271, z=2918-2920) and the tile beside the middle of each is open floor.
     */
    val blue =
        SoulWarsPortal(
            name = "blue",
            type = SoulWarsLocs.blue_exit_portal,
            lobbyPortal = CoordGrid(2192, 2841, 0),
            lobbyArrival = CoordGrid(2193, 2842, 0),
            arenaPortal = CoordGrid(2143, 2904, 0),
            arenaArrival = CoordGrid(2142, 2905, 0),
        )

    val red =
        SoulWarsPortal(
            name = "red",
            type = SoulWarsLocs.red_exit_portal,
            lobbyPortal = CoordGrid(2227, 2841, 0),
            lobbyArrival = CoordGrid(2226, 2842, 0),
            arenaPortal = CoordGrid(2271, 2918, 0),
            arenaArrival = CoordGrid(2272, 2919, 0),
        )

    val all: List<SoulWarsPortal> = listOf(blue, red)
}
