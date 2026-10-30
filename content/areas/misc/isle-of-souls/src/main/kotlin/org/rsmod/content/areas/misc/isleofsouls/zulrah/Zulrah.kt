package org.rsmod.content.areas.misc.isleofsouls.zulrah

import jakarta.inject.Inject
import java.util.IdentityHashMap
import kotlin.math.abs
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.spotanims
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.api.repo.world.WorldRepository
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
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Zulrah's lair in ordinary map coordinates (the instance is a copy of the block of 8x8 zones round
 * the shrine, x 2240-2303 and z 3040-3103: the shrine is on the border of map squares 35_47 and
 * 35_48). The walkable part is a small U-shaped platform: two arms running north, joined at the
 * south, with a pond between them. Zulrah swims in the water around it.
 */
object ZulrahArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 280, copyZoneZ = 380) {
                zoneWidth = 8
                zoneLength = 8
            }
        }

    /** Where players appear: the south of the platform, in front of the exit scroll. */
    val start = CoordGrid(2268, 3069, 0)

    /** The Zul-andra teleport scroll that is the way out, just south of [start]. */
    val exitScroll = CoordGrid(2268, 3068, 0)

    /**
     * The sacrificial boat on the lobby plaza (3x3, south-west tile) and where players leave to.
     * [exit] stays three tiles east of [boat] and one north, the side the boat is boarded from.
     */
    val boat = CoordGrid(2089, 2937, 0)
    val exit = CoordGrid(2092, 2938, 0)

    /** The box that holds the platform: the tiles of it that can be walked on are the platform. */
    val platformX = 2262..2274
    val platformZ = 3068..3078

    /**
     * The south-west tile of her 5x5 body at each of her four spots. The wiki gives no tiles; these
     * are the Near-Reality server's (as ported by RSPSApp/tsps), which fit the platform: the centre
     * is in the pond, west and east just outside the arms, south just below the platform.
     */
    /**
     * The tiles beside the pillars that her tail never reaches (the wiki's safespot; the tiles are
     * the Near-Reality server's, as ported by tsps).
     */
    val tailSafe =
        listOf(
            CoordGrid(2263, 3072, 0),
            CoordGrid(2264, 3072, 0),
            CoordGrid(2272, 3072, 0),
            CoordGrid(2273, 3072, 0),
        )

    fun spot(spot: ZulrahSpot): CoordGrid =
        when (spot) {
            ZulrahSpot.Centre -> CoordGrid(2266, 3073, 0)
            ZulrahSpot.West -> CoordGrid(2257, 3071, 0)
            ZulrahSpot.East -> CoordGrid(2276, 3072, 0)
            ZulrahSpot.South -> CoordGrid(2266, 3062, 0)
        }
}

/** Zulrah's lair as a kind of team instance. Registered with [TeamInstances] by `ZulrahScript`. */
object ZulrahInstance {
    const val NAME = "zulrah"

    val type =
        TeamInstanceType(
            name = NAME,
            template = ZulrahArena.template,
            entry = ZulrahArena.start,
            exit = ZulrahArena.exit,
        )
}

/** A tile of the platform, as the ordinary map coordinate and as the real one in the instance. */
class PlatformTile(val normal: CoordGrid, val real: CoordGrid)

/** A venom cloud on the ground: [centre] is the middle of its 3x3, a real coordinate. */
class ZulrahCloud(val centre: CoordGrid, val expiresAt: Int, val loc: LocInfo?) {
    fun covers(coords: CoordGrid): Boolean =
        coords.level == centre.level &&
            abs(coords.x - centre.x) <= ZulrahCloud.REACH &&
            abs(coords.z - centre.z) <= ZulrahCloud.REACH

    companion object {
        /** The cloud is 3x3, so it reaches one tile from its middle. */
        const val REACH = 1
    }
}

/** A cloud orb in flight: the cloud appears when it lands. */
class PendingCloud(val landAt: Int, val centre: CoordGrid)

