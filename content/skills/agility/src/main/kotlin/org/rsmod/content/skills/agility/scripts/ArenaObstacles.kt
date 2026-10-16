package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.ArenaEdge
import org.rsmod.content.skills.agility.configs.ArenaKind
import org.rsmod.content.skills.agility.configs.ArenaLayout
import org.rsmod.content.skills.agility.configs.ArenaPit
import org.rsmod.content.skills.agility.configs.ArenaStat
import org.rsmod.content.skills.agility.configs.ArenaStats
import org.rsmod.content.skills.agility.configs.Obstacle
import org.rsmod.content.skills.agility.configs.Shortcut
import org.rsmod.content.skills.agility.configs.ShortcutKind
import org.rsmod.content.skills.agility.configs.Shortcuts
import org.rsmod.content.skills.agility.configs.Step
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Karamja glove experience bonus
//
// The arena's obstacles: the ledges, logs, ropes, bars, hand holds, planks, rope swings, pillars
// and low walls that join one pillar to the next. Clicking one takes the player from the end they
// stand at to the other, for its experience. The Wiki gives no chances of falling, so the chances
// are guesses by tier (see [ArenaStats]); nobody falls at 99 Agility. A fall costs 5% of the
// player's hitpoints and 2 more, and lands them in the pit under the pillar they set out from,
// where a rope leads back up (see [Arena]).
//
// Three planks join the pillars in a few places and one of the three is sound; which changes every
// minute, and the broken ones are drawn broken.
class ArenaObstacles
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val locRepo: LocRepository,
    private val runner: ObstacleRunner,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    /** One way across an edge: [lane] of [edge], as a shortcut for the step builder. */
    private class Crossing(
        val edge: ArenaEdge,
        val lane: Int,
        val shortcut: Shortcut,
        val stat: ArenaStat,
        val plankEdge: Int,
    )

    private val crossings = HashMap<Long, Crossing>()
    private val plankEdges = ArenaLayout.edges.filter { it.kind == ArenaKind.Plank }

    /** The sound lane of each plank edge, in the order of [plankEdges]. */
    private val soundLanes = IntArray(plankEdges.size) { -1 }
    private var nextPlankSwap = 0

    override fun ScriptContext.startup() {
        for (edge in ArenaLayout.edges) {
            val kind = shortcutKind(edge.kind) ?: continue
            val stat = ArenaStats.of(edge.kind)
            val lanes =
                edge.lanes.mapIndexed { index, lane ->
                    val shortcut =
                        Shortcut(
                            key = "arena_${edge.a}_${edge.b}_$index",
                            name = edge.kind.name,
                            level = stat.level,
                            xp = stat.xp,
                            kind = kind,
                            a = lane.from,
                            b = lane.to,
                            clicks = emptyList(),
                        )
                    Crossing(edge, index, shortcut, stat, plankEdges.indexOf(edge))
                }
            for (click in edge.clicks) {
                val crossing =
                    if (edge.kind == ArenaKind.Plank) {
                        lanes.first { it.edge.lanes[it.lane].from.z == click.z }
                    } else {
                        lanes.first()
                    }
                crossings[key(click.x, click.z)] = crossing
            }
        }
        val clickNames = ArenaLayout.edges.flatMap { it.clicks }.map { it.loc }.toSet()
        for (name in clickNames) {
            val type = locTypes.values.first { it.internalName == name }
            onApLoc1(type) {
                if (isWithinApRange(it.loc, AP_RANGE)) {
                    cross(it.loc)
                }
            }
            onOpLoc1(type) { cross(it.loc) }
        }
        onEvent<Arena.Cycle> { cycle(now) }
    }

    /** Changes the sound plank when the minute is up. */
    fun cycle(now: Int) {
        if (now < nextPlankSwap) {
            return
        }
        nextPlankSwap = now + Arena.LIT_TICKS
        for ((index, edge) in plankEdges.withIndex()) {
            var next = random.of(edge.lanes.size)
            if (next == soundLanes[index]) {
                next = (next + 1) % edge.lanes.size
            }
            setSoundLane(index, next)
        }
    }

    /** Makes [lane] of the plank edge number [index] the sound one, and draws all three so. */
    fun setSoundLane(index: Int, lane: Int) {
        soundLanes[index] = lane
        val edge = plankEdges[index]
        val planks = checkNotNull(edge.planks)
        val sound = locTypes.values.first { it.internalName == "agilityarena_plank_middle" }
        val broken = locTypes.values.first { it.internalName == "agilityarena_plank_broke1" }
        for ((number, pieces) in planks.withIndex()) {
            val type = if (number == lane) sound else broken
            for (piece in pieces) {
                val coords = CoordGrid(piece.x, piece.z, 3)
                locRepo.add(coords, type, Int.MAX_VALUE, LocAngle[piece.angle], LocShape[22])
            }
        }
    }

    /** The sound lane of the plank edge that [crossing] is on. */
    fun soundLane(plankEdge: Int): Int = soundLanes[plankEdge]

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        val crossing = crossings[key(loc.coords.x, loc.coords.z)] ?: return
        val stat = crossing.stat
        if (player.agilityLvl < stat.level) {
            mes("You need an Agility level of at least ${stat.level} to attempt this.")
            return
        }
        val lane = crossing.edge.lanes[crossing.lane]
        val nearFrom =
            coords.chebyshevDistance(lane.from.coords()) <=
                coords.chebyshevDistance(lane.to.coords())
        val from = if (nearFrom) lane.from else lane.to
        val to = if (nearFrom) lane.to else lane.from

        val mods = Agility.Modifiers(player, null, null, crossing.shortcut)
        publish(mods)

        val reached = with(runner) { goTo(from, teleport = false) }
        if (!reached) {
            mes("You can't reach that.")
            return
        }

        val sure = mods.guaranteed || player.agilityLvl >= stat.sure
        val plank = crossing.edge.kind == ArenaKind.Plank
        val sound = plank && soundLanes[crossing.plankEdge] == crossing.lane
        val success =
            when {
                sure -> true
                plank -> sound
                else -> roll(stat)
            }
        val shortcut = crossing.shortcut
        if (success) {
            val steps = Shortcuts.steps(shortcut, from, to)
            val slow = plank && !sound
            val obstacle =
                obstacle(shortcut, stat.xp, if (slow) listOf(Step.Wait(1)) + steps else steps)
            with(runner) { perform(obstacle) }
            statAdvance(stats.agility, stat.xp * xpMods.get(player, stats.agility))
        } else if (crossing.edge.kind == ArenaKind.LowWall) {
            val obstacle =
                obstacle(
                    shortcut,
                    0.0,
                    listOf(
                        Step.Msg("You stumble and fall back to where you started."),
                        Step.Wait(2),
                    ),
                )
            with(runner) { perform(obstacle) }
        } else {
            val pillar = if (nearFrom) crossing.edge.a else crossing.edge.b
            fall(crossing, from, to, pillar)
        }
        publish(Agility.ShortcutUsed(player, shortcut, success))
    }

    /** The player starts across, then slips, drops into the pit under [pillar] and is hurt. */
    private suspend fun ProtectedAccess.fall(
        crossing: Crossing,
        from: Tile,
        to: Tile,
        pillar: Int,
    ) {
        val shortcut = crossing.shortcut
        val steps = ArrayList<Step>()
        if (shortcut.kind in WALKED) {
            steps += Shortcuts.steps(shortcut, from, to).takeWhile { it !is Step.Walk }
            steps += Step.Walk(listOf(halfway(from, to)))
        }
        steps += Step.Msg(crossing.stat.failMessage)
        steps += Step.Tele(landing(pillar))
        with(runner) { perform(obstacle(shortcut, 0.0, steps)) }
        player.queueHit(1, HitType.Typeless, ArenaHits.damage(player))
    }

    private fun landing(pillar: Int): Tile {
        val pit: ArenaPit = ArenaLayout.pits.first { it.pillar == pillar }
        return pit.landing[random.of(pit.landing.size)]
    }

    private fun halfway(from: Tile, to: Tile): Tile =
        Tile((from.x * 3 + to.x * 2 + 2) / 5, (from.z * 3 + to.z * 2 + 2) / 5, from.level)

    private fun obstacle(shortcut: Shortcut, xp: Double, steps: List<Step>) =
        Obstacle(
            index = 0,
            level = shortcut.level,
            xp = xp,
            clicks = emptyList(),
            pieces = emptyList(),
            route = null,
            render = Shortcuts.render(shortcut),
            start = null,
            end = null,
            steps = steps,
        )

    /** Passing is likelier the higher the Agility level, out of 256 as the other obstacles. */
    private fun ProtectedAccess.roll(stat: ArenaStat): Boolean {
        val level = player.agilityLvl.coerceIn(1, 99)
        val chance =
            1 + Math.floor((stat.low * (99 - level) + stat.high * (level - 1)) / 98.0 + 0.5).toInt()
        return random.of(256) < chance
    }

    private fun shortcutKind(kind: ArenaKind): ShortcutKind? =
        when (kind) {
            ArenaKind.Ledge -> ShortcutKind.Ledge
            ArenaKind.Log -> ShortcutKind.Log
            ArenaKind.Rope -> ShortcutKind.RopeBalance
            ArenaKind.Bars -> ShortcutKind.MonkeyBars
            ArenaKind.Hands -> ShortcutKind.HandHolds
            ArenaKind.Plank -> ShortcutKind.Plank
            ArenaKind.Swing -> ShortcutKind.RopeSwing
            ArenaKind.Pillar -> ShortcutKind.Pillar
            ArenaKind.LowWall -> ShortcutKind.LowWall
            else -> null
        }

    private fun Tile.coords(): CoordGrid = CoordGrid(x, z, level)

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    companion object {
        /** How far away a click starts an obstacle; the player then walks to its near end. */
        const val AP_RANGE = 8

        /** The obstacles walked along rather than jumped, which can fail half way across. */
        private val WALKED =
            setOf(
                ShortcutKind.Ledge,
                ShortcutKind.Log,
                ShortcutKind.RopeBalance,
                ShortcutKind.MonkeyBars,
                ShortcutKind.HandHolds,
                ShortcutKind.Plank,
            )
    }
}

/** What the arena's falls and traps take off the player. */
object ArenaHits {
    /** 5% of the current hitpoints and 2 more, but never enough to kill. */
    fun damage(player: Player): Int {
        val hp = player.hitpoints
        return minOf(hp * 5 / 100 + 2, maxOf(hp - 1, 0))
    }
}
