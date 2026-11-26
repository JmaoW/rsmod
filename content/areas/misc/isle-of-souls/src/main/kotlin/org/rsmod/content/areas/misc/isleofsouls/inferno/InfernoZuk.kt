package org.rsmod.content.areas.misc.isleofsouls.inferno

import jakarta.inject.Inject
import kotlin.math.abs
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.npc.hit.healHitpoints
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_spotanims
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.game.map.Direction
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** A volley of lava balls that lands on [tiles] at [hitAt] and hurts whoever stands there. */
class LavaStrike(val hitAt: Int, val tiles: Set<CoordGrid>, val damage: Int)

/**
 * The state of the TzKal-Zuk fight of one run. All the coordinates are real ones inside the run's
 * instance. The Ancestral Glyph slides along the row [shieldZ] between [shieldMinX] and
 * [shieldMaxX] (the south-west tile of its 3x3 footprint); a player is covered while they stand in
 * the northmost three rows of the arena ([coverTopZ] downwards) within two tiles of its middle.
 */
class ZukFight(
    val zuk: Npc,
    val glyph: Npc,
    val shieldMinX: Int,
    val shieldMaxX: Int,
    val shieldZ: Int,
    val coverTopZ: Int,
    startedAt: Int,
) {
    var shieldDirection = 1

    /** The x the glyph was last sent walking to, or `null` while it rests at an end. */
    var shieldWalkingTo: Int? = null

    /** Where the glyph was last tick and for how many ticks it has not moved while walking. */
    var shieldLastX = Int.MIN_VALUE
    var shieldStill = 0

    /** How many times in a row walking was tried again because the glyph did not move. */
    var shieldStalls = 0

    /** The prison walls that are cracking and still to turn to rubble. */
    var crumbles: List<Crumble> = emptyList()
    var shieldMoveAt = startedAt + InfernoZuk.SHIELD_START_DELAY
    var nextAttackAt = startedAt + InfernoZuk.FIRST_ATTACK_DELAY

    /** When the next Jal-Xil and Jal-Zek set arrives (frozen while [timerPaused]). */
    var nextSetAt = InfernoZuk.NEVER

    /**
     * The first set waits for the glyph to do one full rotation: it comes when the glyph reaches
     * the end it touched first for the second time (the third end it touches), see [shieldEnds].
     */
    var firstSetPending = true

    /** How many ends of its path the glyph has reached so far. */
    var shieldEnds = 0
    var timerPaused = false

    /** Ticks left on the set timer while it is paused, with the one-off extra already added. */
    var pausedRemaining = 0
    var interludeStarted = false
    var jadSpawned = false
    var enraged = false
    var shieldGone = false

    /** The four Jal-MejJak healers of the enrage. */
    val healers = ArrayList<Npc>()
    val lava = ArrayList<LavaStrike>()
}

/**
 * The TzKal-Zuk fight, the Inferno's last wave. Zuk stands in the north wall and fires at every
 * player, and a glyph slides back and forth in front of the players: a player behind it is not hit.
 * Sets of monsters arrive on a timer and attack the glyph until a player draws them off. Zuk fights
 * in phases: his own Jad at 480 health, healers and a faster attack below 240.
 *
 * It is driven by [Inferno]: [start] when the last wave begins, [tick] every tick after.
 */
