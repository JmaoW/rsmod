package org.rsmod.api.game.process.player

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.forceDisconnect
import org.rsmod.api.player.output.ChatType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.ui.ifCloseModals
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.ShuffledPlayerList
import org.rsmod.game.queue.QueueCategory

public class PlayerLogoutProcess
@Inject
constructor(
    private val eventBus: EventBus,
    private val mapClock: MapClock,
    private val playerList: ShuffledPlayerList,
    private val accountRegistry: AccountRegistry,
    private val exceptionHandler: GameExceptionHandler,
    private val logoutProcessor: PlayerLogoutProcessor,
) {
    public fun process() {
        processPendingLogouts()
        accountRegistry.handleLogouts()
    }

    private fun processPendingLogouts() {
        for (player in playerList) {
            player.tryOrDisconnect {
                processServerShutdown()
                processForcedDisconnect()
                processClientDisconnect()
                processManualLogout()
                processPendingLogout()
            }
        }
    }

    private fun Player.processServerShutdown() {
        if (pendingShutdown && !loggingOut && !pendingLogout) {
            val closeClient = !clientDisconnected.get()
            if (closeClient) {
                closeClient()
            }
            queueLogout()
            clientDisconnected.set(false)
            forceDisconnect = false
        }
    }

    private fun Player.processForcedDisconnect() {
        if (!forceDisconnect) {
            return
        }
        // Clear the flag up front so a failure below cannot re-trigger this every cycle.
        forceDisconnect = false

        if (loggingOut) {
            return
        }

        if (pendingLogout) {
            // A forced disconnect means the player is in an error state. Do not keep waiting on
            // busy/delay conditions for a logout that is already pending.
            pendingLogoutCycles = PENDING_LOGOUT_HARD_CAP_PERIOD
        } else {
            queueLogout()
        }
        closeClient()
    }

    private fun Player.processClientDisconnect() {
        if (!clientDisconnected.get()) {
            clientDisconnectedCycles = 0
            preventLogoutCounter = 0
            return
        }

        if (loggingOut || pendingLogout) {
            return
        }

        // Save as soon as the client is gone, rather than waiting out the reconnect grace period.
        if (clientDisconnectedCycles == 0) {
            requestAutosave(urgent = true)
        }

        val disconnectLogoutQueued = clientDisconnectedCycles > RECONNECT_GRACE_PERIOD
        if (disconnectLogoutQueued) {
            return
        }

        if (clientDisconnectedCycles == RECONNECT_GRACE_PERIOD) {
            val preventLogout = mapClock <= preventLogoutUntil
            val bypassPrevention = preventLogoutCounter++ >= PREVENT_LOGOUT_HARD_CAP_PERIOD
            if (!preventLogout || bypassPrevention) {
                queueLogout()
                clientDisconnectedCycles = RECONNECT_GRACE_PERIOD + 1
            }
            return
        }

        clientDisconnectedCycles++
    }

    private fun Player.processManualLogout() {
        if (!manualLogout) {
            return
        }
        manualLogout = false

        // A logout is already underway; a repeated request must not re-queue it.
        if (pendingLogout || loggingOut) {
            return
        }

        val preventLogout = mapClock <= preventLogoutUntil
        if (preventLogout) {
            preventLogoutMessage?.let { mes(it, ChatType.Engine) }
            return
        }

        queueLogout()
        closeClient()
    }

    private fun Player.queueLogout() {
        check(!pendingLogout) { "`queueLogout` has already been called." }
        check(!loggingOut) { "`loggingOut` flag has already been set." }
        pendingLogout = true
        pendingLogoutCycles = 0
    }

    private fun Player.closeClient() {
        // Safe to call more than once; the client only needs to be closed a single time.
        if (!pendingCloseClient && !closeClient) {
            pendingCloseClient = true
        }
    }

    private fun Player.processPendingLogout() {
        if (!pendingLogout) {
            return
        }
        ifCloseModals(eventBus)
        forceExitAreas()

        val bypassConditions = pendingLogoutCycles++ >= PENDING_LOGOUT_HARD_CAP_PERIOD
        val blocked = isAccessProtected || engineQueueList.isNotEmpty || hasNonDiscardableQueue()
        if (blocked && !bypassConditions) {
            return
        }

        if (blocked) {
            logger.warn { "Forcing logout of $this after $pendingLogoutCycles cycles pending." }
        }

        pendingLogout = false
        pendingLogoutCycles = 0
        loggingOut = true
        logoutProcessor.process(this)

        if (pendingCloseClient) {
            pendingCloseClient = false
            closeClient = true
        }
    }

    private fun Player.forceExitAreas() {
        for (area in activeAreas.iterator()) {
            engineQueueAreaExit(area)
        }
        activeAreas.clear()
    }

    private fun Player.hasNonDiscardableQueue(): Boolean {
        val iterator = queueList.iterator() ?: return false
        while (iterator.hasNext()) {
            val queue = iterator.next()
            val cannotDiscard = queue.category != QueueCategory.LongDiscard.id
            if (cannotDiscard) {
                iterator.cleanUp()
                return true
            }
        }
        iterator.cleanUp()
        return false
    }

    private inline fun Player.tryOrDisconnect(block: Player.() -> Unit) =
        try {
            block(this)
        } catch (e: Exception) {
            forceDisconnect()
            exceptionHandler.handle(e) { "Error processing logout for player: $this." }
        } catch (e: NotImplementedError) {
            forceDisconnect()
            exceptionHandler.handle(e) { "Error processing logout for player: $this." }
        }

    private companion object {
        /**
         * The grace period (in server cycles) during which a disconnected player is allowed to
         * remain in the world before their logout is queued. This gives them a chance to reconnect
         * in time, based on this constant.
         */
        private const val RECONNECT_GRACE_PERIOD: Int = 16

        /**
         * A hard cap period (in server cycles) after which the player's [Player.preventLogoutUntil]
         * will no longer apply. This prevents players from permanently being stuck online after
         * x-logging.
         */
        private const val PREVENT_LOGOUT_HARD_CAP_PERIOD: Int = 100

        /**
         * A hard cap period (in server cycles) a player may remain in [Player.pendingLogout] while
         * busy, delayed, or holding non-discardable queues. Once reached, the logout is completed
         * regardless of those conditions, so a player can never be stuck pending logout.
         */
        private const val PENDING_LOGOUT_HARD_CAP_PERIOD: Int = 50

        private val logger = InlineLogger()
    }
}
