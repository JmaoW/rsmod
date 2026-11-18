package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent

/**
 * Ver Sinhaza's notice board: the list of parties (interface 364) and the panel for one party (50),
 * drawn by the client from scripts the server calls with the rows as text.
 *
 * Contract, read from the cache's scripts (and the same as TSPS found):
 * - The list's Refresh, My party and Friends/Clan buttons resume children 0, 1 and 2 of
 *   `tob_partylist:contents`; each of its 45 rows (`tob_partylist:00`-`44`) has a plain op 1, "View
 *   party". Script 2340 draws a row from `leader|name|name|name|name|size|preferred size| preferred
 *   level|mode|age|`.
 * - Every button of the panel resumes a child of `tob_partydetails:universe`: 0 Back, 1 Refresh, 2
 *   Unblock, 3 Preferred size, 4 Preferred level, 5 Apply/Withdraw/Leave/Disband, 6 Mode, 7-11 the
 *   members (Kick/Quit, script 2319), 12-21 accept and 22-31 decline (2330).
 * - Script 2317 adds a member line, given what the viewer is (only a leader, 2, gets Kick and Quit
 *   party ops) and `name|combat|attack|strength|ranged|magic|defence|hitpoints|prayer|
 *   completions|`; 2321 adds an applicant's, 2323 the footer: what the viewer is (0-4), the
 *   preferred size and level, and the mode (enum 3852: 0 Normal).
 * - 2323 counts applicants as rows drawn minus five, so exactly five member lines are sent. A party
 *   of six to eight (the event allows it) lists its other members in chat instead.
 */
