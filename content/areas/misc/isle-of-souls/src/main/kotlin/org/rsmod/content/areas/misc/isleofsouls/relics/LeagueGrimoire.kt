package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpWorn1
import org.rsmod.api.script.onOpWorn2
import org.rsmod.api.script.onOpWorn3
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.content.areas.misc.isleofsouls.chat.LeagueChat
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GrimoireVarBits : VarBitReferences() {
    /** The spellbook the grimoire last swapped away from, as a [Spellbook.varValue]. */
    val previous = find("league_3_magic_book_previous_spellbook")
}

/**
 * The Grimoire relic (tier 6 in the code, "tier 7" in the plan and on the wiki). Its arcane
 * grimoire swaps to any spellbook for free ("Change Spellbook") and back to the one before
 * ("Previous Spellbook"), from the inventory or worn in the shield slot.
 *
 * The relic's other effects need nothing here: every spell's quest and diary locks are already
 * cleared for everyone (`IsleOfSoulsSpellUnlocks`), every prayer is unlocked, and no spell on the
 * server asks for a Book of the Dead.
 */
class LeagueGrimoire @Inject constructor(private val tools: LeagueRelicTools) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpHeld1(isle_objs.arcane_grimoire) { change() }
        onOpHeld3(isle_objs.arcane_grimoire) { previous() }
        onOpHeld4(isle_objs.arcane_grimoire) { checkPrevious() }
        onOpWorn1(isle_objs.arcane_grimoire) { change() }
        onOpWorn2(isle_objs.arcane_grimoire) { previous() }
        onOpWorn3(isle_objs.arcane_grimoire) { checkPrevious() }
    }

    private suspend fun ProtectedAccess.change() {
        if (!usable()) {
            return
        }
        val book =
            choice4(
                "Standard",
                Spellbook.Standard,
                "Ancient Magicks",
                Spellbook.Ancients,
                "Lunar",
                Spellbook.Lunars,
                "Arceuus",
                Spellbook.Arceuus,
                title = "Choose a spellbook",
            )
        swapTo(book)
    }

    private fun ProtectedAccess.previous() {
        if (!usable()) {
            return
        }
        val book = bookOf(player.vars[GrimoireVarBits.previous])
        swapTo(book)
    }

    private fun ProtectedAccess.checkPrevious() {
        val book = bookOf(player.vars[GrimoireVarBits.previous])
        mes("Your previous spellbook is the ${LeagueChat.blue(nameOf(book))} spellbook.")
    }

    private fun ProtectedAccess.swapTo(book: Spellbook) {
        val current = player.vars[varbits.spellbook]
        if (current == book.varValue) {
            mes("You are already using the ${nameOf(book)} spellbook.")
            return
        }
        VarPlayerIntMapSetter.set(player, GrimoireVarBits.previous, current)
        VarPlayerIntMapSetter.set(player, varbits.spellbook, book.varValue)
        mes("You turn to the ${LeagueChat.blue(nameOf(book))} spellbook.")
    }

    private fun ProtectedAccess.usable(): Boolean {
        if (tools.isChosen(player, RelicItem.Grimoire)) {
            return true
        }
        mes("Only someone with the Grimoire relic can use the grimoire.")
        return false
    }

    private fun bookOf(value: Int): Spellbook =
        Spellbook.entries.firstOrNull { it.varValue == value } ?: Spellbook.Standard

    private fun nameOf(book: Spellbook): String =
        when (book) {
            Spellbook.Standard -> "standard"
            Spellbook.Ancients -> "Ancient Magicks"
            Spellbook.Lunars -> "Lunar"
            Spellbook.Arceuus -> "Arceuus"
        }
}
