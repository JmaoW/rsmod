package org.rsmod.content.areas.misc.isleofsouls.wilderness

import jakarta.inject.Inject
import java.util.IdentityHashMap
import org.rsmod.annotations.InternalApi
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isValidTarget as npcIsValid
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.spells.attack.NpcFreezeImmunity
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.game.type.spot.HashedSpotanimType
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid
import org.rsmod.map.util.Bounds
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/** Something a fight put off until tick [at]. */
class Delayed(val at: Int, val action: () -> Unit)

/**
 * What the director remembers about one boss. Each family of bosses extends it with its own state
 * (traps, webs, the form it is in).
 */
open class BossFight(val npc: Npc, val lair: WildLair) {
    var target: Player? = null

    /** Somebody came close or hurt it, so it is now after the players. */
    var engaged = false

    /** The tick of its next attack. */
    var attackAt = 0

    var walkDest: CoordGrid? = null

    /** For how many ticks in a row nobody has been in the lair. */
    var emptyTicks = 0

    /** Set when it is dying, so nothing else acts for it. */
    var dead = false

    private val delayed = ArrayList<Delayed>()

    /** Runs [action] on tick [at] (or the first tick after it). */
    fun later(at: Int, action: () -> Unit) {
        delayed += Delayed(at, action)
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

    /** Back to how it started: the boss respawned, or was left alone long enough to heal. */
    open fun reset(now: Int) {
        target = null
        engaged = false
        attackAt = now
        walkDest = null
        emptyTicks = 0
        dead = false
        delayed.clear()
    }
}

/**
 * How one family of Wilderness bosses fights. The director calls [act] each tick for every boss in
 * a lair that has a player in it.
 */
abstract class WildLogic(protected val d: WildBosses) {
    abstract fun create(npc: Npc, lair: WildLair): BossFight

    /** One tick of the fight; [inside] are the players who can be attacked in the lair. */
    abstract fun act(fight: BossFight, inside: List<Player>, now: Int)

    /** The boss was hit (even for 0) by [attacker]. */
    open fun hurt(fight: BossFight, attacker: Player?, damage: Int) {}

    /** An incoming hit, before it is applied: the logic may lower it (immunity). */
    open fun modifyHit(fight: BossFight, hit: HitBuilder) {}

    /**
     * The boss reached 0 hitpoints. Returns `true` if this was only the end of a form and the boss
     * lives on (Vet'ion's first form), `false` if it is a real death.
     */
    open fun interceptDeath(fight: BossFight): Boolean = false

    /** Removes what the fight put in the world (minions, webs, traps). */
    open fun clear(fight: BossFight) {}

    /** The boss died for good: by default everything it put in the world goes at once. */
    open fun died(fight: BossFight) {
        clear(fight)
    }

