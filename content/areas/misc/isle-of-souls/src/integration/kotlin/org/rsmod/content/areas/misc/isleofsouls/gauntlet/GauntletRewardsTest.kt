package org.rsmod.content.areas.misc.isleofsouls.gauntlet

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import jakarta.inject.Inject
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.api.type.refs.varp.VarpReferences
import org.rsmod.content.areas.misc.isleofsouls.tasks.LeagueTaskTracker
import org.rsmod.content.areas.misc.isleofsouls.teams.LeagueTeams
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * What a run is worth: the reward kinds, the chest's tables and opening it, the statistics, the
 * scoreboard and the league tasks.
 */
object GauntletTestCounterVarps : VarpReferences() {
    val hm50 = find("isle_count_gauntlet_hm_50")
}

class GauntletRewardsTest {
    private object RewardsModule : AbstractModule() {
        override fun configure() {
            bind(LeagueTeams::class.java).`in`(Scopes.SINGLETON)
            bind(Gauntlet::class.java).`in`(Scopes.SINGLETON)
            bind(GauntletScoreboard::class.java).`in`(Scopes.SINGLETON)
        }
    }

    class Deps
    @Inject
    constructor(
        val gauntlet: Gauntlet,
        val teams: LeagueTeams,
        val collision: CollisionFlagMap,
        val rewards: GauntletRewards,
        val scoreboard: GauntletScoreboard,
        val tracker: LeagueTaskTracker,
        val launcher: ProtectedAccessLauncher,
        val objTypes: ObjTypeList,
    )

    private fun Deps.seed() {
        for (offset in listOf(0, 8)) {
            for (chunkX in (232..239)) {
                for (chunkZ in 704..711) {
                    for (level in 0..3) {
                        collision.allocateIfAbsent((chunkX + offset) * 8, chunkZ * 8, level)
                        collision.allocateIfAbsent((chunkX + offset) * 8 + 8, chunkZ * 8 + 8, level)
                    }
                }
            }
        }
        collision.allocateIfAbsent(Gauntlet.LOBBY.x, Gauntlet.LOBBY.z, Gauntlet.LOBBY.level)
    }

    private fun GameTestState.runRewardsTest(
        pick: (Int) -> Int = { 0 },
        body: GameTestScope.(Deps, Player, Player) -> Unit,
    ) =
        runInjectedGameTest(
            Deps::class,
            RewardsModule,
            GauntletScript::class,
            GauntletCombatScript::class,
            GauntletHunllefScript::class,
        ) { deps ->
            deps.seed()
            deps.gauntlet.pick = pick
            deps.scoreboard.file = Files.createTempDirectory("gauntlet").resolve("scoreboard.json")
            val alice = player
            alice.username = "alice"
            val bob = registerPlayer(player = Player().apply { username = "bob" })
            deps.teams.createTeam(1, "Alpha")
            deps.teams.assign("alice", 1)
            deps.teams.assign("bob", 1)
            for (p in listOf(alice, bob)) {
                VarPlayerIntMapSetter.set(p, gauntlet_varps.bryn, 1)
            }
            body(deps, alice, bob)
        }

    // The kinds ---------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a win is a win and anything else goes by the points`() =
        runRewardsTest { deps, _, _ ->
            val rewards = deps.rewards
            assertEquals(RewardKind.Completed, rewards.kindFor(GauntletExit.Completed, 0))
            // The platform gives nothing, whatever was earned.
            assertNull(rewards.kindFor(GauntletExit.Exit, 400))
            for (exit in
                listOf(
                    GauntletExit.Escape,
                    GauntletExit.Died,
                    GauntletExit.Logout,
                    GauntletExit.Left,
                )) {
                assertEquals(RewardKind.Incomplete, rewards.kindFor(exit, 50), "$exit")
                assertEquals(RewardKind.Junk, rewards.kindFor(exit, 49), "$exit")
                assertEquals(RewardKind.Junk, rewards.kindFor(exit, 1), "$exit")
                assertNull(rewards.kindFor(exit, 0), "$exit")
            }
        }

