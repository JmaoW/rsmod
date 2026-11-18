package org.rsmod.content.areas.misc.isleofsouls.tob

import jakarta.inject.Inject
import org.rsmod.api.combat.NpcAttack
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.stat
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.type.refs.loc.LocReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.seq.SeqReferences
import org.rsmod.api.type.refs.spot.SpotanimReferences
import org.rsmod.api.type.refs.synth.SynthReferences
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.map.Direction
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

object SotetsegNpcs : NpcReferences() {
    val waiting = find("tob_sotetseg_noncombat")
    val fighting = find("tob_sotetseg_combat")
    val tornado = find("tob_sotetseg_creeper")
}

object SotetsegLocs : LocReferences() {
    val dark = find("tob_sotetseg_darktile")
    val light = find("tob_sotetseg_lighttile")
    val wrong = find("tob_sotetseg_triggeredtile")
    val portal = find("tob_sotetseg_darkrealm_exit")
}

object SotetsegSeqs : SeqReferences() {
    val melee = find("tob_sotetseg_attack_melee")
    val ball = find("tob_sotetseg_attack_ranged")
    val death = find("tob_sotetseg_death")
}

object SotetsegSpots : SpotanimReferences() {
    val big_ball = find("tob_sotetseg_sharedattack")
    val big_impact = find("tob_sotetseg_sharedattack_impact")
    val magic = find("tob_sotetseg_maging")
    val ranged = find("tob_sotetseg_ranging")
    val drain = find("tob_sotetseg_drain")
    val zap = find("tob_sotetseg_zap")
    val explosion = find("devious_explosion")
}

object SotetsegSynths : SynthReferences() {
    val melee = find("tob_sotetseg_melee")
    val ball = find("tob_sotetseg_ball")
    val zap = find("tob_sotetseg_maze_zap")
}

/**
 * Wave 4, Sotetseg. It stands still and attacks every 5 ticks: a horn lunge on someone beside it
 * (up to 45, 22 through Protect from Melee) or a red ball (up to 50, fully blocked by the right
 * prayer; one that isn't blocked turns the player's prayers off). The first ball of an attack
 * splits off the player it hits into a magic and a ranged ball at two others. Every eleventh attack
 * is a big ball whose damage is shared by everyone within a tile of its target.
 *
 * At 66.6% and 33.3% it takes one player into the Shadow Realm and pulls the rest to the arena's
 * south-west corner. The runner follows the red path to the portal (losing 1-3 every 7 ticks);
 * their tile glows on the real maze, and anyone else on a wrong tile hurts themselves and their
 * neighbours (6.67% of their hitpoints + 15 a tick). Once someone reaches the fourth row a tornado
 * follows the path. Clearing the maze ends the phase.
 *
 * Wiki numbers. Guesses (TSPS's): melee or a ball half the time when someone is beside it, the maze
 * generator, the pulled-to and return tiles, the tornado's 35-45, the big ball's 70 for one player
 * and its steps between the wiki's 121 (two) and 188 (five); all prayers go off, not only the
 * protection ones (the engine has no lock-out of protection prayers).
 */
