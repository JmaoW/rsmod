package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object XarpusNpcs : NpcReferences() {
    val waiting = find("tob_xarpus_static")
    val feeding = find("tob_xarpus_feeding")
    val fighting = find("tob_xarpus_combat")
    val dead = find("xarpus_death")
}

object XarpusLocs : LocReferences() {
    val exhumed = find("tob_xarpus_exhumed")
    val pool = find("tob_xarpus_acidpool")
    val skeleton = find("tob_skeleton_with_weapon")
    val skeleton_empty = find("tob_skeleton_without_weapon")
}

object XarpusSeqs : SeqReferences() {
    val spit = find("tob_xarpus_attack_ranged")
    val rise = find("tob_xarpus_fly_up")
    val exhumed = find("tob_xarpus_exhumed_loop")
    val splat = find("tob_xarpus_acid_splat_loop")
    val death_a = find("tob_xarpus_death_a")
}

object XarpusSpots : SpotanimReferences() {
    val exhumed_gone = find("tob_xarpus_exhumed_end")
    val orb = find("tob_xarpus_exhumed_energyorb")
    val spit = find("tob_xarpus_acidspit")
    val splash = find("tob_xarpus_acidsplash")
    val pebbles = find("tob_xarpus_guano")
}

object XarpusSynths : SynthReferences() {
    val spit = find("tob_xarpus_spit")
    val death = find("tob_xarpus_death")
}

/**
 * Wave 5, Xarpus. Recovery: it starts at three quarters of its hitpoints and does not attack while
 * exhumed rise from the floor and heal it every tick nobody stands on them. Then it rises and spits
 * every 4 ticks at a player: the splat poisons the 3x3 round where it lands, splashes on (to
 * another player or a tile or two nearby) and stays as acidic miasma that hurts 4-8 a tick for the
 * rest of the fight; standing under it hurts too. At 25% it screeches and stares at one quadrant,
 * turning every 8 ticks: each hit from a player in that quadrant comes back as 50-75 poison.
 *
 * Wiki numbers: the exhumed counts (12 for three, 15 for four), 75% start, the counter's 50-75.
 * Guesses (TSPS's): the exhumed timing and heals for other sizes (one to eight), their life, the
 * splash rules, the pauses round the phases, the quadrants' corners.
 */
class XarpusRuntime(env: TobRoomEnv, raid: TobRaid) : TobRoomRuntime(env, raid, TobRoom.Xarpus) {
    enum class Phase {
        Waiting,
        Recovery,
        Poison,
        Counter,
    }

    enum class Quadrant(val corner: CoordGrid) {
        SE(CoordGrid(3177, 4380, 1)),
        SW(CoordGrid(3163, 4380, 1)),
        NW(CoordGrid(3163, 4394, 1)),
        NE(CoordGrid(3177, 4394, 1));

        fun contains(tile: CoordGrid): Boolean {
            val east = tile.x > CENTRE.x
            val north = tile.z > CENTRE.z
            return when (this) {
                SE -> east && !north
                SW -> !east && !north
                NW -> !east && north
                NE -> east && north
            }
        }
    }

    private class Exhumed(val tile: CoordGrid) {
        var age = 0
    }

    var xarpus: Npc? = null
        private set

    var phase = Phase.Waiting
        private set

    var staring: Quadrant? = null
        private set

    private var ticks = 0
    private var exhumedLeft = 0
    private var heal = 0
    private var every = 0
    private var after = 0
    private val exhumed = ArrayList<Exhumed>()
    private val pools = LinkedHashSet<CoordGrid>()
    private var lastTarget: Player? = null
    private var dying = false

    override fun build() {
        val npc = spawn(XarpusNpcs.waiting, SPAWN, scale = true)
        xarpus = npc
        bossBar = npc
    }

    override fun onStart() {
        val npc = xarpus ?: return
        npc.hitpoints = npc.baseHitpointsLvl * START_PERCENT / 100
        transform(npc, XarpusNpcs.feeding)
        val size = teamSize.coerceIn(1, EXHUMED_COUNT.size - 1)
        exhumedLeft = EXHUMED_COUNT[size]
        heal = EXHUMED_HEAL[size]
        every = EXHUMED_EVERY[size]
        after = 0
        ticks = 0
        phase = Phase.Recovery
    }

    override fun bar(): TobBar {
        val npc = xarpus ?: return TobBar.NONE
        return barOf(npc, if (phase == Phase.Recovery) TobBar.GREYED else TobBar.REGULAR)
    }

