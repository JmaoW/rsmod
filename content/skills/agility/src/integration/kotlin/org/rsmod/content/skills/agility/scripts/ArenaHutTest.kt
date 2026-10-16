package org.rsmod.content.skills.agility.scripts

import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.agility.configs.ArenaEntrance
import org.rsmod.content.skills.agility.configs.ArenaHut
import org.rsmod.content.skills.agility.configs.ArenaLocs
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

class ArenaHutTest {
    class Deps @Inject constructor(val arena: Arena)

    private fun GameTestState.runHutTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(Deps::class, null, Arena::class) { deps -> body(deps) }

    /** The hut stands over four zones, which the test world must have collision for. */
    private fun GameTestScope.allocHut() {
        for (dx in listOf(-8, 0, 8)) {
            for (dz in listOf(0, 8)) {
                allocZoneCollision(ArenaHut.DOOR.translate(dx, dz))
            }
        }
    }

    private fun GameTestScope.placeBush() {
        val bush = locTypes.values.first { it.internalName == ArenaHut.BUSH }
        placeMapLoc(ArenaHut.BUSH_AT, bush, LocShape[10], LocAngle[0])
    }

    private fun GameTestScope.locsAt(coords: CoordGrid) =
        findLocs(coords).filter { it.coords == coords }

    @Test
    fun GameTestState.`every piece of the hut and the ladder is put up`() = runHutTest { deps ->
        allocHut()
        deps.arena.placeEntrance()

        val byName = locTypes.values.associateBy { it.internalName }
        for (piece in ArenaHut.pieces) {
            val at = piece.at(ArenaHut.DOOR)
            val type = byName.getValue(piece.loc)
            check(locsAt(at).any { it.id == type.id && it.shape.id == piece.shape }) {
                "Missing ${piece.loc} at $at."
            }
        }
        val ladder = locTypes[ArenaLocs.ladder_down]
        check(locsAt(ArenaEntrance.ladder).any { it.id == ladder.id }) { "Missing the ladder." }
        check(ArenaEntrance.ladder == ArenaHut.DOOR.translate(0, 5)) { "The ladder moved." }
    }

    @Test
    fun GameTestState.`the hut stands where it was asked to`() = runHutTest {
        check(ArenaHut.DOOR == CoordGrid(2280, 2802, 0)) { "The door is at ${ArenaHut.DOOR}." }
        val doorway = ArenaHut.pieces.single { it.loc == "bamboo_doorway" }
        check(doorway.at(ArenaHut.DOOR) == ArenaHut.DOOR) { "The doorway is not on the door tile." }
    }

    @Test
    fun GameTestState.`the beach bush under the hut is taken out`() = runHutTest { deps ->
        allocHut()
        placeBush()
        val bush = locTypes.values.first { it.internalName == ArenaHut.BUSH }
        check(locsAt(ArenaHut.BUSH_AT).any { it.id == bush.id }) { "The test bush is missing." }

        deps.arena.placeEntrance()

        check(locsAt(ArenaHut.BUSH_AT).none { it.id == bush.id }) { "The bush is still there." }
    }

    @Test
    fun GameTestState.`the walls close the hut except at the doorway`() = runHutTest { deps ->
        allocHut()
        deps.arena.placeEntrance()
        fun wallNorth(dx: Int, dz: Int) =
            collision[ArenaHut.DOOR.x + dx, ArenaHut.DOOR.z + dz, 0] and CollisionFlag.WALL_NORTH !=
                0

        check(wallNorth(-1, 0)) { "No wall west of the doorway." }
        check(wallNorth(1, 0)) { "No wall east of the doorway." }
        check(!wallNorth(0, 0)) { "The doorway is walled up." }
    }

    @Test
    fun GameTestState.`the clerk, Jackie and the exit have room inside the hut`() =
        runHutTest { deps ->
            allocHut()
            deps.arena.placeEntrance()
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            for ((name, tile) in
                listOf(
                    "exit" to ArenaEntrance.exit,
                    "clerk" to ArenaEntrance.clerk,
                    "Jackie" to ArenaEntrance.jackie,
                )) {
                check(collision[tile.x, tile.z, tile.level] and blocked == 0) {
                    "The $name tile $tile is blocked."
                }
            }
            check(ArenaEntrance.clerk != ArenaEntrance.jackie) { "Both stand on one tile." }
        }
}
