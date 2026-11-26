package org.rsmod.api.game.process.npc

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.route.RouteFactory
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Walks follower npcs (a pet, a thrall, a guardian) behind the player they belong to.
 *
 * Each kind of follower [register]s once, with its [Rank], a function that calls [walk] (or
 * [approach]) for every follower of that kind. Every follower script calls [lateCycle] from its
 * `GameLifecycle.LateCycle` handler, which comes after players have moved. The first call each tick
 * steps every registered kind in rank order, and later calls that tick do nothing. So with several
 * followers out, they always form the same line behind their owner: the pet right behind them, then
 * the thrall, then the guardian. Each stops one tile further back along the trail than the one
 * ahead of it, and moves after it, into the tile it has just left.
 *
 * This replaces `NpcMode.PlayerFollow` for followers, which has two flaws:
 * - Npcs move before players. The follow mode heads for the tile next to where the owner *was*, and
 *   the owner then often steps onto the npc.
 * - Any npc that moves without `giveChase` gets `defaultMode()` the next tick, and that also drops
 *   its face target, so the follower keeps looking away.
 *
 * A follower here has npc mode `None`, so the engine never moves it. It walks the [Trail] of tiles
 * its owner left: it steps where they stepped, runs when they run, goes round corners the way they
 * did, and stops on the tile behind them, never on theirs. Facing is left to the caller (the owner,
 * or what the owner is fighting).
 */
