package org.rsmod.content.areas.misc.isleofsouls.toa

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import kotlin.reflect.KClass
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.event.EventSettings
import org.rsmod.content.areas.misc.isleofsouls.relics.LeagueRelicTracker
import org.rsmod.content.areas.misc.isleofsouls.relics.RelicVarBits
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The raid's shell: the party board, entering the tombs, the Nexus and the paths, deaths and
 * attempts, the Wardens, and the vault with its chest. The test world has no map, so every zone the
 * raid copies is given collision by hand.
 */
class ToaRaidTest {
    private object ToaModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val parties: ToaParties,
        val board: ToaBoard,
        val raids: ToaRaids,
        val rewards: ToaRewards,
        val collision: CollisionFlagMap,
        val tracker: LeagueTaskTracker,
        val objTypes: ObjTypeList,
        val locTypes: LocTypeList,
        val collectionLog: CollectionLog,
    )

    @AfterEach
    fun resetSettings() {
        EventSettings.current = EventSettings()
    }

    private fun GameTestScope.allocate(deps: Deps) {
        for (column in ToaPacking.columns) {
            for (zx in column.realZoneX until column.realZoneX + column.zoneWidth) {
                for (zz in
                    ToaPacking.REAL_ZONE_Z until ToaPacking.REAL_ZONE_Z + ToaPacking.ZONE_ROWS) {
                    for (level in 0 until CoordGrid.LEVEL_COUNT) {
                        deps.collision.allocateIfAbsent(
                            zx * ZoneGrid.LENGTH,
                            zz * ZoneGrid.LENGTH,
                            level,
                        )
                    }
                }
            }
        }
    }

    private fun GameTestState.runToaTest(
        vararg scripts: KClass<out PluginScript>,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(Deps::class, ToaModule, ToaVaultScript::class, *scripts) { deps ->
            allocate(deps)
            // The moves between rooms fade the screen first; the tests that check it turn it on.
            deps.raids.fadeTicks = 0
            val alice = player
            alice.displayName = "Alice"
            // The boss drops are owned by a player, which needs an observer id.
            alice.observerUUID = 1L
            val bob = registerPlayer(coords = ToaLobby.RETURN)
            bob.displayName = "Bob"
            bob.observerUUID = 2L
            PathingEntityCommon.telejump(alice, deps.collision, ToaLobby.RETURN)
            deps.collision.allocateIfAbsent(ToaLobby.RETURN.x, ToaLobby.RETURN.z, 0)
            body(deps, alice, bob)
        }

    /** Alice leads a party of Alice and Bob. */
    private fun Deps.party(alice: Player, bob: Player): ToaParty {
        val party = parties.create(alice)
        assertEquals(PartyResult.Done, parties.apply(party, bob))
        assertEquals(PartyResult.Done, parties.answer(party, bob, accept = true))
        return party
    }

    private fun ToaRaid.inRoom(player: Player): ToaRoom? = roomAt(player.coords)

    /**
     * The scripts that drive the raid each tick and roll its loot are not loaded in these tests.
     */
    private fun GameTestScope.tick(deps: Deps, ticks: Int = 3) {
        repeat(ticks) {
            advance()
            deps.raids.tick()
        }
    }

    /** The vault script rolls the loot when the Wardens fall, as it does in the game. */
    private fun Deps.win(raid: ToaRaid) {
        raids.wardensDefeated(raid)
    }

    // The party board
    // ------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a party is formed, applied to, accepted into and left`() =
        runToaTest { deps, alice, bob ->
            val board = deps.board
            val parties = deps.parties

            // Alice makes a party from the list's My Party button.
            board.openList(alice)
            board.clickList(alice, ToaBoard.ListButton.MY_PARTY)
            val party = checkNotNull(parties.currentParty(alice))
            assertTrue(party.isLeader(alice))
            assertTrue(parties.isListed(party))

            // Bob finds it on the list, opens it and applies.
            board.openList(bob)
            board.selectParty(bob, 0)
            assertSame(party, parties.stateOf(bob).viewing)
            assertEquals(PartyView.NonMember.value, parties.stateOf(bob).viewingValue)
            board.clickDetails(bob, ToaBoard.Button.MEMBER_OPTION)
            assertTrue(party.isApplicant(bob))

            // Alice accepts him.
            board.clickDetails(alice, ToaBoard.Button.ACCEPT_FIRST)
            assertTrue(party.isMember(bob))
            assertFalse(party.isApplicant(bob))
            assertEquals(2, party.members.size)

            // A leader can kick; a member can leave; the next member leads.
            board.clickDetails(alice, ToaBoard.Button.MEMBER_FIRST + 1)
            assertFalse(party.isMember(bob))
            assertNull(parties.currentParty(bob))
        }

    @Test
    fun GameTestState.`a declined player is blocked until the leader lifts it`() =
        runToaTest { deps, alice, bob ->
            val party = deps.parties.create(alice)
            deps.parties.apply(party, bob)
            assertEquals(PartyResult.Done, deps.parties.answer(party, bob, accept = false))
            assertTrue(party.isBlocked(bob))
            deps.parties.stateOf(bob).viewing = party
            deps.board.clickDetails(bob, ToaBoard.Button.MEMBER_OPTION)
            assertFalse(party.isApplicant(bob))
            deps.parties.stateOf(alice).viewing = party
            deps.board.clickDetails(alice, ToaBoard.Button.UNBLOCK)
            assertFalse(party.isBlocked(bob))
        }

    @Test
    fun GameTestState.`the leader switches invocations and a leaving leader hands the board on`() =
        runToaTest { deps, alice, bob ->
            val party = deps.party(alice, bob)
            deps.parties.stateOf(alice).viewing = party
            val catalog = deps.parties.catalog
            val tryAgain = checkNotNull(catalog.byName(Inv.TRY_AGAIN))
            deps.board.clickDetails(alice, ToaBoard.Button.INVOCATION_FIRST + tryAgain.slot)
            assertTrue(party.settings.isActive(Inv.TRY_AGAIN))

            // A member cannot.
            deps.parties.stateOf(bob).viewing = party
            val persistence = checkNotNull(catalog.byName(Inv.PERSISTENCE))
            deps.board.clickDetails(bob, ToaBoard.Button.INVOCATION_FIRST + persistence.slot)
            assertFalse(party.settings.isActive(Inv.PERSISTENCE))

            // Alice leaves: Bob leads, with the same invocations, on his own board.
            deps.parties.leave(party, alice)
            assertTrue(party.isLeader(bob))
            assertTrue(party.settings.isActive(Inv.TRY_AGAIN))
            assertTrue(deps.parties.boardOf(bob).isActive(Inv.TRY_AGAIN))
        }

    @Test
    fun GameTestState.`presets are saved to the player and loaded back`() =
        runToaTest { deps, alice, _ ->
            val party = deps.parties.create(alice)
            deps.parties.stateOf(alice).viewing = party
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.PATHSEEKER)))
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.DEADLY_PRAYERS)))
            val saved = party.settings.words.toList()

            deps.board.clickPreset(alice, 2, clear = false)
            assertEquals(3, alice.vars[toa_varbits.preset_selected])
            deps.board.clickDetails(alice, ToaBoard.Button.SAVE_PRESET)
            assertEquals(saved[0], alice.vars[toa_varps.presets[2][0]])
            assertEquals(0, alice.vars[toa_varbits.preset_selected])

            party.settings.clear()
            deps.board.clickPreset(alice, 2, clear = false)
            deps.board.clickDetails(alice, ToaBoard.Button.LOAD_PRESET)
            assertEquals(saved, party.settings.words.toList())

            deps.board.clickPreset(alice, 2, clear = true)
            assertEquals(0, alice.vars[toa_varps.presets[2][0]])
        }

    // Going in
    // ---------------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the leader starts the raid and the party follows into the Nexus`() =
        runToaTest { deps, alice, bob ->
            assertEquals(ToaEntry.NoParty, deps.raids.enterFromLobby(alice))
            val party = deps.party(alice, bob)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.PATHSEEKER)))

            // A member cannot start it.
            assertEquals(ToaEntry.LeaderFirst, deps.raids.enterFromLobby(bob))

            assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(alice))
            val raid = checkNotNull(deps.raids.raidOf(alice))
            assertFalse(deps.parties.isListed(party))
            assertEquals(15, raid.raidLevel)
            assertEquals(ToaMode.Entry, raid.mode)
            assertEquals(ToaRoom.Nexus, raid.inRoom(alice))
            assertTrue(raid.pathLevels.all { it == 1 })
            assertEquals(1, alice.vars[toa_varps.attempts[ToaMode.Entry.ordinal]])

            assertEquals(ToaEntry.AlreadyInside, deps.raids.enterFromLobby(alice))
            assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(bob))
            assertSame(raid, deps.raids.raidOf(bob))
            assertEquals(ToaRoom.Nexus, raid.inRoom(bob))
            assertEquals(listOf(0, 1), raid.members.map { it.slot })
            assertEquals(raid.region.southWest.x, raid.region.southWest.x.coerceAtLeast(0))
        }

    @Test
    fun GameTestState.`a player who was not in the party cannot join its raid`() =
        runToaTest { deps, alice, bob ->
            deps.parties.create(alice)
            assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(alice))
            // Bob is in no party.
            assertEquals(ToaEntry.NoParty, deps.raids.enterFromLobby(bob))
        }

    @Test
    fun GameTestState.`a player moved out of the tombs is out of the raid and can go in again`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(alice))
            val first = checkNotNull(deps.raids.raidOf(alice))
            // A teleport, a spell or a command takes them out of the region.
            PathingEntityCommon.telejump(alice, deps.collision, ToaLobby.RETURN)
            repeat(10) { tick(deps) }
            assertNull(deps.raids.raidOf(alice))
            assertEquals(RaidStage.Ended, first.stage)
            // The party is still theirs; going in again starts a fresh raid.
            deps.parties.create(alice)
            assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(alice))
            assertNotEquals(first.id, checkNotNull(deps.raids.raidOf(alice)).id)
        }

    @Test
    fun GameTestState.`two raids get separate regions`() = runToaTest { deps, alice, bob ->
        deps.parties.create(alice)
        deps.parties.create(bob)
        assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(alice))
        assertEquals(ToaEntry.Entered, deps.raids.enterFromLobby(bob))
        val first = checkNotNull(deps.raids.raidOf(alice))
        val second = checkNotNull(deps.raids.raidOf(bob))
        assertNotEquals(first.region.southWest, second.region.southWest)
        assertEquals(2, deps.raids.liveRaids().size)
    }

    // Paths
    // --------------------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the leader picks a path, the others follow and the other doors wait`() =
        runToaTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enterFromLobby(alice)
            deps.raids.enterFromLobby(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))

            assertEquals(ToaRaids.PathChoice.LeaderFirst, deps.raids.choosePath(bob, ToaPath.Het))
            assertEquals(ToaRaids.PathChoice.Started, deps.raids.choosePath(alice, ToaPath.Het))
            assertEquals(ToaRoom.HetPuzzle, raid.inRoom(alice))
            assertEquals(ToaPath.Het, raid.currentPath)
            assertTrue(raid.challengeStartCycle >= 0)
            assertEquals(2, alice.vars[toa_varbits.current_path])

            assertEquals(
                ToaRaids.PathChoice.OtherPathChosen,
                deps.raids.choosePath(bob, ToaPath.Crondis),
            )
            assertEquals(ToaRaids.PathChoice.Joined, deps.raids.choosePath(bob, ToaPath.Het))
            assertEquals(ToaRoom.HetPuzzle, raid.inRoom(bob))
        }

    @Test
    fun GameTestState
        .`clearing a path levels the others up with Walk the Path and opens the Wardens`() =
        runToaTest { deps, alice, bob ->
            val party = deps.party(alice, bob)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.WALK_THE_PATH)))
            deps.raids.enterFromLobby(alice)
            deps.raids.enterFromLobby(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.pick = { 0 }

            assertEquals(ToaRaids.WardensEntry.Sealed, deps.raids.enterWardens(alice))

            val order = listOf(ToaPath.Het, ToaPath.Scabaras, ToaPath.Apmeken, ToaPath.Crondis)
            for ((index, path) in order.withIndex()) {
                assertEquals(ToaRaids.PathChoice.Started, deps.raids.choosePath(alice, path))
                deps.raids.completeRoom(raid, path.puzzle)
                deps.raids.completeRoom(raid, path.boss)
                deps.raids.completePath(raid, path)
                deps.raids.returnToNexus(alice)
                deps.raids.returnToNexus(bob)
                assertEquals(ToaRoom.Nexus, raid.inRoom(alice))
                assertEquals(index + 1, raid.completed.size)
                assertEquals(0, alice.vars[toa_varbits.current_path])
            }
            // One clear levels two paths, the next two one each (the first open path each time).
            assertEquals(2 + 1 + 1, raid.pathLevels.sum())
            assertEquals(
                raid.pathLevels[ToaPath.Crondis.index],
                alice.vars[toa_varbits.pathLevel(ToaPath.Crondis)],
            )
            assertTrue(raid.wardensOpen)
            assertEquals(ToaRaids.WardensEntry.LeaderFirst, deps.raids.enterWardens(bob))
            assertEquals(ToaRaids.WardensEntry.Entered, deps.raids.enterWardens(alice))
            assertEquals(ToaRoom.WardensFirst, raid.inRoom(alice))
            assertEquals(RaidStage.Wardens, raid.stage)
            assertEquals(ToaRaids.WardensEntry.Entered, deps.raids.enterWardens(bob))
        }

    @Test
    fun GameTestState.`the opened Wardens door can be clicked`() =
        runToaTest(ToaLobbyScript::class) { deps, alice, _ ->
            EventSettings.current = EventSettings(toa = ToaSettings(pathsRequired = 1))
            deps.parties.create(alice)
            deps.raids.enterFromLobby(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            // The test world has no map, so the shut door is placed by hand on the real tile the
            // raid's region copies.
            placeMapLoc(ToaRaids.WARDENS_DOOR, toa_locs.nexus_wardens, angle = LocAngle.South)
            val door = raid.regionOf(ToaRaids.WARDENS_DOOR)

            deps.raids.completePath(raid, ToaPath.Scabaras)
            val open = checkNotNull(findLoc(door, toa_locs.nexus_wardens_open))
            assertEquals(ToaRoom.Nexus, raid.inRoom(alice))
            alice.teleport(door.translate(3, 3))
            alice.opLoc1(BoundLocInfo(open, deps.locTypes[toa_locs.nexus_wardens_open]))
            advance(ticks = 3)
            assertEquals(ToaRoom.WardensFirst, raid.inRoom(alice))
        }

    @Test
    fun GameTestState.`the event can shorten the raid to fewer paths`() =
        runToaTest { deps, alice, _ ->
            EventSettings.current = EventSettings(toa = ToaSettings(pathsRequired = 1))
            deps.parties.create(alice)
            deps.raids.enterFromLobby(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            assertFalse(raid.wardensOpen)
            deps.raids.completePath(raid, ToaPath.Scabaras)
            assertTrue(raid.wardensOpen)
        }

    // Deaths
    // ---------------------------------------------------------------------------------------------------

    private fun GameTestScope.die(deps: Deps, player: Player) {
        deps.raids.died(player)
        val coords = checkNotNull(deps.raids.respawnFor(player))
        PathingEntityCommon.telejump(player, deps.collision, coords)
    }

    @Test
    fun GameTestState.`a death in a fight makes a ghost until the room is won`() =
        runToaTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enterFromLobby(alice)
            deps.raids.enterFromLobby(bob)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.choosePath(alice, ToaPath.Het)
            deps.raids.choosePath(bob, ToaPath.Het)

            die(deps, bob)
            assertEquals(1, raid.totalDeaths)
            assertEquals(4_000, raid.points.total(bob.accountId))
            assertEquals(5_000, raid.points.total(alice.accountId))
            assertEquals(1, bob.vars[toa_varps.deaths[ToaMode.Entry.ordinal]])
            assertTrue(deps.raids.isGhost(bob))
            assertNotNull(bob.transmog)
            assertEquals(ToaRoom.HetPuzzle, raid.inRoom(bob))

            // Alice still stands, so there is no wipe.
            tick(deps, 2)
            assertEquals(0, raid.teamWipes)

            deps.raids.completeRoom(raid, ToaRoom.HetPuzzle)
            assertFalse(deps.raids.isGhost(bob))
            assertNull(bob.transmog)
        }

    @Test
    fun GameTestState.`a death in the Nexus is just a death`() = runToaTest { deps, alice, _ ->
        deps.parties.create(alice)
        deps.raids.enterFromLobby(alice)
        val raid = checkNotNull(deps.raids.raidOf(alice))
        die(deps, alice)
        assertFalse(deps.raids.isGhost(alice))
        assertEquals(ToaRoom.Nexus, raid.inRoom(alice))
        assertEquals(1, raid.totalDeaths)
    }

    @Test
    fun GameTestState.`a wipe spends an attempt and the room starts again`() =
        runToaTest { deps, alice, _ ->
            val party = deps.parties.create(alice)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.SOFTCORE_RUN)))
            deps.raids.enterFromLobby(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.choosePath(alice, ToaPath.Crondis)

            die(deps, alice)
            tick(deps)
            assertEquals(1, raid.teamWipes)
            assertEquals(2, raid.attemptsLeft)
            assertFalse(deps.raids.isGhost(alice))
            assertEquals(ToaRoom.CrondisPuzzle, raid.inRoom(alice))
            assertFalse(raid.failed)
        }

    @Test
    fun GameTestState.`running out of attempts fails the raid and sends everyone to the lobby`() =
        runToaTest { deps, alice, _ ->
            val party = deps.parties.create(alice)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.HARDCORE_RUN)))
            deps.raids.enterFromLobby(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.choosePath(alice, ToaPath.Scabaras)

            die(deps, alice)
            tick(deps)
            assertTrue(raid.failed)
            assertNull(deps.raids.raidOf(alice))
            assertEquals(ToaLobby.RETURN, alice.coords)
            assertEquals(RaidStage.Ended, raid.stage)
            // Nothing is lost on this server: a failed raid is a normal death.
            assertTrue(deps.raids.liveRaids().isEmpty())
        }

    // The Wardens and the vault
    // ----------------------------------------------------------------------------

    private fun GameTestScope.reachWardens(deps: Deps, alice: Player, bob: Player?): ToaRaid {
        EventSettings.current = EventSettings(toa = ToaSettings(pathsRequired = 1))
        deps.raids.enterFromLobby(alice)
        bob?.let { deps.raids.enterFromLobby(it) }
        val raid = checkNotNull(deps.raids.raidOf(alice))
        deps.raids.choosePath(alice, ToaPath.Het)
        bob?.let { deps.raids.choosePath(it, ToaPath.Het) }
        deps.raids.completeRoom(raid, ToaRoom.HetPuzzle)
        deps.raids.completeRoom(raid, ToaRoom.HetBoss)
        deps.raids.completePath(raid, ToaPath.Het)
        deps.raids.returnToNexus(alice)
        bob?.let { deps.raids.returnToNexus(it) }
        assertEquals(ToaRaids.WardensEntry.Entered, deps.raids.enterWardens(alice))
        bob?.let { deps.raids.enterWardens(it) }
        deps.raids.advanceWardens(raid)
        assertEquals(ToaRoom.WardensFinal, raid.inRoom(alice))
        return raid
    }

    @Test
    fun GameTestState.`beating the Wardens opens the vault and fills each chest`() =
        runToaTest { deps, alice, bob ->
            val party = deps.party(alice, bob)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.PATHSEEKER)))
            val raid = reachWardens(deps, alice, bob)
            raid.points.damage(alice.accountId, 6_000)
            raid.points.damage(bob.accountId, 2_000)

            // Dice that never give a unique, a thread or a jewel; common rolls are the first line.
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)

            assertEquals(RaidStage.Vault, raid.stage)
            assertEquals(ToaRoom.Vault, raid.inRoom(alice))
            assertEquals(ToaRoom.Vault, raid.inRoom(bob))
            assertEquals(1, alice.vars[toa_varps.completions[ToaMode.Entry.ordinal]])
            assertFalse(raid.uniqueFound)
            assertTrue(deps.rewards.hasLoot(alice))
            assertTrue(deps.rewards.hasLoot(bob))
            assertEquals(1, alice.vars[toa_varbits.should_have_loot])
            // Entry mode does not count for the league's completion task.
            assertFalse(deps.tracker.isComplete(alice, ToaRewards.TASK_COMPLETE))
        }

    /** Level 150 is Normal mode: Pathseeker, Pathfinder is exclusive, so use others. */
    private fun normalModeParty(deps: Deps, alice: Player, bob: Player) {
        val party = deps.party(alice, bob)
        for (name in
            listOf(
                Inv.PATHSEEKER,
                Inv.DEADLY_PRAYERS,
                Inv.DOUBLE_TROUBLE,
                Inv.ON_A_DIET,
                Inv.DEHYDRATION,
                Inv.AERIAL_ASSAULT,
                Inv.MEDIC,
                Inv.UPSET_STOMACH,
                Inv.BOULDERDASH,
            )) {
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(name)))
        }
    }

    private fun chestNames(deps: Deps, player: Player): List<String?> =
        deps.rewards.chest(player).let { c ->
            (0 until c.size).mapNotNull { c[it] }.map { deps.objTypes[it.id]?.name }
        }

    @Test
    fun GameTestState.`a unique goes to one player and takes their common rolls`() =
        runToaTest { deps, alice, bob ->
            normalModeParty(deps, alice, bob)
            val raid = reachWardens(deps, alice, bob)
            // Team uniques off, as in the real game.
            EventSettings.current =
                EventSettings(
                    toa = ToaSettings(pathsRequired = 1, teamUniquesFromLevel = Int.MAX_VALUE)
                )
            assertTrue(raid.raidLevel >= 100)
            raid.points.damage(alice.accountId, 20_000)
            raid.points.completeRoom()

            // Every roll comes up 0: the unique chance hits, the fang is first in the table, and
            // Alice (index 0 of the weighted pick) is the finder.
            deps.rewards.pick = { 0 }
            deps.win(raid)

            assertTrue(raid.uniqueFound)
            assertSame(alice, raid.uniqueFinder)
            val names = chestNames(deps, alice)
            assertTrue("Osmumten's fang" in names, "$names")
            val bobNames = chestNames(deps, bob)
            assertFalse("Osmumten's fang" in bobNames, "$bobNames")
            assertTrue(bobNames.isNotEmpty())
        }

    @Test
    fun GameTestState.`with team uniques everyone else gets a unique in their own chest`() =
        runToaTest { deps, alice, bob ->
            normalModeParty(deps, alice, bob)
            val raid = reachWardens(deps, alice, bob)
            raid.points.damage(alice.accountId, 20_000)
            raid.points.damage(bob.accountId, 5_000)
            raid.points.completeRoom()

            deps.rewards.pick = { 0 }
            deps.win(raid)

            // Alice's unique is the sarcophagus one; Bob gets his own in place of his commons.
            assertSame(alice, raid.uniqueFinder)
            assertTrue("Osmumten's fang" in chestNames(deps, alice))
            val bobNames = chestNames(deps, bob)
            assertTrue("Osmumten's fang" in bobNames, "$bobNames")
            assertFalse("Coins" in bobNames, "$bobNames")
            assertFalse("Fossilised dung" in bobNames, "$bobNames")
        }

    @Test
    fun GameTestState.`the unique chance sums each player's points past the 64,000 cap`() =
        runToaTest { deps, alice, bob ->
            val party = deps.party(alice, bob)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.PATHSEEKER)))
            val raid = reachWardens(deps, alice, bob)
            repeat(2) {
                raid.points.damage(alice.accountId, 20_000)
                raid.points.damage(bob.accountId, 20_000)
                raid.points.completeRoom()
            }
            assertTrue(raid.points.teamLootPoints > 80_000)

            // A unique roll that a single team-wide cap (59,000 loot points) would miss.
            val level = raid.finalRaidLevel
            val capped = ToaLootMath.uniqueChance(59_000, level)
            val summed = ToaLootMath.uniqueChance(raid.points.teamLootPoints, level)
            assertTrue(capped < summed, "level $level: $capped vs $summed")
            val roll = ((capped + summed) / 2 * 1_000_000).toInt()
            deps.rewards.pick = { n -> if (n == 1_000_000) roll else 0 }
            deps.win(raid)

            assertTrue(raid.uniqueFound)
        }

    @Test
    fun GameTestState.`the drop rate relics boost the unique chance by each player's share`() =
        runToaTest { deps, alice, bob ->
            val party = deps.party(alice, bob)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.PATHSEEKER)))
            val raid = reachWardens(deps, alice, bob)
            raid.points.award(alice.accountId, 6_000)
            raid.points.award(bob.accountId, 2_000)
            assertEquals(1.0, deps.rewards.relicBoost(raid))

            // Alice has a tier 1 relic and earned three quarters of the points; Bob has none.
            VarPlayerIntMapSetter.set(alice, RelicVarBits.selection[0], 1)
            val relic = LeagueRelicTracker().dropMultiplier(alice)
            assertTrue(relic > 1.0)
            val boost = deps.rewards.relicBoost(raid)
            assertEquals(0.75 * relic + 0.25, boost, 1e-9)

            // A roll above the wiki's chance but under the boosted one finds a unique.
            val base = ToaLootMath.uniqueChance(raid.points.teamLootPoints, raid.finalRaidLevel)
            val roll = ((base + base * boost) / 2 * 1_000_000).toInt()
            assertTrue(roll > base * 1_000_000)
            deps.rewards.pick = { n -> if (n == 1_000_000) roll else 0 }
            deps.win(raid)

            assertTrue(raid.uniqueFound)
        }

    @Test
    fun GameTestState.`the chest is taken into the inventory or the bank and then empties`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            val raid = reachWardens(deps, alice, null)
            raid.points.damage(alice.accountId, 10_000)
            raid.points.completeRoom()
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)
            assertTrue(deps.rewards.hasLoot(alice))

            deps.rewards.open(alice)
            deps.rewards.takeAll(alice, toBank = false)
            assertFalse(deps.rewards.hasLoot(alice))
            assertEquals(0, alice.vars[toa_varbits.should_have_loot])
            assertTrue(alice.inv.occupiedSpace() > 0)
        }

    @Test
    fun GameTestState.`the collection log counts the loot only once it is claimed`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            val raid = reachWardens(deps, alice, null)
            raid.points.damage(alice.accountId, 10_000)
            raid.points.completeRoom()
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)
            for (obj in
                (0 until deps.rewards.chest(alice).size).mapNotNull {
                    deps.rewards.chest(alice)[it]
                }) {
                assertEquals(0, deps.collectionLog.owned(alice, obj.id), "nothing given away yet")
            }

            // A pet (in the log) left in the chest, as if from the lobby's rewards hole.
            val pet = checkNotNull(deps.objTypes.values.first { it.name == "Tumeken's guardian" })
            deps.rewards.discardAll(alice)
            alice.invAdd(deps.rewards.chest(alice), pet, 1)
            assertEquals(0, deps.collectionLog.owned(alice, pet.id))
            deps.rewards.open(alice)
            deps.rewards.take(alice, 0, 1)
            assertEquals(1, deps.collectionLog.owned(alice, pet.id), "logged on claiming")
        }

    @Test
    fun GameTestState.`take all moves whole stacks even after a partial take`() =
        runToaTest { deps, alice, _ ->
            val coins = checkNotNull(deps.objTypes.values.first { it.internalName == "coins" })
            val runes = checkNotNull(deps.objTypes.values.first { it.internalName == "deathrune" })
            val chest = deps.rewards.chest(alice)
            alice.invAdd(chest, coins, 1_000)
            alice.invAdd(chest, runes, 400)
            deps.rewards.take(alice, 0, 5)
            assertEquals(5, alice.inv.count(coins))
            deps.rewards.takeAll(alice, toBank = false)
            assertEquals(1_000, alice.inv.count(coins))
            assertEquals(400, alice.inv.count(runes))
            assertTrue(chest.isEmpty())

            // Take-All on one slot takes the whole stack.
            alice.invAdd(chest, coins, 700)
            deps.rewards.take(alice, 0, Int.MAX_VALUE)
            assertEquals(1_700, alice.inv.count(coins))
            assertTrue(chest.isEmpty())
        }

    @Test
    fun GameTestState.`the chest buttons take whole stacks through the real click path`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            val raid = reachWardens(deps, alice, null)
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)
            val coins = checkNotNull(deps.objTypes.values.first { it.internalName == "coins" })
            val runes = checkNotNull(deps.objTypes.values.first { it.internalName == "deathrune" })
            val chest = deps.rewards.chest(alice)
            deps.rewards.discardAll(alice)
            alice.invAdd(chest, coins, 1_000)
            alice.invAdd(chest, runes, 400)
            deps.rewards.open(alice)

            // Take-5 then Take-All on the same stack.
            alice.ifButton(toa_components.chests_items, 0, IfButtonOp.Op2)
            advance()
            assertEquals(5, alice.inv.count(coins))
            alice.ifButton(toa_components.chests_items, 0, IfButtonOp.Op4)
            advance()
            assertEquals(1_000, alice.inv.count(coins))

            // The Take-all button takes everything left.
            alice.ifButton(toa_components.chests_inventory)
            advance()
            assertEquals(400, alice.inv.count(runes))
            assertTrue(chest.isEmpty())
        }

    @Test
    fun GameTestState.`take all handles a full roll of notes, uniques and repeats`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            val raid = reachWardens(deps, alice, null)
            raid.points.damage(alice.accountId, 20_000)
            raid.points.completeRoom()
            // Every roll is the last line of the table's 27 (the cache of runes) with no
            // tertiaries.
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)
            val chest = deps.rewards.chest(alice)
            val before =
                (0 until chest.size)
                    .mapNotNull { chest[it] }
                    .groupBy { it.id }
                    .mapValues { (_, stacks) -> stacks.sumOf { it.count } }
            assertTrue(before.isNotEmpty())
            deps.rewards.open(alice)
            deps.rewards.takeAll(alice, toBank = false)
            assertTrue(chest.isEmpty())
            for ((id, count) in before) {
                assertEquals(count, alice.inv.count(checkNotNull(deps.objTypes[id])), "obj $id")
            }
        }

    @Test
    fun GameTestState.`normal mode completions count for the league tasks`() =
        runToaTest { deps, alice, _ ->
            val party = deps.parties.create(alice)
            for (name in
                listOf(
                    Inv.PATHSEEKER,
                    Inv.DEADLY_PRAYERS,
                    Inv.DOUBLE_TROUBLE,
                    Inv.ON_A_DIET,
                    Inv.DEHYDRATION,
                    Inv.AERIAL_ASSAULT,
                    Inv.MEDIC,
                    Inv.UPSET_STOMACH,
                    Inv.BOULDERDASH,
                )) {
                party.settings.toggle(checkNotNull(deps.parties.catalog.byName(name)))
            }
            val raid = reachWardens(deps, alice, null)
            assertEquals(ToaMode.Normal, raid.mode)
            deps.rewards.pick = { n -> n - 1 }
            deps.win(raid)
            assertEquals(1, alice.vars[toa_varps.completions[ToaMode.Normal.ordinal]])
            assertTrue(deps.tracker.isComplete(alice, ToaRewards.TASK_COMPLETE))
            assertFalse(deps.tracker.isComplete(alice, ToaRewards.TASK_25))
        }

    @Test
    fun GameTestState.`missing the target time costs the raid levels`() =
        runToaTest { deps, alice, _ ->
            val party = deps.parties.create(alice)
            party.settings.toggle(checkNotNull(deps.parties.catalog.byName(Inv.WALK_FOR_IT)))
            val raid = reachWardens(deps, alice, null)
            raid.challengeStartCycle = 0
            // Walk for It is worth 10 levels and asks for 40 minutes (4,000 ticks); missing it
            // costs 20.
            assertEquals(10, raid.levelAt(3_999))
            assertEquals(0, raid.levelAt(4_001))
        }

    @Test
    fun GameTestState.`leaving the last way out ends the raid`() = runToaTest { deps, alice, bob ->
        deps.party(alice, bob)
        deps.raids.enterFromLobby(alice)
        deps.raids.enterFromLobby(bob)
        val raid = checkNotNull(deps.raids.raidOf(alice))
        assertTrue(deps.raids.leave(alice, ToaExit.Abandoned))
        assertEquals(ToaLobby.RETURN, alice.coords)
        assertNull(deps.raids.raidOf(alice))
        assertEquals(bob, raid.leader?.player)
        assertEquals(RaidStage.Paths, raid.stage)
        assertTrue(deps.raids.leave(bob, ToaExit.Abandoned))
        assertEquals(RaidStage.Ended, raid.stage)
        // Alice cannot go back in.
        assertEquals(ToaEntry.NoParty, deps.raids.enterFromLobby(alice))
    }

    private fun assertSame(expected: Any?, actual: Any?) =
        org.junit.jupiter.api.Assertions.assertSame(expected, actual)

    // Playtest fixes
    // ----------------------------------------------------------------------------

    @Test
    fun GameTestState.`moving between rooms fades to black before the move`() =
        runToaTest { deps, alice, bob ->
            deps.party(alice, bob)
            deps.raids.enterFromLobby(alice)
            val raid = checkNotNull(deps.raids.raidOf(alice))
            deps.raids.fadeTicks = ToaRaids.FADE_TICKS
            assertEquals(ToaRaids.PathChoice.Started, deps.raids.choosePath(alice, ToaPath.Het))
            assertEquals(
                ToaRoom.Nexus,
                raid.inRoom(alice),
                "still in the Nexus while it goes black",
            )
            tick(deps, ToaRaids.FADE_TICKS)
            assertEquals(ToaRoom.HetPuzzle, raid.inRoom(alice))
        }

    @Test
    fun GameTestState.`the final Warden phase starts on the arena floor, not the respawn spot`() =
        runToaTest { deps, alice, _ ->
            deps.parties.create(alice)
            val raid = reachWardens(deps, alice, null)
            val real = checkNotNull(raid.realOf(alice.coords))
            val floor = checkNotNull(ToaRoom.WardensFinal.challenge)
            assertTrue(real.z <= floor.z + 2, "landed at $real, the floor is at $floor")
        }
}
