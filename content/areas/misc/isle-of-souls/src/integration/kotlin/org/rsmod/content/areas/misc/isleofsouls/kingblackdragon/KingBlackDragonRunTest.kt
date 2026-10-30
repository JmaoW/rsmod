package org.rsmod.content.areas.misc.isleofsouls.kingblackdragon

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.dungeons.PlayerPoison
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.content.other.dragonfire.BreathEffect
import org.rsmod.content.other.dragonfire.DragonBreathLanded
import org.rsmod.content.other.dragonfire.DragonScript
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class KingBlackDragonRunTest {
    private object KbdModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(KingBlackDragon::class.java).`in`(Scopes.SINGLETON)
            bind(KbdScript::class.java).`in`(Scopes.SINGLETON)
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
        val kbd: KingBlackDragon,
        val script: KbdScript,
        val tracker: LeagueTaskTracker,
        val dropTables: DropTables,
        val collision: CollisionFlagMap,
        val eventBus: EventBus,
        val poison: PlayerPoison,
    )

    /** alice and bob, one team, in a lair the dragon has not yet appeared in. */
    private fun GameTestState.runKbdTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            KbdModule,
            KbdScript::class,
            DragonScript::class,
            LeagueKillTasks::class,
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
                deps.kbd.enter(alice)
                deps.kbd.enter(bob)
            }
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

    /** Waits for the dragon to appear, and returns the run. */
    private fun GameTestScope.runWithBoss(deps: Deps, alice: Player, bob: Player): KbdRun {
        val run = checkNotNull(deps.kbd.runOf(alice))
        until("the dragon appears", alice, bob, timeout = 60) { run.boss != null }
        // A npc added this tick is not processed until the next.
        advance(ticks = 2)
        return run
    }

    private fun KbdRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    @Test
    fun GameTestState.`entering puts the team in the lair and the dragon appears at its spot`() =
        runKbdTest { deps, alice, bob ->
            val run = checkNotNull(deps.kbd.runOf(alice))
            assertEquals(KbdInstance.type, run.instance.type)
            assertEquals(run.real(KbdArena.start), alice.coords)
            assertEquals(run, deps.kbd.runOf(bob))

            runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            // It has already started towards the players, so only a few tiles from its spot.
            val spot = run.real(KbdArena.bossSpot)
            assertTrue(boss.coords.chebyshevDistance(spot) <= 4, "${boss.coords} is far from $spot")
            assertEquals(kbd_npcs.boss.id, boss.type.id)
            assertEquals(WaveState.InWave, run.state)
        }

    @Test
    fun GameTestState.`the dragon has the wiki's size and hitpoints from the cache`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            assertEquals(KbdArena.SIZE, npcTypes[kbd_npcs.boss].size)
            assertEquals(240, boss.baseHitpointsLvl)
        }

    @Test
    fun GameTestState.`the start and the dragon's spot are open floor`() =
        runKbdTest { deps, alice, _ ->
            val run = checkNotNull(deps.kbd.runOf(alice))
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
            open(KbdArena.start, 1, "the start")
            open(KbdArena.bossSpot, KbdArena.SIZE, "the dragon's spot")
        }

    @Test
    fun GameTestState.`two teams get lairs of their own`() = runKbdTest { deps, alice, bob ->
        val carol = registerPlayer(player = Player().apply { username = "carol" })
        carol.uuid = 203L
        carol.observerUUID = 203L
        deps.teams.createTeam(2, "Beta")
        deps.teams.assign("carol", 2)
        deps.kbd.enter(carol)
        val first = checkNotNull(deps.kbd.runOf(alice))
        val second = checkNotNull(deps.kbd.runOf(carol))
        assertTrue(first !== second)
        assertTrue(first.instance !== second.instance)
        assertEquals(first, deps.kbd.runOf(bob))
    }

    @Test
    fun GameTestState.`the lever inside leaves the lair for the lever in the cave`() =
        runKbdTest { deps, alice, _ ->
            assertTrue(deps.kbd.leave(alice))
            assertEquals(KbdArena.exit, alice.coords)
        }

    // The levers ------------------------------------------------------------------------------

    private fun GameTestScope.locAt(coords: CoordGrid): LocInfo? =
        findLocs(coords).firstOrNull { it.coords == coords && it.id == kbd_locs.entrance.id }

    @Test
    fun GameTestState
        .`the lever hangs on a wall beside the cave's arrival and pulling it enters`() =
        runKbdTest(enter = false) { deps, alice, _ ->
            allocZoneCollision(KbdLever.coords)
            deps.script.placeLever()
            val info = checkNotNull(locAt(KbdLever.coords)) { "the lever is placed" }

            // It hangs over a floor tile, with a wall along its south edge, and the tile north of
            // it is free for players to land on.
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            val flags = deps.collision[KbdLever.coords.x, KbdLever.coords.z, KbdLever.coords.level]
            assertEquals(0, flags and blocked, "the lever's tile can be stood on")
            val front = KbdLever.front
            assertEquals(0, deps.collision[front.x, front.z, front.level] and blocked)

            alice.placeAt(KbdLever.front)
            alice.opLoc1(BoundLocInfo(info, locTypes[kbd_locs.entrance]))
            advance(ticks = 8)
            val run = checkNotNull(deps.kbd.runOf(alice)) { "alice is in a lair" }
            assertEquals(run.real(KbdArena.start), alice.coords)
        }

    // The fight -------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the dragon goes after a player and hurts them with breath and bites`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            alice.telejump(boss.coords.translate(KbdArena.SIZE + 3, 2))
            bob.telejump(run.real(KbdArena.start))
            val taken = ArrayList<Int>()
            repeat(400) {
                topUp(alice, bob)
                advance()
                val lost = 99 - alice.hitpoints
                if (lost > 0) taken += lost
            }
            assertTrue(taken.size > 10, "it attacks: $taken")
            assertTrue(taken.all { it <= 65 }, "nothing tops 65: $taken")
            assertTrue(taken.any { it > 25 }, "some breath tops a bite: $taken")
        }

    @Test
    fun GameTestState.`its special breaths carry poison, a freeze or a drain now and then`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            alice.telejump(boss.coords.translate(KbdArena.SIZE + 3, 2))
            bob.telejump(run.real(KbdArena.start))
            alice.stats[stats.attack] = 60
            var poisoned = false
            var frozen = false
            var drained = false
            repeat(1_500) {
                topUp(alice, bob)
                advance()
                poisoned = poisoned || deps.poison.isPoisoned(alice)
                frozen = frozen || alice.isFrozen
                drained = drained || alice.stats[stats.attack] < 60
                if (poisoned && frozen && drained) {
                    return@repeat
                }
            }
            assertTrue(poisoned, "poisoned")
            assertTrue(frozen, "frozen")
            assertTrue(drained, "drained")
        }

    @Test
    fun GameTestState.`each breath effect does what the wiki says`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            alice.stats[stats.attack] = 60
            alice.stats[stats.mining] = 40

            deps.eventBus.publish(DragonBreathLanded(alice, boss, BreathEffect.Poison, 20))
            assertTrue(deps.poison.isPoisoned(alice))
            assertEquals(KbdScript.POISON_DAMAGE, deps.poison.damageOf(alice))
            assertEquals(8, deps.poison.damageOf(alice))

            assertFalse(alice.isFrozen)
            deps.eventBus.publish(DragonBreathLanded(alice, boss, BreathEffect.Freeze, 20))
            assertTrue(alice.isFrozen)
            advance(ticks = 11)
            assertFalse(alice.isFrozen, "the freeze lasts 10 ticks")

            val hitpoints = alice.stats[stats.hitpoints]
            deps.eventBus.publish(DragonBreathLanded(alice, boss, BreathEffect.Shock, 20))
            assertEquals(58, alice.stat(stats.attack))
            assertEquals(38, alice.stat(stats.mining))
            assertEquals(hitpoints, alice.stat(stats.hitpoints), "hitpoints are not drained")
        }

    // Death -----------------------------------------------------------------------------------

    @Test
    fun GameTestState
        .`killing the dragon counts the task for the team and drops loot at its middle`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val boss = checkNotNull(run.boss)
            val table = DropTable("test", always = listOf(DropStack(objTypes[objs.bones], 1, 1)))
            deps.dropTables.replaceAll(mapOf(kbd_npcs.boss.id to table))
            assertFalse(deps.tracker.isComplete(alice, KILL))

            boss.heroPoints(alice, 5)
            val centre = boss.coords.translate(2, 2)
            boss.hitpoints = 0
            boss.queueDeath()
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertTrue(deps.tracker.isComplete(bob, KILL), "the team's kill counts for bob too")

            assertEquals(1, objRepo.findAll(centre).toList().size, "the loot lands at its middle")
            // Nobody is sent out: it waits to come back.
            assertEquals(WaveState.WaitingForNextWave, run.state)
            assertNotNull(deps.instances.instanceOf(alice), "alice is still in the lair")
        }

    @Test
    fun GameTestState.`after a kill the next dragon appears in the same lair`() =
        runKbdTest { deps, alice, bob ->
            val run = runWithBoss(deps, alice, bob)
            val dead = checkNotNull(run.boss)
            dead.hitpoints = 0
            dead.queueDeath()
            until("it is back", alice, bob, timeout = 300) {
                run.state == WaveState.InWave && run.boss !== dead && run.boss != null
            }
            val back = checkNotNull(run.boss)
            assertTrue(back.isSlotAssigned, "a new dragon is in the lair")
            assertTrue(run.instance.contains(back.coords))
            assertEquals(240, back.baseHitpointsLvl)
        }

    private companion object {
        /** The league task "Defeat the King Black Dragon". */
        const val KILL = 1000
    }
}
