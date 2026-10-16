package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.route.RayCastValidator
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class InfernoPillarsTest {
    private object InfernoModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Inferno::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val inferno: Inferno,
        val pillars: InfernoPillars,
        val locRepo: LocRepository,
        val rays: RayCastValidator,
        val collision: CollisionFlagMap,
    )

    /** Enters alice into an Inferno that plays [waves] and waits for its first wave. */
    private fun GameTestState.runPillarTest(
        waves: String,
        body: GameTestScope.(Deps, Player, InfernoRun) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            InfernoModule,
            InfernoScript::class,
            InfernoCombatScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            alice.stats[stats.hitpoints] = 99
            deps.inferno.waveLoader = { InfernoWaves.parse(waves, "test waves") }
            deps.inferno.enter(alice)
            val run = checkNotNull(deps.inferno.runOf(alice))
            body(deps, alice, run)
        }

    private fun GameTestScope.nextWave(run: InfernoRun, number: Int) {
        advanceUntil(
            predicate = { run.waveNumber == number },
            timeoutTicks = 40,
            timeoutMessage = { "wave $number never spawned" },
        )
    }

    private fun waves(vararg first: String): String {
        val text = StringBuilder()
        first.forEachIndexed { i, line ->
            text.append("[[wave]]\nnumber = ${i + 1}\nmonsters = [$line]\n\n")
        }
        text.append("[[wave]]\nnumber = ${first.size + 1}\nmonsters = [\"zuk\"]\nboss = true\n")
        return text.toString()
    }

    private val nibblers = "\"nibbler\", \"nibbler\", \"nibbler\""

    private fun GameTestScope.hasLoc(deps: Deps, pillar: InfernoPillar): Boolean =
        deps.locRepo.findExact(
            pillar.southWest,
            locTypes[InfernoPillarLocs.pillars[pillar.index]],
        ) != null

    /** The damage varbit [player] has been told about for [pillar]. */
    private fun varbit(player: Player, pillar: InfernoPillar): Int =
        player.vars[InfernoVarBits.pillar_damage[pillar.index]]

    private fun GameTestScope.flags(deps: Deps, pillar: InfernoPillar): List<Int> =
        (0 until 3).flatMap { dz ->
            (0 until 3).map { dx ->
                deps.collision[pillar.southWest.x + dx, pillar.southWest.z + dz, 0] and
                    InfernoPillars.MASK
            }
        }

    @Test
    fun GameTestState.`a run starts with three whole pillars in its own copy of the arena`() =
        runPillarTest(waves(nibblers)) { deps, _, run ->
            assertEquals(3, run.pillars.size)
            for (pillar in run.pillars) {
                assertEquals(255, pillar.hitpoints)
                assertTrue(run.instance.contains(pillar.southWest))
                assertTrue(hasLoc(deps, pillar), "the pillar loc stands")
                assertTrue(
                    flags(deps, pillar).all { it == InfernoPillars.MASK },
                    "nine blocked tiles",
                )
            }
            assertEquals(3, run.pillars.map { it.southWest }.toSet().size)
        }

    @Test
    fun GameTestState.`a pillar blocks line of sight and walking until it falls`() =
        runPillarTest(waves(nibblers)) { deps, _, run ->
            val pillar = run.pillars.first()
            val south = CoordGrid(pillar.centre.x, pillar.southWest.z - 3, 0)
            val north = CoordGrid(pillar.centre.x, pillar.southWest.z + 5, 0)
            assertFalse(deps.rays.hasLineOfSight(south, north), "behind the pillar")
            val aside = CoordGrid(pillar.southWest.x - 3, pillar.centre.z, 0)
            val across = CoordGrid(pillar.southWest.x + 5, pillar.centre.z, 0)
            assertFalse(deps.rays.hasLineOfSight(aside, across), "through its middle row")
            val clear = CoordGrid(pillar.southWest.x - 3, pillar.southWest.z - 3, 0)
            val clearFar = CoordGrid(pillar.southWest.x - 3, pillar.southWest.z + 5, 0)
            assertTrue(deps.rays.hasLineOfSight(clear, clearFar), "beside it")

            deps.pillars.damage(run, pillar, 255)

            assertTrue(deps.rays.hasLineOfSight(south, north), "the way is open again")
            assertTrue(flags(deps, pillar).all { it == 0 })
        }

    @Test
    fun GameTestState.`a pillar changes look as it weakens and disappears at nothing`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            val pillar = run.pillars.first()
            assertEquals(0, varbit(alice, pillar), "a whole pillar")
            deps.pillars.damage(run, pillar, 63) // 192 left: still the first rock
            assertEquals(63, varbit(alice, pillar))
            assertEquals(0, InfernoPillar.stageFor(pillar.hitpoints))
            deps.pillars.damage(run, pillar, 1) // 191: the second rock begins at 64 damage
            assertEquals(64, varbit(alice, pillar))
            assertEquals(1, InfernoPillar.stageFor(pillar.hitpoints))
            deps.pillars.damage(run, pillar, 128) // 63
            assertEquals(192, varbit(alice, pillar))
            assertEquals(3, InfernoPillar.stageFor(pillar.hitpoints))
            assertTrue(hasLoc(deps, pillar))
            assertTrue(flags(deps, pillar).all { it == InfernoPillars.MASK }, "still blocking")

            deps.pillars.damage(run, pillar, 63)

            assertFalse(pillar.alive)
            assertFalse(hasLoc(deps, pillar))
            assertNotNull(run.pillars.first { it.alive })
        }

    @Test
    fun GameTestState.`a pillar shows its health bar when a nibbler bites it`() =
        runPillarTest(waves(nibblers)) { deps, _, run ->
            nextWave(run, 1)
            val pillar = run.pillars.first()
            val target = checkNotNull(pillar.target)
            assertEquals(3, target.size)
            assertEquals(255, target.hitpoints)
            assertEquals(255, target.baseHitpointsLvl)
            assertEquals(pillar.southWest, target.coords)
            val nibbler = run.monsters.first()

            deps.pillars.damage(run, pillar, 4, source = nibbler)
            advance(ticks = 2)

            assertEquals(251, pillar.hitpoints)
            assertEquals(251, target.hitpoints, "the bar shows what the pillar has left")
            assertTrue(target.isSlotAssigned)
        }

    @Test
    fun GameTestState.`every nibbler of a wave goes for the same pillar`() =
        runPillarTest(waves(nibblers)) { _, _, run ->
            nextWave(run, 1)

            advance(ticks = 3)

            val chosen = checkNotNull(run.nibblerPillar)
            assertTrue(chosen.alive)
            val targets =
                run.monsters.map { run.states.getValue(it).walkTarget }.filterNotNull().toSet()
            assertTrue(targets.isNotEmpty(), "they were sent somewhere")
            assertTrue(targets.all { chosen.isNextTo(it) }, "all next to $chosen, got $targets")
            advance(ticks = 60)
            assertTrue(
                run.pillars.filter { it !== chosen }.all { it.hitpoints == 255 },
                "the others were not touched",
            )
            assertTrue(chosen.hitpoints < 255, "the chosen one was bitten")
        }

    @Test
    fun GameTestState.`a fallen pillar leaves rubble that plays the collapse and then goes`() =
        runPillarTest(waves(nibblers)) { deps, _, run ->
            val pillar = run.pillars.first()
            val health = checkNotNull(pillar.target)

            deps.pillars.damage(run, pillar, 255)

            val rubble = checkNotNull(pillar.rubble)
            assertTrue(rubble.isSlotAssigned)
            assertFalse(health.isSlotAssigned, "the health bar npc is gone")
            assertEquals(3, rubble.size)
            advance(ticks = 30)
            assertFalse(rubble.isSlotAssigned, "the rubble disappeared by itself")
        }

    @Test
    fun GameTestState.`a falling pillar hurts a player and a monster next to it, no one else`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            nextWave(run, 1)
            val pillar = run.pillars.first()
            // Stop the nibblers going for the players or the pillars while the test runs.
            run.monsters.forEach { it.actionDelay = Int.MAX_VALUE }
            val near = run.monsters[0]
            val far = run.monsters[1]
            near.telejump(deps.collision, CoordGrid(pillar.southWest.x - 1, pillar.centre.z, 0))
            far.telejump(deps.collision, CoordGrid(pillar.southWest.x - 6, pillar.centre.z, 0))
            alice.telejump(CoordGrid(pillar.southWest.x + 3, pillar.southWest.z + 3, 0))
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.assign("bob", 1)
            bob.stats[stats.hitpoints] = 99
            deps.inferno.enter(bob)
            bob.telejump(CoordGrid(pillar.southWest.x + 5, pillar.centre.z, 0))
            val before = alice.hitpoints

            deps.pillars.damage(run, pillar, 255)
            advance(ticks = 3)

            assertEquals(before - InfernoPillars.COLLAPSE_DAMAGE, alice.hitpoints, "the corner")
            assertEquals(99, bob.hitpoints, "two tiles away")
            assertFalse(near in run.monsters, "the nibbler beside it died")
            assertTrue(far in run.monsters, "the one further off did not")
        }

    @Test
    fun GameTestState.`nibblers eat the pillars and leave the player alone`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            nextWave(run, 1)
            val before = alice.hitpoints

            advance(ticks = 60)

            assertTrue(run.pillars.sumOf { it.hitpoints } < 3 * 255, "the pillars were bitten")
            assertEquals(before, alice.hitpoints, "the player was not attacked")
            for (npc in run.monsters) {
                assertEquals(NpcMode.None, npc.mode)
                assertTrue(
                    run.pillars.any { it.isNextTo(npc.coords) },
                    "a nibbler at ${npc.coords} (hp ${npc.hitpoints}) is not next to a pillar: ${run.pillars.map { it.southWest to it.hitpoints }}",
                )
            }
        }

    @Test
    fun GameTestState.`a nibbler bites a pillar for two to four every four ticks`() =
        runPillarTest(waves("\"nibbler\"")) { deps, _, run ->
            nextWave(run, 1)
            val nibbler = run.monsters.single()
            val pillar = run.pillars.first()
            run.nibblerPillar = pillar
            nibbler.telejump(deps.collision, CoordGrid(pillar.southWest.x - 1, pillar.centre.z, 0))
            nibbler.actionDelay = -1
            val start = pillar.hitpoints

            advance(ticks = 1)
            val first = start - pillar.hitpoints
            assertTrue(first in 2..4, "the first bite did $first")

            advance(ticks = 2)
            assertEquals(start - first, pillar.hitpoints, "nothing for the next ticks")
            advance(ticks = 2)
            assertTrue(start - first - pillar.hitpoints in 2..4, "and then the next bite")
        }

    @Test
    fun GameTestState.`a nibbler that is hit does not fight back while a pillar stands`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            nextWave(run, 1)
            val before = alice.hitpoints

            for (nibbler in run.monsters) {
                nibbler.queueCombatRetaliate(alice)
            }
            advance(ticks = 8)

            assertEquals(before, alice.hitpoints, "nobody bit the player")
            assertTrue(run.monsters.all { it.mode == NpcMode.None }, "still busy with the pillars")

            run.pillars.forEach { deps.pillars.damage(run, it, 255) }
            advance(ticks = 2)
            for (nibbler in run.monsters) {
                nibbler.queueCombatRetaliate(alice)
            }
            advance(ticks = 2)

            assertTrue(run.monsters.all { it.mode == NpcMode.OpPlayer2 }, "now they fight")
        }

    @Test
    fun GameTestState.`when the last pillar is gone the nibblers turn on the player`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            nextWave(run, 1)

            run.pillars.forEach { deps.pillars.damage(run, it, 255) }
            advance(ticks = 2)

            assertTrue(run.pillars.none { it.alive })
            val nibblersLeft = run.monsters.filter { it.hitpoints > 0 }
            assertTrue(nibblersLeft.all { it.mode == NpcMode.OpPlayer2 })
        }

    @Test
    fun GameTestState.`the pillars fall when the first Jad wave starts`() =
        runPillarTest(waves("\"nibbler\"", "\"jad\"")) { deps, _, run ->
            nextWave(run, 1)
            assertTrue(run.pillars.all { it.alive })
            run.pillars.first().hitpoints = 100

            for (npc in run.monsters.toList()) {
                npc.hitpoints = 0
                npc.queueDeath()
            }
            nextWave(run, 2)

            assertTrue(run.pillars.none { it.alive })
            assertTrue(run.pillars.all { flags(deps, it).all { f -> f == 0 } })
        }

    @Test
    fun GameTestState.`ending the run takes the pillars out of the arena`() =
        runPillarTest(waves(nibblers)) { deps, alice, run ->
            val pillar = run.pillars.first()
            assertNotEquals(0, flags(deps, pillar).first())

            deps.inferno.leave(alice)
            advance(ticks = 2)

            assertTrue(deps.inferno.liveRuns().isEmpty())
            assertTrue(flags(deps, pillar).all { it == 0 }, "no blocked tiles left behind")
        }
}
