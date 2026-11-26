package org.rsmod.content.areas.misc.isleofsouls.cox

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.stat
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.areas.misc.isleofsouls.toa.ToaProjAnims
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitBuilder
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The puzzle rooms. Tiles are the cache's, by room version (west, north, east turning), from TSPS's
 * rsprox notes; the rules are the Wiki's where it has them (see each room's comment).
 */
class CoxPuzzles
@Inject
constructor(
    private val rooms: CoxRoomRegistry,
    private val raids: CoxRaids,
    private val npcRepo: NpcRepository,
    private val locRepo: LocRepository,
    private val locTypes: LocTypeList,
    private val objTypes: ObjTypeList,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(CoxRoomType.IceDemon) { env, raid, room -> CoxIceDemon(env, raid, room) }
        rooms.register(CoxRoomType.Thieving) { env, raid, room -> CoxThieving(env, raid, room) }
        rooms.register(CoxRoomType.Tightrope) { env, raid, room -> CoxTightrope(env, raid, room) }
        rooms.register(CoxRoomType.Crabs) { env, raid, room -> CoxCrabs(env, raid, room) }

        onOpLoc1(cox_locs.brazier_unlit) { brazier(it.loc) }
        onOpLoc1(cox_locs.brazier_lit) { brazier(it.loc) }
        onOpLocU(cox_locs.brazier_lit, cox_objs.kindling) { brazier(it.loc) }
        onOpLocU(cox_locs.brazier_unlit, cox_objs.kindling) { brazier(it.loc) }

        onOpLoc1(cox_locs.thieving_chest) { openChest(it.loc) }
        onOpLoc1(cox_locs.trough_empty) { deposit() }
        onOpLoc1(cox_locs.trough_full) { deposit() }
        onOpLoc1(cox_locs.creature_keeper) {
            mes("The creature keeper's notes: feed the scavenger beast to put it to sleep.")
        }

        onOpLoc1(cox_locs.tightrope) { cross(it.loc) }
        onOpLoc1(cox_locs.keystone) { takeKeystone(it.loc) }
        onOpLoc1(cox_locs.tightrope_barrier) { dispel() }

        for (crab in
            listOf(
                cox_npcs.crab_grey,
                cox_npcs.crab_red,
                cox_npcs.crab_green,
                cox_npcs.crab_blue,
            )) {
            onOpNpc3(crab) { smash(it.npc) }
        }

        coxMonsterDeaths(
            rooms,
            npcRepo,
            mapOf(
                cox_npcs.ice_demon to cox_seqs.demon_death,
                cox_npcs.icefiend to null,
                cox_npcs.tightrope_mage to null,
                cox_npcs.tightrope_ranger to null,
            ),
        )
    }

    private fun <T : CoxRoomRuntime> ProtectedAccess.room(type: Class<T>): T? {
        val raid = raids.raidOf(player) ?: return null
        val room = raid.roomAt(player.coords) ?: return null
        return rooms.runtime(raid, room)?.let { if (type.isInstance(it)) type.cast(it) else null }
    }

    // Ice Demon
    // ---------------------------------------------------------------------------------------------

    private fun ProtectedAccess.brazier(loc: BoundLocInfo) {
        val room = room(CoxIceDemon::class.java) ?: return
        if (cox_objs.tinderbox !in inv || cox_objs.kindling !in inv) {
            mes("You need a tinderbox and some suitable kindling to light the brazier.")
            return
        }
        val held = inv.count(objTypes[cox_objs.kindling])
        anim(cox_seqs.light_brazier)
        invDel(inv, cox_objs.kindling, held)
        statAdvance(stats.firemaking, 5.0 * held * xpMods.get(player, stats.firemaking))
        room.fuel(player, loc.coords, held)
    }

    // Thieving
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.openChest(loc: BoundLocInfo) {
        val room = room(CoxThieving::class.java) ?: return
        anim(cox_seqs.open_chest)
        delay(1)
        room.open(player, loc.coords)
    }

    private fun ProtectedAccess.deposit() {
        val room = room(CoxThieving::class.java) ?: return
        val grubs = inv.count(objTypes[cox_objs.grubs])
        if (grubs == 0) {
            mes("You have no grubs to put in the trough.")
            return
        }
        invDel(inv, cox_objs.grubs, grubs)
        room.deposit(player, grubs)
    }

    // Tightrope
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        val room = room(CoxTightrope::class.java) ?: return
        val dest = room.otherEnd(loc.coords) ?: return
        val needed = room.agilityNeeded()
        if (player.stat(stats.agility) < needed) {
            mes("You need an Agility level of $needed to cross the tightrope.")
            return
        }
        delay(1)
        val steps = maxOf(kotlin.math.abs(dest.x - coords.x), kotlin.math.abs(dest.z - coords.z))
        PathingEntityCommon.exactMove(
            player,
            coords,
            dest,
            0,
            steps * 30,
            faceAngle(coords, dest),
            room.collision(),
        )
        delay(steps.coerceAtLeast(1))
        statAdvance(stats.agility, 7.5 * steps * xpMods.get(player, stats.agility))
    }

    private fun faceAngle(from: CoordGrid, to: CoordGrid): Int {
        val dx = to.x - from.x
        val dz = to.z - from.z
        return when {
            kotlin.math.abs(dx) >= kotlin.math.abs(dz) && dx > 0 -> 1536
            kotlin.math.abs(dx) >= kotlin.math.abs(dz) -> 512
            dz > 0 -> 1024
            else -> 0
        }
    }

    private fun ProtectedAccess.takeKeystone(loc: BoundLocInfo) {
        val room = room(CoxTightrope::class.java) ?: return
        if (cox_objs.keystone in inv) {
            mes("You already have the keystone crystal.")
            return
        }
        anim(cox_seqs.pickup_table)
        invAdd(inv, cox_objs.keystone, 1)
        room.keystoneTaken(player, loc.coords)
    }

    private fun ProtectedAccess.dispel() {
        val room = room(CoxTightrope::class.java) ?: return
        if (cox_objs.keystone !in inv) {
            mes("The barrier won't budge. Perhaps something could dispel it.")
            return
        }
        invDel(inv, cox_objs.keystone, 1)
        room.dispel(player)
    }

    // Crabs
    // ---------------------------------------------------------------------------------------------

    private suspend fun ProtectedAccess.smash(npc: Npc) {
        val room = room(CoxCrabs::class.java) ?: return
        if (cox_objs.hammer !in inv && cox_objs.hammer !in worn) {
            mes("You need a hammer to smash the crab.")
            return
        }
        anim(cox_seqs.crab_smash)
        delay(1)
        room.smashed(npc)
    }
}

