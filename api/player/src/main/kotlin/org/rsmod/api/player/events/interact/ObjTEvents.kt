package org.rsmod.api.player.events.interact

import org.rsmod.game.obj.Obj
import org.rsmod.game.type.comp.ComponentType
import org.rsmod.game.type.obj.UnpackedObjType

public class ObjTEvents {
    /**
     * A component, such as a spell, was used on a ground obj within reach of the player. There is
     * no walking up to the obj first: whoever handles this checks the distance they need.
     *
     * @param comsub The sub-index of [component] that was used, or `-1`.
     */
    public class Cast(
        public val obj: Obj,
        public val type: UnpackedObjType,
        public val comsub: Int,
        component: ComponentType,
    ) : OpEvent(component.packed.toLong())
}
