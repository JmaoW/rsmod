package org.rsmod.content.areas.misc.isleofsouls.toa

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.NpcImpactListener
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdd
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.instances.BossBarBits
import org.rsmod.content.areas.misc.isleofsouls.instances.BossBarScripts
import org.rsmod.content.areas.misc.isleofsouls.instances.BossBarVars
import org.rsmod.content.areas.misc.isleofsouls.instances.BossHealthBar
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
import org.rsmod.game.interact.InteractionLoc
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.game.obj.Obj
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
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
class ToaRoomEnv
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
    val raids: ToaRaids,
    val eventBus: EventBus,
    val interactions: AiPlayerInteractions,
    val npcHitModifier: NpcHitModifier,
    val venom: PlayerVenom,
    val poison: PlayerPoison,
    val projAnimTypes: ProjAnimTypeList,
    val objRepo: ObjRepository,
    val seqTypes: SeqTypeList,
    val wornBonuses: WornBonuses,
)

/**
 * One room of one raid while the party is in it: its monsters and objects, whether the challenge
 * has begun, and the room's own rules each tick. Everything a room puts into the raid's region is
 * tracked so that it can be taken away when the room is reset or the raid ends.
 *
 * Coordinates given to the helpers here are **real** (the tombs' own coordinates); the packed
 * region translates them.
 */
abstract class ToaRoomRuntime(protected val env: ToaRoomEnv, val raid: ToaRaid, val room: ToaRoom) {
    enum class Stage {
        Idle,
        Started,
        Completed,
    }

    var stage: Stage = Stage.Idle
        private set

    var startCycle: Int = 0
        private set

    /** Every npc the room spawned that is still there. */
    val npcs: MutableSet<Npc> = LinkedHashSet()

    /** Points for each point of damage dealt to a room npc (the wiki's multipliers). */
    private val pointsPer = HashMap<Npc, Double>()

    /** Who dealt how much damage to a room npc, for the boss trophy. */
    private val damageBy = HashMap<Npc, MutableMap<Int, Int>>()

    private val placed = LinkedHashMap<Pair<CoordGrid, Int>, LocInfo>()

    /** Work that waits for a later tick: tick to run at, and what to run. */
    private val scheduled = ArrayList<Pair<Int, () -> Unit>>()

    val now: Int
        get() = env.clock.cycle

    val isStarted: Boolean
        get() = stage == Stage.Started

    val isCompleted: Boolean
        get() = stage == Stage.Completed

    // Lifecycle -----------------------------------------------------------------------------------

    /**
     * Called once when the first player arrives: spawn what stands in the room before the fight.
     */
    open fun build() {}

    /** The challenge begins. */
    open fun onStart() {}

    /** Every game tick before the challenge begins (a boss wandering about its room). */
    open fun onIdle() {}

    /** The room npc [npc] ran out of hitpoints, which these fights never let it do. */
    open fun hitpointsGone(npc: Npc) {}

    /** Every game tick while the challenge is on. */
    open fun onTick() {}

    /** The challenge was won. */
    open fun onComplete() {}

    /** The team wiped: put the room back as it was. */
    open fun onReset() {}

    /** The runtime is going away (the raid ended): [clear] runs after this. */
    open fun onDestroy() {}

    /**
     * A hit from [source] is about to land on the room npc [npc]: a boss that is immune, or cannot
     * die yet, changes the damage here. Runs before the damage counts for points.
     */
    open fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {}

    /**
     * A hit of [damage] is landing on the room npc [npc]; returns what it may take. [modifyHit]
     * runs when a hit is queued, so a hit already in flight when the fight moves on (a phase
     * begins, a threshold is reached by another hit) is trimmed here again by the same rules.
     */
    open fun landingDamage(npc: Npc, damage: Int): Int = damage

    /** [landingDamage], for a room that also needs the landing [hit] itself (its hitmark). */
    open fun landing(npc: Npc, hit: Hit, damage: Int): Int = landingDamage(npc, damage)

