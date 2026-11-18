package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.hitmark_groups
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaProjAnims
import org.rsmod.content.areas.misc.isleofsouls.zulrah.PlayerVenom
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.Hit
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.proj.ProjAnimTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.game.type.synth.SynthType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

/** Everything a room's content needs from the engine, in one injectable place. */
@Singleton
class TobRoomEnv
@Inject
constructor(
    val npcRepo: NpcRepository,
    val npcTypes: NpcTypeList,
    val locRepo: LocRepository,
    val locTypes: LocTypeList,
    val worldRepo: WorldRepository,
    val attacks: NpcAttacks,
    val random: GameRandom,
    val collision: CollisionFlagMap,
    val clock: MapClock,
    val raids: TobRaids,
    val eventBus: EventBus,
    val interactions: AiPlayerInteractions,
    val venom: PlayerVenom,
    val poison: PlayerPoison,
    val projAnimTypes: ProjAnimTypeList,
    val objRepo: ObjRepository,
    val seqTypes: SeqTypeList,
)

/**
 * One room of one raid: its monsters and locs, whether its fight is on, and the room's own rules
 * each tick. Everything a room puts into the raid's region is tracked so it can be taken away when
 * the raid ends.
 *
 * Coordinates given to the helpers here are **real** (the theatre's own); the raid's region
 * translates them.
 */
abstract class TobRoomRuntime(protected val env: TobRoomEnv, val raid: TobRaid, val room: TobRoom) {
    enum class Stage {
        Idle,
        Started,
        Completed,
    }

    var stage: Stage = Stage.Idle
        private set

    var startCycle: Int = 0
        private set

    val npcs: MutableSet<Npc> = LinkedHashSet()
    private val placed = LinkedHashMap<Pair<CoordGrid, Int>, LocInfo>()
    private val scheduled = ArrayList<Pair<Int, () -> Unit>>()

    val now: Int
        get() = env.clock.cycle

    val isStarted: Boolean
        get() = stage == Stage.Started

    val isCompleted: Boolean
        get() = stage == Stage.Completed

    // Lifecycle -----------------------------------------------------------------------------------

    /** The party's first arrival: spawn what waits in the room before the fight. */
    open fun build() {}

    /** The leader crossed the barrier (or spoke to Verzik): the fight begins. */
    open fun onStart() {}

    open fun onIdle() {}

    open fun onTick() {}

    open fun onComplete() {}

    open fun onDestroy() {}

    /** The room npc [npc] ran out of hitpoints, which the bosses are never allowed to do. */
    open fun hitpointsGone(npc: Npc) {}

    /** A hit from [source] is about to land on the room npc [npc]. */
    open fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {}

    /** A hit of [damage] lands on [npc] (queued earlier): what it may still take. */
    open fun landing(npc: Npc, hit: Hit, damage: Int): Int = damage

    /** What the HUD's bar shows while the fight is on: the boss's health, by default. */
    open fun bar(): TobBar = bossBar?.takeIf { it.isSlotAssigned }?.let(::barOf) ?: TobBar.NONE

    fun barOf(npc: Npc, kind: Int = TobBar.REGULAR): TobBar =
        TobBar.of(kind, npc.hitpoints, npc.baseHitpointsLvl)

    /** The npc whose health the HUD's bar shows. */
    var bossBar: Npc? = null

    fun start() {
        if (stage != Stage.Idle) {
            return
        }
        stage = Stage.Started
        startCycle = now
        onStart()
    }

    /** The fight is won. */
    fun complete() {
        if (stage == Stage.Completed) {
            return
        }
        stage = Stage.Completed
        scheduled.clear()
        bossBar = null
        raid.bar = TobBar.NONE
        onComplete()
        env.raids.completeRoom(raid, room, now - startCycle)
    }

    fun destroy() {
        onDestroy()
        clear()
    }

    fun clear() {
        for (npc in npcs.toList()) {
            despawn(npc)
        }
        monsters.clear()
        for (loc in placed.values.toList()) {
            env.locRepo.del(loc, Int.MAX_VALUE)
        }
        placed.clear()
        restoreLocs()
        scheduled.clear()
    }

    fun tick() {
        if (scheduled.isNotEmpty()) {
            val due = scheduled.filter { it.first <= now }
            scheduled.removeAll(due.toSet())
            due.forEach { it.second() }
        }
        if (isStarted) {
            onTick()
            if (isStarted && raid.room == room) {
                raid.bar = bar()
            }
        } else if (stage == Stage.Idle) {
            onIdle()
        }
    }

