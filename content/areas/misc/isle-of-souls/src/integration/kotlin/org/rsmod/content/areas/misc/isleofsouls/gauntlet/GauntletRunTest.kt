package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.death.PlayerDeathEvents
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.registry.region.RegionRegistry
import org.rsmod.api.script.onEvent
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.lobby.LobbySpawnScript
import org.rsmod.content.areas.misc.isleofsouls.lobby.SoulWarsLobby
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

class GauntletRunTest {
    /** Records which worn slots were announced as changed. */
    class WearSpy : PluginScript() {
        override fun ScriptContext.startup() {
            onEvent<HeldEquipEvents.WearposChange> { changes += wearpos }
        }

        companion object {
            val changes = ArrayList<Wearpos>()
        }
    }

    private object GauntletModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(Gauntlet::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val gauntlet: Gauntlet,
        val teams: LeagueTeams,
        val collision: CollisionFlagMap,
        val registry: RegionRegistry,
    )

    /**
     * The test world has no map, so the template chunks the maze copies from have no collision of
     * their own, and a region zone copied from one would count as unwalkable. This gives every
     * chunk of both templates empty collision, which stands in for the real floor.
     */
    private fun Deps.allocateTemplates() {
        // The lobby and the isle's portal are on the real map, which the test world lacks too.
        collision.allocateIfAbsent(Gauntlet.LOBBY.x, Gauntlet.LOBBY.z, Gauntlet.LOBBY.level)
        collision.allocateIfAbsent(
            Gauntlet.PORTAL_FRONT.x,
            Gauntlet.PORTAL_FRONT.z,
            Gauntlet.PORTAL_FRONT.level,
        )
        for (chunkX in 232..247) {
            for (chunkZ in 704..711) {
                for (level in 0..3) {
                    collision.allocateIfAbsent(chunkX * 8, chunkZ * 8, level)
                }
            }
        }
    }

    /** alice and bob are on team 1, carol is alone. All three are briefed by Bryn. */
    private fun GameTestState.runGauntletTest(
        body: GameTestScope.(Deps, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(
            Deps::class,
            GauntletModule,
            GauntletScript::class,
            LobbySpawnScript::class,
            WearSpy::class,
        ) { deps ->
            deps.allocateTemplates()
            deps.gauntlet.pick = { 0 }
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            for (p in listOf(alice, bob, carol)) {
                VarPlayerIntMapSetter.set(p, gauntlet_varps.bryn, 1)
            }
            body(deps, alice, bob, carol)
        }

    private fun Player.carrying(): Int = inv.filterNotNull().size + worn.filterNotNull().size

    @Test
    fun GameTestState.`entering needs Bryn first and empty hands`() =
        runGauntletTest { deps, alice, _, carol ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.bryn, 0)
            assertEquals(GauntletEntry.NotBriefed, deps.gauntlet.enter(alice, GauntletMode.Regular))

            VarPlayerIntMapSetter.set(alice, gauntlet_varps.bryn, 1)
            alice.invAdd(alice.inv, gauntlet_objs.axe, 1)
            assertEquals(GauntletEntry.HandsFull, deps.gauntlet.enter(alice, GauntletMode.Regular))
            alice.inv.fillNulls()

            VarPlayerIntMapSetter.set(alice, gauntlet_varbits.reward_available, 1)
            assertEquals(
                GauntletEntry.RewardWaiting,
                deps.gauntlet.enter(alice, GauntletMode.Regular),
            )
            VarPlayerIntMapSetter.set(alice, gauntlet_varbits.reward_available, 0)

            // The Corrupted version opens after one completion.
            assertEquals(
                GauntletEntry.CorruptedLocked,
                deps.gauntlet.enter(carol, GauntletMode.Corrupted),
            )
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(alice, GauntletMode.Regular))
        }

    @Test
    fun GameTestState.`a player goes in with the starting kit and the maze map`() =
        runGauntletTest { deps, alice, _, _ ->
            WearSpy.changes.clear()
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(alice, GauntletMode.Regular))
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            // Equipping the sceptre is announced as equipping from the pack is, so the weapon
            // scripts (combat tab, special attack) and the player's model see it.
            assertEquals(listOf(Wearpos.RightHand), WearSpy.changes)

            // Inside the start room, on the play level, in the run's own region.
            assertTrue(run.contains(alice.coords))
            assertEquals(GauntletGrid.PLAY_LEVEL, alice.coords.level)
            assertEquals(run.layout.start, run.roomAt(alice.coords))

            // Wielding the sceptre, with the five tools in the pack.
            assertEquals(gauntlet_objs.sceptre.id, alice.worn[Wearpos.RightHand.slot]?.id)
            val pack = alice.inv.filterNotNull().map { it.id }
            assertEquals(GauntletMode.Regular.tools.map { it.id }.sorted(), pack.sorted())