    /** Called by the room's barrier, crystal or the like; does nothing twice. */
    fun start() {
        if (stage != Stage.Idle) {
            return
        }
        stage = Stage.Started
        startCycle = now
        for (member in raid.active) {
            member.player.mes("Challenge started: ${challengeName()}.")
        }
        onStart()
    }

    /**
     * The challenge is won: points, the dead come back, Osmumten appears, a boss clears its path.
     */
    fun complete() {
        if (stage == Stage.Completed) {
            return
        }
        stage = Stage.Completed
        val ticks = now - startCycle
        scheduled.clear()
        bossBar = null
        onComplete()
        for (member in raid.active) {
            for (line in completionLines(ticks)) {
                member.player.mes(line)
            }
        }
        env.raids.completeRoom(raid, room)
        room.osmumten?.let { spawnOsmumten(it) }
        if (room.kind == RoomKind.Boss || room.kind == RoomKind.WardensFinal) {
            handOutTrophies()
        }
        if (room.kind == RoomKind.Boss) {
            env.raids.completePath(raid, checkNotNull(room.path))
            restoreAfterBoss()
        }
    }

    /**
     * A path boss is beaten: everyone in its room gets their hitpoints and prayer back (a boost is
     * kept) and is cured of poison and venom.
     */
    private fun restoreAfterBoss() {
        for (player in roomPlayers()) {
            for (stat in listOf(stats.hitpoints, stats.prayer)) {
                val missing = player.statBase(stat) - player.stat(stat)
                if (missing > 0) {
                    player.statAdd(stat, missing, percent = 0)
                }
            }
            env.venom.cure(player)
            env.poison.cure(player)
        }
    }

    /** The party wiped: the room goes back to how it was. */
    fun reset() {
        stage = Stage.Idle
        scheduled.clear()
        bossBar = null
        onReset()
    }

    fun destroy() {
        onDestroy()
        clear()
    }

    /** Removes every npc and loc the room put in the region. */
    fun clear() {
        bossBar = null
        for (member in raid.members) {
            hideBar(member.player)
        }
        for (npc in npcs.toList()) {
            despawn(npc)
        }
        monsters.clear()
        for (loc in placed.values.toList()) {
            env.locRepo.del(loc, Int.MAX_VALUE)
        }
        placed.clear()
        scheduled.clear()
        for (obj in dropped) {
            env.objRepo.del(obj, Int.MAX_VALUE)
        }
        dropped.clear()
    }

    /** Runs one game tick of the room (the director calls this). */
    fun tick() {
        if (scheduled.isNotEmpty()) {
            val due = scheduled.filter { it.first <= now }
            scheduled.removeAll(due.toSet())
            due.forEach { it.second() }
        }
        if (isStarted) {
            onTick()
        } else if (stage == Stage.Idle) {
            onIdle()
        }
        tickBossBar()
    }

    /** Runs [action] [ticks] game ticks from now, in this room's tick. */
    fun later(ticks: Int, action: () -> Unit) {
        scheduled += (now + ticks) to action
    }

    /** How many ticks [seq] plays for, and at least [least]: when an npc that plays it may go. */
    fun ticksOf(seq: SeqType, least: Int = 1): Int = maxOf(least, env.seqTypes[seq].tickDuration)

    /**
     * The name the challenge messages use: "Path of Het" for a puzzle, the boss for a boss room
     * (the RuneLite Tombs of Amascut plugin reads these, e.g. "Challenge complete: Kephri.").
     */
    open fun challengeName(): String =
        when (room) {
            ToaRoom.ScabarasBoss -> "Kephri"
            ToaRoom.HetBoss -> "Akkha"
            ToaRoom.ApmekenBoss -> "Ba-Ba"
            ToaRoom.CrondisBoss -> "Zebak"
            ToaRoom.WardensFirst,
            ToaRoom.WardensFinal -> "The Wardens"
            else -> if (room.kind == RoomKind.Puzzle) "Path of ${room.path?.label}" else room.label
        }

