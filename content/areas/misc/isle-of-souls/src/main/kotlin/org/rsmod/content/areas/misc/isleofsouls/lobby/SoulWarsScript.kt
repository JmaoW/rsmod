package org.rsmod.content.areas.misc.isleofsouls.lobby

import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Soul Wars barriers and portals.
 * - **Barriers** (the lobby's two waiting areas and every barrier in the arena) are open to anyone:
 *   "Pass" slides the player across the wall to the tile on its other side. The game's team and
 *   capture rules are not used.
 * - **Portals** link each waiting area to the matching team's graveyard in the arena, and the arena
 *   portal ("Leave") leads back to the waiting area. The lobby portals are placed at startup; the
 *   arena ones are already in the map, so a portal is told apart by where it stands.
 */
class SoulWarsScript @Inject constructor(private val locRepo: LocRepository) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placePortals() }

        for (barrier in SoulWarsLocs.barriers) {
            onOpLoc1(barrier) { pass(it.loc) }
        }
        for (portal in SoulWarsPortals.all) {
            onOpLoc1(portal.type) { use(portal, it.loc) }
        }
    }

    /** Puts each team's portal in its waiting area. */
    fun placePortals() {
        for (portal in SoulWarsPortals.all) {
            locRepo.add(
                portal.lobbyPortal,
                portal.type,
                duration = Int.MAX_VALUE,
                angle = portal.angle,
                shape = portal.shape,
            )
        }
    }

    private suspend fun ProtectedAccess.use(portal: SoulWarsPortal, loc: BoundLocInfo) {
        arriveDelay()
        val dest =
            when (loc.coords) {
                portal.lobbyPortal -> portal.arenaArrival
                portal.arenaPortal -> portal.lobbyArrival
                else -> {
                    mes("Nothing interesting happens.")
                    return
                }
            }
        mes("You step through the portal.")
        delay(2)
        telejump(dest)
    }

    private suspend fun ProtectedAccess.pass(loc: BoundLocInfo) {
        arriveDelay()
        val (a, b) = sidesOf(loc)
        // Whichever side the player stands nearer is the one they leave.
        val dest = if (coords.chebyshevDistance(a) <= coords.chebyshevDistance(b)) b else a
        mes("You pass through the barrier.")
        exactMove(coords, dest, 0, EXACT_MOVE_CYCLES, directionOf(coords, dest))
        delay(1)
    }

    /** The two tiles either side of the wall [loc] stands on. */
    private fun sidesOf(loc: BoundLocInfo): Pair<CoordGrid, CoordGrid> {
        val at = loc.coords
        return when (loc.angle) {
            LocAngle.West -> at.translate(-1, 0) to at
            LocAngle.North -> at to at.translate(0, 1)
            LocAngle.East -> at to at.translate(1, 0)
            LocAngle.South -> at.translate(0, -1) to at
        }
    }

    private fun directionOf(from: CoordGrid, to: CoordGrid): Int =
        when {
            to.x > from.x -> constants.em_face_east
            to.x < from.x -> constants.em_face_west
            to.z > from.z -> constants.em_face_north
            else -> constants.em_face_south
        }

    private companion object {
        /** A tick is 30 client cycles; the slide takes a little under one. */
        const val EXACT_MOVE_CYCLES = 25
    }
}
