package org.rsmod.content.areas.misc.isleofsouls.toa

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.projanims
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcTint
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

typealias akkha_npcs = AkkhaNpcs

typealias akkha_locs = AkkhaLocs

typealias akkha_seqs = AkkhaSeqs

typealias akkha_spotanims = AkkhaSpotanims

object AkkhaNpcs : NpcReferences() {
    /** The three forms: each is hurt only by the style it fears. */
    val melee = find("akkha_melee")
    val range = find("akkha_range")
    val mage = find("akkha_mage")

    val shadow = find("akkha_shadow")

    /**
     * The final stand: the real Akkha in full colour (both protections up), and his shadows, which
     * are tinted as immune ([AkkhaTint]).
     */
    val enrage = find("akkha_enrage")
    val shadow_enrage = find("akkha_shadow_enrage")
    val unstable_orb = find("akkha_enrage_orb")

    /** The orbs left behind a player, by quadrant: darkness, lightning, ice, fire. */
    val darkness_orb = find("akkha_trail_orb_darkness")
    val lightning_orb = find("akkha_trail_orb_lightning")
    val frozen_orb = find("akkha_trail_orb_freeze")
    val burning_orb = find("akkha_trail_orb_burn")
}

/**
 * The colour overlay on Akkha and his shadows while nothing can hurt them. The cache has no shaded
 * models for either, so (before rev 239 added npc transparency) the pale look can only be the npc
 * tint. The values are a guess from screenshots of the real fight: hue kept, saturation low,
 * lightness pulled up so the shading flattens. `::akkhatint` changes them while the server runs
 * (not saved: copy the ones that look right in here).
 */
object AkkhaTint {
    var akkha = NpcTint(hue = NpcTint.KEEP, saturation = 1, lightness = 100, weight = 100)
    var shadow = NpcTint(hue = NpcTint.KEEP, saturation = 1, lightness = 100, weight = 100)

    /** How long one send of the tint lasts, and how often it is sent again while it should show. */
    const val SEND_TICKS = 50
    const val RESEND_TICKS = 10
}

object AkkhaLocs : LocReferences() {
    /** The quadrant symbols that glow in the memory blast, by quadrant. */
    val glow_darkness = find("akkha_symbol_glow_darkness")
    val glow_lightning = find("akkha_symbol_glow_lightning")
    val glow_freeze = find("akkha_symbol_glow_freeze")
    val glow_burn = find("akkha_symbol_glow_burn")
}

object AkkhaSeqs : SeqReferences() {
    val melee = find("npc_akkha_attack_melee_spear")
    val ranged = find("npc_akkha_attack_ranged_spear")
    val magic = find("npc_akkha_attack_magic_spear")
    val vanish = find("npc_akkha_teleport_disappear_spear")
    val appear = find("npc_akkha_teleport_appear_spear")
    val detonation = find("npc_akkha_attack_magic_01_spear")
    val memory = find("npc_akkha_attack_magic_02_spear")
    val trail = find("npc_akkha_attack_magic_03_spear")
    val final_stand = find("npc_akkha_attack_magic_04_spear")
    val shadow_spawn = find("npc_akkha_spawn_spear")
    val shadow_death = find("npc_akkha_death_spear")
    val ready = find("npc_akkha_idle_spear")
}

object AkkhaSpotanims : SpotanimReferences() {
    val magic_travel = find("akkha_magic_travel")
    val magic_impact = find("akkha_magic_impact")
    val ranged_travel = find("akkha_ranged_travel")

    /** Quadrant blasts by quadrant: darkness, lightning, ice, fire. */
    val darkness = find("akkha_darkness")
    val lightning = find("akkha_lightning")
    val freeze = find("akkha_freeze")
    val burn = find("akkha_burn")
    val orb_impact = find("akkha_enrage_orb_impact")
}

/**
 * Akkha, the Sands of Time. He cycles melee, ranged and magic forms, each praying against the style
 * it attacks with (the overhead shows it), and between swings runs a special: the memory blast, a
 * trail of orbs behind every player, or detonations in a plus around them. At 80, 60, 40 and 20
 * percent his four shadows appear and blast their quadrant on a timer; while he stands in a
 * quadrant whose shadow lives he cannot be hurt. At zero he makes a final stand as four shadows,
 * one of them real and only hurt in melee.
 *
 * What the wiki gives (forms, the thresholds, the specials, the final stand, the hits that move the
 * real shadow) is followed. The timers, the quadrant blast order and the max hits are the timings
 * that were read off the real fight; the effects of the lightning, ice and fire are approximations.
 */
class AkkhaRuntime(env: ToaRoomEnv, raid: ToaRaid) : ToaRoomRuntime(env, raid, ToaRoom.HetBoss) {
    private class Shadow(val npc: Npc, val index: Int, var immune: Boolean) {
        var counter = 0
        var delay = 0
        var dispelled = false
    }

    private class TrailOrb(val npc: Npc, var life: Int, val quadrant: Int)

    private class RollingOrb(val npc: Npc, val direction: AkkhaDirection) {
        var last: CoordGrid? = null
    }

    private class Step(val delay: Int, val action: () -> Unit)

    /** A burn or a darkness that keeps hurting for a few ticks. */
    private class Dot(val player: Player, val fire: Boolean, var next: Int, var remaining: Int)

    private class Final(val npc: Npc, var real: Boolean) {
        var hits = 0
    }

    var akkha: Npc? = null
        private set

    /**
     * Whether he walks and swings on his own; the tests turn it off to work on one rule at a time.
     */
    var fightEnabled = true

    private var form = 0
    private var shownType = akkha_npcs.melee.id
    private var nextWander = 0
    var standing = false
        private set

    var finalReal: Npc? = null
        private set

    /** How many of [SHADOW_THRESHOLDS] his health has gone below. */
    private var shadowPhases = 0
    /** Attacks left until the next special, which is itself the last of them. */
    private var attackCycles = 0
    private var attacksInStyle = 0
    private var lastSwingAt = 0

    /** Who he is after: kept until a special or until they leave the fight. */
    private var target: Player? = null
    private var specialIndex = 0
    private var nextAttack = 0
    private var vanished = false
    private var memory = ArrayDeque<Step>()

    /** The tick he last took a step towards his target. */
    private var lastStepAt = Int.MIN_VALUE / 2
    private var memoryDelay = 0
    private var trailTicks = 0
    private var detonationTicks = 0
    private var unstableTicks = -1
    private var memoryIterations = 4

