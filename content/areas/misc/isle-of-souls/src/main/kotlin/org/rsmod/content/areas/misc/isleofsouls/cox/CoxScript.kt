package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onIfPauseButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Mount Quidamortem and the ways round the raid: the isle's portal there and back, the fence that
 * keeps the mountain apart from the rest of Kebos, Xeric's talisman, the recruiting board, the
 * entrance, and inside the raid the start button, passages, holes, ropes, steps and the bank chest.
 */
class CoxScript
@Inject
constructor(
    private val board: CoxBoard,
    private val parties: CoxParties,
    private val raids: CoxRaids,
    private val hud: CoxHud,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val collision: CollisionFlagMap,
    private val bankAccess: BankAccess,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onGameStartup {
            placePortals()
            fence()
        }
        onOpLoc1(cox_locs.portal) { portal(it.loc.coords) }
        onOpLoc1(cox_locs.portal_back) { portal(it.loc.coords) }

        onOpLoc1(cox_locs.recruiting_board) { board.openBoard(player) }
        onIfPauseButton(cox_components.list_buttons) { board.clickBoard(player, comsub) }
        onIfPauseButton(cox_components.list_rows) { board.clickRow(player, comsub) }
        onIfPauseButton(cox_components.details_buttons) { board.clickParty(player, comsub) }
        onOpLoc1(cox_locs.entrance) { enter() }
        onOpLoc1(cox_locs.lobby_bank) { bankAccess.open(this) }
        onOpLoc1(cox_locs.minecart) {
            mes("The minecart doesn't run while the mountain is closed off.")
        }
        onOpLoc1(cox_locs.score_board) { mes("No Challenge Mode times have been recorded yet.") }

        onOpHeld3(cox_objs.xeric_talisman) { rubTalisman() }
        onOpHeld4(cox_objs.xeric_talisman) {
            mes("Your talisman has unlimited charges in this league.")
        }
        onOpHeld1(cox_objs.ancient_tablet) { readTablet() }

        // Inside.
        onIfOverlayButton(cox_components.sidepanel_start) { startRaid(player) }
        onOpLoc1(cox_locs.passage) { passage(it.loc) }
        for (hole in listOf(cox_locs.hole_down, cox_locs.hole_down_lower, cox_locs.boss_hole)) {
            onOpLoc1(hole) { descend() }
        }
        for (rope in listOf(cox_locs.rope_up, cox_locs.rope_up_lower, cox_locs.boss_rope)) {
            onOpLoc1(rope) { climb() }
        }
        onOpLoc1(cox_locs.exit_steps) { leaveBySteps() }
        // The first room's steps are a multi-loc: its ops reach the server on the base loc.
        onOpLoc1(cox_locs.exit_steps_multi) { leaveBySteps() }
        onOpLoc1(cox_locs.inside_bank) {
            if (raids.raidOf(player)?.started == true) {
                mes("The bank chest can only be used before the raid begins.")
            } else {
                bankAccess.open(this)
            }
        }
        onOpLoc1(cox_locs.energy_well) {
            player.runEnergy = org.rsmod.api.config.constants.run_max_energy
            mes("You feel refreshed.")
        }

        onEvent<GameLifecycle.StartCycle> { raids.tick() }
        onEvent<PlayerDeathEvents.Died> { raids.died(player) }
        onPlayerLogout {
            raids.leave(player, CoxExit.LoggedOut)
            parties.partyOf(player)?.let { party ->
                if (party.raid == null) {
                    parties.leave(party, player)
                    board.refreshViewers(party)
                }
            }
            parties.forget(player)
        }
    }

    // The isle and the mountain
    // ---------------------------------------------------------------------------------------------

    private fun placePortals() {
        for ((tile, type) in
            listOf(
                CoxMountain.ISLE_PORTAL to cox_locs.portal,
                CoxMountain.MOUNTAIN_PORTAL to cox_locs.portal_back,
            )) {
            locRepo.add(
                tile,
                locTypes[type],
                duration = Int.MAX_VALUE,
                angle = LocAngle.South,
                shape = LocShape.CentrepieceStraight,
            )
        }
    }

    /** Blocks the mountain's paths down into Kebos: one row the flood-fill showed seals it. */
    private fun fence() {
        for (x in CoxMountain.FENCE_X) {
            collision.add(x, CoxMountain.FENCE_Z, 0, CollisionFlag.BLOCK_WALK)
        }
    }

    private fun ProtectedAccess.portal(at: CoordGrid) {
        when (at) {
            CoxMountain.ISLE_PORTAL -> {
                mes("You step through the portal to Mount Quidamortem.")
                telejump(CoxMountain.ARRIVAL)
            }
            CoxMountain.MOUNTAIN_PORTAL -> telejump(CoxMountain.ISLE_RETURN)
        }
    }

    private suspend fun ProtectedAccess.rubTalisman() {
        var go = false
        startDialogue {
            go =
                choice5(
                    "Xeric's Lookout",
                    false,
                    "Xeric's Glade",
                    false,
                    "Xeric's Inferno",
                    false,
                    "Xeric's Heart",
                    false,
                    "Xeric's Honour",
                    true,
                    title = "Where would you like to teleport to?",
                )
        }
        if (!go) {
            mes("That part of Kourend is not open in this league.")
            return
        }
        if (player.vars[CoxTalismanVars.honour_unlocked] == 0) {
            mes(
                "You need to read an ancient tablet from the Chambers of Xeric to use this teleport."
            )
            return
        }
        if (raids.raidOf(player) != null) {
            mes("You cannot teleport from the Chambers of Xeric.")
            return
        }
        CoxTalisman.honourUsed(player)
        telejump(CoxMountain.HONOUR)
    }

    private suspend fun ProtectedAccess.readTablet() {
        if (player.vars[CoxTalismanVars.honour_unlocked] != 0) {
            mes("You have already learnt the secrets of the tablet.")
            return
        }
        player.invDel(player.inv, objTypes[cox_objs.ancient_tablet], 1)
        VarPlayerIntMapSetter.set(player, CoxTalismanVars.honour_unlocked, 1)
        startDialogue {
            mesbox(
                "You read the tablet and learn how to use Xeric's talisman to reach " +
                    "Xeric's Honour."
            )
        }
    }

    private suspend fun ProtectedAccess.enter() {
        val party = parties.partyOf(player)
        if (party == null) {
            startDialogue {
                mesbox("You need to be in a raiding party to enter. Read the recruiting board.")
            }
            return
        }
        if (player.vars[cox_varbits.itemloss_warning] == 0) {
            var accept = false
            startDialogue {
                mesbox(
                    "Items left in a dungeon cannot be retrieved. Participants may be ejected at " +
                        "any time. Don't risk dropping items that you want to keep."
                )
                accept =
                    choice2("Accept warning and proceed.", true, "Cancel.", false, title = "Enter?")
            }
            if (!accept) return
            VarPlayerIntMapSetter.set(player, cox_varbits.itemloss_warning, 1)
        }
        val building = party.raid == null
        if (building && party.isLeader(player)) {
            mes("Please wait a few moments as your raid is being constructed...")
        }
        when (raids.enter(player)) {
            CoxEntry.Entered -> Unit
            CoxEntry.NoParty -> mes("You need to be in a raiding party to enter.")
            CoxEntry.LeaderFirst ->
                startDialogue {
                    mesbox("Your party leader, ${party.leader?.displayName}, must go in first.")
                }
            CoxEntry.AlreadyStarted -> mes("Your party's raid has already begun without you.")
            CoxEntry.NoSpace ->
                mes("There is no room for another raid right now. Try again shortly.")
        }
    }

    // Inside
    // ---------------------------------------------------------------------------------------------

    private fun startRaid(player: org.rsmod.game.entity.Player) {
        val raid = raids.raidOf(player) ?: return
        if (!raid.isLeader(player)) {
            player.mes("Only your party's leader may start the raid.")
            return
        }
        if (raids.start(player)) {
            for (member in raid.active) {
                if (member.player !== player) member.player.mes("Your raid has begun!")
            }
        }
    }

    private suspend fun ProtectedAccess.passage(loc: BoundLocInfo) {
        val raid = raids.raidOf(player) ?: return
        val width = loc.adjustedWidth
        val length = loc.adjustedLength
        val footprint =
            (0 until width).flatMap { dx ->
                (0 until length).map { dz -> loc.coords.translate(dx, dz) }
            }
        when (raids.passage(player, footprint)) {
            CoxRaids.PassResult.Moved,
            CoxRaids.PassResult.NotHere -> Unit
            CoxRaids.PassResult.NotStarted ->
                startDialogue {
                    if (raid.isLeader(player)) {
                        mesbox(
                            "Use the controls on the <col=7f0000>side-panel</col><br>when you are " +
                                "ready to begin the raid."
                        )
                    } else {
                        mesbox(
                            "You cannot proceed until ${raid.leader()?.displayName} has started the raid."
                        )
                    }
                }
            CoxRaids.PassResult.Blocked -> mes("The way on is blocked.")
        }
    }

    private suspend fun ProtectedAccess.descend() {
        anim(cox_seqs.climb_down)
        delay(1)
        raids.descend(player)
    }

    private suspend fun ProtectedAccess.climb() {
        anim(cox_seqs.climb_up)
        delay(1)
        raids.climb(player)
    }

    private suspend fun ProtectedAccess.leaveBySteps() {
        if (raids.raidOf(player) == null) return
        var leave = false
        startDialogue {
            leave =
                choice2("Leave.", true, "Stay.", false, title = "You won't be able to re-enter.")
        }
        if (leave) raids.leave(player, CoxExit.Steps)
    }
}

/** Mount Quidamortem's tiles (picked off the cache map with the census tools). */
object CoxMountain {
    /** The isle's portal: in the Soul Wars lobby, east of the Theatre of Blood's. */
    val ISLE_PORTAL = CoordGrid(2225, 2821, 0)
    val ISLE_RETURN = CoordGrid(2225, 2820, 0)

    /** The mountain's portal back, by the recruiting boards. */
    val MOUNTAIN_PORTAL = CoordGrid(1240, 3559, 0)
    val ARRIVAL = CoordGrid(1240, 3558, 0)

    /** Xeric's Honour, on the mountain. */
    val HONOUR = CoordGrid(1254, 3559, 0)

    /** A row of blocked tiles across every path down (flood-filled from the board: it seals). */
    const val FENCE_Z = 3532
    val FENCE_X = 1205..1290
}

internal object CoxTalismanVars : VarBitReferences() {
    /** Read the ancient tablet: Xeric's Honour works. */
    val honour_unlocked = find("raids_guide_travel_unlock")
}

/** League task hooks for the talisman (set by the task script). */
object CoxTalisman {
    var honourUsed: (org.rsmod.game.entity.Player) -> Unit = {}
}
