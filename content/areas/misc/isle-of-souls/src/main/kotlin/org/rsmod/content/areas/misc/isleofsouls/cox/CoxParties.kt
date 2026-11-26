package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * A raiding party on Mount Quidamortem. In OSRS a party belongs to a chat-channel; the isle has no
 * friends chat, so a party here is its own list of members: the first is the leader, anyone may
 * join from the party page while there is room and the raid has not begun.
 */
class CoxParty(leader: Player, val createdCycle: Int) {
    val members: MutableList<Player> = arrayListOf(leader)

    /** Shown on the recruiting board. */
    var advertised: Boolean = false

    var preferredSize: Int = 0
    var preferredCombat: Int = 0
    var preferredTotal: Int = 0
    var challenge: Boolean = false

    /** Set once a member has walked in and the raid is built. */
    var raid: CoxRaid? = null

    val leader: Player?
        get() = members.firstOrNull()

    fun isLeader(player: Player): Boolean = members.firstOrNull() === player

    fun isMember(player: Player): Boolean = members.any { it === player }
}

@Singleton
class CoxParties @Inject constructor(private val clock: MapClock) {
    private val parties = ArrayList<CoxParty>()
    private val partyOf = HashMap<Int, CoxParty>()
    private val viewing = HashMap<Int, CoxParty>()

    val cap: Int
        get() = EventSettings.current.cox.partyCap

    fun all(): List<CoxParty> = parties.toList()

    /** The parties on the recruiting board, newest last. */
    fun advertised(): List<CoxParty> =
        parties.filter { it.advertised && it.raid?.started != true }.take(BOARD_ROWS)

    fun partyOf(player: Player): CoxParty? = partyOf[player.accountId]

    fun viewing(player: Player): CoxParty? = viewing[player.accountId]

    fun view(player: Player, party: CoxParty?) {
        if (party == null) viewing.remove(player.accountId) else viewing[player.accountId] = party
    }

    fun create(leader: Player): CoxParty {
        partyOf(leader)?.let { leave(it, leader) }
        val party = CoxParty(leader, clock.cycle)
        parties += party
        partyOf[leader.accountId] = party
        return party
    }

    enum class JoinResult {
        Joined,
        Full,
        Started,
        Already,
    }

    fun join(party: CoxParty, player: Player): JoinResult {
        if (party.isMember(player)) return JoinResult.Already
        if (party.raid?.started == true) return JoinResult.Started
        if (party.members.size >= cap) return JoinResult.Full
        partyOf(player)?.let { leave(it, player) }
        party.members += player
        partyOf[player.accountId] = party
        return JoinResult.Joined
    }

    /** Takes [player] out of [party]; the next member leads. An empty party is gone. */
    fun leave(party: CoxParty, player: Player): Boolean {
        if (!party.isMember(player)) return false
        party.members.removeIf { it === player }
        if (partyOf[player.accountId] === party) partyOf.remove(player.accountId)
        if (party.members.isEmpty()) parties -= party
        return true
    }

    fun disband(party: CoxParty) {
        for (member in party.members.toList()) {
            if (partyOf[member.accountId] === party) partyOf.remove(member.accountId)
        }
        party.members.clear()
        parties -= party
        viewing.values.removeIf { it === party }
    }

    fun forget(player: Player) {
        viewing.remove(player.accountId)
    }

    companion object {
        /** The board lists 40 parties (enum 1537). */
        const val BOARD_ROWS = 40
    }
}
