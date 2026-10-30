package org.rsmod.content.areas.misc.isleofsouls.dagannothkings

import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstance
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstanceType
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveDirector
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.type.npc.NpcType
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The three kings (OSRS Wiki, "Dagannoth Kings"): each fights with one style, is weak to the style
 * that beats it on the combat triangle and has high defence against the other two (their stats and
 * defences are the cache's and `npcs.toml`'s). [home] is the south-west tile of the spot the spawn
 * list has them on in the real lair; each roams round its own spot and only picks fights there, so
 * a player can take them on one at a time, but once one is on a player it follows them anywhere.
 */
enum class DkKing(val type: NpcType, val home: CoordGrid, val ranged: Boolean) {
    /** Melee (slash, up to 26). Weak to magic. */
    Rex(dk_npcs.rex, CoordGrid(2913, 4445, 0), ranged = false),

    /** Magic (up to 50, every player round its target). Weak to ranged. */
    Prime(dk_npcs.prime, CoordGrid(2911, 4451, 0), ranged = true),

    /** Ranged (up to 30, every player near its target). Weak to melee. */
    Supreme(dk_npcs.supreme, CoordGrid(2906, 4448, 0), ranged = true);

    companion object {
        /** Each king is 3x3. */
        const val SIZE = 3

        /** Every king attacks once every 4 ticks (wiki). */
        const val ATTACK_RATE = 4

        /** Ticks between a king's death and its return (wiki: 150, or 90 seconds). */
        const val RESPAWN_TICKS = 150

        /**
         * How far from a king a player can be before it notices them. The wiki says only that they
         * are aggressive; 8 is a guess (the God Wars generals' range).
         */
        const val HUNT_RANGE = 8

        /**
         * How far from its [home] a player can be for a king to pick them as a new target, so each
         * starts fights on its own side of the lair. A guess: the wiki only says each "spawns and
         * roams" on its side. Once it has a target it follows them anywhere in the lair.
         */
        const val SIDE = 12

        /**
         * How far from its [home] a king follows a target before the engine drops the chase: more
         * than the whole lair (31 by 29 tiles), so running to the far side does not shake it off.
         */
        const val LEASH = 40

        /** How far a king strolls from its [home] when it has nobody to fight. A guess. */
        const val WANDER = 4

        /**
         * Prime and Supreme shoot from up to this many tiles ("very long-ranged", wiki): the whole
         * lair, so they hit their target anywhere in it they can see without walking over.
         */
        const val ATTACK_RANGE = 32

        fun of(npc: Npc): DkKing? = entries.firstOrNull { it.type.id == npc.type.id }
    }
}

/**
 * The spinolyps that sit in the water round the edge of the lair (OSRS Wiki, "Spinolyp"). Each spot
 * is one of three cache kinds, a spinolyp with its own "suspicious water" twin. The 25 spots are
 * the wiki's 25, read from the spawn list's two copies of the lair (each spot was a spinolyp in one
 * and suspicious water in the other, or the same in both).
 */
object DkSpinolyps {
    class Kind(val visible: NpcType, val submerged: NpcType)

    class Spot(val coords: CoordGrid, val kind: Kind)

    private val a = Kind(dk_npcs.spinolyp_a, dk_npcs.water_a)
    private val b = Kind(dk_npcs.spinolyp_b, dk_npcs.water_b)
    private val c = Kind(dk_npcs.spinolyp_c, dk_npcs.water_c)

    val kinds = listOf(a, b, c)

    /** Offsets in the lair's map square (x 2880, z 4416). */
    val spots: List<Spot> =
        listOf(
                a to (24 to 47),
                a to (39 to 47),
                a to (48 to 40),
                a to (19 to 42),
                a to (28 to 48),
                a to (42 to 47),
                a to (50 to 37),
                b to (17 to 25),
                b to (18 to 39),
                b to (21 to 20),
                b to (30 to 17),
                b to (36 to 48),
                b to (17 to 35),
                b to (26 to 17),
                b to (38 to 17),
                b to (50 to 24),
                c to (35 to 17),
                c to (46 to 43),
                c to (50 to 30),
                c to (45 to 20),
                c to (17 to 30),
                c to (19 to 22),
                c to (21 to 45),
                c to (32 to 49),
                c to (50 to 34),
            )
            .map { (kind, offset) ->
                Spot(CoordGrid(SQUARE_X + offset.first, SQUARE_Z + offset.second, 0), kind)
            }

