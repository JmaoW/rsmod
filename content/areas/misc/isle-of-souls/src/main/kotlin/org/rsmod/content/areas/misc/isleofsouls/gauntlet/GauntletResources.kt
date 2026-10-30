package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.stat.StatType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The five kinds of resource node in the maze (wiki): deposits, phren roots, fishing spots, linum
 * tirinum and grym roots. None needs a level; gathering yields the [harvests] shown, one every two
 * ticks, and the node is then spent.
 */
enum class GauntletNode(
    val size: Int,
    val harvests: Int,
    val start: String?,
    val gained: String,
    val noTool: String?,
    val full: String,
    val spent: String?,
) {
    Deposit(
        size = 1,
        harvests = 3,
        start = "You swing your pick at the rock.",
        gained = "You manage to mine some ore.",
        noTool = "You need a pickaxe to mine this rock.",
        full = "Your inventory is too full to hold any more ore.",
        spent = "You mine the last of the salvageable ore from the rock.",
    ),
    Roots(
        size = 1,
        harvests = 3,
        start = "You swing your axe at the roots.",
        gained = "You get some bark.",
        noTool = "You need an axe to chop bark from these roots.",
        full = "Your inventory is too full to hold any more bark.",
        spent = "You chop away the last of the bark.",
    ),
    Fishing(
        size = 2,
        harvests = 4,
        start = "You start harpooning fish.",
        gained = "You manage to catch a fish.",
        noTool = "You need a harpoon to catch these fish.",
        full = "You can't carry any more fish.",
        spent = "The fish have all been caught.",
    ),
    Linum(
        size = 1,
        harvests = 3,
        start = null,
        gained = "You pick some fibre from the plant.",
        noTool = null,
        full = "Your inventory is too full to hold any more fibre.",
        spent = "You pick the last of the fibre.",
    ),
    Grym(
        size = 1,
        harvests = 1,
        start = null,
        gained = "You pick a herb from the roots.",
        noTool = null,
        full = "Your inventory is too full to hold any more herbs.",
        spent = null,
    );

    /** The node as it stands in the maze. */
    fun type(mode: GauntletMode): LocType =
        when (this) {
            Deposit -> if (mode.corrupted) gauntlet_locs.rock_hm else gauntlet_locs.rock
            Roots -> if (mode.corrupted) gauntlet_locs.tree_hm else gauntlet_locs.tree
            Fishing -> if (mode.corrupted) gauntlet_locs.pond_hm else gauntlet_locs.pond
            Linum -> if (mode.corrupted) gauntlet_locs.fibre_hm else gauntlet_locs.fibre
            Grym -> if (mode.corrupted) gauntlet_locs.herb_hm else gauntlet_locs.herb
        }

    /** What is left of the node once it is spent. */
    fun spentType(mode: GauntletMode): LocType =
        when (this) {
            Deposit -> if (mode.corrupted) gauntlet_locs.rock_spent_hm else gauntlet_locs.rock_spent
            Roots -> if (mode.corrupted) gauntlet_locs.tree_spent_hm else gauntlet_locs.tree_spent
            Fishing -> if (mode.corrupted) gauntlet_locs.pond_spent_hm else gauntlet_locs.pond_spent
            Linum -> if (mode.corrupted) gauntlet_locs.fibre_spent_hm else gauntlet_locs.fibre_spent
            Grym -> if (mode.corrupted) gauntlet_locs.herb_spent_hm else gauntlet_locs.herb_spent
        }

    /** What one harvest gives. */
    fun product(items: GauntletItemSet): ObjType =
        when (this) {
            Deposit -> items.ore
            Roots -> items.bark
            Fishing -> GauntletShared.rawFood
            Linum -> items.linum
            Grym -> items.grymLeaf
        }

    /** The tool a harvest needs, or `null` if it needs none. */
    fun tool(items: GauntletItemSet): ObjType? =
        when (this) {
            Deposit -> items.pickaxe
            Roots -> items.axe
            Fishing -> items.harpoon
            else -> null
        }

    /**
     * The skill that gets the 1 experience a harvest gives (Near-Reality's, the wiki gives none).
     */
    val stat: StatType
        get() =
            when (this) {
                Deposit -> stats.mining
                Roots -> stats.woodcutting
                Fishing -> stats.fishing
                Linum,
                Grym -> stats.farming
            }

    fun anim(mode: GauntletMode): SeqType =
        when (this) {
            Deposit -> if (mode.corrupted) gauntlet_seqs.mine_hm else gauntlet_seqs.mine
            Roots -> if (mode.corrupted) gauntlet_seqs.chop_hm else gauntlet_seqs.chop
            Fishing -> if (mode.corrupted) gauntlet_seqs.harpoon_hm else gauntlet_seqs.harpoon
            Linum,
            Grym -> gauntlet_seqs.pick
        }

    companion object {
        /** The kinds a room may hold besides grym roots. */
        val GATHERING = listOf(Deposit, Roots, Fishing, Linum)

        /**
         * Whether a node is spent after its [harvests]. Off, nodes give for as long as the player
         * keeps gathering (until the pack is full or they walk off). Switch it on for the wiki's
         * behaviour.
         */
        @JvmField var NODES_DEPLETE = false

        /** A harvest every two ticks. */
        const val YIELD_TICKS = 2

        /** One harvest in five also finds 5 to 30 shards (Near-Reality's). */
        const val SHARD_ODDS = 5
        const val SHARDS_MIN = 5
        const val SHARDS_MAX = 30
    }
}

