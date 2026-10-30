package org.rsmod.content.areas.misc.isleofsouls.wilderness

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid

/**
 * What Vet'ion and Calvar'ion remember: which of their two forms they are in, the hellhounds they
 * have called and whether those were killed already.
 */
class VetionFight(npc: Npc, lair: WildLair) : BossFight(npc, lair) {
    /** 0 is the normal form, 1 the enraged form. */
    var form = 0

    val hounds = ArrayList<Npc>()

    /** The hounds of each form were called (and, when they were killed, not called again). */
    val houndsCalled = BooleanArray(2)

    /** The tick the enraged form began, for the revert to the normal form. */
    var enragedAt = 0
    var busyUntil = 0

    /** Where he must keep looking while he is held still by a bash (set when it was aimed). */
    var faceLock: CoordGrid? = null

    /** Whether hellhounds were alive last tick, to yell when the last of them falls. */
    var houndsWereAlive = false

    override fun reset(now: Int) {
        super.reset(now)
        form = 0
        houndsWereAlive = false
        hounds.clear()
        houndsCalled.fill(false)
        enragedAt = 0
        busyUntil = 0
        faceLock = null
    }
}

/**
 * Vet'ion and Calvar'ion, his weaker version (OSRS Wiki, "Vet'ion", "Calvar'ion" and their
 * Strategies pages). He walks up to a player and fights with area attacks you can step out of: five
 * bolts of lightning (one per player in a group, up to seven) that strike a 3x3 after a warning and
 * hurt the middle tile most, and, when someone is next to him, a shield bash that stuns. He only
 * walks between attacks: each one holds him still until it has landed. A lightning attack is: he
 * shouts and glowing circles appear, a tick later he swings, the strike plays on the circles and
 * the damage lands as it ends.
 *
 * He has two forms. At half health he calls two skeleton hellhounds and cannot be hurt while they
 * live. When the normal form falls he rises enraged with full health, attacks faster and hits
 * harder, and calls two greater hellhounds at half health again; the enraged form dies for good. If
 * he is not killed within five minutes enraged, he turns back to the normal form with the health he
 * has left.
 *
 * Guesses where the wiki is silent: the damage on the edge of a bolt (half the middle), how often
 * he bashes (one turn in three when someone is next to him), the bash's reach (his body and a strip
 * two tiles deep, a tile wider than him each side, on the target's side, shown by the bash
 * graphic's shadow then dust) and that the stun is a freeze. The enraged ring goes like the other
 * bolts.
 */
class VetionLogic(d: WildBosses) : WildLogic(d) {
    override fun create(npc: Npc, lair: WildLair): BossFight = VetionFight(npc, lair)

    override fun clear(fight: BossFight) {
        fight as VetionFight
        for (hound in fight.hounds) {
            if (hound.isSlotAssigned) {
                d.npcRepo.del(hound, Int.MAX_VALUE)
            }
        }
        fight.hounds.clear()
    }

    private fun enraged(fight: VetionFight) = fight.form == 1

    override fun modifyHit(fight: BossFight, hit: HitBuilder) {
        fight as VetionFight
        // The hounds channel immunity to him (wiki).
        if (fight.hounds.any { it.isSlotAssigned && it.hitpoints > 0 }) {
            hit.damage = 0
        }
    }

    override fun hurt(fight: BossFight, attacker: Player?, damage: Int) {
        fight.engaged = true
    }

    override fun interceptDeath(fight: BossFight): Boolean {
        fight as VetionFight
        if (fight.form == 1) {
            yell(fight, VetionQuotes.Kind.Death)
            return false
        }
        // The normal form fell: he rises again.
        val npc = fight.npc
        fight.form = 1
        fight.enragedAt = d.now
        npc.hitpoints = npc.baseHitpointsLvl
        npc.transmog(
            d.npcTypes[
                    if (fight.lair.multi) wild_npcs.vetion_enraged
                    else wild_npcs.calvarion_enraged],
            Int.MAX_VALUE,
        )
        npc.anim(wild_seqs.vetion_enrage)
        yell(fight, VetionQuotes.Kind.Enrage)
        // He stands still, even if he was walking when he fell, until the ring has struck.
        npc.abortRoute()
        fight.walkDest = null
        fight.faceLock = null
        fight.busyUntil = d.now + ENRAGE_TICKS
        fight.attackAt = d.now + ENRAGE_TICKS + 1
        enrageBlast(fight)
        return true
    }

