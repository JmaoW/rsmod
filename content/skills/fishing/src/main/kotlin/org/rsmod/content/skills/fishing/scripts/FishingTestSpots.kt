package org.rsmod.content.skills.fishing.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.content.skills.fishing.configs.FishingNpcs
import org.rsmod.content.skills.fishing.configs.FishingSpots
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Development helper: spawns one fishing spot of every supported kind in the water next to the
 * [ANCHOR] tile when the server starts, so each fishing method can be tried without hunting for
 * spots in the world. Set [ENABLED] to `false` (or delete this file) to remove them.
 *
 * The tiles are found from the loaded collision map: water tiles that touch walkable land, picked
 * closest to the anchor first and kept at least two tiles apart so a player can stand between them.
 */
class FishingTestSpots
@Inject
constructor(
    private val npcTypes: NpcTypeList,
    private val npcRepo: NpcRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        if (!ENABLED) {
            return
        }
        onGameStartup { spawnSpots() }
    }

    private fun spawnSpots() {
        val types = spotTypes()
        val tiles =
            findShoreTiles(
                flagsAt = { x, z -> collision[x, z, ANCHOR.level] },
                anchorX = ANCHOR.x,
                anchorZ = ANCHOR.z,
                count = types.size,
            )
        if (tiles.size < types.size) {
            logger.warn {
                "Fishing test spots: only found ${tiles.size} shore tiles for ${types.size} " +
                    "spots near $ANCHOR."
            }
        }

        for ((type, tile) in types.zip(tiles)) {
            val coords = CoordGrid(tile.first, tile.second, ANCHOR.level)
            val npc = Npc(type, coords)
            npc.noneMode()
            npcRepo.add(npc, Int.MAX_VALUE)
            logger.info { "Fishing test spot: ${describe(type)} at ${coords.x}, ${coords.z}" }
        }
    }

    /** One npc type for each distinct pair of fishing methods, plus the karambwan spots. */
    private fun spotTypes(): List<UnpackedNpcType> {
        val seen = HashSet<Any>()
        val types = ArrayList<UnpackedNpcType>()
        for (type in npcTypes.values.sortedBy { it.id }) {
            if (!type.name.endsWith("Fishing spot", ignoreCase = true)) {
                continue
            }
            // Only the standard overworld spots. Others (cavefish, eels, and so on) share the same
            // options but catch different fish.
            val internal = type.internalName ?: continue
            if (OVERWORLD_SPOTS.none { internal.endsWith(it) }) {
                continue
            }
            // The shark spot also exists labelled "Net" / "Harpoon". Only spawn the "Big Net" one.
            if (type.op.getOrNull(0) == "Net" && type.op.getOrNull(2) == "Harpoon") {
                continue
            }
            val methods = FishingSpots.fromOps(type.op.getOrNull(0), type.op.getOrNull(2))
            if (methods != null && seen.add(methods)) {
                types += type
            }
        }
        types += npcTypes.getValue(FishingNpcs.monkfish_spots.first().id)
        types += npcTypes.getValue(FishingNpcs.karambwanji_spots.first().id)
        types += npcTypes.getValue(FishingNpcs.karambwan_spots.first().id)
        return types
    }

    private fun describe(type: UnpackedNpcType): String =
        "${type.name} (${type.op.take(3).filterNotNull().joinToString("/")})"

    companion object {
        /** Set to `false` to stop spawning the test spots. */
        const val ENABLED = true

        /** Just off the shore where the spots are placed. Level is the third coordinate. */
        val ANCHOR = CoordGrid(0, 33, 43, 50, 31)

        private val OVERWORLD_SPOTS = listOf("saltfish", "freshfish", "rarefish", "memberfish")

        private const val SEARCH_RADIUS = 14
        private const val MIN_SPACING = 2

        private val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /**
         * Finds up to [count] water tiles that touch walkable land, closest to the anchor first and
         * at least [MIN_SPACING] tiles apart, returned from west to east.
         *
         * Water is any tile with [CollisionFlag.BLOCK_WALK] and no loc on it.
         */
        internal fun findShoreTiles(
            flagsAt: (Int, Int) -> Int,
            anchorX: Int,
            anchorZ: Int,
            count: Int,
        ): List<Pair<Int, Int>> {
            fun isWater(x: Int, z: Int): Boolean {
                val flags = flagsAt(x, z)
                return (flags and CollisionFlag.BLOCK_WALK) != 0 &&
                    (flags and CollisionFlag.LOC) == 0
            }

            fun isLand(x: Int, z: Int): Boolean = (flagsAt(x, z) and BLOCKED) == 0

            val candidates = ArrayList<Pair<Int, Int>>()
            for (x in anchorX - SEARCH_RADIUS..anchorX + SEARCH_RADIUS) {
                for (z in anchorZ - SEARCH_RADIUS..anchorZ + SEARCH_RADIUS) {
                    if (!isWater(x, z)) {
                        continue
                    }
                    val touchesLand =
                        isLand(x - 1, z) || isLand(x + 1, z) || isLand(x, z - 1) || isLand(x, z + 1)
                    if (touchesLand) {
                        candidates += x to z
                    }
                }
            }

            candidates.sortBy { (x, z) ->
                max(abs(x - anchorX), abs(z - anchorZ)) * 1000 + abs(x - anchorX)
            }
            val picked = ArrayList<Pair<Int, Int>>()
            for (candidate in candidates) {
                if (picked.size == count) {
                    break
                }
                val spaced =
                    picked.all {
                        max(abs(it.first - candidate.first), abs(it.second - candidate.second)) >=
                            MIN_SPACING
                    }
                if (spaced) {
                    picked += candidate
                }
            }
            return picked.sortedWith(compareBy({ it.first }, { it.second }))
        }
    }
}
