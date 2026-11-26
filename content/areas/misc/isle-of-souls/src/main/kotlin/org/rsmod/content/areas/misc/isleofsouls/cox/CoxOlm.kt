package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaProjAnims
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.npc.NpcType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The barrier, the Olm's room and the ancient chest's handlers. */
class CoxOlmScript
@Inject
constructor(private val rooms: CoxRoomRegistry, private val raids: CoxRaids) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.olmFactory = { env, raid -> CoxOlm(env, raid) }
        onOpLoc1(cox_locs.olm_barrier) { pass(ask = true) }
        onOpLoc2(cox_locs.olm_barrier) { pass(ask = false) }
        coxBosses(
            rooms,
            listOf(
                cox_npcs.olm_head,
                cox_npcs.olm_head_spawning,
                cox_npcs.olm_left,
                cox_npcs.olm_left_spawning,
                cox_npcs.olm_right,
                cox_npcs.olm_right_spawning,
            ),
        )
    }

    private suspend fun ProtectedAccess.pass(ask: Boolean) {
        val raid = raids.raidOf(player) ?: return
        val olm = rooms.olm(raid) as? CoxOlm ?: return
        if (raid.olmTile(CoxTiles.OLM_CENTRE).z <= coords.z) return
        if (ask) {
            var go = false
            startDialogue {
                go =
                    choice2(
                        "Yes.",
                        true,
                        "No.",
                        false,
                        title = "Pass the barrier? You won't be able to come back.",
                    )
            }
            if (!go) return
        }
        olm.pass(player)
    }
}

/**
 * The Great Olm. Wiki rules (and TSPS's rsprox notes for tiles and timings; the rest is in
 * `docs/isle_of_souls_cox_plan.md` as guesses):
 * - Passing the barrier wakes it; 7 ticks later it rises on a random side with a power (acid,
 *   flame, crystal), never the same twice running.
 * - Its head acts every 4 ticks through the rotation: standard, empty, standard, Crystal Burst,
 *   standard, empty, standard, Lightning, standard, empty, standard, Swap. A standard attack is
 *   magic or ranged (1 in 5 to switch after each; prayer lets 20% through); 1 in 3 (1 in 2 in the
 *   last phase) it is the power's phase attack instead. A skipped standard is caught up once.
 * - Claw phases: 3 + raiders / 8. Both claws down: the head sinks and rises on the other side 38
 *   ticks later with a new power. In the last phase the claws must fall within 34 ticks of each
 *   other or the first comes back; then the head's final stand, the only time it can be hurt.
 * - The head takes a third of Magic damage; the left (melee) claw a third of Magic and Ranged; the
 *   right (mage) claw a third of Ranged.
 */
class CoxOlm(env: CoxRoomEnv, raid: CoxRaid) : CoxRoomRuntime(env, raid, null) {
    override fun tile(template: CoordGrid, size: Int): CoordGrid = raid.olmTile(template)

    override fun template(coords: CoordGrid): CoordGrid = raid.olmReal(coords)

    override fun inRoom(coords: CoordGrid): Boolean {
        if (!raid.inOlmChamber(coords)) return false
        val real = raid.olmReal(coords)
        return real.x in ARENA_MIN_X..ARENA_MAX_X && real.z in ARENA_MIN_Z..ARENA_MAX_Z
    }

    private enum class Power(val label: String) {
        Acid("<col=00ff00>acid</col>"),
        Flame("<col=ff0000>flame</col>"),
        Crystal("<col=ff00ff>crystal</col>"),
    }

    private var side = 0
    private var phase = 0
    private var phases = 4
    private var power: Power? = null
    private var head: Npc? = null
    private var left: Npc? = null
    private var right: Npc? = null
    private var risen = false
    private var step = 0
    private var nextAction = 0
    private var magic = true
    private var skipped = false
    private var finalStand = false
    private var leftDownAt = -1
    private var rightDownAt = -1
    private val hazards = ArrayList<LocInfo>()
    private val acid = HashMap<CoordGrid, Int>()
    private val burning = HashMap<Player, Int>()
    private var exitCrystal: LocInfo? = null
    private var defeated = false

