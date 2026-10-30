package org.rsmod.content.skills.fishing.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.testing.GameTestState
import org.rsmod.map.zone.ZoneKey

class FishingSpotSpawnsTest {
    @Test
    fun GameTestState.`every spot spawns on its tile`() =
        runGameTest(FishingSpotSpawns::class) {
            eventBus.publish(GameLifecycle.Startup)

            for (spot in FishingSpotSpawns.SPOTS) {
                val npc =
                    npcRepo.findAll(ZoneKey.from(spot.coords)).firstOrNull {
                        it.coords == spot.coords && it.id == spot.npcId
                    }
                check(npc != null) { "${spot.label} is missing at ${spot.coords}." }
            }
        }

    @Test
    fun GameTestState.`every spot has the options of its fish`() =
        runGameTest(FishingSpotSpawns::class) {
            for (spot in FishingSpotSpawns.SPOTS) {
                val type = npcTypes[spot.npcId]
                check(type != null) { "${spot.label}: no npc type ${spot.npcId}." }
                val ops = listOf(type.op.getOrNull(0), type.op.getOrNull(2))
                val internal = type.internalName
                val ok =
                    when (spot.npcId) {
                        FishingSpotSpawns.SHARK ->
                            ops[0] in listOf("Big Net", "Net") && ops[1] == "Harpoon"
                        FishingSpotSpawns.ANGLERFISH -> internal == "piscariliusfish"
                        FishingSpotSpawns.SALMON -> ops == listOf("Lure", "Bait")
                        FishingSpotSpawns.MONKFISH -> internal == "swan_fishingspot"
                        FishingSpotSpawns.KARAMBWAN -> internal == "0_45_48_karambwan"
                        FishingSpotSpawns.KARAMBWANJI -> internal == "0_43_47_karambwanji"
                        else -> false
                    }
                check(ok) { "${spot.label} (${spot.npcId}) is $internal with options $ops." }
            }
        }
}
