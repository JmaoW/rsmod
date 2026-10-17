package org.rsmod.content.areas.misc.isleofsouls.kalphite

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttackStyle
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.drops.DropStack
import org.rsmod.api.drops.DropTable
import org.rsmod.api.drops.DropTables
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.isValidTarget
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.dungeons.Dungeon
import org.rsmod.content.areas.misc.isleofsouls.dungeons.DungeonEntered
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class KalphiteQueenRunTest {
    private object KalphiteModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(KalphiteQueen::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val queen: KalphiteQueen,
        val tracker: LeagueTaskTracker,
        val dropTables: DropTables,
        val collision: CollisionFlagMap,
        val eventBus: EventBus,
    )

    /** alice, bob and carol, one team, in a chamber the queen has not yet spawned in. */
    private fun GameTestState.runKalphiteTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            KalphiteModule,
            KalphiteScript::class,
            KalphiteCombatScript::class,
            LeagueKillTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            val carol = registerPlayer(player = Player().apply { username = "carol" })
            deps.teams.createTeam(1, "Alpha")
            for (name in listOf("alice", "bob", "carol")) {
                deps.teams.assign(name, 1)
            }
            topUp(alice, bob, carol)
            if (enter) {
                deps.queen.enter(alice)
                deps.queen.enter(bob)
                deps.queen.enter(carol)
            }
            body(deps, alice, bob, carol)
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

    /** Waits for the queen to appear, and returns the run. */
    private fun GameTestScope.fightOf(deps: Deps, alice: Player, bob: Player): KalphiteRun {
        val run = checkNotNull(deps.queen.runOf(alice))
        until("the queen appears", alice, bob, timeout = 60) { run.fight != null }
        // A npc added this tick is not processed until the next.
        advance(ticks = 2)
        return run
    }

    /** Stops her from acting at all, so a test can do the acting. */
    private fun KalphiteFight.freeze() {
        step = KalphiteStep.Molting
    }

    private fun KalphiteRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    @Test
    fun GameTestState.`entering puts the team in the chamber and the queen waits in the west`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = checkNotNull(deps.queen.runOf(alice))
            assertEquals(KalphiteInstance.type, run.instance.type)
            assertEquals(run.real(KalphiteArena.start), alice.coords)
            assertEquals(run, deps.queen.runOf(bob))
            assertEquals(run, deps.queen.runOf(carol))

            val fight = fightOf(deps, alice, bob).fight!!
            assertEquals(run.real(KalphiteArena.queenSpot), fight.boss.coords)
            assertEquals(255, fight.boss.hitpoints)
            assertEquals(kalphite_npcs.queen.id, fight.boss.type.id)
            assertEquals(WaveState.InWave, run.state)
            assertFalse(deps.queen.isAirborne(fight.boss))
        }

    @Test
    fun GameTestState.`the start, her spot and every cocoon spawn are open floor`() =
        runKalphiteTest { deps, alice, _, _ ->
            val run = checkNotNull(deps.queen.runOf(alice))
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
            open(KalphiteArena.start, 1, "the start")
            open(KalphiteArena.queenSpot, KalphiteArena.SIZE, "her spot")
            for (cocoon in KalphiteArena.cocoons) {
                open(cocoon.spawn, 2, "the worker at ${cocoon.loc}")
            }
            assertEquals(13, KalphiteArena.cocoons.size)
        }

    @Test
    fun GameTestState.`the rope up leaves the chamber`() = runKalphiteTest { deps, alice, _, _ ->
        assertTrue(deps.queen.leave(alice))
        assertEquals(KalphiteArena.exit, alice.coords)
    }

    @Test
    fun GameTestState.`she stays where she is until a player comes close`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val spot = fight.boss.coords
            // Everyone is at the east end, far from her.
            advance(ticks = 40)
            assertFalse(fight.engaged)
            assertEquals(spot, fight.boss.coords)
        }

    @Test
    fun GameTestState.`she walks up to a player who comes close and stands beside them`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.attackAt = mapClock.cycle + 100_000
            val boss = fight.boss
            alice.telejump(boss.coords.translate(KalphiteArena.SIZE + 6, 2))
            bob.telejump(run.real(KalphiteArena.start))
            carol.telejump(run.real(KalphiteArena.start))
            until("she is beside alice", alice, bob, carol, timeout = 60) {
                boss.distanceTo(alice) == KalphiteQueen.ADJACENT
            }
            assertTrue(fight.engaged)
            assertEquals(alice, fight.target)
        }

    @Test
    fun GameTestState.`a player under her makes her step off`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.attackAt = mapClock.cycle + 100_000
            val boss = fight.boss
            alice.telejump(boss.coords.translate(2, 2))
            bob.telejump(run.real(KalphiteArena.start))
            carol.telejump(run.real(KalphiteArena.start))
            assertEquals(0, boss.distanceTo(alice))
            until("she steps off", alice, bob, carol, timeout = 20) {
                boss.distanceTo(alice) == KalphiteQueen.ADJACENT
            }
        }

    @Test
    fun GameTestState.`hurting her from afar brings her after the attacker`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            assertFalse(fight.engaged)
            deps.queen.hurt(fight.boss, alice)
            assertTrue(fight.engaged)
            assertEquals(alice, fight.target)
        }

    @Test
    fun GameTestState
        .`next to a player she melees one turn in two, and shoots from further away`() =
        runKalphiteTest { deps, _, _, _ ->
            val adjacent = HashMap<NpcAttackStyle, Int>()
            val far = HashMap<NpcAttackStyle, Int>()
            val trials = 8_000
            repeat(trials) {
                adjacent.merge(deps.queen.pickStyle(adjacent = true), 1, Int::plus)
                far.merge(deps.queen.pickStyle(adjacent = false), 1, Int::plus)
            }
            fun share(map: Map<NpcAttackStyle, Int>, style: NpcAttackStyle) =
                (map[style] ?: 0) * 100.0 / trials
            assertEquals(50.0, share(adjacent, NpcAttackStyle.Melee), 4.0)
            assertEquals(25.0, share(adjacent, NpcAttackStyle.Ranged), 4.0)
            assertEquals(25.0, share(adjacent, NpcAttackStyle.Magic), 4.0)
            assertEquals(0, far[NpcAttackStyle.Melee] ?: 0, "no melee from a distance")
            assertEquals(50.0, share(far, NpcAttackStyle.Ranged), 4.0)
        }

    @Test
    fun GameTestState.`beside a player she hurts them for no more than thirty-one`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val boss = fight.boss
            alice.telejump(boss.coords.translate(KalphiteArena.SIZE, 2))
            bob.telejump(run.real(KalphiteArena.start))
            carol.telejump(run.real(KalphiteArena.start))
            val taken = ArrayList<Int>()
            repeat(300) {
                topUp(alice, bob, carol)
                advance()
                val lost = 99 - alice.hitpoints
                if (lost > 0) taken += lost
            }
            assertTrue(taken.size > 10, "she hits her target: $taken")
            assertTrue(taken.all { it <= KalphiteQueen.MAX_HIT }, "no more than 31: $taken")
            assertTrue(taken.any { it > 15 }, "her hits are big: $taken")
        }

    @Test
    fun GameTestState.`the spine hurts those beside its target and takes a prayer point`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.freeze()
            val at = run.real(CoordGrid(3490, 9490, 0))
            alice.telejump(at)
            bob.telejump(at.translate(1, 0))
            carol.telejump(at.translate(9, 0))
            val hurt = HashSet<Player>()
            repeat(60) {
                topUp(alice, bob, carol)
                deps.queen.ranged(run, fight, alice)
                advance(ticks = 6)
                for (each in listOf(alice, bob, carol)) {
                    val lost = 99 - each.hitpoints
                    assertTrue(lost <= KalphiteQueen.MAX_HIT, "$each lost $lost")
                    if (lost > 0) hurt += each
                }
            }
            assertTrue(alice in hurt, "the target is hurt")
            assertTrue(bob in hurt, "the player beside the target is hurt too")
            assertFalse(carol in hurt, "the player nine tiles away is not")
        }

    @Test
    fun GameTestState.`protect from missiles stops the spine but it still costs a prayer point`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.freeze()
            alice.telejump(run.real(CoordGrid(3490, 9490, 0)))
            bob.telejump(run.real(KalphiteArena.start))
            alice.setVarBit(varbits.protect_from_missiles, 1)
            alice.stats[stats.prayer] = 70
            var lost = 0
            repeat(40) {
                topUp(alice)
                deps.queen.ranged(run, fight, alice)
                advance(ticks = 6)
                lost += 99 - alice.hitpoints
            }
            assertEquals(0, lost, "the prayer blocks every spine")
            assertTrue(alice.stats[stats.prayer] < 70, "but the spines that rolled damage drain")
        }

    @Test
    fun GameTestState.`the magic glow bounces to a player near the last one and no further`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.freeze()
            val at = run.real(CoordGrid(3490, 9490, 0))
            alice.telejump(at)
            bob.telejump(at.translate(2, 0))
            carol.telejump(at.translate(12, 0))
            val hurt = HashSet<Player>()
            repeat(60) {
                topUp(alice, bob, carol)
                deps.queen.magic(run, fight, alice)
                advance(ticks = 8)
                for (each in listOf(alice, bob, carol)) {
                    val lost = 99 - each.hitpoints
                    assertTrue(lost <= KalphiteQueen.MAX_HIT, "$each lost $lost")
                    if (lost > 0) hurt += each
                }
            }
            assertTrue(alice in hurt && bob in hurt, "the glow reaches the second player")
            assertFalse(carol in hurt, "but not one twelve tiles away")
        }

    @Test
    fun GameTestState.`when the first form falls she splits open and rises with full hitpoints`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val boss = fight.boss
            boss.defenceLvl = 150
            boss.heroPoints(alice, 5)
            boss.hitpoints = 0
            boss.queueDeath()
            until("she is molting", alice, bob, timeout = 10) { fight.step == KalphiteStep.Molting }
            assertFalse(boss.isValidTarget(), "nothing can touch her while she splits")
            assertTrue(deps.queen.isAirborne(boss), "the emerge animation is the airborne model's")
            assertEquals(0, boss.hitpoints)

            until("she has risen", alice, bob, timeout = KalphiteQueen.MOLT_TICKS + 10) {
                fight.step == KalphiteStep.Fighting
            }
            assertTrue(deps.queen.isAirborne(boss))
            assertEquals(255, boss.hitpoints)
            assertEquals(150, boss.defenceLvl, "a drained defence carries over")
            assertEquals(kalphite_npcs.queen.id, boss.type.id)
            assertEquals(kalphite_npcs.airborne.id, boss.visType.id)
            assertTrue(boss.isValidTarget())
            assertFalse(fight.dead)
            assertEquals(WaveState.InWave, run.state)
        }

    @Test
    fun GameTestState.`a hit already in the air cannot kill the form that is rising`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val boss = fight.boss
            boss.hitpoints = 0
            boss.queueDeath()
            until("she is molting", alice, bob, timeout = 10) { fight.step == KalphiteStep.Molting }
            // A second death is queued by a hit that lands after the first was taken.
            boss.queueDeath()
            until("she has risen", alice, bob, timeout = KalphiteQueen.MOLT_TICKS + 10) {
                fight.step == KalphiteStep.Fighting
            }
            advance(ticks = 6)
            assertTrue(boss.isSlotAssigned)
            assertEquals(255, boss.hitpoints)
            assertTrue(deps.queen.isAirborne(boss))
            assertFalse(fight.dead)
        }

    @Test
    fun GameTestState.`after twenty minutes in the second form she goes back to the first`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val boss = fight.boss
            fight.freeze()
            deps.queen.beginMolt(boss)
            deps.queen.endMolt(boss)
            assertTrue(deps.queen.isAirborne(boss))
            boss.hitpoints = 100

            fight.airborneSince = mapClock.cycle - KalphiteQueen.AIRBORNE_TIMEOUT
            advance(ticks = 3)
            assertFalse(deps.queen.isAirborne(boss), "she is crawling again")
            assertEquals(100, boss.hitpoints, "with the hitpoints she had left")

            // The next time the first form falls, the second rises with full hitpoints.
            boss.hitpoints = 0
            boss.queueDeath()
            until("she has risen again", alice, bob, timeout = KalphiteQueen.MOLT_TICKS + 20) {
                deps.queen.isAirborne(boss) && fight.step == KalphiteStep.Fighting
            }
            assertEquals(255, boss.hitpoints)
        }

    @Test
    fun GameTestState.`killing the second form wins the run, counts the tasks and drops loot`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            val boss = fight.boss
            fight.freeze()
            deps.queen.beginMolt(boss)
            deps.queen.endMolt(boss)
            fight.freeze()
            val table = DropTable("test", always = listOf(DropStack(objTypes[objs.bones], 1, 1)))
            deps.dropTables.replaceAll(mapOf(kalphite_npcs.queen.id to table))
            assertFalse(deps.tracker.isComplete(alice, KILL))

            alice.observerUUID = 1L
            boss.heroPoints(alice, 5)
            val centre = boss.coords.translate(2, 2)
            boss.hitpoints = 0
            boss.queueDeath()
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertTrue(deps.tracker.isComplete(bob, KILL), "the team's kill counts for bob too")
            assertEquals(1, alice.vars[LeagueProgressVarps.kill_kalphite_queen_50])
            assertEquals(1, alice.vars[LeagueProgressVarps.kill_kalphite_queen_150])
            advance(ticks = 20)

            assertEquals(1, objRepo.findAll(centre).toList().size, "the loot lands at her middle")
            // Nobody is sent out: she waits to come back.
            assertEquals(WaveState.WaitingForNextWave, run.state)
            assertTrue(deps.instances.instanceOf(alice) != null, "alice is still in the chamber")
        }

    @Test
    fun GameTestState.`after a kill she is back in the same chamber as the crawling form`() =
        runKalphiteTest { deps, alice, bob, _ ->
            val run = fightOf(deps, alice, bob)
            val dead = checkNotNull(run.fight)
            dead.freeze()
            deps.queen.beginMolt(dead.boss)
            deps.queen.endMolt(dead.boss)
            dead.freeze()
            dead.boss.hitpoints = 0
            dead.boss.queueDeath()
            until("she is back", alice, bob, timeout = 200) {
                run.state == WaveState.InWave && run.fight !== dead
            }
            val back = checkNotNull(run.fight)
            assertTrue(back.boss.isSlotAssigned, "a new queen is in the chamber")
            assertTrue(run.instance.contains(back.boss.coords))
            assertFalse(back.boss === dead.boss)
            assertFalse(deps.queen.isAirborne(back.boss))
            assertEquals(255, back.boss.hitpoints)
        }

    @Test
    fun GameTestState.`a hit near the cocoons can hatch a worker that bites and then goes`() =
        runKalphiteTest { deps, alice, bob, carol ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.freeze()
            val boss = fight.boss
            boss.telejump(deps.collision, run.real(CoordGrid(3484, 9497, 0)))
            alice.telejump(run.real(CoordGrid(3500, 9490, 0)))
            bob.telejump(alice.coords)
            carol.telejump(alice.coords)
            // A queen that is splitting open cannot be hurt, so nothing hatches yet.
            repeat(2_000) { deps.queen.hurt(boss, alice) }
            assertTrue(run.monsters.isEmpty())
            fight.step = KalphiteStep.Fighting
            fight.attackAt = mapClock.cycle + 100_000
            repeat(2_000) { deps.queen.hurt(boss, alice) }
            val workers = run.monsters.toList()
            assertTrue(workers.isNotEmpty(), "a worker hatched")
            assertTrue(
                workers.size <= KalphiteQueen.MAX_WORKERS,
                "no more than ${KalphiteQueen.MAX_WORKERS} at once",
            )
            val spawns = KalphiteArena.cocoons.map { run.real(it.spawn) }.toSet()
            for (worker in workers) {
                assertEquals(kalphite_npcs.worker.id, worker.type.id)
                assertTrue(worker.coords in spawns, "hatched at a cocoon: ${worker.coords}")
            }

            // They do not stay for ever.
            advance(ticks = KalphiteQueen.WORKER_LIFETIME + 5)
            assertTrue(workers.none { it.isSlotAssigned }, "the workers have gone")
        }

    @Test
    fun GameTestState.`entering the lair shows the roped tunnel so no rope is needed`() =
        runKalphiteTest(enter = false) { deps, alice, _, _ ->
            alice.setVarBit(kalphite_varbits.entrance, 0)
            assertNotEquals(1, alice.vars[kalphite_varbits.entrance])
            deps.eventBus.publish(DungeonEntered(alice, Dungeon.KALPHITE_LAIR))
            assertEquals(1, alice.vars[kalphite_varbits.entrance])
        }

    private companion object {
        /** The league task "Defeat the Kalphite Queen". */
        const val KILL = 864
    }
}
