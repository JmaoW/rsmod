package org.rsmod.content.areas.misc.isleofsouls.giants

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** What Obor's lair remembers about one team's fight. */
class OborRun(instance: TeamInstance, startClock: Int) :
    GiantRun(instance, startClock, GiantLairs.obor) {
    /** The tick of his next attack. */
    var attackAt = 0

    /** How close he wants a player before he attacks: next to him, or anywhere within range. */
    var reach = Obor.MELEE_REACH

    var walkDest: CoordGrid? = null
}

/**
 * Obor, the hill giant boss, as captured by tsps (stats from the wiki: 120 hitpoints, max hits 22
 * melee and 26 ranged, attack speed 6):
 * - in reach he punches, the hit landing a tick later, and now and then knocks the player back: a
 *   5-tile slide away from him, stopping at walls, the hit 2 ticks later;
 * - from further away the same swing throws a boulder: a tick later the earthstrike splash and the
 *   player is knocked down;
 * - his stamp sends falling rocks from in front of him along the line to the player, a tile every 6
 *   client cycles with the camera shaking; the hit lands 2 ticks after the stamp;
 * - his ranged attacks do half damage through Protect from Missiles.
 *
 * He is confined to a pit that players slide into and out of by the rocks, and ignores (and cannot
 * be hurt by) anyone standing safely outside it. How often he stamps, knocks back or stays at range
 * is not on the wiki; the odds are tsps' estimates.
 */
