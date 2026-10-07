package org.rsmod.content.skills.cooking.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class CookingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.placeRange(): BoundLocInfo {
        val type = locTypes.values.first { it.name == "Range" && it.op.getOrNull(0) == "Cook" }
        val range = placeMapLoc(coords, type)
        player.teleport(range.coords.translateX(-1))
        return range
    }

    @Test
    fun GameTestState.`cook shrimp on a range`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("raw_shrimp"), 1)
            player.stats[stats.cooking] = 1

            random.next = 0 // Guarantee the cook succeeds.
            player.opLoc1(range)
            advanceUntil({ player.count(obj("shrimp")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You successfully cook some shrimps.")
            check(player.count(obj("shrimp")) == 1) { "Expected cooked shrimp in inventory." }
            check(player.count(obj("raw_shrimp")) == 0) { "Expected raw shrimp to be used." }
        }

    @Test
    fun GameTestState.`burn shrimp on a range`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("raw_shrimp"), 1)
            player.stats[stats.cooking] = 1

            random.next = 100 // Guarantee the cook fails.
            player.opLoc1(range)
            advanceUntil({ player.count(obj("burnt_shrimp")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You accidentally burn the shrimps.")
            check(player.count(obj("burnt_shrimp")) == 1) { "Expected burnt shrimp in inventory." }
            check(player.count(obj("shrimp")) == 0) { "Expected no cooked shrimp." }
        }

    @Test
    fun GameTestState.`cook every raw item of the same kind`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("raw_shrimp"))
            player.inv[1] = InvObj(obj("raw_shrimp"))
            player.inv[2] = InvObj(obj("raw_shrimp"))
            player.stats[stats.cooking] = 99 // Never burns.

            random.next = 0
            random.next = 0
            random.next = 0
            player.opLoc1(range)
            advanceUntil({ player.count(obj("shrimp")) >= 3 }, timeoutTicks = 40)

            check(player.count(obj("shrimp")) == 3) { "Expected all three shrimp to be cooked." }
        }

    @Test
    fun GameTestState.`lobster needs level 40 to cook`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("raw_lobster"))
            player.stats[stats.cooking] = 39

            player.opLoc1(range)
            advance(ticks = 3)

            assertMessageSent("You need a Cooking level of 40 to cook this.")
            check(player.count(obj("raw_lobster")) == 1) { "Raw lobster should be untouched." }
        }

    @Test
    fun GameTestState.`nothing to cook`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()

            player.opLoc1(range)
            advance(ticks = 3)

            assertMessageSent("You have nothing to cook.")
        }

    @Test
    fun GameTestState.`walking away stops cooking and never holds the player`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            for (slot in 0 until 3) {
                player.inv[slot] = InvObj(obj("raw_shrimp"))
            }
            player.stats[stats.cooking] = 99

            random.next = 0
            player.opLoc1(range)
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while cooking." }

            // Walking away clears the pending action, which ends the cooking loop.
            player.clearPendingAction(eventBus)
            advance(ticks = 15)

            check(player.count(obj("shrimp")) == 0) { "Expected nothing to be cooked." }
            check(player.count(obj("raw_shrimp")) == 3) { "Expected the raw shrimp to be kept." }
        }
}
