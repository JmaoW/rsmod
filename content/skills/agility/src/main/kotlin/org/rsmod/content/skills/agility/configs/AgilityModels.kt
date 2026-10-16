package org.rsmod.content.skills.agility.configs

/** A tile. The isle's courses all stand on level 0; the arena is on level 3. */
data class Tile(val x: Int, val z: Int, val level: Int = 0)

/** One loc of an obstacle: the cache loc by internal name, where it stands and how it is turned. */
data class Piece(val loc: String, val x: Int, val z: Int, val angle: Int, val shape: Int)

/**
 * What an obstacle does to the player, one step at a time. The steps come from the tsps agility
 * plugin (`RSPSApp/tsps`), which wrote them from rsprox captures and the OSRS Wiki; their tiles are
 * flattened onto level 0 and moved onto the isle. Animations are cache seq names.
 */
sealed interface Step {
    /** Plays [seq], [delay] client cycles late. */
    data class Anim(val seq: String, val delay: Int = 0) : Step

    /** Waits [ticks] game ticks. */
    data class Wait(val ticks: Int) : Step

    /** Puts the player on [tile] at once. */
    data class Tele(val tile: Tile) : Step

    /** Walks one tile a tick through each waypoint in turn, ignoring what is in the way. */
    data class Walk(val path: List<Tile>) : Step

    /**
     * Moves the player to [tile] in [travel] client cycles after [begin] cycles, facing [dir] (an
     * exact move direction: 0 south, 512 west, 1024 north, 1536 east) on arrival, then waits
     * [ticks] game ticks. [anim] plays as it starts.
     */
    data class Move(
        val tile: Tile,
        val anim: String?,
        val begin: Int,
        val travel: Int,
        val ticks: Int,
        val dir: Int,
    ) : Step

    /** Turns the player to look at [tile]. */
    data class Face(val tile: Tile) : Step

    /** Swaps the walk and stand animations for the ones in [seq] (`null` puts them back). */
    data class Render(val seq: String?) : Step

    /** Says [text] in the chat box. */
    data class Msg(val text: String) : Step
}

/**
 * What a failed attempt does: the Wiki's success chart for the obstacle ([low] and [high] out of
 * 256 at levels 1 and 99), then [steps], [xp] instead of the obstacle's, and [damage] to the thief.
 */
class Fail(
    val low: Int,
    val high: Int,
    val xp: Double,
    val damage: IntRange,
    val steps: List<Step>,
    val message: String?,
)

/**
 * One obstacle of a course. Clicking any of [clicks] starts it: the player walks to [route] if
 * there is one, then runs [steps]. [pieces] are the rest of what makes it look right (the other
 * ends of a rope, the signs) and are never clicked. [index] is its place in the lap; obstacles that
 * share an index are alternatives (the two pipes, say).
 */
class Obstacle(
    val index: Int,
    val level: Int,
    val xp: Double,
    val clicks: List<Piece>,
    val pieces: List<Piece>,
    val route: Tile?,
    val render: String?,
    val start: String?,
    val end: String?,
    val steps: List<Step>,
    val fail: Fail? = null,
)

/**
 * A course. A full lap tops its experience up to [lapXp]. A rooftop course drops marks of grace
 * while a lap is under way, on one of [marks] (each a tile on the isle), and each obstacle there
 * restores a little run energy.
 */
class Course(
    val key: String,
    val name: String,
    val level: Int,
    val lapXp: Double,
    val rooftop: Boolean,
    val marks: List<Tile>,
    val obstacles: List<Obstacle>,
) {
    val shortName: String = name.removeSuffix(" Course")

    val finalIndex: Int = obstacles.maxOf { it.index }

    /** The experience the obstacles give by themselves. */
    val obstacleXp: Double =
        obstacles.groupBy { it.index }.values.sumOf { alternatives -> alternatives.maxOf { it.xp } }

    /** What completing the lap adds on top of its obstacles. */
    val lapBonus: Double = maxOf(0.0, lapXp - obstacleXp)
}

/** The kinds of shortcut the isle has, each crossed its own way. */
enum class ShortcutKind {
    RopeSwing,
    Log,
    Stones,
    Pipe,
    LowWall,
    Ledge,
    MonkeyBars,
    HandHolds,
    RopeBalance,
    Plank,
    Pillar,
}

/**
 * A two-way shortcut across a gap between [a] and [b]. Clicking any of [clicks] takes the player to
 * the nearer end and across to the other. [stones] are the tiles hopped across (stepping stones)
 * and [via] is the middle of a pipe. [pieces] only decorate. A [fail] that has no steps still
 * crosses, and only hurts.
 */
class Shortcut(
    val key: String,
    val name: String,
    val level: Int,
    val xp: Double,
    val kind: ShortcutKind,
    val a: Tile,
    val b: Tile,
    val clicks: List<Piece>,
    val pieces: List<Piece> = emptyList(),
    val stones: List<Tile> = emptyList(),
    val via: Tile? = null,
    val fail: Fail? = null,
)
