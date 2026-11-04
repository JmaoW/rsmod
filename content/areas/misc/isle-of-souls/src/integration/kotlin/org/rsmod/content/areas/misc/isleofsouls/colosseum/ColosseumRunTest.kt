package org.rsmod.content.areas.misc.isleofsouls.colosseum

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.manager.AttackRangeModifiers
import org.rsmod.api.combat.manager.ForcedMaxHits
import org.rsmod.api.combat.manager.PlayerAttackKind
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.bonus.WornBonuses
import org.rsmod.api.player.interact.WornClickHooks
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.BossBarBits
import org.rsmod.content.areas.misc.isleofsouls.instances.BossBarVars
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueKillTasks
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

class ColosseumRunTest {
    private object ColosseumModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(TeamInstances::class.java).`in`(Scopes.SINGLETON)
            bind(Colosseum::class.java).`in`(Scopes.SINGLETON)
            bind(AttackStyles::class.java).`in`(Scopes.SINGLETON)
            bind(AttackTypes::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val teams: LeagueTeams,
        val instances: TeamInstances,
        val colosseum: Colosseum,
        val effects: ColosseumEffects,
        val sol: SolHeredit,
        val forced: ForcedMaxHits,
        val ranges: AttackRangeModifiers,
        val wornClicks: WornClickHooks,
        val bonuses: WornBonuses,
        val tracker: LeagueTaskTracker,
        val collision: CollisionFlagMap,
    )

    /** alice and bob, one team, in the lobby; with [enter] they are in the arena. */
    private fun GameTestState.runColosseumTest(
        enter: Boolean = true,
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            ColosseumModule,
            ColosseumScript::class,
            LeagueKillTasks::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 301L
            alice.observerUUID = 301L
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            bob.uuid = 302L
            bob.observerUUID = 302L
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            deps.colosseum.settings = ColosseumSettings()
            topUp(alice, bob)
            alice.telejump(ColosseumArena.lobbyRespawn)
            bob.telejump(ColosseumArena.lobbyRespawn.translate(1, 0))
            advance()
            if (enter) {
                assertEquals(ColosseumEntry.Entered, deps.colosseum.enter(alice))
                assertEquals(ColosseumEntry.Entered, deps.colosseum.enter(bob))
                advance(ticks = 2)
            }
            body(deps, alice, bob)
        }

    private fun GameTestScope.topUp(vararg players: Player) {
        for (each in players) {
            each.stats[stats.hitpoints] = 99
            each.stats[stats.prayer] = 99
        }
    }

    private fun GameTestScope.until(
        what: String,
        vararg players: Player,
        timeout: Int = 200,
        predicate: () -> Boolean,
    ) {
        var ticks = 0
        while (!predicate()) {
            check(ticks++ < timeout) { "never happened: $what" }
            topUp(*players)
            advance()
        }
    }

    private fun Deps.run(player: Player): ColosseumRun = checkNotNull(colosseum.runOf(player))

    /** Start-wave, the first offer's first modifier, confirm, and the wave on the floor. */
    private fun GameTestScope.startWave(deps: Deps, alice: Player, bob: Player, choice: Int = 1) {
        val run = deps.run(alice)
        deps.colosseum.startWaveRequested(alice)
        deps.colosseum.selectModifier(alice, choice)
        deps.colosseum.confirm(alice)
        until("the wave starts", alice, bob, timeout = 20) {
            run.stage == ColosseumStage.Wave || run.stage == ColosseumStage.SolIntro
        }
    }

    private fun GameTestScope.clearWave(deps: Deps, alice: Player, bob: Player) {
        val run = deps.run(alice)
        assertTrue(deps.colosseum.killWave(alice))
        until("the wave is cleared", alice, bob, timeout = 30) {
            run.stage == ColosseumStage.Intermission || run.stage == ColosseumStage.Finished
        }
    }

