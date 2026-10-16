package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_seqs
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_spotanims
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Inferno's geometry, in ordinary map coordinates (the instance is a copy of this map square).
 *
 * The arena is one open rectangle of 29 by 30 tiles; everything around it is rock and lava. The
 * pillars, monsters and TzKal-Zuk are all placed by the script, none are in the map.
 */
object InfernoArena {
    /** The Inferno's map square (35_83) as a block of 8x8 zones, copied on every level. */
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 280, copyZoneZ = 664) {
                zoneWidth = 8
                zoneLength = 8
            }
        }

    /** The south-west and north-east floor tiles of the arena, both included. */
    val floorSouthWest = CoordGrid(2257, 5329, 0)
    val floorNorthEast = CoordGrid(2285, 5358, 0)

    /** Where players appear: the middle of the south edge, in front of the exit. */
    val start = CoordGrid(2271, 5329, 0)

    /**
     * Where players are sent when they leave: Mor Ul Rek, at the end of the corridor south of the
     * real Inferno entrance.
     */
    val exit = CoordGrid(2497, 5119, 0)

    /**
     * Where the nibblers appear: together, roughly midway between the three pillars (wiki). A guess
     * at the exact tile.
     */
    val nibblerSpawn = CoordGrid(2267, 5347, 0)

    /**
     * The places the other monsters of a wave appear, as south-west tiles of a footprint. Every one
     * has room for the biggest monster (size 4) and none is on a pillar. The wiki gives no spawn
     * positions, so these are a guess: spread round the north half, away from where players start.
     */
    val spawnZones: List<CoordGrid> =
        listOf(
            CoordGrid(2262, 5351, 0),
            CoordGrid(2269, 5351, 0),
            CoordGrid(2279, 5350, 0),
            CoordGrid(2260, 5344, 0),
            CoordGrid(2281, 5343, 0),
            CoordGrid(2271, 5343, 0),
        )

    /**
     * The south-west tiles of the three 3x3 pillars, read off the wiki's picture of the arena: one
     * against the west wall in the north (SW), one towards the north-east (N) and one left of the
     * middle (C). The rest of the arena is open. Positions are accurate to about a tile.
     */
    val pillarSouthWest: List<CoordGrid> =
        listOf(CoordGrid(2257, 5351, 0), CoordGrid(2274, 5353, 0), CoordGrid(2267, 5337, 0))

    /** Where a lone JalTok-Jad appears (south-west tile of his 5x5 footprint). */
    val jadSingle = CoordGrid(2268, 5348, 0)

    /**
     * Where the three Jads of the triple wave appear: "in a triangle around the centre" (wiki), one
     * to the north, one to the west and one to the east. The positions are a guess.
     */
    val jadTriangle: List<CoordGrid> =
        listOf(CoordGrid(2268, 5349, 0), CoordGrid(2261, 5339, 0), CoordGrid(2276, 5339, 0))

    /** The glyph's west end: its south-west tile on the lava row just south of Zuk. */
    val glyphWest = CoordGrid(2257, 5359, 0)

    /**
     * Zuk's south-west tile: the prison cell in the north wall that the map has (size 7). A guess
     * from where the map places the wall and its roof.
     */
    val zukSouthWest = CoordGrid(2268, 5363, 0)

    /**
     * Where the players are put when Zuk breaks out: the north edge of the arena, in front of him.
     */
    val zukStart = CoordGrid(2271, 5357, 0)

    /**
     * Where the monsters of the Zuk fight appear: behind the players, to the south of them. The
     * wiki gives no positions, so these are a guess.
     */
    val zukRanger = CoordGrid(2277, 5351, 0)
    val zukMager = CoordGrid(2262, 5351, 0)
    val zukJad = CoordGrid(2268, 5347, 0)

    /**
     * The pieces of Zuk's prison that the map has in its closed state, where they stand (the
     * south-west tile of each loc, read from the map file). See [InfernoPrison].
     */
    val prisonGlyph = CoordGrid(2270, 5363, 0)
    val prisonSideLeft = CoordGrid(2268, 5364, 0)
    val prisonSideRight = CoordGrid(2273, 5364, 0)
    val prisonCornerLeft = CoordGrid(2267, 5364, 0)
    val prisonCornerRight = CoordGrid(2275, 5364, 0)
    val prisonJoinRight = CoordGrid(2275, 5366, 0)
    val prisonJoinLeft = CoordGrid(2267, 5366, 0)

    /** The lava in front of Zuk, where the four Jal-MejJak stand. */
    val mejjakSpots: List<CoordGrid> =
        listOf(
            CoordGrid(2263, 5362, 0),
            CoordGrid(2266, 5362, 0),
            CoordGrid(2276, 5362, 0),
            CoordGrid(2279, 5362, 0),
        )

    /** Where a Jal-Zek's revived monster appears: "near the centre-east of the arena" (wiki). */
    val reviveSpot = CoordGrid(2277, 5345, 0)
}

