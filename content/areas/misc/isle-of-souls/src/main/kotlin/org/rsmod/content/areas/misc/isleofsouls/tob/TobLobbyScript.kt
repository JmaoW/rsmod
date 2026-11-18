package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfPauseButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ver Sinhaza and the theatre's doors: the isle's portal there and back, the notice board and its
 * party screens, the way in, the bank, the party HUD while a player is in town, and inside the
 * theatre the barriers, passages, Vyre Orators and the escape crystal, and what happens when a
 * player dies or logs out.
 */
class TobLobbyScript
@Inject
constructor(
    private val board: TobBoard,
    private val raids: TobRaids,
    private val parties: TobParties,
    private val hud: TobHud,
    private val players: PlayerList,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val bankAccess: BankAccess,
) : PluginScript() {
    /** Players in Ver Sinhaza and the party names their HUD last showed. */
    private val inTown = HashMap<Int, String>()

    override fun ScriptContext.startup() {
        onGameStartup { placePortals() }
        onOpLoc1(tob_locs.isle_portal) { portal(it.loc.coords) }

        onOpLoc1(tob_locs.notice_board) { board.openList(player) }
        onIfPauseButton(tob_components.party_list_buttons) { board.clickList(player, comsub) }
        tob_components.party_list_rows.forEachIndexed { row, component ->
            onIfModalButton(component) { board.selectParty(player, row) }
        }
        onIfPauseButton(tob_components.details_buttons) { board.clickDetails(player, comsub) }

        onOpLoc1(tob_locs.entrance) { enterTheatre() }
        onOpLoc1(tob_locs.retrieval_chest) {
            mes(
                "The chest seems to be empty. If it did have any of your items, but you died " +
                    "before collecting them, they'll now be lost."
            )
        }
        onOpLoc2(VerSinhazaLocs.bank_booth) { bankAccess.open(this) }

        // Inside the theatre.
        onOpLoc1(tob_locs.barrier) { barrier(it.loc.coords) }
        for (exit in listOf(tob_locs.passage, tob_locs.xarpus_exit)) {
            onOpLoc1(exit) { passOn(ask = true) }
            onOpLoc2(exit) { passOn(ask = false) }
        }
        onOpLoc1(tob_locs.treasure_door) { passOn(ask = false) }
        for (orator in listOf(tob_locs.orator, tob_locs.orator_seated)) {
            onOpLoc1(orator) { talkToOrator() }
            onOpLoc2(orator) { resign() }
        }
        onOpLoc1(tob_locs.teleport_crystal) { leaveVault() }
        onOpLoc1(tob_locs.vault_stairs) { leaveVault() }
        onOpHeld1(tob_objs.escape_crystal) { escape() }

        onEvent<GameLifecycle.StartCycle> {
            raids.tick()
            tickTown()
        }
        onEvent<PlayerDeathEvents.Died> { raids.died(player) }
        onPlayerLogout { loggedOut(player) }
    }

    // The isle and Ver Sinhaza
    // ----------------------------------------------------------------------------------------

    private fun placePortals() {
        for (tile in listOf(TobIsle.PORTAL, VER_SINHAZA_PORTAL)) {
            locRepo.add(
                tile,
                locTypes[tob_locs.isle_portal],
                duration = Int.MAX_VALUE,
                angle = LocAngle.South,
                shape = LocShape.CentrepieceStraight,
            )
        }
    }

    private fun ProtectedAccess.portal(at: CoordGrid) {
        if (at == VER_SINHAZA_PORTAL) {
            telejump(TobIsle.RETURN)
        } else if (at == TobIsle.PORTAL) {
            mes("You step through the portal to Ver Sinhaza.")
            telejump(VerSinhaza.ARRIVAL)
        }
    }

    private suspend fun ProtectedAccess.enterTheatre() {
        val party = parties.currentParty(player)
        if (party == null) {
            startDialogue {
                val form =
                    choice2(
                        "Form or join a party.",
                        true,
                        "Cancel.",
                        false,
                        title = "You are not in a party.",
                    )
                if (form) {
                    board.openList(player)
                }
            }
            return
        }
        val startsRaid = party.raid == null || party.raid?.ended == true
        if (startsRaid && party.isLeader(player)) {
            var ready = false
            startDialogue {
                ready =
                    choice2(
                        "Yes, let's go.",
                        true,
                        "No, not yet.",
                        false,
                        title = "Is your party ready? (Members: ${party.members.size})",
                    )
            }
            if (!ready) {
                return
            }
        }
        when (raids.enter(player)) {
            TobEntry.Entered -> Unit
            TobEntry.NoParty -> mes("You are not in a party.")
            TobEntry.LeaderFirst -> {
                val leader = party.leader?.displayName ?: "your leader"
                startDialogue { mesbox("Your leader, $leader, must go first.") }
            }
            TobEntry.NotInRaid -> mes("Your party's raid started without you, or you have left it.")
            TobEntry.AlreadyInside -> mes("You are already in the Theatre of Blood.")
            TobEntry.RewardWaiting ->
                mes("You have unclaimed rewards. Collect them from the chest by the bank first.")
            TobEntry.NoSpace ->
                mes("There is no room for another raid right now. Try again in a moment.")
        }
    }

    /**
     * Ver Sinhaza's HUD: opened with the party's names as a player walks into town, closed (and
     * their party or application dropped) when they walk out other than into the theatre.
     */
    private fun tickTown() {
        for (player in players) {
            val here = VerSinhaza.contains(player.coords)
            val known = player.accountId in inTown
            if (here) {
                val party = parties.currentParty(player)
                val names = party?.members?.joinToString(",") { it.displayName } ?: ""
                if (!known || inTown[player.accountId] != names) {
                    inTown[player.accountId] = names
                    hud.lobby(player, party)
                }
            } else if (known) {
                inTown.remove(player.accountId)
                leftTown(player)
            }
        }
        inTown.keys.removeIf { id -> players.none { it.accountId == id } }
    }

    private fun leftTown(player: Player) {
        if (raids.memberRaid(player) != null) {
            return
        }
        hud.close(player)
        val (applied, current) = parties.forget(player)
        applied?.let { board.refreshViewers(it) }
        if (current != null && !current.insideRaid) {
            player.mes("You have left Ver Sinhaza, so you have left your party.")
            board.refreshViewers(current)
        }
    }

    private fun loggedOut(player: Player) {
        raids.leave(player, TobExit.LoggedOut)
        inTown.remove(player.accountId)
        val (applied, current) = parties.forget(player)
        applied?.let { board.refreshViewers(it) }
        current?.let { board.refreshViewers(it) }
    }

    // Inside
    // -------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.barrier(tile: CoordGrid) {
        when (raids.barrier(player, tile)) {
            TobRaids.BarrierResult.Crossed,
            TobRaids.BarrierResult.NotHere -> Unit
            TobRaids.BarrierResult.LeaderFirst -> {
                val leader = raids.raidOf(player)?.leader?.player?.displayName ?: "your leader"
                startDialogue { mesbox("Your leader, $leader, must go first.") }
            }
            TobRaids.BarrierResult.StayAndFight -> mes("You must stay and fight!")
            TobRaids.BarrierResult.AskToStart -> {
                var begin = false
                startDialogue {
                    if (!raids.everyoneHere(player)) {
                        mesbox(
                            "Some of your party don't seem to have arrived yet.<br>" +
                                "If you proceed, they will be abandoned."
                        )
                    }
                    begin =
                        choice2(
                            "Yes, let's begin.",
                            true,
                            "No, don't start yet.",
                            false,
                            title = "Is your party ready to fight?",
                        )
                }
                if (begin) {
                    raids.startFight(player, tile)
                }
            }
        }
    }

    private suspend fun ProtectedAccess.passOn(ask: Boolean) {
        val raid = raids.raidOf(player) ?: return
        val room = raid.member(player)?.room ?: return
        if (room !in raid.completed) {
            mes("You can't proceed until the challenge is complete.")
            return
        }
        if (ask) {
            var go = false
            startDialogue {
                go = choice2("Yes.", true, "No.", false, title = "Are you ready to proceed?")
            }
            if (!go) {
                return
            }
        }
        raids.pass(player)
    }

    private suspend fun ProtectedAccess.talkToOrator() {
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val room = member.room ?: return
        if (raids.isStarted(raid, room) && room !in raid.completed && member.inFight) {
            mes("You must stay and fight!")
            return
        }
        resign()
    }

    private suspend fun ProtectedAccess.resign() {
        val raid = raids.raidOf(player) ?: return
        val member = raid.member(player) ?: return
        val room = member.room
        if (
            room != null && raids.isStarted(raid, room) && room !in raid.completed && member.inFight
        ) {
            mes("You must stay and fight!")
            return
        }
        var leave = false
        startDialogue {
            mesbox("There is no penalty for resigning now.")
            leave =
                choice2(
                    "Resign and leave the Theatre.",
                    true,
                    "Do not resign.",
                    false,
                    title = "Resign from the Theatre of Blood?",
                )
        }
        if (leave) {
            raids.leave(player, TobExit.Resigned)
        }
    }

    private suspend fun ProtectedAccess.leaveVault() {
        val raid = raids.raidOf(player) ?: return
        if (raid.member(player)?.room != TobRoom.Vault) {
            return
        }
        var leave = false
        startDialogue {
            leave = choice2("Yes.", true, "No.", false, title = "Leave the Theatre of Blood?")
        }
        if (leave) {
            if (player.vars[tob_varbits.should_have_loot] != 0) {
                mes("Your unclaimed rewards wait in the chest by the bank in Ver Sinhaza.")
            }
            raids.leave(player, TobExit.Completed)
        }
    }

    private fun ProtectedAccess.escape() {
        if (raids.raidOf(player) == null) {
            mes("The crystal can only be used inside the Theatre of Blood.")
            return
        }
        player.invDel(player.inv, objTypes[tob_objs.escape_crystal], 1)
        mes("The escape crystal shatters as it carries you out of the Theatre.")
        raids.leave(player, TobExit.EscapeCrystal)
    }

    private companion object {
        /** The way home from Ver Sinhaza: a portal beside the notice board. */
        val VER_SINHAZA_PORTAL = CoordGrid(3660, 3220, 0)
    }
}

internal object VerSinhazaLocs : LocReferences() {
    val bank_booth = find("tob_surface_bankbooth")
}
