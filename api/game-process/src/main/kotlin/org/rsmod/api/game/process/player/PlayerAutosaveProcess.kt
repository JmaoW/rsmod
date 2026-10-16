package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.ShuffledPlayerList

/**
 * Saves the account of every online player now and then, so a server crash or a hard stop only
 * loses the last couple of minutes instead of everything since the player logged in.
 *
 * A player is saved when:
 * - [AUTOSAVE_INTERVAL] cycles have passed since their last save; or
 * - something asked for a save with [Player.requestAutosave] (a level up, a finished task...) and
 *   [AUTOSAVE_REQUEST_DELAY] cycles have passed since their last save, so a burst of requests only
 *   saves once; or
 * - the request was urgent, such as their client disconnecting, in which case it happens on the
 *   next cycle.
 *
 * Each player's schedule starts at an offset based on their slot, so the periodic saves of players
 * who logged in together are spread out over the interval rather than all landing on one cycle.
 *
 * The state to save is copied here, on the game thread, and written later by the saving service.
 */
public class PlayerAutosaveProcess
@Inject
constructor(
    private val mapClock: MapClock,
    private val playerList: ShuffledPlayerList,
    private val accountRegistry: AccountRegistry,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process() {
        val cycle = mapClock.cycle
        for (player in playerList) {
            player.processAutosave(cycle)
        }
    }

    private fun Player.processAutosave(cycle: Int) {
        // A player who is leaving gets a full save on logout. Skipping them here also keeps a
        // half-finished logout from racing with an autosave.
        if (loggingOut || pendingLogout || pendingShutdown) {
            return
        }

        if (lastSaveCycle == Int.MIN_VALUE) {
            lastSaveCycle = cycle - (slotId * STAGGER_PER_SLOT).mod(AUTOSAVE_STAGGER_RANGE)
            return
        }

        val elapsed = cycle - lastSaveCycle
        val periodic = elapsed >= AUTOSAVE_INTERVAL
        val requested = autosaveRequested && (autosaveUrgent || elapsed >= AUTOSAVE_REQUEST_DELAY)
        if (!periodic && !requested) {
            return
        }

        // Reset up front so a failure below does not retry (and log) on every cycle.
        lastSaveCycle = cycle
        autosaveRequested = false
        autosaveUrgent = false

        try {
            accountRegistry.queueAutosave(this)
        } catch (e: Exception) {
            exceptionHandler.handle(e) { "Error queueing autosave for player: $this." }
        }
    }

    public companion object {
        /** The cycles between a player's periodic saves: two minutes. */
        public const val AUTOSAVE_INTERVAL: Int = 200

        /**
         * The fewest cycles that must pass since the last save before a [Player.requestAutosave]
         * request is saved: thirty seconds. Urgent requests do not wait.
         */
        public const val AUTOSAVE_REQUEST_DELAY: Int = 50

        private const val AUTOSAVE_STAGGER_RANGE: Int = 100
        private const val STAGGER_PER_SLOT: Int = 7
    }
}
