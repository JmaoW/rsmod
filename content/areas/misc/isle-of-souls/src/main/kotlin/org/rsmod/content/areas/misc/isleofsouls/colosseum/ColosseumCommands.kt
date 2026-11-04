package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Admin commands for checking the Colosseum in the client:
 * - `::colosseum`: to the lobby (and Minimus's introduction is marked done).
 * - `::colowave <1-12>`: between waves, the next wave is the one given (12 is Sol Heredit).
 * - `::colokill`: kills the wave on the floor.
 * - `::cologlory <glory>`: sets your best Glory (title, bank chest, ring, Fortis Salute).
 * - `::colomod <modifier> <1-3>`: between waves, raises a modifier to that tier (`blasphemy`,
 *   `doom`, `redflag`, ... the modifier's name without spaces).
 */
class ColosseumCommands
@Inject
constructor(private val colosseum: Colosseum, private val collision: CollisionFlagMap) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("colosseum") {
            modLevel = modlevels.admin
            desc = "Go to the Fortis Colosseum's lobby"
            cheat {
                VarPlayerIntMapSetter.set(player, colosseum_varbits.master_intro, 1)
                PathingEntityCommon.telejump(player, collision, ColosseumArena.lobbyArrival)
            }
        }
        onCommand("colowave") {
            modLevel = modlevels.admin
            desc = "Between Colosseum waves: make the next wave the one given (1-12)"
            cheat {
                val wave = args.firstOrNull()?.toIntOrNull()
                val ok = wave != null && colosseum.setNextWave(player, wave)
                player.mes(
                    if (ok) "The next wave is wave $wave."
                    else "Use ::colowave <wave> between waves, with a wave this run plays."
                )
            }
        }
        onCommand("colokill") {
            modLevel = modlevels.admin
            desc = "Kill the Colosseum wave on the floor"
            cheat {
                if (!colosseum.killWave(player)) {
                    player.mes("There is no wave on the floor.")
                }
            }
        }
        onCommand("cologlory") {
            modLevel = modlevels.admin
            desc = "Set your best Colosseum Glory"
            cheat {
                val glory = args.firstOrNull()?.toIntOrNull()
                if (glory == null || glory < 0) {
                    player.mes("Use ::cologlory <glory>.")
                    return@cheat
                }
                VarPlayerIntMapSetter.set(player, colosseum_varps.glory, glory)
                player.mes("Your best Glory is now $glory (${Colosseum.rankOf(glory)}).")
            }
        }
        onCommand("colomod") {
            modLevel = modlevels.admin
            desc = "Between Colosseum waves: set a modifier's tier"
            cheat {
                val name = args.getOrNull(0)?.lowercase()
                val tier = args.getOrNull(1)?.toIntOrNull()
                val modifier =
                    ColosseumModifier.entries.firstOrNull {
                        it.label.lowercase().filter(Char::isLetter) == name
                    }
                val ok =
                    modifier != null &&
                        tier != null &&
                        colosseum.setModifier(player, modifier, tier)
                player.mes(
                    if (ok) "${modifier?.label} is now tier $tier."
                    else
                        "Use ::colomod <modifier> <tier> between waves. Modifiers: " +
                            ColosseumModifier.entries.joinToString(", ") {
                                it.label.lowercase().filter(Char::isLetter)
                            }
                )
            }
        }
    }
}
