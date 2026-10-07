package org.rsmod.content.skills.crafting.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.components
import org.rsmod.api.config.refs.interfaces
import org.rsmod.api.config.refs.stats
import org.rsmod.api.inv.HeldUOpScript
import org.rsmod.api.inv.LocUOpScript
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.content.skills.crafting.configs.GoldComponents
import org.rsmod.content.skills.crafting.configs.SilverComponents
import org.rsmod.content.skills.crafting.configs.TannerComponents
import org.rsmod.game.inv.InvObj
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class CraftingStationTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.prepare(level: Int, vararg items: Pair<String, Int>) {
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

    /** Places the loc and stands the player on the side it can be used from ([east] or west). */
    private fun GameTestScope.place(
        internal: String,
        at: CoordGrid = coords,
        east: Boolean = false,
    ): BoundLocInfo {
        val type = locTypes.values.first { it.internalName == internal }
        val loc = placeMapLoc(at, type)
        player.teleport(loc.coords.translateX(if (east) 1 else -1))
        return loc
    }

    private fun GameTestScope.placeFurnace(): BoundLocInfo {
        val type =
            locTypes.values.first {
                it.name.equals("Furnace", ignoreCase = true) && it.op.take(3).contains("Smelt")
            }
        val furnace = placeMapLoc(coords, type)
        player.teleport(furnace.coords.translateX(-1))
        return furnace
    }

    private fun GameTestScope.useOnLoc(loc: BoundLocInfo, item: String) {
        val slot = player.inv.indexOfFirst { it?.id == obj(item).id }
        player.opLocT(loc, obj(item), slot)
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

    private fun GameTestScope.fineXp(): Double = player.statMap.getFineXP(stats.crafting) / 10.0

    @Test
    fun GameTestState.`a spinning wheel spins wool into balls of wool`() =
        runGameTest(Crafting::class, Stations::class) {
            val wheel = place("spinningwheel")
            prepare(1, "wool" to 3)

            player.opLoc2(wheel)
            choose(index = 0, quantity = 2)
            advanceUntil({ player.count(obj("ball_of_wool")) == 2 }, timeoutTicks = 30)

            check(player.count(obj("wool")) == 1) { "Expected one wool left." }
            check(fineXp() == 5.0) { "Expected 5 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`flax is spun into a bow string at level 10`() =
        runGameTest(Crafting::class, Stations::class) {
            val wheel = place("spinningwheel")
            prepare(10, "flax" to 1)

            player.opLoc2(wheel)
            // Ball of wool, bow string, crossbow string, magic string, rope.
            choose(index = 1, quantity = 1)
            advanceUntil({ player.count(obj("bow_string")) == 1 }, timeoutTicks = 20)

            check(fineXp() == 15.0) { "Expected 15 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`using an item on a spinning wheel lists only what it makes`() =
        runGameTest(Crafting::class, Stations::class, LocUOpScript::class) {
            val wheel = place("spinningwheel")
            prepare(10, "flax" to 1)

            useOnLoc(wheel, "flax")
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("bow_string")) == 1 }, timeoutTicks = 20)
        }

    @Test
    fun GameTestState.`a loom weaves four balls of wool into a strip of cloth`() =
        runGameTest(Crafting::class, Stations::class) {
            val loom = place("loom")
            prepare(10, "ball_of_wool" to 4)

            player.opLoc2(loom)
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("regicide_cloth")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("ball_of_wool")) == 0) { "Expected the wool to be used." }
            check(fineXp() == 12.0) { "Expected 12 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a potter's wheel shapes soft clay and the oven fires it`() =
        runGameTest(Crafting::class, Stations::class) {
            val wheel = place("potterywheel", east = true)
            prepare(13, "softclay" to 1)

            player.opLoc1(wheel)
            // Pot, cup, pie dish, bowl, plant pot, pot lid.
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("pot_unfired")) == 1 }, timeoutTicks = 20)
            check(fineXp() == 6.3) { "Expected 6.3 experience, was ${fineXp()}." }

            val oven = place("potteryoven", coords.translateX(8), east = true)
            player.opLoc1(oven)
            choose(index = 0, quantity = 1)
            advanceUntil({ player.count(obj("pot_empty")) == 1 }, timeoutTicks = 30)

            check(player.count(obj("pot_unfired")) == 0) { "Expected the clay to be used." }
            check(fineXp() == 12.6) { "Expected 12.6 experience in all, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`one soft clay makes four cups`() =
        runGameTest(Crafting::class, Stations::class) {
            val wheel = place("potterywheel", east = true)
            prepare(3, "softclay" to 1)

            player.opLoc1(wheel)
            choose(index = 1, quantity = 1)
            advanceUntil({ player.count(obj("cup_unfired")) == 4 }, timeoutTicks = 20)

            check(fineXp() == 8.5) { "Expected 8.5 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`sand and soda ash on a furnace make molten glass`() =
        runGameTest(Crafting::class, Stations::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(1, "bucket_sand" to 2, "soda_ash" to 2)

            useOnLoc(furnace, "bucket_sand")
            advanceUntil({ player.count(obj("molten_glass")) == 2 }, timeoutTicks = 30)

            check(player.count(obj("bucket_empty")) == 2) { "Expected the buckets back." }
            check(fineXp() == 40.0) { "Expected 40 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a gold bar on a furnace opens the interface and makes a ring`() =
        runGameTest(Crafting::class, Stations::class, Jewellery::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(20, "gold_bar" to 1, "sapphire" to 1, "ring_mould" to 1)

            useOnLoc(furnace, "gold_bar")
            advance(ticks = 8)
            player.ifButton(GoldComponents.sapphire_ring)
            advanceUntil({ player.count(obj("sapphire_ring")) == 1 }, timeoutTicks = 20)

            check(player.count(obj("gold_bar")) == 0) { "Expected the bar to be used." }
            check(player.count(obj("sapphire")) == 0) { "Expected the gem to be used." }
            check(player.count(obj("ring_mould")) == 1) { "Expected the mould to be kept." }
            check(fineXp() == 40.0) { "Expected 40 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`the quantity buttons set how many are made`() =
        runGameTest(Crafting::class, Stations::class, Jewellery::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(5, "gold_bar" to 5, "ring_mould" to 1)

            useOnLoc(furnace, "gold_bar")
            advance(ticks = 8)
            player.ifButton(GoldComponents.make_5)
            player.ifButton(GoldComponents.gold_ring)
            advanceUntil({ player.count(obj("gold_ring")) == 5 }, timeoutTicks = 40)

            check(player.count(obj("gold_bar")) == 0) { "Expected all five bars to be used." }
        }

    @Test
    fun GameTestState.`jewellery needs the level and the mould`() =
        runGameTest(Crafting::class, Stations::class, Jewellery::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(5, "gold_bar" to 1, "amulet_mould" to 1, "emerald" to 1)

            useOnLoc(furnace, "gold_bar")
            advance(ticks = 8)
            player.ifButton(GoldComponents.emerald_amulet)
            advanceUntilMessage("You need a Crafting level of 31 to make emerald amulet (u).")

            check(player.count(obj("gold_bar")) == 1) { "Expected the bar to be kept." }
        }

    @Test
    fun GameTestState.`a slayer ring is made from an enchanted gem`() =
        runGameTest(Crafting::class, Stations::class, Jewellery::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(75, "gold_bar" to 1, "ring_mould" to 1, "slayer_gem" to 1)

            useOnLoc(furnace, "gold_bar")
            advance(ticks = 8)
            player.ifButton(GoldComponents.slayer_ring)
            advanceUntil({ player.count(obj("slayer_ring_8")) == 1 }, timeoutTicks = 20)

            check(fineXp() == 15.0) { "Expected 15 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a silver bar makes silver jewellery and bolts`() =
        runGameTest(Crafting::class, Stations::class, Jewellery::class, LocUOpScript::class) {
            val furnace = placeFurnace()
            prepare(21, "silver_bar" to 1, "xbows_silver_bolt_mould" to 1)

            useOnLoc(furnace, "silver_bar")
            advance(ticks = 8)
            player.ifButton(SilverComponents.crossbow_bolt)
            advanceUntil(
                { player.count(obj("xbows_crossbow_bolts_silver_unfeathered")) == 10 },
                timeoutTicks = 20,
            )

            check(fineXp() == 50.0) { "Expected 50 experience, was ${fineXp()}." }
        }

    @Test
    fun GameTestState.`a tanner tans hides for coins`() =
        runGameTest(Crafting::class, Tanner::class, HeldUOpScript::class) {
            val tanner = npcTypes.values.first { it.internalName == "ellis_tanner" }
            val ellis = spawnNpc(coords, tanner)
            player.teleport(coords.translateX(-1))
            prepare(1, "cow_hide" to 3, "coins" to 100)

            player.opNpc3(ellis)
            advance(ticks = 3)
            player.ifLegacyButton(TannerComponents.tan_all[0])
            advance(ticks = 2)

            check(player.count(obj("leather")) == 3) { "Expected three leathers." }
            check(player.count(obj("cow_hide")) == 0) { "Expected the hides to be used." }
            check(player.count(obj("coins")) == 97) { "Expected 3 coins to be paid." }
        }

    @Test
    fun GameTestState.`tanning is limited by the coins`() =
        runGameTest(Crafting::class, Tanner::class, HeldUOpScript::class) {
            val tanner = npcTypes.values.first { it.internalName == "ellis_tanner" }
            val ellis = spawnNpc(coords, tanner)
            player.teleport(coords.translateX(-1))
            prepare(1, "dragonhide_green" to 3, "coins" to 45)

            player.opNpc3(ellis)
            advance(ticks = 3)
            player.ifLegacyButton(TannerComponents.tan_all[4])
            advance(ticks = 2)

            check(player.count(obj("dragon_leather")) == 2) { "Expected two leathers." }
            check(player.count(obj("dragonhide_green")) == 1) { "Expected one hide left." }
            check(player.count(obj("coins")) == 5) { "Expected 40 coins to be paid." }
        }
}
