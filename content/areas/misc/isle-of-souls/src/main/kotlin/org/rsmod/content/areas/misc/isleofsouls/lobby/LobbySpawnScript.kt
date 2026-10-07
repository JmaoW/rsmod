package org.rsmod.content.areas.misc.isleofsouls.lobby

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Starts new characters and respawns dead players on a random tile in the Soul Wars lobby's
 * graveyards, instead of the realm's configured spawn and respawn points.
 */
class LobbySpawnScript @Inject constructor(private val random: GameRandom) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.NewPlayerSpawn> { coords = SoulWarsLobby.randomSpawn(random) }
        onEvent<PlayerDeathEvents.Respawn> { coords = SoulWarsLobby.randomSpawn(random) }
    }
}
