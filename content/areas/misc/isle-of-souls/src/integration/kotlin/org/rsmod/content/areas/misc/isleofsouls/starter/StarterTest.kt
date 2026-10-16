package org.rsmod.content.areas.misc.isleofsouls.starter

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.objs
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.obj.ObjScope
import org.rsmod.game.type.loc.LocType
import org.rsmod.map.CoordGrid

class StarterTest {
    class Deps @Inject constructor(val script: StarterScript, val locRepo: LocRepository)

    private fun GameTestState.runStarterTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, StarterScript::class) { deps -> body(deps) }

    private fun GameTestScope.placeEverything(deps: Deps) {
        for (coords in
            listOf(
                StarterScript.PICKAXE_ROCKS,
                StarterScript.AXE_STUMP,
                StarterScript.GERRANT,
                StarterScript.SHOP_KEEPER,
                StarterScript.FURNACE,
                StarterScript.HAMMER,
            )) {
            allocZoneCollision(coords)
        }
        deps.script.place()
    }

    /** [findLoc] looks through the whole zone; this one wants the loc on exactly [coords]. */
    private fun GameTestScope.locAt(coords: CoordGrid, type: LocType): LocInfo? =
        findLocs(coords).firstOrNull { it.coords == coords && it.id == type.id }

    private fun GameTestScope.bound(info: LocInfo, type: LocType) =
        BoundLocInfo(info, locTypes[type])

    @Test
    fun GameTestState.`everything is placed on the tiles it was asked for`() =
        runStarterTest { deps ->
            placeEverything(deps)

            assertNotNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe))
            assertNotNull(locAt(StarterScript.AXE_STUMP, StarterLocs.stump_with_axe))
            assertNotNull(locAt(StarterScript.FURNACE, StarterLocs.furnace))

            val hammer = objRepo.findAll(StarterScript.HAMMER).single()
            assertEquals(objs.hammer.id, hammer.type)
            assertEquals(
                ObjScope.Perm,
                hammer.scope,
                "a permanent spawn, so it respawns when taken",
            )

            assertEquals(
                listOf(StarterNpcs.gerrant.id),
                npcRepo.findAll(StarterScript.GERRANT).map { it.type.id }.toList(),
            )
            assertEquals(
                listOf(StarterNpcs.shop_keeper.id),
                npcRepo.findAll(StarterScript.SHOP_KEEPER).map { it.type.id }.toList(),
            )
        }

    @Test
    fun GameTestState.`taking the pickaxe empties the rocks until they come back`() =
        runStarterTest { deps ->
            placeEverything(deps)
            val rocks =
                bound(
                    checkNotNull(
                        locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe)
                    ),
                    StarterLocs.rocks_with_pickaxe,
                )
            player.placeAt(StarterScript.PICKAXE_ROCKS.translateZ(-1))

            player.opLoc1(rocks)
            advance(ticks = 3)

            assertEquals(1, player.count(objs.bronze_pickaxe))
            assertNotNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks))
            assertNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe))

            advance(ticks = StarterScript.TOOL_RESPAWN_TICKS + 2)

            assertNotNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe))
            assertNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks))
        }

    @Test
    fun GameTestState.`a player who already has a pickaxe cannot take another`() =
        runStarterTest { deps ->
            placeEverything(deps)
            val rocks =
                bound(
                    checkNotNull(
                        locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe)
                    ),
                    StarterLocs.rocks_with_pickaxe,
                )
            player.placeAt(StarterScript.PICKAXE_ROCKS.translateZ(-1))
            player.inv[0] = InvObj(objs.bronze_pickaxe)

            player.opLoc1(rocks)
            advance(ticks = 3)

            assertEquals(1, player.count(objs.bronze_pickaxe))
            assertNotNull(locAt(StarterScript.PICKAXE_ROCKS, StarterLocs.rocks_with_pickaxe))
        }

    @Test
    fun GameTestState.`a placed loc that is swapped comes back where it was`() =
        runStarterTest { deps ->
            val coords = StarterScript.AXE_STUMP
            allocZoneCollision(coords)
            val info =
                deps.locRepo.add(
                    coords,
                    locTypes[StarterLocs.stump_with_axe],
                    Int.MAX_VALUE,
                    LocAngle.West,
                    LocShape.CentrepieceStraight,
                )

            deps.locRepo.change(info, StarterLocs.rocks, 5)
            assertNotNull(locAt(coords, StarterLocs.rocks))
            assertNull(locAt(coords, StarterLocs.stump_with_axe))

            advance(ticks = 7)

            assertNotNull(locAt(coords, StarterLocs.stump_with_axe))
            assertNull(locAt(coords, StarterLocs.rocks))
        }

    @Test
    fun GameTestState.`a map loc that is swapped still comes back`() = runStarterTest { deps ->
        val coords = StarterScript.AXE_STUMP
        val map = placeMapLoc(coords, StarterLocs.stump_with_axe)

        deps.locRepo.change(map, StarterLocs.rocks, 5)
        assertNotNull(locAt(coords, StarterLocs.rocks))

        advance(ticks = 7)

        assertNotNull(locAt(coords, StarterLocs.stump_with_axe))
        assertNull(locAt(coords, StarterLocs.rocks))
    }

    @Test
    fun GameTestState.`the hammer comes back after it is taken`() = runStarterTest { deps ->
        placeEverything(deps)
        val hammer = objRepo.findAll(StarterScript.HAMMER).single()

        assertTrue(objRepo.del(hammer, 3))
        assertFalse(objRepo.findAll(StarterScript.HAMMER).any())

        advance(ticks = 5)

        assertEquals(objs.hammer.id, objRepo.findAll(StarterScript.HAMMER).single().type)
    }
}
