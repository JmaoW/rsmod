package org.rsmod.content.areas.misc.isleofsouls.kalphite

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.dungeons.GodWarsZamorakScript
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The Kalphite Queen's chamber in ordinary map coordinates, under the Kalphite Lair. The instance
 * is a copy of the block of 8x8 zones x 3464-3511, z 9480-9519 (all levels, so the lair above is
 * copied too). The floor of the chamber is x 3466-3508, z 9482-9517 on level 0; players climb down
 * the rope at its east end. Read from the cache with `tools/dungeons/collision.py`.
 */
object KalphiteArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 433, copyZoneZ = 1185) {
                zoneWidth = 6
                zoneLength = 5
            }
        }

    /** Where players appear: beside the rope, at the east end of the chamber. */
    val start = CoordGrid(3507, 9494, 0)

    /** Where players leave to: on the lair level, beside the hole they climbed down (tsps). */
    val exit = CoordGrid(3508, 9494, 2)

    /** She is 5x5. This is the south-west tile of the spot she stands on, where she spawned. */
    const val SIZE = 5
    val queenSpot = CoordGrid(3474, 9496, 0)

    /** The open floor of the chamber. */
    val floorX = 3466..3508
    val floorZ = 9482..9517

    /**
     * The 13 cocoons round the chamber. [spawn] is the south-west tile of the 2x2 a worker hatches
     * on (two tiles out from the cocoon, tsps); every one of them is clear floor.
     */
    class Cocoon(val loc: CoordGrid, val spawn: CoordGrid)

    val cocoons =
        listOf(
            cocoon(3502, 9501, -2, 0),
            cocoon(3502, 9504, -2, 0),
            cocoon(3482, 9502, -2, 0),
            cocoon(3486, 9498, 0, -2),
            cocoon(3486, 9518, 0, -2),
            cocoon(3488, 9518, 0, -2),
            cocoon(3464, 9495, 2, 0),
            cocoon(3472, 9508, 2, 0),
            cocoon(3477, 9481, 0, 2),
            cocoon(3480, 9481, 0, 2),
            cocoon(3486, 9506, 0, 2),
            cocoon(3490, 9502, 2, 0),
            cocoon(3493, 9483, 0, 2),
        )

    private fun cocoon(x: Int, z: Int, dx: Int, dz: Int) =
        Cocoon(CoordGrid(x, z, 0), CoordGrid(x + dx, z + dz, 0))
}

/** The queen's chamber as a kind of team instance. Registered with [TeamInstances]. */
object KalphiteInstance {
    const val NAME = "kalphite_queen"

    val type =
        TeamInstanceType(
            name = NAME,
            template = KalphiteArena.template,
            entry = KalphiteArena.start,
            exit = KalphiteArena.exit,
        )
}

enum class KalphiteStep {
    /** Fighting, or waiting for someone to come close. */
    Fighting,

    /** The first form has fallen and she is splitting open; nothing can touch her. */
    Molting,
}

/** What the director remembers about one Kalphite Queen. */
class KalphiteFight(val boss: Npc) {
    var step = KalphiteStep.Fighting

    /** Somebody came close or hurt her, so she is now after the players. */
    var engaged = false

    /** The tick of her next attack. */
    var attackAt = 0

    var target: Player? = null

    /** The tick the second form rose, or [KalphiteQueen.NOT_AIRBORNE] while she is in the first. */
    var airborneSince = KalphiteQueen.NOT_AIRBORNE

    /** Set when she is dying, so the run no longer owns her. */
    var dead = false

    var walkDest: CoordGrid? = null
}

/**
 * One team's fight with the Kalphite Queen. It lives as long as its [instance] has someone in it.
 * The hatched workers are the run's `monsters`; the queen is [fight].
 */
class KalphiteRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var fight: KalphiteFight? = null

    /** Who got the kill, the dead queen and where the drops fall, set when she dies. */
    var hero: Player? = null
    var killed: Npc? = null
    var dropAt: CoordGrid? = null

    override fun allNpcs(): List<Npc> {
        val boss = fight?.takeIf { !it.dead }?.boss
        return monsters.toList() + listOfNotNull(boss)
    }

    override fun clearNpcs() {
        super.clearNpcs()
        fight = null
    }
}