    override fun onTick() {
        val npc = xarpus ?: return
        if (dying) {
            return
        }
        ticks++
        when (phase) {
            Phase.Recovery -> tickRecovery(npc)
            Phase.Poison -> tickPoison(npc)
            Phase.Counter -> tickCounter(npc)
            Phase.Waiting -> Unit
        }
        if (phase == Phase.Poison || phase == Phase.Counter) {
            tickFloor(npc)
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== xarpus || dying) {
            return
        }
        dying = true
        transform(npc, XarpusNpcs.dead)
        npc.anim(XarpusSeqs.death_a)
        sound(XarpusSynths.death)
        complete()
        later(ticksOf(XarpusSeqs.death_a, least = 4)) { despawn(npc) }
    }

    override fun onComplete() {
        for (tile in pools) {
            removeLoc(tile, LocShape.GroundDecor)
        }
        pools.clear()
        for (entry in exhumed) {
            removeLoc(entry.tile, LocShape.GroundDecor)
        }
        exhumed.clear()
    }

    // Recovery
    // ------------------------------------------------------------------------------------------

    private fun tickRecovery(npc: Npc) {
        if (ticks > PAUSE && exhumedLeft > 0 && ticks % every == 0) {
            raiseExhumed(npc)
        }
        for (entry in exhumed.toList()) {
            tickExhumed(npc, entry)
        }
        if (exhumedLeft == 0 && exhumed.isEmpty() && ++after >= PAUSE) {
            rise(npc)
        }
    }

    private fun raiseExhumed(npc: Npc) {
        exhumedLeft--
        val tile = randomFloor(npc)
        placeLoc(tile, XarpusLocs.exhumed, LocShape.GroundDecor)
        exhumed += Exhumed(tile)
        later(1) { animLoc(tile, XarpusLocs.exhumed, XarpusSeqs.exhumed) }
    }

    private fun tickExhumed(npc: Npc, entry: Exhumed) {
        entry.age++
        if (entry.age > EXHUMED_LIFE) {
            removeLoc(entry.tile, LocShape.GroundDecor)
            exhumed.remove(entry)
            graphic(XarpusSpots.exhumed_gone, entry.tile)
            return
        }
        if (entry.age <= EXHUMED_RISING) {
            return
        }
        if (fightPlayers().any { realOf(it.coords) == entry.tile }) {
            return
        }
        val to = centre(npc)
        val flight =
            projectileBy(
                entry.tile,
                to,
                XarpusSpots.orb,
                0,
                20,
                10,
                heights = 0 to 67,
                arc = EXHUMED_ARC,
                target = npc,
            )
        later((flight - 1).coerceAtLeast(1)) { heal(npc, heal) }
    }

    private fun rise(npc: Npc) {
        transform(npc, XarpusNpcs.fighting)
        npc.anim(XarpusSeqs.rise)
        phase = Phase.Poison
        ticks = 0
        lastTarget = null
    }

    // Poison
    // -------------------------------------------------------------------------------------------

    private fun tickPoison(npc: Npc) {
        if (npc.hitpoints * 100 <= npc.baseHitpointsLvl * COUNTER_BELOW) {
            startCounter(npc)
            return
        }
        if (ticks < PAUSE || ticks % SPIT_EVERY != 0) {
            return
        }
        val players = fightPlayers()
        if (players.isEmpty()) {
            return
        }
        val others = players.filter { it !== lastTarget }
        val target = pick(others.ifEmpty { players })
        lastTarget = target
        npc.facePlayer(target)
        npc.anim(XarpusSeqs.spit)
        sound(XarpusSynths.spit, delay = 30)
        val to = realOf(target.coords) ?: return
        spit(centre(npc), to, target, first = true)
    }

    private fun spit(from: CoordGrid, tile: CoordGrid, target: Player?, first: Boolean) {
        // A slow lob onto the tile, high off Xarpus's mouth and lower for the splash on.
        val flight =
            if (first) {
                projectileBy(from, tile, XarpusSpots.spit, 30, 60, 15, 100 to 0, arc = SPIT_ARC)
            } else {
                projectileBy(from, tile, XarpusSpots.spit, 0, 45, 12, 0 to 0, arc = SPLASH_ARC)
            }
        later(flight) {
            if (!isStarted) {
                return@later
            }
            placePool(tile)
            graphic(XarpusSpots.splash, tile)
            for (player in fightPlayers()) {
                val at = realOf(player.coords) ?: continue
                if (maxOf(kotlin.math.abs(at.x - tile.x), kotlin.math.abs(at.z - tile.z)) <= 1) {
                    hurtPoison(player, roll(SPLAT_MIN, SPLAT_MAX))
                }
            }
            if (!first) {
                return@later
            }
            val others = fightPlayers().filter { it !== target }
            if (others.isNotEmpty() && env.random.of(2) == 0) {
                realOf(pick(others).coords)?.let { spit(tile, it, null, first = false) }
                return@later
            }
            val npc = xarpus ?: return@later
            repeat(roll(1, 2)) { spit(tile, randomFloor(npc), null, first = false) }
        }
    }

