package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object BloatNpcs : NpcReferences() {
    val bloat = find("tob_bloat")
}

object BloatSeqs : SeqReferences() {
    val sleep = find("tob_bloat_sleep")
    val flies = find("tob_bloat_flies_large")
    val death = find("tob_bloat_death")
}

object BloatSpots : SpotanimReferences() {
    val flies = find("tob_bloat_flies_large")
    val fly_impact = find("tob_bloat_flies_small")
    val flesh =
        listOf(
            find("tob_bloat_falling_flesh1"),
            find("tob_bloat_falling_flesh2"),
            find("tob_bloat_falling_flesh3"),
            find("tob_bloat_falling_flesh4"),
        )
    val stunned = find("tob_bloat_stunned")
}

object BloatSynths : SynthReferences() {
    val death = find("tob_bloat_death")
}

/**
 * Wave 2, the Pestilent Bloat. It walks a loop round the tank in the middle of the room, now and
 * then turning round (at most once every 32 ticks). While it walks, flies bite everyone it can see
 * for up to 20 every tick (Protect from Missiles takes a quarter off) and hits on it are halved.
 * After 34-42 ticks of walking (38-46 the first time, four more if nobody hit it while it slept) it
 * shuts down for 32 ticks; on the 30th it stomps anyone under or beside it for 40-80 and its
 * Defence comes back. Below 90% hands fall while it walks (marked by flesh; 30-50 and a short
 * stun); from 60% to 40% it runs, and below 40% each hit switches it between walking and running.
 *
 * Wiki numbers. Guesses (TSPS's too): the loop's corners, sixteen hands every five ticks landing
 * three ticks after their marks, the turn chance, the stun's length; flies also bite whoever stands
 * beside a bitten player.
 */
class BloatRuntime(env: TobRoomEnv, raid: TobRaid) : TobRoomRuntime(env, raid, TobRoom.Bloat) {
    var bloat: Npc? = null
        private set

    var asleep = false
        private set

    private var walkLeft = 0
    private var sleepTicks = 0
    private var turnCooldown = TURN_COOLDOWN
    private var sinceTurn = 99
    private var corner = 3
    private var reverse = false
    private var hitWhileAsleep = true
    private var running = false
    private var handTicks = 0
    private var hands: List<CoordGrid>? = null
    private var lastHands: List<CoordGrid> = emptyList()
    private var dying = false

    override fun build() {
        val npc = spawn(BloatNpcs.bloat, START, scale = true, ignoreCollision = true)
        bloat = npc
        bossBar = npc
        walkLeft = roll(FIRST_WALK.first, FIRST_WALK.last)
        npc.walk(regionOf(CORNERS[corner]))
    }

    override fun onIdle() {
        bloat?.takeIf { !dying }?.let { walk(it, started = false) }
    }

