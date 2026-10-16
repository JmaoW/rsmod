package org.rsmod.content.areas.misc.isleofsouls.zulrah

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class ZulrahLobbyTest {
    private object ZulrahModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Zulrah::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val zulrah: Zulrah,
        val script: ZulrahScript,
        val collision: CollisionFlagMap,
        val locRepo: LocRepository,
    )

    private fun GameTestState.runLobbyTest(body: GameTestScope.(Deps) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            ZulrahModule,
            ZulrahScript::class,
            ZulrahCombatScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            body(deps)
        }

    private val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

    @Test
    fun GameTestState.`the boat and the way out of it stand on free ground of the plaza`() =
        runLobbyTest { deps ->
            for (dz in 0 until 3) {
                for (dx in 0 until 3) {
                    val tile = ZulrahArena.boat.translate(dx, dz)
                    assertEquals(
                        0,
                        deps.collision[tile.x, tile.z, tile.level] and blocked,
                        "boat tile $tile is blocked",
                    )
                }
            }
            val exit = ZulrahArena.exit
            assertEquals(0, deps.collision[exit.x, exit.z, exit.level] and blocked)
            // The exit is next to the boat, so a player who leaves stands in front of it.
            assertTrue(
                exit.x == ZulrahArena.boat.x + 3 &&
                    exit.z in ZulrahArena.boat.z..ZulrahArena.boat.z + 2
            )
            assertEquals(3, locTypes[zulrah_locs.boat].width)
            assertEquals("Board", locTypes[zulrah_locs.boat].op[0])
        }

    @Test
    fun GameTestState
        .`boarding the boat puts the player in a shrine and the scroll takes them out`() =
        runLobbyTest { deps ->
            deps.script.placeBoat()
            val boat = checkNotNull(findLoc(ZulrahArena.boat, zulrah_locs.boat))
            player.placeAt(ZulrahArena.exit)

            player.opLoc1(BoundLocInfo(boat, locTypes[zulrah_locs.boat]))
            advance(ticks = 3)

            val run = checkNotNull(deps.zulrah.runOf(player))
            assertTrue(run.instance.contains(player.coords))
            assertEquals(run.instance.region.normal[ZulrahArena.start], player.coords)

            val scroll = run.instance.region.normal[ZulrahArena.exitScroll]
            val exit = checkNotNull(findLoc(scroll, zulrah_locs.exit))
            assertNotNull(exit)
            player.opLoc1(BoundLocInfo(exit, locTypes[zulrah_locs.exit]))
            advance(ticks = 3)

            assertEquals(ZulrahArena.exit, player.coords)
            assertTrue(deps.zulrah.liveRuns().isEmpty())

            // The collision map is shared by the tests of this run: take the boat away again.
            deps.locRepo.del(boat, Int.MAX_VALUE)
        }

    @Test
    fun GameTestState.`the shrine copy covers the whole platform and Zulrah's four spots`() =
        runLobbyTest { _ ->
            val zones = (280 until 288) to (380 until 388)
            fun inside(c: CoordGrid, size: Int = 1): Boolean =
                (c.x shr 3) in zones.first &&
                    (c.z shr 3) in zones.second &&
                    ((c.x + size - 1) shr 3) in zones.first &&
                    ((c.z + size - 1) shr 3) in zones.second
            for (spot in ZulrahSpot.entries) {
                assertTrue(inside(ZulrahArena.spot(spot), 5), "$spot is outside the copied block")
            }
            assertTrue(inside(ZulrahArena.start))
            for (x in ZulrahArena.platformX) {
                for (z in ZulrahArena.platformZ) {
                    assertTrue(inside(CoordGrid(x, z, 0)))
                }
            }
        }
}