@Singleton
public class NpcFollowWalker
@Inject
constructor(
    private val movement: NpcMovementProcessor,
    private val routes: RouteFactory,
    private val collision: CollisionFlagMap,
    private val mapClock: MapClock,
) {
    private val validator = StepValidator(collision)

    /** A kind of follower's place in the line behind its owner: the first walks closest. */
    public enum class Rank {
        Pet,
        Thrall,
        Guardian,
    }

    private val kinds = mutableListOf<Pair<Rank, () -> Unit>>()
    private var lastCycle = Int.MIN_VALUE

    /** How many followers of each owner have already taken their place in the line this tick. */
    private val placed = HashMap<Player, Int>()

    /** Adds a kind of follower: [stepAll] walks every follower of that kind for one tick. */
    public fun register(rank: Rank, stepAll: () -> Unit) {
        kinds += rank to stepAll
        kinds.sortBy { it.first.ordinal }
    }

    /**
     * Steps every registered kind of follower, in [Rank] order. Call it from each follower script's
     * `GameLifecycle.LateCycle` handler: only the first call each tick does anything.
     */
    public fun lateCycle() {
        if (lastCycle == mapClock.cycle) {
            return
        }
        lastCycle = mapClock.cycle
        placed.clear()
        for ((_, stepAll) in kinds) {
            stepAll()
        }
    }

    /** The tiles a follower's owner walked off, oldest first. Keep one per follower. */
    public class Trail {
        internal val tiles: ArrayDeque<CoordGrid> = ArrayDeque()

        public val size: Int
            get() = tiles.size

        public fun toList(): List<CoordGrid> = tiles.toList()

        public fun clear() {
            tiles.clear()
        }
    }

    /** Takes [npc] off the engine's own movement modes. Done again on every [walk]. */
    public fun claim(npc: Npc) {
        if (npc.mode != NpcMode.None) {
            npc.mode = NpcMode.None
        }
    }

    /**
     * One tick of [npc] following [owner]. When it is on another level or more than [leash] tiles
     * away (a teleport, a ladder), it jumps beside them instead. Followers of [owner] walked
     * earlier this tick are ahead of it in the line, so it stops that many tiles further back.
     */
    public fun walk(npc: Npc, owner: Player, trail: Trail, leash: Int) {
        claim(npc)
        val ahead = placed.merge(owner, 1, Int::plus)!! - 1
        val far = npc.coords.level != owner.coords.level || !npc.isWithinDistance(owner, leash)
        if (far) {
            jumpBeside(npc, owner, trail)
            return
        }
        record(owner, trail)
        step(npc, owner, trail, ahead)
    }

    /** Puts [npc] on a free tile next to [owner] (see [tileBeside]) and forgets its trail. */
    public fun jumpBeside(npc: Npc, owner: Player, trail: Trail) {
        npc.abortRoute()
        npc.telejump(collision, tileBeside(owner, npc.size))
        trail.clear()
    }

    /**
     * A free tile next to [owner] for a follower of [size]: west, east, south or north, in that
     * order. It is [owner]'s own tile only if all four are blocked.
     */
    public fun tileBeside(owner: Player, size: Int = 1): CoordGrid {
        val at = owner.coords
        val beside =
            CARDINALS.firstOrNull { (dx, dz) ->
                validator.canTravel(
                    level = at.level,
                    x = at.x,
                    z = at.z,
                    offsetX = dx,
                    offsetZ = dz,
                    size = size,
                    extraFlag = CollisionFlag.BLOCK_NPCS,
                )
            }
        return beside?.let { (dx, dz) -> at.translate(dx, dz) } ?: at
    }

    /**
     * One tick of [npc] walking up to melee [target]. It heads for a tile directly beside one of
     * the target's sides (melee does not reach diagonally) and stops there. Its trail is forgotten:
     * once the fight is over, [walk] brings it straight back to its owner.
     */
    public fun approach(npc: Npc, target: Npc, trail: Trail) {
        claim(npc)
        trail.clear()
        if (inMeleeReach(npc, target)) {
            return
        }
        walkPath(npc, pathBeside(npc, target))
    }

    /**
     * Whether [npc] stands directly beside one of [target]'s sides, on the same level: where a
     * melee attack reaches (not diagonally, and not from underneath).
     */
    public fun inMeleeReach(npc: Npc, target: Npc): Boolean {
        val a = npc.coords
        val b = target.coords
        if (a.level != b.level) {
            return false
        }
        val overlapX = a.x < b.x + target.size && b.x < a.x + npc.size
        val overlapZ = a.z < b.z + target.size && b.z < a.z + npc.size
        val touchX = a.x + npc.size == b.x || b.x + target.size == a.x
        val touchZ = a.z + npc.size == b.z || b.z + target.size == a.z
        return (overlapX && touchZ) || (overlapZ && touchX)
    }

    /** Adds the tiles [owner] walked off this tick to [trail]. */
    private fun record(owner: Player, trail: Trail) {
        val tiles = trail.tiles
        val from = owner.previousCoords
        val to = owner.coords
        if (from != to) {
            if (from.level != to.level || from.chebyshevDistance(to) > 2) {
                // A teleport or a ladder: there is no trail to walk.
                tiles.clear()
            } else {
                tiles.addTile(from)
                if (owner.pendingStepCount == 2 && owner.lastProcessedCoord != from) {
                    tiles.addTile(owner.lastProcessedCoord)
                }
            }
        }
        // The owner walked back over their own trail: what lies past them is no longer behind.
        val back = tiles.indexOf(to)
        if (back >= 0) {
            while (tiles.size > back) {
                tiles.removeLast()
            }
        }
        while (tiles.size > MAX_TRAIL) {
            tiles.removeFirst()
        }
    }

    /** Adds [tile] to the end of the trail, cutting out any loop back to it. */
    private fun ArrayDeque<CoordGrid>.addTile(tile: CoordGrid) {
        val seen = indexOf(tile)
        if (seen >= 0) {
            while (size > seen + 1) {
                removeLast()
            }
            return
        }
        addLast(tile)
    }

    /** One tick of the walk: along the trail, or up to the owner when there is none. */
    private fun step(npc: Npc, owner: Player, trail: Trail, ahead: Int) {
        val tiles = trail.tiles
        val at = owner.coords
        val pos = npc.coords

        // Drop the part of the trail the npc has already walked, and join it as far along as the
        // npc can reach in one step.
        val on = tiles.lastIndexOf(pos)
        val passed = if (on >= 0) on + 1 else tiles.indexOfLast { it.chebyshevDistance(pos) <= 1 }
        repeat(passed.coerceAtLeast(0)) { tiles.removeFirst() }

        // The last [ahead] tiles of the trail are for the followers in front of it in the line.
        val own = tiles.toList().dropLast(ahead)
        val path: List<CoordGrid> =
            when {
                own.isNotEmpty() -> own
                // On its owner's tile (they stepped onto it, or every tile around them was
                // blocked when it spawned): step off onto a free tile beside them, if there is one.
                pos == at -> listOf(tileBeside(owner, npc.size).takeIf { it != at } ?: return)
                // Its place in the line is already reached.
                tiles.isNotEmpty() -> return
                // Already beside its owner, or as close as its place in the line allows.
                pos.chebyshevDistance(at) <= 1 + ahead -> return
                // Behind with no trail (blocked, or the trail ran out): straight at them.
                else -> pathBeside(npc, owner)
            }
        walkPath(npc, path)
    }

    /** Walks one step along [path], or runs two when it is longer than that. */
    private fun walkPath(npc: Npc, path: List<CoordGrid>) {
        val first = path.firstOrNull() ?: return
        if (path.size == 1 && first == npc.coords) {
            return
        }
        val stepCount = if (path.size >= 2 || npc.coords.chebyshevDistance(first) > 1) 2 else 1
        move(npc, path, stepCount)
    }

    /**
     * The waypoints from [npc] to a tile directly beside one of [target]'s sides, found with the
     * route finder (round walls and fences, like a player walking up to it). Without a route it is
     * the side tile [npc] reaches first walking straight at [target].
     */
    private fun pathBeside(npc: Npc, target: PathingEntity): List<CoordGrid> {
        val level = npc.coords.level
        val route = routes.create(npc.avatar, target.avatar)
        if (route.success && route.waypoints.isNotEmpty()) {
            return route.waypoints.map { CoordGrid(it.x, it.z, level) }
        }
        return listOf(naiveBeside(npc, target))
    }

    /** The side tile of [target] that [npc] would reach first, walking straight at it. */
    private fun naiveBeside(npc: Npc, target: PathingEntity): CoordGrid {
        val dest =
            RouteFinding.naiveDestination(
                sourceX = npc.coords.x,
                sourceZ = npc.coords.z,
                sourceWidth = npc.size,
                sourceLength = npc.size,
                targetX = target.coords.x,
                targetZ = target.coords.z,
                targetWidth = target.size,
                targetLength = target.size,
            )
        return CoordGrid(dest.x, dest.z, npc.coords.level)
    }

    /**
     * Walks (one step) or runs (two) [npc] along [path] through the engine's npc movement, so the
     * client gets a normal walk or run. The map clock has already ticked by `LateCycle`, so the
     * move is stamped on the npc's own cycle. Otherwise the engine would count it again next tick
     * and reset the npc's mode and face target.
     */
    private fun move(npc: Npc, path: List<CoordGrid>, stepCount: Int) {
        // Something else moved it this tick already (a client can only be sent one walk or run).
        if (npc.isFrozen || npc.isDelayed || npc.coords != npc.previousCoords) {
            return
        }
        npc.routeRequest = null
        npc.routeDestination.clear()
        npc.routeDestination.addAll(path)
        npc.moveSpeed = if (stepCount >= 2) MoveSpeed.Run else MoveSpeed.Walk
        val before = npc.coords
        movement.process(npc)
        npc.routeDestination.clear()
        if (npc.coords != before) {
            npc.lastMovement = npc.processedMapClock
        }
    }

    private companion object {
        /** The most trail tiles kept. Past any leash the follower jumps anyway. */
        const val MAX_TRAIL = 32

        val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
