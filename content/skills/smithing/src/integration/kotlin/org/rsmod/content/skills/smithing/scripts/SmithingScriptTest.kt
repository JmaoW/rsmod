package org.rsmod.content.skills.smithing.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.smithing.configs.SmithingComponents
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class SmithingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.placeAnvil(): BoundLocInfo {
        val type = locTypes.values.first { Smithing.isAnvil(it) }
        val anvil = placeMapLoc(coords, type)
        player.teleport(anvil.coords.translateX(-1))
        return anvil
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 10) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    @Test
    fun GameTestState.`smith a bronze dagger`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            player.inv[1] = InvObj(obj("bronze_bar"))
            player.stats[stats.smithing] = 1

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[0]) // Dagger.
            advanceUntil({ player.count(obj("bronze_dagger")) >= 1 }, timeoutTicks = 30)

            assertMessageSent("You hammer the bronze and make a dagger.")
            check(player.count(obj("bronze_bar")) == 0) { "Expected the bar to be used." }
        }

    @Test
    fun GameTestState.`smith until the bars run out`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            for (slot in 1..5) {
                player.inv[slot] = InvObj(obj("bronze_bar"))
            }
            player.stats[stats.smithing] = 1

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[5]) // Axe, one bar each.
            advanceUntil({ player.count(obj("bronze_axe")) >= 5 }, timeoutTicks = 60)

            check(player.count(obj("bronze_bar")) == 0) { "Expected every bar to be used." }
        }

    @Test
    fun GameTestState.`smith stackable nails`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            player.inv[1] = InvObj(obj("bronze_bar"))
            player.stats[stats.smithing] = 4

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[14]) // Nails.
            advanceUntil({ player.count(obj("nails_bronze")) >= 15 }, timeoutTicks = 30)

            assertMessageSent("You hammer the bronze and make 15 nails.")
        }

    @Test
    fun GameTestState.`smith an item that costs five bars`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            for (slot in 1..5) {
                player.inv[slot] = InvObj(obj("bronze_bar"))
            }
            player.stats[stats.smithing] = 18

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[13]) // Plate body, five bars.
            advanceUntil({ player.count(obj("bronze_platebody")) >= 1 }, timeoutTicks = 30)

            check(player.count(obj("bronze_bar")) == 0) { "Expected all five bars to be used." }
        }

    @Test
    fun GameTestState.`anvil needs a hammer`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("bronze_bar"))

            player.opLoc1(anvil)

            advanceUntilMessage("You need a hammer to work the metal with.")
        }

    @Test
    fun GameTestState.`anvil needs bars`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))

            player.opLoc1(anvil)

            advanceUntilMessage("You do not have any bars to work with.")
        }

    @Test
    fun GameTestState.`item needs the level`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            player.inv[1] = InvObj(obj("bronze_bar"))
            player.stats[stats.smithing] = 1

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[1]) // Sword, level 4.

            advanceUntilMessage("You need a Smithing level of 4 to make this.")
            check(player.count(obj("bronze_bar")) == 1) { "The bar should be untouched." }
        }

    @Test
    fun GameTestState.`item needs enough bars`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            player.inv[1] = InvObj(obj("bronze_bar"))
            player.stats[stats.smithing] = 18

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[13]) // Plate body, five bars.

            advanceUntilMessage("You do not have enough bronze bars to make this.")
        }

    @Test
    fun GameTestState.`a plain click shows the best metal`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            player.inv[1] = InvObj(obj("bronze_bar"))
            player.inv[2] = InvObj(obj("iron_bar"))
            player.stats[stats.smithing] = 15

            // The iron dagger is on the first button because iron is the better metal.
            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[0])
            advanceUntil({ player.count(obj("iron_dagger")) >= 1 }, timeoutTicks = 30)

            check(player.count(obj("bronze_bar")) == 1) { "Bronze bar should be untouched." }
        }

    @Test
    fun GameTestState.`walking away stops smithing and never holds the player`() =
        runGameTest(Smithing::class) {
            val anvil = placeAnvil()
            player.clearInv()
            player.inv[0] = InvObj(obj("hammer"))
            for (slot in 1..5) {
                player.inv[slot] = InvObj(obj("bronze_bar"))
            }
            player.stats[stats.smithing] = 1

            player.opLoc1(anvil)
            advance(ticks = 3)
            player.ifButton(SmithingComponents.slots[5]) // Axe, one bar each.
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while smithing." }

            player.clearPendingAction(eventBus)
            advance(ticks = 15)

            check(player.count(obj("bronze_axe")) == 0) { "Expected nothing to be smithed." }
            check(player.count(obj("bronze_bar")) == 5) { "Expected the bars to be kept." }
        }
}
