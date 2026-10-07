package org.rsmod.content.skills.smithing.scripts

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Registers [action] for whichever of the first three ops of [type] is called [opName]. Furnaces
 * keep "Smelt" in different slots, so it cannot be a fixed op.
 *
 * @return `false` if [type] has no such op in its first three slots.
 */
internal fun ScriptContext.onOpLocNamed(
    type: UnpackedLocType,
    opName: String,
    action: suspend ProtectedAccess.(BoundLocInfo) -> Unit,
): Boolean {
    when (type.op.indexOf(opName)) {
        0 -> onOpLoc1(type) { action(it.loc) }
        1 -> onOpLoc2(type) { action(it.loc) }
        2 -> onOpLoc3(type) { action(it.loc) }
        else -> return false
    }
    return true
}
