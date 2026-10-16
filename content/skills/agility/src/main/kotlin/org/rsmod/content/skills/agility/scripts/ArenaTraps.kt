package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.ArenaEdge
import org.rsmod.content.skills.agility.configs.ArenaKind
import org.rsmod.content.skills.agility.configs.ArenaLayout
import org.rsmod.content.skills.agility.configs.ArenaSeqs
import org.rsmod.content.skills.agility.configs.ArenaStat
import org.rsmod.content.skills.agility.configs.ArenaStats
import org.rsmod.content.skills.agility.configs.Obstacle
import org.rsmod.content.skills.agility.configs.Shortcuts
import org.rsmod.content.skills.agility.configs.Step
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Karamja glove experience bonus
// - the Wiki's timings (4 ticks over the pads and spikes, 5 over the blades, 10 for the darts) and
//   the real success charts, which it does not print
//
// The arena's traps, which are corridors between the pillars. The pressure pads, floor spikes,
// spinning blades and darts have an invisible wall across the middle of the corridor, so a player
// who walks up to it is taken across by a jump, a dash or a dive for the trap's experience, and
// may be hit and thrown back instead. The pads give experience twice and then none for 8 ticks.
// The timed blades have no wall: a blade comes up out of the floor for a few ticks every few ticks,
// and anyone standing where it rises is hit and thrown clear. A failure takes 5% of the player's
// hitpoints and 2 more, and the darts also drain two Agility levels.
class ArenaTraps
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val locTypes: LocTypeList,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
    private val runner: ObstacleRunner,
    private val xpMods: XpModifiers,
    private val random: GameRandom,
) : PluginScript() {
    /** Which side of a trap a player steps up to: they cross from [from] to [to]. */
    private class Side(val edge: ArenaEdge, val from: Tile, val to: Tile)

    private class PadState(var uses: Int = 0, var lastUse: Int = 0, var lockedUntil: Int = 0)

    private val sides = HashMap<Long, Side>()
    private val trapEdges =
        ArenaLayout.edges.filter {
            it.kind == ArenaKind.Pads ||
                it.kind == ArenaKind.Spikes ||
                it.kind == ArenaKind.Saw ||
                it.kind == ArenaKind.Darts
        }
    private val bladeEdges = ArenaLayout.edges.filter { it.kind == ArenaKind.Blade }

    private val lastTile = WeakHashMap<Player, Long>()
    private val ignoreUntil = WeakHashMap<Player, Int>()
    private val pads = WeakHashMap<Player, PadState>()

    override fun ScriptContext.startup() {
        for (edge in trapEdges) {
            val (first, second) = checkNotNull(edge.trap)
            sides[key(first)] = Side(edge, first, second)
            sides[key(second)] = Side(edge, second, first)
        }
        onEvent<Arena.PlayerTick> { tick(player, now) }
        onEvent<Arena.Cycle> { cycle(now) }
    }

    /** Raises the blades and plays the spikes and the saws. */
    fun cycle(now: Int) {
        for ((index, edge) in bladeEdges.withIndex()) {
            if ((now + index * BLADE_STAGGER) % BLADE_PERIOD == 0) {
                raiseBlade(edge)
            }
        }
        if (now % ANIM_PERIOD == 0) {
            playTrapAnims()
        }
    }

    private fun raiseBlade(edge: ArenaEdge) {
        val tiles = checkNotNull(edge.blade)
        val type = locTypes.values.first { it.internalName == "agilityarena_trap_timedblade2" }
        val origin = CoordGrid(tiles.minOf { it.x }, tiles.minOf { it.z }, 3)
        val angle = if (edge.horizontal) 1 else 0
        locRepo.add(origin, type, BLADE_UP, LocAngle[angle], LocShape[10])
    }

    private fun playTrapAnims() {
        val saw = locTypes.values.first { it.internalName == "agilityarena_sawblades" }
        val spikes = locTypes.values.first { it.internalName == "agilityarena_floorspikes" }
        val twist = ArenaSeqs.saw_twist
        val activate = ArenaSeqs.spikes_activate
        for (edge in trapEdges) {
            val (first, second) = checkNotNull(edge.trap)
            when (edge.kind) {
                ArenaKind.Saw -> {
                    val zone = ZoneKey.from(CoordGrid(first.x, first.z, 3))
                    locRepo
                        .findAll(zone)
                        .filter { it.id == saw.id }
                        .forEach { worldRepo.locAnim(it, twist) }
                }
                ArenaKind.Spikes ->
                    for (tile in listOf(first, second)) {
                        locRepo.findExact(CoordGrid(tile.x, tile.z, 3), spikes)?.let {
                            worldRepo.locAnim(it, activate)
                        }
                    }
                else -> {}
            }
        }
    }

    /** Looks for a player stepping up to a trap, or under a raised blade. */
    fun tick(player: Player, now: Int) {
        if (player.coords.level != 3) {
            lastTile.remove(player)
            return
        }
        val here = key(player.coords.x, player.coords.z)
        val previous = lastTile.put(player, here)
        if (ignoreUntil[player]?.let { now < it } == true) {
            return
        }
        val blade = bladeAt(player.coords, now)
        if (blade != null) {
            ignoreUntil[player] = now + IGNORE_TICKS
            launcher.launch(player) { bladeHit(blade) }
            return
        }
        if (previous == here) {
            return
        }
        val side = sides[here] ?: return
        ignoreUntil[player] = now + IGNORE_TICKS
        launcher.launch(player) { trap(side, now) }
    }

    /** The blade edge whose blade is up and covers [coords], if there is one. */
    private fun bladeAt(coords: CoordGrid, now: Int): ArenaEdge? {
        for ((index, edge) in bladeEdges.withIndex()) {
            val phase = (now + index * BLADE_STAGGER) % BLADE_PERIOD
            if (phase >= BLADE_UP) {
                continue
            }
            if (checkNotNull(edge.blade).any { it.x == coords.x && it.z == coords.z }) {
                return edge
            }
        }
        return null
    }

    private suspend fun ProtectedAccess.bladeHit(edge: ArenaEdge) {
        val tiles = checkNotNull(edge.blade).sortedBy { it.x + it.z }
        // Thrown out of whichever end is nearer.
        val low = tiles.first()
        val high = tiles.last()
        val onLow = coords.x == low.x && coords.z == low.z
        val out =
            if (onLow) {
                Tile(low.x - (high.x - low.x), low.z - (high.z - low.z), 3)
            } else {
                Tile(high.x + (high.x - low.x), high.z + (high.z - low.z), 3)
            }
        mes("You were hit by the saw blade!")
        telejump(CoordGrid(out.x, out.z, 3))
        player.queueHit(1, HitType.Typeless, ArenaHits.damage(player))
        ignoreUntil[player] = player.currentMapClock + IGNORE_TICKS
    }

    private suspend fun ProtectedAccess.trap(side: Side, now: Int) {
        val edge = side.edge
        val stat = ArenaStats.of(edge.kind)
        if (player.agilityLvl < stat.level) {
            mes("You need an Agility level of at least ${stat.level} to attempt this.")
            return
        }
        val mods = Agility.Modifiers(player, null, null, null)
        publish(mods)

        val sure = mods.guaranteed || player.agilityLvl >= stat.sure
        val success = sure || roll(stat)
        if (success) {
            with(runner) { perform(obstacle(passSteps(edge.kind, side), stat)) }
            val xp = if (edge.kind == ArenaKind.Pads) padXp(stat, now) else stat.xp
            if (xp > 0) {
                statAdvance(stats.agility, xp * xpMods.get(player, stats.agility))
            }
        } else {
            val back =
                Tile(
                    side.from.x + (side.from.x - side.to.x),
                    side.from.z + (side.from.z - side.to.z),
                    3,
                )
            val steps = ArrayList<Step>()
            steps += Step.Msg(stat.failMessage)
            steps += Step.Anim("agilityarena_player_spiked")
            steps += Step.Wait(1)
            steps += Step.Tele(back)
            with(runner) { perform(obstacle(steps, stat)) }
            player.queueHit(1, HitType.Typeless, ArenaHits.damage(player))
            if (edge.kind == ArenaKind.Darts) {
                statDrain(stats.agility, 2, 0)
            }
        }
        lastTile[player] = key(player.coords.x, player.coords.z)
        ignoreUntil[player] = player.currentMapClock + IGNORE_TICKS
        publish(ArenaTrapped(player, edge.kind, success))
    }

    /** The pads give their experience twice and then none for [PAD_LOCK_TICKS] ticks. */
    private fun ProtectedAccess.padXp(stat: ArenaStat, now: Int): Double {
        val state = pads.getOrPut(player) { PadState() }
        if (now < state.lockedUntil) {
            return 0.0
        }
        state.uses = if (now - state.lastUse <= PAD_WINDOW) state.uses + 1 else 1
        state.lastUse = now
        if (state.uses >= 2) {
            state.uses = 0
            state.lockedUntil = now + PAD_LOCK_TICKS
        }
        return stat.xp
    }

    private fun passSteps(kind: ArenaKind, side: Side): List<Step> {
        val dir = Shortcuts.direction(side.from, side.to)
        return when (kind) {
            ArenaKind.Darts ->
                listOf(
                    Step.Face(side.to),
                    Step.Anim("agilityarena_dive_player"),
                    Step.Move(side.to, null, 15, 45, 2, dir),
                    Step.Anim("agilityarena_land_player"),
                    Step.Wait(1),
                )
            else ->
                listOf(Step.Face(side.to), Step.Move(side.to, "human_jump_hurdle", 0, 60, 2, dir))
        }
    }

    private fun obstacle(steps: List<Step>, stat: ArenaStat) =
        Obstacle(
            index = 0,
            level = stat.level,
            xp = stat.xp,
            clicks = emptyList(),
            pieces = emptyList(),
            route = null,
            render = null,
            start = null,
            end = null,
            steps = steps,
        )

    private fun ProtectedAccess.roll(stat: ArenaStat): Boolean {
        val level = player.agilityLvl.coerceIn(1, 99)
        val chance =
            1 + Math.floor((stat.low * (99 - level) + stat.high * (level - 1)) / 98.0 + 0.5).toInt()
        return random.of(256) < chance
    }

    private fun key(tile: Tile): Long = key(tile.x, tile.z)

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    /** Published after a trap has been tried, [success]fully or not. */
    class ArenaTrapped(val player: Player, val kind: ArenaKind, val success: Boolean) :
        UnboundEvent

    companion object {
        /** A blade rises every [BLADE_PERIOD] ticks and stays up for [BLADE_UP] of them. */
        const val BLADE_PERIOD = 8
        const val BLADE_UP = 3
        private const val BLADE_STAGGER = 3

        private const val ANIM_PERIOD = 10
        const val PAD_LOCK_TICKS = 8
        private const val PAD_WINDOW = 20
        private const val IGNORE_TICKS = 2
    }
}
