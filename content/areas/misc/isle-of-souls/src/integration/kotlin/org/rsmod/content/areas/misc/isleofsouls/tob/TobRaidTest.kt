package org.rsmod.content.areas.misc.isleofsouls.tob

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import kotlin.reflect.KClass
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
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The theatre's shell: parties, going in, barriers and the way on, deaths and the jail, a wipe, and
 * the vault. The test world has no map, so the theatre's zones are given collision by hand.
 */
class TobRaidTest {
    private object TobModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: TobParties,
        val raids: TobRaids,
        val rooms: TobRoomRegistry,
        val rewards: TobRewards,
        val collision: CollisionFlagMap,
        val tracker: LeagueTaskTracker,
        val objTypes: ObjTypeList,
    )

    @AfterEach
    fun resetSettings() {
        EventSettings.current = EventSettings()
    }

    private fun GameTestScope.allocate(deps: Deps) {
        val zoneX = TobLayout.REAL_MIN_X / ZoneGrid.LENGTH
        val zoneZ = TobLayout.REAL_MIN_Z / ZoneGrid.LENGTH
        for (zx in zoneX until zoneX + TobLayout.ZONES) {
            for (zz in zoneZ until zoneZ + TobLayout.ZONES) {
                for (level in 0 until CoordGrid.LEVEL_COUNT) {
                    deps.collision.allocateIfAbsent(
                        zx * ZoneGrid.LENGTH,
                        zz * ZoneGrid.LENGTH,
                        level,
                    )
                }
            }
        }
        deps.collision.allocateIfAbsent(VerSinhaza.OUTSIDE.x, VerSinhaza.OUTSIDE.z, 0)
    }

    private fun GameTestState.runTobTest(
        vararg scripts: KClass<out PluginScript>,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            TobModule,
            TobRoomDirector::class,
            TobMaidenScript::class,
            TobBloatScript::class,
            TobVaultScript::class,
            TobSuppliesScript::class,
            *scripts,
        ) { deps ->
            allocate(deps)
            deps.raids.fadeTicks = 0
            val alice = player
            alice.displayName = "Alice"
            alice.observerUUID = 1L
            val bob = registerPlayer(coords = VerSinhaza.OUTSIDE)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            PathingEntityCommon.telejump(alice, deps.collision, VerSinhaza.OUTSIDE)
            body(deps, alice, bob)
        }

    private fun Deps.party(alice: Player, bob: Player): TobParty {
        val party = parties.create(alice)
        assertEquals(TobPartyResult.Done, parties.apply(party, bob))
        assertEquals(TobPartyResult.Done, parties.answer(party, bob, accept = true))
        return party
    }

    private fun GameTestScope.tick(deps: Deps, ticks: Int = 1) {
        repeat(ticks) {
            advance()
            deps.raids.tick()
        }
    }

    private fun TobRaid.real(player: Player): CoordGrid? = realOf(player.coords)

    private fun GameTestScope.die(deps: Deps, player: Player) {
        deps.raids.died(player)
        val coords = checkNotNull(deps.raids.respawnFor(player))
        PathingEntityCommon.telejump(player, deps.collision, coords)
    }

    /** The leader starts the Maiden's fight and both walk in through the barrier. */
    private fun GameTestScope.startMaiden(deps: Deps, raid: TobRaid, alice: Player, bob: Player) {
        val barrier = raid.regionOf(TobRoom.Maiden.barriers.single().tiles.first())
        PathingEntityCommon.telejump(alice, deps.collision, barrier.translate(1, 0))
        PathingEntityCommon.telejump(bob, deps.collision, barrier.translate(1, 0))
        assertEquals(TobRaids.BarrierResult.LeaderFirst, deps.raids.barrier(bob, barrier))
        assertEquals(TobRaids.BarrierResult.AskToStart, deps.raids.barrier(alice, barrier))
        deps.raids.startFight(alice, barrier)
        assertTrue(deps.raids.isStarted(raid, TobRoom.Maiden))
        assertEquals(TobRaids.BarrierResult.Crossed, deps.raids.barrier(bob, barrier))
    }

    // Parties
    // -----------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a party takes up to the event's cap`() = runTobTest { deps, alice, bob ->
        EventSettings.current = EventSettings(tob = TobSettings(partyCap = 1))
        val party = deps.parties.create(alice)
        assertEquals(TobPartyResult.Done, deps.parties.apply(party, bob))
        assertEquals(TobPartyResult.Full, deps.parties.answer(party, bob, accept = true))
        EventSettings.current = EventSettings(tob = TobSettings(partyCap = 8))
        assertEquals(TobPartyResult.Done, deps.parties.answer(party, bob, accept = true))
        assertEquals(2, party.members.size)
    }

    // Going in
    // ------------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the leader goes in first and the party follows to the Maiden`() =
        runTobTest { deps, alice, bob ->
            assertEquals(TobEntry.NoParty, deps.raids.enter(alice))
            deps.party(alice, bob)
            assertEquals(TobEntry.LeaderFirst, deps.raids.enter(bob))
            assertEquals(TobEntry.Entered, deps.raids.enter(alice))
            val raid = checkNotNull(deps.raids.raidOf(alice))
            assertEquals(TobRoom.Maiden, raid.member(alice)?.room)
            assertEquals(TobRoom.Maiden, TobRoom.at(checkNotNull(raid.real(alice))))
            assertEquals(TobEntry.AlreadyInside, deps.raids.enter(alice))
            assertEquals(TobEntry.Entered, deps.raids.enter(bob))
            assertEquals(TobRoom.Maiden, raid.member(bob)?.room)
            assertEquals(2, alice.vars[tob_varbits.party_status])
            // The room was built on arrival: the waiting Maiden is a loc until the fight.
            val maiden = deps.rooms.runtime(raid, TobRoom.Maiden) as MaidenRuntime
            assertNull(maiden.maiden)
        }

    @Test
    fun GameTestState.`the barrier starts the fight, which holds the fighters until it is won`() =
        runTobTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            startMaiden(deps, raid, alice, bob)
            val runtime = deps.rooms.runtime(raid, TobRoom.Maiden) as MaidenRuntime
            val maiden = checkNotNull(runtime.maiden)
            // Two of two: 75% of her 3,500.
            assertEquals(2625, maiden.baseHitpointsLvl)
            assertTrue(raid.member(alice)?.inFight == true)
            assertTrue(TobRoom.Maiden.inArena(checkNotNull(raid.real(alice))))

            // Nobody walks out of a fight that is on, or on before it is won.
            val barrier = raid.regionOf(TobRoom.Maiden.barriers.single().tiles.first())
            PathingEntityCommon.telejump(alice, deps.collision, barrier.translate(-1, 0))
            assertEquals(TobRaids.BarrierResult.StayAndFight, deps.raids.barrier(alice, barrier))
            assertEquals(TobRaids.PassResult.NotComplete, deps.raids.pass(alice))

            runtime.hitpointsGone(maiden)
            assertTrue(TobRoom.Maiden in raid.completed)
            assertEquals(TobRaids.BarrierResult.Crossed, deps.raids.barrier(alice, barrier))
            assertEquals(TobRaids.PassResult.Moved, deps.raids.pass(alice))
            assertEquals(TobRoom.Bloat, raid.room)
            assertEquals(TobRoom.Bloat, raid.member(alice)?.room)
            assertEquals(TobRoom.Bloat, TobRoom.at(checkNotNull(raid.real(alice))))
            // The Bloat waits in its room from the start.
            val bloat = deps.rooms.runtime(raid, TobRoom.Bloat) as BloatRuntime
            assertNotNull(bloat.bloat)
        }

    // Deaths
    // -------------------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`a death in the fight waits in the jail and comes back when the room is won`() =
        runTobTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            startMaiden(deps, raid, alice, bob)

            die(deps, bob)
            val member = checkNotNull(raid.member(bob))
            assertTrue(member.jailed)
            assertEquals(1, raid.totalDeaths)
            assertTrue(checkNotNull(raid.real(bob)) in TobRoom.Maiden.jails)
            // Alice still stands: no wipe.
            tick(deps, TobRaids.WIPE_DELAY + 2)
            assertFalse(raid.failed)

            val runtime = deps.rooms.runtime(raid, TobRoom.Maiden) as MaidenRuntime
            runtime.hitpointsGone(checkNotNull(runtime.maiden))
            assertFalse(member.jailed)
            assertTrue(TobRoom.Maiden.inArena(checkNotNull(raid.real(bob))))
        }

    @Test
    fun GameTestState.`everyone dead in the fight fails the raid and puts the party outside`() =
        runTobTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            startMaiden(deps, raid, alice, bob)

            die(deps, alice)
            die(deps, bob)
            tick(deps, TobRaids.WIPE_DELAY + 2)
            assertTrue(raid.failed)
            assertTrue(raid.ended)
            assertNull(deps.raids.raidOf(alice))
            assertEquals(VerSinhaza.OUTSIDE, alice.coords)
            assertEquals(VerSinhaza.OUTSIDE, bob.coords)
        }

    @Test
    fun GameTestState.`a death outside a fight is just a death`() = runTobTest { deps, alice, _ ->
        deps.parties.create(alice)
        deps.raids.enter(alice)
        val raid = checkNotNull(deps.raids.raidOf(alice))
        die(deps, alice)
        assertFalse(checkNotNull(raid.member(alice)).jailed)
        assertEquals(0, raid.totalDeaths)
        assertEquals(TobRoom.Maiden, TobRoom.at(checkNotNull(raid.real(alice))))
    }

    // Supplies and the vault
    // -------------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the bloat's room gives supply points for the chest`() =
        runTobTest { deps, alice, _ ->
            deps.parties.create(alice)
            deps.raids.enter(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            val member = checkNotNull(raid.member(alice))
            member.damageIn[TobRoom.Maiden] = 500
            deps.raids.completeRoom(raid, TobRoom.Maiden, 100)
            deps.raids.pass(alice)
            deps.raids.completeRoom(raid, TobRoom.Bloat, 100)
            assertTrue(member.supplyPoints in 8..11, "points ${member.supplyPoints}")
            assertEquals(member.supplyPoints, alice.vars[tob_varbits.supply_points])
        }

    @Test
    fun GameTestState.`beating Verzik counts the completion and opening a chest gives the loot`() =
        runTobTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            raid.member(alice)?.damageIn?.put(TobRoom.Verzik, 900)
            raid.member(bob)?.damageIn?.put(TobRoom.Verzik, 300)
            // Dice that never give a unique or a tertiary; the common rolls are the last lines.
            deps.rewards.pick = { n -> n - 1 }
            deps.raids.raidWon(raid)

            assertEquals(1, alice.vars[tob_varps.completions])
            assertEquals(1, bob.vars[tob_varps.completions])
            assertFalse(raid.uniqueFound)
            // Nothing is given until the chest is opened.
            assertFalse(deps.rewards.hasLoot(alice))
            assertEquals(0, alice.vars[tob_varbits.should_have_loot])
            assertTrue(deps.rewards.claim(raid, alice))
            assertFalse(deps.rewards.claim(raid, alice))
            assertTrue(deps.rewards.hasLoot(alice))
            assertEquals(1, alice.vars[tob_varbits.should_have_loot])
            // Leaving first sends the loot to the Rewards Chest.
            assertFalse(deps.rewards.hasLoot(bob))
            deps.raids.leave(bob, TobExit.Resigned)
            assertTrue(deps.rewards.hasLoot(bob))

            deps.rewards.takeAll(alice, toBank = true)
            assertFalse(deps.rewards.hasLoot(alice))
            assertEquals(0, alice.vars[tob_varbits.should_have_loot])
        }

    @Test
    fun GameTestState.`with team uniques everyone gets a unique when one drops`() =
        runTobTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enter(alice)
            deps.raids.enter(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            raid.member(alice)?.damageIn?.put(TobRoom.Verzik, 900)
            raid.member(bob)?.damageIn?.put(TobRoom.Verzik, 300)
            // Dice that always land on the first entry: a unique (the hilt) for everyone.
            deps.rewards.pick = { 0 }
            deps.raids.raidWon(raid)
            assertTrue(raid.uniqueFound)
            val hilt = deps.objTypes.values.first { it.internalName == "infernal_defender_hilt" }
            deps.rewards.claim(raid, alice)
            deps.rewards.claim(raid, bob)
            assertEquals(1, deps.rewards.chest(alice).count(hilt))
            assertEquals(1, deps.rewards.chest(bob).count(hilt))
        }

    @Test
    fun GameTestState.`twenty-five completions complete the league task`() =
        runTobTest { deps, alice, _ ->
            org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(alice, tob_varps.completions, 24)
            deps.rewards.countCompletion(alice)
            assertEquals(25, alice.vars[tob_varps.completions])
            assertTrue(deps.tracker.isComplete(alice, TobRewards.TASK_25))
            assertFalse(deps.tracker.isComplete(alice, TobRewards.TASK_50))
        }
}
