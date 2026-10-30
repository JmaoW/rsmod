package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.cooking.scripts.Cooking
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.loc.LocTypeList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Resource nodes, gathering, the start room's stations and what the singing bowl and the Egniol
 * chain make. The test world has no map, so each room template gets a ring of wall flags by hand
 * (with a gap in the middle of every side for the doorways) for the stocking to read.
 */
class GauntletPrepTest {
    private object PrepModule : AbstractModule() {
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
        val resources: GauntletResources,
        val crafting: GauntletCrafting,
        val launcher: ProtectedAccessLauncher,
        val locRepo: LocRepository,
        val locTypes: LocTypeList,
        val objTypes: ObjTypeList,
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
            // Walls round the room, open in the middle of each side.
            for (i in 0 until 16) {
                for (depth in listOf(0, 1)) {
                    val gap = i in 6..9
                    val ring = listOf(depth to i, 15 - depth to i, i to depth, i to 15 - depth)
                    for ((x, z) in ring) {
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

    private fun GameTestState.runPrepTest(
        pick: (Int) -> Int = { 0 },
        body: GameTestScope.(Deps, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            PrepModule,
            GauntletScript::class,
            GauntletPrepScript::class,
            Cooking::class,
        ) { deps ->
            deps.templates()
            deps.gauntlet.pick = pick
            val alice = player
            alice.username = "alice"
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.bryn, 1)
            body(deps, alice)
        }

    private fun Deps.nodesIn(
        run: GauntletRun,
        room: GauntletRoom,
    ): List<Pair<GauntletNode, CoordGrid>> {
        val sw = run.region.southWestZone
        val found = ArrayList<Pair<GauntletNode, CoordGrid>>()
        for (dx in 0 until 2) {
            for (dz in 0 until 2) {
                val zone =
                    org.rsmod.map.zone.ZoneKey(sw.x + room.x * 2 + dx, sw.z + room.z * 2 + dz, 1)
                for (loc in locRepo.findAll(zone)) {
                    val kind =
                        GauntletNode.entries.firstOrNull { it.type(run.mode).id == loc.id }
                            ?: continue
                    found += kind to loc.coords
                }
            }
        }
        return found
    }

    private fun GameTestScope.give(player: Player, obj: ObjType, count: Int = 1) {
        player.invAdd(player.inv, obj, count)
    }

    private fun Player.count(deps: Deps, obj: ObjType): Int = inv.count(deps.objTypes[obj])

    // Stocking ----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a lit room is stocked once and the start and boss rooms never`() =
        runPrepTest(pick = { 0 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            assertTrue(deps.nodesIn(run, run.layout.start).isEmpty())
            assertTrue(deps.nodesIn(run, run.layout.boss).isEmpty())
            assertFalse(run.layout.start.stocked)

            val next = run.layout.room(run.layout.start.x - 1, run.layout.start.z)!!
            deps.gauntlet.lightRoom(run, next)

            assertTrue(next.stocked)
            // pick = 0: no gathering nodes, monsters in the room, and the one grym root.
            assertTrue(next.monsters)
            assertEquals(listOf(GauntletNode.Grym), deps.nodesIn(run, next).map { it.first })
            assertTrue(deps.resources.stock(run, next) { 0 }.isEmpty())
        }

    @Test
    fun GameTestState.`stocked rooms keep their rules for any roll`() = runPrepTest { deps, alice ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        val rooms =
            run.layout.all().filter { it.kind != RoomKind.Start && it.kind != RoomKind.Boss }
        var gathered = 0
        for ((i, room) in rooms.withIndex()) {
            val random = Random(i)
            deps.gauntlet.pick = { random.nextInt(it) }
            deps.gauntlet.lightRoom(run, room)
            val placed = deps.nodesIn(run, room)
            if (room.demiBoss != null) {
                assertTrue(placed.isEmpty(), "$room")
                continue
            }
            val gather = placed.count { it.first != GauntletNode.Grym }
            assertTrue(gather <= 3, "$room $gather")
            assertTrue(room.monsters || gather == 0 || gather >= 2, "$room $gather")
            gathered += gather
            // Nobody gets two nodes on the same tile or in a doorway's approach.
            assertEquals(placed.size, placed.map { it.second }.toSet().size)
            for ((kind, at) in placed) {
                val x = at.x - run.roomTile(room, 0, 0).x
                val z = at.z - run.roomTile(room, 0, 0).z
                assertTrue(x in 2..13 && z in 2..13, "$kind at $x,$z")
                assertFalse(z in 6..9 && (x < 5 || x > 10), "doorway approach $x,$z")
                assertFalse(x in 6..9 && (z < 5 || z > 10), "doorway approach $x,$z")
            }
        }
        assertTrue(gathered > 0)
    }

    // Gathering ---------------------------------------------------------------------------------

    private fun GameTestScope.placeNode(
        deps: Deps,
        run: GauntletRun,
        kind: GauntletNode,
        at: CoordGrid,
    ): BoundLocInfo {
        val type = deps.locTypes.getValue(kind.type(run.mode).id)
        val loc =
            deps.locRepo.add(at, type, Int.MAX_VALUE, LocAngle.West, LocShape.CentrepieceStraight)
        run.nodeLeft[at] = kind.harvests
        return BoundLocInfo(loc, type)
    }

    @Test
    fun GameTestState.`mining a deposit gives three ores and spends it`() =
        runPrepTest(pick = { 1 }) { deps, alice ->
            GauntletNode.NODES_DEPLETE = true
            try {
                deps.gauntlet.enter(alice, GauntletMode.Regular)
                val run = checkNotNull(deps.gauntlet.runOf(alice))
                val deposit =
                    placeNode(deps, run, GauntletNode.Deposit, alice.coords.translate(1, 0))
                val before = alice.statMap.getFineXP(stats.mining)

                alice.opLoc1(deposit)
                advance(12)

                assertEquals(3, alice.count(deps, GauntletRegularItems.ore))
                assertTrue(alice.statMap.getFineXP(stats.mining) > before)
                // The node is spent: its place now holds the spent version.
                val at = deps.locRepo.findAll(deposit.coords).map { it.id }.toList()
                assertEquals(listOf(GauntletNode.Deposit.spentType(GauntletMode.Regular).id), at)
                assertFalse(deposit.coords in run.nodeLeft)
            } finally {
                GauntletNode.NODES_DEPLETE = false
            }
        }

    @Test
    fun GameTestState.`a deposit keeps giving ore when nodes do not deplete`() =
        runPrepTest(pick = { 1 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val deposit = placeNode(deps, run, GauntletNode.Deposit, alice.coords.translate(1, 0))

            alice.opLoc1(deposit)
            advance(20)

            assertTrue(alice.count(deps, GauntletRegularItems.ore) > 3)
            val at = deps.locRepo.findAll(deposit.coords).map { it.id }.toList()
            assertEquals(listOf(GauntletNode.Deposit.type(GauntletMode.Regular).id), at)
        }

    @Test
    fun GameTestState.`a tool is needed and a full pack stops the harvest`() =
        runPrepTest(pick = { 1 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val roots = placeNode(deps, run, GauntletNode.Roots, alice.coords.translate(1, 0))

            alice.inv.fillNulls()
            alice.invAdd(alice.inv, GauntletRegularItems.pickaxe, 1)
            alice.opLoc1(roots)
            advance(8)
            assertEquals(0, alice.count(deps, GauntletRegularItems.bark))

            alice.invAdd(alice.inv, GauntletRegularItems.axe, 1)
            repeat(26) { alice.invAdd(alice.inv, GauntletShared.burntFood, 1) }
            alice.opLoc1(roots)
            advance(12)
            // Only the one free slot could be filled; bark stacks nowhere.
            assertTrue(alice.count(deps, GauntletRegularItems.bark) <= 1)
        }

    @Test
    fun GameTestState.`a harvest can also find shards`() =
        runPrepTest(pick = { 0 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val linum = placeNode(deps, run, GauntletNode.Linum, alice.coords.translate(1, 0))

            alice.opLoc1(linum)
            advance(12)

            assertEquals(3, alice.count(deps, GauntletRegularItems.linum))
            // pick = 0 always rolls the shards, and the amount is the minimum of 5.
            assertEquals(15, alice.count(deps, GauntletRegularItems.shard))
        }

    // The stations ------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the water pump fills every vial and the range cooks the fish`() =
        runPrepTest(pick = { n -> n - 1 }) { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val pump = placeNode(deps, run, GauntletNode.Deposit, alice.coords.translate(1, 0))
            // Reuse the placement helper for a pump: the sink loc has the same footprint.
            val sink =
                BoundLocInfo(
                    deps.locRepo.add(
                        alice.coords.translate(0, 1),
                        deps.locTypes.getValue(gauntlet_locs.sink.id),
                        Int.MAX_VALUE,
                        LocAngle.West,
                        LocShape.CentrepieceStraight,
                    ),
                    deps.locTypes.getValue(gauntlet_locs.sink.id),
                )
            repeat(3) { give(alice, GauntletRegularItems.vial) }

            alice.opLoc1(sink)
            advance(8)
            assertEquals(0, alice.count(deps, GauntletRegularItems.vial))
            assertEquals(3, alice.count(deps, GauntletShared.waterVial))

            val range =
                BoundLocInfo(
                    deps.locRepo.add(
                        alice.coords.translate(0, -1),
                        deps.locTypes.getValue(gauntlet_locs.range.id),
                        Int.MAX_VALUE,
                        LocAngle.West,
                        LocShape.CentrepieceStraight,
                    ),
                    deps.locTypes.getValue(gauntlet_locs.range.id),
                )
            alice.stats[stats.cooking] = 60
            repeat(2) { give(alice, GauntletShared.rawFood) }
            val cooking = alice.statMap.getFineXP(stats.cooking)
            alice.opLoc1(range)
            advance(14)

            assertEquals(0, alice.count(deps, GauntletShared.rawFood))
            assertEquals(2, alice.count(deps, GauntletShared.food))
            assertTrue(alice.statMap.getFineXP(stats.cooking) > cooking)
            assertEquals(2, run.points[alice.accountId])
            assertTrue(pump.coords != range.coords)
        }

    // The singing bowl --------------------------------------------------------------------------

    @Test
    fun GameTestState.`a fresh player is offered ten things and each upgrade replaces its piece`() =
        runPrepTest { deps, alice ->
            val mode = GauntletMode.Regular
            val owned = HashSet<Int>()
            fun offer() = GauntletRecipes.available(mode) { it.id in owned }

            assertEquals(10, offer().size)
            val first = offer().filter { it.gear == GearKind.Armour }
            assertEquals(listOf(50, 50, 50), first.map { it.shards })

            owned += GauntletRegularItems.helm[0].id
            val helm =
                offer().first {
                    it.gear == GearKind.Armour && it.product.id == GauntletRegularItems.helm[1].id
                }
            assertEquals(2 - 1, helm.needs.count { it.first.id == GauntletRegularItems.helm[0].id })
            assertEquals(50, helm.shards)
            assertEquals(1, helm.tier)

            owned += GauntletRegularItems.helm[1].id
            val perfect = offer().first { it.product.id == GauntletRegularItems.helm[2].id }
            assertEquals(100, perfect.shards)

            owned += GauntletRegularItems.helm[2].id
            assertEquals(9, offer().size)
        }

    @Test
    fun GameTestState.`a full set costs what the wiki says`() = runPrepTest { deps, alice ->
        val mode = GauntletMode.Regular
        val owned = HashSet<Int>()
        var shards = 0
        var resources = 0
        val totals = ArrayList<Pair<Int, Int>>()
        for (tier in 0..2) {
            shards = 0
            resources = 0
            for (piece in listOf(mode.items.helm, mode.items.body, mode.items.legs)) {
                val recipe =
                    GauntletRecipes.available(mode) { it.id in owned }
                        .first { it.product.id == piece[tier].id }
                shards += recipe.shards
                resources += recipe.needs.first { it.first.id == mode.items.ore.id }.second
                owned += piece[tier].id
            }
            totals += shards to resources
        }
        // 150 / 200 / 300 more shards, so 150 / 350 / 650 in all; 3 / 4 / 6 more of each resource.
        assertEquals(listOf(150, 200, 300), totals.map { it.first })
        assertEquals(listOf(3, 4, 6), totals.map { it.second })
    }

    @Test
    fun GameTestState.`the bowl takes materials and shards and gives the product and its xp`() =
        runPrepTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            val items = GauntletRegularItems
            give(alice, items.shard, 120)
            give(alice, items.ore, 1)
            give(alice, items.bark, 1)
            give(alice, items.linum, 1)
            val crafting = alice.statMap.getFineXP(stats.crafting)
            val smithing = alice.statMap.getFineXP(stats.smithing)

            val recipe =
                GauntletRecipes.available(run.mode) { false }
                    .first { it.product.id == items.helm[0].id }
            with(deps.crafting) { deps.launcher.launch(alice) { make(run, recipe, 1) } }
            advance()

            assertEquals(1, alice.count(deps, items.helm[0]))
            assertEquals(70, alice.count(deps, items.shard))
            assertEquals(0, alice.count(deps, items.ore))
            assertTrue(alice.statMap.getFineXP(stats.crafting) > crafting)
            assertTrue(alice.statMap.getFineXP(stats.smithing) > smithing)
            assertEquals(2, run.points[alice.accountId])
        }

    @Test
    fun GameTestState.`a worn piece is upgraded where it is worn`() = runPrepTest { deps, alice ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        val run = checkNotNull(deps.gauntlet.runOf(alice))
        val items = GauntletRegularItems
        alice.worn[Wearpos.Hat.slot] = org.rsmod.game.inv.InvObj(items.helm[0])
        give(alice, items.shard, 60)
        give(alice, items.ore, 1)
        give(alice, items.bark, 1)
        give(alice, items.linum, 1)

        val recipe =
            GauntletRecipes.available(run.mode) { it.id == items.helm[0].id }
                .first { it.product.id == items.helm[1].id }
        with(deps.crafting) { deps.launcher.launch(alice) { make(run, recipe, 1) } }
        advance()

        assertEquals(items.helm[1].id, alice.worn[Wearpos.Hat.slot]?.id)
        assertEquals(0, alice.count(deps, items.helm[0]))
        assertEquals(0, alice.count(deps, items.helm[1]))
        assertEquals(10, alice.count(deps, items.shard))
        assertEquals(5, run.points[alice.accountId])
    }

    @Test
    fun GameTestState.`without the materials the bowl makes nothing`() =
        runPrepTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            give(alice, GauntletRegularItems.shard, 49)
            val recipe =
                GauntletRecipes.available(run.mode) { false }
                    .first { it.product.id == GauntletRegularItems.teleportCrystal.id }
            with(deps.crafting) { deps.launcher.launch(alice) { make(run, recipe, 1) } }
            advance()

            assertEquals(49, alice.count(deps, GauntletRegularItems.shard))
            assertEquals(1, alice.count(deps, GauntletRegularItems.teleportCrystal))
        }

    // Egniol ------------------------------------------------------------------------------------

    @Test
    fun GameTestState.`vial, grym leaf and ten dust make an Egniol potion`() =
        runPrepTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val items = GauntletRegularItems
            give(alice, GauntletShared.waterVial)
            give(alice, items.grymLeaf)
            give(alice, items.shard, 10)
            val herblore = alice.statMap.getFineXP(stats.herblore)

            with(deps.crafting) {
                deps.launcher.launch(alice) {
                    addLeaf(items)
                    grind(items)
                    finishPotion(items)
                }
            }
            advance()

            assertEquals(0, alice.count(deps, GauntletShared.waterVial))
            assertEquals(0, alice.count(deps, GauntletShared.grymPotion))
            assertEquals(0, alice.count(deps, items.shard))
            assertEquals(0, alice.count(deps, items.dust))
            assertEquals(1, alice.count(deps, GauntletShared.egniol))
            assertTrue(alice.statMap.getFineXP(stats.herblore) > herblore)
        }

    @Test
    fun GameTestState.`nine shards are not enough to grind`() = runPrepTest { deps, alice ->
        deps.gauntlet.enter(alice, GauntletMode.Regular)
        give(alice, GauntletRegularItems.shard, 9)
        with(deps.crafting) { deps.launcher.launch(alice) { grind(GauntletRegularItems) } }
        advance()
        assertEquals(9, alice.count(deps, GauntletRegularItems.shard))
        assertEquals(0, alice.count(deps, GauntletRegularItems.dust))
    }

    // The escape crystal ------------------------------------------------------------------------

    @Test
    fun GameTestState.`the escape crystal takes a player out and the kit with it`() =
        runPrepTest { deps, alice ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            give(alice, GauntletRegularItems.escapeCrystal)
            assertTrue(deps.gauntlet.useEscapeCrystal(alice))
            assertFalse(deps.gauntlet.useEscapeCrystal(alice))

            assertEquals(Gauntlet.LOBBY, alice.coords)
            assertEquals(0, alice.inv.filterNotNull().size + alice.worn.filterNotNull().size)
        }
}
