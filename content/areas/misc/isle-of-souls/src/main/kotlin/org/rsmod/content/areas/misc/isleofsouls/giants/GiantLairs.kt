package org.rsmod.content.areas.misc.isleofsouls.giants

import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
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
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.timer.TimerType
import org.rsmod.game.type.varp.VarpType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * One of the two key-locked boss lairs, in ordinary map coordinates (OSRS Wiki "Obor", "Bryophyta"
 * and their chests; positions from the cache's map and tsps' `giant-boss-lairs.json`).
 *
 * The lair is a team instance: a copy of the zones around [template] that lives while anyone is in
 * it. [entry] and [spawn] are positions in the original map, as are [chest] and [loot]; the run
 * maps them into the copy. [exit] is where players are put in the real dungeon, outside the gate.
 */
class GiantLair(
    val name: String,
    val lairName: String,
    val instanceName: String,
    val template: RegionTemplate,
    val entry: CoordGrid,
    val exit: CoordGrid,
    val spawn: CoordGrid,
    val chest: CoordGrid,
    val loot: CoordGrid,
    val chestClosed: LocType,
    val chestOpen: LocType,
    val key: ObjType,
    val keyName: String,
    val unlockFlag: Int,
    val countVarp: VarpType,
    val chestNpc: NpcType,
    val tickTimer: TimerType,
    val chestStir: String,
) {
    val type =
        TeamInstanceType(name = instanceName, template = template, entry = entry, exit = exit)

    /** "You can't loot the chest whilst Obor is still attacking you!" */
    val attackingMessage: String
        get() = "You can't loot the chest whilst $name is still attacking you!"

    val needKeyMessage: String
        get() = "You need a $keyName to open this chest."

    val countMessage: String
        get() = "Your $name chests opened count is: <col=ff0000>%d</col>."
}

/** Obor's and Bryophyta's lairs. */
object GiantLairs {
    /**
     * Obor's lair is x 3072-3106 by z 9786-9817 in the Edgeville Dungeon: the zones x 384-388 by z
     * 1223-1227 (3072-3111, 9784-9823). His pit is the south part, behind a fence; the gate he is
     * reached through stands in the corridor north of that block.
     */
    val obor =
        GiantLair(
            name = "Obor",
            lairName = "Obor's Lair",
            instanceName = "obor",
            template =
                RegionTemplate.create {
                    copyAllLevels(copyZoneX = 384, copyZoneZ = 1223) {
                        zoneWidth = 5
                        zoneLength = 5
                    }
                },
            entry = CoordGrid(3091, 9815, 0),
            exit = CoordGrid(3095, 9833, 0),
            spawn = CoordGrid(3092, 9799, 0),
            chest = CoordGrid(3085, 9797, 0),
            loot = CoordGrid(3085, 9798, 0),
            chestClosed = giant_locs.obor_chest,
            chestOpen = giant_locs.obor_chest_open,
            key = giant_objs.giant_key,
            keyName = "Giant key",
            unlockFlag = GiantFlags.OBOR_UNLOCKED,
            countVarp = giant_varps.obor_chests,
            chestNpc = giant_npcs.obor_chest,
            tickTimer = giant_timers.obor_tick,
            chestStir = "You hear the ground rumble...",
        )

    /**
     * Bryophyta's lair is x 3204-3238 by z 9922-9947 in the Varrock Sewers (the real game puts it
     * in an instance of its own): the zones x 400-404 by z 1240-1243 (3200-3239, 9920-9951).
     */
    val bryophyta =
        GiantLair(
            name = "Bryophyta",
            lairName = "Bryophyta's Lair",
            instanceName = "bryophyta",
            template =
                RegionTemplate.create {
                    copyAllLevels(copyZoneX = 400, copyZoneZ = 1240) {
                        zoneWidth = 5
                        zoneLength = 4
                    }
                },
            entry = CoordGrid(3214, 9937, 0),
            exit = CoordGrid(3174, 9900, 0),
            spawn = CoordGrid(3220, 9933, 0),
            chest = CoordGrid(3229, 9934, 0),
            loot = CoordGrid(3228, 9934, 0),
            chestClosed = giant_locs.bryophyta_chest,
            chestOpen = giant_locs.bryophyta_chest_open,
            key = giant_objs.mossy_key,
            keyName = "Mossy key",
            unlockFlag = GiantFlags.BRYOPHYTA_UNLOCKED,
            countVarp = giant_varps.bryophyta_chests,
            chestNpc = giant_npcs.bryophyta_chest,
            tickTimer = giant_timers.bryophyta_tick,
            chestStir = "You hear the leaves stir...",
        )
}

/** Something a fight put off until game tick [at]. */
class GiantDelayed(val at: Int, val action: () -> Unit)

/**
 * One team's lair. It lives as long as its [instance] has someone in it; the boss comes back in it
 * after each kill. [GiantDirector] advances it.
 */
