package org.rsmod.content.areas.misc.isleofsouls.teams

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
import org.rsmod.api.config.refs.objs
import org.rsmod.api.registry.zone.ZoneUpdateTransformer
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Player
import org.rsmod.game.obj.Obj
import org.rsmod.map.zone.ZoneKey

class TeamLootTest {
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps @Inject constructor(val teams: LeagueTeams, val membership: TeamMembership)

    /** alice and bob are on team 1, carol has no team, dave is on team 2. */
    private fun GameTestState.runLootTest(
        body: GameTestScope.(Deps, Player, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(Deps::class, TeamsModule) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.createTeam(2, "Beta")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.teams.assign("dave", 2)
            for (member in listOf(alice, bob, carol, dave)) {
                deps.membership.refresh(member)
            }
            body(deps, alice, bob, carol, dave)
        }

    @Test
    fun GameTestState.`teammates share an observer id and everyone else keeps their own`() =
        runLootTest { _, alice, bob, carol, dave ->
            assertEquals(alice.observerUUID, bob.observerUUID)
            assertNotEquals(alice.uuid, alice.observerUUID)
            assertEquals(carol.uuid, carol.observerUUID)
            assertNotEquals(alice.observerUUID, dave.observerUUID)
            assertNotEquals(Obj.NULL_OBSERVER_ID, alice.observerUUID)
        }

    @Test
    fun GameTestState.`a drop made for one teammate is visible to the other but not outsiders`() =
        runLootTest { _, alice, bob, carol, dave ->
            val drop = Obj.fromOwner(alice, alice.coords, objs.logs, 1)

            assertTrue(drop.isVisibleTo(alice))
            assertTrue(drop.isVisibleTo(bob))
            assertFalse(drop.isVisibleTo(carol))
            assertFalse(drop.isVisibleTo(dave))
            assertTrue(drop.isOriginalOwner(bob))
        }

    @Test
    fun GameTestState.`a kill drop is shared the same way`() =
        runLootTest { _, alice, bob, carol, _ ->
            val npc = spawnNpc(alice.coords, npcTypes.values.first())
            val drop = Obj.fromPvn(bob, npc, objs.logs, 1)

            assertTrue(drop.isVisibleTo(alice))
            assertFalse(drop.isVisibleTo(carol))
        }

    @Test
    fun GameTestState.`the client is sent the shared drop to the teammate only`() =
        runLootTest { _, alice, bob, carol, _ ->
            val drop = Obj.fromOwner(alice, alice.coords, objs.logs, 1)

            assertNotNull(ZoneUpdateTransformer.toPersistentObjAdd(drop, bob.observerUUID))
            assertNull(ZoneUpdateTransformer.toPersistentObjAdd(drop, carol.observerUUID))
        }

    @Test
    fun GameTestState.`leaving the team gives back a personal id and the shared drop is hidden`() =
        runLootTest { deps, alice, bob, _, _ ->
            val drop = Obj.fromOwner(alice, alice.coords, objs.logs, 1)

            deps.teams.remove("bob")
            deps.membership.refresh(bob)

            assertEquals(bob.uuid, bob.observerUUID)
            assertFalse(drop.isVisibleTo(bob))
            assertTrue(drop.isVisibleTo(alice))
        }

    @Test
    fun GameTestState.`moving to another team switches the shared id`() =
        runLootTest { deps, _, bob, _, dave ->
            deps.teams.assign("bob", 2)
            deps.membership.refresh(bob)

            assertEquals(dave.observerUUID, bob.observerUUID)
        }

    @Test
    fun GameTestState.`changing the id makes the zone processor send the nearby drops again`() =
        runLootTest { deps, _, bob, _, _ ->
            bob.lastProcessedZone = ZoneKey.from(bob.coords)
            bob.visibleZoneKeys.add(ZoneKey.from(bob.coords).packed)

            // Nothing changed, so nothing is reset.
            deps.membership.refresh(bob)
            assertEquals(ZoneKey.from(bob.coords), bob.lastProcessedZone)
            assertEquals(1, bob.visibleZoneKeys.size)

            deps.teams.remove("bob")
            deps.membership.refresh(bob)
            // The registry's zone tracking must survive, or it re-adds bob to his current zone.
            assertEquals(ZoneKey.from(bob.coords), bob.lastProcessedZone)
            assertTrue(bob.visibleZoneKeys.isEmpty())
        }
}
