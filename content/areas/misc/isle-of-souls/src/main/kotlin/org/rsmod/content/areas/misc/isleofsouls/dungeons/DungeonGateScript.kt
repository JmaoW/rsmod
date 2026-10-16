package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Carries a player through the dungeons' gates ([DungeonGates]): Karuulm's alchemical doors, the
 * God Wars Dungeon's big doors, Armadyl's pillar and Zamorak's ice bridge. Each takes the player
 * from the side they stand on to the other, if they meet its rule, and the real map's walls and
 * water are left as they are.
 *
 * Bandos' door, Armadyl's pillar and Zamorak's bridge play the captured sequences
 * ([GodWarsCrossings]), and a chamber's door swings open for the walk in. The Wiki's items (the
 * hammer for Bandos' gong, the grapple and crossbow for Armadyl's pillar) are only asked for while
 * [GodWarsCrossingItems] is on. A chamber's door needs [KILL_COUNT] kills of its god's followers
 * once (they are not spent), or an ecumenical key each time instead.
 */
class DungeonGateScript
@Inject
constructor(private val rooms: GodRooms, private val crossings: GodWarsCrossings) : PluginScript() {
    override fun ScriptContext.startup() {
        // Several gates share one door type, so the type has one handler that finds its gate.
        val byType =
            DungeonGates.all
                .flatMap { gate -> gate.clicks.map { click -> click to gate } }
                .groupBy({ it.first.type }, { it })
        for ((type, entries) in byType) {
            onOpLoc1(type) { pass(entries, it.loc) }
        }

        // The generals' doors also have "Open (private)" and "Peek" (the private door, which a
        // player sees once `door_state` is 1). "Open (normal)" is the op1 above.
        val doors = byType.getValue(DungeonGateLocs.boss_door_private)
        onOpLoc2(DungeonGateLocs.boss_door_private) { enterPrivate(doors, it.loc) }
        onOpLoc3(DungeonGateLocs.boss_door_private) { peek(doors, it.loc) }
        onOpLoc3(DungeonGateLocs.boss_door) { peek(doors, it.loc) }
    }

    /** The chamber [loc] is the door of, if it is one. */
    private fun doorOf(
        entries: List<Pair<GateClick, DungeonGate>>,
        loc: BoundLocInfo,
    ): DungeonGate? =
        entries
            .firstOrNull { (click, _) -> click.coords == null || click.coords == loc.coords }
            ?.second

    /** "Open (private)": the usual kill count (or key), then the team's own copy of the room. */
    private suspend fun ProtectedAccess.enterPrivate(
        entries: List<Pair<GateClick, DungeonGate>>,
        loc: BoundLocInfo,
    ) {
        arriveDelay()
        if (rooms.instanceOf(player) != null) {
            rooms.leave(player)
            return
        }
        val gate = doorOf(entries, loc)
        val room = gate?.let { rooms.defOf(it) }
        if (gate == null || room == null) {
            mes("Nothing interesting happens.")
            return
        }
        if (coords.chebyshevDistance(gate.a) > coords.chebyshevDistance(gate.b)) {
            // Standing inside the public room: the door only lets you out.
            delay(2)
            telejump(gate.a)
            return
        }
        if (!meets(gate.rule, forward = true)) {
            return
        }
        delay(2)
        when (rooms.enterPrivate(player, room)) {
            EnterResult.Entered -> mes("You enter your private room.")
            EnterResult.AlreadyInside -> Unit
            EnterResult.NoSpace -> mes("There is no room for another private room right now.")
            EnterResult.InvalidEntry -> mes("You cannot open a private room here.")
        }
    }

    /** "Peek": how many players are in the public room behind the door. */
    private suspend fun ProtectedAccess.peek(
        entries: List<Pair<GateClick, DungeonGate>>,
        loc: BoundLocInfo,
    ) {
        arriveDelay()
        val room = doorOf(entries, loc)?.let { rooms.defOf(it) }
        if (room == null) {
            mes("Nothing interesting happens.")
            return
        }
        val count = rooms.publicCount(room)
        mes(
            if (count == 1) "You peek inside: there is 1 player in the room."
            else "You peek inside: there are $count players in the room."
        )
    }

    private suspend fun ProtectedAccess.pass(
        entries: List<Pair<GateClick, DungeonGate>>,
        loc: BoundLocInfo,
    ) {
        arriveDelay()
        // Inside a private room the door is a copy at other coordinates, and only leads out.
        if (rooms.leave(player)) {
            return
        }
        val gate = doorOf(entries, loc)
        if (gate == null) {
            mes("Nothing interesting happens.")
            return
        }
        val forward = coords.chebyshevDistance(gate.a) <= coords.chebyshevDistance(gate.b)
        val dest: CoordGrid = if (forward) gate.b else gate.a
        if (!meets(gate.rule, forward)) {
            return
        }
        val crossing = gate.crossing
        if (crossing != null) {
            with(crossings) {
                if (hasItems(crossing)) {
                    cross(gate, crossing, forward, loc)
                }
            }
            return
        }
        gate.message?.let { mes(it) }
        val door = godOfDoor(gate)
        if (door != null) {
            // The chamber's door swings open for the walk through, as captured.
            crossings.swingDoor(door)
            delay(1)
        } else {
            delay(2)
        }
        telejump(dest)
    }

    /** The god whose chamber door [gate] is, if it is one. */
    private fun godOfDoor(gate: DungeonGate): God? = (gate.rule as? GateRule.KillCount)?.god

    /** Whether the player may go through, telling them what they lack if not. */
    private fun ProtectedAccess.meets(rule: GateRule, forward: Boolean): Boolean =
        when (rule) {
            GateRule.Free -> true
            is GateRule.Level -> {
                val have = if (rule.base) statBase(rule.stat) else stat(rule.stat)
                if ((forward || rule.bothWays) && have < rule.level) {
                    mes("You need a ${rule.skill} level of at least ${rule.level} to do this.")
                    false
                } else {
                    true
                }
            }
            is GateRule.KillCount -> {
                val have = player.vars[rule.god.kills]
                if (forward && have < KILL_COUNT) {
                    // The kills are only counted; without enough, one ecumenical key will do.
                    if (invTotal(inv, GodWarsObjs.ecumenical_key) > 0) {
                        invDel(inv, GodWarsObjs.ecumenical_key, 1)
                        mes("Your ecumenical key melts into the door.")
                        true
                    } else {
                        mes(
                            "This door is locked by the power of ${rule.god.title}! You will " +
                                "need to collect the essence of at least $KILL_COUNT of his " +
                                "followers before the door will open."
                        )
                        false
                    }
                } else {
                    true
                }
            }
        }
}
