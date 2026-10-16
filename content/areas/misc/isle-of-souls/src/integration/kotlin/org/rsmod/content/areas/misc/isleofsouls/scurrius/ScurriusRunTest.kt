package org.rsmod.content.areas.misc.isleofsouls.scurrius

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.NpcAttackDelays
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
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.queueHit as queueNpcHit
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueProgressVarps
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.areas.misc.isleofsouls.waves.WaveState
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

class ScurriusRunTest {
    private object ScurriusModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Scurrius::class.java).`in`(Scopes.SINGLETON)
            // The accuracy formulas read weapon styles that this script loads.
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val scurrius: Scurrius,
        val tracker: LeagueTaskTracker,
        val hitModifier: StandardNpcHitModifier,
        val delays: NpcAttackDelays,
        val dropTables: DropTables,
    )

    /** alice and bob, one team, in a lair that Scurrius has not yet spawned in unless [enter]. */
    private fun GameTestState.runScurriusTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            ScurriusModule,
            ScurriusScript::class,
            ScurriusCombatScript::class,
            LeagueKillTasks::class,
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
            topUp(alice, bob)
            if (enter) {
                deps.scurrius.enter(alice)
                deps.scurrius.enter(bob)
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

    /** Waits for Scurrius to appear and start fighting, and returns the run. */
    private fun GameTestScope.fightOf(deps: Deps, alice: Player, bob: Player): ScurriusRun {
        val run = checkNotNull(deps.scurrius.runOf(alice))
        until("Scurrius appears and starts", alice, bob, timeout = 60) {
            run.fight?.step == ScurriusStep.Fighting
        }
        return run
    }

    /** Makes him do nothing for a good while. */
    private fun ScurriusFight.quiet(now: Int) {
        attackAt = now + 100_000
    }

    private fun ScurriusRun.real(spot: CoordGrid): CoordGrid = instance.region.normal[spot]

    @Test
    fun GameTestState.`entering puts the team in the lair and Scurrius appears on the east side`() =
        runScurriusTest { deps, alice, bob ->
            val run = checkNotNull(deps.scurrius.runOf(alice))
            assertEquals(ScurriusInstance.type, run.instance.type)
            assertEquals(run.real(ScurriusArena.start), alice.coords)
            assertEquals(run, deps.scurrius.runOf(bob))
            assertTrue(run.floor.size > 200, "the floor has ${run.floor.size} tiles")
            assertTrue(run.real(ScurriusArena.start) in run.floor, "the start tile is floor")

            val fight = fightOf(deps, alice, bob).fight!!
            assertEquals(run.real(ScurriusArena.east), fight.boss.coords)
            assertEquals(500, fight.boss.hitpoints)
            assertEquals(scurrius_npcs.boss.id, fight.boss.type.id)
            assertEquals(WaveState.InWave, run.state)
        }

    @Test
    fun GameTestState.`every spot he stands on is open floor`() =
        runScurriusTest { deps, alice, _ ->
            val run = checkNotNull(deps.scurrius.runOf(alice))
            val floor = run.floor.toSet()
            val spots =
                listOf(ScurriusArena.east, ScurriusArena.middle) +
                    ScurriusArena.piles.map { it.bossSpot }
            for (spot in spots) {
                for (dz in 0 until ScurriusArena.SIZE) {
                    for (dx in 0 until ScurriusArena.SIZE) {
                        val tile = run.real(CoordGrid(spot.x + dx, spot.z + dz, spot.level))
                        assertTrue(tile in floor, "$spot is not floor at +$dx,+$dz")
                    }
                }
            }
        }

    @Test
    fun GameTestState.`the way out is where the bars are`() = runScurriusTest { deps, alice, _ ->
        assertTrue(deps.scurrius.leave(alice))
        assertEquals(ScurriusArena.exit, alice.coords)
    }

    @Test
    fun GameTestState.`he walks up to a player before he swipes`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            // Both players wait on the far side of the room from where he stands.
            alice.telejump(run.real(CoordGrid(3292, 9862, 0)))
            bob.telejump(run.real(CoordGrid(3292, 9873, 0)))
            until("he is beside a player", alice, bob, timeout = 80) {
                fight.boss.distanceTo(alice) <= 1 || fight.boss.distanceTo(bob) <= 1
            }
        }

    @Test
    fun GameTestState.`at eighty percent he walks to a food pile, eats and heals`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.boss.hitpoints = 399
            until("he goes to a pile", alice, bob) { fight.step == ScurriusStep.ToPile }
            val pile = checkNotNull(fight.pile)
            until("he is at the pile", alice, bob, timeout = 80) {
                fight.step == ScurriusStep.Eating
            }
            assertEquals(run.real(pile.bossSpot), fight.boss.coords)
            assertTrue(fight.fed)

            // Two players in the room: five heals of 5 each.
            until("he is done eating", alice, bob, timeout = 80) {
                fight.step == ScurriusStep.Fighting
            }
            assertEquals(
                399 + 2 * Scurrius.HEAL_PER_PLAYER * (Scurrius.BITES / 2),
                fight.boss.hitpoints,
            )
        }

    @Test
    fun GameTestState.`he eats only once`() = runScurriusTest { deps, alice, bob ->
        val run = fightOf(deps, alice, bob)
        val fight = checkNotNull(run.fight)
        fight.boss.hitpoints = 399
        until("he is done eating", alice, bob, timeout = 200) {
            fight.fed && fight.step == ScurriusStep.Fighting
        }
        fight.boss.hitpoints = 300
        fight.quiet(mapClock.cycle)
        advance(ticks = 5)
        assertEquals(ScurriusStep.Fighting, fight.step)
    }

    @Test
    fun GameTestState.`he never swipes while he eats`() = runScurriusTest { deps, alice, bob ->
        val run = fightOf(deps, alice, bob)
        val fight = checkNotNull(run.fight)
        fight.boss.hitpoints = 399
        until("he is eating", alice, bob, timeout = 120) { fight.step == ScurriusStep.Eating }
        until("he is done eating", alice, bob, timeout = 80) {
            assertTrue(fight.lastAction != ScurriusAction.Melee, "he swiped while eating")
            fight.step == ScurriusStep.Fighting
        }
    }

    @Test
    fun GameTestState.`while he eats he takes a tenth less damage`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            val boss = fight.boss

            boss.queueNpcHit(1, HitType.Typeless, 50, deps.hitModifier)
            advance(ticks = 2)
            assertEquals(50, 500 - boss.hitpoints)

            fight.step = ScurriusStep.Eating
            fight.nextBiteAt = mapClock.cycle + 1_000
            val before = boss.hitpoints
            boss.queueNpcHit(1, HitType.Typeless, 50, deps.hitModifier)
            advance(ticks = 2)
            assertEquals(45, before - boss.hitpoints)
        }

    @Test
    fun GameTestState.`at thirty percent he roars, goes to the middle and fights from there`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.boss.hitpoints = 149
            until("he goes to the middle", alice, bob) { fight.step == ScurriusStep.ToCentre }
            until("he is in the middle", alice, bob, timeout = 80) {
                fight.step == ScurriusStep.Fighting
            }
            assertTrue(fight.enraged)
            assertEquals(run.real(ScurriusArena.middle), fight.boss.coords)
            // From the middle he only shoots, drops rubble and calls rats: no swipes.
            fight.attackAt = mapClock.cycle
            until("he has acted a few times", alice, bob, timeout = 60) {
                assertTrue(fight.lastAction != ScurriusAction.Melee, "he swiped when enraged")
                fight.lastAction != null
            }
        }

    @Test
    fun GameTestState.`rockfall hurts only players on a shadow, for fifteen to twenty-two`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            alice.telejump(run.real(CoordGrid(3292, 9862, 0)))
            bob.telejump(run.real(CoordGrid(3292, 9873, 0)))
            topUp(alice, bob)
            fight.bricks += FallingBricks(mapClock.cycle + 1, setOf(alice.coords))
            advance(ticks = 3)
            val taken = 99 - alice.hitpoints
            assertTrue(taken in Scurrius.BRICKS_MIN_HIT..Scurrius.BRICKS_MAX_HIT, "took $taken")
            assertEquals(99, bob.hitpoints, "bob was not on a shadow")
            assertTrue(fight.bricks.isEmpty())
        }

    @Test
    fun GameTestState.`a rockfall always puts a shadow on the player's own tile`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            deps.scurrius.fallingBricks(run, fight, alice, mapClock.cycle)
            val bricks = fight.bricks.single()
            assertTrue(alice.coords in bricks.tiles)
            assertTrue(bricks.tiles.size in 2..Scurrius.BRICK_TILES_MAX + 1)
            assertTrue(bricks.tiles.all { it in run.floor })
            assertEquals(mapClock.cycle + Scurrius.BRICKS_DELAY, bricks.landAt)
        }

    @Test
    fun GameTestState.`six rats a time, up to twelve times`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            deps.scurrius.summonRats(run, fight, mapClock.cycle)
            assertEquals(Scurrius.RATS_PER_WAVE, run.monsters.size)
            for (rat in run.monsters) {
                assertEquals(scurrius_npcs.rat.id, rat.type.id)
                assertEquals(1, rat.hitpoints)
                assertTrue(run.instance.contains(rat.coords))
            }
            repeat(Scurrius.RAT_WAVE_LIMIT + 3) {
                deps.scurrius.summonRats(run, fight, mapClock.cycle)
            }
            assertEquals(Scurrius.RAT_WAVE_LIMIT, fight.ratWaves)
            assertEquals(Scurrius.RAT_WAVE_LIMIT * Scurrius.RATS_PER_WAVE, run.monsters.size)
        }

    @Test
    fun GameTestState.`a giant rat dies to any hit and leaves nothing`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            deps.scurrius.summonRats(run, fight, mapClock.cycle)
            val rat = run.monsters.first()
            val at = rat.coords
            rat.queueNpcHit(1, HitType.Typeless, 1, deps.hitModifier)
            until("the rat dies", alice, bob, timeout = 20) { rat !in run.monsters }
            advance(ticks = 10)
            assertFalse(rat.isSlotAssigned)
            assertTrue(objRepo.findAll(at).toList().isEmpty(), "nothing dropped")
        }

    @Test
    fun GameTestState.`a giant rat bites for up to four, and one through Protect from Melee`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            bob.telejump(run.real(CoordGrid(3292, 9873, 0)))
            alice.telejump(run.real(CoordGrid(3296, 9867, 0)))
            deps.scurrius.summonRats(run, fight, mapClock.cycle)
            // Only alice's rat matters: keep one next to her.
            val rat = run.monsters.first()
            run.monsters.drop(1).forEach { npcRepo.del(it, Int.MAX_VALUE) }
            run.monsters.retainAll(listOf(rat))

            fun bites(protect: Boolean): List<Int> {
                alice.setVarBit(varbits.protect_from_melee, if (protect) 1 else 0)
                val damage = mutableListOf<Int>()
                repeat(120) {
                    topUp(alice, bob)
                    val before = alice.hitpoints
                    advance()
                    damage += before - alice.hitpoints
                }
                return damage.filter { it > 0 }
            }

            val plain = bites(protect = false)
            assertTrue(plain.isNotEmpty(), "the rat bit")
            assertTrue(plain.all { it <= ScurriusAttacks.RAT_MAX_HIT }, "bites $plain")

            val prayed = bites(protect = true)
            assertTrue(prayed.all { it <= ScurriusAttacks.RAT_PRAYER_MAX_HIT }, "bites $prayed")
        }

    @Test
    fun GameTestState.`a bone weapon has no delay against the rats and no other weapon has`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            deps.scurrius.summonRats(run, fight, mapClock.cycle)
            val rat = run.monsters.first()
            val boss = fight.boss

            assertEquals(5, deps.delays.adjust(alice, rat, 5))
            for (weapon in listOf(scurrius_objs.rat_bone_mace, scurrius_objs.rat_bone_bow)) {
                alice.righthand = InvObj(weapon)
                assertEquals(1, deps.delays.adjust(alice, rat, 5))
                assertEquals(5, deps.delays.adjust(alice, boss, 5), "not against him")
            }
            alice.righthand = InvObj(objs.abyssal_whip)
            assertEquals(5, deps.delays.adjust(alice, rat, 5))
        }

    @Test
    fun GameTestState.`killing him wins the run, counts the tasks and gives everyone a drop`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val fight = checkNotNull(run.fight)
            fight.quiet(mapClock.cycle)
            val table =
                DropTable(
                    "test",
                    always = listOf(DropStack(objTypes[scurrius_objs.big_bones], 1, 1)),
                )
            deps.dropTables.replaceAll(
                mapOf(scurrius_npcs.boss.id to table, scurrius_npcs.non_mvp_drops.id to table)
            )
            assertFalse(deps.tracker.isComplete(alice, KILL))

            // Only alice hurts him, so bob gets the non-MVP drops and alice the main ones.
            alice.observerUUID = 1L
            bob.observerUUID = 2L
            fight.boss.heroPoints(alice, 5)
            val centre = fight.boss.coords.translate(1, 1)
            fight.boss.hitpoints = 0
            fight.boss.queueDeath()
            until("the kill is counted", alice, bob, timeout = 60) {
                deps.tracker.isComplete(alice, KILL)
            }
            assertTrue(deps.tracker.isComplete(bob, KILL), "the team's kill counts for bob too")
            assertEquals(1, alice.vars[LeagueProgressVarps.kill_scurrius_10])
            assertEquals(1, alice.vars[LeagueProgressVarps.kill_scurrius_25])
            advance(ticks = 20)

            val bones = objRepo.findAll(centre).toList()
            assertEquals(2, bones.size, "one for the top damage dealer, one for the rest: $bones")
            // Nobody is sent out: he waits to come back.
            assertEquals(WaveState.WaitingForNextWave, run.state)
            assertTrue(deps.instances.instanceOf(alice) != null, "alice is still in the lair")
        }

    @Test
    fun GameTestState.`after a kill he comes back in the same lair`() =
        runScurriusTest { deps, alice, bob ->
            val run = fightOf(deps, alice, bob)
            val dead = checkNotNull(run.fight)
            dead.quiet(mapClock.cycle)
            dead.boss.hitpoints = 0
            dead.boss.queueDeath()
            until("he is back", alice, bob, timeout = 200) {
                run.state == WaveState.InWave && run.fight !== dead
            }
            val back = checkNotNull(run.fight)
            assertTrue(back.boss.isSlotAssigned, "a new Scurrius is in the lair")
            assertTrue(run.instance.contains(back.boss.coords))
            assertFalse(back.boss === dead.boss)
        }

    private companion object {
        /** The league task "Defeat Scurrius". */
        const val KILL = 1648
    }
}