    private const val SQUARE_X = 2880
    private const val SQUARE_Z = 4416

    /** A spinolyp attacks every 4 ticks (wiki). */
    const val ATTACK_RATE = 4

    /**
     * Players within this many tiles of a spinolyp get shot at. A guess: about a third of the lair.
     */
    const val RANGE = 10

    /** Ticks before a killed spinolyp is back (wiki: 10). */
    const val RESPAWN_TICKS = 10

    /**
     * How long a spinolyp stays up, and how long it stays under as suspicious water. The wiki only
     * says "on occasion" and "after a while"; these are guesses.
     */
    val UP_TICKS = 25..50
    val UNDER_TICKS = 8..20

    fun isSubmerged(npc: Npc): Boolean = kinds.any { it.submerged.id == npc.visType.id }

    fun isSpinolyp(type: NpcType): Boolean =
        kinds.any { it.visible.id == type.id || it.submerged.id == type.id }
}

/**
 * The Dagannoth Kings' lair in ordinary map coordinates (the real map, Waterbirth Island Dungeon,
 * sixth level). The instance is a copy of the block of 5x5 zones x 2896-2935, z 4432-4471 on all
 * levels; the lair's floor is x 2899-2929 by z 4435-4463, with the Kings' ladder up at (2899, 4449)
 * on its west side. Read from the cache with `tools/dungeons/collision.py`.
 */
object DkArena {
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 362, copyZoneZ = 554) {
                zoneWidth = 5
                zoneLength = 5
            }
        }

    /** The real ladder up out of the lair. */
    val ladderUp = CoordGrid(2899, 4449, 0)

    /** Where players appear: the tile east of the ladder up. */
    val start = CoordGrid(2900, 4449, 0)

    /** Where players leave to: beside the ladder down on the isle. */
    val exit = DkLadder.front
}

/**
 * The Kings' ladder down on the isle: a copy of the real one, on the field south of the plaza, west
 * of the dungeon entrances (a free spot found with `tools/dungeons/collision.py`, to be moved by
 * the user later). [front] is the tile south of it, where players land when they climb back up.
 */
object DkLadder {
    val coords = CoordGrid(2176, 2808, 0)
    val angle = LocAngle.South
    val front = CoordGrid(2176, 2807, 0)
}

/** The lair as a kind of team instance. Registered with [TeamInstances] by `DkScript`. */
object DkInstance {
    const val NAME = "dagannoth_kings"

    val type =
        TeamInstanceType(
            name = NAME,
            template = DkArena.template,
            entry = DkArena.start,
            exit = DkArena.exit,
        )
}

/** One king's place in a run: the npc while it is up, and when it comes back once it has died. */
class DkKingSlot(val king: DkKing) {
    var npc: Npc? = null
    var respawnAt = 0
}

/** One spinolyp spot in a run. */
class DkSpinolypSlot(val spot: DkSpinolyps.Spot) {
    var npc: Npc? = null
    var respawnAt = 0

    /** When it dives (while up) or comes back up (while under). */
    var switchAt = 0
}

/**
 * One team's lair. It lives as long as its [instance] has someone. The kings and the spinolyps are
 * spawned once when the first player arrives, and each comes back on its own timer after it dies.
 */
class DkRun(instance: TeamInstance, startClock: Int) : WaveRun(instance, startClock) {
    override val waveCount: Int
        get() = 1

    val kings = DkKing.entries.associateWith { DkKingSlot(it) }
    val spinolyps = DkSpinolyps.spots.map { DkSpinolypSlot(it) }

    /**
     * The kings each player has killed (as the one who gets the drops) since they last came in, for
     * the task "Defeat the Dagannoth Kings Without Leaving". Keyed by username.
     */
    val visitKills = HashMap<String, MutableSet<DkKing>>()

    init {
        // The kings are in the lair from the start, not after a wave pause.
        nextWaveAt = startClock + FIRST_SPAWN_DELAY
    }

    fun kingOf(npc: Npc): DkKingSlot? = kings.values.firstOrNull { it.npc === npc }

