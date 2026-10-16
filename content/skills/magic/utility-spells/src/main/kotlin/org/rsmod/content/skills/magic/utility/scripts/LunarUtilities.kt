package org.rsmod.content.skills.magic.utility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onApNpcT
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.spells.events.SpellCast
import org.rsmod.content.skills.magic.utility.configs.lunar_seqs
import org.rsmod.content.skills.magic.utility.configs.lunar_spotanims
import org.rsmod.content.skills.magic.utility.configs.utility_timers
import org.rsmod.content.skills.runecraft.scripts.Runecrafting
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// Magic Imbue lets combination runes be made without the talisman for 21 ticks (it only matters at
// an altar, see [Runecrafting.ImbueCheck]); it can be cast again once those are over.
//
// Spellbook Swap lets the player pick another spellbook (the standard, Ancient Magicks or Arceuus)
// and cast one spell from it within two minutes; the Lunar spellbook comes back after that cast,
// when the time runs out, or at logout.
//
// Monster Examine lists a monster's levels and hitpoints in chat, in place of the real game's
// interface. NPC Contact and the spells for farming, hunting and curing poison are in
// [DisabledSpells].
class LunarUtilities @Inject constructor(private val caster: SpellCaster) : PluginScript() {
    private var Player.spellbook by intVarBit(varbits.spellbook)

    /** The cycle each player's Magic Imbue runs out. */
    private val imbuedUntil = WeakHashMap<Player, Int>()

    /** The players who have swapped away from the Lunar spellbook. */
    private val swapped = WeakHashMap<Player, Boolean>()

    override fun ScriptContext.startup() {
        with(caster) {
            onSpell(objs.spell_magic_imbue) { imbue() }
            onSpell(objs.spell_spellbook_swap) { swap() }
            onApNpcT(spell(objs.spell_monster_examine).component) { examine(it.npc) }
        }
        onEvent<Runecrafting.ImbueCheck> {
            imbued = player.currentMapClock < (imbuedUntil[player] ?: Int.MIN_VALUE)
        }
        onEvent<SpellCast> {
            if (swapped.containsKey(player) && spell.spellbook != Spellbook.Lunars) {
                restoreBook(player)
            }
        }
        onPlayerSoftTimer(utility_timers.spellbook_swap) { restoreBook(player) }
        onPlayerLogout {
            restoreBook(player)
            imbuedUntil.remove(player)
        }
    }

    fun isImbued(player: Player): Boolean =
        player.currentMapClock < (imbuedUntil[player] ?: Int.MIN_VALUE)

    private fun ProtectedAccess.imbue() {
        if (isImbued(player)) {
            return
        }
        with(caster) { cast(objs.spell_magic_imbue, IMBUE_TICKS) } ?: return
        imbuedUntil[player] = mapClock + IMBUE_TICKS
        anim(lunar_seqs.imbue)
        mes("You are charged to combine runes!")
        publish(Imbued(player))
    }

    private suspend fun ProtectedAccess.swap() {
        val book =
            when (choice3("Standard", 0, "Ancient Magicks", 1, "Arceuus", 2)) {
                0 -> Spellbook.Standard
                1 -> Spellbook.Ancients
                else -> Spellbook.Arceuus
            }
        swapTo(book)
    }

    /** Pays for the spell and swaps to [book] for one cast. */
    fun ProtectedAccess.swapTo(book: Spellbook) {
        with(caster) { cast(objs.spell_spellbook_swap, SWAP_COOLDOWN) } ?: return
        player.spellbook = book.varValue
        swapped[player] = true
        player.softTimer(utility_timers.spellbook_swap, SWAP_TICKS)
        anim(lunar_seqs.spellbook_swap)
        spotanim(lunar_spotanims.spellbook_swap, height = SPOTANIM_HEIGHT)
        mes("You swap to the ${book.name.lowercase()} spellbook for one spell.")
        publish(SpellbookSwapped(player, book))
    }

    private fun restoreBook(player: Player) {
        if (swapped.remove(player) == null) {
            return
        }
        player.clearSoftTimer(utility_timers.spellbook_swap)
        player.spellbook = Spellbook.Lunars.varValue
        player.mes("Your spellbook returns to the Lunar spellbook.")
    }

    private fun ProtectedAccess.examine(npc: Npc) {
        with(caster) { cast(objs.spell_monster_examine, EXAMINE_COOLDOWN) } ?: return
        val type = npc.visType
        mes(
            "${type.name} (level ${type.vislevel}): ${npc.hitpoints}/${npc.baseHitpointsLvl} hitpoints."
        )
        mes(
            "Attack ${npc.attackLvl}, Strength ${npc.strengthLvl}, Defence ${npc.defenceLvl}, " +
                "Magic ${npc.magicLvl}, Ranged ${npc.rangedLvl}."
        )
        anim(lunar_seqs.cast)
        publish(Examined(player, npc))
    }

    /** Published once [player] has cast Magic Imbue. */
    class Imbued(val player: Player) : UnboundEvent

    /** Published once [player] has swapped to [book]. */
    class SpellbookSwapped(val player: Player, val book: Spellbook) : UnboundEvent

    /** Published once [player] has examined [npc]. */
    class Examined(val player: Player, val npc: Npc) : UnboundEvent

    private companion object {
        const val IMBUE_TICKS = 21
        const val SWAP_TICKS = 200
        const val SWAP_COOLDOWN = 4
        const val EXAMINE_COOLDOWN = 4
        const val SPOTANIM_HEIGHT = 92
    }
}
