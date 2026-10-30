package org.rsmod.content.areas.misc.isleofsouls.toa

import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.invtx.invDel
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.movement.MoveSpeed
import org.rsmod.game.obj.Obj
import org.rsmod.map.CoordGrid

typealias crondis_npcs = CrondisNpcs

typealias crondis_locs = CrondisLocs

typealias crondis_objs = CrondisObjs

typealias crondis_seqs = CrondisSeqs

typealias crondis_spotanims = CrondisSpotanims

object CrondisNpcs : NpcReferences() {
    /** The Palm of Resourcefulness as it grows, stage by stage ("Water"). */
    val palm =
        listOf(
            find("toa_crondis_tree_1"),
            find("toa_crondis_tree_2"),
            find("toa_crondis_tree_3"),
            find("toa_crondis_tree_4"),
            find("toa_crondis_tree_5"),
        )
    val crocodile = find("toa_crondis_crocodile")
}

object CrondisLocs : LocReferences() {
    val waterfall = find("toa_crondis_water_source")
    val waterfall_dry = find("toa_crondis_water_source_empty")
    val acid_statue = find("toa_crondis_column_trap")
    val acid_orb = find("toa_crondis_orb")
    val spear_statue = find("toa_crondis_row_trap")
    val spear = find("toa_crondis_row_trap_fire")
    val crocodile_opening = find("toa_wall02_crocodiles04")
    val blocking = find("invisible_type8_blocking_size3")
}

object CrondisObjs : ObjReferences() {
    val container = find("toa_crondis_water_container")
}

object CrondisSeqs : SeqReferences() {
    val fill = find("human_pickupfloor")
    val spear_idle = find("crondis_spear_trap_idle")
    val spear_mouth = find("crondis_spear_trap_activate")
    val spear_out = find("crondis_spear_trap_spear02")
    val acid_statue = find("crondis_column_trap_anim")
    val croc_attack = find("croc_attack")
    val croc_death = find("croc_death")
}

object CrondisSpotanims : SpotanimReferences() {
    val acid = find("crondis_column_trap_anim")
}

/**
 * Path of Crondis, the Test of Resourcefulness: carry water from the waterfalls to the Palm of
 * Resourcefulness past acid statues, spear statues and crocodiles. Any hit on the way spills half
 * the water carried; crocodiles drink from a watered palm. The room is won when the palm is grown.
 *
 * From the wiki: 175 water and 125 more for each extra player, a trap hit spills 50% and takes 6
 * Defence, 3 Agility and 2 Prayer, the crocodiles' 18 bite (3 more for each recent trap hit, up to
 * 36, a third through Protect from Melee for 12 Prayer) and their priorities. From TSPS (OpenRune
 * captures): the trap rhythms, waves every 46-50 ticks, the drinking, the waterfall refill time.
 */