/** A node that [GauntletResources.stock] put in a room. */
class PlacedNode(val kind: GauntletNode, val at: CoordGrid)

/**
 * Stocks the rooms of a maze with resources as they are lit. The wiki gives no placement; this is
 * Near-Reality's, as TSPS found it: three rooms in four get 1 to 3 gathering nodes (at most 2 when
 * monsters share the room), one in five also a few grym roots; nodes go on floor beside a wall or
 * another object, apart from each other, clear of the doorways and never in a way that cuts one
 * doorway off from another. A room with a demi-boss holds only that.
 *
 * Whether a tile is free is read from the run's collision, so a room is stocked from the floor the
 * map really has, turned or not.
 */
class GauntletResources
@Inject
constructor(
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val collision: CollisionFlagMap,
) {
    private class Spot(val x: Int, val z: Int)

    private class Spots(val single: MutableList<Spot>, val square: MutableList<Spot>)

    /**
     * Stocks [room] and marks whether monsters will share it. Does nothing for the start and boss
     * rooms or one that was stocked already. Returns the nodes put down.
     */
    fun stock(run: GauntletRun, room: GauntletRoom, pick: (Int) -> Int): List<PlacedNode> {
        if (room.stocked || room.kind == RoomKind.Start || room.kind == RoomKind.Boss) {
            return emptyList()
        }
        room.stocked = true
        if (room.demiBoss != null) {
            return emptyList()
        }
        val spots = spawnSpots(run, room)
        shuffle(spots.single, pick)
        shuffle(spots.square, pick)

        val placed = ArrayList<PlacedNode>()
        fun place(kind: GauntletNode) {
            val list = if (kind.size > 1) spots.square else spots.single
            val spot = list.firstOrNull { placeable(run, room, it, kind.size) } ?: return
            claim(spots, spot, kind.size)
            placed += PlacedNode(kind, add(run, room, kind, spot, pick))
        }

        val gathering = pick(100) >= 25
        room.monsters = !gathering || pick(100) >= 25
        if (gathering) {
            val count = between(pick, if (room.monsters) 1 else 2, 3)
            repeat(count) { place(GauntletNode.GATHERING[pick(GauntletNode.GATHERING.size)]) }
        }
        if (pick(100) < 20) {
            val most = if (pick(3) == 0) 3 else 2
            repeat(between(pick, 1, most)) { place(GauntletNode.Grym) }
        }
        return placed
    }

    private fun add(
        run: GauntletRun,
        room: GauntletRoom,
        kind: GauntletNode,
        spot: Spot,
        pick: (Int) -> Int,
    ): CoordGrid {
        val at = run.roomTile(room, spot.x, spot.z)
        locRepo.add(
            at,
            kind.type(run.mode),
            Int.MAX_VALUE,
            LocAngle[pick(4)],
            LocShape.CentrepieceStraight,
        )
        run.nodeLeft[at] = kind.harvests
        return at
    }

    // Where a node can go -----------------------------------------------------------------------

    private fun isFree(run: GauntletRun, room: GauntletRoom, x: Int, z: Int): Boolean {
        val at = run.roomTile(room, x, z)
        return collision[at.x, at.z, at.level] and BLOCKED == 0
    }

    /** Inner-floor tiles beside a wall or object, as single tiles and as 2x2 squares. */
    private fun spawnSpots(run: GauntletRun, room: GauntletRoom): Spots {
        val single = ArrayList<Spot>()
        val square = ArrayList<Spot>()
        for (x in INNER_MIN..INNER_MAX) {
            for (z in INNER_MIN..INNER_MAX) {
                if (inDoorway(x, z) || !isFree(run, room, x, z)) continue
                val nearWall = NEAR.any { (dx, dz) -> !isFree(run, room, x + dx, z + dz) }
                if (!nearWall) continue
                single += Spot(x, z)
                if (
                    x < INNER_MAX &&
                        z < INNER_MAX &&
                        isFree(run, room, x + 1, z) &&
                        isFree(run, room, x, z + 1) &&
                        isFree(run, room, x + 1, z + 1)
                ) {
                    square += Spot(x, z)
                }
            }
        }
        return Spots(single, square)
    }

    /** Takes a spot and everything too close to it: neighbours, and its row and column. */
    private fun claim(spots: Spots, spot: Spot, size: Int) {
        for (list in listOf(spots.single, spots.square)) {
            list.removeAll {
                Math.abs(it.x - spot.x) <= size && Math.abs(it.z - spot.z) <= size ||
                    it.x == spot.x ||
                    it.z == spot.z
            }
        }
    }

    private fun inDoorway(x: Int, z: Int): Boolean =
        (x == INNER_MIN || x == INNER_MAX) && z in DOORWAY_FROM..DOORWAY_TO ||
            (z == INNER_MIN || z == INNER_MAX) && x in DOORWAY_FROM..DOORWAY_TO

    /** The approach in front of each doorway, kept clear. */
    private fun inApproach(x: Int, z: Int): Boolean =
        z in DOORWAY_FROM..DOORWAY_TO && (x < INNER_MIN + APPROACH || x > INNER_MAX - APPROACH) ||
            x in DOORWAY_FROM..DOORWAY_TO && (z < INNER_MIN + APPROACH || z > INNER_MAX - APPROACH)

    /** A node's tiles, if it fits there without blocking a doorway or a way through the room. */
    private fun placeable(run: GauntletRun, room: GauntletRoom, spot: Spot, size: Int): Boolean {
        val taken = HashSet<Pair<Int, Int>>()
        for (dx in 0 until size) {
            for (dz in 0 until size) {
                if (inApproach(spot.x + dx, spot.z + dz)) return false
                taken += (spot.x + dx) to (spot.z + dz)
            }
        }
        return staysConnected(run, room, taken)
    }

    /**
     * Whether every doorway that leads on somewhere can still reach every other once [taken] is
     * built over. A node must never wall off a way through the room.
     */
    private fun staysConnected(
        run: GauntletRun,
        room: GauntletRoom,
        taken: Set<Pair<Int, Int>>,
    ): Boolean {
        fun open(x: Int, z: Int) =
            x in INNER_MIN..INNER_MAX &&
                z in INNER_MIN..INNER_MAX &&
                (x to z) !in taken &&
                isFree(run, room, x, z)
        val doorways = DOORWAY_TILES.filter { (x, z) -> open(x, z) && leadsOut(run, room, x, z) }
        if (doorways.size < 2) return true
        val seen = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        seen += doorways.first()
        queue += doorways.first()
        while (queue.isNotEmpty()) {
            val (x, z) = queue.removeFirst()
            for ((dx, dz) in STEPS) {
                val next = (x + dx) to (z + dz)
                if (next !in seen && open(next.first, next.second)) {
                    seen += next
                    queue += next
                }
            }
        }
        return doorways.all { it in seen }
    }

    /** Whether the tile beyond a doorway is floor (rim rooms have closed sides). */
    private fun leadsOut(run: GauntletRun, room: GauntletRoom, x: Int, z: Int): Boolean {
        val dx = if (x == INNER_MIN) -1 else if (x == INNER_MAX) 1 else 0
        val dz = if (z == INNER_MIN) -1 else if (z == INNER_MAX) 1 else 0
        return isFree(run, room, x + dx, z + dz)
    }

    private fun shuffle(list: MutableList<Spot>, pick: (Int) -> Int) {
        for (i in list.size - 1 downTo 1) {
            val j = pick(i + 1)
            val swap = list[i]
            list[i] = list[j]
            list[j] = swap
        }
    }

    private fun between(pick: (Int) -> Int, min: Int, max: Int): Int = min + pick(max - min + 1)

    /** The node kind a loc type is, or `null` if it is not a resource node. */
    fun kindOf(type: LocType): GauntletNode? =
        GauntletNode.entries.firstOrNull { node ->
            GauntletMode.entries.any { node.type(it).id == type.id }
        }

    private companion object {
        /** The floor inside a room's walls (room-relative tiles). */
        const val INNER_MIN = 2
        const val INNER_MAX = 13
        const val DOORWAY_FROM = 6
        const val DOORWAY_TO = 9
        const val APPROACH = 3

        const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        val NEAR = listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0, 0 to 2, 0 to -2, 2 to 0, -2 to 0)
        val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

        /** The inner floor tiles each doorway opens onto. */
        val DOORWAY_TILES =
            listOf(2 to 7, 2 to 8, 13 to 7, 13 to 8, 7 to 2, 8 to 2, 7 to 13, 8 to 13)
    }
}
