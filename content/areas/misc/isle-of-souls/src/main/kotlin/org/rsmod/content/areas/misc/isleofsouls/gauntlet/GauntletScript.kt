package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Gauntlet's doors and people: the portal on the isle and the lobby's platform, Bryn and the
 * entrance, the start room's exit platform and the Hunllef's barrier, and what happens when a
 * player dies, logs out or logs in. The rules are [Gauntlet]'s.
 */
class GauntletScript
@Inject
constructor(
    private val gauntlet: Gauntlet,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val npcTypes: NpcTypeList,
    private val rewards: GauntletRewards,
    private val scoreboard: GauntletScoreboard,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup { placePortal() }

        onOpLoc1(gauntlet_locs.portal) { telejump(Gauntlet.LOBBY) }
        onOpLoc1(gauntlet_locs.lobby_exit) { channelUp() }

        onOpLoc1(gauntlet_locs.entrance) { enter(GauntletMode.Regular) }
        onOpLoc1(gauntlet_locs.entrance_corrupted) { enter(GauntletMode.Regular) }
        onOpLoc2(gauntlet_locs.entrance_corrupted) { enter(GauntletMode.Corrupted) }

        onOpLoc1(gauntlet_locs.exit) { askToExit() }
        onOpLoc2(gauntlet_locs.exit) { gauntlet.leave(player, GauntletExit.Exit) }
        onOpLoc1(gauntlet_locs.exit_hm) { askToExit() }
        onOpLoc2(gauntlet_locs.exit_hm) { gauntlet.leave(player, GauntletExit.Exit) }

        onOpLoc1(gauntlet_locs.barrier) { askToPass() }
        onOpLoc2(gauntlet_locs.barrier) { pass() }
        onOpLoc1(gauntlet_locs.barrier_hm) { askToPass() }
        onOpLoc2(gauntlet_locs.barrier_hm) { pass() }
        onOpLoc5(gauntlet_locs.barrier_escape) { escape() }
        onOpLoc5(gauntlet_locs.barrier_escape_hm) { escape() }

        for (node in
            listOf(
                gauntlet_locs.node_a,
                gauntlet_locs.node_b,
                gauntlet_locs.node_a_hm,
                gauntlet_locs.node_b_hm,
            )) {
            onOpLoc1(node) { light(it.loc.coords, it.type, it.loc.angleId) }
        }
        onOpHeld1(gauntlet_objs.teleport_crystal) { useCrystal() }
        onOpHeld1(gauntlet_objs.teleport_crystal_hm) { useCrystal() }

        onOpLoc1(gauntlet_locs.scoreboard) { scoreboard.read(player) }
        onOpLoc1(gauntlet_locs.chest) { openChest() }

        onOpNpc1(gauntlet_npcs.bryn) { talkToBryn(it.npc) }

        onEvent<GameLifecycle.StartCycle> { gauntlet.tick() }
        onEvent<PlayerDeathEvents.Died> { gauntlet.died(player) }
        onPlayerLogin { gauntlet.loggedIn(player) }
        onPlayerLogout { gauntlet.loggedOut(player) }
    }

    /** The real portal is in Prifddinas; the isle has a copy of it for the lobby's way down. */
    private fun placePortal() {
        locRepo.add(
            PORTAL,
            locTypes[gauntlet_locs.portal],
            duration = Int.MAX_VALUE,
            angle = PORTAL_ANGLE,
            shape = PORTAL_SHAPE,
        )
    }

    private fun ProtectedAccess.channelUp() {
        telejump(Gauntlet.PORTAL_FRONT)
    }

    // The entrance ------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.enter(mode: GauntletMode) {
        when (gauntlet.enter(player, mode)) {
            GauntletEntry.Entered -> mes("You enter the Gauntlet.")
            GauntletEntry.AlreadyInside -> mes("You are already in the Gauntlet.")
            GauntletEntry.NotBriefed -> brynSays(BRYN_NOT_BRIEFED)
            GauntletEntry.RewardWaiting -> brynSays(BRYN_REWARD)
            GauntletEntry.HandsFull ->
                mesbox("You can't take any items into the Gauntlet. Use the deposit box first.")
            GauntletEntry.CorruptedLocked ->
                mes("You must complete the Gauntlet before you can enter the Corrupted Gauntlet.")
            GauntletEntry.TooLate ->
                mes("Your team has already reached the Hunllef. Wait for them to finish.")
            GauntletEntry.OtherVersion ->
                mes("Your team is already in the other version of the Gauntlet.")
            GauntletEntry.Barred ->
                mes("You can't return to the Gauntlet your team is in. Wait for them to finish.")
            GauntletEntry.NoSpace ->
                mes("There is no room for another Gauntlet right now. Try again.")
            GauntletEntry.InvalidEntry -> mes("The Gauntlet is not open.")
        }
    }

    private suspend fun ProtectedAccess.brynSays(text: String) {
        startDialogue { chatNpcSpecific("Bryn", npcTypes[gauntlet_npcs.bryn], neutral, text) }
    }

    // The chest ---------------------------------------------------------------------------------

    private fun ProtectedAccess.openChest() {
        val cape = GauntletRewardObjs.gauntlet_crystalline_cape
        val owns = cape in inv || cape in worn || cape in bank
        with(rewards) { open(owns, gauntlet.pick) }
    }

    // Bryn --------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.talkToBryn(npc: Npc) {
        startDialogue(npc) {
            chatNpc(neutral, "What are you doing down here?")
            val choice =
                choice2(
                    "Could you explain this place to me?",
                    1,
                    "I'm thinking of giving the Gauntlet a go.",
                    2,
                )
            VarPlayerIntMapSetter.set(player, gauntlet_varps.bryn, 1)
            if (choice == 1) {
                chatPlayer(quiz, "Could you explain this place to me?")
                for (line in EXPLANATION) {
                    chatNpc(neutral, line)
                }
                mesbox(TELEPORT_WARNING)
                chatNpc(
                    neutral,
                    "I think that covers it... Everything else, you'll have to work out for yourself while you're in there.",
                )
            } else {
                chatPlayer(happy, "I'm thinking of giving the Gauntlet a go.")
                chatNpc(neutral, "You? Attempting the Gauntlet? If you say so!")
            }
        }
    }

    // Inside ------------------------------------------------------------------------------------

    private fun ProtectedAccess.light(at: CoordGrid, type: UnpackedLocType, angle: Int) {
        when (gauntlet.lightNode(player, at, type, angle)) {
            Gauntlet.LightResult.Lit ->
                mes("You light the nodes in the corridor to help guide the way.")
            Gauntlet.LightResult.NoSceptre -> mes("You need something to light the nodes with.")
            Gauntlet.LightResult.NothingBeyond -> mes("There is nothing past this node to light.")
            Gauntlet.LightResult.AlreadyLit,
            Gauntlet.LightResult.NotInRun -> {}
        }
    }

    private fun ProtectedAccess.useCrystal() {
        when (gauntlet.useTeleportCrystal(player)) {
            Gauntlet.CrystalResult.Teleported -> {}
            Gauntlet.CrystalResult.InStartRoom ->
                mes("You're already at the start of the Gauntlet.")
            Gauntlet.CrystalResult.BossPhase ->
                mes("That won't help you now. At this point in the Gauntlet, you win or you die.")
            Gauntlet.CrystalResult.NotInRun -> {}
        }
    }

    private suspend fun ProtectedAccess.askToExit() {
        val go =
            choice2(
                "Yes, let me out.",
                true,
                "No.",
                false,
                title =
                    "Are you sure you wish to exit the Gauntlet? All of your progress will be " +
                        "lost and you will start again upon re-entering.",
            )
        if (go) {
            gauntlet.leave(player, GauntletExit.Exit)
        }
    }

    private suspend fun ProtectedAccess.askToPass() {
        val go =
            choice2(
                "Yes, I'm ready.",
                true,
                "No.",
                false,
                title =
                    "Start the boss fight now? There's no turning back, and your team comes too.",
            )
        if (go) {
            pass()
        }
    }

    /** Through the barrier: the fight begins and everyone in the maze is taken to it. */
    private fun ProtectedAccess.pass() {
        val run = gauntlet.runOf(player) ?: return
        if (run.stage == GauntletStage.Prep) {
            gauntlet.startBossPhase(run, forced = false)
        }
    }

    private fun ProtectedAccess.escape() {
        gauntlet.leave(player, GauntletExit.Escape)
    }

    companion object {
        /** The portal's south-west tile on the isle, 6 wide, facing north. */
        val PORTAL = CoordGrid(2122, 2889, 0)
        val PORTAL_ANGLE = LocAngle.South
        val PORTAL_SHAPE = LocShape.CentrepieceStraight

        const val BRYN_NOT_BRIEFED =
            "Don't think you want to be heading down there without knowing what you're getting " +
                "into! Come and see me, if you really want to go down there."
        const val BRYN_REWARD =
            "There's something in that there chest waiting for you. Don't leave it for too long."

        const val TELEPORT_WARNING =
            "The Gauntlet is not a safe environment, and teleports are blocked inside! If you " +
                "die in there, you lose everything you made."

        val EXPLANATION =
            listOf(
                "This here is the Gauntlet, the finest creation of the Amlodd clan. It was built " +
                    "years ago to help train Prifddinas' finest warriors and survivalists.",
                "The Gauntlet contains a range of deadly creatures formed from crystal. " +
                    "Defeating them is the key to victory, but it is no easy task.",
                "All participants start with nothing at hand beyond a few basic tools. Resources " +
                    "can be gathered throughout the dungeon. These can be used to create the " +
                    "weapons, armour, food and potions needed to survive.",
                "Once you feel you're ready, or once we decide that you've spent long enough down " +
                    "there, you'll have to face the fearsome Crystalline Hunllef.",
                "Once you go down there, there are only two ways out... You can make a break for " +
                    "the exit, or if you're bested in combat we'll send someone in for you.",
            )
    }
}