class CrondisPuzzleRuntime(env: ToaRoomEnv, raid: ToaRaid) :
    ToaRoomRuntime(env, raid, ToaRoom.CrondisPuzzle) {
    private class Trail(val x: Int, val z: Int, val north: Boolean, var ticks: Int)

    private class Croc(val npc: Npc) {
        var wakeAt = 0
        var drinkAt = 0
        val attackers = HashSet<Int>()
    }

    var palm: Npc? = null
        private set

    var goal = WATER_FIRST
        private set

    var watered = 0
        private set

    private var palmStage = 0
    private val water = HashMap<Int, Int>()
    private val containers = HashMap<Int, List<Obj>>()
    private val trails = ArrayList<Trail>()
    private var acidTicks = 0
    private var spearTick = 0
    private var crocTicks = 0
    private val crocs = LinkedHashMap<Npc, Croc>()
    private val recentHits = HashMap<Int, ArrayDeque<Int>>()
    private val trapCooldown = HashMap<Pair<Int, Boolean>, Int>()
    private var side = 0
    private val dryUntil = HashMap<CoordGrid, Int>()

    /** The tests choose the crocodiles' side and stop the traps. */
    var trapsEnabled = true

    val crocodileCount: Int
        get() = crocs.size

    fun waterOf(player: Player): Int = water[player.accountId] ?: 0

    fun setWater(player: Player, amount: Int) {
        water[player.accountId] = amount.coerceIn(0, 100)
    }

    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun build() {
        watered = 0
        palmStage = 0
        palm = spawn(crondis_npcs.palm[0], PALM, scale = false, points = 0.0, hitpoints = goal)
        for (tile in STATUES_SOUTH) {
            placeLoc(tile, crondis_locs.acid_statue, LocShape.CentrepieceStraight, LocAngle[0])
        }
        for (tile in STATUES_NORTH) {
            placeLoc(tile, crondis_locs.acid_statue, LocShape.CentrepieceStraight, LocAngle[2])
        }
        side = env.random.of(CROC_SIDES.size)
        for ((x, z, face) in CROC_SIDES[side].walls) {
            placeLoc(
                CoordGrid(x, z, Z),
                crondis_locs.crocodile_opening,
                LocShape.CentrepieceStraight,
                LocAngle[face],
            )
        }
        crocTicks = nextWave()
    }

    private fun nextWave(): Int = env.random.of(CROC_WAVE.first, CROC_WAVE.last) - 1

    override fun onIdle() {
        handOutContainers()
    }

    override fun onStart() {
        goal = WATER_FIRST + (teamSize - 1) * WATER_PER_EXTRA
        watered = 0
        palm?.let { tree ->
            tree.baseHitpointsLvl = goal
            tree.hitpoints = 1
            bossBar = tree
        }
        acidTicks = 0
        spearTick = 0
    }

    override fun onComplete() {
        clearRoom()
        for (member in raid.active) {
            if (member.room == room) {
                raid.points.award(member.player.accountId, COMPLETION_POINTS)
            }
        }
    }

    override fun onReset() {
        clearRoom()
        palm?.let(::despawn)
        val sooner = crocTicks - CROC_RESET_SOONER
        clear()
        build()
        crocTicks = sooner.coerceAtLeast(1)
    }

    private fun clearRoom() {
        for (npc in crocs.keys.toList()) {
            despawn(npc)
        }
        crocs.clear()
        trails.clear()
        for (player in roomPlayers()) {
            val held = player.inv.count { it?.id == crondis_objs.container.id }
            if (held > 0) {
                player.invDel(player.inv, crondis_objs.container, held, strict = false)
            }
        }
        water.clear()
        for (objs in containers.values) {
            for (obj in objs) {
                env.objRepo.del(obj)
            }
        }
        containers.clear()
        for (tile in dryUntil.keys.toList()) {
            swapLoc(tile, listOf(crondis_locs.waterfall_dry), crondis_locs.waterfall)
        }
        dryUntil.clear()
    }

    /** The palm's bar counts the water given (its hitpoints never go below 1). */
    override fun bossBarHp(npc: Npc): Int = if (npc == palm) watered else npc.hitpoints

    // Containers and water
    // -------------------------------------------------------------------------------------

    /** Each player sees their own pair of containers on the floor, and can take one at a time. */
    private fun handOutContainers() {
        if (isCompleted) {
            return
        }
        for (player in roomPlayers()) {
            if (player.accountId in containers) {
                continue
            }
            containers[player.accountId] =
                CONTAINER_TILES.map { tile ->
                    env.objRepo.add(
                        crondis_objs.container,
                        raid.regionOf(tile),
                        Int.MAX_VALUE,
                        receiver = player,
                        reveal = Int.MAX_VALUE,
                    )
                }
        }
    }

    /** Taking a container leaves the floor one in place, for another after a death. */
    fun takeContainer(player: Player) {
        when {
            isCompleted -> player.mes("You don't need a container right now.")
            player.inv.any { it?.id == crondis_objs.container.id } ->
                player.mes("You already have a container.")
            !player.invAdd(player.inv, crondis_objs.container).success ->
                player.mes("You do not have enough space to pick this up.")
            else -> {
                player.anim(crondis_seqs.fill)
                player.soundSynth(toa_synths.take)
            }
        }
    }

    fun hasContainer(player: Player): Boolean =
        player.inv.any { it?.id == crondis_objs.container.id }

    /** "Fill" at a waterfall: a full container, and the waterfall runs dry for a while. */
    fun fill(player: Player, waterfall: CoordGrid, dry: Boolean) {
        when {
            isCompleted -> player.mes("You don't need to do that right now.")
            dry || waterfall in dryUntil -> {
                player.mes("It's empty.")
                player.soundSynth(toa_synths.crondis_empty)
            }
            !hasContainer(player) -> player.mes("You don't have anything to fill.")
            waterOf(player) >= 100 -> player.mes("Your container is full.")
            else -> {
                player.mes("You fill your container.")
                player.anim(crondis_seqs.fill)
                player.soundSynth(toa_synths.crondis_fill)
                player.runEnergy = (player.runEnergy + RUN_ENERGY_BACK).coerceAtMost(MAX_RUN)
                setWater(player, 100)
                swapLoc(waterfall, listOf(crondis_locs.waterfall), crondis_locs.waterfall_dry)
                dryUntil[waterfall] = now + REFILL_TICKS - (teamSize - 1) * REFILL_PER_PLAYER
            }
        }
    }

    /** "Water" (or a container used on it): everything carried goes into the palm. */
    fun waterPalm(player: Player) {
        val amount = waterOf(player)
        if (!hasContainer(player) || amount < 1) {
            player.mes("You have nothing to water the palm with.")
            return
        }
        if (!isStarted) {
            return
        }
        val size =
            when {
                amount > 50 -> "lot"
                amount > 25 -> "reasonable amount"
                else -> "small amount"
            }
        player.mes("You empty a $size of water onto the palm.")
        player.anim(crondis_seqs.fill)
        player.soundSynth(toa_synths.crondis_water_palm)
        setWater(player, 0)
        watered = (watered + amount).coerceAtMost(goal)
        raid.points.award(player.accountId, amount)
        updatePalm()
    }

    /** The palm grows a stage with each quarter of its water; fully watered wins the room. */
    private fun updatePalm() {
        val tree = palm ?: return
        val want = (watered * 4 / goal.coerceAtLeast(1)).coerceIn(0, 4)
        if (want != palmStage) {
            sound(
                if (want > palmStage) toa_synths.crondis_palm_grow
                else toa_synths.crondis_palm_shrink,
                roomPlayers(),
            )
            palmStage = want
            if (want == 0) {
                tree.resetTransmog()
            } else {
                tree.transmog(env.npcTypes[crondis_npcs.palm[want]], Int.MAX_VALUE)
            }
        }
        tree.hitpoints = watered.coerceAtLeast(1)
        if (want == 4 && isStarted) {
            complete()
        }
    }

    fun emptyContainer(player: Player) {
        if (waterOf(player) <= 0) {
            player.mes("It's already empty.")
            return
        }
        setWater(player, 0)
        player.mes("You empty your water container.")
    }

    /** A hit on the way spills half of the water carried (rounded in the player's favour). */
    private fun spill(player: Player) {
        val amount = waterOf(player)
        if (amount <= 0) {
            return
        }
        player.mes("Water spills out of your container.")
        player.soundSynth(toa_synths.water_pour)
        setWater(player, amount / 2)
    }

    /** A trap hit: water spilled, damage, and Defence, Agility and Prayer sapped (wiki). */
    private fun trapHit(player: Player, base: Int, spear: Boolean) {
        val key = player.accountId to spear
        if ((trapCooldown[key] ?: -1) > now) {
            return
        }
        trapCooldown[key] = now + if (spear) 3 else 2
        recentHits.getOrPut(player.accountId) { ArrayDeque() }.addLast(now)
        spill(player)
        val low = maxHit(base)
        hurtFlat(player, env.random.of(low, low + TRAP_SPREAD))
        player.statSub(stats.defence, 6, 0)
        player.statSub(stats.agility, 3, 0)
        player.statSub(stats.prayer, 2, 0)
    }

    // The tick
    // ----------------------------------------------------------------------------------------

    override fun onTick() {
        handOutContainers()
        for ((tile, until) in dryUntil.toList()) {
            if (now >= until) {
                dryUntil.remove(tile)
                swapLoc(tile, listOf(crondis_locs.waterfall_dry), crondis_locs.waterfall)
            }
        }
        val players = challengePlayers()
        if (trapsEnabled) {
            tickAcid(players)
            tickSpears(players)
        }
        if (crocTicks-- <= 0) {
            crocTicks = nextWave()
            if (crocs.size < CROC_LIMIT) {
                val spawns = CROC_SIDES[side].spawns
                repeat(minOf((teamSize + 1) / 2, spawns.size)) { spawnCroc(spawns[it]) }
            }
        }
        tickCrocs(players)
        driveMonsters()
        palm?.let { it.hitpoints = watered.coerceAtLeast(1) }
    }

    private fun tickAcid(players: List<Player>) {
        trails.removeAll { --it.ticks <= 0 }
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            for (trail in trails) {
                if (at.x != trail.x) {
                    continue
                }
                val along = if (trail.north) at.z - trail.z else trail.z - at.z
                if (along !in 1..10) {
                    continue
                }
                // The cloud travels a tile every few client ticks: you are hit as it passes you.
                val reach = 1 + (along - 1) / 3
                if (trail.ticks < 6 - reach && trail.ticks >= 6 - reach - 2) {
                    trapHit(player, ACID_BASE, spear = false)
                }
            }
        }
        if (--acidTicks <= 0) {
            acidTicks = ACID_INTERVAL
            spawnAcid(ACID_NORTH, north = false)
            spawnAcid(ACID_SOUTH, north = true)
        }
    }

    private fun spawnAcid(bases: List<CoordGrid>, north: Boolean) {
        for (base in bases) {
            val offsets = shuffled(listOf(0, 1, 2, 3, 4)).take(env.random.of(2, 3))
            for (dx in offsets) {
                val tile = base.translate(dx, 0)
                placeLoc(tile, crondis_locs.acid_orb, LocShape.CentrepieceStraight, LocAngle[0])
                later(3) { removeLoc(tile) }
                later(2) {
                    trails += Trail(tile.x, tile.z, north, 6)
                    for (step in 1..10) {
                        graphic(
                            crondis_spotanims.acid,
                            tile.translate(0, if (north) step else -step),
                            delay = 10 * step,
                        )
                    }
                }
            }
        }
    }

    /** Each row of spear statues fires on its own rhythm (TSPS's tables). */
    private fun tickSpears(players: List<Player>) {
        val active = ArrayList<CoordGrid>()
        for ((index, row) in SPEAR_ROWS.withIndex()) {
            for (east in listOf(false, true)) {
                for (y in 0 until 5) {
                    val delay = SPEAR_DELAYS[index][y + if (east) 5 else 0]
                    val z = row.z + y * 2 + if (east) 1 else 0
                    val statue = CoordGrid(row.x + if (east) 7 else 0, z, Z)
                    val spear = CoordGrid(row.x + if (east) 4 else 2, z, Z)
                    when (spearTick) {
                        delay ->
                            animLoc(statue, crondis_locs.spear_statue, crondis_seqs.spear_mouth)
                        (delay + 3) % 10 ->
                            animLoc(spear, crondis_locs.spear, crondis_seqs.spear_out)
                        (delay + 4) % 10,
                        (delay + 5) % 10 -> active += spear
                        (delay + 6) % 10 -> {
                            animLoc(spear, crondis_locs.spear, crondis_seqs.spear_idle)
                            active += spear
                        }
                        (delay + 7) % 10 ->
                            animLoc(statue, crondis_locs.spear_statue, crondis_seqs.spear_idle)
                    }
                }
            }
        }
        spearTick = (spearTick + 1) % 10
        for (player in players) {
            val at = raid.realOf(player.coords) ?: continue
            if (active.any { at.z == it.z && at.x in it.x..it.x + 2 }) {
                trapHit(player, SPEAR_BASE, spear = true)
            }
        }
    }

    // Crocodiles
    // --------------------------------------------------------------------------------------

    private val bite =
        NpcAttack(NpcAttackStyle.Melee, anim = crondis_seqs.croc_attack, maxHit = CROC_BITE)

    private fun spawnCroc(tile: CoordGrid) {
        val npc = spawn(crondis_npcs.crocodile, tile, scale = false, points = 1.0)
        val croc = Croc(npc)
        croc.wakeAt = now + CROC_WAKE_TICKS
        crocs[npc] = croc
        val monster = monster(npc, bite, range = 1, speed = CROC_SPEED, delay = CROC_WAKE_TICKS)
        monster.scripted = true
        // Crocodiles crawl: a tile every two ticks, which the client glides at half speed.
        monster.stepTicks = 2
        npc.defaultMoveSpeed = MoveSpeed.Crawl
    }

    /**
     * A crocodile goes for the palm once it has water, else for anyone carrying water near it, else
     * for whoever hit it (the wiki's order; the sight range is a guess).
     */
    private fun tickCrocs(players: List<Player>) {
        for ((npc, croc) in crocs.toList()) {
            if (!npc.isSlotAssigned || npc.hitpoints <= 0) {
                crocs.remove(npc)
                continue
            }
            val monster = monsters[npc] ?: continue
            if (now < croc.wakeAt) {
                continue
            }
            val tree = palm
            if (watered > 0 && tree != null) {
                monster.scripted = true
                monster.target = null
                if (!touching(npc, tree)) {
                    step(monster, tree.coords.translate(tree.size / 2, tree.size / 2), false)
                } else if (now >= croc.drinkAt) {
                    npc.resetMovement()
                    npc.faceSquare(tree.coords, tree.size, tree.size)
                    croc.drinkAt = now + CROC_SPEED
                    npc.anim(crondis_seqs.croc_attack)
                    watered = (watered - env.random.of(2, 5)).coerceAtLeast(0)
                    updatePalm()
                }
                continue
            }
            val carrier =
                players
                    .filter { waterOf(it) > 0 && gap(npc, it.coords) <= CROC_SIGHT }
                    .minByOrNull { gap(npc, it.coords) }
            val hitter = players.firstOrNull { it.accountId in croc.attackers }
            val target = carrier ?: hitter
            monster.scripted = target == null
            monster.target = target
        }
    }

    private fun touching(npc: Npc, tree: Npc): Boolean {
        val c = npc.coords
        val p = tree.coords
        val xOverlap = c.x < p.x + tree.size && c.x + npc.size > p.x
        val zOverlap = c.z < p.z + tree.size && c.z + npc.size > p.z
        val xTouch = c.x + npc.size == p.x || c.x == p.x + tree.size
        val zTouch = c.z + npc.size == p.z || c.z == p.z + tree.size
        return (xOverlap && zTouch) || (zOverlap && xTouch)
    }

    /** The bite: 18, and 3 more for each trap hit in the last 30 seconds, up to 36 (wiki). */
    override fun swing(monster: ToaMonster, target: Player) {
        val npc = monster.npc
        val hits = recentHits[target.accountId]
        hits?.removeAll { now - it >= HAZARD_WINDOW }
        val damage = minOf(CROC_BITE_MAX, CROC_BITE + CROC_PER_HAZARD * (hits?.size ?: 0))
        val attack = bite.copy(maxHit = damage, minHit = damage, typeless = true)
        env.attacks.attack(npc, target, attack) { victim, hit ->
            if (hit.damage <= 0) {
                return@attack
            }
            if (victim.vars[varbits.protect_from_melee] == 1) {
                // A third through Protect from Melee, at the cost of 12 Prayer (wiki).
                hit.damage = hit.damage / 3
                victim.statSub(stats.prayer, 12, 0)
            }
            spill(victim)
        }
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (source != null) {
            crocs[npc]?.attackers?.add(source.accountId)
        }
    }

    companion object {
        const val Z = 0
        val PALM = CoordGrid(3934, 5278, Z)

        /** Each player's two containers on the floor. */
        val CONTAINER_TILES = listOf(CoordGrid(3934, 5273, Z), CoordGrid(3938, 5287, Z))

        /** The acid statues and where their clouds start. */
        val STATUES_SOUTH = listOf(CoordGrid(3943, 5255, Z), CoordGrid(3929, 5255, Z))
        val STATUES_NORTH = listOf(CoordGrid(3929, 5304, Z), CoordGrid(3943, 5304, Z))
        val ACID_SOUTH = listOf(CoordGrid(3941, 5257, Z), CoordGrid(3927, 5257, Z))
        val ACID_NORTH = listOf(CoordGrid(3941, 5303, Z), CoordGrid(3927, 5303, Z))

        /** The waterfalls (the map's own locs). */
        val WATERFALLS =
            listOf(
                CoordGrid(3926, 5250, Z),
                CoordGrid(3940, 5250, Z),
                CoordGrid(3926, 5306, Z),
                CoordGrid(3940, 5306, Z),
            )

        /** The west spear statue of each block of rows (the map's own locs). */
        val SPEAR_ROWS =
            listOf(
                CoordGrid(3925, 5293, Z),
                CoordGrid(3939, 5293, Z),
                CoordGrid(3925, 5258, Z),
                CoordGrid(3939, 5258, Z),
            )
        val SPEAR_DELAYS =
            arrayOf(
                intArrayOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8),
                intArrayOf(0, 3, 6, 9, 2, 0, 3, 6, 9, 2),
                intArrayOf(2, 1, 1, 0, 0, 2, 2, 1, 1, 0),
                intArrayOf(0, 1, 2, 3, 4, 4, 3, 2, 1, 0),
            )

        /** The three sides the crocodiles may come from: spawn tiles and wall openings. */
        class CrocSide(val spawns: List<CoordGrid>, val walls: List<Triple<Int, Int, Int>>)

        val CROC_SIDES =
            listOf(
                CrocSide(
                    listOf(
                        CoordGrid(3946, 5274, Z),
                        CoordGrid(3925, 5285, Z),
                        CoordGrid(3946, 5285, Z),
                        CoordGrid(3925, 5274, Z),
                    ),
                    listOf(Triple(3950, 5273, 0), Triple(3948, 5271, 1)),
                ),
                CrocSide(
                    listOf(
                        CoordGrid(3925, 5274, Z),
                        CoordGrid(3946, 5285, Z),
                        CoordGrid(3925, 5285, Z),
                        CoordGrid(3946, 5274, Z),
                    ),
                    listOf(Triple(3922, 5273, 2), Triple(3924, 5271, 1)),
                ),
                CrocSide(
                    listOf(
                        CoordGrid(3925, 5285, Z),
                        CoordGrid(3946, 5274, Z),
                        CoordGrid(3925, 5274, Z),
                        CoordGrid(3946, 5285, Z),
                    ),
                    listOf(Triple(3922, 5287, 2), Triple(3923, 5289, 3)),
                ),
            )

        const val WATER_FIRST = 175
        const val WATER_PER_EXTRA = 125
        const val COMPLETION_POINTS = 400
        const val RUN_ENERGY_BACK = 2_000
        const val MAX_RUN = 10_000
        const val REFILL_TICKS = 128
        const val REFILL_PER_PLAYER = 18
        const val ACID_INTERVAL = 5
        const val ACID_BASE = 5
        const val SPEAR_BASE = 6
        const val TRAP_SPREAD = 8
        val CROC_WAVE = 46..50
        const val CROC_RESET_SOONER = 10
        const val CROC_WAKE_TICKS = 4
        const val CROC_LIMIT = 8
        const val CROC_SPEED = 7
        const val CROC_SIGHT = 3
        const val CROC_BITE = 18
        const val CROC_PER_HAZARD = 3
        const val CROC_BITE_MAX = 36
        const val HAZARD_WINDOW = 50
    }
}