/**
 * Runs the fight with the Kalphite Queen (OSRS Wiki, "Kalphite Queen" and its Strategies page; tsps
 * `KalphiteQueen.plugin.js` for the ids and timings). The wave loop (enter, leave, the run tick,
 * clean-up) is [WaveDirector]; this adds the fight:
 * - She has two forms of 255 hitpoints each, as one npc. When the crawling form falls she splits
 *   open for [MOLT_TICKS] ticks (see [beginMolt] and [endMolt]) and rises again as the airborne
 *   form with full hitpoints. If the airborne form lives for [AIRBORNE_TIMEOUT] ticks she goes back
 *   to the crawling form with the hitpoints she has left.
 * - She walks up to her target and attacks every [ATTACK_RATE] ticks, hitting for up to [MAX_HIT].
 *   Next to the target a turn is melee 1 in 2, ranged 1 in 4 and magic 1 in 4; from further away,
 *   ranged or magic. Ranged and magic never miss (but protection prayers work). A player standing
 *   under her is out of reach, so she steps off.
 * - Her ranged spine hurts anyone close to its target and takes a prayer point from each hit that
 *   rolled damage; her magic glow bounces from player to player.
 * - Each hit on her has a 1 in [WORKER_ODDS] chance of hatching a worker from a cocoon near her.
 */