/**
 * The Ice Demon (Wiki): it waits frozen in a storm until its four braziers thaw it. Lighting takes
 * a tinderbox and kindling (all carried goes in); each lit brazier thaws the storm and burns a
 * kindling every 8 ticks; unlit, the storm grows back (TSPS's fit of its captures: 0.15% + 0.02% a
 * kindling a tick per brazier, 0.17% regrowth). Points: 32 a lighting plus 36 a kindling. Once
 * thawed it walks out and fights: every 3 ticks a boulder (ranged, 30) or an ice burst (magic, 25,
 * frozen 3 ticks), whichever its target doesn't pray against. It takes a third of most damage, fire
 * spells 250% of that, demonbane in full. Its death drops 7 stinkhorn, 5 cicely and 7 juice for
 * everyone (Wiki).
 */
class CoxIceDemon(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private lateinit var demon: Npc
    private var storm = STORM_MAX
    private val braziers = HashMap<CoordGrid, Int>()
    private var thawed = false

    override fun build() {
        // The frozen demon sits in the corridor to the way on; the passage stays shut until it
        // dies (CoxRaids.passage checks the room).
        demon = spawn(cox_npcs.ice_demon_frozen, pick3(DEMON_TILES))
    }

    fun fuel(player: Player, at: CoordGrid, kindling: Int) {
        if (thawed || kindling <= 0) return
        val first = at !in braziers
        braziers.merge(at, kindling, Int::plus)
        if (first) {
            findLoc(at, listOf(cox_locs.brazier_unlit))?.let { swapLoc(it, cox_locs.brazier_lit) }
        }
        env.raids.addPoints(
            raid,
            player,
            (if (first) LIGHT_POINTS else 0) + KINDLING_POINTS * kindling,
        )
        start()
    }

    override fun onTick() {
        if (thawed) {
            driveDemon()
            return
        }
        var thaw = 0
        for ((tile, kindling) in braziers.toList()) {
            if (kindling <= 0) continue
            thaw += THAW_BASE + THAW_PER_KINDLING * kindling
            if ((now - startCycle) % BURN_TICKS == 0) {
                val left = kindling - 1
                braziers[tile] = left
                if (left <= 0) {
                    findLoc(tile, listOf(cox_locs.brazier_lit))?.let {
                        swapLoc(it, cox_locs.brazier_unlit)
                    }
                    braziers.remove(tile)
                }
            }
        }
        storm = if (thaw == 0) minOf(STORM_MAX, storm + REGROW) else storm - thaw
        if (storm <= 0) thaw()
    }

    private fun thaw() {
        thawed = true
        for (tile in braziers.keys.toList()) {
            findLoc(tile, listOf(cox_locs.brazier_lit))?.let { swapLoc(it, cox_locs.brazier_unlit) }
        }
        braziers.clear()
        transform(demon, cox_npcs.ice_demon)
        restat(demon, CoxScaling.Bases.ice_demon)
        val scaled = CoxScaling.scale(CoxScaling.Bases.ice_demon, scaling)
        demon.baseHitpointsLvl = scaled.hitpoints
        demon.hitpoints = scaled.hitpoints
        nextAttack = now + HOSTILE_AFTER
        for (player in roomPlayers()) player.mes("The Ice Demon breaks free of its icy prison!")
    }

    private var nextAttack = 0

    private fun driveDemon() {
        if (!demon.isSlotAssigned || demon.hitpoints <= 0 || now < nextAttack) return
        val target =
            fightPlayers()
                .ifEmpty {
                    return
                }
                .let(::pick)
        nextAttack = now + ATTACK_SPEED
        demon.facePlayer(target)
        val ranged =
            when {
                prays(target, NpcAttackStyle.Ranged) -> false
                prays(target, NpcAttackStyle.Magic) -> true
                else -> chance(2)
            }
        if (ranged) {
            strike(
                demon,
                target,
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = cox_seqs.demon_cast,
                    projectile = cox_spots.iceball_travel,
                    projanim = ToaProjAnims.lob,
                    impactSpot = cox_spots.iceball_hit,
                    maxHit = scaledMaxHit(CoxScaling.Bases.ice_demon, 30),
                ),
            )
        } else {
            strike(
                demon,
                target,
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = cox_seqs.demon_cast,
                    projectile = cox_spots.ice_travel,
                    projanim = ToaProjAnims.orb,
                    impactSpot = cox_spots.ice_impact,
                    maxHit = scaledMaxHit(CoxScaling.Bases.ice_demon, 25),
                ),
            ) { victim, hit ->
                if (hit.damage > 0) {
                    victim.mes("<col=ef1020>You have been frozen!</col>")
                    victim.abortRoute()
                    victim.delay = now + FREEZE_TICKS
                }
            }
        }
    }

    override fun modifyHit(npc: Npc, hit: HitBuilder, source: Player?) {
        if (npc !== demon) return
        if (!thawed) {
            hit.damage = 0
            return
        }
        // A third of most damage (Wiki); fire spells do 250% of that third.
        val fire = source?.let { CoxCombat.isFireSpell(it) } == true
        hit.damage = if (fire) hit.damage * 250 / 300 else hit.damage / 3
    }

    override fun monsterDied(npc: Npc) {
        if (npc !== demon) return
        for (player in roomPlayers()) {
            val at = player.coords
            drop(cox_objs.stinkhorn, at, 7)
            drop(cox_objs.cicely, at, 5)
            drop(cox_objs.endarkened_juice, at, 7)
        }
        complete()
    }

    companion object {
        const val STORM_MAX = 10000
        const val THAW_BASE = 15
        const val THAW_PER_KINDLING = 2
        const val REGROW = 17
        const val BURN_TICKS = 8
        const val LIGHT_POINTS = 32
        const val KINDLING_POINTS = 36
        const val ATTACK_SPEED = 3
        const val HOSTILE_AFTER = 8
        const val FREEZE_TICKS = 3

        val DEMON_TILES =
            listOf(CoordGrid(3271, 5365, 0), CoordGrid(3310, 5368, 0), CoordGrid(3349, 5362, 0))
    }
}