    override fun build() {
        exitCrystal = placeLoc(EXIT_CRYSTAL, cox_locs.crystal_green)
        side = roll(0, 1)
    }

    fun pass(player: Player) {
        val at = tile(CoordGrid(player.coords.let { raid.olmReal(it).x }, BARRIER_Z + 1, 0))
        env.raids.move(player, open(at))
        if (!started) {
            phases = 3 + raid.scaling.size / 8 + 1
            start()
            later(RISE_AFTER_BARRIER) { rise() }
        }
    }

    private fun rise() {
        if (defeated) return
        phase++
        // The last claw phase has no one power: its phase attacks are any of the three.
        val last = phase >= phases - 1
        power =
            if (last) {
                null
            } else {
                Power.entries.filter { it != power }.let(::pick)
            }
        val s = SIDES[side]
        val headHp = CoxScaling.olmHitpoints(raid.scaling.size, head = true)
        val clawHp = CoxScaling.olmHitpoints(raid.scaling.size, head = false)
        head?.let(::despawn)
        left?.let(::despawn)
        right?.let(::despawn)
        head = spawnOlm(cox_npcs.olm_head, s.head, CoxScaling.Bases.olm_head, headHp)
        left = spawnOlm(cox_npcs.olm_left, s.left, CoxScaling.Bases.olm_left_claw, clawHp)
        right = spawnOlm(cox_npcs.olm_right, s.right, CoxScaling.Bases.olm_right_claw, clawHp)
        head?.anim(if (last) cox_seqs.olm_head_spawn_enraged else cox_seqs.olm_head_spawn)
        left?.anim(cox_seqs.olm_left_spawn)
        right?.anim(cox_seqs.olm_right_spawn)
        leftDownAt = -1
        rightDownAt = -1
        risen = false
        val text =
            if (power == null) "The Great Olm rises one final time."
            else "The Great Olm rises with the power of ${power!!.label}."
        for (p in roomPlayers() + raidPlayers()) p.mes(text)
        later(RISEN_TICKS) {
            risen = true
            step = 0
            nextAction = now + 1
        }
    }

    private fun raidPlayers(): List<Player> =
        raid.active
            .map { it.player }
            .filter { raid.inOlmChamber(it.coords) && it !in roomPlayers() }

    private fun spawnOlm(type: NpcType, at: CoordGrid, base: CoxBase, hp: Int): Npc =
        spawnAt(type, tile(at), base, hitpoints = hp, ignoreCollision = true)

    override fun onTick() {
        tickHazards()
        if (!risen || defeated) return
        if (finalStand && head?.isSlotAssigned != true) return
        lastPhaseClaws()
        if (now < nextAction) return
        nextAction = now + ATTACK_TICKS
        val sequence = ROTATION
        val action = sequence[step % sequence.size]
        step++
        val players = fightPlayers()
        if (players.isEmpty()) return
        when (action) {
            "auto" -> standardOrPhase(players)
            "skip" ->
                if (skipped) {
                    skipped = false
                    standardOrPhase(players)
                }
            "burst" -> if (left.alive()) crystalBurst(players)
            "lightning" -> if (left.alive()) lightning(players)
            "swap" -> if (left.alive()) swap(players)
        }
    }

    private fun Npc?.alive(): Boolean = this != null && isSlotAssigned && hitpoints > 0

