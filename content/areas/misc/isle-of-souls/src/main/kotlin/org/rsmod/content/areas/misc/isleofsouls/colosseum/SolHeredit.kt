package org.rsmod.content.areas.misc.isleofsouls.colosseum

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.sign
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.cinematic.Cinematic
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.worn.WornUnequipOp
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.game.type.spot.SpotanimType
import org.rsmod.map.CoordGrid

/** Sol Heredit's attacks. */
enum class SolAttack {
    Spear,
    Shield,
    Triple,
    Grapple,
}

/** A light crystal: the edge it patrols, which end it walks to, and when it is busy until. */
class SolCrystal(val npc: Npc, val edge: SolCrystalEdge, var end: Int = 0, var busyUntil: Int)

/** Each crystal's edge, in the order they come (RSPS): where it starts, its ends and its beam. */
class SolCrystalEdge(
    val spawn: CoordGrid,
    val ends: List<CoordGrid>,
    val dx: Int,
    val dz: Int,
    val charge: () -> SpotanimType,
    val beam: () -> SpotanimType,
)

/** A grapple in progress: the slot called, who it is aimed at, and the click that answered it. */
class SolGrapple(val slot: Wearpos, val part: String, val target: Player, val startedAt: Int) {
    var clickedSlot: Int? = null
    var clickedAt = -1
}

/** Sol Heredit's fight in a run's wave 12. */
class SolFight(val npc: Npc) {
    var phase = 0

    /** He opens the fight, and every phase, with a Spear (wiki). */
    var forced: SolAttack? = SolAttack.Spear
    var last: SolAttack? = null
    var second = false
    var specialCooldown = 0
    var nextAttackAt = 0
    var target: Player? = null

    val crystals = ArrayList<SolCrystal>()
    var laserIn = SolHeredit.LASER_MIN
    var enragedSandIn = SolHeredit.ENRAGED_SAND_EVERY
    var grapple: SolGrapple? = null

    /** He died: the run is won once his death animation has played, at [deathAt]. */
    var dead = false
    var deathAt = 0
}

/**
 * Wave 12: Sol Heredit (wiki: "Sol Heredit", "Fortis Colosseum/Strategies"; RSPS facts via tsps,
 * marked).
 * - Spear and Shield are AoE attacks of up to 44 typeless damage landing 2 ticks after the swing,
 *   which need him beside his target; he cannot move for 4 ticks from one. A second Spear or Shield
 *   in a row uses the second pattern; another attack or a special resets it. Spears are 7-tick and
 *   Shields 6-tick attacks, a tick faster from 75%.
 * - The triple attack (below 90%) hits 3, 6 and 9 ticks after it starts (the last at 10 from 50%)
 *   for 15/25/35 (15/30/45 from 50%) unless Protect from Melee is on when it lands. Turned on in
 *   the 2 ticks before a hit, it is switched off and the hit lands.
 * - The grapple (below 75%): he calls a slot, and clicking the worn item there within 4 ticks
 *   defends it; on the last tick it is a perfect parry, a max hit within 5 ticks. Otherwise up to
 *   45 damage (20 at least, RSPS) and the item is torn off into the inventory.
 * - Phases at 1350, 1125, 750, 375 and 150 HP: 6 beams of light in the 9x9 round each player (one
 *   on their tile) leave molten sand 2 ticks later, and a light crystal joins (four at most). At
 *   150 he is enraged: 5 beams, then one every 3 ticks, and the crystals fire every 12.
 * - The crystals patrol an edge each and fire together every 25-35 ticks (RSPS), 3 ticks after
 *   charging (2 enraged), for 60-75 to a player in the beam.
 *
 * In a duo he fights one player at a time, the nearest; the triple and the grapple go to his
 * target, the AoEs hit anyone standing in them, and the beams of light fall round every player.
 */
