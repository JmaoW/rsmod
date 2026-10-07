package org.rsmod.content.areas.misc.isleofsouls.lobby

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_components
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_seqs
import org.rsmod.content.areas.misc.isleofsouls.configs.lobby_spotanims
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Makes every spellbook's Home Teleport take the player to a random tile in the Soul Wars lobby's
 * graveyards, with the normal teleport spell animation. It can be cast at any time, including in
 * combat.
 */
class HomeTeleportScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val random: GameRandom,
    private val tracker: LeagueTaskTracker,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (button in lobby_components.home_teleports) {
            onIfOverlayButton(button) {
                // The spellbook button's other op toggles the cast animation; only "Cast"
                // teleports.
                if (op == IfButtonOp.Op1) {
                    protectedAccess.launch(player) { castHomeTeleport() }
                }
            }
        }
    }

    private suspend fun ProtectedAccess.castHomeTeleport() {
        anim(lobby_seqs.cast_teleport)
        spotanim(lobby_spotanims.teleport_casting, height = SPOTANIM_HEIGHT)
        delay(CAST_TICKS)
        // `telejump`, not `teleport`: it makes the client snap its camera to the new tile, where
        // `teleport` has it glide there as if the player had walked, which is noticeable up close.
        telejump(SoulWarsLobby.randomSpawn(random))
        anim(lobby_seqs.cast_teleport_reverse)
        resetSpotanim()
        tracker.complete(player, TASK_CAST_HOME_TELEPORT)
    }

    private companion object {
        const val TASK_CAST_HOME_TELEPORT = 431

        /** Ticks the cast animation plays before the player moves. */
        const val CAST_TICKS = 3

        /** Raises the teleport graphic to the player's waist, as the normal teleport spells do. */
        const val SPOTANIM_HEIGHT = 92
    }
}
