package org.rsmod.api.game.process.player

import jakarta.inject.Inject
import org.rsmod.api.registry.account.AccountRegistry
import org.rsmod.api.utils.logging.GameExceptionHandler
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.SessionStateEvent

/*
 * This class is not strictly required, as it is not expected to grow and could have been inlined
 * into `PlayerLogoutProcess`. However, the split promotes a clear logical boundary between the
 * pending logout step and the actual logout logic.
 */
public class PlayerLogoutProcessor
@Inject
constructor(
    private val eventBus: EventBus,
    private val accountRegistry: AccountRegistry,
    private val exceptionHandler: GameExceptionHandler,
) {
    public fun process(player: Player) {
        player.completeLogout()
    }

    // Finalizes the logout process. The player remains in the player list until their account
    // save callback completes, which is guaranteed to occur.
    //
    // A failing logout event handler must never prevent the save and removal below, otherwise the
    // player would remain in the world in a `loggingOut` state indefinitely.
    private fun Player.completeLogout() {
        try {
            eventBus.publish(SessionStateEvent.Logout(this))
        } catch (e: Exception) {
            exceptionHandler.handle(e) { "Error publishing logout event for player: $this." }
        }
        accountRegistry.queueLogout(this)
    }
}
