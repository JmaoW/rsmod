package org.rsmod.content.areas.misc.isleofsouls.dungeons

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.content
import org.rsmod.api.config.refs.params
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.obj.ObjReferences
import org.rsmod.content.generic.locs.doors.DoorScript
import org.rsmod.content.generic.locs.doors.DoubleDoorScript
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.content.ContentGroupType
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

internal object PassageObjs : ObjReferences() {
    val knife = find("knife")
    val bronze_axe = find("bronze_axe")
}

class DungeonPassageTest {
    class Deps @Inject constructor(val script: DungeonPassageScript)

    private fun GameTestState.runPassageTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            DungeonPassageScript::class,
            DoorScript::class,
            DoubleDoorScript::class,
        ) { deps ->
            body(deps)
        }

    /** A quiet corner of the isle's field, clear of the dungeons, for placing doors on. */
    private val spot = CoordGrid(2200, 2815, 0)

    private fun GameTestScope.has(coords: CoordGrid, type: LocType): Boolean =
        findLocs(coords).any { it.coords == coords && it.id == type.id }

    private fun GameTestScope.place(
        coords: CoordGrid,
        type: LocType,
        shape: LocShape = LocShape.WallStraight,
        angle: LocAngle = LocAngle.North,
    ): BoundLocInfo {
        allocZoneCollision(coords)
        return placeMapLoc(coords, type, shape, angle)
    }

    @Test
    fun GameTestState
        .`every swinging door has the next stage and content group that make it open`() =
        runPassageTest { _ ->
            data class Swing(
                val closed: LocType,
                val opened: LocType,
                val closedGroup: ContentGroupType,
                val openedGroup: ContentGroupType,
            )
            fun single(closed: LocType, opened: LocType) =
                Swing(closed, opened, content.closed_single_door, content.opened_single_door)
            fun left(closed: LocType, opened: LocType) =
                Swing(closed, opened, content.closed_left_door, content.opened_left_door)
            fun right(closed: LocType, opened: LocType) =
                Swing(closed, opened, content.closed_right_door, content.opened_right_door)

            val doors =
                listOf(
                    single(DungeonDoorLocs.prison_door, DungeonDoorLocs.prison_door_open),
                    left(DungeonDoorLocs.prison_door_left, DungeonDoorLocs.prison_door_left_open),
                    right(
                        DungeonDoorLocs.prison_door_right,
                        DungeonDoorLocs.prison_door_right_open,
                    ),
                    single(DungeonDoorLocs.tower_door, DungeonDoorLocs.tower_door_open),
                    left(DungeonDoorLocs.tower_door_left, DungeonDoorLocs.tower_door_left_open),
                    right(DungeonDoorLocs.tower_door_right, DungeonDoorLocs.tower_door_right_open),
                    left(DungeonDoorLocs.odd_wall_left, DungeonDoorLocs.odd_wall_left_open),
                    right(DungeonDoorLocs.odd_wall_right, DungeonDoorLocs.odd_wall_right_open),
                    left(DungeonDoorLocs.metal_gate_left, DungeonDoorLocs.metal_gate_left_open),
                    right(DungeonDoorLocs.metal_gate_right, DungeonDoorLocs.metal_gate_right_open),
                )
            for ((closed, opened, closedGroup, openedGroup) in doors) {
                val closedType = locTypes[closed]
                val openType = locTypes[opened]
                assertEquals(opened.id, closedType.param(params.next_loc_stage).id, "$closed")
                assertEquals(closed.id, openType.param(params.next_loc_stage).id, "$opened")
                assertTrue(closedType.isContentType(closedGroup), "$closed")
                assertTrue(openType.isContentType(openedGroup), "$opened")
            }
        }

    @Test
    fun GameTestState.`a slayer tower door swings open when used`() = runPassageTest { _ ->
        val door = place(spot, DungeonDoorLocs.tower_door)
        player.placeAt(spot.translateZ(-1))

        player.opLoc1(door)
        advance(ticks = 3)

        assertTrue(!has(spot, DungeonDoorLocs.tower_door), "the closed door is gone")
        val opened =
            (-1..1).any { dx ->
                (-1..1).any { dz -> has(spot.translate(dx, dz), DungeonDoorLocs.tower_door_open) }
            }
        assertTrue(opened, "an open door stands where it swung")
    }

    @Test
    fun GameTestState.`a metal gate opens as a pair, the halves swinging apart`() =
        runPassageTest { _ ->
            // A west-facing pair: the right half is one tile north of the left half.
            val left = place(spot, DungeonDoorLocs.metal_gate_left, angle = LocAngle.West)
            place(spot.translateZ(1), DungeonDoorLocs.metal_gate_right, angle = LocAngle.West)
            player.placeAt(spot.translateX(1))

            player.opLoc1(left)
            advance(ticks = 3)

            assertTrue(!has(spot, DungeonDoorLocs.metal_gate_left), "the left half swung")
            assertTrue(
                !has(spot.translateZ(1), DungeonDoorLocs.metal_gate_right),
                "the right half swung",
            )
            // Both halves land on the far side of the doorway, each hinged at its own outer end:
            // the left on the south wall of its tile, the right on the north wall of its tile.
            val leftOpen =
                findLocs(spot.translateX(-1)).single {
                    it.id == DungeonDoorLocs.metal_gate_left_open.id
                }
            val rightOpen =
                findLocs(spot.translate(-1, 1)).single {
                    it.id == DungeonDoorLocs.metal_gate_right_open.id
                }
            assertEquals(LocAngle.South, leftOpen.angle)
            assertEquals(LocAngle.North, rightOpen.angle)
        }

    @Test
    fun GameTestState
        .`a gate with no open model swings apart with its other half and comes back`() =
        runPassageTest { _ ->
            val right =
                place(spot.translateZ(1), DungeonDoorLocs.member_gate_right, angle = LocAngle.West)
            val left = place(spot, DungeonDoorLocs.member_gate_left, angle = LocAngle.West)
            player.placeAt(spot.translateX(-1))

            player.opLoc1(right)
            advance(ticks = 3)

            assertTrue(!has(spot, DungeonDoorLocs.member_gate_left), "left left its place")
            assertTrue(!has(spot.translateZ(1), DungeonDoorLocs.member_gate_right))
            // The same models stand turned on the next tile over: left to the south, right north.
            val leftSwung =
                findLocs(spot.translateX(-1)).single {
                    it.id == DungeonDoorLocs.member_gate_left.id
                }
            val rightSwung =
                findLocs(spot.translate(-1, 1)).single {
                    it.id == DungeonDoorLocs.member_gate_right.id
                }
            assertEquals(LocAngle.South, leftSwung.angle)
            assertEquals(LocAngle.North, rightSwung.angle)

            advance(ticks = 110)
            assertTrue(has(spot, DungeonDoorLocs.member_gate_left), "left is back")
            assertTrue(has(spot.translateZ(1), DungeonDoorLocs.member_gate_right), "right is back")
            assertTrue(!has(spot.translateX(-1), DungeonDoorLocs.member_gate_left), "swing is gone")
            assertNotNull(left)
        }

    @Test
    fun GameTestState.`the grubby door takes 57 thieving`() = runPassageTest { _ ->
        val door = place(spot, DungeonDoorLocs.grubby_door)
        player.placeAt(spot.translateZ(-1))
        player.setBaseLevel(stats.thieving, 56)
        player.setCurrentLevel(stats.thieving, 56)

        player.opLoc1(door)
        advance(ticks = 5)
        assertTrue(has(spot, DungeonDoorLocs.grubby_door), "refused at 56")

        player.setBaseLevel(stats.thieving, 57)
        player.setCurrentLevel(stats.thieving, 57)
        player.opLoc1(door)
        advance(ticks = 6)

        assertTrue(!has(spot, DungeonDoorLocs.grubby_door), "the lock is picked at 57")
        assertTrue(has(spot, DungeonDoorLocs.grubby_door_open), "the open door stands")
        assertTrue(player.statMap.getFineXP(stats.thieving) > 0)
    }

    @Test
    fun GameTestState.`a web needs a blade and a knife gets through it sooner or later`() =
        runPassageTest { _ ->
            val web = place(spot, DungeonDoorLocs.web, LocShape.CentrepieceStraight)
            player.placeAt(spot.translateZ(-1))

            repeat(5) {
                player.opLoc1(web)
                advance(ticks = 3)
            }
            assertTrue(has(spot, DungeonDoorLocs.web), "bare hands do nothing")

            player.inv[0] = InvObj(PassageObjs.knife)
            var gone = false
            for (attempt in 1..60) {
                player.opLoc1(web)
                advance(ticks = 3)
                if (!has(spot, DungeonDoorLocs.web)) {
                    gone = true
                    break
                }
            }
            assertTrue(gone, "sixty slashes with a knife (50% each) cut the web")
        }

    @Test
    fun GameTestState.`vines need the woodcutting level and an axe`() = runPassageTest { _ ->
        val vine = place(spot, DungeonDoorLocs.vine_3, LocShape.CentrepieceStraight)
        player.placeAt(spot.translateZ(-1))
        player.setBaseLevel(stats.woodcutting, 33)
        player.setCurrentLevel(stats.woodcutting, 33)
        player.inv[0] = InvObj(PassageObjs.bronze_axe)

        repeat(3) {
            player.opLoc1(vine)
            advance(ticks = 20)
        }
        assertTrue(has(spot, DungeonDoorLocs.vine_3), "33 is not enough for the far vines")

        player.setBaseLevel(stats.woodcutting, 34)
        player.setCurrentLevel(stats.woodcutting, 34)
        player.inv[0] = null
        player.opLoc1(vine)
        advance(ticks = 20)
        assertTrue(has(spot, DungeonDoorLocs.vine_3), "no axe, no luck")

        player.inv[0] = InvObj(PassageObjs.bronze_axe)
        var gone = false
        for (attempt in 1..10) {
            player.opLoc1(vine)
            advance(ticks = 20)
            if (!has(spot, DungeonDoorLocs.vine_3)) {
                gone = true
                break
            }
        }
        assertTrue(gone, "an axe at level 34 chops the vine")
        assertNull(
            findLocs(spot).firstOrNull { it.coords == spot && it.id == DungeonDoorLocs.vine_3.id }
        )
    }
}
