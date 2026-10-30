package org.rsmod.content.skills.fishing.scripts

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Spawns every fishing spot of the Isle of Souls when the server starts. Each spot stands on a
 * fixed tile, listed in [SPOTS]: to move a spot, change its tile there. The spots never move or
 * deplete on their own, so a tile that is changed here is the only thing that needs editing.
 */
class FishingSpotSpawns
@Inject
constructor(private val npcTypes: NpcTypeList, private val npcRepo: NpcRepository) :
    PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onGameStartup { spawnSpots() }
    }

    private fun spawnSpots() {
        for (spot in SPOTS) {
            val type = npcTypes[spot.npcId]
            if (type == null) {
                logger.warn { "Fishing spot ${spot.label}: no npc type with id ${spot.npcId}." }
                continue
            }
            val npc = Npc(type, spot.coords)
            npc.noneMode()
            npcRepo.add(npc, Int.MAX_VALUE)
            logger.info {
                "Fishing spot: ${spot.label} (${type.internalName}) at ${spot.coords.toConventionalString()}"
            }
        }
    }

    /** A fishing spot: [npcId] is the npc type's id, [label] is only used for logging. */
    data class Spot(val label: String, val npcId: Int, val coords: CoordGrid)

    companion object {
        /** Npc ids of the spot types. */
        const val SHARK = 1520
        const val MONKFISH = 4316
        /** `piscariliusfish`, the Piscarilius spot: bait it with sandworms. */
        const val ANGLERFISH = 6825
        const val SALMON = 394
        const val KARAMBWAN = 4712
        const val KARAMBWANJI = 4710

        /**
         * Every fishing spot on the isle and the tile it stands on. All are on the ground floor.
         */
        val SPOTS =
            listOf(
                Spot("Shark", SHARK, CoordGrid(2085, 2894, 0)),
                Spot("Monkfish", MONKFISH, CoordGrid(2086, 2890, 0)),
                Spot("Anglerfish", ANGLERFISH, CoordGrid(2087, 2902, 0)),
                Spot("Salmon", SALMON, CoordGrid(2287, 2974, 0)),
                Spot("Salmon", SALMON, CoordGrid(2276, 2976, 0)),
                Spot("Karambwan", KARAMBWAN, CoordGrid(2268, 2785, 0)),
                Spot("Karambwan", KARAMBWAN, CoordGrid(2267, 2785, 0)),
                Spot("Karambwanji", KARAMBWANJI, CoordGrid(2261, 2786, 0)),
                Spot("Karambwanji", KARAMBWANJI, CoordGrid(2263, 2786, 0)),
            )
    }
}
