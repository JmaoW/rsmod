package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.map.CoordGrid

/** The five places a wave's monsters appear in the Fight Cave. */
enum class SpawnPoint {
    SouthEast,
    SouthWest,
    Centre,
    NorthWest,
    South,
}

/**
 * The cave's geometry, in ordinary map coordinates (the instance is a copy of this square).
 *
 * Each spawn point is the south-west tile of the monster's footprint, and every one has room for
 * the biggest monsters (size 5). Cave monsters have no pathfinding: they step straight towards a
 * player and stop at the first wall, as in the real game. So the tiles were picked from where a
 * monster of every size really can walk to the start tile (a test checks that), which rules out the
 * closed-off north-west room and the pocket behind the south-east rock.
 */
object FightCaveArena {
    /** The cave's map square (37_79) as a block of 8x8 zones, copied on every level. */
    val template: RegionTemplate =
        RegionTemplate.create {
            copyAllLevels(copyZoneX = 296, copyZoneZ = 632) {
                zoneWidth = 8
                zoneLength = 8
            }
        }

    /** Where players appear (the real cave's start, at the top right). */
    val start = CoordGrid(2413, 5117, 0)

    /** The exit loc inside the cave, next to where players start. */
    val exitLoc = CoordGrid(2412, 5118, 0)

    /** Where players are sent when they leave: in front of the entrance, on the lobby plaza. */
    val exit = CoordGrid(2208, 2858, 0)

    val points: Map<SpawnPoint, CoordGrid> =
        mapOf(
            SpawnPoint.SouthEast to CoordGrid(2402, 5080, 0),
            SpawnPoint.SouthWest to CoordGrid(2374, 5070, 0),
            SpawnPoint.Centre to CoordGrid(2398, 5086, 0),
            SpawnPoint.NorthWest to CoordGrid(2388, 5098, 0),
            SpawnPoint.South to CoordGrid(2390, 5068, 0),
        )

    /** The tile all four healers appear around, relative to Jad's south-west tile. */
    val healerOffsets: List<Pair<Int, Int>> = listOf(-2 to 1, -2 to 3, 6 to 1, 6 to 3)
}

/**
 * Decides where each monster of each wave spawns, following the real cave's rule: the places come
 * from a fixed cycle, a wave's strongest monster takes the next place and its weaker ones the
 * places after it, and the next wave carries on from there. A run picks where in the cycle it
 * starts, which gives the real game's 15 rotations. Jad appears where the first Tz-Kek did.
 */
class FightCaveSpawnPlanner(rotation: Int) {
    private var cursor = rotation.mod(CYCLE.size)
    private var kekPoint: SpawnPoint? = null

    /** The spawn point for each monster of [wave], strongest first. */
    fun plan(wave: FightCaveWave): List<Pair<FightCaveMonster, SpawnPoint>> {
        if (FightCaveMonster.Jad in wave.monsters) {
            val point = kekPoint ?: next()
            return wave.monsters.map { it to point }
        }
        val ordered = wave.monsters.sortedByDescending { it.level }
        return ordered.map { monster ->
            val point = next()
            if (monster == FightCaveMonster.TzKek && kekPoint == null) {
                kekPoint = point
            }
            monster to point
        }
    }

    private fun next(): SpawnPoint {
        val point = CYCLE[cursor]
        cursor = (cursor + 1) % CYCLE.size
        return point
    }

    companion object {
        /** The repeating order of spawn points, from the wiki's rotations page. */
        val CYCLE: List<SpawnPoint> =
            listOf(
                SpawnPoint.SouthEast,
                SpawnPoint.SouthWest,
                SpawnPoint.Centre,
                SpawnPoint.NorthWest,
                SpawnPoint.SouthWest,
                SpawnPoint.SouthEast,
                SpawnPoint.South,
                SpawnPoint.NorthWest,
                SpawnPoint.Centre,
                SpawnPoint.SouthEast,
                SpawnPoint.SouthWest,
                SpawnPoint.South,
                SpawnPoint.NorthWest,
                SpawnPoint.Centre,
                SpawnPoint.South,
            )
    }
}
