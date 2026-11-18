package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetText
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player

/**
 * The party HUD (interface 28, on the atmosphere overlay as TSPS captured it).
 *
 * Contract (the HUD's scripts and TSPS's captures): `tob_client_partystatus` is 0 without a party,
 * 1 in a party in Ver Sinhaza (the HUD lists the names in its `names` text) and 2 inside the
 * theatre; there, script 2301 gives the five names, `tob_client_partyslot` is the viewer's slot
 * (from 1) and each slot's varbit is 0 empty, 1-27 health, 30 dead and 31 in another room (the
 * viewer's own slot reads 1, as captured). The room's bar is `tob_client_waveprogress_*`: its kind
 * (0 none, 1 regular, 2 greyed, 3 cyan) and the boss's hitpoints in thousandths of 1000.
 *
 * Only five slots exist: members six to eight see the first five (their own slot reads 0).
 */
@Singleton
class TobHud @Inject constructor(private val eventBus: EventBus) {
    /** What each viewer was last sent, so only changes go out. */
    private val sent = HashMap<Int, IntArray>()
    private val open = HashSet<Int>()

    fun open(player: Player) {
        if (open.add(player.accountId)) {
            player.ifOpenOverlay(
                tob_interfaces.hud,
                components.toplevel_target_overlay_atmosphere,
                eventBus,
            )
        }
    }

    fun isOpen(player: Player): Boolean = player.accountId in open

    fun close(player: Player) {
        if (open.remove(player.accountId)) {
            player.ifCloseOverlay(tob_interfaces.hud, eventBus)
        }
        sent.remove(player.accountId)
        VarPlayerIntMapSetter.set(player, tob_varbits.party_status, 0)
        VarPlayerIntMapSetter.set(player, tob_varbits.party_slot, 0)
        for (slot in tob_varbits.member_slots) {
            VarPlayerIntMapSetter.set(player, slot, 0)
        }
        VarPlayerIntMapSetter.set(player, tob_varbits.bar_type, 0)
    }

    /** In Ver Sinhaza: the party's names, or nothing without one. */
    fun lobby(player: Player, party: TobParty?) {
        open(player)
        VarPlayerIntMapSetter.set(player, tob_varbits.party_status, if (party != null) 1 else 0)
        val names =
            List(TobParties.CLIENT_SLOTS) { party?.members?.getOrNull(it)?.displayName ?: "-" }
        player.ifSetText(TobHudComponents.names, names.joinToString("<br>"))
    }

    /** Inside the theatre: the names, the viewer's slot and every slot's state. */
    fun raid(raid: TobRaid, player: Player) {
        open(player)
        sent.remove(player.accountId)
        VarPlayerIntMapSetter.set(player, tob_varbits.party_status, 2)
        val member = raid.member(player) ?: return
        val slot = if (member.slot < TobParties.CLIENT_SLOTS) member.slot + 1 else 0
        VarPlayerIntMapSetter.set(player, tob_varbits.party_slot, slot)
        names(raid, player)
        refresh(raid, player)
    }

    fun names(raid: TobRaid, player: Player) {
        val names =
            List(TobParties.CLIENT_SLOTS) { raid.members.getOrNull(it)?.player?.displayName ?: "" }
        player.runClientScript(NAMES_SCRIPT, *names.toTypedArray())
    }

    fun refreshAll(raid: TobRaid) {
        for (member in raid.active) {
            refresh(raid, member.player)
        }
    }

    /** The slots and the room's bar as [viewer] should see them. */
    fun refresh(raid: TobRaid, viewer: Player) {
        val me = raid.member(viewer) ?: return
        val last = sent.getOrPut(viewer.accountId) { IntArray(TobParties.CLIENT_SLOTS + 3) { -1 } }
        for (slot in 0 until TobParties.CLIENT_SLOTS) {
            val other = raid.members.getOrNull(slot)
            val value =
                when {
                    other == null || other.left -> 0
                    other === me -> 1
                    other.room != me.room -> ELSEWHERE
                    other.jailed -> DEAD
                    else -> health(other.player)
                }
            if (last[slot] != value) {
                last[slot] = value
                VarPlayerIntMapSetter.set(viewer, tob_varbits.member_slots[slot], value)
            }
        }
        val bar = raid.bar.takeIf { me.room == raid.room } ?: TobBar.NONE
        val base = TobParties.CLIENT_SLOTS
        if (last[base] != bar.kind) {
            last[base] = bar.kind
            VarPlayerIntMapSetter.set(viewer, tob_varbits.bar_type, bar.kind)
        }
        if (bar.kind != 0) {
            if (last[base + 1] != bar.value) {
                last[base + 1] = bar.value
                VarPlayerIntMapSetter.set(viewer, tob_varbits.bar_value, bar.value)
            }
            if (last[base + 2] != BAR_MAX) {
                last[base + 2] = BAR_MAX
                VarPlayerIntMapSetter.set(viewer, tob_varbits.bar_max, BAR_MAX)
            }
        }
    }

    private fun health(player: Player): Int {
        val max = player.statBase(stats.hitpoints).coerceIn(1, 99)
        val now = player.stat(stats.hitpoints).coerceIn(0, 99)
        return 1 + Math.round(26.0 * now / max).toInt().coerceIn(0, 26)
    }

    /** A fade to [transparency] (0 black, 255 clear) with a line of text in the middle. */
    fun fade(player: Player, transparency: Int, text: String = "", instant: Boolean = false) {
        if (!isOpen(player)) {
            return
        }
        player.runClientScript(FADE_SCRIPT, transparency, if (instant) 1 else 0, text)
    }

    /** The red portal fade used when entering the theatre and moving between rooms. */
    fun fadeRed(player: Player, text: String = "") {
        if (!isOpen(player)) {
            return
        }
        player.runClientScript(PORTAL_SCRIPT, text)
    }

    companion object {
        const val NAMES_SCRIPT = 2301
        const val FADE_SCRIPT = 2306
        const val PORTAL_SCRIPT = 2307
        const val DEAD = 30
        const val ELSEWHERE = 31
        const val BAR_MAX = 1000
    }
}

/** What the room's bar shows: its [kind] and the boss's health in thousandths. */
data class TobBar(val kind: Int, val value: Int) {
    companion object {
        val NONE = TobBar(0, 0)
        const val REGULAR = 1
        const val GREYED = 2
        const val CYAN = 3

        fun of(kind: Int, hitpoints: Int, max: Int): TobBar {
            if (hitpoints <= 0 || max <= 0) {
                return NONE
            }
            val value = (TobHud.BAR_MAX.toLong() * hitpoints / max).toInt()
            return TobBar(kind, value.coerceIn(1, TobHud.BAR_MAX))
        }
    }
}

object TobHudComponents : org.rsmod.api.type.refs.comp.ComponentReferences() {
    val names = find("tob_hud:names")
}