    private fun standardOrPhase(players: List<Player>) {
        if (chance(if (phase >= phases - 1) 2 else 3)) {
            phaseAttack(players)
            return
        }
        if (chance(SPHERE_CHANCE)) {
            spheres(players)
            return
        }
        val zone = bestZone(players)
        val targets =
            players
                .filter { zoneOf(it) == zone }
                .ifEmpty {
                    skipped = true
                    return
                }
        val h = head ?: return
        h.anim(
            if (finalStand) cox_seqs.olm_head_attack_front_enraged
            else cox_seqs.olm_head_attack_front
        )
        for (target in targets) {
            val style = if (magic) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
            strike(
                h,
                target,
                NpcAttack(
                    style,
                    projectile = if (magic) cox_spots.olm_firebreath else cox_spots.olm_range_proj,
                    projanim = ToaProjAnims.orb,
                    maxHit = BASE_MAX + (phase - 1).coerceIn(0, 2),
                ),
                through = PRAYER_THROUGH,
                playAnim = false,
            )
        }
        if (chance(5)) magic = !magic
    }

    private fun zoneOf(player: Player): Int {
        val z = raid.olmReal(player.coords).z
        return when {
            z < SOUTH_MIDDLE_Z -> 0
            z < NORTH_MIDDLE_Z -> 1
            else -> 2
        }
    }

    private fun bestZone(players: List<Player>): Int =
        players.groupingBy(::zoneOf).eachCount().maxByOrNull { it.value }?.key ?: 1

    // Phase attacks
    // ---------------------------------------------------------------------------------------------

    private fun phaseAttack(players: List<Player>) {
        when (power ?: pick(Power.entries)) {
            Power.Acid -> if (chance(2)) acidSpray() else acidDrip(pick(players))
            Power.Flame -> if (chance(2)) deepBurn(pick(players)) else fireWall(pick(players))
            Power.Crystal -> if (chance(2)) fallingCrystals(pick(players)) else crystalBombs()
        }
    }

    private fun arenaTile(): CoordGrid =
        tile(CoordGrid(roll(ARENA_MIN_X, ARENA_MAX_X), roll(ARENA_MIN_Z, ARENA_MAX_Z), 0))

    private fun acidSpray() {
        repeat(ACID_POOLS) {
            val at = arenaTile()
            val from = head?.coords ?: return
            val land =
                projectileBy(
                    from,
                    at,
                    cox_spots.olm_acid,
                    delay = 32,
                    base = 60,
                    perTile = 2,
                    type = ToaProjAnims.lob,
                )
            later(land) { pool(at) }
        }
    }

    private fun acidDrip(target: Player) {
        target.mes("The Great Olm has smothered you in acid. It starts to drip off slowly.")
        repeat(DRIP_TICKS) { i -> later(i) { if (target.isSlotAssigned) pool(target.coords) } }
    }

    private fun pool(at: CoordGrid) {
        if (at in acid) return
        acid[at] = now + POOL_TICKS
        hazards += placeLocAt(at, cox_locs.olm_acid_pool, org.rsmod.game.loc.LocShape.GroundDecor)
    }

    private fun deepBurn(target: Player) {
        target.mes("The Great Olm infects you with a burning overwhelming power.")
        burning[target] = BURN_HITS
    }

    private fun fireWall(target: Player) {
        val real = raid.olmReal(target.coords)
        val zs = listOf(real.z - 1, real.z + 1).map { it.coerceIn(ARENA_MIN_Z, ARENA_MAX_Z) }
        for (z in zs) {
            for (x in ARENA_MIN_X..ARENA_MAX_X) graphicAt(
                cox_spots.olm_firewall,
                tile(CoordGrid(x, z, 0)),
            )
        }
        later(FIRE_WALL_TICKS) {
            for (p in fightPlayers()) {
                val z = raid.olmReal(p.coords).z
                if (z > zs.min() && z < zs.max()) hurt(p, roll(50, 65))
            }
        }
    }

    private fun fallingCrystals(target: Player) {
        target.mes("The Great Olm has chosen you as its target - watch out!")
        repeat(FALLING_CRYSTALS) { i ->
            later(i * 2) {
                if (!target.isSlotAssigned) return@later
                val at = target.coords
                graphicAt(cox_spots.olm_falling_crystal, at)
                later(2) {
                    if (target.coords == at) {
                        target.mes("The falling crystal shatters into you.")
                        hurt(target, roll(15, 20))
                    }
                }
            }
        }
    }

