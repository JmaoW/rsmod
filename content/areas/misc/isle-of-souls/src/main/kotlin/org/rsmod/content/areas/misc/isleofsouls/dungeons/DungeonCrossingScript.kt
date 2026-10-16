package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.script.onApLoc1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.Obstacle
import org.rsmod.content.skills.agility.configs.Shortcut
import org.rsmod.content.skills.agility.configs.Shortcuts
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.content.skills.agility.scripts.Agility
import org.rsmod.content.skills.agility.scripts.ObstacleRunner
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Crosses the dungeons' Agility shortcuts ([DungeonCrossings]) in place, on the real map's own
 * pipes, stones and cracks. It borrows the agility module's step builder and obstacle runner, so a
 * pipe, a run of stones and a squeeze look and move as they do in the Brimhaven Agility Arena.
 */
class DungeonCrossingScript
@Inject
constructor(private val runner: ObstacleRunner, private val xpMods: XpModifiers) : PluginScript() {
    private val byClick = HashMap<Long, DungeonCrossing>()
    private val shortcuts = HashMap<DungeonCrossing, Shortcut>()

    override fun ScriptContext.startup() {
        for (crossing in DungeonCrossings.all) {
            shortcuts[crossing] = crossing.toShortcut()
            for (click in crossing.clicks) {
                byClick[key(click.coords)] = crossing
            }
        }
        val types = DungeonCrossings.all.flatMap { it.clicks }.map { it.type }.distinctBy { it.id }
        for (type in types) {
            // Some stones stand where the walker cannot reach them (rock all round), so a click
            // from within a few tiles starts the crossing; it then walks to the nearer end.
            onApLoc1(type) {
                if (isWithinApRange(it.loc, AP_RANGE)) {
                    cross(it.loc)
                }
            }
            onOpLoc1(type) { cross(it.loc) }
        }
    }

    private suspend fun ProtectedAccess.cross(loc: BoundLocInfo) {
        val crossing = byClick[key(loc.coords)] ?: return
        val nearA =
            coords.chebyshevDistance(crossing.a.coords()) <=
                coords.chebyshevDistance(crossing.b.coords())
        val from = if (nearA) crossing.a else crossing.b
        val to = if (nearA) crossing.b else crossing.a
        val required = if (nearA) crossing.levelAtoB else crossing.levelBtoA
        if (player.agilityLvl < required) {
            mes("You need an Agility level of at least $required to use this.")
            return
        }
        val reached = with(runner) { goTo(from, teleport = false) }
        if (!reached) {
            mes("You can't reach that.")
            return
        }

        val shortcut = checkNotNull(shortcuts[crossing])
        val steps =
            if (crossing.squeeze) {
                crossing.squeezeSteps(from, to)
            } else {
                Shortcuts.steps(shortcut, from, to)
            }
        val obstacle =
            Obstacle(
                index = 0,
                level = required,
                xp = crossing.xp,
                clicks = emptyList(),
                pieces = emptyList(),
                route = null,
                render = Shortcuts.render(shortcut),
                start = Shortcuts.start(shortcut),
                end = Shortcuts.end(shortcut),
                steps = steps,
            )
        with(runner) { perform(obstacle) }
        if (crossing.xp > 0.0) {
            statAdvance(stats.agility, crossing.xp * xpMods.get(player, stats.agility))
        }
        publish(Agility.ShortcutUsed(player, shortcut, success = true))
    }

    private fun DungeonCrossing.toShortcut() =
        Shortcut(
            key = key,
            name = name,
            level = maxOf(levelAtoB, levelBtoA),
            xp = xp,
            kind = kind,
            a = a,
            b = b,
            clicks = emptyList(),
            stones = stones,
            via = via,
        )

    private fun Tile.coords(): CoordGrid = CoordGrid(x, z, level)

    private companion object {
        /** How far away a click starts a crossing. */
        const val AP_RANGE = 8
    }

    private fun key(coords: CoordGrid): Long = (coords.x.toLong() shl 32) or coords.z.toLong()
}
