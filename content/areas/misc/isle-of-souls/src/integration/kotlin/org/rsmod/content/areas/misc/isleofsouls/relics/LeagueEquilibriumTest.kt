package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.areas.misc.isleofsouls.tasks.leagueStats
import org.rsmod.game.type.stat.StatType

class LeagueEquilibriumTest {
    private val equilibrium = LeagueEquilibrium(LeagueRelicTracker())

    @AfterEach
    fun removeHook() {
        PlayerSkillXP.bonus = null
    }

    /** Every skill at 50 except Attack at 40, so the total is 1,140 and Attack has the least xp. */
    private fun GameTestScope.prepare() {
        PlayerSkillXP.bonus = equilibrium
        for (stat in leagueStats) {
            player.stats[stat] = 50
        }
        player.stats[stats.attack] = 40
    }

    private fun GameTestScope.gain(stat: StatType, xp: Double): Double {
        val before = player.statMap.getFineXP(stat)
        player.statAdvance(stat, xp, rate = 1.0, globalRate = 1.0)
        return (player.statMap.getFineXP(stat) - before) / 10.0
    }

    @Test
    fun GameTestState.`without the relic xp is unchanged`() = runGameTest {
        prepare()
        assertEquals(100.0, gain(stats.woodcutting, 100.0))
    }

    @Test
    fun GameTestState.`each gain adds 10 percent of the total level`() = runGameTest {
        prepare()
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueEquilibrium.TIER], 3)
        assertEquals(214.0, gain(stats.woodcutting, 100.0))
        assertEquals(115.0, gain(stats.mining, 1.0), "a tiny gain still gets the full bonus")
    }

    @Test
    fun GameTestState.`the skill with the least xp gets 20 percent`() = runGameTest {
        prepare()
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueEquilibrium.TIER], 3)
        assertEquals(328.0, gain(stats.attack, 100.0))
    }

    @Test
    fun GameTestState.`the bonus is not multiplied by the xp rate`() = runGameTest {
        prepare()
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueEquilibrium.TIER], 3)
        val before = player.statMap.getFineXP(stats.fishing)
        player.statAdvance(stats.fishing, 10.0, rate = 5.0, globalRate = 2.0)
        val gained = (player.statMap.getFineXP(stats.fishing) - before) / 10.0
        assertEquals(100.0 + 114.0, gained)
    }

    @Test
    fun GameTestState.`another tier 4 relic gives nothing`() = runGameTest {
        prepare()
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueEquilibrium.TIER], 1)
        assertEquals(100.0, gain(stats.woodcutting, 100.0))
    }
}
