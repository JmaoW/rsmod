package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import jakarta.inject.Inject
import org.rsmod.api.drops.DropResult
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.interact.InteractionPlayerOp
import org.rsmod.game.type.npc.NpcTypeList
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The maze's monsters: who a lit room holds, who they go for, and what they drop.
 *
 * Rooms (Near-Reality's, as TSPS found them; the wiki lists only the kinds): a room marked for
 * monsters when it was stocked gets, three times in four away from the rim, one to four weak
 * monsters (rat, spider, bat), otherwise two strong ones (unicorn, scorpion, wolf). A demi-boss
 * room holds its demi-boss in the middle and nothing else. Nothing respawns, and the maze takes
 * them with it when it closes.
 *
 * Every monster is aggressive (wiki). One goes for the nearest player in its own room and stays on
 * them until they leave the room or die. Nothing attacks into the start room, which is safe.
 */
class GauntletMonsters
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val npcTypes: NpcTypeList,
    private val collision: CollisionFlagMap,
    private val interactions: AiPlayerInteractions,
) {
    /**
     * Fills a newly lit [room] with its monsters (if it is to have any) and returns them. Does
     * nothing for the start and boss rooms, or a room that was filled already.
     */
    fun populate(run: GauntletRun, room: GauntletRoom, pick: (Int) -> Int): List<Npc> {
        if (room.kind == RoomKind.Start || room.kind == RoomKind.Boss || room.populated) {
            return emptyList()
        }
        room.populated = true
        val spawned = ArrayList<Npc>()
        val demi = room.demiBoss
        if (demi != null) {
            val monster = GauntletMonster.entries.first { it.name == demi.name }
            spawned += spawn(run, room, monster, DEMI_X, DEMI_Z)
            return spawned
        }
        if (!room.monsters) {
            return spawned
        }
        val rim = room.x == 0 || room.z == 0 || room.x == GRID_LAST || room.z == GRID_LAST
        val weak = !rim && pick(100) < WEAK_ODDS
        val count = if (weak) 1 + pick(MAX_WEAK) else STRONG_COUNT
        val taken = ArrayList<Triple<Int, Int, Int>>()
        repeat(count) {
            val kinds = if (weak) GauntletMonster.WEAK else GauntletMonster.STRONG
            val monster = kinds[pick(kinds.size)]
            val size = npcTypes[monster.type(run.mode)].size
            val spots =
                squares(run, room, size).filter { (x, z) ->
                    taken.all { (ox, oz, osize) ->
                        Math.abs(ox - x) > osize + 1 || Math.abs(oz - z) > osize + 1
                    }
                }
            if (spots.isEmpty()) {
                return@repeat
            }
            val (x, z) = spots[pick(spots.size)]
            taken += Triple(x, z, size)
            spawned += spawn(run, room, monster, x, z)
        }
        return spawned
    }

    private fun spawn(
        run: GauntletRun,
        room: GauntletRoom,
        monster: GauntletMonster,
        x: Int,
        z: Int,
    ): Npc {
        val npc = Npc(npcTypes[monster.type(run.mode)], run.roomTile(room, x, z))
        npc.mode = NpcMode.None
        npcRepo.add(npc, LIFETIME)
        run.monsters += npc
        return npc
    }

    /** South-west tiles of free floor of [size], away from the walls and doorways. */
    private fun squares(run: GauntletRun, room: GauntletRoom, size: Int): List<Pair<Int, Int>> {
        val found = ArrayList<Pair<Int, Int>>()
        for (x in FLOOR_MIN..FLOOR_MAX - size + 1) {
            for (z in FLOOR_MIN..FLOOR_MAX - size + 1) {
                var fits = true
                for (dx in 0 until size) {
                    for (dz in 0 until size) {
                        val at = run.roomTile(room, x + dx, z + dz)
                        if (collision[at.x, at.z, at.level] and BLOCKED != 0) {
                            fits = false
                        }
                    }
                }
                if (fits) {
                    found += x to z
                }
            }
        }
        return found
    }

    // Going for the players ---------------------------------------------------------------------

    /** Aims every monster of [run] at the nearest player in its room. Called once a tick. */
    fun tick(run: GauntletRun) {
        if (run.monsters.isEmpty()) {
            return
        }
        val safe = run.layout.start
        val targets =
            run.members.filter {
                it.isValidTarget() && run.contains(it.coords) && run.roomAt(it.coords) !== safe
            }
        for (npc in run.monsters.toList()) {
            if (!npc.isSlotAssigned) {
                run.monsters.remove(npc)
                continue
            }
            if (npc.hitpoints <= 0) {
                continue
            }
            val room = run.roomAt(npc.coords)
            val current = (npc.interaction as? InteractionPlayerOp)?.target
            if (
                current != null &&
                    current in targets &&
                    hunting(npc) &&
                    run.roomAt(current.coords) === room
            ) {
                npc.spawnCoords = current.coords
                continue
            }
            val target =
                targets.filter { run.roomAt(it.coords) === room }.minByOrNull { it.distanceTo(npc) }
            if (target == null) {
                if (hunting(npc)) {
                    npc.mode = NpcMode.None
                }
                continue
            }
            val monster = GauntletMonster.of(npc.type) ?: continue
            if (monster.shoots) npc.apPlayer2(target, interactions)
            else npc.opPlayer2(target, interactions)
            // The engine drops a chase once the target is further than the monster's range from
            // where it spawned; the leash moves along with the target.
            npc.spawnCoords = target.coords
        }
    }

    private fun hunting(npc: Npc): Boolean =
        npc.mode == NpcMode.OpPlayer2 || npc.mode == NpcMode.ApPlayer2

    // Their ends --------------------------------------------------------------------------------

    /** The run a monster belongs to, among [runs]. */
    fun runOf(npc: Npc, runs: Collection<GauntletRun>): GauntletRun? =
        runs.firstOrNull { run -> run.monsters.any { it === npc } }

    /** A monster was killed: counts the points for [hero] and returns what it drops for them. */
    fun killed(
        run: GauntletRun,
        npc: Npc,
        hero: Player,
        owns: (org.rsmod.game.type.obj.ObjType) -> Boolean,
        pick: (Int) -> Int,
    ): List<DropResult> {
        run.monsters.removeAll { it === npc }
        val monster = GauntletMonster.of(npc.type) ?: return emptyList()
        run.addPoints(hero, monster.tier.points)
        return GauntletDrops.roll(monster, run.mode.items, run.state(hero), owns, pick)
    }

    /** Removes every monster the run still has. */
    fun clear(run: GauntletRun) {
        for (npc in run.monsters) {
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        run.monsters.clear()
    }

    companion object {
        /** A monster never expires on its own; the run removes them. About 16 hours. */
        const val LIFETIME = 100_000

        private const val GRID_LAST = GauntletGrid.SIZE - 1
        private const val WEAK_ODDS = 75
        private const val MAX_WEAK = 4
        private const val STRONG_COUNT = 2

        /** Room-relative: a demi-boss stands in the middle of its room. */
        private const val DEMI_X = 7
        private const val DEMI_Z = 7

        /** The floor monsters may stand on, clear of the walls and the doorways. */
        private const val FLOOR_MIN = 3
        private const val FLOOR_MAX = 12

        private const val BLOCKED =
            CollisionFlag.BLOCK_WALK or CollisionFlag.LOC or CollisionFlag.BLOCK_NPCS
    }
}
