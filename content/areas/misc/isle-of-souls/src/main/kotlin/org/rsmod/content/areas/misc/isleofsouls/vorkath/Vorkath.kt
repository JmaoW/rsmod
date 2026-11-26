package org.rsmod.content.areas.misc.isleofsouls.vorkath

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.config.refs.queues
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.clearInteractionRoute
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Ungael crater in ordinary map coordinates (the real map, map square 35_63). The instance is a
 * copy of the block of 6x6 zones x 2248-2295, z 4040-4087 on all levels; the crater's floor is x
 * 2261-2283 by z 4054-4076, and its way out is the ice chunks on the south edge at (2272, 4053).
 * Read from the cache with `tools/dungeons/collision.py` and `census.py`.
 */
object VorkathArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 281, copyZoneZ = 505) {
                zoneWidth = 6
                zoneLength = 6
            }
        }

    /** Where players appear: just north of the ice chunks, as after climbing over them. */
    val start = CoordGrid(2272, 4054, 0)

    /** Where players leave to: beside the boat on the isle. */
    val exit = VorkathBoat.front

    /** Vorkath is 7x7. This is the south-west tile of the spot it sleeps on (the map's spawn). */
    const val SIZE = 7
    val bossSpot = CoordGrid(2269, 4062, 0)

    /**
     * The south-west corner of the grid the acid pools fall on: 7x7 cells of 3x3 tiles round
     * Vorkath (tsps, from rsprox captures of 297 acid specials).
     */
    val acidOrigin = CoordGrid(2262, 4055, 0)
}

/**
 * The boat on the isle that takes a team to their crater: a copy of Torfinn's boat at Ungael, on
 * the field south of the plaza, east of the Dagannoth Kings' ladder (a free spot found with
 * `tools/dungeons/collision.py`; the user will move it onto the water later). [front] is the tile
 * south of it, where players land when they come back.
 */
object VorkathBoat {
    val coords = CoordGrid(2178, 2796, 0)
    val angle = LocAngle.West
    val front = CoordGrid(2180, 2795, 0)
}

/** The crater as a kind of team instance. Registered with [TeamInstances] by `VorkathScript`. */
object VorkathInstance {
    const val NAME = "vorkath"

    val type =
        TeamInstanceType(
            name = NAME,
            template = VorkathArena.template,
            entry = VorkathArena.start,
            exit = VorkathArena.exit,
        )
}

/** Where a team's Vorkath is in its fight. */
enum class VorkathPhase {
    /** Asleep in the middle of the crater, waiting for a poke. */
    Asleep,

    /** Rising after a poke; it cannot be attacked yet. */
    Waking,

    /** Using its standard attacks. */
    Fighting,

    /**
     * The acid special: pools on the floor and rapid fire at its target. Damage on it is halved.
     */
    Acid,

    /** The freezing special: its target is frozen and a spawn walks up to them. It is immune. */
    Spawn,

    /** Dead, until it lies down asleep again. */
    Dead,
}

enum class VorkathSpecial {
    Acid,
    Spawn,
}

/**
 * What a player has done since they last came into the crater, for the tasks "Defeat Vorkath 15
 * Times Without Leaving" and "... 5 Times Without Special Damage". [kills] are the Vorkaths that
 * died while they were inside; [streak] counts those kills while nothing of a special has hurt
 * them, which a special's damage stops for the rest of the visit ([failed]).
 */
class VorkathVisit {
    var kills = 0
    var streak = 0
    var failed = false
}

/** Something of Vorkath's in the air that lands on [tile] at [landsAt]. */
class VorkathLanding(val tile: CoordGrid, val landsAt: Int, val roll: Int = 0)