    private fun crystalBombs() {
        val count = if (raid.scaling.size >= 5) 3 else 2
        repeat(count) {
            val at = arenaTile()
            val bomb = placeLocAt(at, cox_locs.olm_bomb)
            hazards += bomb
            later(BOMB_FUSE) {
                removeLoc(bomb)
                hazards -= bomb
                graphicAt(cox_spots.olm_bomb_explode, at)
                for (p in fightPlayers()) {
                    val d = max(abs(p.coords.x - at.x), abs(p.coords.z - at.z))
                    if (d <= BOMB_RADIUS) hurt(p, 15 + 15 * (BOMB_RADIUS - d))
                }
            }
        }
    }

    private fun spheres(players: List<Player>) {
        val styles = listOf(NpcAttackStyle.Melee, NpcAttackStyle.Ranged, NpcAttackStyle.Magic)
        for (p in players) {
            val style = pick(styles)
            val spot =
                when (style) {
                    NpcAttackStyle.Melee -> cox_spots.olm_melee_sphere
                    NpcAttackStyle.Ranged -> cox_spots.olm_range_sphere
                    NpcAttackStyle.Magic -> cox_spots.olm_mage_proj
                }
            val colour =
                when (style) {
                    NpcAttackStyle.Melee -> "<col=ff3045>red</col>"
                    NpcAttackStyle.Ranged -> "<col=229628>green</col>"
                    NpcAttackStyle.Magic -> "<col=3366ff>blue</col>"
                }
            p.mes("The Great Olm fires a sphere of $colour at you!")
            val from = head?.coords ?: return
            val land = projectile(from, p.coords, spot, 32..180, heights = 120 to 31)
            later(land) {
                if (p.isSlotAssigned && !prays(p, style)) {
                    hurt(p, (p.stat(stats.hitpoints) / 2).coerceAtLeast(1))
                }
            }
        }
    }

    // The left claw's specials
    // ---------------------------------------------------------------------------------------------

    private fun crystalBurst(players: List<Player>) {
        left?.anim(cox_seqs.olm_left_cast_stun)
        for (p in players) {
            val at = p.coords
            val crystal = placeLocAt(at, cox_locs.olm_crystal_small)
            hazards += crystal
            later(BURST_TICKS) {
                removeLoc(crystal)
                hazards -= crystal
                graphicAt(cox_spots.olm_crystal_explode, at)
                for (q in fightPlayers()) if (q.coords == at) hurt(q, roll(10, 40))
            }
        }
    }

    private fun lightning(players: List<Player>) {
        left?.anim(cox_seqs.olm_left_cast_shock)
        for (p in players) {
            if (!chance(2)) continue
            graphicAt(cox_spots.olm_shockwave, p.coords)
            hurt(p, roll(15, 25))
            for (prayer in
                listOf(
                    varbits.protect_from_melee,
                    varbits.protect_from_missiles,
                    varbits.protect_from_magic,
                )) {
                VarPlayerIntMapSetter.set(p, prayer, 0)
            }
            p.mes("You've been electrocuted to the spot!")
            p.delay = now + BIND_TICKS
        }
    }

    private fun swap(players: List<Player>) {
        left?.anim(cox_seqs.olm_left_cast_quake)
        val shuffled = shuffled(players)
        if (shuffled.size == 1) {
            shuffled[0].mes(
                "The Great Olm had no one to pair you with! The magical power will enact soon..."
            )
            return
        }
        for (i in 0 until shuffled.size - 1 step 2) {
            val a = shuffled[i]
            val b = shuffled[i + 1]
            graphicAt(cox_spots.olm_swap[i / 2 % 4], a.coords)
            graphicAt(cox_spots.olm_swap[i / 2 % 4], b.coords)
            later(SWAP_TICKS) {
                if (!a.isSlotAssigned || !b.isSlotAssigned) return@later
                val distance = max(abs(a.coords.x - b.coords.x), abs(a.coords.z - b.coords.z))
                if (distance == 0) {
                    a.mes("The teleport attack has no effect!")
                    return@later
                }
                val at = a.coords
                PathingEntityCommon.telejump(a, env.collision, b.coords)
                PathingEntityCommon.telejump(b, env.collision, at)
                a.mes("Yourself and ${b.displayName} have swapped places!")
                b.mes("Yourself and ${a.displayName} have swapped places!")
                hurt(a, distance * 5)
                hurt(b, distance * 5)
            }
        }
    }

