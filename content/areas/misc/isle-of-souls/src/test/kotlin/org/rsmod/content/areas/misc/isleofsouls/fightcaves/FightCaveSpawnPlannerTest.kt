package org.rsmod.content.areas.misc.isleofsouls.fightcaves

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightCaveSpawnPlannerTest {
    private val waves = FightCaveWaves.load(FightCaveMode.Full)

    private fun points(planner: FightCaveSpawnPlanner, number: Int) =
        planner.plan(waves[number]).map { it.second }

    @Test
    fun `the cycle has fifteen places using all five points`() {
        assertEquals(15, FightCaveSpawnPlanner.CYCLE.size)
        assertEquals(SpawnPoint.entries.toSet(), FightCaveSpawnPlanner.CYCLE.toSet())
    }

    @Test
    fun `rotation zero starts at the first place and carries on across waves`() {
        val planner = FightCaveSpawnPlanner(rotation = 0)

        assertEquals(listOf(SpawnPoint.SouthEast), points(planner, 1))
        assertEquals(listOf(SpawnPoint.SouthWest, SpawnPoint.Centre), points(planner, 2))
        assertEquals(listOf(SpawnPoint.NorthWest), points(planner, 3))
    }

    @Test
    fun `a rotation is an offset into the cycle that wraps around`() {
        val planner = FightCaveSpawnPlanner(rotation = 14)

        assertEquals(listOf(SpawnPoint.South), points(planner, 1))
        assertEquals(listOf(SpawnPoint.SouthEast, SpawnPoint.SouthWest), points(planner, 2))
    }

    @Test
    fun `each wave puts its strongest monster in the first place`() {
        val planner = FightCaveSpawnPlanner(rotation = 3)
        // Wave 4 is a Tz-Kek and a Tz-Kih.
        val plan = planner.plan(waves[4])

        assertEquals(FightCaveMonster.TzKek, plan[0].first)
        assertEquals(SpawnPoint.NorthWest, plan[0].second)
        assertEquals(FightCaveMonster.TzKih, plan[1].first)
        assertEquals(SpawnPoint.SouthWest, plan[1].second)
    }

    @Test
    fun `Jad appears where the first Tz-Kek did`() {
        for (rotation in 0 until FightCaveSpawnPlanner.CYCLE.size) {
            val planner = FightCaveSpawnPlanner(rotation)
            var kekPoint: SpawnPoint? = null
            for (number in 1..62) {
                for ((monster, point) in planner.plan(waves[number])) {
                    if (monster == FightCaveMonster.TzKek && kekPoint == null) {
                        kekPoint = point
                    }
                }
            }

            val jad = planner.plan(waves[63]).single()
            assertEquals(FightCaveMonster.Jad, jad.first)
            assertEquals(kekPoint, jad.second, "rotation $rotation")
        }
    }

    @Test
    fun `every spawn point has a tile`() {
        assertEquals(SpawnPoint.entries.toSet(), FightCaveArena.points.keys)
        assertTrue(FightCaveArena.points.values.all { it.level == 0 })
    }
}
