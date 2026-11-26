package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifOpenMainModal
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent

/**
 * The recruiting board (interface 499) and the party page (507), drawn by the client from rows the
 * server sends as text. Read from this client's scripts (see `docs/isle_of_souls_cox_plan.md`):
 * - Board: script 1566 draws row `r` (0-39) from `leader|members|preferred size|preferred combat|
 *   preferred total|scaling|challenge|age|`; each row resumes `raids_lobby_partylist:list` child r.
 *   Refresh and Make party / My party resume children 0 and 1 of `:contents` (enum 1538). Varp
 *   `raids_party_groupholder` is -1 without a party (the button reads "Make party").
 * - Party page: script 1517 adds a member from `name|combat|total|kill count|` and the 23 skills
 *   (in the page's order), script 1524 the header: what the viewer is (0 outsider, 1 member, 2
 *   leader), advertised, adverts left and the title. Its buttons resume children of `:contents`
 *   (enum 1539): 0 Join/Leave, 1 Advertise/De-list, 2 Disband, 3 Refresh, 4 Back, 5 preferred size,
 *   6 preferred combat, 7 preferred total, 8 scaling, 9 Challenge Mode, 10 map layout. The
 *   preferences show from varbits `raids_lobby_partysize`, `_mincombat`, `_minskilltotal` and
 *   `raids_challenge_mode`.
 */