/** One team's crater. It lives as long as its [instance] has someone. */
class VorkathRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var phase = VorkathPhase.Asleep

    /** Vorkath asleep, or rising, while [phase] is [VorkathPhase.Asleep] or `Waking`. */
    var sleeper: Npc? = null

    /** Vorkath awake, from the end of its rise until it dies. */
    var boss: Npc? = null

    /** The player it fights: the one who poked it, while they are in the crater. */
    var target: Player? = null

    var awakeAt = 0
    var nextAttackAt = 0
    var sleepAt = 0

    /** Standard attacks since the last special: a special comes after six. */
    var attacks = 0
    var nextSpecial = VorkathSpecial.Acid

    /** Fireballs in the air. */
    val fireballs = ArrayList<VorkathLanding>()

    // The acid special.
    var acidTarget: Player? = null
    var pendingPools: List<CoordGrid> = emptyList()
    var poolsLandAt = 0
    val pools = HashSet<CoordGrid>()
    var rapidLeft = 0
    var rapidNextAt = 0
    val rapidShots = ArrayList<VorkathLanding>()
    var acidEndsAt = 0

    // The freezing special.
    var frozen: Player? = null
    var freezeLandsAt = 0
    var spawnTile: CoordGrid? = null
    var spawnLandsAt = 0
    var spawn: Npc? = null
    var spawnBornAt = 0

    /** What each player has done since they came in, by username. */
    val visits = HashMap<String, VorkathVisit>()

    init {
        // Vorkath lies in the crater from the start.
        nextWaveAt = startClock + FIRST_SPAWN_DELAY
    }

    fun visitOf(player: Player): VorkathVisit = visits.getOrPut(player.username) { VorkathVisit() }

    override fun allNpcs(): List<Npc> = listOfNotNull(sleeper, boss, spawn)

    override fun clearNpcs() {
        super.clearNpcs()
        sleeper = null
        boss = null
        spawn = null
    }

    private companion object {
        const val FIRST_SPAWN_DELAY = 1
    }
}

/**
 * Runs a team's fight with Vorkath (OSRS Wiki, "Vorkath" and "Vorkath/Strategies"; timings from
 * tsps' notes on rsprox captures). The instance loop (enter, leave, the run tick, clean-up) is
 * [WaveDirector]; the run has one "wave", which lays Vorkath down asleep in the crater, and never
 * ends.
 *
 * A player pokes it awake; it rises for [WAKE_TICKS] and attacks [FIRST_ATTACK_DELAY] ticks later.
 * It never moves. Every [ATTACK_RATE] ticks it makes a standard attack at its target (see
 * [VorkathAttacks]), and after six of them one of its two specials, taking turns between them (the
 * first one is picked at random, and comes after five). The run times the specials tick by tick:
 * - **Acid**: pools land on the floor round it ([acidTiles]) three ticks after it spits; then 25
 *   shots of rapid fire, one a tick, each at the tile the target stands on, hit them for 25-41 if
 *   they are still on it. Anyone standing in acid takes up to 10 a tick, which heals Vorkath. Hits
 *   on Vorkath are halved meanwhile.
 * - **Spawn**: an icy breath freezes the target, then a zombified spawn lands about 8 tiles from
 *   them and walks up to them. Next to them it explodes for up to 60, less the more it was hurt;
 *   killing it (Crumble Undead does it at once) ends the special. Vorkath takes no damage
 *   meanwhile.
 *
 * When it dies the drops fall on the tile beside it nearest the player who gets them, and it lies
 * down asleep again a few ticks later, ready for the next poke.
 *
 * In a team's crater the other players fight it too: it attacks only its target (the player who
 * poked it, or the nearest player once they are gone), the fireball and the acid pools hurt whoever
 * stands in them.
 */
