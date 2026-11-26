package org.rsmod.content.skills.hunter.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.events.SkillingPet
import org.rsmod.api.player.events.SkillingPetRoll
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hunterLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.hunter.configs.Creature
import org.rsmod.content.skills.hunter.configs.HunterCreatures
import org.rsmod.content.skills.hunter.configs.HunterLoot
import org.rsmod.content.skills.hunter.configs.HunterLoot.Item
import org.rsmod.content.skills.hunter.configs.TrapKind
import org.rsmod.content.skills.hunter.configs.TrapLocs
import org.rsmod.content.skills.hunter.configs.hunter_locs
import org.rsmod.content.skills.hunter.configs.hunter_objs
import org.rsmod.content.skills.hunter.configs.hunter_seqs
import org.rsmod.content.skills.hunter.configs.hunter_timers
import org.rsmod.events.EventBus
import org.rsmod.events.UnboundEvent
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.hunt.HuntVis
import org.rsmod.game.type.loc.LocType
import org.rsmod.game.type.obj.ObjType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import org.rsmod.routefinder.loc.LocLayerConstants

// TODO:
// - the Wilderness trap that is allowed, and the trap limit that goes up there
// - the monkey trap and rabbit snare, which have no creatures on the isle
// - pets (baby chinchompa)
//
// Every trap is owned by the player who laid it, lives in a loc (or two) that changes with its
// state, and is run from a timer on its owner: it looks for a creature of its kind close by every
// few ticks, calls it over, and rolls the creature's chance when it gets there. The catch stays in
// the trap until the owner checks it (that is when the experience comes); a trap nobody visits
// collapses after [LIFETIME] ticks. The Wiki's numbers are in [HunterCreatures].
class HunterTraps
@Inject
constructor(
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val objRepo: ObjRepository,
    private val search: NpcSearch,
    private val xpMods: XpModifiers,
    private val mapClock: MapClock,
    private val collision: CollisionFlagMap,
    private val random: GameRandom,
    private val eventBus: EventBus,
) : PluginScript() {
    private enum class Phase {
        Idle,
        Luring,
        Catching,
        Caught,
        Failing,
        Failed,
    }

    private class Trap(
        val owner: Player,
        val kind: TrapKind,
        val locs: TrapLocs,
        val coords: CoordGrid,
        val angle: LocAngle,
        /** The lying-about loc this trap was set on, put back when it ends. */
        val base: LocInfo?,
        /** What dismantling the trap gives back. */
        val refund: List<Item>,
    ) {
        var phase = Phase.Idle
        var expires = 0
        var nextScan = 0
        var transitionEnds = 0
        var luredAt = 0
        var bait: ObjType? = null
        var smoked = false
        var prey: Npc? = null
        var creature: Creature? = null
        var succeeded = false
        val placed = ArrayList<LocInfo>()
        val covers = HashSet<CoordGrid>()
    }

    /** Every trap, by each tile it covers. */
    private val index = HashMap<CoordGrid, Trap>()

    private val owned = WeakHashMap<Player, MutableList<Trap>>()

    /** The creatures on their way to a trap. */
    private val reserved = HashMap<Npc, Trap>()

    override fun ScriptContext.startup() {
        onPlayerSoftTimer(hunter_timers.tick) {
            val busy = tick(player)
            val event = Tick(player)
            eventBus.publish(event)
            if (!busy && !event.keep) {
                player.clearSoftTimer(hunter_timers.tick)
            }
        }
        onPlayerLogout { dropTraps(player) }

        onOpHeld1(hunter_objs.bird_snare) { layOnFloor(TrapKind.Bird, hunter_objs.bird_snare) }
        onOpHeld1(hunter_objs.box_trap) { layOnFloor(TrapKind.Box, hunter_objs.box_trap) }
        onOpHeld1(hunter_objs.magic_box) { layOnFloor(TrapKind.MagicBox, hunter_objs.magic_box) }

        onOpLoc1(hunter_locs.boulder) { layDeadfall(it.loc) }
        for (tree in TREES) {
            onOpLoc1(tree.base) { layNet(it.loc, tree.locs) }
        }

        for (loc in idleLocs()) {
            onOpLoc1(loc) { dismantle(it.loc.coords) }
            onOpLoc2(loc) { investigate(it.loc.coords) }
        }
        for (loc in fullLocs()) {
            onOpLoc1(loc) { check(it.loc.coords, reset = false) }
            onOpLoc2(loc) { check(it.loc.coords, reset = true) }
        }
        for (loc in failedLocs()) {
            onOpLoc1(loc) { dismantle(it.loc.coords) }
            onOpLoc2(loc) { reset(it.loc.coords) }
        }

        for ((loc, bait) in baitPairs()) {
            onOpLocU(loc, bait) { addBait(it.loc.coords, bait) }
        }
        for (loc in idleLocs()) {
            onOpLocU(loc, hunter_objs.torch_lit) { smoke(it.loc.coords) }
        }
    }

    // region laying

    private suspend fun ProtectedAccess.layOnFloor(kind: TrapKind, item: ObjType) {
        if (!canLay(kind, coords)) {
            return
        }
        val locs = HunterCreatures.locsOf(kind)
        anim(if (kind == TrapKind.MagicBox) hunter_seqs.lay_small else hunter_seqs.lay_trap)
        delay(LAY_TICKS)
        if (!canLay(kind, coords) || !invDel(inv, item, 1).success) {
            return
        }
        val spot = coords
        val trap = Trap(player, kind, locs, spot, LocAngle.West, null, listOf(Item(item)))
        start(trap)
        mes("You set up the ${kind.label}.")
        stepAside(spot)
    }

    private suspend fun ProtectedAccess.layDeadfall(boulder: BoundLocInfo) {
        if (!canLay(TrapKind.Deadfall, boulder.coords, onBase = true)) {
            return
        }
        val log = logsInInventory()
        if (!hasKnife() || log == null) {
            mes("You need a knife and logs to build this trap.")
            return
        }
        anim(hunter_seqs.lay_small)
        delay(LAY_TICKS)
        val still = canLay(TrapKind.Deadfall, boulder.coords, onBase = true)
        val log2 = logsInInventory()
        if (!still || !hasKnife() || log2 == null || !invDel(inv, log2, 1).success) {
            return
        }
        val locs = HunterCreatures.locsOf(TrapKind.Deadfall)
        val trap =
            Trap(
                player,
                TrapKind.Deadfall,
                locs,
                boulder.coords,
                boulder.angle,
                boulder.toInfo(),
                emptyList(),
            )
        start(trap)
        mes("You set up the deadfall.")
    }

    private suspend fun ProtectedAccess.layNet(tree: BoundLocInfo, locs: TrapLocs) {
        val creature = HunterCreatures.all.first { it.locs === locs }
        if (!canLay(TrapKind.Net, tree.coords, onBase = true, level = creature.level)) {
            return
        }
        if (invTotal(inv, hunter_objs.rope) < 1 || invTotal(inv, hunter_objs.small_net) < 1) {
            mes("You need a rope and a small fishing net to set this trap.")
            return
        }
        anim(hunter_seqs.lay_sapling)
        delay(LAY_TICKS)
        val ready =
            canLay(TrapKind.Net, tree.coords, onBase = true, level = creature.level) &&
                invTotal(inv, hunter_objs.rope) >= 1 &&
                invTotal(inv, hunter_objs.small_net) >= 1
        if (!ready) {
            return
        }
        invDel(inv, hunter_objs.rope, 1, hunter_objs.small_net, 1)
        val refund = listOf(Item(hunter_objs.rope), Item(hunter_objs.small_net))
        val trap = Trap(player, TrapKind.Net, locs, tree.coords, tree.angle, tree.toInfo(), refund)
        start(trap)
        mes("You set up the net trap.")
    }

    private fun ProtectedAccess.canLay(
        kind: TrapKind,
        at: CoordGrid,
        onBase: Boolean = false,
        level: Int = LAY_LEVEL.getValue(kind),
    ): Boolean {
        if (player.hunterLvl < level) {
            mes("You need a Hunter level of $level to set a ${kind.label}.")
            return false
        }
        val mine = owned[player].orEmpty()
        val same = mine.count { it.kind == kind }
        val limit = if (kind == TrapKind.Deadfall) DEADFALL_LIMIT else trapLimit(player.hunterLvl)
        val others = mine.count { it.kind != TrapKind.Deadfall }
        val used = if (kind == TrapKind.Deadfall) same else others
        if (used >= limit) {
            mes("You have already laid the maximum number of traps for your Hunter level.")
            return false
        }
        if (index.containsKey(at) || (!onBase && !isFree(at))) {
            mes("You can't lay a trap here.")
            return false
        }
        return true
    }

    private fun isFree(at: CoordGrid): Boolean {
        if ((collision[at.x, at.z, at.level] and BLOCKED) != 0) {
            return false
        }
        return locRepo.findAll(at).none { it.layer == LocLayerConstants.GROUND }
    }

    private fun ProtectedAccess.stepAside(from: CoordGrid) {
        for ((dx, dz) in SIDESTEPS) {
            val next = from.translate(dx, dz)
            if (!mapBlocked(next) && !index.containsKey(next)) {
                walk(next)
                return
            }
        }
    }

    private fun ProtectedAccess.logsInInventory(): ObjType? =
        LOGS.firstOrNull { invTotal(inv, it) > 0 }

    private fun ProtectedAccess.hasKnife(): Boolean = invTotal(inv, hunter_objs.knife) > 0

    // endregion

    // region trap state

    private fun start(trap: Trap) {
        val now = mapClock.cycle
        trap.expires = now + LIFETIME
        trap.nextScan = now + SCAN_TICKS
        trap.base?.let { locRepo.del(it, Int.MAX_VALUE) }
        showIdle(trap)
        owned.getOrPut(trap.owner) { ArrayList() } += trap
        trap.owner.softTimer(hunter_timers.tick, 1)
    }

    /** Shows a loc of the trap's, taking away what showed before. */
    private fun show(trap: Trap, vararg shown: Pair<LocType, CoordGrid>) {
        clearPlaced(trap)
        for ((type, at) in shown) {
            val angle = trap.angle
            trap.placed += locRepo.add(at, type, Int.MAX_VALUE, angle, LocShape.CentrepieceStraight)
        }
        for (coords in coverOf(trap)) {
            trap.covers += coords
            index[coords] = trap
        }
    }

    private fun coverOf(trap: Trap): List<CoordGrid> {
        val cover = arrayListOf(trap.coords)
        if (trap.kind == TrapKind.Net) {
            cover += trap.coords.translate(NET_OFFSET.getValue(trap.angle))
        }
        if (trap.kind == TrapKind.Deadfall) {
            // A deadfall is two tiles long, along the way it faces.
            val turned = trap.angle == LocAngle.North || trap.angle == LocAngle.South
            cover += if (turned) trap.coords.translate(0, 1) else trap.coords.translate(1, 0)
        }
        return cover
    }

    private fun CoordGrid.translate(offset: Pair<Int, Int>): CoordGrid =
        translate(offset.first, offset.second)

    private fun clearPlaced(trap: Trap) {
        for (info in trap.placed) {
            locRepo.del(info, Int.MAX_VALUE)
        }
        trap.placed.clear()
    }

    private fun showIdle(trap: Trap) {
        val idle = trap.locs.idle
        val beside = trap.locs.beside
        if (beside != null) {
            show(
                trap,
                idle to trap.coords,
                beside to trap.coords.translate(NET_OFFSET.getValue(trap.angle)),
            )
        } else {
            show(trap, idle to trap.coords)
        }
    }

    /** Shows [type], which for a net trap is the two tile loc that replaces tree and net. */
    private fun showWhole(trap: Trap, type: LocType) {
        val anchor =
            if (trap.kind == TrapKind.Net) {
                when (trap.angle) {
                    LocAngle.East -> trap.coords.translate(0, -1)
                    LocAngle.South -> trap.coords.translate(-1, 0)
                    LocAngle.West,
                    LocAngle.North -> trap.coords
                }
            } else {
                trap.coords
            }
        show(trap, type to anchor)
    }

    /** Takes the trap away, puts back what it was set on, and drops [items] for its owner. */
    private fun end(trap: Trap, items: List<Item> = emptyList()) {
        release(trap)
        clearPlaced(trap)
        for (coords in trap.covers) {
            index.remove(coords)
        }
        trap.covers.clear()
        trap.base?.let { locRepo.add(it, Int.MAX_VALUE) }
        owned[trap.owner]?.remove(trap)
        for (item in items) {
            objRepo.add(item.obj, trap.coords, DROP_TICKS, trap.owner, item.count)
        }
    }

    private fun release(trap: Trap) {
        val prey = trap.prey ?: return
        trap.prey = null
        if (reserved[prey] === trap) {
            reserved.remove(prey)
            if (!prey.isInvisible) {
                prey.defaultMode()
            }
        }
    }

    private fun dropTraps(player: Player) {
        for (trap in owned[player].orEmpty().toList()) {
            end(trap, trap.refund)
        }
    }

    // endregion

    // region ticking

    /** Runs the owner's traps for one tick. Returns whether any are left. */
    private fun tick(player: Player): Boolean {
        val traps = owned[player] ?: return false
        for (trap in traps.toList()) {
            process(trap)
        }
        return traps.isNotEmpty()
    }

    private fun process(trap: Trap) {
        val now = mapClock.cycle
        when (trap.phase) {
            Phase.Idle -> {
                if (now >= trap.expires) {
                    collapse(trap)
                } else if (now >= trap.nextScan) {
                    scan(trap, now)
                }
            }
            Phase.Luring -> lure(trap, now)
            Phase.Catching -> {
                if (now >= trap.transitionEnds) {
                    trap.phase = Phase.Caught
                    trap.expires = now + LIFETIME
                    showWhole(trap, trap.creature!!.full!!)
                    trap.owner.mes("Something has been caught in your trap!")
                }
            }
            Phase.Failing -> {
                if (now >= trap.transitionEnds) {
                    fail(trap, now)
                }
            }
            Phase.Caught,
            Phase.Failed -> {
                if (now >= trap.expires) {
                    collapse(trap)
                }
            }
        }
    }

    private fun collapse(trap: Trap) {
        val caught = trap.phase == Phase.Caught
        trap.owner.mes("Your trap has collapsed.")
        end(trap, if (caught && trap.kind == TrapKind.MagicBox) emptyList() else trap.refund)
    }

    private fun scan(trap: Trap, now: Int) {
        val mods = Modifiers(trap.owner, trap.kind)
        eventBus.publish(mods)
        trap.nextScan = now + mods.scanTicks

        val range = if (trap.kind == TrapKind.Bird || trap.kind == TrapKind.MagicBox) 2 else 3
        val level = trap.owner.hunterLvl
        val prey =
            search.findAllAny(trap.coords, range, HuntVis.Off).firstOrNull { npc ->
                eligible(trap, npc, level)
            }
        if (prey == null) {
            return
        }
        reserved[prey] = trap
        trap.prey = prey
        trap.phase = Phase.Luring
        trap.luredAt = now
        prey.noneMode()
        prey.walk(trap.coords)
    }

    private fun eligible(trap: Trap, npc: Npc, level: Int): Boolean {
        if (npc.isInvisible || reserved.containsKey(npc)) {
            return false
        }
        val creature = HunterCreatures.forNpcName(npc.type.name) ?: return false
        if (creature.kind != trap.kind || level < creature.level) {
            return false
        }
        if (trap.kind == TrapKind.Net && creature.locs !== trap.locs) {
            return false
        }
        return trap.owner.coords != trap.coords
    }

    private fun lure(trap: Trap, now: Int) {
        val prey = trap.prey
        if (prey == null || prey.isInvisible || now - trap.luredAt > LURE_TICKS) {
            release(trap)
            trap.phase = Phase.Idle
            return
        }
        if (distance(prey.coords, trap.coords) > 1) {
            return
        }
        resolve(trap, prey, now)
    }

    private fun resolve(trap: Trap, prey: Npc, now: Int) {
        val creature = HunterCreatures.forNpcName(prey.type.name)
        release(trap)
        if (creature == null) {
            trap.phase = Phase.Idle
            return
        }
        val owner = trap.owner
        val level = owner.hunterLvl
        val bonus =
            (if (baitMatches(trap, creature)) BAIT_BONUS else 0.0) +
                (if (trap.smoked) SMOKE_BONUS else 0.0)
        val roll = Roll(owner, creature)
        roll.success = random.randomDouble() < min(1.0, creature.chance(level) + bonus)
        eventBus.publish(roll)

        val mods = Modifiers(owner, trap.kind)
        eventBus.publish(mods)
        trap.transitionEnds = now + mods.settleTicks
        if (roll.success) {
            trap.creature = creature
            trap.bait = null
            trap.phase = Phase.Catching
            npcRepo.despawn(prey, npcRepo.respawnSpeed.scale(RESPAWN_TICKS))
            val catching = creature.catching
            showWhole(trap, catching[catchingIndex(trap, prey, catching.size)])
        } else {
            trap.phase = Phase.Failing
            val failing = trap.locs.failing
            if (failing != null) {
                if (trap.kind == TrapKind.Net) showWhole(trap, failing)
                else show(trap, failing to trap.coords)
            }
        }
    }

    private fun fail(trap: Trap, now: Int) {
        val failed = trap.locs.failed
        if (failed == null) {
            // A deadfall that fails is simply gone: the boulder is back and the logs are lost.
            trap.owner.mes("Your trap has fallen.")
            end(trap)
            return
        }
        trap.phase = Phase.Failed
        trap.expires = now + LIFETIME
        if (trap.kind == TrapKind.Net) showWhole(trap, failed)
        else show(trap, failed to trap.coords)
    }

    private fun baitMatches(trap: Trap, creature: Creature): Boolean =
        trap.bait != null && creature.baits.any { it.id == trap.bait?.id }

    /** Which of a box trap's four catching locs shows, from the side the creature came from. */
    private fun catchingIndex(trap: Trap, prey: Npc, size: Int): Int {
        if (size < 4) {
            return 0
        }
        val dx = prey.coords.x - trap.coords.x
        val dz = prey.coords.z - trap.coords.z
        return when {
            abs(dz) >= abs(dx) && dz >= 0 -> 0
            abs(dx) > abs(dz) && dx > 0 -> 1
            abs(dz) >= abs(dx) -> 2
            else -> 3
        }
    }

    private fun distance(a: CoordGrid, b: CoordGrid): Int = max(abs(a.x - b.x), abs(a.z - b.z))

    // endregion

    // region checking

    private suspend fun ProtectedAccess.ownTrap(at: CoordGrid): Trap? {
        val trap = index[at] ?: return null
        if (trap.owner !== player) {
            mes("This isn't your trap.")
            return null
        }
        if (
            trap.phase == Phase.Luring ||
                trap.phase == Phase.Catching ||
                trap.phase == Phase.Failing
        ) {
            mes("Wait until the creature has finished investigating your trap.")
            return null
        }
        return trap
    }

    private suspend fun ProtectedAccess.investigate(at: CoordGrid) {
        val trap = index[at] ?: return
        if (trap.owner !== player) {
            mes("This isn't your trap.")
            return
        }
        val state =
            when (trap.phase) {
                Phase.Idle,
                Phase.Luring -> "waiting for something to wander in"
                Phase.Catching,
                Phase.Failing -> "being investigated"
                Phase.Caught -> "holding a catch"
                Phase.Failed -> "broken"
            }
        val bait = if (trap.bait != null) "It has been baited." else "It has no bait."
        val scent = if (trap.smoked) "Its scent is masked." else "Your scent lingers around it."
        mes("Your trap is $state. $bait $scent")
    }

    private suspend fun ProtectedAccess.dismantle(at: CoordGrid) {
        val trap = ownTrap(at) ?: return
        if (trap.phase == Phase.Caught) {
            // Taking a trap down with a catch in it is the same as checking it.
            check(at, reset = false)
            return
        }
        anim(hunter_seqs.take)
        delay(TAKE_TICKS)
        if (index[at] !== trap) {
            return
        }
        end(trap)
        for (item in trap.refund) {
            give(item)
        }
        mes("You dismantle the trap.")
    }

    private suspend fun ProtectedAccess.check(at: CoordGrid, reset: Boolean) {
        val trap = ownTrap(at) ?: return
        if (trap.phase != Phase.Caught) {
            return
        }
        val creature = trap.creature ?: return
        val collecting = Collecting(player, creature)
        eventBus.publish(collecting)

        anim(hunter_seqs.take)
        delay(TAKE_TICKS)
        if (index[at] !== trap || trap.phase != Phase.Caught) {
            return
        }
        val loot = ArrayList<Item>()
        repeat(collecting.catches) { loot += HunterLoot.roll(creature, stat(stats.hunter), random) }
        // The baby chinchompa (wiki base chances), rolled ahead of the catch like other pets.
        CHINCHOMPA_PETS[creature.key]?.let { (base, colour) ->
            repeat(collecting.catches) {
                publish(SkillingPetRoll(player, SkillingPet.BabyChinchompa, base, colour))
            }
        }
        val refund = if (trap.kind == TrapKind.MagicBox) emptyList() else trap.refund
        if (reset && trap.kind != TrapKind.MagicBox) {
            again(trap)
        } else {
            end(trap)
            if (!reset) {
                for (item in refund) {
                    give(item)
                }
            }
        }
        for (item in loot) {
            give(item)
        }
        val xp = creature.xp * collecting.catches * collecting.xpMultiplier
        statAdvance(stats.hunter, xp * xpMods.get(player, stats.hunter))
        mes("You've caught ${article(creature)}.")
        publish(Trapped(player, creature, collecting.catches, loot.map { it.obj }))
    }

    private suspend fun ProtectedAccess.reset(at: CoordGrid) {
        val trap = ownTrap(at) ?: return
        if (trap.phase != Phase.Failed) {
            return
        }
        anim(hunter_seqs.take)
        delay(TAKE_TICKS)
        if (index[at] !== trap) {
            return
        }
        again(trap)
        mes("You reset the trap.")
    }

    /** Sets a used trap up again, as it was laid. */
    private fun again(trap: Trap) {
        val now = mapClock.cycle
        trap.phase = Phase.Idle
        trap.creature = null
        trap.bait = null
        trap.smoked = false
        trap.expires = now + LIFETIME
        trap.nextScan = now + SCAN_TICKS
        showIdle(trap)
    }

    private fun ProtectedAccess.give(item: Item) {
        if (!invAdd(inv, item.obj, item.count).success) {
            invAddOrDrop(objRepo, item.obj, item.count)
        }
    }

    private fun article(creature: Creature): String {
        val name = creature.npcName.lowercase()
        return "${if (name.first() in VOWELS) "an" else "a"} $name"
    }

    // endregion

    // region bait

    private suspend fun ProtectedAccess.addBait(at: CoordGrid, bait: ObjType) {
        val trap = index[at] ?: return
        if (trap.owner !== player) {
            mes("This isn't your trap.")
            return
        }
        if (trap.phase != Phase.Idle) {
            return
        }
        if (trap.bait != null) {
            mes("This trap is already baited.")
            return
        }
        val amount = if (trap.kind == TrapKind.Net) NET_BAIT else 1
        if (invTotal(inv, bait) < amount) {
            mes("You need $amount of that to bait this trap.")
            return
        }
        anim(hunter_seqs.bait)
        delay(1)
        if (index[at] !== trap || !invDel(inv, bait, amount).success) {
            return
        }
        trap.bait = bait
        mes("You bait the trap.")
    }

    private suspend fun ProtectedAccess.smoke(at: CoordGrid) {
        val trap = index[at] ?: return
        if (trap.owner !== player || trap.phase != Phase.Idle) {
            return
        }
        anim(hunter_seqs.bait)
        delay(1)
        trap.smoked = true
        mes("You mask your scent with smoke.")
    }

    // endregion

    // region events

    class Tick(val player: Player) : UnboundEvent {
        /** Set by whatever else needs this player's hunter timer to keep running. */
        var keep: Boolean = false
    }

    /**
     * Published whenever a trap looks for a creature or settles: a relic changes how fast. The
     * defaults are the real game's: [scanTicks] between looks and [settleTicks] for a catch to
     * show.
     */
    class Modifiers(val player: Player, val kind: TrapKind) : UnboundEvent {
        var scanTicks: Int = SCAN_TICKS
        var settleTicks: Int = SETTLE_TICKS
    }

    /** Published when a creature reaches a trap: a relic can make the catch certain. */
    class Roll(val player: Player, val creature: Creature) : UnboundEvent {
        var success: Boolean = false
    }

    /** Published when a trap is checked: a relic can double the catch and the experience. */
    class Collecting(val player: Player, val creature: Creature) : UnboundEvent {
        var catches: Int = 1
        var xpMultiplier: Double = 1.0
    }

    /** Published after a catch is collected from a trap. */
    class Trapped(
        val player: Player,
        val creature: Creature,
        val catches: Int,
        val items: List<ObjType>,
    ) : UnboundEvent

    // endregion

    private fun trapLimit(level: Int): Int = min(MAX_TRAPS, 1 + level / 20)

    private fun idleLocs(): List<LocType> =
        HunterCreatures.all
            .mapNotNull { it.locs }
            .flatMap { listOf(it.idle) + listOfNotNull(it.beside) }
            .distinct()

    private fun fullLocs(): List<LocType> = HunterCreatures.all.mapNotNull { it.full }.distinct()

    private fun failedLocs(): List<LocType> =
        HunterCreatures.all.mapNotNull { it.locs?.failed }.distinct()

    private fun baitPairs(): List<Pair<LocType, ObjType>> =
        HunterCreatures.all
            .filter { it.locs != null }
            .flatMap { creature ->
                val locs = creature.locs!!
                (listOf(locs.idle) + listOfNotNull(locs.beside)).flatMap { loc ->
                    creature.baits.map { bait -> loc to bait }
                }
            }
            .distinct()

    private fun BoundLocInfo.toInfo(): LocInfo = LocInfo(layer, coords, entity)

    companion object {
        /** Wiki `Baby chinchompa#Drop rates`: base chance and the pet's colour per creature. */
        private val CHINCHOMPA_PETS =
            mapOf(
                "chinchompa" to (131_395 to "grey"),
                "carnivorous_chinchompa" to (98_373 to "red"),
                "black_chinchompa" to (82_758 to "black"),
            )

        /** Ticks the owner spends laying a trap. */
        const val LAY_TICKS = 3

        /** Ticks the owner spends checking or taking down a trap. */
        const val TAKE_TICKS = 2

        /** Ticks a trap survives without being checked. */
        const val LIFETIME = 100

        /** Ticks between one look for a creature and the next. */
        const val SCAN_TICKS = 5

        /** Ticks a creature takes to give up on a trap it was called to. */
        const val LURE_TICKS = 12

        /** Ticks a trap shows a creature going in before it shows the result. */
        const val SETTLE_TICKS = 3

        /** Ticks before a creature that was caught comes back. */
        const val RESPAWN_TICKS = 30

        /** Ticks that something dropped by a collapsing trap lasts. */
        const val DROP_TICKS = 200

        const val MAX_TRAPS = 5
        const val DEADFALL_LIMIT = 2

        /** Tar needed to bait a net trap. */
        const val NET_BAIT = 5

        /** The extra chance, out of one, that the right bait and a masked scent give. */
        const val BAIT_BONUS = 3.0 / 256.0
        const val SMOKE_BONUS = 2.0 / 256.0

        private const val VOWELS = "aeiou"

        private val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        private val SIDESTEPS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        /** Which side of its tree a net trap's net lies on, for the way the tree faces. */
        private val NET_OFFSET =
            mapOf(
                LocAngle.West to (0 to 1),
                LocAngle.North to (1 to 0),
                LocAngle.East to (0 to -1),
                LocAngle.South to (-1 to 0),
            )

        private val LAY_LEVEL =
            mapOf(
                TrapKind.Bird to 1,
                TrapKind.Box to 27,
                TrapKind.MagicBox to 71,
                TrapKind.Deadfall to 23,
                TrapKind.Net to 29,
            )

        private val LOGS =
            listOf(
                hunter_objs.logs,
                hunter_objs.oak_logs,
                hunter_objs.willow_logs,
                hunter_objs.teak_logs,
                hunter_objs.maple_logs,
                hunter_objs.mahogany_logs,
                hunter_objs.yew_logs,
                hunter_objs.magic_logs,
                hunter_objs.redwood_logs,
            )

        private class Tree(val base: LocType, val locs: TrapLocs)

        private val TREES: List<Tree> =
            HunterCreatures.all
                .filter { it.kind == TrapKind.Net }
                .map { Tree(it.locs!!.base!!, it.locs) }

        internal fun logs(): List<ObjType> = LOGS
    }
}
