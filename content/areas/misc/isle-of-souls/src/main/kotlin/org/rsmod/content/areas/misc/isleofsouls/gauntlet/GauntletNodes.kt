package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.routefinder.util.Rotations

/**
 * The nodes in the corridors of the maze. Every side of a room has two nodes ("Light") and two
 * symbols; lighting a node draws the room on the other side of that side, and the nodes and symbols
 * of that passage, on both sides of it, change to their lit versions (wiki: nodes are lit "to visit
 * new rooms").
 *
 * The nodes are map locs in the room templates, so the only way to find one in a run's maze is to
 * ask which locs the room's zones hold now. A room can be turned, so the side a node is on is
 * worked out from where it stands in the turned room, not read from the template.
 */
class GauntletNodes
@Inject
constructor(private val locRepo: LocRepository, private val locTypes: LocTypeList) {
    /** What an unlit node or symbol of [mode] turns into once its passage is lit. */
    private fun litVersions(mode: GauntletMode): Map<Int, LocType> =
        if (mode.corrupted) {
            mapOf(
                locTypes[gauntlet_locs.node_a_hm].id to locTypes[gauntlet_locs.node_a_lit_hm],
                locTypes[gauntlet_locs.node_b_hm].id to locTypes[gauntlet_locs.node_b_lit_hm],
                locTypes[gauntlet_locs.symbol_hm].id to locTypes[gauntlet_locs.symbol_lit_hm],
            )
        } else {
            mapOf(
                locTypes[gauntlet_locs.node_a].id to locTypes[gauntlet_locs.node_a_lit],
                locTypes[gauntlet_locs.node_b].id to locTypes[gauntlet_locs.node_b_lit],
                locTypes[gauntlet_locs.symbol].id to locTypes[gauntlet_locs.symbol_lit],
            )
        }

    /**
     * The side of its room that a loc of [type] at [at] stands on: the side nearest the middle of
     * its footprint. [angle] is the loc's angle id.
     */
    fun sideOf(
        run: GauntletRun,
        room: GauntletRoom,
        at: CoordGrid,
        type: UnpackedLocType,
        angle: Int,
    ): Side {
        val origin = run.roomTile(room, 0, 0)
        val width = Rotations.rotate(angle, type.width, type.length)
        val length = Rotations.rotate(angle, type.length, type.width)
        val x = at.x - origin.x + (width - 1) / 2.0
        val z = at.z - origin.z + (length - 1) / 2.0
        val last = (GauntletGrid.ROOM_TILES - 1).toDouble()
        val distance =
            mapOf(Side.North to last - z, Side.East to last - x, Side.South to z, Side.West to x)
        return Side.entries.minBy { distance.getValue(it) }
    }

    /**
     * Lights every passage between [room] and a lit neighbour: the nodes and symbols on [room]'s
     * side of it, and on the neighbour's. Called when a room has just been lit, so passages that
     * were already lit are simply passed over.
     */
    fun lightPassages(run: GauntletRun, room: GauntletRoom) {
        for (side in Side.entries) {
            val next = run.layout.room(room.x + side.dx, room.z + side.dz) ?: continue
            if (!next.lit) {
                continue
            }
            light(run, room, side)
            light(run, next, side.opposite)
        }
    }

    /** Changes the unlit nodes and symbols on [side] of [room] to their lit versions. */
    private fun light(run: GauntletRun, room: GauntletRoom, side: Side) {
        val lit = litVersions(run.mode)
        val sw = run.region.southWestZone
        for (dx in 0 until GauntletGrid.ROOM_CHUNKS) {
            for (dz in 0 until GauntletGrid.ROOM_CHUNKS) {
                val zone =
                    ZoneKey(
                        sw.x + room.x * GauntletGrid.ROOM_CHUNKS + dx,
                        sw.z + room.z * GauntletGrid.ROOM_CHUNKS + dz,
                        GauntletGrid.PLAY_LEVEL,
                    )
                for (found in locRepo.findAll(zone).toList()) {
                    val into = lit[found.id] ?: continue
                    val loc = turned(found, room)
                    if (sideOf(run, room, loc, locTypes.getValue(loc.id)) == side) {
                        locRepo.change(loc, into, Int.MAX_VALUE)
                    }
                }
            }
        }
    }

    /**
     * `findAll` hands back a room's static locs with the angle they have in the template, but the
     * room is turned: the loc stands (and must be redrawn) at its angle plus the room's rotation.
     */
    private fun turned(loc: LocInfo, room: GauntletRoom): LocInfo {
        val angle = (loc.angleId + room.rotation) and ANGLE_MASK
        return loc.copy(entity = loc.entity.copy(angle = angle))
    }

    private fun sideOf(
        run: GauntletRun,
        room: GauntletRoom,
        loc: LocInfo,
        type: UnpackedLocType,
    ): Side = sideOf(run, room, loc.coords, type, loc.angleId)
}

private const val ANGLE_MASK = 3
