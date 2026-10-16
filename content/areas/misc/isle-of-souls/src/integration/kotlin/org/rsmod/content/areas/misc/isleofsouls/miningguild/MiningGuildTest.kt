package org.rsmod.content.areas.misc.isleofsouls.miningguild

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueSkillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.generic.locs.doors.DoorScript
import org.rsmod.content.generic.locs.ladders.DungeonLadderScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

class MiningGuildTest {
    class Deps @Inject constructor(val script: MiningGuildScript)

    private fun GameTestState.runGuildTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            MiningGuildScript::class,
            DoorScript::class,
            DungeonLadderScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.setLevel(level: Int) {
        player.setBaseLevel(stats.mining, level)
        player.setCurrentLevel(stats.mining, level)
    }

    /** [findLoc] looks through the whole zone; this one wants the loc on exactly [coords]. */
    private fun GameTestScope.locAt(coords: CoordGrid, type: LocType): LocInfo? =
        findLocs(coords).firstOrNull { it.coords == coords && it.id == type.id }

    private fun GameTestScope.bound(info: LocInfo, type: LocType) =
        BoundLocInfo(info, locTypes[type])

    private fun GameTestScope.ladder(deps: Deps): BoundLocInfo {
        allocZoneCollision(MiningGuildScript.LADDER)
        deps.script.placeLadder()
        return bound(
            checkNotNull(locAt(MiningGuildScript.LADDER, MiningGuildLocs.ladder_down)),
            MiningGuildLocs.ladder_down,
        )
    }

    @Test
    fun GameTestState.`the isle has a single ladder`() = runGuildTest { deps ->
        allocZoneCollision(MiningGuildScript.LADDER)
        deps.script.placeLadder()

        val ladders =
            findLocs(MiningGuildScript.LADDER).filter { it.id == MiningGuildLocs.ladder_down.id }
        assertEquals(listOf(MiningGuildScript.LADDER), ladders.map { it.coords }.toList())
        assertEquals("Climb-down", locTypes[MiningGuildLocs.ladder_down].op[0])
        assertEquals(
            MiningGuildScript.LADDER.translateZ(1),
            MiningGuildScript.LADDER_ARRIVAL,
            "arrival is north of the ladder",
        )
    }

    @Test
    fun GameTestState.`climbing down is refused below the guild level`() = runGuildTest { deps ->
        val ladder = ladder(deps)
        setLevel(MiningGuildScript.GUILD_MINING_LEVEL - 1)
        allocZoneCollision(MiningGuildScript.GUILD_ARRIVAL)
        player.placeAt(MiningGuildScript.LADDER_ARRIVAL)

        player.opLoc1(ladder)
        advance(ticks = 4)

        assertEquals(MiningGuildScript.LADDER_ARRIVAL, player.coords)
    }

    @Test
    fun GameTestState.`climbing down at the guild level lands in front of the guild ladder`() =
        runGuildTest { deps ->
            val ladder = ladder(deps)
            setLevel(MiningGuildScript.GUILD_MINING_LEVEL)
            allocZoneCollision(MiningGuildScript.GUILD_ARRIVAL)
            player.placeAt(MiningGuildScript.LADDER_ARRIVAL)

            player.opLoc1(ladder)
            advance(ticks = 4)

            assertEquals(MiningGuildScript.GUILD_ARRIVAL, player.coords)
        }

    @Test
    fun GameTestState.`climbing down into the guild completes the league task`() =
        runInjectedGameTest(Deps::class, null, MiningGuildScript::class, LeagueSkillTasks::class) {
            deps ->
            val ladder = ladder(deps)
            val task = 1845
            val tracker = LeagueTaskTracker(eventBus, LeagueTeams(players))
            setLevel(MiningGuildScript.GUILD_MINING_LEVEL)
            allocZoneCollision(MiningGuildScript.GUILD_ARRIVAL)

            // Refused at the ladder because of the level: not entered.
            setLevel(1)
            player.placeAt(MiningGuildScript.LADDER_ARRIVAL)
            player.opLoc1(ladder)
            advance(ticks = 4)
            assertFalse(tracker.isComplete(player, task))

            setLevel(MiningGuildScript.GUILD_MINING_LEVEL)
            player.opLoc1(ladder)
            advance(ticks = 4)
            assertEquals(MiningGuildScript.GUILD_ARRIVAL, player.coords)
            assertTrue(tracker.isComplete(player, task))
        }

    @Test
    fun GameTestState
        .`every one of the guild's four ladders leads to the north of the isle ladder`() =
        runGuildTest { _ ->
            val hole = MiningGuildScript.GUILD_HOLE
            // Each piece with the tile outside it that a player climbs it from.
            val pieces =
                listOf(
                    hole.translateX(-1) to LocAngle.East,
                    hole.translateX(1) to LocAngle.West,
                    hole.translateZ(-1) to LocAngle.North,
                    hole.translateZ(1) to LocAngle.South,
                )
            allocZoneCollision(MiningGuildScript.LADDER_ARRIVAL)
            for ((tile, angle) in pieces) {
                val ladder =
                    placeMapLoc(
                        tile,
                        MiningGuildLocs.ladder_up,
                        LocShape.CentrepieceStraight,
                        angle,
                    )
                player.placeAt(tile.translate(tile.x.compareTo(hole.x), tile.z.compareTo(hole.z)))

                player.opLoc1(ladder)
                advance(ticks = 4)

                assertEquals(MiningGuildScript.LADDER_ARRIVAL, player.coords, "from $tile")
            }
        }

    @Test
    fun GameTestState.`the same ladder anywhere else still climbs up 6400 tiles`() =
        runGuildTest { _ ->
            val ladderTile = CoordGrid(3209, 9616, 0)
            val ladder =
                placeMapLoc(
                    ladderTile,
                    MiningGuildLocs.ladder_up,
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
    fun GameTestState.`guild doors open and close like any other door`() = runGuildTest { _ ->
        val closedAt = CoordGrid(3019, 9732, 0)
        val closed =
            placeMapLoc(
                closedAt,
                MiningGuildLocs.door_inner_1,
                LocShape.WallStraight,
                LocAngle.North,
            )
        assertEquals("Open", locTypes[MiningGuildLocs.door_inner_1].op[0])

        player.placeAt(closedAt.translateZ(-1))
        player.opLoc1(closed)
        advance(ticks = 3)

        assertFalse(locExists(closed), "the closed door is still in the way")
        val openAt = closedAt.translateZ(1)
        val opened = checkNotNull(locAt(openAt, MiningGuildLocs.door_opened))
        assertEquals(LocAngle.East.id, opened.angleId)
        assertEquals("Close", locTypes[MiningGuildLocs.door_opened].op[0])

        player.placeAt(openAt)
        player.opLoc1(bound(opened, MiningGuildLocs.door_opened))
        advance(ticks = 3)

        assertFalse(locExists(openAt, MiningGuildLocs.door_opened), "the open door is still open")
        val shut = locAt(closedAt, MiningGuildLocs.door_plain)
        assertNotNull(shut, "no door was put back")
        assertEquals(LocAngle.North.id, shut!!.angleId)
        assertTrue(locExists(closedAt, MiningGuildLocs.door_plain))
    }
}