    fun later(ticks: Int, action: () -> Unit) {
        scheduled += (now + ticks) to action
    }

    fun ticksOf(seq: SeqType, least: Int = 1): Int = maxOf(least, env.seqTypes[seq].tickDuration)

    // The party
    // -------------------------------------------------------------------------------------------

    /** Party members in the fight, alive and able to be hit. */
    fun fightPlayers(): List<Player> =
        raid.active
            .filter { member ->
                member.room == room &&
                    member.inFight &&
                    !member.jailed &&
                    member.player.isValidTarget() &&
                    inArena(member.player.coords)
            }
            .map { it.player }

    /** Everyone in the room, the dead included. */
    fun roomPlayers(): List<Player> = raid.active.filter { it.room == room }.map { it.player }

    fun inArena(coords: CoordGrid): Boolean {
        val real = raid.realOf(coords) ?: return false
        return room.inArena(real)
    }

    fun realOf(coords: CoordGrid): CoordGrid? = raid.realOf(coords)

    fun regionOf(real: CoordGrid): CoordGrid = raid.regionOf(real)

    /** How many are in the party: what the bosses' hitpoints and some mechanics scale with. */
    val teamSize: Int
        get() = raid.members.size.coerceAtLeast(1)

    // Npcs
    // ------------------------------------------------------------------------------------------

    /**
     * Spawns [type] with its south-west tile on the real tile [real]. Unless [scale] is off its
     * hitpoints are scaled for the party ([TobScaling]). A [passThrough] npc walks through players
     * and other npcs (walls still stop it).
     */
    fun spawn(
        type: NpcType,
        real: CoordGrid,
        scale: Boolean = false,
        hitpoints: Int? = null,
        facing: Direction? = null,
        passThrough: Boolean = false,
        ignoreCollision: Boolean = false,
    ): Npc {
        val npc = Npc(env.npcTypes[type], raid.regionOf(real))
        npc.mode = NpcMode.None
        if (ignoreCollision) {
            npc.collisionOverride = CollisionStrategy.Ignore
        } else if (passThrough) {
            npc.collisionOverride = CollisionStrategy.IgnoreEntities
        }
        facing?.let { npc.respawnDir = it }
        val base = hitpoints ?: npc.type.hitpoints
        val hp = if (scale) TobScaling.hitpoints(base, teamSize) else base
        if (scale || hitpoints != null) {
            npc.baseHitpointsLvl = hp
            npc.hitpoints = hp
        }
        env.npcRepo.add(npc, LIFETIME)
        npcs += npc
        return npc
    }

