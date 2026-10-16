package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.seqs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenFullOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.timer.TimerReferences
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object GodWarsLocs : LocReferences() {
    // The two rocks inside the dungeon that a rope is tied to, in both of their states, and the
    // rope's other end below them.
    val rock_1_untied = find("godwars_rock_no_rope1")
    val rock_1_tied = find("godwars_rock_rope1")
    // The cache's second bottom multi (godwars_rock_rope_multi2_bottom) reuses this same tied state
    // instead of godwars_rock_rope_bottom2, so both ropes' bottoms click as this one type.
    val rope_bottom = find("godwars_rock_rope_bottom1")
    val rock_2_untied = find("godwars_rock_no_rope2")
    val rock_2_tied = find("godwars_rock_rope2")
}

object GodWarsInterfaces : InterfaceReferences() {
    /** The kill count box, whose own scripts show it only inside the dungeon's map area. */
    val overlay = find("godwars_overlay")
}

object GodWarsTimers : TimerReferences() {
    /** Ticks on every player and shows the kill count box while they are in the dungeon. */
    val overlay = find("godwars_overlay_tick")
}

/** A rope inside the dungeon: down from [top] to [below], and back up. */
private class GodWarsRope(
    val untied: LocType,
    val tied: LocType,
    val varbit: VarBitType,
    val top: CoordGrid,
    val landBelow: CoordGrid,
    val bottomAt: CoordGrid,
    val landAbove: CoordGrid,
)

/**
 * The God Wars Dungeon, on top of the real map ([Dungeon.GOD_WARS_DUNGEON]):
 * - **Ropes.** The entrance rope and the two ropes down to Saradomin's encampment start untied in
 *   the real game and take a rope item. Here they are tied for a player who comes in, and using an
 *   untied rock ties it (no rope needed), so a relog never leaves anyone stuck. Tying or climbing
 *   down the Saradomin ropes takes 70 base Agility; climbing back up is free. With
 *   [GodWarsCrossingItems] on, the Saradomin rocks start untied and tying one spends a rope.
 * - **Kill counts.** Each of the four gods counts the followers a player has killed in the dungeon
 *   (the Wiki's lists, by cache name). They are saved with the player and never reset by coming or
 *   going. The chambers' doors ask for [KILL_COUNT] of them (see [DungeonGates]) but do not spend
 *   them, so reaching it once opens the door for good. The count stops at [MAX_KILLS], the most the
 *   kill count box can show, and is also told in the chat box with each kill.
 * - **Kill count box.** The real `godwars_overlay` interface (406) is open while the player is in
 *   the dungeon's map area and closed outside it. It reads the four counts from the same varbits
 *   with its own client scripts, so the server only opens and closes it.
 */
class GodWarsScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val godItems: GodItems,
    private val rooms: GodRooms,
) : PluginScript() {
    private val ropes =
        listOf(
            GodWarsRope(
                untied = GodWarsLocs.rock_1_untied,
                tied = GodWarsLocs.rock_1_tied,
                varbit = GodWarsVarbits.saradomin_rope_1,
                top = CoordGrid(2913, 5300, 2),
                landBelow = CoordGrid(2915, 5300, 1),
                bottomAt = CoordGrid(2914, 5300, 1),
                landAbove = CoordGrid(2912, 5300, 2),
            ),
            GodWarsRope(
                untied = GodWarsLocs.rock_2_untied,
                tied = GodWarsLocs.rock_2_tied,
                varbit = GodWarsVarbits.saradomin_rope_2,
                top = CoordGrid(2920, 5274, 1),
                landBelow = CoordGrid(2919, 5274, 0),
                bottomAt = CoordGrid(2920, 5274, 0),
                landAbove = CoordGrid(2920, 5276, 1),
            ),
        )

    override fun ScriptContext.startup() {
        GodWarsCrossingItems.required = GodWarsSettings.load().crossingItems

        onCommand("gwditems") {
            modLevel = modlevels.admin
            desc = "Ask for the God Wars crossings' items (hammer, grapple, rope): on or off"
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "on" -> GodWarsCrossingItems.required = true
                    "off" -> GodWarsCrossingItems.required = false
                    else -> Unit
                }
                val state = if (GodWarsCrossingItems.required) "on" else "off"
                player.mes("God Wars crossing items are $state. Use ::gwditems on|off.")
            }
        }

        onEvent<DungeonEntered> {
            if (dungeon == Dungeon.GOD_WARS_DUNGEON) {
                prepare(player)
            }
        }

        onPlayerLogin { player.softTimer(GodWarsTimers.overlay, 1) }
        onPlayerSoftTimer(GodWarsTimers.overlay) { syncOverlay(player) }

        for (rope in ropes) {
            onOpLoc1(rope.untied) { tie(rope) }
            onOpLoc2(rope.tied) { climbDown(rope) }
        }
        onOpLoc1(GodWarsLocs.rope_bottom) {
            val rope = ropes.firstOrNull { rope -> rope.bottomAt == it.loc.coords }
            if (rope != null) {
                climbUp(rope)
            }
        }

        onEvent<NpcDeathEvents.Killed> {
            val hero = hero ?: return@onEvent
            val name = npc.visType.internalName ?: return@onEvent
            val god = God.ofFollower(name) ?: return@onEvent
            if (Dungeon.GOD_WARS_DUNGEON.areas.none { npc.coords in it }) {
                return@onEvent
            }
            count(hero, god)
        }
    }

    /**
     * Opens the kill count box on a player who is in the dungeon, closes it on one who is not. Also
     * keeps the god item switches right while they are inside, whatever changed what they wear (a
     * death, a bank swap), on top of the worn-item hook in [GodItemsScript].
     */
    fun syncOverlay(player: Player) {
        // A private room is a copy of the map, so look at where the player would stand in the real
        // one.
        val at = rooms.toNormal(player.coords)
        val inside = Dungeon.GOD_WARS_DUNGEON.areas.any { at in it }
        if (inside) {
            godItems.sync(player)
        }
        val open = player.ui.containsOverlay(GodWarsInterfaces.overlay)
        if (inside && !open) {
            player.ifOpenFullOverlay(GodWarsInterfaces.overlay, eventBus)
        } else if (!inside && open) {
            player.ifCloseOverlay(GodWarsInterfaces.overlay, eventBus)
        }
    }

    /**
     * Ties the ropes (unless [GodWarsCrossingItems] asks for a rope to tie them) and shows the
     * chamber doors' private option. The kill counts stay as they are.
     */
    fun prepare(player: Player) {
        if (!GodWarsCrossingItems.required) {
            for (rope in ropes) {
                VarPlayerIntMapSetter.set(player, rope.varbit, 1)
            }
        }
        VarPlayerIntMapSetter.set(player, GodWarsVarbits.entrance_rope, 1)
        // 1 shows the chamber doors as "Open (normal)", "Open (private)" and "Peek".
        VarPlayerIntMapSetter.set(player, GodWarsVarbits.door_state, 1)
    }

    /** Adds a kill to [god]'s count for [player]. */
    fun count(player: Player, god: God) {
        val kills = minOf(player.vars[god.kills] + 1, MAX_KILLS)
        VarPlayerIntMapSetter.set(player, god.kills, kills)
        player.mes("Your ${god.title} kill count is now $kills.")
    }

    /** The Wiki asks 70 Agility, not boostable, for the Saradomin ropes (down only). */
    private fun ProtectedAccess.agileEnough(): Boolean {
        if (statBase(stats.agility) >= ROPE_AGILITY) {
            return true
        }
        mes("You need an Agility level of at least $ROPE_AGILITY to do this.")
        return false
    }

    private fun ProtectedAccess.tie(rope: GodWarsRope) {
        if (!agileEnough()) {
            return
        }
        if (GodWarsCrossingItems.required) {
            if (invTotal(inv, CrossingObjs.rope) == 0) {
                mes("You need a rope to tie here.")
                return
            }
            invDel(inv, CrossingObjs.rope, 1)
        }
        anim(seqs.human_reachforladder)
        soundSynth(GodWarsSynths.rope_tie)
        mes("You tie the rope to the rock.")
        VarPlayerIntMapSetter.set(player, rope.varbit, 1)
    }

    private suspend fun ProtectedAccess.climbDown(rope: GodWarsRope) {
        arriveDelay()
        if (!agileEnough()) {
            return
        }
        anim(seqs.human_reachforladder)
        mes("You climb down the rope.")
        delay(1)
        telejump(rope.landBelow)
    }

    private suspend fun ProtectedAccess.climbUp(rope: GodWarsRope) {
        arriveDelay()
        anim(seqs.human_reachforladder)
        delay(1)
        telejump(rope.landAbove)
    }

    companion object {
        /** The Wiki's limit; the varbit holds 12 bits (4095). */
        const val MAX_KILLS = 4000

        private const val ROPE_AGILITY = 70
    }
}
