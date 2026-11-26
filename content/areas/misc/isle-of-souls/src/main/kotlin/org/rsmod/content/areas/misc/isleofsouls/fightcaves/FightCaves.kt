package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

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
 * Runs the Fight Cave: one [FightCaveRun] per live cave instance. The wave loop (enter, leave,
 * timing, aiming, clean-up) is [WaveDirector]; this adds what is particular to the cave: where each
 * wave spawns, the Tz-Kek split, Jad's healers and the cave's rewards.
 */
class FightCaves
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    private val players: PlayerList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    private val random: GameRandom,
    mapClock: MapClock,
    private val eventBus: EventBus,
    private val rewards: FightCaveRewards,
) : WaveDirector<FightCaveRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    /** Set when the server starts, from `event-settings.json`. */
    var settings: FightCaveSettings = FightCaveSettings()

    /** Where a run gets its waves. Tests replace it so they do not depend on the event's file. */
    var waveLoader: (FightCaveMode) -> FightCaveWaves = FightCaveWaves::load

    override val instanceType: TeamInstanceType
        get() = FightCaveInstance.type

    override fun createRun(instance: TeamInstance): FightCaveRun {
        val mode = settings.mode
        return FightCaveRun(
            instance = instance,
            mode = mode,
            waves = waveLoader(mode),
            rotation = random.of(FightCaveSpawnPlanner.CYCLE.size),
            startClock = mapClock.cycle,
        )
    }

    override fun startPlayer(player: Player) {
        player.softTimer(cave_timers.tick, 1)
        player.strongQueue(cave_queues.enter, 1)
    }

    override fun spawnWave(run: FightCaveRun, inside: List<Player>) {
        val wave = run.waves[run.waveNumber]
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

    override fun onWaveTick(run: FightCaveRun) {
        val jad = run.jad
        if (jad != null && jad.isSlotAssigned && jad.hitpoints < JAD_HEALER_THRESHOLD) {
            summonHealers(run, jad)
        }
        healJad(run)
    }

    override fun retargetExtras(run: FightCaveRun, targets: List<Player>) {
        val jad = run.jad
        for (healer in run.healers) {
            if (healer.hitpoints < healer.baseHitpointsLvl) {
                retarget(run, healer, targets)
            } else if (jad != null && jad.isSlotAssigned) {
                follow(healer, jad)
            }
        }
    }

    override fun engage(run: FightCaveRun, npc: Npc, target: Player): Boolean {
        val def = FightCaveMonsterDefs.of(npc.type) ?: return false
        engage(npc, target, ranged = def.apRange != null)
        return true
    }

    override fun onWin(run: FightCaveRun, inside: List<Player>) {
        rewards.win(inside, run)
    }

    /** Spawns [def] with its south-west tile at [origin]. The caller keeps track of the npc. */
    fun spawn(run: FightCaveRun, def: FightCaveMonsterDef, origin: CoordGrid): Npc =
        spawn(run, def.type, origin)

    private fun follow(healer: Npc, jad: Npc) {
        if (healer.distanceTo(jad) > HEALER_FOLLOW_DISTANCE) {
            healer.walk(jad.coords)
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
            jad.healHitpoints(HEAL_AMOUNT)
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
        /** Jad's healers appear when he is hurt below this many hitpoints (from the wiki). */
        const val JAD_HEALER_THRESHOLD = 150

        private const val LIFETIME = WaveDirector.LIFETIME
        private const val HEAL_AMOUNT = 5
        private const val HEAL_INTERVAL = 4
        private const val HEAL_DISTANCE = 5
        private const val HEALER_FOLLOW_DISTANCE = 3
    }
}