class Obor
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    players: PlayerList,
    random: GameRandom,
    locRepo: LocRepository,
    locTypes: LocTypeList,
    private val worldRepo: WorldRepository,
    private val hits: GiantHits,
) :
    GiantDirector<OborRun>(
        GiantLairs.obor,
        instances,
        npcRepo,
        npcTypes,
        interactions,
        collision,
        mapClock,
        players,
        random,
        locRepo,
        locTypes,
    ) {
    private val validator = StepValidator(collision)

    override val bossType: NpcType
        get() = giant_npcs.obor

    override fun newRun(instance: TeamInstance): OborRun = OborRun(instance, mapClock.cycle)

    override fun bossSpawned(run: OborRun, boss: Npc) {
        run.attackAt = mapClock.cycle + 1
        run.reach = MELEE_REACH
        run.walkDest = null
        boss.faceSquare(run.real(GiantLairs.obor.spawn.translate(0, -4)), 1, 1)
    }

    /** Players inside the pit: the rest are in the safe zone and he leaves them alone. */
    override fun targetsOf(run: OborRun): List<Player> =
        super.targetsOf(run).filter { inPit(run, it) }

    /** Whether [player] stands in the pit (below the fence) rather than in the safe zone. */
    fun inPit(run: GiantRun, player: Player): Boolean =
        player.coords.z <= run.real(CoordGrid(ROCKS_X, ROCKS_TOP_Z - 1, 0)).z

    override fun fight(run: OborRun, boss: Npc, targets: List<Player>, now: Int) {
        val target = targets.minBy { boss.distanceTo(it) }
        boss.facePlayer(target)
        val inRange =
            if (run.reach == MELEE_REACH) inReach(boss, target.coords)
            else boss.distanceTo(target) <= RANGED_REACH
        if (now < run.attackAt || !inRange) {
            if (!inRange) {
                approach(run, boss, target)
            }
            return
        }
        attack(run, boss, target, now)
    }

    private fun attack(run: OborRun, boss: Npc, target: Player, now: Int) {
        val adjacent = inReach(boss, target.coords)
        val stamp = random.of(if (adjacent) STAMP_ODDS_ADJACENT else STAMP_ODDS_DISTANT) == 0
        run.reach = if (random.of(2) == 0) MELEE_REACH else RANGED_REACH
        run.attackAt = now + ATTACK_RATE
        run.walkDest = null
        when {
            stamp -> stamp(run, boss, target, now)
            adjacent -> punch(run, boss, target)
            else -> boulder(run, boss, target, now)
        }
    }

    /** The punch: the hit a tick later, or two if it also knocks the player back. */
    private fun punch(run: OborRun, boss: Npc, target: Player) {
        boss.anim(giant_seqs.obor_punch)
        val knocked = random.of(KNOCKBACK_ODDS) == 0 && knockback(boss, target)
        hits.strike(boss, target, NpcAttackStyle.Melee, MELEE_MAX_HIT, if (knocked) 2 else 1)
    }

    /** The same swing from further away throws a boulder that lands a tick later. */
    private fun boulder(run: OborRun, boss: Npc, target: Player, now: Int) {
        boss.anim(giant_seqs.obor_punch)
        hits.strike(
            boss,
            target,
            NpcAttackStyle.Ranged,
            RANGED_MAX_HIT,
            1,
            reduction = GiantHits.HALF,
        )
        run.later(now + 1) {
            if (!target.isValid()) {
                return@later
            }
            target.spotanim(
                giant_spotanims.boulder_impact,
                delay = BOULDER_DELAY,
                height = BOULDER_HEIGHT,
            )
            target.anim(giant_seqs.player_knockdown, delay = BOULDER_DELAY)
            target.soundSynth(giant_synths.boulder_impact)
            shake(run, target, now + 1)
        }
    }

    /**
     * The stamp: the splash on his tile now; a tick later the rocks fall, tile by tile, to the
     * player. The hit lands two ticks after the stamp.
     */
    private fun stamp(run: OborRun, boss: Npc, target: Player, now: Int) {
        boss.anim(giant_seqs.obor_stamp)
        val (origin, tiles) = rockLine(boss, target.coords)
        hits.strike(
            boss,
            target,
            NpcAttackStyle.Ranged,
            RANGED_MAX_HIT,
            2,
            reduction = GiantHits.HALF,
        )
        if (tiles.isEmpty()) {
            return
        }
        worldRepo.spotanimMap(giant_spotanims.stamp_splash, origin, delay = STAMP_SPLASH_DELAY)
        run.later(now + 1) {
            if (!target.isValid() || boss.hitpoints <= 0) {
                return@later
            }
            for ((index, tile) in tiles.withIndex()) {
                worldRepo.spotanimMap(
                    giant_spotanims.rockfall,
                    tile,
                    height = ROCK_HEIGHT,
                    delay = ROCK_CYCLES_PER_TILE * (index + 1),
                )
            }
            val landing = ROCK_CYCLES_PER_TILE * tiles.size
            target.anim(giant_seqs.player_rockfall, delay = landing)
            target.soundSynth(giant_synths.rockfall_a)
            target.soundSynth(giant_synths.rockfall_b)
            shake(run, target, now + 1)
        }
    }

    private fun shake(run: GiantRun, target: Player, at: Int) {
        Camera.camShake(target, 4, 4, 0, 1)
        run.later(at + 1) { if (target.isValid()) Camera.camShake(target, 4, 0, 0, 0) }
    }

    private fun Player.isValid(): Boolean = isSlotAssigned

    /**
     * The stamp's line: the splash goes on his own tile nearest the player; the rocks fall on every
     * tile from beside him to the player's.
     */
    private fun rockLine(boss: Npc, to: CoordGrid): Pair<CoordGrid, List<CoordGrid>> {
        val at = boss.coords
        val size = boss.size
        val fromX = to.x.coerceIn(at.x, at.x + size - 1)
        val fromZ = to.z.coerceIn(at.z, at.z + size - 1)
        val steps = maxOf(kotlin.math.abs(to.x - fromX), kotlin.math.abs(to.z - fromZ))
        val tiles = ArrayList<CoordGrid>()
        for (i in 1..steps) {
            val x = Math.round(fromX + (to.x - fromX) * i.toDouble() / steps).toInt()
            val z = Math.round(fromZ + (to.z - fromZ) * i.toDouble() / steps).toInt()
            tiles += CoordGrid(x, z, to.level)
        }
        return CoordGrid(fromX, fromZ, to.level) to tiles
    }

    /**
     * Throws [target] up to [KNOCKBACK_TILES] tiles straight away from him, stopping at a wall.
     * Returns `false` (and does nothing) if there is no room for even one tile.
     */
    private fun knockback(boss: Npc, target: Player): Boolean {
        val centreX = boss.coords.x + (boss.size - 1) / 2.0
        val centreZ = boss.coords.z + (boss.size - 1) / 2.0
        val from = target.coords
        val horizontal = kotlin.math.abs(from.x - centreX) >= kotlin.math.abs(from.z - centreZ)
        val dx = if (horizontal) sign(from.x - centreX) else 0
        val dz = if (horizontal) 0 else sign(from.z - centreZ)
        var at = from
        var moved = 0
        while (
            moved < KNOCKBACK_TILES &&
                validator.canTravel(at.level, at.x, at.z, dx, dz) &&
                openFloor(CoordGrid(at.x + dx, at.z + dz, at.level))
        ) {
            at = CoordGrid(at.x + dx, at.z + dz, at.level)
            moved++
        }
        if (moved == 0) {
            return false
        }
        target.anim(giant_seqs.player_flyback)
        PathingEntityCommon.exactMove(
            target,
            from,
            at,
            KNOCKBACK_START_CYCLES,
            KNOCKBACK_END_CYCLES,
            facing(-dx, -dz),
            collision,
        )
        return true
    }

    private fun openFloor(tile: CoordGrid): Boolean =
        (collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) == 0

    private fun sign(value: Double): Int =
        when {
            value > 0 -> 1
            value < 0 -> -1
            else -> 1
        }

    /** Whether [tile] is next to his body (diagonals do not count for melee). */
    fun inReach(boss: Npc, tile: CoordGrid): Boolean {
        val at = boss.coords
        val size = boss.size
        val gapX =
            when {
                tile.x < at.x -> at.x - tile.x
                tile.x > at.x + size - 1 -> tile.x - (at.x + size - 1)
                else -> 0
            }
        val gapZ =
            when {
                tile.z < at.z -> at.z - tile.z
                tile.z > at.z + size - 1 -> tile.z - (at.z + size - 1)
                else -> 0
            }
        return gapX + gapZ == 1
    }

    /**
     * Takes a step towards [target]: to the nearest spot that puts his body straight beside them.
     */
    private fun approach(run: OborRun, boss: Npc, target: Player) {
        val size = boss.size
        val here = boss.coords
        var best: CoordGrid? = null
        var bestAway = Int.MAX_VALUE
        for (dz in -size..1) {
            for (dx in -size..1) {
                val spot = CoordGrid(target.coords.x + dx, target.coords.z + dz, here.level)
                if (!straightBeside(spot, size, target.coords) || !standable(spot, size)) {
                    continue
                }
                val away = distance(here, spot)
                if (away < bestAway) {
                    best = spot
                    bestAway = away
                }
            }
        }
        val dest = best ?: return
        if (run.walkDest != dest || !boss.hasMovedPreviousCycle) {
            run.walkDest = dest
            boss.walk(dest)
        }
    }

    private fun straightBeside(spot: CoordGrid, size: Int, tile: CoordGrid): Boolean {
        val gapX =
            when {
                tile.x < spot.x -> spot.x - tile.x
                tile.x > spot.x + size - 1 -> tile.x - (spot.x + size - 1)
                else -> 0
            }
        val gapZ =
            when {
                tile.z < spot.z -> spot.z - tile.z
                tile.z > spot.z + size - 1 -> tile.z - (spot.z + size - 1)
                else -> 0
            }
        return gapX + gapZ == 1
    }

    private fun standable(spot: CoordGrid, size: Int): Boolean {
        for (z in 0 until size) {
            for (x in 0 until size) {
                if ((collision[spot.x + x, spot.z + z, spot.level] and WALK_BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    // The pit's rocks --------------------------------------------------------------------------

    /**
     * Slides [player] down into the pit from the safe zone, or back up out of it, at the rocks
     * column [rockX] (a real coordinate). As they slide in he goes for them: his first attack came
     * the next tick, as captured.
     */
    fun climb(run: OborRun, player: Player, rockX: Int) {
        val top = run.real(CoordGrid(ROCKS_X, ROCKS_TOP_Z, 0)).z
        val bottom = top - (ROCKS_TOP_Z - ROCKS_BOTTOM_Z)
        val down = !inPit(run, player)
        val from = CoordGrid(rockX, if (down) top else bottom, player.coords.level)
        val to = CoordGrid(rockX, if (down) bottom else top, player.coords.level)
        player.mes(if (down) "You climb into the pit." else "You climb out of the pit.")
        player.soundSynth(giant_synths.pit_climb)
        if (player.coords != from) {
            PathingEntityCommon.telejump(player, collision, from)
        }
        player.anim(giant_seqs.player_climb)
        PathingEntityCommon.exactMove(
            player,
            from,
            to,
            CLIMB_START_CYCLES,
            CLIMB_END_CYCLES,
            NORTH,
            collision,
        )
        if (down && !run.dead) {
            run.attackAt = minOf(run.attackAt, mapClock.cycle + 1)
        }
    }

    private fun facing(dx: Int, dz: Int): Int =
        when {
            dx > 0 -> EAST
            dx < 0 -> WEST
            dz > 0 -> NORTH
            else -> SOUTH
        }

    companion object {
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** The wiki: attack speed 6; max hits 22 melee and 26 ranged. */
        const val ATTACK_RATE = 6
        const val MELEE_MAX_HIT = 22
        const val RANGED_MAX_HIT = 26

        /** He wants a player next to him, or anywhere within eight tiles (tsps). */
        const val MELEE_REACH = 1
        const val RANGED_REACH = 8

        /** A stamp is 1 attack in 3 next to him and 1 in 2 further away; a knockback 1 in 4. */
        const val STAMP_ODDS_ADJACENT = 3
        const val STAMP_ODDS_DISTANT = 2
        const val KNOCKBACK_ODDS = 4
        const val KNOCKBACK_TILES = 5
        const val KNOCKBACK_START_CYCLES = 20
        const val KNOCKBACK_END_CYCLES = 40

        const val BOULDER_DELAY = 20
        const val BOULDER_HEIGHT = 160
        const val STAMP_SPLASH_DELAY = 40
        const val ROCK_HEIGHT = 96
        const val ROCK_CYCLES_PER_TILE = 6

        /** The rocks into the pit (x 3091-3092, z 9805-9806); the safe zone starts at z 9807. */
        const val ROCKS_X = 3091
        const val ROCKS_TOP_Z = 9807
        const val ROCKS_BOTTOM_Z = 9804
        const val CLIMB_START_CYCLES = 10
        const val CLIMB_END_CYCLES = 90

        const val SOUTH = 0
        const val WEST = 512
        const val NORTH = 1024
        const val EAST = 1536
    }
}
