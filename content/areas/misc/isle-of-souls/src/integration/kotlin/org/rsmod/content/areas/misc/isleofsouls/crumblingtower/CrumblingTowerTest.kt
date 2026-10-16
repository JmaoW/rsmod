package org.rsmod.content.areas.misc.isleofsouls.crumblingtower

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.content
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.generic.locs.staircase.SpiralStaircaseScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

class CrumblingTowerTest {
    class Deps @Inject constructor(val locRepo: LocRepository)

    private fun GameTestState.runTowerTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            null,
            CrumblingTowerScript::class,
            SpiralStaircaseScript::class,
        ) { deps ->
            body(deps)
        }

    private fun GameTestScope.place(deps: Deps, coords: CoordGrid, type: LocType): BoundLocInfo {
        allocZoneCollision(coords)
        deps.locRepo.add(
            coords,
            locTypes[type],
            duration = Int.MAX_VALUE,
            angle = LocAngle.South,
            shape = LocShape.CentrepieceStraight,
        )
        val info = findLocs(coords).first { it.coords == coords && it.id == type.id }
        return BoundLocInfo(info, locTypes[type])
    }

    @Test
    fun GameTestState.`the trapdoor leads down to the open side of the basement ladder`() =
        runTowerTest { deps ->
            val trapdoor = place(deps, CrumblingTowerScript.TRAPDOOR, CrumblingTowerLocs.trapdoor)
            allocZoneCollision(CrumblingTowerScript.BASEMENT_ARRIVAL)
            player.placeAt(CrumblingTowerScript.TRAPDOOR_ARRIVAL)

            player.opLoc1(trapdoor)
            advance(ticks = 4)

            assertEquals(CrumblingTowerScript.BASEMENT_ARRIVAL, player.coords)
        }

    @Test
    fun GameTestState.`the basement ladder leads up beside the trapdoor`() = runTowerTest { deps ->
        val ladder = place(deps, CrumblingTowerScript.LADDER, CrumblingTowerLocs.ladder)
        allocZoneCollision(CrumblingTowerScript.TRAPDOOR_ARRIVAL)
        player.placeAt(CrumblingTowerScript.BASEMENT_ARRIVAL)

        player.opLoc1(ladder)
        advance(ticks = 4)

        assertEquals(CrumblingTowerScript.TRAPDOOR_ARRIVAL, player.coords)
    }

    @Test
    fun GameTestState.`the staircase pieces use the generic stair content groups`() =
        runTowerTest { _ ->
            assertEquals(
                content.spiralstaircase_up.id,
                locTypes[CrumblingTowerLocs.stairs_bottom].contentGroup,
            )
            assertEquals(
                content.spiralstaircase_option.id,
                locTypes[CrumblingTowerLocs.stairs_middle].contentGroup,
            )
            assertEquals(
                content.spiralstaircase_down.id,
                locTypes[CrumblingTowerLocs.stairs_top].contentGroup,
            )
            assertNotNull(locTypes[CrumblingTowerLocs.trapdoor])
        }
}
