package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.type.interf.IfEvent

/**
 * The raid's side panel (500, in place of the chat-channel tab) and points overlay (513).
 *
 * Contract (this client's scripts 1538-1553 and 1505-1511, 2289):
 * - Side panel: `raids_client_partysize`, `_highestcombat`, `_partysize_scaled`, `_isleader` and
 *   `_progress` (0 waiting for the leader, 1 upper level, 2 middle (Challenge Mode) or lower, 3
 *   lower, 4 the bottom, 5 "Get out!"). While waiting the leader gets "Start raid", an op on
 *   `raids_sidepanel:start`. Script 1547 sizes the list, 1548 adds `name|combat|total|`.
 * - Overlay: `raids_client_partyscore` the team's points, varp `raids_playerscore` the player's,
 *   `raids_timer` the seconds-ish timer the client itself counts up every 30 client cycles while
 *   the progress is under 5 (the server sets where it starts).
 */
@Singleton
class CoxHud @Inject constructor(private val eventBus: EventBus, private val clock: MapClock) {
    private val open = HashSet<Int>()

    fun isOpen(player: Player): Boolean = player.accountId in open

    fun open(raid: CoxRaid, player: Player) {
        if (open.add(player.accountId)) {
            player.ifOpenOverlay(
                cox_interfaces.sidepanel,
                components.toplevel_target_side7,
                eventBus,
            )
            player.ifOpenOverlay(
                cox_interfaces.overlay,
                components.toplevel_target_overlay_atmosphere,
                eventBus,
            )
        }
        VarPlayerIntMapSetter.set(player, cox_varbits.in_dungeon, 1)
        refresh(raid, player)
        members(raid, player)
    }

    fun close(player: Player) {
        if (open.remove(player.accountId)) {
            player.ifCloseOverlay(cox_interfaces.overlay, eventBus)
            player.ifCloseOverlay(cox_interfaces.sidepanel, eventBus)
            player.ifOpenOverlay(
                interfaces.side_channels,
                components.toplevel_target_side7,
                eventBus,
            )
        }
        VarPlayerIntMapSetter.set(player, cox_varbits.in_dungeon, 0)
        VarPlayerIntMapSetter.set(player, cox_varbits.progress, 0)
        VarPlayerIntMapSetter.set(player, cox_varbits.party_score, 0)
        VarPlayerIntMapSetter.set(player, cox_varps.player_score, 0)
        VarPlayerIntMapSetter.set(player, cox_varbits.is_leader, 0)
    }

    fun refresh(raid: CoxRaid, player: Player) {
        val member = raid.member(player) ?: return
        val size = raid.active.size
        VarPlayerIntMapSetter.set(
            player,
            cox_varbits.is_leader,
            if (raid.isLeader(player)) 1 else 0,
        )
        VarPlayerIntMapSetter.set(player, cox_varbits.party_size, size.coerceAtMost(MAX_SIZE))
        VarPlayerIntMapSetter.set(
            player,
            cox_varbits.party_size_scaled,
            (if (raid.started) raid.scaling.size else size).coerceAtMost(MAX_SIZE),
        )
        val combat = raid.active.maxOfOrNull { it.player.combatLevel } ?: 3
        VarPlayerIntMapSetter.set(player, cox_varbits.highest_combat, combat)
        VarPlayerIntMapSetter.set(player, cox_varbits.map_pool, if (raid.challenge) 2 else 0)
        VarPlayerIntMapSetter.set(player, cox_varbits.progress, raid.stage.value)
        points(raid, player, member)
        if (raid.isLeader(player) && !raid.started) {
            player.ifSetEvents(cox_components.sidepanel_start, -1..-1, IfEvent.Op1)
        }
    }

    fun points(raid: CoxRaid, player: Player, given: CoxMember? = null) {
        val member = given ?: raid.member(player) ?: return
        VarPlayerIntMapSetter.set(
            player,
            cox_varbits.party_score,
            raid.totalPoints.coerceIn(0, MAX_POINTS),
        )
        VarPlayerIntMapSetter.set(player, cox_varps.player_score, member.points.coerceAtLeast(0))
    }

    /** Sets the overlay's timer to the raid's time so far (the client counts on from it). */
    fun timer(raid: CoxRaid, player: Player) {
        val ticks = if (raid.startCycle < 0) 0 else clock.cycle - raid.startCycle
        VarPlayerIntMapSetter.set(player, cox_varbits.timer, ticks.coerceIn(0, MAX_TIMER))
    }

    fun members(raid: CoxRaid, player: Player) {
        val list = raid.active
        player.runClientScript(LINES_SCRIPT, list.size)
        for ((i, member) in list.withIndex()) {
            val total = CoxBoard.SKILLS.sumOf { member.player.statBase(it) }
            player.runClientScript(
                LINE_SCRIPT,
                i,
                "${member.player.displayName}|${member.player.combatLevel}|$total|",
            )
        }
    }

    fun refreshAll(raid: CoxRaid) {
        for (member in raid.active) {
            if (isOpen(member.player)) refresh(raid, member.player)
        }
    }

    fun membersAll(raid: CoxRaid) {
        for (member in raid.active) {
            if (isOpen(member.player)) members(raid, member.player)
        }
    }

    /** The overlay's fade to black (script 1512) and back (1513). */
    fun fadeOut(player: Player) {
        if (isOpen(player)) player.runClientScript(FADE_OUT_SCRIPT)
    }

    fun fadeIn(player: Player) {
        if (isOpen(player)) player.runClientScript(FADE_IN_SCRIPT)
    }

    companion object {
        const val LINES_SCRIPT = 1547
        const val LINE_SCRIPT = 1548
        const val FADE_OUT_SCRIPT = 1512
        const val FADE_IN_SCRIPT = 1513

        /** `raids_client_partyscore` is 17 bits. */
        const val MAX_POINTS = 131071
        const val MAX_SIZE = 100
        const val MAX_TIMER = 65535
    }
}
