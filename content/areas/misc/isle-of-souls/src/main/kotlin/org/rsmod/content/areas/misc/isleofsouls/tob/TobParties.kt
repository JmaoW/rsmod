package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * A party forming at Ver Sinhaza's notice board. The leader (the first member) sets the preferences
 * and accepts [applicants]; those declined stay in [blocked] until the leader lifts it. When the
 * leader walks into the theatre the party becomes a raid and leaves the list.
 */
class TobParty(leader: Player, val createdCycle: Int) {
    val members: MutableList<Player> = arrayListOf(leader)
    val applicants: MutableList<Player> = ArrayList()
    val blocked: MutableList<Int> = ArrayList()

    /** The size the leader would like (0 for any) and the least combat level (0 for any). */
    var preferredSize: Int = 0
    var preferredLevel: Int = 0

    /** Set once the party is inside the theatre. */
    var raid: TobRaid? = null

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
enum class TobPartyResult {
    Done,
    Full,
    NoLongerRecruiting,
    AlreadyApplied,
    NotApplicant,
}

/** What a player looking at a party is to it; the panel's footer script (2323) is told this. */
enum class TobPartyView(val value: Int) {
    NonMember(0),
    Member(1),
    Leader(2),
    Applicant(3),
    Declined(4),
}

/** One player at the board: the party they belong to, applied to and are looking at. */
class TobLobbyState {
    var current: TobParty? = null
    var applied: TobParty? = null
    var viewing: TobParty? = null
    var viewingValue: Int = 0

    /** The parties on the list as the player last saw it, by row. */
    var listed: List<TobParty> = emptyList()
}

/** Every recruiting party and every player's place at the board. Runs on the game thread. */
@Singleton
class TobParties @Inject constructor(private val clock: MapClock) {
    private val parties = ArrayList<TobParty>()
    private val states = HashMap<Int, TobLobbyState>()

    /** The most members a party may have (the event setting; the real theatre takes five). */
    val cap: Int
        get() = EventSettings.current.tob.partyCap

    fun recruiting(): List<TobParty> = parties.toList()

    fun stateOf(player: Player): TobLobbyState =
        states.getOrPut(player.accountId) { TobLobbyState() }

    fun currentParty(player: Player): TobParty? = states[player.accountId]?.current

    fun isListFull(): Boolean = parties.size >= MAX_LISTED

    fun isListed(party: TobParty): Boolean = party in parties

    fun create(leader: Player): TobParty {
        val party = TobParty(leader, clock.cycle)
        parties += party
        stateOf(leader).current = party
        return party
    }

    fun apply(party: TobParty, player: Player): TobPartyResult {
        if (party.insideRaid || !isListed(party)) {
            return TobPartyResult.NoLongerRecruiting
        }
        if (party.isApplicant(player)) {
            return TobPartyResult.AlreadyApplied
        }
        if (party.applicants.size >= MAX_APPLICANTS) {
            return TobPartyResult.Full
        }
        party.applicants += player
        stateOf(player).applied = party
        return TobPartyResult.Done
    }

    fun withdraw(party: TobParty, player: Player): Boolean {
        val removed = party.applicants.removeIf { it === player }
        if (removed && stateOf(player).applied === party) {
            stateOf(player).applied = null
        }
        return removed
    }

    fun answer(party: TobParty, applicant: Player, accept: Boolean): TobPartyResult {
        if (!party.isApplicant(applicant) || party.isMember(applicant)) {
            return TobPartyResult.NotApplicant
        }
        if (accept && party.members.size >= cap) {
            return TobPartyResult.Full
        }
        withdraw(party, applicant)
        if (accept) {
            stateOf(applicant).current?.let { leave(it, applicant) }
            party.members += applicant
            stateOf(applicant).current = party
        } else if (applicant.accountId !in party.blocked) {
            party.blocked += applicant.accountId
        }
        return TobPartyResult.Done
    }

    /** Removes a member; the next in line leads. An empty party leaves the list. */
    fun leave(party: TobParty, player: Player): Boolean {
        if (!party.isMember(player)) {
            return false
        }
        party.members.removeIf { it === player }
        val state = stateOf(player)
        if (state.current === party) {
            state.current = null
        }
        if (party.members.isEmpty()) {
            for (applicant in party.applicants.toList()) {
                withdraw(party, applicant)
            }
            parties -= party
        }
        return true
    }

    fun disband(party: TobParty) {
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

    /** The party left the list because it entered the theatre. */
    fun unlist(party: TobParty) {
        parties -= party
        for (applicant in party.applicants.toList()) {
            withdraw(party, applicant)
        }
    }

    /** Drops [player] from the board: their application, and their party if it has not started. */
    fun forget(player: Player): Pair<TobParty?, TobParty?> {
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
        /** Enum 1937: the list has 45 rows. */
        const val MAX_LISTED = 45

        /** The panel lists up to ten applicants (accept children 12-21, decline 22-31). */
        const val MAX_APPLICANTS = 10

        /** The client's party panel, HUD and vault draw five members. */
        const val CLIENT_SLOTS = 5
    }
}
