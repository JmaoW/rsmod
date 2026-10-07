package org.rsmod.content.skills.smithing.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.protect.clearPendingAction
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class SmeltingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.placeFurnace(): BoundLocInfo {
        val type = locTypes.values.first { Smelting.isFurnace(it) }
        val furnace = placeMapLoc(coords, type)
        player.teleport(furnace.coords.translateX(-1))
        return furnace
    }

    private fun GameTestScope.useFurnace(furnace: BoundLocInfo) {
        val type = checkNotNull(locTypes[furnace.id])
        when (type.op.indexOf("Smelt")) {
            0 -> player.opLoc1(furnace)
            1 -> player.opLoc2(furnace)
            else -> player.opLoc3(furnace)
        }
    }

    /** Waits for the smelting menu, then clicks its nth item with the given quantity. */
    private fun GameTestScope.choose(index: Int, quantity: Int) {
        advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
        player.resumePauseButton(components.skillmulti_buttons[index], quantity)
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
    fun GameTestState.`every ore auto-smelts into one bar`() =
        runGameTest(Smelting::class) {
            val expected =
                listOf(
                    "copper_ore" to "bronze_bar",
                    "tin_ore" to "bronze_bar",
                    "iron_ore" to "iron_bar",
                    "coal" to "steel_bar",
                    "silver_ore" to "silver_bar",
                    "gold_ore" to "gold_bar",
                    "mithril_ore" to "mithril_bar",
                    "adamantite_ore" to "adamantite_bar",
                    "runite_ore" to "runite_bar",
                )
            for ((ore, bar) in expected) {
                val smelt = Smelting.autoSmelt(obj(ore))
                check(smelt != null && smelt.bar.id == obj(bar).id) { "Wrong bar for $ore." }
                check(smelt.xp > 0) { "Expected experience for $ore." }
            }
            check(Smelting.autoSmelt(obj("hammer")) == null) { "A hammer is not an ore." }
        }

    @Test
    fun GameTestState.`smelt a bronze bar`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            player.inv[0] = InvObj(obj("copper_ore"))
            player.inv[1] = InvObj(obj("tin_ore"))
            player.stats[stats.smithing] = 1

            useFurnace(furnace)
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("bronze_bar")) >= 1 }, timeoutTicks = 30)

            assertMessageSent("You retrieve a bar of bronze.")
            check(player.count(obj("copper_ore")) == 0) { "Expected the copper ore to be used." }
            check(player.count(obj("tin_ore")) == 0) { "Expected the tin ore to be used." }
        }

    @Test
    fun GameTestState.`smelt as many bars as asked for`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            for (slot in 0 until 3) {
                player.inv[slot] = InvObj(obj("copper_ore"))
                player.inv[slot + 3] = InvObj(obj("tin_ore"))
            }
            player.stats[stats.smithing] = 1

            useFurnace(furnace)
            choose(index = 0, quantity = 2)
            advanceUntil({ player.count(obj("bronze_bar")) >= 2 }, timeoutTicks = 60)
            advance(ticks = 10)

            check(player.count(obj("bronze_bar")) == 2) { "Expected exactly two bars." }
            check(player.count(obj("copper_ore")) == 1) { "Expected one copper ore left." }
        }

    @Test
    fun GameTestState.`all smelts every bar the ore allows`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            for (slot in 0 until 3) {
                player.inv[slot] = InvObj(obj("copper_ore"))
                player.inv[slot + 3] = InvObj(obj("tin_ore"))
            }
            player.stats[stats.smithing] = 1

            useFurnace(furnace)
            choose(index = 0, quantity = 28)
            advanceUntil({ player.count(obj("bronze_bar")) >= 3 }, timeoutTicks = 60)

            check(player.count(obj("bronze_bar")) == 3) { "Expected three bars." }
            check(player.count(obj("copper_ore")) == 0) { "Expected no copper ore left." }
        }

    @Test
    fun GameTestState.`steel takes two coal`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            player.inv[0] = InvObj(obj("iron_ore"))
            player.inv[1] = InvObj(obj("coal"))
            player.inv[2] = InvObj(obj("coal"))
            player.stats[stats.smithing] = 30

            useFurnace(furnace)
            choose(index = 3, quantity = 1)
            advanceUntil({ player.count(obj("steel_bar")) >= 1 }, timeoutTicks = 30)

            check(player.count(obj("coal")) == 0) { "Expected both coal to be used." }
        }

    @Test
    fun GameTestState.`iron ore is lost when smelting fails`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            player.inv[0] = InvObj(obj("iron_ore"))
            player.stats[stats.smithing] = 15

            random.next = 100 // Guarantee the roll fails.
            useFurnace(furnace)
            choose(index = 1, quantity = 1)
            advanceUntil({ player.count(obj("iron_ore")) == 0 }, timeoutTicks = 30)

            assertMessageSent("The ore is too impure and you fail to refine it.")
            check(player.count(obj("iron_bar")) == 0) { "Expected no bar from the failed smelt." }
        }

    @Test
    fun GameTestState.`smelting needs the level`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            player.inv[0] = InvObj(obj("iron_ore"))
            player.stats[stats.smithing] = 14

            useFurnace(furnace)
            choose(index = 1, quantity = 1)

            advanceUntilMessage("You need a Smithing level of 15 to make an iron bar.")
            check(player.count(obj("iron_ore")) == 1) { "Iron ore should be untouched." }
        }

    @Test
    fun GameTestState.`smelting needs the ores`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            player.inv[0] = InvObj(obj("copper_ore"))
            player.stats[stats.smithing] = 1

            useFurnace(furnace)
            choose(index = 0, quantity = 1)

            advanceUntilMessage("You do not have the ores needed to smelt this bar.")
            check(player.count(obj("copper_ore")) == 1) { "Copper ore should be untouched." }
        }

    @Test
    fun GameTestState.`walking away stops smelting and never holds the player`() =
        runGameTest(Smelting::class) {
            val furnace = placeFurnace()
            player.clearInv()
            for (slot in 0 until 3) {
                player.inv[slot] = InvObj(obj("copper_ore"))
                player.inv[slot + 3] = InvObj(obj("tin_ore"))
            }
            player.stats[stats.smithing] = 1

            useFurnace(furnace)
            choose(index = 0, quantity = 3)
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while smelting." }

            player.clearPendingAction(eventBus)
            advance(ticks = 15)

            check(player.count(obj("bronze_bar")) == 0) { "Expected nothing to be smelted." }
            check(player.count(obj("copper_ore")) == 3) { "Expected the ore to be kept." }
        }
}