    /**
     * The chat lines for a challenge won after [ticks]: its duration and the raid time so far, or
     * for the final Wardens the raid's completion times (the plugin's splits read "Total:" and, for
     * the Wardens, "completion time:" on the same line).
     */
    fun completionLines(ticks: Int): List<String> {
        val duration =
            "Challenge complete: ${challengeName()}. Duration: <col=ef1020>${clock(ticks)}</col>"
        if (room.kind != RoomKind.WardensFinal) {
            return listOf("$duration. Total: <col=ef1020>${clock(raid.elapsed(now))}</col>")
        }
        val mode = raid.mode.label
        return listOf(
            "$duration. Tombs of Amascut: $mode Mode challenge completion time: " +
                "<col=ef1020>${clock(raid.elapsed(now))}</col>",
            "Tombs of Amascut: $mode Mode total completion time: " +
                "<col=ef1020>${clock(now - raid.startedCycle)}</col>",
        )
    }

    private fun clock(ticks: Int): String {
        val seconds = ticks * 6 / 10
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    // The party
    // -------------------------------------------------------------------------------------

    /** Party members standing in the room's challenge area, alive and not ghosts. */
    fun challengePlayers(): List<Player> =
        raid.active
            .filter { member ->
                member.room == room &&
                    !member.ghost &&
                    member.player.isValidTarget() &&
                    inChallenge(member.player.coords)
            }
            .map { it.player }

    /** Every party member in this room, ghosts included. */
    fun roomPlayers(): List<Player> = raid.active.filter { it.room == room }.map { it.player }

    /** Whether a region coordinate is on the challenge floor. */
    fun inChallenge(coords: CoordGrid): Boolean {
        val real = raid.realOf(coords) ?: return false
        return room.inChallenge(real)
    }

    val teamSize: Int
        get() = raid.members.size.coerceAtLeast(1)

    val pathLevel: Int
        get() = room.path?.let { raid.pathLevels[it.index] } ?: 0

    // Scaling
    // ---------------------------------------------------------------------------------------

    /** A max hit scaled for the raid level and this path's level. */
    fun maxHit(base: Int): Int =
        (base * ToaScaling.damageFactor(raid.raidLevel, pathLevel)).toInt().coerceAtLeast(1)

    // Npcs
    // ------------------------------------------------------------------------------------------

    /**
     * Spawns [type] with its south-west tile on the real tile [real]. Unless [scale] is off, its
     * hitpoints are the type's scaled for the raid level, party size and path level, and its attack
     * and defence rolls are raised with the raid level. [points] is the room points a point of
     * damage on it is worth. [facing] is the way it looks when it appears (the type's own when
     * null). A [passThrough] npc (rolling orbs, boulders, swarms) is still stopped by walls and
     * locs but moves through players and other npcs, which would otherwise hold it up.
     */
    fun spawn(
        type: NpcType,
        real: CoordGrid,
        scale: Boolean = true,
        points: Double = 1.0,
        hitpoints: Int? = null,
        facing: Direction? = null,
        passThrough: Boolean = false,
    ): Npc {
        val npc = Npc(env.npcTypes[type], raid.regionOf(real))
        npc.mode = NpcMode.None
        if (passThrough) {
            npc.collisionOverride = CollisionStrategy.IgnoreEntities
        }
        // The client is told the direction when the npc is added, so it has to be set first.
        facing?.let { npc.respawnDir = it }
        if (scale) {
            val base = hitpoints ?: npc.type.hitpoints
            val scaled = ToaScaling.hitpoints(base, raid.raidLevel, teamSize, pathLevel)
            npc.baseHitpointsLvl = scaled
            npc.hitpoints = scaled
            npc.attackRollPercent = (ToaScaling.rollFactor(raid.raidLevel) * 100).toInt()
        } else if (hitpoints != null) {
            npc.baseHitpointsLvl = hitpoints
            npc.hitpoints = hitpoints
        }
        env.npcRepo.add(npc, LIFETIME)
        npcs += npc
        pointsPer[npc] = points
        return npc
    }

    fun despawn(npc: Npc) {
        npcs -= npc
        monsters.remove(npc)
        pointsPer.remove(npc)
        if (npc.isSlotAssigned) {
            env.npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    fun owns(npc: Npc): Boolean = npc in npcs

    /** [dealt] points of damage from [player] on [npc]: points, and the credit for a trophy. */
    fun damageDealt(player: Player, npc: Npc, dealt: Int) {
        val member = raid.member(player) ?: return
        member.damageDone += dealt
        raid.points.damage(player.accountId, dealt, pointsPer[npc] ?: 1.0)
        val by = damageBy.getOrPut(npc) { HashMap() }
        by.merge(player.accountId, dealt, Int::plus)
    }

    /** The player who did the most damage to [npc]. */
    fun topDamage(npc: Npc): Player? {
        val by = damageBy[npc] ?: return null
        val best = by.maxByOrNull { it.value }?.key ?: return null
        return raid.active.firstOrNull { it.player.accountId == best }?.player
    }

    /** The player who dealt the most damage in the room altogether. */
    fun topDamageOverall(): Player? {
        val totals = HashMap<Int, Int>()
        for (by in damageBy.values) {
            for ((id, dealt) in by) {
                totals.merge(id, dealt, Int::plus)
            }
        }
        val best = totals.maxByOrNull { it.value }?.key ?: return null
        return raid.active.firstOrNull { it.player.accountId == best }?.player
    }

    /**
     * A boss leaves its story book for everyone who has not had it yet, and its trophy (the big
     * banana, the fang, Eldritch ashes, scarab dung) for whoever did the most damage (wiki). They
     * land on the floor in front of Osmumten (on the floor tile players arrive on in a room without
     * him), each seen only by the player it is for.
     */
    private fun handOutTrophies() {
        val book = ToaTrophies.books[room]
        val trophy = ToaTrophies.trophies[room]
        val tile = dropTile() ?: return
        val top = topDamageOverall()
        for (member in raid.active) {
            if (member.room != room) {
                continue
            }
            val player = member.player
            if (book != null && player.vars[book.second] == 0) {
                VarPlayerIntMapSetter.set(player, book.second, 1)
                drop(book.first, tile, player)
            }
            if (trophy != null && player === top) {
                drop(trophy, tile, player)
            }
        }
    }

    /** Objs the room dropped for its players, taken away with the room. */
    private val dropped = ArrayList<Obj>()

    private fun drop(type: ObjType, real: CoordGrid, receiver: Player) {
        val obj =
            env.objRepo.add(
                type,
                raid.regionOf(real),
                DROP_DURATION,
                receiver = receiver,
                reveal = Int.MAX_VALUE,
            )
        dropped += obj
    }

    /** The tile in front of Osmumten (the way he faces), or the room's arrival tile. */
    private fun dropTile(): CoordGrid? {
        val osmumten = room.osmumten ?: return room.challenge
        val front = osmumten.translate(osmumtenFacing(osmumten).xOff, 0)
        return if (walkable(front)) front else osmumten
    }

    /** Osmumten looks across the arena, towards where the party arrives on its floor. */
    private fun osmumtenFacing(osmumten: CoordGrid): Direction {
        val challenge = room.challenge ?: return Direction.South
        return if (challenge.x >= osmumten.x) Direction.East else Direction.West
    }

    /**
     * Everyone in the room working at the loc on the real tile [real], or at [npc] (mining a wall
     * or the seal), stops at once: the action ends and the swing is put down, instead of the
     * pickaxe going on after the thing is gone.
     */
    fun stopWorkingOn(real: CoordGrid? = null, npc: Npc? = null) {
        val tile = real?.let(raid::regionOf)
        for (player in roomPlayers()) {
            val working =
                when (val interaction = player.interaction) {
                    is InteractionLoc -> tile != null && interaction.target.coords == tile
                    is InteractionNpc -> npc != null && interaction.target === npc
                    else -> false
                }
            if (working) {
                player.clearInteraction()
                player.resetAnim()
            }
        }
    }

    /** Whether [npc] is a tile the party could walk to: nothing blocks the 1x1 tile. */
    fun walkable(real: CoordGrid): Boolean {
        val tile = raid.regionOf(real)
        return (env.collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) == 0
    }

    // Locs
    // ----------------------------------------------------------------------------------------------

    /** Puts a loc of [type] on the real tile [real] for this raid, replacing what was there. */
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

    /** Takes a loc placed by [placeLoc] away again. */
    fun removeLoc(real: CoordGrid, shape: LocShape = LocShape.CentrepieceStraight) {
        placed.remove(real to shape.id)?.let { env.locRepo.del(it, Int.MAX_VALUE) }
    }

    fun placedLoc(real: CoordGrid, shape: LocShape = LocShape.CentrepieceStraight): LocInfo? =
        placed[real to shape.id]

    // Effects
    // ------------------------------------------------------------------------------------------

    /** A graphic on the real tile [real] that everyone near sees. */
    fun graphic(spot: SpotanimType, real: CoordGrid, height: Int = 0, delay: Int = 0) {
        env.worldRepo.spotanimMap(spot, raid.regionOf(real), height, delay)
    }

    /**
     * A projectile from the real tile [fromReal] to the real tile [toReal]. [flight], when given,
     * replaces the type's timing: the client cycles it leaves at and lands at. [heights] replaces
     * its start and end heights (a projectile from a tile to itself with a high start falls
     * straight down).
     */
    fun projectile(
        fromReal: CoordGrid,
        toReal: CoordGrid,
        spot: SpotanimType,
        type: ProjAnimType,
        flight: IntRange? = null,
        heights: Pair<Int, Int>? = null,
    ) {
        val proj =
            ProjAnim.fromCoordToCoord(
                raid.regionOf(fromReal),
                raid.regionOf(toReal),
                spot.id,
                env.projAnimTypes[type],
            )
        val timed = flight?.let { proj.copy(startTime = it.first, endTime = it.last) } ?: proj
        val placed =
            heights?.let { timed.copy(startHeight = it.first, endHeight = it.second) } ?: timed
        env.worldRepo.projAnim(placed)
    }

    /** A graphic on the real tile [real] that only [viewer] sees. */
    fun privateGraphic(
        viewer: Player,
        spot: SpotanimType,
        real: CoordGrid,
        height: Int = 0,
        delay: Int = 0,
    ) {
        env.worldRepo.spotanimMapFor(viewer, spot, raid.regionOf(real), height, delay)
    }

    /** Damage that nothing prevents (a rockfall, a beam), scaled like the room's attacks. */
    fun hurt(player: Player, base: Int, extra: Int = 0, delay: Int = 1) {
        player.queueHit(delay, HitType.Typeless, maxHit(base) + extra)
    }

    /**
     * Throws [player] to the real tile [toReal] (no walking: they slide there), facing [facing] (a
     * client angle: 0 south, 512 west, 1024 north, 1536 east), arriving after [cycles] client
     * cycles.
     */
    fun knockback(player: Player, toReal: CoordGrid, facing: Int? = null, cycles: Int = 30) {
        val dest = raid.regionOf(toReal)
        if (dest == player.coords) {
            return
        }
        val face = facing ?: facingBack(player.coords, dest)
        player.abortRoute()
        PathingEntityCommon.exactMove(player, player.coords, dest, 0, cycles, face, env.collision)
    }

    /**
     * The client angle (0 south, 512 west, 1024 north, 1536 east) for someone moved from [from] to
     * [to] who faces back the way they came: knocked back, not walking forward.
     */
    private fun facingBack(from: CoordGrid, to: CoordGrid): Int {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return when {
            abs(dx) >= abs(dz) && dx > 0 -> 512
            abs(dx) >= abs(dz) -> 1536
            dz > 0 -> 0
            else -> 1024
        }
    }

    /** Exactly [damage] that nothing prevents and nothing scales again. */
    /** Plays [synth] to each of [players] (everyone in the challenge unless given). */
    fun sound(synth: SynthType, players: List<Player> = challengePlayers(), delay: Int = 0) {
        for (player in players) {
            player.soundSynth(synth, delay = delay)
        }
    }

    fun hurtFlat(player: Player, damage: Int, delay: Int = 1) {
        if (damage > 0) {
            player.queueHit(delay.coerceAtLeast(1), HitType.Typeless, damage)
        }
    }

    /** [list] in a random order drawn from the game's random numbers. */
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

    /**
     * Attacks [target] with [attack], whose max hit is scaled for the raid. [through] is the share
     * of the damage that still lands through the matching protection prayer, in percent (0 for a
     * prayer that blocks it all); Quiet Prayers adds 10 to it.
     */
    fun strike(
        npc: Npc,
        target: Player,
        attack: NpcAttack,
        through: Int = 0,
        playAnim: Boolean = true,
        onImpact: NpcImpactListener? = null,
    ): Boolean {
        val scaled = attack.copy(maxHit = attack.maxHit?.let(::maxHit), typeless = true)
        val prayer = prayerVarbit(attack)
        val extra = if (raid.settings.isActive(Inv.QUIET_PRAYERS)) QUIET_PRAYERS_THROUGH else 0
        val listener = NpcImpactListener { victim, hit ->
            if (victim.vars[prayer] == 1) {
                hit.damage = hit.damage * (through + extra).coerceAtMost(100) / 100
            }
            onImpact?.onImpact(victim, hit)
        }
        return env.attacks.attack(npc, target, scaled, playAnim = playAnim, onImpact = listener)
    }

    private fun prayerVarbit(attack: NpcAttack) =
        when (attack.style) {
            org.rsmod.api.combat.NpcAttackStyle.Melee -> varbits.protect_from_melee
            org.rsmod.api.combat.NpcAttackStyle.Ranged -> varbits.protect_from_missiles
            org.rsmod.api.combat.NpcAttackStyle.Magic -> varbits.protect_from_magic
        }

    // Monsters
    // ------------------------------------------------------------------------------------------

    /** The room's monsters that it moves and swings for (see [ToaMonster]). */
    val monsters: MutableMap<Npc, ToaMonster> = LinkedHashMap()

    /**
     * Makes [npc] one of the room's monsters: it chases a player of the challenge and attacks with
     * [attack] every [speed] ticks once within [range] tiles, starting [delay] ticks from now. With
     * a [spawnAnim] it plays that first and neither moves nor attacks until it has finished.
     */
    fun monster(
        npc: Npc,
        attack: NpcAttack,
        range: Int,
        speed: Int,
        through: Int = 0,
        delay: Int = 2,
        spawnAnim: SeqType? = null,
    ): ToaMonster {
        val monster = ToaMonster(npc, attack, range, speed, through)
        monster.nextAttack = now + delay
        if (spawnAnim != null) {
            npc.anim(spawnAnim)
            monster.heldUntil = now + ticksOf(spawnAnim)
            monster.nextAttack = maxOf(monster.nextAttack, monster.heldUntil)
        }
        monsters[npc] = monster
        return monster
    }

    /** Whether [npc] is one of the room's monsters still playing its spawn animation. */
    fun isHeld(npc: Npc): Boolean = (monsters[npc]?.heldUntil ?: 0) > now

    /** Each monster picks a target if it has none, closes in and swings. Call from [onTick]. */
    fun driveMonsters() {
        if (monsters.isEmpty()) {
            return
        }
        val players = challengePlayers()
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
                step(monster, target.coords, keepOff = monster.range <= 1 || gap == 0)
                continue
            }
            npc.resetMovement()
            if (now >= monster.nextAttack) {
                monster.nextAttack = now + monster.speed
                swing(monster, target)
            }
        }
    }

    /**
     * One step of [monster] towards [coords], if its walking pace ([ToaMonster.stepTicks]) allows.
     */
    fun step(monster: ToaMonster, coords: CoordGrid, keepOff: Boolean = true) {
        if (now - monster.lastStepAt < monster.stepTicks) {
            return
        }
        monster.lastStepAt = now
        stepToward(monster.npc, coords, keepOff)
    }

    /** Who a monster with no target goes for: anyone in the fight, by default. */
    open fun pickTarget(monster: ToaMonster, players: List<Player>): Player? =
        if (players.isEmpty()) null else players[env.random.of(players.size)]

    /** [monster] attacks [target]. Rooms change what a swing does for their special monsters. */
    open fun swing(monster: ToaMonster, target: Player) {
        strike(monster.npc, target, monster.attack, through = monster.through)
    }

    /**
     * A monster of the room died (the death script has it play its death and leave the world): the
     * room forgets it, then [monsterDied] lets the room react.
     */
    fun died(npc: Npc) {
        if (npc !in npcs) {
            return
        }
        npcs -= npc
        monsters.remove(npc)
        monsterDied(npc)
    }

    open fun monsterDied(npc: Npc) {}

    /** The distance from the edge of [npc] to [coords], in tiles: 0 when it is under the npc. */
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

    /** Whether [npc] could shoot at [coords]: no wall or the like is in the way. */
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

    /** Whether [coords] is off a corner of [npc] (a melee attack cannot reach it). */
    fun diagonal(npc: Npc, coords: CoordGrid): Boolean {
        val sw = npc.coords
        val offX = coords.x < sw.x || coords.x >= sw.x + npc.size
        val offZ = coords.z < sw.z || coords.z >= sw.z + npc.size
        return offX && offZ
    }

    /**
     * One step of [npc] straight towards [coords] (npcs do not path-find). With [keepOff] it never
     * steps onto the tile itself, so a melee monster stops beside its target.
     */
    fun stepToward(npc: Npc, coords: CoordGrid, keepOff: Boolean = true) {
        val centreX = npc.coords.x + (npc.size - 1) / 2
        val centreZ = npc.coords.z + (npc.size - 1) / 2
        val dx = Integer.signum(coords.x - centreX)
        val dz = Integer.signum(coords.z - centreZ)
        if (dx == 0 && dz == 0) {
            // Under the target: any free tile beside it will do.
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
    fun canStand(npc: Npc, dest: CoordGrid): Boolean {
        for (dx in 0 until npc.size) {
            for (dz in 0 until npc.size) {
                val tile = dest.translate(dx, dz)
                if ((env.collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) != 0) {
                    return false
                }
                if (!inChallenge(tile)) {
                    return false
                }
            }
        }
        return true
    }

    // The boss health bar
    // ------------------------------------------------------------------------------------------

    /** The npc whose health the room's players see at the top of the screen, if any. */
    var bossBar: Npc? = null

    private val bar = BossHealthBar()

    /** The health [bossBar] shows: the npc's own, unless the room counts something else. */
    open fun bossBarHp(npc: Npc): Int = npc.hitpoints

    /** The bar's colours for [npc]: the usual red and green, or a shield's blue. */
    open fun bossBarColours(npc: Npc): List<Int> = ToaBossBarScripts.COLOURS

    /**
     * Keeps every player in the room shown [bossBar]'s health, and takes it away from anyone who
     * left (see [BossHealthBar]).
     */
    private fun tickBossBar() {
        val boss = bossBar?.takeIf { it.isSlotAssigned }
        val inRoom = roomPlayers()
        for (player in inRoom) {
            if (boss == null) {
                bar.hide(player)
            } else {
                bar.show(player, boss, bossBarHp(boss), bossBarColours(boss))
            }
        }
        if (bar.viewers.size > inRoom.size) {
            for (member in raid.members) {
                if (member.player !in inRoom) {
                    bar.hide(member.player)
                }
            }
        }
    }

    private fun hideBar(player: Player) {
        bar.hide(player)
    }

    // Changing the room's own locs
    // ------------------------------------------------------------------------------------------

    /** The map locs [swapLoc] changed: what each tile had, and what it has now. */
    private val swapped = LinkedHashMap<CoordGrid, Pair<LocType, LocType>>()

    /**
     * Turns the map's own loc on the real tile [real] (any of [from]) into [into], remembering what
     * it was so [restoreLocs] can put it back. Returns `false` if none of [from] is there.
     */
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

    /** Plays [seq] on the loc of [type] on the real tile [real], if it is there. */
    fun animLoc(real: CoordGrid, type: LocType, seq: SeqType) {
        val loc = env.locRepo.findExact(raid.regionOf(real), type) ?: return
        env.worldRepo.locAnim(loc, seq)
    }

    /** Puts every loc [swapLoc] changed back as the map had it. */
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

    // Osmumten
    // ------------------------------------------------------------------------------------------

    /**
     * Osmumten, in the form with "Talk-to" and "Proceed" (the others are shown by a varbit the
     * client sets from the raid, which this server does not).
     */
    private fun spawnOsmumten(real: CoordGrid) {
        val npc = spawn(toa_npcs.osmumten_vis, real, scale = false, facing = osmumtenFacing(real))
        npc.anim(ToaOsmumtenSeqs.appear)
    }

    companion object {
        /** A room npc never expires on its own; the room removes them. */
        const val LIFETIME = 100_000

        /** How long a dropped book or trophy waits on the floor: an hour. */
        private const val DROP_DURATION = 6000

        private const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** Quiet Prayers: protection prayers are 10% less effective. */
        const val QUIET_PRAYERS_THROUGH = 10
    }
}

/**
 * A monster the room moves and swings for, instead of the engine's combat ai: it picks a player of
 * the challenge, walks straight at them (npcs do not path-find) and attacks with [attack] every
 * [speed] ticks once within [range] tiles (1 is melee: beside, not off a corner). [through] is the
 * share of its damage that still lands through the matching protection prayer, in percent.
 */
class ToaMonster(
    val npc: Npc,
    var attack: NpcAttack,
    var range: Int,
    var speed: Int,
    var through: Int = 0,
) {
    var target: Player? = null
    var nextAttack: Int = 0

    /** The room moves it itself (a wanderer, a charge): the shared chase and swing leave it be. */
    var scripted: Boolean = false

    /** It stands still until this tick, while its spawn animation plays. */
    var heldUntil: Int = 0

    /** It takes a step every this many ticks (2 for half speed), and last stepped then. */
    var stepTicks: Int = 1
    var lastStepAt: Int = Int.MIN_VALUE / 2

    /** Free for the room: what kind of monster it is, a counter, and the like. */
    var kind: Int = 0
    var counter: Int = 0
}

typealias ToaBossBarScripts = BossBarScripts

typealias ToaBossBarVars = BossBarVars

typealias ToaBossBarBits = BossBarBits

/** Osmumten rising from the floor when a room is won (TSPS uses the same seq). */
object ToaOsmumtenSeqs : org.rsmod.api.type.refs.seq.SeqReferences() {
    val appear = find("ghost_summon2_priority")
}

/** Each boss room's story book (and the varbit that marks it received) and its trophy. */
object ToaTrophies {
    object Objs : org.rsmod.api.type.refs.obj.ObjReferences() {
        val book_akkha = find("toa_book_akkha")
        val book_baba = find("toa_book_baba")
        val book_kephri = find("toa_book_kephri")
        val book_zebak = find("toa_book_zebak")
        val book_wardens = find("toa_book_wardens")
        val ashes = find("toa_akkha_ashes")
        val banana = find("toa_baba_banana")
        val dung = find("toa_kephri_poo")
        val fang = find("toa_zebak_fang")
    }

    object Bits : VarBitReferences() {
        val akkha = find("toa_book_akkha")
        val baba = find("toa_book_baba")
        val kephri = find("toa_book_kephri")
        val zebak = find("toa_book_zebak")
        val wardens = find("toa_book_wardens")
    }

    val books =
        mapOf(
            ToaRoom.HetBoss to (Objs.book_akkha to Bits.akkha),
            ToaRoom.ApmekenBoss to (Objs.book_baba to Bits.baba),
            ToaRoom.ScabarasBoss to (Objs.book_kephri to Bits.kephri),
            ToaRoom.CrondisBoss to (Objs.book_zebak to Bits.zebak),
            ToaRoom.WardensFinal to (Objs.book_wardens to Bits.wardens),
        )

    val trophies =
        mapOf(
            ToaRoom.HetBoss to Objs.ashes,
            ToaRoom.ApmekenBoss to Objs.banana,
            ToaRoom.ScabarasBoss to Objs.dung,
            ToaRoom.CrondisBoss to Objs.fang,
        )
}
