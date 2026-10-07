package org.rsmod.content.skills.firemaking.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.firemakingLvl
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.firemaking.configs.FiremakingLocs
import org.rsmod.content.skills.firemaking.configs.FiremakingLog
import org.rsmod.content.skills.firemaking.configs.FiremakingLogs
import org.rsmod.content.skills.firemaking.configs.FiremakingObjs
import org.rsmod.content.skills.firemaking.configs.FiremakingQueues
import org.rsmod.content.skills.firemaking.configs.FiremakingSeqs
import org.rsmod.events.UnboundEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.loc.LocLayerConstants

// TODO:
// - bonfires, Forester's campfires, Wintertodt, and pyre logs
// - adding logs to a fire, and lighting logs that lie on the ground
// - bow firemaking, gnomish firelighters, and the firemaking skillcape emote
// - lighting logs inside the places where the real game refuses it (bank booths, doorways, etc.)
//
// The fire is placed on the tile the player stands on and the player then steps away: west if
// possible, then east, south, and north. Cooking finds the fire by its name, so there is nothing to
// connect here.
//
// Attempts are timed with a weak queue rather than a delay, so the player is never held in place:
// walking away or clicking something else clears the queue and stops lighting.
class Firemaking
@Inject
constructor(
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (log in FiremakingLogs.all) {
            onOpHeldU(objs.tinderbox, log.obj) { light(log) }
        }
        onPlayerQueueWithArgs<LightJob>(FiremakingQueues.attempt) { attempt(it.args) }
        onPlayerQueueWithArgs<CoordGrid>(FiremakingQueues.face_fire) { faceSquare(it.args) }
    }

    private fun ProtectedAccess.light(log: FiremakingLog) {
        if (!canLight(log)) {
            return
        }
        mes("You attempt to light the logs.")
        schedule(LightJob(log))
    }

    /** Starts the animation and queues the attempt that is made once it has played. */
    private fun ProtectedAccess.schedule(job: LightJob) {
        anim(FiremakingSeqs.light_fire)
        weakQueue(FiremakingQueues.attempt, ATTEMPT_TICKS, job)
    }

    /** Makes one lighting attempt, and queues another if it failed. */
    private fun ProtectedAccess.attempt(job: LightJob) {
        val log = job.log
        if (log.obj !in inv || !canLight(log)) {
            resetAnim()
            return
        }

        val chance = FiremakingLogs.lightChance(player.firemakingLvl)
        val roll = LightRoll(player, log.obj, random.randomDouble() < chance)
        publish(roll)
        if (!roll.success) {
            schedule(job)
            return
        }

        if (!invDel(inv, log.obj, 1).success) {
            resetAnim()
            return
        }

        val fireCoords = coords
        val duration = random.of(FIRE_MIN_TICKS, FIRE_MAX_TICKS)
        locRepo.add(
            fireCoords,
            FiremakingLocs.fire,
            duration,
            LocAngle.West,
            LocShape.CentrepieceStraight,
        )
        val ashes = Obj.fromServer(clock, fireCoords, FiremakingObjs.ashes, 1)
        objRepo.addDelayed(ashes, spawnDelay = duration, duration = ASHES_TICKS)

        mes("The fire catches and the logs begin to burn.")
        statAdvance(stats.firemaking, log.xp * xpMods.get(player, stats.firemaking))
        publish(LogLit(player, log.obj, fireCoords))

        val step = stepAway(fireCoords)
        resetAnim()
        if (step != null) {
            walk(step)
            // The player faces the fire once they have stepped away from it.
            weakQueue(FiremakingQueues.face_fire, FACE_TICKS, fireCoords)
        } else {
            faceSquare(fireCoords)
        }
    }

    /** Checks level and tile. Sends a message for the first problem. */
    private fun ProtectedAccess.canLight(log: FiremakingLog): Boolean {
        if (player.firemakingLvl < log.level) {
            val name = objTypes[log.obj].name.lowercase()
            mes("You need a Firemaking level of ${log.level} to burn $name.")
            return false
        }
        val occupied =
            locRepo.findAll(coords).any {
                it.layer == LocLayerConstants.GROUND || it.layer == LocLayerConstants.GROUND_DECOR
            }
        if (occupied) {
            mes("You can't light a fire here.")
            return false
        }
        return true
    }

    /** The tile the player steps to after lighting a fire: west, then east, south, and north. */
    private fun stepAway(from: CoordGrid): CoordGrid? =
        listOf(from.translateX(-1), from.translateX(1), from.translateZ(-1), from.translateZ(1))
            .firstOrNull { !collision.isWalkBlocked(it) }

    /** A lighting job in progress: the player keeps trying to light [log] until it catches. */
    private class LightJob(val log: FiremakingLog)

    /**
     * Published on every lighting attempt, after the normal roll. Listeners can change whether the
     * attempt is a [success], for example to make lighting never fail.
     */
    class LightRoll(val player: Player, val log: ObjType, var success: Boolean) : UnboundEvent

    /** Published once a fire has been lit at [coords]. */
    class LogLit(val player: Player, val log: ObjType, val coords: CoordGrid) : UnboundEvent

    companion object {
        /** The Firemaking experience for burning [log], or `null` if it cannot be burnt. */
        fun logXp(log: ObjType): Double? =
            FiremakingLogs.all.firstOrNull { it.obj.id == log.id }?.xp

        /** Ticks between each lighting attempt. */
        private const val ATTEMPT_TICKS = 3

        /** Ticks after stepping away from a new fire before the player turns to face it. */
        private const val FACE_TICKS = 2

        /** A fire lasts 60 to 119 seconds, whatever the log. */
        private const val FIRE_MIN_TICKS = 100
        private const val FIRE_MAX_TICKS = 198

        /** How long the ashes stay on the ground once the fire has burnt out. */
        private const val ASHES_TICKS = 200
    }
}
