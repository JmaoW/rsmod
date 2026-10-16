package org.rsmod.content.areas.misc.isleofsouls.scurrius

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The broken bars and the sewage water (the way in and out of the lair), the timer that runs the
 * fight, and the food piles players can eat from. The fight itself is [Scurrius].
 */
class ScurriusScript
@Inject
constructor(private val scurrius: Scurrius, private val instances: TeamInstances) : PluginScript() {
    /** The game tick each player last ate from a food pile (not kept over a restart). */
    private val ate = HashMap<String, Int>()

    override fun ScriptContext.startup() {
        instances.register(ScurriusInstance.type)

        // The wiki's "normal" and "private" ways in: the isle has only the private lair.
        onOpLoc1(scurrius_locs.entrance) { enter() }
        onOpLoc2(scurrius_locs.entrance) { enter() }
        onOpLoc3(scurrius_locs.entrance) { peek() }
        onOpLoc1(scurrius_locs.exit) { leave() }
        onOpLoc2(scurrius_locs.exit) { leave() }

        for (pile in listOf(scurrius_locs.food_pile, scurrius_locs.food_pile_other)) {
            onOpLoc1(pile) { eatFromPile() }
        }

        onPlayerSoftTimer(scurrius_timers.tick) {
            if (!scurrius.tick(player)) {
                player.clearSoftTimer(scurrius_timers.tick)
            }
        }
        onPlayerLogout { scurrius.sweep(except = player) }
    }

    private fun ProtectedAccess.enter() {
        when (scurrius.enter(player)) {
            EnterResult.Entered -> {}
            EnterResult.AlreadyInside -> mes("You are already in the rats' lair.")
            EnterResult.NoSpace -> mes("There is no room for another lair right now. Try again.")
            EnterResult.InvalidEntry -> mes("The bars will not budge.")
        }
    }

    private fun ProtectedAccess.peek() {
        mes("You peek through the bars. Something huge is moving about in the dark.")
    }

    private fun ProtectedAccess.leave() {
        scurrius.leave(player)
    }

    /**
     * A food pile heals a player to full once every 10 minutes (wiki), when Scurrius is not up. It
     * cannot be done mid-fight.
     */
    private fun ProtectedAccess.eatFromPile() {
        val run = scurrius.runOf(player)
        if (run?.state == WaveState.InWave && run.fight?.dead == false) {
            mes("You cannot eat from the pile while Scurrius is about.")
            return
        }
        val now = player.currentMapClock
        val last = ate[player.username]
        if (last != null && now - last < EAT_COOLDOWN) {
            mes("You have already eaten your fill from the food pile recently.")
            return
        }
        ate[player.username] = now
        val base = player.baseHitpointsLvl
        if (player.hitpoints < base) {
            statHeal(stats.hitpoints, base, 0)
        }
        mes("You eat some food from the pile and feel much better.")
    }

    companion object {
        /** Ten minutes. */
        const val EAT_COOLDOWN = 1000
    }
}