    @Test
    fun GameTestState
        .`leaving a run gives the reward its points earn and blocks re-entry until collected`() =
        runRewardsTest { deps, alice, bob ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            deps.gauntlet.enter(bob, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            run.addPoints(alice, 60)
            run.addPoints(bob, 3)

            deps.gauntlet.leave(alice, GauntletExit.Escape)
            deps.gauntlet.leave(bob, GauntletExit.Exit)

            assertEquals(RewardKind.Incomplete to GauntletMode.Regular, deps.rewards.pending(alice))
            assertEquals(1, alice.vars[gauntlet_varbits.reward_available])
            // The platform gave bob nothing.
            assertNull(deps.rewards.pending(bob))
            assertEquals(0, bob.vars[gauntlet_varbits.reward_available])

            assertEquals(
                GauntletEntry.RewardWaiting,
                deps.gauntlet.enter(alice, GauntletMode.Regular),
            )
            assertEquals(GauntletEntry.Entered, deps.gauntlet.enter(bob, GauntletMode.Regular))
        }

    @Test
    fun GameTestState.`a death keeps the points the player earned`() =
        runRewardsTest { deps, alice, _ ->
            deps.gauntlet.enter(alice, GauntletMode.Regular)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            run.addPoints(alice, 10)
            deps.gauntlet.died(alice)
            assertEquals(RewardKind.Junk to GauntletMode.Regular, deps.rewards.pending(alice))
            assertEquals(1, alice.vars[gauntlet_varps.deaths])
            assertEquals(1, deps.scoreboard.totals(GauntletMode.Regular).deaths)
        }

    @Test
    fun GameTestState.`the chest shows what waits after a login`() =
        runRewardsTest { deps, alice, _ ->
            deps.rewards.award(alice, GauntletMode.Corrupted, RewardKind.Completed)
            VarPlayerIntMapSetter.set(alice, gauntlet_varbits.reward_available, 0)
            deps.rewards.syncChest(alice)
            assertEquals(1, alice.vars[gauntlet_varbits.reward_available])
            assertEquals(
                RewardKind.Completed to GauntletMode.Corrupted,
                deps.rewards.pending(alice),
            )
        }

    // The tables --------------------------------------------------------------------------------

    @Test
    fun GameTestState.`a win gives shards, the right number of rolls and the cape once`() =
        runRewardsTest { deps, alice, _ ->
            val shard = GauntletRewardObjs.prif_crystal_shard.id
            val cape = GauntletRewardObjs.gauntlet_crystalline_cape.id
            val regular =
                deps.rewards.roll(RewardKind.Completed, GauntletMode.Regular, false) { n -> n - 1 }
            // The shards, then two table rolls; no tertiary with a pick that never rolls 0.
            assertEquals(9, regular.first { it.obj.id == shard }.count)
            assertEquals(3, regular.size)
            assertFalse(regular.any { it.obj.id == cape })

            val corrupted =
                deps.rewards.roll(RewardKind.Completed, GauntletMode.Corrupted, false) { n ->
                    n - 1
                }
            assertEquals(12, corrupted.first { it.obj.id == shard }.count)
            assertTrue(corrupted.any { it.obj.id == cape })
            assertEquals(5, corrupted.size)

            val owner =
                deps.rewards.roll(RewardKind.Completed, GauntletMode.Corrupted, true) { n -> n - 1 }
            assertFalse(owner.any { it.obj.id == cape })
        }

    @Test
    fun GameTestState.`every tertiary can come with a win`() = runRewardsTest { deps, _, _ ->
        val items =
            deps.rewards
                .roll(RewardKind.Completed, GauntletMode.Regular, false) { 0 }
                .map { it.obj.id }
        for (obj in
            listOf(
                GauntletRewardObjs.trail_elite_emote_exp1,
                GauntletRewardObjs.crystal_seed_old,
                GauntletRewardObjs.prif_armour_seed,
                GauntletRewardObjs.prif_weapon_seed_enhanced,
                GauntletRewardObjs.gauntletpet,
            )) {
            assertTrue(obj.id in items, "${obj.id}")
        }
        val hm =
            deps.rewards
                .roll(RewardKind.Completed, GauntletMode.Corrupted, false) { 0 }
                .map { it.obj.id }
        // Wiki: the Corrupted Gauntlet gives the ordinary Youngllef too.
        assertTrue(GauntletRewardObjs.gauntletpet.id in hm)
        assertFalse(GauntletRewardObjs.gauntletpet_corrupt.id in hm)
    }

    @Test
    fun GameTestState.`the drop-rate relics make only the three seeds likelier`() =
        runRewardsTest { deps, _, _ ->
            val plain =
                deps.rewards.roll(RewardKind.Completed, GauntletMode.Regular, false) { n -> n - 1 }
            val boosted =
                deps.rewards
                    .roll(RewardKind.Completed, GauntletMode.Regular, false, 100000.0) { n ->
                        n - 1
                    }
                    .map { it.obj.id }
            for (seed in
                listOf(
                    GauntletRewardObjs.crystal_seed_old,
                    GauntletRewardObjs.prif_armour_seed,
                    GauntletRewardObjs.prif_weapon_seed_enhanced,
                )) {
                assertFalse(plain.any { it.obj.id == seed.id })
                assertTrue(seed.id in boosted)
            }
            assertFalse(GauntletRewardObjs.gauntletpet.id in boosted)
            assertFalse(GauntletRewardObjs.trail_elite_emote_exp1.id in boosted)
        }

    @Test
    fun GameTestState.`the other tables give one item and gems and bars are noted`() =
        runRewardsTest { deps, _, _ ->
            val incomplete =
                deps.rewards.roll(RewardKind.Incomplete, GauntletMode.Regular, false) { 0 }
            assertEquals(1, incomplete.size)
            val junk = deps.rewards.roll(RewardKind.Junk, GauntletMode.Regular, false) { 0 }
            assertEquals(listOf(GauntletRewardObjs.flier_prif.id), junk.map { it.obj.id })

            // The last line of the incomplete table is three uncut sapphires, as a note.
            val last =
                deps.rewards
                    .roll(RewardKind.Incomplete, GauntletMode.Regular, false) { n -> n - 1 }
                    .single()
            assertTrue(deps.objTypes[last.obj].isCert)
            assertEquals(GauntletRewardObjs.uncut_sapphire.id, deps.objTypes[last.obj].certlink)
        }

    @Test
    fun GameTestState.`the tables have the wiki's shapes`() = runRewardsTest { _, _, _ ->
        assertEquals(24, GauntletRewardTables.crystalline.size)
        assertEquals(24, GauntletRewardTables.corrupted.size)
        assertEquals(27, GauntletRewardTables.incomplete.size)
        assertEquals(3, GauntletRewardTables.junk.size)
        // Halves of x/24: the regular table is 24 lines of weight 2 (48), the corrupted one 49.
        assertEquals(48, GauntletRewardTables.crystalline.sumOf { it.weight })
        assertEquals(48, GauntletRewardTables.corrupted.sumOf { it.weight })
    }

    // The chest ---------------------------------------------------------------------------------

    @Test
    fun GameTestState.`opening the chest gives the items and empties it`() =
        runRewardsTest { deps, alice, _ ->
            deps.rewards.award(alice, GauntletMode.Regular, RewardKind.Completed)
            var given: List<RewardItem>? = null
            with(deps.rewards) {
                deps.launcher.launch(alice) { given = open(false) { n -> n - 1 } }
            }
            advance()

            assertEquals(3, given?.size)
            assertNull(deps.rewards.pending(alice))
            assertEquals(0, alice.vars[gauntlet_varbits.reward_available])
            assertTrue(alice.inv.filterNotNull().size >= 2)
            // Nothing is left to collect.
            var again: List<RewardItem>? = emptyList()
            with(deps.rewards) { deps.launcher.launch(alice) { again = open(false) { 0 } } }
            advance()
            assertNull(again)
        }

    @Test
    fun GameTestState.`a full pack keeps the reward in the chest`() =
        runRewardsTest { deps, alice, _ ->
            deps.rewards.award(alice, GauntletMode.Regular, RewardKind.Completed)
            repeat(28) { alice.invAdd(alice.inv, GauntletShared.burntFood, 1) }
            alice.inv.fillNulls()
            for (slot in 0 until 28) {
                alice.invAdd(alice.inv, GauntletRegularItems.axe, 1)
            }
            var given: List<RewardItem>? = emptyList()
            with(deps.rewards) { deps.launcher.launch(alice) { given = open(false) { 0 } } }
            advance()
            assertNull(given)
            assertEquals(RewardKind.Completed to GauntletMode.Regular, deps.rewards.pending(alice))
        }

    // Winning -----------------------------------------------------------------------------------

    @Test
    fun GameTestState.`winning counts for the whole team once and records the time`() =
        runRewardsTest { deps, alice, bob ->
            VarPlayerIntMapSetter.set(alice, gauntlet_varps.done, 1)
            VarPlayerIntMapSetter.set(bob, gauntlet_varps.done, 1)
            deps.gauntlet.enter(alice, GauntletMode.Corrupted)
            deps.gauntlet.enter(bob, GauntletMode.Corrupted)
            val run = checkNotNull(deps.gauntlet.runOf(alice))
            deps.gauntlet.startBossPhase(run, forced = false)
            advance(40)

            deps.gauntlet.hunllefDied(run)

            for (p in listOf(alice, bob)) {
                assertEquals(1, p.vars[gauntlet_varps.done_hm])
                assertEquals(1, p.vars[gauntlet_varps.kc_hm])
                assertTrue(
                    p.vars[gauntlet_varps.best_hm] >= 40,
                    "${p.vars[gauntlet_varps.best_hm]}",
                )
                assertTrue(deps.tracker.isComplete(p, Gauntlet.TASK_COMPLETE_CORRUPTED))
            }
            // The team's counters ran once for the pair.
            assertEquals(1, alice.vars[GauntletTestCounterVarps.hm50])
            assertEquals(1, deps.scoreboard.totals(GauntletMode.Corrupted).completions)

            advance(Gauntlet.COMPLETE_DELAY + 2)
            assertEquals(
                RewardKind.Completed to GauntletMode.Corrupted,
                deps.rewards.pending(alice),
            )
            assertEquals(RewardKind.Completed to GauntletMode.Corrupted, deps.rewards.pending(bob))
        }

    // The scoreboard ----------------------------------------------------------------------------

    @Test
    fun GameTestState.`the world's totals are saved and read back`() =
        runRewardsTest { deps, _, _ ->
            val file = checkNotNull(deps.scoreboard.file)
            deps.scoreboard.completion(GauntletMode.Regular, 3000)
            deps.scoreboard.completion(GauntletMode.Regular, 2500)
            deps.scoreboard.death(GauntletMode.Corrupted)
            assertEquals(2, deps.scoreboard.totals(GauntletMode.Regular).completions)
            assertEquals(2500, deps.scoreboard.totals(GauntletMode.Regular).bestTicks)
            assertTrue(Files.exists(file))

            // A fresh read of the same file.
            deps.scoreboard.file = file
            assertEquals(2, deps.scoreboard.totals(GauntletMode.Regular).completions)
            assertEquals(1, deps.scoreboard.totals(GauntletMode.Corrupted).deaths)
            assertEquals(2500, deps.scoreboard.totals(GauntletMode.Regular).bestTicks)
        }

    @Test
    fun GameTestState.`times are shown as minutes seconds and hundredths`() =
        runRewardsTest { _, _, _ ->
            assertEquals("-", GauntletScoreboard.time(0))
            assertEquals("0:00.60", GauntletScoreboard.time(1))
            // 1000 ticks is ten minutes.
            assertEquals("10:00.00", GauntletScoreboard.time(1000))
            assertEquals("12:34.20", GauntletScoreboard.time(1257))
        }
}