    fun spinolypOf(npc: Npc): DkSpinolypSlot? = spinolyps.firstOrNull { it.npc === npc }

    override fun allNpcs(): List<Npc> =
        kings.values.mapNotNull { it.npc } + spinolyps.mapNotNull { it.npc }

    override fun clearNpcs() {
        super.clearNpcs()
        kings.values.forEach { it.npc = null }
        spinolyps.forEach { it.npc = null }
    }

    private companion object {
        const val FIRST_SPAWN_DELAY = 1
    }
}

/**
 * Runs a team's lair of the Dagannoth Kings. The instance loop (enter, leave, the run tick,
 * clean-up) is [WaveDirector]; the run has one "wave", which puts the three kings and the 25
 * spinolyps in the lair, and never ends: each king comes back the wiki's 150 ticks after it dies
 * (scaled by the event's respawn speed), each spinolyp 10 ticks after.
 *
 * Every tick the run aims the kings, as the other team bosses are aimed: a king with nobody to
 * fight goes for the nearest player within [DkKing.HUNT_RANGE] of it who is on its side of the lair
 * ([DkKing.SIDE]), and then stays on them wherever they go in the lair ([DkKing.LEASH]); Prime and
 * Supreme shoot across the whole lair ([DkKing.ATTACK_RANGE]). A king that is hit fights back as
 * usual. The spinolyps never move: each shoots at the nearest player in range, and now and then
 * dives into the water as suspicious water for a while (see [DkSpinolyps]). How each of them
 * attacks is [DkAttacks].
 */
