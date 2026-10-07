package org.rsmod.content.skills.firemaking.configs

import kotlin.math.floor
import org.rsmod.api.config.refs.objs
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.queue.QueueReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.game.type.obj.ObjType

internal object FiremakingSeqs : SeqReferences() {
    val light_fire = find("human_createfire")
}

internal object FiremakingQueues : QueueReferences() {
    /** Fires when a lighting attempt is due. */
    val attempt = find("firemaking_light_attempt")

    /** Fires once the player has stepped away from a new fire and can turn to face it. */
    val face_fire = find("firemaking_face_fire")
}

internal object FiremakingObjs : ObjReferences() {
    val ashes = find("ashes")
}

internal object FiremakingLocs : LocReferences() {
    val fire = find("fire")
}

internal class FiremakingLog(val obj: ObjType, val level: Int, val xp: Double)

internal object FiremakingLogs {
    /** The logs that can be lit with a tinderbox, with the level and xp values from the wiki. */
    val all: List<FiremakingLog> =
        listOf(
            FiremakingLog(objs.logs, 1, 40.0),
            FiremakingLog(objs.achey_tree_logs, 1, 40.0),
            FiremakingLog(objs.oak_logs, 15, 60.0),
            FiremakingLog(objs.willow_logs, 30, 90.0),
            FiremakingLog(objs.teak_logs, 35, 105.0),
            FiremakingLog(objs.arctic_pine_logs, 42, 125.0),
            FiremakingLog(objs.maple_logs, 45, 135.0),
            FiremakingLog(objs.mahogany_logs, 50, 157.5),
            FiremakingLog(objs.yew_logs, 60, 202.5),
            FiremakingLog(objs.magic_logs, 75, 303.8),
            FiremakingLog(objs.redwood_logs, 90, 350.0),
        )

    /**
     * The chance (from `0.0` to `1.0`) of lighting any log at Firemaking [level]. The wiki gives
     * 65/256 at level 1 and 513/256 at level 99, interpolated linearly, so the chance is certain
     * from level 43.
     */
    fun lightChance(level: Int): Double {
        val clamped = level.coerceIn(1, 99)
        val rate = (LOW * (99 - clamped) + HIGH * (clamped - 1)) / 98.0
        return ((1 + floor(rate + 0.5)) / 256.0).coerceIn(0.0, 1.0)
    }

    private const val LOW = 64
    private const val HIGH = 512
}
