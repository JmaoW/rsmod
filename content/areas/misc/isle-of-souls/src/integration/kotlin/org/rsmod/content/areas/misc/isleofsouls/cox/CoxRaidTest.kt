package org.rsmod.content.areas.misc.isleofsouls.cox

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The raid's shell end to end: a party, going in, starting, every room built, the way on opening as
 * rooms are done, floors, deaths and points, the Great Olm's phases, the loot and leaving. The test
 * world has no map, so the raid map's zones are given collision by hand (and no map locs exist:
 * passages are crossed through [CoxRaids.passage] directly).
 */
class CoxRaidTest {
    private object CoxModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: CoxParties,
        val raids: CoxRaids,
        val rooms: CoxRoomRegistry,
        val rewards: CoxRewards,
        val collision: CollisionFlagMap,
    )

    @AfterEach
    fun resetSettings() {
        EventSettings.current = EventSettings()
    }

    private fun allocate(deps: Deps) {
        // The rooms (zone x 400-419, z 640-719, levels 0-2): the wall zone and the Olm's too.
        for (zx in 400 until 420) {
            for (zz in 640 until 720) {
                for (level in 0..2) {
                    deps.collision.allocateIfAbsent(
                        zx * ZoneGrid.LENGTH,
                        zz * ZoneGrid.LENGTH,
                        level,
                    )
                }
            }
        }
        deps.collision.allocateIfAbsent(CoxTiles.OUTSIDE.x, CoxTiles.OUTSIDE.z, 0)
        deps.collision.allocateIfAbsent(CoxMountain.ARRIVAL.x, CoxMountain.ARRIVAL.z, 0)
    }

    private fun GameTestState.runCoxTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            CoxModule,
            CoxRoomDirector::class,
            CoxPuzzles::class,
            CoxCombatScript::class,
            CoxOlmScript::class,
            CoxRewardScript::class,
        ) { deps ->
            allocate(deps)
            deps.raids.fadeTicks = 0
            val alice = player
            alice.displayName = "Alice"
            alice.observerUUID = 1L
            val bob = registerPlayer(coords = CoxMountain.ARRIVAL)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            PathingEntityCommon.telejump(alice, deps.collision, CoxMountain.ARRIVAL)
            body(deps, alice, bob)
        }

    private fun GameTestScope.tick(deps: Deps, ticks: Int = 1) {
        repeat(ticks) {
            advance()
            deps.raids.tick()
            deps.rooms.tick()
        }
    }

    private fun GameTestScope.enterBoth(deps: Deps, alice: Player, bob: Player): CoxRaid {
        val party = deps.parties.create(alice)
        assertEquals(CoxParties.JoinResult.Joined, deps.parties.join(party, bob))
        assertEquals(CoxEntry.LeaderFirst, deps.raids.enter(bob))
        assertEquals(CoxEntry.Entered, deps.raids.enter(alice))
        assertEquals(CoxEntry.Entered, deps.raids.enter(bob))
        tick(deps)
        return checkNotNull(deps.raids.raidOf(alice))
    }

    @Test
    fun GameTestState.`a party goes in, starts and every room is built`() =
        runCoxTest { deps, alice, bob ->
            val raid = enterBoth(deps, alice, bob)
            assertTrue(raid.contains(bob.coords))
            assertEquals(CoxRoomType.RaidStart, raid.roomAt(alice.coords)?.type)
            val withContent =
                raid.rooms.count {
                    it.type.kind !in setOf(CoxRoomKind.Start, CoxRoomKind.End) &&
                        it.type != CoxRoomType.ResourcesA &&
                        it.type != CoxRoomType.ResourcesB
                }
            // Resource rooms have no runtime in this test (CoxResources isn't loaded); the Olm has.
            assertEquals(withContent + 1, deps.rooms.all(raid).size)
            val first = raid.rooms.first()
            val tile =
                raid.regionOf(
                    first,
                    CoordGrid(first.type.sourceX(first.variant) + 14, first.type.sourceZ + 3, 0),
                )
            assertEquals(CoxRaids.PassResult.NotStarted, deps.raids.passage(alice, listOf(tile)))
            assertFalse(deps.raids.start(bob), "only the leader starts")
            assertTrue(deps.raids.start(alice))
            assertTrue(raid.started)
            assertEquals(CoxStage.Upper, raid.stage)
            assertEquals(2, raid.scaling.size)
            assertTrue(raid.members.all { it.started })
            val late = registerPlayer(coords = CoxMountain.ARRIVAL)
            assertEquals(CoxParties.JoinResult.Started, deps.parties.join(raid.party, late))
        }

    @Test
    fun GameTestState.`rooms open as they are done and the floors lead to the olm`() =
        runCoxTest { deps, alice, bob ->
            val raid = enterBoth(deps, alice, bob)
            deps.raids.start(alice)
            for (room in raid.rooms) {
                val runtime = deps.rooms.runtime(raid, room)
                if (runtime != null && !runtime.completed) runtime.complete()
                if (runtime == null && room.type.kind == CoxRoomKind.Resources) continue
                assertTrue(deps.raids.isOpen(raid, room), "${room.type} should be open")
            }
            for (end in raid.rooms.filter { it.type == CoxRoomType.FloorEnd }) {
                val at = deps.raids.landing(raid, end, CoxTiles.FLOOR_END_LANDING[end.variant])
                deps.raids.move(alice, at)
                assertTrue(deps.raids.descend(alice), "the hole in room ${end.index}")
                tick(deps)
            }
            assertTrue(raid.inOlmChamber(alice.coords))
            assertEquals(CoxStage.Olm, raid.stage)
            assertTrue(raid.inOlmChamber(deps.raids.respawnTile(raid)))
            assertTrue(deps.raids.climb(alice))
            tick(deps)
            assertEquals(CoxRoomType.FloorEnd, raid.roomAt(alice.coords)?.type)
        }

    @Test
    fun GameTestState.`points, and what a death costs`() = runCoxTest { deps, alice, bob ->
        val raid = enterBoth(deps, alice, bob)
        deps.raids.start(alice)
        deps.raids.addPoints(raid, alice, 10_000)
        deps.raids.addPoints(raid, bob, 1_000)
        assertEquals(11_000, raid.totalPoints)
        deps.raids.died(alice)
        assertEquals(6_000, raid.member(alice)!!.points)
        assertNotNull(deps.raids.respawnFor(alice))
        // Under 5% of the total: the team loses 5% instead.
        deps.raids.addPoints(raid, alice, 100_000)
        val before = raid.totalPoints
        deps.raids.died(bob)
        assertTrue(raid.totalPoints in (before - before / 20 - 2)..(before - before / 20 + 2))
    }

    @Test
    fun GameTestState.`the olm rises, falls phase by phase and leaves loot`() =
        runCoxTest { deps, alice, bob ->
            val raid = enterBoth(deps, alice, bob)
            deps.raids.start(alice)
            deps.raids.addPoints(raid, alice, 30_000)
            deps.raids.addPoints(raid, bob, 30_000)
            val olm = checkNotNull(deps.rooms.olm(raid) as? CoxOlm)
            val inside = raid.olmTile(CoordGrid(3232, 5728, 0))
            deps.raids.move(alice, inside)
            deps.raids.move(bob, inside.translate(1, 0))
            olm.pass(alice)
            olm.pass(bob)
            assertTrue(olm.started)
            var guard = 0
            while (!raid.olmDefeated && guard++ < 3000) {
                tick(deps)
                for (npc in olm.npcs.toList()) {
                    if (!npc.isSlotAssigned || npc.hitpoints <= 0) continue
                    if (isClaw(npc) || final(olm)) {
                        npc.hitpoints = 0
                        olm.hitpointsGone(npc)
                    }
                }
            }
            assertTrue(raid.olmDefeated, "the Olm should fall")
            assertTrue(deps.rewards.hasLoot(alice))
            assertTrue(deps.rewards.hasLoot(bob))
            assertEquals(1, alice.vars[cox_varps.completions])
        }

    private fun isClaw(npc: Npc): Boolean = npc.type.name.contains("claw", ignoreCase = true)

    private fun final(olm: CoxOlm): Boolean {
        val field = CoxOlm::class.java.getDeclaredField("finalStand")
        field.isAccessible = true
        return field.getBoolean(olm)
    }

    @Test
    fun GameTestState.`leaving by the steps ends the raid when the last raider goes`() =
        runCoxTest { deps, alice, bob ->
            val raid = enterBoth(deps, alice, bob)
            assertTrue(deps.raids.leave(bob, CoxExit.Steps))
            assertEquals(CoxTiles.OUTSIDE, bob.coords)
            assertFalse(raid.ended)
            assertTrue(deps.raids.leave(alice, CoxExit.Steps))
            assertTrue(raid.ended)
            assertNull(deps.parties.partyOf(alice))
        }
}