class DagannothKings
@Inject
constructor(
    instances: TeamInstances,
    npcRepo: NpcRepository,
    npcTypes: NpcTypeList,
    interactions: AiPlayerInteractions,
    collision: CollisionFlagMap,
    mapClock: MapClock,
    private val players: PlayerList,
    private val random: GameRandom,
    private val attacks: DkAttacks,
) : WaveDirector<DkRun>(instances, npcRepo, npcTypes, interactions, collision, mapClock) {
    override val instanceType: TeamInstanceType
        get() = DkInstance.type

    override fun createRun(instance: TeamInstance): DkRun = DkRun(instance, mapClock.cycle)

    override fun waveMessage(run: DkRun): String? = null

    override fun startPlayer(player: Player) {
        player.softTimer(dk_timers.tick, 1)
        runOf(player)?.visitKills?.remove(player.username)
        player.mes("You climb down into the Dagannoth Kings' lair.")
    }

    override fun spawnWave(run: DkRun, inside: List<Player>) {
        for (slot in run.kings.values) {
            spawnKing(run, slot)
        }
        for (slot in run.spinolyps) {
            spawnSpinolyp(run, slot)
        }
    }

    /** The kings and spinolyps are not the run's `monsters`: they are aimed in [onWaveTick]. */
    override fun engage(run: DkRun, npc: Npc, target: Player): Boolean = false

    override fun onWin(run: DkRun, inside: List<Player>) {}

    override fun onWaveTick(run: DkRun) {
        val now = mapClock.cycle
        val inside = instances.playersIn(run.instance).filter { it.isValidTarget() }
        for (slot in run.kings.values) {
            val npc = slot.npc
            if (npc == null) {
                if (now >= slot.respawnAt) {
                    spawnKing(run, slot)
                }
                continue
            }
            aim(run, slot.king, npc, inside)
        }
        for (slot in run.spinolyps) {
            tickSpinolyp(run, slot, inside, now)
        }
    }

    private fun spawnKing(run: DkRun, slot: DkKingSlot) {
        slot.npc = spawn(run, slot.king.type, slot.king.home)
    }

    private fun spawnSpinolyp(run: DkRun, slot: DkSpinolypSlot) {
        val npc = spawn(run, slot.spot.kind.visible, slot.spot.coords)
        slot.npc = npc
        slot.switchAt = mapClock.cycle + random.of(DkSpinolyps.UP_TICKS)
    }

    /**
     * Keeps [npc] on a player it may fight: the one it has, wherever they go in the lair, or else
     * the nearest one within its hunt range and on its side of the lair. With nobody, it wanders
     * round its home.
     */
    private fun aim(run: DkRun, king: DkKing, npc: Npc, inside: List<Player>) {
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || npc.isDelayed) {
            return
        }
        val home = run.instance.region.normal[king.home]
        val current = (npc.interaction as? InteractionPlayerOp)?.target
        if (current != null && current in inside) {
            return
        }
        val target =
            inside
                .filter { npc.isWithinDistance(it, DkKing.HUNT_RANGE) && onSide(home, it) }
                .minByOrNull { it.distanceTo(npc) }
        if (target != null) {
            engage(npc, target, ranged = king.ranged)
            return
        }
        if (current != null || npc.mode != NpcMode.Wander) {
            npc.resetMode()
            npc.mode = NpcMode.Wander
        }
    }

    private fun onSide(home: CoordGrid, player: Player): Boolean =
        player.coords.chebyshevDistance(home) <= DkKing.SIDE

    private fun tickSpinolyp(run: DkRun, slot: DkSpinolypSlot, inside: List<Player>, now: Int) {
        val npc = slot.npc
        if (npc == null) {
            if (now >= slot.respawnAt) {
                spawnSpinolyp(run, slot)
            }
            return
        }
        if (!npc.isSlotAssigned || npc.hitpoints <= 0 || npc.isDelayed) {
            return
        }
        val submerged = DkSpinolyps.isSubmerged(npc)
        if (now >= slot.switchAt) {
            if (submerged) surface(npc, slot) else dive(npc, slot)
            return
        }
        if (submerged || npc.actionDelay > now) {
            return
        }
        val target =
            inside
                .filter { npc.isWithinDistance(it, DkSpinolyps.RANGE) }
                .minByOrNull { it.distanceTo(npc) } ?: return
        npc.facePlayer(target)
        attacks.spinolyp(npc, target)
        npc.actionDelay = now + DkSpinolyps.ATTACK_RATE
    }

    /** The spinolyp goes under: shown as its suspicious water, which cannot be attacked. */
    fun dive(npc: Npc, slot: DkSpinolypSlot) {
        npc.anim(dk_seqs.spinolyp_dive)
        npc.resetFaceEntity()
        npc.transmog(npcTypes[slot.spot.kind.submerged], Int.MAX_VALUE)
        slot.switchAt = mapClock.cycle + random.of(DkSpinolyps.UNDER_TICKS)
    }

    /** The spinolyp comes back up and can attack, and be attacked, again. */
    fun surface(npc: Npc, slot: DkSpinolypSlot) {
        npc.resetTransmog()
        npc.anim(dk_seqs.spinolyp_surface)
        npc.actionDelay = mapClock.cycle + DkSpinolyps.ATTACK_RATE
        slot.switchAt = mapClock.cycle + random.of(DkSpinolyps.UP_TICKS)
    }

    /**
     * A king has died. Returns where its drops fall (the middle of its 3x3), and schedules its
     * return. The player who gets the drops has the kill counted for this visit.
     */
    fun kingDied(npc: Npc): CoordGrid? {
        val run = runOf(npc) ?: return null
        val slot = run.kingOf(npc) ?: return null
        slot.npc = null
        slot.respawnAt = mapClock.cycle + npcRepo.respawnSpeed.scale(DkKing.RESPAWN_TICKS)
        npc.findHero(players)?.let { hero ->
            run.visitKills.getOrPut(hero.username) { mutableSetOf() } += slot.king
        }
        return npc.coords.translate(DkKing.SIZE / 2, DkKing.SIZE / 2)
    }

    /** A spinolyp has died: it is back after a short while. */
    fun spinolypDied(npc: Npc) {
        val run = runOf(npc) ?: return
        val slot = run.spinolypOf(npc) ?: return
        slot.npc = null
        slot.respawnAt = mapClock.cycle + npcRepo.respawnSpeed.scale(DkSpinolyps.RESPAWN_TICKS)
    }

    /** The kings [player] has killed since they came into their team's lair. */
    fun killsThisVisit(player: Player): Set<DkKing> =
        runOf(player)?.visitKills?.get(player.username).orEmpty()

    /** How many of [player]'s team are in their lair right now, for the ladder's "Peek". */
    fun teammatesInside(player: Player): Int {
        val instance = instances.teamInstance(player, instanceType) ?: return 0
        return instances.playersIn(instance).size
    }
}
