package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.generic.locs.ladders.DungeonLadderScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

class DungeonTest {
    class Deps @Inject constructor(val script: DungeonScript)

    private fun GameTestState.runDungeonTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, DungeonScript::class, DungeonLadderScript::class) {
            deps ->
            body(deps)
        }

    private fun GameTestScope.locAt(coords: CoordGrid, type: LocType): LocInfo? =
        findLocs(coords).firstOrNull { it.coords == coords && it.id == type.id }

    private fun GameTestScope.bound(info: LocInfo, type: LocType) =
        BoundLocInfo(info, locTypes[type])

    @Test
    fun GameTestState.`every dungeon is distinct and has a way out`() = runDungeonTest { _ ->
        val dungeons = Dungeon.entries
        assertEquals(21, dungeons.size)
        assertEquals(dungeons.size, dungeons.map { it.key }.toSet().size)
        assertEquals(dungeons.size, dungeons.map { it.entranceType.id }.toSet().size)
        assertEquals(dungeons.size, dungeons.map { it.entrance }.toSet().size)
        assertEquals(dungeons.size, dungeons.map { it.front }.toSet().size)
        // Every dungeon is left by a listed exit, or through the generic dungeon ladders, which
        // only Taverley and Edgeville rely on.
        for (dungeon in dungeons) {
            assertTrue(
                dungeon.exits.isNotEmpty() ||
                    dungeon in listOf(Dungeon.TAVERLEY, Dungeon.EDGEVILLE),
                "${dungeon.key} has no way out",
            )
        }
    }

    @Test
    fun GameTestState.`the dungeon areas do not overlap`() = runDungeonTest { _ ->
        val areas = Dungeon.entries.flatMap { d -> d.areas.map { d to it } }
        for ((i, a) in areas.withIndex()) {
            for ((d2, b) in areas.drop(i + 1)) {
                if (a.first == d2) continue
                val overlap =
                    a.second.level == b.level &&
                        a.second.minX <= b.maxX &&
                        b.minX <= a.second.maxX &&
                        a.second.minZ <= b.maxZ &&
                        b.minZ <= a.second.maxZ
                assertTrue(!overlap, "${a.first.key} overlaps ${d2.key}")
            }
        }
    }

    @Test
    fun GameTestState.`every arrival is inside its own dungeon`() = runDungeonTest { _ ->
        for (dungeon in Dungeon.entries) {
            assertEquals(dungeon, Dungeon.containing(dungeon.arrival), dungeon.key)
        }
        assertEquals(3, Dungeon.SLAYER_TOWER_BASEMENT.arrival.level)
    }

    @Test
    fun GameTestState.`each entrance is placed on the isle`() = runDungeonTest { deps ->
        for (dungeon in Dungeon.entries) {
            allocZoneCollision(dungeon.entrance)
        }
        deps.script.placeEntrances()

        for (dungeon in Dungeon.entries) {
            if (dungeon.onMap) {
                // Already part of the map: placing another would stack two objects.
                assertEquals(null, locAt(dungeon.entrance, dungeon.entranceType), dungeon.key)
            } else {
                assertNotNull(locAt(dungeon.entrance, dungeon.entranceType), dungeon.key)
            }
        }
    }

    @Test
    fun GameTestState.`using an entrance lands on the dungeon's arrival tile`() =
        runDungeonTest { deps ->
            for (dungeon in Dungeon.entries) {
                allocZoneCollision(dungeon.entrance)
                allocZoneCollision(dungeon.arrival)
            }
            deps.script.placeEntrances()

            for (dungeon in Dungeon.entries) {
                val entrance =
                    if (dungeon.onMap) {
                        placeMapLoc(
                            dungeon.entrance,
                            dungeon.entranceType,
                            dungeon.shape,
                            dungeon.angle,
                        )
                    } else {
                        bound(
                            checkNotNull(locAt(dungeon.entrance, dungeon.entranceType)),
                            dungeon.entranceType,
                        )
                    }
                player.placeAt(dungeon.front)

                player.opLoc1(entrance)
                advance(ticks = 4)

                assertEquals(dungeon.arrival, player.coords, dungeon.key)
            }
        }

    @Test
    fun GameTestState.`the isle's cave twin in the tutorial area does not lead anywhere`() =
        runDungeonTest { _ ->
            val dungeon = Dungeon.ISLE_OF_SOULS_DUNGEON
            val twin = CoordGrid(2116, 5990, 0)
            allocZoneCollision(twin)
            val loc = placeMapLoc(twin, dungeon.entranceType, dungeon.shape, dungeon.angle)

            for ((dx, dz) in listOf(-1 to 0, 0 to -1, 1 to 0, 0 to 1)) {
                player.placeAt(twin.translate(dx, dz))
                player.opLoc1(loc)
                advance(ticks = 4)
                assertNotEquals(dungeon.arrival, player.coords)
            }
        }

    /**
     * Uses [loc] from each tile beside it in turn, until one of them is a place the object can be
     * used from (the real map is loaded, so some sides are rock), and returns where that left the
     * player; null if no side worked.
     */
    private fun GameTestScope.useFromAnySide(loc: BoundLocInfo, landing: CoordGrid): CoordGrid? {
        val sides = listOf(-1 to 0, 0 to -1, 1 to 0, 0 to 1)
        for ((dx, dz) in sides) {
            player.placeAt(loc.coords.translate(dx, dz))
            player.opLoc1(loc)
            advance(ticks = 4)
            if (player.coords == landing) return player.coords
        }
        return null
    }

    /** The way the real map turns the objects whose open side depends on it. */
    private fun realAngle(type: LocType): LocAngle =
        when (type.id) {
            DungeonLocs.fremennik_exit.id,
            DungeonLocs.isle_dungeon_exit.id -> LocAngle.North
            DungeonLocs.mos_exit_left.id,
            DungeonLocs.mos_exit_right.id -> LocAngle.South
            else -> LocAngle.West
        }

    @Test
    fun GameTestState.`every listed exit leads to the front of its entrance`() =
        runDungeonTest { _ ->
            for (dungeon in Dungeon.entries) {
                allocZoneCollision(dungeon.front)
                for (exit in dungeon.exits) {
                    allocZoneCollision(exit.coords)
                    val loc =
                        placeMapLoc(
                            exit.coords,
                            exit.type,
                            LocShape.CentrepieceStraight,
                            realAngle(exit.type),
                        )

                    val landed = useFromAnySide(loc, dungeon.front)

                    assertEquals(dungeon.front, landed, "${dungeon.key} ${exit.coords}")
                }
            }
        }

    @Test
    fun GameTestState.`a generic ladder inside a dungeon area leads to its entrance`() =
        runDungeonTest { _ ->
            val ladderTile = CoordGrid(2884, 9797, 0)
            allocZoneCollision(Dungeon.TAVERLEY.front)
            val ladder =
                placeMapLoc(
                    ladderTile,
                    DungeonLocs.generic_ladder,
                    LocShape.CentrepieceStraight,
                    LocAngle.West,
                )
            player.placeAt(ladderTile.translateX(-1))

            player.opLoc1(ladder)
            advance(ticks = 4)

            assertEquals(Dungeon.TAVERLEY.front, player.coords)
        }

    @Test
    fun GameTestState.`a generic ladder outside every dungeon still climbs 6400 tiles`() =
        runDungeonTest { _ ->
            val ladderTile = CoordGrid(3209, 9616, 0)
            assertEquals(null, Dungeon.containing(ladderTile))
            val ladder =
                placeMapLoc(
                    ladderTile,
                    DungeonLocs.generic_ladder,
                    LocShape.CentrepieceStraight,
                    LocAngle.West,
                )
            val stand = ladderTile.translateX(-1)
            allocZoneCollision(stand.translateZ(-6400))
            player.placeAt(stand)

            player.opLoc1(ladder)
            advance(ticks = 4)

            assertEquals(stand.translateZ(-6400), player.coords)
        }

    @Test
    fun GameTestState.`the cave between the Karamja Dungeon and Mor Ul Rek works both ways`() =
        runDungeonTest { _ ->
            val caves = Dungeon.links.take(2)
            for (link in caves) {
                allocZoneCollision(link.coords)
                allocZoneCollision(link.dest)
                val loc =
                    placeMapLoc(link.coords, link.type, LocShape.CentrepieceStraight, LocAngle.West)
                player.placeAt(link.coords.translateX(-1))

                player.opLoc1(loc)
                advance(ticks = 4)

                assertEquals(link.dest, player.coords)
            }
            assertEquals(Dungeon.KARAMJA, Dungeon.containing(caves[0].dest))
            assertEquals(Dungeon.KARAMJA, Dungeon.containing(caves[1].dest))
            assertNotEquals(caves[0].dest, caves[1].dest)
        }

    /** A Brimhaven link with the tile a player stands on to use it and how the object is turned. */
    private class BrimhavenCase(
        val link: DungeonLink,
        val stand: CoordGrid,
        val shape: LocShape,
        val angle: LocAngle,
    )

    private fun brimhavenCases(): List<BrimhavenCase> {
        val links = Dungeon.links
        fun of(type: LocType): DungeonLink = links.first { it.type.id == type.id }
        return listOf(
            BrimhavenCase(
                of(DungeonLocs.brimhaven_stairs_1_up),
                CoordGrid(2650, 9591, 0),
                LocShape.CentrepieceStraight,
                LocAngle.West,
            ),
            BrimhavenCase(
                of(DungeonLocs.brimhaven_stairs_1_down),
                CoordGrid(2643, 9594, 2),
                LocShape.CentrepieceStraight,
                LocAngle.South,
            ),
            BrimhavenCase(
                of(DungeonLocs.brimhaven_stairs_2_up),
                CoordGrid(2637, 9517, 0),
                LocShape.CentrepieceStraight,
                LocAngle.East,
            ),
            BrimhavenCase(
                of(DungeonLocs.brimhaven_stairs_2_down),
                CoordGrid(2637, 9510, 2),
                LocShape.CentrepieceStraight,
                LocAngle.East,
            ),
            BrimhavenCase(
                of(DungeonLocs.brimhaven_vine_up),
                CoordGrid(2673, 9583, 0),
                LocShape.WallStraight,
                LocAngle.East,
            ),
            BrimhavenCase(
                of(DungeonLocs.brimhaven_vine_down),
                CoordGrid(2670, 9583, 2),
                LocShape.GroundDecor,
                LocAngle.East,
            ),
        )
    }

    @Test
    fun GameTestState.`brimhaven's stairs and vine join its two levels`() = runDungeonTest { _ ->
        for (case in brimhavenCases()) {
            val link = case.link
            allocZoneCollision(link.coords)
            allocZoneCollision(link.dest)
            allocZoneCollision(case.stand)
            player.setBaseLevel(stats.agility, 99)
            player.setCurrentLevel(stats.agility, 99)
            val loc = placeMapLoc(link.coords, link.type, case.shape, case.angle)
            player.placeAt(case.stand)

            player.opLoc1(loc)
            advance(ticks = 5)

            assertEquals(link.dest, player.coords, "${link.coords}")
            assertEquals(Dungeon.BRIMHAVEN, Dungeon.containing(link.dest))
        }
    }

    @Test
    fun GameTestState.`the north vine asks for 87 agility to climb up and nothing to climb down`() =
        runDungeonTest { _ ->
            val up = brimhavenCases().first { it.link.type.id == DungeonLocs.brimhaven_vine_up.id }
            allocZoneCollision(up.link.coords)
            allocZoneCollision(up.link.dest)
            val loc = placeMapLoc(up.link.coords, up.link.type, up.shape, up.angle)
            player.setBaseLevel(stats.agility, 86)
            player.setCurrentLevel(stats.agility, 86)
            player.placeAt(up.stand)

            player.opLoc1(loc)
            advance(ticks = 5)
            assertEquals(up.stand, player.coords, "refused at 86")

            player.setBaseLevel(stats.agility, 87)
            player.setCurrentLevel(stats.agility, 87)
            player.opLoc1(loc)
            advance(ticks = 5)
            assertEquals(up.link.dest, player.coords, "climbs at 87")
        }
}
