package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varps
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.stat
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid

typealias baba_npcs = BaBaNpcs

typealias baba_locs = BaBaLocs

typealias baba_seqs = BaBaSeqs

typealias baba_spotanims = BaBaSpotanims

object BaBaNpcs : NpcReferences() {
    val baba = find("toa_baba")

    /** Ba-Ba crouched on her throne, throwing boulders (her idle is the throwing stance). */
    val digging = find("toa_baba_digging")
    val baboon = find("toa_baba_baboon")
    val boulder = find("toa_baba_boulder")
    val boulder_cracked = find("toa_baba_boulder_weak")

    /** The debris piles, from whole to nearly gone. */
    val rubble =
        listOf(
            find("toa_baba_rubble"),
            find("toa_baba_rubble_2"),
            find("toa_baba_rubble_3"),
            find("toa_baba_rubble_4"),
        )
}

object BaBaLocs : LocReferences() {
    val sarcophagus = find("toa_baba_coffin_closed")
    val sarcophagus_open = find("toa_baba_coffin_open")
    val banana_peel = find("toa_baba_banana_peel")

    /** What a debris pile stands on, so nothing walks through it. */
    val rubble_block = find("invisible_type8_blocking_size3")
}

object BaBaSeqs : SeqReferences() {
    val attack = find("npc_baba_attack_melee")
    val throw_rock = find("npc_mandrill_attack_ranged_01")
    val fly = find("npc_mandrill_attack_special_jump01")
    val throw_boulders = find("npc_mandrill_attack_special_ranged01")
    val spawn = find("npc_mandrill_spawn01")
    val baboon_spawn = find("npc_mandrill_spawn02")
    val baboon_ranged = find("npc_mandrill_attack_ranged_02")
    val baboon_melee = find("npc_mandrill_attack_melee")
    val death = find("npc_mandrill_despawn01")
    val player_flyback = find("human_troll_flyback_merge")
    val player_pit = find("quest_lunar_falling_on_platform")
    val player_slip = find("royal_human_slip_fall")
    val player_knocked = find("human_knockdown")
}

object BaBaSpotanims : SpotanimReferences() {
    val shadow_end = find("darkbow_smoke_arrow_impact")
    val shadow_slow = find("gargboss_debris_shadow_120")
    val shadow_fast = find("gargboss_debris_shadow_60")
    val shadow_ring = find("gargboss_debris_shadow_90")
    val rock_fall =
        listOf(
            find("toa_baba_rock_fall"),
            find("toa_baba_rock_fall_faster"),
            find("toa_baba_rock_fall_fastest"),
        )
    val rock_travel = find("toa_baba_ranged_travel")
    val rock_impact = find("earthsurge_impact")
    val boulder_travel = find("toa_baba_balls_travel")
    val skull = find("toa_baba_skull_proj")
    val sarcophagus_burst = find("firebolt_impact")
    val skull_impact = find("wild_falloff_meteor_blast_red")
    val baboon_travel = find("toa_baboon_ranged_travel_2")
    val slip = find("tob_bloat_stunned")
    val stun = find("stunned_thieving")
}

/**
 * Ba-Ba, Ape-ex Predator. She swings at the player with the least health, slams the ground in a
 * cross around her target every few swings (or quickly when they stand under her), and drops rubble
 * on everyone before throwing a great rock at them: standing beside a debris pile, a closed
 * sarcophagus or a baboon soaks it all. Below 75% baboons come and break the sarcophagi open, which
 * then spit skulls. At 66% and 33% she knocks the party to the bottom of the slope and rolls ten
 * waves of boulders at them, one cracked in each wave.
 *
 * The wiki gives her stats, the melee fully blocked by prayer, the cover rule and costs (10, 30,
 * 12), the debris health, the boulders' health table and cracked boulder, the invocations and her
 * targeting. Timings, max hits other than her melee, the slam pattern and the sarcophagus volleys
 * are TSPS's (Near-Reality).
 */
class BaBaRuntime(env: ToaRoomEnv, raid: ToaRaid) : ToaRoomRuntime(env, raid, ToaRoom.ApmekenBoss) {
    private class Rubble(val npc: Npc, val tile: CoordGrid) {
        var pending = 0
    }

    private class Sarcophagus(val tile: CoordGrid, val north: Boolean) {
        var health = SARCOPHAGUS_HEALTH
        var open = false
        var pending = 0
    }

    private class Slam(val centre: CoordGrid, val fast: Boolean, var ticks: Int)

    private class Step(val delay: Int, val action: () -> Unit)

    var baba: Npc? = null
        private set

    /** The tests turn her own swinging off to work on one rule at a time. */
    var fightEnabled = true

    private var nextAttack = 0
    private var slamCycle = 2
    private var slam: Slam? = null
    private var underneath = 2
    private var rockThrow = 0
    private var rockImpact = 0
    private var baboonTicks = -1
    private var phases = 0

    /**
     * The threshold she was just brought to, held until her boulder special visibly begins (she
     * leaps for her throne): no damage takes her below it during the wind-up. -1 when none.
     */
    private var heldFloor = -1

    /** Whether she is held at the threshold she reached, her special not yet begun. */
    val holdsThreshold: Boolean
        get() = heldFloor >= 0

    /** How many boulder phases have begun. */
    val boulderPhases: Int
        get() = phases

    private var babbonsUnlocked = false
    private var cracked = 2
    private var rubbleTicks = 0
    private val rockTiles = ArrayList<CoordGrid>()
    private val nextRubble = arrayOfNulls<CoordGrid>(2)
    private val rubble = arrayOfNulls<Rubble>(2)
    private val sarcophagi = SARCOPHAGI.mapIndexed { i, tile -> Sarcophagus(tile, i < 4) }
    private val baboons = arrayOfNulls<Npc>(4)
    private val baboonShots = HashMap<Npc, Int>()

