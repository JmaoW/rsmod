package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.account.saver.AccountSavingService
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.MapClock

/*
 * Nothing runs the saving service in tests, so every autosave stays in its queue where it can be
 * counted. The first cycle only starts a player's schedule, at an offset based on their slot, so
 * most tests start with `pin()`: it makes the last save "now", and after that `k` ticks a player
 * has gone `k - 1` cycles since their last save.
 */
class PlayerAutosaveProcessTest {
    @Test
    fun GameTestState.`player is saved every interval`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            saves.pin(player)

            advance(ticks = INTERVAL)
            assertEquals(0, saves.pending)

            advance(ticks = 1)
            assertEquals(1, saves.pending)

            advance(ticks = INTERVAL)
            assertEquals(2, saves.pending)
        }

    @Test
    fun GameTestState.`requested save waits until the delay since the last save passes`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            saves.pin(player)
            player.requestAutosave()

            advance(ticks = DELAY)
            assertEquals(0, saves.pending)
            assertTrue(player.autosaveRequested)

            advance(ticks = 1)
            assertEquals(1, saves.pending)
            assertFalse(player.autosaveRequested)
        }

    @Test
    fun GameTestState.`a burst of requests is saved at most once per delay`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            saves.pin(player)

            // The delay is up on the 51st tick, so the 60 ticks hold one save. The requests that
            // follow it wait for their own delay.
            repeat(12) {
                player.requestAutosave()
                advance(ticks = 5)
            }
            assertEquals(1, saves.pending)
            assertTrue(player.autosaveRequested)

            advance(ticks = DELAY)
            assertEquals(2, saves.pending)
        }

    @Test
    fun GameTestState.`urgent request saves on the next cycle`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            saves.pin(player)
            player.requestAutosave(urgent = true)

            advance(ticks = 1)
            assertEquals(1, saves.pending)
            assertFalse(player.autosaveUrgent)
        }

    @Test
    fun GameTestState.`client disconnect saves immediately`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            saves.pin(player)
            player.clientDisconnected.set(true)

            advance(ticks = 1)
            assertEquals(1, saves.pending)

            // Only the disconnect itself asks, not every cycle that follows it.
            advance(ticks = 5)
            assertEquals(1, saves.pending)
        }

    @Test
    fun GameTestState.`player logging out is not autosaved`() =
        runInjectedGameTest(Saves::class) { saves ->
            advance(ticks = 1)
            player.loggingOut = true
            player.requestAutosave(urgent = true)

            advance(ticks = INTERVAL * 2)
            assertEquals(0, saves.pending)
        }

    @Test
    fun GameTestState.`periodic saves of different slots are spread out`() =
        runInjectedGameTest(Saves::class) { saves ->
            val other = registerPlayer(slot = 10)
            val offsets = listOf(player, other).map { (it.slotId * 7).mod(100) }
            check(offsets[0] != offsets[1]) { "Test needs slots with different offsets." }
            val early = offsets.max()
            val late = offsets.min()

            // The first tick only starts the schedules, `offset` cycles into the interval.
            advance(ticks = 1)
            advance(ticks = INTERVAL - early - 1)
            assertEquals(0, saves.pending)

            advance(ticks = 1)
            assertEquals(1, saves.pending)

            advance(ticks = early - late)
            assertEquals(2, saves.pending)
        }

    class Saves
    @Inject
    constructor(private val saver: AccountSavingService, private val clock: MapClock) {
        val pending: Int
            get() = saver.pendingCount

        fun pin(player: org.rsmod.game.entity.Player) {
            player.lastSaveCycle = clock.cycle
        }
    }

    private companion object {
        const val INTERVAL = PlayerAutosaveProcess.AUTOSAVE_INTERVAL
        const val DELAY = PlayerAutosaveProcess.AUTOSAVE_REQUEST_DELAY
    }
}
