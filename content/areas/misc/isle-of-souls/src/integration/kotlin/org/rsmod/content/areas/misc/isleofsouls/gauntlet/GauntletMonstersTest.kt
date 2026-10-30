package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.game.type.obj.ObjType
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * The maze's monsters: who a lit room holds, who they go for, what they drop and what happens to
 * them when the run ends. The room templates get a ring of wall flags by hand, as in
 * [GauntletPrepTest].
 */
class GauntletMonstersTest {
    private object MonstersModule : AbstractModule() {
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
        val monsters: GauntletMonsters,
    )

    private fun Deps.templates() {
        val origins = ArrayList<Pair<Int, Int>>()
        for (offset in listOf(0, 8)) {
            for (chunkX in listOf(232, 234, 236)) {
                for (chunkZ in listOf(704, 706, 708, 710)) {
                    origins += (chunkX + offset) to chunkZ
                }
            }
            origins += (238 + offset) to 708
            origins += (238 + offset) to 710
        }
        for ((chunkX, chunkZ) in origins) {
            for (level in 0..3) {
                for (x in 0 until 2) {
                    for (z in 0 until 2) {
                        collision.allocateIfAbsent((chunkX + x) * 8, (chunkZ + z) * 8, level)
                    }
                }
            }
            for (i in 0 until 16) {
                for (depth in listOf(0, 1)) {
                    val gap = i in 6..9
                    for ((x, z) in
                        listOf(depth to i, 15 - depth to i, i to depth, i to 15 - depth)) {
                        val doorway =
                            gap && (x == depth || x == 15 - depth || z == depth || z == 15 - depth)
                        if (!doorway) {
                            collision.add(
                                chunkX * 8 + x,
                                chunkZ * 8 + z,
                                1,
                                CollisionFlag.BLOCK_WALK,
                            )
                        }
                    }
                }
            }
        }
        collision.allocateIfAbsent(Gauntlet.LOBBY.x, Gauntlet.LOBBY.z, Gauntlet.LOBBY.level)
    }

    private fun GameTestState.runMonstersTest(
        pick: (Int) -> Int = { 0 },
        body: GameTestScope.(Deps, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            MonstersModule,
            GauntletScript::class,
            GauntletCombatScript::class,
        ) { deps ->
            deps.templates()
            deps.gauntlet.pick = pick
            val alice = player
            alice.username = "alice"
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.bryn, 1)
            body(deps, alice)
        }

    private fun GauntletRun.demiRoom(): GauntletRoom = layout.all().first { it.demiBoss != null }

    /** Lights [room] with the monsters it is to have: `monsters` is forced on. */
    private fun Deps.lightWithMonsters(run: GauntletRun, room: GauntletRoom, random: Random) {
        gauntlet.pick = { random.nextInt(it) }
        room.stocked = true
        room.monsters = true
        gauntlet.lightRoom(run, room)
    }

    private fun Npc.kind(): GauntletMonster = checkNotNull(GauntletMonster.of(type))

