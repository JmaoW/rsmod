package org.rsmod.content.areas.misc.isleofsouls.colosseum

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.combat.weapon.scripts.WeaponAttackStylesScript
import org.rsmod.api.combat.weapon.scripts.WeaponAttackTypesScript
import org.rsmod.api.combat.weapon.styles.AttackStyles
import org.rsmod.api.combat.weapon.types.AttackTypes
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.NpcHitScript
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.instances.InstanceTargetScope
import org.rsmod.content.areas.misc.isleofsouls.instances.TeamInstances
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos
import org.rsmod.routefinder.collision.CollisionFlagMap

class ColosseumFightTest {
    private object FightModule : AbstractModule() {
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
        val colosseum: Colosseum,
        val enemies: ColosseumEnemies,
        val collectionLog: CollectionLog,
        val screens: ColosseumScreens,
        val collision: CollisionFlagMap,
    )

    /** alice alone (a team of one) in the arena, between waves. */
    private fun GameTestState.runFightTest(
        body: GameTestScope.(Deps, Player, ColosseumRun) -> Unit
    ) =
        runInjectedGameTest(
            Deps::class,
            FightModule,
            ColosseumScript::class,
            ColosseumItemsScript::class,
            PlayerHitScript::class,
            NpcHitScript::class,
            WeaponAttackStylesScript::class,
            WeaponAttackTypesScript::class,
            InstanceTargetScope::class,
        ) { deps ->
            val alice = player
            alice.username = "alice"
            alice.uuid = 401L
            alice.observerUUID = 401L
            deps.colosseum.settings = ColosseumSettings()
            topUp(alice)
            alice.telejump(ColosseumArena.lobbyRespawn)
            advance()
            assertEquals(ColosseumEntry.Entered, deps.colosseum.enter(alice))
            advance(ticks = 2)
            body(deps, alice, checkNotNull(deps.colosseum.runOf(alice)))
        }

    private fun GameTestScope.topUp(player: Player) {
        player.stats[stats.hitpoints] = 99
        player.stats[stats.prayer] = 99
    }

    private fun GameTestScope.startWave(
        deps: Deps,
        alice: Player,
        run: ColosseumRun,
        wave: Int = 1,
    ) {
        if (wave != 1) {
            assertTrue(deps.colosseum.setNextWave(alice, wave))
        }
        deps.colosseum.startWaveRequested(alice)
        deps.colosseum.selectModifier(alice, 1)
        deps.colosseum.confirm(alice)
        var ticks = 0
        while (run.stage != ColosseumStage.Wave) {
            check(ticks++ < 60) { "the wave never started" }
            topUp(alice)
            advance()
        }
    }

    private fun ColosseumRun.first(kind: ColosseumMonster): Npc =
        monsters.first { stateOf(it)?.kind == kind }

    // The monsters ------------------------------------------------------------------------------

    @Test
    fun GameTestState.`echo boots hit back at whatever hurts the wearer up close`() =
        runFightTest { deps, alice, run ->
            alice.worn[Wearpos.Feet.slot] = InvObj(colosseum_objs.echo_boots)
            VarPlayerIntMapSetter.set(alice, colosseum_varbits.echo_charges, 100)
            startWave(deps, alice, run)
            var ticks = 0
            while (alice.vars[colosseum_varbits.echo_charges] == 100) {
                check(ticks++ < 60) { "nothing that hurt her was answered" }
                topUp(alice)
                advance()
            }
            assertTrue(alice.vars[colosseum_varbits.echo_charges] < 100, "a charge a recoil")
            VarPlayerIntMapSetter.set(alice, colosseum_varps.echo_off, 1)
            val left = alice.vars[colosseum_varbits.echo_charges]
            repeat(20) {
                topUp(alice)
                advance()
            }
            assertEquals(left, alice.vars[colosseum_varbits.echo_charges], "switched off")
        }

    @Test
    fun GameTestState.`the wave's monsters come for the player and hurt them`() =
        runFightTest { deps, alice, run ->
            startWave(deps, alice, run)
            var lost = 0
            repeat(40) {
                topUp(alice)
                advance()
                lost += 99 - alice.hitpoints
            }
            assertTrue(lost > 0, "nobody hit alice")
            val berserker = run.first(ColosseumMonster.Berserker)
            assertTrue(
                berserker.distanceTo(alice) <= 2,
                "the berserker is by her: ${berserker.coords}",
            )
            assertTrue(checkNotNull(run.memberOf(alice)).hitByEnemy)
        }

