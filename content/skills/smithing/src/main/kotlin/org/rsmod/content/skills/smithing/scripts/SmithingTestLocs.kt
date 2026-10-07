package org.rsmod.content.skills.smithing.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Development helper: puts a furnace and an anvil inside the ruined building next to the island's
 * fishing spots when the server starts, so smithing can be tried without a trip across the map. Set
 * [ENABLED] to `false` (or delete this file) to remove them.
 *
 * Each one goes on its preferred tile if the loaded collision map says the whole footprint is free,
 * and otherwise on the free spot inside the building that is closest to it.
 */
class SmithingTestLocs
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val locRepo: LocRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        if (!ENABLED) {
            return
        }
        onGameStartup { spawn() }
    }

    private fun spawn() {
        val furnace = locTypes.values.firstOrNull { it.internalName == FURNACE }
        val anvil = locTypes.values.firstOrNull { it.internalName == ANVIL }
        if (furnace == null || anvil == null) {
            logger.warn { "Smithing test locs: could not find $FURNACE or $ANVIL." }
            return
        }
        place(furnace, FURNACE_TILE)
        place(anvil, ANVIL_TILE)
    }

    private fun place(type: UnpackedLocType, preferred: CoordGrid) {
        val turned = ANGLE == LocAngle.North || ANGLE == LocAngle.South
        val width = if (turned) type.length else type.width
        val length = if (turned) type.width else type.length
        val coords = findFree(preferred, width, length)
        if (coords == null) {
            logger.warn { "Smithing test locs: no free space for ${type.internalName}." }
            return
        }
        locRepo.add(coords, type, Int.MAX_VALUE, ANGLE, LocShape.CentrepieceStraight)
        logger.info { "Smithing test loc: ${type.internalName} at ${coords.x}, ${coords.z}" }
    }

    private fun findFree(preferred: CoordGrid, width: Int, length: Int): CoordGrid? {
        val candidates = ArrayList<CoordGrid>()
        for (x in MIN_X..MAX_X) {
            for (z in MIN_Z..MAX_Z) {
                candidates += CoordGrid(x, z, preferred.level)
            }
        }
        candidates.sortBy { abs(it.x - preferred.x) + abs(it.z - preferred.z) }
        return candidates.firstOrNull { isFree(it, width, length) }
    }

    private fun isFree(origin: CoordGrid, width: Int, length: Int): Boolean {
        for (dx in 0 until width) {
            for (dz in 0 until length) {
                val x = origin.x + dx
                val z = origin.z + dz
                if (x !in MIN_X..MAX_X || z !in MIN_Z..MAX_Z) {
                    return false
                }
                if (x in DOOR_X && z >= DOOR_MIN_Z) {
                    return false
                }
                if ((collision[x, z, origin.level] and BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    companion object {
        /** Set to `false` to stop spawning the furnace and anvil. */
        const val ENABLED = true

        private const val FURNACE = "fai_falador_furnace"
        private const val ANVIL = "anvil"

        /** Both face north, towards the gap in the building's north wall. */
        private val ANGLE = LocAngle.North

        private val FURNACE_TILE = CoordGrid(2157, 2805, 0)
        private val ANVIL_TILE = CoordGrid(2160, 2806, 0)

        /** The inside of the ruin, from the walls' tiles. */
        private const val MIN_X = 2156
        private const val MAX_X = 2166
        private const val MIN_Z = 2803
        private const val MAX_Z = 2807

        /** The gap in the north wall, which stays clear so players can walk in. */
        private val DOOR_X = 2161..2163
        private const val DOOR_MIN_Z = 2806

        private val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
    }
}
