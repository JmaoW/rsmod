package org.rsmod.content.areas.misc.isleofsouls.relics

import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.player.PlayerRunUpdateProcessor
import org.rsmod.api.testing.GameTestState
import org.rsmod.game.entity.Player
import org.rsmod.game.type.obj.ObjTypeList

class RunDrainTest {
    class Deps @Inject constructor(val objTypes: ObjTypeList)

    private val relics = LeagueRelicTracker()

    private fun Player.run(processor: PlayerRunUpdateProcessor): Int {
        runEnergy = 10_000
        pendingStepCount = 2
        processor.process(this)
        return runEnergy
    }

    @Test
    fun GameTestState.`a tier 1 relic stops run energy draining`() =
        runInjectedGameTest(Deps::class) { deps ->
            val processor =
                PlayerRunUpdateProcessor(deps.objTypes, setOf(LeagueRunDrainMod(relics)))

            assertTrue(player.run(processor) < 10_000, "running drains without a relic")

            relics.view(player, 0)
            relics.confirm(player)
            assertEquals(1, relics.selected(player, 0))
            assertEquals(10_000, player.run(processor), "running no longer drains")
        }
}