/**
 * Thieving (Wiki): chests open 39%-61% of the time by Thieving level (+21 with lockpicks) and give
 * cavern grubs (0 to level / 25, at least 1, 2 at 95); some hold bats that bite for 5-25 instead.
 * The scavenger beast eats grubs from its trough; once fed 16 grubs a raider (30 solo) it falls
 * asleep and the way on opens. 115 points a grub, up to max(4500, total Thieving / 6 x 150). A
 * chest locks again 12 ticks after opening (TSPS's capture).
 */
class CoxThieving(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private val locked = HashMap<CoordGrid, Int>()
    private var fed = 0
    private var points = 0
    private var beast: Npc? = null

    override fun build() {
        beast = spawn(cox_npcs.thieving_beast, pick3(BEAST_TILES))
    }

    private val needed: Int
        get() = if (partySize <= 1) 30 else 16 * partySize

    fun open(player: Player, at: CoordGrid) {
        if (completed) {
            player.mes("The beast is asleep; there's no need to open more chests.")
            return
        }
        if ((locked[at] ?: 0) > now) {
            player.mes("This chest has already been opened.")
            return
        }
        val thieving = player.stat(stats.thieving)
        val lockpick = player.inv.count(env.objTypes[cox_objs.lockpick]) > 0
        val chance = (39 + (thieving * 22) / 99 + if (lockpick) 21 else 0).coerceAtMost(95)
        if (!percent(chance)) {
            player.mes("You fail to pick the lock.")
            return
        }
        locked[at] = now + RELOCK_TICKS
        findLoc(at, listOf(cox_locs.thieving_chest))?.let { loc ->
            val open = swapLoc(loc, cox_locs.thieving_chest_open)
            later(RELOCK_TICKS) { swapLoc(open, cox_locs.thieving_chest) }
        }
        if (chance(BAT_CHEST)) {
            player.mes("A swarm of bats flies out of the chest!")
            hurt(player, roll(5, 25))
            return
        }
        val grubs = (thieving / 25).coerceAtLeast(1) + if (thieving >= 95) 1 else 0
        val carried = player.inv.count(env.objTypes[cox_objs.grubs])
        val take = minOf(grubs, GRUB_CAP - carried)
        if (take <= 0) {
            player.mes("You can't carry any more grubs.")
            return
        }
        player.invAdd(player.inv, env.objTypes[cox_objs.grubs], take)
        player.mes("Some cavern grubs have hatched; you take them.")
    }

    fun deposit(player: Player, grubs: Int) {
        fed += grubs
        val cap = maxOf(4500, raid.raiders.sumOf { it.player.stat(stats.thieving) } / 6 * 150)
        val give = minOf(grubs * POINTS_PER_GRUB, (cap - points).coerceAtLeast(0))
        points += give
        env.raids.addPoints(raid, player, give)
        if (fed >= needed && !completed) {
            beast?.let { b ->
                transform(b, cox_npcs.thieving_beast_sleeping)
                b.say("Yawwwwwn!")
            }
            for (p in roomPlayers()) p.mes("The scavenger beast falls asleep.")
            complete()
        } else {
            player.mes("The beast eats the grubs. (${fed.coerceAtMost(needed)} / $needed)")
        }
    }

    companion object {
        const val RELOCK_TICKS = 12
        const val GRUB_CAP = 28
        const val POINTS_PER_GRUB = 115
        const val BAT_CHEST = 8

        /** The beast sits in the corridor to the way on (TSPS's first tile of five). */
        val BEAST_TILES =
            listOf(CoordGrid(3271, 5390, 0), CoordGrid(3318, 5397, 0), CoordGrid(3350, 5397, 0))
    }
}

