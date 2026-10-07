package org.rsmod.content.skills.crafting.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.player.ui.IfOverlayButtonT
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.ui.Component
import org.rsmod.map.CoordGrid

class CraftingScriptTest {
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
        player.stats[stats.crafting] = level
        player.statMap.setXP(stats.crafting, 0)
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

    private fun GameTestScope.xp(): Double = player.statMap.getXP(stats.crafting).toDouble()

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.crafting) / 10.0

    @Test
    fun GameTestState.`needle and thread on leather make leather gloves`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "thread" to 5, "leather" to 1)

            useOn("needle", "leather")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("leather_gloves")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("leather")) == 0) { "Expected the leather to be used." }
            check(player.count(obj("needle")) == 1) { "Expected the needle to be kept." }
            check(player.count(obj("thread")) == 5) { "Expected the thread to last a while." }
            check(fineXp() == 13.8) { "Expected 13.8 Crafting experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`using the thread on leather works too`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "thread" to 5, "leather" to 1)

            useOn("thread", "leather")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("leather_gloves")) == 1 }, timeoutTicks = 20)
        }

    @Test
    fun GameTestState.`a spool of thread lasts five actions`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "thread" to 2, "leather" to 6)

            useOn("needle", "leather")
            choose(index = 0, quantity = 5)
            advanceUntilMessage("You use up a reel of thread.", timeoutTicks = 40)

            check(player.count(obj("leather_gloves")) == 5) { "Expected five gloves." }
            check(player.count(obj("thread")) == 1) { "Expected one spool to be used up." }
        }

    @Test
    fun GameTestState.`no thread means no sewing`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "leather" to 1)

            useOn("needle", "leather")
            choose(index = 0, quantity = 1)
            advanceUntilMessage("You need thread to make this.")

            check(player.count(obj("leather")) == 1) { "Expected the leather to be kept." }
        }

    @Test
    fun GameTestState.`the level is needed`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "thread" to 1, "leather" to 1)

            useOn("needle", "leather")
            // The list is gloves, boots, cowl, vambraces, body, chaps, coif.
            choose(index = 4, quantity = 1)
            advanceUntilMessage("You need a Crafting level of 14 to make leather body.")

            check(player.count(obj("leather")) == 1) { "Expected the leather to be kept." }
        }

    @Test
    fun GameTestState.`dragonhide body takes three leathers`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(63, "needle" to 1, "thread" to 1, "dragon_leather" to 4)

            useOn("needle", "dragon_leather")
            // Vambraces, chaps, body.
            choose(index = 2, quantity = 1)
            advanceUntil({ player.count(obj("dragonhide_body")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("dragon_leather")) == 1) { "Expected three leathers used." }
            check(fineXp() == 186.0) { "Expected 186 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a chisel on a gem cuts every gem in the stack`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(20, "chisel" to 1, "uncut_sapphire" to 3)

            useOn("chisel", "uncut_sapphire")
            advanceUntil({ player.count(obj("sapphire")) == 3 }, timeoutTicks = 30)

            check(player.count(obj("uncut_sapphire")) == 0) { "Expected the gems to be used." }
            check(fineXp() == 150.0) { "Expected 150 experience, was ${fineXp()}." }
            assertMessageSent("You cut the sapphire.")
        }

    @Test
    fun GameTestState.`opals are cut or crushed and either way the gem is used`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "chisel" to 1, "uncut_opal" to 20)

            useOn("chisel", "uncut_opal")
            advanceUntil({ player.count(obj("uncut_opal")) == 0 }, timeoutTicks = 80)

            val cut = player.count(obj("opal"))
            val crushed = player.count(obj("crushed_gemstone"))
            check(cut + crushed == 20) { "Expected every opal to be cut or crushed." }
            val expected = cut * 15.0 + crushed * 3.8
            check(kotlin.math.abs(fineXp() - expected) < 0.5) {
                "Expected $expected experience for $cut cut and $crushed crushed, was ${fineXp()}."
            }
        }

    @Test
    fun GameTestState.`a sapphire needs level 20`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(19, "chisel" to 1, "uncut_sapphire" to 1)

            // Nothing is asked first, so the message is sent as soon as the items are used.
            useOn("chisel", "uncut_sapphire")
            assertMessageSent("You need a Crafting level of 20 to make sapphire.")

            check(player.count(obj("uncut_sapphire")) == 1) { "Expected the gem to be kept." }
        }

    @Test
    fun GameTestState.`a hard leather shield needs a hammer, nails, and a shield`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(41, "hammer" to 1, "oak_shield" to 1, "hard_leather" to 2, "nails_bronze" to 15)

            useOn("hammer", "oak_shield")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("leather_shield")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("hard_leather")) == 0) { "Expected the leather to be used." }
            check(player.count(obj("nails_bronze")) == 0) { "Expected the nails to be used." }
            check(player.count(obj("hammer")) == 1) { "Expected the hammer to be kept." }
            check(fineXp() == 70.0) { "Expected 70 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a broodoo shield takes nails of any type`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(
                35,
                "hammer" to 1,
                "broodoomask_poison" to 1,
                "village_snake_skin" to 2,
                "nails_bronze" to 3,
                "nails_rune" to 5,
            )

            useOn("hammer", "broodoomask_poison")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("broodoo_poisonshield_10")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("nails_bronze")) + player.count(obj("nails_rune")) == 0) {
                "Expected all eight nails to be used."
            }
        }

    @Test
    fun GameTestState.`an orb on a battlestaff makes an elemental staff`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(66, "battlestaff" to 1, "air_orb" to 1)

            useOn("air_orb", "battlestaff")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("air_battlestaff")) == 1 }, timeoutTicks = 20)

            check(fineXp() == 137.5) { "Expected 137.5 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`amethyst is cut into tips by the batch`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(85, "chisel" to 1, "amethyst" to 2)

            useOn("chisel", "amethyst")
            // Bolt tips, arrowtips, javelin tips, dart tips.
            choose(index = 1, quantity = 1)
            advanceUntil({ player.count(obj("amethyst_arrowheads")) > 0 }, timeoutTicks = 20)

            check(player.count(obj("amethyst_arrowheads")) == 15) { "Expected 15 arrowtips." }
            check(player.count(obj("amethyst")) == 1) { "Expected one amethyst left." }
            check(fineXp() == 60.0) { "Expected 60 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a ball of wool strings an amulet`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "ball_of_wool" to 1, "unstrung_sapphire_amulet" to 1)

            useOn("ball_of_wool", "unstrung_sapphire_amulet")
            advanceUntil({ player.count(obj("strung_sapphire_amulet")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("ball_of_wool")) == 0) { "Expected the wool to be used." }
            check(fineXp() == 4.0) { "Expected 4 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`water on clay makes soft clay and keeps the container`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "clay" to 2, "bucket_water" to 1)

            useOn("bucket_water", "clay")
            advanceUntil({ player.count(obj("softclay")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("bucket_empty")) == 1) { "Expected the empty bucket back." }
            check(player.count(obj("bucket_water")) == 0) { "Expected the water to be used." }
            check(player.count(obj("clay")) == 1) { "Expected one clay left." }
        }

    @Test
    fun GameTestState.`a pipe on molten glass blows a beer glass`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "glassblowingpipe" to 1, "molten_glass" to 1)

            useOn("glassblowingpipe", "molten_glass")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("beer_glass")) == 1 }, timeoutTicks = 20)

            check(fineXp() == 17.5) { "Expected 17.5 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a birdhouse needs a chisel and a hammer`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(5, "poh_clockwork_mechanism" to 1, "logs" to 1)

            useOn("poh_clockwork_mechanism", "logs")
            choose(index = 0, quantity = 1)
            advanceUntilMessage("You need a hammer to make this.")

            check(player.count(obj("logs")) == 1) { "Expected the logs to be kept." }
        }

    @Test
    fun GameTestState.`walking away stops the job and never holds the player`() =
        runGameTest(Crafting::class, HeldUOpScript::class) {
            prepare(1, "needle" to 1, "thread" to 5, "leather" to 10)

            useOn("needle", "leather")
            choose(index = 0, quantity = 10)
            advanceUntil({ player.count(obj("leather_gloves")) >= 1 }, timeoutTicks = 20)
            check(!player.isDelayed) { "The player must never be delayed by crafting." }

            player.ifClose(eventBus)
            val made = player.count(obj("leather_gloves"))
            advance(ticks = 10)

            check(player.count(obj("leather_gloves")) == made) { "Expected the job to stop." }
        }
}
