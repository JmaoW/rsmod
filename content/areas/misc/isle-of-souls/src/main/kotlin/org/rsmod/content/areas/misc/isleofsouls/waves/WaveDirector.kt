package org.rsmod.content.areas.misc.isleofsouls.waves

import java.util.IdentityHashMap
import kotlin.math.abs
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Runs wave-based fights inside team instances: one [WaveRun] per live instance. A run advances
 * when any player inside it ticks (see [tick]), so everything here happens on the game thread.
 *
 * The flow: a player enters (creating the run if the team has none), each wave spawns a short pause
 * after the last one was cleared, the monsters hunt the players inside, and killing the boss ends
 * it. A run ends when nobody is left inside (see [sweep]).
 *
 * A game (the Fight Cave, the Inferno) extends this with its own wave spawning, monster aiming and
 * win handling.
 */
abstract class WaveDirector<R : WaveRun>(
    protected val instances: TeamInstances,
    protected val npcRepo: NpcRepository,
    protected val npcTypes: NpcTypeList,
    protected val interactions: AiPlayerInteractions,
    protected val collision: CollisionFlagMap,
    protected val mapClock: MapClock,
) {
    protected val runs = IdentityHashMap<TeamInstance, R>()

    /** The kind of instance this game's runs live in. */
    protected abstract val instanceType: TeamInstanceType

    /** The colour of the "Wave: N" message. */
    protected open val waveColour: String = WAVE_COLOUR

    /** What players are told when a wave begins, or `null` to tell them nothing. */
    protected open fun waveMessage(run: R): String? =
        if (run.isLastWave) "<col=$waveColour>Final Challenge!"
        else "<col=$waveColour>Wave: ${run.waveNumber}"

    /** Makes the run for a team's freshly created [instance]. */
    protected abstract fun createRun(instance: TeamInstance): R

    /** Called for each player that enters: start their run timer and any welcome. */
    protected abstract fun startPlayer(player: Player)

    /** Spawns the monsters of the wave [run] has just moved on to (`run.waveNumber`). */
    protected abstract fun spawnWave(run: R, inside: List<Player>)

    /** Called every tick while a wave is on the floor, after the monsters were aimed. */
    protected open fun onWaveTick(run: R) {}

    /** Aims the run's own extras (healers and the like) at [targets]. */
    protected open fun retargetExtras(run: R, targets: List<Player>) {}

    /**
     * Points [npc] at [target]. Returns `false` if the npc is not one of this game's monsters, in
     * which case it is left alone.
     */
    protected abstract fun engage(run: R, npc: Npc, target: Player): Boolean

    /** The run is over (nobody is left in it): undo whatever it set up besides its monsters. */
    protected open fun onRunEnded(run: R) {}

    /** The boss died: reward everyone in [inside]. The run is already marked won. */
    protected abstract fun onWin(run: R, inside: List<Player>)

    /** The run [player] is in, if they stand inside one of this game's instances. */
    fun runOf(player: Player): R? = instances.instanceOf(player)?.let { runs[it] }

    /** The run a monster belongs to. */
    fun runOf(npc: Npc): R? =
        runs.values.firstOrNull { it.instance.registered && it.instance.contains(npc.coords) }

    /** Every run that is going on right now. */
    fun liveRuns(): List<R> = runs.values.toList()

    /**
     * Puts [player] into their team's instance, starting a run if the team has none. The result
     * says what happened so the caller can tell the player.
     */
    fun enter(player: Player): EnterResult {
        sweep()
        val result = instances.enter(player, instanceType)
        if (result != EnterResult.Entered) {
            return result
        }

        val instance = checkNotNull(instances.instanceOf(player))
        runs.getOrPut(instance) { createRun(instance) }
        startPlayer(player)
        return result
    }

    /** Sends [player] out of the instance. */
    fun leave(player: Player): Boolean {
        val left = instances.leave(player)
        sweep(except = player)
        return left
    }

    /** Called each tick for a player with the run timer. Returns `false` to stop their timer. */
    fun tick(player: Player): Boolean {
        sweep()
        val instance = instances.instanceOf(player) ?: return false
        val run = runs[instance] ?: return false
        if (run.lastTick != mapClock.cycle) {
            run.lastTick = mapClock.cycle
            advance(run)
        }
        return true
    }

    /**
     * Ends every run that has nobody inside, and removes the monsters it left behind. [except] is a
     * player who is leaving and should not count (they are still online during their logout).
     */
    fun sweep(except: Player? = null) {
        val iterator = runs.entries.iterator()
        while (iterator.hasNext()) {
            val (instance, run) = iterator.next()
            val live = instance.registered && instances.playersIn(instance).any { it !== except }
            if (!live) {
                onRunEnded(run)
                clearMonsters(run)
                iterator.remove()
            }
        }
    }

    private fun advance(run: R) {
        val inside = instances.playersIn(run.instance)
        val now = mapClock.cycle
        when (run.state) {
            WaveState.Won -> {
                if (now >= run.leaveAt) {
                    inside.forEach { instances.leave(it) }
                    sweep()
                }
            }
            WaveState.WaitingForNextWave -> {
                if (now >= run.nextWaveAt) {
                    startWave(run, inside)
                }
                retarget(run, inside)
            }
            WaveState.InWave -> {
                if (run.monsters.isEmpty() && !run.isLastWave) {
                    run.state = WaveState.WaitingForNextWave
                    run.nextWaveAt = now + WaveRun.WAVE_PAUSE
                }
                retarget(run, inside)
                onWaveTick(run)
            }
        }
    }

    private fun startWave(run: R, inside: List<Player>) {
        run.waveNumber++
        run.state = WaveState.InWave
        waveMessage(run)?.let { message -> inside.forEach { it.mes(message) } }
        spawnWave(run, inside)
    }

    /**
     * Spawns an npc of [type] with its south-west tile at [origin], a normal map coordinate that is
     * moved into the run's copy of the map. The caller keeps track of the npc.
     */
    fun spawn(run: WaveRun, type: NpcType, origin: CoordGrid): Npc {
        val coords = run.instance.region.normal[origin]
        val npc = Npc(npcTypes[type], coords)
        npc.mode = NpcMode.None
        npcRepo.add(npc, LIFETIME)
        return npc
    }

    /**
     * The first tile at or near [wanted] where a monster of [size] fits: clear of walls and of the
     * other monsters spawned this wave. Falls back to [wanted] itself.
     */
    protected fun freeSpot(
        run: WaveRun,
        wanted: CoordGrid,
        size: Int,
        taken: List<Pair<CoordGrid, Int>>,
    ): CoordGrid {
        for (radius in 0..MAX_SPAWN_RADIUS) {
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    if (maxOf(abs(dx), abs(dz)) != radius) {
                        continue
                    }
                    val candidate = CoordGrid(wanted.x + dx, wanted.z + dz, wanted.level)
                    if (
                        fits(run, candidate, size) && taken.none { overlaps(it, candidate, size) }
                    ) {
                        return candidate
                    }
                }
            }
        }
        return wanted
    }

    private fun fits(run: WaveRun, origin: CoordGrid, size: Int): Boolean =
        fitsAt(run.instance.region.normal[origin], size)

    /**
     * Whether a monster of [size] with its south-west tile on [real] (a coordinate inside the
     * instance) stands clear of walls and of other monsters.
     */
    protected fun fitsAt(real: CoordGrid, size: Int): Boolean {
        for (z in 0 until size) {
            for (x in 0 until size) {
                if ((collision[real.x + x, real.z + z, real.level] and BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    private fun overlaps(taken: Pair<CoordGrid, Int>, origin: CoordGrid, size: Int): Boolean {
        val (other, otherSize) = taken
        return origin.x < other.x + otherSize &&
            other.x < origin.x + size &&
            origin.z < other.z + otherSize &&
            other.z < origin.z + size
    }

    /** Keeps every monster aimed at a player inside the instance: the nearest one. */
    private fun retarget(run: R, inside: List<Player>) {
        val targets = inside.filter { it.isValidTarget() }
        if (targets.isEmpty()) {
            return
        }
        for (npc in run.monsters) {
            retarget(run, npc, targets)
        }
        retargetExtras(run, targets)
    }

    /** Aims [npc] at the nearest of [targets], keeping its current target while it is valid. */
    protected fun retarget(run: R, npc: Npc, targets: List<Player>) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
            return
        }
        val current = (npc.interaction as? InteractionPlayerOp)?.target
        val target =
            if (current != null && current in targets && npc.isHunting()) {
                current
            } else {
                val nearest = targets.minBy { it.distanceTo(npc) }
                if (!engage(run, npc, nearest)) {
                    return
                }
                nearest
            }
        // The engine drops a chase once the target is further than the npc's `maxRange` from
        // where it spawned. The monsters have to cross the whole map, so the leash is moved along
        // with the target.
        npc.spawnCoords = target.coords
    }

    private fun Npc.isHunting(): Boolean = mode == NpcMode.OpPlayer2 || mode == NpcMode.ApPlayer2

    /**
     * Points [npc] at [target]: a melee monster (`ranged = false`) walks up and fights from next to
     * them, one with a ranged attack stops once it is within its attack range, which is the
     * `attackRange` set on its type.
     */
    protected fun engage(npc: Npc, target: Player, ranged: Boolean) {
        if (!ranged) {
            npc.opPlayer2(target, interactions)
        } else {
            npc.apPlayer2(target, interactions)
        }
    }

    /** The boss is dead: the run is won and everyone inside gets their reward. */
    protected fun win(run: R) {
        run.state = WaveState.Won
        run.leaveAt = mapClock.cycle + WaveRun.LEAVE_DELAY
        clearMonsters(run)
        onWin(run, instances.playersIn(run.instance))
    }

    protected fun clearMonsters(run: WaveRun) {
        for (npc in run.allNpcs()) {
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        run.clearNpcs()
    }

    companion object {
        /** The red the real games write their wave messages in. */
        const val WAVE_COLOUR = "ef1020"

        /** A monster never expires on its own; the run removes them. About 16 hours. */
        const val LIFETIME = 100_000

        private const val MAX_SPAWN_RADIUS = 6

        private const val BLOCKED =
            CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.BLOCK_NPCS
    }
}
