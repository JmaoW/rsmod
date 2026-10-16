package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fight Cave's entrance and exit handlers and the timer that runs a cave. The lobby has no cave
 * entrance and no gatekeeper of its own: players use the entrance in Mor Ul Rek. The fighting is in
 * [FightCaveCombatScript]; the run itself is [FightCaves].
 */
class FightCaveScript
@Inject
constructor(
    private val caves: FightCaves,
    private val instances: TeamInstances,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        instances.register(FightCaveInstance.type)
        caves.settings = FightCaveSettings.load()
        logger.info { "Fight Cave mode: ${caves.settings.mode.key}" }

        onOpLoc1(cave_locs.entrance) { enterCave() }
        onOpLoc1(cave_locs.exit) { leaveCave() }

        onPlayerSoftTimer(cave_timers.tick) {
            if (!caves.tick(player)) {
                player.clearSoftTimer(cave_timers.tick)
            }
        }
        onPlayerLogout { caves.sweep(except = player) }

        onPlayerQueue(cave_queues.enter) { gatekeeperSays(ENTER_TEXT) }
        onPlayerQueue(cave_queues.win) { gatekeeperSays(WIN_TEXT) }
    }

    private fun ProtectedAccess.enterCave() {
        val result = caves.enter(player)
        when (result) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the Fight Cave.")
            EnterResult.NoSpace -> mes("There is no room for another cave right now. Try again.")
            EnterResult.InvalidEntry -> mes("The cave is not open.")
        }
    }

    private fun ProtectedAccess.leaveCave() {
        caves.leave(player)
    }

    /** TzHaar-Mej-Jal speaking to a player who is inside the cave, not standing next to him. */
    private suspend fun ProtectedAccess.gatekeeperSays(text: String) {
        startDialogue {
            chatNpcSpecific(GATEKEEPER_NAME, npcTypes[cave_npcs.gatekeeper], neutral, text)
        }
    }

    companion object {
        const val GATEKEEPER_NAME = "TzHaar-Mej-Jal"
        const val ENTER_TEXT = "You're on your own now JalYt, prepare to fight for your life!"
        const val WIN_TEXT =
            "You even defeated TzTok-Jad, I am most impressed! Please accept this gift. " +
                "Give cape back to me if you not want it."
    }
}
