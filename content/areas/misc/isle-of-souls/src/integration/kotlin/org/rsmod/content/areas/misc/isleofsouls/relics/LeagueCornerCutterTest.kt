package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.configs.isle_objs
import org.rsmod.content.skills.agility.scripts.Agility
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.Wearpos

class LeagueCornerCutterTest {
    private fun GameTestScope.choose() {
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[1], 1)
    }

    private fun GameTestScope.modifiers(): Agility.Modifiers {
        val mods = Agility.Modifiers(player, null, null)
        eventBus.publish(mods)
        return mods
    }

    private fun GameTestScope.wearGreaves() {
        player.worn[Wearpos.Feet.slot] = InvObj(isle_objs.sages_greaves)
    }

    private fun GameTestScope.agilityXp(): Double = player.statMap.getFineXP(stats.agility) / 10.0

    /**
     * Moves the player [tiles] tiles a tick for [ticks] ticks from a fresh timer, and returns the
     * XP.
     */
    private fun GameTestScope.run(ticks: Int, tiles: Int = 2): Double {
        player.stats[stats.agility] = 60
        player.statMap.setXP(stats.agility, 0)
        player.softTimer(CornerCutterTimers.run_xp, 1)
        repeat(ticks) {
            player.placeAt(player.coords.translateX(tiles))
            advance()
        }
        return agilityXp()
    }

    @Test
    fun GameTestState.`without the relic the lap modifiers are the plain ones`() =
        runGameTest(Agility::class, LeagueCornerCutter::class) {
            val mods = modifiers()
            assertEquals(1, mods.completions)
            assertEquals(0, mods.lapBonusPercent)
            assertEquals(0, mods.markCoins)
            assertEquals(false, mods.guaranteed)
        }

    @Test
    fun GameTestState
        .`Corner Cutter always succeeds, doubles laps, adds a quarter and sends coins with marks`() =
        runGameTest(Agility::class, LeagueCornerCutter::class) {
            choose()
            val mods = modifiers()
            assertEquals(2, mods.completions)
            assertEquals(25, mods.lapBonusPercent)
            assertEquals(10_000, mods.markCoins)
            assertEquals(true, mods.guaranteed)
        }

    @Test
    fun GameTestState.`running in the greaves pays ten plus two thirds of the level`() =
        runGameTest(LeagueCornerCutter::class) {
            choose()
            wearGreaves()
            // The timer sees the start tile on its first tick, then two tiles of running a tick.
            val xp = run(ticks = 5)
            assertEquals(50.0, xp, 0.11) // 10 + floor(60 * 2 / 3)
        }

    @Test
    fun GameTestState.`the greaves pay about every ten ticks`() =
        runGameTest(LeagueCornerCutter::class) {
            choose()
            wearGreaves()
            val xp = run(ticks = 24)
            assertTrue(xp in 100.0..150.0, "Expected two or three payments, got $xp.")
        }

    @Test
    fun GameTestState.`walking in the greaves pays nothing`() =
        runGameTest(LeagueCornerCutter::class) {
            choose()
            wearGreaves()
            assertEquals(0.0, run(ticks = 12, tiles = 1), 0.01)
        }

    @Test
    fun GameTestState.`running without the greaves pays nothing`() =
        runGameTest(LeagueCornerCutter::class) {
            choose()
            assertEquals(0.0, run(ticks = 12), 0.01)
        }

    @Test
    fun GameTestState.`running in the greaves without the relic pays nothing`() =
        runGameTest(LeagueCornerCutter::class) {
            wearGreaves()
            assertEquals(0.0, run(ticks = 12), 0.01)
        }
}
