package org.rsmod.content.skills.cooking.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class PreparationTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: String) {
        player.teleport(coords)
        player.clearInv()
        items.forEachIndexed { slot, name -> player.inv[slot] = InvObj(obj(name)) }
        player.stats[stats.cooking] = level
        player.statMap.setXP(stats.cooking, 0)
    }

    private fun GameTestScope.useOn(first: String, second: String) {
        val comp = Component(components.inv_items.packed)
        val firstSlot = player.inv.indexOfFirst { it?.id == obj(first).id }
        val secondSlot = player.inv.indexOfFirst { it?.id == obj(second).id }
        eventBus.publish(
            IfOverlayButtonT(player, firstSlot, obj(first), secondSlot, obj(second), comp, comp)
        )
    }

    /** Waits for the menu, then clicks its nth item with the given quantity. */
    private fun GameTestScope.choose(index: Int, quantity: Int = 1) {
        advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
        player.resumePauseButton(components.skillmulti_buttons[index], quantity)
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 10) {
        repeat(timeoutTicks) {
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
            advance()
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.xp(): Double = player.statMap.getFineXP(stats.cooking) / 10.0

    private fun GameTestScope.placeRange(): BoundLocInfo {
        val type = locTypes.values.first { it.name == "Range" && it.op.getOrNull(0) == "Cook" }
        val range = placeMapLoc(coords, type)
        player.teleport(range.coords.translateX(-1))
        return range
    }

    @Test
    fun GameTestState.`flour and water make dough and give back the pot and jug`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(1, "pot_flour", "jug_water")

            useOn("pot_flour", "jug_water")
            choose(0)
            advanceUntil({ player.count(obj("bread_dough")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("pot_flour")) == 0) { "The flour should be used up." }
            check(player.count(obj("jug_water")) == 0) { "The water should be used up." }
            check(player.count(obj("pot_empty")) == 1) { "Expected an empty pot." }
            check(player.count(obj("jug_empty")) == 1) { "Expected an empty jug." }
        }

    @Test
    fun GameTestState.`a pizza base needs level 35`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(34, "pot_flour", "jug_water")

            useOn("pot_flour", "jug_water")
            choose(2)
            advanceUntilMessage("You need a Cooking level of 35 to make pizza base.")

            check(player.count(obj("pot_flour")) == 1) { "Nothing should be used up." }
        }

    @Test
    fun GameTestState.`a pie is built in steps and the stages keep their order`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(34, "pie_shell", "tomato", "onion", "cabbage")

            useOn("pie_shell", "tomato")
            choose(0)
            advanceUntil({ player.count(obj("unfinished_garden_pie_1")) == 1 }, timeoutTicks = 10)

            useOn("unfinished_garden_pie_1", "onion")
            choose(0)
            advanceUntil({ player.count(obj("unfinished_garden_pie_2")) == 1 }, timeoutTicks = 10)

            useOn("unfinished_garden_pie_2", "cabbage")
            choose(0)
            advanceUntil({ player.count(obj("uncooked_garden_pie")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("uncooked_garden_pie")) == 1) { "Expected a raw garden pie." }
        }

    @Test
    fun GameTestState.`a cake needs four things and gives back the bucket and pot`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(40, "cake_tin", "egg", "pot_flour", "bucket_milk")

            useOn("egg", "bucket_milk")
            choose(0)
            advanceUntil({ player.count(obj("uncooked_cake")) == 1 }, timeoutTicks = 10)

            check(player.count(obj("bucket_empty")) == 1) { "Expected an empty bucket." }
            check(player.count(obj("pot_empty")) == 1) { "Expected an empty pot." }
            check(player.count(obj("cake_tin")) == 0) { "The tin goes into the cake." }
        }

    @Test
    fun GameTestState.`chopping an onion needs a knife and a bowl`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(1, "bowl_empty", "onion")

            useOn("bowl_empty", "onion")
            choose(0)
            advanceUntilMessage("You need a knife to do this.")

            check(player.count(obj("onion")) == 1) { "Nothing should be used up." }
        }

    @Test
    fun GameTestState.`making several items takes two ticks each`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(1, "pie_shell", "redberries", "redberries", "redberries")
            player.stats[stats.cooking] = 10

            useOn("pie_shell", "redberries")
            choose(0, quantity = 3)
            advanceUntil({ player.count(obj("uncooked_redberry_pie")) == 1 }, timeoutTicks = 10)

            // There is one shell, so only one pie can be made.
            check(player.count(obj("redberries")) == 2) { "Expected two berries left." }
        }

    @Test
    fun GameTestState.`unfermented wine becomes wine and gives 200 experience`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(99, "grapes", "jug_water")

            useOn("grapes", "jug_water")
            choose(0)
            advanceUntil({ player.count(obj("jug_unfermented_wine")) == 1 }, timeoutTicks = 10)
            check(xp() == 0.0) { "Mixing the grapes gives no experience." }

            random.next = 0 // The roll succeeds.
            advanceUntil({ player.count(obj("jug_wine")) == 1 }, timeoutTicks = 40)

            check(player.count(obj("jug_unfermented_wine")) == 0) {
                "Expected the wine to ferment."
            }
            check(xp() == 200.0) { "Expected 200 experience, got ${xp()}." }
        }

    @Test
    fun GameTestState.`a failed batch of wine is bad wine`() =
        runGameTest(Preparation::class, HeldUOpScript::class) {
            prepare(35, "grapes", "jug_water")

            useOn("grapes", "jug_water")
            choose(0)
            advanceUntil({ player.count(obj("jug_unfermented_wine")) == 1 }, timeoutTicks = 10)

            random.next = 100 // The roll fails.
            advanceUntil({ player.count(obj("jug_bad_wine")) == 1 }, timeoutTicks = 40)

            check(xp() == 0.0) { "A failed wine gives no experience." }
        }

    @Test
    fun GameTestState.`a potato is baked on a range`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("potato"))
            player.stats[stats.cooking] = 7

            random.next = 0
            player.opLoc1(range)
            advanceUntil({ player.count(obj("potato_baked")) >= 1 }, timeoutTicks = 20)

            check(player.count(obj("potato_baked")) == 1) { "Expected a baked potato." }
        }

    @Test
    fun GameTestState.`a cake is baked and the tin comes back`() =
        runGameTest(Cooking::class) {
            val range = placeRange()
            player.clearInv()
            player.inv[0] = InvObj(obj("uncooked_cake"))
            player.stats[stats.cooking] = 40

            random.next = 0
            player.opLoc1(range)
            advanceUntil({ player.count(obj("cake")) >= 1 }, timeoutTicks = 20)

            check(player.count(obj("cake")) == 1) { "Expected a cake." }
            check(player.count(obj("cake_tin")) == 1) { "Expected the cake tin back." }
        }
}