@Singleton
class TobBoard
@Inject
constructor(
    private val parties: TobParties,
    private val eventBus: EventBus,
    private val clock: MapClock,
) {
    object ListButton {
        const val REFRESH = 0
        const val MY_PARTY = 1
        const val FILTER = 2
    }

    object Button {
        const val BACK = 0
        const val REFRESH = 1
        const val UNBLOCK = 2
        const val SIZE = 3
        const val LEVEL = 4
        const val ACTION = 5
        const val MODE = 6
        const val MEMBER_FIRST = 7
        const val MEMBER_LAST = 11
        const val ACCEPT_FIRST = 12
        const val DECLINE_FIRST = 22
        const val LAST = 31
    }

    // The list
    // -------------------------------------------------------------------------------------------

    fun openList(player: Player) {
        val state = parties.stateOf(player)
        state.viewing = null
        VarPlayerIntMapSetter.set(
            player,
            tob_varps.my_controller,
            if (state.current != null) 0 else -1,
        )
        player.ifOpenMainModal(tob_interfaces.party_list, eventBus)
        player.ifSetEvents(tob_components.party_list_buttons, 0..2, IfEvent.PauseButton)
        for (row in tob_components.party_list_rows) {
            player.ifSetEvents(row, -1..-1, IfEvent.Op1)
        }
        sendList(player)
    }

    private fun sendList(player: Player) {
        val state = parties.stateOf(player)
        state.listed = parties.recruiting().filter { !it.insideRaid }
        for (row in 0 until TobParties.MAX_LISTED) {
            val party = state.listed.getOrNull(row)
            player.runClientScript(ROW_SCRIPT, row, party?.let { listRow(player, it) } ?: "")
        }
    }

    private fun listRow(player: Player, party: TobParty): String {
        val leader = party.leader?.displayName ?: ""
        val fields = ArrayList<Any>()
        fields += if (party.isMember(player)) "<col=FFFFFF>$leader" else leader
        for (i in 1 until TobParties.CLIENT_SLOTS) {
            fields += party.members.getOrNull(i)?.displayName ?: ""
        }
        fields += party.members.size
        fields += party.preferredSize
        fields += party.preferredLevel
        fields += MODE_NORMAL
        fields += clock.cycle - party.createdCycle
        return fields.joinToString("|") + "|"
    }

    fun clickList(player: Player, button: Int) {
        when (button) {
            ListButton.REFRESH -> sendList(player)
            ListButton.MY_PARTY -> makeOrViewParty(player)
            ListButton.FILTER -> {
                val on = player.vars[tob_varbits.list_filter]
                VarPlayerIntMapSetter.set(player, tob_varbits.list_filter, if (on == 0) 1 else 0)
                sendList(player)
            }
        }
    }

    private fun makeOrViewParty(player: Player) {
        val state = parties.stateOf(player)
        if (state.current == null) {
            if (parties.isListFull()) {
                player.mes("The list of parties is full. Please come back later or apply to one.")
                return
            }
            state.applied?.let { applied ->
                parties.withdraw(applied, player)
                refreshViewers(applied)
            }
            state.viewing = parties.create(player)
        } else {
            state.viewing = state.current
        }
        openDetails(player)
    }

    fun selectParty(player: Player, row: Int) {
        val state = parties.stateOf(player)
        val party = state.listed.getOrNull(row) ?: return
        if (state.current?.insideRaid == true) {
            player.mes("You should join your party in the Theatre of Blood.")
            return
        }
        if (!parties.isListed(party) || party.insideRaid) {
            player.mes("That party is no longer recruiting.")
            return
        }
        state.viewing = party
        openDetails(player)
    }

    // One party
    // ------------------------------------------------------------------------------------------

    fun openDetails(player: Player) {
        val state = parties.stateOf(player)
        val party = state.viewing
        if (party == null || party.leader == null) {
            openList(player)
            return
        }
        player.ifOpenMainModal(tob_interfaces.party_details, eventBus)
        state.viewingValue =
            when {
                party.isLeader(player) -> TobPartyView.Leader.value
                party.isMember(player) -> TobPartyView.Member.value
                party.isApplicant(player) -> TobPartyView.Applicant.value
                party.isBlocked(player) -> TobPartyView.Declined.value
                else -> TobPartyView.NonMember.value
            }
        for (slot in 0 until TobParties.CLIENT_SLOTS) {
            val member = party.members.getOrNull(slot)
            player.runClientScript(
                MEMBER_SCRIPT,
                state.viewingValue,
                member?.let { statLine(it, it === player) } ?: "",
            )
        }
        for (applicant in party.applicants.take(TobParties.MAX_APPLICANTS)) {
            player.runClientScript(APPLICANT_SCRIPT, statLine(applicant, applicant === player))
        }
        player.runClientScript(
            FOOTER_SCRIPT,
            state.viewingValue,
            party.preferredSize,
            party.preferredLevel,
            MODE_NORMAL,
        )
        player.ifSetEvents(tob_components.details_buttons, 0..Button.LAST, IfEvent.PauseButton)
        val extra = party.members.drop(TobParties.CLIENT_SLOTS)
        if (extra.isNotEmpty()) {
            player.mes("Also in this party: ${extra.joinToString(", ") { it.displayName }}.")
        }
    }

    private fun statLine(player: Player, self: Boolean): String {
        val fields =
            listOf(
                (if (self) "<col=FFFFFF>" else "") + player.displayName,
                player.combatLevel,
                player.statBase(stats.attack),
                player.statBase(stats.strength),
                player.statBase(stats.ranged),
                player.statBase(stats.magic),
                player.statBase(stats.defence),
                player.statBase(stats.hitpoints),
                player.statBase(stats.prayer),
                player.vars[tob_varps.completions],
            )
        return fields.joinToString("|") + "|"
    }

    fun refreshViewers(party: TobParty) {
        for (viewer in party.members + party.applicants) {
            refreshIfViewing(viewer, party)
        }
    }

    private fun refreshIfViewing(player: Player, party: TobParty? = null) {
        val state = parties.stateOf(player)
        if (!player.ui.containsModal(tob_interfaces.party_details)) {
            return
        }
        if (party != null && state.viewing !== party) {
            return
        }
        openDetails(player)
    }

    fun clickDetails(player: Player, button: Int) {
        val state = parties.stateOf(player)
        val party = state.viewing
        if (party == null || party.leader == null) {
            openList(player)
            return
        }
        val leader = party.isLeader(player)
        when {
            button == Button.BACK -> openList(player)
            button == Button.REFRESH -> openDetails(player)
            button == Button.UNBLOCK && leader -> {
                party.blocked.clear()
                player.mes("All players declined by this party may now apply again.")
                openDetails(player)
            }
            button == Button.SIZE && leader -> {
                party.preferredSize = (party.preferredSize + 1) % (parties.cap + 1)
                refreshViewers(party)
                openDetails(player)
            }
            button == Button.LEVEL && leader -> {
                val next = LEVELS.indexOf(party.preferredLevel) + 1
                party.preferredLevel = LEVELS[next % LEVELS.size]
                refreshViewers(party)
                openDetails(player)
            }
            button == Button.MODE && leader ->
                player.mes("Only the Normal Mode of the Theatre is open in this event.")
            button == Button.ACTION -> action(player, party)
            button in Button.MEMBER_FIRST..Button.MEMBER_LAST ->
                kick(player, party, party.members.getOrNull(button - Button.MEMBER_FIRST))
            button in Button.ACCEPT_FIRST..Button.LAST && leader -> {
                val accept = button < Button.DECLINE_FIRST
                val base = if (accept) Button.ACCEPT_FIRST else Button.DECLINE_FIRST
                answer(player, party, party.applicants.getOrNull(button - base), accept)
            }
        }
    }

    /** The panel's one context button: apply, withdraw, leave or disband. */
    private fun action(player: Player, party: TobParty) {
        val state = parties.stateOf(player)
        when (state.viewingValue) {
            TobPartyView.NonMember.value -> apply(player, party)
            TobPartyView.Member.value -> {
                if (party.insideRaid) {
                    player.mes("You should join your party in the Theatre of Blood.")
                    return
                }
                if (parties.leave(party, player)) {
                    player.mes("You have left the party.")
                    refreshViewers(party)
                    openList(player)
                }
            }
            TobPartyView.Leader.value -> {
                if (party.insideRaid) {
                    return
                }
                val others = (party.members + party.applicants).filter { it !== player }
                parties.disband(party)
                for (other in others) {
                    other.mes("Your party has disbanded.")
                    refreshIfViewing(other)
                }
                openList(player)
            }
            TobPartyView.Applicant.value -> {
                if (parties.withdraw(party, player)) {
                    player.mes("You have withdrawn your application.")
                    refreshViewers(party)
                    openDetails(player)
                }
            }
            TobPartyView.Declined.value -> player.mes("You have been declined by this party.")
        }
    }

    private fun apply(player: Player, party: TobParty) {
        val state = parties.stateOf(player)
        if (state.current?.insideRaid == true) {
            player.mes("You should join your party in the Theatre of Blood.")
            return
        }
        if (party.isBlocked(player)) {
            player.mes("You have been declined by this party.")
            return
        }
        state.current?.let { old ->
            parties.leave(old, player)
            refreshViewers(old)
        }
        state.applied?.let { previous ->
            if (previous !== party && parties.withdraw(previous, player)) {
                refreshViewers(previous)
            }
        }
        when (parties.apply(party, player)) {
            TobPartyResult.Done -> {
                player.mes("You have applied to join the party of ${party.leader?.displayName}.")
                party.leader?.mes("${player.displayName} has applied to join your party.")
                refreshViewers(party)
                openDetails(player)
            }
            TobPartyResult.AlreadyApplied -> player.mes("You have already applied to that party.")
            TobPartyResult.Full -> player.mes("That party has too many applicants.")
            else -> player.mes("That party is no longer recruiting.")
        }
    }

    private fun kick(player: Player, party: TobParty, target: Player?) {
        if (target == null || party.insideRaid) {
            return
        }
        if (target === player) {
            if (parties.leave(party, player)) {
                player.mes("You have left the party.")
                refreshViewers(party)
                openList(player)
            }
            return
        }
        if (!party.isLeader(player)) {
            return
        }
        parties.leave(party, target)
        target.mes("You have been removed from the party of ${player.displayName}.")
        player.mes("You have removed ${target.displayName} from your party.")
        refreshIfViewing(target, party)
        openDetails(player)
        refreshViewers(party)
    }

    private fun answer(player: Player, party: TobParty, applicant: Player?, accept: Boolean) {
        if (applicant == null) {
            return
        }
        val result = parties.answer(party, applicant, accept)
        if (result == TobPartyResult.Full) {
            player.mes("Your party is full.")
            return
        }
        if (result != TobPartyResult.Done) {
            return
        }
        if (accept) {
            player.mes("You have accepted ${applicant.displayName} into your party.")
            applicant.mes("You have been accepted into the party of ${party.leader?.displayName}.")
        } else {
            player.mes("You have declined ${applicant.displayName}'s application.")
            applicant.mes(
                "Your application to the party of ${party.leader?.displayName} was declined."
            )
        }
        refreshViewers(party)
        refreshIfViewing(applicant, party)
    }

    companion object {
        const val ROW_SCRIPT = 2340
        const val MEMBER_SCRIPT = 2317
        const val APPLICANT_SCRIPT = 2321
        const val FOOTER_SCRIPT = 2323

        /** Enum 3852: 0 is the Normal Mode. */
        const val MODE_NORMAL = 0

        /** The preferred combat levels the leader cycles through (0 is any). */
        private val LEVELS = listOf(0, 70, 90, 100, 110, 120, 126)
    }
}
