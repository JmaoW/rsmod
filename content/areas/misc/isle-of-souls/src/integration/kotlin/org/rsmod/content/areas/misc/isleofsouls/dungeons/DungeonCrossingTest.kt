package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.agility.configs.Tile
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class DungeonCrossingTest {
    class Deps @Inject constructor(val collision: CollisionFlagMap)

    private fun GameTestState.runCrossingTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, DungeonCrossingScript::class) { deps -> body(deps) }

    private fun GameTestScope.setAgility(level: Int) {
        player.setBaseLevel(stats.agility, level)
        player.setCurrentLevel(stats.agility, level)
    }

    private fun Tile.coords() = CoordGrid(x, z, level)

    private fun GameTestScope.clickLoc(crossing: DungeonCrossing, near: Tile): BoundLocInfo {
        val click = crossing.clicks.minBy { it.coords.chebyshevDistance(near.coords()) }
        allocZoneCollision(click.coords)
        return placeMapLoc(click.coords, click.type, click.shape, click.angle)
    }

    /** Uses the crossing from [from] and returns where the player stands afterwards. */
    private fun GameTestScope.use(crossing: DungeonCrossing, from: Tile): CoordGrid {
        val loc = clickLoc(crossing, from)
        allocZoneCollision(crossing.a.coords())
        allocZoneCollision(crossing.b.coords())
        player.placeAt(from.coords())
        player.opLoc1(loc)
        advance(ticks = 25)
        return player.coords
    }

    @Test
    fun GameTestState.`both ends of every crossing are open ground`() = runCrossingTest { deps ->
        val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        for (crossing in DungeonCrossings.all) {
            for (end in listOf(crossing.a, crossing.b)) {
                assertEquals(
                    0,
                    deps.collision[end.x, end.z, end.level] and blocked,
                    "${crossing.key} end $end is blocked",
                )
            }
        }
    }

    @Test
    fun GameTestState.`the crossings have distinct keys and every click is listed once`() =
        runCrossingTest { _ ->
            val all = DungeonCrossings.all
            assertEquals(all.size, all.map { it.key }.toSet().size)
            val clicks = all.flatMap { c -> c.clicks.map { it.coords } }
            assertEquals(clicks.size, clicks.toSet().size)
        }

    @Test
    fun GameTestState.`every crossing is made in both directions at the right level`() =
        runCrossingTest { _ ->
            for (crossing in DungeonCrossings.all) {
                setAgility(99)
                assertEquals(
                    crossing.b.coords(),
                    use(crossing, crossing.a),
                    "${crossing.key} a to b",
                )
                assertEquals(
                    crossing.a.coords(),
                    use(crossing, crossing.b),
                    "${crossing.key} b to a",
                )
            }
        }

    @Test
    fun GameTestState.`a crossing is refused below its level in the direction that asks for one`() =
        runCrossingTest { _ ->
            for (crossing in DungeonCrossings.all) {
                if (crossing.levelAtoB > 1) {
                    setAgility(crossing.levelAtoB - 1)
                    assertEquals(
                        crossing.a.coords(),
                        use(crossing, crossing.a),
                        "${crossing.key} a to b",
                    )
                }
                if (crossing.levelBtoA > 1) {
                    setAgility(crossing.levelBtoA - 1)
                    assertEquals(
                        crossing.b.coords(),
                        use(crossing, crossing.b),
                        "${crossing.key} b to a",
                    )
                }
            }
        }

    @Test
    fun GameTestState.`shortcuts that ask for nothing one way are free to cross that way`() =
        runCrossingTest { _ ->
            val oneWay = DungeonCrossings.all.filter { (it.levelAtoB == 0) != (it.levelBtoA == 0) }
            assertTrue(oneWay.isNotEmpty())
            for (crossing in oneWay) {
                setAgility(1)
                val free = if (crossing.levelAtoB == 0) crossing.a else crossing.b
                val other = if (crossing.levelAtoB == 0) crossing.b else crossing.a
                assertEquals(other.coords(), use(crossing, free), crossing.key)
            }
        }

    @Test
    fun GameTestState.`a crossing clicked from a few tiles away walks up and crosses`() =
        runCrossingTest { _ ->
            val crossing = DungeonCrossings.taverleyPipe
            setAgility(99)
            val loc = clickLoc(crossing, crossing.a)
            allocZoneCollision(crossing.a.coords())
            allocZoneCollision(crossing.b.coords())
            player.placeAt(CoordGrid(crossing.a.x - 5, crossing.a.z, crossing.a.level))
            player.opLoc1(loc)
            advance(ticks = 40)
            assertEquals(crossing.b.coords(), player.coords)
            assertMessageNotSent("You can't reach that.")
        }

    @Test
    fun GameTestState.`a crossing gives its experience`() = runCrossingTest { _ ->
        val crossing = DungeonCrossings.taverleyPipe
        setAgility(70)
        val before = player.statMap.getFineXP(stats.agility)
        use(crossing, crossing.a)
        assertTrue(player.statMap.getFineXP(stats.agility) > before)
    }

    @Test
    fun GameTestState.`the Fremennik and Kalphite shortcuts ask for the Wiki's levels`() =
        runCrossingTest { _ ->
            fun levels(c: DungeonCrossing) = c.levelAtoB to c.levelBtoA
            assertEquals(43 to 43, levels(DungeonCrossings.fremennikFloor))
            assertEquals(61 to 61, levels(DungeonCrossings.fremennikCrack))
            assertEquals(86 to 86, levels(DungeonCrossings.kalphiteCrevice))
            // The Kalphite Lair is on level 2 of its map square.
            assertEquals(2, DungeonCrossings.kalphiteCrevice.a.level)
            assertEquals(
                Dungeon.KALPHITE_LAIR,
                Dungeon.containing(DungeonCrossings.kalphiteCrevice.a.coords()),
            )
        }
}
