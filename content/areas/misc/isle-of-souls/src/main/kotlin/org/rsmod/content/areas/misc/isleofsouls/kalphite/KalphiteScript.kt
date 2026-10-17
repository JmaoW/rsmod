package org.rsmod.content.areas.misc.isleofsouls.kalphite

import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.dungeons.Dungeon
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonEntered
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rope down into the Kalphite Queen's chamber and the rope back up, and the timer that runs the
 * fight. The fight itself is [KalphiteQueen].
 *
 * The chamber entrance in the Kalphite Lair is a tunnel that a rope has to be used on. Its cache
 * object changes with a varbit, so the isle simply shows every player the roped tunnel
 * ([KalphiteVarbits.entrance] = 1) and no rope is needed. Each team has a chamber of its own.
 */
class KalphiteScript
@Inject
constructor(private val queen: KalphiteQueen, private val instances: TeamInstances) :
    PluginScript() {
    override fun ScriptContext.startup() {
        instances.register(KalphiteInstance.type)

        onEvent<DungeonEntered> {
            if (dungeon == Dungeon.KALPHITE_LAIR) {
                showRope(player)
            }
        }
        onPlayerLogin { showRope(player) }

        onOpLoc1(kalphite_locs.entrance) { enter() }
        onOpLoc3(kalphite_locs.entrance) { peek() }
        // The wiki's "normal" and "private" ways in: the isle has only the private chamber.
        onOpLoc1(kalphite_locs.entrance_private) { enter() }
        onOpLoc2(kalphite_locs.entrance_private) { enter() }
        onOpLoc3(kalphite_locs.entrance_private) { peek() }
        onOpLoc1(kalphite_locs.exit) { leave() }

        onPlayerSoftTimer(kalphite_timers.tick) {
            if (!queen.tick(player)) {
                player.clearSoftTimer(kalphite_timers.tick)
            }
        }
        onPlayerLogout { queen.sweep(except = player) }
    }

    private fun showRope(player: Player) {
        VarPlayerIntMapSetter.set(player, kalphite_varbits.entrance, 1)
    }

    private fun ProtectedAccess.enter() {
        when (queen.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the chamber.")
            EnterResult.NoSpace -> mes("There is no room for another chamber right now. Try again.")
            EnterResult.InvalidEntry -> mes("The tunnel is blocked.")
        }
    }

    private fun ProtectedAccess.peek() {
        mes("You peek down the tunnel. Something huge is moving about below.")
    }

    private fun ProtectedAccess.leave() {
        queen.leave(player)
    }
}
