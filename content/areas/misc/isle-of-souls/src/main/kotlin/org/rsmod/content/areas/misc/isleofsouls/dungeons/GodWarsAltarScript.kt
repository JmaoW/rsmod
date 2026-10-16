package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.entity.Player
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object AltarSeqs : SeqReferences() {
    /** The kneeling animation of any altar. */
    val pray = find("human_pray")
}

object GodWarsAltarLocs : LocReferences() {
    val armadyl = find("godwars_dungeon_armadyl_altar01")
    val bandos = find("godwars_dungeon_bandos_altar01")
    val saradomin = find("godwars_dungeon_saradomin_altar01")
    val zamorak = find("godwars_dungeon_zamorak_altar01")
}

/**
 * The altar in each general's chamber (OSRS Wiki, God Wars Dungeon).
 * - **Pray:** restores the Prayer points and, for each item of the altar's god that the player
 *   wears ([GodItems]), lifts them one point above their maximum. It works once every 10 minutes
 *   (shared by the four altars, kept until the server restarts) and not while in combat; when the
 *   wait is not over it says how long is left.
 * - **Teleport:** takes the player out to just outside the chamber's door, even in combat.
 *
 * Not built: the Zaros altar of the Ancient Prison (Nex is not part of the isle).
 */
class GodWarsAltarScript
@Inject
constructor(private val godItems: GodItems, private val rooms: GodRooms) : PluginScript() {
    /** The map clock tick from which each player may use an altar again. */
    private val readyAt = WeakHashMap<Player, Int>()

    override fun ScriptContext.startup() {
        for (altar in ALTARS) {
            onOpLoc1(altar.loc) { pray(altar.god) }
            onOpLoc2(altar.loc) { leave(altar.door) }
        }
    }

    private suspend fun ProtectedAccess.pray(god: God) {
        if (isInCombat()) {
            mes("You can't use the altar while you are in combat.")
            return
        }
        val wait = (readyAt[player] ?: 0) - mapClock
        if (wait > 0) {
            mes("You must wait ${waitText(wait)} before you can use the altar again.")
            return
        }

        anim(AltarSeqs.pray)
        delay(PRAY_TICKS)
        readyAt[player] = mapClock + COOLDOWN_TICKS

        statRestore(stats.prayer)
        val boost = godItems.wornCount(player, god)
        if (boost > 0) {
            statBoost(stats.prayer, boost, 0)
            mes("You pray at the altar and feel a surge of devotion: $boost extra Prayer points.")
        } else {
            mes("You pray at the altar and recharge your Prayer points.")
        }
    }

    private fun ProtectedAccess.leave(outside: CoordGrid) {
        mes("You feel a tug as the altar sends you out of the chamber.")
        // Out of a private room the usual way, so the room can close; otherwise straight outside.
        if (!rooms.leave(player)) {
            telejump(outside)
        }
    }

    /** "3 minutes" or "45 seconds" for [ticks] game ticks still to wait. */
    fun waitText(ticks: Int): String {
        val seconds = (ticks * SECONDS_PER_TICK10 + 9) / 10
        return if (seconds >= 60) {
            val minutes = (seconds + 59) / 60
            "$minutes minute${if (minutes == 1) "" else "s"}"
        } else {
            "$seconds second${if (seconds == 1) "" else "s"}"
        }
    }

    /** Whether [player] has to wait before the next use (for tests). */
    fun ticksUntilReady(player: Player, now: Int): Int = maxOf(0, (readyAt[player] ?: 0) - now)

    /** Lets [player] use an altar again straight away (for tests and admin commands). */
    fun reset(player: Player) {
        readyAt.remove(player)
    }

    private class Altar(val god: God, val loc: LocType, val door: CoordGrid)

    companion object {
        /** The kneeling animation takes this long before the points come back. */
        private const val PRAY_TICKS = 2

        /** 10 minutes of 0.6 second ticks. */
        const val COOLDOWN_TICKS = 1000

        /** Tenths of a second per game tick. */
        private const val SECONDS_PER_TICK10 = 6

        private val ALTARS =
            listOf(
                Altar(God.ARMADYL, GodWarsAltarLocs.armadyl, DungeonGates.armadylChamber.a),
                Altar(God.BANDOS, GodWarsAltarLocs.bandos, DungeonGates.bandosChamber.a),
                Altar(God.SARADOMIN, GodWarsAltarLocs.saradomin, DungeonGates.saradominChamber.a),
                Altar(God.ZAMORAK, GodWarsAltarLocs.zamorak, DungeonGates.zamorakChamber.a),
            )
    }
}