    override fun act(fight: BossFight, inside: List<Player>, now: Int) {
        fight as VetionFight
        val npc = fight.npc
        fight.hounds.removeAll { !it.isSlotAssigned || it.hitpoints <= 0 }
        if (fight.houndsWereAlive && fight.hounds.isEmpty()) {
            yell(fight, VetionQuotes.Kind.HoundsDown)
        }
        fight.houndsWereAlive = fight.hounds.isNotEmpty()
        aimHounds(fight, inside)
        if (enraged(fight) && now - fight.enragedAt >= ENRAGED_LIMIT) {
            revert(fight, now)
        }
        if (now < fight.busyUntil) {
            // Being hit turns an npc to its attacker, so a bash's direction is set again each tick.
            fight.faceLock?.let {
                npc.resetFaceEntity()
                npc.faceSquare(it)
            }
            return
        }
        fight.faceLock = null
        if (!fight.engaged) {
            fight.engaged = true
            fight.attackAt = maxOf(fight.attackAt, now + FIRST_ATTACK_DELAY)
        }
        callHounds(fight)

        val target = d.pickTarget(fight, inside)
        fight.target = target
        npc.facePlayer(target)
        val gap = npc.distanceTo(target)
        val ready = now >= fight.attackAt && gap != 0
        // He only walks between attacks: when one is ready he stands and does it.
        if (gap != 1 && !ready) {
            d.approach(fight, target)
        }
        if (!ready) {
            return
        }

        val singles = !fight.lair.multi
        val hold =
            if (gap == 1 && d.random.of(BASH_ODDS) == 0) {
                bash(fight, target, singles)
                BASH_WARNING
            } else {
                lightning(fight, inside)
                LIGHTNING_HOLD
            }
        // He stays where he is until the attack has landed.
        npc.abortRoute()
        fight.walkDest = null
        fight.busyUntil = now + hold
        fight.attackAt = now + if (enraged(fight)) ENRAGED_RATE else NORMAL_RATE
    }

    // The attacks -----------------------------------------------------------------------------

    private fun maxHit(fight: VetionFight): Int {
        val singles = !fight.lair.multi
        val base = if (singles) CALVARION_MAX else VETION_MAX
        // The enraged form hits harder (wiki).
        return if (enraged(fight)) base * ENRAGED_MAX_PERCENT / 100 else base
    }

    /**
     * The lightning (from the wiki and the game's footage): he shouts and a glowing circle appears
     * on the ground where each bolt will land; a tick later he swings (lightning crackles round his
     * weapon) and the strike plays on the circles; the damage lands as the strike ends. The circle
     * and the strike are one graphic that lasts 4 ticks. The first bolt lands on or beside the
     * target, the others are spread over the arena.
     */
    private fun lightning(fight: VetionFight, inside: List<Player>) {
        val bolts = if (inside.size == 1) SOLO_BOLTS else minOf(MAX_GROUP_BOLTS, inside.size)
        val centres = ArrayList<CoordGrid>()
        if (inside.size == 1) {
            val aimed = inside.first()
            centres += near(aimed.coords)
            repeat(bolts - 1) { centres += randomTile(fight) }
        } else {
            for (player in inside.take(bolts)) {
                centres += near(player.coords)
            }
        }
        yell(fight, VetionQuotes.Kind.Lightning)
        val circle =
            if (enraged(fight)) wild_spotanims.vetion_bolt_impact_enraged
            else wild_spotanims.vetion_bolt_impact
        for (centre in centres) {
            d.worldRepo.spotanimMap(circle, centre)
        }
        fight.later(d.now + SWING_START) { swing(fight) }
        fight.later(d.now + STRIKE_HIT) {
            if (fight.dead) {
                return@later
            }
            for (centre in centres) {
                strike(fight, centre)
            }
        }
    }

