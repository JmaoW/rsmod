package org.rsmod.content.areas.misc.isleofsouls.dungeons

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onCommand
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lists the locs on and around a tile, with the layer, shape and angle each one is placed with.
 * Used to see how a multi-part object (the Kourend statue) is laid out on the map. Without
 * arguments it looks round the player; with `::locs x z level` it looks round that tile instead.
 */
/** The Kourend statue's pieces, by loc id: the plinth and the three parts above it. */
private val STATUE_IDS = setOf(27785, 14193, 14194, 14195)

class LocListCommand
@Inject
constructor(private val locRepo: LocRepository, private val locTypes: LocTypeList) :
    PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onCommand("locs") {
            modLevel = modlevels.admin
            desc = "List the locs round a tile: [x z level], or round the player"
            invalidArgs = "Use as ::locs or ::locs x z level"
            cheat {
                val centre =
                    if (args.size >= 3) {
                        CoordGrid(args[0].toInt(), args[1].toInt(), args[2].toInt())
                    } else {
                        player.coords
                    }
                val radius = 3
                var count = 0
                var matches = 0
                // Each level is its own tile for the loc registry, so check all four.
                for (level in 0..3) for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        val coords = CoordGrid(centre.x + dx, centre.z + dz, level)
                        for (loc in locRepo.findAll(coords)) {
                            count++
                            val name = locTypes[loc.id]?.name ?: "unknown"
                            val line =
                                "${coords.x},${coords.z},${coords.level}: $name (${loc.id}) " +
                                    "layer=${loc.layer} shape=${loc.shape} angle=${loc.angle}"
                            // Every loc goes to the server log, so the whole list can be read.
                            logger.info { "locs: $line" }
                            if (
                                name.contains("statue", ignoreCase = true) || loc.id in STATUE_IDS
                            ) {
                                matches++
                                player.mes(line)
                            }
                        }
                    }
                }
                player.mes(
                    "Found $count locs, $matches statue matches. Full list in the server log."
                )
            }
        }
    }
}
