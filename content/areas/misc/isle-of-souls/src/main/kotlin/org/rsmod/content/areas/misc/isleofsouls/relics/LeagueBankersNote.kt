package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object BankersNoteVarBits : VarBitReferences() {
    /** What "Activate" does: `0` notes, `1` un-notes. Switched by "Toggle". */
    val mode = find("league_bankers_note_mode")

    /** How many items one use notes; `0` means all of them. */
    val note_quantity = find("league_bankers_note_quantity")

    /** How many items one use un-notes; `0` means as many as fit. */
    val unnote_quantity = find("league_bankers_unnote_quantity")
}

/**
 * The Banker's Note relic (tier 5 in the code, "tier 6" in the plan and on the wiki). Using an item
 * on the banker's note notes it (or un-notes a noted one), up to the chosen quantity. "Activate"
 * does the same to the last item used on the note, in the direction "Toggle" picks, and
 * "Note-Quantity" / "Un-note-Quantity" set the quantities.
 *
 * The last item used on the note is not saved over logout, and what the real note's "Toggle" did is
 * not known, so here it switches "Activate" between noting and un-noting.
 */
class LeagueBankersNote @Inject constructor(private val tools: LeagueRelicTools) : PluginScript() {
    /** The un-noted form of the last item each player used on the note. */
    private val lastItem = WeakHashMap<Player, UnpackedObjType>()

    override fun ScriptContext.startup() {
        onOpHeldU(isle_objs.bankers_note) { useOn(it.second) }
        onOpHeld1(isle_objs.bankers_note) { activate() }
        onOpHeld2(isle_objs.bankers_note) { chooseQuantity(BankersNoteVarBits.note_quantity) }
        onOpHeld3(isle_objs.bankers_note) { chooseQuantity(BankersNoteVarBits.unnote_quantity) }
        onOpHeld4(isle_objs.bankers_note) { toggle() }
    }

    private fun ProtectedAccess.useOn(item: UnpackedObjType) {
        if (!usable()) {
            return
        }
        when {
            item.isCert -> {
                lastItem[player] = ocUncert(item)
                unnote(ocUncert(item), item)
            }
            item.canCert -> {
                lastItem[player] = item
                note(item, ocCert(item))
            }
            else -> mes("That item cannot be noted.")
        }
    }

    private fun ProtectedAccess.activate() {
        if (!usable()) {
            return
        }
        val item = lastItem[player]
        if (item == null) {
            mes("Use an item on the banker's note first.")
            return
        }
        if (player.vars[BankersNoteVarBits.mode] == MODE_UNNOTE) {
            unnote(item, ocCert(item))
        } else {
            note(item, ocCert(item))
        }
    }

    private fun ProtectedAccess.toggle() {
        val unnote = player.vars[BankersNoteVarBits.mode] != MODE_UNNOTE
        VarPlayerIntMapSetter.set(player, BankersNoteVarBits.mode, if (unnote) 1 else 0)
        val action = if (unnote) "un-note" else "note"
        mes("Activating the banker's note will now ${LeagueChat.blue(action)} items.")
    }

    private suspend fun ProtectedAccess.chooseQuantity(varbit: VarBitType) {
        if (!usable()) {
            return
        }
        val max = (1 shl (varbit.msb - varbit.lsb + 1)) - 1
        val input = countDialog("Enter amount (0 for all):")
        val quantity = if (input >= max) 0 else input
        VarPlayerIntMapSetter.set(player, varbit, quantity)
        val text = if (quantity == 0) "all" else "$quantity"
        mes("The banker's note will now handle ${LeagueChat.blue(text)} at a time.")
    }

    private fun ProtectedAccess.note(item: UnpackedObjType, cert: UnpackedObjType) {
        val held = invTotal(inv, item)
        if (held == 0) {
            mes("You have no ${item.name.lowercase()} to note.")
            return
        }
        val count = held.limitTo(player.vars[BankersNoteVarBits.note_quantity])
        invDel(inv, item, count)
        invAdd(inv, cert, count)
    }

    private fun ProtectedAccess.unnote(item: UnpackedObjType, cert: UnpackedObjType) {
        val noted = invTotal(inv, cert)
        if (noted == 0) {
            mes("You have no noted ${item.name.lowercase()} to un-note.")
            return
        }
        val wanted = noted.limitTo(player.vars[BankersNoteVarBits.unnote_quantity])
        val free = inv.freeSpace()
        val count =
            when {
                wanted <= free -> wanted
                // Un-noting every note frees the slot the notes were in.
                wanted == noted && noted == free + 1 -> noted
                else -> free
            }
        if (count == 0) {
            mes("You don't have enough inventory space.")
            return
        }
        invDel(inv, cert, count)
        invAdd(inv, item, count)
    }

    private fun Int.limitTo(quantity: Int): Int = if (quantity == 0) this else minOf(this, quantity)

    private fun ProtectedAccess.usable(): Boolean {
        if (tools.isChosen(player, RelicItem.BankersNote)) {
            return true
        }
        mes("Only someone with the Banker's Note relic can use the note.")
        return false
    }

    private companion object {
        const val MODE_UNNOTE = 1
    }
}