class SolHeredit
@Inject
constructor(
    private val world: ColosseumWorld,
    private val hazards: ColosseumHazards,
    private val effects: ColosseumEffects,
    private val unequip: WornUnequipOp,
    private val objTypes: ObjTypeList,
    private val interactions: AiPlayerInteractions,
    private val eventBus: EventBus,
) {
    private val edges: List<SolCrystalEdge> by lazy {
        val sw = ColosseumArena.solFloorSouthWest
        val ne = ColosseumArena.solFloorNorthEast
        val s = colosseum_spotanims
        listOf(
            SolCrystalEdge(
                CoordGrid(sw.x + 1, ne.z, 0),
                listOf(CoordGrid(ne.x - 1, ne.z, 0), CoordGrid(sw.x + 1, ne.z, 0)),
                0,
                -1,
                { s.crystal_charge_1 },
                { s.crystal_beam_1 },
            ),
            SolCrystalEdge(
                CoordGrid(ne.x, sw.z + 2, 0),
                listOf(CoordGrid(ne.x, ne.z - 1, 0), CoordGrid(ne.x, sw.z + 1, 0)),
                -1,
                0,
                { s.crystal_charge_2 },
                { s.crystal_beam_2 },
            ),
            SolCrystalEdge(
                CoordGrid(sw.x + 1, sw.z, 0),
                listOf(CoordGrid(ne.x - 1, sw.z, 0), CoordGrid(sw.x + 1, sw.z, 0)),
                0,
                1,
                { s.crystal_charge_3 },
                { s.crystal_beam_3 },
            ),
            SolCrystalEdge(
                CoordGrid(sw.x, sw.z + 1, 0),
                listOf(CoordGrid(sw.x, ne.z - 1, 0), CoordGrid(sw.x, sw.z + 1, 0)),
                1,
                0,
                { s.crystal_charge_4 },
                { s.crystal_beam_4 },
            ),
        )
    }

    // The start ---------------------------------------------------------------------------------

    /** His greeting for [player]'s attempt (wiki transcript), by how often they failed him. */
    fun greeting(player: Player, beaten: Boolean): List<String> {
        if (beaten) {
            return emptyList()
        }
        return when (player.vars[colosseum_varbits.sol_failures]) {
            0 ->
                listOf(
                    "By Ralos, we finally have a worthy challenger.",
                    "You've torn through multiple waves of combatants and you're still standing. " +
                        "I'm almost impressed.",
                    "Now let's see how you handle a real foe...",
                )
            1 ->
                listOf(
                    "You're back so soon? A lesser combatant would've retired in shame after the " +
                        "show you put on last time...",
                    "But you're clearly different. Let's see what you have this time...",
                )
            2 ->
                listOf(
                    "What is it they say? Third time's the charm? Let's see if that's true.",
                    "To arms!",
                )
            else ->
                listOf(
                    "You might be an incapable fighter, but at least you're persistent. Shall we?"
                )
        }
    }

    // The cutscene -----------------------------------------------------------------------------

    /**
     * The cutscene's first half: he leaps from his seat (3 ticks) while each fighter's camera
     * watches it, and their screens fade to black as he goes. [land] comes [JUMP_TICKS] later,
     * behind the black. The players cannot act or click anything until it ends. The wiki only says
     * a cutscene plays: the camera and the fade here are ours.
     */
    fun jump(run: ColosseumRun): Int {
        run.seatedSol?.anim(colosseum_seqs.sol_jump)
        val seat = ColosseumArena.solSeat.translate(SEAT_MIDDLE, SEAT_MIDDLE)
        for (member in run.fighters()) {
            val player = member.player
            player.mes("<col=ef1020>Sol Heredit jumps down from his seat...</col>")
            member.inCutscene = true
            player.abortRoute()
            player.delay = world.now + JUMP_TICKS + LAND_TICKS
            Cinematic.setHideEntityOps(player, true)
            val eye = run.inst(seat.translate(0, -JUMP_CAMERA_BACK))
            Camera.camMoveTo(player, eye, JUMP_CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
            Camera.camLookAt(player, run.inst(seat), JUMP_LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
        }
        world.later(run, FADE_OUT_AT) {
            for (member in run.fighters().filter { it.inCutscene }) {
                Cinematic.fadeOverlay(
                    member.player,
                    0,
                    FADE_CLEAR,
                    0,
                    FADE_BLACK,
                    FADE_CYCLES,
                    eventBus,
                )
            }
        }
        return JUMP_TICKS
    }

    /** The cutscene is over for [member]: their own camera, clicks and screen come back. */
    fun endCutscene(member: ColosseumMember) {
        if (!member.inCutscene) {
            return
        }
        member.inCutscene = false
        val player = member.player
        Camera.camReset(player)
        Cinematic.setHideEntityOps(player, false)
        Cinematic.closeFadeOverlay(player, eventBus)
    }

    /**
     * The cutscene's second half, while the screen is black: the seat goes, the gladiators close
     * off the middle, the players are put in front of him and he lands (RSPS tiles). The screen
     * fades back in on the landing, and the players get their camera back once it has played.
     */
    fun land(run: ColosseumRun) {
        world.remove(run.seatedSol)
        run.seatedSol = null
        world.removeLoc(run.seatBlocker)
        run.seatBlocker = null
        buildBarricade(run)
        for ((index, member) in run.fighters().withIndex()) {
            val spot = ColosseumArena.solPlayerStart.translate(index, 0)
            PathingEntityCommon.telejump(member.player, world.collision, run.inst(spot))
        }
        val npc = world.spawn(run, colosseum_npcs.sol, ColosseumArena.solLanding)
        npc.anim(colosseum_seqs.sol_land)
        npc.spotanim(colosseum_spotanims.sol_land)
        npc.freeze(LAND_FREEZE)
        val fight = SolFight(npc)
        fight.nextAttackAt = world.now + LAND_FREEZE
        run.sol = fight
        run.monsters += npc
        run.states[npc] = ColosseumMonsterState(ColosseumMonster.Sol, world.now)
        npc.say("Let's start by testing your footwork.")
        val middle = ColosseumArena.solLanding.translate(SOL_MIDDLE, SOL_MIDDLE)
        for (member in run.fighters().filter { it.inCutscene }) {
            val player = member.player
            val eye = run.inst(middle.translate(0, -LAND_CAMERA_BACK))
            Camera.camMoveTo(player, eye, LAND_CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
            Camera.camLookAt(player, run.inst(middle), LAND_LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE2)
            Cinematic.fadeOverlay(player, 0, FADE_BLACK, 0, FADE_CLEAR, FADE_CYCLES, eventBus)
        }
        world.later(run, LAND_TICKS) {
            for (member in run.members.values) {
                endCutscene(member)
            }
        }
    }

    private fun buildBarricade(run: ColosseumRun) {
        val sw = ColosseumArena.barricadeSouthWest
        val ne = ColosseumArena.barricadeNorthEast
        val shields = colosseum_locs.shields
        fun place(x: Int, z: Int, angle: Int) {
            val type = shields[world.roll(shields.size)]
            run.barricade += world.addLoc(run, type, CoordGrid(x, z, 0), angle)
        }
        // Each faces in (tsps' rotations): the south row 2, the north row 0, east 1, west 3.
        for (x in sw.x + 2..ne.x - 2) {
            place(x, sw.z, 2)
            place(x, ne.z, 0)
        }
        for (z in sw.z + 2..ne.z - 2) {
            place(ne.x, z, 1)
            place(sw.x, z, 3)
        }
    }

    fun clear(run: ColosseumRun) {
        for (loc in run.barricade) {
            world.removeLoc(loc)
        }
        run.barricade.clear()
        run.sol?.let { fight ->
            for (crystal in fight.crystals) {
                world.remove(crystal.npc)
            }
            fight.crystals.clear()
            fight.grapple = null
        }
    }

    // Every tick --------------------------------------------------------------------------------

    fun tick(run: ColosseumRun) {
        val fight = run.sol ?: return
        val npc = fight.npc
        if (fight.dead || !npc.isSlotAssigned || npc.hitpoints <= 0) {
            return
        }
        while (fight.phase + 1 < PHASES.size && npc.hitpoints <= PHASES[fight.phase + 1].first) {
            fight.phase++
            npc.say(PHASES[fight.phase].second)
            transition(run, fight)
        }
        if (fight.phase == ENRAGED) {
            if (--fight.enragedSandIn <= 0) {
                fight.enragedSandIn = ENRAGED_SAND_EVERY
                for (member in run.fighters()) {
                    beams(run, member.player, 1, includeOwnTile = false)
                }
            }
        }
        for (crystal in fight.crystals) {
            patrol(run, crystal)
        }
        if (fight.crystals.isNotEmpty() && --fight.laserIn <= 0) {
            for (crystal in fight.crystals) {
                fire(run, fight, crystal)
            }
            fight.laserIn =
                if (fight.phase == ENRAGED) LASER_ENRAGED else world.between(LASER_MIN, LASER_MAX)
        }
        resolveGrapple(run, fight)
        aim(run, fight)
    }

    /** He goes for the nearest player, keeping his target while it is still in the fight. */
    private fun aim(run: ColosseumRun, fight: SolFight) {
        val fighters = run.fighters().map { it.player }
        if (fighters.isEmpty()) {
            return
        }
        val current = fight.target?.takeIf { it in fighters }
        val target = current ?: fighters.minBy { it.distanceTo(fight.npc) }
        fight.target = target
        run.states[fight.npc]?.target = target
        val npc = fight.npc
        npc.spawnCoords = target.coords
        if ((npc.interaction as? InteractionPlayerOp)?.target !== target) {
            npc.opPlayer2(target, interactions)
        }
    }

    /** A phase change: sand round every player, a new crystal, and a Spear to open with. */
    private fun transition(run: ColosseumRun, fight: SolFight) {
        val enraged = fight.phase == ENRAGED
        for (member in run.fighters()) {
            beams(run, member.player, if (enraged) ENRAGED_SAND else PHASE_SAND, true)
        }
        if (!enraged && fight.crystals.size < edges.size) {
            spawnCrystal(run, fight)
        }
        if (enraged) {
            fight.laserIn = LASER_ENRAGED
        }
        fight.npc.freeze(TRANSITION_FREEZE)
        fight.nextAttackAt = world.now + TRANSITION_SPEED
        fight.forced = SolAttack.Spear
    }

    /**
     * [count] beams of light in the 9x9 round [player], one on their own tile when
     * [includeOwnTile]; each leaves molten sand for the rest of the fight 2 ticks later.
     */
    private fun beams(run: ColosseumRun, player: Player, count: Int, includeOwnTile: Boolean) {
        val at = run.real(player.coords)
        val tiles = ArrayList<CoordGrid>()
        if (includeOwnTile && ColosseumArena.inSolFloor(at)) {
            tiles += at
        }
        var tries = 0
        while (tiles.size < count && tries++ < count * TRIES_PER_TILE) {
            val sw = ColosseumArena.solFloorSouthWest
            val ne = ColosseumArena.solFloorNorthEast
            val x = (at.x - 4 + world.roll(9)).coerceIn(sw.x, ne.x)
            val z = (at.z - 4 + world.roll(9)).coerceIn(sw.z, ne.z)
            val tile = CoordGrid(x, z, 0)
            if (tile !in tiles && !hazards.hasSand(run, tile)) {
                tiles += tile
            }
        }
        for (tile in tiles) {
            world.graphic(run, colosseum_spotanims.sunfire_beam, tile)
        }
        world.later(run, SAND_LANDS) {
            for (tile in tiles) {
                hazards.addSand(run, tile, permanent = true, solSand = true)
            }
        }
    }

    private fun spawnCrystal(run: ColosseumRun, fight: SolFight) {
        val edge = edges[fight.crystals.size]
        val npc = world.spawn(run, colosseum_npcs.crystal, edge.spawn)
        npc.anim(colosseum_seqs.crystal_spawn)
        fight.crystals += SolCrystal(npc, edge, busyUntil = world.now + CRYSTAL_SPAWN_LOCK)
    }

    private fun patrol(run: ColosseumRun, crystal: SolCrystal) {
        if (world.now < crystal.busyUntil || world.now % CRYSTAL_STEP_TICKS != 0) {
            return
        }
        val here = run.real(crystal.npc.coords)
        var target = crystal.edge.ends[crystal.end]
        if (here.x == target.x && here.z == target.z) {
            crystal.end = (crystal.end + 1) % crystal.edge.ends.size
            target = crystal.edge.ends[crystal.end]
        }
        val step =
            CoordGrid(here.x + (target.x - here.x).sign, here.z + (target.z - here.z).sign, 0)
        crystal.npc.walk(run.inst(step))
    }

    /** A beam across the floor from the crystal's edge: charged now, fired a few ticks later. */
    private fun fire(run: ColosseumRun, fight: SolFight, crystal: SolCrystal) {
        val here = run.real(crystal.npc.coords)
        val path = ArrayList<CoordGrid>()
        for (n in 1..LASER_LENGTH) {
            val tile = CoordGrid(here.x + crystal.edge.dx * n, here.z + crystal.edge.dz * n, 0)
            if (!ColosseumArena.inSolFloor(tile)) {
                break
            }
            path += tile
        }
        crystal.npc.anim(colosseum_seqs.crystal_charge)
        for ((index, tile) in path.withIndex()) {
            world.graphic(run, crystal.edge.charge(), tile, height = BEAM_HEIGHT, delay = index * 2)
        }
        val charge = if (fight.phase == ENRAGED) LASER_CHARGE_ENRAGED else LASER_CHARGE
        crystal.busyUntil = world.now + charge + 2
        world.later(run, charge) {
            if (!crystal.npc.isSlotAssigned || fight.dead) {
                return@later
            }
            crystal.npc.anim(colosseum_seqs.crystal_attack)
            for ((index, tile) in path.withIndex()) {
                world.graphic(
                    run,
                    crystal.edge.beam(),
                    tile,
                    height = BEAM_HEIGHT,
                    delay = index * 2,
                )
            }
            path.lastOrNull()?.let {
                world.graphic(run, colosseum_spotanims.crystal_impact, it, delay = path.size * 2)
            }
            for (member in run.fighters()) {
                if (run.real(member.player.coords) in path) {
                    world.hurt(
                        member.player,
                        world.between(LASER_MIN_HIT, LASER_MAX_HIT),
                        fight.npc,
                    )
                }
            }
        }
    }

    // His attacks -------------------------------------------------------------------------------

    /**
     * He is next to [target] and may attack: called every tick while he is (the engine's chase got
     * him there). Returns without attacking while his last one is still going.
     */
    fun attack(run: ColosseumRun, target: Player) {
        val fight = run.sol ?: return
        if (fight.dead || world.now < fight.nextAttackAt || fight.grapple != null) {
            return
        }
        val choice = fight.forced ?: next(fight)
        fight.forced = null
        val speed =
            when (choice) {
                SolAttack.Spear,
                SolAttack.Shield -> {
                    fight.specialCooldown--
                    aoe(run, fight, choice, target)
                }
                SolAttack.Triple -> {
                    fight.specialCooldown = SPECIAL_COOLDOWN
                    fight.second = false
                    triple(run, fight, target)
                }
                SolAttack.Grapple -> {
                    fight.specialCooldown = SPECIAL_COOLDOWN
                    fight.second = false
                    grapple(run, fight, target)
                }
            }
        fight.nextAttackAt = world.now + speed
    }

    /** Two Spears and two Shields, and the specials his phase allows once off cooldown (RSPS). */
    private fun next(fight: SolFight): SolAttack {
        val pool =
            mutableListOf(SolAttack.Spear, SolAttack.Spear, SolAttack.Shield, SolAttack.Shield)
        if (fight.specialCooldown <= 0) {
            if (fight.phase >= 1) pool += SolAttack.Triple
            if (fight.phase >= 2) pool += SolAttack.Grapple
        }
        return pool[world.roll(pool.size)]
    }

    private fun aoe(run: ColosseumRun, fight: SolFight, style: SolAttack, target: Player): Int {
        val second = fight.last == style && !fight.second
        fight.second = second
        fight.last = style
        val npc = fight.npc
        npc.anim(
            if (style == SolAttack.Spear) colosseum_seqs.sol_spear else colosseum_seqs.sol_shield
        )
        npc.freeze(AOE_FREEZE)
        val sol = run.real(npc.coords)
        val aim = run.real(target.coords)
        val tiles =
            if (style == SolAttack.Spear) SolPatterns.spear(sol, aim, second)
            else SolPatterns.shield(sol, second)
        world.later(run, AOE_LANDS) {
            if (fight.dead || !npc.isSlotAssigned) {
                return@later
            }
            val smokes = colosseum_spotanims.sol_smokes
            for (tile in tiles) {
                world.graphic(
                    run,
                    smokes[world.roll(smokes.size)],
                    tile.tile,
                    delay = tile.ring * 2,
                )
            }
            val hit = tiles.map { it.tile }.toSet()
            for (member in run.fighters()) {
                if (run.real(member.player.coords) in hit) {
                    val damage = world.between(1, AOE_MAX + effects.maxHitBonus(run))
                    world.hurt(member.player, damage, npc)
                }
            }
        }
        val faster = if (fight.phase >= 2) 1 else 0
        return (if (style == SolAttack.Spear) SPEAR_SPEED else SHIELD_SPEED) - faster
    }

    /** Three blows, each blocked only by Protect from Melee switched on as it lands. */
    private fun triple(run: ColosseumRun, fight: SolFight, target: Player): Int {
        val late = fight.phase >= 3
        fight.npc.anim(if (late) colosseum_seqs.sol_triple else colosseum_seqs.sol_triple_short)
        fight.npc.spotanim(
            if (late) colosseum_spotanims.sol_triple else colosseum_spotanims.sol_triple_short
        )
        val damage = if (late) TRIPLE_LATE else TRIPLE_EARLY
        val hits = if (late) TRIPLE_HITS_LATE else TRIPLE_HITS_EARLY
        for ((index, at) in hits.withIndex()) {
            var early = false
            for (before in listOf(at - 2, at - 1)) {
                world.later(run, before) {
                    if (fight.dead || !praying(target)) {
                        return@later
                    }
                    early = true
                    VarPlayerIntMapSetter.set(target, varbits.protect_from_melee, 0)
                    target.overheadIcon = null
                    target.mes("<col=ef1020>Sol Heredit doesn't take kindly to your eager prayer.")
                }
            }
            world.later(run, at) {
                if (fight.dead || !fight.npc.isSlotAssigned) {
                    return@later
                }
                if (early || !praying(target)) {
                    world.hurt(target, damage[index] + effects.maxHitBonus(run), fight.npc)
                }
            }
        }
        fight.last = null
        val faster = if (!late && fight.phase >= 2) 1 else 0
        return TRIPLE_SPEED - faster
    }

    private fun praying(player: Player): Boolean = player.vars[varbits.protect_from_melee] == 1

    private fun grapple(run: ColosseumRun, fight: SolFight, target: Player): Int {
        val (slot, part, call) = GRAPPLES[world.roll(GRAPPLES.size)]
        fight.grapple = SolGrapple(slot, part, target, world.now)
        fight.npc.say(call)
        fight.npc.anim(colosseum_seqs.sol_grapple)
        fight.npc.freeze(GRAPPLE_FREEZE)
        fight.last = null
        return GRAPPLE_SPEED
    }

    /**
     * A worn slot clicked while he grapples [player]: the item stays on, and the click is their
     * answer. Returns whether the click was taken.
     */
    fun defend(run: ColosseumRun, player: Player, wornSlot: Int): Boolean {
        val grip = run.sol?.grapple ?: return false
        if (grip.target !== player) {
            return false
        }
        if (grip.clickedSlot == null) {
            grip.clickedSlot = wornSlot
            grip.clickedAt = world.now
            if (wornSlot != grip.slot.slot) {
                player.mes("<col=ef1020>You defended the wrong body part!")
            }
        }
        return true
    }

    private fun resolveGrapple(run: ColosseumRun, fight: SolFight) {
        val grip = fight.grapple ?: return
        if (world.now <= grip.startedAt + GRAPPLE_WINDOW) {
            return
        }
        fight.grapple = null
        val player = grip.target
        val member = run.memberOf(player)
        if (member == null || member.dead || !run.instance.contains(player.coords)) {
            return
        }
        if (grip.clickedSlot == grip.slot.slot) {
            if (grip.clickedAt >= grip.startedAt + GRAPPLE_WINDOW) {
                player.mes("<col=14b414>You perfectly parry Sol Heredit's grapple!")
                member.maxHitUntil = world.now + PARRY_TICKS
            } else {
                player.mes(
                    "<col=14b414>You successfully defend your ${grip.part} from Sol Heredit's " +
                        "grapple!"
                )
            }
            return
        }
        val worn = player.worn[grip.slot.slot]
        val name = worn?.let { objTypes[it].name } ?: grip.part
        world.hurt(
            player,
            world.between(GRAPPLE_MIN, GRAPPLE_MAX) + effects.maxHitBonus(run),
            fight.npc,
        )
        player.mes(
            "Sol Heredit grabs hold of your <col=00ffff>$name</col> and deals massive damage!"
        )
        if (worn != null) {
            unequip.unequip(player, grip.slot.slot, player.worn, player.inv)
        }
    }

    /** Whether [player]'s attack on [target] is Sol Heredit's perfect parry's max hit. */
    fun parried(run: ColosseumRun, player: Player, target: Npc): Boolean {
        val fight = run.sol ?: return false
        if (target !== fight.npc) {
            return false
        }
        val until = run.memberOf(player)?.maxHitUntil ?: return false
        return until >= world.now
    }

    // His end -----------------------------------------------------------------------------------

    /** He is beaten: his last words and his death graphic, and his crystals break up. */
    fun died(run: ColosseumRun, fight: SolFight) {
        fight.dead = true
        fight.grapple = null
        fight.npc.say(DEATH_LINES[world.roll(DEATH_LINES.size)])
        fight.npc.spotanim(colosseum_spotanims.sol_death)
        val crystals = fight.crystals.toList()
        fight.crystals.clear()
        for (crystal in crystals) {
            crystal.npc.anim(colosseum_seqs.crystal_despawn)
        }
        world.later(run, CRYSTAL_DESPAWN_TICKS) {
            for (crystal in crystals) {
                world.remove(crystal.npc)
            }
        }
    }

    fun playerDied(run: ColosseumRun) {
        val fight = run.sol ?: return
        if (!fight.dead && fight.npc.isSlotAssigned) {
            fight.npc.say(KILL_LINES[world.roll(KILL_LINES.size)])
        }
    }

    companion object {
        /** The HP each phase begins at, and what he says (wiki transcript). */
        val PHASES =
            listOf(
                1500 to "Let's start by testing your footwork.",
                1350 to "Not bad. Let's try something else...",
                1125 to "Impressive. Let's see how you handle this...",
                750 to "You can't win!",
                375 to "Ralos guides my hand!",
                150 to "LET'S END THIS!",
            )
        const val ENRAGED = 5

        const val AOE_MAX = 44
        const val AOE_LANDS = 2
        const val AOE_FREEZE = 4
        const val SPEAR_SPEED = 7
        const val SHIELD_SPEED = 6
        const val TRIPLE_SPEED = 12
        const val GRAPPLE_SPEED = 7
        const val TRANSITION_SPEED = 7
        const val TRANSITION_FREEZE = 5
        const val LAND_FREEZE = 3

        /**
         * The cutscene: his jump plays for 3 ticks, the screen goes black over its last 2, and his
         * landing (3 ticks) plays as it fades back in. Camera heights are the client's units.
         */
        const val JUMP_TICKS = 3
        const val LAND_TICKS = 3
        const val FADE_OUT_AT = 1
        const val FADE_CYCLES = 50
        const val FADE_CLEAR = 255
        const val FADE_BLACK = 0
        const val SEAT_MIDDLE = 2
        const val SOL_MIDDLE = 2
        const val JUMP_CAMERA_BACK = 12
        const val JUMP_CAMERA_HEIGHT = 900
        const val JUMP_LOOK_HEIGHT = 500
        const val LAND_CAMERA_BACK = 9
        const val LAND_CAMERA_HEIGHT = 700
        const val LAND_LOOK_HEIGHT = 150
        const val CAMERA_RATE = 100
        const val CAMERA_RATE2 = 100

        /** The crystals' break-up animation is 2 ticks long. */
        const val CRYSTAL_DESPAWN_TICKS = 2
        const val SPECIAL_COOLDOWN = 2

        val TRIPLE_EARLY = listOf(15, 25, 35)
        val TRIPLE_LATE = listOf(15, 30, 45)
        val TRIPLE_HITS_EARLY = listOf(3, 6, 9)
        val TRIPLE_HITS_LATE = listOf(3, 6, 10)

        const val GRAPPLE_WINDOW = 4
        const val GRAPPLE_FREEZE = 5
        const val GRAPPLE_MIN = 20
        const val GRAPPLE_MAX = 45
        const val PARRY_TICKS = 5

        const val PHASE_SAND = 6
        const val ENRAGED_SAND = 5
        const val ENRAGED_SAND_EVERY = 3
        const val SAND_LANDS = 2
        private const val TRIES_PER_TILE = 20

        const val LASER_MIN = 25
        const val LASER_MAX = 35
        const val LASER_ENRAGED = 12
        const val LASER_CHARGE = 3
        const val LASER_CHARGE_ENRAGED = 2
        const val LASER_LENGTH = 14
        const val LASER_MIN_HIT = 60
        const val LASER_MAX_HIT = 75
        const val CRYSTAL_STEP_TICKS = 2
        const val CRYSTAL_SPAWN_LOCK = 4
        private const val BEAM_HEIGHT = 128

        /** The slots he grapples, the part the messages name, and what he calls out. */
        val GRAPPLES =
            listOf(
                Triple(Wearpos.Torso, "body", "I'LL CRUSH YOUR BODY!"),
                Triple(Wearpos.Back, "back", "I'LL BREAK YOUR BACK!"),
                Triple(Wearpos.Hands, "hands", "I'LL TWIST YOUR HANDS OFF!"),
                Triple(Wearpos.Legs, "legs", "I'LL BREAK YOUR LEGS!"),
                Triple(Wearpos.Feet, "feet", "I'LL CUT YOUR FEET OFF!"),
            )

        val DEATH_LINES =
            listOf(
                "Your light shines... brightly...",
                "A worthy... opponent...",
                "Ralos has smiled upon you...",
                "My compliments, champion...",
            )

        val KILL_LINES =
            listOf(
                "How disappointing...",
                "I knew you weren't the one.",
                "You had me excited for a moment.",
                "Your lack of coordination is concerning.",
                "Your light shines no more.",
                "Maybe next time...",
                "Pathetic, really...",
                "I was just getting into my rhythm...",
            )
    }
}

/**
 * The tiles Sol Heredit's attacks cover, inside the barricade (wiki shapes; where each line starts
 * for each direction, and Spear 2's 7x7 at a diagonal, are RSPS). Tiles are the real arena's.
 */
object SolPatterns {
    private const val SIZE = 5
    private const val LINE = 4
    private const val SHIELD_REACH = 7

    private class SpearShape(
        val strip: IntArray?,
        val first: List<Pair<Int, Int>>,
        val second: List<Pair<Int, Int>>,
    )

    private val DIRECTIONS =
        mapOf(
            "N" to (0 to 1),
            "NE" to (1 to 1),
            "E" to (1 to 0),
            "SE" to (1 to -1),
            "S" to (0 to -1),
            "SW" to (-1 to -1),
            "W" to (-1 to 0),
            "NW" to (-1 to 1),
        )

    /** From his south-west tile, per direction: the row in front and where each line starts. */
    private val SPEAR =
        mapOf(
            "W" to
                SpearShape(
                    intArrayOf(-1, 0, 0, 5),
                    listOf(-2 to 1, -2 to 3),
                    listOf(-2 to 0, -2 to 2, -2 to 4),
                ),
            "E" to
                SpearShape(
                    intArrayOf(5, 0, 6, 5),
                    listOf(6 to 1, 6 to 3),
                    listOf(6 to 0, 6 to 2, 6 to 4),
                ),
            "N" to
                SpearShape(
                    intArrayOf(0, 5, 5, 6),
                    listOf(1 to 6, 3 to 6),
                    listOf(0 to 6, 2 to 6, 4 to 6),
                ),
            "S" to
                SpearShape(
                    intArrayOf(0, -1, 5, 0),
                    listOf(1 to -2, 3 to -2),
                    listOf(0 to -2, 2 to -2, 4 to -2),
                ),
            "NE" to SpearShape(null, listOf(4 to 5, 5 to 4), listOf(6 to 6, 3 to 6, 6 to 3)),
            "SE" to SpearShape(null, listOf(5 to 0, 4 to -1), listOf(6 to 1, 6 to -2, 3 to -2)),
            "SW" to SpearShape(null, listOf(-1 to 0, 0 to -1), listOf(-2 to -2, -2 to 1, 1 to -2)),
            "NW" to SpearShape(null, listOf(-1 to 4, 0 to 5), listOf(-2 to 3, -2 to 6, 1 to 6)),
        )

    /** Which way [player] is from his nearest tile. */
    fun directionTo(sol: CoordGrid, player: CoordGrid): String {
        val nearestX = player.x.coerceIn(sol.x, sol.x + SIZE - 1)
        val nearestZ = player.z.coerceIn(sol.z, sol.z + SIZE - 1)
        val dx = (player.x - nearestX).sign
        val dz = (player.z - nearestZ).sign
        return DIRECTIONS.entries.firstOrNull { it.value == (dx to dz) }?.key ?: "NW"
    }

    private fun square(cx: Int, cz: Int, radius: Int, skipRing: Int = -1): List<TimedTile> {
        val tiles = ArrayList<TimedTile>()
        for (dx in -radius..radius) {
            for (dz in -radius..radius) {
                val ring = maxOf(abs(dx), abs(dz))
                if (ring == skipRing) {
                    continue
                }
                val tile = CoordGrid(cx + dx, cz + dz, 0)
                if (ColosseumArena.inSolFloor(tile)) {
                    tiles += TimedTile(tile, ring)
                }
            }
        }
        return tiles
    }

    private fun line(x: Int, z: Int, dx: Int, dz: Int, ring: Int): List<TimedTile> {
        val tiles = ArrayList<TimedTile>()
        for (n in 0 until LINE) {
            val tile = CoordGrid(x + dx * n, z + dz * n, 0)
            if (!ColosseumArena.inSolFloor(tile)) {
                break
            }
            tiles += TimedTile(tile, ring + n)
        }
        return tiles
    }

    /** Spear 1 or 2, from his south-west tile [sol], towards [player]. */
    fun spear(sol: CoordGrid, player: CoordGrid, second: Boolean): List<TimedTile> {
        val key = directionTo(sol, player)
        val shape = SPEAR.getValue(key)
        val (dx, dz) = DIRECTIONS.getValue(key)
        val diagonal = key.length == 2
        val radius = if (second && diagonal) 3 else 2
        val tiles = ArrayList(square(sol.x + 2, sol.z + 2, radius))
        var ring = radius + 1
        shape.strip?.let { (fromX, fromZ, toX, toZ) ->
            for (x in sol.x + fromX until sol.x + toX) {
                for (z in sol.z + fromZ until sol.z + toZ) {
                    val tile = CoordGrid(x, z, 0)
                    if (ColosseumArena.inSolFloor(tile)) {
                        tiles += TimedTile(tile, ring)
                    }
                }
            }
            ring++
        }
        for ((ox, oz) in if (second) shape.second else shape.first) {
            tiles += line(sol.x + ox, sol.z + oz, dx, dz, ring)
        }
        return tiles.distinctBy { it.tile }
    }

    /** Shield 1 or 2: everything within 7 tiles of his middle but the safe ring (9x9 or 11x11). */
    fun shield(sol: CoordGrid, second: Boolean): List<TimedTile> =
        square(sol.x + 2, sol.z + 2, SHIELD_REACH, skipRing = if (second) 5 else 4)
}
