package org.rsmod.content.areas.misc.isleofsouls.wilderness

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid

/** One bear trap on the floor. */
class BearTrap(
    /** The tick it fades away. */
    var expires: Int,
    /** The scenery of the trap, once it has appeared. */
    var loc: LocInfo? = null,
    /** Only a trap that has appeared can be stepped on. */
    var armed: Boolean = false,
    var sprung: Boolean = false,
)

/** What Callisto and Artio remember: the traps on the floor and how far the roars have gone. */
class CallistoFight(npc: Npc, lair: WildLair) : BossFight(npc, lair) {
    /** Bear traps on the floor. */
    val traps = HashMap<CoordGrid, BearTrap>()

    /** 0 before the first roar, 1 after the roar at 66%, 2 after the roar at 33%. */
    var stage = 0

    /** While roaring (or recovering) it does nothing else. */
    var busyUntil = 0

    /** Attacks made so far, and the number of the attack that is the next orb (-1: not drawn). */
    var attacks = 0
    var orbAttack = -1

    /** The tick the next wave of traps goes down (from the first roar on). */
    var nextWaveAt = Int.MAX_VALUE

    /** After its death: the tick the sprung traps are taken away. */
    var clearAt = Int.MAX_VALUE

    override fun reset(now: Int) {
        super.reset(now)
        traps.clear()
        stage = 0
        busyUntil = 0
        attacks = 0
        orbAttack = -1
        nextWaveAt = Int.MAX_VALUE
        clearAt = Int.MAX_VALUE
        attackAt = now + CallistoLogic.FIRST_ATTACK_DELAY
    }
}

/**
 * Callisto and Artio, his weaker version (OSRS Wiki, "Callisto" and "Artio" and their Strategies
 * pages), with the timings, projectiles and traps of the rsprox captures that tsps documents. They
 * walk up to a target and swing: a melee hit that also hurts everyone close to the target and is
 * only halved by Protect from Melee. Out of reach they fire a crescent of ranged damage at everyone
 * in the lair. Every 8 to 12 attacks they throw a wind orb that knocks back whoever is not praying
 * Protect from Magic. At 66% and 33% of their health they roar; from the first roar on five bear
 * traps go down on the floor every six ticks.
 *
 * Captured: melee is followed by an attack six ticks later, ranged five, the orb eight, and the
 * first attack comes seven ticks after the spawn. The wiki's attack speeds (4 and 5) do not match.
 * Guesses: that a roar takes four ticks, and where a trap that cannot be placed is skipped.
 */
class CallistoLogic(d: WildBosses) : WildLogic(d) {
    override fun create(npc: Npc, lair: WildLair): BossFight = CallistoFight(npc, lair)

    override fun clear(fight: BossFight) {
        fight as CallistoFight
        for (trap in fight.traps.values) {
            removeLoc(trap)
        }
        fight.traps.clear()
        fight.nextWaveAt = Int.MAX_VALUE
        fight.clearAt = Int.MAX_VALUE
    }

    /** When the bear dies every armed trap springs, and is gone a tick later. */
    override fun died(fight: BossFight) {
        fight as CallistoFight
        fight.nextWaveAt = Int.MAX_VALUE
        for ((tile, trap) in fight.traps) {
            val loc = trap.loc ?: continue
            d.worldRepo.locAnim(loc, wild_seqs.trap_spring)
            d.worldRepo.soundArea(tile, wild_synths.callisto_trap_clear, radius = TRAP_SOUND_RADIUS)
        }
        fight.clearAt = d.now + 1
    }

    override fun deadTick(fight: BossFight, now: Int) {
        fight as CallistoFight
        if (now >= fight.clearAt) {
            clear(fight)
        }
    }

