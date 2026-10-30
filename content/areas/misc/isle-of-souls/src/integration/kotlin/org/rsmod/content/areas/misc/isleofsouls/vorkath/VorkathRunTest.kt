package org.rsmod.content.areas.misc.isleofsouls.vorkath

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.drops.NpcDropEvents
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueVorkathTasks
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.skills.prayer.scripts.Burying
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionNpc
import org.rsmod.game.inv.InvObj
import org.rsmod.game.inv.isType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class VorkathRunTest {
    private object VorkathModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Vorkath::class.java).`in`(Scopes.SINGLETON)
            bind(VorkathScript::class.java).`in`(Scopes.SINGLETON)
            bind(VorkathRewards::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that these scripts load.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val vorkath: Vorkath,
        val script: VorkathScript,
        val rewards: VorkathRewards,
        val attacks: VorkathAttacks,
        val tracker: LeagueTaskTracker,
        val dropTables: DropTables,
        val collision: CollisionFlagMap,
        val eventBus: EventBus,
        val launcher: ProtectedAccessLauncher,
    )

    /** alice and bob, one team, in a crater with Vorkath asleep. */
    private fun GameTestState.runVorkathTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            VorkathModule,
            VorkathScript::class,
            VorkathRewards::class,
            LeagueKillTasks::class,
            LeagueVorkathTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 201L
            alice.observerUUID = 201L
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.uuid = 202L
            bob.observerUUID = 202L
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            topUp(alice, bob)
            if (enter) {
                deps.vorkath.enter(alice)
                deps.vorkath.enter(bob)
                val run = checkNotNull(deps.vorkath.runOf(alice))
                until("Vorkath lies in the crater", alice, bob, timeout = 30) {
                    run.sleeper != null
                }
                advance(ticks = 2)
            }
            body(deps, alice, bob)
        }

    private fun GameTestScope.topUp(vararg players: Player) {
        for (each in players) {
            each.stats[stats.hitpoints] = 99
        }
    }

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

    private fun VorkathRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    /** Pokes the team's Vorkath and waits for it to be up and fighting. */
    private fun GameTestScope.awake(deps: Deps, alice: Player, bob: Player): VorkathRun {
        val run = checkNotNull(deps.vorkath.runOf(alice))
        assertTrue(deps.vorkath.poke(alice, checkNotNull(run.sleeper)))
        until("Vorkath is up", alice, bob, timeout = 30) { run.phase == VorkathPhase.Fighting }
        advance()
        return run
    }

    /** Makes the next attack, this tick, the [special]. */
    private fun GameTestScope.forceSpecial(run: VorkathRun, special: VorkathSpecial) {
        run.nextSpecial = special
        run.attacks = Vorkath.STANDARDS_BEFORE_SPECIAL
        run.nextAttackAt = mapClock.cycle
    }

    // The crater -------------------------------------------------------------------------------

    @Test
    fun GameTestState.`entering puts the team in the crater with Vorkath asleep at its spot`() =
        runVorkathTest { deps, alice, bob ->
            val run = checkNotNull(deps.vorkath.runOf(alice))
            assertEquals(VorkathInstance.type, run.instance.type)
            assertEquals(run.real(VorkathArena.start), alice.coords)
            assertEquals(run, deps.vorkath.runOf(bob))
            val sleeper = checkNotNull(run.sleeper)
            assertEquals(vorkath_npcs.sleeping.id, sleeper.type.id)
            assertEquals(run.real(VorkathArena.bossSpot), sleeper.coords)
            assertEquals(VorkathPhase.Asleep, run.phase)
            assertEquals(VorkathArena.SIZE, npcTypes[vorkath_npcs.boss].size)
        }

    @Test
    fun GameTestState.`the start and the tiles round Vorkath are open floor`() =
        runVorkathTest(enter = false) { deps, alice, _ ->
            deps.vorkath.enter(alice)
            val run = checkNotNull(deps.vorkath.runOf(alice))
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            fun open(spot: CoordGrid, size: Int, what: String) {
                val real = run.real(spot)
                for (dz in 0 until size) {
                    for (dx in 0 until size) {
                        val flags = deps.collision[real.x + dx, real.z + dz, real.level]
                        assertEquals(0, flags and blocked, "$what is blocked at +$dx,+$dz")
                    }
                }
            }
            open(VorkathArena.start, 1, "the start")
            // Vorkath lies on blocked ground (it never moves); the tiles round it must be open.
            for ((dx, dz) in Vorkath.LOOT_OFFSETS) {
                open(VorkathArena.bossSpot.translate(dx, dz), 1, "the loot tile $dx,$dz")
            }
        }

    @Test
    fun GameTestState.`the boat takes the team in and the ice chunks bring them back`() =
        runVorkathTest(enter = false) { deps, alice, _ ->
            allocZoneCollision(VorkathBoat.coords)
            deps.script.placeBoat()
            // The test map has collision but no map locs: put the real ice chunks in the source
            // crater, so the team's copy has them as the game's does.
            placeMapLoc(
                ICE_CHUNKS,
                vorkath_locs.ice_chunks,
                LocShape.CentrepieceStraight,
                LocAngle.West,
            )
            val boat =
                checkNotNull(
                    findLocs(VorkathBoat.coords).firstOrNull { it.id == vorkath_locs.boat.id }
                ) {
                    "no boat: " + findLocs(VorkathBoat.coords).map { it.id to it.coords }.toList()
                }
            alice.placeAt(VorkathBoat.front)
            alice.opLoc1(BoundLocInfo(boat, locTypes[vorkath_locs.boat]))
            advance(ticks = 8)
            val run = checkNotNull(deps.vorkath.runOf(alice)) { "alice is in a crater" }
            assertEquals(run.real(VorkathArena.start), alice.coords)

            val chunksAt = run.real(ICE_CHUNKS)
            val chunks =
                checkNotNull(
                    findLocs(chunksAt).firstOrNull { it.id == vorkath_locs.ice_chunks.id }
                ) {
                    "no ice chunks: " + findLocs(chunksAt).map { it.id to it.coords }.toList()
                }
            alice.opLoc1(BoundLocInfo(chunks, locTypes[vorkath_locs.ice_chunks]))
            advance(ticks = 6)
            assertNull(deps.instances.instanceOf(alice), "alice has left")
            assertEquals(VorkathArena.exit, alice.coords)
        }

    // Waking and the standard attacks ----------------------------------------------------------

    @Test
    fun GameTestState.`a poke wakes it, and after its rise it is up with 750 hitpoints`() =
        runVorkathTest { deps, alice, bob ->
            val run = checkNotNull(deps.vorkath.runOf(alice))
            val sleeper = checkNotNull(run.sleeper)
            alice.opNpc1(sleeper)
            until("the poke lands", alice, bob, timeout = 30) { run.phase == VorkathPhase.Waking }
            assertEquals(vorkath_npcs.rising.id, sleeper.visType.id)
            assertFalse(deps.vorkath.poke(bob, sleeper), "a second poke does nothing")
            until("it is up", alice, bob, timeout = 20) { run.phase == VorkathPhase.Fighting }
            val boss = checkNotNull(run.boss)
            assertEquals(vorkath_npcs.boss.id, boss.type.id)
            assertEquals(Vorkath.HITPOINTS, boss.hitpoints)
            assertNull(run.sleeper)
            assertEquals(alice, run.target)
        }

    @Test
    fun GameTestState.`it attacks its target from where it lies and never moves`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            val spot = boss.coords
            // No specials in this test.
            run.attacks = Int.MIN_VALUE / 2
            val taken = ArrayList<Int>()
            repeat(200) {
                topUp(alice, bob)
                advance()
                run.fireballs.clear()
                val lost = 99 - alice.hitpoints
                if (lost > 0) taken += lost
            }
            assertEquals(spot, boss.coords, "it never moved")
            assertTrue(taken.size > 5, "it attacks: $taken")
            assertTrue(taken.all { it <= 80 }, "no standard attack tops 80: $taken")
        }

    @Test
    fun GameTestState.`its melee is only used next to it`() = runVorkathTest { deps, _, _ ->
        val far = (1..500).map { deps.attacks.choose(adjacent = false) }
        assertFalse(VorkathAttack.Melee in far)
        assertTrue(VorkathAttack.Fireball in far)
        val near = (1..500).map { deps.attacks.choose(adjacent = true) }
        assertTrue(VorkathAttack.Melee in near)
    }

    @Test
    fun GameTestState.`the fireball hits its tile in full and the tiles next to it by half`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            run.attacks = Int.MIN_VALUE / 2
            run.nextAttackAt = Int.MAX_VALUE
            val tile = run.real(VorkathArena.start).translate(0, 2)
            alice.telejump(tile)
            bob.telejump(tile.translate(1, 0))
            advance()
            topUp(alice, bob)
            run.fireballs += VorkathLanding(tile, mapClock.cycle, roll = 60)
            advance(ticks = 3)
            assertEquals(39, alice.hitpoints)
            assertEquals(69, bob.hitpoints)
        }

    // The specials -----------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`the acid special lays pools, hurts a player who stands still and heals it`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            bob.telejump(run.real(VorkathArena.start).translate(8, 0))
            boss.hitpoints = 500
            forceSpecial(run, VorkathSpecial.Acid)
            advance()
            assertEquals(VorkathPhase.Acid, run.phase)
            assertEquals(20, deps.vorkath.incomingDamage(boss, 40), "damage on it is halved")
            until("the pools land", alice, bob, timeout = 10) { run.pools.isNotEmpty() }
            assertTrue(alice.coords in run.pools, "one lands where alice stands")
            assertTrue(run.pools.none { Vorkath.underBoss(boss, it) }, "none under Vorkath")
            assertTrue(run.pools.size > 40, "pools all over: ${run.pools.size}")
            assertTrue(locExists(alice.coords, vorkath_locs.acid), "the pool is on the floor")

            var lost = 0
            repeat(10) {
                alice.stats[stats.hitpoints] = 99
                advance()
                lost += 99 - alice.hitpoints
            }
            assertTrue(lost > 25, "standing in acid under rapid fire hurts: $lost")
            assertTrue(boss.hitpoints > 500, "the acid heals it: ${boss.hitpoints}")
            assertTrue(checkNotNull(run.visits["alice"]).failed, "alice's clean streak is over")
            assertTrue(run.visits["bob"]?.failed != true, "bob was never hurt")

            until("the special ends", alice, bob, timeout = 40) {
                run.phase == VorkathPhase.Fighting
            }
            assertTrue(run.pools.isEmpty())
            assertEquals(40, deps.vorkath.incomingDamage(boss, 40))
        }

    @Test
    fun GameTestState.`the spawn special freezes its target and killing the spawn frees them`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            forceSpecial(run, VorkathSpecial.Spawn)
            advance()
            assertEquals(VorkathPhase.Spawn, run.phase)
            assertEquals(0, deps.vorkath.incomingDamage(boss, 40), "it is immune")
            until("the spawn lands", alice, bob, timeout = 20) { run.spawn != null }
            assertTrue(alice.isFrozen, "alice is frozen")
            val spawn = checkNotNull(run.spawn)
            assertEquals(vorkath_npcs.spawn.id, spawn.type.id)
            assertEquals(Vorkath.SPAWN_HITPOINTS, spawn.hitpoints)
            val distance = spawn.coords.chebyshevDistance(alice.coords)
            assertTrue(distance in 6..10, "it lands about 8 tiles away: $distance")

            spawn.hitpoints = 0
            spawn.queueDeath()
            until("the spawn dies", alice, bob, timeout = 20) { run.phase == VorkathPhase.Fighting }
            assertFalse(alice.isFrozen, "alice is free")
            assertNull(run.spawn)
            assertEquals(40, deps.vorkath.incomingDamage(boss, 40))
            assertTrue(run.visits["alice"]?.failed != true)
        }

    @Test
    fun GameTestState.`being frozen drops the attack on Vorkath, but not a teammate's`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            alice.opNpc2(boss)
            bob.opNpc2(boss)
            advance()
            assertEquals(boss, (alice.interaction as? InteractionNpc)?.target, "alice attacks")
            forceSpecial(run, VorkathSpecial.Spawn)
            until("alice is frozen", alice, bob, timeout = 20) { alice.isFrozen }
            assertNull(alice.interaction, "alice's attack is dropped")
            assertEquals(boss, (bob.interaction as? InteractionNpc)?.target, "bob keeps his")
        }

    @Test
    fun GameTestState.`a spawn that reaches its target explodes for up to 60`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            forceSpecial(run, VorkathSpecial.Spawn)
            until("the spawn lands", alice, bob, timeout = 20) { run.spawn != null }
            run.fireballs.clear()
            val spawn = checkNotNull(run.spawn)
            topUp(alice, bob)
            spawn.telejump(alice.coords.translate(1, 0))
            var ticks = 0
            while (run.phase == VorkathPhase.Spawn) {
                check(ticks++ < 10) { "the spawn never exploded" }
                advance()
            }
            advance(ticks = 2)
            assertEquals(99 - 60, alice.hitpoints, "a full spawn explodes for 60")
            assertFalse(alice.isFrozen)
            assertTrue(checkNotNull(run.visits["alice"]).failed)
            assertFalse(spawn.isSlotAssigned, "the spawn is gone")
        }

    @Test
    fun GameTestState.`the specials take turns`() = runVorkathTest { deps, alice, bob ->
        val run = awake(deps, alice, bob)
        run.nextSpecial = VorkathSpecial.Acid
        forceSpecial(run, run.nextSpecial)
        advance()
        assertEquals(VorkathPhase.Acid, run.phase)
        assertEquals(VorkathSpecial.Spawn, run.nextSpecial)
    }

    // Its death, the drops and the rewards -----------------------------------------------------

    private fun GameTestScope.kill(deps: Deps, run: VorkathRun, hero: Player): Npc {
        val boss = checkNotNull(run.boss)
        boss.heroPoints(hero, 5)
        boss.hitpoints = 0
        boss.queueDeath()
        return boss
    }

    @Test
    fun GameTestState.`it dies, its drops land beside it near the hero, and it lies down again`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val table = DropTable("test", always = listOf(DropStack(objTypes[objs.bones], 1, 1)))
            deps.dropTables.replaceAll(mapOf(vorkath_npcs.boss.id to table))
            val boss = checkNotNull(run.boss)
            val expected = deps.vorkath.lootTile(boss, alice)
            assertFalse(Vorkath.underBoss(boss, expected))
            kill(deps, run, alice)
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertEquals(1, objRepo.findAll(expected).toList().size, "the loot lands beside it")
            assertEquals(1, checkNotNull(run.visits["alice"]).kills)
            assertEquals(1, checkNotNull(run.visits["bob"]).kills, "bob was there for it too")
            until("it lies down again", alice, bob, timeout = 30) {
                run.phase == VorkathPhase.Asleep && run.sleeper != null
            }
            assertNotNull(deps.instances.instanceOf(alice), "nobody is sent out")
        }

    @Test
    fun GameTestState.`the head is guaranteed on the 10th kill and every 50th`() =
        runVorkathTest { deps, alice, bob ->
            val heads = (1..300).filter { VorkathRewards.isHeadKill(it) }
            assertEquals(listOf(10, 50, 100, 150, 200, 250, 300), heads)

            val run = awake(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            VarPlayerIntMapSetter.set(alice, VorkathVarps.kills, 10)
            val modify = NpcDropEvents.Modify(boss, alice)
            deps.eventBus.publish(modify)
            assertEquals(listOf(vorkath_objs.head.id), modify.guaranteed.map { it.obj.id })

            VarPlayerIntMapSetter.set(alice, VorkathVarps.kills, 11)
            val other = NpcDropEvents.Modify(boss, alice)
            deps.eventBus.publish(other)
            assertTrue(other.guaranteed.isEmpty())
        }

    @Test
    fun GameTestState.`a guaranteed head is not doubled when the table gives one too`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            val head = objTypes[vorkath_objs.head]
            val table = DropTable("test", always = listOf(DropStack(head, 1, 1)))
            deps.dropTables.replaceAll(mapOf(vorkath_npcs.boss.id to table))
            VarPlayerIntMapSetter.set(alice, VorkathVarps.kills, 50)
            val boss = checkNotNull(run.boss)
            val at = deps.vorkath.lootTile(boss, alice)
            kill(deps, run, alice)
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            val heads = objRepo.findAll(at).filter { it.type == head.id }.toList()
            assertEquals(1, heads.size, "one head, not two")
        }

    @Test
    fun GameTestState.`fifteen kills without leaving complete their task`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            run.visitOf(alice).kills = 14
            run.visitOf(alice).failed = true
            kill(deps, run, alice)
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertTrue(deps.tracker.isComplete(alice, LeagueVorkathTasks.TASK_WITHOUT_LEAVING))
            assertFalse(
                deps.tracker.isComplete(alice, LeagueVorkathTasks.TASK_WITHOUT_SPECIAL_DAMAGE),
                "hurt by a special, alice cannot finish the clean streak",
            )
        }

    @Test
    fun GameTestState.`five clean kills in a row complete the special damage task`() =
        runVorkathTest { deps, alice, bob ->
            val run = awake(deps, alice, bob)
            run.visitOf(alice).streak = 4
            kill(deps, run, alice)
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertTrue(
                deps.tracker.isComplete(alice, LeagueVorkathTasks.TASK_WITHOUT_SPECIAL_DAMAGE)
            )
            assertFalse(deps.tracker.isComplete(alice, LeagueVorkathTasks.TASK_WITHOUT_LEAVING))
        }

    @Test
    fun GameTestState.`leaving and coming back starts a new visit`() =
        runVorkathTest { deps, alice, _ ->
            val run = checkNotNull(deps.vorkath.runOf(alice))
            run.visitOf(alice).kills = 9
            run.visitOf(alice).failed = true
            deps.vorkath.leave(alice)
            deps.vorkath.enter(alice)
            val visit = checkNotNull(deps.vorkath.runOf(alice)).visitOf(alice)
            assertEquals(0, visit.kills)
            assertFalse(visit.failed)
        }

    @Test
    fun GameTestState.`the head, the accumulator and 75 mithril arrows make Ava's assembler`() =
        runVorkathTest(enter = false) { deps, alice, _ ->
            alice.inv.objs[0] = InvObj(vorkath_objs.head)
            alice.inv.objs[1] = InvObj(vorkath_objs.accumulator)
            alice.inv.objs[2] = InvObj(vorkath_objs.mithril_arrow, 74)
            with(deps.rewards) { deps.launcher.launch(alice) { assemble() } }
            advance()
            assertContains(alice.inv, vorkath_objs.head)
            assertDoesNotContain(alice.inv, vorkath_objs.assembler)

            alice.inv.objs[2] = InvObj(vorkath_objs.mithril_arrow, 80)
            with(deps.rewards) { deps.launcher.launch(alice) { assemble() } }
            advance()
            assertContains(alice.inv, vorkath_objs.assembler)
            assertDoesNotContain(alice.inv, vorkath_objs.head)
            assertDoesNotContain(alice.inv, vorkath_objs.accumulator)
            val arrows =
                alice.inv.objs.filterNotNull().filter { it.isType(vorkath_objs.mithril_arrow) }
            assertEquals(5, arrows.sumOf { it.count })
        }

    @Test
    fun GameTestState.`the dragonbone necklace restores prayer as bones are buried`() =
        runVorkathTest(enter = false) { deps, alice, _ ->
            alice.stats[stats.prayer] = 99
            alice.stats.setCurrentLevel(stats.prayer, 10)
            val superior = firstObjType { it.internalName == "dragon_bones_superior" }
            deps.eventBus.publish(Burying.BoneBuried(alice, superior))
            assertEquals(10, alice.stat(stats.prayer), "no necklace, no prayer")

            alice.worn[Wearpos.Front.slot] = InvObj(vorkath_objs.dragonbone_necklace)
            deps.eventBus.publish(Burying.BoneBuried(alice, superior))
            assertEquals(15, alice.stat(stats.prayer), "superior dragon bones restore 5")
            deps.eventBus.publish(Burying.BoneBuried(alice, objTypes[objs.bones]))
            assertEquals(16, alice.stat(stats.prayer), "plain bones restore 1")
        }

    private companion object {
        const val KILL = 585
        val ICE_CHUNKS = CoordGrid(2272, 4053, 0)
    }
}
