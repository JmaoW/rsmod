package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player

/**
 * The raid's heads-up display (interface 481). The client draws all of it from varbits and two
 * scripts: one that gives the party's names, one that runs the timer.
 *
 * Contract (read from the HUD's scripts): `toa_client_current_path` is 0 in the Nexus, 1-4 on a
 * path (Scabaras, Het, Apmeken, Crondis) and 5 or 6 in the Wardens; each party slot has an orb
 * varbit that is 0 for an empty slot, 1-29 for health, 30 for a dead player and 31 for one in
 * another room; the viewer's own slot (counting from 1) is `toa_client_partyslot`.
 */
@Singleton
class ToaHud @Inject constructor(private val eventBus: EventBus, private val clock: MapClock) {
    fun open(raid: ToaRaid, player: Player) {
        player.ifOpenOverlay(toa_interfaces.hud, components.toplevel_target_overlay_hud, eventBus)
        VarPlayerIntMapSetter.set(player, toa_varbits.raid_level, raid.raidLevel)
        names(raid, player)
        timer(raid, player)
        pathLevels(raid, player)
        refreshOrbs(raid, player)
    }

    fun close(player: Player) {
        player.ifCloseOverlay(toa_interfaces.hud, eventBus)
        VarPlayerIntMapSetter.set(player, toa_varbits.party_slot, 0)
        VarPlayerIntMapSetter.set(player, toa_varbits.current_path, 0)
        for (orb in toa_varbits.member_orbs) {
            VarPlayerIntMapSetter.set(player, orb, 0)
        }
    }

    /** The HUD's name list, one entry for each slot of the party (empty for the unused ones). */
    fun names(raid: ToaRaid, player: Player) {
        val names =
            List(ToaParties.MAX_SIZE) { raid.members.getOrNull(it)?.player?.displayName ?: "" }
        player.runClientScript(NAMES_SCRIPT, *names.toTypedArray())
    }

    fun namesForAll(raid: ToaRaid) {
        for (member in raid.active) {
            names(raid, member.player)
        }
    }

    /** The timer: running from the first path, or frozen at the finishing time. */
    fun timer(raid: ToaRaid, player: Player) {
        if (raid.challengeStartCycle < 0) {
            return
        }
        if (raid.endCycle >= 0) {
            player.runClientScript(TIMER_SCRIPT, raid.elapsed(clock.cycle), 1)
        } else {
            player.runClientScript(TIMER_SCRIPT, raid.elapsed(clock.cycle), 0)
        }
    }

    fun pathLevels(raid: ToaRaid, player: Player) {
        for (path in ToaPath.entries) {
            VarPlayerIntMapSetter.set(
                player,
                toa_varbits.pathLevel(path),
                raid.pathLevels[path.index],
            )
        }
    }

    fun pathLevelsForAll(raid: ToaRaid) {
        for (member in raid.active) {
            pathLevels(raid, member.player)
        }
    }

    fun currentPath(player: Player, value: Int) {
        VarPlayerIntMapSetter.set(player, toa_varbits.current_path, value)
    }

    /** Every viewer's orbs. Called every other tick, and whenever someone dies or moves room. */
    fun refreshAllOrbs(raid: ToaRaid) {
        for (member in raid.active) {
            refreshOrbs(raid, member.player)
        }
    }

    fun refreshOrbs(raid: ToaRaid, viewer: Player) {
        val viewerMember = raid.member(viewer) ?: return
        VarPlayerIntMapSetter.set(
            player = viewer,
            varbit = toa_varbits.party_slot,
            value = viewerMember.slot + 1,
        )
        for (slot in 0 until ToaParties.MAX_SIZE) {
            val other = raid.members.getOrNull(slot)
            val value =
                when {
                    other == null || other.left || raid.endCycle >= 0 -> 0
                    other.room != viewerMember.room -> ELSEWHERE
                    other.ghost -> DEAD
                    else -> health(other.player)
                }
            VarPlayerIntMapSetter.set(viewer, toa_varbits.member_orbs[slot], value)
        }
    }

    private fun health(player: Player): Int {
        val max = player.statBase(stats.hitpoints).coerceAtLeast(1)
        val now = player.stat(stats.hitpoints)
        return 1 + (now * 28 / max).coerceIn(0, 28)
    }

    companion object {
        const val NAMES_SCRIPT = 6585
        const val TIMER_SCRIPT = 6580
        const val DEAD = 30
        const val ELSEWHERE = 31
    }
}