    override fun act(fight: BossFight, inside: List<Player>, now: Int) {
        fight as CallistoFight
        val npc = fight.npc
        tickTraps(fight, inside, now)
        if (now < fight.busyUntil) {
            return
        }
        if (!fight.engaged) {
            fight.engaged = true
            fight.attackAt = maxOf(fight.attackAt, now + ARRIVAL_DELAY)
        }
        if (checkRoar(fight, now)) {
            return
        }

        val target = d.pickTarget(fight, inside)
        fight.target = target
        npc.facePlayer(target)

        val gap = npc.distanceTo(target)
        if (gap != 1) {
            d.approach(fight, target)
        }
        if (now < fight.attackAt || gap == 0 || gap > ATTACK_RANGE) {
            return
        }

        val singles = !fight.lair.multi
        if (fight.orbAttack < 0) {
            fight.orbAttack = fight.attacks + d.random.of(ORB_MIN..ORB_MAX)
        }
        fight.attacks++
        val rate =
            when {
                fight.attacks >= fight.orbAttack -> {
                    fight.orbAttack = fight.attacks + d.random.of(ORB_MIN..ORB_MAX)
                    orb(fight, target, now)
                    ORB_RATE
                }
                gap == 1 -> {
                    melee(fight, target, inside, singles)
                    MELEE_RATE
                }
                else -> {
                    ranged(fight, inside, singles, now)
                    RANGED_RATE
                }
            }
        fight.attackAt = now + rate
    }

    // The attacks -----------------------------------------------------------------------------

    /** A swing at the target, and at everyone within a tile of them. It never misses (capture). */
    private fun melee(
        fight: CallistoFight,
        target: Player,
        inside: List<Player>,
        singles: Boolean,
    ) {
        val npc = fight.npc
        npc.anim(wild_seqs.callisto_melee)
        val min = if (singles) ARTIO_MIN_MELEE else CALLISTO_MIN_MELEE
        val max = if (singles) ARTIO_MAX_MELEE else CALLISTO_MAX_MELEE
        for (victim in inside) {
            if (victim !== target && d.chebyshev(victim.coords, target.coords) > MELEE_SPLASH) {
                continue
            }
            d.hits.strike(
                npc,
                victim,
                NpcAttackStyle.Melee,
                min,
                max,
                delay = 1,
                reduction = MELEE_PRAYER_REDUCTION,
                alwaysHits = true,
                retaliate = victim === target,
            )
        }
    }

    /**
     * The crescent that reaches everyone in the lair when nothing is in reach. The projectile flies
     * for 30 + 6 per tile client cycles (counted from the middle of the bear, plus one) and the
     * impact graphic and sound play as it arrives (capture).
     */
    private fun ranged(fight: CallistoFight, inside: List<Player>, singles: Boolean, now: Int) {
        val npc = fight.npc
        npc.anim(wild_seqs.callisto_ranged)
        npc.spotanim(wild_spotanims.callisto_ranged_cast)
        val max = if (singles) ARTIO_MAX_RANGED else CALLISTO_MAX_RANGED
        val middle = d.centre(npc)
        for (victim in inside) {
            val tiles = d.chebyshev(middle, victim.coords) + 1
            val end = RANGED_START + RANGED_PER_TILE * tiles
            val flight =
                shoot(
                    fight,
                    victim,
                    wild_spotanims.callisto_ranged_projectile,
                    RANGED_START,
                    end,
                    RANGED_START_HEIGHT,
                    RANGED_END_HEIGHT,
                )
            fight.later(now + flight) {
                victim.spotanim(wild_spotanims.callisto_ranged_impact)
                d.worldRepo.soundArea(
                    victim.coords,
                    wild_synths.callisto_ranged_impact,
                    radius = IMPACT_SOUND_RADIUS,
                )
            }
            d.hits.strike(
                npc,
                victim,
                NpcAttackStyle.Ranged,
                0,
                max,
                delay = flight,
                alwaysHits = true,
                retaliate = victim === inside.first(),
            )
        }
    }

