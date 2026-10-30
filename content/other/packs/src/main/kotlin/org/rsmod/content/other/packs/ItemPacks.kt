package org.rsmod.content.other.packs

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Opening a pack ("Open" on the pack) removes one pack and gives its contents. Contents that do not
 * stack are given as noted items, so a full pack fits in one slot. With a full inventory, a pack
 * can only be opened when it is the only pack held, because removing it frees the slot for the
 * contents.
 */
class ItemPacks @Inject constructor(private val objTypes: ObjTypeList) : PluginScript() {
    override fun ScriptContext.startup() {
        for (pack in PackObjs.all) {
            onOpHeld1(pack.pack) { open(pack) }
        }
    }

    private fun ProtectedAccess.open(pack: ItemPack) {
        val held = invTotal(inv, pack.pack)
        if (held == 0) {
            return
        }
        if (inv.isFull() && held > 1) {
            mes("Your inventory is too full to open more than one pack at a time.")
            return
        }
        invDel(inv, pack.pack, 1)
        val noted = !objTypes[pack.contents].isStackable
        val added = invAdd(inv, pack.contents, pack.count, cert = noted)
        if (!added.success) {
            invAdd(inv, pack.pack, 1)
            mes("You don't have enough inventory space to open the pack.")
            return
        }
        mes("You open the pack and find ${pack.found}.")
    }
}
