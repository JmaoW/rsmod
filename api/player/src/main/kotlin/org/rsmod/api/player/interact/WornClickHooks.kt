package org.rsmod.api.player.interact

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.game.entity.Player

/**
 * Sees a click on a worn equipment slot before the item's own option runs. Returning `true` takes
 * the click: the item's option (normally Remove) does not run. Sol Heredit's grapple, where the
 * player defends a slot by clicking it, is one.
 */
public fun interface WornClickHook {
    public fun onClick(player: Player, wornSlot: Int): Boolean
}

/** The [WornClickHook]s content has registered. */
@Singleton
public class WornClickHooks @Inject constructor() {
    private val hooks = ArrayList<WornClickHook>()

    public fun register(hook: WornClickHook) {
        hooks += hook
    }

    /** Whether a hook took [player]'s click on [wornSlot]. */
    public fun consume(player: Player, wornSlot: Int): Boolean =
        hooks.any { it.onClick(player, wornSlot) }
}