/** The Inferno as a kind of team instance. Registered with [TeamInstances] by `InfernoScript`. */
object InfernoInstance {
    const val NAME = "inferno"

    val type =
        TeamInstanceType(
            name = NAME,
            template = InfernoArena.template,
            entry = InfernoArena.start,
            exit = InfernoArena.exit,
        )
}

/**
 * Decides where each monster of a wave appears: the nibblers together, the others in their own
 * [InfernoArena.spawnZones], the biggest first so they get a zone to themselves. The zones are
 * shuffled for every wave. [roll] gives a number from 0 until its argument.
 */
class InfernoSpawnPlanner(private val roll: (Int) -> Int) {
    /** Where each monster of [wave] that has a definition appears (south-west tiles). */
    fun plan(wave: InfernoWave): List<Pair<InfernoMonster, CoordGrid>> {
        val zones = InfernoArena.spawnZones.toMutableList()
        for (i in zones.lastIndex downTo 1) {
            val j = roll(i + 1)
            zones[i] = zones[j].also { zones[j] = zones[i] }
        }

        val result = ArrayList<Pair<InfernoMonster, CoordGrid>>()
        val jads = wave.monsters.count { it == InfernoMonster.Jad }
        for (index in 0 until jads) {
            result +=
                InfernoMonster.Jad to
                    if (jads == 1) InfernoArena.jadSingle
                    else InfernoArena.jadTriangle[index % InfernoArena.jadTriangle.size]
        }
        val others =
            wave.monsters
                .filter { it != InfernoMonster.Nibbler && it != InfernoMonster.Jad }
                .mapNotNull { monster ->
                    InfernoMonsterDefs.of(monster)?.let { monster to it.size }
                }
                .sortedByDescending { it.second }
        for ((index, entry) in others.withIndex()) {
            result += entry.first to zones[index % zones.size]
        }
        for (monster in wave.monsters) {
            if (monster == InfernoMonster.Nibbler) {
                result += monster to InfernoArena.nibblerSpawn
            }
        }
        return result
    }
}

/** What the director remembers about one living monster. */
class InfernoMonsterState(val spawnedAt: Int) {
    /** When a melee monster next burrows (it digs if it has not been fighting by then). */
    var burrowAt: Int = spawnedAt + Inferno.BURROW_FIRST

    /** While a melee monster is underground, when it comes up; `0` when it is not. */
    var digUntil: Int = 0

    /** A Jal-Zek brought this one back, so it cannot be revived again. */
    var revived: Boolean = false

    /** The blob's chosen style for its next attack (it picks it a few ticks ahead). */
    var blobStyle: NpcAttackStyle? = null

    /** Zuk's minions attack the glyph until a player hits them (see [InfernoZuk]). */
    var shieldAttacker: Boolean = false

    /** A player has hurt this monster. Once set it stays set, however much it heals after. */
    var provoked: Boolean = false

    private var lastHitpoints: Int = -1

