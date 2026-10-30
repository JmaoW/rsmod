package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.script.onOpObj4
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Obor's and Bryophyta's lairs as the player sees them: the key-locked gates in, the exits, the
 * pit's rocks, the chests, the logs with a bronze axe, and giant bones. The fights are [Obor] and
 * [Bryophyta]; the combat rules are [GiantCombatScript].
 */
class GiantLairScript
@Inject
constructor(
    private val obor: Obor,
    private val bryophyta: Bryophyta,
    private val instances: TeamInstances,
    private val chests: GiantChests,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val bones: GiantBones,
    private val staff: BryophytasStaff,
) : PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(GiantLairs.obor.type)
        instances.register(GiantLairs.bryophyta.type)

        // Obor's gate, exit and pit.
        for (gate in listOf(giant_locs.obor_gate_north, giant_locs.obor_gate_south)) {
            onOpLoc1(gate) { enterLair(obor, ObOrGate) }
        }
        for (exit in listOf(giant_locs.obor_exit_east, giant_locs.obor_exit_west)) {
            onOpLoc1(exit) { askToLeave(obor) }
            onOpLoc2(exit) { leave(obor) }
        }
        onOpLoc1(giant_locs.obor_rocks) { climb(it.loc) }
        onOpLoc1(giant_locs.obor_chest) { with(chests) { open(obor, it.loc) } }

        // Bryophyta's gate, rock pile, chest and logs.
        onOpLoc1(giant_locs.bryophyta_gate) { enterLair(bryophyta, BryophytaGate) }
        onOpLoc2(giant_locs.bryophyta_gate) { checkCount() }
        onOpLoc1(giant_locs.bryophyta_exit) { askToLeave(bryophyta) }
        onOpLoc2(giant_locs.bryophyta_exit) { leave(bryophyta) }
        onOpLoc1(giant_locs.bryophyta_chest) { with(chests) { open(bryophyta, it.loc) } }
        onOpLoc1(giant_locs.logs_with_axe) { takeAxe(it.loc) }

        onPlayerSoftTimer(giant_timers.obor_tick) {
            if (!obor.tick(player)) {
                player.clearSoftTimer(giant_timers.obor_tick)
            }
        }
        onPlayerSoftTimer(giant_timers.bryophyta_tick) {
            if (!bryophyta.tick(player)) {
                player.clearSoftTimer(giant_timers.bryophyta_tick)
            }
        }
        onPlayerLogout {
            obor.sweep(except = player)
            bryophyta.sweep(except = player)
        }

        // Bryophyta's staff: her essence on a battlestaff (62 Crafting), charged with nature runes.
        onOpHeldU(giant_objs.bryophytas_essence, giant_objs.battlestaff) { with(staff) { make() } }
        onOpHeldU(giant_objs.bryophytas_staff_uncharged, objs.nature_rune) {
            with(staff) { charge() }
        }
        onOpHeld4(giant_objs.bryophytas_staff) { with(staff) { uncharge() } }

        // Giant bones: not picked up, only buried where they lie.
        onOpObj3(giant_objs.giant_bones) { with(bones) { bury(it.obj) } }
        onOpObj4(giant_objs.giant_bones) { with(bones) { take() } }
    }

    // The gates ---------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.enterLair(director: GiantDirector<*>, gate: GateText) {
        val lair = gate.lair
        val keyed = invTotal(inv, lair.key) > 0
        val flags = player.vars[giant_varps.flags]
        if (!keyed && (flags and lair.unlockFlag) == 0) {
            mes(gate.locked)
            return
        }
        gate.warning?.let { mesbox(it) }
        val go = choice2(gate.yes, true, gate.no, false, title = gate.question)
        if (!go) {
            return
        }
        // The key is kept, and needed only until the gate is first unlocked (wiki).
        if (keyed) {
            VarPlayerIntMapSetter.set(player, giant_varps.flags, flags or lair.unlockFlag)
        }
        when (director.enter(player)) {
            EnterResult.Entered -> if (keyed) mes(KEY_FITS)
            EnterResult.AlreadyInside -> mes("You are already in the lair.")
            EnterResult.NoSpace -> mes("There is no room for another lair right now. Try again.")
            EnterResult.InvalidEntry -> mes("The gate will not budge.")
        }
    }

    private suspend fun ProtectedAccess.askToLeave(director: GiantDirector<*>) {
        val lair = director.runOf(player)?.lair ?: return
        val go =
            choice2(
                "Exit the lair.",
                true,
                "Stay.",
                false,
                title = "Are you sure you want to leave ${lair.lairName}?",
            )
        if (go) {
            leave(director)
        }
    }

    private fun ProtectedAccess.leave(director: GiantDirector<*>) {
        director.leave(player)
    }

    private fun ProtectedAccess.checkCount() {
        val count = player.vars[GiantLairs.bryophyta.countVarp]
        mes(GiantLairs.bryophyta.countMessage.format(count))
    }

    // Obor's pit --------------------------------------------------------------------------------

    private fun ProtectedAccess.climb(loc: BoundLocInfo) {
        val run = obor.runOf(player) ?: return
        obor.climb(run, player, loc.coords.x)
    }

    // The logs ----------------------------------------------------------------------------------

    /** The logs give a bronze axe to a player without one, and grow it back. */
    private suspend fun ProtectedAccess.takeAxe(logs: BoundLocInfo) {
        if (bryophyta.runOf(player) == null) {
            return
        }
        if (hasAxe(player)) {
            mesbox("You already have an axe.")
            return
        }
        if (invAdd(inv, giant_objs.bronze_axe, 1).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        locRepo.change(logs, locTypes[giant_locs.logs_without_axe], AXE_RETURN_TICKS)
        objbox(giant_objs.bronze_axe, "You take a bronze axe from the logs.")
    }

    private fun hasAxe(player: Player): Boolean {
        val worn = objTypes.getOrNull(player.righthand)?.let { GiantAxes.isAxe(it.name) } == true
        if (worn) {
            return true
        }
        return player.inv.any { obj -> obj != null && GiantAxes.isAxe(objTypes[obj].name) }
    }

    /** The words of a gate: its questions and messages. */
    private class GateText(
        val lair: GiantLair,
        val locked: String,
        val question: String,
        val yes: String,
        val no: String,
        val warning: String? = null,
    )

    private companion object {
        const val KEY_FITS = "Your key fits the gate, causing it to swing open."

        /** The logs show bare for 49 ticks (tsps). */
        const val AXE_RETURN_TICKS = 49

        val ObOrGate =
            GateText(
                lair = GiantLairs.obor,
                locked = "The gate is locked shut.",
                question = "Enter Obor's Lair?",
                yes = "Yes.",
                no = "No.",
            )

        val BryophytaGate =
            GateText(
                lair = GiantLairs.bryophyta,
                locked = "It's locked!",
                question = "Are you sure you wish to open it?",
                yes = "Yes, let's go!",
                no = "I don't think I'm quite ready yet.",
                warning =
                    "<col=7f0000>Warning!</col> You're about to enter an instanced area. " +
                        "Anything left on the ground when you leave will be lost. " +
                        "Would you like to continue?",
            )
    }
}
