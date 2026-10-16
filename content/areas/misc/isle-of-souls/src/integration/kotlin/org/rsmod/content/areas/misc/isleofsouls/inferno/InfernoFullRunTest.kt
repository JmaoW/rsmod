package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState

/** Plays the real shortened Inferno from wave one to Zuk's death. */
class InfernoFullRunTest {
    private object InfernoModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Inferno::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps @Inject constructor(val teams: LeagueTeams, val inferno: Inferno)

    private fun GameTestScope.killAll(run: InfernoRun) {
        repeat(4) {
            for (npc in run.monsters.toList()) {
                npc.hitpoints = 0
                npc.queueDeath()
            }
            advance(ticks = 2)
        }
    }

    @Test
    fun GameTestState.`the whole shortened Inferno can be played to a win`() =
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
            deps.inferno.settings = InfernoSettings(InfernoMode.Shortened)
            deps.inferno.waveLoader = { InfernoWaves.parse(FULL_SHORTENED, "test waves") }
            deps.inferno.enter(alice)
            val run = checkNotNull(deps.inferno.runOf(alice))
            assertEquals(InfernoMode.Shortened, run.mode)
            assertEquals(9, run.waves.count)
            assertEquals(3, run.pillars.count { it.alive })

            for (number in 1..8) {
                advanceUntil(
                    predicate = { run.waveNumber == number },
                    timeoutTicks = 60,
                    timeoutMessage = { "wave $number never began" },
                )
                assertTrue(run.monsters.isNotEmpty(), "wave $number has monsters")
                killAll(run)
                assertEquals(0, run.monsters.size, "wave $number was cleared")
            }
            // The first Jad wave brought the pillars down.
            assertTrue(run.pillars.none { it.alive })

            advanceUntil(
                predicate = { run.zukFight != null },
                timeoutTicks = 60,
                timeoutMessage = { "Zuk never appeared" },
            )
            assertEquals(9, run.waveNumber)
            val fight = checkNotNull(run.zukFight)
            fight.nextAttackAt = Int.MAX_VALUE

            fight.zuk.hitpoints = 0
            fight.zuk.queueDeath()
            advance(ticks = 3)

            assertEquals(WaveState.Won, run.state)
            advance(ticks = WaveRun.LEAVE_DELAY + 3)
            assertTrue(deps.inferno.liveRuns().isEmpty())
            assertNotNull(alice.coords)
        }
}

/**
 * The full shortened Inferno (the event file is just Zuk while he is being tested): six waves that
 * show every monster once, a lone Jad, three Jads, then Zuk.
 */
private val FULL_SHORTENED: String = buildString {
    val waves =
        listOf(
            listOf("nibbler", "nibbler", "nibbler", "bat"),
            listOf("nibbler", "nibbler", "nibbler", "blob", "bat"),
            listOf("nibbler", "nibbler", "nibbler", "melee", "blob"),
            listOf("nibbler", "nibbler", "nibbler", "ranger", "melee"),
            listOf("nibbler", "nibbler", "nibbler", "mager", "ranger"),
            listOf("nibbler", "nibbler", "nibbler", "mager", "ranger", "melee", "blob"),
            listOf("jad"),
            listOf("jad", "jad", "jad"),
            listOf("zuk"),
        )
    waves.forEachIndexed { i, monsters ->
        val list = monsters.joinToString(", ") { "\"$it\"" }
        append("[[wave]]\nnumber = ${i + 1}\nmonsters = [$list]\n")
        if (i == waves.lastIndex) append("boss = true\n")
        append("\n")
    }
}