            // The map shows the start room and the Hunllef's room as found, and nothing else.
            assertEquals(1, alice.vars[gauntlet_varps.in_run])
            assertEquals(1, alice.vars[gauntlet_varbits.in_gauntlet])
            assertEquals(0, alice.vars[gauntlet_varbits.corrupted])
            assertEquals(0, alice.vars[gauntlet_varbits.boss_started])
            val found = (0 until 49).filter { alice.vars[gauntlet_varbits.room_found[it]] == 1 }
            assertEquals(setOf(run.layout.start.index, run.layout.boss.index), found.toSet())
            assertEquals(run.layout.start.x, alice.vars[gauntlet_varbits.current_room_x])
            assertEquals(run.layout.start.z, alice.vars[gauntlet_varbits.current_room_z])
        }

    @Test
    fun GameTestState.`teammates share one maze and strangers get their own`() =
        runGauntletTest { deps, alice, bob, carol ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)
            deps.gauntlet.enter(carol, GauntletMode.Regular)

            val shared = checkNotNull(deps.gauntlet.runOf(alice))
            assertTrue(shared === deps.gauntlet.runOf(bob))
            assertEquals(listOf(alice, bob), shared.members)
            assertFalse(shared === deps.gauntlet.runOf(carol))
            assertEquals(2, deps.gauntlet.liveRuns().size)
        }

    @Test
    fun GameTestState.`a team cannot start the other version while one is on`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            VarPlayerIntMapSetter.set(bob, gauntlet_varps.done, 1)
            assertEquals(
                GauntletEntry.OtherVersion,
                deps.gauntlet.enter(bob, GauntletMode.Corrupted),
            )
        }

    @Test
    fun GameTestState.`the corrupted version has its own template and a shorter timer`() =
        runGauntletTest { deps, alice, _, _ ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.done, 1)
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(alice, GauntletMode.Corrupted))
            val run = checkNotNull(deps.gauntlet.runOf(alice))

            assertEquals(750, run.prepLeft)
            assertEquals(1, alice.vars[gauntlet_varbits.corrupted])
            assertEquals(gauntlet_objs.sceptre_hm.id, alice.worn[Wearpos.RightHand.slot]?.id)
            // The start room's first zone is copied from chunk 246,708 (238 + 8).
            val start = run.layout.start
            val sw = run.region.southWestZone
            val zone = org.rsmod.map.zone.ZoneKey(sw.x + start.x * 2, sw.z + start.z * 2, 1)
            val copy = deps.registry[zone]
            assertNotEquals(RegionZoneCopy.NULL, copy)
            assertEquals(246, copy.normalX)
            assertEquals(708, copy.normalZ)
        }

    @Test
    fun GameTestState.`the clock runs out and everyone is taken to the Hunllef`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))

            repeat(10) { deps.gauntlet.tick() }
            assertEquals(990, run.prepLeft)
            assertEquals(GauntletStage.Prep, run.stage)

            run.prepLeft = 1
            deps.gauntlet.tick()

            assertEquals(GauntletStage.Boss, run.stage)
            for (member in listOf(alice, bob)) {
                assertEquals(run.layout.boss, run.roomAt(member.coords))
                assertEquals(1, member.vars[gauntlet_varbits.boss_started])
            }
        }

    @Test
    fun GameTestState.`once the fight has begun a teammate can no longer join`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            deps.gauntlet.startBossPhase(run, forced = false)

            assertEquals(GauntletEntry.TooLate, deps.gauntlet.enter(bob, GauntletMode.Regular))
            assertNull(deps.gauntlet.runOf(bob))
        }

    @Test
    fun GameTestState.`the barrier starts the fight for the whole team`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))

            assertTrue(deps.gauntlet.startBossPhase(run, forced = false))
            assertFalse(deps.gauntlet.startBossPhase(run, forced = false))
            assertEquals(run.layout.boss, run.roomAt(alice.coords))
            assertEquals(run.layout.boss, run.roomAt(bob.coords))
            assertNotEquals(alice.coords, bob.coords)
        }

    @Test
    fun GameTestState.`leaving takes everything and the maze closes behind the last player`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)
            WearSpy.changes.clear()

            assertTrue(deps.gauntlet.leave(alice, GauntletExit.Exit))
            assertEquals(Gauntlet.LOBBY, alice.coords)
            assertEquals(0, alice.carrying())
            // What they wore is taken off as unequipping would, so the combat tab and the model
            // follow.
            assertEquals(listOf(Wearpos.RightHand), WearSpy.changes)
            assertEquals(0, alice.vars[gauntlet_varps.in_run])
            assertEquals(0, alice.vars[gauntlet_varbits.in_gauntlet])
            assertNull(deps.gauntlet.runOf(alice))
            assertEquals(1, deps.gauntlet.liveRuns().size)

            // They cannot go back into the maze their team is still in.
            assertEquals(GauntletEntry.Barred, deps.gauntlet.enter(alice, GauntletMode.Regular))

            assertTrue(deps.gauntlet.leave(bob, GauntletExit.Escape))
            assertTrue(deps.gauntlet.liveRuns().isEmpty())
            assertFalse(deps.gauntlet.leave(bob, GauntletExit.Exit))

            // With the maze gone the next entry builds a fresh one for both.
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(alice, GauntletMode.Regular))
        }

    @Test
    fun GameTestState.`a death puts the player out of the run and wakes them in the lobby`() =
        runGauntletTest { deps, alice, bob, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)

            deps.gauntlet.died(alice)
            assertEquals(0, alice.carrying())
            assertNull(deps.gauntlet.runOf(alice))
            assertNotNull(deps.gauntlet.runOf(bob))
            assertEquals(Gauntlet.LOBBY, deps.gauntlet.respawnFor(alice))
            // The respawn answer is given once.
            assertNull(deps.gauntlet.respawnFor(alice))
            assertNull(deps.gauntlet.respawnFor(bob))
        }

    @Test
    fun GameTestState.`the respawn after a Gauntlet death is the lobby and only that once`() =
        runGauntletTest { deps, alice, _, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            eventBus.publish(PlayerDeathEvents.Died(alice))
            assertNull(deps.gauntlet.runOf(alice))

            val respawn = PlayerDeathEvents.Respawn(alice, CoordGrid(3200, 3200, 0))
            eventBus.publish(respawn)
            assertEquals(Gauntlet.LOBBY, respawn.coords)

            // The next death anywhere else goes to the Soul Wars graveyards as before.
            val again = PlayerDeathEvents.Respawn(alice, CoordGrid(3200, 3200, 0))
            eventBus.publish(again)
            assertTrue(again.coords in SoulWarsLobby.spawnTiles)
        }

    @Test
    fun GameTestState.`being moved out of the maze counts as leaving it`() =
        runGauntletTest { deps, alice, _, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            alice.coords = CoordGrid(3200, 3200, 0)

            deps.gauntlet.tick()

            assertNull(deps.gauntlet.runOf(alice))
            assertEquals(0, alice.carrying())
            assertTrue(deps.gauntlet.liveRuns().isEmpty())
        }

    @Test
    fun GameTestState
        .`logging out inside leaves nothing behind and logging in clears a cut-short run`() =
        runGauntletTest { deps, alice, _, carol ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.loggedOut(alice)
            assertEquals(Gauntlet.LOBBY, alice.coords)
            assertEquals(0, alice.carrying())

            // A run that was cut short by the server stopping: the flag is still set at login.
            deps.gauntlet.enter(carol, GauntletMode.Regular)
            deps.gauntlet.liveRuns().forEach { it.members.clear() }
            deps.gauntlet.sweep()
            assertEquals(1, carol.vars[gauntlet_varps.in_run])
            deps.gauntlet.loggedIn(carol)
            assertEquals(0, carol.vars[gauntlet_varps.in_run])
            assertEquals(0, carol.carrying())
            assertEquals(Gauntlet.LOBBY, carol.coords)
        }

    @Test
    fun GameTestState
        .`lighting a room adds its zones and makes players inside resend the region`() =
        runGauntletTest { deps, alice, _, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            alice.visibleZoneKeys.add(1)
            assertFalse(alice.pendingRegionRebuild)

            // A room beside the start room, which a node would light.
            val next =
                Side.entries
                    .mapNotNull {
                        run.layout.room(run.layout.start.x + it.dx, run.layout.start.z + it.dz)
                    }
                    .first { !it.lit }
            val litBefore =
                run.region.toZoneList().count { deps.registry[it] != RegionZoneCopy.NULL }

            assertTrue(deps.gauntlet.lightRoom(run, next))
            assertFalse(deps.gauntlet.lightRoom(run, next))

            // 2 x 2 zones on each of four levels.
            val litAfter =
                run.region.toZoneList().count { deps.registry[it] != RegionZoneCopy.NULL }
            assertEquals(litBefore + 16, litAfter)
            assertTrue(alice.pendingRegionRebuild)
            assertTrue(alice.visibleZoneKeys.isEmpty())
            assertEquals(1, alice.vars[gauntlet_varbits.room_found[next.index]])
        }

    @Test
    fun GameTestState.`finishing a run counts it and opens the corrupted version`() =
        runGauntletTest { deps, alice, _, _ ->
            assertFalse(deps.gauntlet.hasCompleted(alice))
            deps.gauntlet.markCompleted(alice, GauntletMode.Regular)

            assertTrue(deps.gauntlet.hasCompleted(alice))
            assertEquals(1, alice.vars[gauntlet_varps.done])
            assertEquals(1, alice.vars[gauntlet_varps.client_done])
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(alice, GauntletMode.Corrupted))
        }

    @Test
    fun GameTestState.`the game tick drives the clock`() = runGauntletTest { deps, alice, _, _ ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        val before = run.prepLeft

        advance(5)

        assertTrue(run.prepLeft <= before - 5)
    }
}
