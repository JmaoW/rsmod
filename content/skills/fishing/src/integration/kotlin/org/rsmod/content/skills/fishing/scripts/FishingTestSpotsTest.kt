package org.rsmod.content.skills.fishing.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.testing.GameTestState
import org.rsmod.map.zone.ZoneKey

class FishingTestSpotsTest {
    @Test
    fun GameTestState.`spawn one spot of every kind next to the shore`() =
        runGameTest(FishingTestSpots::class) {
            eventBus.publish(GameLifecycle.Startup)

            val spots =
                npcRepo
                    .findAll(ZoneKey.from(FishingTestSpots.ANCHOR), zoneRadius = 3)
                    .filter { it.type.name.endsWith("Fishing spot") }
                    .toList()
            check(spots.size == 7) { "Expected 7 spots, found ${spots.size}." }
            check(spots.any { it.type.internalName == "swan_fishingspot" }) {
                "Expected the monkfish spot."
            }
            check(spots.any { it.type.op.getOrNull(0) == "Big Net" }) {
                "Expected the big net shark spot."
            }
            check(
                spots.none {
                    it.type.op.getOrNull(0) == "Net" &&
                        it.type.op.getOrNull(2) == "Harpoon" &&
                        it.type.internalName != "swan_fishingspot"
                }
            ) {
                "Did not expect a net/harpoon shark spot."
            }
        }
}
