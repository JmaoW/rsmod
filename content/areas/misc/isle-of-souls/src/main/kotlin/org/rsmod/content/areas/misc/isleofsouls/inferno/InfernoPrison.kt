package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocEntity
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.loc.LocLayerConstants

/** A prison wall that is crumbling and becomes [rubble] at cycle [at]. */
class Crumble(val loc: LocInfo, val rubble: LocType, val at: Int, val turn: Int = 0)

/**
 * Zuk's prison in the north wall. The map holds it closed: a glyph set into the wall, two walls
 * either side of it and a roof on the level above. When Zuk breaks out the glyph goes, the corner
 * pieces open, the side walls crack (playing the cache's collapse animations) and then turn to
 * rubble, and the roof is hidden, so that Zuk and the glyph in front of him stand in the open.
 *
 * Every piece is one of the cache's own locs, each state a different loc type: the map's pieces are
 * swapped for the next state in this run's copy of the arena, and the roof is a multiloc that hides
 * itself from a varbit that each player in the run is sent (see [showTo]).
 */
class InfernoPrison
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val worldRepo: WorldRepository,
    private val seqTypes: SeqTypeList,
) {
    private class Piece(
        val at: CoordGrid,
        val closed: LocType,
        val open: LocType,
        val rubble: LocType? = null,
        val crack: SeqType? = null,
        /** Quarter turns the open piece is turned from the closed one it replaces. */
        val openTurn: Int = 0,
        /**
         * Quarter turns the rubble is turned from the wall it replaces (the cache has one rubble
         * loc for both sides).
         */
        val turn: Int = 0,
    )

    /** Tells [player] whether the prison roof is on, which it is until Zuk has broken out. */
    fun showTo(player: Player, run: InfernoRun) {
        val hidden = if (run.zukFight != null) 1 else 0
        VarPlayerIntMapSetter.set(player, InfernoVarBits.prison_roof_hidden, hidden)
    }

    /**
     * Breaks Zuk's prison open in [run]'s arena at cycle [now]. Returns the walls that are
     * crumbling and still have to be turned into rubble (see [settle]).
     */
    fun open(run: InfernoRun, now: Int): List<Crumble> {
        val region = run.instance.region.normal
        locRepo.del(
            mapLoc(region[InfernoArena.prisonGlyph], locTypes[InfernoPrisonLocs.glyph_closed]),
            Int.MAX_VALUE,
        )
        val crumbles = ArrayList<Crumble>()
        for (piece in pieces) {
            val at = region[piece.at]
            locRepo.del(mapLoc(at, piece.closed), Int.MAX_VALUE)
            val angle = LocAngle[(MAP_ANGLE.id + piece.openTurn) and 3]
            val open = locRepo.add(at, piece.open, Int.MAX_VALUE, angle, MAP_SHAPE)
            val crack = piece.crack
            val rubble = piece.rubble
            if (crack != null && rubble != null) {
                worldRepo.locAnim(open, crack)
                val ticks = maxOf(MIN_CRACK_TICKS, seqTypes[crack].tickDuration)
                crumbles += Crumble(open, rubble, now + ticks, piece.turn)
            }
        }
        return crumbles
    }

    /** Turns the walls whose crack animation has finished into rubble. Returns what is left. */
    fun settle(crumbles: List<Crumble>, now: Int): List<Crumble> {
        val left = ArrayList<Crumble>()
        for (crumble in crumbles) {
            if (now < crumble.at) {
                left += crumble
                continue
            }
            val loc = crumble.loc
            locRepo.del(loc, Int.MAX_VALUE)
            val angle = LocAngle[(loc.angle.id + crumble.turn) and 3]
            locRepo.add(loc.coords, crumble.rubble, Int.MAX_VALUE, angle, loc.shape)
        }
        return left
    }

    /**
     * The loc the map file has at [coords]: every piece of the prison is a centrepiece with
     * rotation 3 in it (read from the map). Deleting it needs the exact type, shape and angle.
     */
    private fun mapLoc(coords: CoordGrid, type: LocType): LocInfo =
        LocInfo(
            LocLayerConstants.of(MAP_SHAPE.id),
            coords,
            LocEntity(type.id, MAP_SHAPE.id, MAP_ANGLE.id),
        )

    private val pieces: List<Piece> by lazy {
        listOf(
            Piece(
                InfernoArena.prisonSideRight,
                locTypes[InfernoPrisonLocs.side_right_closed],
                locTypes[InfernoPrisonLocs.side_right_cracking],
                locTypes[InfernoPrisonLocs.side_right_rubble],
                inferno_seqs.wall_collapse_right,
            ),
            Piece(
                InfernoArena.prisonSideLeft,
                locTypes[InfernoPrisonLocs.side_left_closed],
                locTypes[InfernoPrisonLocs.side_left_cracking],
                locTypes[InfernoPrisonLocs.side_left_rubble],
                inferno_seqs.wall_collapse_left,
                turn = LEFT_RUBBLE_TURN,
            ),
            Piece(
                InfernoArena.prisonCornerRight,
                locTypes[InfernoPrisonLocs.corner_right_closed],
                locTypes[InfernoPrisonLocs.corner_right_open],
            ),
            Piece(
                InfernoArena.prisonCornerLeft,
                locTypes[InfernoPrisonLocs.corner_left_closed],
                locTypes[InfernoPrisonLocs.corner_left_open],
                openTurn = LEFT_CORNER_TURN,
            ),
            Piece(
                InfernoArena.prisonJoinLeft,
                locTypes[InfernoPrisonLocs.join_left_static],
                locTypes[InfernoPrisonLocs.join_left_open],
                openTurn = LEFT_JOIN_TURN,
            ),
            Piece(
                InfernoArena.prisonJoinRight,
                locTypes[InfernoPrisonLocs.join_right_closed],
                locTypes[InfernoPrisonLocs.join_right_open],
            ),
        )
    }

    companion object {
        private val MAP_SHAPE = LocShape.CentrepieceStraight
        private val MAP_ANGLE = LocAngle.South

        /**
         * Both side walls become the same rubble loc, which looked wrong on the left side as it
         * came (turned the same way as the wall): a half turn is a guess at the right one.
         */
        const val LEFT_RUBBLE_TURN = 2

        /**
         * The open left join is the east one's model mirrored, and with the map's turn it would
         * reach into the cell instead of outwards behind Zuk's side (worked out from the models'
         * measurements: with a half turn it lies exactly where the east one's mirror image does).
         */
        const val LEFT_JOIN_TURN = 2

        /**
         * The open left floor corner measured as a near tie between a half turn and none (about 15
         * units either way), and the gap that was left at the foot of the wall points at this
         * piece: a half turn is a guess. Set it back to 0 if the gap just moves.
         */
        const val LEFT_CORNER_TURN = 2

        /** A cracking wall lasts at least this many ticks, whatever its animation says. */
        const val MIN_CRACK_TICKS = 4
    }
}