class KalphiteQueen
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
    private val worldRepo: WorldRepository,
    private val attacks: NpcAttacks,
    private val objTypes: ObjTypeList,
) : WaveDirector<KalphiteRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = KalphiteInstance.type

    override fun createRun(instance: TeamInstance): KalphiteRun =
        KalphiteRun(instance, mapClock.cycle)

    override fun waveMessage(run: KalphiteRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(kalphite_timers.tick, 1)
        player.mes("You climb down the rope into the Kalphite Queen's chamber.")
    }

    override fun spawnWave(run: KalphiteRun, inside: List<Player>) {
        val boss = spawn(run, kalphite_npcs.queen, KalphiteArena.queenSpot)
        val fight = KalphiteFight(boss)
        fight.attackAt = mapClock.cycle
        run.fight = fight
    }

    override fun engage(run: KalphiteRun, npc: Npc, target: Player): Boolean {
        if (npc.type.id != kalphite_npcs.worker.id) {
            return false
        }
        engage(npc, target, ranged = false)
        return true
    }

    override fun onWaveTick(run: KalphiteRun) {
        run.monsters.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        val fight = run.fight ?: return
        if (fight.dead || fight.step == KalphiteStep.Molting) {
            return
        }
        act(run, fight, mapClock.cycle)
    }

    override fun onWin(run: KalphiteRun, inside: List<Player>) {
        // She comes back in the same chamber after a while (the wiki's respawn is 50 ticks), for
        // whoever is still in it: each kill counts, and the next starts with the next spawn.
        run.state = WaveState.WaitingForNextWave
        run.nextWaveAt = mapClock.cycle + npcRepo.respawnSpeed.scale(RESPAWN_TICKS)
    }

    // The queen's life ------------------------------------------------------------------------

    /** Whether [npc] is showing her airborne form. */
    fun isAirborne(npc: Npc): Boolean = npc.visType.id == kalphite_npcs.airborne.id

    /**
     * The airborne form has fallen (called by the death script, which then spawns her drops: that
     * publishes the kill).
     */
    fun bossDied(npc: Npc) {
        val run = runOf(npc) ?: return
        val fight = run.fight ?: return
        if (fight.boss !== npc || fight.dead) {
            return
        }
        fight.dead = true
        run.hero = npc.findHero(players)
        run.killed = npc
        run.dropAt = npc.coords.translate(KalphiteArena.SIZE / 2, KalphiteArena.SIZE / 2)
        win(run)
    }

    /**
     * The crawling form has fallen. Returns `false` if [npc] is not the queen of a run (nothing
     * else spawns her, but a stray one dies like any npc).
     */
    fun beginMolt(npc: Npc): Boolean {
        val fight = runOf(npc)?.fight ?: return false
        if (fight.boss !== npc || fight.dead) {
            return false
        }
        fight.step = KalphiteStep.Molting
        fight.target = null
        fight.walkDest = null
        return true
    }

    /**
     * She is shown as the airborne form from the start of the molt, since the emerge animation
     * belongs to that model. Her hitpoints stay at 0 and she stays untargetable until [endMolt].
     */
    fun showAirborne(npc: Npc) {
        val fight = runOf(npc)?.fight ?: return
        if (fight.boss !== npc || isAirborne(npc)) {
            return
        }
        // Stat drains stay: it is the same npc, only shown as another type.
        npc.transmog(npcTypes[kalphite_npcs.airborne], Int.MAX_VALUE)
    }

    /** The molt is over: the airborne form has full hitpoints and can be fought. */
    fun endMolt(npc: Npc) {
        val fight = runOf(npc)?.fight ?: return
        if (fight.boss !== npc) {
            return
        }
        showAirborne(npc)
        npc.hitpoints = npc.baseHitpointsLvl
        fight.airborneSince = mapClock.cycle
        fight.step = KalphiteStep.Fighting
        fight.attackAt = mapClock.cycle + MOLT_RECOVER_TICKS
    }

    /**
     * [npc] was hit (even for 0). She now goes after whoever hurt her if she had no target, and the
     * hit may hatch a worker.
     */
    fun hurt(npc: Npc, attacker: Player?) {
        val run = runOf(npc) ?: return
        val fight = run.fight ?: return
        if (fight.boss !== npc || fight.dead || fight.step == KalphiteStep.Molting) {
            return
        }
        fight.engaged = true
        if (attacker != null && fight.target == null && attacker.isValidTarget()) {
            fight.target = attacker
        }
        if (random.of(WORKER_ODDS) == 0) {
            hatchWorker(run, npc)
        }
    }

    /** A worker hatches from a cocoon within [COCOON_REACH] tiles of her, if there is one. */
    private fun hatchWorker(run: KalphiteRun, queen: Npc) {
        if (run.monsters.size >= MAX_WORKERS) {
            return
        }
        val here = run.instance.region.normal
        val near =
            KalphiteArena.cocoons.filter { distance(queen.coords, here[it.loc]) <= COCOON_REACH }
        if (near.isEmpty()) {
            return
        }
        val cocoon = near[random.of(near.size)]
        val worker = Npc(npcTypes[kalphite_npcs.worker], here[cocoon.spawn])
        worker.mode = NpcMode.None
        npcRepo.add(worker, WORKER_LIFETIME)
        run.monsters += worker
    }

    // The fight -------------------------------------------------------------------------------

    private fun act(run: KalphiteRun, fight: KalphiteFight, now: Int) {
        val boss = fight.boss
        if (
            isAirborne(boss) &&
                fight.airborneSince != NOT_AIRBORNE &&
                now - fight.airborneSince >= AIRBORNE_TIMEOUT
        ) {
            // The second form never lives for longer than this: back to the first, with the
            // hitpoints she has left.
            boss.resetTransmog()
            fight.airborneSince = NOT_AIRBORNE
        }

        val inside = instances.playersIn(run.instance).filter { it.isValidTarget() }
        if (inside.isEmpty()) {
            fight.target = null
            return
        }
        if (!fight.engaged) {
            if (inside.none { boss.distanceTo(it) <= AGGRO_RANGE }) {
                return
            }
            fight.engaged = true
            fight.attackAt = now
        }

        val target = pickTarget(fight, inside)
        fight.target = target
        boss.facePlayer(target)

        val gap = boss.distanceTo(target)
        if (gap != ADJACENT) {
            // Either out of reach or standing under her: take the nearest spot beside them.
            approach(fight, target)
        }
        if (now < fight.attackAt || gap == 0) {
            return
        }
        val adjacent = gap == ADJACENT
        if (!adjacent && gap > ATTACK_RANGE) {
            return
        }

        val style = pickStyle(adjacent)
        when (style) {
            NpcAttackStyle.Melee -> melee(fight, target)
            NpcAttackStyle.Ranged -> ranged(run, fight, target)
            NpcAttackStyle.Magic -> magic(run, fight, target)
        }
        fight.attackAt = now + ATTACK_RATE
    }

    /**
     * What she attacks with this turn (wiki): next to the target, melee 1 in 2, ranged 1 in 4 and
     * magic 1 in 4; from further away, ranged or magic.
     */
    fun pickStyle(adjacent: Boolean): NpcAttackStyle =
        if (adjacent) {
            when (random.of(ADJACENT_ODDS)) {
                0,
                1 -> NpcAttackStyle.Melee
                2 -> NpcAttackStyle.Ranged
                else -> NpcAttackStyle.Magic
            }
        } else if (random.of(2) == 0) {
            NpcAttackStyle.Ranged
        } else {
            NpcAttackStyle.Magic
        }

    /** Keeps her current target while they can still be fought, otherwise the nearest player. */
    private fun pickTarget(fight: KalphiteFight, inside: List<Player>): Player {
        val current = fight.target
        if (
            current != null && current in inside && fight.boss.distanceTo(current) <= TARGET_LEASH
        ) {
            return current
        }
        return inside.minBy { fight.boss.distanceTo(it) }
    }

    private fun animOf(fight: KalphiteFight, style: NpcAttackStyle): SeqType {
        val airborne = isAirborne(fight.boss)
        return when {
            airborne && style == NpcAttackStyle.Magic -> kalphite_seqs.airborne_magic
            airborne -> kalphite_seqs.airborne_ranged
            style == NpcAttackStyle.Melee -> kalphite_seqs.crawling_melee
            else -> kalphite_seqs.crawling_ranged
        }
    }

    fun melee(fight: KalphiteFight, target: Player) {
        val attack =
            NpcAttack(
                style = NpcAttackStyle.Melee,
                anim = animOf(fight, NpcAttackStyle.Melee),
                maxHit = MAX_HIT,
                meleeType = MeleeAttackType.Stab,
            )
        attacks.attack(fight.boss, target, attack)
    }

    /** A spine at the target, and one at everyone else who stands close to them. */
    fun ranged(run: KalphiteRun, fight: KalphiteFight, target: Player) {
        val boss = fight.boss
        val victims = LinkedHashSet<Player>()
        victims += target
        for (other in instances.playersIn(run.instance)) {
            if (other.isValidTarget() && distance(other.coords, target.coords) <= SPLASH_RADIUS) {
                victims += other
            }
        }
        for ((index, victim) in victims.withIndex()) {
            val delay = flight(boss, victim, kalphite_spotanims.spine)
            strike(
                fight,
                victim,
                NpcAttackStyle.Ranged,
                delay,
                impact = null,
                drains = true,
                first = index == 0,
            )
        }
    }

    /** A glow at the target that bounces to the nearest player close to where it landed. */
    fun magic(run: KalphiteRun, fight: KalphiteFight, target: Player) {
        val boss = fight.boss
        val others = instances.playersIn(run.instance).filter { it.isValidTarget() }
        var current = target
        var delay = flight(boss, target, kalphite_spotanims.magic_travel)
        strike(
            fight,
            target,
            NpcAttackStyle.Magic,
            delay,
            impact = kalphite_spotanims.magic_impact,
            drains = false,
            first = true,
        )
        val hit = HashSet<Player>()
        hit += target
        while (true) {
            val next =
                others
                    .filter { it !in hit && distance(it.coords, current.coords) <= BOUNCE_RADIUS }
                    .minByOrNull { distance(it.coords, current.coords) } ?: break
            delay += flight(current, next, kalphite_spotanims.magic_travel)
            strike(
                fight,
                next,
                NpcAttackStyle.Magic,
                delay,
                impact = kalphite_spotanims.magic_impact,
                drains = false,
                first = false,
            )
            hit += next
            current = next
        }
    }

    /** Sends the projectile and returns the ticks it takes to arrive. */
    private fun flight(from: Npc, to: Player, spot: SpotanimType): Int =
        maxOf(1, worldRepo.projAnim(from, to, spot, projanims.magic_spell).durations.serverDelay)

    private fun flight(from: Player, to: Player, spot: SpotanimType): Int =
        maxOf(1, worldRepo.projAnim(from, to, spot, projanims.magic_spell).durations.serverDelay)

    /**
     * One ranged or magic hit: it never misses, rolls 0 to [MAX_HIT], and lands [delay] ticks from
     * now. Protection prayers still stop it, but a ranged hit that rolled damage takes a prayer
     * point even then (wiki), which is why the roll is made here.
     */
    private fun strike(
        fight: KalphiteFight,
        victim: Player,
        style: NpcAttackStyle,
        delay: Int,
        impact: SpotanimType?,
        drains: Boolean,
        first: Boolean,
    ) {
        val damage = random.of(0..MAX_HIT)
        val attack =
            NpcAttack(
                style = style,
                anim = animOf(fight, style),
                castSpot =
                    if (style == NpcAttackStyle.Magic) kalphite_spotanims.magic_cast else null,
                impactSpot = impact,
                minHit = damage,
                maxHit = damage,
                hitDelay = delay,
                alwaysHits = true,
            )
        val onImpact =
            if (drains && damage > 0) NpcImpactListener { player, _ -> drainPrayer(player) }
            else null
        attacks.attack(fight.boss, victim, attack, playAnim = first, onImpact = onImpact)
    }

    /** One prayer point; the spectral spirit shield stops it half of the time (tsps). */
    private fun drainPrayer(player: Player) {
        val shield =
            player.worn.objs.any {
                it != null && objTypes[it].internalName == GodWarsZamorakScript.SPECTRAL_SHIELD
            }
        if (shield && random.of(2) == 0) {
            return
        }
        if (player.stat(stats.prayer) > 0) {
            player.statSub(stats.prayer, 1, 0)
        }
    }

    /** Takes a step towards [target]: to the nearest spot that puts her 5x5 beside them. */
    private fun approach(fight: KalphiteFight, target: Player) {
        val boss = fight.boss
        val here = boss.coords
        var best: CoordGrid? = null
        var bestAway = Int.MAX_VALUE
        val goal = Bounds(target.coords, 1, 1)
        for (dz in -KalphiteArena.SIZE..1) {
            for (dx in -KalphiteArena.SIZE..1) {
                val spot = CoordGrid(target.coords.x + dx, target.coords.z + dz, here.level)
                if (Bounds(spot, KalphiteArena.SIZE, KalphiteArena.SIZE).distanceTo(goal) != 1) {
                    continue
                }
                if (!standable(spot)) {
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
        if (fight.walkDest != dest || !boss.hasMovedPreviousCycle) {
            fight.walkDest = dest
            boss.walk(dest)
        }
    }

    /** Whether a 5x5 with its south-west tile on [spot] is on open floor. */
    private fun standable(spot: CoordGrid): Boolean {
        for (z in 0 until KalphiteArena.SIZE) {
            for (x in 0 until KalphiteArena.SIZE) {
                if ((collision[spot.x + x, spot.z + z, spot.level] and WALK_BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    private fun distance(a: CoordGrid, b: CoordGrid): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    companion object {
        /** [KalphiteFight.airborneSince] while she is in her first form. */
        const val NOT_AIRBORNE = Int.MIN_VALUE

        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** The wiki: attack speed 4, and every attack hits for up to 31. */
        const val ATTACK_RATE = 4
        const val MAX_HIT = 31

        /** The wiki: next to the target, melee 1 in 2, ranged 1 in 4, magic 1 in 4. */
        const val ADJACENT_ODDS = 4
        const val ADJACENT = 1

        /** She shoots from this far at most (a guess; the wiki gives none). */
        const val ATTACK_RANGE = 10

        /** How close a player must come before she notices them (a guess). */
        const val AGGRO_RANGE = 12

        /** She drops a target that is further away than this and takes the nearest instead. */
        const val TARGET_LEASH = 25

        /** The wiki: 20 game ticks for the transformation. */
        const val MOLT_TICKS = 10

        /** After rising she waits this long before her first attack. */
        const val MOLT_RECOVER_TICKS = 2

        /** The wiki: after 20 minutes in the second form she goes back to the first. */
        const val AIRBORNE_TIMEOUT = 2000

        /** How long after a kill she is back in the chamber (the wiki's respawn is 50 ticks). */
        const val RESPAWN_TICKS = 50

        /** Players this close to her ranged target are hit too (wiki: "close"; tsps: 1). */
        const val SPLASH_RADIUS = 1

        /** The magic glow reaches players this close to the last one it hit (a guess; tsps: 1). */
        const val BOUNCE_RADIUS = 2

        /** The wiki (Mod Ash): 1 in 20 per hit, even a hit of 0, if she is near the cocoons. */
        const val WORKER_ODDS = 20
        const val COCOON_REACH = 10
        const val WORKER_LIFETIME = 100
        const val MAX_WORKERS = 12
    }
}
