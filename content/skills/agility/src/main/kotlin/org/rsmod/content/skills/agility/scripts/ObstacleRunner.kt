package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import kotlin.math.sign
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.agility.configs.Obstacle
import org.rsmod.content.skills.agility.configs.Step
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.game.type.bas.UnpackedBasType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.seq.UnpackedSeqType
import org.rsmod.map.CoordGrid

/**
 * Runs the [Step]s of an obstacle, one tile at a time with the animations the cache has for them.
 * The courses and shortcuts and the arena all cross their gaps this way: the steps move the player
 * by exact moves, so what is in the way (a pit, an invisible wall) does not matter.
 */
class ObstacleRunner @Inject constructor(private val seqTypes: SeqTypeList) {
    private val seqByName: Map<String, UnpackedSeqType> by lazy {
        seqTypes.types.values.filter { it.internalName != null }.associateBy { it.internalName!! }
    }

    /** Runs the obstacle's steps with the walk animation swapped in when it asks for one. */
    suspend fun ProtectedAccess.perform(obstacle: Obstacle) {
        obstacle.route?.let { goTo(it) }
        obstacle.start?.let { mes(it) }
        var renderSeq: UnpackedSeqType? = null
        if (obstacle.render != null) {
            renderSeq = setRender(obstacle.render)
        }
        try {
            for (step in obstacle.steps) {
                if (step is Step.Render) {
                    renderSeq = setRender(step.seq)
                    continue
                }
                run(step, renderSeq)
            }
        } finally {
            player.bas = null
        }
    }

    suspend fun ProtectedAccess.run(step: Step, renderSeq: UnpackedSeqType?) {
        when (step) {
            is Step.Anim -> if (step.seq.isEmpty()) resetAnim() else anim(seq(step.seq), step.delay)
            is Step.Wait -> if (step.ticks > 0) delay(step.ticks)
            is Step.Tele -> telejump(step.tile.coords())
            is Step.Face -> faceSquare(step.tile.coords())
            is Step.Msg -> mes(step.text)
            is Step.Render -> setRender(step.seq)
            is Step.Move -> {
                step.anim?.let { anim(seq(it)) }
                val from = coords
                val to = step.tile.coords()
                exactMove(from, to, step.begin, step.travel, step.dir)
                if (step.ticks > 0) delay(step.ticks)
            }
            is Step.Walk -> {
                for (target in step.path) {
                    walkTo(target.coords(), renderSeq)
                }
            }
        }
    }

    /** One tile a tick towards [dest], ignoring what is in the way, as a rope or a ledge does. */
    suspend fun ProtectedAccess.walkTo(dest: CoordGrid, renderSeq: UnpackedSeqType?) {
        while (coords.x != dest.x || coords.z != dest.z) {
            val dx = sign((dest.x - coords.x).toDouble()).toInt()
            val dz = sign((dest.z - coords.z).toDouble()).toInt()
            val next = CoordGrid(coords.x + dx, coords.z + dz, coords.level)
            renderSeq?.let { anim(it) }
            exactMove(coords, next, 0, WALK_CYCLES, exactDirection(dx, dz))
            delay(1)
        }
    }

    /**
     * Takes the player to where an obstacle starts. When [teleport] is set and there is no way
     * there on foot, the player is put there; otherwise `false` comes back and they stay.
     */
    suspend fun ProtectedAccess.goTo(route: Tile, teleport: Boolean = true): Boolean {
        val dest = route.coords()
        if (coords == dest) {
            return true
        }
        playerWalkWithMinDelay(dest)
        // The delay above is only an estimate of the walk, and falls short from a few tiles
        // away. Keep waiting while the player is still on their way.
        var waited = 0
        while (coords != dest && isWalking() && waited < MAX_WALK_TICKS) {
            delay(1)
            waited++
        }
        if (coords == dest) {
            return true
        }
        if (!teleport) {
            return false
        }
        telejump(dest)
        delay(1)
        return true
    }

    /** Swaps the walk, run and stand animations for [name]'s. Passing `null` puts them back. */
    fun ProtectedAccess.setRender(name: String?): UnpackedSeqType? {
        if (name == null) {
            player.bas = null
            return null
        }
        val walk = seq(name)
        val ready = seqByName["${name}_ready"] ?: walk
        player.bas =
            UnpackedBasType(
                readyAnim = ready,
                turnOnSpot = walk,
                walkForward = walk,
                walkBack = walk,
                walkLeft = walk,
                walkRight = walk,
                running = walk,
                internalId = null,
                internalName = null,
            )
        return walk
    }

    fun seq(name: String): UnpackedSeqType =
        seqByName[name] ?: error("Agility: no seq named $name.")

    private fun ProtectedAccess.isWalking(): Boolean =
        player.routeRequest != null || player.routeDestination.isNotEmpty()

    private fun Tile.coords(): CoordGrid = CoordGrid(x, z, level)

    private fun exactDirection(dx: Int, dz: Int): Int =
        when {
            dx > 0 && dz == 0 -> 1536
            dx < 0 && dz == 0 -> 512
            dz > 0 && dx == 0 -> 1024
            dz < 0 && dx == 0 -> 0
            dx > 0 && dz > 0 -> 1280
            dx > 0 -> 1792
            dz > 0 -> 768
            else -> 256
        }

    private companion object {
        const val WALK_CYCLES = 30

        /** The most ticks [goTo] waits on a walk that is still going after its estimate. */
        const val MAX_WALK_TICKS = 30
    }
}