    /** His lightning swing: the animation, and the sparks that crackle round his weapon. */
    private fun swing(fight: VetionFight) {
        if (fight.dead) {
            return
        }
        val npc = fight.npc
        npc.anim(if (enraged(fight)) wild_seqs.vetion_magic_enraged else wild_seqs.vetion_magic)
        npc.spotanim(
            if (enraged(fight)) wild_spotanims.vetion_bolt_enraged else wild_spotanims.vetion_bolt
        )
    }

    /** The bolt lands: the middle tile hurts most, the rest of the 3x3 half as much. */
    private fun strike(fight: VetionFight, centre: CoordGrid) {
        val max = maxHit(fight)
        for (victim in d.inside(fight.lair)) {
            val away = d.chebyshev(victim.coords, centre)
            if (away > 1) {
                continue
            }
            val damage = if (away == 0) d.random.of(0..max) else d.random.of(0..max / EDGE_DIVISOR)
            d.hits.typeless(fight.npc, victim, damage, 1)
        }
    }

    /**
     * The shield bash (patterns and timing from the game's footage): shadows appear on the tiles of
     * two lines and he stands facing that way for 4 ticks; then he swings his shield, and a tick
     * later the smoke bursts off the tiles and whoever is standing on them, or under him, at that
     * moment is hit and stunned. He holds still, and keeps facing the way the bash was aimed, until
     * it is done.
     */
    fun bash(fight: VetionFight, target: Player, singles: Boolean) {
        val npc = fight.npc
        yell(fight, VetionQuotes.Kind.BashShield)
        // The direction is picked once, and he is turned to it in the same breath.
        val lines = bashLines(npc, target)
        val facing = bashFacing(npc, target)
        fight.faceLock = facing
        npc.resetFaceEntity()
        npc.faceSquare(facing)
        val shadow =
            if (d.bashShadowId >= 0) d.spotanim(d.bashShadowId) else wild_spotanims.vetion_shadow
        val smoke = if (d.bashDustId >= 0) d.spotanim(d.bashDustId) else wild_spotanims.vetion_bash
        for (tile in lines) {
            d.worldRepo.spotanimMap(shadow, tile)
        }
        val max = if (singles) CALVARION_BASH else VETION_BASH
        // The shield swing starts a tick before the smoke and the hit.
        fight.later(d.now + BASH_STRIKE - 1) {
            if (!fight.dead) {
                npc.anim(
                    if (enraged(fight)) wild_seqs.vetion_melee_enraged else wild_seqs.vetion_melee
                )
            }
        }
        fight.later(d.now + BASH_STRIKE) {
            if (fight.dead) {
                return@later
            }
            for (tile in lines) {
                d.worldRepo.spotanimMap(smoke, tile)
            }
            // Only whoever is on a shadowed tile or under him as the smoke appears is caught (their
            // hitsplat
            // shows a tick later, the shortest delay the engine has).
            for (victim in d.inside(fight.lair)) {
                if (victim.coords in lines || d.onBody(npc, victim.coords)) {
                    d.hits.typeless(npc, victim, d.random.of(0..max), 1)
                    victim.freeze(BASH_STUN)
                    victim.spotanim(org.rsmod.api.config.refs.spotanims.stunned)
                }
            }
        }
    }

    /** Which side and corner the bash goes towards: -1, 0 (the middle) or 1 on each axis. */
    private fun bashSides(npc: Npc, target: Player): Pair<Int, Int> {
        val half = npc.size / 2
        val rx = target.coords.x - npc.coords.x
        val rz = target.coords.z - npc.coords.z
        val sx = if (rx < half) -1 else if (rx > half) 1 else 0
        val sz = if (rz < half) -1 else if (rz > half) 1 else 0
        return sx to sz
    }

    /** A far tile in the direction of the bash (straight for a side, diagonal for a corner). */
    fun bashFacing(npc: Npc, target: Player): CoordGrid {
        val (sx, sz) = bashSides(npc, target)
        val half = npc.size / 2
        return CoordGrid(
            npc.coords.x + half + sx * FACING_FAR,
            npc.coords.z + half + sz * FACING_FAR,
            npc.coords.level,
        )
    }