    override fun onTick() {
        val npc = bloat ?: return
        if (dying) {
            return
        }
        if (asleep) {
            tickAsleep(npc)
            return
        }
        walk(npc, started = true)
        flies(npc)
        tickHands(npc)
        if (--walkLeft <= 0 && sinceTurn >= AFTER_TURN) {
            fallAsleep(npc)
        }
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !== bloat) {
            return
        }
        if (asleep) {
            hitWhileAsleep = true
            return
        }
        hit.damage = hit.damage / 2
        if (npc.hitpoints * 100 < npc.baseHitpointsLvl * RUN_SWITCH_BELOW) {
            running = !running
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== bloat || dying) {
            return
        }
        dying = true
        npc.resetMovement()
        npc.anim(BloatSeqs.death)
        sound(BloatSynths.death)
        complete()
        later(ticksOf(BloatSeqs.death, least = 4)) { despawn(npc) }
    }

    // Walking
    // ------------------------------------------------------------------------------------------

    private fun walk(npc: Npc, started: Boolean) {
        val share = npc.hitpoints * 100 / npc.baseHitpointsLvl.coerceAtLeast(1)
        val runs = started && share < RUN_BELOW && (share >= RUN_SWITCH_BELOW || running)
        npc.defaultMoveSpeed = if (runs) MoveSpeed.Run else MoveSpeed.Walk
        sinceTurn++
        if (started && --turnCooldown <= 0 && chance(TURN_ONE_IN)) {
            turnCooldown = TURN_COOLDOWN
            sinceTurn = 0
            reverse = !reverse
            corner = (corner + if (reverse) 1 else 3) % 4
            npc.walk(regionOf(CORNERS[corner]))
            return
        }
        val target = regionOf(CORNERS[corner])
        if (npc.coords == target) {
            corner = (corner + if (reverse) 3 else 1) % 4
            npc.walk(regionOf(CORNERS[corner]))
        } else if (npc.routeDestination.isEmpty()) {
            npc.walk(target)
        }
    }

    // Flies
    // --------------------------------------------------------------------------------------------

    private fun flies(npc: Npc) {
        val players = fightPlayers()
        val seen = players.filter { canSee(npc, it.coords) }
        if (seen.isEmpty()) {
            return
        }
        npc.anim(BloatSeqs.flies)
        val bitten = LinkedHashSet(seen)
        for (player in seen) {
            for (other in players) {
                if (other !== player && near(player, other)) {
                    bitten += other
                }
            }
        }
        val from = centre(npc)
        for (player in bitten) {
            val to = realOf(player.coords) ?: continue
            projectile(from, to, BloatSpots.flies, 0..30, heights = 0 to 0)
            val damage = roll(1, FLIES_MAX)
            later(1) {
                if (player.isSlotAssigned && player in fightPlayers()) {
                    player.spotanim(BloatSpots.fly_impact)
                    val dealt = if (prays(player, NpcAttackStyle.Ranged)) damage * 3 / 4 else damage
                    hurt(player, dealt)
                }
            }
        }
    }

    private fun near(a: Player, b: Player): Boolean =
        maxOf(kotlin.math.abs(a.coords.x - b.coords.x), kotlin.math.abs(a.coords.z - b.coords.z)) <=
            1

    // Sleeping
    // ------------------------------------------------------------------------------------------

    private fun fallAsleep(npc: Npc) {
        asleep = true
        sleepTicks = 0
        hitWhileAsleep = false
        hands = null
        npc.resetMovement()
        npc.anim(BloatSeqs.sleep)
    }

    private fun tickAsleep(npc: Npc) {
        sleepTicks++
        if (sleepTicks == STOMP_AT) {
            for (player in fightPlayers()) {
                if (gap(npc, player.coords) <= 1) {
                    hurt(player, roll(STOMP_MIN, STOMP_MAX))
                }
            }
        }
        if (sleepTicks < SLEEP_TICKS) {
            return
        }
        asleep = false
        walkLeft = roll(LATER_WALK.first, LATER_WALK.last) + if (hitWhileAsleep) 0 else UNHIT_EXTRA
        npc.walk(regionOf(CORNERS[corner]))
    }

    // Hands
    // -------------------------------------------------------------------------------------------

    private fun tickHands(npc: Npc) {
        if (npc.hitpoints * 100 >= npc.baseHitpointsLvl * HANDS_BELOW) {
            return
        }
        handTicks++
        hands?.let { if (handTicks == HANDS_LAND_AFTER) landHands(it) }
        if (handTicks < HANDS_EVERY) {
            return
        }
        handTicks = 0
        val tiles = LinkedHashSet<CoordGrid>()
        var tries = 0
        while (tiles.size < HANDS_COUNT && tries++ < HANDS_COUNT * 20) {
            val tile = CoordGrid(roll(FLOOR.minX, FLOOR.maxX), roll(FLOOR.minZ, FLOOR.maxZ), 0)
            if (tile !in TANK && tile !in lastHands) {
                tiles += tile
            }
        }
        hands = tiles.toList()
        lastHands = tiles.toList()
        for (tile in tiles) {
            graphic(pick(BloatSpots.flesh), tile)
        }
    }

    private fun landHands(tiles: List<CoordGrid>) {
        for (player in fightPlayers()) {
            val at = realOf(player.coords) ?: continue
            if (at !in tiles) {
                continue
            }
            hurt(player, roll(HANDS_MIN, HANDS_MAX))
            player.spotanim(BloatSpots.stunned, height = 92)
            player.abortRoute()
            player.freeze(HANDS_STUN)
        }
    }

    private fun centre(npc: Npc): CoordGrid =
        (realOf(npc.coords) ?: START).translate(npc.size / 2, npc.size / 2)

    companion object {
        /**
         * The loop's corners (its south-west tile), north-west, south-west, south-east, north-east.
         */
        val CORNERS =
            listOf(
                CoordGrid(3288, 4451, 0),
                CoordGrid(3288, 4440, 0),
                CoordGrid(3299, 4440, 0),
                CoordGrid(3299, 4451, 0),
            )
        val START = CoordGrid(3299, 4445, 0)
        val FLOOR = TobBox(3288, 4440, 3303, 4455)
        val TANK = TobBox(3293, 4445, 3298, 4450)

        val FIRST_WALK = 38..46
        val LATER_WALK = 34..42
        private const val UNHIT_EXTRA = 4
        private const val AFTER_TURN = 5
        const val SLEEP_TICKS = 32
        const val STOMP_AT = 30
        private const val STOMP_MIN = 40
        private const val STOMP_MAX = 80
        private const val TURN_COOLDOWN = 32
        private const val TURN_ONE_IN = 20
        const val FLIES_MAX = 20
        private const val HANDS_BELOW = 90
        private const val HANDS_COUNT = 16
        private const val HANDS_EVERY = 5
        private const val HANDS_LAND_AFTER = 3
        private const val HANDS_MIN = 30
        private const val HANDS_MAX = 50
        private const val HANDS_STUN = 5
        private const val RUN_BELOW = 60
        private const val RUN_SWITCH_BELOW = 40
    }
}

class TobBloatScript @Inject constructor(private val rooms: TobRoomRegistry) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Bloat) { env, raid -> BloatRuntime(env, raid) }
        tobBosses(rooms, listOf(BloatNpcs.bloat))
    }
}
