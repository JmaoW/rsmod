package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public object HintArrows {
    /** Puts the flashing yellow arrow above [coords]. Only one arrow shows at a time. */
    public fun tile(player: Player, coords: CoordGrid, height: Int = 0) {
        val position = HintArrow.TileHintArrow.HintArrowTilePosition.CENTER
        val arrow = HintArrow.TileHintArrow(coords.x, coords.z, height, position)
        player.client.write(HintArrow(arrow))
    }

    /** Takes the arrow away. */
    public fun reset(player: Player) {
        player.client.write(HintArrow(HintArrow.ResetHintArrow))
    }
}
