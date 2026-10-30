package org.rsmod.content.areas.misc.isleofsouls.wilderness

import kotlin.math.abs
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.projanims
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid

/**
 * A sticky web on the floor: the tiles that hurt, the scenery drawn over it and the tick it is
 * gone.
 */
class StickyWeb(val tiles: Set<CoordGrid>, val until: Int, val locs: List<LocInfo>)

/** What Venenatis and Spindel remember: where they are in the 16-attack cycle, and their webs. */
class VenenatisFight(npc: Npc, lair: WildLair) : BossFight(npc, lair) {
    /** Attacks made so far; the cycle is 8 ranged, then 8 magic. */
    var attacks = 0

    val spiderlings = ArrayList<Npc>()
    val webs = ArrayList<StickyWeb>()
    var busyUntil = 0

    override fun reset(now: Int) {
        super.reset(now)
        attacks = 0
        spiderlings.clear()
        webs.clear()
        busyUntil = 0
    }
}

/**
 * Venenatis and Spindel, her weaker version (OSRS Wiki, "Venenatis" and "Spindel" and their
 * Strategies pages). She fights from where she stands: a target next to her gets the melee lunge,
 * anyone else gets her ranged spit or her magic web. The cycle is 8 ranged attacks then 8 magic
 * ones, and every 4 attacks she shuffles to another place in the lair. She calls two spiderlings on
 * the first ranged attack of the cycle and spits a sticky web on the third magic attack. The web
 * covers 7x5 tiles and hurts, drains prayer and run energy of everyone in it.
 *
 * Her spiderlings drain a prayer point with every attack, raise her max hit and let her hit through
 * protection prayers. Guesses where the wiki is silent: +10% max hit and 20% less protection for
 * each living spiderling, how long a web lasts (12 ticks) and how often it hurts (every tick).
 */
class VenenatisLogic(d: WildBosses) : WildLogic(d) {
    override fun create(npc: Npc, lair: WildLair): BossFight = VenenatisFight(npc, lair)

    override fun clear(fight: BossFight) {
        fight as VenenatisFight
        for (spiderling in fight.spiderlings) {
            if (spiderling.isSlotAssigned) {
                d.npcRepo.del(spiderling, Int.MAX_VALUE)
            }
        }
        fight.spiderlings.clear()
        removeWebs(fight)
    }

    override fun act(fight: BossFight, inside: List<Player>, now: Int) {
        fight as VenenatisFight
        val npc = fight.npc
        fight.spiderlings.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        tickWebs(fight, inside, now)
        aimSpiderlings(fight, inside)
        if (now < fight.busyUntil) {
            return
        }
        if (!fight.engaged) {
            fight.engaged = true
            fight.attackAt = maxOf(fight.attackAt, now + FIRST_ATTACK_DELAY)
        }

        val target = d.pickTarget(fight, inside)
        fight.target = target
        npc.facePlayer(target)
        if (now < fight.attackAt) {
            return
        }

        val singles = !fight.lair.multi
        val alive = fight.spiderlings.size
        val position = fight.attacks % CYCLE
        val adjacent = npc.distanceTo(target) == 1
        when {
            adjacent -> melee(fight, target, singles, alive)
            position < RANGED_TURNS -> {
                if (position == 0) {
                    summon(fight, target, now)
                }
                ranged(fight, target, singles, alive)
            }
            else -> {
                if (position == WEB_TURN) {
                    web(fight, target, now)
                }
                magic(fight, target, singles, alive)
            }
        }
        fight.attacks++
        fight.attackAt = now + ATTACK_RATE
        if (fight.attacks % MOVE_EVERY == 0) {
            shuffle(fight, now)
        }
    }

    // The attacks -----------------------------------------------------------------------------

    private fun maxHit(base: Int, alive: Int): Int = base + base * minOf(alive, 5) / 10

    /** How much of the damage a protection prayer takes off while spiderlings live. */
    private fun reduction(alive: Int): Int = maxOf(0, WildHits.FULL - PIERCE_PER_SPIDERLING * alive)

    private fun melee(fight: VenenatisFight, target: Player, singles: Boolean, alive: Int) {
        fight.npc.anim(wild_seqs.venenatis_melee)
        val max = maxHit(if (singles) SPINDEL_MELEE else VENENATIS_MELEE, alive)
        d.hits.strike(
            fight.npc,
            target,
            NpcAttackStyle.Melee,
            0,
            max,
            delay = 1,
            reduction = reduction(alive),
            retaliate = true,
        )
    }

    private fun ranged(fight: VenenatisFight, target: Player, singles: Boolean, alive: Int) {
        val npc = fight.npc
        npc.anim(wild_seqs.venenatis_ranged)
        val max = maxHit(if (singles) SPINDEL_RANGED else VENENATIS_RANGED, alive)
        val flight = flightTicks(npc, target, wild_spotanims.venenatis_ranged_projectile)
        d.hits.strike(
            npc,
            target,
            NpcAttackStyle.Ranged,
            0,
            max,
            delay = flight,
            reduction = reduction(alive),
            retaliate = true,
        )
        target.spotanim(wild_spotanims.venenatis_ranged_impact, delay = flight * CYCLES_PER_TICK)
    }

