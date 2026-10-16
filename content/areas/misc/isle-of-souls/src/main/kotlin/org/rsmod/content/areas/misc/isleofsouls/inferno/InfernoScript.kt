package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.FightCaveScript
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_npcs
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Inferno's entrance and exit handlers, and the timer that runs an Inferno. The lobby has no
 * entrance of its own (removed 2026-10-10), so the handler only answers the entrance loc type. Who
 * may enter is [InfernoUnlock]; the run itself is [Inferno].
 */
class InfernoScript
@Inject
constructor(
    private val inferno: Inferno,
    private val unlock: InfernoUnlock,
    private val instances: TeamInstances,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        instances.register(InfernoInstance.type)
        inferno.settings = InfernoSettings.load()
        logger.info { "Inferno mode: ${inferno.settings.mode.key}" }

        onOpLoc1(inferno_locs.entrance) { enterInferno() }
        onOpLoc1(inferno_locs.exit) { leaveInferno() }
        onOpLoc2(inferno_locs.exit) { leaveInferno() }

        onPlayerSoftTimer(inferno_timers.tick) {
            if (!inferno.tick(player)) {
                player.clearSoftTimer(inferno_timers.tick)
                player.client.resetNpcViewDistance()
            }
        }
        onPlayerLogout { inferno.sweep(except = player) }

        onPlayerQueue(inferno_queues.enter) { gatekeeperSays(ENTER_TEXT) }
        onPlayerQueue(inferno_queues.win) { gatekeeperSays(WIN_TEXT) }
    }

    private fun ProtectedAccess.enterInferno() {
        if (!unlock.isUnlocked(player)) {
            mes("The TzHaar will not let you in until you have given up a fire cape.")
            return
        }
        when (inferno.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the Inferno.")
            EnterResult.NoSpace -> mes("There is no room for another Inferno right now. Try again.")
            EnterResult.InvalidEntry -> mes("The Inferno is not open.")
        }
    }

    private fun ProtectedAccess.leaveInferno() {
        inferno.leave(player)
    }

    /** TzHaar-Mej-Jal speaking to a player who is inside the Inferno, not standing next to him. */
    private suspend fun ProtectedAccess.gatekeeperSays(text: String) {
        startDialogue {
            chatNpcSpecific(
                FightCaveScript.GATEKEEPER_NAME,
                npcTypes[cave_npcs.gatekeeper],
                neutral,
                text,
            )
        }
    }

    companion object {
        const val ENTER_TEXT =
            "Face TzKal-Zuk if you dare, JalYt. There is no way back but through him."
        const val WIN_TEXT =
            "You have defeated TzKal-Zuk! I am most impressed. Please accept this gift."
    }
}
