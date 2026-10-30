package org.rsmod.content.skills.fishing.scripts

import org.junit.jupiter.api.Test
import org.rsmod.api.config.refs.stats
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.inv.InvObj
import org.rsmod.game.type.npc.UnpackedNpcType
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.map.CoordGrid

class FishingScriptTest {
    private val coords = CoordGrid(0, 50, 50, 34, 31)

    private fun GameTestScope.obj(internal: String): UnpackedObjType =
        objTypes.values.first { it.internalName == internal }

    private fun GameTestScope.spot(op1: String, op3: String): UnpackedNpcType =
        npcTypes.values.first {
            it.name.endsWith("Fishing spot") &&
                it.op.getOrNull(0) == op1 &&
                it.op.getOrNull(2) == op3
        }

    private fun GameTestScope.fishAt(type: UnpackedNpcType) =
        spawnNpc(coords, type).also { player.teleport(it.coords.translateX(-1)) }

    @Test
    fun GameTestState.`catch shrimp with a small net`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("net"))
            player.stats[stats.fishing] = 1

            random.next = 0 // Guarantee the catch roll succeeds.
            player.opNpc1(npc)
            advance(ticks = 2)
            assertMessageSent("You cast out your net...")

            advanceUntil({ player.count(obj("raw_shrimp")) >= 1 }, timeoutTicks = 20)
            assertMessageSent("You catch some shrimps.")
        }

    @Test
    fun GameTestState.`small net spot needs a small net`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.stats[stats.fishing] = 99

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("You need a small fishing net to catch these fish.")
        }

    @Test
    fun GameTestState.`bait fishing uses up bait`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("fishing_rod"))
            player.inv[1] = InvObj(obj("fishing_bait"), 10)
            player.stats[stats.fishing] = 5

            random.next = 0
            player.opNpc3(npc)
            advanceUntil({ player.count(obj("raw_sardine")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You catch a sardine.")
            check(player.count(obj("raw_sardine")) == 1) { "Expected a single raw sardine." }
            check(player.count(obj("fishing_bait")) == 9) { "Expected one bait to be used." }
        }

    @Test
    fun GameTestState.`bait fishing needs bait`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("fishing_rod"))
            player.stats[stats.fishing] = 5

            player.opNpc3(npc)
            advance(ticks = 1)

            assertMessageSent("You don't have any fishing bait left.")
        }

    @Test
    fun GameTestState.`lobster needs level 40`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Cage", "Harpoon"))
            player.clearInv()
            player.inv[0] = InvObj(obj("lobster_pot"))
            player.stats[stats.fishing] = 39

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("You need a Fishing level of 40 to catch lobsters.")
        }

    @Test
    fun GameTestState.`catch tuna with a harpoon`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Cage", "Harpoon"))
            player.clearInv()
            player.inv[0] = InvObj(obj("harpoon"))
            player.stats[stats.fishing] = 35

            random.next = 0
            player.opNpc3(npc)
            advance(ticks = 2)
            assertMessageSent("You start harpooning fish.")

            advanceUntil({ player.count(obj("raw_tuna")) >= 1 }, timeoutTicks = 20)
            assertMessageSent("You catch a tuna.")
        }

    @Test
    fun GameTestState.`fishing stops when the inventory is full`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.fillInv(InvObj(obj("net")))
            player.stats[stats.fishing] = 1

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("Your inventory is too full to hold any more fish.")
        }

    @Test
    fun GameTestState.`echo harpoon works as a net and is a tick faster`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("league_trailblazer_harpoon"))
            player.stats[stats.fishing] = 1

            random.next = 0
            player.opNpc1(npc)
            // A standard net catches on the fifth tick. The Echo harpoon catches on the fourth.
            advance(ticks = 3)
            check(player.count(obj("raw_shrimp")) == 0) { "Caught too early." }
            advance(ticks = 1)
            assertMessageSent("You catch some shrimps.")
        }

    @Test
    fun GameTestState.`standard net catches on the fifth tick`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("net"))
            player.stats[stats.fishing] = 1

            random.next = 0
            player.opNpc1(npc)
            advance(ticks = 4)
            check(player.count(obj("raw_shrimp")) == 0) { "Caught too early." }
            advance(ticks = 1)
            assertMessageSent("You catch some shrimps.")
        }

    @Test
    fun GameTestState.`echo harpoon needs no bait`() =
        runGameTest(Fishing::class) {
            val npc = fishAt(spot("Small Net", "Bait"))
            player.clearInv()
            player.inv[0] = InvObj(obj("league_trailblazer_harpoon"))
            player.stats[stats.fishing] = 5

            random.next = 0
            player.opNpc3(npc)
            advanceUntil({ player.count(obj("raw_sardine")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You catch a sardine.")
        }

    @Test
    fun GameTestState.`echo harpoon works as a karambwan vessel`() =
        runGameTest(Fishing::class) {
            val type = npcTypes.values.first { it.internalName == "0_45_48_karambwan" }
            val npc = fishAt(type)
            player.clearInv()
            player.inv[0] = InvObj(obj("league_trailblazer_harpoon"))
            player.stats[stats.fishing] = 65

            random.next = 0
            player.opNpc1(npc)
            advanceUntil({ player.count(obj("tbwt_raw_karambwan")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You catch a karambwan.")
        }

    @Test
    fun GameTestState.`karambwan needs a vessel and karambwanji without the echo harpoon`() =
        runGameTest(Fishing::class) {
            val type = npcTypes.values.first { it.internalName == "0_45_48_karambwan" }
            val npc = fishAt(type)
            player.clearInv()
            player.stats[stats.fishing] = 65

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("You need a karambwan vessel to catch these fish.")
        }

    @Test
    fun GameTestState.`anglerfish uses a rod and sandworms`() =
        runGameTest(Fishing::class) {
            val type = npcTypes.values.first { it.internalName == "piscariliusfish" }
            val npc = fishAt(type)
            player.clearInv()
            player.inv[0] = InvObj(obj("fishing_rod"))
            player.inv[1] = InvObj(obj("piscarilius_sandworms"), 10)
            player.stats[stats.fishing] = 82

            random.next = 0
            player.opNpc1(npc)
            advanceUntil({ player.count(obj("raw_anglerfish")) >= 1 }, timeoutTicks = 20)

            assertMessageSent("You catch an anglerfish.")
            check(player.count(obj("piscarilius_sandworms")) == 9) { "Expected one worm used." }
        }

    @Test
    fun GameTestState.`anglerfish needs level 82`() =
        runGameTest(Fishing::class) {
            val type = npcTypes.values.first { it.internalName == "piscariliusfish" }
            val npc = fishAt(type)
            player.clearInv()
            player.inv[0] = InvObj(obj("fishing_rod"))
            player.inv[1] = InvObj(obj("piscarilius_sandworms"), 10)
            player.stats[stats.fishing] = 81

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("You need a Fishing level of 82 to catch anglerfish.")
        }

    @Test
    fun GameTestState.`anglerfish needs sandworms`() =
        runGameTest(Fishing::class) {
            val type = npcTypes.values.first { it.internalName == "piscariliusfish" }
            val npc = fishAt(type)
            player.clearInv()
            player.inv[0] = InvObj(obj("fishing_rod"))
            player.inv[1] = InvObj(obj("fishing_bait"), 10)
            player.stats[stats.fishing] = 99

            player.opNpc1(npc)
            advance(ticks = 1)

            assertMessageSent("You need sandworms to fish at this spot.")
        }
}
