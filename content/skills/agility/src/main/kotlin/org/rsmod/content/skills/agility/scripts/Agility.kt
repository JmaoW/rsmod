package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import java.util.WeakHashMap
import kotlin.math.min
import org.rsmod.api.config.Constants
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onGameStartup
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpObj3
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.agility.configs.AgilityObjs
import org.rsmod.content.skills.agility.configs.AgilityVarps
import org.rsmod.content.skills.agility.configs.Course
import org.rsmod.content.skills.agility.configs.Courses
import org.rsmod.content.skills.agility.configs.Obstacle
import org.rsmod.content.skills.agility.configs.Piece
import org.rsmod.content.skills.agility.configs.Shortcut
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.events.UnboundEvent
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.obj.Obj
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.loc.UnpackedLocType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

// TODO:
// - the Wiki's chance of failing a rooftop obstacle: tsps has none for these courses, so they never
//   fail (the relic's "always succeed" only matters to shortcuts and the arena)
// - the giant squirrel pet, and Grace's counter toggle
//
// Puts the park's obstacles on the isle when the server starts, and runs one when it is clicked.
// A lap is tracked in memory only: the first obstacle begins one, each later one must follow the
// last, and the last completes it. The Corner Cutter relic reaches in through [Modifiers].
class Agility
@Inject
constructor(
    private val locTypes: LocTypeList,
    private val runner: ObstacleRunner,
    private val objTypes: ObjTypeList,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {
    private class Entry(val course: Course, val obstacle: Obstacle)

    private class Progress(val course: Course, var index: Int)

    private val entries = HashMap<Long, Entry>()
    private val progress = WeakHashMap<Player, Progress>()
    private val locByName: Map<String, UnpackedLocType> by lazy {
        locTypes.values.filter { it.internalName != null }.associateBy { it.internalName!! }
    }

    override fun ScriptContext.startup() {
        for (course in Courses.all) {
            for (obstacle in course.obstacles) {
                for (click in obstacle.clicks) {
                    entries[key(click.x, click.z)] = Entry(course, obstacle)
                }
            }
        }
        val clickNames =
            Courses.all.flatMap { it.obstacles }.flatMap { it.clicks }.map { it.loc }.toSet()
        for (name in clickNames) {
            val type = locByName[name] ?: error("Agility: no loc named $name.")
            onOpLoc1(type) { clicked(it.loc) }
        }
        onOpObj3(AgilityObjs.mark_of_grace) { take(it.obj) }
        onGameStartup { placeObstacles() }
    }

    /** Puts every obstacle of every course on the isle. Runs when the server starts. */
    fun placeObstacles() {
        for (course in Courses.all) {
            for (obstacle in course.obstacles) {
                for (piece in obstacle.clicks + obstacle.pieces) {
                    place(piece)
                }
            }
        }
    }

    private fun place(piece: Piece) {
        val type = locByName[piece.loc] ?: error("Agility: no loc named ${piece.loc}.")
        val coords = CoordGrid(piece.x, piece.z, 0)
        locRepo.add(coords, type, Int.MAX_VALUE, LocAngle[piece.angle], LocShape[piece.shape])
    }

    /**
     * Picking up a mark of grace, which does what taking any item does and then tells whoever is
     * listening. Public so that a test can pick one up.
     */
    fun ProtectedAccess.take(obj: Obj) {
        val transaction = player.invAdd(player.inv, obj.type, obj.count, autoCommit = false)
        if (transaction.failure) {
            mes(Constants.dm_take_invspace)
            return
        }
        if (!objRepo.del(obj)) {
            mes(Constants.dm_take_taken)
            return
        }
        transaction.commitAll()
        publish(MarkTaken(player))
    }

    private suspend fun ProtectedAccess.clicked(loc: BoundLocInfo) {
        val entry = entries[key(loc.coords.x, loc.coords.z)] ?: return
        attempt(entry.course, entry.obstacle)
    }

    private suspend fun ProtectedAccess.attempt(course: Course, obstacle: Obstacle) {
        if (player.agilityLvl < obstacle.level) {
            mes("You need an Agility level of at least ${obstacle.level} to attempt this.")
            return
        }
        val mods = Modifiers(player, course, obstacle)
        publish(mods)

        if (obstacle.index == 1) {
            begin(course, mods)
        }
        with(runner) { perform(obstacle) }

        val xp = obstacle.xp * xpMods.get(player, stats.agility)
        statAdvance(stats.agility, xp)
        obstacle.end?.let { mes(it) }
        if (course.rooftop) {
            restoreEnergy()
        }
        publish(ObstacleDone(player, course, obstacle))
        advance(course, obstacle, mods)
    }

    /** The first obstacle of a lap: starts the lap and drops a mark of grace now and then. */
    private fun ProtectedAccess.begin(course: Course, mods: Modifiers) {
        progress[player] = Progress(course, 1)
        if (!course.rooftop || course.marks.isEmpty()) {
            return
        }
        var chance = MARK_CHANCE
        if (player.agilityLvl >= course.level + MARK_OVERLEVEL) {
            chance *= MARK_OVERLEVEL_RATE
        }
        if (random.randomDouble() >= chance) {
            return
        }
        val tile = course.marks[random.of(course.marks.size)]
        val coords = CoordGrid(tile.x, tile.z, 0)
        objRepo.add(objTypes[AgilityObjs.mark_of_grace], coords, MARK_LIFETIME, player)
        if (mods.markCoins > 0) {
            objRepo.add(objTypes[objs.coins], coords, MARK_LIFETIME, player, mods.markCoins)
        }
    }

    private fun ProtectedAccess.advance(course: Course, obstacle: Obstacle, mods: Modifiers) {
        val current = progress[player]
        if (obstacle.index == 1) {
            // begin() set it already
        } else if (
            current != null && current.course === course && current.index == obstacle.index - 1
        ) {
            current.index = obstacle.index
        } else {
            progress.remove(player)
            return
        }
        if (obstacle.index != course.finalIndex) {
            return
        }
        progress.remove(player)
        complete(course, mods)
    }

    private fun ProtectedAccess.complete(course: Course, mods: Modifiers) {
        val varp = AgilityVarps.laps(course.key)
        var laps = 0
        if (varp != null) {
            laps = player.vars[varp] + mods.completions
            VarPlayerIntMapSetter.set(player, varp, laps)
        }
        var bonus = course.lapBonus + course.lapXp * mods.lapBonusPercent / 100.0
        if (bonus > 0) {
            statAdvance(stats.agility, bonus * xpMods.get(player, stats.agility))
        }
        if (player.vars[AgilityVarps.lap_counter_off] == 0) {
            mes("Your ${course.shortName} lap count is: <col=ff0000>$laps</col>.")
        }
        publish(LapDone(player, course, mods.completions, laps))
    }

    private fun ProtectedAccess.restoreEnergy() {
        player.runEnergy = min(MAX_ENERGY, player.runEnergy + ENERGY_PER_OBSTACLE)
        UpdateRun.energy(player, player.runEnergy)
    }

    private fun Tile.coords(): CoordGrid = CoordGrid(x, z, level)

    private fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or z.toLong()

    /**
     * Published before an obstacle runs, so a relic can change what comes of it: [completions] is
     * how many laps a finished lap counts as, [lapBonusPercent] is a share of the course's lap
     * experience added to a finished lap, and [markCoins] is a stack of coins that comes down with
     * each mark of grace.
     */
    class Modifiers(
        val player: Player,
        val course: Course?,
        val obstacle: Obstacle?,
        val shortcut: Shortcut? = null,
    ) : UnboundEvent {
        var guaranteed: Boolean = false
        var completions: Int = 1
        var lapBonusPercent: Int = 0
        var markCoins: Int = 0
    }

    /** Published after every obstacle of a course. */
    class ObstacleDone(val player: Player, val course: Course, val obstacle: Obstacle) :
        UnboundEvent

    /** Published after a shortcut is crossed, [success]fully or with a slip. */
    class ShortcutUsed(val player: Player, val shortcut: Shortcut, val success: Boolean) :
        UnboundEvent

    /** Published when a player picks up a mark of grace. */
    class MarkTaken(val player: Player) : UnboundEvent

    /** Published after a course lap; [completions] is what it counted as, [laps] the new total. */
    class LapDone(val player: Player, val course: Course, val completions: Int, val laps: Int) :
        UnboundEvent

    companion object {
        /** The chance of a mark of grace when a lap begins, and its drop for those far above it. */
        const val MARK_CHANCE = 0.25
        const val MARK_OVERLEVEL = 20
        const val MARK_OVERLEVEL_RATE = 0.75

        /** Ticks a mark of grace lies there for. */
        const val MARK_LIFETIME = 100

        /** One percent of the run energy bar, which is out of 10,000. */
        const val ENERGY_PER_OBSTACLE = 100
        private const val MAX_ENERGY = 10_000
    }
}