    // Hazards
    // ---------------------------------------------------------------------------------------------

    private fun tickHazards() {
        if (acid.isNotEmpty()) {
            for ((tile, until) in acid.entries.toList()) {
                if (now >= until) {
                    acid.remove(tile)
                    hazards
                        .firstOrNull { it.coords == tile }
                        ?.let {
                            removeLoc(it)
                            hazards -= it
                        }
                }
            }
            for (p in fightPlayers()) {
                if (p.coords in acid) {
                    hurtPoison(p, roll(3, 6))
                    env.poison.poison(p, 4)
                }
            }
        }
        if (burning.isNotEmpty() && now % BURN_EVERY == 0) {
            for ((p, left) in burning.entries.toList()) {
                if (!p.isSlotAssigned || left <= 0) {
                    burning.remove(p)
                    continue
                }
                hurt(p, 5)
                for (stat in
                    listOf(
                        stats.attack,
                        stats.strength,
                        stats.defence,
                        stats.ranged,
                        stats.magic,
                    )) {
                    p.statSub(stat, 2, 0)
                }
                burning[p] = left - 1
                p.say("Burn with me!")
                for (q in fightPlayers()) {
                    if (
                        q !== p &&
                            q !in burning &&
                            max(abs(q.coords.x - p.coords.x), abs(q.coords.z - p.coords.z)) <= 1
                    ) {
                        burning[q] = BURN_HITS
                        q.say("I will burn with you.")
                    }
                }
            }
        }
    }

