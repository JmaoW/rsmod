package org.rsmod.content.generic.locs.ladders

import jakarta.inject.Inject
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Published when [player] climbs a dungeon ladder ([loc]) and is about to land on [dest], which is
 * the tile 6400 tiles north or south of them. A subscriber may change [dest] to send them somewhere
 * else, as the Mining Guild and the Isle of Souls dungeons do.
 */
class DungeonLadderClimb(
    val player: Player,
    val loc: BoundLocInfo,
    val up: Boolean,
    var dest: CoordGrid,
) : UnboundEvent

class DungeonLadderScript @Inject constructor(private val eventBus: EventBus) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(content.dungeonladder_down) { climbDown(it.loc, it.type) }
        onOpLoc1(content.dungeonladder_up) { climbUp(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.climbUp(loc: BoundLocInfo, type: UnpackedLocType): Unit =
        climb(loc, type, up = true, translateZ = -6400)

    private suspend fun ProtectedAccess.climbDown(loc: BoundLocInfo, type: UnpackedLocType): Unit =
        climb(loc, type, up = false, translateZ = 6400)

    private suspend fun ProtectedAccess.climb(
        loc: BoundLocInfo,
        type: UnpackedLocType,
        up: Boolean,
        translateZ: Int,
    ) {
        arriveDelay()
        val climb = DungeonLadderClimb(player, loc, up, player.coords.translateZ(translateZ))
        eventBus.publish(climb)
        anim(type.climbAnim())
        delay(1)
        telejump(climb.dest)
    }

    private fun UnpackedLocType.climbAnim(): SeqType = param(params.climb_anim)
}