    /**
     * Looks at [npc] to see whether it has lost health since the last look, and remembers it if so.
     * Call it every tick for a monster whose behaviour depends on being attacked. Returns
     * [provoked].
     */
    fun watch(npc: Npc): Boolean {
        if (lastHitpoints >= 0 && npc.hitpoints < lastHitpoints) {
            provoked = true
        }
        lastHitpoints = npc.hitpoints
        return provoked
    }

    /** The Jad this Yt-HurKot heals. */
    var owner: Npc? = null

    /** A Jad has summoned his healers (they come once). */
    var healersSummoned: Boolean = false

    /** How many healers this Jad summons. */
    var healerCount: Int = Inferno.HEALERS_SINGLE

    /** Ticks between this Jad attacks (slower in the triple wave). */
    var jadRate: Int = Inferno.JAD_RATE

    /** Where a nibbler was last told to walk, so it is not told again every tick. */
    var walkTarget: CoordGrid? = null
}

/** A monster that died this wave and may be revived by a Jal-Zek. */
class SlainMonster(val def: InfernoMonsterDef, var revived: Boolean = false)

/**
 * One team's attempt at the Inferno. It lives as long as its [instance] has someone in it; the mode
 * and wave list are fixed when it starts, so changing the setting never changes a run in progress.
 */
class InfernoRun(
    instance: TeamInstance,
    val mode: InfernoMode,
    val waves: InfernoWaves,
    startClock: Int,
) : WaveRun(instance, startClock) {
    val states = IdentityHashMap<Npc, InfernoMonsterState>()

    /** The pillars of the arena, standing or fallen. */
    val pillars = ArrayList<InfernoPillar>()

    /** The pillar every nibbler of the wave is eating, until it falls (they all pick the same). */
    var nibblerPillar: InfernoPillar? = null

    /** The monsters of this wave that died and can still be revived. */
    val slain = ArrayList<SlainMonster>()

    /** The Yt-HurKot healers, kept apart from the wave monsters. */
    val healers = ArrayList<Npc>()

    /** Zuk, his glyph and his Jal-MejJak: owned by the run but not a wave monster. */
    val extras = ArrayList<Npc>()

    /** The Zuk fight, once the last wave has begun. */
    var zukFight: ZukFight? = null

    override val waveCount: Int
        get() = waves.count

    override fun allNpcs(): List<Npc> = (monsters + healers + extras).toList()

    override fun clearNpcs() {
        super.clearNpcs()
        healers.clear()
        extras.clear()
        states.clear()
    }
}

/**
 * Runs the Inferno: one [InfernoRun] per live Inferno instance. The wave loop (enter, leave,
 * timing, aiming, clean-up) is [WaveDirector]; this adds what is particular to the Inferno: where
 * each wave spawns, the blob's split, the melee monster's burrow and the Jal-Zek's revive.
 */
