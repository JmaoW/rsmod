package org.rsmod.content.skills.runecraft.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.player.stat.runecraftingLvl
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld3
import org.rsmod.api.script.onOpHeldU
import org.rsmod.content.skills.runecraft.configs.Pouch
import org.rsmod.content.skills.runecraft.configs.PouchKind
import org.rsmod.content.skills.runecraft.configs.Pouches
import org.rsmod.content.skills.runecraft.configs.RunecraftObjs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - degrading and repairing pouches (left out on purpose: they never wear out here)
// - emptying a pouch straight into the bank, and rune essence in the colossal pouch's cousin, the
//   Guardians of the Rift pouch
//
// A pouch holds one kind of essence at a time: Fill takes it from the inventory, Empty puts it
// back, Check says what is inside. The pouches' contents are kept in the varbits the cache already
// has for them. A colossal pouch is made by using an abyssal needle on a set of the four others.
class EssencePouches @Inject constructor() : PluginScript() {
    override fun ScriptContext.startup() {
        for (pouch in Pouches.all) {
            onOpHeld1(pouch.obj) { fill(pouch) }
            onOpHeld2(pouch.obj) { empty(pouch) }
            onOpHeld3(pouch.obj) { check(pouch) }
        }
        for (pouch in Pouches.standard) {
            onOpHeldU(RunecraftObjs.abyssal_needle, pouch.obj) { makeColossal() }
        }
    }

    private fun ProtectedAccess.fill(pouch: Pouch) {
        val level = player.runecraftingLvl
        if (level < pouch.level) {
            mes("You need a Runecraft level of ${pouch.level} to use this pouch.")
            return
        }
        val stored = vars[pouch.count]
        val holding = if (stored > 0) PouchKind.of(vars[pouch.kind]) else PouchKind.None
        val free = pouch.capacity(level) - stored
        if (free <= 0) {
            mes("You cannot fit any more essence into the ${pouch.name} pouch.")
            return
        }

        val pure = invTotal(inv, RunecraftObjs.pure_essence)
        val plain = invTotal(inv, RunecraftObjs.rune_essence)
        if (pure + plain == 0) {
            mes("You do not have any essence to put in the pouch.")
            return
        }
        val kind =
            when (holding) {
                PouchKind.None -> if (pure > 0) PouchKind.Pure else PouchKind.Rune
                else -> holding
            }
        val available = if (kind == PouchKind.Pure) pure else plain
        if (available == 0) {
            mes("You can only keep one kind of essence in a pouch.")
            return
        }

        val moved = minOf(free, available)
        invDel(inv, essenceOf(kind), moved)
        vars[pouch.count] = stored + moved
        vars[pouch.kind] = kind.value
        publish(PouchFilled(player, pouch, kind, moved, stored + moved))
    }

    private fun ProtectedAccess.empty(pouch: Pouch) {
        val stored = vars[pouch.count]
        if (stored == 0) {
            mes("There is no essence in the ${pouch.name} pouch.")
            return
        }
        val space = inv.freeSpace()
        if (space == 0) {
            mes("You do not have enough inventory space.")
            return
        }
        val kind = PouchKind.of(vars[pouch.kind])
        val moved = minOf(stored, space)
        invAdd(inv, essenceOf(kind), moved)
        vars[pouch.count] = stored - moved
        if (stored == moved) {
            vars[pouch.kind] = PouchKind.None.value
        }
    }

    private fun ProtectedAccess.check(pouch: Pouch) {
        val stored = vars[pouch.count]
        val capacity = pouch.capacity(player.runecraftingLvl)
        if (stored == 0) {
            mes("The ${pouch.name} pouch is empty. It can hold $capacity essence.")
            return
        }
        val kind = if (PouchKind.of(vars[pouch.kind]) == PouchKind.Pure) "pure" else "rune"
        mes("The ${pouch.name} pouch holds $stored $kind essence. It can hold $capacity.")
    }

    private fun ProtectedAccess.makeColossal() {
        if (player.runecraftingLvl < Pouches.colossal.level) {
            mes("You need a Runecraft level of ${Pouches.colossal.level} to make a colossal pouch.")
            return
        }
        if (player.craftingLvl < COLOSSAL_CRAFTING_LEVEL) {
            mes("You need a Crafting level of $COLOSSAL_CRAFTING_LEVEL to make a colossal pouch.")
            return
        }
        if (Pouches.standard.any { invTotal(inv, it.obj) == 0 }) {
            mes("You need a small, medium, large and giant pouch to make a colossal pouch.")
            return
        }
        if (Pouches.standard.any { vars[it.count] > 0 }) {
            mes("Empty your pouches before you stitch them together.")
            return
        }
        for (pouch in Pouches.standard) {
            invDel(inv, pouch.obj, 1)
        }
        invDel(inv, RunecraftObjs.abyssal_needle, 1)
        invAdd(inv, RunecraftObjs.colossal_pouch, 1)
        mes("You stitch the four pouches together into a colossal pouch.")
        publish(ColossalPouchMade(player))
    }

    private fun essenceOf(kind: PouchKind): ObjType =
        if (kind == PouchKind.Pure) RunecraftObjs.pure_essence else RunecraftObjs.rune_essence

    /** Published after essence went into a pouch; [total] is what the pouch holds now. */
    class PouchFilled(
        val player: Player,
        val pouch: Pouch,
        val kind: PouchKind,
        val moved: Int,
        val total: Int,
    ) : UnboundEvent

    /** Published when a colossal pouch has been made. */
    class ColossalPouchMade(val player: Player) : UnboundEvent

    private companion object {
        const val COLOSSAL_CRAFTING_LEVEL = 56
    }
}
