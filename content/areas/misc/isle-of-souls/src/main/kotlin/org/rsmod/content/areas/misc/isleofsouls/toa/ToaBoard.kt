package org.rsmod.content.areas.misc.isleofsouls.toa

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
 * The obelisk's party board: the list of recruiting parties (interface 772) and the panel for one
 * party (774), whose lists the client draws from scripts the server calls with the rows as text.
 *
 * Contract, read from the cache's scripts and from TSPS's live testing:
 * - Both interfaces are built from pause buttons: the buttons and rows are children of one
 *   component, and a click arrives as `resume_pausebutton` with the child index.
 * - The list's rows come from script 6601 (row index, then `leader|members...|size|kc|invocations|
 *   level|mode|age|`).
 * - The panel's member rows come from 6722 (what the viewer is, then a stat line), applicants from
 *   6727, and the panel's header, settings and invocation buttons from 6729 (what the viewer is,
 *   the wanted completions, the number of invocations, the raid level, the tab and the three
 *   bitmaps).
 * - The panel's buttons are numbered from its scripts: 0-11 fixed (see [Button]), 12-19 members,
 *   36-43 accept and 44-51 decline for applicants, and from 52 the invocations in board order.
 * - A panel must be reopened to be redrawn, because the row scripts count into varcs that only the
 *   interface's own onload resets.
 */
@Singleton
class ToaBoard
@Inject
constructor(
    private val parties: ToaParties,
    private val eventBus: EventBus,
    private val clock: MapClock,
) {
    /** The party list's buttons (children 0-2 of its button component). */
    object ListButton {
        const val REFRESH = 0
        const val MY_PARTY = 1
        const val FILTER = 2
    }

    /** The details panel's fixed buttons. */
    object Button {
        const val OPEN_LIST = 0
        const val REFRESH = 1
        const val UNBLOCK = 2
        const val SET_COMPLETIONS = 3
        const val MEMBER_OPTION = 4
        const val CLEAR_ALL = 5
        const val LOAD_PRESET = 6
        const val SAVE_PRESET = 7
        const val TAB_FIRST = 8
        const val TAB_LAST = 11
        const val MEMBER_FIRST = 12
        const val MEMBER_LAST = 19
        const val ACCEPT_FIRST = 36
        const val DECLINE_FIRST = 44
        const val APPLICANT_LAST = 51
        const val INVOCATION_FIRST = 52

        /** The last child the panel's pause buttons are enabled for. */
        const val LAST = 97
    }

    // The party list
    // ----------------------------------------------------------------------------------

    fun openList(player: Player) {
        val state = parties.stateOf(player)
        state.viewing = null
        VarPlayerIntMapSetter.set(
            player,
            toa_varps.current_party,
            if (state.current != null) 0 else -1,
        )
        player.ifOpenMainModal(toa_interfaces.party_list, eventBus)
        player.ifSetEvents(toa_components.party_list_buttons, 0..2, IfEvent.PauseButton)
        player.ifSetEvents(
            toa_components.party_list_rows,
            0 until ToaParties.MAX_LISTED,
            IfEvent.PauseButton,
        )
        sendList(player)
    }

    private fun sendList(player: Player) {
        val state = parties.stateOf(player)
        state.listed = parties.recruiting()
        for (row in 0 until ToaParties.MAX_LISTED) {
            val party = state.listed.getOrNull(row)
            player.runClientScript(ROW_SCRIPT, row, party?.let { listRow(player, it) } ?: "")
        }
    }

    private fun listRow(player: Player, party: ToaParty): String {
        val leader = party.leader?.displayName ?: ""
        val fields = ArrayList<Any>()
        fields += if (party.isMember(player)) "<col=FFFFFF>$leader" else leader
        for (i in 1 until ToaParties.MAX_SIZE) {
            fields += party.members.getOrNull(i)?.displayName ?: ""
        }
        fields += party.members.size
        fields += party.completionsWanted
        fields += party.settings.activeCount
        fields += party.settings.raidLevel
        fields += party.settings.mode.label
        fields += clock.cycle - party.createdCycle
        return fields.joinToString("|") + "|"
    }

    fun clickList(player: Player, button: Int) {
        when (button) {
            ListButton.REFRESH -> sendList(player)
            ListButton.MY_PARTY -> makeOrViewParty(player)
            ListButton.FILTER -> {
                val on = player.vars[toa_varbits.partylist_filter]
                VarPlayerIntMapSetter.set(
                    player,
                    toa_varbits.partylist_filter,
                    if (on == 0) 1 else 0,
                )
                sendList(player)
            }
        }
    }

    private fun makeOrViewParty(player: Player) {
        val state = parties.stateOf(player)
        if (state.current == null) {
            if (parties.isListFull()) {
                player.mes(
                    "The list of lobby parties is currently full. Please come back later or " +
                        "apply to an existing party."
                )
                return
            }
            state.applied?.let { applied ->
                parties.withdraw(applied, player)
                applied.leader?.let { refreshIfViewing(it, applied) }
            }
            state.viewing = parties.create(player)
            state.tab = 1
        } else {
            state.viewing = state.current
            state.tab = 0
        }
        openDetails(player)
    }

    /** A row of the list was clicked: look at that party. */
    fun selectParty(player: Player, row: Int) {
        val state = parties.stateOf(player)
        val party = state.listed.getOrNull(row) ?: return
        if (state.current?.insideRaid == true) {
            player.mes("You should join your party in the tombs.")
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
    // ---------------------------------------------------------------------------------------

    /** Opens (or reopens) the details panel on the party the player is looking at, and draws it. */
    fun openDetails(player: Player) {
        val state = parties.stateOf(player)
        val party = state.viewing
        if (party == null || party.leader == null) {
            openList(player)
            return
        }
        player.ifOpenMainModal(toa_interfaces.party_details, eventBus)
        state.viewingValue =
            when {
                party.isLeader(player) -> PartyView.Leader.value
                party.isMember(player) -> PartyView.Member.value
                party.isApplicant(player) -> PartyView.Applicant.value
                party.isBlocked(player) -> PartyView.Declined.value
                else -> PartyView.NonMember.value
            }
        for (slot in 0 until ToaParties.MAX_SIZE) {
            val member = party.members.getOrNull(slot)
            player.runClientScript(
                MEMBER_SCRIPT,
                state.viewingValue,
                member?.let { statLine(it, it === player) } ?: "",
            )
        }
        for (applicant in party.applicants) {
            player.runClientScript(APPLICANT_SCRIPT, statLine(applicant, applicant === player))
        }
        val settings = party.settings
        player.runClientScript(
            INIT_SCRIPT,
            state.viewingValue,
            party.completionsWanted,
            settings.activeCount,
            settings.raidLevel,
            state.tab,
            settings.words[0],
            settings.words[1],
            settings.words[2],
        )
        player.ifSetEvents(toa_components.details_buttons, 0..Button.LAST, IfEvent.PauseButton)
        player.ifSetEvents(toa_components.details_reward_info, 0..0, IfEvent.Op1)
        if (party.isLeader(player)) {
            player.ifSetEvents(
                toa_components.details_presets,
                0 until ToaParties.PRESET_COUNT,
                IfEvent.Op1,
                IfEvent.Op2,
            )
        }
    }

    /**
     * `name|combat|attack|strength|ranged|magic|defence|hitpoints|prayer|entry / normal / expert|`
     */
    private fun statLine(player: Player, self: Boolean): String {
        val counts = ToaMode.entries.map { player.vars[toa_varps.completions[it.ordinal]] }
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
                "${counts[0]} / ${counts[1]} / ${counts[2]}",
            )
        return fields.joinToString("|") + "|"
    }

    /** Redraws the panel of everyone who is looking at [party]. */
    fun refreshViewers(party: ToaParty) {
        for (viewer in party.members + party.applicants) {
            refreshIfViewing(viewer, party)
        }
    }

    private fun refreshIfViewing(player: Player, party: ToaParty? = null) {
        val state = parties.stateOf(player)
        if (!player.ui.containsModal(toa_interfaces.party_details)) {
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
            button == Button.OPEN_LIST -> openList(player)
            button == Button.REFRESH -> openDetails(player)
            button == Button.UNBLOCK && leader -> {
                party.blocked.clear()
                player.mes(
                    "All players rejected from this party have been unblocked and may apply again."
                )
                openDetails(player)
            }
            button == Button.SET_COMPLETIONS && leader ->
                player.mes("Setting a preferred number of completions is not available here.")
            button == Button.MEMBER_OPTION -> memberOption(player, party)
            button == Button.CLEAR_ALL && leader -> {
                party.settings.clear()
                refreshViewers(party)
                openDetails(player)
            }
            button == Button.LOAD_PRESET && leader -> loadPreset(player, party)
            button == Button.SAVE_PRESET && leader -> savePreset(player, party)
            button in Button.TAB_FIRST..Button.TAB_LAST -> {
                state.tab = button - Button.TAB_FIRST
                openDetails(player)
            }
            button in Button.MEMBER_FIRST..Button.MEMBER_LAST && leader ->
                kick(player, party, party.members.getOrNull(button - Button.MEMBER_FIRST))
            button in Button.ACCEPT_FIRST..Button.APPLICANT_LAST && leader -> {
                val accept = button < Button.DECLINE_FIRST
                val base = if (accept) Button.ACCEPT_FIRST else Button.DECLINE_FIRST
                answer(player, party, party.applicants.getOrNull(button - base), accept)
            }
            button >= Button.INVOCATION_FIRST && leader ->
                toggle(player, party, parties.catalog.bySlot(button - Button.INVOCATION_FIRST))
        }
    }

    private fun toggle(player: Player, party: ToaParty, invocation: Invocation?) {
        if (invocation == null || party.insideRaid) {
            return
        }
        when (val result = party.settings.toggle(invocation)) {
            is InvocationToggle.NeedsOther ->
                player.mes(
                    "You cannot activate this invocation without first enabling " +
                        "<col=ff0000>${result.needs.name}</col>."
                )
            InvocationToggle.NotOffered ->
                player.mes("That invocation is not available in this event.")
            else -> refreshViewers(party)
        }
    }

    /**
     * The panel's one context button: apply, withdraw, leave or disband, depending on who looks.
     */
    private fun memberOption(player: Player, party: ToaParty) {
        val state = parties.stateOf(player)
        when (state.viewingValue) {
            PartyView.NonMember.value -> apply(player, party)
            PartyView.Member.value -> {
                if (state.current?.insideRaid == true) {
                    player.mes("You should join your party in the tombs.")
                    return
                }
                if (parties.leave(party, player)) {
                    player.mes(
                        "You have left the party of ${party.leader?.displayName ?: "your leader"}."
                    )
                    refreshViewers(party)
                    openList(player)
                }
            }
            PartyView.Leader.value -> {
                if (party.insideRaid) {
                    return
                }
                val members = party.members.toList()
                parties.disband(party)
                members
                    .filter { it !== player }
                    .forEach {
                        it.mes("Your party has disbanded.")
                        refreshIfViewing(it)
                    }
                openList(player)
            }
            PartyView.Applicant.value -> {
                if (parties.withdraw(party, player)) {
                    player.mes("You have withdrawn your party application.")
                    refreshViewers(party)
                    openDetails(player)
                }
            }
            PartyView.Declined.value -> player.mes("You have been declined by this party.")
        }
    }

    private fun apply(player: Player, party: ToaParty) {
        val state = parties.stateOf(player)
        if (state.current?.insideRaid == true) {
            player.mes("You should join your party in the tombs.")
            return
        }
        if (party.isBlocked(player)) {
            player.mes("You have been declined by this party.")
            return
        }
        state.current?.let { old ->
            // Applying elsewhere leaves the old party, as the real board does after asking.
            parties.leave(old, player)
            refreshViewers(old)
        }
        state.applied?.let { previous ->
            if (previous !== party && parties.withdraw(previous, player)) {
                refreshViewers(previous)
            }
        }
        when (parties.apply(party, player)) {
            PartyResult.Done -> {
                player.mes("You have applied to join the party of ${party.leader?.displayName}.")
                state.tab = 1
                refreshViewers(party)
                openDetails(player)
            }
            PartyResult.AlreadyApplied -> player.mes("You have already applied to that party.")
            PartyResult.Full -> player.mes("That party has too many applicants.")
            else -> player.mes("That party is no longer recruiting.")
        }
    }

    private fun kick(player: Player, party: ToaParty, target: Player?) {
        if (target == null) {
            return
        }
        if (target === player) {
            if (parties.leave(party, player)) {
                player.mes("You have left your party.")
                refreshViewers(party)
                openList(player)
            }
            return
        }
        parties.leave(party, target)
        target.mes("You have been kicked from the party of ${player.displayName}.")
        player.mes("You have kicked ${target.displayName} from your party.")
        refreshIfViewing(target, party)
        openDetails(player)
        refreshViewers(party)
    }

    private fun answer(player: Player, party: ToaParty, applicant: Player?, accept: Boolean) {
        if (applicant == null) {
            return
        }
        val result = parties.answer(party, applicant, accept)
        if (result == PartyResult.Full) {
            player.mes("Your party is full.")
            return
        }
        if (result != PartyResult.Done) {
            return
        }
        if (accept) {
            player.mes("You have accepted ${applicant.displayName} into your party.")
            applicant.mes(
                "Your application to the party of ${party.leader?.displayName} has been accepted."
            )
        } else {
            player.mes("You have declined the party application from ${applicant.displayName}.")
            applicant.mes(
                "Your application to the party of ${party.leader?.displayName} has been declined."
            )
        }
        refreshViewers(party)
        refreshIfViewing(applicant, party)
    }

    // Presets
    // -----------------------------------------------------------------------------------------

    private fun selectedPreset(player: Player): Int = player.vars[toa_varbits.preset_selected] - 1

    private fun presetOf(player: Player, slot: Int): IntArray =
        IntArray(InvocationSet.WORDS) { player.vars[toa_varps.presets[slot][it]] }

    private fun writePreset(player: Player, slot: Int, words: IntArray) {
        for (word in 0 until InvocationSet.WORDS) {
            VarPlayerIntMapSetter.set(player, toa_varps.presets[slot][word], words[word])
        }
    }

    private fun savePreset(player: Player, party: ToaParty) {
        val slot = selectedPreset(player)
        if (slot !in 0 until ToaParties.PRESET_COUNT) {
            player.mes("You do not have a valid preset selected to save to.")
            return
        }
        writePreset(player, slot, party.settings.words)
        VarPlayerIntMapSetter.set(player, toa_varbits.preset_selected, 0)
        player.mes("Your preset has been saved.")
        openDetails(player)
    }

    private fun loadPreset(player: Player, party: ToaParty) {
        val slot = selectedPreset(player)
        VarPlayerIntMapSetter.set(player, toa_varbits.preset_selected, 0)
        if (slot !in 0 until ToaParties.PRESET_COUNT) {
            player.mes("You do not have a valid preset selected to load from.")
            return
        }
        val preset = presetOf(player, slot)
        if (preset.all { it == 0 }) {
            player.mes("You do not have any invocations stored in this preset.")
            return
        }
        party.settings.load(preset)
        player.mes("Your preset has been loaded.")
        refreshViewers(party)
        openDetails(player)
    }

    /** A click on a preset slot: op 1 selects it, op 2 clears it. */
    fun clickPreset(player: Player, slot: Int, clear: Boolean) {
        val party = parties.stateOf(player).viewing
        if (party == null || !party.isLeader(player) || slot !in 0 until ToaParties.PRESET_COUNT) {
            return
        }
        if (!clear) {
            val selected = player.vars[toa_varbits.preset_selected]
            VarPlayerIntMapSetter.set(
                player,
                toa_varbits.preset_selected,
                if (selected == slot + 1) 0 else slot + 1,
            )
        } else {
            writePreset(player, slot, IntArray(InvocationSet.WORDS))
            player.mes("Your preset has been cleared.")
        }
        openDetails(player)
    }

    companion object {
        const val ROW_SCRIPT = 6601
        const val MEMBER_SCRIPT = 6722
        const val APPLICANT_SCRIPT = 6727
        const val INIT_SCRIPT = 6729
    }
}
