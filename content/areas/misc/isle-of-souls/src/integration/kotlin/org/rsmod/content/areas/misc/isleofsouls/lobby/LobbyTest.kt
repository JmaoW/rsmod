package org.rsmod.content.areas.misc.isleofsouls.lobby

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.player.isInCombat
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_components
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.map.CoordGrid

/** New characters, death respawns and Home Teleport all land in the Soul Wars graveyards. */
class LobbyTest {
    private val elsewhere = CoordGrid(0, 50, 50, 21, 18)

    private val lobbyTiles = SoulWarsLobby.spawnTiles.toSet()

    private fun GameTestScope.allocLobbyZones() {
        for (tile in SoulWarsLobby.spawnTiles) {
            allocZoneCollision(tile)
        }
    }

    private fun GameTestScope.castHomeTeleport(button: ComponentType) {
        eventBus.publish(IfOverlayButton(player, button, -1, null, IfButtonOp.Op1))
    }

    @Test
    fun GameTestState.`a new player starts in one of the graveyards`() =
        runGameTest(LobbySpawnScript::class) {
            val starts = mutableSetOf<CoordGrid>()
            repeat(200) {
                val spawn = SessionStateEvent.NewPlayerSpawn(player, elsewhere)
                eventBus.publish(spawn)
                assertTrue(spawn.coords in lobbyTiles, "started outside the graveyards: $spawn")
                starts += spawn.coords
            }
            // Both graveyards get used, so it is not stuck on a single tile.
            assertTrue(starts.size > 10, "only ${starts.size} different start tiles in 200 tries")
            assertTrue(starts.any { it in SoulWarsLobby.blue.tiles })
            assertTrue(starts.any { it in SoulWarsLobby.red.tiles })
        }

    @Test
    fun GameTestState.`a dead player respawns in one of the graveyards`() =
        runGameTest(LobbySpawnScript::class) {
            repeat(50) {
                val respawn = PlayerDeathEvents.Respawn(player, elsewhere)
                eventBus.publish(respawn)
                assertTrue(respawn.coords in lobbyTiles, "respawned outside: $respawn")
            }
        }

    @Test
    fun GameTestState.`home teleport plays the cast then lands in a graveyard`() =
        runGameTest(HomeTeleportScript::class) {
            allocLobbyZones()
            allocZoneCollision(elsewhere)
            player.teleport(elsewhere)
            advance()

            val tracker = LeagueTaskTracker(eventBus, LeagueTeams(players))
            castHomeTeleport(lobby_components.home_standard)
            advance()
            assertEquals(elsewhere, player.coords, "should still be casting after 1 tick")
            assertFalse(tracker.isComplete(player, 431), "task done before the cast finished")

            advance(5)
            assertTrue(player.coords in lobbyTiles, "ended up at ${player.coords}")
            assertTrue(tracker.isComplete(player, 431), "Cast Home Teleport not completed")
        }

    @Test
    fun GameTestState.`every spellbook's home teleport goes to the lobby`() =
        runGameTest(HomeTeleportScript::class) {
            allocLobbyZones()
            allocZoneCollision(elsewhere)
            for (button in lobby_components.home_teleports) {
                player.teleport(elsewhere)
                advance()
                castHomeTeleport(button)
                advance(6)
                assertTrue(player.coords in lobbyTiles, "$button ended up at ${player.coords}")
            }
        }

    @Test
    fun GameTestState.`home teleport can be cast during combat`() =
        runGameTest(HomeTeleportScript::class) {
            allocLobbyZones()
            allocZoneCollision(elsewhere)
            player.teleport(elsewhere)
            // A fresh test world is still inside the post-combat window, so this is in combat.
            advance()
            assertTrue(player.isInCombat())

            castHomeTeleport(lobby_components.home_standard)
            advance(6)
            assertTrue(player.coords in lobbyTiles, "ended up at ${player.coords}")
        }

    @Test
    fun GameTestState.`the animation toggle op does not teleport`() =
        runGameTest(HomeTeleportScript::class) {
            allocLobbyZones()
            allocZoneCollision(elsewhere)
            player.teleport(elsewhere)

            eventBus.publish(
                IfOverlayButton(player, lobby_components.home_standard, -1, null, IfButtonOp.Op10)
            )
            advance(8)
            assertEquals(elsewhere, player.coords)
        }
}
