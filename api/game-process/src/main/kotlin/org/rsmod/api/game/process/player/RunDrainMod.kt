package org.rsmod.api.game.process.player

import org.rsmod.game.entity.Player

public fun interface RunDrainMod {
    /** Whether running should not use up any of this player's run energy. */
    public fun Player.preventsRunDrain(): Boolean
}
