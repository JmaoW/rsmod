package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttacks
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcMode
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

class InfernoJadTest {
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
        val attacks: NpcAttacks,
        val collision: CollisionFlagMap,
    )

    /** Enters alice and bob (one team) into an Inferno that plays [waves]; waits for wave one. */
    private fun GameTestState.runJadTest(
        waves: String,
        body: GameTestScope.(Deps, Player, Player, InfernoRun) -> Unit,
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
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            alice.stats[stats.hitpoints] = 99
            bob.stats[stats.hitpoints] = 99
            deps.inferno.waveLoader = { InfernoWaves.parse(waves, "test waves") }
            deps.inferno.enter(alice)
            deps.inferno.enter(bob)
            val run = checkNotNull(deps.inferno.runOf(alice))
            nextWave(run, 1)
            body(deps, alice, bob, run)
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

    private val one = "\"jad\""
    private val three = "\"jad\", \"jad\", \"jad\""

    private fun Inferno.jadState(npc: Npc) = checkNotNull(stateOf(npc))

    /** Keeps every Jad quiet so a test can look at what the healers do. */
    private fun quiet(run: InfernoRun) {
        run.monsters.forEach { it.actionDelay = Int.MAX_VALUE }
    }

    @Test
    fun GameTestState.`a lone Jad stands on his own spot with five healers to come`() =
        runJadTest(waves(one)) { deps, _, _, run ->
            val jad = run.monsters.single()
            assertEquals(InfernoMonsterDefs.jad, InfernoMonsterDefs.of(jad.type))
            assertEquals(NpcMode.ApPlayer2, jad.mode)
            val state = deps.inferno.jadState(jad)
            assertEquals(8, state.jadRate)
            assertEquals(5, state.healerCount)
            assertTrue(run.instance.contains(jad.coords))
            assertEquals(350, jad.baseHitpointsLvl)
        }

    @Test
    fun GameTestState.`the triple wave has three Jads that attack 3 ticks apart every 9`() =
        runJadTest(waves(three)) { deps, _, _, run ->
            assertEquals(3, run.monsters.size)
            val states = run.monsters.map { deps.inferno.jadState(it) }
            assertTrue(states.all { it.jadRate == 9 })
            assertTrue(states.all { it.healerCount == 3 })

            val delays = run.monsters.map { it.actionDelay }.sorted()
            assertEquals(3, delays[1] - delays[0])
            assertEquals(3, delays[2] - delays[1])
            assertEquals(3, run.monsters.map { it.coords }.toSet().size, "three places")
        }

    @Test
    fun GameTestState.`a Jad that attacks waits his rate before the next attack`() =
        runJadTest(waves(one)) { deps, alice, _, run ->
            val jad = run.monsters.single()
            val first = jad.actionDelay
            alice.telejump(CoordGrid(jad.coords.x, jad.coords.z - 8, 0))

            advanceUntil(
                predicate = { jad.actionDelay != first },
                timeoutTicks = 40,
                timeoutMessage = { "Jad never attacked" },
            )

            // 8 ticks after a spell or rocks, 4 after a bite; the test sees it a tick later.
            val wait = jad.actionDelay - mapClock.cycle
            assertTrue(wait in setOf(8, 7, 4, 3), "next attack in $wait ticks")
        }

    @Test
    fun GameTestState.`in the triple wave a Jad waits 9 ticks whatever he does`() =
        runJadTest(waves(three)) { deps, alice, _, run ->
            alice.telejump(CoordGrid(run.monsters[0].coords.x + 2, run.monsters[0].coords.z - 6, 0))
            val first = run.monsters.associateWith { it.actionDelay }

            advanceUntil(
                predicate = { run.monsters.any { it.actionDelay != first.getValue(it) } },
                timeoutTicks = 40,
                timeoutMessage = { "no Jad attacked" },
            )

            val attacker = run.monsters.first { it.actionDelay != first.getValue(it) }
            assertTrue(attacker.actionDelay - mapClock.cycle in 8..9, "9 ticks, a tick on")
        }

    @Test
    fun GameTestState.`five healers appear round a lone Jad once he is hurt, and heal him`() =
        runJadTest(waves(one)) { deps, _, _, run ->
            val jad = run.monsters.single()
            quiet(run)
            assertEquals(0, run.healers.size)

            jad.hitpoints = Inferno.JAD_HEALER_THRESHOLD
            advance(ticks = 2)
            assertEquals(0, run.healers.size, "not below 175 yet")

            jad.hitpoints = Inferno.JAD_HEALER_THRESHOLD - 1
            advance(ticks = 2)
            assertEquals(5, run.healers.size)
            assertTrue(run.healers.all { it.distanceTo(jad) <= 1 }, "next to him")
            assertTrue(run.healers.all { it.mode == NpcMode.None }, "not aggressive")

            val hurt = jad.hitpoints
            advance(ticks = 12)
            assertTrue(jad.hitpoints >= hurt + 15 * 5, "healed ${jad.hitpoints - hurt}")

            // They only ever come once.
            run.healers.toList().forEach {
                it.hitpoints = 0
                it.queueDeath()
            }
            advance(ticks = 5)
            jad.hitpoints = 20
            advance(ticks = 3)
            assertEquals(0, run.healers.size)
        }

    @Test
    fun GameTestState.`each Jad of the triple wave gets three healers of his own`() =
        runJadTest(waves(three)) { deps, _, _, run ->
            quiet(run)
            run.monsters.forEach { it.hitpoints = 100 }

            advance(ticks = 2)

            assertEquals(9, run.healers.size)
            for (jad in run.monsters) {
                val own = run.healers.filter { run.states.getValue(it).owner === jad }
                assertEquals(3, own.size)
            }
        }

    @Test
    fun GameTestState.`a healer heals fifteen to twenty four per heal`() =
        runJadTest(waves(one)) { deps, _, _, run ->
            val jad = run.monsters.single()
            quiet(run)
            jad.hitpoints = 100
            advance(ticks = 2)
            // Only one healer works; the others are busy with something else.
            val healer = run.healers.first()
            run.healers.filter { it !== healer }.forEach { it.actionDelay = Int.MAX_VALUE }
            jad.hitpoints = 100
            healer.actionDelay = -1

            advance(ticks = 1)

            val healed = jad.hitpoints - 100
            assertTrue(healed in 15..24, "healed $healed")
        }

    @Test
    fun GameTestState.`a healer that is attacked stops healing and goes for the player`() =
        runJadTest(waves(one)) { deps, alice, _, run ->
            val jad = run.monsters.single()
            quiet(run)
            jad.hitpoints = 100
            advance(ticks = 2)
            val healer = run.healers.first()
            healer.hitpoints -= 1
            jad.hitpoints = 100
            healer.actionDelay = -1
            run.healers.filter { it !== healer }.forEach { it.actionDelay = Int.MAX_VALUE }

            advance(ticks = 2)

            assertEquals(NpcMode.OpPlayer2, healer.mode)
            assertEquals(100, jad.hitpoints, "it no longer heals")
        }

    @Test
    fun GameTestState.`a Jad hits every player and the right prayer blocks it`() =
        runJadTest(waves(one)) { deps, alice, bob, run ->
            val jad = run.monsters.single()
            quiet(run)
            repeat(12) { random.next = 3 }
            alice.setVarBit(varbits.protect_from_magic, 1)
            val before = mapOf(alice to alice.hitpoints, bob to bob.hitpoints)

            val sure = InfernoAttacks.jadMagic.copy(alwaysHits = true, maxHit = 5)
            val hit = deps.attacks.perform(jad, alice, sure)
            advance(ticks = 20)

            assertEquals(setOf(alice, bob), hit.toSet())
            assertEquals(0, before.getValue(alice) - alice.hitpoints, "prayer failed")
            assertTrue(before.getValue(bob) - bob.hitpoints > 0, "bob was not hit")
        }

    @Test
    fun GameTestState
        .`a dead Jad takes his healers with him, and the wave ends with the last Jad`() =
        runJadTest(waves(three, "\"bat\"")) { deps, _, _, run ->
            quiet(run)
            val jads = run.monsters.toList()
            jads.forEach { it.hitpoints = 100 }
            advance(ticks = 2)
            assertEquals(9, run.healers.size)

            val dead = jads[0]
            dead.hitpoints = 0
            dead.queueDeath()
            advance(ticks = 2)

            assertEquals(6, run.healers.size, "his three are gone")
            assertTrue(run.healers.none { run.states.getValue(it).owner === dead })
            assertEquals(2, run.monsters.size)

            for (jad in jads.drop(1)) {
                jad.hitpoints = 0
                jad.queueDeath()
            }
            advance(ticks = 3)
            assertEquals(0, run.healers.size)
            assertEquals(0, run.monsters.size)
            nextWave(run, 2)
        }

    @Test
    fun GameTestState.`a Jad is never brought back by a Jal-Zek`() =
        runJadTest(waves(one)) { deps, _, _, run ->
            quiet(run)
            val jad = run.monsters.single()
            jad.hitpoints = 0
            jad.queueDeath()
            advance(ticks = 3)

            assertTrue(run.slain.isEmpty())
            assertFalse(deps.inferno.canRevive(run))
        }
}