    /**
     * The wind orb at the target. When it lands everyone within a tile of the target who is not
     * praying Protect from Magic is thrown [KNOCKBACK_TILES] tiles away from Callisto. They take 5
     * damage three ticks later, and 15 more for each tile a wall stopped them short (wiki). There
     * is no stun in the captures.
     */
    private fun orb(fight: CallistoFight, target: Player, now: Int) {
        val npc = fight.npc
        npc.anim(wild_seqs.callisto_magic)
        val tiles = d.chebyshev(d.centre(npc), target.coords)
        val end = ORB_START + ORB_PER_TILE * tiles
        shoot(fight, target, wild_spotanims.orb_projectile, ORB_START, end, ORB_HEIGHT, ORB_HEIGHT)
        val thrownAt = target.coords
        val ticks = maxOf(1, end / CYCLES_PER_TICK)
        fight.later(now + ticks) {
            val aim = if (target.isValidTarget()) target.coords else thrownAt
            for (victim in d.inside(fight.lair)) {
                if (d.chebyshev(victim.coords, aim) > ORB_SPLASH) {
                    continue
                }
                if (d.hits.isProtected(victim, NpcAttackStyle.Magic)) {
                    continue
                }
                knockBack(fight, victim)
            }
        }
    }

    private fun knockBack(fight: CallistoFight, victim: Player) {
        val npc = fight.npc
        val from = victim.coords
        val centre = d.centre(npc)
        val angle = atan2((from.z - centre.z).toDouble(), (from.x - centre.x).toDouble())
        val octant = (angle / (PI / 4)).roundToInt()
        val dx = cos(octant * PI / 4).roundToInt()
        val dz = sin(octant * PI / 4).roundToInt()
        var at = from
        var moved = 0
        for (step in 1..KNOCKBACK_TILES) {
            val next = CoordGrid(at.x + dx, at.z + dz, at.level)
            if (!canStep(at, next, dx, dz) || next !in fight.lair.arena) {
                break
            }
            at = next
            moved = step
            if (fight.traps[next]?.armed == true) {
                break
            }
        }
        val trapped = fight.traps[at]?.armed == true
        if (at != from) {
            PathingEntityCommon.exactMove(
                victim,
                from,
                at,
                0,
                KNOCKBACK_CLIENT_CYCLES,
                facing(dx, dz),
                d.collision,
            )
        }
        victim.anim(wild_seqs.player_knockback)
        d.worldRepo.soundArea(from, wild_synths.callisto_orb_hit_a, radius = IMPACT_SOUND_RADIUS)
        d.worldRepo.soundArea(from, wild_synths.callisto_orb_hit_b, radius = IMPACT_SOUND_RADIUS)
        // A trap that stops them deals only the 5 (and springs on them by itself).
        val damage =
            if (trapped) KNOCK_BASE else KNOCK_BASE + KNOCK_PER_TILE * (KNOCKBACK_TILES - moved)
        d.hits.typeless(npc, victim, damage, KNOCK_DAMAGE_DELAY)
    }

    /** Whether a step from [from] to [to] is open, without cutting a diagonal round a wall. */
    private fun canStep(from: CoordGrid, to: CoordGrid, dx: Int, dz: Int): Boolean {
        if (!d.walkable(to)) {
            return false
        }
        if (dx != 0 && dz != 0) {
            return d.walkable(CoordGrid(from.x + dx, from.z, from.level)) &&
                d.walkable(CoordGrid(from.x, from.z + dz, from.level))
        }
        return true
    }

    // The roars and the traps -----------------------------------------------------------------

    /** Roars at 66% and 33% of its health. Returns `true` on the tick it starts one. */
    private fun checkRoar(fight: CallistoFight, now: Int): Boolean {
        val npc = fight.npc
        val percent = npc.hitpoints * 100 / maxOf(1, npc.baseHitpointsLvl)
        val due =
            when {
                fight.stage == 0 && percent <= FIRST_ROAR -> 1
                fight.stage == 1 && percent <= SECOND_ROAR -> 2
                else -> return false
            }
        fight.stage = due
        npc.anim(wild_seqs.callisto_roar)
        npc.spotanim(
            if (fight.lair.multi) wild_spotanims.callisto_roar
            else wild_spotanims.callisto_roar_small
        )
        // The roar breaks any freeze.
        npc.frozenUntil = Int.MIN_VALUE
        d.freezeImmunity.clear(npc)
        fight.busyUntil = now + ROAR_TICKS
        fight.attackAt = now + ROAR_ATTACK_DELAY
        if (due == 1) {
            fight.nextWaveAt = now + 1
        }
        return true
    }

