package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

typealias het_locs = HetLocs

typealias het_npcs = HetNpcs

typealias het_objs = HetObjs

typealias het_spotanims = HetSpotanims

typealias het_seqs = HetSeqs

object HetLocs : LocReferences() {
    val mirror = find("toa_het_mirror")
    val mirror_fixed = find("toa_het_mirror_fixed")
    val mirror_dirty = find("toa_het_mirror_dirty")

    /** The walls: plain and breakable, inner and end pieces. Each rises as the next id up. */
    val wall_inner = find("toa_het_barrier_internal")
    val wall_inner_rising = find("toa_het_barrier_internal_rising")
    val wall_end = find("toa_het_barrier_end")
    val wall_end_rising = find("toa_het_barrier_end_rising")
    val breakable_inner = find("toa_het_barrier_destructable_internal")
    val breakable_inner_rising = find("toa_het_barrier_destructable_internal_rising")
    val breakable_end = find("toa_het_barrier_destructable_end")
    val breakable_end_rising = find("toa_het_barrier_destructable_end_rising")
    val wall_broken = find("toa_het_barrier_destroyed")

    /**
     * Left on a tile a movable mirror was taken from: invisible and walkable. The RuneLite plugin's
     * Het solver reads it as an empty tile (it does not watch locs going away).
     */
    val vacated = find("invisible_type8_nonblocking")

    /** The statues the beam goes from and to. */
    val caster = find("toa_het_source")
    val target = find("toa_het_target")

    /** The wall cavity that keeps a pickaxe: "Take-pickaxe", "Deposit-pickaxe". */
    val statue = find("toa_het_statue_parent")
    val lobby_cavity = find("toa_lobby_wall02_cavity_parent")
}

object HetNpcs : NpcReferences() {
    /** Het seal: protected, and weakened for a while after the beam hits the shielded statue. */
    val seal = find("toa_het_goal")
    val seal_weak = find("toa_het_goal_vulnerable")
    val orb = find("toa_het_orb")
}

object HetObjs : ObjReferences() {
    /** The mirror a player carries: "Place". */
    val mirror = find("toa_het_mirror")
    val bronze_pickaxe = find("bronze_pickaxe")
}

/** What a player does with the room's mirrors and cavities, and when a wall rising shoves them. */
object HetSeqs : SeqReferences() {
    val pick_up = find("human_pickupfloor")
    val push = find("human_push")
    val take = find("human_pickuptable")
    val slide = find("agilityarena_player_spikedback")
}

object HetSpotanims : SpotanimReferences() {
    val beam_horizontal = find("fx_beam_attack03")
    val beam_vertical = find("fx_beam_attack01")
    val beam_south_east = find("fx_beam_attack05")
    val beam_south_west = find("fx_beam_attack06")
    val beam_north_west = find("fx_beam_attack07")
    val beam_north_east = find("fx_beam_attack08")
    val beam_end = find("fx_beam_impact01")
    val roof = find("rockfall")
    val shield_hit = find("quest_lunar_spellbook_magic_embue_spot_anim")
    val orb_warning = find("shadow_burst_impact")
    val orb_burst = find("shadow_rush_impact")
}

/**
 * Het seal damage (wiki, Het seal): the pickaxe's tier and the Mining level decide the range of a
 * hit, never the mining speed.
 */
object HetMining {
    /** Dragon, crystal and third age pickaxes are the top tier (they all need Mining 61 to use). */
    fun damageRange(miningLevel: Int, pickaxeLevelReq: Int): IntRange =
        when {
            pickaxeLevelReq >= 61 ->
                when {
                    miningLevel >= 99 -> 19..21
                    miningLevel >= 85 -> 17..19
                    else -> 10..12
                }
            pickaxeLevelReq >= 41 ->
                when {
                    miningLevel >= 99 -> 17..19
                    miningLevel >= 85 -> 15..17
                    else -> 8..10
                }
            else -> 5..7
        }

    /** The seal health by party size (wiki): raid level and invocations do not change it. */
    val sealHitpoints = intArrayOf(119, 225, 321, 417, 512, 608, 704, 800)
}

/**
 * Path of Het, the Test of Strength. A beam leaves the caster statue and must be bounced off
 * mirrors onto the shielded statue; that weakens Het seal for [WEAKENED_TICKS], and the party mines
 * it down with pickaxes before the walls rise again in a new layout. Orbs of darkness roll across
 * the floor the whole time.
 *
 * The walls and mirrors are spawned by the room (they are not part of the map). Their layouts, the
 * orbs paths and the room coordinates are read off the game; the seal health, the pickaxe damage
 * and the 15-tick window are the wiki.
 */
class HetPuzzleRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.HetPuzzle) {
    /** One wall or mirror in the room now. */
    class Placed(var piece: HetPiece, var angle: Int, var rising: Boolean, var dirty: Boolean)

    private val tiles = LinkedHashMap<CoordGrid, Placed>()

    /** The rubble of mined walls: it goes with the rest of the layout. */
    private val rubble = HashSet<CoordGrid>()

    /** Tiles a movable mirror left, marked with [HetLocs.vacated]. */
    private val vacated = HashSet<CoordGrid>()
    private var seal: Npc? = null
    private var layout = env.random.of(HetData.layouts.size)
    private var orbIndex = env.random.of(HetData.orbs.size)
    private var weakenedUntil = 0
    private var risingUntil = 0
    private var nextBeam = 0
    private var nextOrb = 0
    private var weakenPending = false
    private var sealDown = false

    private class RollingOrb(val npc: Npc, val dx: Int, val dz: Int)

    private val orbs = ArrayList<RollingOrb>()

    val sealNpc: Npc?
        get() = seal

    /** The seal can be mined only while it is weakened. */
    val canDestroySeal: Boolean
        get() = isStarted && now < weakenedUntil

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        val hitpoints = HetMining.sealHitpoints[(teamSize - 1).coerceIn(0, 7)]
        seal =
            spawn(het_npcs.seal, SEAL, scale = false, points = SEAL_POINTS, hitpoints = hitpoints)
        placeLoc(CASTER, het_locs.caster, LocShape.CentrepieceStraight, LocAngle[1])
        placeLoc(SHIELD, het_locs.target, LocShape.CentrepieceStraight, LocAngle[3])
    }

    override fun onStart() {
        bossBar = seal
        weakenedUntil = 0
        nextBeam = now + BEAM_INTERVAL
        nextOrb = now + ORB_INTERVAL
        raiseWalls()
    }

    override fun onTick() {
        if (sealDown) {
            stopWorkingOn(npc = seal)
            complete()
            return
        }
        if (now >= nextOrb) {
            nextOrb = now + ORB_INTERVAL
            spawnOrb()
            spawnOrb()
        }
        moveOrbs()
        if (weakenedUntil > 0 && now >= weakenedUntil) {
            weakenedUntil = 0
            seal?.resetTransmog()
            raiseWalls()
        }
        if (weakenPending) {
            weakenPending = false
            weaken()
        }
        if (weakenedUntil == 0 && risingUntil == 0 && now >= nextBeam) {
            nextBeam = now + BEAM_INTERVAL
            fireBeam()
        }
        if (risingUntil != 0 && now >= risingUntil) {
            risingUntil = 0
            settleWalls()
        }
    }

    override fun onComplete() {
        sound(toa_synths.het_end)
        clearLayout()
        takeMirrors()
        clearOrbs()
    }

    override fun onReset() {
        clearLayout()
        takeMirrors()
        clearOrbs()
        seal?.let(::despawn)
        seal = null
        layout = env.random.of(HetData.layouts.size)
        weakenedUntil = 0
        risingUntil = 0
        weakenPending = false
        sealDown = false
        build()
    }

    override fun onDestroy() {
        takeMirrors()
    }

    // The walls
    // ---------------------------------------------------------------------------------------

    private fun wallType(piece: HetPiece, rising: Boolean): LocType =
        when (piece) {
            HetPiece.BreakInner ->
                if (rising) het_locs.breakable_inner_rising else het_locs.breakable_inner
            HetPiece.BreakEnd ->
                if (rising) het_locs.breakable_end_rising else het_locs.breakable_end
            HetPiece.SolidInner -> if (rising) het_locs.wall_inner_rising else het_locs.wall_inner
            HetPiece.SolidEnd -> if (rising) het_locs.wall_end_rising else het_locs.wall_end
            HetPiece.MirrorMovable -> het_locs.mirror
            HetPiece.MirrorFixed -> het_locs.mirror_fixed
        }

    private fun isWall(piece: HetPiece): Boolean =
        piece != HetPiece.MirrorMovable && piece != HetPiece.MirrorFixed

    private fun show(real: CoordGrid, placed: Placed) {
        if (vacated.remove(real) && !isWall(placed.piece)) {
            removeLoc(real, LocShape.CentrepieceStraight)
        }
        val type =
            if (placed.dirty) het_locs.mirror_dirty else wallType(placed.piece, placed.rising)
        val shape =
            if (isWall(placed.piece)) LocShape.CentrepieceStraight else LocShape.CentrepieceDiagonal
        placeLoc(real, type, shape, LocAngle[placed.angle])
    }

    /** The next layout rises: players in the way are pushed off. */
    private fun raiseWalls() {
        risingUntil = now + RISE_TICKS
        sound(toa_synths.het_walls, roomPlayers())
        for (tile in HetData.layouts[layout]) {
            val real = CoordGrid(tile.x, tile.z, 0)
            val dirty = teamSize > 1 && tile.piece == HetPiece.MirrorFixed && env.random.of(2) == 0
            val placed = Placed(tile.piece, tile.angle, rising = isWall(tile.piece), dirty = dirty)
            tiles[real] = placed
            show(real, placed)
        }
        for (player in challengePlayers()) {
            if (blocked(player)) {
                slideOff(player)
            }
        }
        layout = (layout + 1) % HetData.layouts.size
    }

    private fun blocked(player: Player): Boolean {
        val real = raid.realOf(player.coords) ?: return false
        return tiles.containsKey(real)
    }

    /** The risen walls settle into their solid form. */
    private fun settleWalls() {
        for ((real, placed) in tiles) {
            if (placed.rising) {
                placed.rising = false
                show(real, placed)
            }
        }
    }

    /** Takes every wall and mirror out of the room (also what a reset and a win do). */
    fun clearLayout() {
        for (real in tiles.keys.toList()) {
            removeLoc(real, LocShape.CentrepieceStraight)
            removeLoc(real, LocShape.CentrepieceDiagonal)
        }
        tiles.clear()
        for (real in rubble) {
            removeLoc(real, LocShape.CentrepieceStraight)
        }
        rubble.clear()
        for (real in vacated) {
            removeLoc(real, LocShape.CentrepieceStraight)
        }
        vacated.clear()
    }

    /** A movable mirror is taken off [real]. */
    private fun vacate(real: CoordGrid) {
        tiles.remove(real)
        removeLoc(real, LocShape.CentrepieceDiagonal)
        placeLoc(real, het_locs.vacated, LocShape.CentrepieceStraight, LocAngle[0])
        vacated += real
    }

    private fun slideOff(player: Player) {
        val here = raid.realOf(player.coords) ?: return
        for (radius in 1..2) {
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != radius) {
                        continue
                    }
                    val spot = here.translate(dx, dz)
                    if (tiles.containsKey(spot) || !walkable(spot) || !room.inChallenge(spot)) {
                        continue
                    }
                    player.anim(het_seqs.slide)
                    knockback(player, spot, cycles = 29)
                    return
                }
            }
        }
    }

    // The mirrors
    // -------------------------------------------------------------------------------------

    fun placedAt(real: CoordGrid): Placed? = tiles[real]

    /** Puts a piece straight onto the real tile [real] (the layouts and the tests use this). */
    fun put(real: CoordGrid, piece: HetPiece, angle: Int, dirty: Boolean = false) {
        val placed = Placed(piece, angle, rising = false, dirty = dirty)
        tiles[real] = placed
        show(real, placed)
    }

    /** Takes a movable mirror up into the inventory. */
    fun pickUp(player: Player, real: CoordGrid): Boolean {
        val placed = tiles[real] ?: return false
        if (placed.piece != HetPiece.MirrorMovable || placed.dirty) {
            return false
        }
        if (!player.invAdd(player.inv, het_objs.mirror, 1).success) {
            return false
        }
        vacate(real)
        return true
    }

    /** Turns a movable mirror a quarter. */
    fun rotate(real: CoordGrid, clockwise: Boolean) {
        val placed = tiles[real] ?: return
        if (placed.piece != HetPiece.MirrorMovable) {
            return
        }
        placed.angle = (placed.angle + if (clockwise) 1 else 3) and 3
        show(real, placed)
    }

    /** Pushes a movable mirror one tile away from [from]. */
    fun push(real: CoordGrid, from: CoordGrid): Boolean {
        val placed = tiles[real] ?: return false
        if (placed.piece != HetPiece.MirrorMovable) {
            return false
        }
        val target =
            real.translate(Integer.signum(real.x - from.x), Integer.signum(real.z - from.z))
        if (tiles.containsKey(target) || !walkable(target) || !room.inChallenge(target)) {
            return false
        }
        vacate(real)
        tiles[target] = placed
        show(target, placed)
        return true
    }

    /** Wipes a dirty fixed mirror so that it reflects. */
    fun clean(real: CoordGrid): Boolean {
        val placed = tiles[real] ?: return false
        if (!placed.dirty) {
            return false
        }
        placed.dirty = false
        show(real, placed)
        return true
    }

    /** Puts a carried mirror down on the real tile [real], where the player stands. */
    fun placeMirror(player: Player, real: CoordGrid): Boolean {
        if (!isStarted || tiles.containsKey(real) || !room.inChallenge(real)) {
            return false
        }
        if (!player.invDel(player.inv, het_objs.mirror, 1).success) {
            return false
        }
        val placed = Placed(HetPiece.MirrorMovable, 0, rising = false, dirty = false)
        tiles[real] = placed
        show(real, placed)
        return true
    }

    /** A breakable wall that has settled can be mined through. */
    fun isBreakable(real: CoordGrid): Boolean {
        val placed = tiles[real] ?: return false
        return !placed.rising &&
            (placed.piece == HetPiece.BreakInner || placed.piece == HetPiece.BreakEnd)
    }

    /** The wall crumbles into rubble that no longer blocks anything. */
    fun breakWall(real: CoordGrid): Boolean {
        if (!isBreakable(real)) {
            return false
        }
        tiles.remove(real)
        stopWorkingOn(real)
        placeLoc(real, het_locs.wall_broken, LocShape.CentrepieceStraight, LocAngle[0])
        rubble += real
        return true
    }

    /** Carried mirrors go back to the room when the walls come down. */
    private fun takeMirrors() {
        for (player in roomPlayers()) {
            player.invDel(player.inv, het_objs.mirror, MAX_MIRRORS, strict = false)
        }
    }

    // The beam
    // ----------------------------------------------------------------------------------------

    private fun blockerAt(x: Int, z: Int): HetBlocker {
        val real = CoordGrid(x, z, 0)
        val placed = tiles[real]
        if (placed != null) {
            return when {
                placed.dirty -> HetBlocker.Wall
                placed.piece == HetPiece.MirrorMovable || placed.piece == HetPiece.MirrorFixed ->
                    HetBlocker.Mirror(placed.angle)
                else -> HetBlocker.Wall
            }
        }
        // Past the edge of the room is a wall as far as the beam is concerned.
        if (!room.inChallenge(real)) {
            return HetBlocker.Wall
        }
        val tile = raid.regionOf(real)
        val flags = env.collision[tile.x, tile.z, tile.level]
        return if ((flags and CollisionFlag.LOC) != 0) HetBlocker.Wall else HetBlocker.Open
    }

    private fun inside3x3(corner: CoordGrid, x: Int, z: Int): Boolean =
        x in corner.x..corner.x + 2 && z in corner.z..corner.z + 2

    /**
     * The caster statue fires: whatever the beam reaches is hit, and the shield weakens the seal.
     */
    fun fireBeam() {
        sound(toa_synths.het_beam, roomPlayers())
        val result =
            HetBeam.trace(
                BEAM_START.x,
                BEAM_START.z,
                blocker = ::blockerAt,
                shield = { x, z -> inside3x3(SHIELD, x, z) },
                stops = { x, z -> inside3x3(CASTER, x, z) || inside3x3(SEAL, x, z) },
            )
        val players = challengePlayers()
        for ((index, tile) in result.tiles.withIndex()) {
            val spot =
                when (tile.shape) {
                    HetBeamTile.Shape.Horizontal -> het_spotanims.beam_horizontal
                    HetBeamTile.Shape.Vertical -> het_spotanims.beam_vertical
                    HetBeamTile.Shape.SouthEast -> het_spotanims.beam_south_east
                    HetBeamTile.Shape.SouthWest -> het_spotanims.beam_south_west
                    HetBeamTile.Shape.NorthWest -> het_spotanims.beam_north_west
                    HetBeamTile.Shape.NorthEast -> het_spotanims.beam_north_east
                    HetBeamTile.Shape.End -> het_spotanims.beam_end
                }
            graphic(spot, CoordGrid(tile.x, tile.z, 0), height = BEAM_HEIGHT, delay = index + 1)
            for (player in players) {
                val real = raid.realOf(player.coords) ?: continue
                if (real.x == tile.x && real.z == tile.z) {
                    hurt(player, BEAM_DAMAGE, extra = env.random.of(BEAM_EXTRA + 1))
                }
            }
        }
        if (result.hitsShield) {
            weakenPending = true
        }
    }

    /** The beam struck the shielded statue: the seal weakens, and the walls come down. */
    fun weaken() {
        weakenedUntil = now + WEAKENED_TICKS
        for (player in challengePlayers()) {
            player.mes("<col=185820>The statue has been struck! The seal weakens!</col>")
            player.mes("The room begins to shake violently!")
        }
        sound(toa_synths.het_statue)
        sound(toa_synths.het_roof, roomPlayers())
        graphic(het_spotanims.shield_hit, SHIELD_HIT, height = 254)
        seal?.transmog(env.npcTypes[het_npcs.seal_weak], Int.MAX_VALUE)
        takeMirrors()
        clearLayout()
        for (tile in ROOF_TILES) {
            graphic(het_spotanims.roof, tile, delay = 15)
        }
    }

    // The orbs
    // ----------------------------------------------------------------------------------------

    private fun spawnOrb() {
        val orb = HetData.orbs[orbIndex]
        orbIndex = (orbIndex + 1 + env.random.of(HetData.orbs.size - 1)) % HetData.orbs.size
        val real = CoordGrid(orb.x, orb.z, 0)
        graphic(het_spotanims.orb_warning, real)
        later(ORB_WARNING_TICKS) {
            val npc = spawn(het_npcs.orb, real, scale = false, points = 0.0, passThrough = true)
            graphic(het_spotanims.orb_burst, real)
            orbs += RollingOrb(npc, orb.dx, orb.dz)
        }
    }

    private fun moveOrbs() {
        for (orb in orbs.toList()) {
            val here = raid.realOf(orb.npc.coords) ?: continue
            val victim = challengePlayers().firstOrNull { raid.realOf(it.coords) == here }
            if (victim != null) {
                hurt(victim, ORB_DAMAGE, extra = env.random.of(ORB_EXTRA + 1))
                burst(orb)
                continue
            }
            val next = here.translate(orb.dx, orb.dz)
            if (!walkable(next) || tiles.containsKey(next)) {
                burst(orb)
                continue
            }
            orb.npc.walk(raid.regionOf(next))
        }
    }

    private fun burst(orb: RollingOrb) {
        raid.realOf(orb.npc.coords)?.let { graphic(het_spotanims.orb_burst, it) }
        orbs -= orb
        despawn(orb.npc)
    }

    private fun clearOrbs() {
        for (orb in orbs.toList()) {
            orbs -= orb
            despawn(orb.npc)
        }
    }

    // The seal
    // ----------------------------------------------------------------------------------------

    /**
     * A pickaxe hit of [damage] on the weakened seal. It is a real hit, so the seal shows its
     * health bar and the hitsplat; points come with it. Breaking it wins the room.
     */
    fun damageSeal(player: Player, damage: Int) {
        val target = seal ?: return
        if (!canDestroySeal || sealDown) {
            return
        }
        target.queueHit(player, 1, HitType.Typeless, damage, env.npcHitModifier)
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !== seal) {
            return
        }
        if (!canDestroySeal || sealDown) {
            hit.damage = 0
        } else if (hit.damage >= npc.hitpoints) {
            // It never dies: the last hit leaves one hitpoint and the room is won.
            sealDown = true
            hit.damage = (npc.hitpoints - 1).coerceAtLeast(0)
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc === seal) {
            sealDown = true
            npc.hitpoints = 1
        }
    }

    companion object {
        /** The seal, the statues and the beam, as real tombs coordinates. */
        val SEAL = CoordGrid(3679, 5279, 0)
        val CASTER = CoordGrid(3676, 5279, 0)
        val SHIELD = CoordGrid(3682, 5279, 0)
        val BEAM_START = CoordGrid(3676, 5280, 0)
        val SHIELD_HIT = CoordGrid(3683, 5280, 0)
        val ROOF_TILES =
            listOf(
                CoordGrid(3678, 5275, 0),
                CoordGrid(3676, 5277, 0),
                CoordGrid(3674, 5275, 0),
                CoordGrid(3677, 5283, 0),
                CoordGrid(3674, 5283, 0),
                CoordGrid(3686, 5284, 0),
            )

        /** The seal is worth 2.5 room points a point of damage. */
        const val SEAL_POINTS = 2.5

        /** Wiki: after the beam hits the statue the seal can be mined for 15 ticks. */
        const val WEAKENED_TICKS = 15
        const val RISE_TICKS = 2
        const val BEAM_INTERVAL = 9
        const val ORB_INTERVAL = 7
        const val ORB_WARNING_TICKS = 3
        const val BEAM_HEIGHT = 255
        const val BEAM_DAMAGE = 5
        const val BEAM_EXTRA = 3
        const val ORB_DAMAGE = 5
        const val ORB_EXTRA = 3
        const val MAX_MIRRORS = 28
    }
}
