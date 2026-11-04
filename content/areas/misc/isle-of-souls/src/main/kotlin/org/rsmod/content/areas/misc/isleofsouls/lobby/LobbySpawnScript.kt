package org.rsmod.content.areas.misc.isleofsouls.lobby

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onEvent
import org.rsmod.content.areas.misc.isleofsouls.colosseum.Colosseum
import org.rsmod.content.areas.misc.isleofsouls.gauntlet.Gauntlet
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaRaids
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Starts new characters and respawns dead players on a random tile in the Soul Wars lobby's
 * graveyards, instead of the realm's configured spawn and respawn points.
 */
class LobbySpawnScript
@Inject
constructor(
    private val random: GameRandom,
    private val gauntlet: Gauntlet,
    private val toa: ToaRaids,
    private val colosseum: Colosseum,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<SessionStateEvent.NewPlayerSpawn> { coords = SoulWarsLobby.randomSpawn(random) }
        // Someone who died in the Gauntlet wakes up in its lobby, someone who died in the Tombs of
        // Amascut at the start of their room, and someone who died in the Fortis Colosseum in its
        // lobby; everyone else in the graveyards.
        onEvent<PlayerDeathEvents.Respawn> {
            coords =
                gauntlet.respawnFor(player)
                    ?: toa.respawnFor(player)
                    ?: colosseum.respawnFor(player)
                    ?: SoulWarsLobby.randomSpawn(random)
        }
    }
}
