package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Fight Cave's entrance, exit and gatekeeper on the isle, and the timer that runs a cave. The
 * fighting is in [FightCaveCombatScript]; the run itself is [FightCaves].
 */
class FightCaveScript
@Inject
constructor(
    private val caves: FightCaves,
    private val instances: TeamInstances,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        instances.register(FightCaveInstance.type)
        caves.settings = FightCaveSettings.load()
        logger.info { "Fight Cave mode: ${caves.settings.mode.key}" }

        onGameStartup { placeEntrance() }

        onOpLoc1(cave_locs.entrance) { enterCave() }
        onOpLoc1(cave_locs.exit) { leaveCave() }

        onOpNpc1(cave_npcs.gatekeeper) { talkToGatekeeper(it.npc) }
        onOpNpc3(cave_npcs.gatekeeper) { exchangeFireCape(it.npc) }

        onPlayerSoftTimer(cave_timers.tick) {
            if (!caves.tick(player)) {
                player.clearSoftTimer(cave_timers.tick)
            }
        }
        onPlayerLogout { caves.sweep(except = player) }

        onPlayerQueue(cave_queues.enter) { gatekeeperSays(ENTER_TEXT) }
        onPlayerQueue(cave_queues.win) { gatekeeperSays(WIN_TEXT) }
    }

    /** Puts the cave's entrance and its gatekeeper on the isle. Runs when the server starts. */
    fun placeEntrance() {
        locRepo.add(
            ENTRANCE,
            locTypes[cave_locs.entrance],
            duration = Int.MAX_VALUE,
            angle = ENTRANCE_ANGLE,
            shape = LocShape.CentrepieceStraight,
        )

        val npc = Npc(npcTypes[cave_npcs.gatekeeper], GATEKEEPER)
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
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

    private suspend fun ProtectedAccess.talkToGatekeeper(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "Welcome to the Fight Cave, JalYt. Few survive all of it.")
            chatNpc(
                neutral,
                "Beat every wave and TzTok-Jad, and the cave rewards you with a fire cape.",
            )
            chatPlayer(quiz, "Where is the cave?")
            chatNpc(
                neutral,
                "The entrance is the great crack just beside me. Your whole team enters together.",
            )
            explainInferno()
        }
    }

    /** TzHaar-Mej-Jal speaking to a player who is inside the cave, not standing next to him. */
    private suspend fun ProtectedAccess.gatekeeperSays(text: String) {
        startDialogue {
            chatNpcSpecific(GATEKEEPER_NAME, npcTypes[cave_npcs.gatekeeper], neutral, text)
        }
    }

    private suspend fun Dialogue.explainInferno() {
        chatNpc(
            neutral,
            "Bring a fire cape to me when the Inferno opens, and I will let you face TzKal-Zuk.",
        )
    }

    private suspend fun ProtectedAccess.exchangeFireCape(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "The Inferno is not open yet, JalYt. Keep your fire cape safe.")
        }
    }

    companion object {
        const val GATEKEEPER_NAME = "TzHaar-Mej-Jal"
        const val ENTER_TEXT = "You're on your own now JalYt, prepare to fight for your life!"
        const val WIN_TEXT =
            "You even defeated TzTok-Jad, I am most impressed! Please accept this gift. " +
                "Give cape back to me if you not want it."

        /**
         * The cave's mouth, set into the rock row north of the lobby graveyards' plaza. The loc is
         * 3 wide and 2 deep, so it covers x 2207-2209, z 2861-2862; players use it from the plaza
         * tile in front of it.
         */
        val ENTRANCE = CoordGrid(2207, 2861, 0)

        /** TzHaar-Mej-Jal stands to its side. */
        val GATEKEEPER = CoordGrid(2206, 2859, 0)

        /**
         * Which way the entrance faces. The real cave's entrance uses East; if it opens towards the
         * rock instead of the plaza in the client, turn it round with West.
         */
        val ENTRANCE_ANGLE = LocAngle.East
    }
}
