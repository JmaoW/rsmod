package org.rsmod.content.areas.misc.isleofsouls.cox

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
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaProjAnims
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
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.proj.ProjAnimType
import org.rsmod.game.type.proj.ProjAnimTypeList
import org.rsmod.game.type.seq.SeqType
import org.rsmod.game.type.seq.SeqTypeList
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.collision.CollisionStrategy
import org.rsmod.routefinder.flag.CollisionFlag

/** Everything a room's content needs from the engine, in one injectable place. */
@Singleton
class CoxRoomEnv
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
    val raids: CoxRaids,
    val eventBus: EventBus,
    val projAnimTypes: ProjAnimTypeList,
    val objRepo: ObjRepository,
    val objTypes: ObjTypeList,
    val seqTypes: SeqTypeList,
    val poison: PlayerPoison,
)

/**
 * One room of one raid: its monsters and locs, whether its encounter is done, its own rules each
 * tick. Tiles given to the helpers are **template** tiles (the cache's tiles of the room's
 * version); [tile] turns them into the raid's. Most rooms give a tile per version: [pick3] picks
 * the one of this raid's version.
 */
abstract class CoxRoomRuntime(
    protected val env: CoxRoomEnv,
    val raid: CoxRaid,
    val room: CoxPlacement?,
) {
    var completed: Boolean = false
        private set

    /** The encounter has begun (a player crossed in, or the room's own trigger). */
    var started: Boolean = false
        private set

    var startCycle: Int = -1
        private set

    val npcs: MutableSet<Npc> = LinkedHashSet()
    private val placed = LinkedHashMap<Pair<CoordGrid, Int>, LocInfo>()
    private val scheduled = ArrayList<Pair<Int, () -> Unit>>()

    val now: Int
        get() = env.clock.cycle

    val variant: Int
        get() = room?.variant ?: 0

    val scaling: CoxScalingInputs
        get() = raid.scaling

    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    /** The raid was built: spawn what waits in the room. */
    open fun build() {}

    /** A raider walked in for the first time. */
    open fun onEntered(player: Player) {}

    open fun onStart() {}

    /** Every tick before the encounter begins. */
    open fun onIdle() {}

    /** Every tick while it is on. */
    open fun onTick() {}

    open fun onComplete() {}

    open fun onDestroy() {}

    /** A room npc ran out of hitpoints (only those registered as bosses come here). */
    open fun hitpointsGone(npc: Npc) {}

    /** A hit from [source] is about to land on the room npc [npc]. */
    open fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {}

    open fun landing(npc: Npc, hit: Hit, damage: Int): Int = damage

    /** Points a hitpoint of damage to [npc] is worth (5 by default; 0 for none). */
    open fun pointsPerHitpoint(npc: Npc): Int = POINTS_PER_HP

    fun start() {
        if (started || completed) return
        started = true
        startCycle = now
        onStart()
    }

    /** The encounter is done: the way on opens and the exit crystals shatter. */
    fun complete() {
        if (completed) return
        completed = true
        started = true
        scheduled.clear()
        onComplete()
        shatterCrystals()
        room?.let { env.raids.openRoom(raid, it) }
    }

    fun destroy() {
        onDestroy()
        for (npc in npcs.toList()) despawn(npc)
        monsters.clear()
        for (loc in placed.values.toList()) env.locRepo.del(loc, Int.MAX_VALUE)
        placed.clear()
        scheduled.clear()
    }

    fun tick() {
        if (scheduled.isNotEmpty()) {
            val due = scheduled.filter { it.first <= now }
            scheduled.removeAll(due.toSet())
            due.forEach { it.second() }
        }
        if (completed) {
            onAfter()
            return
        }
        if (started) onTick() else onIdle()
    }

    /** Every tick after the encounter is done (resources keep growing, for one). */
    open fun onAfter() {}

    fun later(ticks: Int, action: () -> Unit) {
        scheduled += (now + ticks.coerceAtLeast(0)) to action
    }

    fun ticksOf(seq: SeqType, least: Int = 1): Int = maxOf(least, env.seqTypes[seq].tickDuration)

    // Tiles
    // ---------------------------------------------------------------------------------------------

    fun <T> pick3(options: List<T>): T = options[variant.coerceAtMost(options.size - 1)]

    /** The raid tile of the template tile [template], for something [size] tiles square. */
    open fun tile(template: CoordGrid, size: Int = 1): CoordGrid =
        raid.regionOf(checkNotNull(room), template, size)

    open fun template(coords: CoordGrid): CoordGrid = raid.templateOf(checkNotNull(room), coords)

    fun tile(x: Int, z: Int, size: Int = 1): CoordGrid = tile(CoordGrid(x, z, 0), size)

    /** Whether the raid tile [coords] is in this room. */
    open fun inRoom(coords: CoordGrid): Boolean = raid.roomAt(coords) == room

    // The party
    // ---------------------------------------------------------------------------------------------

    /** Raiders in the room, alive and able to be hit. */
    fun fightPlayers(): List<Player> =
        raid.active.map { it.player }.filter { it.isValidTarget() && inRoom(it.coords) }

    fun roomPlayers(): List<Player> = raid.active.map { it.player }.filter { inRoom(it.coords) }

    val partySize: Int
        get() = if (raid.started) raid.scaling.size else raid.active.size.coerceAtLeast(1)

    // Npcs
    // ---------------------------------------------------------------------------------------------

    /**
     * Spawns [type] with its south-west on the raid tile [at]. With [base] its levels and hitpoints
     * are scaled for the raid ([CoxScaling]); [hitpoints] overrides them.
     */
    fun spawnAt(
        type: NpcType,
        at: CoordGrid,
        base: CoxBase? = null,
        hitpoints: Int? = null,
        facing: Direction? = null,
        passThrough: Boolean = false,
        ignoreCollision: Boolean = false,
        tekton: Boolean = false,
        crystal: Boolean = false,
    ): Npc {
        val npc = Npc(env.npcTypes[type], at)
        npc.mode = NpcMode.None
        if (ignoreCollision) {
            npc.collisionOverride = CollisionStrategy.Ignore
        } else if (passThrough) {
            npc.collisionOverride = CollisionStrategy.IgnoreEntities
        }
        facing?.let { npc.respawnDir = it }
        if (base != null) {
            val scaled = CoxScaling.scale(base, scaling, tekton = tekton, crystal = crystal)
            CoxScaling.apply(npc, scaled)
        }
        if (hitpoints != null) {
            npc.baseHitpointsLvl = hitpoints
            npc.hitpoints = hitpoints
        }
        env.npcRepo.add(npc, LIFETIME)
        npcs += npc
        return npc
    }

    /** As [spawnAt], at the template tile [template]. */
    fun spawn(
        type: NpcType,
        template: CoordGrid,
        base: CoxBase? = null,
        hitpoints: Int? = null,
        facing: Direction? = null,
        passThrough: Boolean = false,
        ignoreCollision: Boolean = false,
        tekton: Boolean = false,
        crystal: Boolean = false,
    ): Npc {
        val size = env.npcTypes[type].size
        return spawnAt(
            type,
            tile(template, size),
            base,
            hitpoints,
            facing,
            passThrough,
            ignoreCollision,
            tekton,
            crystal,
        )
    }

    /** Scaled levels for [base] (a transformed npc keeps its hitpoints). */
    fun restat(npc: Npc, base: CoxBase, tekton: Boolean = false) {
        CoxScaling.apply(
            npc,
            CoxScaling.scale(base, scaling, tekton = tekton),
            keepHitpoints = true,
        )
    }

    fun scaledMaxHit(base: CoxBase, maxHit: Int): Int =
        CoxScaling.maxHit(maxHit, CoxScaling.scale(base, scaling))

    fun despawn(npc: Npc) {
        npcs -= npc
        monsters.remove(npc)
        if (npc.isSlotAssigned) env.npcRepo.del(npc, Int.MAX_VALUE)
    }

    fun owns(npc: Npc): Boolean = npc in npcs

    fun heal(npc: Npc, amount: Int) {
        if (amount <= 0 || !npc.isSlotAssigned) return
        npc.healHitpoints(amount)
    }

    /** Changes [npc]'s form in place; its levels and hitpoints are kept. */
    fun transform(npc: Npc, type: NpcType) {
        val hp = npc.hitpoints
        val max = npc.baseHitpointsLvl
        val levels =
            intArrayOf(npc.attackLvl, npc.strengthLvl, npc.defenceLvl, npc.rangedLvl, npc.magicLvl)
        npc.transmog(env.npcTypes[type], Int.MAX_VALUE)
        npc.baseHitpointsLvl = max
        npc.hitpoints = hp
        npc.attackLvl = levels[0]
        npc.baseAttackLvl = levels[0]
        npc.strengthLvl = levels[1]
        npc.baseStrengthLvl = levels[1]
        npc.defenceLvl = levels[2]
        npc.baseDefenceLvl = levels[2]
        npc.rangedLvl = levels[3]
        npc.baseRangedLvl = levels[3]
        npc.magicLvl = levels[4]
        npc.baseMagicLvl = levels[4]
    }

    /** Drops [count] of [obj] on [at] (a raid tile), for anyone. */
    fun drop(obj: ObjType, at: CoordGrid, count: Int = 1) {
        if (count <= 0) return
        env.objRepo.add(env.objTypes[obj], at, DROP_TICKS, null, count)
    }

    // Locs
    // ---------------------------------------------------------------------------------------------

    /** Places [type] with its south-west on the template tile [template], facing [angle] there. */
    fun placeLoc(
        template: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): LocInfo {
        val loc = env.locTypes[type]
        val turns = room?.rotation ?: 0
        val turned = LocAngle.entries[(angle.id + turns) and 3]
        val width = if (angle.id % 2 == 0) loc.width else loc.length
        val length = if (angle.id % 2 == 0) loc.length else loc.width
        val at =
            if (room == null) {
                tile(template)
            } else {
                // Turn the footprint's two far corners and keep the south-west of the result.
                val a = tile(template)
                val b = tile(template.translate(width - 1, length - 1))
                CoordGrid(minOf(a.x, b.x), minOf(a.z, b.z), a.level)
            }
        return placeLocAt(at, type, shape, turned)
    }

    fun placeLocAt(
        at: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.CentrepieceStraight,
        angle: LocAngle = LocAngle.West,
    ): LocInfo {
        val key = at to shape.id
        placed.remove(key)?.let { env.locRepo.del(it, Int.MAX_VALUE) }
        val loc = env.locRepo.add(at, type, Int.MAX_VALUE, angle, shape)
        placed[key] = loc
        return loc
    }

    fun removeLoc(loc: LocInfo) {
        val key = placed.entries.firstOrNull { it.value == loc }?.key ?: return
        placed.remove(key)
        env.locRepo.del(loc, Int.MAX_VALUE)
    }

    fun placedLocs(): Collection<LocInfo> = placed.values

    /** The map's own loc of any of [types] whose footprint covers the raid tile [at]. */
    fun findLoc(at: CoordGrid, types: List<LocType>): LocInfo? =
        types.firstNotNullOfOrNull { env.locRepo.findExact(at, it) }

    fun swapLoc(loc: LocInfo, into: LocType): LocInfo {
        env.locRepo.change(loc, into, Int.MAX_VALUE)
        return env.locRepo.findExact(loc.coords, into) ?: loc
    }

    fun animLoc(loc: LocInfo, seq: SeqType) {
        env.worldRepo.locAnim(loc, seq)
    }

    // Exit crystals
    // ---------------------------------------------------------------------------------------------

    private val crystals = ArrayList<LocInfo>()

    /** Blocks the way on with crystals at the template tiles [tiles] until [complete]. */
    fun blockExit(tiles: List<CoordGrid>, type: LocType = cox_locs.crystal_green) {
        for (t in tiles) crystals += placeLoc(t, type)
    }

    private fun shatterCrystals() {
        for (crystal in crystals.toList()) {
            animLoc(crystal, cox_seqs.crystal_smash)
            later(SHATTER_TICKS) { removeLoc(crystal) }
        }
        crystals.clear()
    }

    // Effects
    // ---------------------------------------------------------------------------------------------

    fun graphicAt(spot: SpotanimType, at: CoordGrid, height: Int = 0, delay: Int = 0) {
        env.worldRepo.spotanimMap(spot, at, height, delay)
    }

    /**
     * A projectile from [from] to [to] (raid tiles), leaving at client cycle `flight.first` and
     * landing at `flight.last`. Returns the ticks until it lands.
     */
    fun projectile(
        from: CoordGrid,
        to: CoordGrid,
        spot: SpotanimType,
        flight: IntRange,
        heights: Pair<Int, Int> = 43 to 31,
        type: ProjAnimType = ToaProjAnims.orb,
    ): Int {
        val proj = ProjAnim.fromCoordToCoord(from, to, spot.id, env.projAnimTypes[type])
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

    /** A projectile timed by distance: it leaves at [delay] and flies [base] + [perTile] a tile. */
    fun projectileBy(
        from: CoordGrid,
        to: CoordGrid,
        spot: SpotanimType,
        delay: Int = 30,
        base: Int = 30,
        perTile: Int = 5,
        heights: Pair<Int, Int> = 43 to 31,
        type: ProjAnimType = ToaProjAnims.orb,
    ): Int {
        val distance = max(abs(from.x - to.x), abs(from.z - to.z))
        return projectile(from, to, spot, delay..(delay + base + perTile * distance), heights, type)
    }

    fun ticksFor(cycles: Int): Int =
        ((cycles + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK).coerceAtLeast(1)

    /** Damage nothing prevents. */
    fun hurt(player: Player, damage: Int, delay: Int = 1) {
        if (damage > 0) player.queueHit(delay.coerceAtLeast(1), HitType.Typeless, damage)
    }

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

    fun percent(chance: Int): Boolean = env.random.of(100) < chance

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

    fun prays(player: Player, style: NpcAttackStyle): Boolean =
        player.vars[prayerVarbit(style)] == 1

    /**
     * Attacks [target] with [attack]; [through] is the share (percent) that still lands through the
     * matching protection prayer.
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

    fun knockback(player: Player, to: CoordGrid, cycles: Int = 30) {
        if (to == player.coords) return
        val dx = to.x - player.coords.x
        val dz = to.z - player.coords.z
        val face =
            when {
                abs(dx) >= abs(dz) && dx > 0 -> 512
                abs(dx) >= abs(dz) -> 1536
                dz > 0 -> 0
                else -> 1024
            }
        player.abortRoute()
        PathingEntityCommon.exactMove(player, player.coords, to, 0, cycles, face, env.collision)
    }

    fun teleport(player: Player, to: CoordGrid) {
        PathingEntityCommon.telejump(player, env.collision, to)
    }

    // Monsters
    // ---------------------------------------------------------------------------------------------

    val monsters: MutableMap<Npc, CoxMonster> = LinkedHashMap()

    fun monster(
        npc: Npc,
        attack: NpcAttack,
        range: Int,
        speed: Int,
        through: Int = 0,
        delay: Int = 2,
        aggressive: Boolean = true,
    ): CoxMonster {
        val monster = CoxMonster(npc, attack, range, speed, through)
        monster.nextAttack = now + delay
        monster.aggressive = aggressive
        monsters[npc] = monster
        return monster
    }

    /** Each monster picks a target, closes in and swings. Call from [onTick] or [onIdle]. */
    fun driveMonsters() {
        if (monsters.isEmpty()) return
        val players = fightPlayers()
        for (monster in monsters.values.toList()) {
            val npc = monster.npc
            if (!npc.isSlotAssigned || npc.hitpoints <= 0 || monster.scripted) continue
            if (now < monster.heldUntil) continue
            if (!monster.aggressive && !monster.provoked) continue
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
                if (!monster.stationary && now - monster.lastStepAt >= monster.stepTicks) {
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

    open fun pickTarget(monster: CoxMonster, players: List<Player>): Player? =
        players
            .filter { monster.reach == 0 || gap(monster.npc, it.coords) <= monster.reach }
            .ifEmpty { if (monster.reach == 0) players else emptyList() }
            .let { if (it.isEmpty()) null else it[env.random.of(it.size)] }

    open fun swing(monster: CoxMonster, target: Player) {
        strike(monster.npc, target, monster.attack, through = monster.through)
    }

    fun died(npc: Npc) {
        if (npc !in npcs) return
        npcs -= npc
        monsters.remove(npc)
        monsterDied(npc)
    }

    open fun monsterDied(npc: Npc) {}

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
        if (coords.level != npc.coords.level) return false
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
            if (sx == 0 && sz == 0) continue
            val dest = npc.coords.translate(sx, sz)
            if (keepOff && npc.size == 1 && dest == coords) continue
            if (canStand(npc, dest)) {
                npc.walk(dest)
                return
            }
        }
    }

    fun canStand(npc: Npc, dest: CoordGrid): Boolean {
        for (dx in 0 until npc.size) {
            for (dz in 0 until npc.size) {
                val tile = dest.translate(dx, dz)
                if ((env.collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) != 0) return false
                if (!inRoom(tile)) return false
            }
        }
        return true
    }

    fun walkable(at: CoordGrid): Boolean =
        (env.collision[at.x, at.z, at.level] and WALK_BLOCKED) == 0

    /** The nearest of [player]'s tiles round [around] (raid tile) that can be stood on. */
    fun open(around: CoordGrid): CoordGrid = env.raids.nearOpen(around)

    companion object {
        const val LIFETIME = 100_000
        const val CYCLES_PER_TICK = 30
        const val POINTS_PER_HP = 5
        const val DROP_TICKS = 200
        private const val SHATTER_TICKS = 4
        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
    }
}

/**
 * A monster the room moves and swings for: it picks a raider, walks straight at them and attacks
 * every [speed] ticks once within [range] tiles. [through] is what still lands through the matching
 * protection prayer, in percent.
 */
class CoxMonster(
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
    var stationary: Boolean = false
    var aggressive: Boolean = true

    /** A non-aggressive monster fights back once hit. */
    var provoked: Boolean = false

    /** Only raiders within this many tiles are picked (0 for anyone in the room). */
    var reach: Int = 0
    var kind: Int = 0
    var counter: Int = 0
}
