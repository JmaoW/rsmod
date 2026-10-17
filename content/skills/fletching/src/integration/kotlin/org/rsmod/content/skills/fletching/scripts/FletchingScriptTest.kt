package org.rsmod.content.skills.fletching.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.slayer.configs.SlayerUnlocks
import org.rsmod.content.skills.slayer.configs.SlayerVarps
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class FletchingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
        player.teleport(coords)
        player.clearInv()
        var slot = 0
        for ((name, count) in items) {
            val type = obj(name)
            if (type.isStackable) {
                player.inv[slot++] = InvObj(type, count)
            } else {
                repeat(count) { player.inv[slot++] = InvObj(type) }
            }
        }
        player.stats[stats.fletching] = level
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
    private fun GameTestScope.choose(index: Int, quantity: Int) {
        advanceUntil({ player.ui.containsModal(interfaces.skillmulti) }, timeoutTicks = 10)
        player.resumePauseButton(components.skillmulti_buttons[index], quantity)
    }

    private fun GameTestScope.advanceUntilMessage(text: String, timeoutTicks: Int = 20) {
        repeat(timeoutTicks) {
            advance()
            if (runCatching { assertMessageSent(text) }.isSuccess) {
                return
            }
        }
        assertMessageSent(text)
    }

    private fun GameTestScope.xp(): Double = player.statMap.getXP(stats.fletching).toDouble()

    @Test
    fun GameTestState.`knife on logs makes a batch of arrow shafts`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "knife" to 1, "logs" to 1)

            useOn("knife", "logs")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("arrow_shaft")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("arrow_shaft")) == 15) { "Expected 15 arrow shafts." }
            check(player.count(obj("logs")) == 0) { "Expected the log to be used." }
            check(xp() > 0) { "Expected Fletching experience." }
        }

    @Test
    fun GameTestState.`the quantity asked for is made`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "knife" to 1, "logs" to 3)

            useOn("knife", "logs")
            choose(index = 0, quantity = 2)
            advanceUntil({ player.count(obj("arrow_shaft")) >= 30 }, timeoutTicks = 30)
            advance(ticks = 6)

            check(player.count(obj("arrow_shaft")) == 30) { "Expected two batches of shafts." }
            check(player.count(obj("logs")) == 1) { "Expected one log left." }
        }

    @Test
    fun GameTestState.`logs are cut into a bow only at the level`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "knife" to 1, "oak_logs" to 1)

            useOn("knife", "oak_logs")
            // Oak logs offer shafts, a shortbow, a longbow, a stock and a shield.
            choose(index = 1, quantity = 1)
            advanceUntilMessage("You need a Fletching level of 20 to make oak shortbow (u).")

            check(player.count(obj("oak_logs")) == 1) { "Expected the log to be kept." }

            player.stats[stats.fletching] = 20
            useOn("knife", "oak_logs")
            choose(index = 1, quantity = 1)
            advanceUntil({ player.count(obj("unstrung_oak_shortbow")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("oak_logs")) == 0) { "Expected the log to be used." }
        }

    @Test
    fun GameTestState.`a bow string strings an unstrung bow`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(5, "unstrung_shortbow" to 1, "bow_string" to 1)

            useOn("bow_string", "unstrung_shortbow")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("shortbow")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("unstrung_shortbow")) == 0) { "Expected the bow to be used." }
            check(player.count(obj("bow_string")) == 0) { "Expected the string to be used." }
        }

    @Test
    fun GameTestState.`feathers on shafts make a batch of headless arrows`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "arrow_shaft" to 20, "feather" to 20)

            useOn("feather", "arrow_shaft")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("headless_arrow")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("headless_arrow")) == 15) { "Expected one batch of 15." }
            check(player.count(obj("arrow_shaft")) == 5) { "Expected five shafts left." }
            check(player.count(obj("feather")) == 5) { "Expected five feathers left." }
        }

    @Test
    fun GameTestState.`a batch is as large as the materials allow`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(15, "headless_arrow" to 40, "iron_arrowheads" to 7)

            useOn("headless_arrow", "iron_arrowheads")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("iron_arrow")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("iron_arrow")) == 7) { "Expected seven arrows." }
            check(player.count(obj("headless_arrow")) == 33) { "Expected 33 headless arrows left." }
            // 2.5 experience each, and the experience rate multiplies it.
            check(xp() >= 17.5) { "Expected experience for every arrow." }
        }

    @Test
    fun GameTestState.`a crossbow needs a hammer`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(9, "xbows_crossbow_stock_wood" to 1, "xbows_crossbow_limbs_bronze" to 1)

            useOn("xbows_crossbow_stock_wood", "xbows_crossbow_limbs_bronze")
            choose(index = 0, quantity = 1)
            advanceUntilMessage("You need a hammer to make this.")

            check(player.count(obj("xbows_crossbow_stock_wood")) == 1) {
                "Expected the stock kept."
            }
        }

    @Test
    fun GameTestState.`a stock and limbs make an unstrung crossbow`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(
                9,
                "hammer" to 1,
                "xbows_crossbow_stock_wood" to 1,
                "xbows_crossbow_limbs_bronze" to 1,
            )

            useOn("xbows_crossbow_stock_wood", "xbows_crossbow_limbs_bronze")
            choose(index = 0, quantity = 1)
            advanceUntil(
                { player.count(obj("xbows_crossbow_unstrung_bronze")) == 1 },
                timeoutTicks = 20,
            )

            check(player.count(obj("xbows_crossbow_limbs_bronze")) == 0) { "Expected limbs used." }
            check(player.count(obj("hammer")) == 1) { "Expected the hammer kept." }
        }

    @Test
    fun GameTestState.`a chisel cuts a gem into a dozen bolt tips`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(56, "chisel" to 1, "sapphire" to 1)

            useOn("chisel", "sapphire")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("xbows_bolt_tips_sapphire")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("xbows_bolt_tips_sapphire")) == 12) { "Expected 12 bolt tips." }
            check(player.count(obj("sapphire")) == 0) { "Expected the gem to be used." }
        }

    @Test
    fun GameTestState.`tips on bolts make tipped bolts`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(56, "xbows_bolt_tips_sapphire" to 12, "xbows_crossbow_bolts_mithril" to 10)

            useOn("xbows_bolt_tips_sapphire", "xbows_crossbow_bolts_mithril")
            choose(index = 0, quantity = 1)
            advanceUntil(
                { player.count(obj("xbows_crossbow_bolts_mithril_tipped_sapphire")) > 0 },
                timeoutTicks = 20,
            )

            check(player.count(obj("xbows_crossbow_bolts_mithril_tipped_sapphire")) == 10) {
                "Expected ten sapphire bolts."
            }
            check(player.count(obj("xbows_bolt_tips_sapphire")) == 2) { "Expected two tips left." }
        }

    @Test
    fun GameTestState.`a coloured feather works and plain ones are used first`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(
                1,
                "arrow_shaft" to 6,
                "feather" to 2,
                "hunting_stripy_bird_feather" to 3,
                "hunting_polar_feather" to 1,
            )

            useOn("hunting_stripy_bird_feather", "arrow_shaft")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("headless_arrow")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("headless_arrow")) == 6) { "Expected six headless arrows." }
            check(player.count(obj("feather")) == 0) { "Expected the plain feathers used first." }
            check(player.count(obj("hunting_stripy_bird_feather")) == 0) { "Expected stripy used." }
            check(player.count(obj("hunting_polar_feather")) == 0) { "Expected the blue used." }
        }

    @Test
    fun GameTestState.`dragon darts need level 95`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(94, "dragon_dart_tip" to 10, "feather" to 10)

            useOn("dragon_dart_tip", "feather")
            choose(index = 0, quantity = 1)
            advanceUntilMessage("You need a Fletching level of 95 to make dragon dart.")

            player.stats[stats.fletching] = 95
            useOn("dragon_dart_tip", "feather")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("dragon_dart")) == 10 }, timeoutTicks = 20)
        }

    @Test
    fun GameTestState.`pearl tips come from an oyster pearl`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(41, "chisel" to 1, "smalloysterpearls" to 1)

            useOn("chisel", "smalloysterpearls")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("pearl_bolttips")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("pearl_bolttips")) == 6) { "Expected six pearl bolt tips." }
        }

    @Test
    fun GameTestState.`broad arrows need the Broader Fletching unlock`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(52, "headless_arrow" to 15, "slayer_broad_arrowhead" to 15)

            useOn("headless_arrow", "slayer_broad_arrowhead")
            advance(ticks = 5)
            check(player.count(obj("slayer_broad_arrows")) == 0) { "Expected no broad arrows." }
            check(player.count(obj("headless_arrow")) == 15) {
                "Expected the materials to be kept."
            }
        }

    @Test
    fun GameTestState.`broad arrows are made with the Broader Fletching unlock`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            VarPlayerIntMapSetter.set(
                player,
                SlayerVarps.unlocks,
                1 shl SlayerUnlocks.BROADER_FLETCHING,
            )
            prepare(52, "headless_arrow" to 15, "slayer_broad_arrowhead" to 15)

            useOn("headless_arrow", "slayer_broad_arrowhead")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("slayer_broad_arrows")) == 15 }, timeoutTicks = 20)
        }

    @Test
    fun GameTestState.`walking away stops fletching and never holds the player`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "knife" to 1, "logs" to 3)

            useOn("knife", "logs")
            choose(index = 0, quantity = 3)
            advance(ticks = 1)
            check(!player.isDelayed) { "Expected the player to be free to walk while fletching." }

            // Walking and clicking something else both close everything with `ifClose`.
            player.ifClose(eventBus)
            advance(ticks = 10)

            check(player.count(obj("arrow_shaft")) == 0) { "Expected the job to stop." }
            check(player.count(obj("logs")) == 3) { "Expected the logs to be kept." }
        }

    @Test
    fun GameTestState.`using items again while fletching starts a new job without an error`() =
        runGameTest(Fletching::class, HeldUOpScript::class) {
            prepare(1, "knife" to 1, "logs" to 3)

            useOn("knife", "logs")
            choose(index = 0, quantity = 3)
            advance(ticks = 1)

            // This used to throw while the player was delayed, which logged them out.
            useOn("knife", "logs")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("arrow_shaft")) > 0 }, timeoutTicks = 20)
            advance(ticks = 10)

            check(player.count(obj("arrow_shaft")) == 15) {
                "Expected only the second job's batch."
            }
            check(player.count(obj("logs")) == 2) { "Expected two logs left." }
        }
}
