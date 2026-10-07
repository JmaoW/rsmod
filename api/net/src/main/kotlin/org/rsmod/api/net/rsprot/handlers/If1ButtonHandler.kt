package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.buttons.If1Button
import org.rsmod.annotations.InternalApi
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.ui.IfModalButton
import org.rsmod.api.player.ui.IfOverlayButton
import org.rsmod.api.player.ui.ifCloseInputDialog
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.comp.ComponentTypeList
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.InterfaceTypeList
import org.rsmod.game.ui.Component

/**
 * Handles clicks on old-style (pre-"if3") interface buttons, such as the ones on the tanner
 * interface. The client sends only the component for these, and the server treats it as the first
 * op of the component, exactly like [If3ButtonHandler] does for a modern button. Only components
 * that really are old-style buttons (button type 1, 4 or 5) are accepted.
 */
class If1ButtonHandler
@Inject
constructor(
    private val eventBus: EventBus,
    private val interfaceTypes: InterfaceTypeList,
    private val componentTypes: ComponentTypeList,
    private val protectedAccess: ProtectedAccessLauncher,
) : MessageHandler<If1Button> {
    private val logger = InlineLogger()

    @OptIn(InternalApi::class)
    override fun handle(player: Player, message: If1Button) {
        val component = Component(message.interfaceId, message.componentId)
        val componentType = componentTypes[component]
        if (componentType.v3 || componentType.buttonType !in LEGACY_BUTTON_TYPES) {
            logger.debug { "If1Button on a component that is not an old-style button: $message" }
            return
        }
        val interfaceType = interfaceTypes[component]

        if (player.ui.containsOverlay(interfaceType) || player.ui.containsTopLevel(interfaceType)) {
            val event = IfOverlayButton(player, componentType, -1, null, IfButtonOp.Op1)
            logger.debug { "[Overlay] If1Button: $message (event=$event)" }
            eventBus.publish(event)
            return
        }

        if (player.ui.containsModal(interfaceType)) {
            val event = IfModalButton(componentType, -1, null, IfButtonOp.Op1)
            player.ifCloseInputDialog()
            if (player.isModalButtonProtected) {
                logger.debug { "[Modal][BLOCKED] If1Button: $message (event=$event)" }
                return
            }
            logger.debug { "[Modal] If1Button: $message (event=$event)" }
            protectedAccess.launchLenient(player) { eventBus.publish(this, event) }
        }
    }

    private companion object {
        val LEGACY_BUTTON_TYPES = intArrayOf(1, 4, 5)
    }
}