class InfernoZuk
@Inject
constructor(
    private val instances: TeamInstances,
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val mapClock: MapClock,
    private val worldRepo: WorldRepository,
    private val attacks: NpcAttacks,
    private val prison: InfernoPrison,
) {
    /** Tells [player] whether the prison roof is still on (it comes off when Zuk breaks out). */
    fun showTo(player: Player, run: InfernoRun) {
        prison.showTo(player, run)
    }

    /** Whether [player] stands behind the glyph, where Zuk cannot hit them. */
    fun isCovered(fight: ZukFight, player: Player): Boolean {
        if (fight.shieldGone) {
            return false
        }
        val middle = fight.glyph.coords.x + GLYPH_SIZE / 2
        val z = player.coords.z
        return abs(player.coords.x - middle) <= COVER_REACH &&
            z in fight.coverTopZ - COVER_DEPTH + 1..fight.coverTopZ
    }

    /** Brings Zuk and the glyph out and puts the players in front of them. */
    fun start(inferno: Inferno, run: InfernoRun, inside: List<Player>) {
        val region = run.instance.region
        for ((index, player) in inside.withIndex()) {
            val dest = CoordGrid(InfernoArena.zukStart.x + 2 * index, InfernoArena.zukStart.z, 0)
            PathingEntityCommon.telejump(player, collision, region.normal[dest])
        }

        val west = region.normal[InfernoArena.glyphWest]
        val zuk = Npc(npcTypes[inferno_npcs.zuk], region.normal[InfernoArena.zukSouthWest])
        zuk.mode = NpcMode.None
        // He appears facing south and never turns (his type cannot rotate, so this is what he
        // keeps). The direction has to be set before he is added: that is when the client is told.
        zuk.respawnDir = Direction.South
        npcRepo.add(zuk, WaveDirector.LIFETIME)
        zuk.faceDirection(Direction.South)
        zuk.anim(inferno_seqs.zuk_spawn)

        val glyphStart = CoordGrid(west.x + (SHIELD_TRAVEL / 2), west.z, west.level)
        val glyph = Npc(npcTypes[inferno_npcs.glyph], glyphStart)
        glyph.mode = NpcMode.None
        glyph.hideAllOps()
        npcRepo.add(glyph, WaveDirector.LIFETIME)

        run.extras += zuk
        run.extras += glyph
        run.zukFight =
            ZukFight(
                zuk = zuk,
                glyph = glyph,
                shieldMinX = west.x,
                shieldMaxX = west.x + SHIELD_TRAVEL,
                shieldZ = west.z,
                coverTopZ = west.z - 1,
                startedAt = mapClock.cycle,
            )
        run.zukFight?.let { it.crumbles = prison.open(run, mapClock.cycle) }
        inside.forEach {
            prison.showTo(it, run)
            it.mes("<col=ef1020>TzKal-Zuk breaks free of the wall!")
        }
    }

    fun tick(inferno: Inferno, run: InfernoRun) {
        val fight = run.zukFight ?: return
        val now = mapClock.cycle
        if (fight.crumbles.isNotEmpty()) {
            fight.crumbles = prison.settle(fight.crumbles, now)
        }
        moveShield(fight, now)
        minionsAttackShield(run, fight, now)
        phases(inferno, run, fight, now)
        healersAndLava(run, fight, now)
        zukAttacks(run, fight, now)
    }

    /**
     * The glyph walks along the row, a tile a tick, and rests a while at each end. It is sent
     * walking (so the client slides it from tile to tile) to the far end in one go and turned round
     * when it gets there.
     */
    private fun moveShield(fight: ZukFight, now: Int) {
        if (fight.shieldGone || now < fight.shieldMoveAt) {
            return
        }
        val glyph = fight.glyph
        val end = if (fight.shieldDirection > 0) fight.shieldMaxX else fight.shieldMinX
        if (glyph.coords.x == end) {
            fight.shieldEnds++
            if (fight.firstSetPending && fight.shieldEnds >= FIRST_SET_END_TOUCHES) {
                fight.firstSetPending = false
                fight.nextSetAt = now
            }
            fight.shieldDirection = -fight.shieldDirection
            fight.shieldMoveAt = now + SHIELD_PAUSE
            fight.shieldWalkingTo = null
            return
        }
        if (glyph.coords.x == fight.shieldLastX) {
            fight.shieldStill++
        } else {
            fight.shieldStill = 0
            if (fight.shieldStalls < SHIELD_STALLS_BEFORE_JUMP) {
                fight.shieldStalls = 0
            }
        }
        fight.shieldLastX = glyph.coords.x
        if (fight.shieldStalls >= SHIELD_STALLS_BEFORE_JUMP) {
            // Walking does not work here: it must never freeze, so it hops a tile a tick instead.
            val x = glyph.coords.x + fight.shieldDirection
            glyph.telejump(collision, CoordGrid(x, glyph.coords.z, glyph.coords.level))
            return
        }
        if (fight.shieldWalkingTo != end || fight.shieldStill >= SHIELD_STALL_TICKS) {
            // Also sent again if something stopped it on the way.
            if (fight.shieldStill >= SHIELD_STALL_TICKS) {
                fight.shieldStalls++
            }
            fight.shieldWalkingTo = end
            fight.shieldStill = 0
            glyph.walk(CoordGrid(end, glyph.coords.z, glyph.coords.level))
        }
    }

    /**
     * Every monster that has not been hit yet attacks the glyph, with its own attack and its own
     * damage roll against the glyph's defence, at its own pace. A monster that is hit goes for the
     * players; when the glyph is gone they all do.
     */
    private fun minionsAttackShield(run: InfernoRun, fight: ZukFight, now: Int) {
        for (npc in run.monsters.toList()) {
            val state = run.states[npc] ?: continue
            if (!state.shieldAttacker) {
                continue
            }
            if (state.watch(npc) || fight.shieldGone) {
                state.shieldAttacker = false
                continue
            }
            if (now < npc.actionDelay) {
                continue
            }
            val def = InfernoMonsterDefs.of(npc.type) ?: continue
            val attack = shieldAttack(def) ?: continue
            npc.anim(attack.anim ?: continue)
            val shot = attack.projectile
            val flight = attack.projanim
            if (shot != null && flight != null) {
                worldRepo.projAnim(npc, fight.glyph, shot, flight)
            }
            val damage = attacks.rollDamage(npc, fight.glyph, attack)
            npc.actionDelay =
                now + (if (InfernoMonsterDefs.isJad(def)) state.jadRate else def.attackRate)
            hitShield(run, fight, damage)
        }
    }

    private fun shieldAttack(def: InfernoMonsterDef): NpcAttack? =
        when {
            def.kind == InfernoMonster.Ranger -> InfernoAttacks.rangerRanged
            def.kind == InfernoMonster.Mager -> InfernoAttacks.magerMagic
            InfernoMonsterDefs.isJad(def) ->
                if (random.of(2) == 0) InfernoAttacks.jadMagic else InfernoAttacks.jadRanged
            else -> null
        }

    private fun hitShield(run: InfernoRun, fight: ZukFight, damage: Int) {
        if (fight.shieldGone || damage <= 0) {
            return
        }
        val glyph = fight.glyph
        glyph.hitpoints = maxOf(0, glyph.hitpoints - damage)
        if (glyph.hitpoints > 0) {
            return
        }
        fight.shieldGone = true
        if (fight.firstSetPending) {
            // With no glyph left to rotate, the first set comes straight away.
            fight.firstSetPending = false
            fight.nextSetAt = mapClock.cycle
        }
        run.extras.remove(glyph)
        if (glyph.isSlotAssigned) {
            npcRepo.del(glyph, Int.MAX_VALUE)
        }
        instances.playersIn(run.instance).forEach {
            it.mes("<col=ef1020>The Ancestral Glyph has been destroyed!")
        }
    }

    /**
     * The set timer, Jad and the enrage. At 600 health the timer stops and gets a one-off extra
     * 1:45; at 480 Zuk's own Jad arrives and the timer carries on; below 240 the healers come.
     */
    private fun phases(inferno: Inferno, run: InfernoRun, fight: ZukFight, now: Int) {
        val zuk = fight.zuk
        if (!fight.interludeStarted && zuk.hitpoints <= PAUSE_AT) {
            fight.interludeStarted = true
            fight.timerPaused = true
            val untilSet = if (fight.firstSetPending) 0 else maxOf(0, fight.nextSetAt - now)
            fight.pausedRemaining = untilSet + INTERLUDE_EXTRA
        }
        if (!fight.jadSpawned && zuk.hitpoints <= JAD_AT) {
            fight.jadSpawned = true
            inferno.spawnMinion(
                run,
                InfernoMonsterDefs.jadFinal,
                InfernoArena.zukJad,
                shield = true,
            )
            if (fight.timerPaused) {
                fight.timerPaused = false
                fight.nextSetAt = now + fight.pausedRemaining
            }
        }
        if (!fight.timerPaused && now >= fight.nextSetAt) {
            fight.nextSetAt = now + SET_INTERVAL
            inferno.spawnMinion(run, InfernoMonsterDefs.rangerFinal, InfernoArena.zukRanger, true)
            inferno.spawnMinion(run, InfernoMonsterDefs.magerFinal, InfernoArena.zukMager, true)
        }
        if (!fight.enraged && zuk.hitpoints <= ENRAGE_AT) {
            fight.enraged = true
            summonHealers(run, fight)
        }
    }

    private fun summonHealers(run: InfernoRun, fight: ZukFight) {
        val region = run.instance.region
        for (spot in InfernoArena.mejjakSpots) {
            val healer = Npc(npcTypes[inferno_npcs.zuk_healer], region.normal[spot])
            healer.mode = NpcMode.None
            npcRepo.add(healer, WaveDirector.LIFETIME)
            fight.healers += healer
            run.extras += healer
            run.states[healer] = InfernoMonsterState(mapClock.cycle).also { it.watch(healer) }
            // They stand with their backs to the arena, facing Zuk, until they are hit.
            healer.faceDirection(Direction.North)
        }
    }

    /**
     * A Jal-MejJak that nobody has hit heals Zuk for 15 to 24 every 3 ticks. One that has been hit
     * stops healing, turns round to face the arena and throws lava onto the ground in front of it
     * (not at a player): every 3 ticks a volley that lands a moment later on four tiles south of
     * it.
     */
    private fun healersAndLava(run: InfernoRun, fight: ZukFight, now: Int) {
        val zuk = fight.zuk
        val players = instances.playersIn(run.instance).filter { it.isValidTarget() }
        for (healer in fight.healers.toList()) {
            if (!healer.isSlotAssigned || healer.hitpoints <= 0) {
                continue
            }
            val hit = run.states[healer]?.watch(healer) ?: false
            if (now < healer.actionDelay) {
                continue
            }
            healer.actionDelay = now + HEALER_RATE
            if (!hit) {
                if (zuk.hitpoints < zuk.baseHitpointsLvl) {
                    zuk.spotanim(cave_spotanims.heal)
                    val heal = HEAL_MIN + random.of(HEAL_SPREAD)
                    zuk.healHitpoints(heal)
                }
            } else if (players.isNotEmpty()) {
                // Hit, it turns round from Zuk to face the arena and throws its lava there.
                healer.faceDirection(Direction.South)
                healer.anim(inferno_seqs.mejjak_attack)
                fight.lava += lavaStrike(healer, now)
            }
        }

        val landed = fight.lava.filter { now >= it.hitAt }
        fight.lava.removeAll(landed.toSet())
        for (strike in landed) {
            for (player in players) {
                if (player.coords in strike.tiles) {
                    player.queueHit(delay = 1, HitType.Typeless, strike.damage)
                }
            }
        }
    }

    /**
     * A volley of lava balls from [healer]: they fly to tiles on the ground in front of it (south,
     * the way it turned round to face when it was hit), not to any player. Whoever stands on a tile
     * when its ball lands is hurt.
     */
    private fun lavaStrike(healer: Npc, now: Int): LavaStrike {
        val tiles = HashSet<CoordGrid>()
        repeat(LAVA_TILES) {
            val dx = random.of(-LAVA_SPREAD..LAVA_SPREAD)
            val dz = random.of(LAVA_NEAR..LAVA_FAR)
            tiles += CoordGrid(healer.coords.x + dx, healer.coords.z - dz, healer.coords.level)
        }
        // A lava ball flies from the healer to each tile and splashes where it lands.
        var flightTicks = LAVA_DELAY
        for (tile in tiles) {
            val flight =
                worldRepo.projAnim(healer, tile, inferno_spotanims.lava_ball, projanims.magic_spell)
            worldRepo.spotanimMap(inferno_spotanims.lava_splash, tile, delay = flight.endTime)
            flightTicks = maxOf(flightTicks, flight.durations.serverDelay)
        }
        return LavaStrike(now + flightTicks, tiles, LAVA_MIN + random.of(LAVA_SPREAD_DAMAGE))
    }

    /**
     * Zuk fires at every player every 10 ticks (7 once enraged). A player behind the glyph is not
     * hit: the shot goes into the glyph. Anyone else takes typeless damage, which no prayer stops.
     */
    private fun zukAttacks(run: InfernoRun, fight: ZukFight, now: Int) {
        if (now < fight.nextAttackAt) {
            return
        }
        val zuk = fight.zuk
        fight.nextAttackAt = now + if (fight.enraged) ENRAGED_RATE else ATTACK_RATE
        val players = instances.playersIn(run.instance).filter { it.isValidTarget() }
        if (players.isEmpty()) {
            return
        }
        zuk.anim(inferno_seqs.zuk_attack)
        for (player in players) {
            if (isCovered(fight, player)) {
                worldRepo.projAnim(
                    zuk,
                    fight.glyph,
                    inferno_spotanims.zuk_shot,
                    projanims.magic_spell,
                )
                continue
            }
            val flight =
                worldRepo.projAnim(zuk, player, inferno_spotanims.zuk_shot, projanims.magic_spell)
            val delay = maxOf(1, flight.durations.serverDelay)
            player.queueHit(delay, HitType.Typeless, random.of(0..MAX_HIT))
        }
    }

    companion object {
        /** The wiki: Zuk has 1200 hitpoints; the phases are measured against these. */
        const val PAUSE_AT = 600
        const val JAD_AT = 480
        const val ENRAGE_AT = 240

        /** The wiki: a max hit of 148, one attack every 10 ticks and every 7 once enraged. */
        const val MAX_HIT = 148
        const val ATTACK_RATE = 10
        const val ENRAGED_RATE = 7

        /** How long after the fight starts Zuk first fires. The wiki gives nothing: a guess. */
        const val FIRST_ATTACK_DELAY = 12

        /** The glyph is 3x3. */
        const val GLYPH_SIZE = 3

        /** The glyph covers 5 tiles across (2 either side of its middle) and 3 rows deep (wiki). */
        const val COVER_REACH = 2
        const val COVER_DEPTH = 3

        /**
         * The glyph walks a tile a tick ("a walking pace") and rests 5 ticks at each end (wiki).
         * Its speed is not given, so one tile a tick is a guess.
         */
        const val SHIELD_PAUSE = 5

        /** The glyph is sent walking again if it has not moved for this many ticks. */
        const val SHIELD_STALL_TICKS = 3

        /** After this many walks that went nowhere the glyph hops instead. */
        const val SHIELD_STALLS_BEFORE_JUMP = 2
        const val SHIELD_START_DELAY = 8

        /** The glyph's south-west tile runs over this many tiles, from x 2257 to 2283. */
        const val SHIELD_TRAVEL = 26

        /** The wiki: a set every 3:30 (350 ticks), and a one-off extra 1:45 (175 ticks). */
        const val SET_INTERVAL = 350
        const val INTERLUDE_EXTRA = 175

        /**
         * The first set comes when the glyph reaches the first end it touched for the second time:
         * the third end it touches (user, from the real fight).
         */
        const val FIRST_SET_END_TOUCHES = 3

        /** A set time that never comes: the first set is not on a timer yet. */
        const val NEVER = Int.MAX_VALUE / 2

        /** The wiki: a healer heals Zuk 15 to 24 every 3 ticks, until it is attacked. */
        const val HEAL_MIN = 15
        const val HEAL_SPREAD = 10
        const val HEALER_RATE = 3

        /** The wiki: lava balls do 5 to 10 (`5 + 0..5`). The pattern and delay are guesses. */
        const val LAVA_MIN = 5
        const val LAVA_SPREAD_DAMAGE = 6
        const val LAVA_DELAY = 2
        const val LAVA_SPREAD = 1
        const val LAVA_TILES = 4

        /** The balls land 3 to 8 tiles south of the healer: the near rows of the arena. */
        const val LAVA_NEAR = 3
        const val LAVA_FAR = 8
    }
}