open class GiantRun(instance: TeamInstance, startClock: Int, val lair: GiantLair) :
    WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    var boss: Npc? = null

    /** The boss is dead and the lair is waiting to bring it back. */
    var dead = false

    /** The boss's return is already on its way (a chest click since the kill). */
    var respawning = false

    /** The chest has been opened for the current kill. */
    var chestOpened = false

    /** The dead boss, the player who got the kill, and where the drops fell. */
    var killed: Npc? = null
    var hero: Player? = null
    var dropAt: CoordGrid? = null

    private val delayed = ArrayList<GiantDelayed>()

    /** Runs [action] on game tick [at] (or the first tick after it). */
    fun later(at: Int, action: () -> Unit) {
        delayed += GiantDelayed(at, action)
    }

    /** Runs what has fallen due. */
    fun runDue(now: Int) {
        if (delayed.isEmpty()) {
            return
        }
        val due = delayed.filter { it.at <= now }
        delayed.removeAll(due.toSet())
        for (task in due) {
            task.action()
        }
    }

    /** The tile of the original map [tile] in this run's copy of the lair. */
    fun real(tile: CoordGrid): CoordGrid = instance.region.normal[tile]

    override fun allNpcs(): List<Npc> {
        val alive = boss?.takeIf { !dead }
        return monsters.toList() + listOfNotNull(alive)
    }

    override fun clearNpcs() {
        super.clearNpcs()
        delayed.clear()
        boss = null
    }
}

/**
 * Runs a giant's lair for the teams in it. The wave loop (enter, leave, the run tick, clean-up) is
 * [WaveDirector]; this adds the lair: the boss spawns as soon as the team is in, it stays dead
 * after a kill until somebody clicks the chest, and it is back 15 ticks after that.
 */
abstract class GiantDirector<R : GiantRun>(
    protected val lair: GiantLair,
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    protected val players: PlayerList,
    protected val random: GameRandom,
    protected val locRepo: LocRepository,
    protected val locTypes: LocTypeList,
) : WaveDirector<R>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = lair.type

    /** The boss's npc type. */
    abstract val bossType: NpcType

    /** A new run for [instance]. */
    protected abstract fun newRun(instance: TeamInstance): R

    /** The boss has just spawned (or respawned) in [run]. */
    protected abstract fun bossSpawned(run: R, boss: Npc)

    /** One tick of the fight, while the boss is alive and somebody can be attacked. */
    protected abstract fun fight(run: R, boss: Npc, targets: List<Player>, now: Int)

    /** The players in [run]'s lair the boss can attack. */
    protected open fun targetsOf(run: R): List<Player> =
        instances.playersIn(run.instance).filter { it.isValidTarget() }

    final override fun createRun(instance: TeamInstance): R {
        val run = newRun(instance)
        // The boss is already there when the team arrives.
        run.nextWaveAt = mapClock.cycle
        return run
    }

    override fun waveMessage(run: R): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(lair.tickTimer, 1)
    }

    override fun spawnWave(run: R, inside: List<Player>) {
        val boss = spawn(run, bossType, lair.spawn)
        run.boss = boss
        run.dead = false
        run.respawning = false
        run.chestOpened = false
        run.killed = null
        run.hero = null
        bossSpawned(run, boss)
    }

    override fun engage(run: R, npc: Npc, target: Player): Boolean = false

    final override fun onWaveTick(run: R) {
        val now = mapClock.cycle
        run.runDue(now)
        val boss = run.boss ?: return
        if (run.dead || boss.hitpoints <= 0) {
            return
        }
        val targets = targetsOf(run)
        if (targets.isEmpty()) {
            return
        }
        fight(run, boss, targets, now)
    }

    /** The boss stays dead until its chest is clicked. */
    override fun onWin(run: R, inside: List<Player>) {
        run.state = WaveState.WaitingForNextWave
        run.nextWaveAt = Int.MAX_VALUE
    }

    /**
     * The boss has died (called by the death script, which then spawns its drops: that publishes
     * the kill).
     */
    fun bossDied(npc: Npc) {
        val run = runOf(npc) ?: return
        val boss = run.boss ?: return
        if (boss !== npc || run.dead) {
            return
        }
        run.dead = true
        run.hero = npc.findHero(players)
        run.killed = npc
        run.dropAt = npc.coords.translate(npc.size / 2, npc.size / 2)
        win(run)
    }

    /**
     * A click on the chest while the boss is dead: the first one after a kill (with a key or
     * without) brings the boss back [RESPAWN_TICKS] ticks later.
     */
    fun chestClicked(run: GiantRun, player: Player) {
        if (!run.dead || run.respawning) {
            return
        }
        run.respawning = true
        player.mes(lair.chestStir)
        run.nextWaveAt = mapClock.cycle + npcRepo.respawnSpeed.scale(RESPAWN_TICKS)
    }

    /** A minion of the boss has died. */
    open fun minionDied(npc: Npc) {
        runOf(npc)?.monsters?.remove(npc)
    }

    /** Whether [type]'s npc is this lair's boss in a run. */
    fun isBoss(npc: Npc): Boolean = npc.type.id == bossType.id

    protected fun distance(a: CoordGrid, b: CoordGrid): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    companion object {
        /** The wiki: the boss is back 15 ticks (9 seconds) after the chest is clicked. */
        const val RESPAWN_TICKS = 15
    }
}
