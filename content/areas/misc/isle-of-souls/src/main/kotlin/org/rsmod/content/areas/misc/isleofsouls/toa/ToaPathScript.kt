package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What every path shares: the barrier that starts a puzzle, the door from a won puzzle to its boss,
 * the boss room crystal, Osmumten who takes a won room home, and the exits. The room itself (the
 * puzzle or the fight) is the room own [ToaRoomRuntime].
 */
class ToaPathScript
@Inject
constructor(private val raids: ToaRaids, private val rooms: ToaRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(toa_locs.path_barrier) { passBarrier(it.loc.coords, quick = false) }
        onOpLoc2(toa_locs.path_barrier) { passBarrier(it.loc.coords, quick = true) }

        for (door in
            listOf(
                toa_locs.continue_door,
                toa_locs.continue_scabaras,
                toa_locs.continue_apmeken,
                toa_locs.continue_crondis,
            )) {
            onOpLoc1(door) { enterBoss() }
            onOpLoc2(door) { enterBoss() }
        }

        for (crystal in
            listOf(
                toa_locs.crystal_akkha,
                toa_locs.crystal_baba,
                toa_locs.crystal_kephri,
                toa_locs.crystal_zebak,
            )) {
            onOpLoc1(crystal) { useCrystal(quick = false) }
            onOpLoc2(crystal) { useCrystal(quick = true) }
        }

        for (exit in
            listOf<LocType>(
                toa_locs.exit_door,
                toa_locs.exit_wardens,
                toa_locs.exit_akkha,
                toa_locs.exit_baba,
                toa_locs.exit_kephri,
                toa_locs.exit_zebak,
                toa_locs.exit_crondis,
            )) {
            onOpLoc1(exit) { askToLeave() }
        }

        // "Talk-to" and "Proceed": either takes the party back to the Nexus.
        onOpNpc1(toa_npcs.osmumten_vis) { goHome() }
        onOpNpc3(toa_npcs.osmumten_vis) { goHome() }
    }

    private fun ProtectedAccess.blockedAsGhost(): Boolean {
        if (!raids.isGhost(player)) {
            return false
        }
        mes("A mysterious force prevents you from doing that.")
        return true
    }

    /**
     * The barrier into a puzzle: begins the challenge when crossed from outside (after asking,
     * unless "Quick-Pass"), and lets a won room through either way. Once the challenge is on nobody
     * leaves until it is over.
     */
    private suspend fun ProtectedAccess.passBarrier(barrier: CoordGrid, quick: Boolean) {
        if (blockedAsGhost()) {
            return
        }
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val room = member.room ?: return
        val runtime = rooms.ensure(raid, room)
        val across = across(barrier)
        if (runtime == null || runtime.isCompleted) {
            telejump(across)
            return
        }
        val inside = runtime.inChallenge(player.coords)
        when {
            inside && runtime.isStarted -> mes("You can't leave until the challenge is over.")
            inside -> telejump(across)
            runtime.isStarted -> telejump(across)
            quick -> {
                runtime.start()
                telejump(across)
            }
            else ->
                startDialogue {
                    val answer = choice2("Begin the challenge.", 1, "Not yet.", 2)
                    if (answer == 1) {
                        runtime.start()
                        telejump(across)
                    }
                }
        }
    }

    /** The tile two across the barrier from where the player stands. */
    private fun ProtectedAccess.across(barrier: CoordGrid): CoordGrid {
        val dx = if (barrier.x < player.coords.x) -2 else 2
        return player.coords.translate(dx, 0)
    }

    private fun ProtectedAccess.enterBoss() {
        if (blockedAsGhost()) {
            return
        }
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val room = member.room ?: return
        val path = room.path ?: return
        if (room.kind != RoomKind.Puzzle) {
            return
        }
        if (!raids.isWon(raid, room)) {
            mes("You can't proceed until the challenge is complete.")
            return
        }
        raids.fadeMove(player) { raids.place(raid, member, path.boss) }
    }

    /** The boss room crystal: onto the arena floor, beginning the fight the first time. */
    private suspend fun ProtectedAccess.useCrystal(quick: Boolean) {
        if (blockedAsGhost()) {
            return
        }
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val room = member.room?.takeIf { it.kind == RoomKind.Boss } ?: return
        val runtime = rooms.ensure(raid, room)
        val challenge = room.challenge ?: return
        val dest = raid.regionOf(challenge)
        if (runtime == null || runtime.stage != ToaRoomRuntime.Stage.Idle) {
            telejump(dest)
            return
        }
        if (quick) {
            runtime.start()
            telejump(dest)
            return
        }
        startDialogue {
            val answer = choice2("Begin the challenge.", 1, "Not yet.", 2)
            if (answer == 1) {
                runtime.start()
                telejump(dest)
            }
        }
    }

    /** Osmumten takes the player back to the Nexus. */
    private fun ProtectedAccess.goHome() {
        if (blockedAsGhost()) {
            return
        }
        raids.returnToNexus(player)
    }

    private suspend fun ProtectedAccess.askToLeave() {
        if (blockedAsGhost()) {
            return
        }
        if (raids.raidOf(player) == null) {
            return
        }
        startDialogue {
            val answer = choice2("Yes, abandon the raid.", 1, "No, I want to stay.", 2)
            if (answer == 1) {
                player.mes("You abandon the raid and leave the Tombs of Amascut.")
                raids.leave(player, ToaExit.Abandoned)
            }
        }
    }
}