    /** Five open tiles around the bear, off its body, the players and the traps already down. */
    private fun layWave(fight: CallistoFight, inside: List<Player>, now: Int) {
        val npc = fight.npc
        val middle = d.centre(npc)
        val taken = HashSet<CoordGrid>(fight.traps.keys)
        inside.mapTo(taken) { it.coords }
        var placed = 0
        var tries = 0
        while (placed < WAVE_SIZE && tries++ < WAVE_TRIES) {
            val tile =
                CoordGrid(
                    middle.x + d.random.of(2 * TRAP_RADIUS + 1) - TRAP_RADIUS,
                    middle.z + d.random.of(2 * TRAP_RADIUS + 1) - TRAP_RADIUS,
                    middle.level,
                )
            if (
                tile in taken ||
                    tile !in fight.lair.arena ||
                    d.onBody(npc, tile) ||
                    !d.walkable(tile)
            ) {
                continue
            }
            taken += tile
            placed++
            val trap = BearTrap(expires = now + 1 + TRAP_LIFE)
            fight.traps[tile] = trap
            d.worldRepo.spotanimMap(wild_spotanims.callisto_trap, tile)
            d.worldRepo.soundArea(tile, wild_synths.callisto_trap_wave, radius = TRAP_SOUND_RADIUS)
            fight.later(now + 1) { arm(fight, tile, trap) }
        }
    }

    /** The trap scenery appears a tick after the graphic. */
    private fun arm(fight: CallistoFight, tile: CoordGrid, trap: BearTrap) {
        if (fight.traps[tile] !== trap || fight.dead) {
            return
        }
        trap.loc =
            d.locRepo.add(
                tile,
                d.locTypes[wild_locs.callisto_trap],
                Int.MAX_VALUE,
                LocAngle.West,
                LocShape.CentrepieceStraight,
            )
        trap.armed = true
    }

    private fun removeLoc(trap: BearTrap) {
        val loc = trap.loc ?: return
        d.locRepo.del(loc, Int.MAX_VALUE)
        trap.loc = null
    }

    /** Lays the waves, expires old traps and springs them on whoever stands on one. */
    private fun tickTraps(fight: CallistoFight, inside: List<Player>, now: Int) {
        if (fight.stage >= 1 && now >= fight.nextWaveAt) {
            fight.nextWaveAt = now + WAVE_EVERY
            layWave(fight, inside, now)
        }
        if (fight.traps.isEmpty()) {
            return
        }
        val iterator = fight.traps.entries.iterator()
        while (iterator.hasNext()) {
            val trap = iterator.next().value
            if (trap.expires <= now) {
                removeLoc(trap)
                iterator.remove()
            }
        }
        for (player in inside) {
            val trap = fight.traps[player.coords] ?: continue
            if (trap.armed && !trap.sprung) {
                springTrap(fight, player, trap, now)
            }
        }
    }

    /**
     * "The bear trap immobilizes you.": it snaps shut, holds the player for 3 ticks and hurts for
     * up to 15 as it goes a tick later (capture).
     */
    private fun springTrap(fight: CallistoFight, victim: Player, trap: BearTrap, now: Int) {
        trap.sprung = true
        victim.mes(TRAP_MESSAGE)
        trap.loc?.let { d.worldRepo.locAnim(it, wild_seqs.trap_spring) }
        d.worldRepo.soundArea(
            victim.coords,
            wild_synths.callisto_trap_spring,
            radius = IMPACT_SOUND_RADIUS,
        )
        victim.freeze(HOLD_TICKS)
        d.hits.typeless(fight.npc, victim, d.random.of(0..TRAP_MAX_DAMAGE), 1)
        fight.later(now + 1) {
            removeLoc(trap)
            fight.traps.values.remove(trap)
        }
    }

    // Helpers ---------------------------------------------------------------------------------

