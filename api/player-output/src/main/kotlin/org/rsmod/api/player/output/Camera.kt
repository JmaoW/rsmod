package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.camera.CamLookAt
import net.rsprot.protocol.game.outgoing.camera.CamMoveTo
import net.rsprot.protocol.game.outgoing.camera.CamReset
import net.rsprot.protocol.game.outgoing.camera.CamShake
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

public object Camera {
    public fun camReset(player: Player) {
        player.client.write(CamReset)
    }

    public fun camLookAt(player: Player, dest: CoordGrid, height: Int, rate: Int, rate2: Int) {
        // TODO: Add require assertion to make sure coords is valid within build area.
        val dx = dest.x - player.buildArea.x
        val dz = dest.z - player.buildArea.z
        player.client.write(CamLookAt(dx, dz, height, rate, rate2))
    }

    public fun camMoveTo(player: Player, dest: CoordGrid, height: Int, rate: Int, rate2: Int) {
        // TODO: Add require assertion to make sure coords is valid within build area.
        val dx = dest.x - player.buildArea.x
        val dz = dest.z - player.buildArea.z
        player.client.write(CamMoveTo(dx, dz, height, rate, rate2))
    }

    /**
     * Shakes the camera: [type] is the axis (0 to 4), [intensity] how far, [movement] how fast the
     * shake moves and [speed] how fast it cycles. Stop it with a shake of intensity 0.
     */
    public fun camShake(player: Player, type: Int, intensity: Int, movement: Int, speed: Int) {
        player.client.write(CamShake(type, intensity, movement, speed))
    }
}