class SotetsegRuntime(env: TobRoomEnv, raid: TobRaid) :
    TobRoomRuntime(env, raid, TobRoom.Sotetseg) {
    private class Maze(val runner: Player, val path: List<CoordGrid>, val solo: Boolean) {
        val realPath: Set<CoordGrid> = path.map(::toMaze).toSet()
        var glow: CoordGrid? = null
        val wrong = HashSet<CoordGrid>()
        var tornado: Npc? = null
        var tornadoStep = 0
        var drain = 0
    }

    var sotetseg: Npc? = null
        private set

    private var attackIn = ATTACK_TICKS
    private var balls = 0
    private var mazesDone = 0
    private var maze: Maze? = null
    private var dying = false

    val inMaze: Boolean
        get() = maze != null

    val runner: Player?
        get() = maze?.runner

    override fun build() {
        val npc = spawn(SotetsegNpcs.waiting, SPAWN, scale = true, facing = Direction.South)
        sotetseg = npc
        bossBar = npc
    }

    override fun onStart() {
        sotetseg?.let { transform(it, SotetsegNpcs.fighting) }
    }

    override fun bar(): TobBar {
        val npc = sotetseg ?: return TobBar.NONE
        return barOf(npc, if (maze != null) TobBar.GREYED else TobBar.REGULAR)
    }

    override fun onTick() {
        val npc = sotetseg ?: return
        if (dying) {
            return
        }
        maze?.let {
            tickMaze(it)
            return
        }
        if (
            mazesDone < MAZE_AT.size &&
                npc.hitpoints * 1000 <= npc.baseHitpointsLvl * MAZE_AT[mazesDone]
        ) {
            startMaze()
            return
        }
        if (--attackIn <= 0) {
            attackIn = ATTACK_TICKS
            attack(npc)
        }
    }

    override fun hitpointsGone(npc: Npc) {
        if (npc !== sotetseg || dying) {
            return
        }
        if (maze != null) {
            // It cannot die during a maze; the hit leaves it at one.
            npc.hitpoints = 1
            return
        }
        dying = true
        npc.anim(SotetsegSeqs.death)
        complete()
        later(ticksOf(SotetsegSeqs.death, least = 4)) { despawn(npc) }
    }

    override fun onComplete() {
        maze?.let { endMaze(it) }
    }

    // Attacks
    // -------------------------------------------------------------------------------------------

    private fun attack(npc: Npc) {
        val players = fightPlayers()
        if (players.isEmpty()) {
            return
        }
        val target = pick(players)
        npc.facePlayer(target)
        if (gap(npc, target.coords) <= 1 && env.random.of(2) == 0) {
            npc.anim(SotetsegSeqs.melee)
            sound(SotetsegSynths.melee, delay = 30)
            val attack = NpcAttack(NpcAttackStyle.Melee, maxHit = MELEE_MAX, hitDelay = 2)
            strike(npc, target, attack, through = 50, playAnim = false)
            return
        }
        npc.anim(SotetsegSeqs.ball)
        sound(SotetsegSynths.ball)
        if (balls >= BALLS_BEFORE_BIG) {
            balls = 0
            bigBall(npc, target, players)
            return
        }
        balls++
        val from = centre(npc)
        val to = realOf(target.coords) ?: return
        val ticks = projectile(from, to, SotetsegSpots.magic, 23..203, heights = 26 to 31)
        later(ticks) {
            if (maze == null && target in fightPlayers()) {
                ballLands(target, NpcAttackStyle.Magic)
                ricochet(target)
            }
        }
    }

    private fun ricochet(from: Player) {
        val others = shuffled(fightPlayers().filter { it !== from }).take(2)
        val first = if (env.random.of(2) == 0) NpcAttackStyle.Magic else NpcAttackStyle.Ranged
        val start = realOf(from.coords) ?: return
        others.forEachIndexed { index, player ->
            val style =
                if (index == 0) first
                else if (first == NpcAttackStyle.Magic) NpcAttackStyle.Ranged
                else NpcAttackStyle.Magic
            val to = realOf(player.coords) ?: return@forEachIndexed
            val spot =
                if (style == NpcAttackStyle.Magic) SotetsegSpots.magic else SotetsegSpots.ranged
            val ticks = projectile(start, to, spot, 15..216, heights = 31 to 24)
            later(ticks) {
                if (maze == null && player in fightPlayers()) {
                    ballLands(player, style)
                }
            }
        }
    }

    private fun ballLands(player: Player, style: NpcAttackStyle) {
        if (prays(player, style)) {
            return
        }
        hurt(player, roll(1, BALL_MAX))
        player.disablePrayers()
    }

    private fun bigBall(npc: Npc, target: Player, players: List<Player>) {
        for (player in players) {
            player.mes(
                "<col=ef1020>${target.displayName} has discovered a large ball of energy coming " +
                    "their way...</col>"
            )
        }
        val present = players.size
        val from = centre(npc)
        val to = realOf(target.coords) ?: return
        val ticks = projectile(from, to, SotetsegSpots.big_ball, 23..480, heights = 26 to 31)
        later(ticks) {
            if (maze != null || target !in fightPlayers()) {
                return@later
            }
            val sharing =
                fightPlayers().filter {
                    maxOf(
                        kotlin.math.abs(it.coords.x - target.coords.x),
                        kotlin.math.abs(it.coords.z - target.coords.z),
                    ) <= 1
                }
            val total = bigBallDamage(present)
            val each = (total + sharing.size - 1) / sharing.size.coerceAtLeast(1)
            for (player in sharing) {
                player.spotanim(SotetsegSpots.big_impact)
                hurt(player, each)
            }
        }
    }

    // The maze
    // ------------------------------------------------------------------------------------------

    private fun startMaze() {
        val players = fightPlayers()
        if (players.isEmpty()) {
            return
        }
        val runner = pick(players)
        val path = generatePath { env.random.of(it) }
        val maze = Maze(runner, path, solo = players.size == 1)
        this.maze = maze
        for (tile in path) {
            swapLoc(tile, listOf(SotetsegLocs.dark), SotetsegLocs.light)
        }
        for (player in players) {
            player.clearInteraction()
            val to = if (player === runner) REALM_START else PULLED_TO
            if (player === runner) {
                player.mes("<col=ef1020>Sotetseg chooses you...</col>")
            }
            env.raids.fadeMove(player) { teleport(player, to) }
        }
    }

    private fun tickMaze(maze: Maze) {
        val runner = maze.runner
        val member = raid.member(runner)
        if (member == null || member.left || member.jailed) {
            endMaze(maze)
            return
        }
        if (++maze.drain >= DRAIN_EVERY) {
            maze.drain = 0
            runner.spotanim(SotetsegSpots.drain)
            later(2) { hurt(runner, roll(1, 3)) }
        }
        mirrorRunner(maze)
        punishWrongTiles(maze)
        tickTornado(maze)
    }

    private fun mirrorRunner(maze: Maze) {
        val at = realOf(maze.runner.coords) ?: return
        if (at !in REALM) {
            return
        }
        val tile = toMaze(at)
        if (maze.glow == tile) {
            return
        }
        maze.glow?.let {
            swapLoc(it, listOf(SotetsegLocs.light, SotetsegLocs.wrong), SotetsegLocs.dark)
        }
        maze.glow = tile
        swapLoc(tile, listOf(SotetsegLocs.dark, SotetsegLocs.wrong), SotetsegLocs.light)
    }

    private fun punishWrongTiles(maze: Maze) {
        val players = fightPlayers().filter { it !== maze.runner }
        val standing = players.mapNotNull { realOf(it.coords) }.toSet()
        for (tile in maze.wrong.toList()) {
            if (tile !in standing) {
                maze.wrong.remove(tile)
                swapLoc(tile, listOf(SotetsegLocs.wrong), SotetsegLocs.dark)
            }
        }
        for (player in players) {
            val at = realOf(player.coords) ?: continue
            if (at !in MAZE_FLOOR || at in maze.realPath) {
                continue
            }
            val fresh = maze.wrong.add(at)
            if (fresh) {
                swapLoc(at, listOf(SotetsegLocs.dark, SotetsegLocs.light), SotetsegLocs.wrong)
                sound(SotetsegSynths.zap)
                graphic(SotetsegSpots.explosion, at, height = 10)
            }
            for (other in players) {
                if (
                    maxOf(
                        kotlin.math.abs(other.coords.x - player.coords.x),
                        kotlin.math.abs(other.coords.z - player.coords.z),
                    ) > 1
                ) {
                    continue
                }
                if (fresh) {
                    realOf(other.coords)?.let {
                        projectile(at, it, SotetsegSpots.zap, 0..15, heights = 0 to 16)
                    }
                }
                hurt(other, other.stat(stats.hitpoints) * WRONG_SHARE / 10000 + WRONG_FLAT)
            }
        }
    }

    private fun tickTornado(maze: Maze) {
        val players =
            if (maze.solo) listOf(maze.runner) else fightPlayers().filter { it !== maze.runner }
        val onTile: (CoordGrid) -> CoordGrid = if (maze.solo) ({ it }) else ::toMaze
        val tornado = maze.tornado
        if (tornado == null) {
            val box = if (maze.solo) REALM else MAZE_FLOOR
            val triggered =
                players.any { player ->
                    realOf(player.coords)?.let { it in box && it.z >= 0 && it.z - box.minZ >= 3 } ==
                        true
                }
            if (triggered) {
                maze.tornado =
                    spawn(SotetsegNpcs.tornado, onTile(maze.path[0]), ignoreCollision = true)
                maze.tornadoStep = 0
            }
            return
        }
        for (player in players) {
            if (gap(tornado, player.coords) == 0) {
                hurt(player, roll(TORNADO_MIN, TORNADO_MAX))
            }
        }
        if (++maze.tornadoStep >= maze.path.size) {
            despawn(tornado)
            maze.tornado = null
            return
        }
        tornado.walk(regionOf(onTile(maze.path[maze.tornadoStep])))
    }

    /** The runner stepped into the portal: the maze is over. */
    fun leaveRealm(player: Player): Boolean {
        val maze = maze ?: return false
        if (maze.runner !== player) {
            return false
        }
        endMaze(maze)
        return true
    }

    private fun endMaze(maze: Maze) {
        this.maze = null
        mazesDone++
        attackIn = ATTACK_TICKS
        maze.tornado?.let(::despawn)
        restoreLocs()
        val runner = maze.runner
        if (realOf(runner.coords)?.level == REALM.level && raid.member(runner)?.left == false) {
            env.raids.fadeMove(runner) { teleport(runner, RETURN) }
        }
    }

    private fun centre(npc: Npc): CoordGrid =
        (realOf(npc.coords) ?: SPAWN).translate(npc.size / 2, npc.size / 2)

    companion object {
        val SPAWN = CoordGrid(3277, 4326, 0)
        val MAZE_FLOOR = TobBox(3273, 4310, 3286, 4324, 0)
        val REALM = TobBox(3354, 4311, 3367, 4325, 3)
        val REALM_START = CoordGrid(3360, 4309, 3)
        val PULLED_TO = CoordGrid(3274, 4307, 0)
        val RETURN = CoordGrid(3275, 4327, 0)

        const val ATTACK_TICKS = 5
        const val MELEE_MAX = 45
        const val BALL_MAX = 50
        const val BALLS_BEFORE_BIG = 10

        /** Thousandths of its hitpoints at which the mazes start. */
        val MAZE_AT = listOf(666, 333)
        private const val DRAIN_EVERY = 7

        /** 6.67% of the player's hitpoints, in hundredths of a percent, plus 15. */
        private const val WRONG_SHARE = 667
        private const val WRONG_FLAT = 15
        private const val TORNADO_MIN = 35
        private const val TORNADO_MAX = 45

        /** Wiki: 121 for two, 188 for five (the steps between are a straight line); 70 alone. */
        fun bigBallDamage(present: Int): Int =
            when {
                present <= 1 -> 70
                else -> 121 + (present - 2) * 67 / 3
            }

        /** A realm tile's twin on the arena's maze. */
        fun toMaze(realm: CoordGrid): CoordGrid =
            CoordGrid(
                MAZE_FLOOR.minX + realm.x - REALM.minX,
                MAZE_FLOOR.minZ + realm.z - REALM.minZ,
                0,
            )

        /**
         * The maze (TSPS's generator): from a random tile on the bottom row, sideways up to five
         * tiles then up two or four rows, until the top row; then one more tile to the portal.
         */
        fun generatePath(random: (Int) -> Int): List<CoordGrid> {
            val width = REALM.maxX - REALM.minX + 1
            val height = REALM.maxZ - REALM.minZ + 1
            fun pick(min: Int, max: Int) = min + random(max - min + 1)
            val tiles = LinkedHashSet<CoordGrid>()
            fun add(x: Int, z: Int) {
                tiles += CoordGrid(REALM.minX + x, REALM.minZ + z, REALM.level)
            }
            var x = pick(0, width - 1)
            add(x, 0)
            var z = 1
            while (z < height - 1) {
                val nextX = pick(maxOf(0, x - 5), minOf(width - 1, x + 5))
                val step = if (nextX > x) 1 else -1
                var cx = x
                while (true) {
                    add(cx, z)
                    if (cx == nextX) break
                    cx += step
                }
                val nextZ = minOf(height - 1, z + if (random(2) == 0) 2 else 4)
                for (cz in z until nextZ) add(nextX, cz)
                x = nextX
                z = nextZ
            }
            add(x, height - 1)
            return tiles.toList()
        }
    }
}

class TobSotetsegScript
@Inject
constructor(private val rooms: TobRoomRegistry, private val raids: TobRaids) : PluginScript() {
    override fun ScriptContext.startup() {
        rooms.register(TobRoom.Sotetseg) { env, raid -> SotetsegRuntime(env, raid) }
        tobBosses(rooms, listOf(SotetsegNpcs.waiting, SotetsegNpcs.fighting))
        tobBosses(rooms, listOf(SotetsegNpcs.tornado))
        onOpLoc1(SotetsegLocs.portal) {
            val raid = raids.raidOf(player) ?: return@onOpLoc1
            val room = rooms.runtime(raid, TobRoom.Sotetseg) as? SotetsegRuntime ?: return@onOpLoc1
            if (!room.leaveRealm(player)) {
                mes("The portal does not respond.")
            }
        }
    }
}
