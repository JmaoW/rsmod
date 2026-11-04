package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The few things every part of the Colosseum does to a run's copy of the arena: putting npcs, locs
 * and graphics on real arena tiles, hurting players, and doing things a few ticks later.
 * Coordinates given here are the real arena's; they are moved into the run's copy.
 */
class ColosseumWorld
@Inject
constructor(
    val npcRepo: NpcRepository,
    val npcTypes: NpcTypeList,
    val locRepo: LocRepository,
    val locTypes: LocTypeList,
    val worldRepo: WorldRepository,
    val collision: CollisionFlagMap,
    val random: GameRandom,
    private val mapClock: MapClock,
    private val routes: RouteFactory,
) {
    val now: Int
        get() = mapClock.cycle

    /** A number from 0 until [bound]. */
    fun roll(bound: Int): Int = if (bound <= 1) 0 else random.of(bound)

    /** A number from [min] to [max], both included. */
    fun between(min: Int, max: Int): Int = min + roll(max - min + 1)

    /** Puts a npc of [type] with its south-west tile on [real]. The run keeps track of it. */
    fun spawn(run: ColosseumRun, type: NpcType, real: CoordGrid): Npc {
        val npc = Npc(npcTypes[type], run.inst(real))
        npc.mode = NpcMode.None
        npcRepo.add(npc, LIFETIME)
        return npc
    }

    fun remove(npc: Npc?) {
        if (npc != null && npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun addLoc(
        run: ColosseumRun,
        type: LocType,
        real: CoordGrid,
        angle: Int = 0,
        shape: LocShape = LocShape.CentrepieceStraight,
    ): LocInfo = locRepo.add(run.inst(real), locTypes[type], Int.MAX_VALUE, LocAngle[angle], shape)

    fun removeLoc(loc: LocInfo?) {
        if (loc != null) {
            locRepo.del(loc, Int.MAX_VALUE)
        }
    }

    fun graphic(
        run: ColosseumRun,
        spot: SpotanimType,
        real: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        worldRepo.spotanimMap(spot, run.inst(real), height, delay)
    }

    /** Does [action] [ticks] from now, unless the run ends first. */
    fun later(run: ColosseumRun, ticks: Int, action: () -> Unit) {
        run.later += ColosseumLater(now + maxOf(1, ticks), action)
    }

    /**
     * Damage that no protection prayer touches, landing [delay] ticks from now. [source] is the
     * monster that dealt it, if any (it costs the wave's "no damage" Glory); none for the arena's
     * hazards.
     */
    fun hurt(player: Player, damage: Int, source: Npc? = null, delay: Int = 1) {
        if (damage <= 0) {
            return
        }
        if (source != null && source.isSlotAssigned) {
            player.queueHit(source, maxOf(1, delay), HitType.Typeless, damage)
        } else {
            player.queueHit(maxOf(1, delay), HitType.Typeless, damage)
        }
    }

    /** Whether a [size] footprint with its south-west tile on [real] stands clear of walls. */
    fun free(run: ColosseumRun, real: CoordGrid, size: Int = 1): Boolean {
        val at = run.inst(real)
        for (z in 0 until size) {
            for (x in 0 until size) {
                if ((collision[at.x + x, at.z + z, at.level] and BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * Sends [npc] towards [dest] (a tile in the run's copy). With [smart] it finds its way round
     * walls and pillars, as the warband and Red Flag's Minotaur do; without, it walks straight at
     * it like any other npc.
     */
    fun walk(npc: Npc, dest: CoordGrid, smart: Boolean) {
        if (!smart) {
            npc.walk(dest)
            return
        }
        val strategy = npc.collisionStrategy ?: CollisionStrategy.Normal
        val route = routes.create(npc.avatar, dest, strategy)
        if (route.waypoints.isEmpty()) {
            return
        }
        npc.abortRoute()
        for (waypoint in route.waypoints) {
            npc.routeDestination.add(CoordGrid(waypoint.x, waypoint.z, dest.level))
        }
        npc.moveSpeed = npc.defaultMoveSpeed
    }

    companion object {
        /** A monster never expires on its own; the run removes them. About 16 hours. */
        const val LIFETIME = 100_000

        val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** The distance in tiles between two tiles: the larger of the two axes. */
        fun chebyshev(a: CoordGrid, b: CoordGrid): Int = maxOf(abs(a.x - b.x), abs(a.z - b.z))
    }
}