    /**
     * The two lines the bash marks, by where [target] stands in the ring round his 3x3 body. On the
     * middle tile of a side he does the side bash: a line of 5 tiles beside that side, centred on
     * its middle, and a line of 7 a tile further out. On any other tile he does the corner bash,
     * round the nearest corner: the same two lines (5 and 7 tiles) bent round it, the first from
     * the middle of one side to the middle of the next.
     */
    fun bashLines(npc: Npc, target: Player): Set<CoordGrid> {
        val half = npc.size / 2
        val cx = npc.coords.x + half
        val cz = npc.coords.z + half
        val level = npc.coords.level
        val (sx, sz) = bashSides(npc, target)
        val tiles = LinkedHashSet<CoordGrid>()
        fun add(dx: Int, dz: Int) {
            tiles += CoordGrid(cx + dx, cz + dz, level)
        }
        if (sx != 0 && sz != 0) {
            for (reach in intArrayOf(half + 1, half + 2)) {
                // From the middle of one side, round the corner, to the middle of the next.
                for (along in 0..reach) {
                    add(reach * sx, along * sz)
                    add(along * sx, reach * sz)
                }
            }
        } else {
            // Straight out of the side the target is on (the middle tile of that side).
            val px = if (sx == 0) 1 else 0
            val pz = if (sz == 0) 1 else 0
            for (reach in intArrayOf(half + 1, half + 2)) {
                for (k in -reach..reach) {
                    add(reach * sx + k * px, reach * sz + k * pz)
                }
            }
        }
        return tiles
    }

    /**
     * The enraged form's ring of lightning (from the game's footage): twelve strikes in a circle
     * round his body, as the normal bolts go: orange circles appear, the strike plays on those
     * exact tiles and the damage to the 3x3 round each lands as it ends (the middle of his body and
     * of each side are out of reach, the corners of his body are not).
     */
    private fun enrageBlast(fight: VetionFight) {
        if (fight.dead) {
            return
        }
        val centres = enrageCentres(fight.npc)
        for (centre in centres) {
            d.worldRepo.spotanimMap(wild_spotanims.vetion_bolt_impact_enraged, centre)
        }
        fight.later(d.now + STRIKE_HIT) {
            if (fight.dead) {
                return@later
            }
            for (centre in centres) {
                strike(fight, centre)
            }
        }
    }

