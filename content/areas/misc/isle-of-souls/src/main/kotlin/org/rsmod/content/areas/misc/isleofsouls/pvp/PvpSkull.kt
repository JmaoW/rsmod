package org.rsmod.content.areas.misc.isleofsouls.pvp

import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.WeakHashMap
import org.rsmod.api.config.constants
import org.rsmod.content.areas.misc.isleofsouls.revenant.AvariceScript
import org.rsmod.content.areas.misc.isleofsouls.revenant.AvariceVarps
import org.rsmod.game.entity.Player

/**
 * The skull a player gets for attacking another player: the white one, the same as the amulet of
 * avarice's, and it lasts 20 minutes from the latest attack. Dying clears it; logging out does not
 * keep it.
 *
 * Both skulls draw the same icon, so the one that ends must leave the icon on while the other still
 * holds it: [expire] checks the amulet's state, and the amulet's own expiry asks [active].
 */
@Singleton
class PvpSkull @Inject constructor() {
    private val expiry = WeakHashMap<Player, Int>()

    /** Whether [player] has a skull from attacking someone. */
    fun active(player: Player): Boolean = player in expiry

    /** Skulls [player], or restarts the 20 minutes if they already have the skull. */
    fun refresh(player: Player) {
        expiry[player] = player.currentMapClock + SKULL_TICKS
        player.skullIcon = SKULL_ICON
    }

    /** Ends the skull if its time is up. Called every tick while the player is logged in. */
    fun tick(player: Player) {
        val end = expiry[player] ?: return
        if (player.currentMapClock >= end) {
            expire(player)
        }
    }

    /** Ends the skull now. The icon stays while the amulet of avarice still holds it. */
    fun expire(player: Player) {
        if (expiry.remove(player) == null) {
            return
        }
        val amulet = player.vars[AvariceVarps.skull] != AvariceScript.NONE
        player.skullIcon = if (amulet) SKULL_ICON else null
    }

    /** Forgets the skull without touching the icon (death resets the icon itself). */
    fun clear(player: Player) {
        expiry.remove(player)
    }

    companion object {
        /** 20 minutes. */
        const val SKULL_TICKS = 2_000

        /** The white skull, the same one a player gets in the wilderness. */
        const val SKULL_ICON = constants.skullicon_default
    }
}