    // Rooms -------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a demi-boss room holds its demi-boss in the middle and nothing else`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            for (room in run.layout.all().filter { it.demiBoss != null }) {
                deps.gauntlet.lightRoom(run, room)
                val inRoom = run.monsters.filter { run.roomAt(it.coords) === room }
                assertEquals(1, inRoom.size, "$room")
                assertEquals(room.demiBoss?.name, inRoom.single().kind().name)
                assertEquals(run.roomTile(room, 7, 7), inRoom.single().coords)
            }
            assertEquals(6, run.monsters.size)
            assertEquals(2, run.monsters.count { it.kind() == GauntletMonster.Bear })
        }

    @Test
    fun GameTestState.`the start and boss rooms never hold monsters and a room is filled once`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            assertTrue(run.monsters.isEmpty())
            assertTrue(deps.monsters.populate(run, run.layout.start) { 0 }.isEmpty())
            assertTrue(deps.monsters.populate(run, run.layout.boss) { 0 }.isEmpty())

            val room = run.layout.all().first { it.kind == RoomKind.Middle }
            deps.lightWithMonsters(run, room, Random(1))
            val first = run.monsters.size
            assertTrue(first in 1..4, "$first")
            assertTrue(deps.monsters.populate(run, room) { 0 }.isEmpty())
            assertEquals(first, run.monsters.size)
        }

    @Test
    fun GameTestState.`inside rooms get one to four weak monsters or two strong ones`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            var weakRooms = 0
            var strongRooms = 0
            val middles = run.layout.all().filter { it.kind == RoomKind.Middle && !it.lit }
            for ((i, room) in middles.withIndex()) {
                deps.lightWithMonsters(run, room, Random(i))
                val inRoom = run.monsters.filter { run.roomAt(it.coords) === room }
                val tiers = inRoom.map { it.kind().tier }.toSet()
                assertEquals(1, tiers.size, "$room")
                when (tiers.single()) {
                    GauntletTier.Weak -> {
                        weakRooms++
                        assertTrue(inRoom.size in 1..4)
                    }
                    GauntletTier.Strong -> {
                        strongRooms++
                        assertTrue(inRoom.size in 1..2)
                    }
                    else -> error("a demi-boss in an ordinary room")
                }
            }
            assertTrue(
                weakRooms > 0 && strongRooms > 0,
                "$weakRooms weak rooms, $strongRooms strong",
            )
        }

    @Test
    fun GameTestState.`rim rooms only ever get strong monsters`() = runMonstersTest { deps, alice ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        val rim =
            run.layout.all().filter {
                (it.x == 0 || it.z == 0 || it.x == 6 || it.z == 6) && it.demiBoss == null
            }
        for ((i, room) in rim.withIndex()) {
            deps.lightWithMonsters(run, room, Random(100 + i))
        }
        assertTrue(run.monsters.isNotEmpty())
        assertTrue(run.monsters.all { it.kind().tier == GauntletTier.Strong })
    }

    @Test
    fun GameTestState.`the corrupted maze uses the corrupted monsters`() =
        runMonstersTest { deps, alice ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.done, 1)
            deps.gauntlet.enter(alice, GauntletMode.Corrupted)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            deps.gauntlet.lightRoom(run, run.demiRoom())
            val demi = run.monsters.single()
            assertEquals(GauntletMonster.of(demi.type), demi.kind())
            assertTrue(
                GauntletMonster.entries.any { it.type(GauntletMode.Corrupted).id == demi.type.id }
            )
        }

    // Aggression --------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`a monster goes for a player in its room and not for one in the safe start room`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val room = run.layout.all().first { it.kind == RoomKind.Middle }
            deps.lightWithMonsters(run, room, Random(3))
            val npc = run.monsters.first()

            // The player is in the start room: nothing hunts them.
            deps.gauntlet.tick()
            assertEquals(NpcMode.None, npc.mode)

            alice.coords = run.roomTile(room, 4, 4)
            deps.gauntlet.tick()
            assertTrue(
                npc.mode == NpcMode.OpPlayer2 || npc.mode == NpcMode.ApPlayer2,
                "${npc.mode}",
            )

            // Back in the start room the monster gives up.
            alice.coords = run.roomTile(run.layout.start, 5, 5)
            deps.gauntlet.tick()
            assertEquals(NpcMode.None, npc.mode)
        }

    @Test
    fun GameTestState.`the dragon and the dark beast shoot from a distance`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val rooms =
                run.layout.all().filter {
                    it.demiBoss == DemiBoss.Dragon || it.demiBoss == DemiBoss.DarkBeast
                }
            for (room in rooms) {
                deps.gauntlet.lightRoom(run, room)
            }
            alice.coords = run.roomTile(rooms.first(), 3, 3)
            deps.gauntlet.tick()
            val shooters = run.monsters.filter { run.roomAt(it.coords) === rooms.first() }
            assertEquals(NpcMode.ApPlayer2, shooters.single().mode)
        }

    // Drops -------------------------------------------------------------------------------------

    private fun Deps.owns(player: Player): (ObjType) -> Boolean = {
        it in player.inv || it in player.worn
    }

    private fun GameTestScope.drop(
        deps: Deps,
        state: GauntletPlayerState,
        monster: GauntletMonster,
        pick: (Int) -> Int,
    ): List<Pair<Int, Int>> =
        GauntletDrops.roll(monster, GauntletRegularItems, state, { false }, pick).map {
            it.obj.id to it.count
        }

    @Test
    fun GameTestState
        .`the first weak kill always drops a weapon frame and later ones one time in four`() =
        runMonstersTest { deps, alice ->
            val state = GauntletPlayerState()
            val items = GauntletRegularItems
            // A pick that never rolls the 1 in 4 still gives the frame on the first kill.
            val first = drop(deps, state, GauntletMonster.Rat) { it - 1 }
            assertTrue(first.any { it.first == items.frame.id })
            // 20 to 30 shards come with every weak kill.
            assertEquals(30, first.first { it.first == items.shard.id }.second)
            val second = drop(deps, state, GauntletMonster.Rat) { it - 1 }
            assertFalse(second.any { it.first == items.frame.id })
            val third = drop(deps, state, GauntletMonster.Rat) { 0 }
            assertTrue(third.any { it.first == items.frame.id })
        }

    @Test
    fun GameTestState.`a strong monster is sure to drop a frame by the second kill`() =
        runMonstersTest { deps, alice ->
            val state = GauntletPlayerState()
            val items = GauntletRegularItems
            val first = drop(deps, state, GauntletMonster.Wolf) { it - 1 }
            assertFalse(first.any { it.first == items.frame.id })
            val second = drop(deps, state, GauntletMonster.Wolf) { it - 1 }
            assertTrue(second.any { it.first == items.frame.id })
            val shards = second.first { it.first == items.shard.id }.second
            assertTrue(shards in 80..100)
        }

    @Test
    fun GameTestState
        .`a demi-boss drops a frame and its own component, then the others, then none`() =
        runMonstersTest { deps, alice ->
            val state = GauntletPlayerState()
            val items = GauntletRegularItems
            val bear = drop(deps, state, GauntletMonster.Bear) { 0 }
            assertTrue(bear.any { it.first == items.frame.id })
            assertTrue(bear.any { it.first == items.spike.id })

            // The bear again: it has its own already, so any component the player lacks.
            val again = drop(deps, state, GauntletMonster.Bear) { 0 }
            assertFalse(again.any { it.first == items.spike.id })
            assertTrue(again.any { it.first == items.orb.id || it.first == items.bowstring.id })

            val dragon = drop(deps, state, GauntletMonster.Dragon) { 0 }
            val beast = drop(deps, state, GauntletMonster.DarkBeast) { 0 }
            val components = setOf(items.spike.id, items.orb.id, items.bowstring.id)
            val got =
                (again + dragon + beast)
                    .filter { it.first in components }
                    .map { it.first }
                    .toSet() + items.spike.id
            assertEquals(components, got)

            // All three have dropped: no more.
            val none = drop(deps, state, GauntletMonster.Bear) { 0 }
            assertTrue(none.none { it.first in components })
        }

    @Test
    fun GameTestState.`a component the player already holds is not dropped again`() =
        runMonstersTest { deps, alice ->
            val state = GauntletPlayerState()
            val items = GauntletRegularItems
            val drops =
                GauntletDrops.roll(
                    GauntletMonster.Bear,
                    items,
                    state,
                    { it.id == items.spike.id },
                    { 0 },
                )
            assertFalse(drops.any { it.obj.id == items.spike.id })
            assertTrue(drops.any { it.obj.id == items.orb.id })
        }

    @Test
    fun GameTestState.`the resource roll follows the wiki weights`() =
        runMonstersTest { deps, alice ->
            val items = GauntletRegularItems
            fun weak(roll: Int): List<Int> {
                var calls = 0
                // First call: shards amount, second: the frame odds; the third the roll proper.
                val picks = listOf(0, 3, roll, 0)
                return GauntletDrops.roll(
                        GauntletMonster.Rat,
                        items,
                        GauntletPlayerState().also { it.kills[0] = 5 },
                        { false },
                    ) { n ->
                        picks.getOrElse(calls++) { 0 }.coerceAtMost(n - 1)
                    }
                    .map { it.obj.id }
            }
            // 0..8: nothing, 9..17 shards, 18..20 raw paddlefish, 21-22 grym leaf, 23 teleport
            // crystal.
            assertEquals(listOf(items.shard.id), weak(5))
            assertTrue(items.shard.id in weak(12) && weak(12).size == 2)
            assertTrue(GauntletShared.rawFood.id in weak(19))
            assertTrue(items.grymLeaf.id in weak(21))
            assertTrue(items.teleportCrystal.id in weak(23))
        }

    @Test
    fun GameTestState.`a killed monster drops by the run's rules and scores its points`() =
        runMonstersTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val room = run.layout.all().first { it.kind == RoomKind.Middle }
            deps.lightWithMonsters(run, room, Random(5))
            val npc = run.monsters.first()
            val tier = npc.kind().tier

            val claim = NpcDropEvents.Claim(npc, alice, mutableListOf())
            eventBus.publish(claim)

            // Shards are always there, and the table's own (empty) list was replaced.
            assertTrue(claim.drops.any { it.obj.id == GauntletRegularItems.shard.id })
            assertEquals(tier.points, run.points[alice.accountId])
            assertFalse(npc in run.monsters)
            assertEquals(1, run.state(alice).kills[tier.ordinal])
        }

    // The end -----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the monsters go with the maze`() = runMonstersTest { deps, alice ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        deps.gauntlet.lightRoom(run, run.demiRoom())
        val npc = run.monsters.single()
        assertTrue(npc.isSlotAssigned)

        deps.gauntlet.leave(alice, GauntletExit.Exit)

        assertFalse(npc.isSlotAssigned)
        assertTrue(run.monsters.isEmpty())
        assertNotNull(run)
    }
}
