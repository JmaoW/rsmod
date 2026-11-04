package org.rsmod.content.areas.misc.isleofsouls.relics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.varps
import org.rsmod.api.death.NpcDeathEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.specials.SpecialAccuracy
import org.rsmod.api.specials.energy.SpecialAttackEnergy
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.map.CoordGrid

class LeagueSpecialistTest {
    private val energy = SpecialAttackEnergy()

    private fun GameTestScope.prepare(chosen: Boolean) {
        val option = if (chosen) LeagueSpecialist.OPTION else 0
        VarPlayerIntMapSetter.set(player, RelicVarBits.selection[LeagueSpecialist.TIER], option)
        VarPlayerIntMapSetter.set(player, varps.sa_energy, 0)
    }

    @Test
    fun GameTestState.`every special attack costs 20 percent`() =
        runGameTest(LeagueSpecialist::class) {
            prepare(chosen = true)
            assertEquals(200, energy.costFor(player, 500))
            assertEquals(200, energy.costFor(player, 1000))
            assertEquals(200, energy.costFor(player, 250))
        }

    @Test
    fun GameTestState.`without the relic costs and accuracy are unchanged`() =
        runGameTest(LeagueSpecialist::class) {
            prepare(chosen = false)
            assertEquals(500, energy.costFor(player, 500))
            assertEquals(1.0, SpecialAccuracy.rule?.multiplier(player))
            SpecialAccuracy.rule?.missed(player)
            assertEquals(0, player.vars[varps.sa_energy])
        }

    @Test
    fun GameTestState.`accuracy is doubled and a miss gives back 10 percent`() =
        runGameTest(LeagueSpecialist::class) {
            prepare(chosen = true)
            assertEquals(2.0, SpecialAccuracy.rule?.multiplier(player))
            SpecialAccuracy.rule?.missed(player)
            assertEquals(100, player.vars[varps.sa_energy])
        }

    @Test
    fun GameTestState.`a kill restores 15 percent`() =
        runGameTest(LeagueSpecialist::class) {
            prepare(chosen = true)
            val npc = spawnNpc(CoordGrid(0, 50, 50, 10, 10), npcTypeFactory.create())
            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            assertEquals(150, player.vars[varps.sa_energy])

            VarPlayerIntMapSetter.set(player, varps.sa_energy, 950)
            eventBus.publish(NpcDeathEvents.Killed(npc, player))
            assertEquals(1000, player.vars[varps.sa_energy], "capped at 100 percent")
        }
}