/**
 * The tightrope (Wiki): the rope needs Agility of 80%-100% of the party's average level; deathly
 * mages and rangers shoot at whoever is in the room; the keystone at the far end dispels the
 * barrier on the way on and kills the guards. The guards' counts and attacks are TSPS's guesses.
 */
class CoxTightrope(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private var barrier: LocInfo? = null
    private val ends = ArrayList<CoordGrid>()

    fun collision() = env.collision

    override fun build() {
        val b = pick3(BARRIERS)
        barrier = placeLoc(b.first, cox_locs.tightrope_barrier, angle = LocAngle.entries[b.second])
        for (t in pick3(MAGES)) spawnGuard(
            cox_npcs.tightrope_mage,
            t,
            CoxScaling.Bases.deathly_mage,
            magic = true,
        )
        for (t in pick3(RANGERS)) spawnGuard(
            cox_npcs.tightrope_ranger,
            t,
            CoxScaling.Bases.deathly_ranger,
            magic = false,
        )
    }

    private fun spawnGuard(
        type: org.rsmod.game.type.npc.NpcType,
        at: CoordGrid,
        base: CoxBase,
        magic: Boolean,
    ) {
        val npc = spawn(type, at, base)
        val attack =
            if (magic) {
                NpcAttack(
                    NpcAttackStyle.Magic,
                    anim = cox_seqs.mage_cast,
                    castSpot = cox_spots.fire_cast,
                    projectile = cox_spots.fire_travel,
                    projanim = ToaProjAnims.orb,
                    impactSpot = cox_spots.fire_impact,
                    maxHit = scaledMaxHit(base, 22),
                )
            } else {
                NpcAttack(
                    NpcAttackStyle.Ranged,
                    anim = cox_seqs.ranger_shoot,
                    castSpot = cox_spots.arrow_launch,
                    projectile = cox_spots.arrow_travel,
                    projanim = ToaProjAnims.splash,
                    maxHit = scaledMaxHit(base, 30),
                )
            }
        val monster = monster(npc, attack, range = 12, speed = 4)
        monster.stationary = true
    }

    /** The party's average level decides how hard the rope is. */
    fun agilityNeeded(): Int {
        val average =
            raid.raiders.map { it.player.combatLevel }.average().takeIf { !it.isNaN() } ?: 3.0
        val agility =
            raid.raiders.map { it.player.stat(stats.agility) }.average().takeIf { !it.isNaN() }
                ?: 1.0
        return (agility * 0.8).toInt().coerceIn(1, 99).also { average.toInt() }
    }

    fun otherEnd(from: CoordGrid): CoordGrid? {
        if (ends.isEmpty()) {
            // The two rope ends are map locs: find them by searching the room once.
            val sx = checkNotNull(room).type.sourceX(variant)
            val sz = room.type.sourceZ
            for (x in sx until sx + 32) for (z in sz until sz + 32) {
                val t = tile(x, z)
                if (findLoc(t, listOf(cox_locs.tightrope)) != null) ends += t
            }
        }
        val other =
            ends
                .filter { it != from }
                .minByOrNull {
                    (it.x - from.x) * (it.x - from.x) + (it.z - from.z) * (it.z - from.z)
                }
        return other?.let { open(it) }
    }

    fun keystoneTaken(player: Player, at: CoordGrid) {
        env.raids.addPoints(raid, player, KEYSTONE_POINTS)
        for (p in roomPlayers()) p.mes("${player.displayName} has taken the keystone crystal.")
    }

    fun dispel(player: Player) {
        barrier?.let { removeLoc(it) }
        barrier = null
        for (npc in monsters.keys.toList()) despawn(npc)
        for (p in roomPlayers()) p.mes("The barrier dissipates and the guards crumble to dust.")
        complete()
    }

    override fun onIdle() {
        if (raid.started && roomPlayers().isNotEmpty()) start()
    }

    override fun onTick() {
        driveMonsters()
    }

    override fun pointsPerHitpoint(npc: Npc): Int = 0

    companion object {
        const val KEYSTONE_POINTS = 500
        val MAGES =
            listOf(
                listOf(CoordGrid(3285, 5363, 0), CoordGrid(3287, 5363, 0)),
                listOf(CoordGrid(3317, 5363, 0), CoordGrid(3319, 5363, 0)),
                listOf(CoordGrid(3340, 5365, 0), CoordGrid(3340, 5367, 0)),
            )
        val RANGERS =
            listOf(
                listOf(CoordGrid(3284, 5370, 0), CoordGrid(3286, 5370, 0)),
                listOf(CoordGrid(3316, 5370, 0), CoordGrid(3318, 5370, 0)),
                listOf(CoordGrid(3333, 5364, 0), CoordGrid(3333, 5366, 0)),
            )

        /** The barrier's tile and angle (template), by version. */
        val BARRIERS =
            listOf(
                CoordGrid(3270, 5360, 0) to 0,
                CoordGrid(3309, 5368, 0) to 1,
                CoordGrid(3355, 5360, 0) to 2,
            )
    }
}

