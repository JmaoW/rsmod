package org.rsmod.content.areas.misc.isleofsouls.scurrius

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
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
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Scurrius's lair in ordinary map coordinates, in the Varrock Sewers. The instance is a copy of the
 * block of 8x8 zones x 3272-3319, z 9856-9887 (the lair is x 3287-3309, z 9857-9878, in map square
 * 51_154; the broken bars lie on its west side at x 3282). The room itself is the open floor of x
 * 3291-3306 by z 9860-9875; his three food piles are in the middle of the north, south and east
 * walls. Read from the cache with `tools/dungeons/census.py`.
 */
object ScurriusArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 409, copyZoneZ = 1232) {
                zoneWidth = 6
                zoneLength = 4
            }
        }

    /** Where players appear: just inside the lair, on the far side of the sewage water. */
    val start = CoordGrid(3291, 9867, 0)

    /** Where players leave to: in the sewers, in front of the broken bars. */
    val exit = CoordGrid(3281, 9867, 0)

    /** Scurrius is 3x3. These are the south-west tiles of the spots he stands on. */
    const val SIZE = 3

    /** He starts on the east side of the room. */
    val east = CoordGrid(3303, 9866, 0)

    /** The middle of the room, where he goes for the last third of the fight. */
    val middle = CoordGrid(3297, 9866, 0)

    /** The open floor of the room. The tiles of it that can be walked on are the floor. */
    val floorX = 3291..3306
    val floorZ = 9860..9875

    val piles =
        listOf(
            FoodPile(bossSpot = CoordGrid(3298, 9860, 0), pile = CoordGrid(3298, 9858, 0)),
            FoodPile(bossSpot = CoordGrid(3298, 9873, 0), pile = CoordGrid(3298, 9876, 0)),
            FoodPile(bossSpot = CoordGrid(3304, 9866, 0), pile = CoordGrid(3307, 9867, 0)),
        )
}

/**
 * A food pile (2x2, a ring of food locs): [bossSpot] is the south-west tile of Scurrius when he
 * stands beside it to eat, [pile] the pile's own south-west tile.
 */
class FoodPile(val bossSpot: CoordGrid, val pile: CoordGrid)

/**
 * Scurrius's lair as a kind of team instance. Registered with [TeamInstances] by `ScurriusScript`.
 */
object ScurriusInstance {
    const val NAME = "scurrius"

    val type =
        TeamInstanceType(
            name = NAME,
            template = ScurriusArena.template,
            entry = ScurriusArena.start,
            exit = ScurriusArena.exit,
        )
}

enum class ScurriusStep {
    /** He has just appeared and is not yet fighting. */
    Spawning,

    /** Fighting: melee in the first phase, anything but melee while eating or once enraged. */
    Fighting,

    /** Walking to a food pile at 80%. He does nothing else. */
    ToPile,

    /** Eating at the pile: bites and heals, and fighting from there. */
    Eating,

    /** Walking to the middle at 30%. He does nothing else. */
    ToCentre,
}

/** What he does with his turn. */
enum class ScurriusAction {
    Melee,
    Ranged,
    Magic,
    Bricks,
    Summon,
}

/**
 * A rockfall in the air: whoever stands on one of [tiles] (real coordinates) at [landAt] is hit.
 */
class FallingBricks(val landAt: Int, val tiles: Set<CoordGrid>)

/** What the director remembers about one Scurrius. */
class ScurriusFight(val boss: Npc) {
    var step = ScurriusStep.Spawning

    /** The tick the spawn animation is over. */
    var nextAt = 0

    /** The tick of his next attack. */
    var attackAt = 0

    var target: Player? = null

    /** He has already eaten (he eats once, when he first drops to 80%). */
    var fed = false

    /** He is below 30% and fights from the middle of the room. */
    var enraged = false

    /** Set when he is dying, so the run no longer owns him. */
    var dead = false

    /**
     * The tile he is walking to (a real coordinate) and the tick he is put there if he is stuck.
     */
    var walkDest: CoordGrid? = null
    var walkDeadline = 0

    /** How many ticks in a row he has stood still on a walk that is not finished. */
    var walkStalled = 0

