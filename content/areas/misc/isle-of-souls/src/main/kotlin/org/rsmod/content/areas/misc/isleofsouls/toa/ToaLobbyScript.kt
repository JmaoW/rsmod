package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import org.rsmod.api.config.refs.components
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.game.process.player.BuildAreaAnchors
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfPauseButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Jaltevas pyramid lobby and the doors of the raid: the grouping obelisk and the invocation
 * board, the way into the tombs and out, the party overlay that shows while a player stands in the
 * lobby, and what happens to a player who dies, logs out or logs in inside the raid.
 *
 * Inside the tombs the Nexus doors are here too; what happens in each room is the room's own
 * script.
 */
class ToaLobbyScript
@Inject
constructor(
    private val board: ToaBoard,
    private val raids: ToaRaids,
    private val parties: ToaParties,
    private val hud: ToaHud,
    private val eventBus: EventBus,
    private val players: PlayerList,
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val anchors: BuildAreaAnchors,
    private val bankAccess: BankAccess,
) : PluginScript() {
    /** Lobby players and the party text last drawn on their overlay. */
    private val overlays = HashMap<Int, String>()

    override fun ScriptContext.startup() {
        onGameStartup { placeEntrance() }
        // Every room is loaded at the same scene offset as in the real game (see
        // `ToaPacking.sceneAnchor`).
        anchors.register { player -> raids.sceneAnchor(player.coords) }
        onOpLoc1(toa_locs.pyramid_entry) { telejump(ToaLobby.ENTRANCE) }

        onOpLoc1(toa_locs.grouping_board) { board.openList(player) }
        onOpLoc1(toa_locs.invocation_board) {
            player.ifOpenMainModal(toa_interfaces.invocations, eventBus)
        }
        onOpLoc1(toa_locs.lobby_entry) { enterTombs() }
        onOpLoc1(toa_locs.lobby_exit) { leaveLobby() }
        onOpLoc1(toa_locs.bank_camel) { bankAccess.open(this) }

        onIfPauseButton(toa_components.party_list_buttons) { board.clickList(player, comsub) }
        onIfPauseButton(toa_components.party_list_rows) { board.selectParty(player, comsub) }
        onIfPauseButton(toa_components.details_buttons) { board.clickDetails(player, comsub) }
        onIfModalButton(toa_components.details_presets) {
            board.clickPreset(player, it.comsub, clear = it.op == IfButtonOp.Op2)
        }

        // The Nexus.
        for (path in ToaPath.entries) {
            val doors =
                when (path) {
                    ToaPath.Scabaras ->
                        listOf(toa_locs.nexus_scabaras, toa_locs.nexus_scabaras_unselected)
                    ToaPath.Het -> listOf(toa_locs.nexus_het, toa_locs.nexus_het_unselected)
                    ToaPath.Apmeken ->
                        listOf(toa_locs.nexus_apmeken, toa_locs.nexus_apmeken_unselected)
                    ToaPath.Crondis ->
                        listOf(toa_locs.nexus_crondis, toa_locs.nexus_crondis_unselected)
                }
            for (door in doors) {
                onOpLoc1(door) { walkPath(path) }
                onOpLoc2(door) { walkPath(path) }
            }
        }
        // Only the open form of the Wardens door has "Enter" and "Quick-Enter" in the cache.
        onOpLoc1(toa_locs.nexus_wardens_open) { enterWardens() }
        onOpLoc2(toa_locs.nexus_wardens_open) { enterWardens() }

        onEvent<GameLifecycle.StartCycle> {
            raids.tick()
            tickLobby()
        }
        onEvent<PlayerDeathEvents.Died> { raids.died(player) }
        onPlayerLogout { loggedOut(player) }
    }

    /**
     * The real way into the lobby is the door of a pyramid too big for the isle, so the pyramid's
     * small entry (a 2x2 stair door) is set into the clearing east of the Gauntlet's portal.
     */
    private fun placeEntrance() {
        locRepo.add(
            ToaIsle.ENTRANCE,
            locTypes[toa_locs.pyramid_entry],
            duration = Int.MAX_VALUE,
            angle = LocAngle.South,
            shape = LocShape.CentrepieceStraight,
        )
    }

    // Going in and out
    // --------------------------------------------------------------------------------

    private fun ProtectedAccess.enterTombs() {
        when (raids.enterFromLobby(player)) {
            ToaEntry.Entered -> Unit
            ToaEntry.NoParty ->
                mes(
                    "You are currently not in a raiding party. Use the grouping obelisk to form or join one."
                )
            ToaEntry.LeaderFirst -> mes("Your party leader must enter the tombs first.")
            ToaEntry.NotInRaid ->
                mes(
                    "You can't join your party's raid. It started without you, or you have left it."
                )
            ToaEntry.AlreadyInside -> mes("You are already in the tombs.")
            ToaEntry.RewardWaiting ->
                mes(
                    "You have unclaimed rewards from your last raid. Collect them from the chest first."
                )
            ToaEntry.NoSpace ->
                mes("There is no room for another raid right now. Try again in a moment.")
            ToaEntry.InvalidEntry -> mes("The tombs are not open.")
        }
    }

    private fun ProtectedAccess.leaveLobby() {
        val dest = ToaIsle.exit
        if (!collision.isZoneValid(dest)) {
            mes("The way out is blocked.")
            return
        }
        telejump(dest)
    }

    private fun ProtectedAccess.walkPath(path: ToaPath) {
        when (raids.choosePath(player, path)) {
            ToaRaids.PathChoice.Started,
            ToaRaids.PathChoice.Joined -> Unit
            ToaRaids.PathChoice.AlreadyDone -> mes("You have already completed this path.")
            ToaRaids.PathChoice.OtherPathChosen ->
                mes("You can't proceed as a different path has already been selected.")
            ToaRaids.PathChoice.LeaderFirst -> {
                val leader = raids.raidOf(player)?.leader?.player?.displayName ?: "party leader"
                mes("Your leader, $leader, must enter first.")
            }
            ToaRaids.PathChoice.NotInNexus -> Unit
        }
    }

    private fun ProtectedAccess.enterWardens() {
        when (raids.enterWardens(player)) {
            ToaRaids.WardensEntry.Entered -> Unit
            ToaRaids.WardensEntry.Sealed -> mes("The way is sealed. You must walk the paths first.")
            ToaRaids.WardensEntry.LeaderFirst -> mes("Your party leader must enter first.")
            ToaRaids.WardensEntry.NotInNexus -> Unit
        }
    }

    // The party overlay
    // -------------------------------------------------------------------------------

    /**
     * Opens and closes the lobby overlay as players come and go, and redraws it when it changes.
     */
    private fun tickLobby() {
        for (player in players) {
            val inLobby = ToaLobby.contains(player.coords)
            val known = player.accountId in overlays
            when {
                inLobby && !known -> openOverlay(player)
                !inLobby && known -> closeOverlay(player)
            }
            if (inLobby && known) {
                drawOverlay(player)
            }
        }
        // Players who logged out inside are dropped by the logout hook; this catches the rest.
        overlays.keys.removeIf { id -> players.none { it.accountId == id } }
    }

    private fun openOverlay(player: Player) {
        overlays[player.accountId] = ""
        player.ifOpenOverlay(toa_interfaces.lobby, components.toplevel_target_overlay_hud, eventBus)
        drawOverlay(player)
    }

    private fun drawOverlay(player: Player) {
        val party = parties.currentParty(player)
        val names =
            List(ToaParties.MAX_SIZE) { party?.members?.getOrNull(it)?.displayName ?: "-" }
                .joinToString("<br>")
        if (overlays[player.accountId] == names) {
            return
        }
        overlays[player.accountId] = names
        VarPlayerIntMapSetter.set(player, toa_varbits.party_status, if (party != null) 1 else 0)
        player.ifSetText(toa_components.lobby_names, names)
    }

    private fun closeOverlay(player: Player) {
        overlays.remove(player.accountId)
        player.ifCloseOverlay(toa_interfaces.lobby, eventBus)
        VarPlayerIntMapSetter.set(player, toa_varbits.party_status, 0)
        val (applied, current) = parties.forget(player)
        applied?.let { board.refreshViewers(it) }
        if (current != null && !current.insideRaid) {
            player.mes("You have left the lobby, so you have been removed from your party.")
            board.refreshViewers(current)
        }
    }

    // Logging out and in
    // ------------------------------------------------------------------------------

    private fun loggedOut(player: Player) {
        raids.leave(player, ToaExit.LoggedOut)
        if (overlays.remove(player.accountId) != null || parties.currentParty(player) != null) {
            val (applied, current) = parties.forget(player)
            applied?.let { board.refreshViewers(it) }
            current?.let { board.refreshViewers(it) }
        }
    }
}

/** Where the isle meets the lobby. */
object ToaIsle {
    /** The pyramid's entry, in the clearing of the Gauntlet's portal (it covers 2x2 tiles). */
    val ENTRANCE = CoordGrid(2129, 2891, 0)

    /** Where leaving the lobby puts a player: in front of the entry. */
    val exit = CoordGrid(2129, 2889, 0)
}
