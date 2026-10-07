package org.rsmod.content.areas.misc.isleofsouls.bank

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.invs
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.teams.TeamMembership
import org.rsmod.content.generic.locs.banks.BankChest
import org.rsmod.content.interfaces.bank.BankAccess
import org.rsmod.content.interfaces.bank.BankEvents
import org.rsmod.content.interfaces.bank.BankTab
import org.rsmod.content.interfaces.bank.configs.BankQueues
import org.rsmod.content.interfaces.bank.configs.BankVarBits
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.Inventory
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.queue.QueueCategory
import org.rsmod.game.type.inv.InvTypeList
import org.rsmod.game.type.util.UncheckedType
import org.rsmod.map.CoordGrid

@OptIn(UncheckedType::class)
class TeamBankTest {
    private object TeamsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val membership: TeamMembership,
        val banks: TeamBanks,
        val access: BankAccess,
        val invTypes: InvTypeList,
        val eventBus: EventBus,
    )

    private val chestCoords = CoordGrid(0, 50, 50, 34, 31)

    /** alice and bob are on team 1, carol has no team. */
    private fun GameTestState.runBankTest(
        body: GameTestScope.(Deps, Player, Player, Player) -> Unit
    ) =
        runInjectedGameTest(Deps::class, TeamsModule, TeamBankScript::class, BankChest::class) {
            deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            for (member in listOf(alice, bob, carol)) {
                deps.membership.refresh(member)
            }
            body(deps, alice, bob, carol)
        }

    private fun Deps.bankOf(player: Player): Inventory = player.invMap.getOrPut(invTypes[invs.bank])

    private fun GameTestScope.placeChest(): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == "soul_wars_bankchest" }
        return placeMapLoc(chestCoords, type)
    }

    private fun GameTestScope.stand(player: Player, chest: BoundLocInfo, offset: Int) {
        player.teleport(chest.coords.translateX(offset))
    }

    /**
     * Clicks the chest. The real bank script is not loaded in tests (it needs weapon data), so the
     * event it publishes as the screen opens is published here.
     */
    private fun GameTestScope.openChest(deps: Deps, player: Player, chest: BoundLocInfo) {
        player.opLoc1(chest)
        advance(ticks = 3)
        if (player.bankIsOpen()) {
            deps.eventBus.publish(BankEvents.Opened(player))
        }
    }

    private fun GameTestScope.closeBank(deps: Deps, player: Player) {
        player.ifClose(deps.eventBus)
        deps.eventBus.publish(BankEvents.Closed(player))
        advance(ticks = 3)
    }

    private fun Player.bankIsOpen() = ui.containsModal(interfaces.bank_main)

    @Test
    fun GameTestState.`teammates share one bank and outsiders keep their own`() =
        runBankTest { deps, alice, bob, carol ->
            assertSame(deps.bankOf(alice), deps.bankOf(bob))
            assertNotSame(deps.bankOf(alice), deps.bankOf(carol))
            assertTrue(deps.banks.isShared(deps.bankOf(alice)))
            assertFalse(deps.banks.isShared(deps.bankOf(carol)))
        }

    @Test
    fun GameTestState.`an item put in by one teammate is there for the other`() =
        runBankTest { deps, alice, bob, carol ->
            deps.bankOf(alice)[0] = InvObj(995, 500)

            assertEquals(InvObj(995, 500), deps.bankOf(bob)[0])
            assertNull(deps.bankOf(carol)[0])
        }

    @Test
    fun GameTestState.`attaching again changes nothing`() = runBankTest { deps, alice, _, _ ->
        val before = deps.bankOf(alice)
        deps.membership.refresh(alice)
        deps.membership.refresh(alice)

        assertSame(before, deps.bankOf(alice))
    }

    @Test
    fun GameTestState.`leaving the team gives back an empty bank and the team keeps its items`() =
        runBankTest { deps, alice, bob, _ ->
            deps.bankOf(alice)[0] = InvObj(995, 500)

            deps.teams.remove("bob")
            deps.membership.refresh(bob)

            assertNotSame(deps.bankOf(alice), deps.bankOf(bob))
            assertTrue(deps.bankOf(bob).isEmpty())
            assertEquals(InvObj(995, 500), deps.bankOf(alice)[0])
            assertFalse(deps.banks.isShared(deps.bankOf(bob)))
        }

    @Test
    fun GameTestState.`moving to another team gives that team's bank`() =
        runBankTest { deps, alice, bob, _ ->
            deps.teams.createTeam(2, "Beta")
            deps.bankOf(alice)[0] = InvObj(995, 500)

            deps.teams.assign("bob", 2)
            deps.membership.refresh(bob)

            assertNotSame(deps.bankOf(alice), deps.bankOf(bob))
            assertTrue(deps.bankOf(bob).isEmpty())
        }

    @Test
    fun GameTestState.`changing team closes an open bank screen`() =
        runBankTest { deps, _, bob, _ ->
            val chest = placeChest()
            stand(bob, chest, -1)
            openChest(deps, bob, chest)
            assertTrue(bob.bankIsOpen())

            deps.teams.remove("bob")
            deps.membership.refresh(bob)

            assertFalse(bob.bankIsOpen())
        }

    @Test
    fun GameTestState.`a personal bank with items becomes the team bank when the team has none`() =
        runInjectedGameTest(Deps::class, TeamsModule, TeamBankScript::class) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)

            val personal = deps.bankOf(alice)
            personal[0] = InvObj(995, 500)
            deps.membership.refresh(alice)
            deps.membership.refresh(bob)

            assertSame(personal, deps.bankOf(alice))
            assertSame(personal, deps.bankOf(bob))
            assertEquals(InvObj(995, 500), deps.bankOf(bob)[0])
        }

    @Test
    fun GameTestState.`a later member's personal bank does not replace the team bank`() =
        runInjectedGameTest(Deps::class, TeamsModule, TeamBankScript::class) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.bankOf(alice)[0] = InvObj(995, 500)
            deps.bankOf(bob)[0] = InvObj(1511, 9)

            deps.membership.refresh(alice)
            deps.membership.refresh(bob)

            assertEquals(InvObj(995, 500), deps.bankOf(bob)[0])
            assertNull(deps.bankOf(bob)[1])
        }

    @Test
    fun GameTestState.`the bank is saved and loaded back with its tab layout`(@TempDir dir: Path) =
        runBankTest { deps, alice, _, _ ->
            deps.banks.load(dir)
            deps.bankOf(alice)[0] = InvObj(995, 500)
            deps.bankOf(alice)[3] = InvObj(1511, 12)
            VarPlayerIntMapSetter.set(alice, BankTab.Tab1.sizeVarBit, 1)
            VarPlayerIntMapSetter.set(alice, BankTab.Main.sizeVarBit, 2)
            deps.banks.onClose(alice)

            assertTrue(Files.exists(dir.resolve(TeamBanks.FILE)))
            assertFalse(deps.banks.save(), "Nothing changed since the close saved it.")

            val reloaded = TeamBanks(deps.invTypes, deps.teams, deps.eventBus)
            reloaded.load(dir)
            val bank = reloaded.bankOf(deps.teams.team(1)!!)
            assertEquals(InvObj(995, 500), bank[0])
            assertEquals(InvObj(1511, 12), bank[3])
            assertNull(bank[1])

            // The layout came back too: hand it to a player who has never opened the bank.
            val dave = registerPlayer(player = Player().apply { username = "dave" })
            deps.teams.assign("dave", 1)
            reloaded.attach(dave)
            reloaded.onOpen(dave)
            assertEquals(1, dave.vars[BankTab.Tab1.sizeVarBit])
            assertEquals(2, dave.vars[BankTab.Main.sizeVarBit])
        }

    @Test
    fun GameTestState.`a change is saved again and an unchanged bank is not rewritten`(
        @TempDir dir: Path
    ) = runBankTest { deps, alice, _, _ ->
        deps.banks.load(dir)

        assertTrue(deps.banks.save(), "The first save writes the empty team bank.")
        assertFalse(deps.banks.save())

        deps.bankOf(alice)[7] = InvObj(995, 1)
        assertTrue(deps.banks.save())
        assertFalse(deps.banks.save())
    }

    @Test
    fun GameTestState.`a team that did not play keeps its saved bank when another is saved`(
        @TempDir dir: Path
    ) = runBankTest { deps, _, _, _ ->
        deps.banks.load(dir)
        deps.teams.createTeam(2, "Beta")
        deps.teams.assign("dave", 2)
        val dave = registerPlayer(player = Player().apply { username = "dave" })
        deps.membership.refresh(dave)
        deps.bankOf(dave)[0] = InvObj(1511, 9)
        deps.banks.save()

        // A fresh server start where only team 1 plays.
        val restarted = TeamBanks(deps.invTypes, deps.teams, deps.eventBus)
        restarted.load(dir)
        restarted.bankOf(deps.teams.team(1)!!)[0] = InvObj(995, 5)
        restarted.save()

        val again = TeamBanks(deps.invTypes, deps.teams, deps.eventBus)
        again.load(dir)
        assertEquals(InvObj(1511, 9), again.bankOf(deps.teams.team(2)!!)[0])
        assertEquals(InvObj(995, 5), again.bankOf(deps.teams.team(1)!!)[0])
    }

    @Test
    fun GameTestState.`a bank file that cannot be read stops the load`(@TempDir dir: Path) =
        runBankTest { deps, _, _, _ ->
            Files.writeString(dir.resolve(TeamBanks.FILE), "this is not json")

            assertThrows(IllegalStateException::class.java) { deps.banks.load(dir) }
        }

    @Test
    fun GameTestState.`a bank chest opens the team bank for the first teammate only`() =
        runBankTest { deps, alice, bob, carol ->
            val chest = placeChest()
            stand(alice, chest, -1)
            stand(bob, chest, 1)
            stand(carol, chest, 1)

            openChest(deps, bob, chest)
            assertTrue(bob.bankIsOpen())

            // alice is the test's main player, so the message to her is the one the harness
            // records, and it clears sent messages every tick: check straight after the first.
            alice.opLoc1(chest)
            advance(ticks = 1)
            assertMessageSent("bob is using this bank right now.", alice)
            advance(ticks = 2)
            assertFalse(alice.bankIsOpen())

            // A player with their own bank is not held up by anyone else's.
            openChest(deps, carol, chest)
            assertTrue(carol.bankIsOpen())
        }

    @Test
    fun GameTestState.`the other teammate can open it once the first has closed it`() =
        runBankTest { deps, alice, bob, _ ->
            val chest = placeChest()
            stand(alice, chest, -1)
            stand(bob, chest, 1)

            openChest(deps, alice, chest)
            closeBank(deps, alice)

            openChest(deps, bob, chest)
            assertTrue(bob.bankIsOpen())
        }

    @Test
    fun GameTestState.`a bank is still in use until its closing tidy-up has run`() =
        runBankTest { deps, alice, bob, _ ->
            assertNull(deps.access.otherUser(bob))

            // The bank module queues this when the screen closes, and it moves items around.
            alice.queueList.add(BankQueues.bank_compress, QueueCategory.Normal, remainingCycles = 1)
            assertNotNull(deps.access.otherUser(bob))

            alice.queueList.clear()
            assertNull(deps.access.otherUser(bob))
        }

    @Test
    fun GameTestState.`the next teammate gets the tab layout the last one left`() =
        runBankTest { deps, alice, bob, _ ->
            val chest = placeChest()
            stand(alice, chest, -1)
            stand(bob, chest, 1)

            openChest(deps, alice, chest)
            VarPlayerIntMapSetter.set(alice, BankTab.Tab1.sizeVarBit, 3)
            VarPlayerIntMapSetter.set(alice, BankTab.Tab2.sizeVarBit, 2)
            VarPlayerIntMapSetter.set(alice, BankTab.Main.sizeVarBit, 5)
            closeBank(deps, alice)

            openChest(deps, bob, chest)

            assertEquals(3, bob.vars[BankTab.Tab1.sizeVarBit])
            assertEquals(2, bob.vars[BankTab.Tab2.sizeVarBit])
            assertEquals(5, bob.vars[BankTab.Main.sizeVarBit])
        }

    @Test
    fun GameTestState.`a tab that no longer exists is not left selected`() =
        runBankTest { deps, alice, bob, _ ->
            val chest = placeChest()
            stand(alice, chest, -1)
            stand(bob, chest, 1)
            VarPlayerIntMapSetter.set(bob, BankVarBits.selected_tab, BankTab.Tab3.varValue)

            openChest(deps, alice, chest)
            VarPlayerIntMapSetter.set(alice, BankTab.Tab1.sizeVarBit, 4)
            closeBank(deps, alice)

            openChest(deps, bob, chest)

            assertEquals(BankTab.Main.varValue, bob.vars[BankVarBits.selected_tab])
        }

    @Test
    fun GameTestState.`the layout of the player in the bank is the one that gets saved`(
        @TempDir dir: Path
    ) = runBankTest { deps, alice, _, _ ->
        deps.banks.load(dir)
        val chest = placeChest()
        stand(alice, chest, -1)
        openChest(deps, alice, chest)

        // Still open and nobody has closed it, so a periodic save must already know the layout.
        VarPlayerIntMapSetter.set(alice, BankTab.Tab1.sizeVarBit, 6)
        assertTrue(deps.banks.save())
        val saved = TeamBankStore.parse(Files.readString(dir.resolve(TeamBanks.FILE)))
        assertEquals(6, saved[1]!!.layout[BankTab.Tab1.index])
    }
}