    /** Where he stood when [walkStalled] was last counted. */
    var walkLast: CoordGrid? = null

    var pile: FoodPile? = null
    var bites = 0
    var nextBiteAt = 0

    var ratWaves = 0

    /** The tick he started trying to reach a player in melee, or `-1`. */
    var chaseSince = -1
    var bricksInARow = 0
    var lastAction: ScurriusAction? = null

    val bricks = ArrayList<FallingBricks>()
}

/**
 * One team's fight with Scurrius. It lives as long as its [instance] has someone in it. The giant
 * rats are the run's `monsters`; Scurrius is [fight].
 */
class ScurriusRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var fight: ScurriusFight? = null

    /** The walkable tiles of the room (real coordinates). */
    val floor = ArrayList<CoordGrid>()

    /** Who got the kill, the dead boss and where the drops fall, set when he dies. */
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
 * Runs the fight with Scurrius. The wave loop (enter, leave, the run tick, clean-up) is
 * [WaveDirector]; this adds the fight: three phases decided by his hitpoints (melee, then a meal at
 * a food pile at 80% and the rest of the fight with rats and rockfalls, then the middle of the room
 * below 30%).
 */
class Scurrius
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
    private val rewards: ScurriusRewards,
) : WaveDirector<ScurriusRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = ScurriusInstance.type

    override fun createRun(instance: TeamInstance): ScurriusRun {
        val run = ScurriusRun(instance, mapClock.cycle)
        for (z in ScurriusArena.floorZ) {
            for (x in ScurriusArena.floorX) {
                val real = instance.region.normal[CoordGrid(x, z, 0)]
                if ((collision[real.x, real.z, real.level] and WALK_BLOCKED) == 0) {
                    run.floor += real
                }
            }
        }
        return run
    }

    override fun waveMessage(run: ScurriusRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(scurrius_timers.tick, 1)
        player.mes("You climb through the broken bars into the rats' lair.")
    }

    override fun spawnWave(run: ScurriusRun, inside: List<Player>) {
        val boss = spawn(run, scurrius_npcs.boss, ScurriusArena.east)
        boss.anim(scurrius_seqs.spawn)
        val fight = ScurriusFight(boss)
        fight.nextAt = mapClock.cycle + SPAWN_TICKS
        run.fight = fight
    }

    override fun engage(run: ScurriusRun, npc: Npc, target: Player): Boolean {
        if (npc.type.id != scurrius_npcs.rat.id) {
            return false
        }
        engage(npc, target, ranged = false)
        return true
    }

    override fun onWaveTick(run: ScurriusRun) {
        val fight = run.fight ?: return
        val now = mapClock.cycle
        landBricks(run, fight, now)
        if (fight.dead) {
            return
        }
        step(run, fight, now)
    }

    override fun onWin(run: ScurriusRun, inside: List<Player>) {
        // He comes back in the same lair after a while (the wiki's respawn is 30 ticks), for
        // whoever is still in it: each kill counts, and the next starts with the next spawn.
        run.state = WaveState.WaitingForNextWave
        run.nextWaveAt = mapClock.cycle + npcRepo.respawnSpeed.scale(RESPAWN_TICKS)
        rewards.win(inside, run)
    }

    /**
     * Scurrius has died (called by the death script, which then spawns his drops: that publishes
     * the kill).
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
        run.dropAt = npc.coords.translate(SIZE_TO_CENTRE, SIZE_TO_CENTRE)
        win(run)
    }

    /** A giant rat has died. */
    fun ratDied(npc: Npc) {
        runOf(npc)?.monsters?.remove(npc)
    }

    /** Whether [npc] is Scurrius, eating at a pile (he takes a little less damage then). */
    fun isEating(npc: Npc): Boolean {
        val fight = runOf(npc)?.fight ?: return false
        return fight.boss === npc && fight.step == ScurriusStep.Eating
    }

    // The phases ------------------------------------------------------------------------------

    private fun step(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        when (fight.step) {
            ScurriusStep.Spawning ->
                if (now >= fight.nextAt) {
                    fight.step = ScurriusStep.Fighting
                    fight.attackAt = now
                }
            ScurriusStep.ToPile ->
                walking(fight, now) {
                    fight.step = ScurriusStep.Eating
                    fight.bites = 0
                    fight.nextBiteAt = now + BITE_TICKS
                    fight.attackAt = now + BITE_TICKS
                    fight.boss.faceSquare(checkNotNull(fight.pile).pile.real(run), 2, 2)
                }
            ScurriusStep.ToCentre ->
                walking(fight, now) {
                    fight.step = ScurriusStep.Fighting
                    fight.attackAt = now + 1
                }
            ScurriusStep.Fighting,
            ScurriusStep.Eating -> fighting(run, fight, now)
        }
    }

    private fun fighting(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        val boss = fight.boss
        val max = boss.baseHitpointsLvl
        if (!fight.enraged && boss.hitpoints * 100 <= max * ENRAGE_PERCENT) {
            beginEnrage(run, fight, now)
            return
        }
        if (
            fight.step == ScurriusStep.Fighting &&
                !fight.fed &&
                boss.hitpoints * 100 <= max * FEED_PERCENT
        ) {
            beginFeeding(run, fight, now)
            return
        }
        if (fight.step == ScurriusStep.Eating) {
            eat(run, fight, now)
        }
        attack(run, fight, now)
    }

    /** At 80% he leaves the fight and walks to a food pile (usually a near one). */
    private fun beginFeeding(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        fight.fed = true
        val boss = fight.boss
        val ranked = ScurriusArena.piles.sortedBy { distance(boss.coords, it.bossSpot.real(run)) }
        val pile =
            if (random.of(PILE_RANDOM_ODDS) == 0) ranked[random.of(ranked.size)] else ranked[0]
        fight.pile = pile
        fight.step = ScurriusStep.ToPile
        fight.target = null
        walkTo(run, fight, pile.bossSpot.real(run), now)
    }

    /** At 30% he roars and goes to the middle of the room. */
    private fun beginEnrage(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        fight.enraged = true
        fight.step = ScurriusStep.ToCentre
        fight.target = null
        fight.boss.anim(scurrius_seqs.enrage)
        walkTo(run, fight, ScurriusArena.middle.real(run), now)
    }

    private fun walkTo(run: ScurriusRun, fight: ScurriusFight, dest: CoordGrid, now: Int) {
        fight.walkDest = dest
        fight.walkDeadline = now + WALK_PATIENCE
        fight.walkStalled = 0
        fight.walkLast = fight.boss.coords
        fight.boss.resetFaceEntity()
        fight.boss.walk(dest)
    }

    /**
     * Waits for a walk to finish; one that takes too long, or that is held up (rats standing in his
     * way, when he is not yet set to walk through them), ends with him put on the spot.
     */
    private fun walking(fight: ScurriusFight, now: Int, arrived: () -> Unit) {
        val dest = fight.walkDest ?: return
        val boss = fight.boss
        if (boss.coords != dest) {
            fight.walkStalled = if (boss.coords != fight.walkLast) 0 else fight.walkStalled + 1
            fight.walkLast = boss.coords
            if (now < fight.walkDeadline && fight.walkStalled < WALK_STALL_TICKS) {
                return
            }
            boss.telejump(collision, dest)
        }
        fight.walkDest = null
        arrived()
    }

    /** A bite of the pile every few ticks; every second bite heals him (ten bites, five heals). */
    private fun eat(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        if (now < fight.nextBiteAt) {
            return
        }
        val boss = fight.boss
        boss.anim(if (fight.bites % 2 == 0) scurrius_seqs.eat_one else scurrius_seqs.eat_two)
        if (fight.bites % 2 == 1) {
            val inside = instances.playersIn(run.instance).count { it.isValidTarget() }
            val heal = HEAL_PER_PLAYER * maxOf(1, inside)
            boss.healHitpoints(heal)
        }
        fight.bites++
        fight.nextBiteAt = now + BITE_TICKS
        if (fight.bites >= BITES) {
            fight.step = ScurriusStep.Fighting
            fight.boss.resetFaceEntity()
        }
    }

    // The attacks -----------------------------------------------------------------------------

    private fun attack(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        if (now < fight.attackAt) {
            return
        }
        val boss = fight.boss
        val targets = instances.playersIn(run.instance).filter { it.isValidTarget() }
        if (targets.isEmpty()) {
            return
        }
        val target = targets.minBy { boss.distanceTo(it) }
        fight.target = target

        var action = pickAction(fight)
        if (action == ScurriusAction.Melee) {
            if (boss.distanceTo(target) > MELEE_REACH) {
                // He walks up to a player before he swipes at them, but not for ever: someone out
                // of reach gets shot at instead.
                if (fight.chaseSince < 0) {
                    fight.chaseSince = now
                }
                if (now - fight.chaseSince < MELEE_PATIENCE) {
                    approach(run, fight, target)
                    return
                }
                action = if (random.of(2) == 0) ScurriusAction.Ranged else ScurriusAction.Magic
            }
        }
        fight.chaseSince = -1
        boss.facePlayer(target)
        fight.lastAction = action
        fight.bricksInARow = if (action == ScurriusAction.Bricks) fight.bricksInARow + 1 else 0
        val eating = fight.step == ScurriusStep.Eating
        when (action) {
            ScurriusAction.Melee -> {
                val anim = if (random.of(2) == 0) scurrius_seqs.tail_one else scurrius_seqs.tail_two
                attacks.attack(boss, target, ScurriusAttacks.tail.copy(anim = anim))
            }
            ScurriusAction.Ranged -> {
                val anim = if (eating) scurrius_seqs.ranged_eating else scurrius_seqs.ranged
                attacks.attack(boss, target, ScurriusAttacks.fur.copy(anim = anim))
            }
            ScurriusAction.Magic -> {
                val anim = if (eating) scurrius_seqs.magic_eating else scurrius_seqs.magic
                attacks.attack(boss, target, ScurriusAttacks.bolt.copy(anim = anim))
            }
            ScurriusAction.Bricks -> fallingBricks(run, fight, target, now)
            ScurriusAction.Summon -> summonRats(run, fight, now)
        }
        fight.attackAt = now + if (fight.enraged) ENRAGED_RATE else ATTACK_RATE
    }

    /**
     * What he does next, by phase: melee (and the odd rockfall) in the first phase and between
     * meals; magic, ranged, rockfalls and rats while he eats; the same, faster, once enraged.
     */
    private fun pickAction(fight: ScurriusFight): ScurriusAction {
        if (fight.step != ScurriusStep.Eating && !fight.enraged) {
            return if (random.of(BRICKS_ODDS) == 0) ScurriusAction.Bricks else ScurriusAction.Melee
        }
        if (
            fight.enraged &&
                fight.lastAction == ScurriusAction.Bricks &&
                fight.bricksInARow < MAX_BRICKS_IN_A_ROW &&
                random.of(2) == 0
        ) {
            return ScurriusAction.Bricks
        }
        val shots = if (fight.enraged) ENRAGED_SHOT_WEIGHT else EATING_SHOT_WEIGHT
        val roll = random.of(shots * 2 + 2)
        return when {
            roll < shots -> ScurriusAction.Magic
            roll < shots * 2 -> ScurriusAction.Ranged
            roll == shots * 2 -> ScurriusAction.Bricks
            else -> ScurriusAction.Summon
        }
    }

    /** Takes a step towards [target]: to the nearest tile that puts his 3x3 beside them. */
    private fun approach(run: ScurriusRun, fight: ScurriusFight, target: Player) {
        val boss = fight.boss
        val here = boss.coords
        var best: CoordGrid? = null
        var bestDistance = Int.MAX_VALUE
        for (dz in -ScurriusArena.SIZE..1) {
            for (dx in -ScurriusArena.SIZE..1) {
                val spot =
                    CoordGrid(target.coords.x + dx, target.coords.z + dz, target.coords.level)
                if (!standable(spot) || overlaps(spot, target.coords)) {
                    continue
                }
                val away = distance(here, spot)
                if (away < bestDistance) {
                    best = spot
                    bestDistance = away
                }
            }
        }
        val dest = best ?: return
        if (fight.walkDest != dest || !boss.hasMovedPreviousCycle) {
            fight.walkDest = dest
            boss.walk(dest)
        }
    }

    /** Whether a 3x3 with its south-west tile on [spot] is on open floor. */
    private fun standable(spot: CoordGrid): Boolean {
        for (z in 0 until ScurriusArena.SIZE) {
            for (x in 0 until ScurriusArena.SIZE) {
                if ((collision[spot.x + x, spot.z + z, spot.level] and WALK_BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    private fun overlaps(spot: CoordGrid, tile: CoordGrid): Boolean =
        tile.x in spot.x until spot.x + ScurriusArena.SIZE &&
            tile.z in spot.z until spot.z + ScurriusArena.SIZE

    /**
     * He jumps and the ceiling comes down: a shadow on the ground at the player's tile and on a
     * scattering of others. It lands [BRICKS_DELAY] ticks later and hurts whoever stands on a
     * shadow.
     */
    fun fallingBricks(run: ScurriusRun, fight: ScurriusFight, target: Player, now: Int) {
        val boss = fight.boss
        boss.anim(
            if (fight.step == ScurriusStep.Eating) scurrius_seqs.stomp_eating
            else scurrius_seqs.stomp
        )
        val tiles = HashSet<CoordGrid>()
        tiles += target.coords
        val count = random.of(BRICK_TILES_MIN..BRICK_TILES_MAX)
        repeat(count) { tiles += run.floor[random.of(run.floor.size)] }
        for (tile in tiles) {
            worldRepo.spotanimMap(scurrius_spotanims.bricks, tile)
        }
        fight.bricks += FallingBricks(now + BRICKS_DELAY, tiles)
    }

    private fun landBricks(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        val due = fight.bricks.filter { now >= it.landAt }
        if (due.isEmpty()) {
            return
        }
        fight.bricks.removeAll(due.toSet())
        for (bricks in due) {
            for (player in instances.playersIn(run.instance)) {
                if (!player.isValidTarget() || player.coords !in bricks.tiles) {
                    continue
                }
                player.queueHit(1, HitType.Typeless, random.of(BRICKS_MIN_HIT..BRICKS_MAX_HIT))
            }
        }
    }

    /** He screeches and six giant rats come out around the room (up to [RAT_WAVE_LIMIT] times). */
    fun summonRats(run: ScurriusRun, fight: ScurriusFight, now: Int) {
        val boss = fight.boss
        boss.anim(
            if (fight.step == ScurriusStep.Eating) scurrius_seqs.summon_eating
            else scurrius_seqs.summon
        )
        if (fight.ratWaves >= RAT_WAVE_LIMIT) {
            return
        }
        fight.ratWaves++
        val size = npcTypes[scurrius_npcs.rat].size
        val taken = ArrayList<Pair<CoordGrid, Int>>()
        repeat(RATS_PER_WAVE) {
            // Kept clear of the room's edge so the search for a free spot stays inside the copy.
            val wanted =
                CoordGrid(random.of(RAT_SPOT_X), random.of(RAT_SPOT_Z), ScurriusArena.start.level)
            val spot = freeSpot(run, wanted, size, taken)
            taken += spot to size
            val rat = spawn(run, scurrius_npcs.rat, spot)
            rat.baseHitpointsLvl = RAT_HITPOINTS
            rat.hitpoints = RAT_HITPOINTS
            rat.actionDelay = now + RAT_WAKE_TICKS
            run.monsters += rat
        }
    }

    private fun CoordGrid.real(run: ScurriusRun): CoordGrid = run.instance.region.normal[this]

    private fun distance(a: CoordGrid, b: CoordGrid): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    companion object {
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** The wiki: his attack speed is 4 ticks, and in the last phase he is faster (tsps: 3). */
        const val ATTACK_RATE = 4
        const val ENRAGED_RATE = 3

        /** How long after a kill he is back in the lair (the wiki: his respawn is 30 ticks). */
        const val RESPAWN_TICKS = 30

        /** He can be hit as soon as he has risen, and starts fighting 4 ticks after. */
        const val SPAWN_TICKS = 4

        /** He eats at 80% of his hitpoints and goes to the middle at 30% (wiki). */
        const val FEED_PERCENT = 80
        const val ENRAGE_PERCENT = 30

        /** The nearest pile 2 times out of 3, otherwise any (the wiki: random, usually near). */
        const val PILE_RANDOM_ODDS = 3

        /** A walk that has not finished in this many ticks ends with him put on the spot. */
        const val WALK_PATIENCE = 30

        /** A walk where he has not moved for this many ticks in a row is cut short the same way. */
        const val WALK_STALL_TICKS = 3

        /**
         * Ten bites of the pile, one every 4 ticks, and five of them heal (wiki). Each heals him by
         * 5 for every player in the room (tsps: 5, wiki: "5 or 10 to each player"). A guess.
         */
        const val BITES = 10
        const val BITE_TICKS = 4
        const val HEAL_PER_PLAYER = 5

        /** The tail swipe reaches next to him; further than that he walks up first. */
        const val MELEE_REACH = 1
        const val MELEE_PATIENCE = 10

        /** One action in six is a rockfall in the first phase (tsps). */
        const val BRICKS_ODDS = 6

        /** Once eating or enraged: magic and ranged weights, then one rockfall and one summon. */
        const val EATING_SHOT_WEIGHT = 4
        const val ENRAGED_SHOT_WEIGHT = 5
        const val MAX_BRICKS_IN_A_ROW = 3

        /**
         * The wiki (solo): rockfalls do 15 to 22. The shadows are down for 3 ticks (tsps), but the
         * debris graphic plays out longer than that and the hit came 2 to 3 ticks before the rubble
         * reached the ground in the client, so the landing check is at 5 (the hit itself then lands
         * a tick later).
         */
        const val BRICKS_MIN_HIT = 15
        const val BRICKS_MAX_HIT = 22
        const val BRICKS_DELAY = 5
        const val BRICK_TILES_MIN = 14
        const val BRICK_TILES_MAX = 19

        /** Six rats a time, up to 12 times (wiki). They die in one hit. */
        const val RATS_PER_WAVE = 6
        const val RAT_WAVE_LIMIT = 12
        const val RAT_HITPOINTS = 1
        const val RAT_WAKE_TICKS = 3
        val RAT_SPOT_X = 3293..3304
        val RAT_SPOT_Z = 9863..9872

        /** His south-west tile plus this is the middle of his 3x3. */
        const val SIZE_TO_CENTRE = 1
    }
}

/** How Scurrius and his rats attack. The max hits are the wiki's, for the solo instance. */
object ScurriusAttacks {
    const val TAIL_MAX_HIT = 13
    const val BOLT_MAX_HIT = 8
    const val FUR_MAX_HIT = 7

    /** The wiki: a giant rat hits up to 4, and up to 1 through Protect from Melee. */
    const val RAT_MAX_HIT = 4
    const val RAT_PRAYER_MAX_HIT = 1
    const val RAT_RATE = 4

    val tail = NpcAttack(style = NpcAttackStyle.Melee, maxHit = TAIL_MAX_HIT)

    val fur =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            projectile = scurrius_spotanims.fur_ball,
            projanim = projanims.magic_spell,
            impactSpot = scurrius_spotanims.fur_impact,
            maxHit = FUR_MAX_HIT,
        )

    val bolt =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            projectile = scurrius_spotanims.bolt,
            projanim = projanims.magic_spell,
            impactSpot = scurrius_spotanims.bolt_impact,
            maxHit = BOLT_MAX_HIT,
        )

    /**
     * A giant rat's bite: very accurate (wiki), typeless so that [ratBite] can do what the prayer
     * does to it (a rat hits through Protect from Melee, for up to 1).
     */
    val rat =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            maxHit = RAT_MAX_HIT,
            alwaysHits = true,
            typeless = true,
        )

    /** The rat's bite lands: Protect from Melee lowers it to 0 or 1. */
    val ratBite = NpcImpactListener { target, hit ->
        if (target.vars[varbits.protect_from_melee] == 1) {
            hit.damage = minOf(hit.damage, RAT_PRAYER_MAX_HIT)
        }
    }
}
