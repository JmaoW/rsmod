package org.rsmod.api.game.process.npc.hunt

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * Lets a [player] go unnoticed by an aggressive npc: when it returns `true`, the npc does not pick
 * the player as a target when it hunts. It does not stop a npc that is already fighting the player
 * (one the player attacked), only the npc starting the fight.
 */
public fun interface HuntTolerance {
    public fun tolerates(npc: Npc, player: Player): Boolean
}