    /** What sheltering behind each baboon will cost it when the rock lands. */
    private val baboonCover = HashMap<Npc, Int>()
    private val boulders = LinkedHashMap<Npc, Boolean>()

    /**
     * The damage on its way to each boulder (hits are worked out when they are queued), and the
     * boulders it will finish: those roll on until the hit lands but no longer collide.
     */
    private val boulderIncoming = HashMap<Npc, Int>()
    private val doomedBoulders = HashSet<Npc>()
    private val peels = HashSet<CoordGrid>()
    private val stunnedUntil = HashMap<Int, Int>()
    private var script = ArrayDeque<Step>()
    private var scriptDelay = 0

    val inBoulderPhase: Boolean
        get() = script.isNotEmpty()

    val boulderCount: Int
        get() = boulders.size

    val rubbleCount: Int
        get() = rubble.count { it != null }

    fun boulderNpcs(): List<Npc> = boulders.keys.toList()

    /** Whether [npc] is a boulder that a hit on its way will break (it no longer collides). */
    fun isBoulderDoomed(npc: Npc): Boolean = npc in doomedBoulders

    fun openSarcophagi(): Int = sarcophagi.count { it.open }

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        phases = 0
        heldFloor = -1
        babbonsUnlocked = false
        val boss = spawn(baba_npcs.baba, SPAWN, points = BABA_POINTS, facing = Direction.West)
        baba = boss
    }

    override fun onStart() {
        val boss = baba ?: return
        boss.anim(baba_seqs.spawn)
        bossBar = boss
        nextAttack = now + FIRST_ATTACK
        slamCycle = 2
        underneath = 2
        rockThrow = 0
        rockImpact = 0
        baboonTicks = -1
        cracked = 1 + env.random.of(3)
    }

    override fun onComplete() {
        clearAdds()
        restoreLocs()
    }

    override fun onReset() {
        clearAdds()
        restoreLocs()
        for (sarcophagus in sarcophagi) {
            sarcophagus.health = SARCOPHAGUS_HEALTH
            sarcophagus.open = false
            sarcophagus.pending = 0
        }
        baba?.let(::despawn)
        stunnedUntil.clear()
        build()
    }

    private fun clearAdds() {
        for (side in 0..1) {
            rubble[side]?.let(::removeRubble)
            rubble[side] = null
            nextRubble[side] = null
        }
        for (index in baboons.indices) {
            baboons[index]?.let(::despawn)
            baboons[index] = null
        }
        baboonShots.clear()
        baboonCover.clear()
        for (boulder in boulders.keys.toList()) {
            despawn(boulder)
        }
        boulders.clear()
        boulderIncoming.clear()
        doomedBoulders.clear()
        for (tile in peels.toList()) {
            removeLoc(tile, LocShape.GroundDecor)
        }
        peels.clear()
        rockTiles.clear()
        rubbleTicks = 0
        slam = null
        script.clear()
    }

    /**
     * The least health a hit may leave her on: the threshold she just reached until her special
     * visibly begins, otherwise her next boulder threshold, then (while the last boulders roll) one
     * hitpoint; nothing once no phase is left. A threshold reached while boulders roll waits for
     * them to stop (wiki).
     */
    private fun floorHp(npc: Npc): Int =
        when {
            heldFloor >= 0 -> heldFloor
            phases < BOULDER_THRESHOLDS.size ->
                npc.baseHitpointsLvl * BOULDER_THRESHOLDS[phases] / 100
            inBoulderPhase -> 1
            else -> 0
        }

    override fun landingDamage(npc: Npc, damage: Int): Int {
        if (npc in boulders) {
            boulderIncoming.computeIfPresent(npc) { _, left -> (left - damage).takeIf { it > 0 } }
            return damage
        }
        if (npc !== baba) {
            return damage
        }
        val room = (npc.hitpoints - floorHp(npc)).coerceAtLeast(0)
        return minOf(damage, room)
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== baba || !isStarted) {
            return
        }
        if (inBoulderPhase) {
            // Not while her boulders roll: the phase has to end first.
            npc.hitpoints = 1
            return
        }
        complete()
        // After complete(), which clears whatever was scheduled before it.
        npc.anim(baba_seqs.death)
        later(DEATH_TICKS) { despawn(npc) }
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        val boss = baba ?: return
        val players = challengePlayers()
        checkThresholds(boss)
        tickRubble(players)
        tickBoulders(players)
        tickBaboons(players)
        tickPeels(players)
        driveMonsters()
        if (script.isNotEmpty()) {
            if (scriptDelay > 0 && --scriptDelay > 0) {
                return
            }
            while (script.isNotEmpty() && scriptDelay <= 0) {
                val step = script.removeFirst()
                step.action()
                scriptDelay = step.delay
            }
            return
        }
        if (rockThrow > 0 && --rockThrow == 0) {
            throwRock(boss, players)
        } else if (rockImpact > 0 && --rockImpact == 0) {
            landRock(players)
        } else if (baboonTicks > 0 && --baboonTicks == 0) {
            baboonTicks = if (spawnBaboons(boss)) BABOON_RESPAWN else BABOON_RETRY
        }
        slam?.let { pending ->
            if (--pending.ticks <= 0) {
                landSlam(boss, players)
            }
        }
        if (!fightEnabled) {
            return
        }
        val target = target(players) ?: return
        boss.facePlayer(target)
        if (slam == null && gap(boss, target.coords) == 0) {
            if (--underneath <= 0) {
                underneath = 2
                startSlam(boss, target)
            }
            return
        }
        underneath = 2
        if (slam != null) {
            return
        }
        if (gap(boss, target.coords) != 1 || diagonal(boss, target.coords)) {
            stepToward(boss, target.coords)
            return
        }
        boss.resetMovement()
        if (now >= nextAttack) {
            nextAttack = now + ATTACK_SPEED
            swingAt(boss, target)
        }
    }

    /** She goes for whoever has the least health left (wiki: a tank keeps her by staying low). */
    private fun target(players: List<Player>): Player? =
        players.minByOrNull { it.stat(stats.hitpoints) * 1000 + it.slotId % 1000 }

    /** 75%: baboons start to come. 66% and 33%: the boulders. */
    private fun checkThresholds(boss: Npc) {
        val max = boss.baseHitpointsLvl
        // The next boulder phase waits for the current one to end.
        if (inBoulderPhase) {
            if (!babbonsUnlocked && boss.hitpoints <= max * 75 / 100) {
                babbonsUnlocked = true
                baboonTicks = 1
            }
            return
        }
        if (!babbonsUnlocked && boss.hitpoints <= max * 75 / 100) {
            babbonsUnlocked = true
            baboonTicks = 1
        }
        var passed = phases
        while (
            passed < BOULDER_THRESHOLDS.size &&
                boss.hitpoints <= max * BOULDER_THRESHOLDS[passed] / 100
        ) {
            passed++
        }
        if (passed != phases) {
            heldFloor = max * BOULDER_THRESHOLDS[passed - 1] / 100
            phases = passed
            boulderPhase(boss)
        }
    }

    // Her attacks
    // -----------------------------------------------------------------------------------------

    private val melee = NpcAttack(NpcAttackStyle.Melee, anim = baba_seqs.attack, maxHit = MAX_MELEE)

    private fun swingAt(boss: Npc, target: Player) {
        if (rockImpact <= 0 && --slamCycle <= 0) {
            slamCycle = SLAM_EVERY
            startSlam(boss, target)
            return
        }
        if (slamCycle == ROCKFALL_AT) {
            dropRubble(boss)
            rockThrow = ROCK_THROW_TICKS
        }
        // Protect from Melee blocks all of it since June 2025 (wiki).
        strike(boss, target, melee, through = 0)
    }

    /**
     * She turns to one of eight directions (towards her target) and shadows spread over the ground
     * in front of her, then she slams it (wiki). With her target under her she stomps there
     * instead, and faster.
     */
    private fun startSlam(boss: Npc, target: Player) {
        boss.resetMovement()
        val fast = gap(boss, target.coords) == 0
        val middle = raid.realOf(boss.coords.translate(boss.size / 2, boss.size / 2)) ?: return
        val centre =
            if (fast) {
                middle
            } else {
                val aim = raid.realOf(target.coords) ?: return
                val (dx, dz) = eightWay(aim.x - middle.x, aim.z - middle.z)
                // Far enough out that the near edge of the slam is the row beside her.
                val out = boss.size / 2 + slamLength(fast = false)
                middle.translate(dx * out, dz * out)
            }
        slam = Slam(centre, fast, if (fast) 2 else 4)
        graphic(if (fast) baba_spotanims.shadow_fast else baba_spotanims.shadow_slow, centre)
        val reach = if (raid.settings.isActive(Inv.SHAKING_THINGS_UP)) 4 else 3
        sound(toa_synths.baba_slam)
        for (distance in 1 until reach) {
            for ((dx, dz) in CROSS) {
                val tile = centre.translate(dx * distance, dz * distance)
                if (walkable(tile)) {
                    graphic(
                        if (distance == 1 && !fast) baba_spotanims.shadow_ring
                        else baba_spotanims.shadow_fast,
                        tile,
                        delay = distance * 30,
                    )
                }
            }
        }
    }

    /** How far the slam reaches from its centre: 3x3 and one more each way, 5x5 with Shaking. */
    private fun slamLength(fast: Boolean): Int =
        if (raid.settings.isActive(Inv.SHAKING_THINGS_UP) || fast) 3 else 2

    /** The nearest of the eight directions to the offset [dx], [dz]. */
    private fun eightWay(dx: Int, dz: Int): Pair<Int, Int> =
        when {
            dx == 0 && dz == 0 -> 0 to -1
            abs(dx) > 2 * abs(dz) -> Integer.signum(dx) to 0
            abs(dz) > 2 * abs(dx) -> 0 to Integer.signum(dz)
            else -> Integer.signum(dx) to Integer.signum(dz)
        }

    /** Full damage in the middle, a tenth less for each tile out; a 5x5 "circle" (7x7). */
    private fun landSlam(boss: Npc, players: List<Player>) {
        val pending = slam ?: return
        slam = null
        boss.anim(baba_seqs.attack)
        val length = slamLength(pending.fast)
        for (dx in -length..length) {
            for (dz in -length..length) {
                if ((abs(dx) == length && dz != 0) || (abs(dz) == length && dx != 0)) {
                    continue
                }
                val tile = pending.centre.translate(dx, dz)
                if (!walkable(tile)) {
                    continue
                }
                val distance = max(abs(dx), abs(dz))
                graphic(
                    baba_spotanims.shadow_end,
                    tile,
                    delay = if (distance == 0) 0 else (1 + distance) * 6,
                )
                for (player in players) {
                    if (raid.realOf(player.coords) == tile) {
                        val damage = maxHit(MAX_SLAM) * (10 - distance) / 10 + env.random.of(5)
                        hurtFlat(player, damage)
                    }
                }
            }
        }
    }

    // Rockfall and the thrown rock
    // ----------------------------------------------------------------------------------------

    /** Rocks fall on every player's tile, and a debris pile on each side of the arena. */
    private fun dropRubble(boss: Npc) {
        val speed = minOf(2, pathLevel / 2)
        rubbleTicks = 6 - speed * 2
        rockTiles.clear()
        for (player in challengePlayers()) {
            player.soundSynth(toa_synths.baba_rubble)
            val at = raid.realOf(player.coords) ?: continue
            if (at !in rockTiles) {
                rockTiles += at
            }
        }
        for (side in 0..1) {
            if (rubble[side] != null) {
                continue
            }
            for (tile in shuffled(RUBBLE_TILES[side])) {
                if (!fitsRubble(tile) || overlaps(boss, tile, RUBBLE_SIZE)) {
                    continue
                }
                nextRubble[side] = tile
                rockTiles += tile.translate(1, 1)
                break
            }
        }
        for (tile in rockTiles) {
            graphic(baba_spotanims.rock_fall[speed], tile, delay = 20)
        }
    }

    private fun fitsRubble(sw: CoordGrid): Boolean =
        (0 until RUBBLE_SIZE).all { dx ->
            (0 until RUBBLE_SIZE).all { dz -> walkable(sw.translate(dx, dz)) }
        }

    /** The rocks land: whoever is under one is hurt; the piles land and shove players out. */
    private fun tickRubble(players: List<Player>) {
        if (rubbleTicks <= 0 || --rubbleTicks > 0 || script.isNotEmpty()) {
            return
        }
        for (tile in rockTiles) {
            for (player in players) {
                if (raid.realOf(player.coords) == tile) {
                    hurtFlat(player, maxHit(MAX_RUBBLE) + env.random.of(5))
                }
            }
        }
        rockTiles.clear()
        // 10 health, and 10 more for every player (wiki).
        val health = RUBBLE_HEALTH * teamSize
        for (side in 0..1) {
            val tile = nextRubble[side] ?: continue
            nextRubble[side] = null
            val npc =
                spawn(baba_npcs.rubble[0], tile, scale = false, points = 0.0, hitpoints = health)
            placeLoc(tile, baba_locs.rubble_block, LocShape.CentrepieceStraight, LocAngle[0])
            rubble[side] = Rubble(npc, tile)
            for (index in baboons.indices) {
                val baboon = baboons[index] ?: continue
                if (overlaps(npc, baboon.coords)) {
                    baboon.hitpoints = 0
                    baboon.queueDeath()
                }
            }
            for (player in players) {
                if (overlaps(npc, player.coords)) {
                    shoveOut(player, tile)
                }
            }
        }
    }

    private fun overlaps(npc: Npc, coords: CoordGrid): Boolean =
        coords.x >= npc.coords.x &&
            coords.x < npc.coords.x + npc.size &&
            coords.z >= npc.coords.z &&
            coords.z < npc.coords.z + npc.size

    private fun overlaps(npc: Npc, real: CoordGrid, size: Int): Boolean {
        val sw = raid.realOf(npc.coords) ?: return false
        return real.x < sw.x + npc.size &&
            real.x + size > sw.x &&
            real.z < sw.z + npc.size &&
            real.z + size > sw.z
    }

    /** A player under a falling pile is pushed out of it, unhurt. */
    private fun shoveOut(player: Player, pile: CoordGrid) {
        val at = raid.realOf(player.coords) ?: return
        for (radius in 1..3) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (abs(dx) != radius && abs(dz) != radius) {
                        continue
                    }
                    val tile = at.translate(dx, dz)
                    val inPile =
                        tile.x in pile.x until pile.x + RUBBLE_SIZE &&
                            tile.z in pile.z until pile.z + RUBBLE_SIZE
                    if (inPile || !walkable(tile) || !room.inChallenge(tile)) {
                        continue
                    }
                    player.say("Ouch!")
                    knockback(player, tile, cycles = 29)
                    return
                }
            }
        }
    }

    private fun removeRubble(pile: Rubble) {
        removeLoc(pile.tile)
        despawn(pile.npc)
    }

    private fun throwRock(boss: Npc, players: List<Player>) {
        boss.anim(baba_seqs.throw_rock)
        rockImpact = ROCK_IMPACT_TICKS
        for (player in players) {
            player.mes("<col=ef0083>Ba-Ba throws a large boulder at you.</col>")
            player.soundSynth(toa_synths.baba_throw_rock)
            env.worldRepo.projAnim(boss, player, baba_spotanims.rock_travel, projanims.magic_spell)
        }
    }

    /**
     * The rock lands on everyone: beside cover it does nothing (wiki, since the boulder change),
     * and the cover pays for it (debris 10, a sarcophagus 30, a baboon 12 for each player).
     */
    private fun landRock(players: List<Player>) {
        val damage = maxHit(MAX_ROCK)
        for (player in players) {
            player.spotanim(baba_spotanims.rock_impact, height = 50)
            player.soundSynth(toa_synths.baba_rock_impact)
            if (!takeCover(player)) {
                hurtFlat(player, damage)
            }
        }
        var debrisBroken = false
        for (side in 0..1) {
            val pile = rubble[side] ?: continue
            if (pile.pending > 0) {
                pile.npc.hitpoints -= pile.pending
                pile.pending = 0
                if (pile.npc.hitpoints <= 0) {
                    rubble[side] = null
                    removeRubble(pile)
                    debrisBroken = true
                } else {
                    refreshRubbleLook(pile)
                }
            }
        }
        for (sarcophagus in sarcophagi) {
            if (sarcophagus.pending > 0) {
                damageSarcophagus(sarcophagus, sarcophagus.pending)
                sarcophagus.pending = 0
            }
        }
        for ((npc, cost) in baboonCover.toList()) {
            npc.hitpoints = (npc.hitpoints - cost).coerceAtLeast(0)
            if (npc.hitpoints <= 0) {
                npc.queueDeath()
            }
        }
        baboonCover.clear()
        // A rock that broke a debris pile brings the rocks down again (wiki).
        if (debrisBroken) {
            baba?.let(::dropRubble)
        }
    }

    /** Debris piles shrink as they are worn down. */
    private fun refreshRubbleLook(pile: Rubble) {
        val share = pile.npc.hitpoints.toDouble() / pile.npc.baseHitpointsLvl.coerceAtLeast(1)
        val stage =
            ((1.0 - share) * baba_npcs.rubble.size).toInt().coerceIn(0, baba_npcs.rubble.size - 1)
        if (stage == 0) {
            pile.npc.resetTransmog()
        } else {
            pile.npc.transmog(env.npcTypes[baba_npcs.rubble[stage]], Int.MAX_VALUE)
        }
    }

    /** A player beside cover (not on a corner) claims it if it has enough left for one more. */
    private fun takeCover(player: Player): Boolean {
        val at = raid.realOf(player.coords) ?: return false
        for (pile in rubble) {
            if (pile == null || !besideSquare(at, pile.tile, RUBBLE_SIZE)) {
                continue
            }
            if (pile.pending < pile.npc.hitpoints) {
                pile.pending += COVER_RUBBLE
                return true
            }
        }
        for (sarcophagus in sarcophagi) {
            if (sarcophagus.open || !besideSquare(at, sarcophagus.tile, 1)) {
                continue
            }
            if (sarcophagus.pending < sarcophagus.health) {
                sarcophagus.pending += COVER_SARCOPHAGUS
                return true
            }
        }
        for (baboon in baboons) {
            if (baboon == null || !baboon.isSlotAssigned) {
                continue
            }
            val tile = raid.realOf(baboon.coords) ?: continue
            if (!besideSquare(at, tile, 1)) {
                continue
            }
            val pending = baboonCover[baboon] ?: 0
            if (pending < baboon.hitpoints) {
                baboonCover[baboon] = pending + COVER_BABOON
                return true
            }
        }
        return false
    }

    /** Beside a [size] square at [sw], not on a corner. */
    private fun besideSquare(at: CoordGrid, sw: CoordGrid, size: Int): Boolean {
        val insideX = at.x >= sw.x && at.x < sw.x + size
        val insideZ = at.z >= sw.z && at.z < sw.z + size
        if (insideX && (at.z == sw.z - 1 || at.z == sw.z + size)) {
            return true
        }
        return insideZ && (at.x == sw.x - 1 || at.x == sw.x + size)
    }

    // Sarcophagi
    // ------------------------------------------------------------------------------------------

    private fun damageSarcophagus(sarcophagus: Sarcophagus, amount: Int) {
        if (sarcophagus.open) {
            return
        }
        sarcophagus.health -= amount
        if (sarcophagus.health <= 0) {
            openSarcophagus(sarcophagus)
        }
    }

    /** An opened sarcophagus spits three skulls into the arena every few ticks. */
    private fun openSarcophagus(sarcophagus: Sarcophagus) {
        sarcophagus.open = true
        swapLoc(sarcophagus.tile, listOf(baba_locs.sarcophagus), baba_locs.sarcophagus_open)
        volley(sarcophagus)
    }

    private fun volley(sarcophagus: Sarcophagus) {
        if (!isStarted || !sarcophagus.open) {
            return
        }
        graphic(baba_spotanims.sarcophagus_burst, sarcophagus.tile, height = 150)
        // The spread grows with the path level (wiki: every two levels); the base is TSPS's.
        val depth = 8 + minOf(2, pathLevel / 2) * 2
        val tiles = ArrayList<CoordGrid>()
        for (dx in -4..4) {
            for (dz in 1..depth) {
                val tile = sarcophagus.tile.translate(dx, if (sarcophagus.north) -dz else dz)
                if (walkable(tile) && room.inChallenge(tile)) {
                    tiles += tile
                }
            }
        }
        for (tile in shuffled(tiles).take(SKULLS_PER_VOLLEY)) {
            projectile(sarcophagus.tile, tile, baba_spotanims.skull, projanims.magic_spell)
            later(2) {
                graphic(baba_spotanims.skull_impact, tile, delay = 5)
                for (player in challengePlayers()) {
                    if (raid.realOf(player.coords) == tile) {
                        hurtFlat(player, skullDamage(player))
                    }
                }
            }
        }
        later(SARCOPHAGUS_INTERVAL) { volley(sarcophagus) }
    }

    /** Gotta Have Faith: more damage the more prayer the player has left (TSPS's scale). */
    private fun skullDamage(player: Player): Int {
        var damage = maxHit(MAX_SKULL) + env.random.of(2)
        if (raid.settings.isActive(Inv.GOTTA_HAVE_FAITH)) {
            damage += player.stat(stats.prayer) * 15 / 100
        }
        return damage
    }

    // Baboons
    // ------------------------------------------------------------------------------------------

    /** A guess at the wiki's "2-4 depending on party size". */
    private fun baboonsWanted(): Int = minOf(4, 2 + (teamSize - 1) / 2)

    private fun spawnBaboons(boss: Npc): Boolean {
        val free = (0 until baboonsWanted()).filter { baboons[it]?.isSlotAssigned != true }
        if (free.isEmpty()) {
            return false
        }
        val tiles = ArrayList<CoordGrid>()
        for (dx in -5..3) {
            for (dz in -4..4) {
                val tile = BABOON_SPAWN.translate(dx, dz)
                if (walkable(tile) && !overlaps(boss, tile, 1)) {
                    tiles += tile
                }
            }
        }
        val shuffledTiles = shuffled(tiles)
        for ((i, slot) in free.withIndex()) {
            val tile = shuffledTiles.getOrNull(i) ?: break
            val npc = spawn(baba_npcs.baboon, tile, scale = false, points = 1.0)
            baboons[slot] = npc
            baboonShots[npc] = BABOON_SHOTS
            monster(
                npc,
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = baba_seqs.baboon_ranged,
                    projectile = baba_spotanims.baboon_travel,
                    projanim = projanims.arrow,
                    maxHit = MAX_BABOON,
                ),
                range = 7,
                speed = 4,
                spawnAnim = baba_seqs.baboon_spawn,
            )
        }
        return true
    }

    override fun swing(monster: ToaMonster, target: Player) {
        val npc = monster.npc
        // Its damage is not scaled with the raid (TSPS).
        val attack = monster.attack.copy(maxHit = MAX_BABOON)
        env.attacks.attack(npc, target, attack.copy(typeless = false))
        val shots = baboonShots[npc] ?: return
        if (shots > 0) {
            baboonShots[npc] = shots - 1
        }
    }

    /** Baboons shoot three times, then go and batter the nearest closed sarcophagus. */
    private fun tickBaboons(players: List<Player>) {
        for ((index, npc) in baboons.withIndex()) {
            if (npc == null) {
                continue
            }
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                baboons[index] = null
                baboonShots.remove(npc)
                continue
            }
            val monster = monsters[npc] ?: continue
            if ((baboonShots[npc] ?: 0) > 0) {
                monster.scripted = false
                continue
            }
            monster.scripted = true
            val here = raid.realOf(npc.coords) ?: continue
            val target =
                sarcophagi
                    .filter { !it.open }
                    .minByOrNull { max(abs(it.tile.x - here.x), abs(it.tile.z - here.z)) }
            if (target == null) {
                baboonShots[npc] = BABOON_SHOTS
                continue
            }
            val distance = max(abs(target.tile.x - here.x), abs(target.tile.z - here.z))
            if (distance > 1) {
                stepToward(npc, raid.regionOf(target.tile))
            } else if (now % 4 == 0) {
                npc.anim(baba_seqs.baboon_melee)
                damageSarcophagus(target, BABOON_SARCOPHAGUS_HIT)
                if (target.open) {
                    baboonShots[npc] = BABOON_SHOTS
                }
            }
        }
    }

    override fun monsterDied(npc: Npc) {
        val index = baboons.indexOf(npc)
        if (index >= 0) {
            baboons[index] = null
            baboonShots.remove(npc)
            if (raid.settings.isActive(Inv.JUNGLE_JAPES)) {
                raid.realOf(npc.coords)?.let { dropPeels(it) }
            }
        }
    }

    /** Jungle Japes: two banana peels by a fallen baboon (wiki). */
    private fun dropPeels(at: CoordGrid) {
        var dropped = 0
        for ((dx, dz) in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val tile = at.translate(dx, dz)
            if (dropped >= 2 || tile in peels || !walkable(tile)) {
                continue
            }
            peels += tile
            placeLoc(tile, baba_locs.banana_peel, LocShape.GroundDecor, LocAngle[env.random.of(4)])
            dropped++
        }
    }

    /** A player who steps on a peel slips: a little damage and 3 seconds stunned (wiki). */
    private fun tickPeels(players: List<Player>) {
        for (player in players) {
            val until = stunnedUntil[player.accountId]
            if (until != null) {
                if (now >= until) {
                    stunnedUntil.remove(player.accountId)
                } else {
                    player.abortRoute()
                }
            }
            if (peels.isEmpty()) {
                continue
            }
            val at = raid.realOf(player.coords) ?: continue
            if (!peels.remove(at)) {
                continue
            }
            removeLoc(at, LocShape.GroundDecor)
            hurtFlat(player, maxHit(MAX_PEEL))
            player.mes("You slip and fall on a banana skin!")
            player.anim(baba_seqs.player_slip)
            player.spotanim(baba_spotanims.slip, height = 124)
            player.soundSynth(toa_synths.baba_slip)
            player.abortRoute()
            stunnedUntil[player.accountId] = now + PEEL_STUN_TICKS
        }
    }

    // The boulders
    // -----------------------------------------------------------------------------------------

    /** She leaps to her throne, knocks everyone to the bottom of the slope, and rolls boulders. */
    private fun boulderPhase(boss: Npc) {
        boss.resetMovement()
        rockThrow = 0
        slam = null
        underneath = 2
        val steps = ArrayList<Step>()
        steps += Step(3) {}
        steps +=
            Step(1) {
                // The special has begun: from here she can be hit on towards the next threshold.
                heldFloor = -1
                boss.anim(baba_seqs.fly)
                clearRockfall()
            }
        steps += Step(1) { boss.telejump(env.collision, raid.regionOf(SPAWN)) }
        steps += Step(1) { knockToWall() }
        steps +=
            Step(0) {
                clearRockfall()
                boss.transmog(env.npcTypes[baba_npcs.digging], Int.MAX_VALUE)
            }
        val dash = raid.settings.isActive(Inv.BOULDERDASH)
        val waves = if (dash) BOULDERDASH_WAVES else BOULDER_WAVES
        repeat(waves) {
            steps +=
                Step(2) {
                    for (start in BOULDER_STARTS) {
                        env.worldRepo.projAnim(
                            boss,
                            raid.regionOf(start.translate(2, 0)),
                            baba_spotanims.boulder_travel,
                            projanims.magic_spell,
                        )
                    }
                    boss.anim(baba_seqs.throw_boulders)
                    sound(toa_synths.baba_boulders)
                    cracked = (cracked + env.random.of(5) - 2).coerceIn(0, BOULDER_STARTS.size - 1)
                }
            steps +=
                Step(if (dash) 5 else 6) {
                    for ((index, start) in BOULDER_STARTS.withIndex()) {
                        rollBoulder(start, index == cracked)
                    }
                }
        }
        steps += Step(0) { endBoulders(boss) }
        script = ArrayDeque(steps)
        scriptDelay = 0
    }

    private fun endBoulders(boss: Npc) {
        heldFloor = -1
        script.clear()
        boss.resetTransmog()
        nextAttack = now + ATTACK_SPEED
    }

    private fun clearRockfall() {
        rockTiles.clear()
        rubbleTicks = 0
        for (side in 0..1) {
            rubble[side]?.let(::removeRubble)
            rubble[side] = null
            nextRubble[side] = null
        }
    }

    private fun intoGap(z: Int): Boolean = z in PIT_MIN_Z..PIT_MIN_Z + 4

    private fun knockToWall() {
        val gap = raid.settings.isActive(Inv.MIND_THE_GAP)
        for (player in challengePlayers()) {
            player.mes("<col=ef0083>Ba-Ba screams and knocks you back!</col>")
            player.anim(baba_seqs.player_flyback)
            player.spotanim(baba_spotanims.stun, height = 124)
            player.soundSynth(toa_synths.baba_flyback)
            val at = raid.realOf(player.coords) ?: continue
            val pit = gap && intoGap(at.z)
            val x = PIT_WALL_X + if (pit) -2 else 0
            if (x != at.x) {
                knockback(
                    player,
                    CoordGrid(x, at.z, Z),
                    facing = EAST,
                    cycles = 5 + 10 * abs(x - at.x),
                )
            }
            if (pit) {
                later(2) { fallIntoPit(player) }
            }
        }
    }

    /** Mind the Gap: the pit kills (wiki). */
    private fun fallIntoPit(player: Player) {
        player.anim(baba_seqs.player_pit)
        player.mes("<col=ff3045>You're pushed straight into a deep pit! Ouch!</col>")
        hurtFlat(player, player.stat(stats.hitpoints).coerceAtLeast(1))
    }

    private fun rollBoulder(start: CoordGrid, cracked: Boolean) {
        val type = if (cracked) baba_npcs.boulder_cracked else baba_npcs.boulder
        val bucket =
            when {
                pathLevel >= 4 -> 2
                pathLevel >= 2 -> 1
                else -> 0
            }
        val health = BOULDER_HEALTH[(teamSize - 1).coerceIn(0, 7)][bucket]
        val npc =
            spawn(type, start, scale = false, points = 0.0, hitpoints = health, passThrough = true)
        boulders[npc] = cracked
    }

    /** Boulders roll a tile west each tick, knocking back and hurting whoever they meet. */
    private fun tickBoulders(players: List<Player>) {
        if (boulders.isEmpty()) {
            return
        }
        val dash = raid.settings.isActive(Inv.BOULDERDASH)
        val gap = raid.settings.isActive(Inv.MIND_THE_GAP)
        for (npc in boulders.keys.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                forgetBoulder(npc)
                continue
            }
            val sw = raid.realOf(npc.coords) ?: continue
            if (npc in doomedBoulders) {
                // A hit that breaks it is on its way: it rolls on until then, colliding with
                // nothing (the hit's landing removes it as usual).
                rollOn(npc, sw)
                continue
            }
            for (player in players) {
                val at = raid.realOf(player.coords) ?: continue
                if (at.x < sw.x || at.x > sw.x + 1 || at.z < sw.z || at.z > sw.z + 2) {
                    continue
                }
                val pushTo = at.x - if (dash) 5 else 6
                if (gap && pushTo < PIT_WALL_X && intoGap(at.z)) {
                    knockback(player, CoordGrid(PIT_WALL_X - 1, at.z, Z), facing = EAST)
                    later(1) { fallIntoPit(player) }
                } else {
                    player.anim(baba_seqs.player_knocked)
                    player.soundSynth(toa_synths.baba_collision)
                    knockback(player, CoordGrid(max(PIT_WALL_X, pushTo), at.z, Z), facing = EAST)
                    hurtFlat(player, maxHit(MAX_COLLISION))
                }
            }
            for (index in baboons.indices) {
                val baboon = baboons[index] ?: continue
                val at = raid.realOf(baboon.coords) ?: continue
                if (at.x in sw.x..sw.x + 2 && at.z in sw.z..sw.z + 2) {
                    baboon.hitpoints = 0
                    baboon.queueDeath()
                }
            }
            for (side in 0..1) {
                val pile = rubble[side] ?: continue
                if (abs(pile.tile.x - sw.x) <= 2 && abs(pile.tile.z - sw.z) <= 2) {
                    rubble[side] = null
                    removeRubble(pile)
                }
            }
            rollOn(npc, sw)
        }
    }

    /** Rolls the boulder [npc] (south-west tile [sw]) a tile west, or removes it at the end. */
    private fun rollOn(npc: Npc, sw: CoordGrid) {
        val next = sw.translate(-1, 0)
        if (!(0 until 3).all { dz -> walkable(next.translate(0, dz)) } || next.x < PIT_WALL_X - 1) {
            forgetBoulder(npc)
            despawn(npc)
            return
        }
        npc.walk(raid.regionOf(next))
    }

    private fun forgetBoulder(npc: Npc) {
        boulders.remove(npc)
        boulderIncoming.remove(npc)
        doomedBoulders.remove(npc)
    }

    // Hits
    // -----------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc === baba) {
            // Melee on her while the boulders roll stops them early (wiki); not in the wind-up.
            if (inBoulderPhase && heldFloor < 0 && hit.type == HitType.Melee) {
                baba?.let(::endBoulders)
            }
            hit.damage = landingDamage(npc, hit.damage)
            return
        }
        if (npc in boulders) {
            if (boulders[npc] == true && hit.type == HitType.Ranged && source != null) {
                // A cracked boulder always takes the ranged max hit (wiki).
                hit.damage = max(hit.damage, source.vars[varps.com_maxhit])
            }
            // The moment a hit that will break it is queued, it stops colliding.
            val incoming = boulderIncoming.merge(npc, hit.damage, Int::plus) ?: 0
            if (incoming >= npc.hitpoints) {
                doomedBoulders += npc
            }
            return
        }
        if (npc.type.id == baba_npcs.baboon.id && source != null) {
            // Players always land their max hit on Ba-Ba's baboons (wiki).
            hit.damage = max(hit.damage, source.vars[varps.com_maxhit])
        }
    }

    companion object {
        const val Z = 0
        val SPAWN = CoordGrid(3815, 5406, Z)
        val BABOON_SPAWN = CoordGrid(3811, 5408, Z)

        /** The sarcophagi, north side first (as the map has them). */
        val SARCOPHAGI =
            listOf(
                CoordGrid(3803, 5416, Z),
                CoordGrid(3808, 5416, Z),
                CoordGrid(3813, 5416, Z),
                CoordGrid(3818, 5416, Z),
                CoordGrid(3803, 5400, Z),
                CoordGrid(3808, 5400, Z),
                CoordGrid(3813, 5400, Z),
                CoordGrid(3818, 5400, Z),
            )

        /** Where each side's debris pile may land (south-west tiles of the 3x3 pile). */
        val RUBBLE_TILES =
            listOf(
                listOf(
                    CoordGrid(3798, 5413, Z),
                    CoordGrid(3798, 5407, Z),
                    CoordGrid(3798, 5401, Z),
                    CoordGrid(3803, 5401, Z),
                    CoordGrid(3803, 5413, Z),
                    CoordGrid(3805, 5407, Z),
                ),
                listOf(
                    CoordGrid(3816, 5413, Z),
                    CoordGrid(3812, 5413, Z),
                    CoordGrid(3812, 5406, Z),
                    CoordGrid(3812, 5401, Z),
                    CoordGrid(3817, 5401, Z),
                    CoordGrid(3810, 5405, Z),
                ),
            )

        val BOULDER_STARTS =
            listOf(
                CoordGrid(3813, 5401, Z),
                CoordGrid(3813, 5404, Z),
                CoordGrid(3813, 5407, Z),
                CoordGrid(3813, 5410, Z),
                CoordGrid(3813, 5413, Z),
            )

        /** The bottom of the slope, and the pit beside it (rows 5406-5410). */
        const val PIT_WALL_X = 3798
        const val PIT_MIN_Z = 5406

        private val CROSS = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
        private const val EAST = 1536

        /** The boulders' health by party size and path level 0-1, 2-3, 4+ (wiki's table). */
        val BOULDER_HEALTH =
            arrayOf(
                intArrayOf(25, 27, 31),
                intArrayOf(36, 39, 45),
                intArrayOf(47, 51, 58),
                intArrayOf(55, 60, 68),
                intArrayOf(62, 68, 77),
                intArrayOf(70, 77, 87),
                intArrayOf(77, 84, 96),
                intArrayOf(85, 93, 106),
            )

        val BOULDER_THRESHOLDS = intArrayOf(66, 33)

        const val BABA_POINTS = 2.0
        const val FIRST_ATTACK = 4
        const val ATTACK_SPEED = 6
        const val SLAM_EVERY = 5
        const val ROCKFALL_AT = 3
        const val ROCK_THROW_TICKS = 13
        const val ROCK_IMPACT_TICKS = 7
        const val BABOON_RESPAWN = 325
        const val BABOON_RETRY = 10
        const val BABOON_SHOTS = 3
        const val BABOON_SARCOPHAGUS_HIT = 20
        const val SARCOPHAGUS_HEALTH = 100
        const val SARCOPHAGUS_INTERVAL = 4
        const val SKULLS_PER_VOLLEY = 3
        const val RUBBLE_SIZE = 3
        const val RUBBLE_HEALTH = 10
        const val COVER_RUBBLE = 10
        const val COVER_SARCOPHAGUS = 30
        const val COVER_BABOON = 12
        const val BOULDER_WAVES = 10
        const val BOULDERDASH_WAVES = 15
        const val PEEL_STUN_TICKS = 5
        const val DEATH_TICKS = 4

        const val MAX_MELEE = 24
        const val MAX_SLAM = 17
        const val MAX_RUBBLE = 19
        const val MAX_ROCK = 39
        const val MAX_COLLISION = 14
        const val MAX_SKULL = 8
        const val MAX_PEEL = 8
        const val MAX_BABOON = 12
    }
}
