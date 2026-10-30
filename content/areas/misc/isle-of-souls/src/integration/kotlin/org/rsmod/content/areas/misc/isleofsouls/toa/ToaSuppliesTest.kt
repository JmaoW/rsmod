package org.rsmod.content.areas.misc.isleofsouls.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope

/** The helpful spirit, the supplies bag and the raid's own supplies. */
class ToaSuppliesTest {
    private fun GameTestState.runNexus(body: GameTestScope.(ToaTestRun, ToaSupplies) -> Unit) =
        runRoomTest(ToaRoom.Nexus, ToaSuppliesScript::class, floor = null) { run ->
            body(run, run.deps.supplies)
        }

    private fun GameTestScope.clearTwoPaths(run: ToaTestRun) {
        for (path in listOf(ToaPath.Het, ToaPath.Apmeken)) {
            run.deps.raids.completePath(run.raid, path)
            run.deps.supplies.pathCompleted(run.raid, 0)
        }
    }

    @Test
    fun GameTestState.`the spirit comes after two paths with a claim for everyone`() =
        runNexus { run, supplies ->
            assertFalse(checkNotNull(run.raid.member(run.alice)).canClaimSupplies)
            clearTwoPaths(run)
            assertTrue(checkNotNull(run.raid.member(run.alice)).canClaimSupplies)
        }

    @Test
    fun GameTestState.`choosing a bundle fills the bag once`() = runNexus { run, supplies ->
        clearTwoPaths(run)
        supplies.claim(run.raid, run.alice)
        supplies.choose(run.raid, run.alice, 2)
        assertTrue(ToaSupplyObjs.bag in run.alice.inv)
        val bag = supplies.bag(run.alice)
        assertEquals(3, bag.objs.count { it != null }, "two salts and an adrenaline")
        assertFalse(checkNotNull(run.raid.member(run.alice)).canClaimSupplies)
        supplies.choose(run.raid, run.alice, 0)
        assertEquals(3, bag.objs.count { it != null }, "one claim each visit")
    }

    @Test
    fun GameTestState.`the help invocations shrink the bundles but keep one of each`() =
        runNexus { run, supplies ->
            val settings = run.raid.settings.copy()
            val noHelp = checkNotNull(settings.byName(Inv.NO_HELP_NEEDED))
            settings.toggle(noHelp)
            val bundles = supplies.rollBundles(settings)
            val power = bundles[2]
            assertEquals(listOf(1, 1), power.map { it.count })
            assertEquals(5, supplies.rollBundles(run.raid.settings)[0][0].count, "five nectar")
        }

    @Test
    fun GameTestState.`withdrawing the last supply takes the bag away`() =
        runNexus { run, supplies ->
            clearTwoPaths(run)
            supplies.choose(run.raid, run.alice, 2)
            supplies.withdraw(run.alice, slot = null, count = 28)
            assertFalse(ToaSupplyObjs.bag in run.alice.inv)
            assertEquals(2, run.alice.inv.count { it?.id == ToaSupplyObjs.salts[0].id })
            assertTrue(ToaSupplyObjs.adrenaline[0] in run.alice.inv)
        }

    @Test
    fun GameTestState.`resupply tops up a part used supply from the bag`() =
        runNexus { run, supplies ->
            clearTwoPaths(run)
            supplies.choose(run.raid, run.alice, 2)
            run.alice.invAdd(run.alice.inv, ToaSupplyObjs.salts[1], 1)
            supplies.resupply(run.alice)
            assertTrue(ToaSupplyObjs.salts[0] in run.alice.inv)
            assertFalse(ToaSupplyObjs.salts[1] in run.alice.inv)
        }

    @Test
    fun GameTestState.`smelling salts boost and wear off after eight minutes`() =
        runNexus { run, supplies ->
            run.alice.stats[stats.attack] = 99
            supplies.salts(run.alice)
            assertEquals(99 + 99 * 16 / 100 + 11, run.alice.stat(stats.attack))
            repeat(ToaSupplies.SALTS_TICKS) { supplies.tick(run.alice, it) }
            assertEquals(run.alice.statBase(stats.attack), run.alice.stat(stats.attack))
        }

    @Test
    fun GameTestState.`tears restore prayer to the drinker and to an ally beside them`() =
        runRoomTest(ToaRoom.Nexus, ToaSuppliesScript::class, twoPlayers = true, floor = null) { run
            ->
            for (player in listOf(run.alice, run.bob)) {
                player.stats[stats.prayer] = 99
                player.statSub(stats.prayer, 60, 0)
            }
            run.deps.raids.raidOf(run.bob)
            run.bob.telejumpTo(run, run.alice)
            run.deps.supplies.tears(run.raid, run.alice)
            assertEquals(39 + 99 / 4 + 10, run.alice.stat(stats.prayer))
            assertEquals(39 + 99 / 10 + 10, run.bob.stat(stats.prayer))
        }

    @Test
    fun GameTestState.`raid supplies are taken away on leaving`() = runNexus { run, supplies ->
        run.alice.invAdd(run.alice.inv, ToaSupplyObjs.nectar[0], 2)
        run.alice.invAdd(run.alice.inv, ToaSupplyObjs.locust, 3)
        run.deps.raids.leave(run.alice, ToaExit.Abandoned)
        supplies.strip(run.alice)
        assertFalse(ToaSupplyObjs.nectar[0] in run.alice.inv)
        assertFalse(ToaSupplyObjs.locust in run.alice.inv)
    }

    @Test
    fun GameTestState.`overly draining makes specials cost everything and adrenaline halves it`() =
        runNexus { run, supplies ->
            ToaSpecialCosts.overlyDraining[run.alice] = true
            assertEquals(1000, ToaSpecialCosts.cost(run.alice, 250))
            ToaSpecialCosts.clock = { 0 }
            supplies.adrenaline(run.alice, 0)
            assertEquals(500, ToaSpecialCosts.cost(run.alice, 250))
            ToaSpecialCosts.overlyDraining.remove(run.alice)
            assertEquals(125, ToaSpecialCosts.cost(run.alice, 250))
            ToaSpecialCosts.adrenalineUntil.remove(run.alice)
        }

    private fun org.rsmod.game.entity.Player.telejumpTo(
        run: ToaTestRun,
        other: org.rsmod.game.entity.Player,
    ) {
        org.rsmod.game.entity.util.PathingEntityCommon.telejump(
            this,
            run.deps.collision,
            other.coords.translate(1, 0),
        )
    }
}