    /**
     * Sends a projectile from the middle of the bear to [target] with the captured shape and
     * returns the ticks it takes to arrive.
     */
    private fun shoot(
        fight: CallistoFight,
        target: Player,
        spot: SpotanimType,
        start: Int,
        end: Int,
        startHeight: Int,
        endHeight: Int,
    ): Int {
        val flight =
            ProjAnim(
                spotanim = spot.id,
                startHeight = startHeight,
                endHeight = endHeight,
                startTime = start,
                endTime = end,
                angle = PROJECTILE_ANGLE,
                progress = PROJECTILE_PROGRESS,
                sourceIndex = 0,
                targetIndex = -(target.slotId + 1),
                startCoord = d.centre(fight.npc),
                endCoord = target.coords,
            )
        d.worldRepo.projAnim(flight)
        return maxOf(1, flight.serverCycles)
    }

    /** The exact-move direction that faces along (dx, dz). */
    private fun facing(dx: Int, dz: Int): Int =
        when {
            dx > 0 && dz == 0 -> 1536
            dx < 0 && dz == 0 -> 512
            dz > 0 && dx == 0 -> 1024
            dz < 0 && dx == 0 -> 0
            dx > 0 && dz > 0 -> 1280
            dx > 0 -> 1792
            dz > 0 -> 768
            else -> 256
        }

    companion object {
        /** Captured: ticks from each attack to the next, and from the spawn to the first. */
        const val MELEE_RATE = 6
        const val RANGED_RATE = 5
        const val ORB_RATE = 8
        const val FIRST_ATTACK_DELAY = 7

        /** Time to get ready for a player who walks in on a bear that has long been idle. */
        const val ARRIVAL_DELAY = 3

        /** The wiki: melee 35 to 55 (Callisto) and 15 to 35 (Artio), ranged up to 31 and 17. */
        const val CALLISTO_MIN_MELEE = 35
        const val CALLISTO_MAX_MELEE = 55
        const val ARTIO_MIN_MELEE = 15
        const val ARTIO_MAX_MELEE = 35
        const val CALLISTO_MAX_RANGED = 31
        const val ARTIO_MAX_RANGED = 17

        /** The wiki: Protect from Melee reduces the melee damage by about half. */
        const val MELEE_PRAYER_REDUCTION = 50

        /** Players this close to the target are caught by the swing (wiki: "near"). */
        const val MELEE_SPLASH = 1
        const val ATTACK_RANGE = 15

        /** Captured: the crescent flies 30 + 6 per tile cycles at heights 0 to 40. */
        const val RANGED_START = 30
        const val RANGED_PER_TILE = 6
        const val RANGED_START_HEIGHT = 0
        const val RANGED_END_HEIGHT = 40

        /** Captured: the orb flies 60 + 12 per tile cycles at height 40, every 8 to 12 attacks. */
        const val ORB_START = 60
        const val ORB_PER_TILE = 12
        const val ORB_HEIGHT = 40
        const val ORB_MIN = 8
        const val ORB_MAX = 12
        const val ORB_SPLASH = 1
        const val CYCLES_PER_TICK = 30
        const val PROJECTILE_ANGLE = 10
        const val PROJECTILE_PROGRESS = 50

        /** The wiki: a knockback of 3 tiles, 5 damage plus 15 for each tile short of that. */
        const val KNOCKBACK_TILES = 3
        const val KNOCK_BASE = 5
        const val KNOCK_PER_TILE = 15
        const val KNOCK_DAMAGE_DELAY = 3
        const val KNOCKBACK_CLIENT_CYCLES = 30

        const val FIRST_ROAR = 66
        const val SECOND_ROAR = 33

        /** A roar (a guess) holds it for this long; captured: its next attack is 6 ticks after. */
        const val ROAR_TICKS = 4
        const val ROAR_ATTACK_DELAY = 6

        /** Captured: five traps every 6 ticks within 12 tiles, each lasting 30 ticks. */
        const val WAVE_SIZE = 5
        const val WAVE_EVERY = 6
        const val WAVE_TRIES = 200
        const val TRAP_RADIUS = 12
        const val TRAP_LIFE = 30

        /** Captured: a trap holds for 3 ticks and hurts for up to 15. */
        const val HOLD_TICKS = 3
        const val TRAP_MAX_DAMAGE = 15
        const val TRAP_MESSAGE = "<col=ef1020>The bear trap immobilizes you.</col>"

        const val TRAP_SOUND_RADIUS = 15
        const val IMPACT_SOUND_RADIUS = 5
    }
}
