package org.rsmod.content.areas.misc.isleofsouls.inferno

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.events.interact.HeldEquipEvents
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.fightcaves.cave_objs
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.masteries.LeagueMasteryTasks
import org.rsmod.content.areas.misc.isleofsouls.masteries.MasteryKills
import org.rsmod.content.areas.misc.isleofsouls.pets.pet_varps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueGearTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveRun
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos

class InfernoRewardsTest {
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
        val unlock: InfernoUnlock,
        val completions: InfernoCompletions,
        val rewards: InfernoRewards,
        val masteries: LeagueMasteryTasks,
    )

    private fun GameTestScope.tracker() = LeagueTaskTracker(eventBus, LeagueTeams(players))

    private fun GameTestState.runRewardsTest(body: GameTestScope.(Deps, Player, Player) -> Unit) =
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
            body(deps, alice, bob)
        }

    /** Starts the Zuk fight (the only wave) for alice and bob and returns the run. */
    private fun GameTestScope.zukFight(deps: Deps): InfernoRun {
        deps.inferno.waveLoader = { InfernoWaves.parse(ZUK_ONLY, "test waves") }
        deps.inferno.enter(player)
        deps.inferno.enter(players.first { it.username == "bob" })
        val run = checkNotNull(deps.inferno.runOf(player))
        advanceUntil(
            predicate = { run.zukFight != null },
            timeoutTicks = 40,
            timeoutMessage = { "the Zuk fight never began" },
        )
        return run
    }

    private fun GameTestScope.killZuk(run: InfernoRun) {
        val fight = checkNotNull(run.zukFight)
        fight.nextAttackAt = Int.MAX_VALUE
        fight.zuk.hitpoints = 0
        fight.zuk.queueDeath()
        advance(ticks = 3)
    }

    @Test
    fun GameTestState.`killing Zuk gives everyone inside the cape, the Tokkul and the tasks`() =
        runRewardsTest { deps, alice, bob ->
            repeat(12) { random.next = 3 }
            val run = zukFight(deps)

            killZuk(run)

            assertEquals(WaveState.Won, run.state)
            for (each in listOf(alice, bob)) {
                assertTrue(inferno_objs.infernal_cape in each.inv, "${each.username}: no cape")
                assertEquals(16_440, each.inv.count(cave_objs.tokkul))
                assertEquals(1, each.vars[LeagueProgressVarps.inferno_5])
                assertTrue(deps.masteries.isComplete(each, MasteryKills.SLOT_ZUK))
            }
            advance(ticks = WaveRun.LEAVE_DELAY + 3)
            assertTrue(deps.inferno.liveRuns().isEmpty())
        }

    @Test
    fun GameTestState.`a player who has left gets nothing when Zuk dies`() =
        runRewardsTest { deps, alice, bob ->
            repeat(12) { random.next = 3 }
            val run = zukFight(deps)
            deps.inferno.leave(bob)

            killZuk(run)

            assertTrue(inferno_objs.infernal_cape in alice.inv)
            assertFalse(inferno_objs.infernal_cape in bob.inv)
            assertEquals(0, bob.inv.count(cave_objs.tokkul))
        }

    @Test
    fun GameTestState.`the pet is a one in a hundred chance`() = runRewardsTest { deps, alice, _ ->
        random.next = 3
        assertFalse(deps.rewards.petRoll(alice), "nothing on an ordinary roll")
        assertEquals(0, alice.vars[pet_varps.follower])
        random.next = 0
        assertTrue(deps.rewards.petRoll(alice))
        assertEquals(inferno_objs.pet.id, alice.vars[pet_varps.follower], "it follows Alice")
    }

    @Test
    fun GameTestState.`giving up a fire cape completes the sacrifice task, once`() =
        runRewardsTest { deps, alice, _ ->
            assertFalse(tracker().isComplete(alice, InfernoCompletions.TASK_SACRIFICE))
            alice.inv[0] = InvObj(cave_objs.fire_cape)

            assertTrue(deps.unlock.sacrifice(alice))

            assertTrue(tracker().isComplete(alice, InfernoCompletions.TASK_SACRIFICE))
        }

    @Test
    fun GameTestState.`an extra infernal cape can be handed in for another pet chance`() =
        runRewardsTest { deps, alice, _ ->
            assertNull(deps.unlock.tradeInCape(alice), "nothing to hand in")

            random.next = 3
            alice.inv[0] = InvObj(inferno_objs.infernal_cape)
            assertEquals(false, deps.unlock.tradeInCape(alice), "no pet on this roll")
            assertFalse(inferno_objs.infernal_cape in alice.inv, "the cape is taken")
            assertEquals(0, alice.vars[pet_varps.follower])

            random.next = 0
            alice.inv[0] = InvObj(inferno_objs.infernal_cape)
            assertEquals(true, deps.unlock.tradeInCape(alice))
            assertEquals(inferno_objs.pet.id, alice.vars[pet_varps.follower], "it follows Alice")
            assertFalse(inferno_objs.infernal_cape in alice.inv)

            // Wiki: no gamble while the pet is owned.
            alice.inv[0] = InvObj(inferno_objs.infernal_cape)
            assertNull(deps.unlock.tradeInCape(alice))
            assertTrue(inferno_objs.infernal_cape in alice.inv, "the cape is kept")
        }

    @Test
    fun GameTestState
        .`completing the Inferno five, ten and fifteen times completes the count tasks`() =
        runRewardsTest { deps, alice, _ ->
            repeat(4) { deps.completions.completed(alice) }
            assertFalse(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_FIVE))
            deps.completions.completed(alice)
            assertTrue(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_FIVE))
            assertFalse(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_TEN))

            repeat(5) { deps.completions.completed(alice) }
            assertTrue(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_TEN))
            assertFalse(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_FIFTEEN))

            repeat(5) { deps.completions.completed(alice) }
            assertTrue(tracker().isComplete(alice, InfernoCompletions.TASK_COMPLETE_FIFTEEN))
        }

    @Test
    fun GameTestState.`wearing an infernal cape completes its task`() =
        runGameTest(LeagueGearTasks::class) {
            assertFalse(tracker().isComplete(player, 654))
            player.worn[Wearpos.Back.slot] = InvObj(inferno_objs.infernal_cape)
            eventBus.publish(
                HeldEquipEvents.WearposChange(
                    player,
                    Wearpos.Back,
                    objTypes[inferno_objs.infernal_cape],
                )
            )
            assertTrue(tracker().isComplete(player, 654))
        }

    private companion object {
        val ZUK_ONLY =
            """
            [[wave]]
            number = 1
            monsters = ["zuk"]
            boss = true
            """
                .trimIndent()
    }
}