    private val shadows = arrayOfNulls<Shadow>(4)

    /** Each shadow can be killed once: one killed in an earlier phase comes back immune. */
    private val killed = BooleanArray(4)
    private val finals = ArrayList<Final>()
    private val trailOrbs = ArrayList<TrailOrb>()
    private val unstableOrbs = ArrayList<RollingOrb>()
    private val lastTile = HashMap<Int, CoordGrid>()
    private val effectAt = HashMap<Int, IntArray>()
    private val icedUntil = HashMap<Int, Int>()

    /** Struck by lightning: protection prayers stay off until this tick. */
    private val lightningUntil = HashMap<Int, Int>()
    private val slowedFor = HashMap<Int, Int>()
    private val dots = ArrayList<Dot>()

    /** Npcs of the fight that have taken their last hit: the boss, a shadow, the real shadow. */
    private val downed = HashSet<Npc>()

    /** When each tinted npc last had its tint sent. */
    private val tintedAt = HashMap<Npc, Int>()

    private val settings
        get() = raid.settings

    private val styleNames = arrayOf("melee", "ranged", "magic")

    val style: String
        get() = styleNames[form]

    val isMemoryRunning: Boolean
        get() = memory.isNotEmpty()

    val isTrailing: Boolean
        get() = trailTicks in 1..TRAIL_ACTIVE_TICKS

    val shadowCount: Int
        get() = shadows.count { it != null && !it.dispelled }

