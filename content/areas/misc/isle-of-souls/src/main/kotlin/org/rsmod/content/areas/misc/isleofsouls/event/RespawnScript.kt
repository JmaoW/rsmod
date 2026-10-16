package org.rsmod.content.areas.misc.isleofsouls.event

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.RespawnSpeed
import org.rsmod.api.script.onCommand
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Applies the league's global respawn speedup from [RespawnSettings] when the server starts, and
 * lets an admin change it live with `::respawnspeed <multiplier>` (`1` turns it off). A live change
 * lasts until the next restart; edit `event-settings.json` to make it stick.
 */
class RespawnScript @Inject constructor(private val respawnSpeed: RespawnSpeed) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        respawnSpeed.speed = RespawnSettings.load().speed
        logger.info { "Respawn speed: ${describe(respawnSpeed.speed)}" }

        onCommand("respawnspeed") {
            modLevel = modlevels.admin
            desc = "Set the global respawn speed multiplier (1 = off)"
            cheat {
                val value = args.firstOrNull()?.toDoubleOrNull()
                if (value == null || !value.isFinite() || value <= 0.0) {
                    player.mes("Respawn speed is ${describe(respawnSpeed.speed)}.")
                    player.mes("Use ::respawnspeed <multiplier above 0>; 1 turns it off.")
                    return@cheat
                }
                respawnSpeed.speed = value
                player.mes("Respawn speed is now ${describe(value)}.")
            }
        }
    }

    private fun describe(speed: Double): String =
        if (speed == RespawnSpeed.OFF) "off (1x)" else "${speed}x faster"
}