    private fun magic(fight: VenenatisFight, target: Player, singles: Boolean, alive: Int) {
        val npc = fight.npc
        npc.anim(wild_seqs.venenatis_magic)
        val max = maxHit(if (singles) SPINDEL_MAGIC else VENENATIS_MAGIC, alive)
        val flight = flightTicks(npc, target, wild_spotanims.venenatis_magic_projectile)
        d.hits.strike(
            npc,
            target,
            NpcAttackStyle.Magic,
            0,
            max,
            delay = flight,
            reduction = reduction(alive),
            retaliate = true,
        )
        target.spotanim(wild_spotanims.venenatis_magic_impact, delay = flight * CYCLES_PER_TICK)
    }

    // The spiderlings -------------------------------------------------------------------------

    private fun summon(fight: VenenatisFight, target: Player, now: Int) {
        val type =
            if (fight.lair.multi) wild_npcs.venenatis_spiderling else wild_npcs.spindel_spiderling
        repeat(SPIDERLINGS_PER_SUMMON) {
            if (fight.spiderlings.size >= MAX_SPIDERLINGS) {
                return
            }
            val spot = freeNear(fight, target.coords) ?: return
            val spiderling = Npc(d.npcTypes[type], spot)
            spiderling.mode = NpcMode.None
            d.npcRepo.add(spiderling, WildBosses.LIFETIME)
            fight.spiderlings += spiderling
        }
    }