/** A snakeling orb in flight: the snakeling appears when it lands. */
class PendingSnakeling(val landAt: Int, val tile: CoordGrid, val magic: Boolean)

/**
 * One of the two tail swings of a melee action. She aims at the tile a player stands on at [animAt]
 * (that is when the swing animation starts) and at [hitAt] whoever is within a tile of [tile] is
 * hit. [left] is the side of her the tail comes round from. [actionStart] is the tick the melee
 * action began, which the second swing is timed from.
 */
class TailStrike(
    val actionStart: Int,
    val animAt: Int,
    val hitAt: Int,
    val first: Boolean,
    var tile: CoordGrid,
    var left: Boolean,
) {
    var swinging = false
}

enum class ZulrahStep {
    /** She has just appeared and is not yet fighting. */
    Spawning,

    /** Rising out of the water in a new form. */
    Surfacing,

    /** Doing the actions of her phase. */
    Acting,

    /** Sinking out of sight. */
    Diving,
}

/** What the director remembers about one Zulrah. */
class ZulrahFight(val zulrah: Npc, var rotation: ZulrahRotation) {
    var step = ZulrahStep.Spawning
    var nextAt = 0

    /** The index of the phase she is in, and of the next action of it. */
    var phaseIndex = 0
    var actionIndex = 0
    var phase: ZulrahPhase = rotation.phases.first()
    var form: ZulrahForm = ZulrahForm.Green

    /** How many rotations she has finished. */
    var rotationsDone = 0

    /** She is under the water between phases. She can be hit there too. */
    var submerged = true

    /** The player her attacks go to in this phase. */
    var target: Player? = null

    /** Set when she is dying, so the run no longer owns her. */
    var dead = false

    val pendingClouds = ArrayList<PendingCloud>()
    val pendingSnakelings = ArrayList<PendingSnakeling>()
    val tails = ArrayList<TailStrike>()
}

/**
 * One team's fight with Zulrah. It lives as long as its [instance] has someone in it. Snakelings
 * are the run's `monsters`; Zulrah is [fight].
 */
class ZulrahRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var fight: ZulrahFight? = null
    val clouds = ArrayList<ZulrahCloud>()

    /** The walkable tiles of the platform. */
    val platform = ArrayList<PlatformTile>()

    /** The tick each snakeling came up, so they can be sent away when their time is up. */
    val born = IdentityHashMap<Npc, Int>()

    /** The exit scroll, so the run can take it away again. */
    var exitLoc: LocInfo? = null

    override fun allNpcs(): List<Npc> {
        val boss = fight?.takeIf { !it.dead }?.zulrah
        return monsters.toList() + listOfNotNull(boss)
    }

    override fun clearNpcs() {
        super.clearNpcs()
        born.clear()
        fight = null
    }
}

/**
 * Runs the fight with Zulrah. The wave loop (enter, leave, the run tick, clean-up) is
 * [WaveDirector]; this adds the one thing particular to Zulrah: a rotation of phases she goes
 * through, diving and surfacing in a new form at a new spot, shooting, swinging her tail and
 * spitting out venom clouds and snakelings.
 */