class Vorkath
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    private val players: PlayerList,
    private val random: GameRandom,
    private val attacks: VorkathAttacks,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
) : WaveDirector<VorkathRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = VorkathInstance.type

    override fun createRun(instance: TeamInstance): VorkathRun =
        VorkathRun(instance, mapClock.cycle)

    override fun waveMessage(run: VorkathRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(vorkath_timers.tick, 1)
        runOf(player)?.visits?.remove(player.username)
        player.mes("You climb over the ice chunks into the crater.")
    }

    override fun spawnWave(run: VorkathRun, inside: List<Player>) {
        layDown(run)
    }

    /** Vorkath is not one of the run's `monsters`: it is run in [onWaveTick]. */
    override fun engage(run: VorkathRun, npc: Npc, target: Player): Boolean = false

    override fun onWin(run: VorkathRun, inside: List<Player>) {}

    override fun onWaveTick(run: VorkathRun) {
        val now = mapClock.cycle
        val inside = instances.playersIn(run.instance).filter { it.isValidTarget() }
        landFireballs(run, inside, now)
        when (run.phase) {
            VorkathPhase.Asleep -> {}
            VorkathPhase.Waking -> if (now >= run.awakeAt) awaken(run, now)
            VorkathPhase.Fighting -> fight(run, inside, now)
            VorkathPhase.Acid -> tickAcid(run, inside, now)
            VorkathPhase.Spawn -> tickSpawn(run, inside, now)
            VorkathPhase.Dead -> if (now >= run.sleepAt) layDown(run)
        }
    }

    private fun layDown(run: VorkathRun) {
        run.sleeper = spawn(run, vorkath_npcs.sleeping, VorkathArena.bossSpot)
        run.phase = VorkathPhase.Asleep
        run.target = null
    }

    /**
     * [player] pokes [npc], a sleeping Vorkath: it starts to rise. Returns `false` when it is not
     * asleep (already rising, or not a team's Vorkath).
     */
    fun poke(player: Player, npc: Npc): Boolean {
        val run = runOf(npc) ?: return false
        if (run.phase != VorkathPhase.Asleep || run.sleeper !== npc) {
            return false
        }
        run.phase = VorkathPhase.Waking
        run.target = player
        run.awakeAt = mapClock.cycle + WAKE_TICKS
        // The no-op form at once, so a second poke cannot start a second rise.
        npc.transmog(npcTypes[vorkath_npcs.rising], Int.MAX_VALUE)
        npc.anim(vorkath_seqs.wake)
        return true
    }

    /** The rise is over: the sleeper makes way for Vorkath awake, which can now be attacked. */
    private fun awaken(run: VorkathRun, now: Int) {
        run.sleeper?.let { npcRepo.del(it, Int.MAX_VALUE) }
        run.sleeper = null
        val boss = spawn(run, vorkath_npcs.boss, VorkathArena.bossSpot)
        boss.hitpoints = HITPOINTS
        run.boss = boss
        run.phase = VorkathPhase.Fighting
        run.attacks = FIRST_SPECIAL_HEAD_START
        run.nextSpecial = if (random.of(2) == 0) VorkathSpecial.Acid else VorkathSpecial.Spawn
        run.nextAttackAt = now + FIRST_ATTACK_DELAY
    }

    /** The player Vorkath fights: its target while they are inside, or else the nearest. */
    private fun targetOf(run: VorkathRun, npc: Npc, inside: List<Player>): Player? {
        val current = run.target
        if (current != null && current in inside) {
            return current
        }
        val nearest = inside.minByOrNull { edgeDistance(npc, it.coords) }
        run.target = nearest
        return nearest
    }

    private fun fight(run: VorkathRun, inside: List<Player>, now: Int) {
        val boss = run.boss?.takeIf { it.isSlotAssigned && it.hitpoints > 0 } ?: return
        val target = targetOf(run, boss, inside) ?: return
        boss.facePlayer(target)
        if (now < run.nextAttackAt) {
            return
        }
        run.nextAttackAt = now + ATTACK_RATE
        if (run.attacks >= STANDARDS_BEFORE_SPECIAL) {
            run.attacks = 0
            val special = run.nextSpecial
            run.nextSpecial =
                if (special == VorkathSpecial.Acid) VorkathSpecial.Spawn else VorkathSpecial.Acid
            when (special) {
                VorkathSpecial.Acid -> startAcid(run, boss, target, now)
                VorkathSpecial.Spawn -> startSpawn(run, boss, target, now)
            }
            return
        }
        run.attacks++
        val attack = attacks.choose(adjacent = isAdjacent(boss, target.coords))
        if (attack == VorkathAttack.Fireball) {
            val tile = target.coords
            val lands = attacks.launchFireball(boss, tile)
            run.fireballs += VorkathLanding(tile, now + lands, attacks.rollFireball())
        } else {
            attacks.standard(boss, target, attack)
        }
    }

    /** Fireballs that land now hurt whoever is on their tile or next to it. */
    private fun landFireballs(run: VorkathRun, inside: List<Player>, now: Int) {
        if (run.fireballs.isEmpty()) {
            return
        }
        val landed = run.fireballs.filter { now >= it.landsAt }
        run.fireballs.removeAll(landed.toSet())
        val source = run.boss ?: return
        for (fireball in landed) {
            attacks.fireballLands(fireball.tile)
            for (player in inside) {
                val distance = player.coords.chebyshevDistance(fireball.tile)
                attacks.hurt(source, player, attacks.fireballDamage(fireball.roll, distance))
            }
        }
    }

    // The acid special -------------------------------------------------------------------------

    private fun startAcid(run: VorkathRun, boss: Npc, target: Player, now: Int) {
        run.phase = VorkathPhase.Acid
        run.acidTarget = target
        val tiles = acidTiles(run, boss, target.coords)
        attacks.spitAcid(boss, tiles)
        run.pendingPools = tiles
        run.poolsLandAt = now + ACID_POOL_LAND_DELAY
        run.rapidLeft = RAPID_SHOTS
        run.rapidNextAt = now + ACID_FIRST_SHOT_DELAY
        run.acidEndsAt = now + ACID_FORCE_END_TICKS
    }

    private fun tickAcid(run: VorkathRun, inside: List<Player>, now: Int) {
        val boss = run.boss ?: return
        if (run.pendingPools.isNotEmpty() && now >= run.poolsLandAt) {
            val acid = locTypes[vorkath_locs.acid]
            val lasts = maxOf(1, run.acidEndsAt - now)
            for (tile in run.pendingPools) {
                locRepo.add(tile, acid, lasts, LocAngle.West, LocShape.CentrepieceStraight)
            }
            run.pools += run.pendingPools
            run.pendingPools = emptyList()
        }

        val landed = run.rapidShots.filter { now >= it.landsAt }
        run.rapidShots.removeAll(landed.toSet())
        val target = run.acidTarget?.takeIf { it in inside }
        for (shot in landed) {
            attacks.rapidLands(shot.tile)
            if (target != null && target.coords == shot.tile) {
                specialDamage(run, target, attacks.hurt(boss, target, attacks.rollRapid()))
            }
        }
        if (target == null) {
            endAcid(run, now)
            return
        }
        if (run.rapidLeft > 0 && now >= run.rapidNextAt) {
            boss.facePlayer(target)
            val tile = target.coords
            attacks.rapidShot(boss, tile)
            run.rapidShots += VorkathLanding(tile, now + RAPID_FLIGHT_TICKS)
            run.rapidLeft--
            run.rapidNextAt = now + 1
        }

        for (player in inside) {
            if (player.coords !in run.pools) {
                continue
            }
            val done = attacks.hurt(boss, player, attacks.rollPool())
            specialDamage(run, player, done)
            boss.healHitpoints(done, HITPOINTS)
        }

        val shotsDone = run.rapidLeft == 0 && run.rapidShots.isEmpty()
        if (shotsDone || now >= run.acidEndsAt) {
            endAcid(run, now)
        }
    }

    private fun endAcid(run: VorkathRun, now: Int) {
        // The pools' locs go away on their own; they hurt nobody once the special is over.
        run.pools.clear()
        run.pendingPools = emptyList()
        run.rapidShots.clear()
        run.rapidLeft = 0
        run.acidTarget = null
        if (run.phase == VorkathPhase.Acid) {
            run.phase = VorkathPhase.Fighting
            run.nextAttackAt = now + AFTER_SPECIAL_DELAY
        }
    }

    /**
     * The tiles the acid pools land on (tsps, from rsprox captures): one at random in each 3x3 cell
     * of the 7x7 grid round Vorkath but the middle one, two on each side just outside the grid, and
     * the tile the player stands on. None lands under Vorkath or on a blocked tile.
     */
    fun acidTiles(run: VorkathRun, boss: Npc, player: CoordGrid): List<CoordGrid> {
        val origin = run.instance.region.normal[VorkathArena.acidOrigin]
        val pools = LinkedHashSet<CoordGrid>()
        fun free(tile: CoordGrid) = !underBoss(boss, tile) && fitsAt(tile, 1) && tile !in pools
        for (cx in 0 until ACID_GRID_CELLS) {
            for (cz in 0 until ACID_GRID_CELLS) {
                if (cx == ACID_GRID_CELLS / 2 && cz == ACID_GRID_CELLS / 2) {
                    continue
                }
                val cell =
                    (0 until ACID_CELL_SIZE).flatMap { dx ->
                        (0 until ACID_CELL_SIZE).map { dz ->
                            origin.translate(cx * ACID_CELL_SIZE + dx, cz * ACID_CELL_SIZE + dz)
                        }
                    }
                val candidates = cell.filter(::free)
                if (candidates.isNotEmpty()) {
                    pools += candidates[random.of(candidates.size)]
                }
            }
        }
        val span = ACID_GRID_CELLS * ACID_CELL_SIZE
        fun along(first: Int) = first + random.of(ACID_EDGE_SPREAD)
        val edges =
            listOf(
                origin.translate(along(ACID_EDGE_NEAR), -1),
                origin.translate(along(ACID_EDGE_FAR), -1),
                origin.translate(along(ACID_EDGE_NEAR), span),
                origin.translate(along(ACID_EDGE_FAR), span),
                origin.translate(-1, along(ACID_EDGE_NEAR)),
                origin.translate(-1, along(ACID_EDGE_FAR)),
                origin.translate(span, along(ACID_EDGE_NEAR)),
                origin.translate(span, along(ACID_EDGE_FAR)),
            )
        for (tile in edges + player) {
            if (free(tile)) {
                pools += tile
            }
        }
        return pools.toList()
    }

    // The freezing special ---------------------------------------------------------------------

    private fun startSpawn(run: VorkathRun, boss: Npc, target: Player, now: Int) {
        run.phase = VorkathPhase.Spawn
        run.frozen = target
        run.spawnTile = null
        run.spawn = null
        run.freezeLandsAt = now + attacks.iceBreath(boss, target)
    }

    private fun tickSpawn(run: VorkathRun, inside: List<Player>, now: Int) {
        val boss = run.boss ?: return
        val target = run.frozen?.takeIf { it in inside }
        if (target == null) {
            endSpawn(run, now)
            return
        }
        val spawn = run.spawn
        when {
            spawn != null -> walkSpawn(run, boss, spawn, target, now)
            run.spawnTile != null ->
                if (now >= run.spawnLandsAt) {
                    landSpawn(run, target, checkNotNull(run.spawnTile), now)
                }
            now >= run.freezeLandsAt -> {
                target.freeze(FREEZE_TICKS)
                target.mes(FROZEN)
                stopAttacking(target, boss)
                val tile = spawnTileFor(boss, target.coords)
                if (tile == null) {
                    endSpawn(run, now)
                    return
                }
                run.spawnTile = tile
                run.spawnLandsAt = now + attacks.throwSpawn(boss, tile)
            }
        }
    }

    /**
     * Ends [player]'s attack on [boss] when they are frozen: it is immune until the spawn is gone,
     * so they can turn to the spawn at once. An auto-retaliate queued by a hit still in flight is
     * dropped too, so it does not put them back on Vorkath. They can still click it again.
     */
    private fun stopAttacking(player: Player, boss: Npc) {
        if ((player.interaction as? InteractionNpc)?.target === boss) {
            player.clearInteractionRoute()
            player.resetFaceEntity()
        }
        player.clearQueue(queues.com_retaliate_npc)
    }

    private fun landSpawn(run: VorkathRun, target: Player, tile: CoordGrid, now: Int) {
        val spawn = Npc(npcTypes[vorkath_npcs.spawn], tile)
        npcRepo.add(spawn, LIFETIME)
        spawn.hitpoints = SPAWN_HITPOINTS
        run.spawn = spawn
        run.spawnBornAt = now
        engage(spawn, target, ranged = false)
    }

    /** The spawn walks up to [target]; next to them, or after its fuse, it explodes. */
    private fun walkSpawn(run: VorkathRun, boss: Npc, spawn: Npc, target: Player, now: Int) {
        if (!spawn.isSlotAssigned || spawn.hitpoints <= 0) {
            return
        }
        val next = spawn.coords.chebyshevDistance(target.coords) <= 1
        if (!next && now - run.spawnBornAt <= SPAWN_FUSE_TICKS) {
            if ((spawn.interaction as? InteractionPlayerOp)?.target !== target) {
                engage(spawn, target, ranged = false)
            }
            return
        }
        attacks.explode(spawn.coords)
        val damage = attacks.explosionDamage(spawn.hitpoints)
        specialDamage(run, target, attacks.hurt(boss, target, damage))
        npcRepo.del(spawn, Int.MAX_VALUE)
        run.spawn = null
        endSpawn(run, now)
    }

    /**
     * A tile [SPAWN_DISTANCE] tiles from [player] (or near that) that the spawn can land on and
     * walk to them from in a straight line, clear of Vorkath. `null` if there is none.
     */
    fun spawnTileFor(boss: Npc, player: CoordGrid): CoordGrid? {
        for (distance in SPAWN_DISTANCES) {
            val candidates = ArrayList<CoordGrid>()
            for (dx in -distance..distance) {
                for (dz in -distance..distance) {
                    if (maxOf(abs(dx), abs(dz)) != distance) {
                        continue
                    }
                    val tile = player.translate(dx, dz)
                    if (
                        !underBoss(boss, tile) && fitsAt(tile, 1) && clearPath(boss, tile, player)
                    ) {
                        candidates += tile
                    }
                }
            }
            if (candidates.isNotEmpty()) {
                return candidates[random.of(candidates.size)]
            }
        }
        return null
    }

    private fun clearPath(boss: Npc, from: CoordGrid, to: CoordGrid): Boolean {
        var x = from.x
        var z = from.z
        repeat(MAX_PATH_STEPS) {
            if (x == to.x && z == to.z) {
                return true
            }
            x += (to.x - x).sign
            z += (to.z - z).sign
            val step = CoordGrid(x, z, from.level)
            if (step != to && (underBoss(boss, step) || !fitsAt(step, 1))) {
                return false
            }
        }
        return false
    }

    /** The spawn has died (killed by a player): its target is free again. */
    fun spawnDied(npc: Npc) {
        val run = runOf(npc) ?: return
        if (run.spawn !== npc) {
            return
        }
        run.spawn = null
        run.frozen?.mes(SPAWN_KILLED)
        endSpawn(run, mapClock.cycle)
    }

    private fun endSpawn(run: VorkathRun, now: Int) {
        run.frozen?.frozenUntil = Int.MIN_VALUE
        run.frozen = null
        run.spawnTile = null
        run.spawn?.let { if (it.isSlotAssigned) npcRepo.del(it, Int.MAX_VALUE) }
        run.spawn = null
        if (run.phase == VorkathPhase.Spawn) {
            run.phase = VorkathPhase.Fighting
            run.nextAttackAt = now + AFTER_SPECIAL_DELAY
        }
    }

    /**
     * [player] took [damage] from a special: their streak of clean kills is over for this visit.
     */
    private fun specialDamage(run: VorkathRun, player: Player, damage: Int) {
        if (damage > 0) {
            run.visitOf(player).failed = true
        }
    }

    // Damage on Vorkath, and its death ---------------------------------------------------------

    /** What a hit of [damage] on [npc] really does: nothing during the spawn, half during acid. */
    fun incomingDamage(npc: Npc, damage: Int): Int {
        val run = runOf(npc) ?: return damage
        if (run.boss !== npc) {
            return damage
        }
        return when (run.phase) {
            VorkathPhase.Spawn -> 0
            VorkathPhase.Acid -> damage / 2
            else -> damage
        }
    }

    fun isImmune(npc: Npc): Boolean =
        runOf(npc)?.takeIf { it.boss === npc }?.phase == VorkathPhase.Spawn

    /**
     * Vorkath has died (called by the death script, which then plays the death and spawns the
     * drops: that publishes the kill). Ends any special, counts the kill for everyone inside, and
     * returns where the drops fall.
     */
    fun bossDied(npc: Npc): CoordGrid? {
        val run = runOf(npc) ?: return null
        if (run.boss !== npc) {
            return null
        }
        val now = mapClock.cycle
        endAcid(run, now)
        endSpawn(run, now)
        run.fireballs.clear()
        run.boss = null
        run.phase = VorkathPhase.Dead
        run.sleepAt = now + SLEEP_DELAY
        for (player in instances.playersIn(run.instance)) {
            val visit = run.visitOf(player)
            visit.kills++
            if (!visit.failed) {
                visit.streak++
            }
        }
        return lootTile(npc, npc.findHero(players))
    }

    /**
     * The tile the drops fall on: one of the eight just outside Vorkath (beyond its corners and the
     * middles of its sides), the one nearest [hero] (tsps, from 543 captured kills).
     */
    fun lootTile(npc: Npc, hero: Player?): CoordGrid {
        val base = npc.coords
        val tiles = LOOT_OFFSETS.map { (dx, dz) -> base.translate(dx, dz) }.filter { fitsAt(it, 1) }
        if (tiles.isEmpty()) {
            return base.translate(VorkathArena.SIZE / 2, -1)
        }
        val at = hero?.coords ?: return tiles.first()
        return tiles.minBy { (it.x - at.x) * (it.x - at.x) + (it.z - at.z) * (it.z - at.z) }
    }

    /** What [player] has done since they came into their team's crater, or `null` outside. */
    fun visitOf(player: Player): VorkathVisit? = runOf(player)?.visits?.get(player.username)

    /** The players in the crater [npc] is in, each with what they have done this visit. */
    fun visitsAt(npc: Npc): List<Pair<Player, VorkathVisit>> {
        val run = runOf(npc) ?: return emptyList()
        return instances.playersIn(run.instance).map { it to run.visitOf(it) }
    }

    companion object {
        const val HITPOINTS = 750
        const val SPAWN_HITPOINTS = 38

        /** It attacks every 5 ticks (wiki). */
        const val ATTACK_RATE = 5
        const val STANDARDS_BEFORE_SPECIAL = 6

        /**
         * rsprox captures: the first special follows five standard attacks, every later one six.
         */
        const val FIRST_SPECIAL_HEAD_START = 1

        /** rsprox captures: awake 7 ticks into the rise, its first attack 6 ticks after that. */
        const val WAKE_TICKS = 7
        const val FIRST_ATTACK_DELAY = 6

        /** Ticks from the end of a special to the next standard attack (tsps). */
        const val AFTER_SPECIAL_DELAY = 2

        /** Ticks from its death to lying down asleep again: the death animation, and a tick. */
        const val SLEEP_DELAY = 8

        const val ACID_POOL_LAND_DELAY = 3
        const val ACID_FIRST_SHOT_DELAY = 4
        const val ACID_FORCE_END_TICKS = 33
        const val RAPID_SHOTS = 25
        const val RAPID_FLIGHT_TICKS = 1
        const val ACID_GRID_CELLS = 7
        const val ACID_CELL_SIZE = 3
        const val ACID_EDGE_NEAR = 4
        const val ACID_EDGE_FAR = 14
        const val ACID_EDGE_SPREAD = 3

        /** A minute's freeze (tsps): the spawn ends it long before. */
        const val FREEZE_TICKS = 100

        /** rsprox captures: the spawn lands 8 tiles from the player (297 of 317 spawns). */
        val SPAWN_DISTANCES = listOf(8, 7, 9, 6, 10)

        /** A spawn that has not reached its target after this long explodes where it is (tsps). */
        const val SPAWN_FUSE_TICKS = 60
        const val MAX_PATH_STEPS = 64

        const val FROZEN = "<col=ff3045>You have been frozen!</col>"
        const val SPAWN_KILLED = "<col=229628>You become unfrozen as you kill the spawn.</col>"

        /** The loot tiles, from Vorkath's south-west tile (its body is 0-6 on both axes). */
        val LOOT_OFFSETS =
            listOf(-1 to -1, 3 to -1, 7 to -1, -1 to 3, 7 to 3, -1 to 7, 3 to 7, 7 to 7)

        fun underBoss(boss: Npc, tile: CoordGrid): Boolean =
            tile.x >= boss.coords.x &&
                tile.x < boss.coords.x + VorkathArena.SIZE &&
                tile.z >= boss.coords.z &&
                tile.z < boss.coords.z + VorkathArena.SIZE

        /** Tiles from the edge of Vorkath's 7x7 to [tile]; 0 under it. */
        fun edgeDistance(boss: Npc, tile: CoordGrid): Int {
            val minX = boss.coords.x
            val minZ = boss.coords.z
            val maxX = minX + VorkathArena.SIZE - 1
            val maxZ = minZ + VorkathArena.SIZE - 1
            val dx = if (tile.x < minX) minX - tile.x else if (tile.x > maxX) tile.x - maxX else 0
            val dz = if (tile.z < minZ) minZ - tile.z else if (tile.z > maxZ) tile.z - maxZ else 0
            return maxOf(dx, dz)
        }

        /** Next to Vorkath, diagonally too: where its melee reaches. */
        fun isAdjacent(boss: Npc, tile: CoordGrid): Boolean =
            edgeDistance(boss, tile) == 1 && !underBoss(boss, tile)
    }
}
