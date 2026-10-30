package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * A group forming at the grouping obelisk. The leader (the first member) sets the invocations, the
 * rest are accepted from [applicants]; those turned away stay in [blocked] until the leader lifts
 * it. When the leader walks into the tombs the party becomes a raid ([raid]) and leaves the
 * recruiting list.
 */
class ToaParty(leader: Player, var settings: InvocationSet, val createdCycle: Int) {
    val members: MutableList<Player> = arrayListOf(leader)
    val applicants: MutableList<Player> = ArrayList()
    val blocked: MutableList<Int> = ArrayList()

    /** The completions the leader would like applicants to have (0 for none, at most 100). */
    var completionsWanted: Int = 0

    /** Set once the party is inside the tombs. */
    var raid: ToaRaid? = null

    val leader: Player?
        get() = members.firstOrNull()

    val insideRaid: Boolean
        get() = raid != null

    fun isLeader(player: Player): Boolean = members.firstOrNull() === player

    fun isMember(player: Player): Boolean = members.any { it === player }

    fun isApplicant(player: Player): Boolean = applicants.any { it === player }

    fun isBlocked(player: Player): Boolean = player.accountId in blocked
}

/** Why an application or an acceptance did not go through. */
enum class PartyResult {
    Done,
    Full,
    NoLongerRecruiting,
    AlreadyApplied,
    NotApplicant,
}

/** What a player looking at a party is to it; the details panel's script is told this number. */
enum class PartyView(val value: Int) {
    NonMember(0),
    Member(1),
    Leader(2),
    Applicant(3),
    Declined(4),
}

/** What one player is doing at the board: where they belong and what they are looking at. */
class LobbyState {
    var current: ToaParty? = null
    var applied: ToaParty? = null
    var viewing: ToaParty? = null

    /** The details panel's tab (0 members, 1 applicants, 2 invocations, 3 summary). */
    var tab: Int = 0

    /** What the viewing player is to the party being viewed. */
    var viewingValue: Int = 0

    /** The parties on the list as the player last saw it, by row. */
    var listed: List<ToaParty> = emptyList()
}

/**
 * Every recruiting party and every player's place at the board. Runs on the game thread.
 *
 * Each player's own board of invocations is kept for the length of the server run: it is what a
 * party they lead starts with, and what stays with them when they hand over the lead.
 */
@Singleton
class ToaParties
@Inject
constructor(private val catalogs: InvocationCatalogProvider, private val clock: MapClock) {
    private val parties = ArrayList<ToaParty>()
    private val states = HashMap<Int, LobbyState>()
    private val boards = HashMap<Int, InvocationSet>()

    val catalog: InvocationCatalog
        get() = catalogs.catalog

    /** The recruiting parties, oldest first. */
    fun recruiting(): List<ToaParty> = parties.toList()

    fun stateOf(player: Player): LobbyState = states.getOrPut(player.accountId) { LobbyState() }

    fun currentParty(player: Player): ToaParty? = states[player.accountId]?.current

    /** The board of invocations [player] starts a party of their own with. */
    fun boardOf(player: Player): InvocationSet =
        boards.getOrPut(player.accountId) { InvocationSet(catalog) }

    fun isListFull(): Boolean = parties.size >= MAX_LISTED

    fun isListed(party: ToaParty): Boolean = party in parties

    /** Starts a party led by [leader], who joins it. */
    fun create(leader: Player): ToaParty {
        val party = ToaParty(leader, boardOf(leader), clock.cycle)
        parties += party
        stateOf(leader).current = party
        return party
    }

    fun apply(party: ToaParty, player: Player): PartyResult {
        if (party.insideRaid || !isListed(party)) {
            return PartyResult.NoLongerRecruiting
        }
        if (party.isApplicant(player)) {
            return PartyResult.AlreadyApplied
        }
        if (party.applicants.size >= MAX_SIZE) {
            return PartyResult.Full
        }
        party.applicants += player
        stateOf(player).applied = party
        return PartyResult.Done
    }

    /** Takes back an application. `false` if there was none. */
    fun withdraw(party: ToaParty, player: Player): Boolean {
        val removed = party.applicants.removeIf { it === player }
        if (removed && stateOf(player).applied === party) {
            stateOf(player).applied = null
        }
        return removed
    }

    /**
     * The leader accepts or declines an applicant. A declined player is blocked from applying
     * again.
     */
    fun answer(party: ToaParty, applicant: Player, accept: Boolean): PartyResult {
        if (!party.isApplicant(applicant) || party.isMember(applicant)) {
            return PartyResult.NotApplicant
        }
        if (accept && party.members.size >= MAX_SIZE) {
            return PartyResult.Full
        }
        withdraw(party, applicant)
        if (accept) {
            stateOf(applicant).current?.let { leave(it, applicant) }
            party.members += applicant
            stateOf(applicant).current = party
        } else if (applicant.accountId !in party.blocked) {
            party.blocked += applicant.accountId
        }
        return PartyResult.Done
    }

    /**
     * Removes a member; the next in line leads if the leader left (and the party takes the settings
     * the old leader had on the board). An empty party leaves the list.
     */
    fun leave(party: ToaParty, player: Player): Boolean {
        if (!party.isMember(player)) {
            return false
        }
        val wasLeader = party.isLeader(player)
        party.members.removeIf { it === player }
        val state = stateOf(player)
        if (state.current === party) {
            state.current = null
        }
        if (wasLeader && !party.insideRaid) {
            party.leader?.let { newLeader ->
                val inherited = boardOf(newLeader)
                inherited.load(party.settings.words)
                party.settings = inherited
            }
        }
        if (party.members.isEmpty()) {
            parties -= party
        }
        return true
    }

    /** Dissolves a party that has not started: members and applicants are let go. */
    fun disband(party: ToaParty) {
        for (member in party.members.toList()) {
            val state = stateOf(member)
            if (state.current === party) {
                state.current = null
            }
        }
        party.members.clear()
        for (applicant in party.applicants.toList()) {
            withdraw(party, applicant)
        }
        parties -= party
    }

    /** The party left the list because it entered the tombs. */
    fun unlist(party: ToaParty) {
        parties -= party
    }

    /** Drops [player] from the board: their application, and their party if it has not started. */
    fun forget(player: Player): Pair<ToaParty?, ToaParty?> {
        val state = stateOf(player)
        val applied = state.applied
        applied?.let { withdraw(it, player) }
        val current = state.current
        if (current != null && !current.insideRaid) {
            leave(current, player)
        }
        state.viewing = null
        return applied to current
    }

    companion object {
        /** Wiki: a raid party has up to 8 players. */
        const val MAX_SIZE = 8

        /** The party list has room for this many recruiting parties. */
        const val MAX_LISTED = 45
        const val PRESET_COUNT = 5
    }
}
