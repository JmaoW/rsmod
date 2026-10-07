package org.rsmod.api.player.hit.processor

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.config.refs.varbits
import org.rsmod.api.hit.plugin.PlayerHitScript
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.factory.npcTypeFactory
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid

class StandardPlayerHitProcessorTest {
    /**
     * Melee-based hits undergo special validation during processing (when the hitsplat is
     * displayed) to ensure that the hit's source is still alive.
     *
     * Currently, we have only confirmed this behavior for hits originating from an
     * [org.rsmod.game.entity.Npc]. If future evidence shows that the same applies to players, a
     * separate test should be added.
     */
    @Test
    fun GameTestState.`invalidate melee hits when npc source is dead`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Ensure hits are actually going through for test validity.
            advance(ticks = 1)
            check(player.hitpoints == 98) { "Unexpected player hitpoints: ${player.hitpoints}" }

            // Reset state and queue a new hit.
            player.stats[stats.hitpoints] = 99
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 1)

            // Set npc to 0 hitpoints, mimicking its death.
            npc.hitpoints = 0

            // Hit should have been invalidated due to npc being "dead."
            advance(ticks = 1)
            assertEquals(99, player.hitpoints)
        }

    @Test
    fun GameTestState.`redemption heals and drains prayer when hitpoints fall below a tenth`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99
            player.stats.setCurrentLevel(stats.hitpoints, 20)
            player.stats[stats.prayer] = 99
            player.setVarBit(varbits.redemption, 1)

            // 20 - 15 leaves 5 hitpoints, which is below a tenth of 99.
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 15)
            advance(ticks = 1)

            // Healed by a quarter of the Prayer level (99 / 4 = 24).
            check(player.hitpoints == 29) { "Unexpected player hitpoints: ${player.hitpoints}" }
            check(player.prayerLvl == 0) { "Unexpected prayer level: ${player.prayerLvl}" }
        }

    @Test
    fun GameTestState.`redemption does not trigger at a tenth of hitpoints or above`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99
            player.stats.setCurrentLevel(stats.hitpoints, 30)
            player.stats[stats.prayer] = 99
            player.setVarBit(varbits.redemption, 1)

            // 30 - 20 leaves 10 hitpoints, which is just over a tenth of 99.
            player.queueHit(npc, delay = 1, HitType.Melee, damage = 20)
            advance(ticks = 1)

            check(player.hitpoints == 10) { "Unexpected player hitpoints: ${player.hitpoints}" }
            check(player.prayerLvl == 99) { "Unexpected prayer level: ${player.prayerLvl}" }
        }

    @Test
    fun GameTestState.`redemption does nothing when it is not switched on`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99
            player.stats.setCurrentLevel(stats.hitpoints, 20)
            player.stats[stats.prayer] = 99

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 15)
            advance(ticks = 1)

            check(player.hitpoints == 5) { "Unexpected player hitpoints: ${player.hitpoints}" }
            check(player.prayerLvl == 99) { "Unexpected prayer level: ${player.prayerLvl}" }
        }

    @Test
    fun GameTestState.`redemption does not save a player from a fatal hit`() =
        runGameTest(PlayerHitScript::class) {
            val npc = spawnNpc(CoordGrid(0, 0, 50, 50, 0), npcTypeFactory.create())
            player.stats[stats.hitpoints] = 99
            player.stats.setCurrentLevel(stats.hitpoints, 10)
            player.stats[stats.prayer] = 99
            player.setVarBit(varbits.redemption, 1)

            player.queueHit(npc, delay = 1, HitType.Melee, damage = 10)
            advance(ticks = 1)

            check(player.hitpoints == 0) { "Unexpected player hitpoints: ${player.hitpoints}" }
            check(player.prayerLvl == 99) { "Unexpected prayer level: ${player.prayerLvl}" }
        }
}
