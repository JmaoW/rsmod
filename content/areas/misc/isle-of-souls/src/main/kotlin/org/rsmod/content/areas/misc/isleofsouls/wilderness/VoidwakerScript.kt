package org.rsmod.content.areas.misc.isleofsouls.wilderness

import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeldU
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Voidwaker: the hilt, the blade and the gem the three Wilderness bosses drop are put together
 * by using one on another (OSRS Wiki, "Voidwaker"). All three must be in the inventory.
 */
class VoidwakerScript : PluginScript() {
    override fun ScriptContext.startup() {
        val parts =
            listOf(wild_objs.voidwaker_hilt, wild_objs.voidwaker_blade, wild_objs.voidwaker_gem)
        for (first in parts) {
            for (second in parts) {
                if (first.id < second.id) {
                    onOpHeldU(first, second) { combine() }
                }
            }
        }
    }

    private fun ProtectedAccess.combine() {
        val result =
            invDel(
                inv = inv,
                type1 = wild_objs.voidwaker_hilt,
                count1 = 1,
                type2 = wild_objs.voidwaker_blade,
                count2 = 1,
                type3 = wild_objs.voidwaker_gem,
                count3 = 1,
            )
        if (result.failure) {
            mes("You need the hilt, the blade and the gem of the Voidwaker to put it together.")
            return
        }
        invAdd(inv, wild_objs.voidwaker)
        mes("You put the hilt, the blade and the gem together and make the Voidwaker.")
    }
}