    val unstableOrbCount: Int
        get() = unstableOrbs.size

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        form = 0
        shownType = akkha_npcs.melee.id
        standing = false
        vanished = false
        finalReal = null
        shadowPhases = 0
        killed.fill(false)
        attackCycles = specialEvery()
        attacksInStyle = 0
        target = null
        specialIndex = env.random.of(3)
        memoryIterations = 4 + min(2, pathLevel / 2)
        // He crawls: the client glides him a tile over two ticks instead of a step and a wait.
        akkha =
            spawn(akkha_npcs.melee, SPAWN, points = 1.0).also {
                it.defaultMoveSpeed = MoveSpeed.Crawl
            }
    }

    override fun onStart() {
        wanderTo = null
        nextAttack = now + ATTACK_TICKS
        lastSwingAt = now
        target = null
        bossBar = akkha
    }

    override fun onComplete() {
        clearAdds()
        dots.clear()
    }

    override fun onReset() {
        clearAdds()
        dots.clear()
        akkha?.let(::despawn)
        for (shadow in finals) {
            despawn(shadow.npc)
        }
        finals.clear()
        lastTile.clear()
        icedUntil.clear()
        downed.clear()
        tintedAt.clear()
        build()
    }

    private fun clearAdds() {
        for (index in shadows.indices) {
            shadows[index]?.let { despawn(it.npc) }
            shadows[index] = null
        }
        for (orb in trailOrbs) {
            despawn(orb.npc)
        }
        trailOrbs.clear()
        for (orb in unstableOrbs) {
            despawn(orb.npc)
        }
        unstableOrbs.clear()
        memory.clear()
        trailTicks = 0
        detonationTicks = 0
        unstableTicks = -1
    }

    // Hits
    // --------------------------------------------------------------------------------------------

    /** The one style his current form prays against: nothing of it gets through. */
    private fun protectedStyle(): HitType =
        when (form) {
            0 -> HitType.Melee
            1 -> HitType.Ranged
            else -> HitType.Magic
        }

    /** He cannot be hurt while he stands in a quadrant whose shadow is alive. */
    fun onShadedQuadrant(): Boolean {
        val boss = akkha ?: return false
        val real = raid.realOf(boss.coords) ?: return false
        val shadow = shadows[quadrantOf(real.translate(1, 1))]
        return shadow != null && !shadow.dispelled
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        hit.damage = damageAfterRules(npc, hit.type, hit.damage, source)
    }

    /**
     * What a hit of [damage] in [type] does to [npc] under the fight's rules: nothing while he is
     * immune, and never enough to kill (a boss that would die is left on one hitpoint, which the
     * room's tick turns into the next stage).
     */
    fun damageAfterRules(npc: Npc, type: HitType, damage: Int, source: Player?): Int {
        val boss = akkha
        if (npc === boss) {
            val blocked =
                when {
                    standing || vanished -> true
                    onShadedQuadrant() -> {
                        source?.mes(
                            "<col=ef0083>Akkha is currently immune to your attacks, but his " +
                                "shadows are still disrupted.</col>"
                        )
                        true
                    }
                    else -> type == protectedStyle()
                }
            return if (blocked) 0 else leaveOne(npc, damage)
        }
        val shadow = shadows.firstOrNull { it?.npc === npc }
        if (shadow != null) {
            if (shadow.immune || shadow.dispelled) {
                if (!shadow.dispelled) {
                    source?.mes(
                        "<col=ff289d>This shadow is currently immune to your attacks.</col>"
                    )
                }
                return 0
            }
            return leaveOne(npc, damage)
        }
        val last = finals.firstOrNull { it.npc === npc } ?: return damage
        if (!last.real || type != HitType.Melee || isCompleted) {
            return 0
        }
        val dealt = leaveOne(npc, damage)
        if (dealt > 0) {
            last.hits++
        }
        return dealt
    }

    /**
     * A hit never kills these: it leaves one hitpoint and notes that the npc is down, which the
     * room's tick turns into the next stage (hitpoints can creep back up before it gets there).
     */
    private fun leaveOne(npc: Npc, damage: Int): Int {
        if (damage < npc.hitpoints) {
            return damage
        }
        downed += npc
        return (npc.hitpoints - 1).coerceAtLeast(0)
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        tickMemory()
        tickTimers()
        tickShadows()
        tickTrail()
        tickDots()
        tickIce()
        tickLightning()
        tickUnstable()
        checkFinals()
        val boss = akkha ?: return
        // Before the early returns, so the tint keeps being sent through a memory blast.
        refreshLook()
        if (standing || vanished || isMemoryRunning || !isStarted) {
            return
        }
        if (boss in downed) {
            finalStand()
            return
        }
        checkThresholds(boss)
        if (fightEnabled) {
            fight(boss)
        }
    }

    /**
     * At 80, 60, 40 and 20 percent of his health the shadows appear (and later come back). Each
     * threshold counts once: health he regains while immune does not bring the shadows back again,
     * and one hit past two thresholds is one phase.
     */
    private fun checkThresholds(boss: Npc) {
        val hp = boss.hitpoints
        if (hp <= 0) {
            return
        }
        val max = boss.baseHitpointsLvl
        var passed = shadowPhases
        while (passed < SHADOW_THRESHOLDS.size && hp <= max * SHADOW_THRESHOLDS[passed] / 100) {
            passed++
        }
        if (passed == shadowPhases) {
            return
        }
        val first = shadowPhases == 0
        shadowPhases = passed
        spawnShadows(first)
    }

    private fun fight(boss: Npc) {
        // One that cannot get a swing in for a minute changes style anyway (wiki).
        if (now - lastSwingAt > STUCK_TICKS) {
            changeStyle(boss)
            lastSwingAt = now
        }
        val target = target(boss) ?: return
        boss.facePlayer(target)
        if (!inReach(boss, target)) {
            stepToward(boss, target)
            if (style == "melee") {
                return
            }
        }
        if (now < nextAttack) {
            return
        }
        if (style == "melee" && !inReach(boss, target)) {
            return
        }
        nextAttack = now + ATTACK_TICKS
        lastSwingAt = now
        swing(boss, target)
    }

    /**
     * Six attacks and then a special as the seventh; five and the sixth with Stay Vigilant (wiki).
     */
    private fun specialEvery(): Int = if (settings.isActive(Inv.STAY_VIGILANT)) 6 else 7

    /**
     * Every special is followed by a change of style. With Stay Vigilant he may also change after
     * any attack: a 1 in 4 roll after a normal one, 1 in 5 after a special, and never before two
     * attacks in a style (wiki, Mod Ash). The order is always melee, ranged, magic.
     */
    private fun swing(boss: Npc, target: Player) {
        attacksInStyle++
        val vigilant = settings.isActive(Inv.STAY_VIGILANT)
        if (--attackCycles <= 0) {
            attackCycles = specialEvery()
            val players = challengePlayers().size
            if (settings.isActive(Inv.DOUBLE_TROUBLE)) {
                specialIndex %= if (players < 2) 1 else 2
                special(if (specialIndex == 0) SPECIAL_MEMORY else SPECIAL_DETONATION)
                trailTicks = TRAIL_TICKS
            } else {
                specialIndex %= if (players < 2) 2 else 3
                special(specialIndex)
            }
            specialIndex++
            if (!vigilant) {
                changeStyle(boss)
            } else {
                maybeChangeStyle(boss, VIGILANT_SPECIAL_ROLL)
            }
            // After a special he goes for whoever is closest (wiki).
            this.target = pickTarget(boss)
            return
        }
        if (style == "melee") {
            boss.anim(akkha_seqs.melee)
            cleave(boss, target)
        } else {
            volley(boss, magic = style == "magic")
            if (settings.isActive(Inv.KEEP_BACK)) {
                cleave(boss, null)
            }
        }
        if (vigilant) {
            maybeChangeStyle(boss, VIGILANT_ATTACK_ROLL)
        }
    }

    private fun maybeChangeStyle(boss: Npc, roll: Int) {
        if (attacksInStyle >= MIN_STYLE_ATTACKS && env.random.of(roll) == 0) {
            changeStyle(boss)
        }
    }

    /** One of his specials: the memory blast, the orb trail or the detonation. */
    fun special(kind: Int) {
        val boss = akkha ?: return
        when (kind) {
            SPECIAL_MEMORY -> memoryBlast(boss)
            SPECIAL_TRAIL -> {
                boss.anim(akkha_seqs.trail)
                trailTicks = TRAIL_TICKS
            }
            else -> startDetonation(boss)
        }
    }

    fun changeStyle() {
        akkha?.let(::changeStyle)
    }

    fun shadowNpc(index: Int): Npc? = shadows[index]?.npc

    fun shadowImmune(index: Int): Boolean = shadows[index]?.immune ?: false

    val finalNpcs: List<Npc>
        get() = finals.map { it.npc }

    val isVanished: Boolean
        get() = vanished

    val trailOrbCount: Int
        get() = trailOrbs.size

    private fun changeStyle(boss: Npc) {
        attacksInStyle = 0
        sound(toa_synths.akkha_style, roomPlayers())
        form = (form + 1) % styleNames.size
        refreshLook()
    }

    private fun formType(): NpcType =
        when (form) {
            0 -> akkha_npcs.melee
            1 -> akkha_npcs.range
            else -> akkha_npcs.mage
        }

    /**
     * What he looks like: his form's overhead, and the immune tint while nothing can hurt him (he
     * stands in a shaded quadrant).
     */
    private fun refreshLook() {
        val boss = akkha ?: return
        if (standing) {
            return
        }
        showShaded(boss, onShadedQuadrant(), AkkhaTint.akkha)
        val want = formType()
        if (want.id == shownType) {
            return
        }
        shownType = want.id
        if (want.id == akkha_npcs.melee.id) {
            boss.resetTransmog()
        } else {
            boss.transmog(env.npcTypes[want], Int.MAX_VALUE)
        }
    }

    /** Whether he looks shaded right now. */
    val isShaded: Boolean
        get() = akkha?.activeTint != null

    /**
     * Keeps [tint] on [npc] while [shaded] and takes it off after. The tint is sent for a while and
     * sent again before it runs out, so a player who comes into view later sees it too, and a
     * changed [AkkhaTint] shows at once.
     */
    private fun showShaded(npc: Npc, shaded: Boolean, tint: NpcTint) {
        if (!shaded) {
            tintedAt.remove(npc)
            npc.resetTint()
            return
        }
        val sentAt = tintedAt[npc]
        if (npc.activeTint == tint && sentAt != null && now - sentAt < AkkhaTint.RESEND_TICKS) {
            return
        }
        tintedAt[npc] = now
        npc.tint(tint, AkkhaTint.SEND_TICKS)
    }

    override fun onIdle() {
        val boss = akkha ?: return
        wanderTo?.let { dest ->
            wanderStep(boss, dest)
            return
        }
        if (now < nextWander) {
            return
        }
        nextWander = now + WANDER_TICKS + env.random.of(WANDER_TICKS)
        val dest =
            SPAWN.translate(
                env.random.of(-WANDER_RANGE, WANDER_RANGE),
                env.random.of(-WANDER_RANGE, WANDER_RANGE),
            )
        for (dx in 0 until boss.size) {
            for (dz in 0 until boss.size) {
                val tile = dest.translate(dx, dz)
                if (!walkable(tile) || !room.inChallenge(tile)) {
                    return
                }
            }
        }
        wanderTo = dest
        wanderStep(boss, dest)
    }

    /** Where he is strolling to before the fight, a tile every [STEP_TICKS] ticks. */
    private var wanderTo: CoordGrid? = null

    private fun wanderStep(boss: Npc, dest: CoordGrid) {
        val sw = raid.realOf(boss.coords)
        if (sw == null || sw == dest) {
            wanderTo = null
            return
        }
        if (now - lastStepAt < STEP_TICKS) {
            return
        }
        lastStepAt = now
        boss.walk(
            raid.regionOf(
                sw.translate(Integer.signum(dest.x - sw.x), Integer.signum(dest.z - sw.z))
            )
        )
    }

    override fun hitpointsGone(npc: Npc) {
        downed += npc
        if (npc.hitpoints < 1) {
            npc.hitpoints = 1
        }
    }

    // Where he is and who he fights
    // -----------------------------------------------------------------

    private fun centre(boss: Npc): CoordGrid? = raid.realOf(boss.coords)?.translate(1, 1)

    /** He keeps to one player until a special, or until they are out of the fight. */
    private fun target(boss: Npc): Player? {
        val players = challengePlayers()
        target?.let { if (it in players) return it }
        return pickTarget(boss).also { target = it }
    }

    /**
     * The player closest to his centre tile; on a tie the one with the highest defence bonus in any
     * style, a Dinh's bulwark counting half as much again, and then whoever is first in the party
     * (wiki, Mod Ash).
     */
    private fun pickTarget(boss: Npc): Player? {
        val at = centre(boss) ?: return null
        var best: Player? = null
        var bestDistance = Int.MAX_VALUE
        var bestBonus = Int.MIN_VALUE
        for (player in challengePlayers()) {
            val real = raid.realOf(player.coords) ?: continue
            val distance = max(abs(real.x - at.x), abs(real.z - at.z))
            val bonus = defenceScore(player)
            if (distance < bestDistance || (distance == bestDistance && bonus > bestBonus)) {
                best = player
                bestDistance = distance
                bestBonus = bonus
            }
        }
        return best
    }

    private fun defenceScore(player: Player): Int {
        val bonuses = env.wornBonuses
        val highest =
            maxOf(
                bonuses.defensiveStabBonus(player),
                bonuses.defensiveSlashBonus(player),
                bonuses.defensiveCrushBonus(player),
                bonuses.defensiveRangedBonus(player),
                bonuses.defensiveMagicBonus(player),
            )
        val weapon = player.worn[Wearpos.RightHand.slot]
        val bulwark = weapon.isType(objs.dinhs_bulwark) || weapon.isType(objs.dinhs_blazing_bulwark)
        return if (bulwark && highest > 0) highest * 3 / 2 else highest
    }

    /** Whether [player] stands in his reach: beside him or under him, but not on a corner. */
    private fun inReach(boss: Npc, player: Player): Boolean {
        val sw = raid.realOf(boss.coords) ?: return false
        val at = raid.realOf(player.coords) ?: return false
        val dx = if (at.x < sw.x) sw.x - at.x else max(0, at.x - (sw.x + boss.size - 1))
        val dz = if (at.z < sw.z) sw.z - at.z else max(0, at.z - (sw.z + boss.size - 1))
        return max(dx, dz) <= 1 && !(dx == 1 && dz == 1)
    }

    /** He walks at half speed: a step every [STEP_TICKS] ticks. */
    private fun stepToward(boss: Npc, player: Player) {
        if (now - lastStepAt < STEP_TICKS) {
            return
        }
        val from = centre(boss) ?: return
        val to = raid.realOf(player.coords) ?: return
        val dx = Integer.signum(to.x - from.x)
        val dz = Integer.signum(to.z - from.z)
        if (dx == 0 && dz == 0) {
            return
        }
        val sw = raid.realOf(boss.coords) ?: return
        boss.walk(raid.regionOf(sw.translate(dx, dz)))
        lastStepAt = now
    }

    private fun quadrantOf(real: CoordGrid): Int =
        if (real.x >= QUADRANT_BASE.x) {
            if (real.z >= QUADRANT_BASE.z) 0 else 1
        } else {
            if (real.z >= QUADRANT_BASE.z) 3 else 2
        }

    // Attacks
    // -----------------------------------------------------------------------------------------

    private val meleeAttack =
        NpcAttack(NpcAttackStyle.Melee, anim = akkha_seqs.melee, maxHit = MAX_STYLE)

    private val rangedAttack =
        NpcAttack(
            NpcAttackStyle.Ranged,
            anim = akkha_seqs.ranged,
            projectile = akkha_spotanims.ranged_travel,
            projanim = projanims.arrow,
            maxHit = MAX_STYLE,
        )

    private val magicAttack =
        NpcAttack(
            NpcAttackStyle.Magic,
            anim = akkha_seqs.magic,
            projectile = akkha_spotanims.magic_travel,
            projanim = projanims.magic_spell,
            impactSpot = akkha_spotanims.magic_impact,
            maxHit = MAX_STYLE,
        )

    /** Melee hits his target and anyone beside them in his reach. */
    private fun cleave(boss: Npc, target: Player?) {
        for (player in challengePlayers()) {
            if (target != null && player !== target && !beside(player, target)) {
                continue
            }
            if (!inReach(boss, player)) {
                continue
            }
            strike(boss, player, meleeAttack, through = MELEE_THROUGH, playAnim = false)
        }
    }

    private fun beside(a: Player, b: Player): Boolean {
        val one = raid.realOf(a.coords) ?: return false
        val two = raid.realOf(b.coords) ?: return false
        return max(abs(one.x - two.x), abs(one.z - two.z)) <= 1
    }

    /** Ranged and magic hit everyone in the fight. */
    private fun volley(boss: Npc, magic: Boolean) {
        boss.anim(if (magic) akkha_seqs.magic else akkha_seqs.ranged)
        sound(if (magic) toa_synths.akkha_magic else toa_synths.akkha_ranged)
        later(1) {
            if (isStarted && !standing) {
                for (player in challengePlayers()) {
                    if (magic) {
                        strike(boss, player, magicAttack, through = MAGIC_THROUGH, playAnim = false)
                    } else {
                        strike(
                            boss,
                            player,
                            rangedAttack,
                            through = RANGED_THROUGH,
                            playAnim = false,
                        )
                    }
                }
            }
        }
    }

    private fun startDetonation(boss: Npc) {
        boss.anim(akkha_seqs.detonation)
        detonationTicks = DETONATION_TICKS
    }

    /** Three tiles out from every player in a plus (or with Feeling Special, maybe an x). */
    private fun detonate() {
        val cross = settings.isActive(Inv.FEELING_SPECIAL) && env.random.of(2) == 0
        val steps =
            if (cross) {
                listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
            } else {
                listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
            }
        val struck = HashSet<CoordGrid>()
        val players = challengePlayers()
        for (player in players) {
            val start = raid.realOf(player.coords) ?: continue
            val quadrant = quadrantOf(start)
            for ((dx, dz) in steps) {
                var tile = start
                repeat(3) {
                    tile = tile.translate(dx, dz)
                    if (!struck.add(tile) || !walkable(tile)) {
                        return@repeat
                    }
                    graphic(quadrantSpot(quadrant), tile, height = quadrantHeight(quadrant))
                    for (victim in players) {
                        if (raid.realOf(victim.coords) == tile) {
                            hurt(victim, MAX_DETONATION, env.random.of(3))
                            applyEffect(victim, quadrant)
                        }
                    }
                }
            }
        }
    }

    private fun tickTimers() {
        if (detonationTicks > 0) {
            detonationTicks--
            if (detonationTicks == DETONATION_TICKS - 2) {
                for (player in challengePlayers()) {
                    player.mes("<col=a53fff>You have been marked for detonation!</col>")
                }
            } else if (detonationTicks == 0) {
                detonate()
            }
        }
        if (trailTicks > 0) {
            trailTicks--
            if (trailTicks == TRAIL_ACTIVE_TICKS) {
                for (player in challengePlayers()) {
                    player.mes("<col=a53fff>Magical orbs begin to materialise behind you!</col>")
                    raid.realOf(player.coords)?.let { lastTile[player.accountId] = it }
                }
            } else if (trailTicks == 0) {
                for (player in challengePlayers()) {
                    player.mes("<col=229628>The magical orbs stop materialising.</col>")
                }
                lastTile.clear()
            }
        }
    }

    // The memory blast
    // ---------------------------------------------------------------------------------

    /**
     * He vanishes, lights the quadrants in an order, then blasts every quadrant but the lit one.
     */
    private fun memoryBlast(boss: Npc) {
        boss.resetMovement()
        boss.anim(akkha_seqs.memory)
        val order = ArrayList<Int>()
        order += env.random.of(4)
        for (i in 1 until 4) {
            order += (order[i - 1] + if (env.random.of(2) == 0) 3 else 1) % 4
        }
        var shown = 0
        val feeling = settings.isActive(Inv.FEELING_SPECIAL)
        val light: () -> Unit = {
            val quadrant = order[shown++ % 4]
            val at = LIGHT_TILES[quadrant]
            placeLoc(at, glow(quadrant), LocShape.CentrepieceStraight, LocAngle[0])
            later(1) { removeLoc(at) }
            sound(toa_synths.akkha_symbol)
        }
        val steps = ArrayList<Step>()
        steps +=
            Step(1) {
                for (player in challengePlayers()) {
                    player.mes("<col=6800bf>Sections of the room start to glow...</col>")
                }
            }
        steps +=
            Step(1) {
                vanished = true
                boss.anim(akkha_seqs.vanish)
            }
        val hideStep = steps.size
        steps += Step(2) { light() }
        for (i in 1 until memoryIterations) {
            steps += Step(if (feeling && i == memoryIterations - 1) 0 else 2, light)
        }
        for (i in 0 until memoryIterations) {
            val lit = order[i % 4]
            val delay = if (i == memoryIterations - 1) 3 else if (feeling) 2 else 4
            steps += Step(delay) { quadrantBlast(fromShadow = false, (0..3).filter { it != lit }) }
        }
        val appearStep = steps.size
        steps +=
            Step(1) {
                boss.telejump(env.collision, raid.regionOf(MEMORY_TILE))
                boss.anim(akkha_seqs.appear)
            }
        steps +=
            Step(0) {
                vanished = false
                nextAttack = now + ATTACK_TICKS
                lastSwingAt = now
            }
        // The hide step is a step like the others: Akkha is out of the world from it until the
        // appear step, however long the quadrants take.
        val hiddenFor = steps.subList(hideStep, appearStep).sumOf { it.delay } + 2
        steps[hideStep] =
            Step(steps[hideStep].delay) {
                env.npcRepo.hide(boss, hiddenFor)
                light()
            }
        memory = ArrayDeque(steps)
        memoryDelay = 2
    }

    private fun glow(quadrant: Int) =
        when (quadrant) {
            0 -> akkha_locs.glow_darkness
            1 -> akkha_locs.glow_lightning
            2 -> akkha_locs.glow_freeze
            else -> akkha_locs.glow_burn
        }

    private fun tickMemory() {
        if (memory.isEmpty()) {
            return
        }
        if (memoryDelay > 0 && --memoryDelay > 0) {
            return
        }
        while (memory.isNotEmpty() && memoryDelay <= 0) {
            val step = memory.removeFirst()
            step.action()
            memoryDelay = step.delay
        }
    }

    private fun quadrantSpot(quadrant: Int) =
        when (quadrant) {
            0 -> akkha_spotanims.darkness
            1 -> akkha_spotanims.lightning
            2 -> akkha_spotanims.freeze
            else -> akkha_spotanims.burn
        }

    private fun quadrantHeight(quadrant: Int) = if (quadrant == 3) 124 else 0

    private fun quadrantTiles(quadrant: Int): List<CoordGrid> =
        AkkhaData.quadrants[quadrant].flatMap { column ->
            (column.firstZ..column.lastZ).map { CoordGrid(column.x, it, Z) }
        }

    /**
     * Fire sweeps across whole quadrants from their corner (or, from a shadow, towards it). His own
     * memory blast hurts at once; a shadow's sweep reaches the near half a tick before the far.
     */
    private fun quadrantBlast(fromShadow: Boolean, quadrants: List<Int>) {
        sound(toa_synths.akkha_quadrant)
        for (quadrant in quadrants) {
            val origin = QUADRANT_ORIGINS[quadrant]
            val tiles = quadrantTiles(quadrant)
            val delays =
                tiles.associateWith { tile ->
                    val distance = max(abs(tile.x - origin.x), abs(tile.z - origin.z)) * 3
                    if (fromShadow) 27 - distance else distance
                }
            val lead = if (fromShadow) SHADOW_SWEEP_LEAD else 0
            for ((tile, delay) in delays) {
                graphic(
                    quadrantSpot(quadrant),
                    tile,
                    height = quadrantHeight(quadrant),
                    delay = lead + delay,
                )
            }
            if (!fromShadow) {
                for (player in challengePlayers()) {
                    val at = raid.realOf(player.coords) ?: continue
                    if (at in delays) {
                        hurt(player, MAX_QUADRANT, env.random.of(3))
                        applyEffect(player, quadrant)
                    }
                }
                continue
            }
            // The near half of the sweep lands a tick before the far half.
            for (wave in 0..1) {
                later(2 + wave) {
                    for (player in challengePlayers()) {
                        val at = raid.realOf(player.coords) ?: continue
                        val delay = delays[at] ?: continue
                        if ((wave == 0) == (delay < 18)) {
                            hurt(player, MAX_QUADRANT, env.random.of(3))
                            applyEffect(player, quadrant)
                        }
                    }
                }
            }
        }
    }

    // Shadows
    // -----------------------------------------------------------------------------------------

    private fun spawnShadows(first: Boolean) {
        for (player in challengePlayers()) {
            player.mes(
                if (first) {
                    "<col=ef0083>Shadows appear throughout the room!</col>"
                } else {
                    "<col=ef0083>The shadows in the room are restored!</col>"
                }
            )
        }
        // Only the shadows not yet killed can be hurt; the killed ones come back immune.
        for (index in 0 until 4) {
            val shadow = shadows[index]
            if (shadow == null || shadow.dispelled) {
                val npc = spawn(akkha_npcs.shadow, SHADOW_TILES[index], points = 1.0)
                npc.anim(akkha_seqs.shadow_spawn)
                val made = Shadow(npc, index, immune = killed[index])
                shadows[index] = made
                refreshShadowLook(made)
            } else {
                shadow.npc.hitpoints = shadow.npc.baseHitpointsLvl
                shadow.counter = 0
                shadow.delay = 0
                shadow.immune = killed[index]
                refreshShadowLook(shadow)
            }
        }
    }

    /** Each shadow fills its timer and then blasts its own quadrant. */
    private fun tickShadows() {
        for (shadow in shadows) {
            if (shadow == null || shadow.dispelled) {
                continue
            }
            if (shadow.npc in downed) {
                dispel(shadow)
                continue
            }
            refreshShadowLook(shadow)
            if (isMemoryRunning) {
                continue
            }
            if (shadow.delay <= 0) {
                if (++shadow.counter >= SHADOW_TIMER) {
                    shadow.counter = 0
                    shadow.delay = 4
                }
            } else {
                shadow.delay--
                if (shadow.delay == 3) {
                    shadow.npc.anim(akkha_seqs.memory)
                } else if (shadow.delay == 2) {
                    quadrantBlast(fromShadow = true, listOf(shadow.index))
                }
            }
        }
    }

    /** A shadow that cannot be hurt is shown shaded. */
    private fun refreshShadowLook(shadow: Shadow) {
        showShaded(shadow.npc, shadow.immune, AkkhaTint.shadow)
    }

    /**
     * Killing a shadow shields the others until the next threshold, and that shadow for the rest of
     * the fight.
     */
    private fun dispel(shadow: Shadow) {
        shadow.dispelled = true
        killed[shadow.index] = true
        shadow.npc.anim(akkha_seqs.shadow_death)
        later(ticksOf(akkha_seqs.shadow_death)) { despawn(shadow.npc) }
        for (other in shadows) {
            if (other != null && !other.dispelled) {
                other.immune = true
            }
        }
    }

    // Orbs
    // --------------------------------------------------------------------------------------------

    private fun trailOrbType(quadrant: Int) =
        when (quadrant) {
            0 -> akkha_npcs.darkness_orb
            1 -> akkha_npcs.lightning_orb
            2 -> akkha_npcs.frozen_orb
            else -> akkha_npcs.burning_orb
        }

    /** While the trail lasts, a player who moves leaves an orb on the tile they left. */
    private fun tickTrail() {
        if (isTrailing) {
            for (player in challengePlayers()) {
                val here = raid.realOf(player.coords) ?: continue
                val before = lastTile.put(player.accountId, here)
                if (before != null && before != here) {
                    player.soundSynth(toa_synths.akkha_trail)
                    spawnTrailOrb(before)
                    if (settings.isActive(Inv.FEELING_SPECIAL)) {
                        spawnTrailOrb(
                            here.translate(
                                Integer.signum(here.x - before.x),
                                Integer.signum(here.z - before.z),
                            )
                        )
                    }
                }
            }
        }
        for (orb in trailOrbs.toList()) {
            val at = raid.realOf(orb.npc.coords)
            val victim = challengePlayers().firstOrNull { raid.realOf(it.coords) == at }
            if (victim != null) {
                hurt(victim, MAX_TRAIL, env.random.of(3))
                graphic(quadrantSpot(orb.quadrant), at!!, height = quadrantHeight(orb.quadrant))
                applyEffect(victim, orb.quadrant)
            }
            if (victim != null || --orb.life <= 0) {
                trailOrbs -= orb
                despawn(orb.npc)
            }
        }
    }

    private fun spawnTrailOrb(real: CoordGrid) {
        if (!walkable(real) || !room.inChallenge(real)) {
            return
        }
        if (trailOrbs.any { raid.realOf(it.npc.coords) == real }) {
            return
        }
        val quadrant = quadrantOf(real)
        val npc = spawn(trailOrbType(quadrant), real, scale = false, points = 0.0)
        trailOrbs += TrailOrb(npc, TRAIL_ORB_LIFE, quadrant)
    }

    /**
     * Sets an unstable orb rolling from the real tile [real]. It rolls through players and npcs
     * (one would otherwise stop in front of the player it should hit), and only walls stop it.
     */
    fun rollUnstableOrb(real: CoordGrid, direction: AkkhaDirection) {
        val npc =
            spawn(akkha_npcs.unstable_orb, real, scale = false, points = 0.0, passThrough = true)
        unstableOrbs += RollingOrb(npc, direction)
    }

    /** Unstable orbs of the final stand: they roll in a line and burst on the first wall. */
    private fun tickUnstable() {
        if (unstableTicks > 0 && --unstableTicks <= 0) {
            unstableTicks = UNSTABLE_INTERVAL
            repeat(UNSTABLE_ORBS) {
                val path = AkkhaData.orbPaths[env.random.of(AkkhaData.orbPaths.size)]
                rollUnstableOrb(CoordGrid(path.x, path.z, Z), path.direction)
            }
        }
        for (orb in unstableOrbs.toList()) {
            val here = raid.realOf(orb.npc.coords)
            // An orb that did not get where it was sent has met a wall; one that left the arena is
            // done. Either way it bursts and goes.
            if (here == null || !room.inChallenge(here) || orb.last == here) {
                if (here != null) {
                    graphic(akkha_spotanims.orb_impact, here)
                }
                unstableOrbs -= orb
                despawn(orb.npc)
                continue
            }
            val victims = challengePlayers().filter { raid.realOf(it.coords) == here }
            if (victims.isNotEmpty()) {
                // The hit is shared by everyone on the tile; Protect from Magic takes a quarter off
                // before the cap of 25 (wiki).
                val share = (maxHit(MAX_UNSTABLE) + env.random.of(3)) / victims.size
                for (victim in victims) {
                    var damage = share
                    if (victim.vars[varbits.protect_from_magic] == 1) {
                        damage = damage * UNSTABLE_PRAYER_PERCENT / 100
                    }
                    hurtFlat(victim, min(UNSTABLE_CAP, damage))
                }
                unstableOrbs -= orb
                despawn(orb.npc)
                continue
            }
            orb.last = here
            orb.npc.walk(raid.regionOf(here.translate(orb.direction.dx, orb.direction.dz)))
        }
    }

    // Effects
    // -----------------------------------------------------------------------------------------

    /** Each quadrant's element: darkness, lightning, ice or fire. */
    private fun applyEffect(player: Player, quadrant: Int) {
        val last = effectAt.getOrPut(player.accountId) { IntArray(4) { Int.MIN_VALUE / 2 } }
        val cooldown = EFFECT_COOLDOWNS[quadrant]
        if (now <= last[quadrant] + cooldown) {
            return
        }
        last[quadrant] = now
        when (quadrant) {
            0 -> {
                player.mes("<col=ff3045>You have been consumed by darkness!</col>")
                dots += Dot(player, fire = false, next = now + 1, remaining = 4)
                // The darkness also drains every combat stat by 3 (wiki).
                for (stat in DARKNESS_DRAINS) {
                    player.statSub(stat, DARKNESS_DRAIN, 0)
                }
            }
            1 -> {
                player.mes(
                    "<col=ff3045>You have been struck by lightning and can't use protection " +
                        "prayers!</col>"
                )
                dropProtectionPrayers(player)
                lightningUntil[player.accountId] = now + LIGHTNING_LOCKOUT
            }
            2 -> {
                player.mes("<col=ff3045>Your attacks have been slowed by some ice!</col>")
                icedUntil[player.accountId] = now + ICE_TICKS
            }
            else -> {
                player.mes("<col=ff3045>You have been set alight!</col>")
                dots += Dot(player, fire = true, next = now + 1, remaining = 5)
            }
        }
    }

    private fun dropProtectionPrayers(player: Player) {
        for (varbit in
            listOf(
                varbits.protect_from_melee,
                varbits.protect_from_missiles,
                varbits.protect_from_magic,
            )) {
            VarPlayerIntMapSetter.set(player, varbit, 0)
        }
        player.overheadIcon = null
    }

    private fun tickDots() {
        for (dot in dots.toList()) {
            if (!dot.player.isValidDot()) {
                dots -= dot
                continue
            }
            if (now < dot.next) {
                continue
            }
            dot.player.queueHit(1, HitType.Typeless, if (dot.fire) FIRE_DAMAGE else DARKNESS_DAMAGE)
            if (dot.fire) {
                dot.player.soundSynth(toa_synths.akkha_burn)
                dot.next = now + 2
                val here = raid.realOf(dot.player.coords)
                for (other in challengePlayers()) {
                    val at = raid.realOf(other.coords)
                    if (
                        other !== dot.player &&
                            here != null &&
                            at != null &&
                            max(abs(here.x - at.x), abs(here.z - at.z)) <= 1
                    ) {
                        applyEffect(other, 3)
                    }
                }
            } else {
                dot.next = now + 1
            }
            if (--dot.remaining <= 0) {
                if (dot.fire) {
                    dot.player.mes("<col=229628>The fire around you goes out.</col>")
                }
                dots -= dot
            }
        }
    }

    private fun Player.isValidDot(): Boolean = isStarted && this in challengePlayers()

    /**
     * Lightning keeps protection prayers off for a short while: any switched back on in that time
     * goes off again (the length is a guess; the wiki only says they can't be used).
     */
    private fun tickLightning() {
        for ((id, until) in lightningUntil.toList()) {
            if (now >= until) {
                lightningUntil.remove(id)
                continue
            }
            val player = challengePlayers().firstOrNull { it.accountId == id } ?: continue
            val praying =
                player.vars[varbits.protect_from_melee] == 1 ||
                    player.vars[varbits.protect_from_missiles] == 1 ||
                    player.vars[varbits.protect_from_magic] == 1
            if (praying) {
                dropProtectionPrayers(player)
            }
        }
    }

    /** Ice slows a frozen player's attacks by a tick. */
    private fun tickIce() {
        for ((id, until) in icedUntil.toList()) {
            if (now >= until) {
                icedUntil.remove(id)
                slowedFor.remove(id)
                continue
            }
            val player = challengePlayers().firstOrNull { it.accountId == id } ?: continue
            val delay = player.actionDelay
            if (delay > now && slowedFor[id] != delay) {
                player.actionDelay = delay + 1
                slowedFor[id] = delay + 1
            }
        }
    }

    // The shadows and the final stand
    // ---------------------------------------------------------------

    private fun finalStand() {
        val boss = akkha ?: return
        standing = true
        boss.resetMovement()
        boss.anim(akkha_seqs.final_stand)
        for (player in challengePlayers()) {
            player.mes("<col=ef0083>Akkha makes his final stand!</col>")
        }
        for (index in shadows.indices) {
            shadows[index]?.let { despawn(it.npc) }
            shadows[index] = null
        }
        trailTicks = 0
        detonationTicks = 0
        val max = boss.baseHitpointsLvl
        later(3) {
            despawn(boss)
            akkha = null
            val real = env.random.of(4)
            val share = max * FINAL_SHARE_PERCENT / 100
            for ((index, tile) in SHADOW_TILES.withIndex()) {
                val npc =
                    spawn(
                        if (index == real) akkha_npcs.enrage else akkha_npcs.shadow_enrage,
                        tile,
                        scale = false,
                        points = 1.0,
                        hitpoints = share,
                    )
                npc.anim(akkha_seqs.appear)
                finals += Final(npc, index == real)
                if (index == real) {
                    finalReal = npc
                    bossBar = npc
                }
            }
            unstableTicks = UNSTABLE_INTERVAL
        }
    }

    private fun checkFinals() {
        val real = finals.firstOrNull { it.real } ?: return
        if (isCompleted) {
            return
        }
        // The fakes wear the shadow model, and are tinted as immune once they have it.
        for (final in finals) {
            val fake = final.npc.visType.id == akkha_npcs.shadow_enrage.id
            showShaded(final.npc, fake, AkkhaTint.shadow)
        }
        if (real.npc in downed) {
            realShadowDied(real)
            return
        }
        // Enough hits on the real one and it swaps with one of the shadows: the real model shows
        // on the new real Akkha, and the old one is shaded and immune.
        if (real.hits >= 2 + teamSize) {
            real.hits = 0
            val swap = finals.filter { it !== real }.let { it[env.random.of(it.size)] }
            swap.npc.hitpoints = real.npc.hitpoints
            real.real = false
            swap.real = true
            finalReal = swap.npc
            real.npc.anim(akkha_seqs.vanish)
            swap.npc.anim(akkha_seqs.vanish)
            later(1) {
                real.npc.transmog(env.npcTypes[akkha_npcs.shadow_enrage], Int.MAX_VALUE)
                swap.npc.transmog(env.npcTypes[akkha_npcs.enrage], Int.MAX_VALUE)
                real.npc.anim(akkha_seqs.appear)
                swap.npc.anim(akkha_seqs.appear)
            }
        }
    }

    private fun realShadowDied(real: Final) {
        real.npc.anim(akkha_seqs.shadow_death)
        for (other in finals) {
            if (other !== real) {
                despawn(other.npc)
            }
        }
        complete()
        // He goes as soon as the death has played (he used to stand on for a few ticks after it).
        // After complete(), which clears whatever was scheduled before it.
        real.npc.resetMovement()
        later(ticksOf(akkha_seqs.shadow_death)) { despawn(real.npc) }
    }

    companion object {
        const val Z = 1

        val SPAWN = CoordGrid(3673, 5407, Z)
        val MEMORY_TILE = CoordGrid(3680, 5406, Z)
        val QUADRANT_BASE = CoordGrid(3681, 5408, Z)

        /** The corner each quadrant's blasts spread from: darkness, lightning, ice, fire. */
        val QUADRANT_ORIGINS =
            listOf(
                CoordGrid(3681, 5408, Z),
                CoordGrid(3681, 5407, Z),
                CoordGrid(3680, 5407, Z),
                CoordGrid(3680, 5408, Z),
            )

        val SHADOW_TILES =
            listOf(
                CoordGrid(3685, 5412, Z),
                CoordGrid(3685, 5401, Z),
                CoordGrid(3674, 5401, Z),
                CoordGrid(3674, 5412, Z),
            )

        /** Where the quadrant symbol glows in the memory blast. */
        val LIGHT_TILES =
            listOf(
                CoordGrid(3682, 5409, Z),
                CoordGrid(3682, 5406, Z),
                CoordGrid(3679, 5406, Z),
                CoordGrid(3679, 5409, Z),
            )

        /** The health shares (percent) at which the shadows appear, then are restored. */
        val SHADOW_THRESHOLDS = intArrayOf(80, 60, 40, 20)

        const val SPECIAL_MEMORY = 0
        const val SPECIAL_TRAIL = 1
        const val SPECIAL_DETONATION = 2

        const val ATTACK_TICKS = 6

        /** Akkha walks a tile every two ticks. */
        const val STEP_TICKS = 2

        /** Client cycles before a shadow's sweep starts. */
        const val SHADOW_SWEEP_LEAD = 30
        const val SHADOW_TIMER = 50
        const val TRAIL_TICKS = 22
        const val TRAIL_ACTIVE_TICKS = 20
        const val TRAIL_ORB_LIFE = 8
        const val DETONATION_TICKS = 10
        const val UNSTABLE_INTERVAL = 3
        const val UNSTABLE_ORBS = 6
        const val FINAL_SHARE_PERCENT = 20
        const val ICE_TICKS = 24
        const val LIGHTNING_LOCKOUT = 5
        const val WANDER_TICKS = 6
        const val WANDER_RANGE = 5

        const val MAX_STYLE = 22
        const val MAX_QUADRANT = 14
        const val MAX_TRAIL = 13
        const val MAX_DETONATION = 15
        const val MAX_UNSTABLE = 13

        /** The share of each style's damage that still lands through its protection prayer. */
        const val MELEE_THROUGH = 25
        const val RANGED_THROUGH = 15
        const val MAGIC_THROUGH = 15

        const val DARKNESS_DAMAGE = 5
        /** The burn hits for 5 (wiki). */
        const val FIRE_DAMAGE = 5
        const val DARKNESS_DRAIN = 3
        val DARKNESS_DRAINS =
            listOf(stats.attack, stats.strength, stats.defence, stats.ranged, stats.magic)

        const val STUCK_TICKS = 100
        const val MIN_STYLE_ATTACKS = 2
        const val VIGILANT_ATTACK_ROLL = 4
        const val VIGILANT_SPECIAL_ROLL = 5

        /** Unstable orbs never hit for more than 25; Protect from Magic takes a quarter first. */
        const val UNSTABLE_CAP = 25
        const val UNSTABLE_PRAYER_PERCENT = 75

        /** Ticks before an element can be applied to the same player again, by quadrant. */
        val EFFECT_COOLDOWNS = intArrayOf(4, 6, 25, 30)
    }
}