class Inferno
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
    private val pillars: InfernoPillars,
    private val zuk: InfernoZuk,
    private val rewards: InfernoRewards,
) : WaveDirector<InfernoRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    /** Set when the server starts, from `event-settings.json`. */
    var settings: InfernoSettings = InfernoSettings()

    /** Where a run gets its waves. Tests replace it so they do not depend on the event's file. */
    var waveLoader: (InfernoMode) -> InfernoWaves = InfernoWaves::load

    override val instanceType: TeamInstanceType
        get() = InfernoInstance.type

    override fun createRun(instance: TeamInstance): InfernoRun {
        val mode = settings.mode
        val run =
            InfernoRun(
                instance = instance,
                mode = mode,
                waves = waveLoader(mode),
                startClock = mapClock.cycle,
            )
        run.pillars += pillars.place(instance)
        return run
    }

    override fun onRunEnded(run: InfernoRun) {
        pillars.remove(run)
    }

    override fun startPlayer(player: Player) {
        runOf(player)?.let {
            pillars.showTo(player, it)
            zuk.showTo(player, it)
        }
        // The arena is bigger than the 15 tiles a client is normally told about npcs within: Zuk
        // stands at the far end and must not vanish when a player is at the other.
        player.client.setNpcViewDistance(NPC_VIEW_DISTANCE)
        player.softTimer(inferno_timers.tick, 1)
        player.strongQueue(inferno_queues.enter, 1)
    }

    override fun spawnWave(run: InfernoRun, inside: List<Player>) {
        val wave = run.waves[run.waveNumber]
        run.slain.clear()
        run.nibblerPillar = null
        if (wave.number == run.waves.firstJadWave || InfernoMonster.Zuk in wave.monsters) {
            // The pillars do not last into the Jad waves (wiki), nor into the Zuk fight.
            pillars.collapseAll(run)
        }
        val taken = ArrayList<Pair<CoordGrid, Int>>()
        val jads = wave.monsters.count { it == InfernoMonster.Jad }
        var jadIndex = 0
        for ((monster, point) in InfernoSpawnPlanner(random::of).plan(wave)) {
            val def = InfernoMonsterDefs.of(monster) ?: continue
            val origin = freeSpot(run, point, def.size, taken)
            taken += origin to def.size
            val npc = spawn(run, def.type, origin)
            add(run, npc)
            if (InfernoMonsterDefs.isJad(def)) {
                setUpJad(run, npc, jadIndex++, jads)
            }
        }
        if (InfernoMonster.Zuk in wave.monsters) {
            zuk.start(this, run, inside)
        }
    }

    /**
     * Brings in one of Zuk's minions (a set, or his Jad) with its south-west tile near [wanted], a
     * normal map coordinate. With [shield] it attacks the glyph until a player draws it off.
     */
    fun spawnMinion(
        run: InfernoRun,
        def: InfernoMonsterDef,
        wanted: CoordGrid,
        shield: Boolean,
    ): Npc {
        val origin = freeSpot(run, wanted, def.size, emptyList())
        val npc = spawn(run, def.type, origin)
        add(run, npc)
        val state = run.states.getValue(npc)
        state.shieldAttacker = shield
        if (InfernoMonsterDefs.isJad(def)) {
            state.jadRate = JAD_RATE
            state.healerCount = HEALERS_MULTIPLE
        }
        // Nothing attacks until the glyph has been looked at (see [InfernoZuk]).
        npc.actionDelay = mapClock.cycle + SHIELD_FIRST_ATTACK
        return npc
    }

    /**
     * Sets up a Jad of a wave with [count] of them. A lone Jad attacks every 8 ticks and has five
     * healers. In the triple wave each attacks every 9 ticks and they start 3 ticks apart, so one
     * flick of prayer per style covers all three, and each has three healers (wiki).
     */
    private fun setUpJad(run: InfernoRun, jad: Npc, index: Int, count: Int) {
        val state = run.states.getValue(jad)
        val lone = count == 1
        state.jadRate = if (lone) JAD_RATE else STAGGERED_JAD_RATE
        state.healerCount = if (lone) HEALERS_SINGLE else HEALERS_MULTIPLE
        jad.actionDelay = mapClock.cycle + JAD_FIRST_ATTACK + JAD_STAGGER * index
    }

    /** Starts keeping track of [npc] as one of the run's monsters. */
    private fun add(run: InfernoRun, npc: Npc, revived: Boolean = false) {
        run.monsters += npc
        run.states[npc] =
            InfernoMonsterState(mapClock.cycle).also {
                it.revived = revived
                it.watch(npc)
            }
    }

    /** Whether the run [npc] is in still has a pillar standing. */
    fun pillarsStanding(npc: Npc): Boolean = runOf(npc)?.pillars?.any { it.alive } == true

    /** What the director remembers about [npc], if it is a living monster of a run. */
    fun stateOf(npc: Npc): InfernoMonsterState? = runOf(npc)?.states?.get(npc)

    override fun engage(run: InfernoRun, npc: Npc, target: Player): Boolean {
        val def = InfernoMonsterDefs.of(npc.type) ?: return false
        if ((run.states[npc]?.digUntil ?: 0) != 0) {
            // Underground: it comes up by itself (see [burrow]).
            return true
        }
        val state = run.states[npc]
        if (state != null && state.shieldAttacker && !state.provoked) {
            // Zuk's minions have eyes for the glyph until somebody hits them.
            return true
        }
        if (def === InfernoMonsterDefs.nibbler && run.pillars.any { it.alive }) {
            // Nibblers ignore the players while there is a pillar to eat (see [nibblers]).
            return true
        }
        engage(npc, target, ranged = def.apRange != null)
        return true
    }

    override fun onWaveTick(run: InfernoRun) {
        burrow(run)
        nibblers(run)
        jads(run)
        zuk.tick(this, run)
    }

    override fun retargetExtras(run: InfernoRun, targets: List<Player>) {
        for (healer in run.healers) {
            val owner = run.states[healer]?.owner
            if (healer.hitpoints < healer.baseHitpointsLvl) {
                retarget(run, healer, targets)
            } else if (owner != null && owner.isSlotAssigned && healer.distanceTo(owner) > 1) {
                healer.walk(owner.coords)
            }
        }
    }

    /** Summons a Jad healers when he is hurt enough, and lets the healers top him up. */
    private fun jads(run: InfernoRun) {
        for (npc in run.monsters.toList()) {
            val def = InfernoMonsterDefs.of(npc.type) ?: continue
            if (!InfernoMonsterDefs.isJad(def)) {
                continue
            }
            val state = run.states[npc] ?: continue
            if (!state.healersSummoned && npc.hitpoints < JAD_HEALER_THRESHOLD) {
                state.healersSummoned = true
                summonHealers(run, npc, state.healerCount)
            }
        }
        healJads(run)
    }

    /** Puts [count] healers round [jad], spread out, next to him. They come once. */
    private fun summonHealers(run: InfernoRun, jad: Npc, count: Int) {
        // The healers of Zuk's Jad always come up on his north side (wiki).
        val final = jad.type.id == inferno_npcs.jad_final.id
        val free = ringAround(jad.coords, jad.size).filter { fitsAt(it, 1) }
        // Zuk's Jad gets his on the north side; if he has been drawn up against the north wall
        // and there is no room there, they come up wherever there is room.
        val ring = free.filter { !final || it.z >= jad.coords.z + jad.size }.ifEmpty { free }
        if (ring.isEmpty()) {
            return
        }
        val def = if (final) InfernoMonsterDefs.healerFinal else InfernoMonsterDefs.healer
        val spots = (0 until count).map { ring[it * ring.size / count] }.distinct()
        for (tile in spots) {
            val healer = Npc(npcTypes[def.type], tile)
            healer.mode = NpcMode.None
            npcRepo.add(healer, LIFETIME)
            run.healers += healer
            run.states[healer] = InfernoMonsterState(mapClock.cycle).also { it.owner = jad }
        }
    }

    /** The tiles in a ring one tile wide round a footprint of [size] at [southWest]. */
    private fun ringAround(southWest: CoordGrid, size: Int): List<CoordGrid> {
        val x0 = southWest.x - 1
        val x1 = southWest.x + size
        val z0 = southWest.z - 1
        val z1 = southWest.z + size
        val level = southWest.level
        return buildList {
            for (x in x0..x1) add(CoordGrid(x, z0, level))
            for (z in z0 + 1..z1) add(CoordGrid(x1, z, level))
            for (x in x1 - 1 downTo x0) add(CoordGrid(x, z1, level))
            for (z in z1 - 1 downTo z0 + 1) add(CoordGrid(x0, z, level))
        }
    }

    /**
     * Each healer that has not been attacked heals its Jad for 15 to 24 every 4 ticks while it
     * stands next to him.
     */
    private fun healJads(run: InfernoRun) {
        val now = mapClock.cycle
        for (healer in run.healers) {
            val owner = run.states[healer]?.owner ?: continue
            if (!owner.isSlotAssigned || owner.hitpoints <= 0) {
                continue
            }
            if (healer.hitpoints < healer.baseHitpointsLvl || healer.actionDelay > now) {
                continue
            }
            if (owner.hitpoints >= owner.baseHitpointsLvl || healer.distanceTo(owner) > 1) {
                continue
            }
            healer.actionDelay = now + HEAL_INTERVAL
            healer.anim(cave_seqs.yt_mejkot_heal)
            owner.spotanim(cave_spotanims.heal)
            val heal = HEAL_MIN + random.of(HEAL_SPREAD)
            owner.hitpoints = minOf(owner.baseHitpointsLvl, owner.hitpoints + heal)
        }
    }

    /**
     * While a pillar stands, the nibblers all walk to the same pillar (picked at random when the
     * wave arrives, and again if it falls) and gnaw it every few ticks. When none is left they turn
     * on the players (see [engage]).
     */
    private fun nibblers(run: InfernoRun) {
        val standing = run.pillars.filter { it.alive }
        val biters =
            run.monsters.filter { InfernoMonsterDefs.of(it.type) === InfernoMonsterDefs.nibbler }
        if (standing.isEmpty() || biters.isEmpty()) {
            return
        }
        val now = mapClock.cycle
        val pillar =
            run.nibblerPillar?.takeIf { it.alive }
                ?: standing[random.of(standing.size)].also { run.nibblerPillar = it }
        for (npc in biters) {
            val state = run.states[npc] ?: continue
            if (pillar.isNextTo(npc.coords)) {
                if (now >= npc.actionDelay) {
                    npc.anim(inferno_seqs.nibbler_attack)
                    npc.actionDelay = now + NIBBLER_RATE
                    val bite = NIBBLER_MIN_HIT + random.of(NIBBLER_HIT_SPREAD)
                    pillars.damage(run, pillar, bite, source = npc)
                    if (!pillar.alive) {
                        // The others pick the next pillar on the next tick.
                        break
                    }
                }
            } else {
                val dest = approach(npc, pillar)
                if (state.walkTarget != dest) {
                    state.walkTarget = dest
                    npc.walk(dest)
                }
            }
        }
    }

    /**
     * The free tile in the ring around [pillar] that is nearest to [npc], so that the nibblers
     * spread out round it instead of queuing for one spot. Where it already stands if it is next to
     * the pillar, or if the whole ring is taken.
     */
    private fun approach(npc: Npc, pillar: InfernoPillar): CoordGrid {
        if (pillar.isNextTo(npc.coords)) {
            return npc.coords
        }
        val sw = pillar.southWest
        var best: CoordGrid? = null
        var bestDistance = Int.MAX_VALUE
        for (z in sw.z - 1..sw.z + InfernoPillar.SIZE) {
            for (x in sw.x - 1..sw.x + InfernoPillar.SIZE) {
                val tile = CoordGrid(x, z, sw.level)
                if (!pillar.isNextTo(tile) || !fitsAt(tile, 1)) {
                    continue
                }
                val distance =
                    maxOf(kotlin.math.abs(x - npc.coords.x), kotlin.math.abs(z - npc.coords.z))
                if (distance < bestDistance) {
                    best = tile
                    bestDistance = distance
                }
            }
        }
        return best ?: npc.coords
    }

    override fun onWin(run: InfernoRun, inside: List<Player>) {
        rewards.win(inside, run)
    }

    /** A melee monster fought: its burrow timer starts over (not in the wave's first 30 ticks). */
    fun noteAttack(npc: Npc) {
        val state = stateOf(npc) ?: return
        val now = mapClock.cycle
        if (now - state.spawnedAt >= BURROW_LOCK) {
            state.burrowAt = now + burrowInterval()
        }
    }

    private fun burrowInterval(): Int = BURROW_MIN + random.of(BURROW_SPREAD)

    /**
     * Melee monsters that have not been fighting for a while dig down and come up next to the
     * nearest player. Underground they cannot attack.
     */
    private fun burrow(run: InfernoRun) {
        val now = mapClock.cycle
        val targets = instances.playersIn(run.instance).filter { it.isValidTarget() }
        if (targets.isEmpty()) {
            return
        }
        for (npc in run.monsters.toList()) {
            val def = InfernoMonsterDefs.of(npc.type) ?: continue
            if (def.kind != InfernoMonster.Melee) {
                continue
            }
            val state = run.states[npc] ?: continue
            if (state.digUntil != 0) {
                if (now >= state.digUntil) {
                    surface(npc, def, state, targets.minBy { it.distanceTo(npc) })
                }
            } else if (now >= state.burrowAt) {
                npc.anim(inferno_seqs.melee_dig_down)
                npc.mode = NpcMode.None
                npc.actionDelay = Int.MAX_VALUE
                state.digUntil = now + DIG_TICKS
            }
        }
    }

    /** Brings [npc] up on a free spot next to [target], or tries again a little later. */
    private fun surface(
        npc: Npc,
        def: InfernoMonsterDef,
        state: InfernoMonsterState,
        target: Player,
    ) {
        val now = mapClock.cycle
        val spot = surfaceSpot(def.size, target.coords)
        if (spot == null) {
            state.digUntil = now + DIG_TICKS
            return
        }
        npc.telejump(collision, spot)
        npc.anim(inferno_seqs.melee_dig_up)
        npc.actionDelay = now + SURFACE_DELAY
        state.digUntil = 0
        state.burrowAt = now + burrowInterval()
    }

    /**
     * A south-west tile where a monster of [size] touches [target] without standing on them, or
     * `null` if there is no room.
     */
    private fun surfaceSpot(size: Int, target: CoordGrid): CoordGrid? {
        for (dz in -size..1) {
            for (dx in -size..1) {
                val onTarget = dx in -(size - 1)..0 && dz in -(size - 1)..0
                if (onTarget) {
                    continue
                }
                val spot = CoordGrid(target.x + dx, target.z + dz, target.level)
                if (fitsAt(spot, size)) {
                    return spot
                }
            }
        }
        return null
    }

    /** A monster of its run died (its death animation is about to play). */
    fun monsterDied(npc: Npc) {
        val run = runOf(npc) ?: return
        if (npc === run.zukFight?.zuk && run.state == WaveState.Won) {
            // His death was already handled: the rewards are paid once.
            return
        }
        run.monsters.remove(npc)
        run.healers.remove(npc)
        run.extras.remove(npc)
        run.zukFight?.healers?.remove(npc)
        val state = run.states.remove(npc)
        eventBus.publish(NpcDeathEvents.Killed(npc, npc.findHero(players)))
        val def = InfernoMonsterDefs.of(npc.type) ?: return
        if (def === InfernoMonsterDefs.zuk) {
            // Whatever is still alive dies with him.
            win(run)
            return
        }
        if (def === InfernoMonsterDefs.blob) {
            splitBlob(run, npc)
        }
        if (InfernoMonsterDefs.isJad(def)) {
            removeHealersOf(run, npc)
        }
        val reviveable =
            def.kind != InfernoMonster.Nibbler &&
                def.kind != InfernoMonster.Jad &&
                def.kind != InfernoMonster.Zuk &&
                def !== InfernoMonsterDefs.babyRanged &&
                def !== InfernoMonsterDefs.babyMagic &&
                def !== InfernoMonsterDefs.babyMelee
        if (reviveable && state?.revived != true) {
            run.slain += SlainMonster(def)
        }
    }

    /** A dead Jad has nobody left to heal for, so his healers go with him. */
    private fun removeHealersOf(run: InfernoRun, jad: Npc) {
        for (healer in run.healers.filter { run.states[it]?.owner === jad }) {
            run.healers.remove(healer)
            run.states.remove(healer)
            if (healer.isSlotAssigned) {
                npcRepo.del(healer, Int.MAX_VALUE)
            }
        }
    }

    /** A blob leaves a ranged, a magic and a melee splinter behind when it dies. */
    private fun splitBlob(run: InfernoRun, npc: Npc) {
        val splits =
            listOf(
                InfernoMonsterDefs.babyRanged,
                InfernoMonsterDefs.babyMagic,
                InfernoMonsterDefs.babyMelee,
            )
        for ((index, def) in splits.withIndex()) {
            val tile = CoordGrid(npc.coords.x + index, npc.coords.z + 1, npc.coords.level)
            val spawned = Npc(npcTypes[def.type], tile)
            spawned.mode = NpcMode.None
            npcRepo.add(spawned, LIFETIME)
            add(run, spawned)
        }
    }

    /** Whether [run] has a dead monster that a Jal-Zek could bring back. */
    fun canRevive(run: InfernoRun): Boolean = run.slain.any { !it.revived }

    /**
     * [mager] brings back a monster that died this wave, at half health near the centre-east of the
     * arena, once for each monster. Returns `false` if there was none to revive.
     */
    fun revive(run: InfernoRun, mager: Npc): Boolean {
        val slain = run.slain.firstOrNull { !it.revived } ?: return false
        slain.revived = true
        val origin = freeSpot(run, InfernoArena.reviveSpot, slain.def.size, emptyList())
        val npc = spawn(run, slain.def.type, origin)
        npc.hitpoints = maxOf(1, npc.baseHitpointsLvl / 2)
        npc.actionDelay = mapClock.cycle + REVIVED_ATTACK_DELAY
        add(run, npc, revived = true)
        mager.anim(inferno_seqs.mager_revive)
        return true
    }

    companion object {
        /** How far away players in the Inferno are told about npcs (the arena is 30 tiles long). */
        const val NPC_VIEW_DISTANCE = 44

        /** A minion of Zuk starts attacking the glyph this long after it arrives. */
        const val SHIELD_FIRST_ATTACK = 3

        /** A lone Jad attacks every 8 ticks (wiki). */
        const val JAD_RATE = 8

        /** In the triple wave each Jad attacks every 9 ticks, starting 3 ticks apart (wiki). */
        const val STAGGERED_JAD_RATE = 9
        const val JAD_STAGGER = 3

        /** How long after a wave starts the first Jad attack comes. A guess. */
        const val JAD_FIRST_ATTACK = 6

        /** Jad healers appear when he drops below this many hitpoints (wiki: 175). */
        const val JAD_HEALER_THRESHOLD = 175

        /** The wiki: five healers for a lone Jad, three for each of the triple wave. */
        const val HEALERS_SINGLE = 5
        const val HEALERS_MULTIPLE = 3

        /** The wiki: a healer heals its Jad for 15 to 24 (`15 + 0..9`) every 4 ticks. */
        const val HEAL_MIN = 15
        const val HEAL_SPREAD = 10
        const val HEAL_INTERVAL = 4

        /** The wiki: nibblers hit a pillar for 2 to 4 (`2 + 0..2`) every 4 ticks. */
        const val NIBBLER_RATE = 4
        const val NIBBLER_MIN_HIT = 2
        const val NIBBLER_HIT_SPREAD = 3

        /** The wiki: the first burrow comes 50 ticks into a wave. */
        const val BURROW_FIRST = 50

        /** The wiki: after that every 40 to 60 ticks (`40 + 0..20`). */
        const val BURROW_MIN = 40
        const val BURROW_SPREAD = 21

        /** The wiki: fighting does not reset the timer during the first 30 ticks. */
        const val BURROW_LOCK = 30

        /** How long it stays underground. The wiki gives no number. */
        const val DIG_TICKS = 4

        /** The wiki: it waits 6 ticks after coming up before it attacks. */
        const val SURFACE_DELAY = 6

        /** A revived monster waits a little before it attacks ("a short delay"). */
        const val REVIVED_ATTACK_DELAY = 4

        private const val LIFETIME = WaveDirector.LIFETIME
    }
}