    /** One tick while the boss lies dying, for what has to finish after its death. */
    open fun deadTick(fight: BossFight, now: Int) {}
}

/**
 * Runs the Wilderness bosses. Each lair holds one boss that lives in the world for good, like any
 * other spawn: it fights whoever is in its lair, so every team can be in it at once, and when it
 * dies it comes back after a while. A boss ticks on its own through an npc timer; nothing is driven
 * by players. The mechanics are in the three [WildLogic] families, [WildBossScript] wires the
 * events and [WildRewards] hands out the loot by damage.
 */
class WildBosses
@Inject
constructor(
    val npcRepo: NpcRepository,
    val npcTypes: NpcTypeList,
    val players: PlayerList,
    val collision: CollisionFlagMap,
    val mapClock: MapClock,
    val random: GameRandom,
    val worldRepo: WorldRepository,
    val locRepo: LocRepository,
    val locTypes: LocTypeList,
    val objRepo: ObjRepository,
    val attacks: NpcAttacks,
    val accuracy: AccuracyFormulae,
    val eventBus: EventBus,
    val interactions: AiPlayerInteractions,
    val freezeImmunity: NpcFreezeImmunity,
) {
    /** For trying graphics for the bash in the client (`::bashfx`): -1 keeps the configured one. */
    var bashShadowId = -1
    var bashDustId = -1

    /** The spotanim with cache id [id] (only its id is used to draw it). */
    fun spotanim(id: Int): SpotanimType = HashedSpotanimType(null, "bashfx_$id", id)

    private val fights = IdentityHashMap<Npc, BossFight>()
    private val byFamily = HashMap<WildFamily, WildLogic>()

    val hits = WildHits(this)

    init {
        byFamily[WildFamily.Callisto] = CallistoLogic(this)
        byFamily[WildFamily.Venenatis] = VenenatisLogic(this)
        byFamily[WildFamily.Vetion] = VetionLogic(this)
    }

    val now: Int
        get() = mapClock.cycle

    fun logicOf(family: WildFamily): WildLogic = checkNotNull(byFamily[family])

    fun fightOf(npc: Npc): BossFight? = fights[npc]

    fun logicOf(fight: BossFight): WildLogic = logicOf(fight.lair.family)

    /** The boss of [lair], once spawned. */
    fun bossOf(lair: WildLair): Npc? = fights.values.firstOrNull { it.lair == lair }?.npc

    /** The fight of [lair]'s boss, once spawned. */
    fun fightIn(lair: WildLair): BossFight? = fights.values.firstOrNull { it.lair == lair }

    /** Puts a boss in every lair. Called once the game has started. */
    fun spawnAll() {
        for (lair in WildLair.entries) {
            spawn(lair)
        }
    }

    fun spawn(lair: WildLair): Npc {
        val npc = Npc(npcTypes[lair.boss], lair.bossSpawn)
        npc.mode = NpcMode.None
        npcRepo.add(npc, Int.MAX_VALUE)
        val fight = logicOf(lair.family).create(npc, lair)
        fight.reset(now)
        fights[npc] = fight
        npc.timer(wild_timers.tick, 1)
        return npc
    }

    /**
     * The boss respawned: a whole new fight, and the timer set again in case a death cleared it.
     */
    fun respawned(npc: Npc) {
        val fight = fights[npc] ?: return
        logicOf(fight).clear(fight)
        // The death plugin restores these too; doing it here as well keeps the boss whole whichever
        // handler runs first.
        npc.resetTransmog()
        npc.hitpoints = npc.baseHitpointsLvl
        fight.reset(now)
        npc.timer(wild_timers.tick, 1)
    }

    /** The players in [lair] a boss can attack. */
    fun inside(lair: WildLair): List<Player> =
        players.filter { it.isValidTarget() && lair.contains(it.coords) }

    /** The count of players in [lair], for a peek through its entrance. */
    fun countIn(lair: WildLair): Int = players.count { lair.contains(it.coords) }

    /** One tick of one boss. */
    fun tick(npc: Npc) {
        val fight = fights[npc] ?: return
        if (!npc.npcIsValid()) {
            return
        }
        val now = now
        if (fight.dead) {
            logicOf(fight).deadTick(fight, now)
            return
        }
        if (npc.hitpoints <= 0) {
            return
        }
        fight.runDue(now)
        val inside = inside(fight.lair)
        if (inside.isEmpty()) {
            fight.target = null
            fight.emptyTicks++
            if (fight.emptyTicks == HEAL_AFTER) {
                resetAlone(fight)
            }
            return
        }
        fight.emptyTicks = 0
        logicOf(fight).act(fight, inside, now)
    }

    @OptIn(InternalApi::class)
    private fun resetHeroPoints(npc: Npc) {
        npc.clearHeroPoints()
    }

    /** Whether [tile] is one of the tiles under [npc]'s body. */
    fun onBody(npc: Npc, tile: CoordGrid): Boolean =
        tile.level == npc.coords.level &&
            tile.x in npc.coords.x until npc.coords.x + npc.size &&
            tile.z in npc.coords.z until npc.coords.z + npc.size

    /** Nobody has been here for a while: the boss heals and goes back to where it spawned. */
    private fun resetAlone(fight: BossFight) {
        val npc = fight.npc
        logicOf(fight).clear(fight)
        npc.resetTransmog()
        npc.hitpoints = npc.baseHitpointsLvl
        resetHeroPoints(npc)
        if (npc.coords != fight.lair.bossSpawn) {
            PathingEntityCommon.telejump(npc, collision, fight.lair.bossSpawn)
        }
        fight.reset(now)
    }

    // Shared behaviour ------------------------------------------------------------------------

    /** Keeps the current target while it is in reach, otherwise the nearest player. */
    fun pickTarget(fight: BossFight, inside: List<Player>): Player {
        val current = fight.target
        if (current != null && current in inside && fight.npc.distanceTo(current) <= LEASH) {
            return current
        }
        return inside.minBy { fight.npc.distanceTo(it) }
    }

    /** Takes a step towards [target]: to the nearest spot that puts the boss's body beside them. */
    fun approach(fight: BossFight, target: Player) {
        val npc = fight.npc
        if (npc.isFrozen) {
            return
        }
        val size = npc.size
        val here = npc.coords
        var best: CoordGrid? = null
        var bestAway = Int.MAX_VALUE
        val goal = Bounds(target.coords, 1, 1)
        for (dz in -size..1) {
            for (dx in -size..1) {
                val spot = CoordGrid(target.coords.x + dx, target.coords.z + dz, here.level)
                if (Bounds(spot, size, size).distanceTo(goal) != 1) {
                    continue
                }
                if (!standable(spot, size) || spot !in fight.lair.arena) {
                    continue
                }
                val away = chebyshev(here, spot)
                if (away < bestAway) {
                    best = spot
                    bestAway = away
                }
            }
        }
        val dest = best ?: return
        if (fight.walkDest != dest || !npc.hasMovedPreviousCycle) {
            fight.walkDest = dest
            npc.walk(dest)
        }
    }

    /** Whether a body of [size] with its south-west tile on [spot] fits on open floor. */
    fun standable(spot: CoordGrid, size: Int): Boolean {
        for (z in 0 until size) {
            for (x in 0 until size) {
                if ((collision[spot.x + x, spot.z + z, spot.level] and WALK_BLOCKED) != 0) {
                    return false
                }
            }
        }
        return true
    }

    /** Whether one tile is open floor. */
    fun walkable(tile: CoordGrid): Boolean =
        (collision[tile.x, tile.z, tile.level] and WALK_BLOCKED) == 0

    fun chebyshev(a: CoordGrid, b: CoordGrid): Int =
        maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    /**
     * Points a minion or cave creature at [target]: it walks up and fights with the rules of its
     * type. The engine drops a chase once the target is further than its `maxRange` from where it
     * spawned, so the leash moves along with the target.
     */
    fun attackTarget(npc: Npc, target: Player) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
            return
        }
        val current = (npc.interaction as? InteractionPlayerOp)?.target
        val hunting = npc.mode == NpcMode.OpPlayer2 || npc.mode == NpcMode.ApPlayer2
        if (current !== target || !hunting) {
            npc.opPlayer2(target, interactions)
        }
        npc.spawnCoords = target.coords
    }

    /** The tile in the middle of a boss's body (the south-west tile for a body of size 1). */
    fun centre(npc: Npc): CoordGrid =
        CoordGrid(npc.coords.x + npc.size / 2, npc.coords.z + npc.size / 2, npc.coords.level)

    companion object {
        /** Ticks alone in its lair before a boss heals up and walks home. */
        const val HEAL_AFTER = 50

        /** A target further away than this is dropped for the nearest player. */
        const val LEASH = 25

        const val WALK_BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        /** Cave creatures and minions never expire by themselves. */
        const val LIFETIME = 100_000
    }
}