    /**
     * The twelve strike points of the ring, by offset from his middle tile: (+-1, +-3), (+-3, +-1)
     * and (+-2, +-2).
     */
    fun enrageCentres(npc: Npc): List<CoordGrid> {
        val half = npc.size / 2
        val cx = npc.coords.x + half
        val cz = npc.coords.z + half
        val points = ArrayList<CoordGrid>()
        for ((dx, dz) in RING_OFFSETS) {
            for (sx in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    points += CoordGrid(cx + dx * sx, cz + dz * sz, npc.coords.level)
                }
            }
        }
        return points
    }

    // The hounds ------------------------------------------------------------------------------

    /** At half health in each form he calls two hounds (unless that form's were killed before). */
    private fun callHounds(fight: VetionFight) {
        val npc = fight.npc
        if (fight.houndsCalled[fight.form] || npc.hitpoints * 2 > npc.baseHitpointsLvl) {
            return
        }
        fight.houndsCalled[fight.form] = true
        yell(fight, VetionQuotes.Kind.HoundsCalled)
        npc.anim(if (enraged(fight)) wild_seqs.vetion_summon_enraged else wild_seqs.vetion_summon)
        val singles = !fight.lair.multi
        val type: NpcType =
            when {
                singles && enraged(fight) -> wild_npcs.calvarion_hound_greater
                singles -> wild_npcs.calvarion_hound
                enraged(fight) -> wild_npcs.vetion_hound_greater
                else -> wild_npcs.vetion_hound
            }
        repeat(HOUNDS) {
            val spot = freeBeside(fight)
            val hound = Npc(d.npcTypes[type], spot)
            hound.mode = org.rsmod.game.entity.npc.NpcMode.None
            d.npcRepo.add(hound, WildBosses.LIFETIME)
            fight.hounds += hound
        }
    }

    private fun freeBeside(fight: VetionFight): CoordGrid {
        val npc = fight.npc
        val taken = fight.hounds.map { it.coords }
        for (radius in 1..3) {
            for (dz in -radius until npc.size + radius) {
                for (dx in -radius until npc.size + radius) {
                    val spot = CoordGrid(npc.coords.x + dx, npc.coords.z + dz, npc.coords.level)
                    if (
                        spot in fight.lair.arena &&
                            d.standable(spot, HOUND_SIZE) &&
                            taken.none { d.chebyshev(it, spot) < HOUND_SIZE } &&
                            !d.onBody(npc, spot)
                    ) {
                        return spot
                    }
                }
            }
        }
        return npc.coords
    }

    private fun aimHounds(fight: VetionFight, inside: List<Player>) {
        for (hound in fight.hounds) {
            val nearest = inside.minBy { it.distanceTo(hound) }
            d.attackTarget(hound, nearest)
        }
    }

    /** Five minutes enraged without a kill: back to the normal form with the health left. */
    private fun revert(fight: VetionFight, now: Int) {
        fight.npc.resetTransmog()
        fight.form = 0
        fight.busyUntil = now + 1
    }

    // Helpers ---------------------------------------------------------------------------------

    /** He shouts a line of the kind [quotes] holds; the line is a cue for which attack comes. */
    private fun yell(fight: VetionFight, kind: VetionQuotes.Kind) {
        val quotes = VetionQuotes.lines(kind, enraged(fight))
        fight.npc.say(quotes[d.random.of(quotes.size)])
    }

    /** A tile on or beside [coords]: the solo bolt always lands on or next to the player. */
    private fun near(coords: CoordGrid): CoordGrid =
        CoordGrid(coords.x + d.random.of(-1..1), coords.z + d.random.of(-1..1), coords.level)

    private val floors = HashMap<WildLair, List<CoordGrid>>()

    private fun randomTile(fight: VetionFight): CoordGrid {
        val floor =
            floors.getOrPut(fight.lair) { fight.lair.arena.tiles().filter { d.walkable(it) } }
        return floor[d.random.of(floor.size)]
    }

    private companion object {
        /** The wiki: attack speed 6, and 5 once enraged. */
        const val NORMAL_RATE = 6
        const val ENRAGED_RATE = 5
        const val FIRST_ATTACK_DELAY = 3
        /** The ring's graphic lasts 4 ticks and the hit lands at its end: he moves after that. */
        const val ENRAGE_TICKS = 5

        /** The wiki: a max hit of 44 (Vet'ion) and 26 (Calvar'ion), more once enraged (a guess). */
        const val VETION_MAX = 44
        const val CALVARION_MAX = 26
        const val ENRAGED_MAX_PERCENT = 125
        const val VETION_BASH = 35
        const val CALVARION_BASH = 26

        const val SOLO_BOLTS = 5
        const val MAX_GROUP_BOLTS = 7

        /**
         * The circle and strike graphic lasts 4 ticks; the hit is queued a tick before its end (it
         * lands a tick later), and he swings a tick after the shout.
         */
        const val SWING_START = 1
        const val STRIKE_HIT = 3
        const val LIGHTNING_HOLD = 4
        const val EDGE_DIVISOR = 2

        const val BASH_ODDS = 3

        /** The shadows last 4 ticks; on the 4th he bashes, the smoke bursts and the hit lands. */
        const val BASH_WARNING = 5
        const val BASH_STRIKE = 4
        const val FACING_FAR = 8

        /** The wiki: unable to attack for 4.8 seconds. */
        const val BASH_STUN = 8
        val RING_OFFSETS = listOf(1 to 3, 3 to 1, 2 to 2)

        const val HOUNDS = 2
        const val HOUND_SIZE = 2

        /** The wiki: five minutes in the enraged form. */
        const val ENRAGED_LIMIT = 500
    }
}