    /** The first open tile around [around] (closest first) that nothing else stands on. */
    private fun freeNear(fight: VenenatisFight, around: CoordGrid): CoordGrid? {
        val taken = fight.spiderlings.map { it.coords }.toSet()
        val npc = fight.npc
        for (radius in 1..4) {
            val ring = ArrayList<CoordGrid>()
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != radius) {
                        continue
                    }
                    ring += CoordGrid(around.x + dx, around.z + dz, around.level)
                }
            }
            ring.shuffle(java.util.Random(d.random.of(Int.MAX_VALUE).toLong()))
            val spot =
                ring.firstOrNull {
                    it in fight.lair.arena && d.walkable(it) && it !in taken && !d.onBody(npc, it)
                }
            if (spot != null) {
                return spot
            }
        }
        return null
    }

    /** Spiderlings chase the nearest player and, on each bite, take a prayer point. */
    private fun aimSpiderlings(fight: VenenatisFight, inside: List<Player>) {
        for (spiderling in fight.spiderlings) {
            val nearest = inside.minBy { it.distanceTo(spiderling) }
            d.attackTarget(spiderling, nearest)
        }
    }

    // The web ---------------------------------------------------------------------------------

    /** A web 7 wide and 5 tall on the target's tile, with a thin fringe round it. */
    private fun web(fight: VenenatisFight, target: Player, now: Int) {
        val npc = fight.npc
        val flight = flightTicks(npc, target, wild_spotanims.venenatis_web_projectile)
        val centre = target.coords
        fight.later(now + flight) {
            removeWebs(fight)
            val tiles = HashSet<CoordGrid>()
            val locs = ArrayList<LocInfo>()
            for (dz in -(WEB_HALF_LENGTH + 1)..(WEB_HALF_LENGTH + 1)) {
                for (dx in -(WEB_HALF_WIDTH + 1)..(WEB_HALF_WIDTH + 1)) {
                    val piece = piece(dx, dz) ?: continue
                    val tile = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                    if (tile !in fight.lair.arena || !d.walkable(tile)) {
                        continue
                    }
                    if (abs(dx) <= WEB_HALF_WIDTH && abs(dz) <= WEB_HALF_LENGTH) {
                        tiles += tile
                    }
                    val type = d.locTypes[piece.type]
                    locs += d.locRepo.add(tile, type, Int.MAX_VALUE, piece.angle, SHAPE)
                }
            }
            fight.webs += StickyWeb(tiles, d.now + WEB_LIFE, locs)
        }
    }

    /**
     * Which scenery piece goes on the tile ([dx], [dz]) from the middle of the web, if any. The 7x5
     * block is lattice with a rounded piece in each corner; round it runs a one-tile fringe of edge
     * pieces. The cache models join the lattice with their west side (a corner also with its north
     * side) and are ragged on the others, so at angle 0 an edge is the east fringe and a corner the
     * south-east one; the rest are turned from there.
     */
    private fun piece(dx: Int, dz: Int): WebPiece? {
        val west = dx == -WEB_HALF_WIDTH
        val east = dx == WEB_HALF_WIDTH
        val south = dz == -WEB_HALF_LENGTH
        val north = dz == WEB_HALF_LENGTH
        val inside = abs(dx) <= WEB_HALF_WIDTH && abs(dz) <= WEB_HALF_LENGTH
        if (inside) {
            return when {
                south && east -> WebPiece(wild_locs.web_corner, LocAngle.West)
                south && west -> WebPiece(wild_locs.web_corner, LocAngle.North)
                north && west -> WebPiece(wild_locs.web_corner, LocAngle.East)
                north && east -> WebPiece(wild_locs.web_corner, LocAngle.South)
                else -> WebPiece(wild_locs.web_centre, LocAngle.West)
            }
        }
        val farWest = dx == -WEB_HALF_WIDTH - 1
        val farEast = dx == WEB_HALF_WIDTH + 1
        val farSouth = dz == -WEB_HALF_LENGTH - 1
        val farNorth = dz == WEB_HALF_LENGTH + 1
        return when {
            (farWest || farEast) && (farSouth || farNorth) -> null
            farEast -> WebPiece(wild_locs.web_edge, LocAngle.West)
            farSouth -> WebPiece(wild_locs.web_edge, LocAngle.North)
            farWest -> WebPiece(wild_locs.web_edge, LocAngle.East)
            else -> WebPiece(wild_locs.web_edge, LocAngle.South)
        }
    }

    private class WebPiece(val type: LocType, val angle: LocAngle)

    private fun removeWebs(fight: VenenatisFight) {
        for (web in fight.webs) {
            for (loc in web.locs) {
                d.locRepo.del(loc, Int.MAX_VALUE)
            }
        }
        fight.webs.clear()
    }

    /** Drops the old webs and hurts everyone caught in one. */
    private fun tickWebs(fight: VenenatisFight, inside: List<Player>, now: Int) {
        if (fight.webs.isEmpty()) {
            return
        }
        val old = fight.webs.filter { it.until <= now }
        for (web in old) {
            for (loc in web.locs) {
                d.locRepo.del(loc, Int.MAX_VALUE)
            }
        }
        fight.webs.removeAll(old.toSet())
        for (victim in inside) {
            if (fight.webs.any { victim.coords in it.tiles }) {
                d.hits.typeless(fight.npc, victim, WEB_DAMAGE, 1)
                d.hits.drainPrayer(victim, WEB_PRAYER_DRAIN)
                d.hits.drainRun(victim, WEB_RUN_DRAIN)
            }
        }
    }

    // Moving ----------------------------------------------------------------------------------

    /** Every 4 attacks she goes to another place in the lair. */
    private fun shuffle(fight: VenenatisFight, now: Int) {
        val npc = fight.npc
        if (npc.isFrozen) {
            return
        }
        val size = npc.size
        val here = npc.coords
        val arena = fight.lair.arena
        repeat(SHUFFLE_TRIES) {
            val spot =
                CoordGrid(
                    arena.minX + d.random.of(arena.maxX - arena.minX - size + 2),
                    arena.minZ + d.random.of(arena.maxZ - arena.minZ - size + 2),
                    arena.level,
                )
            if (d.chebyshev(spot, here) >= SHUFFLE_MIN_DISTANCE && d.standable(spot, size)) {
                npc.walk(spot)
                val travel = d.chebyshev(spot, here) / 2 + 1
                fight.busyUntil = now + travel
                fight.attackAt = maxOf(fight.attackAt, now + travel + 1)
                return
            }
        }
    }

    // Helpers ---------------------------------------------------------------------------------

    private fun flightTicks(from: Npc, to: Player, spot: SpotanimType): Int =
        maxOf(1, d.worldRepo.projAnim(from, to, spot, projanims.magic_spell).durations.serverDelay)

    private companion object {
        const val ATTACK_RATE = 4
        const val FIRST_ATTACK_DELAY = 3
        const val CYCLES_PER_TICK = 30

        /** The wiki: 8 ranged attacks, then 8 magic; the web on the third magic one. */
        const val CYCLE = 16
        const val RANGED_TURNS = 8
        const val MOVE_EVERY = 4
        const val WEB_TURN = 10

        /** The wiki's max hits. */
        const val VENENATIS_MELEE = 21
        const val VENENATIS_RANGED = 35
        const val VENENATIS_MAGIC = 30
        const val SPINDEL_MELEE = 14
        const val SPINDEL_RANGED = 31
        const val SPINDEL_MAGIC = 24

        const val PIERCE_PER_SPIDERLING = 20
        const val SPIDERLINGS_PER_SUMMON = 2
        const val MAX_SPIDERLINGS = 6

        /** The wiki: a 7x5 area, 3 damage, 3 prayer points and 10% run energy. */
        const val WEB_HALF_WIDTH = 3
        const val WEB_HALF_LENGTH = 2
        const val WEB_DAMAGE = 3
        const val WEB_PRAYER_DRAIN = 3
        const val WEB_RUN_DRAIN = 1000
        const val WEB_LIFE = 12

        const val SHUFFLE_TRIES = 12
        const val SHUFFLE_MIN_DISTANCE = 4
        val SHAPE = LocShape.CentrepieceStraight
    }
}
