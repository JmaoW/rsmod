package org.rsmod.content.other.special.attacks.support

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onEvent
import org.rsmod.content.interfaces.prayer.tab.PrayerEvents
import org.rsmod.game.entity.Player
import org.rsmod.game.type.varbit.VarBitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Keeps a player from using the protection prayers for a while, as Sever and Crystalline Severance
 * do. A protection prayer that is on when the lock starts is turned off, and one that is switched
 * on during the lock is turned off again.
 */
@Singleton
class PrayerLocks @Inject constructor() {
    private val lockedUntil = WeakHashMap<Player, Int>()

    fun isLocked(player: Player): Boolean =
        (lockedUntil[player] ?: Int.MIN_VALUE) > player.currentMapClock

    /** Locks the protection prayers of [player] for [ticks]. Does nothing to an unlocked player's other prayers. */
    fun lock(player: Player, ticks: Int) {
        val wasLocked = isLocked(player)
        lockedUntil[player] = player.currentMapClock + ticks
        for (prayer in PROTECTION_PRAYERS) {
            switchOff(player, prayer)
        }
        if (!wasLocked) {
            player.mes("<col=ff0000>Your protection prayers have been disabled!</col>")
        }
    }

    internal fun enforce(player: Player, prayer: VarBitType) {
        if (isLocked(player) && prayer in PROTECTION_PRAYERS) {
            switchOff(player, prayer)
        }
    }

    private fun switchOff(player: Player, prayer: VarBitType) {
        if (player.vars[prayer] == 0) {
            return
        }
        VarPlayerIntMapSetter.set(player, prayer, 0)
        player.overheadIcon = null
    }

    companion object {
        val PROTECTION_PRAYERS =
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )
    }
}

class PrayerLockScript @Inject constructor(private val locks: PrayerLocks) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<PrayerEvents.Enabled> { locks.enforce(player, prayer.enabled) }
    }
}