/**
 * The crab puzzle, simplified (the Wiki gives no movement rules): every 5 ticks an energy orb
 * leaves the focus and travels a tile a tick, turning off walls; meeting a crab it takes the crab's
 * colour; a raider in its way turns it clockwise. An orb reaching an unlit small crystal in that
 * crystal's colour lights it (400 points to the room, TSPS's capture); with all four lit the dark
 * crystal shatters and the way on opens. Smashing a crab with a hammer changes its colour (white,
 * red, green, blue) and stuns it 12 ticks.
 */
class CoxCrabs(env: CoxRoomEnv, raid: CoxRaid, room: CoxPlacement) :
    CoxRoomRuntime(env, raid, room) {
    private enum class Colour {
        White,
        Red,
        Green,
        Blue,
    }

    private val crabs = LinkedHashMap<Npc, Colour>()
    private val crystals = ArrayList<Pair<CoordGrid, Colour>>()
    private val lit = HashSet<CoordGrid>()
    private var orb: Npc? = null
    private var orbColour = Colour.White
    private var dir = 0
    private var orbSteps = 0
    private var nextOrb = 0

    private val crabTypes =
        mapOf(
            Colour.White to cox_npcs.crab_grey,
            Colour.Red to cox_npcs.crab_red,
            Colour.Green to cox_npcs.crab_green,
            Colour.Blue to cox_npcs.crab_blue,
        )
    private val orbTypes =
        mapOf(
            Colour.White to cox_npcs.crab_orb_white,
            Colour.Red to cox_npcs.crab_orb_red,
            Colour.Green to cox_npcs.crab_orb_green,
            Colour.Blue to cox_npcs.crab_orb_blue,
        )
    private val crystalLocs =
        mapOf(
            Colour.White to cox_locs.crab_small_white,
            Colour.Red to cox_locs.crab_small_magenta,
            Colour.Green to cox_locs.crab_small_yellow,
            Colour.Blue to cox_locs.crab_small_cyan,
        )

    override fun build() {
        val colours = shuffled(Colour.entries)
        pick3(CRABS).forEachIndexed { i, t ->
            val c = colours[i % colours.size]
            crabs[spawn(crabTypes.getValue(c), t, passThrough = true)] = c
        }
        val needs = listOf(Colour.White, Colour.Blue, Colour.Green, Colour.Red)
        pick3(SMALL_CRYSTALS).forEachIndexed { i, t ->
            val c = needs[i]
            crystals += tile(t) to c
            placeLoc(t, crystalLocs.getValue(c))
        }
    }

    override fun onIdle() {
        if (raid.started && roomPlayers().isNotEmpty()) start()
    }

    override fun onTick() {
        val o = orb
        if (o == null || !o.isSlotAssigned) {
            if (now >= nextOrb) launch()
            return
        }
        step(o)
    }

    private fun launch() {
        orbColour = Colour.White
        dir = 0
        orbSteps = 0
        orb = spawnAt(orbTypes.getValue(Colour.White), tile(pick3(FOCUS)), ignoreCollision = true)
    }

    private fun step(o: Npc) {
        orbSteps++
        if (orbSteps > ORB_STEPS) {
            fizzle(o)
            return
        }
        val (dx, dz) = DIRS[dir]
        val next = o.coords.translate(dx, dz)
        // A raider standing in the way turns the orb clockwise.
        if (roomPlayers().any { it.coords == next }) {
            dir = (dir + 1) % 4
            return
        }
        val crystal = crystals.firstOrNull { it.first == next && it.first !in lit }
        if (crystal != null) {
            if (crystal.second == orbColour) light(crystal.first) else fizzle(o)
            if (crystal.second == orbColour) despawnOrb(o)
            return
        }
        if (!walkable(next) || !inRoom(next)) {
            dir = (dir + if (chance(2)) 1 else 3) % 4
            return
        }
        o.teleport(next)
        val crab = crabs.entries.firstOrNull { it.key.coords == next }
        if (crab != null && orbColour != crab.value) {
            orbColour = crab.value
            transform(o, orbTypes.getValue(orbColour))
        }
    }

    private fun Npc.teleport(to: CoordGrid) {
        PathingEntityCommon.telejump(this, env.collision, to)
    }

    private fun light(at: CoordGrid) {
        lit += at
        findLoc(at, crystalLocs.values.toList())?.let { swapLoc(it, cox_locs.crab_small_white) }
        for (p in roomPlayers()) env.raids.addPoints(
            raid,
            p,
            CRYSTAL_POINTS / roomPlayers().size.coerceAtLeast(1),
        )
        if (lit.size >= crystals.size) {
            graphicAt(cox_spots.crab_shatter, tile(pick3(DARK_CRYSTALS)))
            orb?.let(::despawnOrb)
            for (npc in crabs.keys.toList()) despawn(npc)
            complete()
        }
    }

    private fun fizzle(o: Npc) {
        graphicAt(cox_spots.wind_impact, o.coords)
        despawnOrb(o)
    }

    private fun despawnOrb(o: Npc) {
        despawn(o)
        orb = null
        nextOrb = now + ORB_DELAY
    }

    fun smashed(npc: Npc) {
        val colour = crabs[npc] ?: return
        val next = Colour.entries[(colour.ordinal + 1) % Colour.entries.size]
        crabs[npc] = next
        transform(npc, crabTypes.getValue(next))
        graphicAt(cox_spots.stunned, npc.coords)
    }

    companion object {
        const val ORB_STEPS = 40
        const val ORB_DELAY = 5
        const val CRYSTAL_POINTS = 400
        val DIRS = listOf(1 to 0, 0 to -1, -1 to 0, 0 to 1)
        val FOCUS =
            listOf(CoordGrid(3277, 5353, 0), CoordGrid(3318, 5363, 0), CoordGrid(3346, 5358, 0))
        val CRABS =
            listOf(
                listOf(
                    CoordGrid(3286, 5354, 0),
                    CoordGrid(3280, 5358, 0),
                    CoordGrid(3281, 5365, 0),
                    CoordGrid(3287, 5361, 0),
                ),
                listOf(
                    CoordGrid(3306, 5358, 0),
                    CoordGrid(3311, 5353, 0),
                    CoordGrid(3313, 5361, 0),
                    CoordGrid(3318, 5356, 0),
                ),
                listOf(
                    CoordGrid(3337, 5356, 0),
                    CoordGrid(3335, 5362, 0),
                    CoordGrid(3341, 5366, 0),
                    CoordGrid(3344, 5361, 0),
                ),
            )
        val DARK_CRYSTALS =
            listOf(CoordGrid(3272, 5365, 0), CoordGrid(3308, 5366, 0), CoordGrid(3349, 5366, 0))
        val SMALL_CRYSTALS =
            listOf(
                listOf(
                    CoordGrid(3283, 5356, 0),
                    CoordGrid(3279, 5366, 0),
                    CoordGrid(3282, 5362, 0),
                    CoordGrid(3287, 5359, 0),
                ),
                listOf(
                    CoordGrid(3311, 5356, 0),
                    CoordGrid(3320, 5360, 0),
                    CoordGrid(3306, 5361, 0),
                    CoordGrid(3305, 5356, 0),
                ),
                listOf(
                    CoordGrid(3341, 5355, 0),
                    CoordGrid(3337, 5354, 0),
                    CoordGrid(3340, 5363, 0),
                    CoordGrid(3340, 5367, 0),
                ),
            )
    }
}
