package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import jakarta.inject.Inject
import java.util.IdentityHashMap
import kotlin.math.abs
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.areas.misc.isleofsouls.instances.EnterResult
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** The cave as a kind of team instance. Registered with [TeamInstances] by `FightCaveScript`. */
object FightCaveInstance {
    const val NAME = "fightcave"

    val type =
        TeamInstanceType(
            name = NAME,
            template = FightCaveArena.template,
            entry = FightCaveArena.start,
            exit = FightCaveArena.exit,
        )
}

/**
 * Runs the Fight Cave: one [FightCaveRun] per live cave instance. A run advances when any player
 * inside it ticks (see [tick]), so everything here happens on the game thread.
 *
 * The flow: a player enters (creating the run if the team has none), each wave spawns a short pause
 * after the last one was cleared, the monsters hunt the players inside, and killing Jad ends it. A
 * run ends when nobody is left inside (see [sweep]).
 */
class FightCaves
@Inject
constructor(
    private val instances: TeamInstances,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val players: PlayerList,
    private val interactions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val mapClock: MapClock,
    private val eventBus: EventBus,
    private val rewards: FightCaveRewards,
) {
    /** Set when the server starts, from `event-settings.json`. */
    var settings: FightCaveSettings = FightCaveSettings()

    private val runs = IdentityHashMap<TeamInstance, FightCaveRun>()

    /** The run [player] is in, if they stand inside a cave. */
    fun runOf(player: Player): FightCaveRun? = instances.instanceOf(player)?.let { runs[it] }

    /** The run a cave monster belongs to. */
    fun runOf(npc: Npc): FightCaveRun? =
        runs.values.firstOrNull { it.instance.registered && it.instance.contains(npc.coords) }

    /** Every run that is going on right now. */
    fun liveRuns(): List<FightCaveRun> = runs.values.toList()

    /**
     * Puts [player] into their team's cave, starting a run if the team has none. The result says
     * what happened so the caller can tell the player.
     */
    fun enter(player: Player): EnterResult {
        sweep()
        val result = instances.enter(player, FightCaveInstance.type)
        if (result != EnterResult.Entered) {
            return result
        }

        val instance = checkNotNull(instances.instanceOf(player))
        val run =
            runs.getOrPut(instance) {
                val mode = settings.mode
                FightCaveRun(
                    instance = instance,
                    mode = mode,
                    waves = FightCaveWaves.load(mode),
                    rotation = random.of(FightCaveSpawnPlanner.CYCLE.size),
                    startClock = mapClock.cycle,
                )
            }
        player.softTimer(cave_timers.tick, 1)
        player.strongQueue(cave_queues.enter, 1)
        return result
    }

    /** Sends [player] out of the cave. */
    fun leave(player: Player): Boolean {
        val left = instances.leave(player)
        sweep(except = player)
        return left
    }

    /** Called each tick for a player with the cave timer. Returns `false` to stop their timer. */
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
                clearMonsters(run)
                iterator.remove()
            }
        }
    }

    private fun advance(run: FightCaveRun) {
        val inside = instances.playersIn(run.instance)
        val now = mapClock.cycle
        when (run.state) {
            FightCaveState.Won -> {
                if (now >= run.leaveAt) {
                    inside.forEach { instances.leave(it) }
                    sweep()
                }
            }
            FightCaveState.WaitingForNextWave -> {
                if (now >= run.nextWaveAt) {
                    spawnWave(run, inside)
                }
                retarget(run, inside)
            }
            FightCaveState.InWave -> {
                if (run.monsters.isEmpty() && !run.isLastWave) {
                    run.state = FightCaveState.WaitingForNextWave
                    run.nextWaveAt = now + FightCaveRun.WAVE_PAUSE
                }
                retarget(run, inside)
                val jad = run.jad
                if (jad != null && jad.isSlotAssigned && jad.hitpoints < JAD_HEALER_THRESHOLD) {
                    summonHealers(run, jad)
                }
                healJad(run)
            }
        }
    }

    private fun spawnWave(run: FightCaveRun, inside: List<Player>) {
        run.waveNumber++
        run.state = FightCaveState.InWave
        val wave = run.waves[run.waveNumber]
        val message =
            if (run.isLastWave) "<col=$WAVE_COLOUR>Final Challenge!"
            else "<col=$WAVE_COLOUR>Wave: ${run.waveNumber}"
        inside.forEach { it.mes(message) }

        val taken = ArrayList<Pair<CoordGrid, Int>>()
        for ((monster, point) in run.planner.plan(wave)) {
            val def = FightCaveMonsterDefs.of(monster)
            val origin = freeSpot(run, FightCaveArena.points.getValue(point), def.size, taken)
            taken += origin to def.size
            val npc = spawn(run, def, origin)
            run.monsters += npc
            if (monster == FightCaveMonster.Jad) {
                run.jad = npc
            }
        }
    }

    /**
     * Spawns [def] with its south-west tile at [origin], a normal map coordinate that is moved into
     * the run's copy of the cave. The caller keeps track of the npc.
     */
    fun spawn(run: FightCaveRun, def: FightCaveMonsterDef, origin: CoordGrid): Npc {
        val coords = run.instance.region.normal[origin]
        val npc = Npc(npcTypes[def.type], coords)
        npc.mode = NpcMode.None
        npcRepo.add(npc, LIFETIME)
        return npc
    }

    /**
     * The first tile at or near [wanted] where a monster of [size] fits: clear of walls and of the
     * other monsters spawned this wave. Falls back to [wanted] itself.
     */
    private fun freeSpot(
        run: FightCaveRun,
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

    private fun fits(run: FightCaveRun, origin: CoordGrid, size: Int): Boolean {
        val real = run.instance.region.normal[origin]
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

    /** Keeps every monster aimed at a player inside the cave: the nearest one. */
    private fun retarget(run: FightCaveRun, inside: List<Player>) {
        val targets = inside.filter { it.isValidTarget() }
        if (targets.isEmpty()) {
            return
        }
        for (npc in run.monsters) {
            retarget(npc, targets)
        }
        val jad = run.jad
        for (healer in run.healers) {
            if (healer.hitpoints < healer.baseHitpointsLvl) {
                retarget(healer, targets)
            } else if (jad != null && jad.isSlotAssigned) {
                follow(healer, jad)
            }
        }
    }

    private fun retarget(npc: Npc, targets: List<Player>) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
            return
        }
        val current = (npc.interaction as? InteractionPlayerOp)?.target
        val target =
            if (current != null && current in targets && npc.isHunting()) {
                current
            } else {
                val def = FightCaveMonsterDefs.of(npc.type) ?: return
                targets.minBy { it.distanceTo(npc) }.also { engage(npc, it, def) }
            }
        // The engine drops a chase once the target is further than the npc's `maxRange` from
        // where it spawned. The cave's monsters have to cross the whole cave, so the leash is
        // moved along with the target.
        npc.spawnCoords = target.coords
    }

    private fun Npc.isHunting(): Boolean = mode == NpcMode.OpPlayer2 || mode == NpcMode.ApPlayer2

    private fun follow(healer: Npc, jad: Npc) {
        if (healer.distanceTo(jad) > HEALER_FOLLOW_DISTANCE) {
            healer.walk(jad.coords)
        }
    }

    /**
     * Points [npc] at [target]: a melee monster walks up and fights from next to them, one with a
     * ranged attack stops once it is within its attack range.
     */
    fun engage(npc: Npc, target: Player, def: FightCaveMonsterDef) {
        if (def.apRange == null) {
            npc.opPlayer2(target, interactions)
        } else {
            // How far it attacks from is the `attackRange` set on its type (see
            // `FightCaveNpcEdits`).
            npc.apPlayer2(target, interactions)
        }
    }

    /** A monster of its run died (its death animation is about to play). */
    fun monsterDied(npc: Npc) {
        val run = runOf(npc) ?: return
        run.monsters.remove(npc)
        run.healers.remove(npc)
        eventBus.publish(NpcDeathEvents.Killed(npc, npc.findHero(players)))
        val def = FightCaveMonsterDefs.of(npc.type) ?: return
        when {
            def === FightCaveMonsterDefs.tzKek -> splitKek(run, npc)
            def.kind == FightCaveMonster.Jad -> win(run)
        }
    }

    /** A Tz-Kek leaves two small ones behind when it dies. */
    private fun splitKek(run: FightCaveRun, npc: Npc) {
        for (dx in 0..1) {
            val tile = CoordGrid(npc.coords.x + dx, npc.coords.z, npc.coords.level)
            val spawned = Npc(npcTypes[cave_npcs.tz_kek_spawn], tile)
            spawned.mode = NpcMode.None
            npcRepo.add(spawned, LIFETIME)
            run.monsters += spawned
        }
    }

    /** Jad is dead: the run is won and everyone inside gets their reward. */
    private fun win(run: FightCaveRun) {
        run.state = FightCaveState.Won
        run.leaveAt = mapClock.cycle + FightCaveRun.LEAVE_DELAY
        clearMonsters(run)
        rewards.win(instances.playersIn(run.instance), run)
    }

    private fun clearMonsters(run: FightCaveRun) {
        for (npc in (run.monsters + run.healers).toList()) {
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        run.monsters.clear()
        run.healers.clear()
    }

    /** Each healer that is not fighting heals Jad a little, every few ticks, while it is close. */
    private fun healJad(run: FightCaveRun) {
        val jad = run.jad ?: return
        if (!jad.isSlotAssigned || jad.hitpoints <= 0) {
            return
        }
        for (healer in run.healers) {
            if (healer.hitpoints < healer.baseHitpointsLvl || healer.actionDelay > mapClock.cycle) {
                continue
            }
            if (jad.hitpoints >= jad.baseHitpointsLvl || healer.distanceTo(jad) > HEAL_DISTANCE) {
                continue
            }
            healer.actionDelay = mapClock.cycle + HEAL_INTERVAL
            healer.anim(cave_seqs.yt_mejkot_heal)
            jad.spotanim(cave_spotanims.heal)
            jad.hitpoints = minOf(jad.baseHitpointsLvl, jad.hitpoints + HEAL_AMOUNT)
        }
    }

    /** Four healers appear around [jad], once, when he is hurt enough. */
    private fun summonHealers(run: FightCaveRun, jad: Npc) {
        if (run.healersSummoned) {
            return
        }
        run.healersSummoned = true
        val def = FightCaveMonsterDefs.ytHurKot
        for ((dx, dz) in FightCaveArena.healerOffsets) {
            val tile = CoordGrid(jad.coords.x + dx, jad.coords.z + dz, jad.coords.level)
            val healer = Npc(npcTypes[def.type], tile)
            healer.mode = NpcMode.None
            npcRepo.add(healer, LIFETIME)
            run.healers += healer
        }
    }

    companion object {
        /** The red the real cave writes its wave messages in. */
        const val WAVE_COLOUR = "ef1020"

        /** A cave monster never expires on its own; the run removes them. About 16 hours. */
        const val LIFETIME = 100_000

        /** Jad's healers appear when he is hurt below this many hitpoints (from the wiki). */
        const val JAD_HEALER_THRESHOLD = 150

        private const val HEAL_AMOUNT = 5
        private const val HEAL_INTERVAL = 4
        private const val HEAL_DISTANCE = 5
        private const val MAX_SPAWN_RADIUS = 6
        private const val HEALER_FOLLOW_DISTANCE = 3

        private const val BLOCKED =
            CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.BLOCK_NPCS
    }
}
