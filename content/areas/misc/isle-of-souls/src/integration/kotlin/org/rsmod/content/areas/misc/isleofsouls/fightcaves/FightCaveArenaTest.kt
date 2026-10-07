package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

/** Checks the cave's hand-picked tiles against the real map: nothing can stand in a wall. */
class FightCaveArenaTest {
    /** Walls and locs only: other tests leave npc flags behind in the shared collision map. */
    private val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

    private fun GameTestState.footprintFree(origin: CoordGrid, size: Int): Boolean =
        (0 until size).all { dz ->
            (0 until size).all { dx ->
                (collision[origin.x + dx, origin.z + dz, origin.level] and blocked) == 0
            }
        }

    @Test
    fun GameTestState.`every spawn point has room for the biggest monsters`() = runGameTest {
        for ((point, origin) in FightCaveArena.points) {
            assertTrue(footprintFree(origin, 5), "$point at $origin is not free for a size 5 npc")
        }
    }

    @Test
    fun GameTestState.`players start on open ground`() = runGameTest {
        assertTrue(footprintFree(FightCaveArena.start, 1))
        assertTrue(footprintFree(FightCaveArena.exit, 1))
    }

    @Test
    fun GameTestState.`the cave start is inside the copied block`() = runGameTest {
        val zoneX = FightCaveArena.start.x shr 3
        val zoneZ = FightCaveArena.start.z shr 3
        assertTrue(zoneX in 296..303, "start zone x $zoneX")
        assertTrue(zoneZ in 632..639, "start zone z $zoneZ")
    }

    @Test
    fun GameTestState.`the lobby plaza has room for the entrance and the gatekeeper`() =
        runGameTest {
            val entrance = locTypes[cave_locs.entrance]
            println("entrance loc ${entrance.name} ${entrance.width}x${entrance.length}")
            assertTrue(
                footprintFree(FightCaveScript.GATEKEEPER, 1),
                "gatekeeper tile ${FightCaveScript.GATEKEEPER} is blocked",
            )
            assertEquals(3, entrance.width)
            assertEquals(2, entrance.length)
            val front = FightCaveScript.ENTRANCE.translate(1, -1)
            assertTrue(
                footprintFree(front, 1),
                "the tile in front of the entrance $front is blocked",
            )
            assertEquals(1, npcTypes[cave_npcs.gatekeeper].size)
        }

    /**
     * Cave monsters step straight at their target and stop at walls. Every spawn point must let a
     * monster of every size walk up to a player standing at the cave's start.
     */
    @Test
    fun GameTestState.`monsters of every size can walk from every spawn point to the start`() =
        runGameTest {
            val sizes =
                listOf(
                    FightCaveMonsterDefs.tzKih,
                    FightCaveMonsterDefs.tzKek,
                    FightCaveMonsterDefs.tokXil,
                    FightCaveMonsterDefs.ytMejKot,
                    FightCaveMonsterDefs.ketZek,
                )
            val stuck = mutableListOf<String>()
            for ((point, origin) in FightCaveArena.points) {
                for (def in sizes) {
                    player.telejump(FightCaveArena.start)
                    val npc = spawnNpc(origin, npcTypes[def.type])
                    npc.opPlayer2(player)
                    var reached = false
                    for (tick in 0 until MAX_WALK_TICKS) {
                        // The cave script keeps this leash moving with the target.
                        npc.spawnCoords = player.coords
                        advance()
                        if (npc.distanceTo(player) <= 1) {
                            reached = true
                            break
                        }
                    }
                    if (!reached) {
                        stuck += "$point size ${def.size} stopped at ${npc.coords}"
                    }
                    npcRepo.del(npc, Int.MAX_VALUE)
                }
            }
            assertTrue(stuck.isEmpty(), stuck.joinToString("; "))
        }

    private companion object {
        const val MAX_WALK_TICKS = 140
    }
}