    // Damage and claws
    // ---------------------------------------------------------------------------------------------

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        when (npc) {
            head -> {
                if (!finalStand) {
                    hit.damage = 0
                    return
                }
                if (hit.type == HitType.Magic) hit.damage /= 3
            }
            left -> if (hit.type == HitType.Magic || hit.type == HitType.Ranged) hit.damage /= 3
            right -> if (hit.type == HitType.Ranged) hit.damage /= 3
        }
    }

    override fun hitpointsGone(npc: Npc) {
        when (npc) {
            left -> clawDown(isLeft = true)
            right -> clawDown(isLeft = false)
            head -> if (finalStand) die()
        }
    }

    private fun clawDown(isLeft: Boolean) {
        val claw = (if (isLeft) left else right) ?: return
        claw.anim(if (isLeft) cox_seqs.olm_left_death else cox_seqs.olm_right_death)
        claw.hitpoints = 0
        if (isLeft) leftDownAt = now else rightDownAt = now
        later(3) {
            despawn(claw)
            if (isLeft) left = null else right = null
            checkPhase()
        }
    }

    /** In the last claw phase the claws must fall close together. */
    private fun lastPhaseClaws() {
        if (phase != phases - 1) return
        val l = leftDownAt
        val r = rightDownAt
        val first = listOf(l, r).filter { it >= 0 }
        if (first.size == 1 && now - first[0] > CLAW_WINDOW) {
            val clawHp = CoxScaling.olmHitpoints(raid.scaling.size, head = false)
            val s = SIDES[side]
            if (l >= 0) {
                left = spawnOlm(cox_npcs.olm_left, s.left, CoxScaling.Bases.olm_left_claw, clawHp)
                leftDownAt = -1
            } else {
                right =
                    spawnOlm(cox_npcs.olm_right, s.right, CoxScaling.Bases.olm_right_claw, clawHp)
                rightDownAt = -1
            }
            for (p in fightPlayers()) p.mes("The Great Olm's claw regrows!")
        }
    }

    private fun checkPhase() {
        if (left != null || right != null || defeated) return
        if (phase >= phases - 1 && !finalStand) {
            finalStand = true
            for (p in fightPlayers()) p.mes(
                "The Great Olm is giving its all. This is its final stand."
            )
            head?.anim(cox_seqs.olm_head_attack_front_enraged)
            return
        }
        risen = false
        head?.anim(cox_seqs.olm_head_death)
        later(HEAD_GONE) {
            head?.let(::despawn)
            head = null
        }
        // Falling crystals while it moves (Wiki: 16-25 under, 10-16 beside).
        repeat(6) { i ->
            later(6 + i * 4) {
                for (p in fightPlayers()) {
                    graphicAt(cox_spots.olm_falling_crystal, p.coords)
                    val at = p.coords
                    later(2) { if (p.coords == at) hurt(p, roll(16, 25)) }
                }
            }
        }
        side = 1 - side
        later(NEXT_RISE) { rise() }
    }

    private fun die() {
        if (defeated) return
        defeated = true
        val h = head ?: return
        h.anim(cox_seqs.olm_head_death)
        hazards.toList().forEach(::removeLoc)
        hazards.clear()
        acid.clear()
        burning.clear()
        later(HEAD_GONE) { despawn(h) }
        exitCrystal?.let { crystal ->
            animLoc(crystal, cox_seqs.crystal_smash)
            later(4) { removeLoc(crystal) }
        }
        complete()
        env.raids.olmDefeated(raid)
    }

    private class Side(
        val head: CoordGrid,
        val left: CoordGrid,
        val right: CoordGrid,
        val angle: LocAngle,
    )

    companion object {
        const val ARENA_MIN_X = 3228
        const val ARENA_MAX_X = 3237
        const val ARENA_MIN_Z = 5731
        const val ARENA_MAX_Z = 5748
        const val BARRIER_Z = 5729
        const val SOUTH_MIDDLE_Z = 5734
        const val NORTH_MIDDLE_Z = 5746
        const val RISE_AFTER_BARRIER = 7
        const val RISEN_TICKS = 7
        const val ATTACK_TICKS = 4
        const val NEXT_RISE = 38
        const val HEAD_GONE = 6
        const val CLAW_WINDOW = 34
        const val BASE_MAX = 27
        const val PRAYER_THROUGH = 20
        const val SPHERE_CHANCE = 8
        const val ACID_POOLS = 8
        const val DRIP_TICKS = 12
        const val POOL_TICKS = 16
        const val BURN_HITS = 6
        const val BURN_EVERY = 7
        const val FIRE_WALL_TICKS = 8
        const val FALLING_CRYSTALS = 11
        const val BOMB_FUSE = 11
        const val BOMB_RADIUS = 4
        const val BURST_TICKS = 3
        const val BIND_TICKS = 8
        const val SWAP_TICKS = 8

        val ROTATION =
            listOf(
                "auto",
                "skip",
                "auto",
                "burst",
                "auto",
                "skip",
                "auto",
                "lightning",
                "auto",
                "skip",
                "auto",
                "swap",
            )

        /** The cache tiles of the exit crystal, by the chest, and the chest. */
        val EXIT_CRYSTAL = CoordGrid(3232, 5749, 0)
        val CHEST = CoordGrid(3233, 5751, 0)

        /**
         * The two sides (TSPS's capture notes): the head and claws' npc tiles (5 by 5), west and
         * east of the arena. The left claw is to the head's left.
         */
        private val SIDES =
            listOf(
                Side(
                    CoordGrid(3223, 5738, 0),
                    CoordGrid(3223, 5743, 0),
                    CoordGrid(3223, 5733, 0),
                    LocAngle.South,
                ),
                Side(
                    CoordGrid(3238, 5738, 0),
                    CoordGrid(3238, 5733, 0),
                    CoordGrid(3238, 5743, 0),
                    LocAngle.North,
                ),
            )
    }
}