@Singleton
class CoxBoard
@Inject
constructor(
    private val parties: CoxParties,
    private val eventBus: EventBus,
    private val clock: MapClock,
    private val access: ProtectedAccessLauncher,
) {
    object Board {
        const val REFRESH = 0
        const val MY_PARTY = 1
    }

    object Page {
        const val JOIN_LEAVE = 0
        const val ADVERTISE = 1
        const val DISBAND = 2
        const val REFRESH = 3
        const val BACK = 4
        const val SIZE = 5
        const val COMBAT = 6
        const val TOTAL = 7
        const val SCALING = 8
        const val CHALLENGE = 9
        const val LAYOUT = 10
    }

    /** Kill counts for the party page, by mode (the raid sets these). */
    var killCount: (Player, Boolean) -> Int = { _, _ -> 0 }

    // The board
    // ---------------------------------------------------------------------------------------------

    fun openBoard(player: Player) {
        parties.view(player, null)
        VarPlayerIntMapSetter.set(
            player,
            cox_varps.party_holder,
            if (parties.partyOf(player) != null) 0 else -1,
        )
        player.ifOpenMainModal(cox_interfaces.party_list, eventBus)
        player.ifSetEvents(cox_components.list_buttons, 0..1, IfEvent.PauseButton)
        player.ifSetEvents(
            cox_components.list_rows,
            0 until CoxParties.BOARD_ROWS,
            IfEvent.PauseButton,
        )
        val listed = parties.advertised()
        for (row in 0 until CoxParties.BOARD_ROWS) {
            val party = listed.getOrNull(row)
            player.runClientScript(ROW_SCRIPT, row, party?.let { row(player, it) } ?: "")
        }
    }

    private fun row(viewer: Player, party: CoxParty): String {
        val leader = party.leader?.displayName ?: ""
        return listOf(
                if (party.isMember(viewer)) "<col=ffffff>$leader</col>" else leader,
                party.members.size,
                party.preferredSize,
                party.preferredCombat,
                party.preferredTotal,
                0,
                if (party.challenge) 1 else 0,
                clock.cycle - party.createdCycle,
            )
            .joinToString("|") + "|"
    }

    fun clickBoard(player: Player, child: Int) {
        when (child) {
            Board.REFRESH -> openBoard(player)
            Board.MY_PARTY -> {
                val party = parties.partyOf(player) ?: parties.create(player)
                openParty(player, party)
            }
        }
    }

    fun clickRow(player: Player, row: Int) {
        val party = parties.advertised().getOrNull(row) ?: return
        openParty(player, party)
    }

    // The party page
    // -----------------------------------------------------------------------------------------

    fun openParty(player: Player, party: CoxParty) {
        if (party.members.isEmpty()) {
            openBoard(player)
            return
        }
        parties.view(player, party)
        val state =
            when {
                party.isLeader(player) -> 2
                party.isMember(player) -> 1
                else -> 0
            }
        VarPlayerIntMapSetter.set(player, cox_varbits.lobby_size, party.preferredSize)
        VarPlayerIntMapSetter.set(player, cox_varbits.lobby_combat, party.preferredCombat)
        VarPlayerIntMapSetter.set(player, cox_varbits.lobby_total, party.preferredTotal)
        VarPlayerIntMapSetter.set(player, cox_varbits.challenge_mode, if (party.challenge) 1 else 0)
        VarPlayerIntMapSetter.set(player, cox_varbits.channel_member, if (state > 0) 1 else 0)
        player.ifOpenMainModal(cox_interfaces.party_details, eventBus)
        for (member in party.members) {
            player.runClientScript(MEMBER_SCRIPT, 0, memberLine(member, party.challenge))
        }
        val title = "Raiding Party of ${party.leader?.displayName} (${party.members.size})"
        player.runClientScript(
            HEADER_SCRIPT,
            state,
            if (party.advertised) 1 else 0,
            ADVERTS_LEFT,
            title,
        )
        player.ifSetEvents(cox_components.details_buttons, 0..Page.LAYOUT, IfEvent.PauseButton)
    }

    private fun memberLine(player: Player, challenge: Boolean): String {
        val levels = SKILLS.map { player.statBase(it) }
        val fields =
            listOf<Any>(
                player.displayName,
                player.combatLevel,
                levels.sum(),
                killCount(player, challenge),
            ) + levels
        return fields.joinToString("|") + "|"
    }

    fun refreshViewers(party: CoxParty) {
        for (member in party.members) {
            if (
                member.ui.containsModal(cox_interfaces.party_details) &&
                    parties.viewing(member) === party
            ) {
                openParty(member, party)
            }
        }
    }

    fun clickParty(player: Player, child: Int) {
        val party = parties.viewing(player)
        if (party == null || party.members.isEmpty()) {
            openBoard(player)
            return
        }
        val leader = party.isLeader(player)
        when (child) {
            Page.JOIN_LEAVE -> joinOrLeave(player, party)
            Page.ADVERTISE ->
                if (leader) {
                    party.advertised = !party.advertised
                    openParty(player, party)
                }
            Page.DISBAND ->
                if (leader) {
                    if (party.raid != null) {
                        player.mes("Your party's raid has begun; it can't be disbanded now.")
                        return
                    }
                    val others = party.members.filter { it !== player }
                    parties.disband(party)
                    others.forEach { it.mes("Your raiding party has been disbanded.") }
                    openBoard(player)
                }
            Page.REFRESH -> openParty(player, party)
            Page.BACK -> openBoard(player)
            Page.SIZE ->
                if (leader)
                    ask(player, party, "Enter a preferred party size (0 for any):", 0..100) {
                        party.preferredSize = it
                    }
            Page.COMBAT ->
                if (leader)
                    ask(player, party, "Enter a preferred combat level (0 for any):", 0..126) {
                        party.preferredCombat = it
                    }
            Page.TOTAL ->
                if (leader)
                    ask(player, party, "Enter a preferred skill total (0 for any):", 0..2277) {
                        party.preferredTotal = it
                    }
            Page.SCALING -> player.mes("The raid scales to the size of your party.")
            Page.CHALLENGE ->
                if (leader) {
                    if (party.raid != null) {
                        player.mes("Settings can't be changed once your party has entered.")
                        return
                    }
                    party.challenge = !party.challenge
                    refreshViewers(party)
                    openParty(player, party)
                }
            Page.LAYOUT -> player.mes("Raids are laid out at random.")
        }
    }

    private fun joinOrLeave(player: Player, party: CoxParty) {
        if (party.isMember(player)) {
            if (party.raid?.started == true) {
                player.mes("Your party is raiding; leave through the steps inside.")
                return
            }
            parties.leave(party, player)
            player.mes("You have left the party.")
            party.leader?.mes("${player.displayName} has left your party.")
            refreshViewers(party)
            openBoard(player)
            return
        }
        when (parties.join(party, player)) {
            CoxParties.JoinResult.Joined -> {
                player.mes("You have joined the party of ${party.leader?.displayName}.")
                for (other in party.members) {
                    if (other !== player) other.mes("${player.displayName} has joined your party.")
                }
                refreshViewers(party)
                openParty(player, party)
            }
            CoxParties.JoinResult.Full -> player.mes("That party is full.")
            CoxParties.JoinResult.Started ->
                player.mes("No-one may join the party after the raid begins.")
            CoxParties.JoinResult.Already -> Unit
        }
    }

    private fun ask(
        player: Player,
        party: CoxParty,
        title: String,
        range: IntRange,
        set: (Int) -> Unit,
    ) {
        access.launch(player) {
            val value = countDialog(title).coerceIn(range)
            set(value)
            refreshViewers(party)
            openParty(player, party)
        }
    }

    companion object {
        const val ROW_SCRIPT = 1566
        const val MEMBER_SCRIPT = 1517
        const val HEADER_SCRIPT = 1524

        /** OSRS has no advert quota here; the page still shows a count. */
        const val ADVERTS_LEFT = 99

        /** The party page's skill columns, in its script's order. */
        val SKILLS =
            listOf(
                stats.attack,
                stats.strength,
                stats.ranged,
                stats.magic,
                stats.defence,
                stats.hitpoints,
                stats.prayer,
                stats.agility,
                stats.herblore,
                stats.thieving,
                stats.crafting,
                stats.runecrafting,
                stats.mining,
                stats.smithing,
                stats.fishing,
                stats.cooking,
                stats.firemaking,
                stats.woodcutting,
                stats.fletching,
                stats.slayer,
                stats.farming,
                stats.construction,
                stats.hunter,
            )
    }
}