class Zulrah
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
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val attacks: NpcAttacks,
    private val eventBus: EventBus,
    private val venom: PlayerVenom,
    private val rewards: ZulrahRewards,
) : WaveDirector<ZulrahRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = ZulrahInstance.type

    /** Lets a test replace the random choice of the first rotation. */
    var rotationPicker: (Int) -> Int = { random.of(it) }

    override fun createRun(instance: TeamInstance): ZulrahRun {
        val run = ZulrahRun(instance, mapClock.cycle)
        run.exitLoc =
            locRepo.add(
                instance.region.normal[ZulrahArena.exitScroll],
                locTypes[zulrah_locs.exit],
                duration = Int.MAX_VALUE,
                angle = LocAngle.West,
                shape = LocShape.CentrepieceStraight,
            )
        for (z in ZulrahArena.platformZ) {
            for (x in ZulrahArena.platformX) {
                val normal = CoordGrid(x, z, 0)
                val real = instance.region.normal[normal]
                if ((collision[real.x, real.z, real.level] and WALK_BLOCKED) == 0) {
                    run.platform += PlatformTile(normal, real)
                }
            }
        }
        return run
    }

    override fun onRunEnded(run: ZulrahRun) {
        clearClouds(run)
        run.exitLoc?.let { locRepo.del(it, Int.MAX_VALUE) }
        run.exitLoc = null
    }

    override fun waveMessage(run: ZulrahRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(zulrah_timers.tick, 1)
        player.mes("You board the boat and are carried to Zulrah's shrine.")
    }

    override fun spawnWave(run: ZulrahRun, inside: List<Player>) {
        val rotation = ZulrahRotations.all[rotationPicker(ZulrahRotations.all.size)]
        val boss = spawn(run, zulrah_npcs.green, ZulrahArena.spot(rotation.phases.first().spot))
        boss.anim(zulrah_seqs.spawn)
        val fight = ZulrahFight(boss, rotation)
        fight.nextAt = mapClock.cycle + SPAWN_TICKS
        run.fight = fight
    }

    override fun engage(run: ZulrahRun, npc: Npc, target: Player): Boolean {
        val magic = npc.type.id == zulrah_npcs.snakeling_magic.id
        val snakeling = magic || npc.type.id == zulrah_npcs.snakeling_melee.id
        if (!snakeling) {
            return false
        }
        engage(npc, target, ranged = magic)
        return true
    }

    override fun onWaveTick(run: ZulrahRun) {
        val fight = run.fight ?: return
        val now = mapClock.cycle
        expireSnakelings(run, now)
        if (fight.dead) {
            return
        }
        landThings(run, fight, now)
        cloudDamage(run, now)
        step(run, fight, now)
    }

    override fun onWin(run: ZulrahRun, inside: List<Player>) {
        clearClouds(run)
        // She comes back in the same lair after a while, for whoever is still in it. Each kill
        // counts, and the next one starts with the next wave's spawn.
        run.state = WaveState.WaitingForNextWave
        run.nextWaveAt = mapClock.cycle + npcRepo.respawnSpeed.scale(RESPAWN_TICKS)
        rewards.win(inside, run)
    }

    /**
     * Zulrah has died (called by the death script, which then spawns her drops: that publishes the
     * kill).
     */
    fun bossDied(npc: Npc) {
        val run = runOf(npc) ?: return
        val fight = run.fight ?: return
        if (fight.zulrah !== npc || fight.dead) {
            return
        }
        fight.dead = true
        fight.step = ZulrahStep.Spawning
        win(run)
    }

    /** A snakeling has died. */
    fun snakelingDied(npc: Npc) {
        eventBus.publish(NpcDeathEvents.Killed(npc, npc.findHero(players)))
        val run = runOf(npc) ?: return
        run.monsters.remove(npc)
        run.born.remove(npc)
    }

    /** The run [npc] is a snakeling of, if any. */
    fun isSnakeling(npc: Npc): Boolean =
        npc.type.id == zulrah_npcs.snakeling_melee.id ||
            npc.type.id == zulrah_npcs.snakeling_magic.id

    // The phases ------------------------------------------------------------------------------

    private fun step(run: ZulrahRun, fight: ZulrahFight, now: Int) {
        if (now < fight.nextAt) {
            return
        }
        when (fight.step) {
            ZulrahStep.Spawning -> beginActing(run, fight, now, FIRST_ACTION_DELAY)
            ZulrahStep.Surfacing -> beginActing(run, fight, now, 0)
            ZulrahStep.Acting -> act(run, fight, now)
            ZulrahStep.Diving -> surface(run, fight, now)
        }
    }

    /** She is up: she can be hurt again and her phase's actions begin. */
    private fun beginActing(run: ZulrahRun, fight: ZulrahFight, now: Int, firstActionDelay: Int) {
        fight.submerged = false
        fight.step = ZulrahStep.Acting
        fight.actionIndex = 0
        fight.target = pickTarget(run)
        fight.nextAt = now + firstActionDelay
    }

    private fun act(run: ZulrahRun, fight: ZulrahFight, now: Int) {
        val actions = fight.phase.actions
        if (fight.actionIndex >= actions.size) {
            // A tail swing still on its way lands first: she does not dive over it.
            if (fight.tails.isNotEmpty()) {
                fight.nextAt = now + 1
                return
            }
            dive(fight, now)
            return
        }
        val action = actions[fight.actionIndex++]
        val target = fight.target?.takeIf { it.isValidTarget() } ?: pickTarget(run)
        fight.target = target
        if (target == null) {
            // Nobody to shoot at: she waits.
            fight.actionIndex--
            fight.nextAt = now + ATTACK_RATE
            return
        }
        fight.nextAt = now + perform(run, fight, action, target, now)
    }

    private fun dive(fight: ZulrahFight, now: Int) {
        fight.step = ZulrahStep.Diving
        fight.submerged = true
        fight.zulrah.anim(zulrah_seqs.dive)
        fight.nextAt = now + DIVE_TICKS
    }

    /** She comes up in the next phase: a new form at a new spot. */
    private fun surface(run: ZulrahRun, fight: ZulrahFight, now: Int) {
        var index = fight.phaseIndex + 1
        if (index >= fight.rotation.phases.size) {
            // The closing phase counted as the first phase of the next rotation (which may be the
            // same one again), so that one carries on from its second.
            index = ZulrahRotations.FIRST_PHASE_AFTER_WRAP
            fight.rotationsDone++
            fight.rotation = ZulrahRotations.all[random.of(ZulrahRotations.all.size)]
        }
        fight.phaseIndex = index
        val phase = fight.rotation.phases[index]
        fight.phase = phase

        var form = phase.form
        if (fight.rotationsDone > 0 && random.of(VARIATION_ODDS) == 0) {
            // After the first rotation she sometimes shows a different form than expected (wiki).
            val others = ZulrahForm.entries.filter { it != form }
            form = others[random.of(others.size)]
        }
        fight.form = form

        val zulrah = fight.zulrah
        zulrah.transmog(npcTypes[zulrah_npcs.of(form)], Int.MAX_VALUE)
        val dest = run.instance.region.normal[ZulrahArena.spot(phase.spot)]
        zulrah.telejump(collision, dest)
        zulrah.anim(zulrah_seqs.surface)
        fight.step = ZulrahStep.Surfacing
        fight.nextAt = now + SURFACE_TICKS
    }

    private fun pickTarget(run: ZulrahRun): Player? {
        val targets = instances.playersIn(run.instance).filter { it.isValidTarget() }
        return if (targets.isEmpty()) null else targets[random.of(targets.size)]
    }

    // The actions -----------------------------------------------------------------------------

    /** Does [action]; returns the ticks until the next one. */
    private fun perform(
        run: ZulrahRun,
        fight: ZulrahFight,
        action: ZulrahAction,
        target: Player,
        now: Int,
    ): Int {
        // A form can only do what it can: a different form than the rotation expected (the
        // variation) swings or shoots as its own form does. Clouds and orbs are the same in every
        // form.
        val shot =
            action == ZulrahAction.Ranged ||
                action == ZulrahAction.Magic ||
                action == ZulrahAction.Mixed
        val effective =
            when {
                fight.form == ZulrahForm.Red && shot -> ZulrahAction.Melee
                fight.form != ZulrahForm.Red && action == ZulrahAction.Melee -> ZulrahAction.Mixed
                else -> action
            }
        val zulrah = fight.zulrah
        if (effective == ZulrahAction.Melee) {
            // She stares at the tile, not the player: she does not turn to follow them.
            zulrah.resetFaceEntity()
            zulrah.faceSquare(target.coords)
        } else {
            zulrah.facePlayer(target)
        }
        return when (effective) {
            ZulrahAction.Ranged -> shoot(zulrah, target, ZulrahAttacks.ranged)
            ZulrahAction.Magic -> shoot(zulrah, target, ZulrahAttacks.magic)
            ZulrahAction.Mixed -> {
                val attack = if (random.of(2) == 0) ZulrahAttacks.ranged else ZulrahAttacks.magic
                shoot(zulrah, target, attack)
            }
            ZulrahAction.Melee -> tailSwing(fight, target, now)
            is ZulrahAction.Cloud -> cloudBarrage(run, fight, effective, now)
            is ZulrahAction.Orb -> snakelingOrb(run, fight, effective, now)
        }
    }

    private fun shoot(zulrah: Npc, target: Player, attack: NpcAttack): Int {
        attacks.attack(zulrah, target, attack, onImpact = venomOnHit)
        return ATTACK_RATE
    }

    private val venomOnHit = NpcImpactListener { target, hit ->
        venom.hitMayEnvenom(target, hit.damage)
    }

    /**
     * She whips her tail at the tile the player stands on, twice: the swing starts as she aims and
     * lands [FIRST_STRIKE] ticks later; the second is aimed again [SECOND_AIM] ticks into the
     * action and lands at [SECOND_STRIKE]. Whoever is within a tile of the aimed tile when it lands
     * is hit, thrown back and stunned, so a player who moves away in time is not. The timings are
     * tsps's (Near-Reality).
     */
    private fun tailSwing(fight: ZulrahFight, target: Player, now: Int): Int {
        val strike =
            TailStrike(
                actionStart = now,
                animAt = now,
                hitAt = now + FIRST_STRIKE,
                first = true,
                tile = target.coords,
                left = tailIsLeft(fight.zulrah, target.coords),
            )
        startSwing(fight.zulrah, strike)
        fight.tails += strike
        return MELEE_RATE
    }

    private fun tailIsLeft(zulrah: Npc, tile: CoordGrid): Boolean =
        tile.x < zulrah.coords.x + ZULRAH_SIZE / 2

    /** She stares at the aimed tile and the swing animation begins. */
    private fun startSwing(zulrah: Npc, strike: TailStrike) {
        strike.swinging = true
        zulrah.resetFaceEntity()
        zulrah.faceSquare(strike.tile)
        zulrah.anim(if (strike.left) zulrah_seqs.tail_left else zulrah_seqs.tail_right)
    }

    /**
     * She spits two venom clouds at fixed sites of the platform (see [ZulrahCloudSites]): the ones
     * the rotation data gives for this barrage, wherever the players stand.
     */
    private fun cloudBarrage(
        run: ZulrahRun,
        fight: ZulrahFight,
        barrage: ZulrahAction.Cloud,
        now: Int,
    ): Int {
        val zulrah = fight.zulrah
        zulrah.anim(zulrah_seqs.barrage)
        for (site in barrage.centres) {
            val centre = run.instance.region.normal[site]
            val flight =
                worldRepo.projAnim(
                    zulrah,
                    centre,
                    zulrah_spotanims.cloud_shot,
                    projanims.magic_spell,
                )
            val delay = maxOf(1, flight.durations.serverDelay)
            fight.pendingClouds += PendingCloud(now + delay, centre)
        }
        return BARRAGE_RATE
    }

    /**
     * She spits an orb at the tile the rotation data gives; a snakeling comes out where it lands. A
     * tile that is not on the platform (the data has one on the north-east shore) is moved to the
     * nearest tile that is.
     */
    private fun snakelingOrb(
        run: ZulrahRun,
        fight: ZulrahFight,
        orb: ZulrahAction.Orb,
        now: Int,
    ): Int {
        val zulrah = fight.zulrah
        zulrah.anim(zulrah_seqs.shoot)
        val wanted = run.instance.region.normal[orb.tile]
        val tile = run.platform.map { it.real }.minByOrNull { chebyshev(it, wanted) } ?: wanted
        val flight =
            worldRepo.projAnim(zulrah, tile, zulrah_spotanims.snakeling_shot, projanims.magic_spell)
        val delay = maxOf(1, flight.durations.serverDelay) + 1
        val magic = random.of(SNAKELING_MAGIC_ODDS) == 0
        fight.pendingSnakelings += PendingSnakeling(now + delay, tile, magic)
        return ATTACK_RATE
    }

    // What lands ------------------------------------------------------------------------------

    /** Clouds, snakelings and tail swings whose time has come. */
    private fun landThings(run: ZulrahRun, fight: ZulrahFight, now: Int) {
        val clouds = fight.pendingClouds.filter { now >= it.landAt }
        fight.pendingClouds.removeAll(clouds.toSet())
        for (cloud in clouds) {
            placeCloud(run, cloud.centre, now)
        }
        val orbs = fight.pendingSnakelings.filter { now >= it.landAt }
        fight.pendingSnakelings.removeAll(orbs.toSet())
        for (orb in orbs) {
            spawnSnakeling(run, orb.tile, orb.magic, now)
        }
        for (tail in fight.tails) {
            if (!tail.swinging && now >= tail.animAt) {
                // The second swing is aimed now, at wherever the player has got to.
                val target = fight.target?.takeIf { it.isValidTarget() } ?: pickTarget(run)
                if (target != null) {
                    tail.tile = target.coords
                    tail.left = tailIsLeft(fight.zulrah, target.coords)
                }
                startSwing(fight.zulrah, tail)
            }
        }
        val tails = fight.tails.filter { now >= it.hitAt }
        fight.tails.removeAll(tails.toSet())
        for (tail in tails) {
            if (tail.first) {
                fight.tails +=
                    TailStrike(
                        actionStart = tail.actionStart,
                        animAt = tail.actionStart + SECOND_AIM,
                        hitAt = tail.actionStart + SECOND_STRIKE,
                        first = false,
                        tile = tail.tile,
                        left = tail.left,
                    )
            }
            whip(run, fight.zulrah, tail)
        }
    }

    /** The tail lands: whoever is near the aimed tile (and not beside a pillar) is hurt. */
    private fun whip(run: ZulrahRun, zulrah: Npc, tail: TailStrike) {
        val safe = ZulrahArena.tailSafe.map { run.instance.region.normal[it] }
        for (player in instances.playersIn(run.instance)) {
            if (!player.isValidTarget() || chebyshev(player.coords, tail.tile) > TAIL_REACH) {
                continue
            }
            if (player.coords in safe) {
                continue
            }
            val thrownTo = knockbackTile(run, zulrah, player)
            if (thrownTo != player.coords) {
                val dx = Integer.signum(zulrah.coords.x + ZULRAH_SIZE / 2 - player.coords.x)
                val dz = Integer.signum(zulrah.coords.z + ZULRAH_SIZE / 2 - player.coords.z)
                PathingEntityCommon.exactMove(
                    player,
                    player.coords,
                    thrownTo,
                    0,
                    KNOCKBACK_CLIENT_CYCLES,
                    facing(dx, dz),
                    collision,
                )
            }
            player.anim(zulrah_seqs.knockback)
            player.queueHit(1, HitType.Typeless, random.of(TAIL_MIN_HIT..TAIL_MAX_HIT))
            player.freeze(TAIL_STUN)
            player.spotanim(spotanims.stunned)
            venom.envenom(player)
        }
    }

    /**
     * Where [player] ends up when thrown straight away from the middle of her body: up to
     * [KNOCKBACK_TILES] tiles, stopping at the edge of the platform.
     */
    private fun knockbackTile(run: ZulrahRun, zulrah: Npc, player: Player): CoordGrid {
        val centreX = zulrah.coords.x + ZULRAH_SIZE / 2
        val centreZ = zulrah.coords.z + ZULRAH_SIZE / 2
        val dx = (player.coords.x - centreX).toDouble()
        val dz = (player.coords.z - centreZ).toDouble()
        val length = maxOf(1.0, kotlin.math.hypot(dx, dz))
        val standable = run.platform.map { it.real }.toSet()
        var at = player.coords
        for (step in 1..KNOCKBACK_TILES) {
            val next =
                CoordGrid(
                    kotlin.math.round(player.coords.x + dx / length * step).toInt(),
                    kotlin.math.round(player.coords.z + dz / length * step).toInt(),
                    player.coords.level,
                )
            if (next == at) {
                continue
            }
            if (next !in standable) {
                break
            }
            at = next
        }
        return at
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

    private fun placeCloud(run: ZulrahRun, centre: CoordGrid, now: Int) {
        // A second cloud on the same site is the same loc: it just lasts from now on.
        val existing = run.clouds.indexOfFirst { it.centre == centre }
        if (existing >= 0) {
            val old = run.clouds[existing]
            run.clouds[existing] = ZulrahCloud(centre, now + CLOUD_DURATION, old.loc)
            return
        }
        val southWest =
            CoordGrid(centre.x - ZulrahCloud.REACH, centre.z - ZulrahCloud.REACH, centre.level)
        val loc =
            locRepo.add(
                southWest,
                locTypes[zulrah_locs.cloud],
                duration = Int.MAX_VALUE,
                angle = LocAngle.West,
                shape = LocShape.CentrepieceStraight,
            )
        run.clouds += ZulrahCloud(centre, now + CLOUD_DURATION, loc)
    }

    private fun spawnSnakeling(run: ZulrahRun, tile: CoordGrid, magic: Boolean, now: Int) {
        val type = if (magic) zulrah_npcs.snakeling_magic else zulrah_npcs.snakeling_melee
        val npc = Npc(npcTypes[type], tile)
        npc.mode = NpcMode.None
        // The wiki says one hitpoint; the cache says 10, and a type's hitpoints cannot be edited.
        npc.baseHitpointsLvl = SNAKELING_HITPOINTS
        npc.hitpoints = SNAKELING_HITPOINTS
        npcRepo.add(npc, LIFETIME)
        npc.anim(zulrah_seqs.snakeling_spawn)
        // It wakes a few ticks after it lands before it first attacks.
        npc.actionDelay = now + SNAKELING_WAKE_TICKS
        run.monsters += npc
        run.born[npc] = now
    }

    /** A snakeling lives 40 seconds (wiki) and is gone after. */
    private fun expireSnakelings(run: ZulrahRun, now: Int) {
        for (npc in run.monsters.toList()) {
            val born = run.born[npc] ?: continue
            if (now - born >= SNAKELING_LIFETIME) {
                run.monsters.remove(npc)
                run.born.remove(npc)
                if (npc.isSlotAssigned) {
                    npcRepo.del(npc, Int.MAX_VALUE)
                }
            }
        }
    }

    /** Players in a cloud take a little damage every tick (it does not envenom, wiki). */
    private fun cloudDamage(run: ZulrahRun, now: Int) {
        val expired = run.clouds.filter { now >= it.expiresAt }
        for (cloud in expired) {
            cloud.loc?.let { locRepo.del(it, Int.MAX_VALUE) }
        }
        run.clouds.removeAll(expired.toSet())
        if (run.clouds.isEmpty()) {
            return
        }
        for (player in instances.playersIn(run.instance)) {
            if (!player.isValidTarget() || run.clouds.none { it.covers(player.coords) }) {
                continue
            }
            player.queueHit(1, HitType.Typeless, random.of(CLOUD_MIN_HIT..CLOUD_MAX_HIT))
        }
    }

    private fun clearClouds(run: ZulrahRun) {
        for (cloud in run.clouds) {
            cloud.loc?.let { locRepo.del(it, Int.MAX_VALUE) }
        }
        run.clouds.clear()
    }

    private fun chebyshev(a: CoordGrid, b: CoordGrid): Int = maxOf(abs(a.x - b.x), abs(a.z - b.z))

    companion object {
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** Zulrah is 5 by 5 tiles. */
        const val ZULRAH_SIZE = 5

        /** Her attack speed is 3 ticks (wiki). */
        const val ATTACK_RATE = 3

        /**
         * A melee action is two tail swings (Near-Reality, as ported by tsps; unverified): aimed at
         * ticks 0 and 9 of the action, landing at 5 and 15, and the action takes 18 ticks.
         */
        const val FIRST_STRIKE = 5
        const val SECOND_AIM = 9
        const val SECOND_STRIKE = 15
        const val MELEE_RATE = 18

        /** The tail throws a player up to 4 tiles away from her (tsps). */
        const val KNOCKBACK_TILES = 4

        /** The client cycles the throw takes: one game tick. */
        const val KNOCKBACK_CLIENT_CYCLES = 30

        /** The tail hits a 3x3 around the tile it was aimed at, so two tiles away is safe. */
        const val TAIL_REACH = 1

        /** The wiki: a tail swing does 20 to 30 damage. */
        const val TAIL_MIN_HIT = 20
        const val TAIL_MAX_HIT = 30

        /** The tsps notes: stunned for 3 seconds, 5 ticks. */
        const val TAIL_STUN = 5

        /** How long after a kill she is back in the lair. A guess, scaled by the respawn speed. */
        const val RESPAWN_TICKS = 50

        /** Every step, a barrage too, starts 3 ticks after the last (tsps, Near-Reality). */
        const val BARRAGE_RATE = 3

        /**
         * The wiki: "varying damage per tick". The Near-Reality server's numbers: 1 to 4 a tick, a
         * cloud lasts 30 ticks. Not checked against the real game.
         */
        const val CLOUD_MIN_HIT = 1
        const val CLOUD_MAX_HIT = 4
        const val CLOUD_DURATION = 30

        const val SNAKELING_HITPOINTS = 1

        /** The Near-Reality server: one snakeling in four is magic, and it attacks 3 ticks in. */
        const val SNAKELING_MAGIC_ODDS = 4
        const val SNAKELING_WAKE_TICKS = 3

        /** The wiki: a snakeling lives 40 seconds. */
        const val SNAKELING_LIFETIME = 67

        /** After the first rotation, one phase in this many shows another form. A guess. */
        const val VARIATION_ODDS = 10

        /**
         * tsps's (Near-Reality's) timings, unverified: she can be hit 4 ticks after rising and her
         * first phase starts 9 ticks after; after a dive she moves 3 ticks later and her next phase
         * (when she can be hit again) starts 7 ticks after the dive.
         */
        const val SPAWN_TICKS = 4
        const val FIRST_ACTION_DELAY = 5
        const val DIVE_TICKS = 3
        const val SURFACE_TICKS = 4
    }
}