    @Test
    fun GameTestState
        .`a sky javelin lands on the player's tile and reentry II leaves sand there`() =
        runFightTest { deps, alice, run ->
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Reentry, 2))
            startWave(deps, alice, run, wave = 2)
            val javelin = run.first(ColosseumMonster.Javelin)
            val tile = run.real(alice.coords)
            checkNotNull(run.stateOf(javelin)).attacks = ColosseumEnemies.ARTILLERY_EVERY - 1
            javelin.actionDelay = 0
            deps.enemies.fight(run, javelin, alice)
            advance(ticks = ColosseumEnemies.ARTILLERY_LANDS + 1)
            assertTrue(tile in run.sand, "sand where it landed")
            assertTrue(checkNotNull(run.sand[tile]).permanent)
            assertTrue(run.sand.size >= 2, "and the tile south-west of it")
        }

    @Test
    fun GameTestState.`bees sting a player under them, and the totem heals a wounded monster`() =
        runFightTest { deps, alice, run ->
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Bees, 1))
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Totemic, 1))
            startWave(deps, alice, run)
            assertEquals(1, run.bees.size)
            val bees = checkNotNull(run.bees.single().npc)
            val shaman = run.first(ColosseumMonster.Shaman)
            shaman.hitpoints = 40
            topUp(alice)
            bees.telejump(deps.collision, alice.coords)
            advance()
            assertTrue(alice.hitpoints < 99, "the swarm stings: ${alice.hitpoints}")
            var ticks = 0
            while (shaman.hitpoints <= 40) {
                check(ticks++ < 20) { "the totem never healed the shaman" }
                topUp(alice)
                advance()
            }
            assertNotNull(run.totems[shaman]?.npc)
        }

    @Test
    fun GameTestState.`volatility makes a dead monster explode and leaves sand at III`() =
        runFightTest { deps, alice, run ->
            assertTrue(deps.colosseum.setModifier(alice, ColosseumModifier.Volatility, 3))
            startWave(deps, alice, run)
            val seer = run.first(ColosseumMonster.Seer)
            seer.telejump(deps.collision, alice.coords.translate(1, 0))
            advance()
            topUp(alice)
            val centre = run.real(seer.coords)
            seer.hitpoints = 0
            seer.queueDeath()
            advance(ticks = 4)
            assertTrue(centre in run.sand, "sand where it exploded")
        }

    // Sol Heredit ---------------------------------------------------------------------------------

    private fun GameTestScope.toSol(deps: Deps, alice: Player, run: ColosseumRun): SolFight {
        startWave(deps, alice, run, wave = ColosseumWaves.FINAL_WAVE)
        return checkNotNull(run.sol)
    }

    @Test
    fun GameTestState.`protect from melee turned on early is switched off and the triple hits`() =
        runFightTest { deps, alice, run ->
            val fight = toSol(deps, alice, run)
            val npc = fight.npc
            alice.telejump(npc.coords.translate(-1, 2))
            advance()
            topUp(alice)
            fight.forced = SolAttack.Triple
            fight.nextAttackAt = 0
            alice.setVarBit(varbits.protect_from_melee, 1)
            var lost = 0
            repeat(5) {
                advance()
                lost += 99 - alice.hitpoints
                topUp(alice)
            }
            assertEquals(0, alice.vars[varbits.protect_from_melee], "he switched it off")
            assertTrue(lost >= 15, "the first hit lands: $lost")
        }

    @Test
    fun GameTestState.`his shield slam hurts a player standing next to him`() =
        runFightTest { deps, alice, run ->
            val fight = toSol(deps, alice, run)
            alice.telejump(fight.npc.coords.translate(-1, 2))
            advance()
            topUp(alice)
            fight.forced = SolAttack.Shield
            fight.nextAttackAt = 0
            var lost = 0
            repeat(5) {
                advance()
                lost += 99 - alice.hitpoints
                topUp(alice)
            }
            assertTrue(lost > 0, "the slam lands on the tile next to him")
        }

    // The chest -----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`taking all from the chest moves the loot into the inventory`() =
        runFightTest { deps, alice, run ->
            startWave(deps, alice, run)
            assertTrue(deps.colosseum.killWave(alice))
            var ticks = 0
            while (run.stage != ColosseumStage.Intermission) {
                check(ticks++ < 30) { "the wave was never cleared" }
                topUp(alice)
                advance()
            }
            deps.colosseum.forfeit(alice)
            assertEquals(ColosseumStage.Finished, run.stage, "alone, the run is over")
            val member = checkNotNull(run.memberOf(alice))
            // The chest's loc is not in the test map's loc lookups: open its screen directly.
            deps.screens.openChest(member)
            alice.ifButton(colosseum_components.chest_take)
            advance()
            assertTrue(member.rewards.isEmpty())
            assertEquals(
                ColosseumLoot.FIRST_WAVE_SPLINTERS,
                alice.count(colosseum_objs.sunfire_splinters),
            )
            assertEquals(
                ColosseumLoot.FIRST_WAVE_SPLINTERS,
                deps.collectionLog.owned(alice, colosseum_objs.sunfire_splinters.id),
            )
        }
}
