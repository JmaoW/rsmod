package org.rsmod.content.areas.misc.isleofsouls.pvp

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Who started each fight between two players, so that answering an attack is not skulled.
 *
 * A fight is the run of attacks between two players with no pause longer than [RETALIATION_TICKS].
 * The player who made the first attack of a fight is its aggressor. Attacks by the other player are
 * retaliation for as long as the fight lasts, however many there are; a fight that has gone quiet
 * for longer than the window starts again, and whoever attacks first is its new aggressor.
 */
@Singleton
class PvpFights @Inject constructor() {
    private class Fight(val aggressor: Long, var last: Int)

    /** Keyed by the two players' ids, smaller first. */
    private val fights = HashMap<Pair<Long, Long>, Fight>()

    /**
     * Records that [attacker] attacked [target] and returns whether it answers an attack that
     * [target] started, which does not earn the skull.
     */
    fun record(attacker: Player, target: Player): Boolean {
        val now = attacker.currentMapClock
        val key = minOf(attacker.fightId, target.fightId) to maxOf(attacker.fightId, target.fightId)
        val fight = fights[key]
        if (fight != null && now - fight.last <= RETALIATION_TICKS) {
            fight.last = now
            return fight.aggressor == target.fightId
        }
        fights[key] = Fight(attacker.fightId, now)
        if (fights.size > PRUNE_AT) {
            fights.values.removeIf { now - it.last > RETALIATION_TICKS }
        }
        return false
    }

    /** The player's account-wide id; the slot only for a player who has none yet. */
    private val Player.fightId: Long
        get() = uuid ?: slotId.toLong()

    companion object {
        /**
         * How long a fight may go quiet and still count as the same one: one minute. A guess; the
         * Wiki does not give the real window.
         */
        const val RETALIATION_TICKS = 100

        private const val PRUNE_AT = 256
    }
}
