package org.rsmod.api.net.rsprot.handlers

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.game.incoming.objs.OpObjT
import org.rsmod.api.net.rsprot.player.InterfaceEvents
import org.rsmod.api.player.events.interact.ObjTEvents
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.registry.obj.ObjRegistry
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.comp.ComponentTypeList
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.game.type.interf.InterfaceTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

/**
 * A component (a spell) used on a ground obj. Unlike a plain obj op, the player does not walk to
 * the obj: a spell is cast from where they stand, so the event is run at once for scripts to check
 * the range and line of sight they need.
 */
class OpObjTHandler
@Inject
constructor(
    private val eventBus: EventBus,
    private val objTypes: ObjTypeList,
    private val objRegistry: ObjRegistry,
    private val componentTypes: ComponentTypeList,
    private val interfaceTypes: InterfaceTypeList,
    private val protectedAccess: ProtectedAccessLauncher,
) : MessageHandler<OpObjT> {
    private val logger = InlineLogger()

    private val OpObjT.asComponent: Component
        get() = Component(selectedInterfaceId, selectedComponentId)

    override fun handle(player: Player, message: OpObjT) {
        if (player.isDelayed) {
            return
        }
        val coords = CoordGrid(message.x, message.z, player.level)
        val obj = findObj(player, coords, message.id) ?: return
        val type = objTypes[obj.type] ?: return

        val interfaceType = interfaceTypes[message.asComponent]
        val componentType = componentTypes[message.asComponent]
        val validInterface =
            player.ui.containsOverlay(interfaceType) || player.ui.containsModal(interfaceType)
        if (!validInterface) {
            return
        }
        val comsub = message.selectedSub
        val targetEnabled =
            InterfaceEvents.isEnabled(player.ui, componentType, comsub, IfEvent.TgtObj)
        if (!targetEnabled) {
            return
        }

        player.clearPendingAction(eventBus)
        player.resetFaceEntity()
        player.faceSquare(coords)
        logger.debug { "OpObjT: obj=$obj, type=$type, component=$componentType, comsub=$comsub" }
        protectedAccess.launch(player) {
            eventBus.publish(this, ObjTEvents.Cast(obj, type, comsub, componentType))
        }
    }

    private fun findObj(player: Player, coords: CoordGrid, type: Int): Obj? =
        objRegistry.findAll(coords).firstOrNull { it.type == type && it.isVisibleTo(player) }
}