/** How Zulrah shoots. The max hit is the wiki's (the green and blue forms share it). */
object ZulrahAttacks {
    const val MAX_HIT = 41

    val ranged =
        NpcAttack(
            style = NpcAttackStyle.Ranged,
            anim = zulrah_seqs.shoot,
            projectile = zulrah_spotanims.ranged_shot,
            projanim = projanims.magic_spell,
            maxHit = MAX_HIT,
        )

    val magic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = zulrah_seqs.shoot,
            projectile = zulrah_spotanims.magic_shot,
            projanim = projanims.magic_spell,
            maxHit = MAX_HIT,
        )

    /** The wiki: the melee snakeling stabs for up to 15 and the magic one casts for up to 13. */
    const val SNAKELING_MELEE_MAX_HIT = 15
    const val SNAKELING_MAGIC_MAX_HIT = 13
    const val SNAKELING_RATE = 3

    val snakelingMelee =
        NpcAttack(
            style = NpcAttackStyle.Melee,
            meleeType = org.rsmod.api.combat.commons.types.MeleeAttackType.Stab,
            anim = zulrah_seqs.snakeling_attack,
            maxHit = SNAKELING_MELEE_MAX_HIT,
        )

    val snakelingMagic =
        NpcAttack(
            style = NpcAttackStyle.Magic,
            anim = zulrah_seqs.snakeling_attack,
            projectile = zulrah_spotanims.snakeling_spell,
            projanim = projanims.magic_spell,
            maxHit = SNAKELING_MAGIC_MAX_HIT,
        )
}