    // The arena ----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`the team enters one copy of the arena with Minimus and the seated Sol`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            assertEquals(run, deps.run(bob))
            assertEquals(ColosseumInstance.type, run.instance.type)
            assertEquals(2, run.members.size)
            for (spot in
                listOf(ColosseumArena.start, ColosseumArena.solSeat, ColosseumArena.chest)) {
                assertEquals(run.instance.region.normal[spot], run.inst(spot), "the copy's $spot")
                assertEquals(spot, run.real(run.inst(spot)))
            }
            val minimus = checkNotNull(run.minimus)
            assertEquals(colosseum_npcs.minimus.id, minimus.type.id)
            assertEquals(run.inst(ColosseumArena.minimusStart), minimus.coords)
            assertEquals(colosseum_npcs.sol_seated.id, run.seatedSol?.type?.id)
            assertEquals(ColosseumStage.Intermission, run.stage)
            // Each player's first wave's loot is rolled as they arrive.
            assertEquals(
                ColosseumLoot.FIRST_WAVE_SPLINTERS,
                run.memberOf(alice)?.future?.single()?.count,
            )
        }

    @Test
    fun GameTestState.`the arena's start, the spawn points and the chest are open floor`() =
        runColosseumTest { deps, alice, _ ->
            val run = deps.run(alice)
            val blocked = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
            fun open(real: CoordGrid, what: String) {
                val at = run.inst(real)
                assertEquals(
                    0,
                    deps.collision[at.x, at.z, at.level] and blocked,
                    "$what is blocked",
                )
            }
            open(ColosseumArena.start, "the start")
            open(ColosseumArena.arrivalWalk, "the walk-up")
            open(ColosseumArena.solPlayerStart, "Sol's player start")
            for (point in ColosseumArena.spawnPoints) {
                open(point, "spawn point $point")
            }
            // The pillars are map locs, which the test map does not load: their tiles were checked
            // against the cache's collision instead (tools/dungeons/collision.py).
        }

    // The intermission and the waves ------------------------------------------------------------

    @Test
    fun GameTestState.`the first offer is fixed and the first confirm picks for the team`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            deps.colosseum.startWaveRequested(alice)
            deps.colosseum.startWaveRequested(bob)
            assertEquals(ColosseumModifier.FIRST_OFFER.sortedBy { it.key }, run.offered)
            assertTrue(colosseum_interfaces.intermission in alice.ui)
            assertTrue(colosseum_interfaces.intermission in bob.ui)
            deps.colosseum.selectModifier(bob, 2)
            assertEquals(2, bob.vars[colosseum_varbits.selected_modifier])
            deps.colosseum.confirm(bob)
            assertEquals(1, run.modifiers.tier(ColosseumModifier.Relentless))
            assertEquals(1, alice.vars[colosseum_varbits.tier_relentless], "alice sees the tier")
            assertFalse(colosseum_interfaces.intermission in alice.ui, "her screen closes")
            assertEquals(ColosseumStage.Starting, run.stage)
            assertNull(run.minimus)
            deps.colosseum.confirm(alice)
            assertEquals(1, run.modifiers.tier(ColosseumModifier.Relentless), "picked once")

            advance(ticks = Colosseum.WAVE_START_DELAY + 1)
            assertEquals(ColosseumStage.Wave, run.stage)
            assertEquals(1, run.wave)
            val kinds = run.monsters.mapNotNull { run.stateOf(it)?.kind }.sorted()
            assertEquals(
                listOf(
                    ColosseumMonster.Berserker,
                    ColosseumMonster.Seer,
                    ColosseumMonster.Archer,
                    ColosseumMonster.Shaman,
                ),
                kinds,
            )
            // Relentless I: the monsters ignore a third of the players' Defence.
            assertTrue(run.monsters.all { it.targetDefenceRollPercent == 67 })
            assertEquals(1, alice.vars[colosseum_varbits.highest_wave])
        }

    @Test
    fun GameTestState.`clearing a wave gives each player its loot and glory and brings Minimus`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            clearWave(deps, alice, bob)
            assertEquals(ColosseumStage.Intermission, run.stage)
            assertEquals(1, run.cleared)
            for (player in listOf(alice, bob)) {
                val member = checkNotNull(run.memberOf(player))
                assertEquals(ColosseumLoot.FIRST_WAVE_SPLINTERS, member.rewards.single().count)
                assertTrue(
                    member.glory >= 300,
                    "completion, no damage and blasphemy: ${member.glory}",
                )
                assertEquals(member.glory, player.vars[colosseum_varps.current_glory])
                assertTrue(member.future.isNotEmpty(), "wave 2's loot is rolled")
                assertTrue(deps.tracker.isComplete(player, 1664), "the wave 1 task")
            }
            assertNotNull(run.minimus, "Minimus is back")
            // The second offer is drawn from everything but the late modifiers.
            deps.colosseum.startWaveRequested(alice)
            assertEquals(3, run.offered.size)
            assertFalse(ColosseumModifier.RedFlag in run.offered)
        }

    @Test
    fun GameTestState.`reinforcements come in when a wave lasts 40 seconds`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            assertFalse(run.monsters.any { run.stateOf(it)?.kind == ColosseumMonster.Jaguar })
            until("40 seconds pass", alice, bob, timeout = 80) { run.reinforced }
            val jaguar =
                run.monsters.firstOrNull { run.stateOf(it)?.kind == ColosseumMonster.Jaguar }
            assertNotNull(jaguar, "the jaguar warrior joins wave 1")
            val gates = setOf(ColosseumArena.NORTH_GATE_Z, ColosseumArena.SOUTH_GATE_Z)
            assertTrue(run.real(checkNotNull(jaguar).coords).z in gates)
        }

    // Deaths, rejoining and leaving ---------------------------------------------------------------

    @Test
    fun GameTestState.`a dead player keeps their chest and rejoins at the next intermission`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            clearWave(deps, alice, bob)
            startWave(deps, alice, bob)
            deps.colosseum.died(alice)
            assertEquals(ColosseumArena.lobbyRespawn, deps.colosseum.respawnFor(alice))
            assertNull(deps.colosseum.respawnFor(alice), "once")
            alice.telejump(ColosseumArena.lobbyRespawn)
            advance()
            assertEquals(ColosseumEntry.WaveUnderway, deps.colosseum.enter(alice))
            // Alice is in the lobby: bob's run is the one to clear.
            clearWave(deps, bob, alice)
            val aliceMember = checkNotNull(run.memberOf(alice))
            assertEquals(1, aliceMember.rewards.size, "nothing for the wave she missed")
            assertTrue(aliceMember.previous.isEmpty())
            assertTrue(checkNotNull(run.memberOf(bob)).previous.isNotEmpty(), "bob earned wave 2")
            assertEquals(ColosseumEntry.Rejoined, deps.colosseum.enter(alice))
            advance()
            assertFalse(aliceMember.dead)
            assertEquals(run, deps.colosseum.runOf(alice))
        }

    @Test
    fun GameTestState.`the run ends when nobody is left in the arena`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            alice.telejump(ColosseumArena.lobbyRespawn)
            bob.telejump(ColosseumArena.lobbyRespawn)
            advance(ticks = 2)
            assertNull(deps.colosseum.teamRun(alice))
            assertTrue(run.monsters.isEmpty())
        }

    @Test
    fun GameTestState.`forfeiting brings the chest out and leaving keeps the glory`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            clearWave(deps, alice, bob)
            assertEquals(Colosseum.LeaveKind.Forfeit, deps.colosseum.leaveKind(alice))
            deps.colosseum.forfeit(alice)
            assertNotNull(run.chest)
            assertEquals(Colosseum.LeaveKind.Leave, deps.colosseum.leaveKind(alice))
            assertEquals(ColosseumStage.Intermission, run.stage, "bob can carry on")
            val glory = checkNotNull(run.memberOf(alice)).glory
            deps.colosseum.leave(alice)
            advance()
            assertEquals(ColosseumArena.lobbyRespawn, alice.coords)
            assertEquals(glory, alice.vars[colosseum_varps.glory])
            assertNull(run.memberOf(alice))
        }

    // The modifiers and the hooks -----------------------------------------------------------------

    @Test
    fun GameTestState.`the warband always take a max hit from the style they are weak to`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            startWave(deps, alice, bob)
            fun kind(kind: ColosseumMonster): Npc =
                run.monsters.first { run.stateOf(it)?.kind == kind }
            val berserker = kind(ColosseumMonster.Berserker)
            assertTrue(deps.forced.isForced(alice, berserker, PlayerAttackKind.Magic))
            assertFalse(deps.forced.isForced(alice, berserker, PlayerAttackKind.Melee))
            assertTrue(
                deps.forced.isForced(alice, kind(ColosseumMonster.Seer), PlayerAttackKind.Ranged)
            )
            assertTrue(
                deps.forced.isForced(alice, kind(ColosseumMonster.Archer), PlayerAttackKind.Melee)
            )
            assertFalse(
                deps.forced.isForced(alice, kind(ColosseumMonster.Shaman), PlayerAttackKind.Magic)
            )
        }

    @Test
    fun GameTestState.`myopia shortens a player's attack range, never below 1`() =
        runColosseumTest { deps, alice, _ ->
            val run = deps.run(alice)
            val npc = checkNotNull(run.minimus)
            assertEquals(10, deps.ranges.modify(alice, npc, 10))
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Myopia, 2))
            assertEquals(6, deps.ranges.modify(alice, npc, 10))
            assertEquals(1, deps.ranges.modify(alice, npc, 3))
        }

    @Test
    fun GameTestState.`doom kills at its limit and frailty takes away healing above the cap`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Frailty, 3))
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Doom, 3))
            alice.stats[stats.hitpoints] = 99
            advance()
            assertEquals(60, alice.hitpoints, "99 less 40% is 60")
            val member = checkNotNull(run.memberOf(alice))
            repeat(4) { deps.effects.damageTaken(run, member, 1, fromEnemy = true) }
            assertEquals(4, member.doomStacks)
            assertTrue(member.hitByEnemy)
            deps.effects.damageTaken(run, member, 1, fromEnemy = false)
            advance(ticks = 2)
            assertEquals(0, alice.hitpoints, "the fifth stack kills")
        }

    @Test
    fun GameTestState
        .`a charged dizana's quiver adds 10 ranged accuracy and 1 strength to arrows`() =
        runColosseumTest(enter = false) { deps, alice, _ ->
            alice.worn[Wearpos.Quiver.slot] = InvObj(objs.bronze_arrow, 100)
            alice.worn[Wearpos.Back.slot] = InvObj(colosseum_objs.quiver_uncharged, 1)
            val plain = deps.bonuses.calculate(alice)
            alice.worn[Wearpos.Back.slot] = InvObj(colosseum_objs.quiver_charged, 1)
            val charged = deps.bonuses.calculate(alice)
            assertEquals(plain.offRange + 10, charged.offRange)
            assertEquals(plain.rangedStr + 1, charged.rangedStr)
            alice.worn[Wearpos.Back.slot] = InvObj(colosseum_objs.quiver_blessed, 1)
            assertEquals(plain.offRange + 10, deps.bonuses.calculate(alice).offRange)
        }

    // Sol Heredit ---------------------------------------------------------------------------------

    /** Straight to wave 12, past the greeting, with Sol Heredit on the floor. */
    private fun GameTestScope.toSol(deps: Deps, alice: Player, bob: Player): SolFight {
        val run = deps.run(alice)
        assertTrue(deps.colosseum.setNextWave(alice, ColosseumWaves.FINAL_WAVE))
        startWave(deps, alice, bob)
        assertEquals(ColosseumStage.SolIntro, run.stage)
        until("he lands", alice, bob, timeout = 40) { run.stage == ColosseumStage.Wave }
        return checkNotNull(run.sol)
    }

    @Test
    fun GameTestState
        .`sol heredit lands inside the barricade and phases bring sand and a crystal`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            val fight = toSol(deps, alice, bob)
            assertEquals(colosseum_npcs.sol.id, fight.npc.type.id)
            assertEquals(run.inst(ColosseumArena.solLanding), fight.npc.coords)
            assertNull(run.seatedSol)
            assertTrue(run.barricade.isNotEmpty())
            assertTrue(ColosseumArena.inSolFloor(run.real(alice.coords)))
            fight.npc.hitpoints = 1300
            advance()
            assertEquals(1, fight.phase)
            assertEquals(1, fight.crystals.size)
            advance(ticks = SolHeredit.SAND_LANDS + 1)
            assertTrue(run.sand.size >= SolHeredit.PHASE_SAND, "sand round both: ${run.sand.size}")
            assertTrue(run.sand.values.all { it.permanent && it.solSand })
        }

    @Test
    fun GameTestState.`a grapple defended on its last tick is a perfect parry`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            val fight = toSol(deps, alice, bob)
            fight.nextAttackAt = Int.MAX_VALUE
            fight.grapple = SolGrapple(Wearpos.Torso, "body", alice, mapClock.cycle)
            advance(ticks = SolHeredit.GRAPPLE_WINDOW)
            assertTrue(deps.wornClicks.consume(alice, Wearpos.Torso.slot), "the click is his")
            advance(ticks = 2)
            assertNull(fight.grapple)
            val member = checkNotNull(run.memberOf(alice))
            assertTrue(member.maxHitUntil >= mapClock.cycle, "the next blow is empowered")
            assertTrue(deps.forced.isForced(alice, fight.npc, PlayerAttackKind.Melee))
            assertFalse(deps.forced.isForced(bob, fight.npc, PlayerAttackKind.Melee))
        }

    @Test
    fun GameTestState.`his jump is a cutscene, and his health bar shows from his landing`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            assertTrue(deps.colosseum.setNextWave(alice, ColosseumWaves.FINAL_WAVE))
            startWave(deps, alice, bob)
            until("he jumps", alice, bob, timeout = 40) { run.stage == ColosseumStage.SolJump }
            val member = checkNotNull(run.memberOf(alice))
            assertTrue(member.inCutscene, "she watches him jump")
            assertTrue(alice.isDelayed, "and cannot act")
            assertNotNull(run.seatedSol, "he leaves his seat as the screen goes black")
            until("he lands", alice, bob, timeout = SolHeredit.JUMP_TICKS + 1) {
                run.stage == ColosseumStage.Wave
            }
            val fight = checkNotNull(run.sol)
            advance()
            for (player in listOf(alice, bob)) {
                assertEquals(fight.npc.visType.id, player.vars[BossBarVars.npc], "the bar is his")
                assertEquals(1, player.vars[BossBarBits.boss])
                assertEquals(fight.npc.baseHitpointsLvl, player.vars[BossBarBits.base])
            }
            fight.npc.hitpoints = 1200
            advance()
            assertEquals(fight.npc.hitpoints, alice.vars[BossBarBits.hp], "it follows his health")
            advance(ticks = SolHeredit.LAND_TICKS)
            assertFalse(member.inCutscene, "the cutscene is over")
            assertFalse(alice.isDelayed)
            fight.npc.hitpoints = 0
            fight.npc.queueDeath()
            until("the bar goes with him", alice, bob, timeout = 20) {
                alice.vars[BossBarVars.npc] == -1 && bob.vars[BossBarVars.npc] == -1
            }
        }

    @Test
    fun GameTestState.`beating sol heredit brings the chest out with dizana's quiver`() =
        runColosseumTest { deps, alice, bob ->
            val run = deps.run(alice)
            val fight = toSol(deps, alice, bob)
            fight.npc.hitpoints = 0
            fight.npc.queueDeath()
            until("the chest comes out", alice, bob, timeout = 20) {
                run.stage == ColosseumStage.Finished
            }
            assertNotNull(run.chest)
            assertTrue(run.sand.isEmpty(), "his sand subsides")
            for (player in listOf(alice, bob)) {
                val rewards = checkNotNull(run.memberOf(player)).rewards
                assertTrue(rewards.any { it.obj.id == colosseum_objs.quiver_uncharged.id })
                assertTrue(deps.tracker.isComplete(player, 1666), "the wave 12 task")
                assertTrue(player.vars[colosseum_varps.glory] > 0)
            }
            assertEquals(Colosseum.LeaveKind.Leave, deps.colosseum.leaveKind(alice))
        }
}