    private fun placePool(tile: CoordGrid) {
        if (!pools.add(tile)) {
            return
        }
        placeLoc(tile, XarpusLocs.pool, LocShape.GroundDecor, angleFor(tile))
        later(1) { animLoc(tile, XarpusLocs.pool, XarpusSeqs.splat) }
    }

    private fun tickFloor(npc: Npc) {
        for (player in fightPlayers()) {
            val at = realOf(player.coords) ?: continue
            if (at in pools) {
                hurtPoison(player, roll(POOL_MIN, POOL_MAX))
            }
            if (gap(npc, player.coords) == 0) {
                projectile(centre(npc), at, XarpusSpots.pebbles, 0..20, heights = 40 to 24)
                hurt(player, roll(1, UNDER_MAX))
            }
        }
    }

    // Counter
    // -----------------------------------------------------------------------------------------

    private fun startCounter(npc: Npc) {
        phase = Phase.Counter
        ticks = 0
        npc.resetFaceEntity()
        npc.say("Screeeeech!")
        turn(npc, pick(Quadrant.entries))
    }

    private fun tickCounter(npc: Npc) {
        if (ticks % TURN_TICKS == 0) {
            turn(npc, pick(Quadrant.entries.filter { it != staring }))
        }
    }

    private fun turn(npc: Npc, quadrant: Quadrant) {
        staring = quadrant
        npc.faceSquare(regionOf(quadrant.corner))
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !== xarpus || phase != Phase.Counter || ticks == 0 || source == null) {
            return
        }
        val at = realOf(source.coords) ?: return
        if (staring?.contains(at) == true && source in fightPlayers()) {
            hurtPoison(source, roll(COUNTER_MIN, COUNTER_MAX))
        }
    }

    // Helpers
    // -------------------------------------------------------------------------------------------

    private fun randomFloor(npc: Npc): CoordGrid {
        val arena = checkNotNull(room.arena)
        repeat(50) {
            val tile =
                CoordGrid(roll(arena.minX, arena.maxX), roll(arena.minZ, arena.maxZ), arena.level)
            if (gap(npc, regionOf(tile)) > 0) {
                return tile
            }
        }
        return CoordGrid(arena.minX, arena.minZ, arena.level)
    }

    private fun angleFor(tile: CoordGrid): LocAngle =
        when {
            Quadrant.SE.contains(tile) -> LocAngle.West
            Quadrant.SW.contains(tile) -> LocAngle.North
            Quadrant.NW.contains(tile) -> LocAngle.East
            else -> LocAngle.South
        }

    private fun centre(npc: Npc): CoordGrid =
        (realOf(npc.coords) ?: SPAWN).translate(npc.size / 2, npc.size / 2)

    companion object {
        val SPAWN = CoordGrid(3168, 4385, 1)
        val CENTRE = CoordGrid(3170, 4387, 1)
        const val START_PERCENT = 75

        /** By party size (index 1 to 8): exhumed, heal per tick, ticks between them. */
        val EXHUMED_COUNT = intArrayOf(0, 7, 8, 12, 15, 18, 20, 22, 24)
        val EXHUMED_HEAL = intArrayOf(0, 20, 16, 12, 9, 6, 5, 4, 4)
        val EXHUMED_EVERY = intArrayOf(0, 13, 8, 8, 4, 4, 3, 3, 3)
        private const val EXHUMED_LIFE = 11
        private const val EXHUMED_RISING = 3

        /** The client slopes the exhumed's orbs and the spit fly off at (guesses, by eye). */
        private const val EXHUMED_ARC = 32
        private const val SPIT_ARC = 40
        private const val SPLASH_ARC = 30
        private const val PAUSE = 7
        private const val SPIT_EVERY = 4
        private const val SPLAT_MIN = 2
        private const val SPLAT_MAX = 6
        private const val POOL_MIN = 4
        private const val POOL_MAX = 8
        private const val UNDER_MAX = 9
        const val COUNTER_BELOW = 25
        private const val TURN_TICKS = 8
        const val COUNTER_MIN = 50
        const val COUNTER_MAX = 75
    }
}

class TobXarpusScript @Inject constructor(private val rooms: TobRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Xarpus) { env, raid -> XarpusRuntime(env, raid) }
        tobBosses(
            rooms,
            listOf(XarpusNpcs.waiting, XarpusNpcs.feeding, XarpusNpcs.fighting, XarpusNpcs.dead),
        )
    }
}
