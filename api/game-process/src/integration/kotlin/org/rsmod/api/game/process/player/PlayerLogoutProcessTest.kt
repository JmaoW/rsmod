package org.rsmod.api.game.process.player

import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState

class PlayerLogoutProcessTest {
    @Test
    fun GameTestState.`manual logout completes after delay expires`() = runGameTest {
        player.delay = 3
        player.manualLogout = true

        advance(ticks = 1)
        assertTrue(player.pendingLogout)
        assertFalse(player.loggingOut)

        advance(ticks = 5)
        assertFalse(player.pendingLogout)
        assertTrue(player.loggingOut)
    }

    @Test
    fun GameTestState.`pending logout is forced through after hard cap`() = runGameTest {
        player.delay = Int.MAX_VALUE
        player.manualLogout = true

        advance(ticks = 10)
        assertTrue(player.pendingLogout)
        assertFalse(player.loggingOut)

        advance(ticks = 60)
        assertFalse(player.pendingLogout)
        assertTrue(player.loggingOut)
    }

    @Test
    fun GameTestState.`force disconnect during pending logout does not get stuck`() = runGameTest {
        player.delay = Int.MAX_VALUE
        player.manualLogout = true
        advance(ticks = 2)
        check(player.pendingLogout)

        player.forceDisconnect = true
        advance(ticks = 2)

        assertFalse(player.forceDisconnect)
        assertFalse(player.pendingLogout)
        assertTrue(player.loggingOut)
    }

    @Test
    fun GameTestState.`repeated manual logout during pending logout is ignored`() = runGameTest {
        player.delay = Int.MAX_VALUE
        player.manualLogout = true
        advance(ticks = 2)
        check(player.pendingLogout)

        player.manualLogout = true
        advance(ticks = 1)

        assertFalse(player.manualLogout)
        assertFalse(player.forceDisconnect)
        assertTrue(player.pendingLogout)
    }

    @Test
    fun GameTestState.`client disconnect during pending logout does not force disconnect`() =
        runGameTest {
            player.delay = Int.MAX_VALUE
            player.manualLogout = true
            advance(ticks = 2)
            check(player.pendingLogout)

            player.clientDisconnected.set(true)
            advance(ticks = 25)

            assertFalse(player.forceDisconnect)
        }
}
