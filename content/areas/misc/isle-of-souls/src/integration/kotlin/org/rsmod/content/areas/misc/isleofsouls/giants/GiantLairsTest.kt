package org.rsmod.content.areas.misc.isleofsouls.giants

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid

class GiantLairsTest {
    private object GiantModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Obor::class.java).`in`(Scopes.SINGLETON)
            bind(Bryophyta::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val obor: Obor,
        val bryophyta: Bryophyta,
        val tracker: LeagueTaskTracker,
        val dropTables: DropTables,
        val bones: GiantBones,
        val staff: BryophytasStaff,
        val chests: GiantChests,
    )

    /** alice and bob, one team, not yet in any lair. */
    private fun GameTestState.runGiantTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
        runInjectedGameTest(
            Deps::class,
            GiantModule,
            GiantLairScript::class,
            GiantCombatScript::class,
            LeagueKillTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 101L
            alice.observerUUID = 101L
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.uuid = 102L
            bob.observerUUID = 102L
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            topUp(alice, bob)
            body(deps, alice, bob)
        }

    private fun GameTestScope.topUp(vararg players: Player) {
        for (each in players) {
            each.stats[stats.hitpoints] = 99
        }
    }

    /** Advances (keeping the players alive) until [predicate] holds. */
    private fun GameTestScope.until(
        what: String,
        vararg players: Player,
        timeout: Int = 400,
        predicate: () -> Boolean,
    ) {
        var ticks = 0
        while (!predicate()) {
            check(ticks++ < timeout) { "never happened: $what" }
            topUp(*players)
            advance()
        }
    }

    private fun GameTestScope.oborRun(deps: Deps, alice: Player, bob: Player): OborRun {
        deps.obor.enter(alice)
        deps.obor.enter(bob)
        val run = checkNotNull(deps.obor.runOf(alice))
        until("Obor appears", alice, bob, timeout = 60) { run.boss != null }
        return run
    }

    private fun GameTestScope.bryophytaRun(deps: Deps, alice: Player, bob: Player): BryophytaRun {
        deps.bryophyta.enter(alice)
        deps.bryophyta.enter(bob)
        val run = checkNotNull(deps.bryophyta.runOf(alice))
        until("Bryophyta appears", alice, bob, timeout = 60) { run.boss != null }
        return run
    }

    private fun GiantRun.real(tile: CoordGrid): CoordGrid = instance.region.normal[tile]

    /** Messages only live for the tick they were sent in: advances until [text] has been sent. */
    private fun GameTestScope.untilMessage(text: String, player: Player, timeout: Int = 20) {
        repeat(timeout) {
            advance()
            if (runCatching { assertMessageSent(text, player) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text, player)
    }

    /** The test world has no map, so the lair's chest is placed where the copy would have it. */
    private fun GameTestScope.chestOf(run: GiantRun): BoundLocInfo =
        placeMapLoc(run.real(run.lair.chest), run.lair.chestClosed)

    /** The real x of the rocks into Obor's pit. */
    private fun OborRun.rocksX(): Int = real(CoordGrid(Obor.ROCKS_X, Obor.ROCKS_TOP_Z, 0)).x

    private fun table(vararg stacks: DropStack, chance: Double = 1.0): DropTable =
        DropTable(
            name = "test",
            always = stacks.toList(),
            main = emptyList(),
            tertiary = emptyList(),
        )

    private fun GameTestScope.wield(player: Player, name: String) {
        val type = objTypes.values.first { it.internalName == name }
        player.worn[Wearpos.RightHand.slot] = InvObj(type)
    }

    // The lairs ---------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`both lairs are copies of their real map with the chest and the exit in them`() =
        runGiantTest { deps, alice, bob ->
            val obor = oborRun(deps, alice, bob)
            assertEquals(GiantLairs.obor.type, obor.instance.type)
            assertEquals(obor.real(GiantLairs.obor.entry), alice.coords)
            assertEquals(obor, deps.obor.runOf(bob), "the team shares one lair")
            assertEquals(120, obor.boss!!.baseHitpointsLvl)
            assertEquals(obor.real(GiantLairs.obor.spawn), obor.boss!!.coords)
            deps.obor.leave(alice)
            deps.obor.leave(bob)
            advance(ticks = 2)

            val moss = bryophytaRun(deps, alice, bob)
            assertEquals(GiantLairs.bryophyta.type, moss.instance.type)
            assertEquals(115, moss.boss!!.baseHitpointsLvl)
            assertEquals(moss.real(GiantLairs.bryophyta.spawn), moss.boss!!.coords)
        }

    @Test
    fun GameTestState.`leaving sends the team out to the dungeon`() =
        runGiantTest { deps, alice, bob ->
            oborRun(deps, alice, bob)
            assertTrue(deps.obor.leave(alice))
            assertEquals(GiantLairs.obor.exit, alice.coords)
        }

    // Obor ---------------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`Obor leaves the safe zone alone and goes for a player who climbs into the pit`() =
        runGiantTest { deps, alice, bob ->
            val run = oborRun(deps, alice, bob)
            run.attackAt = 0
            advance(ticks = 15)
            assertTrue(run.attackAt <= mapClock.cycle, "nobody in the pit: he does not attack")
            assertEquals(99, alice.hitpoints)
            assertFalse(deps.obor.inPit(run, alice))

            deps.obor.climb(run, alice, run.rocksX())
            advance()
            assertTrue(deps.obor.inPit(run, alice), "alice slid into the pit")
            until("he attacks", alice, bob, timeout = 80) { run.attackAt > mapClock.cycle }

            deps.obor.climb(run, alice, run.rocksX())
            advance()
            assertFalse(deps.obor.inPit(run, alice), "and back out again")
        }

    @Test
    fun GameTestState.`only a player in the pit can hurt Obor`() =
        runGiantTest { deps, alice, bob ->
            val run = oborRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            run.attackAt = mapClock.cycle + 100_000
            val before = boss.hitpoints
            boss.queueHit(alice, 1, HitType.Melee, 30)
            advance(ticks = 3)
            assertEquals(before, boss.hitpoints, "a player in the safe zone does no damage")

            deps.obor.climb(run, alice, run.rocksX())
            advance()
            run.attackAt = mapClock.cycle + 100_000
            boss.queueHit(alice, 1, HitType.Melee, 30)
            advance(ticks = 3)
            assertEquals(before - 30, boss.hitpoints, "a player in the pit does")
        }

    @Test
    fun GameTestState.`Obor stamps from a distance and punches in reach`() =
        runGiantTest { deps, alice, bob ->
            val run = oborRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            assertTrue(deps.obor.inReach(boss, boss.coords.translate(-1, 0)))
            assertTrue(deps.obor.inReach(boss, boss.coords.translate(0, boss.size)))
            assertFalse(deps.obor.inReach(boss, boss.coords.translate(-1, -1)), "no diagonals")
            assertFalse(deps.obor.inReach(boss, boss.coords.translate(-2, 0)))
        }

    @Test
    fun GameTestState
        .`killing Obor leaves him dead until the chest is clicked, then he returns in 15 ticks`() =
        runGiantTest { deps, alice, bob ->
            val run = oborRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            deps.dropTables.replaceAll(
                mapOf(giant_npcs.obor.id to table(DropStack(giant_objs.giant_bones, 1, 1)))
            )
            deps.obor.climb(run, alice, run.rocksX())
            advance()
            boss.heroPoints(alice, 5)
            boss.hitpoints = 0
            boss.queueDeath()
            until("he is dead", alice, bob, timeout = 60) { run.dead }
            assertEquals(WaveState.WaitingForNextWave, run.state)
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, 472)
            }
            advance(ticks = 20)
            assertEquals(WaveState.WaitingForNextWave, run.state, "he waits for the chest")
            assertTrue(deps.instances.instanceOf(alice) != null, "nobody is sent out")

            // The first click, even with no key, brings him back.
            val chest = chestOf(run)
            alice.withProtectedAccess { with(deps.chests) { open(deps.obor, chest) } }
            assertMessageSent("You hear the ground rumble...", alice)
            assertMessageSent("You need a Giant key to open this chest.", alice)
            until("he is back", alice, bob, timeout = 60) {
                run.state == WaveState.InWave && !run.dead
            }
            assertEquals(120, run.boss!!.hitpoints)
        }

    @Test
    fun GameTestState.`the chest uses a key, drops the loot beside it and counts`() =
        runGiantTest { deps, alice, bob ->
            val run = oborRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            deps.dropTables.replaceAll(
                mapOf(
                    giant_npcs.obor.id to table(DropStack(giant_objs.giant_bones, 1, 1)),
                    giant_npcs.obor_chest.id to
                        DropTable(
                            name = "chest",
                            always = listOf(DropStack(giant_objs.giant_key, 2, 2)),
                        ),
                )
            )
            val chest = chestOf(run)
            alice.withProtectedAccess { with(deps.chests) { open(deps.obor, chest) } }
            assertMessageSent("You can't loot the chest whilst Obor is still attacking you!", alice)

            boss.heroPoints(alice, 5)
            boss.hitpoints = 0
            boss.queueDeath()
            until("he is dead", alice, bob, timeout = 60) { run.dead }
            advance(ticks = 8)

            alice.inv[0] = InvObj(giant_objs.giant_key, 1)
            alice.withProtectedAccess { with(deps.chests) { open(deps.obor, chest) } }
            untilMessage("The loot spills out as you open the chest.", alice)
            assertMessageSent("Your Obor chests opened count is: <col=ff0000>1</col>.", alice)
            assertEquals(0, alice.count(giant_objs.giant_key), "the key is used up")
            assertEquals(1, alice.vars[giant_varps.obor_chests])
            val loot = objRepo.findAll(run.real(GiantLairs.obor.loot)).toList()
            assertTrue(loot.any { it.type == giant_objs.giant_key.id && it.count == 2 }, "$loot")
            assertTrue(run.chestOpened)
        }

    // Bryophyta ----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`Bryophyta attacks at once and never moves`() =
        runGiantTest { deps, alice, bob ->
            val run = bryophytaRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            val home = boss.coords
            until("she attacks", alice, bob, timeout = 60) { run.attackAt > mapClock.cycle }
            advance(ticks = 30)
            assertEquals(home, boss.coords)
        }

    @Test
    fun GameTestState.`growthlings make Bryophyta immune until an axe prunes them`() =
        runGiantTest { deps, alice, bob ->
            val run = bryophytaRun(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            run.attackAt = mapClock.cycle + 100_000
            deps.bryophyta.summonGrowthlings(run, mapClock.cycle)
            val growthlings = run.monsters.toList()
            assertEquals(3, growthlings.size)
            assertEquals(3, deps.bryophyta.liveGrowthlings(run))
            for (growthling in growthlings) {
                assertTrue(run.instance.contains(growthling.coords))
                assertEquals(10, growthling.baseHitpointsLvl)
            }

            val before = boss.hitpoints
            boss.queueHit(alice, 1, HitType.Melee, 30)
            advance(ticks = 3)
            assertEquals(before, boss.hitpoints, "she takes no damage while they live")

            // Without an axe a growthling can be hurt but never killed.
            val first = growthlings[0]
            first.queueHit(alice, 1, HitType.Melee, 50)
            assertMessageSent("Cut the growthling down with an axe or secateurs.", alice)
            advance(ticks = 3)
            assertEquals(1, first.hitpoints)
            assertTrue(first.isSlotAssigned)

            // With one, a single hit kills it.
            wield(alice, "bronze_axe")
            for (growthling in growthlings) {
                growthling.queueHit(alice, 1, HitType.Melee, 1)
            }
            assertMessageSent("You prune the Growthling.", alice)
            until("they are pruned", alice, bob, timeout = 40) {
                deps.bryophyta.liveGrowthlings(run) == 0
            }

            boss.queueHit(alice, 1, HitType.Melee, 30)
            advance(ticks = 3)
            assertEquals(before - 30, boss.hitpoints, "she can be hurt again")
        }

    @Test
    fun GameTestState.`only axes and magic secateurs prune`() = runGiantTest { _, _, _ ->
        assertTrue(GiantAxes.isAxe("Bronze axe"))
        assertTrue(GiantAxes.isAxe("Dragon axe (or)"))
        assertFalse(GiantAxes.isAxe("Rune battleaxe"))
        assertFalse(GiantAxes.isAxe("Dragon pickaxe"))
        assertFalse(GiantAxes.isAxe("Zombie axe"))
        assertFalse(GiantAxes.isAxe("Blessed axe"))
        assertTrue(GiantAxes.cutsGrowthlings(giant_objs.secateurs_magic.id, "Magic secateurs"))
        assertFalse(GiantAxes.cutsGrowthlings(giant_objs.secateurs_magic.id + 1, "Dagger"))
    }

    // The gates ----------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`a gate is locked without a key and stays unlocked once a key has opened it`() =
        runGiantTest { deps, alice, _ ->
            val front = CoordGrid(3095, 9834, 0)
            allocZoneCollision(front)
            val gate = placeMapLoc(CoordGrid(3095, 9833, 0), giant_locs.obor_gate_north)
            alice.placeAt(front)
            alice.opLoc1(gate)
            untilMessage("The gate is locked shut.", alice)

            alice.inv[0] = InvObj(giant_objs.giant_key, 1)
            alice.opLoc1(gate)
            advance(ticks = 3)
            alice.resumePauseButton(components.chatmenu_pbutton, 1)
            untilMessage("Your key fits the gate, causing it to swing open.", alice)
            assertNotNull(deps.obor.runOf(alice), "alice is in Obor's lair")
            assertEquals(1, alice.count(giant_objs.giant_key), "the key is kept")
            assertEquals(
                GiantFlags.OBOR_UNLOCKED,
                alice.vars[giant_varps.flags] and GiantFlags.OBOR_UNLOCKED,
            )

            // Out again, and with no key the gate still opens.
            deps.obor.leave(alice)
            advance(ticks = 2)
            alice.inv[0] = null
            alice.placeAt(front)
            alice.opLoc1(gate)
            advance(ticks = 3)
            alice.resumePauseButton(components.chatmenu_pbutton, 1)
            advance(ticks = 3)
            assertNotNull(deps.obor.runOf(alice), "no key needed the second time")
        }

    // Giant bones, the logs and the staff -------------------------------------------------------

    @Test
    fun GameTestState.`giant bones are buried for 150 prayer experience and cannot be taken`() =
        runGiantTest { deps, alice, _ ->
            val at = CoordGrid(3100, 9840, 0)
            allocZoneCollision(at)
            alice.placeAt(at)
            val bones = objRepo.add(giant_objs.giant_bones, at, 200, alice)
            val before = alice.stats[stats.prayer]
            alice.withProtectedAccess { with(deps.bones) { take() } }
            assertMessageSent("I don't think those will fit in my backpack...", alice)
            assertEquals(0, alice.count(giant_objs.giant_bones))

            alice.withProtectedAccess { with(deps.bones) { bury(bones) } }
            advance(ticks = 2)
            alice.resumePauseButton(components.chatmenu_pbutton, 2)
            advance(ticks = 3)
            assertTrue(alice.stats[stats.prayer] > before, "the experience was given")
            assertEquals(0, objRepo.findAll(at).count { it.type == giant_objs.giant_bones.id })
            assertEquals(GiantFlags.BURY_WITHOUT_ASKING, alice.vars[giant_varps.flags])
        }

    @Test
    fun GameTestState.`Bryophyta's essence on a battlestaff makes the staff at 62 crafting`() =
        runGiantTest { deps, alice, _ ->
            alice.inv[0] = InvObj(giant_objs.bryophytas_essence, 1)
            alice.inv[1] = InvObj(giant_objs.battlestaff, 1)
            alice.stats[stats.crafting] = 61
            alice.withProtectedAccess { with(deps.staff) { make() } }
            advance(ticks = 2)
            assertEquals(1, alice.count(giant_objs.battlestaff), "61 is too low")

            alice.stats[stats.crafting] = 62
            alice.withProtectedAccess { with(deps.staff) { make() } }
            advance(ticks = 2)
            assertEquals(0, alice.count(giant_objs.battlestaff))
            assertEquals(0, alice.count(giant_objs.bryophytas_essence))
            assertEquals(1, alice.count(giant_objs.bryophytas_staff_uncharged))
        }
}