    fun despawn(npc: Npc) {
        npcs -= npc
        monsters.remove(npc)
        if (npc.isSlotAssigned) {
            env.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun owns(npc: Npc): Boolean = npc in npcs

    /** [dealt] damage from [player] to a room npc counts for the supplies and the loot. */
    fun damageDealt(player: Player, dealt: Int) {
        val member = raid.member(player) ?: return
        member.damageIn.merge(room, dealt, Int::plus)
    }

    /** Heals [npc] by [amount], up to its scaled maximum. */
    fun heal(npc: Npc, amount: Int) {
        if (amount <= 0 || !npc.isSlotAssigned) {
            return
        }
        npc.hitpoints = minOf(npc.baseHitpointsLvl, npc.hitpoints + amount)
    }

    /** Swaps [npc] for another form ([type]) in place, keeping its hitpoints. */
    fun transform(npc: Npc, type: NpcType) {
        npc.transmog(env.npcTypes[type], Int.MAX_VALUE)
    }

    // Locs
    // ----------------------------------------------------------------------------------------------

    fun placeLoc(
        real: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): LocInfo {
        val tile = raid.regionOf(real)
        val key = real to shape.id
        placed.remove(key)?.let { env.locRepo.del(it, Int.MAX_VALUE) }
        val loc = env.locRepo.add(tile, type, Int.MAX_VALUE, angle, shape)
        placed[key] = loc
        return loc
    }

    fun removeLoc(real: CoordGrid, shape: LocShape = LocShape.CentrepieceStraight) {
        placed.remove(real to shape.id)?.let { env.locRepo.del(it, Int.MAX_VALUE) }
    }

    fun placedLoc(real: CoordGrid, shape: LocShape = LocShape.CentrepieceStraight): LocInfo? =
        placed[real to shape.id]

    private val swapped = LinkedHashMap<CoordGrid, Pair<LocType, LocType>>()

    /** Turns the map's own loc on [real] (any of [from]) into [into]; [restoreLocs] undoes it. */
    fun swapLoc(real: CoordGrid, from: List<LocType>, into: LocType): Boolean {
        val tile = raid.regionOf(real)
        val known = swapped[real]
        val candidates = if (known != null) listOf(known.second) + from else from
        val current =
            candidates.firstNotNullOfOrNull { type ->
                env.locRepo.findExact(tile, type)?.let { it to type }
            } ?: return false
        if (current.second.id != into.id) {
            env.locRepo.change(current.first, into, Int.MAX_VALUE)
        }
        swapped[real] = (known?.first ?: current.second) to into
        return true
    }

    fun restoreLocs() {
        for ((real, entry) in swapped.toList()) {
            val (original, now) = entry
            val current = env.locRepo.findExact(raid.regionOf(real), now) ?: continue
            if (now.id != original.id) {
                env.locRepo.change(current, original, Int.MAX_VALUE)
            }
        }
        swapped.clear()
    }

    fun animLoc(real: CoordGrid, type: LocType, seq: SeqType) {
        val loc = env.locRepo.findExact(raid.regionOf(real), type) ?: return
        env.worldRepo.locAnim(loc, seq)
    }

    // Effects
    // ------------------------------------------------------------------------------------------

    fun graphic(spot: SpotanimType, real: CoordGrid, height: Int = 0, delay: Int = 0) {
        env.worldRepo.spotanimMap(spot, raid.regionOf(real), height, delay)
    }

    /**
     * A projectile from the real tile [fromReal] to [toReal], leaving at client cycle
     * `flight.first` and landing at `flight.last`. Returns the ticks until it lands.
     */
    fun projectile(
        fromReal: CoordGrid,
        toReal: CoordGrid,
        spot: SpotanimType,
        flight: IntRange,
        heights: Pair<Int, Int> = 43 to 31,
        type: ProjAnimType = ToaProjAnims.orb,
    ): Int {
        val proj =
            ProjAnim.fromCoordToCoord(
                raid.regionOf(fromReal),
                raid.regionOf(toReal),
                spot.id,
                env.projAnimTypes[type],
            )
        env.worldRepo.projAnim(
            proj.copy(
                startTime = flight.first,
                endTime = flight.last,
                startHeight = heights.first,
                endHeight = heights.second,
            )
        )
        return ticksFor(flight.last)
    }

    /** A projectile timed by distance: it leaves at [delay] and flies [base] + [perTile] each. */
    fun projectileBy(
        fromReal: CoordGrid,
        toReal: CoordGrid,
        spot: SpotanimType,
        delay: Int,
        base: Int,
        perTile: Int,
        heights: Pair<Int, Int> = 43 to 31,
    ): Int {
        val distance = max(abs(fromReal.x - toReal.x), abs(fromReal.z - toReal.z))
        return projectile(
            fromReal,
            toReal,
            spot,
            delay..(delay + base + perTile * distance),
            heights,
        )
    }

    /** Client cycles (20 ms) to the game tick the projectile lands on, at least the next one. */
    fun ticksFor(cycles: Int): Int =
        ((cycles + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK).coerceAtLeast(1)

    fun sound(synth: SynthType, players: List<Player> = roomPlayers(), delay: Int = 0) {
        for (player in players) {
            player.soundSynth(synth, delay = delay)
        }
    }

    /** Typeless damage nothing prevents. */
    fun hurt(player: Player, damage: Int, delay: Int = 1) {
        if (damage > 0) {
            player.queueHit(delay.coerceAtLeast(1), HitType.Typeless, damage)
        }
    }

    /** Typeless damage shown as poison (green). */
    fun hurtPoison(player: Player, damage: Int, delay: Int = 1) {
        if (damage > 0) {
            player.queueHit(
                delay.coerceAtLeast(1),
                HitType.Typeless,
                damage,
                hitmark = hitmark_groups.poison_damage,
            )
        }
    }

    fun roll(min: Int, max: Int): Int = if (max <= min) min else min + env.random.of(max - min + 1)

    fun chance(oneIn: Int): Boolean = oneIn <= 1 || env.random.of(oneIn) == 0

    fun <T> pick(list: List<T>): T = list[env.random.of(list.size)]

    fun <T> shuffled(list: List<T>): List<T> {
        val out = list.toMutableList()
        for (i in out.size - 1 downTo 1) {
            val j = env.random.of(i + 1)
            val swap = out[i]
            out[i] = out[j]
            out[j] = swap
        }
        return out
    }

    /** Whether [player] is praying against [style]. */
    fun prays(player: Player, style: NpcAttackStyle): Boolean =
        player.vars[prayerVarbit(style)] == 1

    /**
     * Attacks [target] with [attack]. [through] is the share of the damage (in percent) that still
     * lands through the matching protection prayer: 0 for a prayer that blocks it all.
     */
    fun strike(
        npc: Npc,
        target: Player,
        attack: NpcAttack,
        through: Int = 0,
        playAnim: Boolean = true,
        onImpact: NpcImpactListener? = null,
    ): Boolean {
        val typed = attack.copy(typeless = true)
        val prayer = prayerVarbit(attack.style)
        val listener = NpcImpactListener { victim, hit ->
            if (victim.vars[prayer] == 1) {
                hit.damage = hit.damage * through.coerceIn(0, 100) / 100
            }
            onImpact?.onImpact(victim, hit)
        }
        return env.attacks.attack(npc, target, typed, playAnim = playAnim, onImpact = listener)
    }

    private fun prayerVarbit(style: NpcAttackStyle) =
        when (style) {
            NpcAttackStyle.Melee -> varbits.protect_from_melee
            NpcAttackStyle.Ranged -> varbits.protect_from_missiles
            NpcAttackStyle.Magic -> varbits.protect_from_magic
        }

    /** Slides [player] to the real tile [toReal] (no walking), facing back the way they came. */
    fun knockback(player: Player, toReal: CoordGrid, cycles: Int = 30) {
        val dest = raid.regionOf(toReal)
        if (dest == player.coords) {
            return
        }
        val dx = dest.x - player.coords.x
        val dz = dest.z - player.coords.z
        val face =
            when {
                abs(dx) >= abs(dz) && dx > 0 -> 512
                abs(dx) >= abs(dz) -> 1536
                dz > 0 -> 0
                else -> 1024
            }
        player.abortRoute()
        PathingEntityCommon.exactMove(player, player.coords, dest, 0, cycles, face, env.collision)
    }

    fun teleport(player: Player, real: CoordGrid) {
        PathingEntityCommon.telejump(player, env.collision, raid.regionOf(real))
    }

    // Monsters
    // ------------------------------------------------------------------------------------------

    val monsters: MutableMap<Npc, TobMonster> = LinkedHashMap()

    fun monster(
        npc: Npc,
        attack: NpcAttack,
        range: Int,
        speed: Int,
        through: Int = 0,
        delay: Int = 2,
    ): TobMonster {
        val monster = TobMonster(npc, attack, range, speed, through)
        monster.nextAttack = now + delay
        monsters[npc] = monster
        return monster
    }

    /** Each monster picks a target if it has none, closes in and swings. Call from [onTick]. */
    fun driveMonsters() {
        if (monsters.isEmpty()) {
            return
        }
        val players = fightPlayers()
        for (monster in monsters.values.toList()) {
            val npc = monster.npc
            if (!npc.isSlotAssigned || npc.hitpoints <= 0 || monster.scripted) {
                continue
            }
            if (now < monster.heldUntil) {
                continue
            }
            var target = monster.target
            if (target == null || target !in players) {
                target = pickTarget(monster, players) ?: continue
                monster.target = target
            }
            npc.facePlayer(target)
            val gap = gap(npc, target.coords)
            val reaches =
                if (monster.range <= 1) {
                    gap == 1 && !diagonal(npc, target.coords)
                } else {
                    gap in 1..monster.range && canSee(npc, target.coords)
                }
            if (!reaches) {
                if (now - monster.lastStepAt >= monster.stepTicks) {
                    monster.lastStepAt = now
                    stepToward(npc, target.coords, keepOff = monster.range <= 1 || gap == 0)
                }
                continue
            }
            npc.resetMovement()
            if (now >= monster.nextAttack) {
                monster.nextAttack = now + monster.speed
                swing(monster, target)
            }
        }
    }

    open fun pickTarget(monster: TobMonster, players: List<Player>): Player? =
        if (players.isEmpty()) null else players[env.random.of(players.size)]

    open fun swing(monster: TobMonster, target: Player) {
        strike(monster.npc, target, monster.attack, through = monster.through)
    }

    /** A monster of the room died (its death script plays the death): the room forgets it. */
    fun died(npc: Npc) {
        if (npc !in npcs) {
            return
        }
        npcs -= npc
        monsters.remove(npc)
        monsterDied(npc)
    }

    open fun monsterDied(npc: Npc) {}

    /** The distance from the edge of [npc] to [coords] (region tiles): 0 under it. */
    fun gap(npc: Npc, coords: CoordGrid): Int {
        val sw = npc.coords
        val dx =
            when {
                coords.x < sw.x -> sw.x - coords.x
                coords.x >= sw.x + npc.size -> coords.x - (sw.x + npc.size - 1)
                else -> 0
            }
        val dz =
            when {
                coords.z < sw.z -> sw.z - coords.z
                coords.z >= sw.z + npc.size -> coords.z - (sw.z + npc.size - 1)
                else -> 0
            }
        return max(dx, dz)
    }

    fun canSee(npc: Npc, coords: CoordGrid): Boolean {
        if (coords.level != npc.coords.level) {
            return false
        }
        return lineValidator.hasLineOfSight(
            level = coords.level,
            srcX = npc.coords.x,
            srcZ = npc.coords.z,
            destX = coords.x,
            destZ = coords.z,
            srcWidth = npc.size,
            srcLength = npc.size,
        )
    }

    private val lineValidator by lazy { LineValidator(env.collision) }

    fun diagonal(npc: Npc, coords: CoordGrid): Boolean {
        val sw = npc.coords
        val offX = coords.x < sw.x || coords.x >= sw.x + npc.size
        val offZ = coords.z < sw.z || coords.z >= sw.z + npc.size
        return offX && offZ
    }

    /** One step of [npc] straight towards [coords] (region tiles; npcs do not path-find). */
    fun stepToward(npc: Npc, coords: CoordGrid, keepOff: Boolean = true) {
        val centreX = npc.coords.x + (npc.size - 1) / 2
        val centreZ = npc.coords.z + (npc.size - 1) / 2
        val dx = Integer.signum(coords.x - centreX)
        val dz = Integer.signum(coords.z - centreZ)
        if (dx == 0 && dz == 0) {
            for ((sx, sz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val dest = npc.coords.translate(sx, sz)
                if (canStand(npc, dest)) {
                    npc.walk(dest)
                    return
                }
            }
            return
        }
        for ((sx, sz) in listOf(dx to dz, dx to 0, 0 to dz)) {
            if (sx == 0 && sz == 0) {
                continue
            }
            val dest = npc.coords.translate(sx, sz)
            if (keepOff && npc.size == 1 && dest == coords) {
                continue
            }
            if (canStand(npc, dest)) {
                npc.walk(dest)
                return
            }
        }
    }

    /** Whether [npc] could stand with its south-west tile on the region tile [dest]. */
    fun canStand(npc: Npc, dest: CoordGrid, inArenaOnly: Boolean = true): Boolean {
        for (dx in 0 until npc.size) {
            for (dz in 0 until npc.size) {
                val tile = dest.translate(dx, dz)
                if ((env.collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) != 0) {
                    return false
                }
                if (inArenaOnly && !inArena(tile)) {
                    return false
                }
            }
        }
        return true
    }

    fun walkable(real: CoordGrid): Boolean {
        val tile = raid.regionOf(real)
        return (env.collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) == 0
    }

    companion object {
        const val LIFETIME = 100_000
        const val CYCLES_PER_TICK = 30
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
    }
}

/**
 * A monster the room moves and swings for: it picks a player in the fight, walks straight at them
 * and attacks every [speed] ticks once within [range] tiles. [through] is the share of its damage
 * that still lands through the matching protection prayer, in percent.
 */
class TobMonster(
    val npc: Npc,
    var attack: NpcAttack,
    var range: Int,
    var speed: Int,
    var through: Int = 0,
) {
    var target: Player? = null
    var nextAttack: Int = 0
    var scripted: Boolean = false
    var heldUntil: Int = 0
    var stepTicks: Int = 1
    var lastStepAt: Int = Int.MIN_VALUE / 2
    var kind: Int = 0
    var counter: Int = 0
    var spawnedAt: Int = 0
}

/**
 * Wiki: the bosses have 75% of their hitpoints for a party of up to three, 87.5% for four and all
 * of them for five. The event allows parties of up to eight; each player past five adds another
 * 12.5% (a guess, going on as the wiki's steps do).
 */
object TobScaling {
    fun factor(teamSize: Int): Double =
        when {
            teamSize <= 3 -> 0.75
            teamSize == 4 -> 0.875
            else -> 1.0 + 0.125 * (teamSize - 5)
        }

    fun hitpoints(base: Int, teamSize: Int): Int =
        (base * factor(teamSize)).toInt().coerceAtLeast(1)
}
