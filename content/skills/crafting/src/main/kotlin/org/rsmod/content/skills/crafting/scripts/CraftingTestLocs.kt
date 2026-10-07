package org.rsmod.content.skills.crafting.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Development helper: when the server starts, puts a furnace, spinning wheel, loom, potter's wheel,
 * pottery oven and a tanner (Ellis) on open ground just outside the Soul Wars lobby, so every
 * crafting method can be tried without a trip across the map. Set [ENABLED] to `false` (or delete
 * this file) to remove them.
 *
 * Each one goes on the free spot closest to [ANCHOR] (judged from the loaded collision map) that is
 * clear of the lobby's fenced graveyards and of the ones placed before it.
 */
class CraftingTestLocs
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val npcTypes: NpcTypeList,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val logger = InlineLogger()

    /** Tiles taken by what has been placed so far, with a one tile margin around each. */
    private val used = HashSet<Long>()

    override fun ScriptContext.startup() {
        if (!ENABLED) {
            return
        }
        onGameStartup { spawn() }
    }

    private fun spawn() {
        for (internal in LOCS) {
            val type = locTypes.values.firstOrNull { it.internalName == internal }
            if (type == null) {
                logger.warn { "Crafting test locs: could not find $internal." }
                continue
            }
            placeLoc(type)
        }
        placeTanner()
    }

    private fun placeLoc(type: UnpackedLocType) {
        val turned = ANGLE == LocAngle.North || ANGLE == LocAngle.South
        val width = if (turned) type.length else type.width
        val length = if (turned) type.width else type.length
        val coords = findFree(width, length)
        if (coords == null) {
            logger.warn { "Crafting test locs: no free space for ${type.internalName}." }
            return
        }
        reserve(coords, width, length)
        locRepo.add(coords, type, Int.MAX_VALUE, ANGLE, LocShape.CentrepieceStraight)
        logger.info { "Crafting test loc: ${type.internalName} at ${coords.x}, ${coords.z}" }
    }

    private fun placeTanner() {
        val type = npcTypes.values.firstOrNull { it.internalName == TANNER }
        if (type == null) {
            logger.warn { "Crafting test locs: could not find npc $TANNER." }
            return
        }
        val coords = findFree(1, 1)
        if (coords == null) {
            logger.warn { "Crafting test locs: no free space for $TANNER." }
            return
        }
        reserve(coords, 1, 1)
        val npc = Npc(type, coords)
        npc.noneMode()
        npcRepo.add(npc, Int.MAX_VALUE)
        logger.info { "Crafting test npc: $TANNER at ${coords.x}, ${coords.z}" }
    }

    private fun findFree(width: Int, length: Int): CoordGrid? {
        val candidates = ArrayList<CoordGrid>()
        for (x in ANCHOR.x - RADIUS..ANCHOR.x + RADIUS) {
            for (z in ANCHOR.z - RADIUS..ANCHOR.z + RADIUS) {
                candidates += CoordGrid(x, z, ANCHOR.level)
            }
        }
        candidates.sortBy { abs(it.x - ANCHOR.x) + abs(it.z - ANCHOR.z) }
        return candidates.firstOrNull { isFree(it, width, length) }
    }

    private fun isFree(origin: CoordGrid, width: Int, length: Int): Boolean {
        for (dx in 0 until width) {
            for (dz in 0 until length) {
                val x = origin.x + dx
                val z = origin.z + dz
                if (x in LOBBY_X && z in LOBBY_Z) {
                    return false
                }
                if (key(x, z) in used) {
                    return false
                }
                if ((collision[x, z, origin.level] and BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    private fun reserve(origin: CoordGrid, width: Int, length: Int) {
        for (dx in -1..width) {
            for (dz in -1..length) {
                used += key(origin.x + dx, origin.z + dz)
            }
        }
    }

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    companion object {
        /** Set to `false` to stop spawning the test locs and the tanner. */
        const val ENABLED = true

        /** Internal names of the locs to place, in order of how close to the anchor they go. */
        private val LOCS =
            listOf("fai_falador_furnace", "spinningwheel", "loom", "potterywheel", "potteryoven")
        private const val TANNER = "ellis_tanner"

        private val ANGLE = LocAngle.South

        /** The middle of the Soul Wars lobby's graveyards. */
        private val ANCHOR = CoordGrid(2210, 2850, 0)
        private const val RADIUS = 18

        /** The lobby's fenced graveyards, which stay clear. */
        private val LOBBY_X = 2202..2218
        private val LOBBY_Z = 2844..2856

        private val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
    }
}
